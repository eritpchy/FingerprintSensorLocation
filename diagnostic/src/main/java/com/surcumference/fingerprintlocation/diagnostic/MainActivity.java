package com.surcumference.fingerprintlocation.diagnostic;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.surcumference.fingerprintlocation.FingerprintLocation;
import com.surcumference.fingerprintlocation.core.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Ordinary application-process diagnostic; no root or hidden-API exemptions. */
public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView report;
    private TextView mode;
    private Region region;
    private int generation;
    private boolean ifaa;
    private SystemUiDumpReader systemUiDump;
    private String json = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(245, 247, 250));
        region = new Region(this);root.addView(region);
        LinearLayout content = new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            content.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = new TextView(this);title.setText("Fingerprint Location Diagnostic");title.setTextSize(19);
        content.addView(title);
        mode = new TextView(this);
        mode.setText("Ordinary app process - no root, no authentication, no network.\nRed outline = reported region, not independent proof of the sensor position.");
        content.addView(mode);
        LinearLayout controls = new LinearLayout(this);
        button(controls, "Query", () -> query(false));
        button(controls, "Query + IFAA", () -> query(true));
        button(controls, "Copy JSON", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Fingerprint location diagnostic", json));
        });
        content.addView(controls);
        LinearLayout rotation = new LinearLayout(this);
        button(rotation, "Portrait", () -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        button(rotation, "Landscape", () -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        button(rotation, "Close", this::finish);content.addView(rotation);
        report = new TextView(this);report.setTextIsSelectable(true);report.setTextSize(12);
        ScrollView scroll = new ScrollView(this);scroll.setFillViewport(false);scroll.addView(report);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));root.addView(content);
        setContentView(root);
        applyIntent(getIntent());
        root.post(() -> query(ifaa));
    }
    @Override public void onNewIntent(Intent intent) {super.onNewIntent(intent);setIntent(intent);applyIntent(intent);query(ifaa);}
    private void applyIntent(Intent intent) {
        ifaa = intent.getBooleanExtra("ifaa", false);
        int orientation = intent.getIntExtra("orientation", -1);
        if (orientation == 0) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else if (orientation == 1) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);region.post(() -> query(ifaa));
    }
    private void button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);button.setText(label);button.setAllCaps(false);
        button.setTextSize(11);button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private void query(boolean includeIfaa) {
        if (isFinishing() || isDestroyed()) return;
        ifaa = includeIfaa;
        SystemUiDumpReader reader = systemUiDump;
        int request = ++generation;
        Display display = getWindowManager().getDefaultDisplay();
        region.bounds = null;region.invalidate();report.setText("QUERYING - IFAA=" + includeIfaa);
        main.postDelayed(() -> {
            if (request == generation && !isFinishing()) report.setText("TIMEOUT - provider did not finish within 15 seconds. This is not a geometry pass.");
        }, 15000);
        worker.execute(() -> {
            try {
                FingerprintLocation.Options.Builder options = new FingerprintLocation.Options.Builder().includeIfaa(includeIfaa);
                if (reader != null) options.systemUiDump(reader);
                Resolution result = FingerprintLocation.query(this, display, options.build());
                Geometry.DisplayFrame frame = FingerprintLocation.displayFrame(display);
                String value = encode(result, frame, includeIfaa, reader != null).toString(2);
                main.post(() -> {
                    if (request != generation || isFinishing()) return;
                    generation++;json = value;region.bounds = result.bounds;region.invalidate();report.setText(value);
                    try (FileOutputStream stream = new FileOutputStream(new File(getFilesDir(), "latest.json"))) {
                        stream.write(value.getBytes(StandardCharsets.UTF_8));
                    } catch (Exception error) {Log.e("FingerprintLocation", "Unable to save diagnostic", error);}
                    Log.i("FingerprintLocation", "FINGERPRINT_LOCATION_JSON=" + value.replace("\n", ""));
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (request == generation && !isFinishing()) {generation++;report.setText("ERROR - " + error.getClass().getSimpleName());}
                });
            }
        });
    }
    void useSystemUiDump(SystemUiDumpReader reader) {
        systemUiDump = reader;
        mode.setText(reader == null ? "Ordinary app process - no root, no authentication, no network."
                : "App process with instrumentation-owned shell report - separate privilege context.");
        query(false);
    }
    private JSONObject encode(Resolution result, Geometry.DisplayFrame frame, boolean includeIfaa, boolean bridge) throws Exception {
        JSONObject value = new JSONObject().put("schema", 1).put("executionContext", bridge
                ? "app-with-instrumentation-shell-report" : "ordinary-app")
                .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("androidRelease", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
                .put("captureElapsedRealtimeMs", android.os.SystemClock.elapsedRealtime())
                .put("libraryVersion", BuildConfig.VERSION_NAME).put("ifaa", includeIfaa).put("displayId", frame.displayId)
                .put("width", frame.logicalWidth()).put("height", frame.logicalHeight())
                .put("rotation", frame.rotation).put("status", result.status.name()).put("corroborated", result.corroborated);
        if (result.bounds != null) value.put("bounds", bounds(result.bounds)).put("source", result.selected.source.name());
        JSONArray observations = new JSONArray();
        for (Observation observation : result.observations) {
            JSONObject row = new JSONObject().put("source", observation.source.name()).put("type", observation.type.name())
                    .put("sensorId", observation.sensorId).put("space", observation.space.name())
                    .put("evidence", observation.evidence).put("frameworkPlaceholder", observation.frameworkPlaceholder);
            if (observation.bounds != null) row.put("rawBounds", bounds(observation.bounds));
            observations.put(row);
        }
        JSONArray issues = new JSONArray();
        for (Resolution.Issue issue : result.issues) issues.put(new JSONObject().put("provider", issue.provider).put("code", issue.code.name()));
        return value.put("observations", observations).put("issues", issues);
    }
    private static JSONArray bounds(Geometry.Bounds bounds) throws Exception {
        return new JSONArray().put(bounds.left).put(bounds.top).put(bounds.right).put(bounds.bottom);
    }
    @Override public void onDestroy() {generation++;worker.shutdownNow();super.onDestroy();}
    private final class Region extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Geometry.Bounds bounds;
        Region(Context context) {super(context);paint.setColor(Color.RED);paint.setStrokeWidth(4);paint.setStyle(Paint.Style.STROKE);}
        @Override public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bounds == null) return;
            int[] origin = new int[2];getLocationOnScreen(origin);
            canvas.drawRect((float) bounds.left - origin[0], (float) bounds.top - origin[1],
                    (float) bounds.right - origin[0], (float) bounds.bottom - origin[1], paint);
        }
    }
}
