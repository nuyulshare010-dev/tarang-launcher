package com.tarang.launcher.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LauncherUiState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val focusedPackage: String? = null,
)

class LauncherViewModel(private val repository: AppRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = repository.loadApps()
            _uiState.update { it.copy(isLoading = false, apps = apps) }
        }
    }

    /** Drives the (future) top shelf; ignored when the package is already focused. */
    fun onAppFocused(packageName: String) {
        _uiState.update { if (it.focusedPackage == packageName) it else it.copy(focusedPackage = packageName) }
    }

    fun launchApp(packageName: String) {
        repository.launch(packageName)
    }

    companion object {
        fun provideFactory(repository: AppRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LauncherViewModel(repository) }
        }
    }
}
