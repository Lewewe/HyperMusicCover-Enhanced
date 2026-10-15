package com.os4.musiccover

import org.json.JSONObject

/** Two independent card profiles; zero-valued appearance choices retain the native style. */
object MediaCardConfig {
    /** Visualizer bar colour modes: the artwork gradient, white, dark grey, the custom colour. */
    const val WAVE_GRADIENT = 0
    const val WAVE_WHITE = 1
    const val WAVE_DARK = 2
    const val WAVE_CUSTOM = 3
    const val WAVE_DARK_COLOR = 0xFF616161.toInt()
    const val WAVE_CUSTOM_DEFAULT = 0xFF4FC3F7.toInt()

    /** The bar colours [mode] draws instead of the native gradient [top] over [bottom]. */
    fun waveColors(mode: Int, custom: Int, top: Int, bottom: Int): Pair<Int, Int> = when (mode) {
        WAVE_WHITE -> 0xFFFFFFFF.toInt() to 0xFFFFFFFF.toInt()
        // Not black: the Super Island's own background is black, and the bars vanished into it.
        WAVE_DARK -> WAVE_DARK_COLOR to WAVE_DARK_COLOR
        WAVE_CUSTOM -> custom to custom
        else -> top to bottom
    }

    val scopes = listOf("notification", "island")
    val defaults = linkedMapOf("cover" to 0, "scrollText" to 0, "hideSource" to 0, "hideDevice" to 0,
        "background" to 0, "theme" to 0, "flow" to 0, "pauseRestore" to 1,
        "animate" to 1, "blur" to 8, "invert" to 1, "tone" to 1, "visualizer" to 0, "outputWave" to 0, "lockOutputWave" to 0, "hideWaveDevice" to 0, "btSync" to 1, "btOffset" to 0,
        "waveColor" to 0, "waveCustom" to WAVE_CUSTOM_DEFAULT)

    fun limit(name: String, value: Int): Int = when (name) {
        "cover" -> value.coerceIn(0, 3)
        "background" -> value.coerceIn(0, 5)
        "theme" -> value.coerceIn(0, 2)
        "flow" -> value.coerceIn(0, 4)
        "blur" -> value.coerceIn(1, 20)
        "btOffset" -> value.coerceIn(-250, 250)
        "waveColor" -> value.coerceIn(0, 3)
        "waveCustom" -> value
        else -> value.coerceIn(0, 1)
    }

    fun parse(json: String?): Map<String, Int> {
        val obj = runCatching { JSONObject(json ?: "{}") }.getOrNull()
        return buildMap {
            for (scope in scopes) for ((name, default) in defaults) {
                val key = "$scope.$name"
                put(key, limit(name, obj?.optInt(key, default) ?: default))
            }
        }
    }

    fun encode(values: Map<String, Int>): String = JSONObject().also { out ->
        for (scope in scopes) for ((name, default) in defaults) {
            val key = "$scope.$name"
            out.put(key, limit(name, values[key] ?: default))
        }
    }.toString()

    fun normalized(json: String?): String = encode(parse(json))
    fun outputWaveKey(scope: String, onLockScreen: Boolean): String =
        if (scope == "island") "island.outputWave"
        else if (onLockScreen) "notification.lockOutputWave" else "notification.outputWave"

    fun customBackground(style: Int, flow: Int): Boolean = style > 0 || flow in 1..3
    fun animate(playing: Boolean, pauseRestore: Boolean, shown: Boolean, screenOn: Boolean): Boolean =
        shown && screenOn && (playing || !pauseRestore)
}

internal object MediaCardLog {
    fun i(tag: String, message: String) = Xp.d("$tag $message")
    fun w(tag: String, message: String) = Xp.w("$tag $message")
}
