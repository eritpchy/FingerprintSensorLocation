package com.surcumference.fingerprintlocation.diagnostic;

import android.os.Build;
import android.system.ErrnoException;
import android.system.OsConstants;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

final class GoogleCoordinateEvidence {
    private static final String CDT = "/sys/firmware/devicetree/base/chosen/plat/";
    private static JSONObject cached;

    private GoogleCoordinateEvidence() {}

    static synchronized void append(JSONObject report) throws Exception {
        if (!"Google".equalsIgnoreCase(Build.MANUFACTURER) || !"husky".equals(Build.DEVICE)) return;
        if (cached == null) {
            JSONArray files = new JSONArray();
            for (String name : new String[]{"platform", "product", "stage", "major", "minor", "variant"})
                files.put(read(CDT + name, true));
            files.put(read("/sys/devices/platform/exynos-drm/primary-panel/panel_name", false));
            files.put(hash("/vendor/apex/com.google.android.hardware.biometrics.fingerprint.apex"));
            files.put(hash("/vendor/lib64/libgf_hal.so"));
            files.put(hash("/apex/com.google.android.hardware.biometrics.fingerprint/lib64/libgf_hal.so"));
            files.put(hash("/apex/com.google.android.hardware.biometrics.fingerprint/lib64/fp_utils.so"));
            files.put(hash("/apex/com.google.android.hardware.biometrics.fingerprint/bin/hw/android.hardware.biometrics.fingerprint-service.goodix"));
            cached = new JSONObject().put("executionContext", "ordinary-app-file-read")
                    .put("uid", android.os.Process.myUid()).put("device", Build.DEVICE)
                    .put("scope", "read-only-firmware-identity-and-panel-metadata")
                    .put("files", files).put("providesCoordinates", false);
        }
        report.put("googleCoordinateFiles", new JSONObject(cached.toString()));
    }

    private static JSONObject read(String path, boolean integer) throws Exception {
        JSONObject file = new JSONObject().put("path", path);
        try (FileInputStream input = new FileInputStream(path)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int value;
            while ((value = input.read()) != -1) {
                if (bytes.size() == 512) throw new IllegalArgumentException("Oversized panel metadata");
                bytes.write(value);
            }
            byte[] raw = bytes.toByteArray();
            file.put("bytes", raw.length).put("rawHex", hex(raw));
            if (integer) {
                if (raw.length != 4) throw new IllegalArgumentException("CDT value must have four bytes");
                long number = 0;
                for (byte part : raw) number = (number << 8) | (part & 255L);
                file.put("encoding", "uint32-big-endian").put("value", number);
            } else file.put("rawAscii", new String(raw, StandardCharsets.US_ASCII));
            file.put("status", "READABLE");
        } catch (Exception error) { failure(file, error); }
        return file;
    }

    private static JSONObject hash(String path) throws Exception {
        JSONObject file = new JSONObject().put("path", path).put("operation", "sha256-read-only");
        try (FileInputStream input = new FileInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            long bytes = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                bytes += count;
                if (bytes > 64 * 1024 * 1024) throw new IllegalArgumentException("Oversized fingerprint component");
                digest.update(buffer, 0, count);
            }
            if (bytes == 0) throw new IllegalArgumentException("Empty fingerprint component");
            file.put("status", "HASHED").put("bytes", bytes).put("sha256", hex(digest.digest()));
        } catch (Exception error) { failure(file, error); }
        return file;
    }

    private static void failure(JSONObject file, Exception error) throws Exception {
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

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray(), result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[2 * i] = digits[(bytes[i] & 255) >>> 4];
            result[2 * i + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }
}
