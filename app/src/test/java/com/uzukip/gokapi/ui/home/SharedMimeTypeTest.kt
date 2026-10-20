package com.uzukip.gokapi.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedMimeTypeTest {
    @Test fun keepsOneExactType() = assertEquals("image/png", sharedMimeType(listOf("image/png", "IMAGE/PNG")))

    @Test fun widensToTheSharedFamily() = assertEquals("image/*", sharedMimeType(listOf("image/png", "image/jpeg")))

    @Test fun fallsBackToAnyType() = assertEquals("*/*", sharedMimeType(listOf("image/png", "application/pdf")))

    @Test fun treatsBlankAsBinary() = assertEquals("application/*", sharedMimeType(listOf("", "application/pdf")))
}
