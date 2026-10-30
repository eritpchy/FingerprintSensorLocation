package com.surcumference.fingerprintlocation;

import android.content.Context;
import android.os.Build;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import com.surcumference.fingerprintlocation.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class SamsungPosition {
    static final String PATH = "/sys/class/fingerprint/fingerprint/position";
    private SamsungPosition() {}

    static void collect(Context context, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        if (!"samsung".equalsIgnoreCase(Build.MANUFACTURER) || frame.displayId != 0
                || !context.getPackageManager().hasSystemFeature("android.hardware.fingerprint")) return;
        Resolution type = probe.resolve(frame, sensorId);
        if (type.typeStatus != Resolution.TypeStatus.KNOWN || !type.sensorType.underDisplay()
                || type.bounds != null) return;
        try (InputStream input = new FileInputStream(PATH)) {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windows == null) throw new IllegalArgumentException("Missing display");
            windows.getDefaultDisplay().getRealMetrics(metrics);
            Geometry.Bounds bounds = SamsungSensorPosition.parse(read(input), frame, metrics.xdpi / 25.4);
            probe.add(new Observation(sensorId, "", type.sensorType, Observation.Source.SAMSUNG_SYSFS,
                    Geometry.Space.CURRENT_NATURAL, bounds, PATH + ":millimetres/physical-area"));
        } catch (Exception error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof ErrnoException && (((ErrnoException) cause).errno == OsConstants.EACCES
                        || ((ErrnoException) cause).errno == OsConstants.EPERM)) {
                    probe.issue("samsung-position", Resolution.Issue.Code.ACCESS_DENIED, error);
                    return;
                }
            }
            AndroidProviders.failure("samsung-position", error, probe);
        }
    }

    static String read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (bytes.size() >= 512) throw new IllegalArgumentException("Oversized Samsung position");
            bytes.write(value);
        }
        return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
    }
}
