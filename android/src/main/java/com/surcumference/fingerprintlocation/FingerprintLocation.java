package com.surcumference.fingerprintlocation;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import com.surcumference.fingerprintlocation.core.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Synchronous, read-only query. Call on a worker thread and query again after display changes. */
public final class FingerprintLocation {
    private FingerprintLocation() {}
    public interface Provider {
        void collect(Context context, Geometry.DisplayFrame display, int sensorId, Probe probe) throws Exception;
    }
    public static final class Options {
        public final int sensorId;
        public final boolean includeIfaa, includeVivoService, includeDeviceSpecifications;
        public final int vivoServiceTimeoutMillis;
        public final PropertyReader properties;
        public final SystemUiDumpReader systemUiDump;
        public final CommunityGeometryProfile communityGeometry;
        public final StockFirmwareGeometryProfile stockFirmwareGeometry;
        public final FirmwareComponentReader stockFirmwareComponentReader;
        public final List<Provider> providers;
        private Options(Builder builder) {
            sensorId = builder.sensorId; includeIfaa = builder.includeIfaa; properties = builder.properties;
            systemUiDump = builder.systemUiDump;
            communityGeometry = builder.communityGeometry;
            stockFirmwareGeometry = builder.stockFirmwareGeometry;
            stockFirmwareComponentReader = builder.stockFirmwareComponentReader;
            includeVivoService = builder.includeVivoService; vivoServiceTimeoutMillis = builder.vivoServiceTimeoutMillis;
            includeDeviceSpecifications = builder.includeDeviceSpecifications;
            providers = Collections.unmodifiableList(new ArrayList<>(builder.providers));
        }
        public static final class Builder {
            private int sensorId = -1;
            private boolean includeIfaa;
            private boolean includeVivoService = true;
            private boolean includeDeviceSpecifications;
            private int vivoServiceTimeoutMillis = 2000;
            private PropertyReader properties = new AndroidProperties();
            private SystemUiDumpReader systemUiDump;
            private CommunityGeometryProfile communityGeometry;
            private StockFirmwareGeometryProfile stockFirmwareGeometry;
            private FirmwareComponentReader stockFirmwareComponentReader;
            private final List<Provider> providers = new ArrayList<>();
            public Builder sensorId(int id) { if (id < -1) throw new IllegalArgumentException(); sensorId = id; return this; }
            public Builder includeIfaa(boolean value) { includeIfaa = value; return this; }
            public Builder includeVivoService(boolean value) { includeVivoService = value; return this; }
            public Builder includeDeviceSpecifications(boolean value) { includeDeviceSpecifications = value; return this; }
            /** Select only after reviewing the named ROM report; disabled by default. */
            public Builder communityGeometry(CommunityGeometryProfile profile) {
                communityGeometry = java.util.Objects.requireNonNull(profile); return this;
            }
            public Builder stockFirmwareGeometry(StockFirmwareGeometryProfile profile) {
                stockFirmwareGeometry = java.util.Objects.requireNonNull(profile); return this;
            }
            /** Used only with an explicitly selected stock profile; results retain caller provenance. */
            public Builder stockFirmwareComponentReader(FirmwareComponentReader reader) {
                stockFirmwareComponentReader = java.util.Objects.requireNonNull(reader); return this;
            }
            public Builder vivoServiceTimeoutMillis(int value) {
                if (value < 100 || value > 5000) throw new IllegalArgumentException("Vivo service timeout must be 100-5000 ms");
                vivoServiceTimeoutMillis = value; return this;
            }
            public Builder properties(PropertyReader reader) { properties = java.util.Objects.requireNonNull(reader); return this; }
            public Builder systemUiDump(SystemUiDumpReader reader) { systemUiDump = java.util.Objects.requireNonNull(reader); return this; }
            public Builder provider(Provider provider) { providers.add(java.util.Objects.requireNonNull(provider)); return this; }
            public Options build() { return new Options(this); }
        }
    }
    public static Resolution query(Context context, Display display) {
        return query(context, display, new Options.Builder().build());
    }
    public static Resolution query(Context context, Display display, Options options) {
        if (context == null || display == null || options == null) throw new NullPointerException();
        Probe probe = new Probe();
        Geometry.DisplayFrame frame;
        Context scoped;
        try { frame = displayFrame(display); scoped = context.createDisplayContext(display); }
        catch (RuntimeException unavailable) { AndroidProviders.failure("display", unavailable, probe); return probe.unavailable(); }
        AndroidProviders.State state = AndroidProviders.framework(scoped, frame, options.sensorId, probe);
        int boundId = state.ids.size() == 1 ? state.ids.iterator().next() : -1;
        if (state.ids.size() <= 1) {
            VendorProperties.collect(options.properties, frame, boundId, state.type, probe);
            VendorProperties.collectTranssion(options.properties, android.os.Build.MANUFACTURER, frame, boundId, probe);
            AndroidProviders.resources(scoped, frame, boundId, probe);
            AndroidProviders.oem(scoped, frame, boundId, probe);
            SamsungSdk.collect(scoped, frame, boundId, probe);
            SamsungPosition.collect(scoped, frame, boundId, probe);
            if (options.includeVivoService && VivoService.supportsDevice())
                VivoService.collect(scoped, frame, boundId, options.vivoServiceTimeoutMillis, probe);
            if (options.includeIfaa) AndroidProviders.ifaa(scoped, frame, boundId, probe);
            if (options.systemUiDump != null) SystemUiReport.collect(options.systemUiDump, frame, probe);
        }
        for (Provider provider : options.providers) {
            try { provider.collect(scoped, frame, options.sensorId, probe); }
            catch (Exception error) { AndroidProviders.failure("caller-provider", error, probe); }
        }
        if (options.stockFirmwareGeometry != null) {
            StockFirmwareGeometry.collect(scoped, frame, options.sensorId, options.stockFirmwareGeometry, options.stockFirmwareComponentReader, probe);
        }
        if (options.communityGeometry != null) {
            try {
                options.communityGeometry.collect(android.os.Build.MANUFACTURER, android.os.Build.MODEL, frame,
                        options.sensorId, scoped.getPackageManager().hasSystemFeature("android.hardware.fingerprint"), probe);
            } catch (RuntimeException unavailable) { AndroidProviders.failure("community-geometry", unavailable, probe); }
        }
        if (options.includeDeviceSpecifications) {
            try {
                DeviceSpecifications.collect(android.os.Build.MANUFACTURER, android.os.Build.MODEL, frame,
                        options.sensorId, scoped.getPackageManager().hasSystemFeature("android.hardware.fingerprint"), probe);
            } catch (RuntimeException unavailable) { AndroidProviders.failure("device-specifications", unavailable, probe); }
        }
        try {
            if (!frame.sameConfiguration(displayFrame(display))) {
                probe.issue("display", Resolution.Issue.Code.DISPLAY_CHANGED); return probe.unavailable();
            }
        } catch (RuntimeException unavailable) { AndroidProviders.failure("display", unavailable, probe); return probe.unavailable(); }
        return probe.resolve(frame, options.sensorId);
    }
    @SuppressWarnings("deprecation")
    public static Geometry.DisplayFrame displayFrame(Display display) {
        Point size = new Point(); display.getRealSize(size);
        int rotation = display.getRotation(); boolean sideways = rotation % 2 != 0;
        int naturalWidth = sideways ? size.y : size.x, naturalHeight = sideways ? size.x : size.y;
        Display.Mode nativeMode = display.getMode();
        for (Display.Mode mode : display.getSupportedModes())
            if ((long) mode.getPhysicalWidth() * mode.getPhysicalHeight()
                    > (long) nativeMode.getPhysicalWidth() * nativeMode.getPhysicalHeight()) nativeMode = mode;
        String uniqueId;
        try { uniqueId = (String) Reflect.call(display, "getUniqueId"); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { uniqueId = ""; }
        return new Geometry.DisplayFrame(uniqueId == null ? "" : uniqueId, display.getDisplayId(), naturalWidth,
                naturalHeight, nativeMode.getPhysicalWidth(), nativeMode.getPhysicalHeight(), rotation);
    }
    private static final class AndroidProperties implements PropertyReader {
        @Override public String get(String name) throws Exception {
            return (String) Reflect.callStatic("android.os.SystemProperties", "get", new Class<?>[]{String.class}, name);
        }
    }
}
