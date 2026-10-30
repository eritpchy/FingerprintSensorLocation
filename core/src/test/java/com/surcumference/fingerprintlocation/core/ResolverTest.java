package com.surcumference.fingerprintlocation.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.surcumference.fingerprintlocation.core.Observation.*;
import static com.surcumference.fingerprintlocation.core.Resolution.Status.*;

public class ResolverTest {
    private final Geometry.DisplayFrame frame = new Geometry.DisplayFrame("local:1",0,1080,2400,1080,2400,0);
    private Observation observation(int id, String display, Type type, Source source, double y, boolean placeholder) {
        return new Observation(id,display,type,source,Geometry.Space.NATIVE_NATURAL,
                Geometry.Bounds.circle(540,y,130),"fixture",placeholder);
    }
    private Resolution resolve(Observation... input) { return Resolver.resolve(frame,-1,Arrays.asList(input),Collections.emptyList()); }
    @Test public void loneFrameworkPlaceholderDoesNotPretendToBeDeviceLocation() {
        Resolution r=resolve(observation(1,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,1636,true));
        assertEquals(UNAVAILABLE,r.status);assertNull(r.bounds);
    }
    @Test public void independentSourceCanCorroborateLegitimateDefaultCoordinates() {
        Resolution r=resolve(observation(1,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,1636,true),
                observation(1,"",Type.OPTICAL,Source.FRAMEWORK_RESOURCE,1636,false));
        assertEquals(FOUND,r.status);assertTrue(r.corroborated);
    }
    @Test public void incorrectPlaceholderDoesNotOverrideRealOemCoordinates() {
        Resolution r=resolve(observation(1,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,1636,true),
                observation(1,"",Type.OPTICAL,Source.OPLUS_PROPERTIES,2150,false));
        assertEquals(FOUND,r.status);assertEquals(2150,r.bounds.centerY(),0);
    }
    @Test public void vivoAliasCannotCorroborateTheSameFrameworkPlaceholder() {
        Resolution r=resolve(observation(1,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,1636,false),
                observation(1,"",Type.OPTICAL,Source.VIVO_API,1636,false));
        assertEquals(UNAVAILABLE,r.status);assertNull(r.bounds);
    }
    @Test public void disagreeingSourcesReturnConflictWithNoUsableBounds() {
        Resolution r=resolve(observation(1,"",Type.OPTICAL,Source.SAMSUNG_API,1900,false),
                observation(1,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,2150,false));
        assertEquals(CONFLICT,r.status);assertNull(r.bounds);assertNull(r.selected);
    }
    @Test public void multipleSensorsRequireAnExplicitSelection() {
        Observation a=observation(1,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,1900,false);
        Observation b=observation(2,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,2150,false);
        assertEquals(AMBIGUOUS,resolve(a,b).status);
        Resolution chosen=Resolver.resolve(frame,2,Arrays.asList(a,b),Collections.emptyList());
        assertEquals(FOUND,chosen.status);assertEquals(2,chosen.selected.sensorId);
    }
    @Test public void explicitlyRequestedSensorDoesNotUseAnUnboundVendorProperty() {
        Resolution r=Resolver.resolve(frame,2,Collections.singletonList(observation(-1,"",Type.OPTICAL,Source.OPLUS_PROPERTIES,2150,false)),Collections.emptyList());
        assertEquals(UNAVAILABLE,r.status);assertNull(r.bounds);
    }
    @Test public void foreignDisplayNeverLeaksIntoCurrentDisplay() {
        assertEquals(UNAVAILABLE,resolve(observation(1,"local:2",Type.OPTICAL,Source.ANDROID_PROPERTIES,2150,false)).status);
    }
    @Test public void sideFingerprintCoordinatesNeverBecomeAnUnderDisplayTarget() {
        assertEquals(NOT_UNDER_DISPLAY,resolve(observation(1,"",Type.SIDE,Source.ROM_PROPERTIES,1200,false)).status);
    }
    @Test public void staleFodPropertiesCannotOverrideSideSensorClassification() {
        assertEquals(CONFLICT,resolve(observation(1,"",Type.SIDE,Source.ANDROID_PROPERTIES,1200,false),
                observation(1,"",Type.OPTICAL,Source.XIAOMI_PROPERTIES,2150,false)).status);
    }
    @Test public void deniedQueriesRemainDistinguishableFromUnsupportedHardware() {
        Resolution r=Resolver.resolve(frame,-1,Collections.emptyList(),Collections.singletonList(
                new Resolution.Issue("fixture",Resolution.Issue.Code.ACCESS_DENIED)));
        assertEquals(ACCESS_DENIED,r.status);
    }
    @Test public void resultsDoNotExposeMutableCollectionState() {
        List<Observation> input=new ArrayList<>();input.add(observation(1,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,2150,false));
        Resolution r=Resolver.resolve(frame,-1,input,Collections.emptyList());input.clear();
        assertEquals(1,r.observations.size());assertThrows(UnsupportedOperationException.class,r.observations::clear);
    }
    private Observation classification(int id, String display, Type type) {
        return new Observation(id,display,type,Source.ANDROID_PROPERTIES,Geometry.Space.NATIVE_NATURAL,null,"type fixture");
    }
    @Test public void typeSurvivesMissingGeometryAndUnconfirmedPlaceholder() {
        for(Observation o:new Observation[]{classification(7,"",Type.OPTICAL),
                observation(7,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,1636,true)}) {
            Resolution r=resolve(o);assertEquals(UNAVAILABLE,r.status);assertNull(r.bounds);
            assertEquals(Resolution.TypeStatus.KNOWN,r.typeStatus);assertEquals(Type.OPTICAL,r.sensorType);
            assertFalse(r.typeEvidence.isEmpty());
        }
    }
    @Test public void physicalPlacementIsReportedWithoutScreenCoordinates() {
        for(Type type:new Type[]{Type.SIDE,Type.REAR,Type.HOME,Type.FRONT}) {
            Resolution r=resolve(classification(7,"",type));assertEquals(NOT_UNDER_DISPLAY,r.status);
            assertEquals(type,r.sensorType);assertEquals(Resolution.TypeStatus.KNOWN,r.typeStatus);assertNull(r.selected);
        }
    }
    @Test public void genericUnderDisplayEvidenceCanRefineToOpticalOrUltrasonic() {
        for(Type type:new Type[]{Type.OPTICAL,Type.ULTRASONIC}) {
            Resolution r=resolve(classification(7,"",Type.UNDER_DISPLAY),classification(7,"",type));
            assertEquals(type,r.sensorType);assertEquals(Resolution.TypeStatus.KNOWN,r.typeStatus);
        }
    }
    @Test public void frontPlacementDoesNotInventHomeButtonOrUnderDisplayGeometry() {
        assertEquals(Type.UNKNOWN,Type.fromAndroid(6));
        for(boolean reverse:new boolean[]{false,true}) {
            Observation front=classification(7,"",Type.FRONT),home=classification(7,"",Type.HOME);
            Resolution r=reverse?resolve(home,front):resolve(front,home);
            assertEquals(Type.HOME,r.sensorType);assertEquals(NOT_UNDER_DISPLAY,r.status);assertNull(r.bounds);
        }
        for(Type other:new Type[]{Type.SIDE,Type.REAR,Type.OPTICAL}) {
            Resolution r=resolve(classification(7,"",Type.FRONT),classification(7,"",other));
            assertEquals(CONFLICT,r.status);assertNull(r.bounds);
        }
    }
    @Test public void conflictingTypesCannotBeHiddenByMissingCoordinates() {
        for(Type type:new Type[]{Type.SIDE,Type.ULTRASONIC}) {
            Resolution r=resolve(classification(7,"",Type.OPTICAL),classification(7,"",type));
            assertEquals(CONFLICT,r.status);assertEquals(Resolution.TypeStatus.CONFLICT,r.typeStatus);
            assertEquals(Type.UNKNOWN,r.sensorType);assertNull(r.bounds);
        }
        assertEquals(CONFLICT,resolve(classification(7,"",Type.SIDE),classification(7,"",Type.REAR)).status);
    }
    @Test public void coordinateConflictDoesNotEraseAgreedSensorType() {
        Resolution r=resolve(observation(7,"",Type.OPTICAL,Source.ANDROID_PROPERTIES,1900,false),
                observation(7,"",Type.OPTICAL,Source.OPLUS_PROPERTIES,2150,false));
        assertEquals(CONFLICT,r.status);assertEquals(Type.OPTICAL,r.sensorType);assertEquals(Resolution.TypeStatus.KNOWN,r.typeStatus);
    }
    @Test public void multipleTypesWithoutBoundsStillRequireSensorSelection() {
        List<Observation> input=Arrays.asList(classification(7,"",Type.SIDE),classification(8,"",Type.OPTICAL));
        Resolution r=Resolver.resolve(frame,-1,input,Collections.emptyList());
        assertEquals(AMBIGUOUS,r.status);assertEquals(Resolution.TypeStatus.AMBIGUOUS,r.typeStatus);assertEquals(Type.UNKNOWN,r.sensorType);
        assertEquals(Type.SIDE,Resolver.resolve(frame,7,input,Collections.emptyList()).sensorType);
    }
    @Test public void typeEvidenceCannotLeakAcrossDisplayOrExplicitSensorSelection() {
        assertEquals(Type.UNKNOWN,resolve(classification(7,"local:2",Type.SIDE)).sensorType);
        Resolution r=Resolver.resolve(frame,7,Collections.singletonList(classification(-1,"",Type.SIDE)),Collections.emptyList());
        assertEquals(Type.UNKNOWN,r.sensorType);assertTrue(r.typeEvidence.isEmpty());
        assertThrows(UnsupportedOperationException.class,()->resolve(classification(7,"",Type.SIDE)).typeEvidence.clear());
    }
    @Test public void aQueryWithoutTypeEvidenceRemainsUnknown() {
        Resolution r=resolve();assertEquals(Type.UNKNOWN,r.sensorType);assertEquals(Resolution.TypeStatus.UNKNOWN,r.typeStatus);
    }
    @Test public void genericNonDisplayDeclarationRefinesWithoutInventingPlacement() {
        Resolution generic=resolve(classification(7,"",Type.NON_UNDER_DISPLAY));
        assertEquals(NOT_UNDER_DISPLAY,generic.status);assertEquals(Type.NON_UNDER_DISPLAY,generic.sensorType);assertNull(generic.bounds);
        for(Type type:new Type[]{Type.SIDE,Type.REAR,Type.HOME,Type.FRONT}) {
            for(boolean reverse:new boolean[]{false,true}) {
                Observation a=classification(7,"",Type.NON_UNDER_DISPLAY),b=classification(7,"",type);
                Resolution r=reverse?resolve(b,a):resolve(a,b);
                assertEquals(type,r.sensorType);assertEquals(NOT_UNDER_DISPLAY,r.status);
            }
        }
        assertEquals(CONFLICT,resolve(classification(7,"",Type.NON_UNDER_DISPLAY),
                observation(7,"",Type.UNDER_DISPLAY,Source.VIVO_SERVICE,2150,false)).status);
    }

}
