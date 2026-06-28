package com.tarang.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads and caches app icons as [ImageBitmap]s, and derives a backdrop color for the Top Shelf.
 *
 * Icon fallback chain (plan §2.3 / §5.3): the activity's icon → the application icon →
 * a TV app's banner. Results are cached per package.
 */
class IconLoader(context: Context) {

    private val pm: PackageManager = context.applicationContext.packageManager
    private val iconCache = LruCache<String, ImageBitmap>(CACHE_ENTRIES)
    private val colorCache = LruCache<String, Int>(CACHE_ENTRIES)

    suspend fun load(app: AppInfo): ImageBitmap? {
        iconCache.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            val drawable = resolveDrawable(app) ?: return@withContext null
            val bitmap = drawable.toBitmap(width = RENDER_PX, height = RENDER_PX).asImageBitmap()
            iconCache.put(app.packageName, bitmap)
            bitmap
        }
    }

    /** Vibrant/dominant color of the app's icon, for the Top Shelf backdrop (plan §5.5). */
    suspend fun dominantColor(app: AppInfo): Color {
        colorCache.get(app.packageName)?.let { return Color(it) }
        return withContext(Dispatchers.IO) {
            val drawable = resolveDrawable(app)
            val rgb = drawable?.let {
                val bmp = it.toBitmap(width = PALETTE_PX, height = PALETTE_PX)
                val palette = Palette.from(bmp).generate()
                (palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch)?.rgb
            }
            val argb = rgb ?: DEFAULT_SHELF_ARGB
            colorCache.put(app.packageName, argb)
            Color(argb)
        }
    }

    private fun resolveDrawable(app: AppInfo): Drawable? =
        runCatching { pm.getActivityIcon(ComponentName(app.packageName, app.activityName)) }.getOrNull()
            ?: runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
            ?: runCatching { if (app.isTvApp) pm.getApplicationBanner(app.packageName) else null }.getOrNull()

    private companion object {
        const val CACHE_ENTRIES = 256
        const val RENDER_PX = 144
        const val PALETTE_PX = 64
        val DEFAULT_SHELF_ARGB = 0xFF1C1C1E.toInt()
    }
}
