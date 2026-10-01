package com.uzukip.gokapi.data.server

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

    @Test fun checksVersionThenViewWithApiKeyAndAcceptsEmptyList() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"Version\":\"2.2.4\",\"VersionInt\":20204}"))
            server.enqueue(MockResponse().setBody("null"))
            server.enqueue(MockResponse().setBody("{\"MaxFilesize\":100,\"MaxChunksize\":10}"))
            val result = GokapiClient().testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key"))
            assertEquals("2.2.4", result.version)
            assertTrue(result.canViewFiles)
            assertEquals("/api/info/version", server.takeRequest().path)
            val request = server.takeRequest()
            assertEquals("/api/files/list", request.path)
            assertEquals("test-key", request.getHeader("apikey"))
        }
    }

    @Test fun reportsViewDeniedWithoutClaimingSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[{\"Version\":\"2.2.4\"}]"))
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(401))
            val report = GokapiClient().testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key"))
            assertFalse(report.canViewFiles)
        }
    }

    @Test fun rejectsUnexpectedResponse() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>Login</html>"))
            assertThrows(ConnectionFailure::class.java) {
                runBlocking { GokapiClient().testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key")) }
            }
        }
    }

    @Test fun rejectsNonListResponseAndHttpErrors() {
        for (response in listOf(MockResponse().setBody("{}"), MockResponse().setResponseCode(500))) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("{\"Version\":\"2.2.4\"}"))
                server.enqueue(response)
                assertThrows(ConnectionFailure::class.java) {
                    runBlocking { GokapiClient().testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key")) }
                }
            }
        }
    }

    @Test fun doesNotForwardKeyThroughRedirects() {
        MockWebServer().use { destination ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/")))
                val client = GokapiClient()
                assertThrows(ConnectionFailure::class.java) {
                    runBlocking { client.testConnection(ServerCredentials(server.url("/").toString().trimEnd('/'), "test-key")) }
                }
                assertEquals(0, destination.requestCount)
            }
        }
    }
}
