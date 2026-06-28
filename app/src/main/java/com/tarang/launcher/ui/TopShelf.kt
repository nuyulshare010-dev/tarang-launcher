package com.tarang.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader

private val DefaultShelfColor = Color(0xFF1C1C1E)

/**
 * tvOS-style Top Shelf: a hero band whose backdrop color is derived from the focused app's icon
 * and animates as focus moves; the app branding crossfades (plan §5.5). v1 is a color gradient +
 * name; real per-app shelf content (TvProvider channels) is deferred to a later milestone.
 */
@Composable
fun TopShelf(
    app: AppInfo?,
    iconLoader: IconLoader,
    modifier: Modifier = Modifier,
) {
    val targetColor by produceState(initialValue = DefaultShelfColor, key1 = app?.packageName) {
        value = app?.let { iconLoader.dominantColor(it) } ?: DefaultShelfColor
    }
    val shelfColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 450),
        label = "shelfColor",
    )

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(shelfColor.copy(alpha = 0.85f), Color.Black)),
        ),
        contentAlignment = Alignment.BottomStart,
    ) {
        Crossfade(targetState = app, animationSpec = tween(300), label = "shelfBranding") { current ->
            if (current != null) {
                Column(
                    modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = current.label,
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (current.isTvApp) "TV app" else "App",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
