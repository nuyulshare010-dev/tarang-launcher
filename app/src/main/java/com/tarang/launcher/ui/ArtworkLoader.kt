package com.tarang.launcher.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads preview-program poster artwork for the slideshow wallpaper. Posters come back from the
 * TvProvider as either http(s) URLs or content:// URIs (which scheme an app uses varies), so this
 * handles both, decodes down-sampled, caches decoded bitmaps, and logs failures (so an empty
 * slideshow on-device is diagnosable rather than silent).
 */
object ArtworkLoader {
    private const val TAG = "ArtworkLoader"
    private val cache = LruCache<String, ImageBitmap>(32)

    suspend fun load(context: Context, uriString: String, reqW: Int = 1280, reqH: Int = 720): ImageBitmap? {
        cache.get(uriString)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = runCatching { readBytes(context, uriString) }
                .onFailure { Log.w(TAG, "fetch failed [$uriString]: ${it.javaClass.simpleName} ${it.message}") }
                .getOrNull() ?: return@withContext null
            val bmp = runCatching { decodeSampled(bytes, reqW, reqH) }.getOrNull()
            if (bmp == null) Log.w(TAG, "decode failed [$uriString]") else cache.put(uriString, bmp)
            bmp
        }
    }

    private fun readBytes(context: Context, uriString: String): ByteArray {
        val uri = Uri.parse(uriString)
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                val conn = (URL(uriString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }
                try {
                    conn.inputStream.use(InputStream::readBytes)
                } finally {
                    conn.disconnect()
                }
            }

            else -> context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
                ?: error("null input stream")
        }
    }

    private fun decodeSampled(bytes: ByteArray, reqW: Int, reqH: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }
}
