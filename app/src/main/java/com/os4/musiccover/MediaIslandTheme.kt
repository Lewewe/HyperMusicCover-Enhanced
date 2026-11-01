/* Copyright 2026 juren233. Licensed under the Apache License, Version 2.0.
 * Adapted from HyperLyrics-Enhanced, revision 912df58. */
package com.os4.musiccover

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference

/**
 * OLED black for the expanded island: a black layer at the bottom of its card, under the native
 * background motion and over the OEM's glass. The glass itself is left alone: once its material
 * is cleared, updateBackgroundBg does not bring it back.
 */
internal class MediaIslandTheme {
    private var black: WeakReference<View>? = null

    fun apply(player: View?, oled: Boolean) {
        if (!oled) { restore(); return }
        var parent = player?.parent as? View
        var card: ViewGroup? = null
        while (parent != null && card == null) {
            if (parent.javaClass.name == "miui.systemui.dynamicisland.view.DynamicIslandExpandedView") card = parent as? ViewGroup
            parent = parent.parent as? View
        }
        val target = card ?: return
        if (black?.get()?.parent === target) return
        restore()
        val layer = View(target.context).apply {
            setBackgroundColor(Color.BLACK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        target.addView(layer, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        black = WeakReference(layer)
    }

    fun restore() {
        black?.get()?.let { (it.parent as? ViewGroup)?.removeView(it) }
        black = null
    }
}
