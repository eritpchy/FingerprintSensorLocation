package com.surcumference.fingerprintlocation.core;

/** Explicitly selected community ROM reports, not physical sensor calibration. */
public enum CommunityGeometryProfile {
    INFINIX_X6739_8B45A3C8;

    public static final String X6739_SOURCE = "https://github.com/vlxlxlv/android_device_infinix_X6739/blob/"
            + "8b45a3c8dd7189bf26985ad589681b86a25baaf0/overlay/FrameworksResOverlayX6739/res/values/config.xml#L481";

    public void collect(String manufacturer, String model, Geometry.DisplayFrame display, int sensorId,
                        boolean fingerprintFeature, Probe probe) {
        if (sensorId >= 0) return;
        if (display.displayId != 0) {
            probe.issue(X6739_SOURCE, Resolution.Issue.Code.WRONG_DISPLAY); return;
        }
        if (!fingerprintFeature || !"INFINIX".equalsIgnoreCase(manufacturer) || !"Infinix X6739".equals(model)) {
            probe.issue(X6739_SOURCE, Resolution.Issue.Code.UNTRUSTED_SOURCE); return;
        }
        if (display.nativeWidth != 1080 || display.nativeHeight != 2400
                || (long) display.naturalWidth * 2400 != (long) display.naturalHeight * 1080) {
            probe.issue(X6739_SOURCE, Resolution.Issue.Code.INVALID_DATA); return;
        }
        Resolution runtime = probe.resolve(display, sensorId);
        if (runtime.status != Resolution.Status.UNAVAILABLE && runtime.status != Resolution.Status.ACCESS_DENIED) return;
        if (runtime.typeStatus != Resolution.TypeStatus.UNKNOWN && !runtime.sensorType.underDisplay()) return;
        probe.add(new Observation(-1, "", Observation.Type.UNDER_DISPLAY,
                Observation.Source.COMMUNITY_ROM_CONFIGURATION, Geometry.Space.NATIVE_NATURAL,
                Geometry.Bounds.circle(540, 2172, 95), X6739_SOURCE));
    }
}
