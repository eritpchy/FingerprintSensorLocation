package com.surcumference.fingerprintlocation.core;

/** Parses the nine-field Samsung sysfs millimetre layout, without firmware defaults. */
public final class SamsungSensorPosition {
    private SamsungSensorPosition() {}

    public static Geometry.Bounds parse(String value, Geometry.DisplayFrame frame, double pixelsPerMillimetre) {
        if (value == null || value.length() > 512 || frame.displayId != 0
                || frame.naturalWidth >= frame.naturalHeight
                || (Double.isNaN(pixelsPerMillimetre) || Double.isInfinite(pixelsPerMillimetre)) || pixelsPerMillimetre <= 0)
            throw new IllegalArgumentException("Unsupported Samsung position configuration");
        String[] fields = value.trim().split(",", -1);
        if (fields.length != 9) throw new IllegalArgumentException("Unknown Samsung position layout");
        double[] mm = new double[9];
        for (int i = 0; i < mm.length; i++) {
            String field = fields[i].trim();
            if (!field.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException("Invalid millimetres");
            mm[i] = Double.parseDouble(field);
            if ((Double.isNaN(mm[i]) || Double.isInfinite(mm[i])) || Math.abs(mm[i]) > 1000)
                throw new IllegalArgumentException("Invalid millimetres");
        }
        // Firmware UI implementations disagree on the sign of nonzero lateral offsets.
        if (mm[0] < 0 || mm[1] != 0 || mm[2] <= 0 || mm[3] <= 0)
            throw new IllegalArgumentException("Unsupported Samsung sensor margins");
        double width = mm[2] * pixelsPerMillimetre, height = mm[3] * pixelsPerMillimetre;
        double left = frame.naturalWidth / 2.0 - width / 2;
        double bottom = frame.naturalHeight - mm[0] * pixelsPerMillimetre;
        Geometry.Bounds bounds = new Geometry.Bounds(left, bottom - height, left + width, bottom);
        if (!bounds.inside(frame.naturalWidth, frame.naturalHeight))
            throw new IllegalArgumentException("Samsung position outside display");
        return bounds;
    }
}
