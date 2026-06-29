package com.tarang.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.WatchNextItem

/**
 * The home-screen "Continue watching" row — the system Watch Next entries as poster cards that
 * resume the show on click. Sits between the dock and the app grid.
 */
@Composable
fun ContinueRow(
    items: List<WatchNextItem>,
    cardWidth: Dp,
    cardHeight: Dp,
    onClick: (WatchNextItem) -> Unit,
    animate: Boolean,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    firstCardFocusRequester: FocusRequester? = null,
) {
    val colors = LocalLauncherColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Continue watching",
            color = colors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            itemsIndexed(items, key = { _, it -> it.packageName + it.title }) { index, item ->
                WatchNextCard(
                    item = item,
                    width = cardWidth,
                    height = cardHeight,
                    animate = animate,
                    onClick = { onClick(item) },
                    upFocusRequester = upFocusRequester,
                    focusRequester = if (index == 0) firstCardFocusRequester else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WatchNextCard(
    item: WatchNextItem,
    width: Dp,
    height: Dp,
    animate: Boolean,
    onClick: () -> Unit,
    upFocusRequester: FocusRequester?,
    focusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(height * 0.16f)
    var focused by remember { mutableStateOf(false) }
    val poster by produceState<ImageBitmap?>(initialValue = null, key1 = item.posterUri) {
        value = item.posterUri?.let { ArtworkLoader.load(context, it, 640, 360) }
    }
    val scale by animateFloatAsState(
        targetValue = if (focused && animate) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "wnScale",
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(width = width, height = height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 8.dp else 0.dp, shape, clip = false)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2C),
            focusedContainerColor = Color(0xFF2A2A2C),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            poster?.let {
                Image(bitmap = it, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            // Title plate at the bottom (over a gradient) — legible over any poster.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}
