package com.os4.musiccover

import org.junit.Assert.*
import org.junit.Test

class ColorOsNotificationMotionTest {
    @Test fun openingDeceleratesNearTheListWithoutOvershootingOrHittingAClamp() {
        for (hz in listOf(60, 120)) for (start in listOf(0f, 0.43f, 0.8f, 0.95f))
            for (speed in listOf(0f, 2f, 8f)) {
                val spring = Jelly(ColorOsNotificationMotion.OPEN_RESPONSE, ColorOsNotificationMotion.OPEN_DAMPING)
                    .apply {
                        value = start
                        target = 1f
                        velocity = ColorOsNotificationMotion.openingVelocity(start, speed)
                    }
                repeat(hz * 2) {
                    val before = spring.value
                    val velocity = spring.velocity
                    spring.step(1f / hz)
                    assertTrue(spring.value >= before - 0.00001f)
                    assertTrue(spring.value <= 1.00001f)
                    if (before >= start + (1f - start) * 0.8f && velocity > 0.01f)
                        assertTrue("arrival must slow down", spring.velocity <= velocity + 0.00001f)
                }
                assertEquals(1f, spring.value, 0.001f)
            }
    }

    @Test fun foldingFacesCrossfadeWithALightBlurThatClearsAtBothEnds() {
        var previous = 0f
        for (i in 0..120) {
            val p = i / 120f
            val alpha = ColorOsNotificationMotion.foldContentAlpha(p)
            assertTrue(alpha >= previous)
            assertTrue(alpha in 0f..1f)
            assertTrue(ColorOsNotificationMotion.foldBlur(p) in 0f..1f)
            previous = alpha
        }
        assertEquals(0f, ColorOsNotificationMotion.foldContentAlpha(0f), 0f)
        assertEquals(1f, ColorOsNotificationMotion.foldContentAlpha(1f), 0f)
        assertEquals(0f, ColorOsNotificationMotion.foldBlur(0f), 0f)
        assertEquals(0f, ColorOsNotificationMotion.foldBlur(1f), 0f)
        assertEquals(1f, ColorOsNotificationMotion.foldBlur(0.5f), 0f)
    }

    @Test fun glassNeverFadesAwayBeforeItsNativeReplacementIsReady() {
        for (i in 0..1000) {
            val p = i / 1000f
            if (ColorOsNotificationMotion.materialOut(p) < 0.999f)
                assertEquals(1f, ColorOsNotificationMotion.nativeIn(p), 0.001f)
        }
        assertEquals(1f, ColorOsNotificationMotion.materialOut(0f), 0f)
        assertEquals(0f, ColorOsNotificationMotion.materialOut(1f), 0f)
    }

    @Test fun slowPullAndReversalHaveContinuousMaterialAndFocus() {
        val curves = listOf<(Float) -> Float>(ColorOsNotificationMotion::materialOut,
            ColorOsNotificationMotion::contentOut, ColorOsNotificationMotion::blurRemaining)
        for (curve in curves) {
            var previous = curve(0f)
            for (i in 1..1000) {
                val value = curve(i / 1000f)
                assertTrue(value in -0.00001f..1.00001f)
                assertTrue(value <= previous + 0.00001f)
                assertTrue(previous - value < 0.01f)
                previous = value
            }
        }
        assertTrue(ColorOsNotificationMotion.blurRemaining(0.65f) > 0.2f)
        assertEquals(0f, ColorOsNotificationMotion.blurRemaining(1f), 0f)
    }

    @Test fun foldedVisibilityDoesNotExcludeRowsThatWillFitTheOpenViewport() {
        // Native NUMBER keeps these rows below the screen; opening scroll brings them in.
        assertTrue(ColorOsNotificationMotion.entersViewport(3400f, 300f, 1300f, 2656f))
        assertFalse(ColorOsNotificationMotion.entersViewport(4100f, 300f, 1300f, 2656f))
        assertFalse(ColorOsNotificationMotion.entersViewport(400f, 300f, 1300f, 2656f))
    }

    @Test fun completedRearCardsDoNotDisappearOnHandbackToTheList() {
        assertFalse(ColorOsNotificationMotion.restoreHidden(true, true, true, true))
        // A real native hide or a destination past the viewport still takes effect.
        assertTrue(ColorOsNotificationMotion.restoreHidden(true, true, false, true))
        assertTrue(ColorOsNotificationMotion.restoreHidden(true, true, true, false))
        assertTrue(ColorOsNotificationMotion.restoreHidden(true, false, true, true))
    }

    @Test fun preparedRowsKeepTheirOpacityThroughoutTheNativeHandoff() {
        for (i in 0..100) {
            val mix = i / 100f
            assertEquals(1f, ColorOsNotificationMotion.retainedVisibility(true, true, true, mix), 0f)
            assertEquals(0f, ColorOsNotificationMotion.retainedVisibility(true, false, true, mix), 0f)
            assertEquals(1f - mix, ColorOsNotificationMotion.retainedVisibility(true, true, false, mix), 0f)
        }
    }

    @Test fun rearCardsCannotOutgrowTheLeadEvenWhenItsNativeRowIsStillParked() {
        for (i in 0..1000) {
            val progress = i / 1000f
            for (scale in listOf(0.85f, 0.75f, 0.65f)) {
                val width = ColorOsNotificationMotion.rearWidthLimit(622f, scale, progress)
                assertTrue(width <= 622.001f)
                assertTrue(width >= 622f * scale - 0.001f)
            }
        }
        assertEquals(0f, ColorOsNotificationMotion.rearAlpha(0f), 0f)
        assertEquals(1f, ColorOsNotificationMotion.rearAlpha(1f), 0f)
        assertTrue(ColorOsNotificationMotion.rearAlpha(0.05f) < 0.1f)
    }
}
