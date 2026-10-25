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
}
