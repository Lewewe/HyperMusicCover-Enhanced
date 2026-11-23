// SPDX-License-Identifier: AGPL-3.0-only
package com.os4.musiccover

import org.junit.Assert.assertEquals
import org.junit.Test

class CapsuleDemoMotionTest {
    @Test fun springMatchesTheRenderedGuideIncludingItsOvershoot() {
        // Golden samples from the previous Remotion guide, at 60 Hz with a 0.7 s duration.
        val frames = floatArrayOf(49f, 54f, 60f, 66f, 72f, 80f, 89f)
        val expected = floatArrayOf(.01352918f, .31167982f, .71961656f, .94567109f,
            1.02080482f, 1.02298253f, 1.00603222f)
        frames.forEachIndexed { i, frame ->
            assertEquals(expected[i], CapsuleDemoMotion.spring(frame, .8f), .00002f)
        }
        assertEquals(0f, CapsuleDemoMotion.spring(48f, .8f), 0f)
        assertEquals(1f, CapsuleDemoMotion.spring(90f, .8f), 0f)
    }

    @Test fun touchSpringKeepsTheOriginalTiming() {
        assertEquals(.28909454f, CapsuleDemoMotion.spring(54f, .8f, touch = true), .00002f)
        assertEquals(.92908806f, CapsuleDemoMotion.spring(72f, .8f, touch = true), .00002f)
        assertEquals(.99511730f, CapsuleDemoMotion.spring(89f, .8f, touch = true), .00002f)
        assertEquals(0f, CapsuleDemoMotion.glide(28f, 28f, 54f), 0f)
        assertEquals(.5f, CapsuleDemoMotion.glide(41f, 28f, 54f), 0f)
        assertEquals(1f, CapsuleDemoMotion.glide(54f, 28f, 54f), 0f)
    }
    @Test fun hyperOsListOpensHoldsAndReturnsToItsLoopStart() {
        assertEquals(0f, CapsuleDemoMotion.hyperOsListProgress(0f), 0f)
        assertEquals(1f, CapsuleDemoMotion.hyperOsListProgress(120f), .00001f)
        assertEquals(0f, CapsuleDemoMotion.hyperOsListProgress(299f), 0f)
        assertEquals(0f, CapsuleDemoMotion.hyperOsListProgress(35f), 0f)
        assertEquals(1f, CapsuleDemoMotion.hyperOsListProgress(175f), .00001f)
    }

}
