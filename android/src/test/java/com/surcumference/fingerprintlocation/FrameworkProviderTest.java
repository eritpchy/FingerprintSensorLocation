package com.surcumference.fingerprintlocation;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import com.surcumference.fingerprintlocation.core.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import android.hardware.display.DisplayManager;
import android.view.Display;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,36})
public class FrameworkProviderTest {
    private final Geometry.DisplayFrame frame=new Geometry.DisplayFrame("local:1",0,1080,2400,1080,2400,0);
    public static class Legacy31 {
        public int sensorId=7,sensorType=2,sensorLocationX=540,sensorLocationY=2100,sensorRadius=90;
    }
    public static class Location {
        public String displayId;
        public int sensorLocationX=540,sensorLocationY=2100,sensorRadius=90;
        Location(String displayId){this.displayId=displayId;}
    }
    public static class Modern {
        public int sensorId=7,sensorType=2;
        List<?> locations;
        Modern(Object... locations){this.locations=Arrays.asList(locations);}
        public List<?> getAllLocations(){return locations;}
    }
    public static class Rectangular extends Location {
        public int sensorWidth=180,sensorHeight=120;
        Rectangular(){super("");}
    }
    public static class Manager {
        final List<?> sensors;
        Manager(Object... sensors){this.sensors=Arrays.asList(sensors);}
        public List<?> getSensorPropertiesInternal(){return sensors;}
    }
    public static class Denied {
        public List<?> getSensorPropertiesInternal(){throw new SecurityException("fixture");}
    }
    public static class SamsungManager {
        public Rect semGetFingerIconRectInDisplay(){return new Rect(450,2010,630,2190);}
    }
    private Context context(Object manager) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()){
            @Override public Object getSystemService(String name){return "fingerprint".equals(name)?manager:super.getSystemService(name);}
        };
    }
    private Resolution query(Object... sensors) {
        Probe p=new Probe();AndroidProviders.framework(context(new Manager(sensors)),frame,-1,p);return p.resolve(frame,-1);
    }
    @Test public void android12FieldsAreReadWithoutNewerMethods() {
        Resolution r=query(new Legacy31());assertEquals(Resolution.Status.FOUND,r.status);
        assertEquals(2100,r.bounds.centerY(),0);assertEquals(7,r.selected.sensorId);
    }
    @Test public void modernLocationsMatchTheCurrentUniqueId() {
        Location wrong=new Location("local:2");wrong.sensorLocationY=1700;
        Resolution r=query(new Modern(wrong,new Location("local:1")));assertEquals(Resolution.Status.FOUND,r.status);assertEquals(2100,r.bounds.centerY(),0);
    }
    @Test public void vivoNumericDisplayIdentifiersAreSupported() {
        Resolution r=query(new Modern(new Location("0")));assertEquals(Resolution.Status.FOUND,r.status);
    }
    @Test public void rectangularSensorDimensionsArePreserved() {
        Resolution r=query(new Modern(new Rectangular()));assertEquals(Resolution.Status.FOUND,r.status);
        assertEquals(180,r.bounds.width(),0);assertEquals(120,r.bounds.height(),0);
    }
    @Test public void defaultFrameworkTupleRequiresCorroboration() {
        Legacy31 sensor=new Legacy31();sensor.sensorLocationY=1636;sensor.sensorRadius=130;
        Resolution r=query(sensor);assertEquals(Resolution.Status.UNAVAILABLE,r.status);assertNull(r.bounds);
    }
    @Test public void accessDenialIsUnwrappedFromReflectiveInvocation() {
        Probe p=new Probe();AndroidProviders.framework(context(new Denied()),frame,-1,p);
        assertEquals(Resolution.Status.ACCESS_DENIED,p.resolve(frame,-1).status);
    }
    @Test public void sideSensorIsClassifiedBeforeItsOffscreenBounds() {
        Legacy31 sensor=new Legacy31();sensor.sensorType=4;sensor.sensorLocationX=1080;
        assertEquals(Resolution.Status.NOT_UNDER_DISPLAY,query(sensor).status);
    }
    @Test public void multipleSensorsAreNotCollapsedIntoTheFirstEntry() {
        Legacy31 a=new Legacy31(),b=new Legacy31();b.sensorId=9;b.sensorLocationY=1700;
        assertEquals(Resolution.Status.AMBIGUOUS,query(a,b).status);
    }
    @Test public void samsungIconCoordinatesAlreadyUseLogicalSpace() {
        Probe p=new Probe();AndroidProviders.oem(context(new SamsungManager()),frame,-1,p);
        Resolution r=p.resolve(frame,-1);assertEquals(Resolution.Status.FOUND,r.status);
        assertEquals(Observation.Source.SAMSUNG_API,r.selected.source);assertEquals(Geometry.Space.CURRENT_LOGICAL,r.selected.space);
    }
    @Test public void wrongDisplayLocationNeverBecomesTheDefault() {
        assertEquals(Resolution.Status.UNAVAILABLE,query(new Modern(new Location("local:2"))).status);
    }
    @Test public void aRotationDuringTheQueryInvalidatesItsGeometry() {
        Context context=RuntimeEnvironment.getApplication();
        Display display=((DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(0);
        int initial=display.getRotation();
        try {
            FingerprintLocation.Options options=new FingerprintLocation.Options.Builder().properties(key->"")
                    .provider((ctx,frame,id,probe)->{
                        probe.add(new Observation(-1,"",Observation.Type.UNDER_DISPLAY,Observation.Source.CALLER_PROVIDER,
                                Geometry.Space.CURRENT_LOGICAL,Geometry.Bounds.circle(frame.logicalWidth()/2.0,frame.logicalHeight()/2.0,20),"fixture"));
                        Shadows.shadowOf(display).setRotation((initial+1)%4);
                    }).build();
            Resolution result=FingerprintLocation.query(context,display,options);
            assertNotEquals(Resolution.Status.FOUND,result.status);assertNull(result.bounds);
            assertTrue(result.issues.stream().anyMatch(i->i.code==Resolution.Issue.Code.DISPLAY_CHANGED));
        } finally {Shadows.shadowOf(display).setRotation(initial);}
    }
    @Test public void systemUiReportIsOptInAndReadFreshForEachQuery() {
        Context base=RuntimeEnvironment.getApplication();
        Context context=new ContextWrapper(base) {
            @Override public Context createDisplayContext(Display display){return this;}
            @Override public Object getSystemService(String name){return "fingerprint".equals(name)?new Manager():super.getSystemService(name);}
        };
        Display display=((DisplayManager)base.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(0);
        int[] reads={0};
        FingerprintLocation.Options options=new FingerprintLocation.Options.Builder().properties(key->"")
                .systemUiDump(()->{reads[0]++;throw new SecurityException();}).build();
        assertNull(new FingerprintLocation.Options.Builder().build().systemUiDump);
        for(int i=1;i<=2;i++) {
            Resolution result=FingerprintLocation.query(context,display,options);
            assertEquals(i,reads[0]);assertNull(result.bounds);
            assertTrue(result.issues.stream().anyMatch(issue->issue.provider.equals("systemui-report")
                    && issue.code==Resolution.Issue.Code.ACCESS_DENIED));
        }
    }
    @Test public void ambiguousFrameworkSensorsDoNotUseADeviceWideSystemUiFallback() {
        Legacy31 first=new Legacy31(),second=new Legacy31();second.sensorId=8;
        first.sensorLocationX=second.sensorLocationX=20;
        first.sensorLocationY=40;second.sensorLocationY=60;
        first.sensorRadius=second.sensorRadius=5;
        Context context=new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Context createDisplayContext(Display display){return this;}
            @Override public Object getSystemService(String name){return "fingerprint".equals(name)?new Manager(first,second):super.getSystemService(name);}
        };
        Display display=((DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(0);
        int[] reads={0};
        Resolution result=FingerprintLocation.query(context,display,new FingerprintLocation.Options.Builder().properties(key->"")
                .systemUiDump(()->{reads[0]++;return "";}).build());
        assertEquals(0,reads[0]);assertEquals(Resolution.Status.AMBIGUOUS,result.status);assertNull(result.bounds);
    }
}
