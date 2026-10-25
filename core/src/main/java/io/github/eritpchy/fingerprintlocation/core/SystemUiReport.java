package io.github.eritpchy.fingerprintlocation.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static io.github.eritpchy.fingerprintlocation.core.Resolution.Issue.Code.*;

/** AOSP AuthController dumps expose current-natural bounds, already resolution-scaled. */
public final class SystemUiReport {
    private static final String PROVIDER = "systemui-report";
    private static final int MAX_LENGTH = 2 * 1024 * 1024;
    private SystemUiReport() {}

    public static void collect(SystemUiDumpReader reader, Geometry.DisplayFrame display, Probe probe) {
        if (display.displayId != 0) {probe.issue(PROVIDER, WRONG_DISPLAY);return;}
        try {
            String dump = reader.read();
            if (dump == null || dump.trim().isEmpty()) {probe.issue(PROVIDER, UNAVAILABLE);return;}
            if (dump.length() > MAX_LENGTH) throw new IllegalArgumentException();
            if (Pattern.compile("(?m)^\\s*Permission Denial:").matcher(dump).find()) {
                probe.issue(PROVIDER, ACCESS_DENIED);return;
            }
            List<String> auth = sections(dump, "AuthController"), udfps = sections(dump, "UdfpsController");
            if (auth.isEmpty() || udfps.isEmpty()) {probe.issue(PROVIDER, UNAVAILABLE);return;}
            if (auth.size() != 1 || udfps.size() != 1) throw new IllegalArgumentException();
            String block = auth.get(0);
            if (!"true".equals(field(block, "allFingerprintAuthenticatorsRegistered"))) {
                probe.issue(PROVIDER, UNAVAILABLE);return;
            }
            String info = field(block, "mCachedDisplayInfo");
            int id = integer(info, "\\bdisplayId\\s+(\\d+)\\b");
            Matcher size = unique(info, "\\breal\\s+(\\d+)\\s*x\\s*(\\d+)\\b");
            int rotation = integer(info, "\\brotation\\s+([0-3])\\b");
            if (id != display.displayId) {probe.issue(PROVIDER, WRONG_DISPLAY);return;}
            if (Integer.parseInt(size.group(1)) != display.logicalWidth()
                    || Integer.parseInt(size.group(2)) != display.logicalHeight() || rotation != display.rotation) {
                probe.issue(PROVIDER, DISPLAY_CHANGED);return;
            }
            double scale = Double.parseDouble(field(block, "mScaleFactor"));
            double expected = Math.min(display.naturalWidth / (double) display.nativeWidth,
                    display.naturalHeight / (double) display.nativeHeight);
            if (Double.isNaN(scale) || Double.isInfinite(scale) || scale <= 0 || Math.abs(scale - expected) > .0001)
                throw new IllegalArgumentException();
            String boundsValue = field(block, "udfpsBounds");
            if ("null".equals(boundsValue)) {probe.issue(PROVIDER, UNAVAILABLE);return;}
            Matcher rect = unique(boundsValue, "^Rect\\((-?\\d+),\\s*(-?\\d+)\\s*-\\s*(-?\\d+),\\s*(-?\\d+)\\)$");
            Geometry.Bounds bounds = new Geometry.Bounds(Double.parseDouble(rect.group(1)), Double.parseDouble(rect.group(2)),
                    Double.parseDouble(rect.group(3)), Double.parseDouble(rect.group(4)));
            // AOSP reports its default-display UDFPS controller's sensor ID separately.
            Matcher sensor = unique(field(udfps.get(0), "mSensorProps"),
                    "^\\(ID:\\s*(\\d+),\\s*Strength:\\s*\\d+,\\s*Type:\\s*([23])\\)$");
            int sensorId = Integer.parseInt(sensor.group(1));
            Observation.Type type = "2".equals(sensor.group(2)) ? Observation.Type.OPTICAL : Observation.Type.ULTRASONIC;
            display.toLogical(bounds, Geometry.Space.CURRENT_NATURAL);
            boolean placeholder = bounds.left == Math.round(410 * scale) && bounds.top == Math.round(1506 * scale)
                    && bounds.right == Math.round(670 * scale) && bounds.bottom == Math.round(1766 * scale);
            probe.add(new Observation(sensorId, "", type, Observation.Source.SYSTEM_UI_DUMP,
                    Geometry.Space.CURRENT_NATURAL, bounds, "AuthController.udfpsBounds+UdfpsController.mSensorProps", placeholder));
        } catch (Exception error) {
            Throwable cause = error;
            for (int depth = 0; depth < 8 && cause.getCause() != null; depth++) cause = cause.getCause();
            probe.issue(PROVIDER, cause instanceof SecurityException || cause instanceof IllegalAccessException ? ACCESS_DENIED
                    : cause instanceof IllegalArgumentException ? INVALID_DATA : UNAVAILABLE);
        } catch (LinkageError unavailable) {probe.issue(PROVIDER, UNAVAILABLE);}
    }
    private static List<String> sections(String dump, String name) {
        List<String> result = new ArrayList<>();StringBuilder block = null;int indent = 0;
        Pattern header = Pattern.compile("^(\\s*)(?:com\\.android\\.systemui\\.biometrics\\.)?" + name + ":\\s*$");
        for (String line : dump.split("\\r?\\n")) {
            Matcher start = header.matcher(line);
            if (start.matches()) {
                if (block != null) result.add(block.toString());
                block = new StringBuilder();indent = start.group(1).length();continue;
            }
            if (block == null) continue;
            String trim = line.trim();int leading = line.length() - line.replaceFirst("^\\s+", "").length();
            if (trim.isEmpty() || trim.matches("-+")) continue;
            if (leading <= indent) {result.add(block.toString());block = null;}
            else block.append(line).append('\n');
        }
        if (block != null) result.add(block.toString());return result;
    }
    private static String field(String block, String name) {
        return unique(block, "(?m)^\\s*" + name + "=(.*?)\\s*$").group(1);
    }
    private static int integer(String value, String regex) {return Integer.parseInt(unique(value, regex).group(1));}
    private static Matcher unique(String value, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(value);
        if (!matcher.find()) throw new IllegalArgumentException();
        int start = matcher.start();
        if (matcher.find()) throw new IllegalArgumentException();
        matcher.reset();matcher.find(start);return matcher;
    }
}
