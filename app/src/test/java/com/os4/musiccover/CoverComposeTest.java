package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoverComposeTest {
    @Test public void fullBlurPreservesDefaultAndClampsEndpoints() {
        assertEquals(192, CoverCompose.blurWidth(0));
        assertEquals(48, CoverCompose.blurWidth(50));
        assertEquals(12, CoverCompose.blurWidth(100));
        assertEquals(192, CoverCompose.blurWidth(Integer.MIN_VALUE));
        assertEquals(12, CoverCompose.blurWidth(Integer.MAX_VALUE));
    }
}
