package com.os4.musiccover;

import java.lang.reflect.Method;

/**
 * Whether the lock screen "has notifications", as every clock face is told it - corrected for
 * the rows nobody can see. Adapted from upstream (zyl6932/HyperMusicCover 8415b20).
 *
 * KeyguardPanelViewController.updateNotificationState(hasVisibleNotifsOnKeyguard) is the one
 * switch behind all of it: the hard-cut faces (rhombus and the eastern face built on it,
 * oriental A and C) swap to their notification template on it, the magazine and oriental B
 * faces to their notification layout, all_in_one hides its signature, the depth effect goes
 * off. And the system answers it from the stack, where the media card stands in for the media
 * island INVISIBLE and still laid out, and a notification let into an island keeps its row:
 * with nothing but the island on screen, every face folded as if a notification sat under it.
 *
 * Only ever turns the system's yes into a no, never the other way, and only while the mini
 * player's own reading of the stack says not one row is showing. When that changes - a real
 * notification arrives, a card comes out of its island - the system has no reason to say
 * anything new, its flag having been true all along, so the switch is thrown again from here
 * (recheck).
 */
final class NotifPresence {
    private NotifPresence() {}

    private static final String TAG = "[MCProbe] ";
    private static final String CLS_PANEL = "com.android.keyguard.panel.KeyguardPanelViewController";
    private static final String NAME = "access$updateNotificationState";

    private static Method sUpdate;
    private static Object sPanel;
    /** What the system last said, and what we passed on. */
    private static boolean sSystemSays;
    private static boolean sPassed;
    private static boolean sOurs;
    /** recheck() runs off every clock frame; at most this often. */
    private static long sCheckedAt;
    private static final long CHECK_MS = 50L;

    static void install(ClassLoader cl) {
        try {
            Class<?> panel = Xp.findClass(CLS_PANEL, cl);
            for (Method m : panel.getDeclaredMethods()) {
                if (m.getName().equals(NAME) && m.getParameterTypes().length == 2) {
                    m.setAccessible(true);
                    sUpdate = m;
                }
            }
            Xp.hookAll(panel, NAME, chain -> {
                Object[] args = chain.getArgs().toArray();
                if (args.length != 2 || !(args[1] instanceof Boolean)) return chain.proceed();
                sPanel = args[0];
                boolean says = (Boolean) args[1];
                // Our own re-throw carries the system's word, not a new one.
                if (!sOurs) sSystemSays = says;
                boolean pass = wanted(says);
                sPassed = pass;
                if (pass != says) args[1] = pass;
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            Xp.w(TAG + "notification presence hook failed: " + t);
        }
    }

    /**
     * What the faces should be told. While the cover (or an immersive page) holds the lock screen,
     * always yes: the cover's small clock is built on each face's notification layout, and with
     * the stack folded the rows read as unseen, the face fell back to its full layout and the
     * cover shrank that instead.
     */
    private static boolean wanted(boolean systemSays) {
        if (LockHold.clockHeld()) return true;
        if (!systemSays || !rowsShowing()) return false;
        // The media player alone, opened from the pill, under a face whose full layout still
        // fits above it: kept full. The classic face otherwise dropped its date and weather for
        // a player that left half the screen empty.
        return !(mediaOnly() && fullFaceFits());
    }

    private static boolean mediaOnly() {
        try {
            return MiniPlayerRuntime.mediaOnlyShowing();
        } catch (Throwable t) {
            return false;
        }
    }

    /** How low the face reaches in its full layout, on screen; measured whenever it shows it. */
    private static float sFullBottom = Float.NaN;
    /** The face that was measured: its clock view, by identity and size. */
    private static int sFullFace;

    private static boolean fullFaceFits() {
        if (Float.isNaN(sFullBottom) || sFullFace != faceKey()) return false;
        float top;
        try {
            top = MiniPlayerRuntime.stackContentTop();
        } catch (Throwable t) {
            return false;
        }
        android.view.View v = Main.sContainer;
        float gap = v == null ? 48f : FULL_GAP_DP * v.getResources().getDisplayMetrics().density;
        return !Float.isNaN(top) && sFullBottom + gap <= top;
    }

    private static final float FULL_GAP_DP = 16f;

    private static int faceKey() {
        int key = 17;
        for (android.view.View root : Main.clockRoots()) {
            android.view.View t = Main.clockTarget(root);
            key = key * 31 + (t == null ? 0 : System.identityHashCode(t));
        }
        return key;
    }

    /** The lowest piece the face draws now, on screen. Only while it shows its full layout. */
    private static void measureFull() {
        float bottom = Float.NaN;
        int[] at = new int[2];
        for (android.view.View root : Main.clockRoots()) {
            bottom = lowest(root, at, bottom);
        }
        if (!Float.isNaN(bottom)) {
            sFullBottom = bottom;
            sFullFace = faceKey();
        }
    }

    private static float lowest(android.view.View v, int[] at, float bottom) {
        if (v == null || v.getVisibility() != android.view.View.VISIBLE || v.getAlpha() <= 0.01f) return bottom;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) bottom = lowest(g.getChildAt(i), at, bottom);
            return bottom;
        }
        if (v.getWidth() <= 0 || v.getHeight() <= 0) return bottom;
        boolean draws = v instanceof android.widget.TextView
                ? ((android.widget.TextView) v).length() > 0
                : v instanceof android.widget.ImageView && ((android.widget.ImageView) v).getDrawable() != null;
        if (!draws) return bottom;
        v.getLocationOnScreen(at);
        float b = at[1] + v.getHeight() * v.getScaleY();
        return Float.isNaN(bottom) || b > bottom ? b : bottom;
    }

    /** Whether any notification row can be seen. Unknown is a yes: the system's word stands. */
    private static boolean rowsShowing() {
        try {
            return MiniPlayerRuntime.anyRowShowing();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Asked whenever the room under the clock is worked out again, which is whenever the rows
     * move: throws the switch again if what we passed on no longer matches.
     */
    static void recheck() {
        recheck(false);
    }

    /** @param force skip the rate limit - the cover has just let go, and no frame follows. */
    static void recheck(boolean force) {
        final Object panel = sPanel;
        final Method m = sUpdate;
        if (panel == null || m == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!force && now - sCheckedAt < CHECK_MS) return;
        sCheckedAt = now;
        // The full layout is on screen, with nothing of ours on the clock: how far it reaches.
        if (!shown(panel) && !LockHold.clockHeld()) measureFull();
        if (wanted(sSystemSays) == shown(panel)) return;
        android.view.View v = Main.sContainer;
        if (v == null) return;
        v.post(() -> {
            if (wanted(sSystemSays) == shown(panel)) return;
            sOurs = true;
            try {
                m.invoke(null, panel, sSystemSays);
            } catch (Throwable t) {
                Xp.w(TAG + "notification presence re-throw failed: " + t);
            } finally {
                sOurs = false;
            }
        });
    }

    /**
     * What the panel itself holds now. Read rather than remembered: the OEM ignores the call
     * off the keyguard (and when the value is unchanged), so what we passed is not always what
     * it has.
     */
    private static boolean shown(Object panel) {
        try {
            return (Boolean) Xp.getObjectField(panel, "hasNotification");
        } catch (Throwable t) {
            return sPassed;
        }
    }

    private static String safeSource() {
        try {
            return MiniPlayerRuntime.stackContentTop() + " " + MiniPlayerRuntime.stackContentSource();
        } catch (Throwable t) {
            return String.valueOf(t);
        }
    }

    static String describe() {
        return "system=" + sSystemSays + " passed=" + sPassed + " rows=" + rowsShowing()
                + " mediaOnly=" + mediaOnly() + " fullBottom=" + sFullBottom + " fits=" + fullFaceFits()
                + " hooked=" + (sUpdate != null) + " stack=" + safeSource();
    }
}
