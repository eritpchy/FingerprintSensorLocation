package com.surcumference.fingerprintlocation;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import com.surcumference.fingerprintlocation.core.Geometry;
import com.surcumference.fingerprintlocation.core.Observation;
import com.surcumference.fingerprintlocation.core.Probe;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import static com.surcumference.fingerprintlocation.core.Resolution.Issue.Code.*;

final class VivoService {
    private static final String MODERN = "com.vivo.fingerprintui/.export.MessengerService";
    private static final String LEGACY = "com.vivo.udfingerprint/.service.MessengerService";
    private VivoService() {}

    static boolean supportsDevice() {
        return "vivo".equalsIgnoreCase(Build.MANUFACTURER) || "iqoo".equalsIgnoreCase(Build.MANUFACTURER);
    }

    @SuppressWarnings("deprecation")
    static void collect(Context context, Geometry.DisplayFrame frame, int sensorId, int timeoutMillis, Probe probe) {
        if (frame.displayId != 0) return;
        if (Looper.myLooper() == context.getMainLooper()) { probe.issue("vivo-service", WRONG_THREAD); return; }
        String caller = context.getPackageName();
        try {
            String[] packages = context.getPackageManager().getPackagesForUid(android.os.Process.myUid());
            if (packages == null || !Arrays.asList(packages).contains(caller)) {
                probe.issue("vivo-service-caller", ACCESS_DENIED); return;
            }
        } catch (RuntimeException error) { AndroidProviders.failure("vivo-service-caller", error, probe); return; }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        String[] endpoints = Build.VERSION.SDK_INT >= 28 ? new String[]{MODERN, LEGACY} : new String[]{LEGACY, MODERN};
        for (String endpoint : endpoints) {
            String provider = endpoint + "/query_json";
            Query query = null;
            try {
                ComponentName component = ComponentName.unflattenFromString(endpoint);
                int flags = Build.VERSION.SDK_INT >= 24 ? PackageManager.MATCH_DISABLED_COMPONENTS : PackageManager.GET_DISABLED_COMPONENTS;
                ServiceInfo info = context.getPackageManager().getServiceInfo(component, flags);
                if (!info.exported || info.applicationInfo == null || !component.getPackageName().equals(info.packageName)
                        || (info.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0) {
                    probe.issue(provider, UNTRUSTED_SOURCE); continue;
                }
                if (info.permission != null && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED) {
                    probe.issue(provider, ACCESS_DENIED); continue;
                }
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) { probe.issue(provider, TIMEOUT); break; }
                query = new Query(context, caller, endpoint.equals(MODERN) ? 1 : 0);
                Intent intent = new Intent().setComponent(component).putExtra("version_major", 1).putExtra("version_minor", query.minor);
                query.attempted = true;
                if (!context.bindService(intent, query, Context.BIND_AUTO_CREATE)) { probe.issue(provider, UNAVAILABLE); continue; }
                remaining = deadline - System.nanoTime();
                if (remaining <= 0 || !query.done.await(remaining, TimeUnit.NANOSECONDS)) { probe.issue(provider, TIMEOUT); continue; }
                if (query.error != null) { AndroidProviders.failure(provider, query.error, probe); continue; }
                if (query.reply == null) { probe.issue(provider, UNAVAILABLE); continue; }
                parse(query.reply, provider, sensorId, probe);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); probe.issue(provider, UNAVAILABLE, interrupted); break;
            } catch (Exception error) { AndroidProviders.failure(provider, error, probe); }
            catch (LinkageError unavailable) { probe.issue(provider, UNAVAILABLE, unavailable); }
            finally { if (query != null) query.close(provider, probe); }
        }
    }

    static void parse(String raw, String provider, int sensorId, Probe probe) {
        try {
            if (raw.length() > 12000) throw new IllegalArgumentException();
            JSONObject json = new JSONObject(raw);
            if (integer(json.get("version_major")) != 1) throw new IllegalArgumentException();
            int minor = integer(json.get("version_minor"));
            if (minor < 0 || minor > 1 || !(json.get("has_under_display_fingerprint") instanceof Boolean)) throw new IllegalArgumentException();
            boolean under = json.getBoolean("has_under_display_fingerprint");
            Observation.Type type = under ? Observation.Type.UNDER_DISPLAY : Observation.Type.NON_UNDER_DISPLAY;
            String evidence = provider + " v1." + minor;
            probe.add(new Observation(sensorId, "", type, Observation.Source.VIVO_SERVICE, Geometry.Space.CURRENT_NATURAL, null, evidence));
            if (!under) return;
            JSONArray rect = json.getJSONArray("icon_position");
            if (rect.length() != 4) throw new IllegalArgumentException();
            Geometry.Bounds bounds = new Geometry.Bounds(integer(rect.get(0)), integer(rect.get(1)), integer(rect.get(2)), integer(rect.get(3)));
            probe.add(new Observation(sensorId, "", type, Observation.Source.VIVO_SERVICE, Geometry.Space.CURRENT_NATURAL, bounds, evidence));
        } catch (Exception invalid) { AndroidProviders.failure(provider, invalid, probe); }
    }

    private static int integer(Object value) {
        if (!(value instanceof Integer || value instanceof Long)) throw new IllegalArgumentException();
        long result = ((Number) value).longValue();
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException();
        return (int) result;
    }

    private static final class Query implements ServiceConnection {
        final Context context;
        final String caller;
        final int minor;
        final CountDownLatch done = new CountDownLatch(1);
        final Messenger incoming;
        boolean attempted, closed, finished;
        volatile String reply;
        volatile Exception error;
        private Messenger remote;

        Query(Context context, String caller, int minor) {
            this.context = context; this.caller = caller; this.minor = minor;
            incoming = new Messenger(new Handler(context.getMainLooper(), message -> {
                if (message.what == 10000) {
                    try {
                        Bundle data = message.getData();
                        Object value = data.get("query_json");
                        if (!(value instanceof String)) throw new IllegalArgumentException();
                        finish((String) value, null);
                    } catch (RuntimeException invalid) { finish(null, invalid); }
                }
                return true;
            }));
        }
        private synchronized void finish(String value, Exception failure) {
            if (closed || finished) return;
            reply = value; error = failure; finished = true; done.countDown();
        }
        private void send(int what) throws android.os.RemoteException {
            Message message = Message.obtain(null, what);
            message.arg1 = 1; message.arg2 = minor; message.replyTo = incoming;
            Bundle data = new Bundle(); data.putString("token", caller); message.setData(data);
            remote.send(message);
        }
        @Override public synchronized void onServiceConnected(ComponentName name, IBinder service) {
            if (closed || finished) return;
            remote = new Messenger(service);
            try { send(10000); } catch (RuntimeException | android.os.RemoteException failure) { finish(null, failure); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { finish(null, new android.os.DeadObjectException()); }
        @Override public void onBindingDied(ComponentName name) { finish(null, new android.os.DeadObjectException()); }
        @Override public void onNullBinding(ComponentName name) { finish(null, null); }
        synchronized void close(String provider, Probe probe) {
            closed = true;
            if (remote != null) {
                try { send(10002); } catch (RuntimeException | android.os.RemoteException failure) { AndroidProviders.failure(provider + "/cleanup", failure, probe); }
                remote = null;
            }
            if (attempted) {
                try { context.unbindService(this); }
                catch (IllegalArgumentException notRegistered) { /* A false bind can still allocate a dispatcher. */ }
                catch (RuntimeException failure) { AndroidProviders.failure(provider + "/cleanup", failure, probe); }
            }
        }
    }
}
