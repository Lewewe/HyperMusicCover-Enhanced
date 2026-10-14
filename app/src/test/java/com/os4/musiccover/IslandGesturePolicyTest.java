package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandGesturePolicyTest {
    @Test public void toggleOffPreservesEveryNativeState() {
        for (String state : new String[] {"BigIsland", "SmallIsland", "ShowOnceBigIsland", "Expanded", null})
            assertFalse(IslandGesturePolicy.remap(false, state));
    }
    @Test public void remapsOnlyCompactInteractiveIslands() {
        for (String state : new String[] {"BigIsland", "SmallIsland", "ShowOnceBigIsland"})
            assertTrue(IslandGesturePolicy.remap(true, state));
        for (String state : new String[] {"Expanded", "Hidden", "TempHidden", "Immersive", "", null})
            assertFalse(IslandGesturePolicy.remap(true, state));
    }

    @Test public void radiusIsClampedToSliderRange() {
        assertEquals(IslandGesturePolicy.MIN_RADIUS_DP, IslandGesturePolicy.radius(0));
        assertEquals(IslandGesturePolicy.MAX_RADIUS_DP, IslandGesturePolicy.radius(500));
        assertEquals(IslandGesturePolicy.DEFAULT_RADIUS_DP,
                IslandGesturePolicy.radius(IslandGesturePolicy.DEFAULT_RADIUS_DP));
    }
    @Test public void cornerReachIsZeroPastTheCurve() {
        assertEquals(0f, IslandGesturePolicy.cornerReach(146f, 146f), 0f);
        assertEquals(0f, IslandGesturePolicy.cornerReach(146f, 400f), 0f);
        assertEquals(146f, IslandGesturePolicy.cornerReach(146f, 0f), 0.001f);
    }
    @Test public void bottomTextLiftsOnlyWhenCornerCutsIt() {
        // Time label 86 px in and 28 px above the bottom: clear at 146 px, cut at 208 px.
        assertEquals(0f, IslandGesturePolicy.bottomCornerLift(146f, 1131f, 522f, 86f, 180f, 494f), 0f);
        float lift = IslandGesturePolicy.bottomCornerLift(208f, 1131f, 522f, 86f, 180f, 494f);
        assertTrue(lift > 0f);
        float reach = IslandGesturePolicy.cornerReach(208f, 86f);
        assertEquals(522f - reach, 494f - lift, 0.001f);
        // The right-hand label is measured from the right edge.
        assertEquals(lift, IslandGesturePolicy.bottomCornerLift(208f, 1131f, 522f, 951f, 1045f, 494f), 0.001f);
    }
    @Test public void cameraPaddingClearsLens() {
        assertEquals(30f, IslandGesturePolicy.cameraPadding(119f, 95f, 6f), 0.001f);
        assertEquals(0f, IslandGesturePolicy.cameraPadding(80f, 120f, 6f), 0f);
    }
    @Test public void clipRadiusKeepsPillEndsAndReachesSetting() {
        // Compact pill stays fully round whatever the setting.
        assertEquals(53f, IslandGesturePolicy.clipRadius(106f, 106f, 600f, 40f), 0.001f);
        assertEquals(53f, IslandGesturePolicy.clipRadius(106f, 106f, 600f, 150f), 0.001f);
        // Expanded card uses the chosen radius.
        assertEquals(40f, IslandGesturePolicy.clipRadius(600f, 106f, 600f, 40f), 0.001f);
        assertEquals(150f, IslandGesturePolicy.clipRadius(600f, 106f, 600f, 150f), 0.001f);
        // Half-way through the morph a small radius is half-way from the pill.
        assertEquals(46.5f, IslandGesturePolicy.clipRadius(353f, 106f, 600f, 40f), 0.001f);
    }
}
