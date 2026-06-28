package com.tarang.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.tarang.launcher.di.AppContainer
import com.tarang.launcher.viewmodel.LauncherViewModel

/**
 * Top-level launcher UI: an animated wallpaper behind a clean app grid. No content rows or
 * recommendations — the wallpaper gently tints toward the focused app's color.
 */
@Composable
fun LauncherScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val viewModel: LauncherViewModel = viewModel(
        factory = LauncherViewModel.provideFactory(container.appRepository, container.favoritesStore),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val focusedApp = remember(uiState.focusedPackage, uiState.allApps) {
        uiState.allApps.firstOrNull { it.packageName == uiState.focusedPackage }
    }
    val ambient: Color? by produceState<Color?>(initialValue = null, focusedApp?.packageName) {
        value = focusedApp?.let { container.iconLoader.accentColor(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedWallpaper(ambient = ambient, modifier = Modifier.fillMaxSize())

        when {
            uiState.isLoading -> Centered { Text("Loading apps…", color = Color.White, fontSize = 20.sp) }
            uiState.allApps.isEmpty() -> Centered { Text("No apps found", color = Color.White, fontSize = 20.sp) }
            else -> LauncherContent(
                dockApps = uiState.dockApps,
                gridApps = uiState.gridApps,
                iconLoader = container.iconLoader,
                onAppFocused = viewModel::onAppFocused,
                onAppClicked = viewModel::launchApp,
                onToggleFavorite = viewModel::toggleFavorite,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
