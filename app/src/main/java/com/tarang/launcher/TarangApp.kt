package com.tarang.launcher

import android.app.Application

/**
 * Application entry point. M0 keeps this empty; the manual-DI `AppContainer`
 * (AppRepository, IconLoader, FavoritesStore) is introduced in M1.
 *
 * See plans/2026-06-28-tarang-launcher-design.md.
 */
class TarangApp : Application()
