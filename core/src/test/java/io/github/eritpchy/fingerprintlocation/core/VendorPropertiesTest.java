package io.github.eritpchy.fingerprintlocation.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static io.github.eritpchy.fingerprintlocation.core.Observation.Type.*;
import static io.github.eritpchy.fingerprintlocation.core.Resolution.Status.*;

public class VendorPropertiesTest {
    private final Geometry.DisplayFrame frame=new Geometry.DisplayFrame("local:1",0,1264,2780,1264,2780,0);
    private Resolution read(Map<String,String> map) {
        Probe probe=new Probe();VendorProperties.collect(map::get,frame,-1,UNKNOWN,probe);return probe.resolve(frame,-1);
    }
    @Test public void oplusBottomOffsetMeansCenterOffsetNotLowerEdge() {
        Map<String,String> m=new HashMap<>();m.put("persist.vendor.fingerprint.sensor_type","optical");
        m.put("persist.vendor.fingerprint.optical.iconlocation","260");m.put("persist.vendor.fingerprint.optical.iconsize","195");
        Resolution r=read(m);assertEquals(FOUND,r.status);GeometryTest.same(new double[]{534.5,2422.5,729.5,2617.5},r.bounds);
    }
    @Test public void missingOplusPropertiesDoNotUsePublishedExampleDefaults() {
        Map<String,String> m=new HashMap<>();m.put("persist.vendor.fingerprint.sensor_type","optical");
        assertEquals(UNAVAILABLE,read(m).status);
    }
    @Test public void bothXiaomiPropertyGenerationsAreRead() {
        for(String prefix:new String[]{"persist.sys.fp.fod.","persist.vendor.sys.fp.fod."}) {
            Map<String,String> m=new HashMap<>();m.put(prefix+"location.X_Y","453,1640");m.put(prefix+"size.width_height","173,173");
            Resolution r=read(m);assertEquals(FOUND,r.status);GeometryTest.same(new double[]{453,1640,626,1813},r.bounds);
        }
    }
    @Test public void multipleScreenEntriesSelectOnlyTheActiveDisplay() {
        Map<String,String> m=new HashMap<>();m.put("persist.vendor.fingerprint.type","udfps_optical");
        m.put("persist.vendor.fingerprint.sensor_location","632|2000|95|local:2,632|2520|97|local:1");
        assertEquals(2520,read(m).bounds.centerY(),0);
    }
    @Test public void samsungSemicolonListIsSupportedWithoutGuessingDisplayOrder() {
        Map<String,String> m=new HashMap<>();m.put("ro.vendor.fingerprint.type","udfps");
        m.put("ro.vendor.fingerprint.sensor_location","632|2000|95|local:2;632|2520|97|local:1");
        assertEquals(2520,read(m).bounds.centerY(),0);
    }
    @Test public void geometryWithoutSensorTypeDoesNotImplyUnderDisplay() {
        Map<String,String> m=new HashMap<>();m.put("persist.vendor.fingerprint.sensor_location","632|2520|97");
        assertEquals(UNAVAILABLE,read(m).status);
    }
    @Test public void explicitlyDisabledFodDoesNotUseStaleLegacyGeometry() {
        Map<String,String> m=new HashMap<>();m.put("ro.hardware.fp.fod","false");
        m.put("persist.vendor.sys.fp.fod.location.X_Y","453,1640");m.put("persist.vendor.sys.fp.fod.size.width_height","173,173");
        assertEquals(UNAVAILABLE,read(m).status);
    }
    @Test public void nonFiniteMalformedAndOutOfRangeDataAreNeverReturned() {
        for(String value:new String[]{"NaN|2000|95","632|Infinity|95","0|0|95","632|2520|-1","bad","632|2520|9000","632||95","632|2520|95|local:1|extra"}) {
            Map<String,String> m=new HashMap<>();m.put("persist.vendor.fingerprint.type","udfps_optical");m.put("persist.vendor.fingerprint.sensor_location",value);
            assertNotEquals(value,FOUND,read(m).status);
        }
    }
    @Test public void propertiesAreReReadForEveryQuery() {
        Map<String,String> m=new HashMap<>();m.put("persist.vendor.fingerprint.type","udfps_optical");
        m.put("persist.vendor.fingerprint.sensor_location","632|2000|95");assertEquals(2000,read(m).bounds.centerY(),0);
        m.put("persist.vendor.fingerprint.sensor_location","632|2520|95");assertEquals(2520,read(m).bounds.centerY(),0);
    }
    @Test public void permissionErrorsAreReportedWithoutRequestingElevation() {
        Probe probe=new Probe();VendorProperties.collect(key->{throw new SecurityException();},frame,-1,UNKNOWN,probe);
        assertEquals(ACCESS_DENIED,probe.resolve(frame,-1).status);
    }
    @Test public void reflectivePermissionErrorsRemainAccessDenied() {
        Probe p=new Probe();VendorProperties.collect(key->{throw new java.lang.reflect.InvocationTargetException(new SecurityException());},frame,-1,UNKNOWN,p);
        assertEquals(ACCESS_DENIED,p.resolve(frame,-1).status);
    }
}
