package com.os4.musiccover;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Lets the OEM build a complete light-clock palette while a music backdrop is visible. */
final class ClockBackdropContrast {
    private static final WeakHashMap<Object, Entry> clocks = new WeakHashMap<>();
    private static boolean replaying;
    private static int palettes, animations;

    private static final class Entry {
        Object[] nativeArgs;
        boolean music;
    }

    static boolean ownsContrast() {
        return Main.screenOnCached() && Main.keyguardLocked()
                && !CoverBackdrop.nativeWallpaperScene()
                && (Main.sCoverMode || CanvasHostBridge.ownsVideoBackdrop());
    }

    static void install(ClassLoader loader) {
        try {
            // Hook the subclass before it computes colors, not only its superclass setter.
            // Dark/light selection changes the palette AND the native blend coefficients.
            Xp.hookAll(Xp.findClass("com.miui.clock.allInOne.AllInOneBase", loader),
                    "setClockPalette", chain -> {
                Object clock = chain.getThisObject();
                Object[] args = chain.getArgs().toArray();
                if (args.length != 4 || !(args[1] instanceof Boolean)) return chain.proceed();
                Entry entry = clocks.get(clock);
                if (entry == null) { entry = new Entry(); clocks.put(clock, entry); }
                if (!replaying) entry.nativeArgs = args.clone();
                boolean music = ownsContrast();
                if (music) args[1] = false;
                Object result = chain.proceed(args);
                entry.music = music;
                palettes++;
                return result;
            });
            // Color-extraction animations build a separate target style from wallpaper data.
            // Feed the same contrast decision into that builder so their final frame agrees.
            Xp.hookAll(Xp.findClass("com.miui.clock.allInOne.AllInOneColor", loader),
                    "processingClockPalette", chain -> {
                if (!ownsContrast()) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                if (args.length == 4 && args[2] instanceof Boolean) args[2] = false;
                return chain.proceed(args);
            });
            Xp.hookAll(Xp.findClass(
                    "com.android.keyguard.clock.animation.allinone.AllInOneBaseAnimation", loader),
                    "doColorExtractionAnimation", chain -> {
                if (!ownsContrast()) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                if (args.length == 2 && args[0] instanceof Boolean) args[0] = false;
                animations++;
                return chain.proceed(args);
            });
            Xp.log("MCClockContrast: native palette handoff installed");
        } catch (Throwable t) {
            Xp.w("MCClockContrast: unavailable: " + t);
        }
    }

    private static final Runnable UPDATE = () -> {
        boolean music = ownsContrast();
        for (Map.Entry<Object, Entry> item : new ArrayList<>(clocks.entrySet())) {
            Entry entry = item.getValue();
            if (entry.nativeArgs == null || entry.music == music) continue;
            replaying = true;
            try {
                // Recompute only on backdrop transitions, and restore the last OEM decision
                // when returning to wallpaper. Track changes require no palette rebuild.
                Xp.callMethod(item.getKey(), "setClockPalette", entry.nativeArgs.clone());
            } catch (Throwable t) {
                Xp.w("MCClockContrast: refresh failed: " + t);
            } finally {
                replaying = false;
            }
        }
    };

    static void refresh() {
        Main.main().removeCallbacks(UPDATE);
        Main.main().post(UPDATE);
    }

    static String describe() {
        return "music=" + ownsContrast() + " clocks=" + clocks.size()
                + " palettes=" + palettes + " animations=" + animations;
    }
}
