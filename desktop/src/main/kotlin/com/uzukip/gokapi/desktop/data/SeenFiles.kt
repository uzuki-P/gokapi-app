package com.uzukip.gokapi.desktop.data

import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * File IDs the user already knows about, per server, as the Android app's
 * `SettingsRepository.newFileIds`. The first check on a server records the list as a
 * baseline instead of announcing every existing file.
 */
class SeenFiles(private val file: File) {
    private val lock = Mutex()

    /** Records [ids] as the server's current list and returns the ones not seen before. */
    suspend fun update(server: String, ids: Set<String>): Set<String> = lock.withLock {
        val (savedServer, saved) = read()
        write(server, if (savedServer == server && saved != null) saved + ids else ids)
        if (savedServer == server && saved != null) ids - saved else emptySet()
    }

    /**
     * Marks one file as seen; true when it was new. Before the first check there is no
     * baseline yet, and that check will record the file anyway, so nothing is written.
     */
    suspend fun add(server: String, id: String): Boolean = lock.withLock {
        val (savedServer, saved) = read()
        if (savedServer != server || saved == null || id in saved) return@withLock false
        write(server, saved + id)
        true
    }

    private suspend fun read(): Pair<String?, Set<String>?> = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null to null
        val p = Properties().apply { runCatching { file.inputStream().use(::load) } }
        p.getProperty("server") to p.getProperty("ids")?.split(',')?.filter(String::isNotBlank)?.toSet()
    }

    // Kept to the newest 2000 IDs; expired files drop out of the server list anyway.
    private suspend fun write(server: String, ids: Set<String>) = withContext(Dispatchers.IO) {
        file.parentFile.mkdirs()
        val p = Properties().apply {
            setProperty("server", server)
            setProperty("ids", ids.toList().takeLast(2000).joinToString(","))
        }
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.outputStream().use { p.store(it, null) }
        check(temporary.renameTo(file) || (file.delete() && temporary.renameTo(file))) { "Couldn't save the seen files." }
    }
}
