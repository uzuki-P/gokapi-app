package com.uzukip.gokapi.desktop

import com.uzukip.gokapi.data.server.ServerCredentials
import com.uzukip.gokapi.desktop.data.NotificationMode
import com.uzukip.gokapi.desktop.data.SeenFiles
import com.uzukip.gokapi.desktop.notify.NewFileWatcher
import com.uzukip.gokapi.desktop.notify.NtfySubscriber
import com.uzukip.gokapi.desktop.platform.AppDirs
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsTest {
    private val root = Files.createTempDirectory("gokapi-notify-test").toFile().apply { deleteOnExit() }

    @Test
    fun firstCheckIsABaselineThenOnlyNewFilesCount() = runBlocking {
        val seen = SeenFiles(File(root, "seen.properties"))
        assertEquals(emptySet<String>(), seen.update("https://a", setOf("1", "2")))
        assertEquals(setOf("3"), seen.update("https://a", setOf("1", "2", "3")))
        assertEquals(emptySet<String>(), seen.update("https://a", setOf("2", "3")))
        // Own uploads are marked seen, so the next check stays quiet.
        assertTrue(seen.add("https://a", "4"))
        assertEquals(emptySet<String>(), seen.update("https://a", setOf("3", "4")))
        // Another server starts with a fresh baseline.
        assertEquals(emptySet<String>(), seen.update("https://b", setOf("9")))
    }

    @Test
    fun ownUploadBeforeAnyCheckDoesNotHideTheBaseline() = runBlocking {
        val seen = SeenFiles(File(root, "seen-empty.properties"))
        assertFalse(seen.add("https://a", "new"))
        assertEquals(emptySet<String>(), seen.update("https://a", setOf("old", "new")))
    }

    @Test
    fun ntfyHelpersBuildTheStreamUrlAuthAndBody() {
        assertEquals("https://ntfy.example.com/gokapi-uploads/json", NtfySubscriber.streamUrl("https://ntfy.example.com/gokapi-uploads/"))
        assertEquals("https://ntfy.example.com/upAbC/json", NtfySubscriber.streamUrl("https://ntfy.example.com/upAbC?up=1"))
        assertTrue(runCatching { NtfySubscriber.streamUrl("http://ntfy.example.com/t") }.isFailure)
        assertTrue(runCatching { NtfySubscriber.streamUrl("https://ntfy.example.com/") }.isFailure)
        assertEquals("Bearer tk_abc", NtfySubscriber.authorization("tk_abc"))
        assertEquals("Basic " + Base64.getEncoder().encodeToString("android:pw".toByteArray()), NtfySubscriber.authorization("android:pw"))
        assertNull(NtfySubscriber.messageBody("""{"event":"keepalive"}"""))
        assertEquals("""{"Id":"x"}""", NtfySubscriber.messageBody("""{"event":"message","message":"{\"Id\":\"x\"}"}"""))
        val encoded = Base64.getEncoder().encodeToString("""{"Id":"y"}""".toByteArray())
        assertEquals("""{"Id":"y"}""", NtfySubscriber.messageBody("""{"event":"message","message":"$encoded","encoding":"base64"}"""))
    }

    @Test
    fun streamSendsAuthAndDeliversMessages() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"event":"open"}""" + "\n" + """{"event":"message","message":"hello"}""" + "\n"))
        server.start()
        val messages = mutableListOf<String>()
        var opened = false
        NtfySubscriber().stream(server.url("/gokapi-uploads").toString(), "tk_secret", onOpen = { opened = true }) { messages += it }
        val request = server.takeRequest()
        assertEquals("/gokapi-uploads/json", request.path)
        assertEquals("Bearer tk_secret", request.getHeader("Authorization"))
        assertTrue(opened)
        assertEquals(listOf("hello"), messages)
        server.shutdown()
    }

    @Test
    fun cancellingAStalledStreamReturnsPromptly() = runBlocking {
        // A server that accepts the request and never answers, like a quiet ntfy stream.
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        server.start()
        val job = launch(Dispatchers.Default) {
            NtfySubscriber().stream(server.url("/gokapi-uploads").toString(), "tk", onOpen = {}) {}
        }
        server.takeRequest()
        val started = System.nanoTime()
        job.cancel()
        withTimeout(3_000) { job.join() }
        assertTrue("cancel took too long", System.nanoTime() - started < 2_000_000_000L)
        server.shutdown()
    }

    @Test
    fun watcherAnnouncesOnlyFilesAddedAfterTheBaseline() = runBlocking {
        val server = MockWebServer()
        fun list(vararg ids: String) = MockResponse().setBody(ids.joinToString(",", "[", "]") { """{"Id":"$it","Name":"$it.txt","Size":"1 B","SizeBytes":1}""" })
        server.enqueue(list("a", "b"))
        server.enqueue(list("a", "b", "c"))
        server.start()
        val dirs = AppDirs(env = { if (it == "XDG_CONFIG_HOME") "$root/config" else null }, home = root, windows = false)
        val container = DesktopContainer(dirs)
        container.credentials.save(ServerCredentials(server.url("/").toString().trimEnd('/'), "key"))
        container.settings.update { it.copy(notificationMode = NotificationMode.OFF) }
        val notified = mutableListOf<String>()
        var refreshed = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val watcher = NewFileWatcher(container, scope, notify = { title, body -> notified += "$title|$body" }, onNewFiles = { refreshed++ })
        watcher.check()
        assertEquals(emptyList<String>(), notified)
        watcher.check()
        assertEquals(listOf("New file in Gokapi|c.txt · 1 B"), notified)
        assertEquals(1, refreshed)
        scope.cancel()
        server.shutdown()
    }

}
