package com.tarang.launcher

import android.app.Application
import com.tarang.launcher.di.AppContainer

/**
 * Application entry point. Owns the manual-DI [AppContainer] (plan §4).
 */
class TarangApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
