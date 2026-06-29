package com.tarang.launcher.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Per-app counts of preview-program artwork available to drive a wallpaper slideshow. */
data class AppArtwork(val packageName: String, val posters: Int, val videos: Int)

/**
 * Reads the artwork that apps publish as home-screen preview programs (poster images, preview
 * videos) so it can be played as a wallpaper. Needs [TV_LISTINGS_PERMISSION]; returns empty when
 * not granted. The provider rejects a WHERE clause for unprivileged callers, so we scan all rows
 * and filter by package in code (same approach as [TvContentProbe]).
 */
object TvArtwork {
    private val PREVIEW = Uri.parse("content://android.media.tv/preview_program")

    private fun granted(context: Context) =
        ContextCompat.checkSelfPermission(context, TV_LISTINGS_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Poster/video counts keyed by package (only packages that publish at least one). */
    suspend fun availability(context: Context): Map<String, AppArtwork> = withContext(Dispatchers.IO) {
        if (!granted(context)) return@withContext emptyMap()
        val posters = HashMap<String, Int>()
        val videos = HashMap<String, Int>()
        runCatching {
            context.contentResolver.query(
                PREVIEW,
                arrayOf("package_name", "poster_art_uri", "preview_video_uri"),
                null, null, null,
            )?.use { c ->
                val pi = c.getColumnIndex("package_name")
                val poi = c.getColumnIndex("poster_art_uri")
                val vi = c.getColumnIndex("preview_video_uri")
                while (c.moveToNext()) {
                    val pkg = pi.takeIf { it >= 0 }?.let { c.getString(it) } ?: continue
                    if (poi >= 0 && !c.getString(poi).isNullOrBlank()) posters[pkg] = (posters[pkg] ?: 0) + 1
                    if (vi >= 0 && !c.getString(vi).isNullOrBlank()) videos[pkg] = (videos[pkg] ?: 0) + 1
                }
            }
        }
        (posters.keys + videos.keys).associateWith { AppArtwork(it, posters[it] ?: 0, videos[it] ?: 0) }
    }

    /** Distinct, non-blank poster URIs published by [packageName], capped at [limit]. */
    suspend fun posterUris(context: Context, packageName: String, limit: Int = 24): List<String> =
        withContext(Dispatchers.IO) {
            if (!granted(context)) return@withContext emptyList()
            val out = LinkedHashSet<String>()
            runCatching {
                context.contentResolver.query(
                    PREVIEW,
                    arrayOf("package_name", "poster_art_uri"),
                    null, null, null,
                )?.use { c ->
                    val pi = c.getColumnIndex("package_name")
                    val poi = c.getColumnIndex("poster_art_uri")
                    while (c.moveToNext() && out.size < limit) {
                        val pkg = pi.takeIf { it >= 0 }?.let { c.getString(it) } ?: continue
                        if (pkg != packageName) continue
                        val uri = poi.takeIf { it >= 0 }?.let { c.getString(it) }
                        if (!uri.isNullOrBlank()) out.add(uri)
                    }
                }
            }
            out.toList()
        }
}
