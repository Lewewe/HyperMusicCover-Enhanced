package com.os4.musiccover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockPageSizeTest {
    @Test fun theCoversOwnSizeAsksNothingOfTheOemWithoutASizeSet() {
        // At rest out of cover mode the size is the cover's own (native 0).
        assertTrue(ClockPageSize.wantedOemHeight(20f, 100f, Float.NaN).isNaN())
    }

    @Test fun aSizeSetIsAShareOfTheOemsFullHeightWithinItsRange() {
        assertEquals(50f, ClockPageSize.wantedOemHeight(20f, 100f, 0.5f), 0.001f)
        assertEquals(20f, ClockPageSize.wantedOemHeight(20f, 100f, 0.05f), 0.001f)
        assertEquals(100f, ClockPageSize.wantedOemHeight(20f, 100f, 3f), 0.001f)
    }

    @Test fun noFullHeightNoAnswer() {
        assertTrue(ClockPageSize.wantedOemHeight(20f, 0f, 0.5f).isNaN())
    }
}
