package com.tarang.launcher.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

// AGSL refraction: near the rounded edges, bend the sampled (already-blurred) backdrop toward the
// element centre, magnifying the rim like the thick bevel of a real glass slab. Subtle and confined
// to the edge by the squared falloff, so the middle stays a clean frost.
private const val REFRACTION_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float strength;
    half4 main(float2 coord) {
        float2 uv = coord / size;
        float2 toEdge = min(uv, 1.0 - uv);
        float edge = min(toEdge.x, toEdge.y);
        float f = 1.0 - smoothstep(0.0, 0.18, edge);
        float2 dir = uv - 0.5;
        float len = length(dir) + 0.0001;
        float2 ndir = dir / len;
        float2 displaced = coord - ndir * (f * f) * strength;
        return content.eval(displaced);
    }
"""

// We blur a HALF-resolution copy of the backdrop slice (1/4 the pixels through the blur kernel), then
// upscale the result — the detail is gone to the blur anyway, so it's invisible but much cheaper on a
// weak TV GPU.
private const val GLASS_DOWNSCALE = 0.5f

/**
 * tvOS "Liquid Glass": blurs the recorded [backdrop] wallpaper directly behind this element (a true
 * frosted-glass backdrop), then layers a legibility [tint], an optional content-aware [accent]
 * wash, a soft top-left sheen, and a beveled rim highlight on the edge. On Android 13+ an AGSL shader
 * also refracts the backdrop through the glass edges (the bevelled-lens look) — this is always on.
 *
 * Blur is API 31+ and the refraction needs API 33+; older devices fall back to tint + sheen + rim
 * (and a plain blur where available). The element must be drawn above the same [backdrop] graphics
 * layer that recorded the wallpaper (see LauncherScreen) — it re-draws that layer shifted by its own
 * on-screen position so the blurred slice lines up.
 *
 * [live] gates the per-frame backdrop RE-CAPTURE; when false the last captured slice is reused (frozen
 * glass, still frosted — used mid launch/return zoom). [refract] gates the AGSL edge refraction: pass
 * false while anything is animating, since the bevel is imperceptible mid-motion but costs a shader pass.
 */
@Composable
fun Modifier.frostedGlass(
    backdrop: GraphicsLayer,
    shape: Shape,
    tint: Color,
    accent: Color? = null,
    blurRadius: Dp = 16.dp,
    live: Boolean = true,
    refract: Boolean = true,
    blur: Boolean = true,
): Modifier {
    val layer = rememberGraphicsLayer()
    var offset by remember { mutableStateOf(Offset.Zero) }
    var glassSize by remember { mutableStateOf(IntSize.Zero) }
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val supportsShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    // Blur runs on the half-res copy, so halve the kernel too (it's upscaled back afterwards).
    val blurPx = with(LocalDensity.current) { blurRadius.toPx() } * GLASS_DOWNSCALE
    // Beveled edge: a bright specular highlight at the top-left fading to a faint shadow bottom-right.
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.12f)))
    // Half-resolution capture/blur size.
    val dsSize = IntSize(
        (glassSize.width * GLASS_DOWNSCALE).toInt().coerceAtLeast(1),
        (glassSize.height * GLASS_DOWNSCALE).toInt().coerceAtLeast(1),
    )

    // Liquid glass is on at rest: build the refraction shader wherever the device supports AGSL
    // (Android 13+). Two effects — blur+refraction for the resting glass, blur-only for while moving.
    val shader = remember(supportsShader) { if (supportsShader) RuntimeShader(REFRACTION_AGSL) else null }
    val refractEffect: RenderEffect? = remember(dsSize, blurPx, supportsBlur) {
        if (!supportsBlur) null else glassEffect(blurPx, dsSize, shader)
    }
    val blurEffect: RenderEffect? = remember(dsSize, blurPx, supportsBlur) {
        if (!supportsBlur) null else glassEffect(blurPx, dsSize, null)
    }
    // [blur] off → no backdrop effect at all: just the flat tint + sheen + rim (and the caller skips
    // the backdrop capture entirely, so nothing is sampled).
    val effect = if (!blur) null else if (refract) refractEffect else blurEffect

    return this
        .onGloballyPositioned { offset = it.positionInRoot(); glassSize = it.size }
        .clip(shape)
        .drawBehind {
            if (effect != null && glassSize.width > 0 && glassSize.height > 0) {
                layer.renderEffect = effect
                // [live] only gates the expensive re-capture of the backdrop slice. When frozen we
                // still draw the LAST captured slice, so the glass stays frosted mid-animation instead
                // of dropping to a near-transparent tint (which read as a flash on Frame Art enter/exit).
                if (live) {
                    // Capture the backdrop slice at half resolution (scale the draw down into dsSize).
                    layer.record(dsSize) {
                        scale(GLASS_DOWNSCALE, GLASS_DOWNSCALE, pivot = Offset.Zero) {
                            translate(-offset.x, -offset.y) { drawLayer(backdrop) }
                        }
                    }
                }
                // Upscale the blurred half-res slice back to full size.
                scale(1f / GLASS_DOWNSCALE, 1f / GLASS_DOWNSCALE, pivot = Offset.Zero) {
                    drawLayer(layer)
                }
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

/** Blur, optionally chained with the refraction shader (when a [shader] and a real size are given). */
@RequiresApi(Build.VERSION_CODES.S)
private fun glassEffect(blurPx: Float, size: IntSize, shader: RuntimeShader?): RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(
        blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP,
    )
    if (shader != null && size.width > 0 && size.height > 0 &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
        shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("strength", min(min(size.width, size.height) * 0.08f, 18f))
        val refract = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        // createChainEffect(outer, inner): inner runs first, so blur then refract.
        return android.graphics.RenderEffect.createChainEffect(refract, blur).asComposeRenderEffect()
    }
    return blur.asComposeRenderEffect()
}
