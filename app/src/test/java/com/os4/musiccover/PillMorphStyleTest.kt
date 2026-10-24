package com.os4.musiccover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PillMorphStyleTest {
    private val pill = CoverMorphMotion.Box(240f, 2300f, 720f, 216f)
    private val card = CoverMorphMotion.Box(42f, 1700f, 1116f, 557f)
    private val styles = listOf(ColorOsPillMorph, HyperOsPillMorph)

    @Test fun eachRowStyleHasItsOwnChoreography() {
        assertSame(ColorOsPillMorph, PillMorphStyle.of(colorOs = true))
        assertSame(HyperOsPillMorph, PillMorphStyle.of(colorOs = false))
    }

    @Test fun everyStyleIsExactlyEachEndAtRest() = styles.forEach { s ->
        val atPill = s.containerFrame(pill, card, 0f)
        val atCard = s.containerFrame(pill, card, 1f)
        assertEquals(pill.y, atPill.y, 0.01f)
        assertEquals(pill.w, atPill.w, 0.01f)
        assertEquals(card.y, atCard.y, 0.01f)
        assertEquals(card.w, atCard.w, 0.01f)
        assertEquals(108f, s.cornerRadius(108f, 32f, 0f), 0f)
        assertEquals(32f, s.cornerRadius(108f, 32f, 1f), 0f)
    }

    @Test fun everyStyleShowsOnlyOneLiveFaceAtEachEnd() = styles.forEach { s ->
        assertEquals(1f, s.materialOut(0f), 0f)
        assertEquals(0f, s.nativeIn(0f), 0f)
        assertEquals(0f, s.materialOut(1f), 0f)
        assertEquals(1f, s.nativeIn(1f), 0f)
        assertEquals(0f, s.pairedOut(1f), 0f)
    }

    @Test fun neitherStylesGlassThinsInTheMiddle() = styles.forEach { s ->
        var p = 0f
        while (p <= 1f) {
            if (s.materialOut(p) < 1f) assertTrue(s.nativeIn(p) > 0.95f)
            p += 0.01f
        }
    }

    @Test fun ledByTheListTheFacesHandOverByHalfway() = styles.forEach { s ->
        assertEquals(1f, s.faceProgress(0.5f, spreadLed = true), 0f)
        assertEquals(0.5f, s.faceProgress(0.5f, spreadLed = false), 0f)
    }
}
