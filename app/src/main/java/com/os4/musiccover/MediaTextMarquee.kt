package com.os4.musiccover

import android.text.TextUtils
import android.widget.TextView

/** Own only the two song labels and restore their native presentation when disabled. */
internal class MediaTextMarquee(val view: TextView) {
    private data class Original(
        val ellipsize: TextUtils.TruncateAt?,
        val selected: Boolean,
        val repeatLimit: Int,
        val horizontal: Boolean,
        val maxLines: Int,
        val maxHeight: Int,
    )

    private var original: Original? = null

    fun update(enabled: Boolean, running: Boolean) {
        if (!enabled) {
            restore()
            return
        }
        if (original == null) {
            original = Original(view.ellipsize, view.isSelected, view.marqueeRepeatLimit,
                view.isHorizontallyScrollable, view.maxLines, view.maxHeight)
        }
        // Native marquee scrolls only overflowing text; no custom animator or frame loop.
        // Conditional writes avoid restarting it on every existing card pre-draw callback.
        if (view.maxLines != 1) view.maxLines = 1
        if (!view.isHorizontallyScrollable) view.setHorizontallyScrolling(true)
        if (view.marqueeRepeatLimit != -1) view.marqueeRepeatLimit = -1
        val ellipsize = if (running) TextUtils.TruncateAt.MARQUEE else TextUtils.TruncateAt.END
        if (view.ellipsize != ellipsize) view.ellipsize = ellipsize
        if (view.isSelected != running) view.isSelected = running
    }

    fun restore() {
        val saved = original ?: return
        original = null
        view.isSelected = false
        view.ellipsize = saved.ellipsize
        view.marqueeRepeatLimit = saved.repeatLimit
        view.setHorizontallyScrolling(saved.horizontal)
        if (saved.maxLines >= 0) view.maxLines = saved.maxLines
        else view.maxHeight = saved.maxHeight
        view.isSelected = saved.selected
    }
}
