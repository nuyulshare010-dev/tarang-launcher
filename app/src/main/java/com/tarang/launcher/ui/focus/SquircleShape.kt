package com.tarang.launcher.ui.focus

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.withSign

/**
 * An iOS/tvOS-style squircle (superellipse): |x/a|^n + |y/b|^n = 1.
 *
 * n ≈ 4.5 closely matches Apple's continuous-corner icon mask. Hand-rolled as a sampled
 * path so it needs no extra dependency (plan §5.4). Stateless — safe to hoist to a constant.
 */
class SquircleShape(
    private val n: Float = 4.5f,
    private val steps: Int = 72,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = size.width / 2f
        val b = size.height / 2f
        val exp = 2f / n
        val path = Path()
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * (2f * PI.toFloat())
            val c = cos(theta)
            val s = sin(theta)
            val x = a + a * c.absoluteValue.pow(exp).withSign(c)
            val y = b + b * s.absoluteValue.pow(exp).withSign(s)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
