package com.os4.musiccover;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

/** A native Notes button beside the track details, owned only by the lock-screen card. */
final class LyricsButton {
    private static ViewGroup host;
    private static ImageView button;
    private static LyricsSheetDrawable notesDrawable;
    private static ValueAnimator animator;
    private static float progress;
    private static boolean requested, canvasControls;
    /** Laid out as Canvas lays it, under the thumbnail beside the controls, whatever the tap does. */
    private static boolean besideControls;
    /** The slot it is drawn in: it changes slot at the bottom of a quick shrink-and-fade. */
    private static boolean shownBeside;
    /** How much of it the swap leaves visible: 1 at rest, 0 at the moment it changes slot. */
    private static float swapLook = 1f;
    /** The room the title gives it, eased over the whole swap so the title glides, never jumps. */
    private static float titleShare, titleFrom;
    private static ValueAnimator swap;
    private static boolean swapPastMiddle;
    /** Where it last rested, and where a jump of its spot holds it until it has faded out. */
    private static float[] restAt, heldAt;
    private static final float JUMP_DP = 6f;
    private static int tint;
    private static final float SIZE_DP = 40f;
    private static final float GAP_DP = 6f;
    private static final float APPEAR_SCALE = 0.6f;
    private static final float BLUR_DP = 8f;
    private static final long SWAP_MS = 220L;

    private LyricsButton() {}

    static float update(View card, boolean show, boolean controls, boolean beside) {
        if (!(card instanceof ViewGroup)) return 0f;
        if (host != card) release();
        canvasControls = controls;
        besideControls = controls || beside;
        if (button == null || progress <= 0f) {
            // Nothing on screen to swap: it appears where it belongs.
            if (swap != null) swap.cancel();
            swap = null;
            shownBeside = besideControls;
            swapLook = 1f;
            titleShare = besideControls ? 0f : 1f;
        } else if (shownBeside != besideControls && swap == null) {
            // Between the title and the controls it does not travel: it shrinks, fades and blurs
            // away where it was and grows back the same way where it now belongs, quickly.
            startSwap();
        }
        if (button == null && show) {
            host = (ViewGroup) card;
            button = new ImageView(card.getContext());
            button.setId(View.generateViewId());
            notesDrawable = new LyricsSheetDrawable();
            button.setImageDrawable(notesDrawable);
            button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            float density = card.getResources().getDisplayMetrics().density;
            int pad = Math.round(8f * density);
            button.setPadding(pad, pad, pad, pad);
            String language = card.getResources().getConfiguration().getLocales().get(0).getLanguage();
            button.setContentDescription("zh".equals(language) ? "显示歌词" : "Show lyrics");
            button.setOnClickListener(v -> { if (canvasControls) Main.toggleCanvasLyrics(); else Main.openLyricsFromButton(); });
            button.setAlpha(0f);
            button.setScaleX(APPEAR_SCALE);
            button.setScaleY(APPEAR_SCALE);
            button.setVisibility(View.INVISIBLE);
            int size = Math.round(SIZE_DP * density);
            host.addView(button, new ViewGroup.LayoutParams(size, size));
        }
        if (button == null) return 0f;
        // Filled while the lyrics show, on every page: the button is their switch both ways.
        notesDrawable.setFilled(LockLyrics.wantsWindow());
        if (requested != show) {
            requested = show;
            button.setClickable(show);
            button.setFocusable(show);
            button.setImportantForAccessibility(show ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (animator != null) animator.cancel();
            button.setVisibility(View.VISIBLE);
            animator = ValueAnimator.ofFloat(progress, show ? 1f : 0f);
            animator.setDuration(show ? 260L : 200L);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                applyLook();
            });
            animator.start();
        }
        boolean hide = LockLyrics.wantsWindow();
        boolean chinese = "zh".equals(card.getResources().getConfiguration().getLocales().get(0).getLanguage());
        button.setContentDescription(chinese ? (hide ? "隐藏歌词" : "显示歌词") : (hide ? "Hide lyrics" : "Show lyrics"));
        // The title gives the button room only while it is drawn by the title, eased across the
        // whole swap so the title glides rather than jumping.
        return (SIZE_DP + GAP_DP) * card.getResources().getDisplayMetrics().density * progress * titleShare;
    }

    private static void startSwap() {
        swapPastMiddle = false;
        titleFrom = titleShare;
        swap = ValueAnimator.ofFloat(0f, 1f);
        swap.setDuration(SWAP_MS);
        swap.setInterpolator(new LinearInterpolator());
        swap.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            if (t >= .5f) {
                swapPastMiddle = true;
                shownBeside = besideControls;
            }
            swapLook = Math.abs(1f - 2f * t);
            float eased = 1f - (1f - t) * (1f - t);
            titleShare = titleFrom + ((besideControls ? 0f : 1f) - titleFrom) * eased;
            if (t >= 1f) swap = null;
            applyLook();
            Main.refreshMediaCardForMorph();
        });
        swap.start();
    }

    /**
     * Grows out of its own centre where it rests (place keeps it there), blurred and faded in;
     * going, the same way back. A slot swap uses the very same look, only quicker.
     */
    private static void applyLook() {
        ImageView view = button;
        if (view == null) return;
        float look = progress * swapLook;
        view.setAlpha(look);
        float scale = APPEAR_SCALE + (1f - APPEAR_SCALE) * look;
        view.setScaleX(scale);
        view.setScaleY(scale);
        float radius = (1f - look) * BLUR_DP * view.getResources().getDisplayMetrics().density;
        view.setRenderEffect(radius < 0.5f ? null : RenderEffect.createBlurEffect(
                radius, radius, Shader.TileMode.DECAL));
        if (!requested && progress == 0f) view.setVisibility(View.GONE);
        if (host != null) host.postInvalidateOnAnimation();
    }

    static void place(View card, TextView title, TextView artist, View artwork) {
        if (button == null || host != card || title == null || progress <= 0f) return;
        Layout layout = title.getLayout();
        if (layout == null || layout.getLineCount() == 0) return;
        float density = card.getResources().getDisplayMetrics().density;
        int size = Math.round(SIZE_DP * density);
        float titleX = position(card, title, true) + title.getPaddingLeft() + layout.getLineLeft(0);
        float top = position(card, title, false);
        float bottom = artist != null && artist.isShown()
                ? position(card, artist, false) + artist.getHeight() : top + title.getHeight();
        if (!Float.isFinite(titleX) || !Float.isFinite(top) || !Float.isFinite(bottom)) return;
        // Where it rests whatever the progress: the title is pushed right by the inset update()
        // returns (its share of the progress), and following the title it slid in from the left.
        float left = titleX - (SIZE_DP + GAP_DP) * density * progress * titleShare;
        float y = (top + bottom - size) / 2f;
        if (shownBeside) {
            float artLeft = artwork == null ? 16f * density : position(card, artwork, true);
            float artWidth = artwork == null ? size : artwork.getWidth();
            float besideLeft = artLeft + (artWidth - size) / 2f;
            View action = null;
            for (String name : new String[]{"actionPlayPause", "actionPrev", "actionNext", "action0", "media_play_pause"}) {
                int id = card.getResources().getIdentifier(name, "id", "com.android.systemui");
                View candidate = id == 0 ? null : card.findViewById(id);
                if (candidate != null && candidate.isShown()) { action = candidate; break; }
            }
            float centre = action == null ? card.getHeight() * .6f
                    : position(card, action, false) + action.getHeight() / 2f;
            left = besideLeft;
            y = centre - size / 2f;
        }
        // Wherever its spot jumps - the controls row of the Canvas player moving, say - it does
        // not ride along: it holds where it was, shrinks away and grows back at the new spot.
        float jump = JUMP_DP * density;
        if (!shownBeside) restAt = null;
        if (swap == null && shownBeside && restAt != null && progress >= 1f
                && (Math.abs(left - restAt[0]) > jump || Math.abs(y - restAt[1]) > jump)) {
            heldAt = restAt;
            startSwap();
        }
        if (swap == null) heldAt = null;
        if (heldAt != null && !swapPastMiddle) {
            left = heldAt[0];
            y = heldAt[1];
        } else restAt = new float[]{left, y};
        // Let the OEM parent own layout; transforms also keep the click target aligned.
        button.setTranslationX(Math.round(left) - button.getLeft());
        button.setTranslationY(Math.round(y) - button.getTop());
        int color = title.getCurrentTextColor();
        if (tint != color) {
            tint = color;
            button.setImageTintList(ColorStateList.valueOf(color));
        }
    }

    /** Shown, or on its way in, beside the title rather than under the thumbnail. */
    static boolean byTitle() {
        return button != null && requested && !besideControls;
    }

    private static float position(View card, View view, boolean horizontal) {
        float value = 0f;
        while (view != null && view != card) {
            value += horizontal ? view.getLeft() + view.getTranslationX()
                    : view.getTop() + view.getTranslationY();
            if (!(view.getParent() instanceof View)) return Float.NaN;
            view = (View) view.getParent();
        }
        return view == card ? value : Float.NaN;
    }

    static void release() {
        if (animator != null) animator.cancel();
        animator = null;
        if (host != null && button != null) host.removeView(button);
        host = null;
        button = null;
        notesDrawable = null;
        requested = false;
        canvasControls = false;
        besideControls = false;
        if (swap != null) swap.cancel();
        swap = null;
        shownBeside = false;
        swapLook = 1f;
        swapPastMiddle = false;
        titleFrom = titleShare;
        restAt = heldAt = null;
        titleShare = titleFrom = 0f;
        progress = 0f;
        tint = 0;
    }
}
