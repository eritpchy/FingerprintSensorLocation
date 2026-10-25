package com.surcumference.fingerprintlocation.diagnostic;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @Test public void ordinaryAppAndExplicitSystemUiBridge() throws Exception {
        output = new File(context.getExternalFilesDir(null), "e2e/" + SystemClock.elapsedRealtime());
        assertTrue(output.mkdirs());
        try {
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
            click("Close");
            long deadline = SystemClock.elapsedRealtime() + 5000;
            while (!activity.isDestroyed() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
            assertTrue("Close must finish the diagnostic", activity.isFinishing());
            screenshot("07-closed");
            JSONObject result = new JSONObject().put("schema", 1).put("libraryVersion", BuildConfig.VERSION_NAME)
                    .put("steps", steps).put("closed", true)
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
    private JSONObject captureOrientation(String name, JSONObject previous, boolean portrait) throws Exception {
        // Large-screen policies can ignore orientation. Query again after the request settles.
        SystemClock.sleep(1200);
        JSONObject value = capture(name, click("Query"), "ordinary-app");
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
        assertEquals(BuildConfig.VERSION_NAME, value.getString("libraryVersion"));
        if ("FOUND".equals(value.getString("status"))) {
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
            assertNotNull("Missing control " + label, button);assertTrue(button.performClick());
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
        // UiAutomation supplies shell access only inside the explicitly installed test harness.
        ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(
                "dumpsys -t 5 activity service com.android.systemui/.SystemUIService");
        try (FileInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 2 * 1024 * 1024) throw new IOException("SystemUI report exceeds limit");
                bytes.write(buffer, 0, count);
            }
            return bytes.toString("UTF-8");
        }
    }
    private void screenshot(String name) throws IOException {
        if (!"07-closed".equals(name)) {
            final boolean[] focused = {false};
            instrumentation.runOnMainSync(() -> focused[0] = activity != null && activity.hasWindowFocus());
            assertTrue("Diagnostic lost foreground before screenshot", focused[0]);
        }
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("Screenshot unavailable for " + name, bitmap);
        try (FileOutputStream stream = new FileOutputStream(new File(output, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {bitmap.recycle();}
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
