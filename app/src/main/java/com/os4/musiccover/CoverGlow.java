package com.os4.musiccover;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Choreographer;

/**
 * Decides what the wallpaper process draws over the big cover's blurred backdrop: WallpaperGlow.
 *
 * With the drift on, the artwork's colour regions roam while the lock screen is lit and fade out
 * with the screen going to the AOD. With it off they stand where they used to be baked into the
 * backdrop bitmap, as they were, the AOD included.
 */
final class CoverGlow {
    private CoverGlow() {}

    /**
     * The regions (CoverBackdropPalette) were sized and toned for a still image: huge and faint,
     * a drift of them could not be seen. Smaller and stronger when they move.
     */
    private static final float RADIUS_SCALE = 0.65f;
    private static final float ALPHA_SCALE = 2.0f;
    /** Going to the AOD is seen a few frames late at most; no need to look on every vsync. */
    private static final long POLL_MS = 66L;

    private static Bitmap sArt;
    private static float[][] sRegions;
    private static int[] sColors;
    private static float[] sSpec;
    private static boolean sSpecDrift;
    private static boolean sPolling;
    private static boolean sSentOn, sSentDrift;
    private static float[] sSentSpec;

    /** Something it goes by may have changed. Main thread. */
    static void kick() {
        Bitmap now = CoverCardLayer.currentArt();
        if (now != sArt) {
            sArt = now;
            if (now != null && !now.isRecycled()) palette(now);
        }
        boolean base = now != null && !now.isRecycled() && sRegions != null && Main.coverModeOn()
                && Main.effectiveCoverStyle().mode == CoverCardStyle.CARD
                && !Main.sHidePlayerBackground;
        boolean drift = Main.sGlowDrift;
        boolean on = base && (!drift || (Main.screenOnCached() && Main.coverCardVisible()
                && ClockCollapse.phase() != ClockCollapse.Phase.AOD));
        if (sRegions != null && (sSpec == null || sSpecDrift != drift)) {
            sSpec = spec(drift);
            sSpecDrift = drift;
        }
        if (on != sSentOn || drift != sSentDrift || sSpec != sSentSpec) {
            sSentOn = on;
            sSentDrift = drift;
            sSentSpec = sSpec;
            send(on, drift, sSpec);
        }
        // Only a drifting glow has anything to notice by itself; a held-off one is kicked back.
        if (on && drift && !sPolling) {
            sPolling = true;
            Choreographer.getInstance().postFrameCallbackDelayed(frame -> {
                sPolling = false;
                kick();
            }, POLL_MS);
        }
    }

    /** A restarted wallpaper process has forgotten all of it. */
    static void resend() {
        sSentSpec = null;
        kick();
    }

    private static void palette(Bitmap src) {
        Bitmap sample = Bitmap.createScaledBitmap(src, 32, 32, true);
        int[] pixels = new int[32 * 32];
        sample.getPixels(pixels, 0, 32, 0, 0, 32, 32);
        if (sample != src) sample.recycle();
        sColors = CoverBackdropPalette.colors(pixels);
        sRegions = CoverBackdropPalette.regions(sColors);
        sSpec = null;
    }

    private static float[] spec(boolean drift) {
        float[] out = new float[WallpaperGlow.REGIONS * WallpaperGlow.STRIDE];
        for (int i = 0; i < WallpaperGlow.REGIONS; i++) {
            float[] region = sRegions[i];
            int s = i * WallpaperGlow.STRIDE;
            int color = sColors[i % sColors.length];
            if (drift) color = vivid(color, i);
            out[s] = region[0];
            out[s + 1] = region[1];
            out[s + 2] = drift ? region[2] * RADIUS_SCALE : region[2];
            out[s + 3] = drift ? Math.min(0.75f, region[3] * ALPHA_SCALE) : region[3];
            out[s + 4] = Color.red(color) / 255f;
            out[s + 5] = Color.green(color) / 255f;
            out[s + 6] = Color.blue(color) / 255f;
        }
        return out;
    }

    /**
     * Over a blur of the same artwork its own colours vanish, saturation alone was not enough on a
     * one-hue cover: alternate deep and light regions so the drift reads as light moving.
     */
    private static int vivid(int color, int i) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 2f + 0.15f);
        hsv[2] = i % 2 == 0 ? 0.55f : 0.95f;
        return Color.HSVToColor(hsv);
    }

    private static void send(boolean on, boolean drift, float[] spec) {
        Context c = Main.sAppCtx;
        if (c == null) return;
        Intent out = CoverPush.wallpaperIntent("glow");
        out.putExtra("on", on);
        out.putExtra("drift", drift);
        if (spec != null) out.putExtra("spec", spec);
        ProbeGuard.send(c, out);
    }
}
