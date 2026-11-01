package com.os4.musiccover;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/**
 * The artwork's colour regions over the big cover's blurred backdrop, drifting slowly.
 *
 * They used to be baked into the backdrop bitmap (CoverCompose.cardBackdrop) at places chosen
 * once per track, so nothing ever moved. Drawn here they wander on slow, out-of-step paths while
 * the lock screen is lit, and fade out with the screen going to the AOD.
 */
final class CoverGlow extends View implements Choreographer.FrameCallback {
    /** How far a region wanders from the centre, as a share of the screen's width and height. */
    private static final float WANDER = 0.45f;
    /**
     * The baked regions (CoverBackdropPalette) were sized and toned for a still image: huge and
     * faint, a drift of them could not be seen. Smaller and stronger here.
     */
    private static final float RADIUS_SCALE = 0.65f;
    private static final float ALPHA_SCALE = 2.4f;
    /** Time constant of the fade in and out, in seconds. */
    private static final float FADE_TAU = 0.2f;
    private static final float[] FALLOFF = {1f, 0.8f, 0.45f, 0.15f, 0f};
    private static final float[] FALLOFF_AT = {0f, 0.25f, 0.5f, 0.75f, 1f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private Bitmap art;
    private float[][] regions = new float[0][];
    private RadialGradient[] shaders = new RadialGradient[0];
    private int shaderSize = -1;
    private int[] colors = new int[0];
    private float level;
    private long lastFrame;
    private boolean scheduled;

    CoverGlow(Context context) {
        super(context);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Something it shows by may have changed: start looking again. */
    void kick() {
        if (scheduled || !isAttachedToWindow()) return;
        scheduled = true;
        lastFrame = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private static boolean wanted(Bitmap art) {
        return art != null && !art.isRecycled() && Main.coverModeOn()
                && Main.effectiveCoverStyle().mode == CoverCardStyle.CARD
                && !Main.sHidePlayerBackground && Main.screenOnCached()
                && ClockCollapse.phase() != ClockCollapse.Phase.AOD;
    }

    @Override public void doFrame(long nowNs) {
        scheduled = false;
        if (!isAttachedToWindow()) return;
        Bitmap now = CoverCardLayer.currentArt();
        if (now != art) {
            art = now;
            if (now != null && !now.isRecycled()) palette(now);
        }
        float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        float want = wanted(art) ? 1f : 0f;
        level += (want - level) * (dt <= 0f ? 0f : (float) (1.0 - Math.exp(-dt / FADE_TAU)));
        if (Math.abs(want - level) < 0.01f) level = want;
        int visibility = level > 0f ? VISIBLE : GONE;
        if (getVisibility() != visibility) setVisibility(visibility);
        if (level > 0f) invalidate();
        // Running while shown or fading; a held-off glow only needs kick() to come back.
        if (level > 0f || want > 0f) {
            scheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void palette(Bitmap src) {
        Bitmap sample = Bitmap.createScaledBitmap(src, 32, 32, true);
        int[] pixels = new int[32 * 32];
        sample.getPixels(pixels, 0, 32, 0, 0, 32, 32);
        if (sample != src) sample.recycle();
        colors = CoverBackdropPalette.colors(pixels);
        regions = CoverBackdropPalette.regions(colors);
        shaderSize = -1;
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || regions.length == 0 || level <= 0f) return;
        float side = Math.max(w, h);
        if (shaderSize != (int) side) {
            shaderSize = (int) side;
            shaders = new RadialGradient[regions.length];
            for (int i = 0; i < regions.length; i++) {
                int rgb = vivid(colors[i % colors.length], i) & 0x00ffffff;
                float alpha = Math.min(0.9f, regions[i][3] * ALPHA_SCALE);
                // Eased falloff: a linear one shows the disc's edge.
                int[] stops = new int[FALLOFF.length];
                for (int k = 0; k < stops.length; k++) {
                    stops[k] = (Math.round(alpha * FALLOFF[k] * 255f) << 24) | rgb;
                }
                shaders[i] = new RadialGradient(0f, 0f, regions[i][2] * RADIUS_SCALE * side, stops,
                        FALLOFF_AT, Shader.TileMode.CLAMP);
            }
        }
        float t = SystemClock.uptimeMillis() / 1000f;
        paint.setAlpha(Math.round(255f * level));
        for (int i = 0; i < regions.length; i++) {
            // Two out-of-step waves per axis (periods 23-61s) never repeat a path: each region roams
            // the whole screen slowly. The region's own place only seeds its phases.
            float ax = regions[i][0] * 6.3f, ay = regions[i][1] * 6.3f;
            float cx = w * (0.5f + WANDER * (0.6f * wave(t, 41f + 11f * i, ax) + 0.4f * wave(t, 23f + 5f * i, ay)));
            float cy = h * (0.5f + WANDER * (0.6f * wave(t, 47f + 7f * i, ay) + 0.4f * wave(t, 29f + 9f * i, ax)));
            // Stretched and slowly turning, so it reads as a wash of colour, not a circle.
            matrix.setScale(1.5f, 0.75f);
            matrix.postRotate(t * 360f / (53f + 17f * i) + i * 60f);
            matrix.postTranslate(cx, cy);
            shaders[i].setLocalMatrix(matrix);
            paint.setShader(shaders[i]);
            canvas.drawRect(0f, 0f, w, h, paint);
        }
        paint.setShader(null);
    }

    private static float wave(float t, float period, float phase) {
        return (float) Math.sin(2 * Math.PI * t / period + phase);
    }

    /**
     * Over a blur of the same artwork its own colours vanish, saturation alone was not enough on a
     * one-hue cover: alternate deep and light regions so the drift reads as light moving.
     */
    private static int vivid(int color, int i) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 2f + 0.15f);
        hsv[2] = i % 2 == 0 ? 0.35f : 0.95f;
        return android.graphics.Color.HSVToColor(hsv);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        kick();
    }

    @Override protected void onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this);
        scheduled = false;
        super.onDetachedFromWindow();
    }
}
