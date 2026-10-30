package com.surcumference.fingerprintlocation.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class SystemUiReportTest {
    private Geometry.DisplayFrame frame(int rotation) {return new Geometry.DisplayFrame("local:test",0,1080,2400,1440,3200,rotation);}
    private String report(int rotation) {
        return "    com.android.systemui.biometrics.AuthController:\n    -----\n"
                + "      mCachedDisplayInfo=DisplayInfo{displayId 0, real " + (rotation%2==0?"1080 x 2400":"2400 x 1080")
                + ", rotation " + rotation + ", state ON}\n      mScaleFactor=0.75\n"
                + "      udfpsBounds=Rect(450, 1800 - 630, 1980)\n      allFingerprintAuthenticatorsRegistered=true\n\n"
                + "    CRITICAL dump took 1ms -- AuthController\n    UdfpsController:\n    -----\n"
                + "      mSensorProps=(ID: 7, Strength: 2, Type: 2)\n    OtherController:\n      udfpsBounds=Rect(1, 2 - 3, 4)\n";
    }
    private Resolution resolve(String text, Geometry.DisplayFrame frame, int id) {
        Probe probe=new Probe();SystemUiReport.collect(()->text,frame,probe);return probe.resolve(frame,id);
    }
    @Test public void currentNaturalBoundsAreRotatedWithoutScalingTwice() {
        double[][] expected={{450,1800,630,1980},{1800,450,1980,630},{450,420,630,600},{420,450,600,630}};
        for(int rotation=0;rotation<4;rotation++) {
            Resolution result=resolve(report(rotation),frame(rotation),7);
            assertEquals(Resolution.Status.FOUND,result.status);assertEquals(Observation.Source.SYSTEM_UI_DUMP,result.selected.source);
            Geometry.Bounds b=result.bounds;
            assertArrayEquals(expected[rotation],new double[]{b.left,b.top,b.right,b.bottom},.001);
        }
    }
    @Test public void selectedSensorCannotUseAnotherControllerSensor() {assertEquals(Resolution.Status.UNAVAILABLE,resolve(report(0),frame(0),8).status);}
    @Test public void wrongDisplayIsRejected() {
        Resolution r=resolve(report(0).replace("displayId 0","displayId 1"),frame(0),-1);
        assertEquals(Resolution.Status.UNAVAILABLE,r.status);assertEquals(Resolution.Issue.Code.WRONG_DISPLAY,r.issues.get(0).code);
    }
    @Test public void staleRotationAndResolutionAreRejected() {
        for(String value:new String[]{report(0).replace("rotation 0","rotation 1"),report(0).replace("1080 x 2400","1440 x 3200")}) {
            Resolution r=resolve(value,frame(0),-1);assertNull(r.bounds);assertEquals(Resolution.Issue.Code.DISPLAY_CHANGED,r.issues.get(0).code);
        }
    }
    @Test public void duplicateBlocksAndFieldsAreRejected() {
        for(String value:new String[]{report(0)+report(0),report(0).replace("mScaleFactor=0.75","mScaleFactor=0.75\n      mScaleFactor=0.75")})
            assertEquals(Resolution.Status.UNAVAILABLE,resolve(value,frame(0),-1).status);
    }
    @Test public void noBoundsAreInventedFromTheGenericFingerprintPoint() {
        String value=report(0).replace("udfpsBounds=Rect(450, 1800 - 630, 1980)","udfpsBounds=null\n      fingerprintSensorLocation=Point(540, 1890)");
        assertNull(resolve(value,frame(0),-1).bounds);
    }
    @Test public void invalidGeometryScaleAndClassificationAreRejected() {
        for(String value:new String[]{report(0).replace("450, 1800 - 630, 1980","0, 0 - 0, 0"),
                report(0).replace("mScaleFactor=0.75","mScaleFactor=1.0"),report(0).replace("Type: 2","Type: 4"),
                report(0).replace("450, 1800 - 630, 1980","450, 1800 - 1630, 1980")})
            assertNull(resolve(value,frame(0),-1).bounds);
    }
    @Test public void notRegisteredAndUnscopedTextCannotSupplyBounds() {
        assertNull(resolve(report(0).replace("Registered=true","Registered=false"),frame(0),-1).bounds);
        assertNull(resolve("OtherController:\n  udfpsBounds=Rect(450, 1800 - 630, 1980)",frame(0),-1).bounds);
    }
    @Test public void accessDenialIsExplicit() {
        Probe p=new Probe();SystemUiReport.collect(()->{throw new SecurityException();},frame(0),p);
        assertEquals(Resolution.Status.ACCESS_DENIED,p.resolve(frame(0),-1).status);
        assertEquals(Resolution.Status.ACCESS_DENIED,resolve("Permission Denial: missing DUMP",frame(0),-1).status);
    }
    @Test public void scaledFrameworkPlaceholderNeedsIndependentConfirmation() {
        String value=report(0).replace("450, 1800 - 630, 1980","308, 1130 - 503, 1325");
        assertEquals(Resolution.Status.UNAVAILABLE,resolve(value,frame(0),-1).status);
    }
    @Test public void oversizedReportsAndExternalDisplaysDoNotSupplyGeometry() {
        char[] tooLarge=new char[2*1024*1024+1];java.util.Arrays.fill(tooLarge,'x');
        Resolution oversized=resolve(new String(tooLarge),frame(0),-1);
        assertNull(oversized.bounds);assertEquals(Resolution.Issue.Code.INVALID_DATA,oversized.issues.get(0).code);
        Geometry.DisplayFrame external=new Geometry.DisplayFrame("local:external",1,1080,2400,1440,3200,0);
        Probe probe=new Probe();SystemUiReport.collect(()->{throw new AssertionError("Must not read default-display report");},external,probe);
        assertNull(probe.resolve(external,-1).bounds);
    }
    @Test public void opticalClassificationAndWhitespaceArePreserved() {
        Resolution result=resolve(report(0).replace("Type: 2","Type: 3").replace("\n","\r\n"),frame(0),7);
        assertEquals(Resolution.Status.FOUND,result.status);assertEquals(Observation.Type.OPTICAL,result.selected.type);
    }
    @Test public void registeredSensorTypeSurvivesAnEmptyRectangle() {
        Resolution r=resolve(report(0).replace("450, 1800 - 630, 1980","0, 0 - 0, 0"),frame(0),7);
        assertEquals(Resolution.Status.UNAVAILABLE,r.status);assertNull(r.bounds);
        assertEquals(Observation.Type.ULTRASONIC,r.sensorType);assertEquals(Resolution.TypeStatus.KNOWN,r.typeStatus);
    }
    @Test public void staleDisplayCannotSupplyTypeEvidence() {
        Resolution r=resolve(report(0).replace("rotation 0","rotation 1"),frame(0),7);
        assertEquals(Observation.Type.UNKNOWN,r.sensorType);assertTrue(r.typeEvidence.isEmpty());
    }
}
