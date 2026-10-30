package com.surcumference.fingerprintlocation;

import android.content.Context;
import android.os.Build;
import com.surcumference.fingerprintlocation.core.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

final class StockFirmwareGeometry {
    private StockFirmwareGeometry() {}

    static void collect(Context context, Geometry.DisplayFrame frame, int sensorId,
                        StockFirmwareGeometryProfile profile, FirmwareComponentReader reader, Probe probe) {
        if (!profile.matches(Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.ID,
                Build.DISPLAY, Build.VERSION.INCREMENTAL) || sensorId >= 0 || frame.displayId != 0
                || !context.getPackageManager().hasSystemFeature("android.hardware.fingerprint")) return;
        Resolution runtime = probe.resolve(frame, sensorId);
        if (runtime.status != Resolution.Status.UNAVAILABLE && runtime.status != Resolution.Status.ACCESS_DENIED) return;
        if (runtime.typeStatus != Resolution.TypeStatus.UNKNOWN
                && (runtime.typeStatus != Resolution.TypeStatus.KNOWN || !runtime.sensorType.underDisplay())) return;
        if (!profile.supportsFrame(frame)) return;
        try {
            Map<String, String> hashes = new LinkedHashMap<>();
            for (String path : profile.componentHashes().keySet()) hashes.put(path, hash(open(reader, path)));
            Map<String, byte[]> metadata = new LinkedHashMap<>();
            for (String path : profile.metadataPaths()) metadata.put(path, readMetadata(open(reader, path)));
            profile.collect(Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.ID, Build.DISPLAY,
                    Build.VERSION.INCREMENTAL, frame, sensorId, true, hashes, metadata, reader == null ? Observation.Source.STOCK_FIRMWARE_CONFIGURATION
                            : Observation.Source.CALLER_FIRMWARE_CONFIGURATION, probe);
        } catch (Exception error) { AndroidProviders.failure("stock-firmware-geometry", error, probe); }
    }
    private static InputStream open(FirmwareComponentReader reader, String path) throws Exception {
        return reader == null ? new FileInputStream(path) : reader.open(path);
    }
    static byte[] readMetadata(InputStream stream) throws Exception {
        if (stream == null) throw new IOException("Firmware metadata stream unavailable");
        try (InputStream input = stream) {
            byte[] bytes = new byte[4];
            for (int i = 0; i < bytes.length; i++) {
                int value = input.read();
                if (value < 0) throw new IOException("Truncated firmware metadata");
                bytes[i] = (byte) value;
            }
            if (input.read() != -1) throw new IOException("Oversized firmware metadata");
            return bytes;
        }
    }
    static String hash(InputStream stream) throws Exception {
        if (stream == null) throw new IOException("Firmware component stream unavailable");
        try (InputStream input = stream) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            int count, total = 0;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) throw new IOException("Firmware component stream made no progress");
                total += count;
                if (total > 4 * 1024 * 1024) throw new IOException("Oversized firmware component");
                digest.update(buffer, 0, count);
            }
            StringBuilder hash = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hash.append(Character.forDigit((value & 255) >>> 4, 16));
                hash.append(Character.forDigit(value & 15, 16));
            }
            return hash.toString();
        }
    }
}
