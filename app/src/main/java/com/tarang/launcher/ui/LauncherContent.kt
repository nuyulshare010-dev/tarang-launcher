package com.tarang.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.WatchNextItem
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DockShape = RoundedCornerShape(36.dp)
private val SidePad = 56.dp // horizontal screen margin (matches the top bar)
private val TileGap = 24.dp // gap between tiles in a row (matches AppRow's arrangement)
private val DockPad = 20.dp // inner padding of the frosted dock container
// Scrollable top space that drops the dock near the bottom (Apple-TV style). Because the bring-into-
// view spec below won't scroll an already-visible item, the dock stays put here; it only scrolls
// away when you move into the grid, letting the list use the full screen height without clipping.
private val DockTopGap = 300.dp
private val ContinueTopGap = 84.dp // smaller top gap when the Continue row occupies the upper area
private val ContinueCardW = 220.dp // 16:9 poster cards (independent of grid column size)
private val ContinueCardH = 124.dp

/**
 * Minimal bring-into-view: don't move an item that's already fully visible (keeps the dock low on
 * focus), and otherwise scroll the least amount to reveal it (so the grid scrolls up cleanly). The
 * default TV behaviour over-scrolls, pulling the dock up and eating the top gap.
 */
@OptIn(ExperimentalFoundationApi::class)
private val MinimalBringIntoView = object : BringIntoViewSpec {
    private val inset = 24f // keep a focused row a hair off the screen edges (covers the focus scale)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leading = offset - inset
        val trailing = offset + size + inset
        return when {
            leading >= 0f && trailing <= containerSize -> 0f // already visible (with margin): don't move
            size + inset * 2 > containerSize -> 0f // taller than the viewport: leave it
            abs(leading) < abs(trailing - containerSize) -> leading
            else -> trailing - containerSize
        }
    }
}

/**
 * tvOS-style home layout: a frosted "dock" (favorites) row on top, then the rest as a grid of rows.
 * A LazyColumn of rows (not LazyVerticalGrid) for reliable D-pad focus on TV. [topFocusRequester]
 * is where the top row sends D-pad UP (the settings button).
 *
 * Interactions: long-press a grid tile to pin it to the dock ([onToggleFavorite]); long-press a dock
 * tile to "lift" it into move mode and rearrange/remove it (committed via [onReorder]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherContent(
    dockApps: List<AppInfo>,
    gridApps: List<AppInfo>,
    iconLoader: IconLoader,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    columns: Int,
    backdrop: GraphicsLayer,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester? = null,
    onFavoriteHover: (String?) -> Unit = {},
    accent: Color? = null,
    watchNext: List<WatchNextItem> = emptyList(),
    showContinueRow: Boolean = true,
    onWatchNextClick: (WatchNextItem) -> Unit = {},
    reduceMotion: Boolean = false,
) {
    val gridRows = remember(gridApps, columns) { gridApps.chunked(columns) }
    val firstCard = remember { FocusRequester() }
    val firstContinueCard = remember { FocusRequester() }
    val hasDock = dockApps.isNotEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val colors = LocalLauncherColors.current

    // Move mode operates on a local working copy of the dock; it's committed (or abandoned) on exit.
    var movingPackage by remember { mutableStateOf<String?>(null) }
    var workingDock by remember(dockApps) { mutableStateOf(dockApps) }
    val shownDock = if (movingPackage != null) workingDock else dockApps

    // Long-press opens a context menu for the app (favorite/unfavorite, and reorder for dock apps).
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    val openMenu: (String) -> Unit = { pkg ->
        menuApp = (dockApps + gridApps).firstOrNull { it.packageName == pkg }
    }

    fun moveBy(dir: Int) {
        val cur = workingDock.indexOfFirst { it.packageName == movingPackage }
        val target = cur + dir
        if (cur >= 0 && target in workingDock.indices) {
            workingDock = workingDock.toMutableList().also { it.add(target, it.removeAt(cur)) }
        }
    }
    fun commitMove() {
        onReorder(workingDock.map { it.packageName })
        movingPackage = null
    }
    fun removeMoving() {
        movingPackage?.let { onToggleFavorite(it) } // toggle off = unpin from the dock
        movingPackage = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Size tiles to fill the row at the chosen column count (the grid stretches edge-to-edge).
        // The dock reuses the grid size, but shrinks to fit if it holds more tiles than a row.
        val availWidth = maxWidth - SidePad * 2
        val gridTileW = (availWidth - TileGap * (columns - 1)) / columns
        val gridTileH = gridTileW * TILE_ASPECT
        val dockCount = shownDock.size.coerceAtLeast(1)
        val dockTileW = minOf(gridTileW, (availWidth - DockPad * 2 - TileGap * (dockCount - 1)) / dockCount)
        val dockTileH = dockTileW * TILE_ASPECT

        // When the Continue row is shown it takes the upper area (above the dock), so the top gap
        // shrinks; otherwise the dock stays pinned low with the empty top gap above it.
        val continueShown = showContinueRow && watchNext.isNotEmpty()
        val topGap = if (continueShown) ContinueTopGap else DockTopGap

        CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = SidePad, end = SidePad, top = topGap, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (continueShown) {
                item(key = "continue") {
                    ContinueRow(
                        items = watchNext,
                        cardWidth = ContinueCardW,
                        cardHeight = ContinueCardH,
                        onClick = onWatchNextClick,
                        animate = !reduceMotion,
                        upFocusRequester = topFocusRequester, // UP from Continue -> settings
                        firstCardFocusRequester = firstContinueCard, // dock UP lands here
                    )
                }
            }
            if (hasDock) {
                item(key = "dock") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // When focus returns to the dock, scroll back so the layout sits at the
                            // top. When focus leaves the dock entirely, clear the artwork hover.
                            .onFocusChanged {
                                if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } else onFavoriteHover(null)
                            }
                            .frostedGlass(backdrop, DockShape, tint = colors.chrome, accent = accent)
                            .padding(DockPad),
                    ) {
                        AppRow(
                            apps = shownDock,
                            iconLoader = iconLoader,
                            // Dock = favorites: report which one is hovered so its artwork can play.
                            onAppFocused = { onAppFocused(it); onFavoriteHover(it) },
                            onAppClicked = onAppClicked,
                            onAppLongPressed = openMenu, // long-press a dock tile -> context menu
                            tileWidth = dockTileW,
                            tileHeight = dockTileH,
                            firstCardFocusRequester = firstCard,
                            // UP from the dock goes to the Continue row when it's shown, else the bar.
                            // (Default focus search skips over the row to the top bar, so wire it.)
                            upFocusRequester = if (continueShown) firstContinueCard else topFocusRequester,
                            movingPackage = movingPackage,
                            onMove = ::moveBy,
                            onRemoveFromDock = ::removeMoving,
                            onCommitMove = ::commitMove,
                        )
                    }
                }
            }
            itemsIndexed(gridRows, key = { _, row -> row.first().packageName }) { index, row ->
                AppRow(
                    apps = row,
                    iconLoader = iconLoader,
                    onAppFocused = onAppFocused,
                    onAppClicked = onAppClicked,
                    onAppLongPressed = openMenu, // long-press a grid tile -> context menu
                    tileWidth = gridTileW,
                    tileHeight = gridTileH,
                    firstCardFocusRequester = if (!hasDock && index == 0) firstCard else null,
                    // Only the very top row sends UP to the settings button.
                    upFocusRequester = if (!hasDock && index == 0) topFocusRequester else null,
                )
            }
        }
        }

        if (movingPackage != null) {
            MoveHint(colors = colors, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }

    menuApp?.let { app ->
        AppContextMenu(
            appLabel = app.label,
            isFavorite = dockApps.any { it.packageName == app.packageName },
            onToggleFavorite = { onToggleFavorite(app.packageName) },
            onMove = {
                movingPackage = app.packageName // enter move mode for this dock tile
                menuApp = null
            },
            onDismiss = { menuApp = null },
        )
    }

    val firstPackage = dockApps.firstOrNull()?.packageName ?: gridApps.firstOrNull()?.packageName
    LaunchedEffect(firstPackage) {
        if (firstPackage != null) runCatching { firstCard.requestFocus() }
    }
}

@Composable
private fun MoveHint(colors: LauncherColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.panel)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(
            text = "←  →  Move      ↑  Remove      OK  Done",
            color = colors.text,
            fontSize = 14.sp,
        )
    }
}
