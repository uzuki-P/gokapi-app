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
 * Square thumbnails of image hotlinks, the desktop stand-in for the Android app's Coil
 * AsyncImage. Hotlinks need no API key. Images are scaled down on load, so the cache holds
 * small bitmaps only.
 */
internal object Thumbnails {
    private const val SIZE = 128
    private const val MAX_BYTES = 20L * 1024 * 1024
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val cache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 200
        },
    )

    @Composable
    fun remember(url: String): State<ImageBitmap?> = produceState(cache[url], url) {
        if (value == null) value = withContext(Dispatchers.IO) { runCatching { load(url) }.getOrNull() }
    }

    private fun load(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        val bytes = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null || body.contentLength() > MAX_BYTES) return null
            body.bytes()
        }
        val image = Image.makeFromEncoded(bytes)
        // Center crop into a SIZE square, as ContentScale.Crop would.
        val side = minOf(image.width, image.height).toFloat()
        val source = Rect.makeXYWH((image.width - side) / 2, (image.height - side) / 2, side, side)
        val surface = Surface.makeRasterN32Premul(SIZE, SIZE)
        surface.canvas.drawImageRect(image, source, Rect.makeWH(SIZE.toFloat(), SIZE.toFloat()))
        val bitmap = surface.makeImageSnapshot().toComposeImageBitmap()
        image.close()
        surface.close()
        cache[url] = bitmap
        return bitmap
    }
}
