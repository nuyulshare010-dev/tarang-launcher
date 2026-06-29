package com.tarang.launcher.ui

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * tvOS "Liquid Glass": blurs the recorded [backdrop] wallpaper directly behind this element (a true
 * frosted-glass backdrop), then layers a legibility [tint], an optional content-aware [accent]
 * wash, a soft top-left sheen, and a beveled rim highlight on the edge.
 *
 * Blur is API 31+; older devices get tint + sheen + rim only. The element must be drawn above the
 * same [backdrop] graphics layer that recorded the wallpaper (see LauncherScreen) — it re-draws that
 * layer shifted by its own on-screen position so the blurred slice lines up.
 */
@Composable
fun Modifier.frostedGlass(
    backdrop: GraphicsLayer,
    shape: Shape,
    tint: Color,
    accent: Color? = null,
    blurRadius: Dp = 22.dp,
): Modifier {
    val layer = rememberGraphicsLayer()
    var offset by remember { mutableStateOf(Offset.Zero) }
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurPx = with(LocalDensity.current) { blurRadius.toPx() }
    // Beveled edge: a bright specular highlight at the top-left fading to a faint shadow bottom-right.
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.12f)))

    return this
        .onGloballyPositioned { offset = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (supportsBlur) {
                layer.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                layer.record { translate(-offset.x, -offset.y) { drawLayer(backdrop) } }
                drawLayer(layer)
            }
            drawRect(tint)
            accent?.let { drawRect(it.copy(alpha = 0.10f)) }
            // Soft sheen, like light catching the glass from the top-left.
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.7f, size.height),
                ),
            )
        }
        .border(1.dp, rim, shape)
}
