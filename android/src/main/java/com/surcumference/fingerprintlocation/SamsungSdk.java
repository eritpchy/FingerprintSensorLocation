package com.surcumference.fingerprintlocation;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import com.surcumference.fingerprintlocation.core.*;

final class SamsungSdk {
    private static final String NAME = "com.samsung.android.bio.fingerprint.SemFingerprintManager";
    private SamsungSdk() {}

    static void collect(Context context, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        if (frame.displayId != 0 || !"samsung".equalsIgnoreCase(Build.MANUFACTURER)
                || !context.getPackageManager().hasSystemFeature("android.hardware.fingerprint")) return;
        try {
            Object manager = Reflect.callStatic(NAME, "createInstance", new Class<?>[]{Context.class}, context);
            if (manager == null) { probe.issue("samsung-sdk", Resolution.Issue.Code.UNAVAILABLE); return; }
            collectCharacteristics(Reflect.call(manager, "getCharacteristics"), frame, sensorId, probe);
        } catch (Exception error) { AndroidProviders.failure("samsung-sdk", error, probe); }
        catch (LinkageError unavailable) { probe.issue("samsung-sdk", Resolution.Issue.Code.UNAVAILABLE, unavailable); }
    }

    static void collectCharacteristics(Object info, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        if (frame.displayId != 0 || info == null) return;
        Observation.Type type = Observation.Type.UNKNOWN;
        try {
            Object position = Reflect.call(info, "getSensorPosition");
            String[] names = {"SENSOR_POSITION_IN_DISPLAY", "SENSOR_POSITION_POWER_KEY",
                    "SENSOR_POSITION_REAR", "SENSOR_POSITION_HOME_KEY"};
            Observation.Type[] types = {Observation.Type.UNDER_DISPLAY, Observation.Type.SIDE,
                    Observation.Type.REAR, Observation.Type.HOME};
            for (int i = 0; i < names.length; i++) {
                try {
                    Object constant = Reflect.field(info, names[i]);
                    if (constant instanceof Integer && (Integer) constant > 0 && constant.equals(position))
                        type = types[i];
                } catch (NoSuchFieldException unavailable) { }
            }
            if (type != Observation.Type.UNKNOWN)
                probe.add(new Observation(sensorId, "", type, Observation.Source.SAMSUNG_API,
                        Geometry.Space.CURRENT_LOGICAL, null, NAME + ".Characteristics.getSensorPosition"));
        } catch (Exception error) { AndroidProviders.failure("samsung-sdk-type", error, probe); }
        Resolution runtime = probe.resolve(frame, sensorId);
        if (runtime.typeStatus != Resolution.TypeStatus.KNOWN || !runtime.sensorType.underDisplay()) return;
        type = runtime.sensorType;
        try {
            Object value = Reflect.call(info, "getSensorType");
            Observation.Type subtype = Observation.Type.UNKNOWN;
            String[] names = {"SENSOR_TYPE_OPTICAL", "SENSOR_TYPE_ULTRASONIC"};
            Observation.Type[] types = {Observation.Type.OPTICAL, Observation.Type.ULTRASONIC};
            for (int i = 0; i < names.length; i++) {
                try {
                    Object constant = Reflect.field(info, names[i]);
                    if (constant instanceof Integer && (Integer) constant > 0 && constant.equals(value)) subtype = types[i];
                } catch (NoSuchFieldException unavailable) { }
            }
            if (subtype != Observation.Type.UNKNOWN) {
                type = subtype;
                probe.add(new Observation(sensorId, "", type, Observation.Source.SAMSUNG_API,
                        Geometry.Space.CURRENT_LOGICAL, null, NAME + ".Characteristics.getSensorType"));
            }
        } catch (Exception error) { AndroidProviders.failure("samsung-sdk-type", error, probe); }
        runtime = probe.resolve(frame, sensorId);
        if (runtime.typeStatus != Resolution.TypeStatus.KNOWN || !runtime.sensorType.underDisplay()) return;
        type = runtime.sensorType;
        try {
            Rect rect = (Rect) Reflect.call(info, "getSensorAreaInDisplay");
            if (rect == null || rect.isEmpty()) { probe.issue("samsung-sdk-area", Resolution.Issue.Code.UNAVAILABLE); return; }
            Geometry.Bounds bounds = new Geometry.Bounds(rect.left, rect.top, rect.right, rect.bottom);
            frame.toLogical(bounds, Geometry.Space.CURRENT_LOGICAL);
            probe.add(new Observation(sensorId, "", type, Observation.Source.SAMSUNG_API,
                    Geometry.Space.CURRENT_LOGICAL, bounds, NAME + ".Characteristics.getSensorAreaInDisplay:icon-area"));
        } catch (Exception error) { AndroidProviders.failure("samsung-sdk-area", error, probe); }
    }
}
