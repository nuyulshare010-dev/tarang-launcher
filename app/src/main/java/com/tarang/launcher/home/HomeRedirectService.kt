package com.tarang.launcher.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Fallback "be the home screen" mechanism (plan §2.1 / §5.2).
 *
 * The clean path — `cmd package set-home-activity` — does not reliably stick on Google TV, so
 * this service watches for the stock launcher coming to the foreground and bounces straight back
 * to Tarang.
 *
 * NOTE: this does NOT (and cannot) intercept the HOME key — HOME is consumed by the system's
 * window policy before any service sees it. We react to the resulting foreground change instead.
 */
class HomeRedirectService : AccessibilityService() {

    private var lastRedirectAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in STOCK_LAUNCHERS) return

        // Debounce: the launcher can emit several window events in a burst.
        val now = SystemClock.uptimeMillis()
        if (now - lastRedirectAt < DEBOUNCE_MS) return
        lastRedirectAt = now

        val intent = Intent(this, HomeActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
        )
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Home redirect failed", it) }
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "HomeRedirect"
        const val DEBOUNCE_MS = 800L

        /** Stock launcher packages we bounce away from. */
        val STOCK_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx", // Google TV (Chromecast w/ Google TV)
            "com.google.android.tvlauncher", // AOSP / older Android TV Leanback launcher
        )
    }
}
