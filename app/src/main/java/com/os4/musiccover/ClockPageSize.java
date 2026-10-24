package com.os4.musiccover;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.function.Predicate;

/**
 * The cover clock's size for the page on screen, and the move from one size to the next.
 *
 * Its own runtime, apart from ClockCollapse, which places and springs the clock: this only says
 * how big. The big cover keeps the cover's clock, scaled for its artwork's shape; the pages that
 * leave the cover's artwork small - the player alone, the lyrics, the companion, a Canvas scene -
 * take the face's own native clock, and a wide artwork that leaves it room does too. Between two
 * sizes it moves on its own 420ms animator, from the clock as drawn.
 *
 * Kept in one place because the size was read where it was not yet decided: an entry onto the
 * player page set its end from the cover's size, a frame before the page asked for the native
 * one, and the clock landed wider than the screen (2026-10-24).
 */
final class ClockPageSize {
    private ClockPageSize() {}

    /** 0 the cover's own size, 1 the face's native size; in between while it moves. */
    private static float sNative;
    private static boolean sNativeTarget;
    /** The cover clock's scale for its artwork's shape (CoverCardStyle.clockScale). */
    private static float sScale = 1f;
    private static float sScaleTarget = 1f;
    /** Where the clock stood as it took the native size: it keeps that top as its glyphs change. */
    private static float sTop = Float.NaN;
    private static float sDate = Float.NaN;
    private static ValueAnimator sAnimator;
    /** The move's start, the unit as drawn, and how far it has got. */
    private static float sFromUnit = Float.NaN;
    private static float sProgress = 1f;

    /** Whether the page on screen wants the face's native clock rather than the cover's. */
    private static boolean pageWantsNative() {
        return (LockLyrics.compactWithoutLyricsOnEntry() || LockLyrics.companionPage()
                || CanvasHostBridge.nativeClockScene())
                && !ImmersiveHost.holdsClock();
    }

    /**
     * Brings the size to the page's. Out of cover mode ([active] false) it is set outright for
     * the next entry; in it, it moves from the clock as drawn ([on]: the pose is the one on
     * screen), [refresh] asked on each step.
     *
     * @param landscapeFits whether the native clock leaves room under it for this artwork aspect
     */
    static void update(boolean active, boolean on, float poseUnit, float poseTop, float poseDate,
                       Predicate<Float> landscapeFits, Runnable refresh) {
        boolean compact = pageWantsNative();
        Bitmap art = CoverCardLayer.currentArt();
        boolean coverVisible = Main.effectiveCoverStyle().mode == CoverCardStyle.CARD
                && !LockLyrics.wantsCompactArtwork() && !ImmersiveHost.holdsClock()
                && art != null && !art.isRecycled();
        float aspect = coverVisible ? CoverCardStyle.aspect(art.getWidth(), art.getHeight()) : 1f;
        float scale = CoverCardStyle.clockScale(aspect);
        boolean nativeSize = compact || coverVisible && CoverCardStyle.usesNativeClock(aspect)
                && landscapeFits.test(aspect);
        if (!active) {
            if (sAnimator != null) sAnimator.cancel();
            sAnimator = null;
            sNativeTarget = nativeSize;
            sScale = sScaleTarget = scale;
            sNative = nativeSize ? 1f : 0f;
            sFromUnit = Float.NaN;
            sProgress = 1f;
            sTop = sDate = Float.NaN;
            return;
        }
        if (nativeSize == sNativeTarget && Math.abs(scale - sScaleTarget) < 0.001f) return;
        sNativeTarget = nativeSize;
        sScaleTarget = scale;
        if (sAnimator != null) sAnimator.cancel();
        // From the drawn, notification-squeezed clock, not its hidden full-size endpoint.
        sFromUnit = on ? poseUnit : Float.NaN;
        sProgress = 0f;
        if (nativeSize && Float.isNaN(sTop) && on) {
            sTop = poseTop;
            sDate = poseDate;
        }
        final float fromNative = sNative, toNative = nativeSize ? 1f : 0f;
        final float fromScale = sScale, toScale = scale;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        sAnimator = animator;
        animator.setDuration(420L);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a -> {
            float progress = (float) a.getAnimatedValue();
            sProgress = progress;
            sNative = fromNative + (toNative - fromNative) * progress;
            sScale = fromScale + (toScale - fromScale) * progress;
            if (sNative == 0f) sTop = sDate = Float.NaN;
            refresh.run();
        });
        animator.start();
    }

    /** How native the size is now, 0 to 1. */
    static float nativeProgress() {
        return sNative;
    }

    /** The native size is where it is headed. */
    static boolean nativeTarget() {
        return sNativeTarget;
    }

    /** The native size is where it is headed, or all but there. */
    static boolean nativeOrHeaded() {
        return sNativeTarget || sNative >= 0.999f;
    }

    /** The artwork's scale on the cover clock, as it is now. */
    static float artworkScale() {
        return sScale;
    }

    /**
     * The cover clock's unit for this page: the cover's size, scaled for the artwork, toward the
     * face's native unit by how native it is - and while a move runs on screen, from the unit it
     * was drawn at to the one it is headed for.
     */
    static float coverUnit(float requested, float reference, float nativeUnit, float minK, boolean on) {
        float unit = ClockSizePolicy.coverUnit(requested, reference, nativeUnit, sScale, minK, sNative);
        if (on && sProgress < 1f && Float.isFinite(sFromUnit) && sFromUnit > 0f) {
            float destination = ClockSizePolicy.coverUnit(requested, reference, nativeUnit,
                    sScaleTarget, minK, sNativeTarget ? 1f : 0f);
            unit = ClockSizePolicy.transitionUnit(sFromUnit, destination, sProgress);
        }
        return unit;
    }

    /** The top the clock keeps while native, or NaN: the OEM's glyph sizing moves its layout. */
    static float keptTop() {
        return sNative > 0f ? sTop : Float.NaN;
    }

    /** The date's top kept with it, or NaN. */
    static float keptDate() {
        return sNative > 0f ? sDate : Float.NaN;
    }

    /** The editor's geometry changed: a kept top belongs to the old one. */
    static void forgetKeptPose() {
        sTop = sDate = Float.NaN;
    }

    /**
     * The OEM height the size asks for, in the OEM's own timeHeight units: a fraction of
     * adaptTimeHeight. NaN when no size is set and the cover's own is wanted - the dp default is
     * small enough that the floor is always right for it.
     */
    static float wantedOemHeight(float min, float max, float size) {
        if (!(max > 0f)) return Float.NaN;
        if (Float.isNaN(size) && sNative == 0f) return Float.NaN;
        float base = Float.isNaN(size) ? min : Math.max(min, Math.min(max, size * sScale * max));
        return base + (max - base) * sNative;
    }

    static String describe() {
        return "native=" + Main.r2(sNative) + (sNativeTarget ? "->1" : "->0")
                + " scale=" + Main.r2(sScale) + "->" + Main.r2(sScaleTarget)
                + " move=" + Main.r2(sProgress) + " keptTop=" + Main.r1(sTop);
    }
}
