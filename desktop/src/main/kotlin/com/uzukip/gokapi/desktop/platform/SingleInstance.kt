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
 * passes along any files it was started with (Explorer's Upload to Gokapi), and exits.
 *
 * The message is `show`, then one `upload\t<absolute path>` line per file, in UTF-8.
 */
class SingleInstance private constructor(private val lock: FileLock, private val server: ServerSocket) : AutoCloseable {
    /** Calls [onLaunch] on a background thread with the files each later launch passes along. */
    fun listen(onLaunch: (List<File>) -> Unit) {
        thread(name = "gokapi-single-instance", isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                client.use { socket ->
                    socket.soTimeout = 2_000
                    val lines = runCatching { socket.getInputStream().bufferedReader(Charsets.UTF_8).readLines() }.getOrNull()
                    if (lines?.firstOrNull() == SHOW) {
                        onLaunch(lines.drop(1).filter { it.startsWith(UPLOAD) }.map { File(it.removePrefix(UPLOAD)) })
                    }
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
        private const val UPLOAD = "upload\t"

        /**
         * How this launch should go: as the running app, or handed over to one already running
         * along with [files]. When the running app holds the lock but does not answer for a few
         * seconds, this launch starts anyway with no guard, rather than do nothing.
         */
        fun acquire(directory: File, files: List<File> = emptyList()): Launch {
            directory.mkdirs()
            val portFile = File(directory, "instance.port")
            val channel = FileChannel.open(File(directory, "instance.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) {
                channel.close()
                // Explorer starts one launch per selected file at once: the winner may not have
                // written its port yet, and the others queue on the socket's backlog.
                repeat(HANDOVER_ATTEMPTS) { attempt ->
                    if (notifyRunning(portFile, files)) return Launch.HandedOver
                    if (attempt < HANDOVER_ATTEMPTS - 1) Thread.sleep(250)
                }
                return Launch.Primary(null)
            }
            val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            portFile.writeText(server.localPort.toString())
            return Launch.Primary(SingleInstance(lock, server))
        }

        private const val HANDOVER_ATTEMPTS = 12

        private fun notifyRunning(portFile: File, files: List<File>): Boolean = runCatching {
            val port = portFile.readText().trim().toInt()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000)
                val message = buildString {
                    append(SHOW).append('\n')
                    files.forEach { append(UPLOAD).append(it.absolutePath).append('\n') }
                }
                socket.getOutputStream().write(message.toByteArray(Charsets.UTF_8))
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
