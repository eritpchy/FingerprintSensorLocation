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
@Config(sdk={23,31,36})
public class FrameworkProviderTest {
    @Test public void firmwareStreamsAreBoundedHashedAndClosed() throws Exception {
        final boolean[] closed = {false};
        java.io.InputStream input = new java.io.ByteArrayInputStream("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override public void close() {closed[0] = true;}
        };
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", StockFirmwareGeometry.hash(input));
        assertTrue(closed[0]);
        for (int mode = 0; mode < 3; mode++) {
            closed[0] = false;
            final int failure = mode;
            input = new java.io.InputStream() {
                @Override public int read() {return 1;}
                @Override public int read(byte[] bytes) throws java.io.IOException {
                    if (failure == 0) return 0;
                    if (failure == 1) throw new java.io.IOException("read denied");
                    return bytes.length;
                }
                @Override public void close() {closed[0] = true;}
            };
            try {StockFirmwareGeometry.hash(input);fail("Invalid stream accepted");}
            catch (java.io.IOException expected) {assertTrue(closed[0]);}
        }
        try {StockFirmwareGeometry.hash(null);fail("Null stream accepted");}
        catch (java.io.IOException expected) { }
    }
    @Test public void cdtStreamMustHaveExactlyFourBytesAndClose() throws Exception {
        for (int length : new int[]{0,3,4,5}) {
            final boolean[] closed={false};
            java.io.InputStream input=new java.io.ByteArrayInputStream(new byte[length]) {
                @Override public void close() {closed[0]=true;}
            };
            try {assertEquals(4,StockFirmwareGeometry.readMetadata(input).length);assertEquals(4,length);}
            catch (java.io.IOException expected) {assertNotEquals(4,length);}
            assertTrue(closed[0]);
        }
        try {StockFirmwareGeometry.readMetadata(null);fail("Null metadata accepted");}
        catch (java.io.IOException expected) { }
    }
    @Test public void firmwareReaderIsExplicitAndInactiveWithoutProfile() {
        FingerprintLocation.Options defaults = new FingerprintLocation.Options.Builder().build();
        assertNull(defaults.stockFirmwareComponentReader);
        FingerprintLocation.Options selected = new FingerprintLocation.Options.Builder()
                .stockFirmwareComponentReader(path -> {throw new AssertionError("Reader must remain inactive");}).build();
        Context app = RuntimeEnvironment.getApplication();
        Display display = ((DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(0);
        FingerprintLocation.query(app, display, selected);
    }
    @Test public void callerFirmwareErrorsFailClosedBeforeGeometry() {
        Context app = RuntimeEnvironment.getApplication();
        String[] fields = {"MANUFACTURER","MODEL","DEVICE","ID","DISPLAY"};
        String[] values = {"Nothing","A069","Frogger","BQ2A.250913.001-BP2A.250605.031.A3","B4.1-260808-1352"};
        String[] previous = new String[fields.length];
        String incremental = android.os.Build.VERSION.INCREMENTAL;
        boolean feature = app.getPackageManager().hasSystemFeature("android.hardware.fingerprint");
        try {
            for (int i=0;i<fields.length;i++) {
                previous[i] = org.robolectric.util.ReflectionHelpers.getStaticField(android.os.Build.class,fields[i]);
                org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,fields[i],values[i]);
            }
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION.class,"INCREMENTAL","2608081352");
            Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",true);
            Geometry.DisplayFrame frame = new Geometry.DisplayFrame("primary",0,1224,2720,1224,2720,0);
            StockFirmwareGeometryProfile profile = StockFirmwareGeometryProfile.NOTHING_A069_B4_1_260808_1352;
            Probe badHash = new Probe();
            final boolean[] closed = {false};
            StockFirmwareGeometry.collect(app,frame,-1,profile,path -> {
                assertEquals(profile.HAL_PATH,path);
                return new java.io.ByteArrayInputStream(new byte[]{1}) {
                    @Override public void close() {closed[0]=true;}
                };
            },badHash);
            assertTrue(closed[0]);assertNull(badHash.resolve(frame,-1).bounds);
            assertTrue(badHash.issues().stream().anyMatch(i -> i.code==Resolution.Issue.Code.UNTRUSTED_SOURCE));
            Probe denied = new Probe();
            StockFirmwareGeometry.collect(app,frame,-1,profile,path -> {throw new SecurityException("denied");},denied);
            assertNull(denied.resolve(frame,-1).bounds);
            assertTrue(denied.issues().stream().anyMatch(i -> i.code==Resolution.Issue.Code.ACCESS_DENIED));
            Probe runtime = new Probe();
            runtime.add(new Observation(-1,"",Observation.Type.OPTICAL,Observation.Source.ANDROID_PROPERTIES,
                    Geometry.Space.NATIVE_NATURAL,Geometry.Bounds.circle(612,2400,80),"runtime"));
            FirmwareComponentReader unused = path -> {throw new AssertionError("Unexpected firmware read");};
            StockFirmwareGeometry.collect(app,frame,-1,profile,unused,runtime);
            assertEquals(Observation.Source.ANDROID_PROPERTIES,runtime.resolve(frame,-1).selected.source);
            StockFirmwareGeometry.collect(app,frame,2,profile,unused,new Probe());
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MODEL","another SKU");
            StockFirmwareGeometry.collect(app,frame,-1,profile,unused,new Probe());
        } finally {
            for (int i=0;i<fields.length;i++) org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,fields[i],previous[i]);
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION.class,"INCREMENTAL",incremental);
            Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",feature);
        }
    }
    @Test public void huaweiHardwareTechnologyUsesVendorNumberingAndKeepsCoordinates() {
        Context app=RuntimeEnvironment.getApplication();
        Geometry.DisplayFrame frame=new Geometry.DisplayFrame("primary",0,1080,2400,1080,2400,0);
        huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=true;
        try {
            for(int value:new int[]{1,2}) {
                huawei.android.hardware.fingerprint.FingerprintManagerEx.type=value;
                Probe probe=new Probe();AndroidProviders.oem(app,frame,-1,probe);Resolution result=probe.resolve(frame,-1);
                assertEquals(value==1?Observation.Type.OPTICAL:Observation.Type.ULTRASONIC,result.sensorType);
                assertEquals(Resolution.Status.FOUND,result.status);assertEquals(450,result.bounds.left,0.001);
                assertFalse(result.corroborated);
            }
        } finally {huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=false;}
    }
    @Test public void deniedHuaweiTechnologyKeepsExistingGenericGeometry() {
        Context app=RuntimeEnvironment.getApplication();
        Geometry.DisplayFrame frame=new Geometry.DisplayFrame("primary",0,1080,2400,1080,2400,0);
        huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=true;
        huawei.android.hardware.fingerprint.FingerprintManagerEx.denied=true;
        try {
            Probe probe=new Probe();AndroidProviders.oem(app,frame,-1,probe);Resolution result=probe.resolve(frame,-1);
            assertEquals(Resolution.Status.FOUND,result.status);assertEquals(Observation.Type.UNDER_DISPLAY,result.sensorType);
            assertEquals(450,result.bounds.left,0.001);
            assertTrue(result.issues.stream().anyMatch(i->i.provider.equals("huawei-type")&&i.code==Resolution.Issue.Code.ACCESS_DENIED));
        } finally {
            huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=false;
            huawei.android.hardware.fingerprint.FingerprintManagerEx.denied=false;
        }
    }
    @Test public void unknownHuaweiHardwareTypeDoesNotInventTechnologyOrExteriorPlacement() {
        Context app=RuntimeEnvironment.getApplication();
        Geometry.DisplayFrame frame=new Geometry.DisplayFrame("primary",0,1080,2400,1080,2400,0);
        try {
            for(int value:new int[]{-1,0,3,100}) {
                huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=true;
                huawei.android.hardware.fingerprint.FingerprintManagerEx.type=value;
                Probe probe=new Probe();AndroidProviders.oem(app,frame,-1,probe);Resolution result=probe.resolve(frame,-1);
                assertEquals(Observation.Type.UNDER_DISPLAY,result.sensorType);
            }
            huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=false;
            huawei.android.hardware.fingerprint.FingerprintManagerEx.type=1;
            Probe probe=new Probe();AndroidProviders.oem(app,frame,-1,probe);
            assertTrue(probe.resolve(frame,-1).observations.stream().noneMatch(o->o.source==Observation.Source.HUAWEI_API));
        } finally {huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=false;}
    }
    @Test public void huaweiSensorAreaTakesPrecedenceOverLogoPointAndUsesNativeFrame() {
        Context app=RuntimeEnvironment.getApplication();
        huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=true;
        huawei.android.hardware.fingerprint.FingerprintManagerEx.spotPosition=new int[]{450,2000,630,2180};
        huawei.android.hardware.fingerprint.FingerprintManagerEx.legacyPosition=new int[]{540,2090,540,2090};
        try {
            for(int rotation=0;rotation<4;rotation++) {
                Geometry.DisplayFrame frame=new Geometry.DisplayFrame("primary",0,540,1200,1080,2400,rotation);
                Probe probe=new Probe();AndroidProviders.oem(app,frame,-1,probe);Resolution result=probe.resolve(frame,-1);
                assertEquals(Resolution.Status.FOUND,result.status);
                assertEquals("FingerprintManagerEx.getSpotPosition",result.selected.evidence);
                Geometry.Bounds expected=frame.toLogical(new Geometry.Bounds(450,2000,630,2180),Geometry.Space.NATIVE_NATURAL);
                assertEquals(expected.left,result.bounds.left,0.001);assertEquals(expected.top,result.bounds.top,0.001);
                assertFalse(result.corroborated);
            }
        } finally {
            huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=false;
            huawei.android.hardware.fingerprint.FingerprintManagerEx.spotPosition=null;
            huawei.android.hardware.fingerprint.FingerprintManagerEx.legacyPosition=new int[]{450,2000,630,2180};
        }
    }
    @Test public void malformedHuaweiSpotAreaFallsBackToValidLegacyRectangle() {
        Context app=RuntimeEnvironment.getApplication();
        Geometry.DisplayFrame frame=new Geometry.DisplayFrame("primary",0,1080,2400,1080,2400,0);
        huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=true;
        try {
            for(int[] invalid:new int[][]{{1,2},{-1,-1,-1,-1},{540,2090,540,2090},{450,2000,1200,2180}}) {
                huawei.android.hardware.fingerprint.FingerprintManagerEx.spotPosition=invalid;
                Probe probe=new Probe();AndroidProviders.oem(app,frame,-1,probe);Resolution result=probe.resolve(frame,-1);
                assertEquals(Resolution.Status.FOUND,result.status);
                assertEquals("FingerprintManagerEx.getHardwarePosition",result.selected.evidence);
                assertTrue(result.issues.stream().anyMatch(i->i.provider.equals("huawei-getSpotPosition")&&i.code==Resolution.Issue.Code.INVALID_DATA));
            }
        } finally {
            huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=false;
            huawei.android.hardware.fingerprint.FingerprintManagerEx.spotPosition=null;
        }
    }
    @Test public void huaweiLogoPointWithoutSensorAreaDoesNotInventBounds() {
        Context app=RuntimeEnvironment.getApplication();
        Geometry.DisplayFrame frame=new Geometry.DisplayFrame("primary",0,1080,2400,1080,2400,0);
        huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=true;
        huawei.android.hardware.fingerprint.FingerprintManagerEx.legacyPosition=new int[]{540,2090,540,2090};
        try {
            Probe probe=new Probe();AndroidProviders.oem(app,frame,-1,probe);Resolution result=probe.resolve(frame,-1);
            assertNull(result.bounds);assertTrue(result.sensorType.underDisplay());
        } finally {
            huawei.android.hardware.fingerprint.FingerprintManagerEx.screen=false;
            huawei.android.hardware.fingerprint.FingerprintManagerEx.legacyPosition=new int[]{450,2000,630,2180};
        }
    }
    private Observation.Type samsungFeatures(String body) throws Exception {
        return SamsungFeatures.read(new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private String samsungFeaturesXml(String value) {
        return "<SecFloatingFeatureSet><SEC_FLOATING_FEATURE_BIOAUTH_CONFIG_FINGERPRINT_FEATURES>"+value
                +"</SEC_FLOATING_FEATURE_BIOAUTH_CONFIG_FINGERPRINT_FEATURES></SecFloatingFeatureSet>";
    }
    @Test public void samsungFirmwareExplicitTypeDoesNotRequireHiddenApi() throws Exception {
        assertEquals(Observation.Type.OPTICAL,samsungFeatures("\ufeff"+samsungFeaturesXml("optical_display_phone")));
        assertEquals(Observation.Type.OPTICAL,samsungFeatures(samsungFeaturesXml("optical_display_tablet")));
        assertEquals(Observation.Type.SIDE,samsungFeatures(samsungFeaturesXml("capacitive_powerkey_phone")));
        assertEquals(Observation.Type.ULTRASONIC,samsungFeatures(samsungFeaturesXml("ultrasonic_display_phone")));
        for(String value:new String[]{"ultrasonic_display_phone_extra", "ultrasonic", "capacitive_powerkey_phone_extra", "powerkey_phone", "capacitive_phone", "", "none", "optical", "optical_display_phone_extra", "optical_display_tablet_extra", "optical_tablet", "side"})
            assertEquals(Observation.Type.UNKNOWN,samsungFeatures(samsungFeaturesXml(value)));
    }
    @Test public void malformedOrAmbiguousSamsungFeatureFilesAreRejected() throws Exception {
        String field="<SEC_FLOATING_FEATURE_BIOAUTH_CONFIG_FINGERPRINT_FEATURES>optical_display_phone</SEC_FLOATING_FEATURE_BIOAUTH_CONFIG_FINGERPRINT_FEATURES>";
        for(String xml:new String[]{"", "<wrong/>", samsungFeaturesXml("optical_display_phone").replace("</SecFloatingFeatureSet>",""),
                "<SecFloatingFeatureSet>"+field+field+"</SecFloatingFeatureSet>",
                "<SecFloatingFeatureSet><nested>"+field+"</nested></SecFloatingFeatureSet>",
                "<!DOCTYPE SecFloatingFeatureSet [<!ENTITY custom 'optical_display_phone'>]>"+samsungFeaturesXml("&custom;"),
                samsungFeaturesXml(String.join("",Collections.nCopies(131073,"x")))}) {
            try {samsungFeatures(xml);fail("Accepted malformed configuration: "+xml.substring(0,Math.min(xml.length(),120)));} catch(IllegalArgumentException expected) { }
        }
    }
    private final Geometry.DisplayFrame frame=new Geometry.DisplayFrame("local:1",0,1080,2400,1080,2400,0);
    public static class Legacy31 {
        public int sensorId=7,sensorType=3,sensorLocationX=540,sensorLocationY=2100,sensorRadius=90;
    }
    public static class Location {
        public String displayId;
        public int sensorLocationX=540,sensorLocationY=2100,sensorRadius=90;
        Location(String displayId){this.displayId=displayId;}
    }
    public static class Modern {
        public int sensorId=7,sensorType=3;
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
    public static class SamsungPlacement {
        public static final int SEM_SENSOR_POSITION_DISPLAY=102,SEM_SENSOR_POSITION_POWER_KEY=104,
                SEM_SENSOR_POSITION_REAR=103,SEM_SENSOR_POSITION_HOME_KEY=101;
        int position;
        SamsungPlacement(int position){this.position=position;}
        public int semGetSensorPosition(){return position;}
        public Rect semGetFingerIconRectInDisplay(){throw new SecurityException("fixture");}
    }
    public static class UnnamedSamsungPosition {
        public int semGetSensorPosition(){return 2;}
    }
    private Context context(Object manager) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()){
            @Override public Object getSystemService(String name){return "fingerprint".equals(name)?manager:super.getSystemService(name);}
        };
    }
    private Resolution query(Object... sensors) {
        Probe p=new Probe();AndroidProviders.framework(context(new Manager(sensors)),frame,-1,p);return p.resolve(frame,-1);
    }
    @Test @Config(qualifiers="w1080dp-h2400dp-mdpi")
    public void communityGeometryRequiresExplicitProfileSelection() {
        Context app=RuntimeEnvironment.getApplication();
        Display display=((DisplayManager)app.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(0);
        String maker=android.os.Build.MANUFACTURER,model=android.os.Build.MODEL;
        boolean feature=app.getPackageManager().hasSystemFeature("android.hardware.fingerprint");
        try {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MANUFACTURER","INFINIX");
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MODEL","Infinix X6739");
            Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",true);
            FingerprintLocation.Options defaults=new FingerprintLocation.Options.Builder().properties(key->"").build();
            assertNull(defaults.stockFirmwareGeometry);assertNull(defaults.communityGeometry);assertNull(FingerprintLocation.query(app,display,defaults).bounds);
            FingerprintLocation.Options enabled=new FingerprintLocation.Options.Builder().properties(key->"")
                    .communityGeometry(CommunityGeometryProfile.INFINIX_X6739_8B45A3C8).build();
            Resolution r=FingerprintLocation.query(app,display,enabled);
            assertEquals(Resolution.Status.FOUND,r.status);assertFalse(r.corroborated);
            assertEquals(540,r.bounds.centerX(),0);assertEquals(2172,r.bounds.centerY(),0);
            assertEquals(Observation.Source.COMMUNITY_ROM_CONFIGURATION,r.selected.source);
        } finally {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MANUFACTURER",maker);
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MODEL",model);
            Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",feature);
        }
    }
    @Test public void transsionRuntimeTypeDoesNotRequireHiddenPropertiesOrSpecifications() {
        Context app=RuntimeEnvironment.getApplication();
        Display display=((DisplayManager)app.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(0);
        String maker=android.os.Build.MANUFACTURER;
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MANUFACTURER","TECNO");
        try {
            FingerprintLocation.Options options=new FingerprintLocation.Options.Builder()
                    .properties(key->"ro.optical_fingerprint_support".equals(key)?"1":"").build();
            Context denied=new ContextWrapper(context(new Denied())) {
                @Override public Context createDisplayContext(Display target){return this;}
            };
            Resolution r=FingerprintLocation.query(denied,display,options);
            assertEquals(Observation.Type.OPTICAL,r.sensorType);assertNull(r.bounds);
            assertEquals(Resolution.Status.ACCESS_DENIED,r.status);
            assertTrue(r.typeEvidence.stream().anyMatch(o->o.source==Observation.Source.ROM_PROPERTIES
                    && "ro.optical_fingerprint_support".equals(o.evidence)));
        } finally {org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MANUFACTURER",maker);}
    }
    @Test public void officialSpecificationFallbackRequiresExplicitOptionAndDeclaredFeature() {
        Context app=RuntimeEnvironment.getApplication();
        Display display=((DisplayManager)app.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(0);
        String maker=android.os.Build.MANUFACTURER,model=android.os.Build.MODEL;
        try {
            for(String[] identity:new String[][]{{"Google","Pixel 8 Pro","UNDER_DISPLAY"},{"SHARP","SH-01L","FRONT"}}) {
                org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MANUFACTURER",identity[0]);
                org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MODEL",identity[1]);
                assertFalse(new FingerprintLocation.Options.Builder().build().includeDeviceSpecifications);
                Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",true);
                FingerprintLocation.Options defaults=new FingerprintLocation.Options.Builder().properties(key->"").build();
                assertEquals(Resolution.TypeStatus.UNKNOWN,FingerprintLocation.query(app,display,defaults).typeStatus);
                FingerprintLocation.Options enabled=new FingerprintLocation.Options.Builder().properties(key->"")
                        .includeDeviceSpecifications(true).build();
                Resolution fallback=FingerprintLocation.query(app,display,enabled);
                assertEquals(Observation.Type.valueOf(identity[2]),fallback.sensorType);assertNull(fallback.bounds);
                assertEquals(Observation.Source.DEVICE_SPECIFICATION,fallback.typeEvidence.get(0).source);
                Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",false);
                assertEquals(Resolution.TypeStatus.UNKNOWN,FingerprintLocation.query(app,display,enabled).typeStatus);
            }
        } finally {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MANUFACTURER",maker);
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class,"MODEL",model);
        }
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
    @Test public void samsungPlacementUsesNamedOemConstantsAndSurvivesDeniedIconGeometry() {
        Context app=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",true);
        int[] positions={102,104,103,101,0,2};
        Observation.Type[] types={Observation.Type.UNDER_DISPLAY,Observation.Type.SIDE,Observation.Type.REAR,
                Observation.Type.HOME,Observation.Type.UNKNOWN,Observation.Type.UNKNOWN};
        for(int i=0;i<positions.length;i++) {
            Probe p=new Probe();AndroidProviders.oem(context(new SamsungPlacement(positions[i])),frame,-1,p);
            Resolution r=p.resolve(frame,-1);assertEquals(types[i],r.sensorType);assertNull(r.bounds);
            assertTrue(r.issues.stream().anyMatch(x->x.provider.equals("samsung-api")&&x.code==Resolution.Issue.Code.ACCESS_DENIED));
            if(types[i]!=Observation.Type.UNKNOWN)assertEquals(Observation.Source.SAMSUNG_API,r.typeEvidence.get(0).source);
        }
    }
    @Test public void samsungPlacementRequiresFingerprintFeatureAndNamedConstants() {
        Context app=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",false);
        Probe absent=new Probe();AndroidProviders.oem(context(new SamsungPlacement(102)),frame,-1,absent);
        assertEquals(Resolution.TypeStatus.UNKNOWN,absent.resolve(frame,-1).typeStatus);
        Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",true);
        Probe unnamed=new Probe();AndroidProviders.oem(context(new UnnamedSamsungPosition()),frame,-1,unnamed);
        assertEquals(Resolution.TypeStatus.UNKNOWN,unnamed.resolve(frame,-1).typeStatus);
        Probe external=new Probe();AndroidProviders.oem(context(new SamsungPlacement(102)),
                new Geometry.DisplayFrame("external",1,1080,2400,1080,2400,0),-1,external);
        assertEquals(Resolution.TypeStatus.UNKNOWN,external.resolve(frame,-1).typeStatus);
    }
    @Test public void samsungPlacementCannotLeakToAnExplicitUnboundSensorOrHideTypeConflict() {
        Context app=RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.fingerprint",true);
        Probe p=new Probe();AndroidProviders.oem(context(new SamsungPlacement(104)),frame,-1,p);
        assertEquals(Resolution.TypeStatus.UNKNOWN,p.resolve(frame,9).typeStatus);
        p.add(new Observation(-1,"",Observation.Type.OPTICAL,Observation.Source.FRAMEWORK_RESOURCE,
                Geometry.Space.NATIVE_NATURAL,Geometry.Bounds.circle(540,2100,90),"fixture"));
        assertEquals(Resolution.Status.CONFLICT,p.resolve(frame,-1).status);assertNull(p.resolve(frame,-1).bounds);
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
            assertEquals(Observation.Type.UNKNOWN,result.sensorType);assertTrue(result.typeEvidence.isEmpty());
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
    public static class UnreadableLocations {
        public int sensorId=7,sensorType=3;
        public List<?> getAllLocations(){throw new SecurityException("fixture");}
    }
    @Test public void androidSensorTypeConstantsMatchThePlatformContract() {
        Observation.Type[] types={Observation.Type.UNKNOWN,Observation.Type.REAR,Observation.Type.ULTRASONIC,
                Observation.Type.OPTICAL,Observation.Type.SIDE,Observation.Type.HOME};
        for(int i=0;i<types.length;i++) {
            Legacy31 sensor=new Legacy31();sensor.sensorType=i;
            assertEquals(types[i],query(sensor).sensorType);
        }
    }
    @Test public void knownTypeSurvivesMissingInvalidOrDeniedGeometry() {
        Legacy31 malformed=new Legacy31();malformed.sensorRadius=0;
        for(Object sensor:new Object[]{malformed,new Modern(),new UnreadableLocations()}) {
            Resolution r=query(sensor);assertNull(r.bounds);assertEquals(Observation.Type.OPTICAL,r.sensorType);
            assertEquals(Resolution.TypeStatus.KNOWN,r.typeStatus);
        }
    }
    @Test public void sideResourceOnlyTreatsAnExplicitTrueAsEvidence() {
        for(boolean enabled:new boolean[]{true,false}) {
            Context base=RuntimeEnvironment.getApplication();
            android.content.res.Resources original=base.getResources();
            android.content.res.Resources resources=new android.content.res.Resources(original.getAssets(),
                    original.getDisplayMetrics(),original.getConfiguration()) {
                @Override public int getIdentifier(String name,String type,String pkg){
                    return "config_is_powerbutton_fps".equals(name)&&"bool".equals(type)?123:0;
                }
                @Override public boolean getBoolean(int id){return enabled;}
            };
            Context context=new ContextWrapper(base){@Override public android.content.res.Resources getResources(){return resources;}};
            Probe p=new Probe();AndroidProviders.resources(context,frame,-1,p);Resolution r=p.resolve(frame,-1);
            assertEquals(enabled?Observation.Type.SIDE:Observation.Type.UNKNOWN,r.sensorType);assertNull(r.bounds);
            if(enabled)assertEquals(Resolution.Status.NOT_UNDER_DISPLAY,r.status);
        }
    }
    private Resolution sideArray(Geometry.DisplayFrame display,String fixture) {
        Context base=RuntimeEnvironment.getApplication();
        android.content.res.Resources original=base.getResources();
        int fixtureId=original.getIdentifier(fixture,"array",base.getPackageName());assertNotEquals(0,fixtureId);
        android.content.res.Resources resources=new android.content.res.Resources(original.getAssets(),
                original.getDisplayMetrics(),original.getConfiguration()) {
            @Override public int getIdentifier(String name,String type,String pkg){return "config_sfps_sensor_props".equals(name)?fixtureId:0;}
        };
        Context context=new ContextWrapper(base){@Override public android.content.res.Resources getResources(){return resources;}};
        Probe p=new Probe();AndroidProviders.resources(context,display,-1,p);return p.resolve(display,-1);
    }
    @Test public void sideLocationArrayIsTypeOnlyAndDisplayScoped() {
        Resolution r=sideArray(frame,"sfps_default");
        assertEquals(Observation.Type.SIDE,r.sensorType);assertEquals(Resolution.Status.NOT_UNDER_DISPLAY,r.status);assertNull(r.bounds);
        assertEquals(Observation.Type.UNKNOWN,sideArray(frame,"sfps_external").sensorType);
        Geometry.DisplayFrame external=new Geometry.DisplayFrame("local:2",1,1080,2400,1080,2400,0);
        assertEquals(Observation.Type.SIDE,sideArray(external,"sfps_external").sensorType);
        assertEquals(Observation.Type.UNKNOWN,sideArray(external,"sfps_default").sensorType);
    }
    @Test public void malformedSideLocationArraysDoNotInventClassification() {
        for(String fixture:new String[]{"sfps_short","sfps_string","sfps_negative","sfps_zero","sfps_null","sfps_mixed","sfps_mixed_reversed"}) {
            Resolution r=sideArray(frame,fixture);assertEquals(Observation.Type.UNKNOWN,r.sensorType);assertNull(r.bounds);
            assertTrue(r.issues.stream().anyMatch(i->i.code==Resolution.Issue.Code.INVALID_DATA));
        }
    }
    @Test public void failureDiagnosticsKeepExceptionClassWithoutMessage() {
        Probe p=new Probe();
        AndroidProviders.failure("fixture",new java.lang.reflect.InvocationTargetException(new SecurityException("private message")),p);
        Resolution.Issue issue=p.issues().get(0);
        assertEquals(Resolution.Issue.Code.ACCESS_DENIED,issue.code);assertEquals("java.lang.SecurityException",issue.exceptionType);
        AndroidProviders.failure("missing",new NoSuchMethodException("hidden or absent"),p);
        assertEquals(Resolution.Issue.Code.UNAVAILABLE,p.issues().get(1).code);
        assertEquals("java.lang.NoSuchMethodException",p.issues().get(1).exceptionType);
        assertFalse(issue.toString().contains("private message"));
    }

}
