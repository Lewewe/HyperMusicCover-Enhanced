package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LyricDepthBlurTest {
    @Test public void defaultPreservesOriginalDistanceRadii() {
        assertEquals(0f, LyricDepthBlur.radiusDp(0, 100), 0f);
        assertEquals(0.9f, LyricDepthBlur.radiusDp(1, 100), 0.0001f);
        assertEquals(2.5f, LyricDepthBlur.radiusDp(2, 100), 0.0001f);
        assertEquals(5.7f, LyricDepthBlur.radiusDp(4, 100), 0.0001f);
        assertEquals(5.7f, LyricDepthBlur.radiusDp(20, 100), 0.0001f);
    }

    @Test public void zeroRemovesDepthBlurAndActiveRowsAlwaysStaySharp() {
        for (int row = 0; row <= 20; row++) {
            assertEquals(0f, LyricDepthBlur.radiusDp(row, 0), 0f);
        }
        for (int percent = 0; percent <= 200; percent++) {
            assertEquals(0f, LyricDepthBlur.radiusDp(0, percent), 0f);
        }
    }

    @Test public void strengthIsRelativeAndBounded() {
        assertEquals(1.25f, LyricDepthBlur.radiusDp(2, 50), 0.0001f);
        assertEquals(5f, LyricDepthBlur.radiusDp(2, 200), 0.0001f);
        assertEquals(0, LyricDepthBlur.clamp(-50));
        assertEquals(200, LyricDepthBlur.clamp(Integer.MAX_VALUE));
    }
}
