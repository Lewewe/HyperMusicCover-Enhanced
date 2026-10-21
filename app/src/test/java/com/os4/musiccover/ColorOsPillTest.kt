// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// ColorOS-style lock screen capsule, written for this project. Its geometry, springs and timings
// are modelled on the behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is
// included. If you reuse this file, keep this notice and the AGPL-3.0 terms (publish your source).
package com.os4.musiccover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsPillTest {
    private val rest = CoverMorphMotion.Box(300f, 2400f, 600f, 120f)
    private val gap = 26f
    private val out = 29f
    private val stroke = 7f

    private fun f(u: Float, next: Boolean = true) = ColorOsPill.frame(rest, u, next, gap, out, stroke)

    private fun assertBox(e: CoverMorphMotion.Box, a: CoverMorphMotion.Box) {
        assertEquals(e.x, a.x, 0.5f); assertEquals(e.y, a.y, 0.5f)
        assertEquals(e.w, a.w, 0.5f); assertEquals(e.h, a.h, 0.5f)
    }

    @Test fun atRestTheCapsuleIsWhereItIsAndTheArrivingCardIsItsEar() {
        val a = f(0f)
        assertBox(rest, a.old)
        val ear = ColorOsPill.earBox(rest, true, out, stroke)
        assertEquals(ear.x, a.new.x, 0.5f)
        assertEquals(ear.w, a.new.w, 0.5f)
        // ColorOS's arriving card is a hair taller than its ear view at rest (its clip scales with W).
        assertEquals(ear.h, a.new.h, 1f)
        assertEquals(0f, a.bracket, 0.001f)
        assertEquals(1f, a.oldBracket, 0.001f)
    }

    @Test fun atTheEndTheArrivingIslandIsAtRestAndTheLeavingOneIsTheOtherEar() {
        val z = f(1f)
        assertBox(rest, z.new)
        val ear = ColorOsPill.earBox(rest, false, out, stroke)
        assertEquals(ear.x, z.old.x, 0.5f)
        assertEquals(ear.w, z.old.w, 1.5f)
        assertEquals(ear.h, z.old.h, 0.5f)
        assertEquals(1f, z.bracket, 0.001f)
        assertEquals(0f, z.oldBracket, 0.001f)
    }

    @Test fun theArrivingIslandIsACapsuleFromRoundAt() {
        val r = f(ColorOsPill.roundAt(rest, stroke) + 0.001f)
        assertTrue(r.newBean < 0f)
        assertTrue(f(ColorOsPill.roundAt(rest, stroke) - 0.01f).newBean >= 0f)
    }

    /** Nothing moves back against the finger: a held finger holds the frame, a moving one moves it on. */
    @Test fun everyEdgeTravelsOneWayAndStaysBetweenTheEars() {
        var prev = f(0f)
        for (i in 1..1000) {
            val c = f(i / 1000f)
            assertTrue("new left at $i", c.new.x <= prev.new.x + 0.01f)
            assertTrue("old right at $i", c.old.x + c.old.w <= prev.old.x + prev.old.w + 0.01f)
            assertTrue("old left at $i", c.old.x <= prev.old.x + 0.01f)
            assertTrue("old width at $i", c.old.w <= prev.old.w + 0.01f)
            assertTrue("new width at $i", c.new.w >= prev.new.w - 0.01f)
            for (b in listOf(c.old, c.new)) {
                assertTrue(b.x >= rest.x - out - 0.5f && b.x + b.w <= rest.x + rest.w + out + 0.5f)
                assertTrue(b.h <= rest.h + 0.01f)
            }
            assertEquals(1f, c.bracket + c.oldBracket, 0.001f)
            prev = c
        }
    }

    @Test fun switchingBackIsTheMirrorImage() {
        for (u in floatArrayOf(0.1f, 0.4f, 0.7f, 0.95f)) {
            val a = f(u, next = true)
            val b = f(u, next = false)
            assertEquals(2f * rest.cx() - a.new.x - a.new.w, b.new.x, 0.5f)
            assertEquals(2f * rest.cx() - a.old.x - a.old.w, b.old.x, 0.5f)
        }
    }

    @Test fun contentGoesOutOfFocusThenTheNewOneComesIn() {
        val round = ColorOsPill.roundAt(rest, stroke)
        assertEquals(1f, ColorOsPill.oldContent(0f).first, 0.001f)
        assertEquals(0f, ColorOsPill.oldContent(1f).second, 0.001f)
        assertEquals(0f, ColorOsPill.newContent(round, round).second, 0.001f)
        assertEquals(1f, ColorOsPill.newContent(1f, round).first, 0.001f)
    }

    @Test fun theFieldJoinsOnlyOnceTheNarrowerIsLongerThanRound() {
        val capsule = CoverMorphMotion.Box(0f, 0f, 400f, 100f)
        // A circle 20 away: no blend range, a gap between them.
        val circle = ColorOsPill.Field(capsule, CoverMorphMotion.Box(420f, 0f, 100f, 100f))
        assertEquals(0f, circle.halfHeight(410f), 0.5f)
        // The same gap to an island 0.6 heights longer than round: a waist across it.
        val long = ColorOsPill.Field(capsule, CoverMorphMotion.Box(420f, 0f, 160f, 100f))
        val waist = long.halfHeight(410f)
        assertTrue(waist > 5f && waist < 50f)
        // Away from the joint the bodies keep their own height.
        assertEquals(50f, long.halfHeight(200f), 0.5f)
    }

    @Test fun theEarGoesFromABracketToADisc() {
        val (rb0, a0) = ColorOsPill.earArc(0f, 100f, 4f)
        assertEquals(2f, rb0, 0.01f) // one stroke across
        assertEquals(0.47f, a0, 0.001f)
        val (rb1, a1) = ColorOsPill.earArc(1f, 100f, 4f)
        assertEquals(50f, rb1, 0.01f) // the disc, as tall as the capsule
        assertEquals(0f, a1, 0.001f)
        var last = 0f
        for (i in 0..100) {
            val rb = ColorOsPill.earArc(i / 100f, 100f, 4f).first
            assertTrue(rb >= last - 1e-3f)
            last = rb
        }
    }
}
