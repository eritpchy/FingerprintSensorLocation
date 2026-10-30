package com.surcumference.fingerprintlocation.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class StockFirmwareGeometryProfileTest {
    final StockFirmwareGeometryProfile profile = StockFirmwareGeometryProfile.NOTHING_A069_B4_1_260808_1352;
    final String[] identity = {"Nothing", "A069", "Frogger", "BQ2A.250913.001-BP2A.250605.031.A3", "B4.1-260808-1352", "2608081352"};
    Geometry.DisplayFrame frame(int rotation) { return new Geometry.DisplayFrame("primary",0,1224,2720,1224,2720,rotation); }
    Resolution collect(String[] id, Geometry.DisplayFrame f, int sensor, boolean feature, String hash, Probe p) {
        profile.collect(id[0],id[1],id[2],id[3],id[4],id[5],f,sensor,feature,hash,p);
        return p.resolve(f,sensor);
    }
    @Test public void exactFirmwareHashUsesCommonRotationAndScaling() {
        for (int rotation=0;rotation<4;rotation++) {
            Geometry.DisplayFrame f=frame(rotation);Resolution r=collect(identity,f,-1,true,profile.HAL_SHA256,new Probe());
            Geometry.Bounds expected=f.toLogical(Geometry.Bounds.circle(612,2485,103),Geometry.Space.NATIVE_NATURAL);
            assertEquals(Resolution.Status.FOUND,r.status);assertFalse(r.corroborated);
            assertEquals(Observation.Source.STOCK_FIRMWARE_CONFIGURATION,r.selected.source);
            assertEquals(expected.centerX(),r.bounds.centerX(),0.001);assertEquals(expected.centerY(),r.bounds.centerY(),0.001);
        }
        Geometry.DisplayFrame f=new Geometry.DisplayFrame("primary",0,918,2040,1224,2720,0);
        Resolution r=collect(identity,f,-1,true,profile.HAL_SHA256,new Probe());
        assertEquals(459,r.bounds.centerX(),0.001);assertEquals(1863.75,r.bounds.centerY(),0.001);
    }
    @Test public void identityHashFeatureSensorAndDisplayAreRequired() {
        for (int i=0;i<identity.length;i++) {
            String[] wrong=identity.clone();wrong[i]+="x";
            assertNull(collect(wrong,frame(0),-1,true,profile.HAL_SHA256,new Probe()).bounds);
        }
        assertNull(collect(identity,frame(0),-1,true,"bad",new Probe()).bounds);
        assertNull(collect(identity,frame(0),-1,false,profile.HAL_SHA256,new Probe()).bounds);
        assertNull(collect(identity,frame(0),4,true,profile.HAL_SHA256,new Probe()).bounds);
        assertNull(collect(identity,new Geometry.DisplayFrame("external",1,1224,2720,1224,2720,0),-1,true,profile.HAL_SHA256,new Probe()).bounds);
        assertNull(collect(identity,new Geometry.DisplayFrame("primary",0,1080,2400,1080,2400,0),-1,true,profile.HAL_SHA256,new Probe()).bounds);
    }
    @Test public void callerEvidenceIsDistinctAndCannotClaimRuntimeProvenance() {
        Probe probe = new Probe();
        profile.collect(identity[0],identity[1],identity[2],identity[3],identity[4],identity[5],frame(0),-1,true,
                profile.HAL_SHA256,Observation.Source.CALLER_FIRMWARE_CONFIGURATION,probe);
        Resolution result = probe.resolve(frame(0),-1);
        assertEquals(Resolution.Status.FOUND,result.status);
        assertEquals(Observation.Source.CALLER_FIRMWARE_CONFIGURATION,result.selected.source);
        assertFalse(result.corroborated);
        try {
            profile.collect(identity[0],identity[1],identity[2],identity[3],identity[4],identity[5],frame(0),-1,true,
                    profile.HAL_SHA256,Observation.Source.ANDROID_PROPERTIES,new Probe());
            fail("Runtime provenance accepted");
        } catch (IllegalArgumentException expected) { }
    }
    @Test public void pixelRequiresAllActualComponentsAndExactCdtBytes() {
        StockFirmwareGeometryProfile pixel=StockFirmwareGeometryProfile.GOOGLE_PIXEL_8_PRO_BP1A_250505_005_B1;
        java.util.Map<String,String> hashes=new java.util.HashMap<>(pixel.componentHashes());
        java.util.Map<String,byte[]> metadata=new java.util.HashMap<>();
        metadata.put(pixel.CDT+"platform",new byte[]{0,0,0,4});metadata.put(pixel.CDT+"product",new byte[]{0,0,0,2});
        Geometry.DisplayFrame f=new Geometry.DisplayFrame("primary",0,1008,2244,1344,2992,0);
        for (int rotation=0;rotation<4;rotation++) {
            Geometry.DisplayFrame rotated=new Geometry.DisplayFrame("primary",0,1008,2244,1344,2992,rotation);
            Probe p=new Probe();pixel.collect("Google","Pixel 8 Pro","husky","BP1A.250505.005.B1","BP1A.250505.005.B1",
                    "13277630",rotated,-1,true,hashes,metadata,Observation.Source.CALLER_FIRMWARE_CONFIGURATION,p);
            Resolution r=p.resolve(rotated,-1);assertEquals(Resolution.Status.FOUND,r.status);assertFalse(r.corroborated);
            Geometry.Bounds expected=rotated.toLogical(Geometry.Bounds.circle(672,2261,115),Geometry.Space.NATIVE_NATURAL);
            assertEquals(expected.centerX(),r.bounds.centerX(),0.001);assertEquals(expected.centerY(),r.bounds.centerY(),0.001);
            assertEquals(Observation.Source.CALLER_FIRMWARE_CONFIGURATION,r.selected.source);
        }
        for(String path:pixel.componentHashes().keySet()) {
            java.util.Map<String,String> wrong=new java.util.HashMap<>(hashes);wrong.remove(path);
            Probe p=new Probe();pixel.collect("Google","Pixel 8 Pro","husky","BP1A.250505.005.B1","BP1A.250505.005.B1",
                    "13277630",f,-1,true,wrong,metadata,Observation.Source.STOCK_FIRMWARE_CONFIGURATION,p);
            assertNull(p.resolve(f,-1).bounds);
        }
        for(String path:pixel.metadataPaths()) for(byte[] bytes:new byte[][]{new byte[0],new byte[]{4,0,0,0},new byte[]{0,0,0,5},new byte[]{0,0,0,4,0}}) {
            java.util.Map<String,byte[]> wrong=new java.util.HashMap<>(metadata);wrong.put(path,bytes);
            Probe p=new Probe();pixel.collect("Google","Pixel 8 Pro","husky","BP1A.250505.005.B1","BP1A.250505.005.B1",
                    "13277630",f,-1,true,hashes,wrong,Observation.Source.STOCK_FIRMWARE_CONFIGURATION,p);
            assertNull(p.resolve(f,-1).bounds);
        }
        assertFalse(pixel.matches("Google","Pixel 8","husky","BP1A.250505.005.B1","BP1A.250505.005.B1","13277630"));
        assertFalse(pixel.matches("Google","Pixel 8 Pro","husky","BP1A.250505.005.B1","BP1A.250505.005.B1","other"));
        assertFalse(pixel.supportsFrame(new Geometry.DisplayFrame("primary",0,1008,2244,1008,2244,0)));
        assertFalse(pixel.supportsFrame(new Geometry.DisplayFrame("primary",0,1000,2244,1344,2992,0)));
        assertFalse(pixel.supportsFrame(new Geometry.DisplayFrame("external",1,1008,2244,1344,2992,0)));
        Probe singleHash=new Probe();pixel.collect("Google","Pixel 8 Pro","husky","BP1A.250505.005.B1","BP1A.250505.005.B1",
                "13277630",f,-1,true,profile.HAL_SHA256,singleHash);assertNull(singleHash.resolve(f,-1).bounds);
    }
    @Test public void runtimeAndExteriorEvidenceArePreserved() {
        Probe runtime=new Probe();runtime.add(new Observation(-1,"",Observation.Type.OPTICAL,Observation.Source.ANDROID_PROPERTIES,
                Geometry.Space.NATIVE_NATURAL,Geometry.Bounds.circle(612,2400,80),"runtime"));
        Resolution r=collect(identity,frame(0),-1,true,profile.HAL_SHA256,runtime);
        assertEquals(Observation.Source.ANDROID_PROPERTIES,r.selected.source);assertEquals(1,r.observations.size());
        Probe side=new Probe();side.add(new Observation(-1,"",Observation.Type.SIDE,Observation.Source.ANDROID_PROPERTIES,
                Geometry.Space.NATIVE_NATURAL,null,"side"));
        assertEquals(Resolution.Status.NOT_UNDER_DISPLAY,collect(identity,frame(0),-1,true,profile.HAL_SHA256,side).status);
    }
}
