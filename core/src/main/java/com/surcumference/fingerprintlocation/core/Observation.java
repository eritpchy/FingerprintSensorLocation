package com.surcumference.fingerprintlocation.core;

import java.util.Objects;

public final class Observation {
    public enum Type { OPTICAL, ULTRASONIC, UNDER_DISPLAY, NON_UNDER_DISPLAY, SIDE, REAR, HOME, UNKNOWN, FRONT;
        public boolean underDisplay() { return this == OPTICAL || this == ULTRASONIC || this == UNDER_DISPLAY; }
        boolean refines(Type other) {
            return other == UNDER_DISPLAY && underDisplay()
                    || other == FRONT && this == HOME
                    || other == NON_UNDER_DISPLAY && this != UNKNOWN && !underDisplay();
        }
        public static Type fromAndroid(int value) {
            switch (value) {
                case 1: return REAR;
                case 2: return ULTRASONIC;
                case 3: return OPTICAL;
                case 4: return SIDE;
                case 5: return HOME;
                default: return UNKNOWN;
            }
        }
    }
    public enum Source {
        ANDROID_PROPERTIES(90), SAMSUNG_API(100), VIVO_API(100), VIVO_SERVICE(100), HUAWEI_API(100),
        OPLUS_PROPERTIES(80), XIAOMI_PROPERTIES(80), ROM_PROPERTIES(80), VIVO_PROPERTIES(80), IFAA(70),
        FRAMEWORK_RESOURCE(60), SYSTEM_UI_DUMP(85), CALLER_PROVIDER(50), DEVICE_SPECIFICATION(10), SAMSUNG_FEATURES(80),
        COMMUNITY_ROM_CONFIGURATION(5), SAMSUNG_SYSFS(80), STOCK_FIRMWARE_CONFIGURATION(6), CALLER_FIRMWARE_CONFIGURATION(6);
        public final int priority;
        Source(int priority) { this.priority = priority; }
    }
    public final int sensorId;
    public final String displayId;
    public final Type type;
    public final Source source;
    public final Geometry.Space space;
    public final Geometry.Bounds bounds;
    public final String evidence;
    public final boolean frameworkPlaceholder;

    public Observation(int sensorId, String displayId, Type type, Source source, Geometry.Space space,
                       Geometry.Bounds bounds, String evidence, boolean frameworkPlaceholder) {
        if (sensorId < -1) throw new IllegalArgumentException("Invalid sensor id");
        this.sensorId = sensorId; this.displayId = Objects.requireNonNull(displayId);
        this.type = Objects.requireNonNull(type); this.source = Objects.requireNonNull(source);
        this.space = Objects.requireNonNull(space); this.bounds = bounds;
        this.evidence = Objects.requireNonNull(evidence);
        this.frameworkPlaceholder = frameworkPlaceholder || (source == Source.ANDROID_PROPERTIES || source == Source.VIVO_API)
                && space == Geometry.Space.NATIVE_NATURAL && bounds != null && bounds.centerX() == 540
                && bounds.centerY() == 1636 && bounds.width() == 260 && bounds.height() == 260;
    }
    public Observation(int sensorId, String displayId, Type type, Source source, Geometry.Space space,
                       Geometry.Bounds bounds, String evidence) {
        this(sensorId, displayId, type, source, space, bounds, evidence, false);
    }
}
