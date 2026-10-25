package io.github.eritpchy.fingerprintlocation;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import io.github.eritpchy.fingerprintlocation.core.*;
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
        public final boolean includeIfaa;
        public final PropertyReader properties;
        public final List<Provider> providers;
        private Options(Builder builder) {
            sensorId = builder.sensorId; includeIfaa = builder.includeIfaa; properties = builder.properties;
            providers = Collections.unmodifiableList(new ArrayList<>(builder.providers));
        }
        public static final class Builder {
            private int sensorId = -1;
            private boolean includeIfaa;
            private PropertyReader properties = new AndroidProperties();
            private final List<Provider> providers = new ArrayList<>();
            public Builder sensorId(int id) { if (id < -1) throw new IllegalArgumentException(); sensorId = id; return this; }
            public Builder includeIfaa(boolean value) { includeIfaa = value; return this; }
            public Builder properties(PropertyReader reader) { properties = java.util.Objects.requireNonNull(reader); return this; }
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
            AndroidProviders.resources(scoped, frame, boundId, probe);
            AndroidProviders.oem(scoped, frame, boundId, probe);
            if (options.includeIfaa) AndroidProviders.ifaa(scoped, frame, boundId, probe);
        }
        for (Provider provider : options.providers) {
            try { provider.collect(scoped, frame, options.sensorId, probe); }
            catch (Exception error) { AndroidProviders.failure("caller-provider", error, probe); }
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
