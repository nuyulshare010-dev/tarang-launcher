package com.tarang.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

private val WallBase = Color(0xFF06070B)
private val BlobIndigo = Color(0xFF4B3DAA)
private val BlobTeal = Color(0xFF1E7E8C)
private val AccentDefault = Color(0xFF3A6EA5)

/**
 * The launcher background: a near-black base with a few large, soft, slowly-drifting color blobs.
 *
 * Radial gradients are inherently soft (the "blur" look) and cheap to fill, so there is no
 * per-frame blur — important on a 2 GB Chromecast (plan §6). One blob eases toward [ambient]
 * (the focused app's color) so the wallpaper gently breathes as focus moves.
 */
@Composable
fun AnimatedWallpaper(
    ambient: Color?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "wallpaper")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val accent by animateColorAsState(
        targetValue = ambient ?: AccentDefault,
        animationSpec = tween(1400),
        label = "accent",
    )

    Canvas(modifier = modifier) {
        drawRect(WallBase)
        val w = size.width
        val h = size.height
        val r = size.maxDimension
        blob(Offset(w * (0.22f + 0.12f * drift), h * (0.18f + 0.12f * drift)), r * 0.55f, BlobIndigo.copy(alpha = 0.40f))
        blob(Offset(w * (0.82f - 0.14f * drift), h * (0.30f + 0.10f * drift)), r * 0.50f, BlobTeal.copy(alpha = 0.34f))
        blob(Offset(w * (0.55f + 0.12f * drift), h * (0.92f - 0.12f * drift)), r * 0.60f, accent.copy(alpha = 0.30f))
    }
}

private fun DrawScope.blob(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}
