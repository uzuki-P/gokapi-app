package com.uzukip.gokapi.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uzukip.gokapi.data.server.FilePreview
import com.uzukip.gokapi.data.server.GokapiFile
import com.uzukip.gokapi.data.server.PreviewSize
import com.uzukip.gokapi.data.server.PreviewStore
import com.uzukip.gokapi.data.server.ServerCredentials
import java.util.Collections
import java.util.LinkedHashMap

/** A preview ready to draw: a decoded image or the first lines of a text file. */
sealed interface ShownPreview {
    class Picture(val bitmap: ImageBitmap) : ShownPreview
    class Text(val text: String) : ShownPreview
}

/**
 * Loads previews from the thumbnail service through [store] and keeps the decoded ones in
 * memory. [decode] turns the service's WebP bytes into an image: BitmapFactory on Android,
 * Skia on desktop. Pure Compose, so the desktop app compiles it from here.
 */
@Immutable
class PreviewSource(
    private val store: PreviewStore,
    private val credentials: ServerCredentials?,
    private val decode: (ByteArray) -> ImageBitmap?,
) {
    /** Whether this source already uses [c], so a refresh keeps its decoded images. */
    fun uses(c: ServerCredentials) = credentials != null && credentials.serverUrl == c.serverUrl && credentials.apiKey == c.apiKey

    private val memory: MutableMap<String, ShownPreview> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ShownPreview>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShownPreview>?) = size > 150
        },
    )

    /** The file's preview, or null while it loads, when it has none, or off the tailnet. */
    @Composable
    fun preview(file: GokapiFile, size: PreviewSize): ShownPreview? {
        val key = "${file.id}-${size.query}"
        return produceState(memory[key], key, credentials) {
            if (value != null) return@produceState
            val loaded = credentials?.let { store.load(it, file, size) } ?: store.cached(file, size)
            value = when (loaded) {
                is FilePreview.Image -> decode(loaded.bytes)?.let(ShownPreview::Picture)
                is FilePreview.Text -> loaded.text.takeIf { it.isNotBlank() }?.let(ShownPreview::Text)
                else -> null
            }?.also { memory[key] = it }
        }.value
    }
}

/** The app's preview source; null shows type badges only. */
val LocalPreviewSource = staticCompositionLocalOf<PreviewSource?> { null }

private fun GokapiFile.badgeColor() = if (isApk) "#3D8075" else "#6376A8"

/**
 * The extension chip's fill: one fixed color per family so an APK and an EXE of the same app
 * tell apart at a glance; other extensions get a stable color from the palette.
 */
internal fun extensionChipColor(extension: String): Color = when (extension) {
    "apk", "aab", "xapk" -> Color(0xFF2E7D32)
    "exe", "msi", "msix" -> Color(0xFF0B63C4)
    "appimage", "deb", "rpm", "flatpak", "sh" -> Color(0xFFC25E00)
    "dmg", "pkg" -> Color(0xFF5F6368)
    "zip", "7z", "rar", "tar", "gz", "tgz", "xz", "zst" -> Color(0xFF8D6E00)
    "pdf" -> Color(0xFFC62828)
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "avif", "svg" -> Color(0xFF7B1FA2)
    "mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp" -> Color(0xFFAD1457)
    "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav" -> Color(0xFF00796B)
    "md", "markdown", "txt", "log", "json", "csv" -> Color(0xFF546E7A)
    else -> ChipPalette[Math.floorMod(extension.hashCode(), ChipPalette.size)]
}

private val ChipPalette = listOf(Color(0xFF3949AB), Color(0xFF00838F), Color(0xFF6D4C41), Color(0xFF558B2F), Color(0xFF5E35B1))

/** The file's extension on a colored chip, set over the thumbnail's bottom-right corner. */
@Composable
private fun ExtensionChip(label: String, extension: String, modifier: Modifier = Modifier) {
    Text(
        label,
        color = Color.White,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        modifier = modifier
            .background(extensionChipColor(extension), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * A file row's leading square: the file's thumbnail, a miniature page of text, or its type
 * badge, with its extension on a colored chip at the bottom-right.
 */
@Composable
fun FileThumbnail(file: GokapiFile, size: Dp) {
    val label = file.extensionLabel
    Box(Modifier.size(size)) {
        FileThumbnailTile(file, size)
        if (label != null) ExtensionChip(label, file.extension, Modifier.align(Alignment.BottomEnd).offset(x = 5.dp, y = 3.dp))
    }
}

@Composable
private fun FileThumbnailTile(file: GokapiFile, size: Dp) {
    val shape = RoundedCornerShape(16.dp)
    when (val shown = LocalPreviewSource.current?.preview(file, PreviewSize.SMALL)) {
        is ShownPreview.Picture -> Box(Modifier.size(size).clip(shape)) {
            // Icons (APK, EXE) keep their own shape; photos and frames fill the tile.
            Image(shown.bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (file.isVideo) PlayMark(size / 2.4f, Modifier.align(Alignment.Center))
        }
        is ShownPreview.Text -> Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size),
        ) {
            // A page in miniature: the first lines, small enough to fit a few words each.
            Text(
                shown.text,
                fontSize = (size.value / 8.5f).sp,
                lineHeight = (size.value / 7f).sp,
                maxLines = 5,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 6.dp),
            )
        }
        null -> IconBadge(file.glyph, file.badgeColor(), size)
    }
}

/**
 * The details pane's large preview: the whole image fitted, the first lines of a text file,
 * or the type badge of [badgeSize].
 */
@Composable
fun FileLargePreview(file: GokapiFile, badgeSize: Dp) {
    when (val shown = LocalPreviewSource.current?.preview(file, PreviewSize.LARGE)) {
        is ShownPreview.Picture -> Box(Modifier.fillMaxSize()) {
            Image(shown.bitmap, contentDescription = file.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (file.isVideo) PlayMark(56.dp, Modifier.align(Alignment.Center))
        }
        is ShownPreview.Text -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.CenterStart) {
            val lines = shown.text.lines()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lines.first(), style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (lines.size > 1) {
                    Text(
                        lines.drop(1).joinToString("\n"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        null -> IconBadge(file.glyph, file.badgeColor(), badgeSize)
    }
}

/** A round play mark over a video frame, so a frame never reads as a photo. */
@Composable
private fun PlayMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(Color.Black.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val glyph = MciGlyphs.glyphString("play") ?: "▶"
        Text(glyph, fontFamily = MciFamily, fontSize = (size.value * 0.6f).sp, color = Color.White)
    }
}
