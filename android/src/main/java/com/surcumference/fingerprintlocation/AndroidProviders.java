package com.surcumference.fingerprintlocation;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.TypedValue;
import com.surcumference.fingerprintlocation.core.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.json.JSONObject;
import static com.surcumference.fingerprintlocation.core.Geometry.Space.*;
import static com.surcumference.fingerprintlocation.core.Observation.Type.*;
import static com.surcumference.fingerprintlocation.core.Observation.Source.*;
import static com.surcumference.fingerprintlocation.core.Resolution.Issue.Code.*;

final class AndroidProviders {
    static final class State {
        final Set<Integer> ids = new HashSet<>();
        Observation.Type type = UNKNOWN;
    }
    private AndroidProviders() {}
    static State framework(Context context, Geometry.DisplayFrame frame, int requested, Probe probe) {
        State state = new State();
        try {
            Object manager = fingerprintService(context);
            if (manager == null) { probe.issue("android-properties", UNAVAILABLE); return state; }
            Object properties = Reflect.call(manager, "getSensorPropertiesInternal");
            if (!(properties instanceof List)) { probe.issue("android-properties", INVALID_DATA); return state; }
            for (Object sensor : (List<?>) properties) {
                int id = integer(sensor, "sensorId");
                if (requested >= 0 && requested != id) continue;
                Observation.Type type = Observation.Type.fromAndroid(integer(sensor, "sensorType"));
                List<?> locations;
                try {
                    try { locations = (List<?>) Reflect.call(sensor, "getAllLocations"); }
                    catch (NoSuchMethodException legacy) {
                        try { locations = Collections.singletonList(Reflect.call(sensor, "getLocation")); }
                        catch (NoSuchMethodException api31) { locations = Collections.singletonList(sensor); }
                    }
                } catch (Exception unavailable) {
                    failure("android-properties", unavailable, probe);
                    locations = Collections.emptyList();
                }
                if (locations == null) {
                    probe.issue("android-properties", INVALID_DATA);
                    locations = Collections.emptyList();
                }
                boolean matches = locations.isEmpty() && frame.displayId == 0;
                for (Object location : locations) {
                    String displayId;
                    try { displayId = (String) Reflect.field(location, "displayId"); }
                    catch (NoSuchFieldException api31) { displayId = ""; }
                    if (!frame.matches(displayId)) continue;
                    matches = true;
                    if (!type.underDisplay()) {
                        probe.add(new Observation(id, displayId, type, ANDROID_PROPERTIES, NATIVE_NATURAL,
                                null, "FingerprintManager.getSensorPropertiesInternal")); continue;
                    }
                    try {
                        double x = integer(location, "sensorLocationX"), y = integer(location, "sensorLocationY");
                        Geometry.Bounds bounds;
                        try {
                            int width = integer(location, "sensorWidth"), height = integer(location, "sensorHeight");
                            if (width <= 0 || height <= 0) throw new IllegalArgumentException();
                            bounds = new Geometry.Bounds(x - width / 2.0, y - height / 2.0, x + width / 2.0, y + height / 2.0);
                        } catch (NoSuchFieldException circular) {
                            bounds = Geometry.Bounds.circle(x, y, integer(location, "sensorRadius"));
                        }
                        boolean placeholder = x == 540 && y == 1636 && bounds.width() == 260 && bounds.height() == 260;
                        probe.add(new Observation(id, displayId, type, ANDROID_PROPERTIES, NATIVE_NATURAL, bounds,
                                "FingerprintManager.getSensorPropertiesInternal", placeholder));
                    } catch (Exception invalid) {
                        failure("android-properties", invalid, probe);
                        probe.add(new Observation(id, displayId, type, ANDROID_PROPERTIES, NATIVE_NATURAL,
                                null, "FingerprintManager.getSensorPropertiesInternal"));
                    }
                }
                if (matches) {
                    if (locations.isEmpty())
                        probe.add(new Observation(id, "", type, ANDROID_PROPERTIES, NATIVE_NATURAL,
                                null, "FingerprintManager.getSensorPropertiesInternal"));
                    state.ids.add(id);
                    if (state.ids.size() == 1) state.type = type;
                    else state.type = UNKNOWN;
                }
            }
        } catch (Exception error) { failure("android-properties", error, probe); }
        catch (LinkageError unavailable) { probe.issue("android-properties", UNAVAILABLE, unavailable); }
        return state;
    }
    @android.annotation.SuppressLint({"ResourceType", "DiscouragedApi"})
    static void resources(Context context, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        sideResources(context.getResources(), frame, sensorId, probe);
        if (frame.displayId != 0) return;
        String sideProvider = "android:bool/config_is_powerbutton_fps";
        try {
            int id = context.getResources().getIdentifier("config_is_powerbutton_fps", "bool", "android");
            if (id != 0 && context.getResources().getBoolean(id))
                probe.add(new Observation(sensorId, "", SIDE, FRAMEWORK_RESOURCE, NATIVE_NATURAL, null, sideProvider));
        } catch (Exception error) { failure(sideProvider, error, probe); }
        String provider = "android:array/config_udfps_sensor_props";
        try {
            int id = context.getResources().getIdentifier("config_udfps_sensor_props", "array", "android");
            if (id == 0) return;
            TypedArray array = context.getResources().obtainTypedArray(id);
            try {
                if (array.length() == 0) return;
                if (array.length() != 3) throw new IllegalArgumentException();
                for (int i = 0; i < 3; i++) {
                    TypedValue value = array.peekValue(i);
                    if (value == null || value.type < TypedValue.TYPE_FIRST_INT || value.type > TypedValue.TYPE_LAST_INT)
                        throw new IllegalArgumentException();
                }
                probe.add(new Observation(sensorId, "", UNDER_DISPLAY, FRAMEWORK_RESOURCE, NATIVE_NATURAL,
                        Geometry.Bounds.circle(array.getInt(0, -1), array.getInt(1, -1), array.getInt(2, -1)), provider));
            } finally { array.recycle(); }
        } catch (IllegalArgumentException error) { probe.issue(provider, INVALID_DATA); }
        catch (Exception error) { failure(provider, error, probe); }
    }
    @android.annotation.SuppressLint({"ResourceType", "DiscouragedApi"})
    private static void sideResources(Resources resources, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        String provider = "android:array/config_sfps_sensor_props";
        try {
            int id = resources.getIdentifier("config_sfps_sensor_props", "array", "android");
            if (id == 0) return;
            Set<String> matched = new LinkedHashSet<>();
            TypedArray outer = resources.obtainTypedArray(id);
            try {
                if (outer.length() > 16) throw new IllegalArgumentException();
                for (int i = 0; i < outer.length(); i++) {
                    int nested = outer.getResourceId(i, 0);
                    if (nested == 0) throw new IllegalArgumentException();
                    TypedArray entry = resources.obtainTypedArray(nested);
                    try {
                        if (entry.length() != 4 || entry.getString(0) == null) throw new IllegalArgumentException();
                        for (int field = 1; field < 4; field++) {
                            TypedValue value = entry.peekValue(field);
                            if (value == null || value.type < TypedValue.TYPE_FIRST_INT || value.type > TypedValue.TYPE_LAST_INT)
                                throw new IllegalArgumentException();
                        }
                        if (entry.getInt(1, -1) < 0 || entry.getInt(2, -1) < 0 || entry.getInt(3, 0) <= 0)
                            throw new IllegalArgumentException();
                        String displayId = entry.getString(0);
                        if (frame.matches(displayId)) matched.add(displayId);
                    } finally { entry.recycle(); }
                }
            } finally { outer.recycle(); }
            for (String displayId : matched)
                probe.add(new Observation(sensorId, displayId, SIDE, FRAMEWORK_RESOURCE,
                        NATIVE_NATURAL, null, provider));
        } catch (Exception error) { failure(provider, error, probe); }
    }
    static void oem(Context context, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        SamsungFeatures.collect(context, frame, sensorId, probe);
        if (frame.displayId == 0) {
            attempt("samsung-type", probe, () -> {
                if (!context.getPackageManager().hasSystemFeature("android.hardware.fingerprint")) return;
                Object manager = fingerprintService(context);
                if (manager == null) return;
                Object position = Reflect.call(manager, "semGetSensorPosition");
                if (!(position instanceof Integer)) throw new IllegalArgumentException();
                String[] names = {"SEM_SENSOR_POSITION_DISPLAY", "SEM_SENSOR_POSITION_POWER_KEY",
                        "SEM_SENSOR_POSITION_REAR", "SEM_SENSOR_POSITION_HOME_KEY"};
                Observation.Type[] types = {UNDER_DISPLAY, SIDE, REAR, HOME};
                for (int i = 0; i < names.length; i++) {
                    try {
                        Object constant = Reflect.field(manager, names[i]);
                        if (constant instanceof Integer && ((Integer) constant) > 0 && position.equals(constant))
                            probe.add(new Observation(sensorId, "", types[i], SAMSUNG_API, CURRENT_LOGICAL,
                                    null, "FingerprintManager.semGetSensorPosition/" + names[i]));
                    } catch (NoSuchFieldException unavailable) { }
                }
            });
            attempt("samsung-api", probe, () -> {
                Object manager = fingerprintService(context);
                if (manager != null) addRect((Rect) Reflect.call(manager, "semGetFingerIconRectInDisplay"),
                        sensorId, "", UNDER_DISPLAY, SAMSUNG_API, CURRENT_LOGICAL, "semGetFingerIconRectInDisplay", probe);
            });
            attempt("huawei-api", probe, () -> {
                String cls = "huawei.android.hardware.fingerprint.FingerprintManagerEx";
                if (Boolean.TRUE.equals(Reflect.callStatic(cls, "hasFingerprintInScreen", new Class<?>[0]))) {
                    probe.add(new Observation(sensorId, "", UNDER_DISPLAY, HUAWEI_API, NATIVE_NATURAL,
                            null, "FingerprintManagerEx.hasFingerprintInScreen"));
                    attempt("huawei-type", probe, () -> {
                        Object hardware = Reflect.callStatic(cls, "getHardwareType", new Class<?>[0]);
                        if (!(hardware instanceof Integer)) throw new IllegalArgumentException();
                        int value = (Integer) hardware;
                        Observation.Type technology = value == 1 ? OPTICAL : value == 2 ? ULTRASONIC : UNKNOWN;
                        if (technology != UNKNOWN)
                            probe.add(new Observation(sensorId, "", technology, HUAWEI_API, NATIVE_NATURAL,
                                    null, "FingerprintManagerEx.getHardwareType=" + value));
                    });
                    if (!huaweiPosition(cls, "getSpotPosition", frame, sensorId, probe))
                        huaweiPosition(cls, "getHardwarePosition", frame, sensorId, probe);
                }
            });
        }
        attempt("vivo-api", probe, () -> {
            String cls = "com.vivo.fingerprint.FingerprintHelper";
            int sensorType = ((Number) Reflect.callStatic(cls, "getFingerprintSensorType", new Class<?>[]{Context.class}, context)).intValue();
            Observation.Type type = Observation.Type.fromAndroid(sensorType);
            if (frame.displayId == 0 && type != UNKNOWN)
                probe.add(new Observation(sensorId, "", type, VIVO_API, NATIVE_NATURAL,
                        null, "FingerprintHelper.getFingerprintSensorType"));
            if (!type.underDisplay()) return;
            Rect bounds = (Rect) Reflect.callStatic(cls, "getFingerprintSensorLocation", new Class<?>[]{Context.class, int.class}, context, frame.displayId);
            addRect(bounds, sensorId, Integer.toString(frame.displayId), type, VIVO_API, NATIVE_NATURAL,
                    "FingerprintHelper.getFingerprintSensorLocation", probe);
        });
    }
    private static boolean huaweiPosition(String cls, String method, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        String provider = "huawei-" + method;
        try {
            Object value = Reflect.callStatic(cls, method, new Class<?>[0]);
            if (!(value instanceof int[]) || ((int[]) value).length != 4) throw new IllegalArgumentException();
            int[] position = (int[]) value;
            Geometry.Bounds bounds = new Geometry.Bounds(position[0], position[1], position[2], position[3]);
            if (!bounds.inside(frame.nativeWidth, frame.nativeHeight)) throw new IllegalArgumentException();
            probe.add(new Observation(sensorId, "", UNDER_DISPLAY, HUAWEI_API, NATIVE_NATURAL,
                    bounds, "FingerprintManagerEx." + method));
            return true;
        } catch (Exception error) {failure(provider, error, probe);}
        catch (LinkageError unavailable) {probe.issue(provider, UNAVAILABLE, unavailable);}
        return false;
    }
    static void ifaa(Context context, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        if (frame.displayId != 0) return;
        attempt("ifaa", probe, () -> {
            Object manager = Reflect.callStatic("org.ifaa.android.manager.IFAAManagerFactory", "getIFAAManager",
                    new Class<?>[]{Context.class, int.class}, context, 1);
            if (manager == null) return;
            Object support = Reflect.call(manager, "getSupportBIOTypes", new Class<?>[]{Context.class}, context);
            if (!(support instanceof Integer) || (Integer) support < 0) throw new IllegalArgumentException();
            // Some IFAA services return default sensor geometry even for side/rear fingerprint hardware.
            if (((Integer) support & 0x11) != 0x11) {
                probe.issue("ifaa", UNAVAILABLE);
                return;
            }
            Object result = Reflect.call(manager, "getExtInfo", new Class<?>[]{int.class, String.class},
                    1, "org.ifaa.ext.key.GET_SENSOR_LOCATION");
            if (!(result instanceof String) || ((String) result).length() > 8192) throw new IllegalArgumentException();
            JSONObject json = new JSONObject((String) result);
            if (json.getInt("type") != 0) throw new IllegalArgumentException();
            JSONObject area = json.getJSONObject("fullView");
            if (area.has("unit") && !"px".equals(area.getString("unit"))) throw new IllegalArgumentException();
            double x = area.getDouble("startX"), y = area.getDouble("startY");
            Geometry.Bounds bounds = new Geometry.Bounds(x, y, x + area.getDouble("width"), y + area.getDouble("height"));
            probe.add(new Observation(sensorId, "", UNDER_DISPLAY, IFAA, CURRENT_NATURAL, bounds,
                    "IFAAManager.getSupportBIOTypes(screen fingerprint) + getExtInfo(GET_SENSOR_LOCATION)"));
        });
    }
    private static int integer(Object value, String name) throws ReflectiveOperationException {
        return ((Number) Reflect.field(value, name)).intValue();
    }
    private static void addRect(Rect rect, int id, String displayId, Observation.Type type, Observation.Source source,
                                Geometry.Space space, String evidence, Probe probe) {
        if (rect == null || rect.isEmpty()) throw new IllegalArgumentException();
        probe.add(new Observation(id, displayId, type, source, space,
                new Geometry.Bounds(rect.left, rect.top, rect.right, rect.bottom), evidence));
    }
    interface Read { void run() throws Exception; }
    @android.annotation.SuppressLint("WrongConstant")
    private static Object fingerprintService(Context context) {
        // SDK 37 removes the public constant while existing firmware still exposes this service.
        return context.getSystemService("fingerprint");
    }
    private static void attempt(String name, Probe probe, Read read) {
        try { read.run(); }
        catch (Exception error) { failure(name, error, probe); }
        catch (LinkageError unavailable) { probe.issue(name, UNAVAILABLE, unavailable); }
    }
    static void failure(String name, Exception error, Probe probe) {
        Throwable cause = error;
        while (cause instanceof InvocationTargetException && ((InvocationTargetException) cause).getCause() != null)
            cause = cause.getCause();
        probe.issue(name, cause instanceof SecurityException || cause instanceof IllegalAccessException ? ACCESS_DENIED
                : cause instanceof IllegalArgumentException || cause instanceof org.json.JSONException ? INVALID_DATA : UNAVAILABLE, cause);
    }
}
