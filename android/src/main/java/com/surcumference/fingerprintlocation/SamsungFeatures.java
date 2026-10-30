package com.surcumference.fingerprintlocation;

import android.content.Context;
import android.os.Build;
import android.util.Xml;
import com.surcumference.fingerprintlocation.core.*;
import java.io.*;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

final class SamsungFeatures {
    private static final String PATH = "/system/etc/floating_feature.xml";
    private static final String KEY = "SEC_FLOATING_FEATURE_BIOAUTH_CONFIG_FINGERPRINT_FEATURES";
    private SamsungFeatures() {}

    static void collect(Context context, Geometry.DisplayFrame frame, int sensorId, Probe probe) {
        if (frame.displayId != 0 || !"samsung".equalsIgnoreCase(Build.MANUFACTURER)
                || !context.getPackageManager().hasSystemFeature("android.hardware.fingerprint")) return;
        try (InputStream input = new FileInputStream(PATH)) {
            Observation.Type type = read(input);
            if (type != Observation.Type.UNKNOWN)
                probe.add(new Observation(sensorId, "", type, Observation.Source.SAMSUNG_FEATURES,
                        Geometry.Space.NATIVE_NATURAL, null, PATH + ":" + KEY));
            else probe.issue("samsung-features", Resolution.Issue.Code.UNAVAILABLE);
        } catch (Exception error) { AndroidProviders.failure("samsung-features", error, probe); }
    }

    static Observation.Type read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (bytes.size() + size > 131072) throw new IllegalArgumentException();
            bytes.write(buffer, 0, size);
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new ByteArrayInputStream(bytes.toByteArray()), null);
            Observation.Type type = Observation.Type.UNKNOWN;
            boolean seen = false, root = false, closedRoot = false;
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.nextToken()) {
                if (event == XmlPullParser.DOCDECL) throw new IllegalArgumentException();
                if (event == XmlPullParser.END_TAG && parser.getDepth() == 1) closedRoot = true;
                if (event != XmlPullParser.START_TAG) continue;
                if (parser.getDepth() == 1) {
                    if (!"SecFloatingFeatureSet".equals(parser.getName())) throw new IllegalArgumentException();
                    root = true;
                } else if (KEY.equals(parser.getName())) {
                    if (parser.getDepth() != 2 || seen) throw new IllegalArgumentException();
                    seen = true;
                    String value = parser.nextText().trim();
                    if ("optical_display_phone".equals(value) || "optical_display_tablet".equals(value))
                        type = Observation.Type.OPTICAL;
                    else if ("capacitive_powerkey_phone".equals(value)) type = Observation.Type.SIDE;
                    else if ("ultrasonic_display_phone".equals(value)) type = Observation.Type.ULTRASONIC;
                }
            }
            if (!root || !closedRoot) throw new IllegalArgumentException();
            return type;
        } catch (XmlPullParserException malformed) { throw new IllegalArgumentException(malformed); }
    }
}
