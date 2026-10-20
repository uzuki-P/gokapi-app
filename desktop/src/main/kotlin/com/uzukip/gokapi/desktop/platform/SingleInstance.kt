package com.uzukip.gokapi.desktop.platform

import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * Keeps one app per user. The first launch locks `instance.lock` and listens on a loopback
 * port written to `instance.port`. A later launch, for example from the app menu while the
 * window sits in the tray, finds the lock taken, asks the running app to show its window,
 * and exits.
 */
class SingleInstance private constructor(private val lock: FileLock, private val server: ServerSocket) : AutoCloseable {
    /** Calls [onShow] on a background thread each time another launch asks for the window. */
    fun listen(onShow: () -> Unit) {
        thread(name = "gokapi-single-instance", isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                client.use { socket ->
                    socket.soTimeout = 2_000
                    val message = runCatching { socket.getInputStream().bufferedReader().readLine() }.getOrNull()
                    if (message == SHOW) onShow()
                }
            }
        }
    }

    override fun close() {
        runCatching { server.close() }
        runCatching { lock.release(); lock.channel().close() }
    }

    companion object {
        private const val SHOW = "show"

        /**
         * How this launch should go: as the running app, or handed over to one already running.
         * When the running app holds the lock but does not answer, this launch starts anyway
         * with no guard, rather than do nothing.
         */
        fun acquire(directory: File): Launch {
            directory.mkdirs()
            val portFile = File(directory, "instance.port")
            val channel = FileChannel.open(File(directory, "instance.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) {
                channel.close()
                return if (notifyRunning(portFile)) Launch.HandedOver else Launch.Primary(null)
            }
            val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
            portFile.writeText(server.localPort.toString())
            return Launch.Primary(SingleInstance(lock, server))
        }

        private fun notifyRunning(portFile: File): Boolean = runCatching {
            val port = portFile.readText().trim().toInt()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000)
                socket.getOutputStream().write("$SHOW\n".toByteArray())
            }
            true
        }.getOrDefault(false)
    }
}

sealed interface Launch {
    /** Another launch is running and was asked to show its window; exit. */
    data object HandedOver : Launch

    /** Run the app; [instance] is null when the guard could not be set up. */
    class Primary(val instance: SingleInstance?) : Launch
}
