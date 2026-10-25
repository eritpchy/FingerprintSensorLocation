package com.surcumference.fingerprintlocation.core;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.surcumference.fingerprintlocation.core.Geometry.*;

public class GeometryTest {
    @Test public void nativeCoordinatesScaleBeforeRotation() {
        Bounds raw = Bounds.circle(720, 2180, 160);
        double[][] expected = {{420,1515,660,1755},{1515,420,1755,660},{420,585,660,825},{585,420,825,660}};
        for (int rotation = 0; rotation < 4; rotation++) {
            DisplayFrame display = new DisplayFrame("local:1", 0, 1080, 2340, 1440, 3120, rotation);
            same(expected[rotation], display.toLogical(raw, Space.NATIVE_NATURAL));
        }
    }
    @Test public void logicalCoordinatesAreNotScaledOrRotatedTwice() {
        DisplayFrame frame = new DisplayFrame("local:1", 0, 1080, 2340, 1440, 3120, 1);
        Bounds b = new Bounds(1500,400,1700,600); assertSame(b, frame.toLogical(b, Space.CURRENT_LOGICAL));
    }
    @Test public void androidAndOemAxisScalingHaveExplicitlyDifferentContracts() {
        DisplayFrame frame=new DisplayFrame("",0,1080,2300,1440,3200,0);
        Bounds raw=Bounds.circle(720,2800,100);
        assertEquals(720*(2300/3200.0),frame.toLogical(raw,Space.NATIVE_NATURAL).centerX(),.00001);
        assertEquals(540,frame.toLogical(raw,Space.NATIVE_NATURAL_AXES).centerX(),.00001);
    }
    @Test public void onlyDefaultDisplayAcceptsUnspecifiedDisplayId() {
        DisplayFrame frame = new DisplayFrame("local:42", 2, 1000, 2000, 1000, 2000, 0);
        assertTrue(frame.matches("local:42")); assertTrue(frame.matches("2"));
        assertFalse(frame.matches("")); assertFalse(frame.matches("local:43")); assertFalse(frame.matches(null));
    }
    @Test public void invalidCoordinatesAreRejectedInsteadOfClamped() {
        DisplayFrame frame = new DisplayFrame("",0,1080,2400,1080,2400,0);
        for (Bounds b : new Bounds[]{Bounds.circle(0,0,100), Bounds.circle(540,2400,100), Bounds.circle(540,1500,1500)}) {
            assertThrows(IllegalArgumentException.class, () -> frame.toLogical(b, Space.NATIVE_NATURAL));
        }
    }
    @Test public void malformedGeometryCannotEnterResults() {
        assertThrows(IllegalArgumentException.class, () -> Bounds.circle(0,0,0));
        assertThrows(IllegalArgumentException.class, () -> Bounds.circle(0,0,-1));
        assertThrows(IllegalArgumentException.class, () -> Bounds.circle(Double.NaN,0,1));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(0,0,Double.POSITIVE_INFINITY,1));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(-Double.MAX_VALUE,0,Double.MAX_VALUE,1));
        assertThrows(IllegalArgumentException.class, () -> new DisplayFrame("",0,0,100,100,100,0));
        assertThrows(IllegalArgumentException.class, () -> new DisplayFrame("",0,100,100,100,100,4));
    }
    static void same(double[] expected, Bounds value) {
        assertArrayEquals(expected, new double[]{value.left,value.top,value.right,value.bottom}, .00001);
    }
}
