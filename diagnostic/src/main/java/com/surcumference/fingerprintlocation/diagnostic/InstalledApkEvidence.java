package com.surcumference.fingerprintlocation.diagnostic;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;

final class InstalledApkEvidence {
    private static String cached;
    private InstalledApkEvidence() {}

    static synchronized JSONObject collect(Context context) throws Exception {
        if (cached != null) return new JSONObject(cached);
        ApplicationInfo info = context.getApplicationInfo();
        JSONObject value = new JSONObject().put("executionContext", "ordinary-app-self-read")
                .put("uid", android.os.Process.myUid()).put("packageName", context.getPackageName())
                .put("scope", "installed-base-apk")
                .put("splitApkCount", info.splitSourceDirs == null ? 0 : info.splitSourceDirs.length);
        try (FileInputStream input = new FileInputStream(info.sourceDir)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            long bytes = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if ((bytes += count) > 64L * 1024 * 1024) throw new IllegalArgumentException("Diagnostic APK exceeds limit");
                digest.update(buffer, 0, count);
            }
            if (bytes == 0) throw new IllegalArgumentException("Empty diagnostic APK");
            StringBuilder sha = new StringBuilder();
            for (byte b : digest.digest()) sha.append(String.format(Locale.ROOT, "%02x", b & 255));
            value.put("status", "HASHED_BASE_APK").put("sha256", sha.toString()).put("bytes", bytes);
            cached = value.toString();
        } catch (Exception error) {
            value.put("status", "UNAVAILABLE").put("exceptionType", error.getClass().getName());
        }
        return value;
    }
}
