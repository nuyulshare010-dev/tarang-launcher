package com.tarang.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tarang.launcher.data.ThemeMode
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * The launcher's light/dark color tokens, provided via [LocalLauncherColors]. Components read these
 * instead of hardcoding colors so the whole UI flips with the theme. (The TV-probe diagnostic and
 * the image picker stay dark on purpose — they're utility surfaces.)
 */
@Immutable
data class LauncherColors(
    val isDark: Boolean,
    val text: Color,        // primary foreground (clock, labels, icons)
    val textDim: Color,     // secondary foreground
    val page: Color,        // full-page (settings) background
    val panel: Color,       // dialog / menu background
    val chip: Color,        // idle chip / row background
    val highlight: Color,   // focused or selected background fill
    val onHighlight: Color, // text / icon on [highlight]
    val chrome: Color,      // translucent fill over the wallpaper (dock)
    val textBackdrop: Color, // local container behind free-floating text (clock, status pill)
    val line: Color,        // hairline borders
)

val DarkLauncherColors = LauncherColors(
    isDark = true,
    text = Color.White,
    textDim = Color.White.copy(alpha = 0.55f),
    page = Color(0xFF0B0B0E),
    panel = Color(0xFF1C1C20),
    chip = Color(0xFF2A2A2E),
    highlight = Color.White,
    onHighlight = Color.Black,
    chrome = Color.White.copy(alpha = 0.10f),
    textBackdrop = Color.Black.copy(alpha = 0.32f),
    line = Color.White.copy(alpha = 0.12f),
)

val LightLauncherColors = LauncherColors(
    isDark = false,
    text = Color(0xFF17171C),
    textDim = Color(0xFF17171C).copy(alpha = 0.55f),
    page = Color(0xFFECECF1),
    panel = Color(0xFFF7F7FA),
    chip = Color(0x14000000),
    highlight = Color(0xFF17171C),
    onHighlight = Color.White,
    chrome = Color.White.copy(alpha = 0.42f),
    textBackdrop = Color.White.copy(alpha = 0.55f),
    line = Color(0x1F000000),
)

val LocalLauncherColors = staticCompositionLocalOf { DarkLauncherColors }

/**
 * Resolves [ThemeMode] to dark/light. For [ThemeMode.AUTO] it follows the clock — light from 7am to
 * 7pm, dark otherwise — and re-checks each minute so it flips live at the boundaries.
 */
@Composable
fun rememberIsDark(theme: ThemeMode): Boolean = when (theme) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.AUTO -> {
        var dark by remember { mutableStateOf(isNightNow()) }
        LaunchedEffect(Unit) {
            while (true) {
                dark = isNightNow()
                val millis = System.currentTimeMillis()
                delay(60_000L - millis % 60_000L) // re-check on the next minute boundary
            }
        }
        dark
    }
}

/** Light during [7:00, 19:00); dark otherwise. */
private fun isNightNow(): Boolean {
    val h = LocalTime.now().hour
    return h < 7 || h >= 19
}
