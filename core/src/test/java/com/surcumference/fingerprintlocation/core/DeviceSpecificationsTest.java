package com.surcumference.fingerprintlocation.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceSpecificationsTest {
    final Geometry.DisplayFrame display = new Geometry.DisplayFrame("local:1",0,1080,2400,1080,2400,0);
    Resolution collect(String maker, String model, Probe probe) {
        DeviceSpecifications.collect(maker,model,display,-1,true,probe);
        return probe.resolve(display,-1);
    }
    @Test public void exactOfficialModelsSupplyOnlyAttributedType() {
        String[][] models={{"Google","Pixel 8 Pro","UNDER_DISPLAY"},{"samsung","SM-A315N","OPTICAL"},
                {"samsung","SM-S928U1","ULTRASONIC"},{"vivo","vivo X20Plus A","REAR"},
                {"Xiaomi","23127PN0CG","UNDER_DISPLAY"},{"HUAWEI","VOG-L29","UNDER_DISPLAY"},
                {"Nothing","A069","OPTICAL"},{"HONOR","BVL-N49","UNDER_DISPLAY"},{"vivo","V2158A","SIDE"},
                {"OPPO","PEQM00","UNDER_DISPLAY"},{"OPPO","PFVM10","SIDE"},{"Sony","A402SO","SIDE"},
                {"vivo","V1831A","REAR"},{"SHARP","SH-01L","FRONT"},{"TECNO","TECNO KI5k","SIDE"}};
        for(String[] model:models) {
            Resolution r=collect(model[0],model[1],new Probe());
            assertEquals(Observation.Type.valueOf(model[2]),r.sensorType);assertNull(r.bounds);assertNull(r.selected);
            assertFalse(r.corroborated);assertEquals(1,r.typeEvidence.size());
            assertEquals(Observation.Source.DEVICE_SPECIFICATION,r.typeEvidence.get(0).source);
            assertTrue(r.typeEvidence.get(0).evidence.startsWith("https://"));
            assertNotEquals(Resolution.Status.FOUND,r.status);
        }
    }
    @Test public void nearbyNamesForeignManufacturersAndUnverifiedSkusStayUnknown() {
        String[][] models={{"Google","Pixel 8 Pro XL"},{"Google","Pixel 8"},{"Other","Pixel 8 Pro"},
                {"samsung","SM-A315F"},{"vivo","vivo X20Plus UD"},{"vivo","vivo X20Plus A "},
                {"Xiaomi","23127PN0CC"},{"HUAWEI","VOG-L09"},{"Honor","VOG-L29"},
                {"Nothing","A069P"},{"Other","A069"},{"HONOR","BVL-AN16"},
                {"HUAWEI","BVL-N49"},{"HONOR","BVL-N49 "},{"vivo","V2158"},
                {"vivo","V2158A "},{"Other","V2158A"},{"OPPO","PEQM00 "},{"OPPO","PEQM10"},
                {"OPPO","PFVM00"},{"OnePlus","PFVM10"},{"OPPO","Reno6"},{"OPPO","A56"},
                {"Sony","SOG14"},{"Sony","XQ-ES44"},{"Sony","A402SO "},{"Other","A402SO"},
                {"vivo","V1831A "},{"vivo","V1831T"},{"vivo","V1907"},{"vivo","S1"},{"Other","V1831A"},
                {"SHARP","SH-01K"},{"SHARP","SH-01L "},{"SHARP","SH-M08"},{"Other","SH-01L"},
                {"TECNO","TECNO KI5q"},{"TECNO","KI5k"},{"TECNO","TECNO KI5k "},{"Other","TECNO KI5k"}};
        for(String[] model:models)assertEquals(Resolution.TypeStatus.UNKNOWN,collect(model[0],model[1],new Probe()).typeStatus);
    }
    @Test public void declaredHardwareDefaultDisplayAndUnselectedSensorAreRequired() {
        Probe absent=new Probe();DeviceSpecifications.collect("Google","Pixel 8 Pro",display,-1,false,absent);
        Probe selected=new Probe();DeviceSpecifications.collect("Google","Pixel 8 Pro",display,3,true,selected);
        Probe external=new Probe();DeviceSpecifications.collect("Google","Pixel 8 Pro",
                new Geometry.DisplayFrame("external",1,1080,2400,1080,2400,0),-1,true,external);
        assertTrue(absent.observations().isEmpty());assertTrue(selected.observations().isEmpty());assertTrue(external.observations().isEmpty());
    }
    @Test public void runtimePlacementAlwaysWinsEvenWhenSpecificationDisagrees() {
        Probe p=new Probe();p.add(new Observation(-1,"",Observation.Type.SIDE,Observation.Source.CALLER_PROVIDER,
                Geometry.Space.NATIVE_NATURAL,null,"runtime"));
        Resolution r=collect("Google","Pixel 8 Pro",p);assertEquals(Observation.Type.SIDE,r.sensorType);
        assertEquals(1,r.typeEvidence.size());assertEquals(Observation.Source.CALLER_PROVIDER,r.typeEvidence.get(0).source);
    }
    @Test public void runtimeConflictsAndAmbiguityCannotBeHiddenBySpecification() {
        for(boolean multiple:new boolean[]{false,true}) {
            Probe p=new Probe();p.add(new Observation(1,"",Observation.Type.SIDE,Observation.Source.CALLER_PROVIDER,
                    Geometry.Space.NATIVE_NATURAL,null,"a"));
            p.add(new Observation(multiple?2:1,"",Observation.Type.OPTICAL,Observation.Source.ROM_PROPERTIES,
                    Geometry.Space.NATIVE_NATURAL,null,"b"));
            Resolution r=collect("samsung","SM-A315N",p);
            assertEquals(multiple?Resolution.Status.AMBIGUOUS:Resolution.Status.CONFLICT,r.status);
            assertEquals(2,r.typeEvidence.size());
        }
    }
    @Test public void accessDiagnosticsRemainVisibleWithoutInventingGeometry() {
        Probe p=new Probe();p.issue("restricted-api",Resolution.Issue.Code.ACCESS_DENIED);
        Resolution r=collect("Google","Pixel 8 Pro",p);
        assertEquals(Resolution.Status.ACCESS_DENIED,r.status);assertNull(r.bounds);assertEquals(1,r.issues.size());
    }
}
