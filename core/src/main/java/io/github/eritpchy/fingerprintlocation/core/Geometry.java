package io.github.eritpchy.fingerprintlocation.core;

public final class Geometry {
    private Geometry() {}
    public enum Space { NATIVE_NATURAL, NATIVE_NATURAL_AXES, CURRENT_NATURAL, CURRENT_LOGICAL }

    public static final class Bounds {
        public final double left, top, right, bottom;
        public Bounds(double left, double top, double right, double bottom) {
            if (!finite(left) || !finite(top) || !finite(right) || !finite(bottom)
                    || right <= left || bottom <= top || !finite(right - left) || !finite(bottom - top))
                throw new IllegalArgumentException("Invalid bounds");
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        public static Bounds circle(double x, double y, double radius) {
            if (!finite(radius) || radius <= 0) throw new IllegalArgumentException("Invalid radius");
            return new Bounds(x - radius, y - radius, x + radius, y + radius);
        }
        public double centerX() { return left + width() / 2; }
        public double centerY() { return top + height() / 2; }
        public double width() { return right - left; }
        public double height() { return bottom - top; }
        public boolean inside(double width, double height) {
            return left >= 0 && top >= 0 && right <= width && bottom <= height;
        }
        @Override public String toString() { return "[" + left + "," + top + "," + right + "," + bottom + "]"; }
    }

    public static final class DisplayFrame {
        public final String uniqueId;
        public final int displayId, naturalWidth, naturalHeight, nativeWidth, nativeHeight, rotation;
        public DisplayFrame(String uniqueId, int displayId, int naturalWidth, int naturalHeight,
                            int nativeWidth, int nativeHeight, int rotation) {
            if (uniqueId == null || displayId < 0 || naturalWidth <= 0 || naturalHeight <= 0
                    || nativeWidth <= 0 || nativeHeight <= 0 || rotation < 0 || rotation > 3)
                throw new IllegalArgumentException("Invalid display frame");
            this.uniqueId = uniqueId; this.displayId = displayId;
            this.naturalWidth = naturalWidth; this.naturalHeight = naturalHeight;
            this.nativeWidth = nativeWidth; this.nativeHeight = nativeHeight; this.rotation = rotation;
        }
        public int logicalWidth() { return rotation % 2 == 0 ? naturalWidth : naturalHeight; }
        public int logicalHeight() { return rotation % 2 == 0 ? naturalHeight : naturalWidth; }
        public boolean sameConfiguration(DisplayFrame other) {
            return other != null && uniqueId.equals(other.uniqueId) && displayId == other.displayId
                    && naturalWidth == other.naturalWidth && naturalHeight == other.naturalHeight
                    && nativeWidth == other.nativeWidth && nativeHeight == other.nativeHeight && rotation == other.rotation;
        }
        public boolean matches(String id) {
            return id != null && (id.isEmpty() ? displayId == 0 : id.equals(uniqueId) || id.equals(Integer.toString(displayId)));
        }
        public Bounds toLogical(Bounds bounds, Space space) {
            java.util.Objects.requireNonNull(space);
            if (space == Space.CURRENT_LOGICAL) {
                if (!bounds.inside(logicalWidth(), logicalHeight())) throw new IllegalArgumentException("Bounds outside display");
                return bounds;
            }
            boolean nativeSpace = space == Space.NATIVE_NATURAL || space == Space.NATIVE_NATURAL_AXES;
            double sx = nativeSpace ? naturalWidth / (double) nativeWidth : 1;
            double sy = nativeSpace ? naturalHeight / (double) nativeHeight : 1;
            if (space == Space.NATIVE_NATURAL) sx = sy = Math.min(sx, sy);
            Bounds b = new Bounds(bounds.left * sx, bounds.top * sy, bounds.right * sx, bounds.bottom * sy);
            if (!b.inside(naturalWidth, naturalHeight)) throw new IllegalArgumentException("Bounds outside display");
            if (rotation == 1) return new Bounds(b.top, naturalWidth - b.right, b.bottom, naturalWidth - b.left);
            if (rotation == 2) return new Bounds(naturalWidth - b.right, naturalHeight - b.bottom,
                    naturalWidth - b.left, naturalHeight - b.top);
            if (rotation == 3) return new Bounds(naturalHeight - b.bottom, b.left, naturalHeight - b.top, b.right);
            return b;
        }
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
