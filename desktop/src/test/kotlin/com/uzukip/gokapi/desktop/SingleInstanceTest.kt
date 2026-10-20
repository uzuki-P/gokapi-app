package com.uzukip.gokapi.desktop

import com.uzukip.gokapi.desktop.platform.Launch
import com.uzukip.gokapi.desktop.platform.SingleInstance
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleInstanceTest {
    @Test
    fun secondLaunchShowsTheRunningWindowAndHandsOver() {
        val directory = Files.createTempDirectory("gokapi-instance").toFile().apply { deleteOnExit() }
        val first = SingleInstance.acquire(directory)
        assertTrue(first is Launch.Primary)
        val instance = checkNotNull((first as Launch.Primary).instance)
        val shown = CountDownLatch(1)
        instance.listen { shown.countDown() }
        try {
            assertTrue(SingleInstance.acquire(directory) is Launch.HandedOver)
            assertTrue("the running app was not asked to show its window", shown.await(5, TimeUnit.SECONDS))
        } finally {
            instance.close()
        }
        // After the first app quits, the next launch runs as the app again.
        val next = SingleInstance.acquire(directory)
        assertNotNull((next as Launch.Primary).instance)
        next.instance?.close()
    }
}
