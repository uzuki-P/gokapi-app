package com.uzukip.gokapi.desktop.notify

import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class NtfyAccessDenied : IOException("ntfy denied access. Check the account or token and its read access to the topic.")

/**
 * One ntfy topic's JSON stream (`<topic>/json`). The upload hook publishes a small JSON body
 * after each upload, to the regular topic (`NTFY_URL`) or to each endpoint in
 * `ntfy-endpoints`; either kind of topic URL works here.
 */
class NtfySubscriber(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // ntfy sends a keepalive event every 45 s, so a silent minute and a half means a dead link.
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    /**
     * Reads the stream until it ends or fails, calling [onOpen] once connected and [onMessage]
     * with each message body. Returns when the server closes the stream; throws on failure.
     */
    suspend fun stream(topicUrl: String, secret: String, onOpen: () -> Unit, onMessage: suspend (String) -> Unit) {
        val request = Request.Builder().url(streamUrl(topicUrl)).header("Authorization", authorization(secret)).build()
        val call = http.newCall(request)
        // The stream blocks in a socket read, which coroutine cancellation can't interrupt.
        // A sibling that waits for cancellation closes the call, so the read fails at once.
        // (Job.invokeOnCompletion only fires after the job finishes, which the blocked read
        // prevents, so it kept Quit from ending the process.)
        coroutineScope {
            val closer = launch {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                withContext(Dispatchers.IO) {
                    call.execute().use { response ->
                        if (response.code == 401 || response.code == 403) throw NtfyAccessDenied()
                        if (!response.isSuccessful) throw IOException("ntfy returned HTTP ${response.code}.")
                        onOpen()
                        val source = response.body?.source() ?: return@use
                        while (true) {
                            ensureActive()
                            val line = source.readUtf8Line() ?: break
                            messageBody(line)?.let { onMessage(it) }
                        }
                    }
                }
            } catch (e: IOException) {
                // A read failed by the closer above is a cancellation, not a network error.
                currentCoroutineContext().ensureActive()
                throw e
            } finally {
                closer.cancel()
            }
        }
    }

    companion object {
        /** `https://host/topic` to its JSON stream. Loopback HTTP is allowed for tests. */
        fun streamUrl(topicUrl: String): String {
            val url = topicUrl.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Enter the ntfy topic URL.")
            require(url.isHttps || url.host == "127.0.0.1" || url.host == "localhost") { "Use HTTPS for ntfy." }
            require(url.pathSegments.any(String::isNotBlank)) { "Add the topic name to the URL, such as /gokapi-uploads." }
            return url.newBuilder().query(null).addPathSegment("json").build().toString()
        }

        /** A `user:password` pair as Basic auth; anything else as an access token. */
        fun authorization(secret: String): String =
            if (':' in secret) "Basic " + Base64.getEncoder().encodeToString(secret.toByteArray(Charsets.UTF_8))
            else "Bearer $secret"

        /** The body of a `message` event, decoded when ntfy sent it as base64; null for keepalives. */
        fun messageBody(line: String): String? {
            val event = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return null
            if (event["event"]?.jsonPrimitive?.contentOrNull != "message") return null
            val message = event["message"]?.jsonPrimitive?.contentOrNull ?: return ""
            return if (event["encoding"]?.jsonPrimitive?.contentOrNull == "base64") {
                runCatching { String(Base64.getDecoder().decode(message), Charsets.UTF_8) }.getOrDefault("")
            } else message
        }
    }
}
