package com.os4.musiccover;

import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The MIX Flip's outer screen lock screen, SystemUI's "tiny keyguard".
 *
 * Its picture is not the wallpaper engine's: each lock style (BaseTinyTemplateView) paints its
 * own crop in an ImageView (mWallpaperLayer), with an overlay and a depth subject above it, all
 * over the wallpaper window that already holds the cover. While the cover backdrop is on they
 * go transparent, so the outer screen shows the same cover the inner one does.
 *
 * shortcut: only the backdrop; the cover card, lyrics and controls are not on the outer screen
 * yet, the OEM's small media card stays.
 */
final class TinyTemplate {

    private TinyTemplate() {
    }

    private static final String CLS = "com.android.keyguard.tinyPanel.templates.BaseTinyTemplateView";
    private static final String CLS_CONTROLLER =
            "com.android.keyguard.tinyPanel.TinyKeyguardPanelViewControllerImpl";
    private static final String[] LAYERS = {"mWallpaperLayer", "mWallpaperCoverLayer", "mDepthViewLayer"};
    private static final List<WeakReference<View>> sViews = new ArrayList<>();

    static void install(ClassLoader cl) {
        try {
            Class<?> cls = Xp.findClass(CLS, cl);
            Xp.hookAllConstructors(cls, chain -> {
                Object r = chain.proceed();
                synchronized (sViews) {
                    sViews.add(new WeakReference<>((View) chain.getThisObject()));
                }
                return r;
            });
            // The layers are made and refilled here, after a fold and on every style change.
            Xp.hookAll(cls, "bindWallpaperAndDepth", chain -> {
                Object r = chain.proceed();
                apply((View) chain.getThisObject(), covered());
                return r;
            });
            // The controller paints the template pager black on every lock screen show, under
            // the templates' own pictures; with those see-through the black would be all there is.
            Xp.hookAll(Xp.findClass(CLS_CONTROLLER, cl), "updateKeyguardWallpaperStateAnim", chain -> {
                Object r = chain.proceed();
                if (Boolean.TRUE.equals(chain.getArgs().get(1))) sync();
                return r;
            });
            Xp.log(Main.TAG + "tiny keyguard hooked");
        } catch (Throwable t) {
            // Every phone without an outer screen lands here.
            Xp.log(Main.TAG + "tiny keyguard not hooked: " + t);
        }
    }

    private static boolean covered() {
        return Main.coverModeOn() && !CoverBackdrop.hidden();
    }

    static void sync() {
        boolean on = covered();
        synchronized (sViews) {
            for (int i = sViews.size() - 1; i >= 0; i--) {
                View v = sViews.get(i).get();
                if (v == null) sViews.remove(i);
                else v.post(() -> apply(v, on));
            }
        }
    }

    private static void apply(View template, boolean on) {
        // shortcut: the black is not put back when the cover goes; the template's own full-screen
        // picture is opaque again by then. Restore it if a style turns out to rely on it.
        if (on) {
            int pager = template.getResources().getIdentifier(
                    "tiny_keyguard_templates_viewpager", "id", "com.android.systemui");
            for (android.view.ViewParent p = template.getParent(); p instanceof View; p = p.getParent()) {
                if (((View) p).getId() == pager) {
                    ((View) p).setBackgroundColor(0);
                    break;
                }
            }
        }
        for (String name : LAYERS) {
            try {
                Object layer = Xp.getObjectField(template, name);
                if (layer instanceof View) ((View) layer).setAlpha(on ? 0f : 1f);
            } catch (Throwable ignored) {
                // A style without that layer.
            }
        }
        try {
            clockRow(template);
        } catch (Throwable t) {
            // Never SystemUI's crash: a style laid out unlike the classic one is left alone.
            Xp.log(Main.TAG + "tiny clock row skipped: " + t);
        }
    }

    // ------------------------------------------------------------------ the clock row

    private static final java.util.Map<View, Boolean> sWatched = new java.util.WeakHashMap<>();

    private static int id(View v, String name) {
        return v.getResources().getIdentifier(name, "id", "com.android.systemui");
    }

    /**
     * The outer screen's clock the iOS way: the big time shrunk to the date's size and set before
     * it on its line, the status (charging) after it, the face icon at the date's left edge. Moved
     * by transforms only, which the OEM's tiny clock never sets (it animates alpha), so its own
     * layout and minute updates stand; each of those lays the views out again and this re-runs.
     */
    private static void clockRow(View template) {
        View layer;
        try {
            layer = (View) Xp.getObjectField(template, "mTinyClockLayer");
        } catch (Throwable t) {
            return;
        }
        if (layer == null) return;
        // The pets and doodle styles use their own views under the same ids: TextViews only.
        View clockView = layer.findViewById(id(layer, "clock_time"));
        View dateView = layer.findViewById(id(layer, "date_week"));
        TextView clock = clockView instanceof TextView ? (TextView) clockView : null;
        TextView date = dateView instanceof TextView ? (TextView) dateView : null;
        View status = layer.findViewById(id(layer, "classic_battery"));
        View panel = template.getRootView().findViewById(id(layer, "tiny_keyguard_panel_view"));
        View face = panel == null ? null : panel.findViewById(id(layer, "miui_keyguard_face_unlock_view"));
        for (View v : new View[]{clock, date, status, face}) {
            if (v != null && sWatched.put(v, Boolean.TRUE) == null) {
                v.addOnLayoutChangeListener((x, l, t, r, b, ol, ot, or, ob) -> {
                    try {
                        clockRow(template);
                    } catch (Throwable e) {
                        Xp.log(Main.TAG + "tiny clock row skipped: " + e);
                    }
                });
            }
        }
        if (clock == null || date == null || date.getWidth() == 0) return;
        float gap = date.getPaint().measureText(" ");
        float dateX = date.getLeft() + date.getTotalPaddingLeft();
        float shift = 0f;
        if (Main.sFlipClock && clock.getTextSize() > 0f) {
            float s = date.getTextSize() / clock.getTextSize();
            clock.setPivotX(0f);
            clock.setPivotY(0f);
            clock.setScaleX(s);
            clock.setScaleY(s);
            clock.setTranslationX(dateX - (clock.getLeft() + clock.getTotalPaddingLeft() * s));
            clock.setTranslationY(date.getTop() + date.getBaseline()
                    - (clock.getTop() + clock.getBaseline() * s));
            shift = clock.getPaint().measureText(clock.getText().toString()) * s + gap;
        } else {
            clock.setScaleX(1f);
            clock.setScaleY(1f);
            clock.setTranslationX(0f);
            clock.setTranslationY(0f);
        }
        date.setTranslationX(shift);
        // Down by half the date's height: the OEM's row sits right under the face icon. A fixed
        // step, because the icon's layer has its own resting translation and an entrance animation,
        // and a gap measured off either came out wrong (too wide, then wider still mid-entrance).
        float drop = Main.sFlipClock ? date.getTextSize() * 0.5f : 0f;
        date.setTranslationY(drop);
        if (Main.sFlipClock) clock.setTranslationY(clock.getTranslationY() + drop);
        // The OEM's battery gives way to HMC-E's own status, drawn in the date's overlay so the
        // glass the date is drawn through is the status's too. Scaled away, not faded: the OEM
        // animates the battery's alpha itself.
        if (status != null) {
            float k = Main.sFlipStatus ? 0f : 1f;
            status.setScaleX(k);
            status.setScaleY(k);
        }
        StatusDrawable.on(date, Main.sFlipStatus, layer);
        if (face != null && face.getWidth() > 0) {
            float tx = 0f;
            if (Main.sFlipFaceLeft) {
                // The icon is drawn centred in a strip as wide as the screen's free left part.
                float icon = Math.min(face.getHeight(), face.getWidth());
                float faceLeft = laidOut(face)[0] + (face.getWidth() - icon) / 2f;
                float dateLeft = laidOut(date)[0] + date.getTotalPaddingLeft();
                tx = dateLeft - faceLeft;
            }
            face.setTranslationX(tx);
        }
    }

    /**
     * Where the layout put [v] in its window, translations left out: the screen position moves
     * with the panel's own entrance animation, and a gap measured mid-way stayed that size.
     */
    private static float[] laidOut(View v) {
        float x = 0f, y = 0f;
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            x += p.getLeft() - (p == v ? 0 : p.getScrollX());
            y += p.getTop() - (p == v ? 0 : p.getScrollY());
        }
        return new float[]{x, y};
    }

    /** The status after the outer date: do-not-disturb and charging, in the date's own paint. */
    private static final class StatusDrawable extends android.graphics.drawable.Drawable {
        private static final java.util.Map<TextView, StatusDrawable> ON = new java.util.WeakHashMap<>();
        private final TextView date;
        private final android.text.TextPaint paint =
                new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        private StatusDrawable(TextView date) {
            this.date = date;
        }

        static void on(TextView date, boolean want, View layer) {
            StatusDrawable d = ON.get(date);
            if (!want) {
                if (d != null) {
                    date.getOverlay().remove(d);
                    ON.remove(date);
                }
                return;
            }
            if (d == null) {
                d = new StatusDrawable(date);
                ON.put(date, d);
                date.getOverlay().add(d);
                DateStatus.setOuterListener(TinyTemplate::sync);
                // Drawn past the date's right edge: nothing between it and the clock layer clips.
                for (View v = date; v != null && v != layer && v.getParent() instanceof android.view.ViewGroup;
                     v = (View) v.getParent()) {
                    ((android.view.ViewGroup) v.getParent()).setClipChildren(false);
                }
            }
            // The overlay draws inside the date's bounds only: the date is widened, from its left,
            // by the status. Its text starts at the left, so nothing in it moves; the next
            // layout puts the width back and this, run from that layout, widens it again.
            d.paint.set(date.getPaint());
            float need = date.getTotalPaddingLeft() + d.paint.measureText(date.getText().toString())
                    + d.paint.measureText(" ") + DateStatus.drawOuter(null, d.paint,
                    DateStatus.outerSegments(date.getContext()), date.getHeight()) + 2f;
            if (date.getWidth() < need) date.setRight(date.getLeft() + (int) Math.ceil(need));
            d.setBounds(0, 0, date.getWidth(), date.getHeight());
            d.invalidateSelf();
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            try {
                paint.set(date.getPaint());
                paint.setColor(date.getCurrentTextColor());
                float x = date.getTotalPaddingLeft() + paint.measureText(date.getText().toString())
                        + paint.measureText(" ");
                canvas.save();
                canvas.translate(x, 0f);
                DateStatus.drawOuter(canvas, paint, DateStatus.outerSegments(date.getContext()),
                        date.getHeight());
                canvas.restore();
            } catch (Throwable t) {
                Xp.log(Main.TAG + "tiny status not drawn: " + t);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
