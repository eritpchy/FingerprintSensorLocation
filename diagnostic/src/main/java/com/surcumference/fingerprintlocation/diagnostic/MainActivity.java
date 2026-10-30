package com.surcumference.fingerprintlocation.diagnostic;

import android.app.Activity;
import android.app.Dialog;
import android.app.KeyguardManager;
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
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
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
    private Button copy;
    private Button qr;
    private Dialog qrDialog;
    private CheckBox specifications;
    private CloudSequence sequence;
    private boolean exitLogReadback;
    private boolean autoQr = BuildConfig.CLOUD_QR_AUTO;
    private int generation;
    private boolean ifaa;
    private boolean deviceSpecifications;
    private boolean stockFirmwareGeometry;
    private com.surcumference.fingerprintlocation.FirmwareComponentReader firmwareReader;
    private SystemUiDumpReader systemUiDump;
    private String json = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BuildConfig.CLOUD_QR_AUTO) {
            if (Build.VERSION.SDK_INT >= 27) {setShowWhenLocked(true);setTurnScreenOn(true);}
            else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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
        copy = button(controls, "Copy JSON", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Fingerprint location diagnostic", json));
        });
        content.addView(controls);
        LinearLayout rotation = new LinearLayout(this);
        button(rotation, "Portrait", () -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        button(rotation, "Landscape", () -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        button(rotation, "Close", this::finish);content.addView(rotation);
        LinearLayout export = new LinearLayout(this);
        qr = button(export, "QR JSON", this::showQr);
        content.addView(export);
        specifications = new CheckBox(this);
        specifications.setText("Use device specifications (type only)");
        if (BuildConfig.CLOUD_SPECIFICATIONS) {deviceSpecifications = true;specifications.setChecked(true);}
        specifications.setOnCheckedChangeListener((button, checked) -> { deviceSpecifications = checked;query(ifaa); });
        content.addView(specifications);
        if (!BuildConfig.CLOUD_QR_AUTO) {
            CheckBox firmware = new CheckBox(this);
            firmware.setText("Use verified stock firmware geometry");
            firmware.setOnCheckedChangeListener((button, checked) -> {stockFirmwareGeometry = checked;query(ifaa);});
            content.addView(firmware);
        }
        report = new TextView(this);report.setTextIsSelectable(true);report.setTextSize(12);
        ScrollView scroll = new ScrollView(this);scroll.setFillViewport(false);scroll.addView(report);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));root.addView(content);
        setContentView(root);
        applyIntent(getIntent());
        if (BuildConfig.CLOUD_SEQUENCE) {
            if (showPreviousSequenceExit()) return;
            sequence = new CloudSequence();
        }
        root.post(() -> {if (sequence == null) query(ifaa);else sequence.advance();});
    }
    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);setIntent(intent);
        if (!exitLogReadback) {applyIntent(intent);query(ifaa);}
    }
    private void applyIntent(Intent intent) {
        ifaa = intent.getBooleanExtra("ifaa", false);
        int orientation = intent.getIntExtra("orientation", -1);
        if (orientation == 0) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else if (orientation == 1) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (sequence == null && !exitLogReadback) region.post(() -> query(ifaa));
    }
    private boolean showPreviousSequenceExit() {
        JSONObject envelope = SequenceExitLog.read(this);
        try {
            JSONObject installed = InstalledApkEvidence.collect(this);
            if (!SequenceExitLog.unreadForCurrentApp(this, envelope, installed)) return false;
            JSONObject record = envelope.getJSONObject("record");
            Geometry.DisplayFrame frame = FingerprintLocation.displayFrame(getWindowManager().getDefaultDisplay());
            JSONObject value = new JSONObject().put("schema",1).put("executionContext","ordinary-app")
                    .put("cloudMode","sequence-exit-readback").put("callingPackage",getPackageName())
                    .put("captureElapsedRealtimeMs",android.os.SystemClock.elapsedRealtime())
                    .put("sequenceRunId",record.getString("sequenceRunId"))
                    .put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("sdk",Build.VERSION.SDK_INT)
                    .put("buildDisplay",Build.DISPLAY).put("incremental",Build.VERSION.INCREMENTAL)
                    .put("width",frame.logicalWidth()).put("height",frame.logicalHeight()).put("rotation",frame.rotation)
                    .put("uid",android.os.Process.myUid()).put("installedApk",installed).put("exitLog",envelope)
                    .put("autoCloseMs",20000).put("startsFingerprintQuery",false)
                    .put("scope","App-owned lifecycle log readback; not platform logcat or hardware calibration");
            exitLogReadback = true;
            json = value.toString(2);report.setText(json);copy.setEnabled(true);qr.setEnabled(true);
            mode.setText("Read-only previous sequence exit log - no new fingerprint query.");
            showQr(true);
            main.postDelayed(() -> {if (!isFinishing()) finish();},20000);
            if (qrDialog != null) SequenceExitLog.consumed(this,envelope);
            Log.i("FingerprintLocation","FINGERPRINT_SEQUENCE_EXIT_READBACK=" + value);
            return true;
        } catch (Exception error) {
            Log.w("FingerprintLocation","FINGERPRINT_SEQUENCE_EXIT_READBACK_FAILED=" + error.getClass().getSimpleName());
            return exitLogReadback;
        }
    }
    private Button button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);button.setText(label);button.setAllCaps(false);
        button.setTextSize(11);button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        return button;
    }
    private void query(boolean includeIfaa) {
        if (isFinishing() || isDestroyed() || exitLogReadback) return;
        if (qrDialog != null) {qrDialog.dismiss();qrDialog = null;}
        json = "";copy.setEnabled(false);qr.setEnabled(false);
        ifaa = includeIfaa;
        boolean includeSpecifications = deviceSpecifications;
        boolean includeStockFirmware = stockFirmwareGeometry;
        SystemUiDumpReader reader = systemUiDump;
        com.surcumference.fingerprintlocation.FirmwareComponentReader componentReader = firmwareReader;
        int request = ++generation;
        Display display = getWindowManager().getDefaultDisplay();
        region.bounds = null;region.invalidate();report.setText("QUERYING - IFAA=" + includeIfaa);
        main.postDelayed(() -> {
            if (request == generation && !isFinishing()) {
                report.setText("TIMEOUT - provider did not finish within 15 seconds. This is not a geometry pass.");
                if (sequence != null) {generation++;sequence.fail("Query timeout");}
            }
        }, 15000);
        worker.execute(() -> {
            try {
                FingerprintLocation.Options.Builder options = new FingerprintLocation.Options.Builder()
                        .includeIfaa(includeIfaa).includeDeviceSpecifications(includeSpecifications);
                if (includeStockFirmware) {
                    StockFirmwareGeometryProfile profile = StockFirmwareGeometryProfile.NOTHING_A069_B4_1_260808_1352;
                    for (StockFirmwareGeometryProfile candidate : StockFirmwareGeometryProfile.values())
                        if (candidate.matches(Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.ID,
                                Build.DISPLAY, Build.VERSION.INCREMENTAL)) {profile = candidate;break;}
                    options.stockFirmwareGeometry(profile);
                }
                if (componentReader != null) options.stockFirmwareComponentReader(componentReader);
                if (reader != null) options.systemUiDump(reader);
                Resolution result = FingerprintLocation.query(this, display, options.build());
                Geometry.DisplayFrame frame = FingerprintLocation.displayFrame(display);
                JSONObject encoded = encode(result, frame, includeIfaa, reader != null, includeSpecifications);
                encoded.put("stockFirmwareGeometry", includeStockFirmware);
                encoded.put("firmwareComponentReader", componentReader != null);
                if (includeStockFirmware && componentReader != null)
                    encoded.put("executionContext", "app-with-instrumentation-shell-firmware");
                SamsungCoordinateEvidence.append(encoded, display);
                GoogleCoordinateEvidence.append(encoded);
                encoded.put("installedApk", InstalledApkEvidence.collect(this));
                String value = encoded.toString(2), compact = encoded.toString();
                main.post(() -> {
                    if (request != generation || isFinishing()) return;
                    generation++;json = value;copy.setEnabled(true);qr.setEnabled(true);
                    region.bounds = result.bounds;region.invalidate();report.setText(value);
                    try (FileOutputStream stream = new FileOutputStream(new File(getFilesDir(), "latest.json"))) {
                        stream.write(value.getBytes(StandardCharsets.UTF_8));
                    } catch (Exception error) {Log.e("FingerprintLocation", "Unable to save diagnostic", error);}
                    Log.i("FingerprintLocation", "FINGERPRINT_LOCATION_JSON=" + compact);
                    if (sequence != null) sequence.result(encoded);
                    else if (autoQr) {
                        autoQr = false;showQr();
                        main.postDelayed(this::finish,120000);
                    }
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (request == generation && !isFinishing()) {
                        generation++;report.setText("ERROR - " + error.getClass().getSimpleName());
                        if (sequence != null) sequence.fail("Query error: " + error.getClass().getSimpleName());
                    }
                });
            }
        });
    }
    private void showQr() {
        showQr(false);
    }
    private void showQr(boolean finishOnDismiss) {
        if (json.isEmpty()) return;
        try {
            JSONObject value = new JSONObject(json);
            QrExport image = new QrExport(this, value.toString());
            Dialog dialog = new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            if (BuildConfig.CLOUD_SEQUENCE) image.afterDraw(() -> {
                if (qrDialog != dialog || !dialog.isShowing() || isFinishing()) return;
                try {
                    JSONObject drawn = new JSONObject().put("sequenceRunId",value.getString("sequenceRunId"))
                            .put("captureElapsedRealtimeMs",value.getLong("captureElapsedRealtimeMs"))
                            .put("sequenceStage",value.optString("sequenceStage","summary"))
                            .put("drawEvidence","app-onDraw-followed-by-two-animation-callbacks");
                    if (value.has("sequenceStage")) drawn.put("width",value.getInt("width"))
                            .put("height",value.getInt("height")).put("ifaa",value.getBoolean("ifaa"))
                            .put("deviceSpecifications",value.getBoolean("deviceSpecifications"));
                    Log.i("FingerprintLocation","FINGERPRINT_SEQUENCE_DRAWN=" + drawn);
                    if (sequence != null) sequence.drawn(value);
                } catch (Exception error) {Log.e("FingerprintLocation","Unable to report QR draw",error);}
            });
            LinearLayout content = new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
            content.setBackgroundColor(Color.WHITE);
            TextView title = new TextView(this);
            title.setText((BuildConfig.CLOUD_SEQUENCE ? "Cloud sequence JSON - " : BuildConfig.CLOUD_QR_AUTO ? "Cloud snapshot JSON (120s) - " : "Offline complete JSON - ")
                    + value.getLong("captureElapsedRealtimeMs"));
            title.setTextColor(Color.BLACK);title.setPadding(12,12,12,12);content.addView(title);
            content.addView(image, new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout close = new LinearLayout(this);button(close,"Close QR",dialog::dismiss);content.addView(close);
            dialog.setContentView(content);qrDialog = dialog;
            if (finishOnDismiss) dialog.setOnDismissListener(ignored -> {
                if (sequence != null) sequence.exitReason = "summary-dismissed";
                finish();
            });
            if (BuildConfig.CLOUD_QR_AUTO) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            dialog.show();
            dialog.getWindow().setLayout(-1,-1);
        } catch (Exception error) {
            new android.app.AlertDialog.Builder(this).setMessage("QR export unavailable - use Copy JSON.")
                    .setPositiveButton("OK",null).show();
        }
    }
    private final class CloudSequence {
        final String runId = java.util.UUID.randomUUID().toString();
        final String[] names = {"default", "specifications-on", "landscape", "portrait-restored",
                "ifaa", "default-with-specifications", "specifications-off"};
        final JSONArray records = new JSONArray();
        final SequenceOrientation orientation = new SequenceOrientation();
        String exitReason = "not-requested";
        int stage = -1;
        long summaryCaptureId;
        long awaitingDrawCaptureId;
        final JSONArray drawFailures = new JSONArray();
        boolean done;
        void advance() {
            if (done || isFinishing()) return;
            stage++;
            if (stage == names.length) {complete("");return;}
            if (qrDialog != null) {qrDialog.dismiss();qrDialog = null;}
            mode.setText("Automatic ordinary-app query sequence - " + names[stage]);
            setRequestedOrientation(stage == 2 ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            awaitFrame(0);
        }
        void awaitFrame(int attempts) {
            if (done || isFinishing()) return;
            Geometry.DisplayFrame frame = FingerprintLocation.displayFrame(getWindowManager().getDefaultDisplay());
            if (orientation.await(stage == 2, frame.logicalWidth(), frame.logicalHeight(), attempts)) {
                main.postDelayed(() -> awaitFrame(attempts + 1),250);return;
            }
            ifaa = stage == 4;
            boolean enabled = stage >= 1 && stage <= 5;
            if (specifications.isChecked() != enabled) specifications.setChecked(enabled);
            else query(ifaa);
        }
        void result(JSONObject value) {
            if (done) return;
            try {
                if (value.getBoolean("ifaa") != (stage == 4)
                        || value.getBoolean("deviceSpecifications") != (stage >= 1 && stage <= 5))
                    throw new IllegalStateException("Stage flags mismatch");
                value.put("requestedOrientation",stage == 2 ? "landscape" : "portrait")
                        .put("orientationSatisfied",orientation.record(names[stage],stage == 2,
                                value.getInt("width"),value.getInt("height")));
                if (records.length() > 0 && value.getLong("captureElapsedRealtimeMs")
                        <= records.getJSONObject(records.length()-1).getLong("captureElapsedRealtimeMs"))
                    throw new IllegalStateException("Stale query capture");
                value.put("sequenceStage",names[stage]).put("sequenceRunId",runId);records.put(value);
                long captureId = value.getLong("captureElapsedRealtimeMs");
                awaitingDrawCaptureId = captureId;
                json = value.toString(2);report.setText(json);showQr();
                main.postDelayed(() -> {
                    if (!done && awaitingDrawCaptureId == captureId) {
                        drawFailures.put(names[stage]);awaitingDrawCaptureId = 0;advance();
                    }
                },15000);
            } catch (Exception error) {fail(error.getMessage());}
        }
        void drawn(JSONObject value) throws Exception {
            if (done || awaitingDrawCaptureId != value.getLong("captureElapsedRealtimeMs")) return;
            awaitingDrawCaptureId = 0;
            main.postDelayed(this::advance,6000);
        }
        void fail(String reason) {if (!done) complete(reason);}
        void complete(String failure) {
            done = true;
            summaryCaptureId = android.os.SystemClock.elapsedRealtime();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            if (qrDialog != null) {qrDialog.dismiss();qrDialog = null;}
            try {
                JSONObject summary = new JSONObject().put("schema",1).put("executionContext","ordinary-app")
                        .put("captureElapsedRealtimeMs",summaryCaptureId)
                        .put("cloudMode","sequence").put("callingPackage",getPackageName())
                        .put("sequenceRunId",runId)
                        .put("workflowStatus",!failure.isEmpty() ? "FAILED"
                                : orientation.failures().isEmpty() ? "COMPLETE" : "PARTIAL_ORIENTATION")
                        .put("orientationFailures",new JSONArray(orientation.failures()))
                        .put("drawFailures",drawFailures)
                        .put("failure",failure).put("records",records).put("autoCloseMs",30000)
                        .put("interaction","Automatic app controls; no cloud touch forwarding or authentication");
                json = summary.toString(2);report.setText(json);copy.setEnabled(true);qr.setEnabled(true);
                try (FileOutputStream stream = new FileOutputStream(new File(getFilesDir(),"sequence.json"))) {
                    stream.write(json.getBytes(StandardCharsets.UTF_8));
                }
                Log.i("FingerprintLocation","FINGERPRINT_SEQUENCE_JSON=" + summary);
                Log.i("FingerprintLocation","FINGERPRINT_SEQUENCE_PAYLOAD=" + QrExport.payload(summary.toString()));
                showQr(true);
            } catch (Exception error) {report.setText("Sequence export failed: " + error.getClass().getSimpleName());}
            main.postDelayed(() -> {
                if (!isFinishing()) {exitReason = "summary-timeout";finish();}
            },30000);
        }
    }
    void useFirmwareReader(com.surcumference.fingerprintlocation.FirmwareComponentReader reader) {
        firmwareReader = reader;
        mode.setText(reader == null ? "Ordinary app process - no root, no authentication, no network."
                : "App process with instrumentation-owned firmware stream - separate privilege context.");
        query(false);
    }
    void useSystemUiDump(SystemUiDumpReader reader) {
        systemUiDump = reader;
        mode.setText(reader == null ? "Ordinary app process - no root, no authentication, no network."
                : "App process with instrumentation-owned shell report - separate privilege context.");
        query(false);
    }
    private JSONObject encode(Resolution result, Geometry.DisplayFrame frame, boolean includeIfaa, boolean bridge, boolean specifications) throws Exception {
        JSONObject value = new JSONObject().put("schema", 1).put("executionContext", bridge
                ? "app-with-instrumentation-shell-report" : "ordinary-app")
                .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("buildId", Build.ID).put("buildDisplay", Build.DISPLAY).put("incremental", Build.VERSION.INCREMENTAL)
                .put("androidRelease", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
                .put("captureElapsedRealtimeMs", android.os.SystemClock.elapsedRealtime())
                .put("libraryVersion", BuildConfig.VERSION_NAME).put("ifaa", includeIfaa)
                .put("deviceSpecifications", specifications).put("displayId", frame.displayId)
                .put("width", frame.logicalWidth()).put("height", frame.logicalHeight())
                .put("rotation", frame.rotation).put("status", result.status.name()).put("corroborated", result.corroborated);
        JSONArray features = new JSONArray();
        android.content.pm.FeatureInfo[] available = getPackageManager().getSystemAvailableFeatures();
        if (available != null) for (android.content.pm.FeatureInfo feature : available) {
            if (feature.name == null) continue;
            String name = feature.name.toLowerCase(java.util.Locale.ROOT);
            if (name.contains("fingerprint") || name.contains("biometric") || name.contains("udfps") || name.contains("fod"))
                features.put(feature.name);
        }
        value.put("declaredFeatures", features);
        value.put("sensorType", result.sensorType.name()).put("typeStatus", result.typeStatus.name());
        JSONArray typeEvidence = new JSONArray();
        for (Observation evidence : result.typeEvidence)
            typeEvidence.put(new JSONObject().put("source", evidence.source.name()).put("type", evidence.type.name())
                    .put("sensorId", evidence.sensorId).put("display", evidence.displayId).put("evidence", evidence.evidence));
        value.put("typeEvidence", typeEvidence);
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
        for (Resolution.Issue issue : result.issues) issues.put(new JSONObject().put("provider", issue.provider).put("code", issue.code.name()).put("exceptionType", issue.exceptionType));
        value.put("observations", observations).put("issues", issues);
        if (BuildConfig.CLOUD_QR_AUTO) {
            KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            value.put("presentation", "cloud-qr-over-keyguard")
                    .put("cloudMode", BuildConfig.CLOUD_SEQUENCE ? "sequence" : BuildConfig.CLOUD_SPECIFICATIONS ? "specifications" : "runtime")
                    .put("callingPackage",getPackageName())
                    .put(BuildConfig.CLOUD_SEQUENCE ? "stageDisplayMs" : "autoCloseMs",BuildConfig.CLOUD_SEQUENCE ? 6000 : 120000)
                    .put("keyguardLocked", keyguard != null && keyguard.isKeyguardLocked());
        }
        return value;
    }
    private static JSONArray bounds(Geometry.Bounds bounds) throws Exception {
        return new JSONArray().put(bounds.left).put(bounds.top).put(bounds.right).put(bounds.bottom);
    }
    @Override public void onDestroy() {
        if (qrDialog != null) {qrDialog.setOnDismissListener(null);qrDialog.dismiss();}
        main.removeCallbacksAndMessages(null);generation++;worker.shutdownNow();
        if (sequence != null && sequence.done && sequence.summaryCaptureId > 0) {
            String line = "FINGERPRINT_SEQUENCE_EXIT=" + sequence.runId + ":"
                    + sequence.summaryCaptureId + ":finishing=" + isFinishing()
                    + ":workerShutdownRequested=" + worker.isShutdown() + ":exitReason=" + sequence.exitReason;
            Log.i("FingerprintLocation",line);
            try {
                JSONObject first = sequence.records.optJSONObject(0);
                JSONObject installed = first == null ? null : first.optJSONObject("installedApk");
                SequenceExitLog.write(this,new JSONObject().put("schema",1).put("sequenceRunId",sequence.runId)
                        .put("summaryCaptureElapsedRealtimeMs",sequence.summaryCaptureId)
                        .put("exitElapsedRealtimeMs",android.os.SystemClock.elapsedRealtime())
                        .put("finishing",isFinishing()).put("workerShutdownRequested",worker.isShutdown())
                        .put("exitReason",sequence.exitReason).put("logLine",line)
                        .put("apkSha256",installed == null ? "" : installed.optString("sha256"))
                        .put("callingPackage",getPackageName()).put("uid",android.os.Process.myUid())
                        .put("model",Build.MODEL).put("buildDisplay",Build.DISPLAY)
                        .put("incremental",Build.VERSION.INCREMENTAL));
            } catch (Exception error) {
                Log.w("FingerprintLocation","FINGERPRINT_SEQUENCE_EXIT_LOG_WRITE_FAILED=" + error.getClass().getSimpleName());
            }
        }
        super.onDestroy();
    }
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
