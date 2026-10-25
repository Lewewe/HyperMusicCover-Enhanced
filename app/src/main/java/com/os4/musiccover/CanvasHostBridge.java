package com.os4.musiccover;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Optional extension handshake; without a ready Canvas the normal cover path is untouched. */
final class CanvasHostBridge {
    private static String activeTrack = "";
    private static boolean verified, canvasReady;
    private static long lastFrame;
    private static Context sceneContext;
    private static Boolean lastScene;
    private static Boolean lastVisible;
    private static boolean clockOwned;
    private static long resumeUntil;
    private static boolean backdropHeld;
    private static String backdropTrack = "";
    private static boolean backdropAllowed = true;
    private static boolean showInPill;
    private static boolean frozenInAod;
    private static boolean statusCanvas;
    /** When the awake lock screen last started reporting no Spotify session; 0 = it has one. */
    private static long sessionGoneSince;
    /** Longer than a buffering gap between tracks; past it the session has ended. */
    private static final long SESSION_GONE_MS = 2500L;
    /** The extension's last word, for a release decided between its frames. */
    private static boolean lastReady, lastTemporary;
    static void suspendForBackdrop() {
        if (!Main.sHidePlayerBackground || !canvasReady && !frozenInAod
                && !backdropHeld && !clockOwned) return;
        canvasReady = frozenInAod = backdropHeld = false;
        syncDepth();
        activeTrack = backdropTrack = "";
        resumeUntil = 0L;
        if (clockOwned) {
            clockOwned = false;
            LockHold.give(LockHold.Owner.CANVAS, Main.screenOnCached());
        }
        Main.refreshCanvasControls();
        refreshStatusColors();
    }
    static boolean keepsClockInAod() {
        return !MiniPlayerRuntime.canvasPlayerInPill()
                && (canvasReady || frozenInAod);
    }
    static boolean hasBackgroundScene() { return canvasReady; }
    /** The extension already supplies its own live or frozen video backdrop. */
    /** Whether the Canvas has the wallpaper's cut-out subject hidden (syncDepth). */
    private static boolean depthHeld;

    /**
     * A depth-effect wallpaper's cut-out subject is drawn in SystemUI's window, over the Canvas
     * seen through it: with the player in the pill nothing else hides it, and the subject stood
     * on top of the video. It goes while the Canvas owns the backdrop, and comes back with it
     * leaving unless the cover has it hidden anyway.
     */
    private static void syncDepth() {
        boolean want = ownsVideoBackdrop();
        if (want == depthHeld) return;
        depthHeld = want;
        if (want) Main.setDepthHidden(true);
        else if (!Main.sCoverMode) Main.setDepthHidden(false);
    }

    static boolean ownsVideoBackdrop() {
        return (canvasReady || frozenInAod)
                && (showInPill || !MiniPlayerRuntime.canvasPlayerInPill());
    }
    static boolean keepPaletteBackdrop() {
        if (MiniPlayerRuntime.canvasPlayerInPill())
            return showInPill && canvasReady && backdropAllowed && Main.screenOnCached();
        if (frozenInAod && !Main.screenOnCached()) return true;
        return Main.screenOnCached() && backdropAllowed && (active() || nativeClockScene());
    }
    static void backdropRetained() {
        // Keeping the outgoing bitmap does not mean artwork for the new track was loaded.
        backdropHeld = true;
    }
    static boolean backgroundOnly() {
        return active() || nativeClockScene();
    }
    static boolean nativeClockScene() { return clockOwned && !Main.coverModeOn(); }
    static void waking() {
        if (clockOwned && !activeTrack.isEmpty()) resumeUntil = android.os.SystemClock.uptimeMillis() + 5000L;
    }
    private static void syncClock() {
        // Screen-off removes the video, but the clock's AOD flight must finish on its own.
        boolean keep = !Main.coverModeOn() && !MiniPlayerRuntime.canvasPlayerInPill()
                && (canvasReady && backdropAllowed && Main.screenOnCached()
                || clockOwned && (!Main.screenOnCached()
                    || android.os.SystemClock.uptimeMillis() < resumeUntil));
        if (clockOwned == keep) return;
        // Keep the outgoing owner until the cover has taken the clock in this same transition.
        if (!keep && Main.coverModeOn() && !LockHold.heldBy(LockHold.Owner.COVER)) return;
        clockOwned = keep;
        ClockCollapse.refreshArtworkSize();
        if (keep) LockHold.take(LockHold.Owner.CANVAS, Main.screenOnCached(), "canvas");
        else LockHold.give(LockHold.Owner.CANVAS, Main.screenOnCached());
    }
    private static void refreshStatusColors() {
        boolean visible = ownsVideoBackdrop();
        if (statusCanvas == visible) return;
        statusCanvas = visible;
        StatusBarArtwork.refresh();
    }
    static void sceneChanged() {
        syncClock();
        refreshStatusColors();
        // Here as well as on the extension's frames: those come 0.7s apart at best, and the
        // player going to the pill waited for the next one - 1.2-2.6s measured between the
        // swipe and the wallpaper coming back, felt as lag on a video wallpaper.
        releaseBackdrop();
        if (!canvasReady || sceneContext == null) return;
        boolean next = Main.coverModeOn() && (!LockLyrics.wantsCompactArtwork() || LockLyrics.wantsWindow());
        boolean visible = showInPill || !MiniPlayerRuntime.canvasPlayerInPill();
        if (lastScene != null && lastScene == next && lastVisible != null && lastVisible == visible) return;
        lastScene = next;
        lastVisible = visible;
        sceneContext.sendBroadcast(new Intent("com.os4.canvas.SCENE_CHANGED").setPackage("com.android.systemui"));
    }
    /** Drops the retained artwork backdrop once nothing is showing it any more. */
    private static void releaseBackdrop() {
        if (!backdropHeld || keepPaletteBackdrop()) return;
        boolean screenOn = Main.screenOnCached();
        if (backdropAllowed && screenOn && !MiniPlayerRuntime.canvasPlayerInPill()
                && (lastReady || lastTemporary)) return;
        backdropHeld = false;
        backdropTrack = "";
        if (!Main.coverModeOn()) CoverPush.pushArtAsync(false, false);
    }
    static boolean active() { return canvasReady && !MiniPlayerRuntime.canvasPlayerInPill() && Main.screenOnCached() && android.os.SystemClock.uptimeMillis() - lastFrame < 2500L; }
    static Bundle frame(Context context, Intent intent) {
        boolean ready = intent.getBooleanExtra("ready", false);
        String track = intent.getStringExtra("track");
        Bundle out = new Bundle();
        try {
            if (Main.sHidePlayerBackground) {
                suspendForBackdrop();
                out.putBoolean("canvasBlocked", true);
                out.putBoolean("canvasVisible", false);
                return out;
            }
            if (!verified) {
                verified = context.getPackageManager().checkSignatures("com.yzc26623.HyperMusicCoverEnhanced", "com.yzc26623.HyperCanvas") == android.content.pm.PackageManager.SIGNATURE_MATCH;
                if (!verified) return out;
            }
            if (ready && (Main.miniPlayerSession() == null || !"com.spotify.music".equals(Main.miniPlayerSession().getPackageName()))) {
                // Said outright: the extension reads a missing answer as visible, and showed the
                // video under the depth subject this side never hid for it.
                out.putBoolean("canvasVisible", false);
                return out;
            }
            // A hidden layer with no frame yet, asking whether to show: nothing here changes.
            if (intent.getBooleanExtra("visibilityOnly", false)) {
                return answer(out);
            }
            sceneContext = context.getApplicationContext();
            // "temporary" means the session may come back - buffering between tracks. On the awake
            // lock screen one that stays away has ended, and treating it as temporary forever kept
            // the retained artwork backdrop up until the next screen-off: the previous song's
            // (often frosted) cover over the user's own wallpaper, a video one included.
            boolean temporary = intent.getBooleanExtra("temporary", false);
            long now = android.os.SystemClock.uptimeMillis();
            if (ready || !temporary || !Main.screenOnCached() || !Main.onKeyguardNow()) {
                sessionGoneSince = 0L;
            } else if (sessionGoneSince == 0L) {
                sessionGoneSince = now;
            } else if (now - sessionGoneSince >= SESSION_GONE_MS) {
                temporary = false;
            }
            boolean wasReady = canvasReady;
            showInPill = intent.getBooleanExtra("showInPill", false);
            frozenInAod = intent.getBooleanExtra("frozenInAod", false);
            backdropAllowed = intent.getBooleanExtra("backdropAllowed", true);
            // The extension's "artworkFallback" - a track without video after one with it - is
            // not taken up: such a track is presented as if there were no extension at all, the
            // normal cover and pill over the user's own wallpaper. Holding its artwork behind the
            // expanded player instead kept a tap on the clock from reaching the pill, and on a
            // shared video wallpaper put the album art on the desktop too.
            canvasReady = ready;
            if (ready || !temporary) resumeUntil = 0L;
            else if (clockOwned && backdropAllowed && Main.screenOnCached() && resumeUntil == 0L) {
                // A buffering session can disappear briefly between adjacent tracks.
                resumeUntil = android.os.SystemClock.uptimeMillis() + 1500L;
            }
            lastFrame = android.os.SystemClock.uptimeMillis();
            lastReady = ready;
            lastTemporary = temporary;
            if (wasReady != canvasReady) {
                Main.refreshCanvasControls();
                Main.recolorClock();
            }
            // The scene the user left stays: a Canvas arriving under the big cover no longer
            // folds it into the player (2026-10-25), so the track without one has none to undo.
            if (ready && track != null && track.startsWith("spotify:track:")) activeTrack = track;
            else if (!temporary) activeTrack = "";
            sceneChanged();
            syncDepth();
            String backdropIdentity = Main.canvasBackdropKey();
            if (ready && backdropAllowed && Main.screenOnCached() && !Main.coverModeOn()
                    && (!MiniPlayerRuntime.canvasPlayerInPill() || ready && showInPill)
                    && (!backdropHeld || !backdropIdentity.equals(backdropTrack))) {
                // The glass clock samples the wallpaper behind the video, so retain music there.
                if (Main.prepareCanvasBackdrop()) {
                    backdropRetained();
                    backdropTrack = backdropIdentity;
                    CoverPush.pushArtAsync(true, true);
                }
            } else releaseBackdrop();
            answer(out);
        } catch (Throwable t) { Xp.w("Canvas extension handshake failed: " + t); }
        return out;
    }

    private static Bundle answer(Bundle out) {
        out.putBoolean("canvasBouncer", Main.bouncerShown());
        out.putBoolean("canvasVisible", showInPill || !MiniPlayerRuntime.canvasPlayerInPill());
        out.putBoolean("canvasLarge", Main.coverModeOn() && (!LockLyrics.wantsCompactArtwork() || LockLyrics.wantsWindow()));
        return out;
    }
}
