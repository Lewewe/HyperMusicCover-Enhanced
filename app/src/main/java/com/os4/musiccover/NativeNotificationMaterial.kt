// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Developed with the assistance of OpenAI Codex.
package com.os4.musiccover

import android.content.Context
import android.view.View
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Ask the OEM binder to select today's material, not replay a previous shade/AOD effect. */
internal object NativeNotificationMaterial {
    private class Binding(val method: Method, val args: Array<Any?>) {
        var refreshes = 0
    }
    private val bindings = WeakHashMap<View, Binding>()
    private val effects = WeakHashMap<View, String>()
    private var depth = 0
    private var refreshing = false

    fun install(loader: ClassLoader) {
        runCatching {
            val binder = Xp.findClass(
                "com.android.systemui.statusbar.notification.style.view.ExpandableNotificationRowViewBinder", loader)
            val name = "bindInternal\$updateEffect"
            val method = binder.declaredMethods.first { it.name == name && it.parameterTypes.size == 5 }
                .apply { isAccessible = true }
            Xp.hookAll(binder, name) { chain ->
                val row = chain.args.getOrNull(1) as? View
                if (!refreshing && row != null) {
                    val args = chain.args.toTypedArray()
                    // Do not retain the row itself through a weak map's value. These native
                    // interactor and key/effect refs contain state rather than the row.
                    args[1] = null
                    bindings[row] = Binding(method, args)
                }
                chain.proceed()
            }
        }.onFailure { Xp.w("MCNativeMaterial: native binder unavailable: $it") }

        // Probe diagnostics only: these effects do not become restoration recipes.
        runCatching {
            val helper = Xp.findClass(
                "com.android.systemui.statusbar.notification.style.vieweffect.NotificationViewEffectHelper", loader)
            val field = helper.getDeclaredField("notificationRowEffectsMap").apply { isAccessible = true }
            val classes = (field.get(null) as Map<*, *>).values.filterNotNull().map { it.javaClass }.distinct()
            for (cls in classes) Xp.hookAll(cls, "apply") { chain ->
                val row = chain.args.firstOrNull() as? View
                val context = chain.args.getOrNull(1) as? Context
                val outer = depth == 0
                depth++
                val result = try { chain.proceed() } finally { depth-- }
                if (outer && row != null && context != null)
                    effects[row] = "${cls.simpleName}/${context.resources.configuration.uiMode}"
                result
            }
        }.onFailure { Xp.w("MCNativeMaterial: effect probe unavailable: $it") }
    }

    /** Re-evaluate native keyguard/night/glass state only at ownership boundaries. */
    fun restore(row: View): Boolean {
        if (refreshing || MiniPlayerScene.aodActive || !row.isAttachedToWindow) return false
        val binding = bindings[row] ?: return false
        val key = binding.args[2] ?: return false
        val oldKey = runCatching { Xp.getObjectField(key, "element") }.getOrNull()
        refreshing = true
        return try {
            // Clear only the binder's EffectKey cache. Its normal update then computes
            // the appropriate material from the current platform flows and theme context.
            Xp.setObjectField(key, "element", null)
            val args = binding.args.copyOf().also { it[1] = row }
            binding.method.invoke(null, *args)
            binding.refreshes++
            true
        } catch (error: Throwable) {
            runCatching { Xp.setObjectField(key, "element", oldKey) }
            Xp.w("MCNativeMaterial: native row rebind failed: $error")
            false
        } finally { refreshing = false }
    }

    fun describe(row: View): String {
        val binding = bindings[row]
        val keyguard = runCatching {
            val interactor = binding?.args?.get(0) ?: return@runCatching null
            val lazy = Xp.getObjectField(interactor, "materialViewState\$delegate")
            val flow = Xp.callMethod(lazy, "getValue")
            val state = Xp.callMethod(flow, "getValue")
            Xp.getObjectField(state, "isShowingKeyguardNotification")
        }.getOrNull()
        return "${effects[row] ?: "unknown"}/refresh=${binding?.refreshes}/keyguard=$keyguard"
    }
}
