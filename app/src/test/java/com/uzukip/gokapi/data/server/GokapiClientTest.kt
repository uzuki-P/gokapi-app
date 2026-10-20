package com.uzukip.gokapi.data.server

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class GokapiClientTest {
    @Test fun validatesHttpsAndNormalizesUrl() {
        assertEquals("https://files.example.com", normalizeServerUrl(" https://files.example.com/ "))
        assertEquals("https://files.example.com/gokapi", normalizeServerUrl("https://files.example.com/gokapi/"))
        for (url in listOf("http://files.example.com", "bad", "https://user:pass@files.example.com", "https://files.example.com/?key=secret", "https://files.example.com/#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { normalizeServerUrl(url) }
        }
    }

    @Test fun checksVersionViewAndUploadWithApiKeyAndAcceptsEmptyList() = runBlocking {
        MockWebServer().use { server ->
            server.route(
                "/api/info/version" to MockResponse().setBody("{\"Version\":\"2.2.4\",\"VersionInt\":20204}"),
                "/api/files/list" to MockResponse().setBody("null"),
                "/api/info/config" to MockResponse().setBody("{\"MaxFilesize\":100,\"MaxChunksize\":10}"),
            )
            val result = GokapiClient().testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key"))
            assertEquals("2.2.4", result.version)
            assertTrue(result.canViewFiles)
            assertTrue(result.canUpload)
            val requests = List(3) { server.takeRequest() }
            assertEquals(setOf("/api/info/version", "/api/files/list", "/api/info/config"), requests.map { it.path }.toSet())
            assertTrue(requests.all { it.getHeader("apikey") == "test-key" })
        }
    }

    @Test fun sendsConnectionChecksConcurrently() = runBlocking {
        MockWebServer().use { server ->
            val arrived = CountDownLatch(3)
            val bodies = mapOf(
                "/api/info/version" to "{\"Version\":\"2.2.4\"}",
                "/api/files/list" to "[]",
                "/api/info/config" to "{\"MaxFilesize\":100,\"MaxChunksize\":10}",
            )
            // Each response waits until all three requests are in flight.
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    arrived.countDown()
                    if (!arrived.await(5, TimeUnit.SECONDS)) return MockResponse().setResponseCode(500)
                    return MockResponse().setBody(bodies.getValue(request.path!!))
                }
            }
            val report = GokapiClient().testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key"))
            assertTrue(report.canViewFiles && report.canUpload)
        }
    }

    @Test fun reportsViewDeniedWithoutClaimingSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.route("/api/info/version" to MockResponse().setBody("[{\"Version\":\"2.2.4\"}]"))
            val report = GokapiClient().testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key"))
            assertFalse(report.canViewFiles)
            assertFalse(report.canUpload)
        }
    }

    @Test fun rejectsUnexpectedResponse() {
        MockWebServer().use { server ->
            server.route("/api/info/version" to MockResponse().setBody("<html>Login</html>"))
            assertThrows(ConnectionFailure::class.java) {
                runBlocking { GokapiClient().testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key")) }
            }
        }
    }

    @Test fun rejectsNonListResponseAndHttpErrors() {
        for (response in listOf(MockResponse().setBody("{}"), MockResponse().setResponseCode(500))) {
            MockWebServer().use { server ->
                server.route(
                    "/api/info/version" to MockResponse().setBody("{\"Version\":\"2.2.4\"}"),
                    "/api/files/list" to response,
                    "/api/info/config" to MockResponse().setBody("{\"MaxFilesize\":100,\"MaxChunksize\":10}"),
                )
                assertThrows(ConnectionFailure::class.java) {
                    runBlocking { GokapiClient().testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key")) }
                }
            }
        }
    }

    @Test fun doesNotForwardKeyThroughRedirects() {
        MockWebServer().use { destination ->
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest) =
                        MockResponse().setResponseCode(302).setHeader("Location", destination.url("/"))
                }
                val client = GokapiClient()
                assertThrows(ConnectionFailure::class.java) {
                    runBlocking { client.testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key")) }
                }
                assertEquals(0, destination.requestCount)
            }
        }
    }

    // Unrouted paths get 401, as a key without that permission would.
    private fun MockWebServer.route(vararg routes: Pair<String, MockResponse>) {
        val responses = routes.toMap()
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = responses[request.path] ?: MockResponse().setResponseCode(401)
        }
    }
}
