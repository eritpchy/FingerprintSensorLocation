package com.surcumference.fingerprintlocation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicitly selected, byte-bound stock configuration; not physical calibration. */
public enum StockFirmwareGeometryProfile {
    NOTHING_A069_B4_1_260808_1352,
    GOOGLE_PIXEL_8_PRO_BP1A_250505_005_B1;

    public static final String HAL_PATH = "/vendor/bin/hw/vendor.nothing.hardware.biometrics.fingerprint-service";
    public static final String HAL_SHA256 = "f5c8e01d51fd8c8a15735b76ec2b14a5489846449bd82db3593c0ae9d7629995";
    public static final String SOURCE = "https://github.com/spike0en/nothing_archive/releases/tag/Frogger_B4.1-260808-1352";
    public static final String PIXEL_APEX = "/apex/com.google.android.hardware.biometrics.fingerprint/";
    public static final String CDT = "/sys/firmware/devicetree/base/chosen/plat/";
    public static final String PIXEL_SOURCE = "https://github.com/TheMuppets/proprietary_vendor_google_husky/commit/b172714b02563a3871dc7d04cc217e9da6585b04";

    public Map<String, String> componentHashes() {
        Map<String, String> hashes = new LinkedHashMap<>();
        if (this == NOTHING_A069_B4_1_260808_1352) hashes.put(HAL_PATH, HAL_SHA256);
        else {
            hashes.put(PIXEL_APEX + "lib64/libgf_hal.so", "b3f411e464a69211042d72efba386a41d8408063fab1393100667587d442f384");
            hashes.put(PIXEL_APEX + "lib64/fp_utils.so", "fd9c143d978ee8f6bc6ab510b1e618d2621093170fa1fe171c34eeaddd2bb13e");
            hashes.put(PIXEL_APEX + "bin/hw/android.hardware.biometrics.fingerprint-service.goodix",
                    "c605f131aff47fd2a356228a1c896795b06c453c7f1c347472adbc7482977aef");
        }
        return Collections.unmodifiableMap(hashes);
    }
    public String[] metadataPaths() {
        return this == NOTHING_A069_B4_1_260808_1352 ? new String[0]
                : new String[]{CDT + "platform", CDT + "product"};
    }
    public boolean matches(String manufacturer, String model, String device, String buildId,
                           String display, String incremental) {
        if (this == NOTHING_A069_B4_1_260808_1352)
            return "Nothing".equalsIgnoreCase(manufacturer) && "A069".equals(model)
                    && "Frogger".equals(device) && "BQ2A.250913.001-BP2A.250605.031.A3".equals(buildId)
                    && "B4.1-260808-1352".equals(display) && "2608081352".equals(incremental);
        return "Google".equalsIgnoreCase(manufacturer) && "Pixel 8 Pro".equals(model) && "husky".equals(device)
                && "BP1A.250505.005.B1".equals(buildId) && "BP1A.250505.005.B1".equals(display)
                && "13277630".equals(incremental);
    }
    public boolean supportsFrame(Geometry.DisplayFrame frame) {
        int width = this == NOTHING_A069_B4_1_260808_1352 ? 1224 : 1344;
        int height = this == NOTHING_A069_B4_1_260808_1352 ? 2720 : 2992;
        return frame.displayId == 0 && frame.nativeWidth == width && frame.nativeHeight == height
                && (long) frame.naturalWidth * height == (long) frame.naturalHeight * width;
    }
    public void collect(String manufacturer, String model, String device, String buildId,
                        String display, String incremental, Geometry.DisplayFrame frame, int sensorId, boolean fingerprintFeature,
                        String verifiedHalHash, Probe probe) {
        collect(manufacturer, model, device, buildId, display, incremental, frame, sensorId,
                fingerprintFeature, verifiedHalHash, Observation.Source.STOCK_FIRMWARE_CONFIGURATION, probe);
    }
    public void collect(String manufacturer, String model, String device, String buildId,
                        String display, String incremental, Geometry.DisplayFrame frame, int sensorId, boolean fingerprintFeature,
                        String verifiedHalHash, Observation.Source source, Probe probe) {
        collect(manufacturer, model, device, buildId, display, incremental, frame, sensorId, fingerprintFeature,
                Collections.singletonMap(HAL_PATH, verifiedHalHash), Collections.emptyMap(), source, probe);
    }
    public void collect(String manufacturer, String model, String device, String buildId,
                        String display, String incremental, Geometry.DisplayFrame frame, int sensorId, boolean fingerprintFeature,
                        Map<String, String> verifiedHashes, Map<String, byte[]> metadata, Observation.Source source, Probe probe) {
        if (source != Observation.Source.STOCK_FIRMWARE_CONFIGURATION
                && source != Observation.Source.CALLER_FIRMWARE_CONFIGURATION) throw new IllegalArgumentException("Invalid firmware source");
        if (!matches(manufacturer, model, device, buildId, display, incremental)
                || sensorId >= 0 || !fingerprintFeature || frame.displayId != 0) return;
        Resolution runtime = probe.resolve(frame, sensorId);
        if (runtime.status != Resolution.Status.UNAVAILABLE && runtime.status != Resolution.Status.ACCESS_DENIED) return;
        if (runtime.typeStatus != Resolution.TypeStatus.UNKNOWN
                && (runtime.typeStatus != Resolution.TypeStatus.KNOWN || !runtime.sensorType.underDisplay())) return;
        String provenance = this == NOTHING_A069_B4_1_260808_1352 ? SOURCE : PIXEL_SOURCE;
        for (Map.Entry<String, String> component : componentHashes().entrySet()) {
            if (!component.getValue().equals(verifiedHashes.get(component.getKey()))) {
                probe.issue(provenance, Resolution.Issue.Code.UNTRUSTED_SOURCE); return;
            }
        }
        if (this == GOOGLE_PIXEL_8_PRO_BP1A_250505_005_B1
                && (!cdt(metadata.get(CDT + "platform"), 4) || !cdt(metadata.get(CDT + "product"), 2))) {
            probe.issue(provenance, Resolution.Issue.Code.UNTRUSTED_SOURCE); return;
        }
        if (!supportsFrame(frame)) {probe.issue(provenance, Resolution.Issue.Code.INVALID_DATA); return;}
        Geometry.Bounds bounds = this == NOTHING_A069_B4_1_260808_1352
                ? Geometry.Bounds.circle(612, 2485, 103) : Geometry.Bounds.circle(672, 2261, 115);
        probe.add(new Observation(-1, "", Observation.Type.UNDER_DISPLAY, source,
                Geometry.Space.NATIVE_NATURAL, bounds, provenance + " verified component sha256=" + verifiedHashes
                        + (this == GOOGLE_PIXEL_8_PRO_BP1A_250505_005_B1 ? " CDT platform=4 product=2" : "")));
    }
    private static boolean cdt(byte[] bytes, int expected) {
        return bytes != null && bytes.length == 4 && bytes[0] == 0 && bytes[1] == 0
                && bytes[2] == 0 && (bytes[3] & 255) == expected;
    }
}
