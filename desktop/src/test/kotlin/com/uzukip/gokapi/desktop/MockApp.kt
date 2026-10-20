package com.uzukip.gokapi.desktop

import com.uzukip.gokapi.data.server.GokapiClient
import com.uzukip.gokapi.data.server.ServerCredentials
import com.uzukip.gokapi.desktop.platform.AppDirs
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/**
 * The real app against a fake Gokapi server with sample files, in a throwaway config and cache
 * directory, for checking layouts without the private server. Run with `just desktop-mock`.
 */
fun main() {
    val root = Files.createTempDirectory("gokapi-desktop-mock").toFile().apply { deleteOnExit() }
    val server = MockWebServer().apply { dispatcher = MockGokapi(); start() }
    val dirs = AppDirs(env = { name -> mapOf("XDG_CONFIG_HOME" to "$root/config", "XDG_CACHE_HOME" to "$root/cache", "LOCALAPPDATA" to "$root/local")[name] }, home = File(root, "home"))
    val container = DesktopContainer(dirs, GokapiClient())
    // The client accepts plain HTTP; only the Settings form insists on HTTPS.
    runBlocking {
        container.credentials.save(ServerCredentials(server.url("/").toString().trimEnd('/'), "mock-key"))
        // Instant notifications from the fake ntfy topic below; a phone upload arrives 15 s in.
        container.ntfy.save(server.url("/gokapi-uploads").toString(), "tk_mock")
        container.settings.update { it.copy(notificationMode = com.uzukip.gokapi.desktop.data.NotificationMode.INSTANT) }
    }
    runApp(container, title = "Gokapi (mock)")
}

private class MockGokapi : Dispatcher() {
    private val now = System.currentTimeMillis() / 1000
    private val phoneUploaded = java.util.concurrent.atomic.AtomicBoolean(false)
    private val files = mutableListOf(
        file("a1", "gokapi-android-0.1.4-20-oct_09-12.apk", "application/vnd.android.package-archive", 9_437_184, 600, unlimited = false),
        file("b2", "Quarterly report final.pdf", "application/pdf", 1_253_000, 7_200, unlimited = false),
        file("c3", "screenshot-settings-dark.png", "image/png", 412_000, 86_400 * 2, unlimited = true),
        file("d4", "meeting-notes.md", "text/markdown", 3_400, 86_400 * 3, unlimited = false),
        file("e5", "demo-recording.mp4", "video/mp4", 184_000_000, 86_400 * 5, unlimited = true),
    )

    private fun file(id: String, name: String, type: String, size: Long, age: Long, unlimited: Boolean) = """
        {"Id":"$id","Name":"$name","Size":"${com.uzukip.gokapi.data.server.formatBytes(size)}","SizeBytes":$size,"ContentType":"$type",
         "UploadDate":${now - age},"ExpireAt":${now - age + 86_400 * 7},"UnlimitedTime":$unlimited,"DownloadsRemaining":3,
         "UnlimitedDownloads":$unlimited,"HotlinkId":""}
    """.trimIndent()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty()
        return when {
            path == "/gokapi-uploads/json" -> {
                // Only the first subscription gets the upload; later ones stay quiet.
                if (!phoneUploaded.compareAndSet(false, true)) { Thread.sleep(10 * 60_000L); return MockResponse() }
                Thread.sleep(15_000)
                val entry = file("p${System.nanoTime() % 100000}", "IMG_2041 from phone.jpg", "image/jpeg", 2_400_000, 0, unlimited = false)
                synchronized(files) { files.add(0, entry) }
                MockResponse().setBody("""{"event":"open"}""" + "\n" + """{"event":"message","message":"{}"}""" + "\n")
            }
            path == "/api/info/version" -> json("""{"Version":"2.2.4"}""")
            path == "/api/info/config" -> json("""{"MaxFilesize":2048,"MaxChunksize":45}""")
            path == "/api/files/list" -> json(synchronized(files) { files.joinToString(",", "[", "]") })
            path.startsWith("/api/files/download/") -> MockResponse().setBody(Buffer().write(ByteArray(256 * 1024))).throttleBody(64 * 1024, 200, java.util.concurrent.TimeUnit.MILLISECONDS)
            path == "/api/files/delete" -> { synchronized(files) { files.removeAll { it.contains("\"Id\":\"${request.getHeader("id")}\"") } }; MockResponse() }
            path == "/api/chunk/add" -> { Thread.sleep(400); json("""{"result":"OK"}""") }
            path == "/api/chunk/complete" -> {
                val name = String(java.util.Base64.getDecoder().decode(request.getHeader("filename").orEmpty().removePrefix("base64:")))
                val entry = file("u${System.nanoTime() % 100000}", name, request.getHeader("contenttype").orEmpty(), request.getHeader("filesize")?.toLongOrNull() ?: 0, 0, unlimited = false)
                synchronized(files) { files.add(0, entry) }
                json("""{"Result":"OK","FileInfo":$entry}""")
            }
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
