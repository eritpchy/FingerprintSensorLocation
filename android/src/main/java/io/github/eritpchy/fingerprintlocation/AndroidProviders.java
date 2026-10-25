package io.github.eritpchy.fingerprintlocation;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.TypedValue;
import io.github.eritpchy.fingerprintlocation.core.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.json.JSONObject;
import static io.github.eritpchy.fingerprintlocation.core.Geometry.Space.*;
import static io.github.eritpchy.fingerprintlocation.core.Observation.Type.*;
import static io.github.eritpchy.fingerprintlocation.core.Observation.Source.*;
import static io.github.eritpchy.fingerprintlocation.core.Resolution.Issue.Code.*;

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
                Observation.Type type = type(integer(sensor, "sensorType"));
                List<?> locations;
                try { locations = (List<?>) Reflect.call(sensor, "getAllLocations"); }
                catch (NoSuchMethodException legacy) {
                    try { locations = Collections.singletonList(Reflect.call(sensor, "getLocation")); }
                    catch (NoSuchMethodException api31) { locations = Collections.singletonList(sensor); }
                }
                if (locations == null) { probe.issue("android-properties", INVALID_DATA); continue; }
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
                    } catch (IllegalArgumentException invalid) { probe.issue("android-properties", INVALID_DATA); }
                }
                if (matches) {
                    if (locations.isEmpty() && type != UNKNOWN && !type.underDisplay())
                        probe.add(new Observation(id, "", type, ANDROID_PROPERTIES, NATIVE_NATURAL,
                                null, "FingerprintManager.getSensorPropertiesInternal"));
                    state.ids.add(id);
                    if (state.ids.size() == 1) state.type = type;
                    else state.type = UNKNOWN;
                }
            }
        } catch (Exception error) { failure("android-properties", error, probe); }
        catch (LinkageError unavailable) { probe.issue("android-properties", UNAVAILABLE); }
        return state;
    }
    @android.annotation.SuppressLint({"ResourceType", "DiscouragedApi"})
    static void resources(Context context, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        if (frame.displayId != 0) return;
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
    static void oem(Context context, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        if (frame.displayId == 0) {
            attempt("samsung-api", probe, () -> {
                Object manager = fingerprintService(context);
                if (manager != null) addRect((Rect) Reflect.call(manager, "semGetFingerIconRectInDisplay"),
                        sensorId, "", UNDER_DISPLAY, SAMSUNG_API, CURRENT_LOGICAL, "semGetFingerIconRectInDisplay", probe);
            });
            attempt("huawei-api", probe, () -> {
                String cls = "huawei.android.hardware.fingerprint.FingerprintManagerEx";
                if (Boolean.TRUE.equals(Reflect.callStatic(cls, "hasFingerprintInScreen", new Class<?>[0]))) {
                    int[] position = (int[]) Reflect.callStatic(cls, "getHardwarePosition", new Class<?>[0]);
                    if (position == null || position.length != 4) throw new IllegalArgumentException();
                    addRect(new Rect(position[0], position[1], position[2], position[3]), sensorId, "", UNDER_DISPLAY,
                            HUAWEI_API, NATIVE_NATURAL, "FingerprintManagerEx.getHardwarePosition", probe);
                }
            });
        }
        attempt("vivo-api", probe, () -> {
            String cls = "com.vivo.fingerprint.FingerprintHelper";
            int sensorType = ((Number) Reflect.callStatic(cls, "getFingerprintSensorType", new Class<?>[]{Context.class}, context)).intValue();
            Observation.Type type = type(sensorType);
            if (!type.underDisplay()) return;
            Rect bounds = (Rect) Reflect.callStatic(cls, "getFingerprintSensorLocation", new Class<?>[]{Context.class, int.class}, context, frame.displayId);
            addRect(bounds, sensorId, Integer.toString(frame.displayId), type, VIVO_API, NATIVE_NATURAL,
                    "FingerprintHelper.getFingerprintSensorLocation", probe);
        });
    }
    static void ifaa(Context context, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        if (frame.displayId != 0) return;
        attempt("ifaa", probe, () -> {
            Object manager = Reflect.callStatic("org.ifaa.android.manager.IFAAManagerFactory", "getIFAAManager",
                    new Class<?>[]{Context.class, int.class}, context, 1);
            if (manager == null) return;
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
                    "IFAAManager.getExtInfo(GET_SENSOR_LOCATION)"));
        });
    }
    private static Observation.Type type(int value) {
        switch (value) {
            case 1: return REAR;
            case 2: return OPTICAL;
            case 3: return ULTRASONIC;
            case 4: return SIDE;
            case 5: return HOME;
            default: return UNKNOWN;
        }
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
        catch (LinkageError unavailable) { probe.issue(name, UNAVAILABLE); }
    }
    static void failure(String name, Exception error, Probe probe) {
        Throwable cause = error;
        while (cause instanceof InvocationTargetException && ((InvocationTargetException) cause).getCause() != null)
            cause = cause.getCause();
        probe.issue(name, cause instanceof SecurityException || cause instanceof IllegalAccessException ? ACCESS_DENIED
                : cause instanceof IllegalArgumentException || cause instanceof org.json.JSONException ? INVALID_DATA : UNAVAILABLE);
    }
}
