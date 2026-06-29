package com.tarang.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tarang.launcher.data.ScreensaverSource
import com.tarang.launcher.data.WatchNextItem
import com.tarang.launcher.data.TvArtwork
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SLIDE_MS = 12_000L // each screensaver image lingers longer than the hover slideshow
private const val FADE_MS = 1800

/**
 * Full-screen idle screensaver: a slow, cross-fading Ken-Burns slideshow of the user's app artwork
 * with a large clock, or (CLOCK source / no artwork) just the clock over a calm gradient. Purely
 * visual — [LauncherScreen] traps the next key to dismiss it, so this doesn't take focus.
 */
@Composable
fun Screensaver(
    posterPackages: List<String>,
    watchNext: List<WatchNextItem>,
    source: ScreensaverSource,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Build a poster pool from Watch Next + each favorite's published posters (shuffled so it
    // doesn't always open the same). Empty when CLOCK source or nothing publishes artwork.
    val pool by produceState<List<String>>(initialValue = emptyList(), source, posterPackages, watchNext) {
        if (source == ScreensaverSource.CLOCK) {
            value = emptyList()
            return@produceState
        }
        val fromWatch = watchNext.mapNotNull { it.posterUri }
        val fromFavs = posterPackages.flatMap { TvArtwork.posterUris(context, it, limit = 8) }
        value = (fromWatch + fromFavs).distinct().shuffled()
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (pool.isNotEmpty()) {
            ArtworkSlides(pool = pool, reduceMotion = reduceMotion)
            // Darken the bottom so the clock stays legible over any poster.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.7f),
                        ),
                    ),
            )
        } else {
            // Clock-only (or no artwork): a deep, calm gradient.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF05060A), Color(0xFF12131A), Color(0xFF05060A))),
                ),
            )
        }

        ScreensaverClock(modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 56.dp))
    }
}

@Composable
private fun ArtworkSlides(pool: List<String>, reduceMotion: Boolean) {
    val context = LocalContext.current
    var index by remember(pool) { mutableIntStateOf(0) }
    if (!reduceMotion) {
        androidx.compose.runtime.LaunchedEffect(pool) {
            if (pool.size <= 1) return@LaunchedEffect
            while (true) {
                delay(SLIDE_MS)
                index = (index + 1) % pool.size
            }
        }
    }

    val image by produceState<ImageBitmap?>(initialValue = null, pool, index) {
        value = ArtworkLoader.load(context, pool[index % pool.size]) ?: value
    }

    val kb = rememberInfiniteTransition(label = "ssKenBurns")
    val drift by kb.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(SLIDE_MS.toInt() + FADE_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "ssDrift",
    )
    val scale = if (reduceMotion) 1f else drift

    Crossfade(targetState = image, animationSpec = tween(FADE_MS), label = "ssPoster") { img ->
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
    }
}

@Composable
private fun ScreensaverClock(modifier: Modifier = Modifier) {
    // Re-render each minute.
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            tick++
        }
    }
    val now = remember(tick) { Date() }
    val time = remember(tick) { SimpleDateFormat("h:mm", Locale.getDefault()).format(now) }
    val date = remember(tick) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now) }

    Column(modifier = modifier) {
        Text(text = time, color = Color.White, fontSize = 92.sp, fontWeight = FontWeight.Bold)
        Text(text = date, color = Color.White.copy(alpha = 0.82f), fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}
