package com.os4.musiccover;

import android.view.View;

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
    }
}
