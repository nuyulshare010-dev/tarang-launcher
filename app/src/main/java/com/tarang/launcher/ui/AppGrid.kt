package com.tarang.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.IconLoader

@Composable
fun AppGrid(
    apps: List<AppInfo>,
    iconLoader: IconLoader,
    onAppFocused: (String) -> Unit,
    onAppClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstItem = remember { FocusRequester() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(48.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppCard(
                app = app,
                iconLoader = iconLoader,
                onFocused = { onAppFocused(app.packageName) },
                onClick = { onAppClicked(app.packageName) },
                modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
            )
        }
    }

    // Land focus on the first app once the list is populated.
    LaunchedEffect(apps.firstOrNull()?.packageName) {
        if (apps.isNotEmpty()) runCatching { firstItem.requestFocus() }
    }
}

private const val GRID_COLUMNS = 6
