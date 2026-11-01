package com.uzukip.gokapi.data.server

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A device's credentials from the server's `bin/pair add`, shown to the user before saving. */
class PairingLink(val device: String, val credentials: ServerCredentials) {
    override fun toString() = "PairingLink($device)"
}

/**
 * Parses `https://<files host>/pair#<payload>`, where the payload is unpadded base64url
 * JSON: `{"v":1,"name","url","key","relay","relayId","relaySecret"}`. The secrets sit in
 * the fragment, so opening the link in a browser never sends them to a server.
 */
fun parsePairingLink(text: String): PairingLink {
    val invalid = "That isn't a Gokapi pairing link. Pair this device again to get a new one."
    val link = text.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException(invalid)
    require(link.isHttps && link.encodedPath == "/pair" && !link.fragment.isNullOrBlank()) { invalid }
    val payload = try {
        Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(link.fragment!!.trim()), Charsets.UTF_8)).jsonObject
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(invalid)
    }
    require(payload.field("v")?.intOrNull == 1) { "This pairing link needs a newer version of the app." }
    val url = normalizeServerUrl(payload.text("url", invalid))
    // The payload must name the host the link points at; the confirm card shows both hosts.
    require(url.toHttpUrlOrNull()?.host == link.host) { "The pairing link's server doesn't match the link's host." }
    val relay = normalizeServerUrl(payload.text("relay", invalid))
    val key = payload.text("key", invalid)
    val relayId = payload.text("relayId", invalid)
    val relaySecret = payload.text("relaySecret", invalid)
    require(listOf(key, relayId, relaySecret).all { value -> value.all { it.code in 33..126 } }) { invalid }
    val device = payload.field("name")?.content?.takeIf { it.isNotBlank() } ?: "this device"
    return PairingLink(device, ServerCredentials(url, key, RelayRoute(relay, relayId, relaySecret)))
}

private fun JsonObject.field(name: String) = (get(name) as? JsonPrimitive)?.takeIf { it.isString || name == "v" }

private fun JsonObject.text(name: String, invalid: String) =
    field(name)?.content?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException(invalid)
