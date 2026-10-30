package com.surcumference.fingerprintlocation.diagnostic;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.AtomicFile;
import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

/** App-owned lifecycle log; separate from platform logcat and hardware calibration. */
final class SequenceExitLog {
    private static final String NAME = "sequence-exit.json";
    private SequenceExitLog() {}
    static boolean write(Context context, JSONObject record) {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), NAME));
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(record.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            return true;
        } catch (Exception error) {
            if (output != null) file.failWrite(output);
            Log.w("FingerprintLocation", "FINGERPRINT_SEQUENCE_EXIT_LOG_WRITE_FAILED=" + error.getClass().getSimpleName());
            return false;
        }
    }
    static JSONObject read(Context context) {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), NAME));
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] bytes = new byte[4096];int count;
            while ((count = input.read(bytes)) != -1) {
                if (count == 0 || output.size() + count > 65536) throw new IllegalArgumentException("Oversized exit log");
                output.write(bytes, 0, count);
            }
            byte[] raw = output.toByteArray();
            JSONObject record = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            if (record.getInt("schema") != 1) throw new IllegalArgumentException("Unknown exit log schema");
            StringBuilder sha = new StringBuilder(64);
            for (byte b : MessageDigest.getInstance("SHA-256").digest(raw)) {
                sha.append(Character.forDigit((b & 255) >>> 4,16));sha.append(Character.forDigit(b & 15,16));
            }
            return new JSONObject().put("source", "ordinary-app-private-lifecycle-log")
                    .put("file", NAME).put("rawBase64", Base64.encodeToString(raw, Base64.NO_WRAP))
                    .put("rawSha256", sha.toString()).put("bytes", raw.length).put("record", record);
        } catch (Exception error) {return null;}
    }
    static boolean unreadForCurrentApp(Context context, JSONObject envelope, JSONObject installed) {
        if (envelope == null) return false;
        try {
            JSONObject r = envelope.getJSONObject("record");
            return r.getBoolean("finishing") && r.getBoolean("workerShutdownRequested")
                    && r.getLong("summaryCaptureElapsedRealtimeMs") > 0
                    && r.getLong("exitElapsedRealtimeMs") >= r.getLong("summaryCaptureElapsedRealtimeMs")
                    && r.getInt("uid") == Process.myUid() && context.getPackageName().equals(r.getString("callingPackage"))
                    && Build.MODEL.equals(r.getString("model")) && Build.DISPLAY.equals(r.getString("buildDisplay"))
                    && Build.VERSION.INCREMENTAL.equals(r.getString("incremental"))
                    && "HASHED_BASE_APK".equals(installed.optString("status"))
                    && r.getString("apkSha256").equals(installed.getString("sha256"))
                    && !envelope.getString("rawSha256").equals(context.getSharedPreferences("sequence-exit-reader",0)
                            .getString("consumedSha256", ""));
        } catch (Exception invalid) {return false;}
    }
    static void consumed(Context context, JSONObject envelope) throws Exception {
        if (!context.getSharedPreferences("sequence-exit-reader",0).edit()
                .putString("consumedSha256", envelope.getString("rawSha256")).commit())
            throw new IllegalStateException("Exit log consumption was not persisted");
    }
}
