package com.tarang.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader

/**
 * tvOS-style home layout: a "dock" (favorites) row on top, then the remaining apps as a grid
 * of rows. Implemented as a LazyColumn of rows rather than LazyVerticalGrid for reliable D-pad
 * focus traversal on TV. The dock is a placeholder (the first row of apps) until M4 adds
 * editable, persisted favorites.
 */
@Composable
fun LauncherContent(
    apps: List<AppInfo>,
    iconLoader: IconLoader,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dock = remember(apps) { apps.take(DOCK_SIZE) }
    val gridRows = remember(apps) { apps.drop(DOCK_SIZE).chunked(COLUMNS) }
    val firstCard = remember { FocusRequester() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        if (dock.isNotEmpty()) {
            item(key = "dock") {
                AppRow(
                    apps = dock,
                    iconLoader = iconLoader,
                    onAppFocused = onAppFocused,
                    onAppClicked = onAppClicked,
                    firstCardFocusRequester = firstCard,
                )
            }
        }
        items(gridRows, key = { row -> row.first().packageName }) { row ->
            AppRow(
                apps = row,
                iconLoader = iconLoader,
                onAppFocused = onAppFocused,
                onAppClicked = onAppClicked,
            )
        }
    }

    LaunchedEffect(apps.firstOrNull()?.packageName) {
        if (apps.isNotEmpty()) runCatching { firstCard.requestFocus() }
    }
}

private const val DOCK_SIZE = 6
private const val COLUMNS = 6
