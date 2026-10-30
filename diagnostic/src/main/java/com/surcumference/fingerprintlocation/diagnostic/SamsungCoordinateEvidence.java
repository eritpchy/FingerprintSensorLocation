package com.surcumference.fingerprintlocation.diagnostic;

import android.os.Build;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.DisplayMetrics;
import android.view.Display;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class SamsungCoordinateEvidence {
    private SamsungCoordinateEvidence() {}

    static void append(JSONObject report, Display display) throws Exception {
        JSONObject metrics = new JSONObject().put("source", "Display.getRealMetrics");
        try {
            DisplayMetrics real = new DisplayMetrics();
            display.getRealMetrics(real);
            metrics.put("widthPixels", real.widthPixels).put("heightPixels", real.heightPixels)
                    .put("densityDpi", real.densityDpi)
                    .put("xdpi", finite(real.xdpi)).put("ydpi", finite(real.ydpi))
                    .put("pixelsPerMillimetreX", finite(real.xdpi / 25.4));
        } catch (RuntimeException error) { metrics.put("exceptionType", error.getClass().getName()); }
        report.put("displayMetrics", metrics);
        if (!"samsung".equalsIgnoreCase(Build.MANUFACTURER)) return;
        JSONObject file = new JSONObject().put("executionContext", "ordinary-app-file-read")
                .put("uid", android.os.Process.myUid())
                .put("path", "/sys/class/fingerprint/fingerprint/position");
        try (FileInputStream input = new FileInputStream(file.getString("path"))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int next;
            while ((next = input.read()) != -1) {
                if (bytes.size() == 512) throw new IllegalArgumentException("Oversized position metadata");
                bytes.write(next);
            }
            file.put("status", "READABLE").put("raw", new String(bytes.toByteArray(), StandardCharsets.US_ASCII));
        } catch (Exception error) {
            String status = error instanceof SecurityException ? "ACCESS_DENIED"
                    : error instanceof IllegalArgumentException ? "INVALID_DATA" : "UNAVAILABLE";
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof ErrnoException) {
                    int errno = ((ErrnoException) cause).errno;
                    file.put("errno", errno);
                    if (errno == OsConstants.EACCES || errno == OsConstants.EPERM) status = "ACCESS_DENIED";
                    else if (errno == OsConstants.ENOENT) status = "NOT_PRESENT";
                }
            }
            file.put("status", status).put("exceptionType", error.getClass().getName());
        }
        report.put("samsungPositionFile", file);
    }

    private static Object finite(double number) {
        return Double.isNaN(number) || Double.isInfinite(number) ? JSONObject.NULL : number;
    }
}
