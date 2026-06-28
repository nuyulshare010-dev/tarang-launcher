package com.tarang.launcher.ui

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.TileArt

/**
 * tvOS-style Top Shelf: a hero band showing the focused app's banner artwork, blurred, with a
 * scrim that fades into the grid below; it crossfades as focus moves (plan §5.5). Apps without a
 * banner fall back to a color gradient drawn from their icon. Real per-app shelf *content*
 * (TvProvider channels) is a later milestone.
 */
@Composable
fun TopShelf(
    app: AppInfo?,
    iconLoader: IconLoader,
    modifier: Modifier = Modifier,
) {
    val tile: TileArt? by produceState<TileArt?>(initialValue = null, app?.packageName) {
        value = app?.let { iconLoader.loadTile(it) }
    }

    Box(modifier = modifier.background(Color.Black)) {
        Crossfade(targetState = tile, animationSpec = tween(durationMillis = 400), label = "shelfBackdrop") { art ->
            when (art) {
                is TileArt.Banner -> Image(
                    bitmap = art.image,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blurCompat(36.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.65f,
                )

                is TileArt.Fallback -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(art.color.copy(alpha = 0.7f), Color.Black))),
                )

                null -> Box(modifier = Modifier.fillMaxSize())
            }
        }

        // Scrim: keep the top clear, fade to solid black at the bottom so it blends into the grid.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1.0f to Color.Black,
                    ),
                ),
        )
    }
}

/** [Modifier.blur] is a no-op below API 31; gate it so we don't pretend to blur on older TVs. */
private fun Modifier.blurCompat(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this
