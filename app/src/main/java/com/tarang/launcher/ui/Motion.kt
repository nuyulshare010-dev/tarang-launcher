package com.tarang.launcher.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import com.tarang.launcher.data.AnimStyle

/**
 * The motion vocabulary for the four big transitions (enter/exit Frame Art, launch/return an app),
 * factored out of [LauncherScreen] so each [AnimStyle] is one coherent, comparable package of
 * timing + transform. The launcher drives two chrome layers on shared timelines (the dock leads, the
 * top bar trails); this file decides, per style, HOW those layers move and how fast.
 *
 * Progress convention everywhere: 0f = home (chrome fully present), 1f = gone (Frame Art / app open).
 */

/** The two chrome layers the launcher animates independently. */
enum class ChromeLayer { TOP_BAR, DOCK }

// Easings shared across styles. StandardEase is Material/iOS-ish accelerate-then-settle; Decel is a
// pure ease-out (rush in, glide to rest — used on returns); Accel is ease-in (start slow, fly away).
private val StandardEase = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
private val DecelEase = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1f)
private val AccelEase = CubicBezierEasing(0.4f, 0.0f, 1f, 1f)

// GLIDE's progress (0..1) is multiplied by the box height (~950px) to drive translation, so a spring's
// default 0.01 visibility threshold would let it "settle" a visible ~9px from the target and then snap
// there on the last frame. A sub-pixel threshold makes the tail land smoothly on the resting position.
private const val GLIDE_THRESHOLD = 0.0002f

// GLIDE felt too fast on real TV hardware, so slow every glide spring ~2.5×. A spring's settling time
// scales with 1/√stiffness, so 2.5× slower ≈ stiffness ÷ 2.5² (÷6.25). Dividing here (and keeping the
// original stiffness numbers at the call sites) preserves the relative pacing between the layers.
private const val GLIDE_SLOWDOWN = 6.25f

private fun glideSpring(dampingRatio: Float, stiffness: Float): AnimationSpec<Float> =
    spring(dampingRatio, stiffness / GLIDE_SLOWDOWN, visibilityThreshold = GLIDE_THRESHOLD)

// ---------------------------------------------------------------------------------------------------
// Timing — the AnimationSpecs the launcher's Animatables run on. Frame transitions are calm/slow; app
// launches are quick. BASELINE/DEPTH use tuned tweens; GLIDE uses springs so it decelerates
// naturally and stays interruptible.
// ---------------------------------------------------------------------------------------------------

/** Master progress spec (drives clock reveal, art crossfade + all the gating). */
fun frameMasterSpec(style: AnimStyle): AnimationSpec<Float> = when (style) {
    AnimStyle.BASELINE -> tween(1700, easing = StandardEase)
    AnimStyle.GLIDE -> glideSpring(dampingRatio = 1f, stiffness = 130f)
    AnimStyle.DEPTH -> tween(1100, easing = StandardEase)
}

/** Dock layer during a Frame Art enter/exit (the dock leads — shortest/stiffest). */
fun frameDockSpec(style: AnimStyle): AnimationSpec<Float> = when (style) {
    AnimStyle.BASELINE -> tween(1200, easing = StandardEase)
    AnimStyle.GLIDE -> glideSpring(dampingRatio = 1f, stiffness = 190f)
    AnimStyle.DEPTH -> tween(900, easing = StandardEase)
}

/** Top bar layer during a Frame Art enter/exit (trails the dock — longest/softest). */
fun frameTopBarSpec(style: AnimStyle): AnimationSpec<Float> = when (style) {
    AnimStyle.BASELINE -> tween(1500, easing = StandardEase)
    AnimStyle.GLIDE -> glideSpring(dampingRatio = 1f, stiffness = 110f)
    AnimStyle.DEPTH -> tween(1100, easing = StandardEase)
}

/** Dock layer during an app launch ([entering]) / return (!entering). */
fun launchDockSpec(style: AnimStyle, entering: Boolean): AnimationSpec<Float> = when (style) {
    AnimStyle.BASELINE -> tween(600, easing = if (entering) AccelEase else DecelEase)
    AnimStyle.GLIDE -> glideSpring(dampingRatio = if (entering) 1f else 0.82f, stiffness = 340f)
    AnimStyle.DEPTH -> tween(if (entering) 500 else 640, easing = if (entering) AccelEase else DecelEase)
}

/** Top bar layer during an app launch / return. */
fun launchTopBarSpec(style: AnimStyle, entering: Boolean): AnimationSpec<Float> = when (style) {
    AnimStyle.BASELINE -> tween(900, easing = if (entering) AccelEase else DecelEase)
    AnimStyle.GLIDE -> glideSpring(dampingRatio = if (entering) 1f else 0.9f, stiffness = 240f)
    AnimStyle.DEPTH -> tween(if (entering) 620 else 760, easing = if (entering) AccelEase else DecelEase)
}

// ---------------------------------------------------------------------------------------------------
// Transforms — applied inside the chrome layers' graphicsLayer blocks. [frameP] and [launchP] are the
// two progresses; in practice only one is non-zero at a time (you can't launch an app mid-frame), so
// styles that want different behaviour for the two moves (DEPTH) just branch on which is active.
// ---------------------------------------------------------------------------------------------------

/**
 * Shape the chrome for the current style. Called from the layer's `graphicsLayer {}` where `size` is
 * the layer's own size.
 */
fun GraphicsLayerScope.applyChrome(
    style: AnimStyle,
    layer: ChromeLayer,
    frameP: Float,
    launchP: Float,
) {
    when (style) {
        AnimStyle.BASELINE -> baseline(layer, maxOf(frameP, launchP))
        AnimStyle.GLIDE -> glide(layer, maxOf(frameP, launchP))
        AnimStyle.DEPTH -> depth(layer, frameP, launchP)
    }
}

/** The shipped motion: dock scales up 1.28× and drops off the bottom, top bar rises off the top. */
private fun GraphicsLayerScope.baseline(layer: ChromeLayer, p: Float) {
    when (layer) {
        ChromeLayer.TOP_BAR -> {
            translationY = -p * (size.height + 48f)
            alpha = 1f - (p * 1.7f).coerceAtMost(1f)
        }
        ChromeLayer.DOCK -> {
            val s = 1f + 0.28f * p
            scaleX = s
            scaleY = s
            translationY = p * size.height * 0.55f
            alpha = 1f - (p * 1.7f).coerceAtMost(1f)
            transformOrigin = TransformOrigin(0.5f, 0.85f)
        }
    }
}

/** Fluid glide: pure slide-off + fade (no scale-up), the spring timing does the work. */
private fun GraphicsLayerScope.glide(layer: ChromeLayer, p: Float) {
    when (layer) {
        ChromeLayer.TOP_BAR -> {
            translationY = -p * (size.height + 48f)
            alpha = 1f - (p * 1.6f).coerceAtMost(1f)
        }
        ChromeLayer.DOCK -> {
            // A whisper of scale-down as it leaves, so it settles rather than just translating.
            val s = 1f - 0.04f * p
            scaleX = s
            scaleY = s
            translationY = p * size.height * 0.95f
            alpha = 1f - (p * 1.5f).coerceAtMost(1f)
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    }
}

/** Z-axis depth: recede (scale <1) toward Frame Art; approach (scale >1) into an app. */
private fun GraphicsLayerScope.depth(layer: ChromeLayer, frameP: Float, launchP: Float) {
    val p = maxOf(frameP, launchP)
    alpha = 1f - (p * 1.5f).coerceAtMost(1f)
    // No blur: an animated RenderEffect blur on these full-screen layers is far too heavy on weak TV
    // GPUs (it re-blurs every frame — janks badly on the Chromecast). The scale recede/approach + fade,
    // plus the art rising forward, carry the depth on their own at essentially no GPU cost.
    // recede on frame-enter (down to 0.9), approach on launch (up to ~1.14). Mutually exclusive.
    val recede = 1f - 0.10f * frameP
    val approach = 1f + 0.14f * launchP
    val s = recede * approach
    scaleX = s
    scaleY = s
    when (layer) {
        ChromeLayer.TOP_BAR -> {
            translationY = -frameP * 24f - launchP * 30f
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
        ChromeLayer.DOCK -> {
            translationY = frameP * 16f + launchP * 26f
            transformOrigin = TransformOrigin(0.5f, 0.55f)
        }
    }
}

/**
 * The incoming Frame Art's entrance scale for DEPTH (the art rises forward from a hair larger). [p] is
 * the master frame progress. Returns 1f (no scale) for styles that just crossfade the art.
 */
fun artEntryScale(style: AnimStyle, p: Float): Float = when (style) {
    AnimStyle.DEPTH -> 1.06f - 0.06f * p
    else -> 1f
}
