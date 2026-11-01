package com.uzukip.gokapi.data.server

import java.net.ServerSocket
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class RelayTest {
    private fun link(json: String, host: String = "files.example.com") =
        "https://$host/pair#" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private val payload = """{"v":1,"name":"pixel","url":"https://files.example.com","key":"app-key","relay":"https://files-api.example.org","relayId":"pixel-1a2b3c4d","relaySecret":"secret"}"""

    @Test fun parsesPairingLink() {
        val pairing = parsePairingLink(" ${link(payload)} ")
        assertEquals("pixel", pairing.device)
        assertEquals("https://files.example.com", pairing.credentials.serverUrl)
        assertEquals("app-key", pairing.credentials.apiKey)
        val relay = pairing.credentials.relay!!
        assertEquals("https://files-api.example.org", relay.url)
        assertEquals("pixel-1a2b3c4d", relay.id)
        assertEquals("secret", relay.secret)
        assertFalse(pairing.toString().contains("secret") || relay.toString().contains("secret"))
    }

    @Test fun rejectsBadPairingLinks() {
        val bad = listOf(
            "not a link",
            "https://files.example.com/d?id=1",
            "https://files.example.com/pair",
            "https://files.example.com/pair#!!!",
            link(payload, host = "evil.example.net"),
            link(payload).replace("https://", "http://"),
            link(payload.replace("\"v\":1", "\"v\":2")),
            link(payload.replace("https://files-api.example.org", "http://files-api.example.org")),
            link(payload.replace("\"secret\"", "\"\"")),
            link(payload.replace("app-key", "app key")),
        )
        for (text in bad) assertThrows(text, IllegalArgumentException::class.java) { parsePairingLink(text) }
    }

    @Test fun fallsBackToRelayWithDeviceTokenAndHoldsIt() = runBlocking {
        MockWebServer().use { relay ->
            relay.dispatcher = routes("/api/files/list" to MockResponse().setBody("[]"))
            var now = 0L
            val client = GokapiClient(clock = { now })
            val c = ServerCredentials(closedPortUrl(), "app-key", RelayRoute(relay.url("/").toString().trimEnd('/'), "pixel-1a2b3c4d", "secret"))
            assertEquals(emptyList<GokapiFile>(), client.list(c))
            val first = relay.takeRequest()
            assertEquals("/api/files/list", first.path)
            assertEquals("app-key", first.getHeader("apikey"))
            assertEquals("pixel-1a2b3c4d", first.getHeader("X-Relay-Id"))
            assertEquals("secret", first.getHeader("X-Relay-Secret"))
            now += 60_000
            client.list(c)
            assertEquals(2, relay.requestCount)
        }
    }

    @Test fun keepsTailnetWhenItAnswersAndDoesNotRetryServerErrors() {
        MockWebServer().use { relay ->
            MockWebServer().use { direct ->
                relay.dispatcher = routes("/api/files/list" to MockResponse().setBody("[]"))
                direct.dispatcher = routes("/api/files/list" to MockResponse().setResponseCode(500))
                val c = ServerCredentials(direct.url("/").toString().trimEnd('/'), "app-key", RelayRoute(relay.url("/").toString().trimEnd('/'), "id", "secret"))
                assertThrows(ConnectionFailure::class.java) { runBlocking { GokapiClient().list(c) } }
                assertNull(direct.takeRequest().getHeader("X-Relay-Secret"))
                assertEquals(0, relay.requestCount)
            }
        }
    }

    @Test fun reportsRelayRefusals() {
        MockWebServer().use { relay ->
            relay.dispatcher = routes("/api/files/list" to MockResponse().setResponseCode(401).setHeader("X-Relay-Error", "unauthorized"))
            val c = ServerCredentials(closedPortUrl(), "app-key", RelayRoute(relay.url("/").toString().trimEnd('/'), "id", "wrong"))
            val error = assertThrows(ConnectionFailure::class.java) { runBlocking { GokapiClient().list(c) } }
            assertTrue(error.message!!.contains("relay rejected"))
            relay.dispatcher = routes("/api/files/list" to MockResponse().setResponseCode(429).setHeader("X-Relay-Error", "rate-limited"))
            assertTrue(assertThrows(ConnectionFailure::class.java) { runBlocking { GokapiClient().list(c) } }.message!!.contains("limiting"))
            relay.dispatcher = routes("/api/files/list" to MockResponse().setResponseCode(502).setHeader("X-Relay-Error", "upstream-unreachable"))
            assertTrue(assertThrows(ConnectionFailure::class.java) { runBlocking { GokapiClient().list(c) } }.message!!.contains("tunnel"))
            relay.dispatcher = routes("/api/files/list" to MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json").setBody("{}"))
            assertThrows(PermissionFailure::class.java) { runBlocking { GokapiClient().list(c) } }
        }
    }

    @Test fun connectionTestChecksTheRelayFromTheTailnet() = runBlocking {
        MockWebServer().use { relay ->
            MockWebServer().use { direct ->
                direct.dispatcher = routes(
                    "/api/info/version" to MockResponse().setBody("{\"Version\":\"2.2.4\"}"),
                    "/api/files/list" to MockResponse().setBody("[]"),
                    "/api/info/config" to MockResponse().setBody("{\"MaxFilesize\":100,\"MaxChunksize\":10}"),
                )
                relay.dispatcher = routes("/api/info/version" to MockResponse().setResponseCode(401).setHeader("X-Relay-Error", "unauthorized"))
                val c = ServerCredentials(direct.url("/").toString().trimEnd('/'), "app-key", RelayRoute(relay.url("/").toString().trimEnd('/'), "id", "secret"))
                val report = GokapiClient().testConnection(c)
                assertEquals(Route.DIRECT, report.route)
                assertTrue(report.canViewFiles)
                assertTrue(report.relayError!!.contains("relay rejected"))
                assertEquals("id", relay.takeRequest().getHeader("X-Relay-Id"))
            }
        }
    }

    @Test fun connectionTestReportsTheRelayRoute() = runBlocking {
        MockWebServer().use { relay ->
            relay.dispatcher = routes(
                "/api/info/version" to MockResponse().setBody("{\"Version\":\"2.2.4\"}"),
                "/api/files/list" to MockResponse().setBody("[]"),
                "/api/info/config" to MockResponse().setBody("{\"MaxFilesize\":100,\"MaxChunksize\":10}"),
            )
            val c = ServerCredentials(closedPortUrl(), "app-key", RelayRoute(relay.url("/").toString().trimEnd('/'), "id", "secret"))
            val report = GokapiClient().testConnection(c)
            assertEquals(Route.RELAY, report.route)
            assertNull(report.relayError)
        }
    }

    // A port that was just free, so connecting to it is refused.
    private fun closedPortUrl() = ServerSocket(0).use { "http://127.0.0.1:${it.localPort}" }

    private fun routes(vararg routes: Pair<String, MockResponse>) = object : Dispatcher() {
        private val responses = routes.toMap()
        override fun dispatch(request: RecordedRequest) = responses[request.path] ?: MockResponse().setResponseCode(401)
    }
}
