package com.tarang.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * Top-level launcher UI: the Top Shelf hero (top ~40%) over the dock + app grid (~60%).
 * The shelf reacts to the focused app, which the grid reports up through the ViewModel.
 */
@Composable
fun LauncherScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val viewModel: LauncherViewModel = viewModel(
        factory = LauncherViewModel.provideFactory(container.appRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            uiState.isLoading -> Text("Loading apps…", color = Color.White, fontSize = 20.sp)
            uiState.apps.isEmpty() -> Text("No apps found", color = Color.White, fontSize = 20.sp)
            else -> {
                val focusedApp = remember(uiState.focusedPackage, uiState.apps) {
                    uiState.apps.firstOrNull { it.packageName == uiState.focusedPackage }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    TopShelf(
                        app = focusedApp,
                        iconLoader = container.iconLoader,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(TOP_SHELF_WEIGHT),
                    )
                    LauncherContent(
                        apps = uiState.apps,
                        iconLoader = container.iconLoader,
                        onAppFocused = viewModel::onAppFocused,
                        onAppClicked = viewModel::launchApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(GRID_WEIGHT),
                    )
                }
            }
        }
    }
}

private const val TOP_SHELF_WEIGHT = 0.4f
private const val GRID_WEIGHT = 0.6f
