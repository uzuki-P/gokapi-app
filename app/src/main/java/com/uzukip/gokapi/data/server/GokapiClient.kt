package com.uzukip.gokapi.data.server

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

enum class Route { DIRECT, RELAY }
class ConnectionReport(
    val version: String, val canViewFiles: Boolean, val canUpload: Boolean = false,
    val route: Route = Route.DIRECT,
    /** Set when the relay is configured, the tailnet worked, and the relay check failed. */
    val relayError: String? = null,
)
class ConnectionFailure(message: String) : IOException(message)
class PermissionFailure(val permission: String) : IOException("Access denied. Check the API key and its $permission permission.")
data class UploadOptions(val expiryDays: Int = 7, val downloads: Int = 0, val password: String = "") {
    override fun toString() = "UploadOptions(expiryDays=$expiryDays, downloads=$downloads)"
}
data class UploadLimits(val maxFileBytes: Long, val maxChunkBytes: Long)

fun normalizeServerUrl(input: String): String {
    val url = input.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Enter a valid HTTPS server URL.")
    require(url.isHttps) { "Use HTTPS to protect your API key." }
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
        "Enter the server URL without a login, query, or fragment."
    }
    return url.toString().trimEnd('/')
}

class GokapiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where the thumbnail service is for a server URL; tests point it at a mock server. */
    private val thumbsBase: (String) -> String? = ::thumbsUrl,
) {
    // Requests try the tailnet URL first. When it is unreachable and the credentials have a
    // relay, they use the relay for RELAY_HOLD_MS before trying the tailnet again. A failed
    // connect sent nothing, so any request, uploads included, can be retried on the relay.
    private val relayUntil = ConcurrentHashMap<String, Long>()
    // The tailnet name still resolves off the tailnet, so a short connect timeout keeps the
    // fallback quick.
    private val direct by lazy { http.newBuilder().connectTimeout(4, TimeUnit.SECONDS).build() }

    private suspend fun <T> network(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: UnknownHostException) { throw ConnectionFailure("Can't reach the files host. Is Tailscale on? Check the server URL too.") }
        catch (e: ConnectException) { throw ConnectionFailure("Can't connect to the files host. Is Tailscale on and the server running?") }
        catch (e: SocketTimeoutException) { throw ConnectionFailure("The server timed out. Check Tailscale and try again.") }
        catch (e: SSLException) { throw ConnectionFailure("The secure connection failed. Check the server's HTTPS certificate.") }
        catch (e: IllegalArgumentException) { throw ConnectionFailure("The server returned an unexpected response. Check the server URL.") }
        catch (e: IllegalStateException) { throw ConnectionFailure("The server returned an unexpected response. Check the server URL.") }
    }

    private fun builder(c: ServerCredentials, path: String) = Request.Builder()
        .url("${c.serverUrl}/api/$path").header("apikey", c.apiKey)

    private suspend fun send(client: OkHttpClient, request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, unused, _ -> unused.close() }
                }
            })
        }

    private suspend fun sendViaRelay(c: ServerCredentials, relay: RelayRoute, request: Request): Response {
        val url = request.url.toString()
        require(url.startsWith(c.serverUrl + "/"))
        val relayed = request.newBuilder().url(relay.url + url.removePrefix(c.serverUrl))
            .header("X-Relay-Id", relay.id)
            .header("X-Relay-Secret", relay.secret)
            .build()
        return try { send(http, relayed) } catch (e: IOException) {
            if (e.unreachable() || e is SocketTimeoutException) {
                throw ConnectionFailure("Can't reach the files server or its relay. Check your internet connection.")
            }
            throw e
        }
    }

    private suspend fun execute(c: ServerCredentials, request: Request, permission: String, route: Route? = null): Response {
        val relay = c.relay
        val response = when {
            relay == null -> send(http, request)
            route == Route.RELAY || (route == null && clock() < (relayUntil[c.serverUrl] ?: 0L)) -> sendViaRelay(c, relay, request)
            else -> try { send(direct, request) } catch (e: IOException) {
                if (route != null || !e.unreachable()) throw e
                relayUntil[c.serverUrl] = clock() + RELAY_HOLD_MS
                sendViaRelay(c, relay, request)
            }
        }
        if (!response.isSuccessful) {
            val code = response.code
            val relayed = !response.request.url.toString().startsWith(c.serverUrl + "/")
            val relayError = if (relayed) response.header("X-Relay-Error") else null
            response.close()
            // The relay Worker marks its own refusals; anything else came from Gokapi.
            when (relayError) {
                "unauthorized" -> throw ConnectionFailure("The relay rejected this device. Pair it again on the server.")
                "rate-limited" -> throw ConnectionFailure("The relay is limiting requests from this device. Try again in a minute.")
                "not-allowed" -> throw ConnectionFailure("The relay doesn't allow this request. Update the relay on the server.")
                "upstream-unreachable" -> throw ConnectionFailure("The relay can't reach the files server. Check that the tunnel is running.")
            }
            if (code == 401 || code == 403) throw PermissionFailure(permission)
            if (code == 404) throw ConnectionFailure("The file was removed or expired.")
            throw ConnectionFailure("Server request failed with HTTP $code.")
        }
        return response
    }

    private suspend fun body(c: ServerCredentials, request: Request, permission: String, route: Route? = null) = execute(c, request, permission, route).use {
        it.body?.string() ?: throw ConnectionFailure("The server returned an empty response.")
    }

    // The three checks run at once so a distant server costs one round trip, not three.
    suspend fun testConnection(c: ServerCredentials): ConnectionReport = network {
        coroutineScope {
            val view = async { try { parseFiles(body(c, builder(c, "files/list").build(), "VIEW")); true } catch (e: PermissionFailure) { false } }
            val upload = async { try { uploadLimitsInternal(c); true } catch (e: PermissionFailure) { false } }
            val version = version(c, null)
            val canView = view.await()
            val canUpload = upload.await()
            val route = if (c.relay != null && clock() < (relayUntil[c.serverUrl] ?: 0L)) Route.RELAY else Route.DIRECT
            // On the tailnet, also check the relay so pairing problems show up before leaving home.
            val relayError = if (c.relay == null || route == Route.RELAY) null else try {
                version(c, Route.RELAY)
                null
            } catch (e: CancellationException) { throw e }
            catch (e: ConnectionFailure) { e.message }
            catch (e: PermissionFailure) { "Gokapi rejected the API key on the relay." }
            catch (e: IOException) { "Couldn't reach the relay." }
            catch (e: IllegalArgumentException) { "The relay returned an unexpected response." }
            catch (e: IllegalStateException) { "The relay returned an unexpected response." }
            ConnectionReport(version, canView, canUpload, route, relayError)
        }
    }

    private suspend fun version(c: ServerCredentials, route: Route?): String {
        val root = Json.parseToJsonElement(body(c, builder(c, "info/version").build(), "VIEW", route))
        val info = if (root is JsonArray) root.firstOrNull()?.jsonObject else root.jsonObject
        return info?.string("Version")?.takeIf { it.isNotBlank() }
            ?: throw ConnectionFailure("The server did not return a Gokapi version.")
    }

    /**
     * The file's preview from the thumbnail service: at [thumbsUrl] on the tailnet, or at
     * the relay's `/api/thumbnail/...` route when the tailnet is unreachable. Null when the
     * answer is unknown (both unreachable, the service busy, an error), so nothing gets
     * cached for it.
     */
    suspend fun preview(c: ServerCredentials, f: GokapiFile, size: PreviewSize): FilePreview? = withContext(Dispatchers.IO) {
        if (f.encrypted) return@withContext FilePreview.None
        val path = "/api/thumbnail/${encodePath(f.id)}?size=${size.query}"
        val relay = c.relay
        fun viaRelay(r: RelayRoute) = Request.Builder().url(r.url + path).header("apikey", c.apiKey)
            .header("X-Relay-Id", r.id).header("X-Relay-Secret", r.secret).build()
        try {
            val base = thumbsBase(c.serverUrl)
            // The file list already found the tailnet unreachable: go straight to the relay.
            val response = if (relay != null && (base == null || clock() < (relayUntil[c.serverUrl] ?: 0L))) {
                send(http, viaRelay(relay))
            } else if (base == null) {
                return@withContext null
            } else {
                val direct = Request.Builder().url(base + path).header("apikey", c.apiKey).build()
                try { send(if (relay != null) this@GokapiClient.direct else http, direct) } catch (e: IOException) {
                    if (relay == null || !e.unreachable()) throw e
                    send(http, viaRelay(relay))
                }
            }
            response.use {
                val body = it.body
                when {
                    it.code == 204 -> FilePreview.None
                    it.code != 200 || body == null -> null
                    body.contentLength() > MAX_PREVIEW_BYTES -> null
                    body.contentType()?.type == "image" -> FilePreview.Image(body.bytes())
                    body.contentType()?.type == "text" -> FilePreview.Text(body.string())
                    else -> null
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) { null }
    }

    suspend fun list(c: ServerCredentials): List<GokapiFile> = network {
        parseFiles(body(c, builder(c, "files/list").build(), "VIEW"))
    }
    suspend fun get(c: ServerCredentials, id: String): GokapiFile = network {
        parseFile(Json.parseToJsonElement(body(c, builder(c, "files/list/${encodePath(id)}").build(), "VIEW")))
    }
    suspend fun delete(c: ServerCredentials, id: String): Unit = network {
        execute(c, builder(c, "files/delete").header("id", id).delete().build(), "DELETE").close()
    }

    suspend fun download(c: ServerCredentials, f: GokapiFile, target: File, progress: (Long, Long) -> Unit) = network {
        if (f.encrypted) throw ConnectionFailure("This file needs client-side decryption. Open its link in a browser.")
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        try {
            execute(c, builder(c, "files/download/${encodePath(f.id)}").build(), "DOWNLOAD").use { response ->
                val source = response.body ?: throw ConnectionFailure("The download was empty.")
                val total = source.contentLength().takeIf { it >= 0 } ?: f.sizeBytes
                source.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            progress(copied, total)
                        }
                        if (total >= 0 && copied != total) throw ConnectionFailure("The download was incomplete. Try again.")
                    }
                }
            }
            // Replaces an older copy: renameTo fails on Windows when the target exists
            try { java.nio.file.Files.move(partial.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            catch (e: IOException) { throw IOException("Couldn't save the downloaded file.", e) }
        } finally { partial.delete() }
    }

    private suspend fun uploadLimitsInternal(c: ServerCredentials): UploadLimits {
        val root = Json.parseToJsonElement(body(c, builder(c, "info/config").build(), "UPLOAD"))
        val info = if (root is JsonArray) root.first().jsonObject else root.jsonObject
        return UploadLimits(info.long("MaxFilesize") * 1024 * 1024, info.long("MaxChunksize") * 1024 * 1024)
    }
    suspend fun uploadLimits(c: ServerCredentials): UploadLimits = network { uploadLimitsInternal(c) }

    suspend fun upload(
        c: ServerCredentials, name: String, contentType: String, size: Long,
        input: InputStream, options: UploadOptions, limits: UploadLimits,
        progress: (Long, Long) -> Unit,
    ): GokapiFile = network {
        require(size >= 0 && options.expiryDays >= 0 && options.downloads >= 0)
        if (limits.maxFileBytes > 0 && size > limits.maxFileBytes) throw ConnectionFailure("$name exceeds the server upload limit.")
        val minimum = 5 * 1024 * 1024
        if (limits.maxChunkBytes in 1 until minimum.toLong()) throw ConnectionFailure("The server chunk limit is below Gokapi's 5 MB minimum.")
        val chunkSize = minimum
        val uuid = UUID.randomUUID().toString()
        var offset = 0L
        // Only one chunk is held in memory. Content URIs stream directly into this buffer.
        do {
            currentCoroutineContext().ensureActive()
            val bytes = input.readNBytesCompat(minOf(chunkSize.toLong(), size - offset).toInt())
            if (bytes.size.toLong() != minOf(chunkSize.toLong(), size - offset)) throw ConnectionFailure("$name changed size while uploading.")
            val coroutine = currentCoroutineContext()
            val baseOffset = offset
            val chunk = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = bytes.size.toLong()
                override fun writeTo(sink: okio.BufferedSink) {
                    var sent = 0
                    while (sent < bytes.size) {
                        coroutine.ensureActive()
                        val count = minOf(64 * 1024, bytes.size - sent)
                        sink.write(bytes, sent, count)
                        sent += count
                        progress(baseOffset + sent, size)
                    }
                }
            }
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("uuid", uuid).addFormDataPart("filesize", size.toString())
                .addFormDataPart("offset", offset.toString())
                .addFormDataPart("file", "chunk", chunk).build()
            val result = Json.parseToJsonElement(body(c, builder(c, "chunk/add").post(multipart).build(), "UPLOAD")).jsonObject
            // Gokapi 2.2.4 returns lowercase "result" here, despite its OpenAPI schema.
            if (result.string("result", result.string("Result")) != "OK") {
                throw ConnectionFailure("The server rejected an upload chunk.")
            }
            offset += bytes.size
            progress(offset, size)
        } while (offset < size)
        if (input.read() != -1) throw ConnectionFailure("$name changed size while uploading.")
        val result = Json.parseToJsonElement(body(c, builder(c, "chunk/complete")
            .header("uuid", uuid)
            .header("filename", "base64:" + Base64.getEncoder().encodeToString(name.toByteArray(Charsets.UTF_8)))
            .header("filesize", size.toString()).header("contenttype", contentType)
            .header("expiryDays", options.expiryDays.toString()).header("allowedDownloads", options.downloads.toString())
            .header("password", options.password).header("nonblocking", "false")
            .post(ByteArray(0).toRequestBody()).build(), "UPLOAD")).jsonObject
        if (result.string("Result") != "OK") throw ConnectionFailure("The server could not finish the upload.")
        parseFile(result.getValue("FileInfo"))
    }
}

private const val RELAY_HOLD_MS = 2 * 60 * 1000L
private const val MAX_PREVIEW_BYTES = 4L * 1024 * 1024

// Failures where the request never reached the server.
private fun IOException.unreachable() = this is UnknownHostException || this is ConnectException ||
    this is NoRouteToHostException || (this is SocketTimeoutException && message.orEmpty().contains("connect", ignoreCase = true))

private fun InputStream.readNBytesCompat(count: Int): ByteArray {
    val result = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(result, offset, count - offset)
        if (read < 0) break
        if (read == 0) continue
        offset += read
    }
    return if (offset == count) result else result.copyOf(offset)
}
