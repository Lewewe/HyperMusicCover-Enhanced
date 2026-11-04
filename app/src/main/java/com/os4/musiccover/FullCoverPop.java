package com.os4.musiccover;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * The full-screen cover growing and sharpening in place, to and from the player alone.
 *
 * The picture itself is the wallpaper, in another process, which can only crossfade, about 150ms
 * after the tap and over 370ms. So a copy of its sharp band moves instead, under the clock and the
 * media card, over a copy of its backdrop: without that the wallpaper showed through, the same
 * picture at another size. The backdrop crossfades between the cover's and the frosted one, as the
 * wallpaper does beneath it. Everything goes once the wallpaper has caught up.
 */
final class FullCoverPop {
    // shortcut: how the lock screen's wallpaper differs from the band's own box (larger about the
    // screen's centre, and lower), measured on the Xiaomi 17 Pro. Read it from the OEM if the
    // hand-over shows a double image on another phone.
    private static final float WALLPAPER_ZOOM = 1.048f;
    private static final float WALLPAPER_SHIFT = 0.0045f;
    private static final float MIN_SCALE = 0.88f;
    private static final float BLUR_DP = 16f;
    private static final long GROW_MS = 420L;
    private static final long FROST_MS = 500L;
    // shortcut: the frosted copy came out darker than the wallpaper's own, by 24 against 26 of 255
    // on one cover, and short of it would step at the hand-over. Find why if another cover steps.
    private static final float FROST_ALPHA = 0.87f;
    private static final long SHRINK_MS = 360L;
    /** From the tap: when the wallpaper has finished its crossfade. */
    private static final long HAND_OVER_AT_MS = 640L;
    private static final long HAND_OVER_MS = 160L;
    private static final int BACKDROP_DOWNSCALE = 2;

    private static View sView;
    private static ValueAnimator sAnim;

    private FullCoverPop() {}

    static void play(boolean grow) {
        cancel();
        if (Main.coverMorphCardMode()) return;
        ViewGroup layer = CoverPush.coverLayer();
        Bitmap art = Main.coverMorphSource();
        CoverMorphMotion.Box box = art == null ? null : Main.coverMorphTarget(art);
        if (layer == null || box == null || box.w <= 0f || box.h <= 0f
                || Main.sScreenW <= 0 || Main.sScreenH <= 0) return;

        FrameLayout host = new FrameLayout(layer.getContext());
        int backW = Main.sScreenW / BACKDROP_DOWNSCALE, backH = Main.sScreenH / BACKDROP_DOWNSCALE;
        // Opaque from the first frame. Over the wallpaper itself the band's feathered edges were
        // laid on the same edges in the wallpaper, stronger than either, and those flashed.
        backdrop(host, CoverCompose.composedBackdrop(art, backW, backH, Main.sBias));
        ImageView frost = backdrop(host, CoverCompose.frostedFor(art, backW, backH, Main.sBias, false));
        frost.setAlpha(grow ? FROST_ALPHA : 0f);

        int w = Math.round(box.w * WALLPAPER_ZOOM), h = Math.round(box.h * WALLPAPER_ZOOM);
        float centreY = Main.sScreenH / 2f
                + (box.y + box.h / 2f - Main.sScreenH / 2f) * WALLPAPER_ZOOM
                + Main.sScreenH * WALLPAPER_SHIFT;
        int[] at = new int[2];
        layer.getLocationOnScreen(at);
        ImageView band = new ImageView(layer.getContext());
        band.setImageBitmap(CoverCompose.feathered(art, w, h,
                Math.round(Math.min(240f, box.h / 4f) * WALLPAPER_ZOOM)));
        band.setX(box.x + (box.w - w) / 2f - at[0]);
        band.setY(centreY - h / 2f - at[1]);
        band.setPivotX(w / 2f);
        band.setPivotY(h / 2f);
        look(band, grow ? 0f : 1f);
        host.addView(band, new FrameLayout.LayoutParams(w, h));
        layer.addView(host, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sView = host;

        frost.animate().alpha(grow ? 0f : FROST_ALPHA).setDuration(FROST_MS);
        long duration = grow ? GROW_MS : SHRINK_MS;
        ValueAnimator anim = ValueAnimator.ofFloat(grow ? 0f : 1f, grow ? 1f : 0f);
        anim.setDuration(duration);
        // Leaving, it starts from the picture on screen: decelerating, most of the blur landed on
        // the first two frames, and that flashed.
        anim.setInterpolator(grow ? new DecelerateInterpolator(1.6f) : new AccelerateDecelerateInterpolator());
        anim.addUpdateListener(a -> look(band, (Float) a.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(android.animation.Animator a) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (cancelled || sView != host) return;
                host.animate().alpha(0f).setStartDelay(HAND_OVER_AT_MS - duration)
                        .setDuration(HAND_OVER_MS).withEndAction(FullCoverPop::cancel);
            }
        });
        sAnim = anim;
        anim.start();
    }

    private static ImageView backdrop(FrameLayout host, Bitmap picture) {
        ImageView view = new ImageView(host.getContext());
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        view.setImageBitmap(picture);
        // At the wallpaper's own size too: it is darker or lighter by the place on the screen.
        view.setScaleX(WALLPAPER_ZOOM);
        view.setScaleY(WALLPAPER_ZOOM);
        view.setTranslationY(Main.sScreenH * WALLPAPER_SHIFT);
        host.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    /** t 0: small, clear and blurred. t 1: the wallpaper's own picture. */
    private static void look(View view, float t) {
        view.setAlpha(t);
        float scale = MIN_SCALE + (1f - MIN_SCALE) * t;
        view.setScaleX(scale);
        view.setScaleY(scale);
        float radius = (1f - t) * BLUR_DP * view.getResources().getDisplayMetrics().density;
        view.setRenderEffect(radius < 0.5f ? null : RenderEffect.createBlurEffect(
                radius, radius, Shader.TileMode.DECAL));
    }

    private static void cancel() {
        ValueAnimator anim = sAnim;
        sAnim = null;
        if (anim != null) anim.cancel();
        View view = sView;
        sView = null;
        if (view == null) return;
        view.animate().cancel();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }
}
