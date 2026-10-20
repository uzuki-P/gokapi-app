package com.uzukip.gokapi.desktop.notify

import com.uzukip.gokapi.data.server.GokapiFile
import com.uzukip.gokapi.desktop.DesktopContainer
import com.uzukip.gokapi.desktop.data.NotificationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Announces files that someone else uploaded. Polling checks the file list on a timer; Instant
 * also listens to ntfy and checks as soon as a message arrives, with slower polling as a
 * fallback. Both compare against [com.uzukip.gokapi.desktop.data.SeenFiles], so a file is
 * announced once however it was found, and the first check only records a baseline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewFileWatcher(
    private val container: DesktopContainer,
    private val scope: CoroutineScope,
    private val notify: (title: String, body: String) -> Unit,
    private val onNewFiles: () -> Unit,
) {
    private val checks = Mutex()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            container.ntfy.load()
            combine(container.settings.settings.map { it.notificationMode }.distinctUntilChanged(), container.ntfy.config) { mode, ntfy -> mode to ntfy }
                .collectLatest { (mode, ntfy) ->
                    container.ntfy.status.value = null
                    when (mode) {
                        NotificationMode.OFF -> Unit
                        NotificationMode.POLL -> poll(POLL_INTERVAL_MS)
                        NotificationMode.INSTANT -> coroutineScope {
                            launch { poll(FALLBACK_INTERVAL_MS) }
                            if (ntfy != null && ntfy.serverUrl.isNotBlank()) launch { listen(ntfy.serverUrl, ntfy.apiKey) }
                            else container.ntfy.status.value = NtfyStatus("Add the ntfy topic below to get notifications instantly.", error = true)
                        }
                    }
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun poll(intervalMs: Long) {
        while (true) {
            check()
            delay(intervalMs)
        }
    }

    // Reconnects with backoff from 5 s to 5 min; a working stream resets the backoff.
    private suspend fun listen(topicUrl: String, secret: String) {
        val subscriber = NtfySubscriber()
        var backoff = 5_000L
        while (true) {
            container.ntfy.status.value = NtfyStatus("Connecting to ntfy")
            try {
                subscriber.stream(
                    topicUrl, secret,
                    onOpen = {
                        backoff = 5_000L
                        container.ntfy.status.value = NtfyStatus("Listening for new files")
                    },
                    // The message names the file, but the list also covers anything missed while offline.
                    onMessage = { check() },
                )
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) {
                container.ntfy.status.value = NtfyStatus(e.message ?: "Check the ntfy topic URL.", error = true)
                return
            } catch (e: NtfyAccessDenied) {
                container.ntfy.status.value = NtfyStatus(e.message.orEmpty(), error = true)
                backoff = 5 * 60_000L
            } catch (e: Exception) {
                container.ntfy.status.value = NtfyStatus("Can't reach ntfy. Retrying in ${backoff / 1000} s.", error = true)
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(5 * 60_000L)
        }
    }

    /** Fetches the list and announces files not seen before. Network failures wait for the next check. */
    suspend fun check() = checks.withLock {
        try {
            val c = container.credentials.load()
            if (c.serverUrl.isBlank() || c.apiKey.isBlank()) return@withLock
            val files = container.client.list(c)
            val fresh = container.seenFiles.update(c.serverUrl, files.mapTo(HashSet(), GokapiFile::id))
            if (fresh.isEmpty()) return@withLock
            announce(files.filter { it.id in fresh })
            onNewFiles()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { /* Offline or the server is down; the next check tries again. */ }
    }

    private fun announce(files: List<GokapiFile>) {
        if (files.size == 1) {
            val file = files.single()
            notify("New file in Gokapi", "${file.name} · ${file.size}")
        } else {
            val names = files.take(3).joinToString("\n") { it.name }
            notify("${files.size} new files in Gokapi", if (files.size > 3) "$names\n…" else names)
        }
    }

    private companion object {
        // gokapi.pollSeconds shortens both intervals for manual testing (the mock app).
        val override = System.getProperty("gokapi.pollSeconds")?.toLongOrNull()?.times(1000)
        val POLL_INTERVAL_MS = override ?: (5 * 60_000L)
        val FALLBACK_INTERVAL_MS = override ?: (15 * 60_000L)
    }
}

class NtfyStatus(val text: String, val error: Boolean = false)
