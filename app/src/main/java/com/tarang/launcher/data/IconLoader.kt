package com.tarang.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads and caches app icons as [ImageBitmap]s.
 *
 * Fallback chain (plan §2.3 / §5.3): the activity's icon → the application icon →
 * a TV app's banner. TV-only apps frequently ship a poor or missing square icon, so the
 * banner is the last resort before giving up. (A generated letter tile is deferred to M2.)
 */
class IconLoader(context: Context) {

    private val pm: PackageManager = context.applicationContext.packageManager
    private val cache = LruCache<String, ImageBitmap>(CACHE_ENTRIES)

    suspend fun load(app: AppInfo): ImageBitmap? {
        cache.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            val drawable = resolveDrawable(app) ?: return@withContext null
            val bitmap = drawable
                .toBitmap(width = RENDER_PX, height = RENDER_PX)
                .asImageBitmap()
            cache.put(app.packageName, bitmap)
            bitmap
        }
    }

    private fun resolveDrawable(app: AppInfo): Drawable? =
        runCatching { pm.getActivityIcon(ComponentName(app.packageName, app.activityName)) }.getOrNull()
            ?: runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
            ?: runCatching { if (app.isTvApp) pm.getApplicationBanner(app.packageName) else null }.getOrNull()

    private companion object {
        const val CACHE_ENTRIES = 256
        const val RENDER_PX = 144
    }
}
