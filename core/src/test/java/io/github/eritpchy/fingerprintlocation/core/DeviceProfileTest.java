package io.github.eritpchy.fingerprintlocation.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class DeviceProfileTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> profiles() throws IOException {
        List<Object[]> result=new ArrayList<>();
        InputStream stream=DeviceProfileTest.class.getResourceAsStream("/device-profiles.tsv");assertNotNull(stream);
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            for(String row;(row=reader.readLine())!=null;) {
                if(row.startsWith("#")||row.isEmpty())continue;
                String[] values=row.split("\t",-1);assertEquals(5,values.length);result.add(values);
            }
        }
        assertEquals(122,result.size());return result;
    }
    private final String device, format, type, value;
    public DeviceProfileTest(String device,String format,String type,String value,String provenance) {
        this.device=device;this.format=format;this.type=type;this.value=value;
        assertTrue(provenance.startsWith("https://github.com/LineageOS/"));
    }
    @Test public void reportedSensorClassificationSurvivesEveryRotationAndResolutionScale() {
        for(String entry:value.split("[;,]")) {
            String[] fields=entry.split("\\|",-1);
            double x=Double.parseDouble(fields[0]),y=Double.parseDouble(fields[1]),radius=Double.parseDouble(fields[2]);
            String display=fields.length==4?fields[3]:"";
            int nativeWidth=(int)Math.ceil(Math.max(x*2,x+radius+20));
            int nativeHeight=(int)Math.ceil(y+radius+200);
            Observation.Type expected=type.equals("under-display-unspecified")?Observation.Type.UNDER_DISPLAY:VendorProperties.type(type);
            for(int rotation=0;rotation<4;rotation++)for(double scale:new double[]{.5,1,1.5}) {
                int width=(int)Math.round(nativeWidth*scale),height=(int)Math.round(nativeHeight*scale);
                Geometry.DisplayFrame frame=new Geometry.DisplayFrame(display.isEmpty()?"local:fixture":display,0,
                        width,height,nativeWidth,nativeHeight,rotation);
                Probe probe=new Probe();
                if(format.equals("xml")) probe.add(new Observation(1,"",expected,Observation.Source.FRAMEWORK_RESOURCE,
                        Geometry.Space.NATIVE_NATURAL,Geometry.Bounds.circle(x,y,radius),device));
                else {
                    Map<String,String> properties=new HashMap<>();String prefix=format.equals("ro")?"ro.vendor.":"persist.vendor.";
                    properties.put(prefix+"fingerprint.sensor_location",value);
                    if(!type.equals("under-display-unspecified"))properties.put(prefix+"fingerprint.type",type);
                    VendorProperties.collect(properties::get,frame,1,expected,probe);
                }
                Resolution result=probe.resolve(frame,1);
                if(!expected.underDisplay()) {assertEquals(device,Resolution.Status.NOT_UNDER_DISPLAY,result.status);assertNull(result.bounds);continue;}
                assertEquals(device,Resolution.Status.FOUND,result.status);
                assertTrue(device,result.bounds.inside(frame.logicalWidth(),frame.logicalHeight()));
                double factor=Math.min(width/(double)nativeWidth,height/(double)nativeHeight);
                double cx=x*factor,cy=y*factor;
                double expectedX=rotation==1?cy:rotation==2?width-cx:rotation==3?height-cy:cx;
                double expectedY=rotation==1?width-cx:rotation==2?height-cy:rotation==3?cx:cy;
                assertEquals(device,expectedX,result.bounds.centerX(),.000001);
                assertEquals(device,expectedY,result.bounds.centerY(),.000001);
            }
        }
    }
}
