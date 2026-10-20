package com.uzukip.gokapi.desktop

import com.uzukip.gokapi.data.server.ServerCredentials
import com.uzukip.gokapi.desktop.data.CredentialStore
import com.uzukip.gokapi.desktop.data.PlainCipher
import com.uzukip.gokapi.desktop.data.SettingsStore
import com.uzukip.gokapi.desktop.data.ThemeMode
import com.uzukip.gokapi.desktop.platform.AppDirs
import com.uzukip.gokapi.desktop.ui.safeFilename
import com.uzukip.gokapi.desktop.ui.uniqueTarget
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DesktopStorageTest {
    private val root = Files.createTempDirectory("gokapi-desktop-test").toFile().apply { deleteOnExit() }

    @Test
    fun settingsSurviveRestart() = runBlocking {
        val file = File(root, "settings.properties")
        SettingsStore(file).update { it.copy(themeMode = ThemeMode.DARK, userSeed = "#123ABC", uploadDownloads = 4, autoCopyLink = true) }
        val reloaded = SettingsStore(file).settings.value
        assertEquals(ThemeMode.DARK, reloaded.themeMode)
        assertEquals("#123ABC", reloaded.userSeed)
        assertEquals(4, reloaded.uploadDownloads)
        assertEquals(true, reloaded.autoCopyLink)
        assertEquals("closing to the tray is on by default", true, reloaded.closeToTray)
    }

    @Test
    fun credentialsRoundTripInAnOwnerOnlyFile() = runBlocking {
        val file = File(root, "config/credentials.properties")
        CredentialStore(file, PlainCipher).save(ServerCredentials("https://files.example.com", "secret-key"))
        val loaded = CredentialStore(file, PlainCipher).load()
        assertEquals("https://files.example.com", loaded.serverUrl)
        assertEquals("secret-key", loaded.apiKey)
        if (!AppDirs.isWindows) {
            assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(file.toPath()))
        }
    }

    @Test
    fun missingCredentialsAreBlank() = runBlocking {
        val loaded = CredentialStore(File(root, "none.properties"), PlainCipher).load()
        assertEquals("", loaded.serverUrl)
        assertEquals("", loaded.apiKey)
    }

    @Test
    fun appDirsFollowXdgAndWindowsConventions() {
        val home = File("/home/me")
        val linux = AppDirs(env = { if (it == "XDG_CONFIG_HOME") "/tmp/cfg" else null }, home = home, windows = false)
        assertEquals(File("/tmp/cfg/gokapi-desktop"), linux.configDir)
        assertEquals(File("/home/me/.cache/gokapi-desktop"), linux.cacheDir)
        val windows = AppDirs(env = { if (it == "LOCALAPPDATA") "/c/Local" else null }, home = home, windows = true)
        assertEquals(File("/c/Local/GokapiDesktop/config"), windows.configDir)
        assertEquals(File("/home/me/Downloads/Gokapi"), windows.downloadsDir)
    }

    @Test
    fun downloadsUseSafeUniqueNames() {
        assertEquals("a_b_c.txt", safeFilename("a/b:c.txt"))
        assertEquals("file", safeFilename(" . "))
        val dir = File(root, "downloads").apply { mkdirs() }
        File(dir, "report.pdf").writeText("x")
        File(dir, "report (2).pdf").writeText("x")
        assertEquals("report (3).pdf", uniqueTarget(dir, "report.pdf").name)
        assertEquals("notes", uniqueTarget(dir, "notes").name)
        assertFalse(File(dir, "notes").exists())
    }
}
