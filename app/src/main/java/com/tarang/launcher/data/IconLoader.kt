package com.tarang.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artwork for one app tile.
 * - [Banner]: the app's wide banner image (the tvOS-style look).
 * - [Fallback]: apps without a banner — their square icon centered on a color drawn from it.
 */
sealed interface TileArt {
    data class Banner(val image: androidx.compose.ui.graphics.ImageBitmap) : TileArt
    data class Fallback(val icon: androidx.compose.ui.graphics.ImageBitmap?, val color: Color) : TileArt
}

/**
 * Resolves per-app tile artwork (plan §2.3 / §5.3) and a brand accent color. Prefers the app's
 * banner so tiles look like tvOS/Google TV; falls back to icon-on-color when no banner is provided.
 * Cached per package.
 */
class IconLoader(context: Context) {

    private val pm: PackageManager = context.applicationContext.packageManager
    private val tileCache = LruCache<String, TileArt>(CACHE_ENTRIES)
    private val colorCache = LruCache<String, Int>(CACHE_ENTRIES)

    suspend fun loadTile(app: AppInfo): TileArt {
        tileCache.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            val banner = resolveBanner(app)
            val tile = if (banner != null) {
                TileArt.Banner(banner.toBitmap(BANNER_W, BANNER_H).asImageBitmap())
            } else {
                val iconDrawable = resolveIcon(app)
                TileArt.Fallback(
                    icon = iconDrawable?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap(),
                    color = iconDrawable?.let { Color(colorFromDrawable(it)) } ?: DEFAULT_TILE_COLOR,
                )
            }
            tileCache.put(app.packageName, tile)
            tile
        }
    }

    /** Brand color drawn from the app's icon, used for the ambient wallpaper tint. */
    suspend fun accentColor(app: AppInfo): Color {
        colorCache.get(app.packageName)?.let { return Color(it) }
        return withContext(Dispatchers.IO) {
            val argb = resolveIcon(app)?.let { colorFromDrawable(it) } ?: DEFAULT_TILE_ARGB
            colorCache.put(app.packageName, argb)
            Color(argb)
        }
    }

    private fun resolveBanner(app: AppInfo): Drawable? =
        runCatching { pm.getActivityBanner(ComponentName(app.packageName, app.activityName)) }.getOrNull()
            ?: runCatching { pm.getApplicationBanner(app.packageName) }.getOrNull()

    private fun resolveIcon(app: AppInfo): Drawable? =
        runCatching { pm.getActivityIcon(ComponentName(app.packageName, app.activityName)) }.getOrNull()
            ?: runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()

    private fun colorFromDrawable(drawable: Drawable): Int {
        val bmp = drawable.toBitmap(PALETTE_PX, PALETTE_PX)
        val palette = Palette.from(bmp).generate()
        return (palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch)?.rgb
            ?: DEFAULT_TILE_ARGB
    }

    private companion object {
        const val CACHE_ENTRIES = 256
        const val BANNER_W = 320
        const val BANNER_H = 180 // 16:9 native banner; the UI crops it to the 5:3 tile
        const val ICON_PX = 144
        const val PALETTE_PX = 64
        val DEFAULT_TILE_ARGB = 0xFF2A2A2C.toInt()
        val DEFAULT_TILE_COLOR = Color(0xFF2A2A2C)
    }
}
