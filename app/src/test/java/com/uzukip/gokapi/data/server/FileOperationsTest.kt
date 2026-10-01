package com.uzukip.gokapi.data.server

import com.uzukip.gokapi.data.files.safeFilename
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class FileOperationsTest {
    private val fileJson = """{"Id":"scrubbed-id","Name":"sample.apk","SizeBytes":3,"ContentType":"application/vnd.android.package-archive","UploadDate":1708175121,"ExpireAt":1708261521,"UnlimitedTime":false,"DownloadsRemaining":4,"UnlimitedDownloads":false,"HotlinkId":"","UnknownFutureField":true}"""
    @Test fun parsesMetadataAndSortsNewestFirst() {
        val files = parseFiles("[$fileJson,${fileJson.replace("scrubbed-id", "newer-id").replace("1708175121", "1708176121")}]")
        assertEquals("newer-id", files.first().id)
        assertTrue(files.first().isApk)
        assertEquals("Expires in 1 d", files.last().expiry(1708175121))
        assertEquals("4", files.last().downloadsRemaining.toString())
        assertEquals(emptyList<GokapiFile>(), parseFiles("null"))
        assertEquals(emptyList<GokapiFile>(), parseFiles("[]"))
    }
    @Test fun rejectsPathTraversalAndSanitizesServerFilenames() {
        assertThrows(IllegalArgumentException::class.java) { parseFiles("[${fileJson.replace("scrubbed-id", "../../private") }]") }
        assertEquals("example.apk", safeFilename("../../example.apk"))
        assertEquals("example.apk", safeFilename("C:\\folder\\example.apk"))
        assertEquals("file", safeFilename(".."))
        assertEquals("file", safeFilename("/"))
        assertEquals("a_b", safeFilename("a\nb"))
    }
    @Test fun usesConfiguredHostForLinksAndComparesSemverNumerically() {
        val file = parseFiles("[$fileJson]").single()
        assertEquals("https://files.example.com/d?id=scrubbed-id", file.link("https://files.example.com"))
        assertTrue(newerAppVersion("gokapi-android-1.10.0-1.oct-11-22.apk", "1.9.9", false))
        assertFalse(newerAppVersion("DEV-gokapi-android-1.10.0-dev_01-oct_11-22.apk", "1.9.9", false))
        assertTrue(newerAppVersion("DEV-gokapi-android-1.10.0-dev_01-oct_11-22.apk", "1.9.9-dev", true))
        assertFalse(newerAppVersion("gokapi-android-1.9.9-1.oct-11-22.apk", "1.9.9", false))
    }
    @Test fun downloadsAtomicallyAndSendsDownloadAuth() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("apk"))
            val folder = java.nio.file.Files.createTempDirectory("gokapi-download").toFile()
            try {
                val target = File(folder, "sample.apk")
                val updates = mutableListOf<Long>()
                GokapiClient().download(ServerCredentials(server.url("/").toString().trimEnd('/'), "android-key"), parseFiles("[$fileJson]").single(), target) { copied, _ -> updates.add(copied) }
                assertEquals("apk", target.readText())
                assertFalse(File(folder, "sample.apk.part").exists())
                assertEquals(3L, updates.last())
                val request = server.takeRequest()
                assertEquals("/api/files/download/scrubbed-id", request.path)
                assertEquals("android-key", request.getHeader("apikey"))
            } finally { folder.deleteRecursively() }
        }
    }
    @Test fun rejectedDownloadLeavesNoCacheFile() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            val folder = java.nio.file.Files.createTempDirectory("gokapi-download-denied").toFile()
            try {
                val target = File(folder, "sample.apk")
                assertThrows(PermissionFailure::class.java) { runBlocking { GokapiClient().download(ServerCredentials(server.url("/").toString().trimEnd('/'), "android-key"), parseFiles("[$fileJson]").single(), target) { _, _ -> } } }
                assertFalse(target.exists())
                assertFalse(File(folder, "sample.apk.part").exists())
            } finally { folder.deleteRecursively() }
        }
    }
    @Test fun streamsChunksWithActualServerResponsesAndFinalizesEncodedFilename() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"result\":\"OK\"}"))
            server.enqueue(MockResponse().setBody("{\"result\":\"OK\"}"))
            server.enqueue(MockResponse().setBody("{\"Result\":\"OK\",\"FileInfo\":$fileJson}"))
            val size = 5 * 1024 * 1024 + 7
            val input = ByteArrayInputStream(ByteArray(size) { (it % 127).toByte() })
            val result = GokapiClient().upload(ServerCredentials(server.url("/").toString().trimEnd('/'), "android-key"), "sample-🍪.apk", "application/vnd.android.package-archive", size.toLong(), input, UploadOptions(0, 0, "secret"), UploadLimits(size.toLong(), 10 * 1024 * 1024)) { _, _ -> }
            assertEquals("scrubbed-id", result.id)
            val first = server.takeRequest()
            val second = server.takeRequest()
            val completed = server.takeRequest()
            assertEquals("/api/chunk/add", first.path)
            assertEquals("/api/chunk/add", second.path)
            assertEquals("0", first.body.readUtf8().substringAfter("name=\"offset\"").substringAfter("\r\n\r\n").substringBefore("\r\n"))
            assertEquals("5242880", second.body.readUtf8().substringAfter("name=\"offset\"").substringAfter("\r\n\r\n").substringBefore("\r\n"))
            assertEquals("/api/chunk/complete", completed.path)
            assertEquals("base64:" + java.util.Base64.getEncoder().encodeToString("sample-🍪.apk".toByteArray()), completed.getHeader("filename"))
            assertEquals("0", completed.getHeader("expiryDays"))
            assertEquals("0", completed.getHeader("allowedDownloads"))
            assertEquals("secret", completed.getHeader("password"))
        }
    }
    @Test fun acceptsDocumentedUppercaseChunkResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"Result\":\"OK\"}"))
            server.enqueue(MockResponse().setBody("{\"Result\":\"OK\",\"FileInfo\":$fileJson}"))
            val file = GokapiClient().upload(
                ServerCredentials(server.url("/").toString().trimEnd('/'), "android-key"),
                "sample.apk", "application/octet-stream", 3,
                ByteArrayInputStream("apk".toByteArray()), UploadOptions(),
                UploadLimits(10, 10 * 1024 * 1024),
            ) { _, _ -> }
            assertEquals("scrubbed-id", file.id)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun rejectedChunkNeverFinalizesTheUpload() {
        for (response in listOf("{\"result\":\"ERROR\"}", "{}")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(response))
                val failure = assertThrows(ConnectionFailure::class.java) {
                    runBlocking {
                        GokapiClient().upload(
                            ServerCredentials(server.url("/").toString().trimEnd('/'), "android-key"),
                            "sample.apk", "application/octet-stream", 3,
                            ByteArrayInputStream("apk".toByteArray()), UploadOptions(),
                            UploadLimits(10, 10 * 1024 * 1024),
                        ) { _, _ -> }
                    }
                }
                assertEquals("The server rejected an upload chunk.", failure.message)
                assertEquals(1, server.requestCount)
                assertEquals("/api/chunk/add", server.takeRequest().path)
            }
        }
    }

    @Test fun cancellationStopsPendingNetworkRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeadersDelay(5, java.util.concurrent.TimeUnit.SECONDS).setBody("[]"))
            assertThrows(kotlinx.coroutines.TimeoutCancellationException::class.java) {
                runBlocking {
                    kotlinx.coroutines.withTimeout(200) {
                        GokapiClient().list(ServerCredentials(server.url("/").toString().trimEnd('/'), "key"))
                    }
                }
            }
        }
        Unit
    }

    @Test fun rejectsOversizeWithoutUploadingAndDeletesWithIdHeader() = runBlocking {
        MockWebServer().use { server ->
            assertThrows(ConnectionFailure::class.java) { runBlocking { GokapiClient().upload(ServerCredentials(server.url("/").toString().trimEnd('/'), "key"), "sample.apk", "application/octet-stream", 10, ByteArrayInputStream(ByteArray(10)), UploadOptions(), UploadLimits(5, 10 * 1024 * 1024)) { _, _ -> } } }
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody(""))
            GokapiClient().delete(ServerCredentials(server.url("/").toString().trimEnd('/'), "key"), "scrubbed-id")
            val deleted = server.takeRequest()
            assertEquals("DELETE", deleted.method)
            assertEquals("scrubbed-id", deleted.getHeader("id"))
        }
    }
}
