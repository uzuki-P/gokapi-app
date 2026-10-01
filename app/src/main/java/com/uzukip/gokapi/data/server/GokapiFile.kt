package com.uzukip.gokapi.data.server

import kotlinx.serialization.json.*
import java.util.Locale
import kotlin.math.ceil

data class GokapiFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val size: String,
    val contentType: String,
    val uploadDate: Long,
    val expireAt: Long,
    val unlimitedTime: Boolean,
    val downloadsRemaining: Long,
    val unlimitedDownloads: Boolean,
    val hotlinkId: String,
    val encrypted: Boolean = false,
) {
    val isApk get() = name.endsWith(".apk", true) || contentType == "application/vnd.android.package-archive"
    val isImage get() = contentType.startsWith("image/")
    val type get() = when {
        isApk -> "APK"
        isImage -> "Images"
        contentType.startsWith("video/") || contentType.startsWith("audio/") -> "Media"
        else -> "Documents"
    }
    val glyph get() = when {
        isApk -> "android"
        isImage -> "image-outline"
        contentType.startsWith("video/") -> "video-outline"
        contentType.startsWith("audio/") -> "music-note"
        else -> "file-document-outline"
    }
    fun expiry(now: Long = System.currentTimeMillis() / 1000): String = when {
        unlimitedTime -> "Unlimited time"
        expireAt <= now -> "Expired"
        expireAt - now < 86400 -> "Expires in ${ceil((expireAt - now) / 3600.0).toInt()} h"
        else -> "Expires in ${ceil((expireAt - now) / 86400.0).toInt()} d"
    }
    fun age(now: Long = System.currentTimeMillis() / 1000): String {
        val seconds = (now - uploadDate).coerceAtLeast(0)
        return when {
            seconds < 60 -> "Just now"
            seconds < 3600 -> "${seconds / 60} min ago"
            seconds < 86400 -> "${seconds / 3600} h ago"
            else -> "${seconds / 86400} d ago"
        }
    }
    fun link(server: String, imageHotlink: Boolean = false): String =
        if (imageHotlink && isImage && hotlinkId.isNotBlank()) "$server/h/${encodePath(hotlinkId)}"
        else "$server/d?id=${encodePath(id)}"
}

internal fun encodePath(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
internal fun JsonObject.string(name: String, fallback: String = "") = this[name]?.jsonPrimitive?.contentOrNull ?: fallback
internal fun JsonObject.long(name: String) = this[name]?.jsonPrimitive?.longOrNull ?: 0L
internal fun JsonObject.flag(name: String) = this[name]?.jsonPrimitive?.booleanOrNull ?: false

fun parseFile(value: JsonElement): GokapiFile {
    val f = value.jsonObject
    val id = f.string("Id")
    require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "The server returned an invalid file ID." }
    return GokapiFile(
        id = id, name = f.string("Name", "file"), sizeBytes = f.long("SizeBytes"),
        size = f.string("Size", formatBytes(f.long("SizeBytes"))),
        contentType = f.string("ContentType", "application/octet-stream").ifBlank { "application/octet-stream" },
        uploadDate = f.long("UploadDate"), expireAt = f.long("ExpireAt"),
        unlimitedTime = f.flag("UnlimitedTime"), downloadsRemaining = f.long("DownloadsRemaining"),
        unlimitedDownloads = f.flag("UnlimitedDownloads"), hotlinkId = f.string("HotlinkId"),
        encrypted = f.flag("RequiresClientSideDecryption") || f.flag("IsEndToEndEncrypted"),
    )
}

fun parseFiles(body: String): List<GokapiFile> {
    val json = Json.parseToJsonElement(body)
    if (json == JsonNull) return emptyList()
    return json.jsonArray.map(::parseFile).sortedByDescending { it.uploadDate }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

fun newerAppVersion(filename: String, current: String, dev: Boolean): Boolean {
    val pattern = if (dev) Regex("DEV-gokapi-android-(\\d+)\\.(\\d+)\\.(\\d+)-dev_.*\\.apk")
        else Regex("gokapi-android-(\\d+)\\.(\\d+)\\.(\\d+)-.*\\.apk")
    val candidate = pattern.matchEntire(filename)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return false } ?: return false
    val running = current.substringBefore('-').split('.').mapNotNull(String::toIntOrNull)
    if (running.size != 3) return false
    for (i in 0..2) if (candidate[i] != running[i]) return candidate[i] > running[i]
    return false
}
