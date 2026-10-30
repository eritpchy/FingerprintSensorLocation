package com.surcumference.fingerprintlocation.diagnostic;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Self-launch avoids depending on a separate Robo crawler's Activity-start privileges. */
@RunWith(AndroidJUnit4.class)
public final class DiagnosticE2eTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final JSONArray steps = new JSONArray();
    private File output;
    private MainActivity activity;
    private boolean firebaseTestLab;

    @Test public void ordinaryAppAndExplicitSystemUiBridge() throws Exception {
        output = new File(context.getExternalFilesDir(null), "e2e/" + SystemClock.elapsedRealtime());
        assertTrue(output.mkdirs());
        firebaseTestLab = "true".equals(shellReport("getprop firebase.test.lab").trim());
        try {
            assertEquals("FPSLOC_SCRIPT_OK", shellScript("printf '%s\\n' FPSLOC_SCRIPT_OK").trim());
            captureFingerprintServiceReport();
            captureGoogleCoordinateReport();
            long start = SystemClock.elapsedRealtime();
            activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            JSONObject initial = capture("01-default", start, "ordinary-app");
            assertFalse(initial.getBoolean("ifaa"));
            click("Landscape");
            JSONObject landscape = captureOrientation("02-landscape", initial, false);
            click("Portrait");
            JSONObject portrait = captureOrientation("03-portrait", landscape, true);
            start = click("Query + IFAA");
            assertTrue(capture("04-ifaa", start, "ordinary-app").getBoolean("ifaa"));
            start = SystemClock.elapsedRealtime();
            instrumentation.runOnMainSync(() -> activity.useSystemUiDump(this::systemUiReport));
            capture("05-systemui-bridge", start, "app-with-instrumentation-shell-report");
            start = SystemClock.elapsedRealtime();
            instrumentation.runOnMainSync(() -> activity.useSystemUiDump(null));
            JSONObject restored = capture("06-default-restored", start, "ordinary-app");
            assertEquals(portrait.getInt("rotation"), restored.getInt("rotation"));
            assertEquals(portrait.getString("status"), restored.getString("status"));
            assertEquals(portrait.optString("bounds"), restored.optString("bounds"));
            assertEquals(portrait.getString("sensorType"), restored.getString("sensorType"));
            assertEquals(portrait.getString("typeStatus"), restored.getString("typeStatus"));
            assertFalse(restored.getBoolean("deviceSpecifications"));
            start = click("Use device specifications (type only)");
            JSONObject specifications = capture("07-specifications-on", start, "ordinary-app");
            assertTrue(specifications.getBoolean("deviceSpecifications"));
            assertEquals(restored.optString("bounds"), specifications.optString("bounds"));
            if ("KNOWN".equals(restored.getString("typeStatus"))) {
                assertEquals(restored.getString("sensorType"), specifications.getString("sensorType"));
                assertEquals(restored.getJSONArray("typeEvidence").toString(), specifications.getJSONArray("typeEvidence").toString());
            }
            String exteriorSpecification = "SHARP".equalsIgnoreCase(android.os.Build.MANUFACTURER)
                    && "SH-01L".equals(android.os.Build.MODEL) ? "FRONT"
                    : "TECNO".equalsIgnoreCase(android.os.Build.MANUFACTURER)
                    && "TECNO KI5k".equals(android.os.Build.MODEL) ? "SIDE" : null;
            if (exteriorSpecification != null
                    && "UNKNOWN".equals(restored.getString("typeStatus"))
                    && ("UNAVAILABLE".equals(restored.getString("status")) || "ACCESS_DENIED".equals(restored.getString("status")))
                    && context.getPackageManager().hasSystemFeature("android.hardware.fingerprint")) {
                assertEquals(exteriorSpecification,specifications.getString("sensorType"));
                assertEquals("NOT_UNDER_DISPLAY",specifications.getString("status"));
                assertFalse(specifications.has("bounds"));
                assertEquals("DEVICE_SPECIFICATION",specifications.getJSONArray("typeEvidence").getJSONObject(0).getString("source"));
            }
            click("Landscape");
            JSONObject specificationsLandscape = captureOrientation("08-specifications-landscape", specifications, false);
            click("Portrait");
            JSONObject specificationsPortrait = captureOrientation("09-specifications-portrait", specificationsLandscape, true);
            assertTrue(specificationsPortrait.getBoolean("deviceSpecifications"));
            assertEquals(specifications.getString("sensorType"), specificationsPortrait.getString("sensorType"));
            assertEquals(specifications.optString("bounds"), specificationsPortrait.optString("bounds"));
            start = click("Query + IFAA");
            JSONObject specificationsIfaa = capture("10-specifications-ifaa", start, "ordinary-app");
            assertTrue(specificationsIfaa.getBoolean("ifaa"));
            assertTrue(specificationsIfaa.getBoolean("deviceSpecifications"));
            start = click("Query");
            JSONObject specificationsDefault = capture("11-specifications-default", start, "ordinary-app");
            assertFalse(specificationsDefault.getBoolean("ifaa"));
            start = click("Use device specifications (type only)");
            JSONObject disabled = capture("12-specifications-off", start, "ordinary-app");
            assertFalse(disabled.getBoolean("deviceSpecifications"));
            assertFalse(disabled.getBoolean("ifaa"));
            assertEquals(restored.getString("sensorType"), disabled.getString("sensorType"));
            assertEquals(restored.getString("typeStatus"), disabled.getString("typeStatus"));
            assertEquals(restored.getString("status"), disabled.getString("status"));
            assertEquals(restored.optString("bounds"), disabled.optString("bounds"));
            assertFalse(disabled.getBoolean("stockFirmwareGeometry"));
            start = click("Use verified stock firmware geometry");
            JSONObject stock = capture("13-stock-on", start, "ordinary-app");
            assertTrue(stock.getBoolean("stockFirmwareGeometry"));
            com.surcumference.fingerprintlocation.core.StockFirmwareGeometryProfile profile =
                    stockProfile();
            boolean eligible = profile.matches(android.os.Build.MANUFACTURER, android.os.Build.MODEL,
                    android.os.Build.DEVICE, android.os.Build.ID, android.os.Build.DISPLAY, android.os.Build.VERSION.INCREMENTAL)
                    && ("UNAVAILABLE".equals(disabled.getString("status")) || "ACCESS_DENIED".equals(disabled.getString("status")))
                    && context.getPackageManager().hasSystemFeature("android.hardware.fingerprint");
            boolean ordinaryStockFound = eligible && "FOUND".equals(stock.getString("status"));
            if (ordinaryStockFound) assertStockBounds(stock, "STOCK_FIRMWARE_CONFIGURATION", profile);
            else {
                assertEquals(disabled.getString("status"), stock.getString("status"));
                assertEquals(disabled.optString("bounds"), stock.optString("bounds"));
            }
            click("Landscape");
            JSONObject stockLandscape = captureOrientation("14-stock-landscape", stock, false);
            click("Portrait");
            JSONObject stockPortrait = captureOrientation("15-stock-portrait", stockLandscape, true);
            if (ordinaryStockFound) {
                assertTrue(stockLandscape.getBoolean("orientationRequestSatisfied"));
                assertTrue(stockPortrait.getBoolean("orientationRequestSatisfied"));
                assertStockBounds(stockLandscape, "STOCK_FIRMWARE_CONFIGURATION", profile);
                assertStockBounds(stockPortrait, "STOCK_FIRMWARE_CONFIGURATION", profile);
                assertEquals(stock.optString("bounds"), stockPortrait.optString("bounds"));
            }
            start = click("Use verified stock firmware geometry");
            JSONObject stockOff = capture("16-stock-off", start, "ordinary-app");
            assertFalse(stockOff.getBoolean("stockFirmwareGeometry"));
            assertEquals(disabled.getString("status"), stockOff.getString("status"));
            assertEquals(disabled.optString("bounds"), stockOff.optString("bounds"));
            assertEquals(disabled.getString("sensorType"), stockOff.getString("sensorType"));
            boolean callerBytesVerified = eligible;
            if (eligible) {
                JSONArray files = new JSONArray();
                int componentIndex = 0;
                for (java.util.Map.Entry<String, String> entry : profile.componentHashes().entrySet()) {
                    byte[] firmwareBytes = readFirmwareComponent(entry.getKey());
                    StringBuilder firmwareHash = new StringBuilder();
                    for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(firmwareBytes))
                        firmwareHash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                    boolean matches = entry.getValue().equals(firmwareHash.toString());
                    callerBytesVerified &= matches;
                    files.put(new JSONObject().put("path", entry.getKey()).put("bytes", firmwareBytes.length)
                            .put("sha256", firmwareHash.toString()).put("matchesPinnedComponent", matches));
                    try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(new FileOutputStream(
                            new File(output, "firmware-component-shell-output-" + componentIndex++ + ".gz")))) {gzip.write(firmwareBytes);}
                }
                int metadataIndex = 0;
                for (String path : profile.metadataPaths()) {
                    byte[] bytes = readFirmwareComponent(path);
                    boolean matches = java.util.Arrays.equals(bytes, new byte[]{0,0,0,(byte)(metadataIndex++ == 0 ? 4 : 2)});
                    callerBytesVerified &= matches;
                    files.put(new JSONObject().put("path", path).put("bytes", bytes.length)
                            .put("rawBase64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                            .put("matchesPinnedSelector", matches));
                }
                JSONObject firmwareEvidence = new JSONObject().put("executionContext", "instrumentation-shell")
                        .put("profile", profile.name()).put("files", files).put("allPrerequisitesVerified", callerBytesVerified)
                        .put("status", callerBytesVerified ? "VERIFIED_COMPONENT_BYTES" : "COMPONENT_BYTES_NOT_VERIFIED");
                write(new File(output, "firmware-component-shell.json"), firmwareEvidence.toString(2));
                System.out.println("FINGERPRINT_FIRMWARE_COMPONENT_META=" + firmwareEvidence);
            }
            start = SystemClock.elapsedRealtime();
            instrumentation.runOnMainSync(() -> activity.useFirmwareReader(path -> {
                if (!profile.componentHashes().containsKey(path) && !java.util.Arrays.asList(profile.metadataPaths()).contains(path))
                    throw new IOException("Unexpected firmware path");
                return new ParcelFileDescriptor.AutoCloseInputStream(
                        instrumentation.getUiAutomation().executeShellCommand("cat " + path));
            }));
            click("Use verified stock firmware geometry");
            JSONObject callerStock = capture("17-caller-stock-on", start, "app-with-instrumentation-shell-firmware");
            assertTrue(callerStock.getBoolean("firmwareComponentReader"));
            if (callerBytesVerified) assertStockBounds(callerStock, "CALLER_FIRMWARE_CONFIGURATION", profile);
            else {
                assertEquals(disabled.getString("status"), callerStock.getString("status"));
                assertEquals(disabled.optString("bounds"), callerStock.optString("bounds"));
            }
            click("Landscape");
            JSONObject callerLandscape = captureOrientation("18-caller-stock-landscape", callerStock, false,
                    "app-with-instrumentation-shell-firmware");
            click("Portrait");
            JSONObject callerPortrait = captureOrientation("19-caller-stock-portrait", callerLandscape, true,
                    "app-with-instrumentation-shell-firmware");
            if (callerBytesVerified) {
                assertTrue(callerLandscape.getBoolean("orientationRequestSatisfied"));
                assertTrue(callerPortrait.getBoolean("orientationRequestSatisfied"));
                assertStockBounds(callerLandscape, "CALLER_FIRMWARE_CONFIGURATION", profile);
                assertStockBounds(callerPortrait, "CALLER_FIRMWARE_CONFIGURATION", profile);
                assertEquals(callerStock.optString("bounds"), callerPortrait.optString("bounds"));
            }
            start = click("Use verified stock firmware geometry");
            instrumentation.runOnMainSync(() -> activity.useFirmwareReader(null));
            JSONObject callerOff = capture("20-caller-stock-off", start, "ordinary-app");
            assertFalse(callerOff.getBoolean("firmwareComponentReader"));
            assertFalse(callerOff.getBoolean("stockFirmwareGeometry"));
            assertEquals(disabled.getString("status"), callerOff.getString("status"));
            assertEquals(disabled.optString("bounds"), callerOff.optString("bounds"));
            assertEquals(disabled.getString("sensorType"), callerOff.getString("sensorType"));
            verifyQr(initial.toString());
            click("Close");
            long deadline = SystemClock.elapsedRealtime() + 5000;
            while (!activity.isDestroyed() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
            assertTrue("Close must finish the diagnostic", activity.isFinishing());
            screenshot("21-closed");
            JSONObject result = new JSONObject().put("schema", 1).put("libraryVersion", BuildConfig.VERSION_NAME)
                    .put("steps", steps).put("closed", true)
                    .put("stockProfileEligible", eligible).put("ordinaryStockCoordinatesFound", ordinaryStockFound)
                    .put("callerFirmwareBytesVerified", callerBytesVerified)
                    .put("callerFirmwareCoordinatesFound", callerBytesVerified && "FOUND".equals(callerStock.getString("status")))
                    .put("callerFirmwareExecutionContext", "instrumentation-shell; not ordinary-app access")
                    .put("geometryAccuracy", "Not established by diagnostic screenshots or harness success");
            write(new File(output, "summary.json"), result.toString(2));
            System.out.println("FINGERPRINT_E2E_OUTPUT=" + output.getAbsolutePath());
        } catch (Exception | AssertionError failure) {
            try {
                write(new File(output, "failure.json"), new JSONObject().put("steps", steps)
                        .put("error", failure.getClass().getSimpleName()).put("message", failure.getMessage()).toString(2));
                if (activity != null && activity.hasWindowFocus()) screenshot("99-failure");
            } catch (Exception captureFailure) {failure.addSuppressed(captureFailure);}
            throw failure;
        } finally {
            if (activity != null && !activity.isFinishing()) instrumentation.runOnMainSync(activity::finish);
        }
    }
    private void verifyQr(String json) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(800,800,Bitmap.Config.ARGB_8888);
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    QrExport qr = new QrExport(context,json);
                    int size = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
                    qr.measure(size,size);qr.layout(0,0,800,800);qr.draw(new Canvas(bitmap));
                } catch (Exception error) {throw new AssertionError(error);}
            });
            int[] pixels = new int[800*800];bitmap.getPixels(pixels,0,800,0,0,800,800);
            try (FileOutputStream stream = new FileOutputStream(new File(output,"qr-render-roundtrip.png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));
            }
            String payload = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                    new RGBLuminanceSource(800,800,pixels))),
                    Collections.singletonMap(DecodeHintType.PURE_BARCODE,Boolean.TRUE)).getText();
            assertTrue(payload.startsWith("FPSL1:"));
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(
                    android.util.Base64.decode(payload.substring(6),android.util.Base64.DEFAULT)));
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];int count;
                while ((count=gzip.read(buffer))!=-1) bytes.write(buffer,0,count);
                assertEquals(json,bytes.toString("UTF-8"));
            }
        } finally {bitmap.recycle();}
    }
    private com.surcumference.fingerprintlocation.core.StockFirmwareGeometryProfile stockProfile() {
        for (com.surcumference.fingerprintlocation.core.StockFirmwareGeometryProfile candidate :
                com.surcumference.fingerprintlocation.core.StockFirmwareGeometryProfile.values())
            if (candidate.matches(android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.DEVICE,
                    android.os.Build.ID, android.os.Build.DISPLAY, android.os.Build.VERSION.INCREMENTAL)) return candidate;
        return com.surcumference.fingerprintlocation.core.StockFirmwareGeometryProfile.NOTHING_A069_B4_1_260808_1352;
    }
    private byte[] readFirmwareComponent(String path) throws Exception {
        ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand("cat " + path);
        try (FileInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int count;
            while ((count=input.read(buffer))!=-1) {
                if (count==0 || bytes.size()+count>4*1024*1024) throw new IOException("Invalid firmware output");
                bytes.write(buffer,0,count);
            }
            return bytes.toByteArray();
        }
    }
    private void assertStockBounds(JSONObject value, String source,
            com.surcumference.fingerprintlocation.core.StockFirmwareGeometryProfile profile) throws Exception {
        assertEquals("FOUND", value.getString("status"));
        assertEquals(source, value.getString("source"));
        assertFalse(value.getBoolean("corroborated"));
        boolean pixel = profile == com.surcumference.fingerprintlocation.core.StockFirmwareGeometryProfile.GOOGLE_PIXEL_8_PRO_BP1A_250505_005_B1;
        int rotation = value.getInt("rotation");
        int width = value.getInt("width"), height = value.getInt("height");
        com.surcumference.fingerprintlocation.core.Geometry.DisplayFrame frame =
                new com.surcumference.fingerprintlocation.core.Geometry.DisplayFrame("", 0,
                        rotation % 2 == 0 ? width : height, rotation % 2 == 0 ? height : width, pixel ? 1344 : 1224, pixel ? 2992 : 2720, rotation);
        com.surcumference.fingerprintlocation.core.Geometry.Bounds expected = frame.toLogical(
                com.surcumference.fingerprintlocation.core.Geometry.Bounds.circle(pixel ? 672 : 612,pixel ? 2261 : 2485,pixel ? 115 : 103),
                com.surcumference.fingerprintlocation.core.Geometry.Space.NATIVE_NATURAL);
        JSONArray bounds = value.getJSONArray("bounds");
        double[] coordinates = {expected.left,expected.top,expected.right,expected.bottom};
        for (int i=0;i<4;i++) assertEquals(coordinates[i], bounds.getDouble(i), 1.0);
    }
    private JSONObject captureOrientation(String name, JSONObject previous, boolean portrait) throws Exception {
        return captureOrientation(name, previous, portrait, "ordinary-app");
    }
    private JSONObject captureOrientation(String name, JSONObject previous, boolean portrait, String executionContext) throws Exception {
        // Large-screen policies can ignore orientation. Query again after the request settles.
        SystemClock.sleep(1200);
        JSONObject value = capture(name, click("Query"), executionContext);
        value.put("orientationRequestChangedRotation", previous.getInt("rotation") != value.getInt("rotation"));
        boolean satisfied = portrait ? value.getInt("height") > value.getInt("width") : value.getInt("width") > value.getInt("height");
        value.put("orientationRequestSatisfied", satisfied);
        steps.getJSONObject(steps.length() - 1).put("orientationRequestSatisfied", satisfied);
        write(new File(output, name + ".json"), value.toString(2));
        return value;
    }
    private JSONObject capture(String name, long start, String executionContext) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 20000;
        JSONObject value = null;
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                JSONObject candidate = new JSONObject(read(new File(context.getFilesDir(), "latest.json")));
                if (candidate.getLong("captureElapsedRealtimeMs") >= start
                        && executionContext.equals(candidate.getString("executionContext"))) {value = candidate;break;}
            } catch (IOException | org.json.JSONException pending) { /* The query file may still be writing. */ }
            SystemClock.sleep(50);
        }
        assertNotNull("No fresh query for " + name, value);
        assertFalse("Instrumentation requires the interactive app APK", value.has("cloudMode"));
        assertEquals(BuildConfig.VERSION_NAME, value.getString("libraryVersion"));
        JSONObject installed = value.getJSONObject("installedApk");
        assertEquals("ordinary-app-self-read", installed.getString("executionContext"));
        assertEquals(context.getPackageName(), installed.getString("packageName"));
        assertEquals(android.os.Process.myUid(), installed.getInt("uid"));
        assertEquals("HASHED_BASE_APK", installed.getString("status"));
        assertTrue(installed.getString("sha256").matches("[0-9a-f]{64}"));
        assertTrue(installed.getLong("bytes") > 0);
        assertEquals(0, installed.getInt("splitApkCount"));
        String sensorType = value.getString("sensorType");
        boolean knownType = "KNOWN".equals(value.getString("typeStatus"));
        assertEquals(knownType, !"UNKNOWN".equals(sensorType));
        if (knownType) assertTrue(value.getJSONArray("typeEvidence").length() > 0);
        JSONObject metrics = value.getJSONObject("displayMetrics");
        assertEquals("Display.getRealMetrics", metrics.getString("source"));
        if (!metrics.has("exceptionType")) {
            assertEquals(value.getInt("width"), metrics.getInt("widthPixels"));
            assertEquals(value.getInt("height"), metrics.getInt("heightPixels"));
        }
        if ("samsung".equalsIgnoreCase(value.getString("manufacturer"))) {
            JSONObject file = value.getJSONObject("samsungPositionFile");
            assertEquals("ordinary-app-file-read", file.getString("executionContext"));
            assertEquals(android.os.Process.myUid(), file.getInt("uid"));
            String status = file.getString("status");
            assertTrue(java.util.Arrays.asList("READABLE", "ACCESS_DENIED", "NOT_PRESENT", "INVALID_DATA", "UNAVAILABLE").contains(status));
            if ("READABLE".equals(status)) assertTrue(file.getString("raw").length() <= 512);
        }
        if ("FOUND".equals(value.getString("status"))) {
            assertTrue(knownType);
            assertTrue("UNDER_DISPLAY".equals(sensorType) || "OPTICAL".equals(sensorType) || "ULTRASONIC".equals(sensorType));
            JSONArray b = value.getJSONArray("bounds");
            assertTrue(b.getDouble(0) >= 0 && b.getDouble(1) >= 0);
            assertTrue(b.getDouble(2) > b.getDouble(0) && b.getDouble(3) > b.getDouble(1));
            assertTrue(b.getDouble(2) <= value.getInt("width") && b.getDouble(3) <= value.getInt("height"));
            assertFalse(value.getString("source").isEmpty());
        } else assertFalse(value.has("bounds"));
        instrumentation.waitForIdleSync();
        write(new File(output, name + ".json"), value.toString(2));
        screenshot(name);
        steps.put(new JSONObject().put("name", name).put("status", value.getString("status"))
                .put("executionContext", executionContext).put("rotation", value.getInt("rotation")));
        return value;
    }
    private long click(String label) {
        long start = SystemClock.elapsedRealtime();
        instrumentation.runOnMainSync(() -> {
            assertTrue("Diagnostic lost foreground", activity.hasWindowFocus() && !activity.isFinishing() && !activity.isDestroyed());
            Button button = find(activity.getWindow().getDecorView(), label);
            assertNotNull("Missing control " + label, button);
            if (button instanceof CompoundButton) {
                CompoundButton toggle = (CompoundButton) button;
                boolean checked = toggle.isChecked();
                toggle.performClick();
                assertTrue("Toggle did not change " + label, checked != toggle.isChecked());
            } else assertTrue("Button did not handle " + label, button.performClick());
        });
        return start;
    }
    private Button find(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button found = find(((ViewGroup) view).getChildAt(i), label);if (found != null) return found;
        }
        return null;
    }
    private String systemUiReport() throws IOException {
        String report = shellReport("dumpsys -t 5 activity service com.android.systemui/.SystemUIService");
        write(new File(output, "05-systemui-report.txt"), report);
        try { exportShellReport("SYSTEMUI", "05-systemui-report.json", report); }
        catch (Exception error) { System.out.println("SYSTEMUI_DUMP_ERROR=" + error.getClass().getName()); }
        return report;
    }
    private void captureFingerprintServiceReport() {
        try {
            String report = shellReport("dumpsys -t 5 fingerprint");
            write(new File(output, "fingerprint-service-shell.txt"), report);
            exportShellReport("FINGERPRINT_SERVICE", "fingerprint-service-shell.json", report);
        } catch (Exception error) {
            System.out.println("FINGERPRINT_SERVICE_DUMP_ERROR=" + error.getClass().getName());
        }
    }
    private void captureGoogleCoordinateReport() {
        if (!"Google".equalsIgnoreCase(android.os.Build.MANUFACTURER)
                || !"husky".equals(android.os.Build.DEVICE)) return;
        try {
            String report = shellScript("id -u; for p in "
                    + "/sys/firmware/devicetree/base/chosen/plat/platform "
                    + "/sys/firmware/devicetree/base/chosen/plat/product "
                    + "/sys/firmware/devicetree/base/chosen/plat/stage "
                    + "/sys/firmware/devicetree/base/chosen/plat/major "
                    + "/sys/firmware/devicetree/base/chosen/plat/minor "
                    + "/sys/firmware/devicetree/base/chosen/plat/variant "
                    + "/sys/devices/platform/exynos-drm/primary-panel/panel_name; "
                    + "do printf 'FILE %s\\n' \"$p\"; ls -l \"$p\"; head -c 512 \"$p\" | od -An -tx1; done; "
                    + "sha256sum /vendor/apex/com.google.android.hardware.biometrics.fingerprint.apex "
                    + "/vendor/lib64/libgf_hal.so "
                    + "/apex/com.google.android.hardware.biometrics.fingerprint/lib64/libgf_hal.so "
                    + "/apex/com.google.android.hardware.biometrics.fingerprint/lib64/fp_utils.so "
                    + "/apex/com.google.android.hardware.biometrics.fingerprint/bin/hw/android.hardware.biometrics.fingerprint-service.goodix");
            write(new File(output, "google-coordinate-shell.txt"), report);
            exportShellReport("GOOGLE_COORDINATE", "google-coordinate-shell.json", report);
        } catch (Exception error) {
            System.out.println("GOOGLE_COORDINATE_DUMP_ERROR=" + error.getClass().getName());
        }
    }
    private void exportShellReport(String kind, String metadataFile, String report) throws Exception {
        byte[] raw = report.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(compressed)) {gzip.write(raw);}
        String payload = android.util.Base64.encodeToString(compressed.toByteArray(), android.util.Base64.NO_WRAP);
        int parts = (payload.length() + 1999) / 2000;
        StringBuilder sha = new StringBuilder();
        for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(raw))
            sha.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        String id = java.util.UUID.randomUUID().toString();
        JSONObject metadata = new JSONObject().put("probeRunId", id).put("executionContext", "instrumentation-shell")
                .put("reportKind", kind)
                .put("manufacturer", android.os.Build.MANUFACTURER).put("model", android.os.Build.MODEL)
                .put("buildDisplay", android.os.Build.DISPLAY).put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("captureElapsedRealtimeMs", SystemClock.elapsedRealtime()).put("sha256", sha.toString())
                .put("bytes", raw.length).put("parts", parts)
                .put("permissionDenial", report.contains("Permission Denial"));
        write(new File(output, metadataFile), metadata.toString(2));
        System.out.println(kind + "_DUMP_META=" + metadata);
        for (int i = 0; i < parts; i++)
            System.out.println(kind + "_DUMP_PART=" + new JSONObject().put("probeRunId", id)
                    .put("part", i + 1).put("parts", parts).put("payload", payload.substring(i * 2000, Math.min(payload.length(), (i + 1) * 2000))));
    }
    private String shellReport(String command) throws IOException {
        // UiAutomation supplies shell access only inside the explicitly installed test harness.
        ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(
                command);
        try (FileInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            return readShellOutput(input);
        }
    }
    private String shellScript(String script) throws IOException {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            ParcelFileDescriptor[] streams = instrumentation.getUiAutomation().executeShellCommandRw("sh");
            try (FileInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(streams[0])) {
                try (FileOutputStream writer = new ParcelFileDescriptor.AutoCloseOutputStream(streams[1])) {
                    writer.write(("exec 2>&1\n" + script + "\n").getBytes(StandardCharsets.UTF_8));
                }
                return readShellOutput(input);
            }
        }
        File file = new File(output, "shell-" + java.util.UUID.randomUUID() + ".sh");
        if (!file.getAbsolutePath().matches("[A-Za-z0-9_./-]+")) throw new IOException("Unsupported shell script path");
        write(file, "exec 2>&1\n" + script + "\n");
        return shellReport("sh " + file.getAbsolutePath());
    }
    private String readShellOutput(FileInputStream input) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 2 * 1024 * 1024) throw new IOException("Shell report exceeds limit");
                bytes.write(buffer, 0, count);
            }
            return bytes.toString("UTF-8");
        }
    }
    private void screenshot(String name) throws IOException {
        if (!name.endsWith("-closed")) {
            final boolean[] focused = {false};
            instrumentation.runOnMainSync(() -> focused[0] = activity != null && activity.hasWindowFocus());
            assertTrue("Diagnostic lost foreground before screenshot", focused[0]);
            CountDownLatch drawn = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> activity.getWindow().getDecorView().postOnAnimation(() ->
                    activity.getWindow().getDecorView().postOnAnimation(drawn::countDown)));
            try { assertTrue("Diagnostic did not render before screenshot", drawn.await(3, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IOException(interrupted);}
        }
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("Screenshot unavailable for " + name, bitmap);
        try (FileOutputStream stream = new FileOutputStream(new File(output, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {bitmap.recycle();}
        if (firebaseTestLab) publishScreenshot(name);
    }
    private void publishScreenshot(String name) {
        File source = new File(output, name + ".png");
        String destination = "/sdcard/screenshots/fpsloc-" + output.getName() + "-" + name + ".png";
        try {
            String copied = shellScript("mkdir -p /sdcard/screenshots && cp " + shellQuote(source.getAbsolutePath())
                    + " " + shellQuote(destination) + " && sha256sum " + shellQuote(destination));
            String digest = copied.trim().split("\\s+", 2)[0];
            JSONObject metadata = new JSONObject().put("name", name).put("destination", destination)
                    .put("source", "UiAutomation.takeScreenshot").put("executionContext", "instrumentation-shell-copy")
                    .put("sha256", digest).put("copied", digest.matches("[0-9a-f]{64}"));
            System.out.println("FINGERPRINT_SCREENSHOT_EXPORT=" + metadata);
        } catch (Exception error) {
            System.out.println("FINGERPRINT_SCREENSHOT_EXPORT_ERROR=" + error.getClass().getName());
        }
    }
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
    private static String read(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file);ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString("UTF-8");
        }
    }
    private static void write(File file, String value) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {stream.write(value.getBytes(StandardCharsets.UTF_8));}
    }
}
