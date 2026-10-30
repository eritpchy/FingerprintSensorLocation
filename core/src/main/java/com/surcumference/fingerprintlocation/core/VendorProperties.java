package com.surcumference.fingerprintlocation.core;

import static com.surcumference.fingerprintlocation.core.Geometry.Space.NATIVE_NATURAL;
import static com.surcumference.fingerprintlocation.core.Observation.Source.*;
import static com.surcumference.fingerprintlocation.core.Observation.Type.*;
import static com.surcumference.fingerprintlocation.core.Resolution.Issue.Code.*;

public final class VendorProperties {
    private VendorProperties() {}
    public static void collectTranssion(PropertyReader reader, String manufacturer, Geometry.DisplayFrame display,
                                        int sensorId, Probe probe) {
        if (display.displayId != 0 || manufacturer == null) return;
        switch (manufacturer.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "tecno": case "tecno mobile limited":
            case "infinix": case "infinix mobility limited": break;
            default: return;
        }
        String key = "ro.optical_fingerprint_support";
        // The vendor framework compares the raw property with "1", not a boolean parser.
        if ("1".equals(readRaw(reader, key, probe))) classified(sensorId, OPTICAL, ROM_PROPERTIES, key, probe);
    }
    public static void collect(PropertyReader reader, Geometry.DisplayFrame display, int sensorId,
                               Observation.Type knownType, Probe probe) {
        String persistedType = read(reader, "persist.vendor.fingerprint.type", probe);
        String fixedType = read(reader, "ro.vendor.fingerprint.type", probe);
        Observation.Type persisted = type(persistedType), fixed = type(fixedType);
        Observation.Type reportedRomType = persisted == UNKNOWN ? fixed : persisted;
        Observation.Type romType = reportedRomType;
        if (romType == UNKNOWN) romType = knownType;
        String oplusType = read(reader, "persist.vendor.fingerprint.sensor_type", probe);
        Observation.Type oplus = type(oplusType);
        if (display.displayId == 0) {
            for (String key : new String[]{"sys.fingerprint.boot", "vendor.fingerprint.boot", "persist.sys.fptype"}) {
                String module = read(reader, key, probe).toLowerCase(java.util.Locale.ROOT);
                Observation.Type placement = UNKNOWN;
                if (module.matches("udfp_[a-z0-9][a-z0-9_.-]{0,127}")) placement = UNDER_DISPLAY;
                else if (module.matches("sidefp_[a-z0-9][a-z0-9_.-]{0,127}")) placement = SIDE;
                classified(sensorId, placement, VIVO_PROPERTIES, key, probe);
            }
            classified(sensorId, persisted, ROM_PROPERTIES, "persist.vendor.fingerprint.type", probe);
            classified(sensorId, fixed, ROM_PROPERTIES, "ro.vendor.fingerprint.type", probe);
            classified(sensorId, oplus, OPLUS_PROPERTIES, "persist.vendor.fingerprint.sensor_type", probe);
            oplus(reader, display, sensorId, oplus == UNKNOWN ? knownType : oplus, probe);
            String fod = read(reader, "ro.hardware.fp.fod", probe);
            if (enabled(fod)) classified(sensorId, UNDER_DISPLAY, XIAOMI_PROPERTIES, "ro.hardware.fp.fod", probe);
            if (enabled(read(reader, "ro.hardware.fp.udfps", probe)))
                classified(sensorId, UNDER_DISPLAY, ROM_PROPERTIES, "ro.hardware.fp.udfps", probe);
            if (!"false".equalsIgnoreCase(fod) && !"0".equals(fod)) {
                xiaomi(reader, display, sensorId, "persist.sys.fp.fod.", probe);
                xiaomi(reader, display, sensorId, "persist.vendor.sys.fp.fod.", probe);
            }
        }
        locations(read(reader, "persist.vendor.fingerprint.sensor_location", probe), romType,
                sensorId, "persist.vendor.fingerprint.sensor_location", probe);
        locations(read(reader, "ro.vendor.fingerprint.sensor_location", probe), romType,
                sensorId, "ro.vendor.fingerprint.sensor_location", probe);
    }
    public static Observation.Type type(String value) {
        if (value == null) return UNKNOWN;
        switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "optical": case "udfps_optical": return OPTICAL;
            case "ultrasonic": case "udfps": return ULTRASONIC;
            case "side": return SIDE;
            case "rear": case "back": return REAR;
            case "home": case "front": return HOME;
            default: return UNKNOWN;
        }
    }
    private static boolean enabled(String value) { return "true".equalsIgnoreCase(value) || "1".equals(value); }
    private static void classified(int sensorId, Observation.Type type, Observation.Source source, String key, Probe probe) {
        if (type != UNKNOWN)
            probe.add(new Observation(sensorId, "", type, source, NATIVE_NATURAL, null, key));
    }
    private static void locations(String value, Observation.Type type, int sensorId, String key, Probe probe) {
        if (value.isEmpty()) return;
        String[] entries = value.split("[;,]", -1);
        if (entries.length > 16) { probe.issue(key, INVALID_DATA); return; }
        for (String entry : entries) {
            try {
                String[] fields = entry.split("\\|", -1);
                if (fields.length != 3 && fields.length != 4) throw new IllegalArgumentException();
                Geometry.Bounds bounds = Geometry.Bounds.circle(number(fields[0]), number(fields[1]), number(fields[2]));
                probe.add(new Observation(sensorId, fields.length == 4 ? fields[3].trim() : "", type,
                        ROM_PROPERTIES, NATIVE_NATURAL, bounds, key));
            } catch (IllegalArgumentException bad) { probe.issue(key, INVALID_DATA); }
        }
    }
    private static void oplus(PropertyReader reader, Geometry.DisplayFrame display, int sensorId,
                              Observation.Type type, Probe probe) {
        if (!type.underDisplay()) return;
        String size = read(reader, "persist.vendor.fingerprint.optical.iconsize", probe);
        String bottom = read(reader, "persist.vendor.fingerprint.optical.iconlocation", probe);
        if (size.isEmpty() || bottom.isEmpty()) return;
        try {
            double diameter = number(size), margin = number(bottom);
            if (diameter <= 0 || margin < 0) throw new IllegalArgumentException();
            Geometry.Bounds bounds = Geometry.Bounds.circle(display.nativeWidth / 2.0,
                    display.nativeHeight - margin, diameter / 2);
            probe.add(new Observation(sensorId, "", type, OPLUS_PROPERTIES, Geometry.Space.NATIVE_NATURAL_AXES,
                    bounds, "persist.vendor.fingerprint.optical.iconlocation+iconsize"));
        } catch (IllegalArgumentException bad) { probe.issue("oplus-properties", INVALID_DATA); }
    }
    private static void xiaomi(PropertyReader reader, Geometry.DisplayFrame display, int sensorId,
                               String prefix, Probe probe) {
        String location = read(reader, prefix + "location.X_Y", probe);
        String size = read(reader, prefix + "size.width_height", probe);
        if (location.isEmpty() || size.isEmpty()) return;
        try {
            String[] xy = location.split(",", -1), wh = size.split(",", -1);
            if (xy.length != 2 || wh.length != 2) throw new IllegalArgumentException();
            double x = number(xy[0]), y = number(xy[1]);
            Geometry.Bounds bounds = new Geometry.Bounds(x, y, x + number(wh[0]), y + number(wh[1]));
            probe.add(new Observation(sensorId, "", UNDER_DISPLAY, XIAOMI_PROPERTIES, NATIVE_NATURAL,
                    bounds, prefix + "location.X_Y+size.width_height"));
        } catch (IllegalArgumentException bad) { probe.issue(prefix, INVALID_DATA); }
    }
    private static String read(PropertyReader reader, String key, Probe probe) {
        return readRaw(reader, key, probe).trim();
    }
    private static String readRaw(PropertyReader reader, String key, Probe probe) {
        try {
            String value = reader.get(key);
            if (value == null) return "";
            if (value.length() > 4096) { probe.issue(key, INVALID_DATA); return ""; }
            return value;
        } catch (Exception unavailable) {
            Throwable cause = unavailable;
            for (int depth = 0; depth < 8 && cause.getCause() != null; depth++) cause = cause.getCause();
            probe.issue(key, cause instanceof SecurityException || cause instanceof IllegalAccessException ? ACCESS_DENIED : UNAVAILABLE, cause);
        }
        return "";
    }
    private static double number(String value) {
        double number = Double.parseDouble(value.trim());
        if (Double.isInfinite(number) || Double.isNaN(number)) throw new IllegalArgumentException();
        return number;
    }
}
