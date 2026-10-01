package com.uzukip.gokapi.data.server

import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class CredentialCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = CredentialCipher { key }

    @Test fun roundTripUsesFreshIvAndHidesPlaintext() {
        val first = cipher.encrypt("android-test-key")
        val second = cipher.encrypt("android-test-key")
        assertEquals("android-test-key", cipher.decrypt(first))
        assertNotEquals(first, second)
        assertFalse(first.contains("android-test-key"))
    }

    @Test fun rejectsTamperedCiphertextAndWrongKey() {
        val encrypted = cipher.encrypt("android-test-key")
        val parts = encrypted.split('.')
        val bytes = java.util.Base64.getDecoder().decode(parts[1])
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        val tampered = parts[0] + "." + java.util.Base64.getEncoder().encodeToString(bytes)
        assertThrows(javax.crypto.AEADBadTagException::class.java) { cipher.decrypt(tampered) }
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(javax.crypto.AEADBadTagException::class.java) { CredentialCipher { otherKey }.decrypt(encrypted) }
    }
}
