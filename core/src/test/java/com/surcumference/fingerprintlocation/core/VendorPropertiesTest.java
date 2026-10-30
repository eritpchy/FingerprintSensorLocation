package com.surcumference.fingerprintlocation.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.surcumference.fingerprintlocation.core.Observation.Type.*;
import static com.surcumference.fingerprintlocation.core.Resolution.Status.*;

public class VendorPropertiesTest {
    private final Geometry.DisplayFrame frame=new Geometry.DisplayFrame("local:1",0,1264,2780,1264,2780,0);
    private Resolution read(Map<String,String> map) {
        Probe probe=new Probe();VendorProperties.collect(map::get,frame,-1,UNKNOWN,probe);return probe.resolve(frame,-1);
    }
    private Resolution transsion(String manufacturer, PropertyReader reader) {
        Probe probe=new Probe();VendorProperties.collectTranssion(reader,manufacturer,frame,-1,probe);
        return probe.resolve(frame,-1);
    }
    @Test public void transsionOpticalFlagProvidesTypeWithoutGeometry() {
        for(String manufacturer:new String[]{"TECNO","TECNO MOBILE LIMITED","Infinix","INFINIX MOBILITY LIMITED"}) {
            Resolution r=transsion(manufacturer,key->"1");
            assertEquals(OPTICAL,r.sensorType);assertEquals(UNAVAILABLE,r.status);assertNull(r.bounds);
            assertEquals(Observation.Source.ROM_PROPERTIES,r.typeEvidence.get(0).source);
            assertEquals("ro.optical_fingerprint_support",r.typeEvidence.get(0).evidence);
        }
    }
    @Test public void transsionNonPositiveValuesNeverInventPlacement() {
        for(String value:new String[]{"0","false","true",""," 1 ","01","2","optical"})
            assertEquals(value,UNKNOWN,transsion("TECNO",key->value).sensorType);
        assertEquals(UNKNOWN,transsion("TECNO",key->null).sensorType);
    }
    @Test public void transsionFlagIsScopedToManufacturerAndPrimaryDisplay() {
        for(String manufacturer:new String[]{"Sony","samsung","SHARP","TECNO-other","",null})
            assertEquals(UNKNOWN,transsion(manufacturer,key->{fail("Out-of-scope property read");return "1";}).sensorType);
        Probe probe=new Probe();Geometry.DisplayFrame other=new Geometry.DisplayFrame("local:2",1,1264,2780,1264,2780,0);
        VendorProperties.collectTranssion(key->{fail("External display property read");return "1";},"TECNO",other,-1,probe);
        assertEquals(UNKNOWN,probe.resolve(other,-1).sensorType);
    }
    @Test public void transsionReadFailuresPreserveDiagnosticsWithoutPlacement() {
        Resolution denied=transsion("TECNO",key->{throw new SecurityException();});
        assertEquals(UNKNOWN,denied.sensorType);assertEquals(ACCESS_DENIED,denied.status);
        assertEquals("ro.optical_fingerprint_support",denied.issues.get(0).provider);
        Resolution unavailable=transsion("TECNO",key->{throw new java.io.IOException();});
        assertEquals(UNKNOWN,unavailable.sensorType);assertEquals(UNAVAILABLE,unavailable.status);
    }
    @Test public void transsionOpticalEvidenceDoesNotOverrideConflictingSideEvidence() {
        Probe probe=new Probe();
        probe.add(new Observation(-1,"",SIDE,Observation.Source.ANDROID_PROPERTIES,
                Geometry.Space.NATIVE_NATURAL,null,"framework"));
        VendorProperties.collectTranssion(key->"1","TECNO",frame,-1,probe);
        Resolution r=probe.resolve(frame,-1);assertEquals(CONFLICT,r.status);
        assertEquals(Resolution.TypeStatus.CONFLICT,r.typeStatus);assertNull(r.bounds);
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
    @Test public void typeOnlyPropertiesDoNotNeedAReadableIconLocation() {
        Map<String,String> m=new HashMap<>();m.put("persist.vendor.fingerprint.sensor_type","optical");
        Resolution r=read(m);assertEquals(UNAVAILABLE,r.status);assertEquals(OPTICAL,r.sensorType);assertNull(r.bounds);
        m.put("persist.vendor.fingerprint.sensor_type","side");r=read(m);
        assertEquals(NOT_UNDER_DISPLAY,r.status);assertEquals(SIDE,r.sensorType);
    }
    @Test public void fixedAndPersistedTypeDisagreementIsNotSilentlyPreferred() {
        Map<String,String> m=new HashMap<>();m.put("persist.vendor.fingerprint.type","udfps_optical");
        m.put("ro.vendor.fingerprint.type","side");
        assertEquals(Resolution.TypeStatus.CONFLICT,read(m).typeStatus);assertEquals(CONFLICT,read(m).status);
    }
    @Test public void onlyPositiveUdfpsFlagsClassifyPlacement() {
        for(String key:new String[]{"ro.hardware.fp.fod","ro.hardware.fp.udfps"}) {
            Map<String,String> m=new HashMap<>();m.put(key,"true");
            assertEquals(UNDER_DISPLAY,read(m).sensorType);assertNull(read(m).bounds);
            m.put(key,"false");assertEquals(UNKNOWN,read(m).sensorType);
        }
    }
    @Test public void explicitVivoModulesClassifyWithoutGuessingCoordinatesOrSubtype() {
        for(String key:new String[]{"sys.fingerprint.boot","vendor.fingerprint.boot","persist.sys.fptype"}) {
            Map<String,String> values=new HashMap<>();values.put(key,"sidefp_fpc_fpc1552");
            Resolution side=read(values);assertEquals(SIDE,side.sensorType);assertEquals(NOT_UNDER_DISPLAY,side.status);assertNull(side.bounds);
            assertEquals(Observation.Source.VIVO_PROPERTIES,side.typeEvidence.get(0).source);
            values.put(key,"udfp_goodix2_gf9518");Resolution under=read(values);
            assertEquals(UNDER_DISPLAY,under.sensorType);assertEquals(UNAVAILABLE,under.status);assertNull(under.bounds);
            Probe external=new Probe();Geometry.DisplayFrame other=new Geometry.DisplayFrame("local:2",1,1264,2780,1264,2780,0);
            VendorProperties.collect(values::get,other,-1,UNKNOWN,external);assertEquals(UNKNOWN,external.resolve(other,-1).sensorType);
        }
    }
    @Test public void vivoUnknownModulesAndFrontFlagCannotInventPlacement() {
        for(String module:new String[]{"", "unknown", "goodix", "udfp", "udfp_", "sidefp_", "frontfp_fpc", "not_udfp_goodix", "sidefp_fpc garbage"}) {
            Map<String,String> values=new HashMap<>();values.put("persist.sys.fptype",module);values.put("persist.vivo.fingerprint.front","true");
            assertEquals(module,UNKNOWN,read(values).sensorType);
        }
        Map<String,String> values=new HashMap<>();values.put("persist.sys.fptype","sidefp_fpc_fpc1552");values.put("vendor.fingerprint.boot","udfp_goodix");
        assertEquals(CONFLICT,read(values).status);
    }
    @Test public void oplusBackDeclarationIsRearPlacement() {
        Map<String,String> values=new HashMap<>();values.put("persist.vendor.fingerprint.sensor_type","back");
        assertEquals(REAR,read(values).sensorType);assertEquals(NOT_UNDER_DISPLAY,read(values).status);
    }

}
