package com.tarang.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * Top-level launcher UI. M1: a focusable app grid that launches apps.
 * The tvOS top shelf / dock / parallax polish arrive in M2+.
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
            else -> AppGrid(
                apps = uiState.apps,
                iconLoader = container.iconLoader,
                onAppFocused = viewModel::onAppFocused,
                onAppClicked = viewModel::launchApp,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
