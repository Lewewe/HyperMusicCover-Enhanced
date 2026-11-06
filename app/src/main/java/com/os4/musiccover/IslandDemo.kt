// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Animation rendered from demo/island (Remotion). Run npm run render there, then copy
// island_demo_coloros.webp and island_demo_hyperos.webp to app/src/main/res/raw/.
// Geometry modelled on ColorOS 16 (OPPO); no ColorOS code included.
package com.os4.musiccover

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Rendered from demo/island (Remotion): run npm run render there and copy both WebPs to res/raw. */
@Composable
internal fun IslandDemo(colorOs: Boolean, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val drawable = remember(colorOs) {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources,
            if (colorOs) R.raw.island_demo_coloros else R.raw.island_demo_hyperos
        )) as AnimatedImageDrawable
    }
    DisposableEffect(drawable) {
        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        drawable.start()
        onDispose { drawable.stop() }
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().height(112.dp),
        factory = { context ->
            ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        },
        update = { it.setImageDrawable(drawable) }
    )
}
