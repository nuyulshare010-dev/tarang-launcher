package com.tarang.launcher.data

/**
 * A launchable app discovered from the PackageManager.
 *
 * @param isTvApp true if it was found via CATEGORY_LEANBACK_LAUNCHER (a real TV app),
 *   false if it only exposes a phone-style CATEGORY_LAUNCHER entry.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val isTvApp: Boolean,
)
