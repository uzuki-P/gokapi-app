package com.uzukip.gokapi.data.server

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class PreviewsTest {
    private fun file(id: String, encrypted: Boolean = false) =
        GokapiFile(id, "$id.md", 10, "10 B", "application/octet-stream", 0, 0, true, 0, true, "", encrypted)

    @Test fun detectsTypesByExtensionWhenContentTypeIsGeneric() {
        fun named(name: String, type: String = "application/octet-stream") = file("x").copy(name = name, contentType = type)
        assertEquals("Media", named("slide-5-leads.MP4").type)
        assertTrue(named("clip.mov").isVideo)
        assertEquals("video-outline", named("clip.webm").glyph)
        assertEquals("Images", named("wall.png").type)
        assertEquals("microsoft-windows", named("setup.exe").glyph)
        assertEquals("language-markdown-outline", named("notes.md").glyph)
        assertEquals("Archives", named("archive.zip").type)
        assertEquals("Windows", named("setup.EXE").type)
        assertEquals("Linux", named("gokapi-desktop.AppImage").type)
        assertEquals("linux", named("app.deb").glyph)
        assertEquals("Documents", named("cv.pdf").type)
        assertEquals("AppImg", named("gokapi-desktop.AppImage").extensionLabel)
        assertEquals(null, named("noext").extensionLabel)
        assertEquals("folder-zip-outline", named("archive.zip").glyph)
        assertEquals("file-pdf-box", named("cv.PDF").glyph)
        assertEquals("APK", named("app.APK").type)
        assertEquals("Media", named("noext", "audio/mpeg").type)
    }

    @Test fun derivesThumbsHostFromServerUrl() {
        assertEquals("https://files-thumbs.ts.example.com", thumbsUrl("https://files.ts.example.com"))
        assertEquals("https://files-thumbs.ts.example.com:8443", thumbsUrl("https://files.ts.example.com:8443/gokapi"))
        assertNull(thumbsUrl("https://localhost"))
        assertNull(thumbsUrl("https://100.64.0.1"))
        assertNull(thumbsUrl("not a url"))
    }

    @Test fun readsEachAnswerAndSendsTheApiKey() = runBlocking {
        MockWebServer().use { server ->
            val webp = byteArrayOf(1, 2, 3)
            server.enqueue(MockResponse().setHeader("Content-Type", "image/webp").setBody(Buffer().write(webp)))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/plain; charset=utf-8").setBody("Title\nBody"))
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(503))
            val base = server.url("/").toString().trimEnd('/')
            val client = GokapiClient(thumbsBase = { base })
            val c = ServerCredentials("https://files.example.com", "key-1")

            val image = client.preview(c, file("a"), PreviewSize.SMALL)
            assertArrayEquals(webp, (image as FilePreview.Image).bytes)
            assertEquals("Title\nBody", (client.preview(c, file("b"), PreviewSize.LARGE) as FilePreview.Text).text)
            assertEquals(FilePreview.None, client.preview(c, file("c"), PreviewSize.SMALL))
            assertNull(client.preview(c, file("d"), PreviewSize.SMALL))
            assertEquals(FilePreview.None, client.preview(c, file("e", encrypted = true), PreviewSize.SMALL))

            val first = server.takeRequest()
            assertEquals("/api/thumbnail/a?size=s", first.path)
            assertEquals("key-1", first.getHeader("apikey"))
            assertEquals("/api/thumbnail/b?size=l", server.takeRequest().path)
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun fallsBackToTheRelayWhenTheTailnetIsUnreachable() = runBlocking {
        MockWebServer().use { relay ->
            relay.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("via relay"))
            relay.enqueue(MockResponse().setResponseCode(401).setHeader("X-Relay-Error", "unauthorized"))
            val client = GokapiClient(thumbsBase = { "http://127.0.0.1:1" })
            val route = RelayRoute(relay.url("/").toString().trimEnd('/'), "phone-1", "s3cret")
            val c = ServerCredentials("https://files.example.com", "key-1", route)

            assertEquals("via relay", (client.preview(c, file("a b"), PreviewSize.LARGE) as FilePreview.Text).text)
            assertNull(client.preview(c, file("b"), PreviewSize.SMALL))  // relay refusal: unknown, not "none"

            val sent = relay.takeRequest()
            assertEquals("/api/thumbnail/a%20b?size=l", sent.path)
            assertEquals("key-1", sent.getHeader("apikey"))
            assertEquals("phone-1", sent.getHeader("X-Relay-Id"))
            assertEquals("s3cret", sent.getHeader("X-Relay-Secret"))
        }
    }

    @Test fun unreachableServiceIsUnknown() = runBlocking {
        val client = GokapiClient(thumbsBase = { "http://127.0.0.1:1" })
        assertNull(client.preview(ServerCredentials("https://files.example.com", "k"), file("a"), PreviewSize.SMALL))
    }

    @Test fun storeCachesAnswersAndExpiresNone() = runBlocking {
        val dir = Files.createTempDirectory("previews").toFile()
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("Hello"))
                server.enqueue(MockResponse().setResponseCode(204))
                server.enqueue(MockResponse().setResponseCode(503))
                server.enqueue(MockResponse().setResponseCode(204))
                val base = server.url("/").toString().trimEnd('/')
                var now = 1_000_000_000_000L
                val store = PreviewStore(dir, GokapiClient(thumbsBase = { base }), clock = { now })
                val c = ServerCredentials("https://files.example.com", "k")

                assertEquals("Hello", (store.load(c, file("a"), PreviewSize.SMALL) as FilePreview.Text).text)
                assertEquals("Hello", (store.load(c, file("a"), PreviewSize.SMALL) as FilePreview.Text).text)
                assertEquals(FilePreview.None, store.load(c, file("b"), PreviewSize.SMALL))
                File(dir, "b-s.none").setLastModified(now)
                assertEquals(FilePreview.None, store.load(c, file("b"), PreviewSize.SMALL))
                assertEquals(2, server.requestCount)

                // An unknown answer is not cached; a day-old "no preview" is asked again.
                assertNull(store.load(c, file("c"), PreviewSize.SMALL))
                now += 25 * 60 * 60 * 1000L
                assertEquals(FilePreview.None, store.load(c, file("b"), PreviewSize.SMALL))
                assertEquals(4, server.requestCount)

                store.retain(setOf("b"))
                assertNull(store.cached(file("a"), PreviewSize.SMALL))
                assertTrue(File(dir, "b-s.none").exists())
            }
        } finally { dir.deleteRecursively() }
    }
}
