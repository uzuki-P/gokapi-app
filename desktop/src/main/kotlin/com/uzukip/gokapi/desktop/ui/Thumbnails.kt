package com.uzukip.gokapi.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * Thumbnails and previews of image hotlinks, the desktop stand-in for the Android app's Coil
 * AsyncImage. Hotlinks need no API key. Images are scaled down on load, so the cache holds
 * small bitmaps only: 128 px thumbnails and previews of at most 960 px.
 */
internal object Thumbnails {
    private const val SIZE = 128
    private const val MAX_BYTES = 20L * 1024 * 1024
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val cache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 120
        },
    )

    /** A square thumbnail, center-cropped as ContentScale.Crop would. */
    @Composable
    fun remember(url: String): State<ImageBitmap?> = load(url, SIZE, crop = true)

    /** The whole image, scaled to fit [maxSide], for the details pane. */
    @Composable
    fun preview(url: String, maxSide: Int = 960): State<ImageBitmap?> = load(url, maxSide, crop = false)

    @Composable
    private fun load(url: String, size: Int, crop: Boolean): State<ImageBitmap?> {
        val key = "$url@$size${if (crop) "c" else ""}"
        return produceState(cache[key], key) {
            if (value == null) value = withContext(Dispatchers.IO) { runCatching { decode(url, size, crop) }.getOrNull() }?.also { cache[key] = it }
        }
    }

    private fun decode(url: String, size: Int, crop: Boolean): ImageBitmap? {
        val bytes = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null || body.contentLength() > MAX_BYTES) return null
            body.bytes()
        }
        val image = Image.makeFromEncoded(bytes)
        val (source, width, height) = if (crop) {
            val side = minOf(image.width, image.height).toFloat()
            Triple(Rect.makeXYWH((image.width - side) / 2, (image.height - side) / 2, side, side), size, size)
        } else {
            val scale = minOf(1f, size.toFloat() / maxOf(image.width, image.height))
            Triple(Rect.makeWH(image.width.toFloat(), image.height.toFloat()), maxOf(1, (image.width * scale).toInt()), maxOf(1, (image.height * scale).toInt()))
        }
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawImageRect(image, source, Rect.makeWH(width.toFloat(), height.toFloat()))
        val bitmap = surface.makeImageSnapshot().toComposeImageBitmap()
        image.close()
        surface.close()
        return bitmap
    }
}
