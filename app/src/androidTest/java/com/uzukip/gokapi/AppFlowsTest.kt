package com.uzukip.gokapi

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.uzukip.gokapi.data.server.*
import com.uzukip.gokapi.data.upload.UploadDocument
import com.uzukip.gokapi.data.upload.enqueueUploads
import com.uzukip.gokapi.platform.MainActivityState
import com.uzukip.gokapi.ui.home.HomeScreen
import com.uzukip.gokapi.ui.theme.GokapiTheme
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.*
import org.junit.Assert.*

class AppFlowsTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private lateinit var server: MockWebServer
    private lateinit var container: AppContainer
    private lateinit var originalContainer: AppContainer
    private lateinit var originalCredentials: ServerCredentials
    private val graphField = GokapiApplication::class.java.getDeclaredField("container").apply { isAccessible = true }
    private val apkJson = """{"Id":"fixture-apk","Name":"sample.apk","ContentType":"application/vnd.android.package-archive","Size":"3 B","SizeBytes":3,"UploadDate":1900000000,"UnlimitedTime":true,"UnlimitedDownloads":true}"""
    private val reportJson = """{"Id":"fixture-report","Name":"report.txt","ContentType":"text/plain","Size":"7 B","SizeBytes":7,"UploadDate":1899999999,"UnlimitedTime":true,"UnlimitedDownloads":true}"""

    @Before fun setUp() = runBlocking {
        server = MockWebServer()
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server.useHttps(tls.sslSocketFactory(), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/files/list" -> MockResponse().setBody("[$apkJson,$reportJson]")
                "/api/files/list/fixture-apk" -> MockResponse().setBody(apkJson)
                "/api/files/list/fixture-report" -> MockResponse().setBody(reportJson)
                "/api/files/download/fixture-report" -> MockResponse().setBody("fixture")
                "/api/info/config" -> MockResponse().setBody("{\"MaxFilesize\":100,\"MaxChunksize\":10}")
                "/api/chunk/add" -> MockResponse().setBody("{\"result\":\"OK\"}")
                "/api/chunk/complete" -> MockResponse().setBody("{\"Result\":\"OK\",\"FileInfo\":$reportJson}")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val client = GokapiClient(OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
            .followRedirects(false).followSslRedirects(false).build())
        val app = context.applicationContext as GokapiApplication
        originalContainer = app.container
        originalCredentials = originalContainer.credentials.load()
        container = AppContainer(app, client)
        // Only this instrumented process swaps the graph. Release code keeps manual constructor injection.
        graphField.set(app, container)
        container.credentials.save(ServerCredentials(server.url("/").toString().trimEnd('/'), "fixture-android-key"))
    }
    @After fun tearDown() = runBlocking {
        MainActivityState.foreground = false
        container.credentials.save(originalCredentials)
        graphField.set(context.applicationContext, originalContainer)
        server.shutdown()
    }

    @Test fun browseSearchAndConfirmDelete() {
        compose.setContent { GokapiTheme(com.uzukip.gokapi.data.settings.Settings()) { HomeScreen(container, null, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("sample.apk").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("sample.apk").assertIsDisplayed()
        compose.onNodeWithText("Search files").performTextInput("report")
        compose.onNodeWithText("report.txt").assertExists()
        compose.onNodeWithText("sample.apk").assertDoesNotExist()
        compose.onNodeWithText("Search files").performTextClearance()
        device.pressBack()
        compose.onNodeWithText("sample.apk").performTouchInput { longClick() }
        compose.onNodeWithText("Share file").assertIsDisplayed()
        compose.onNodeWithText("Delete file").performClick()
        compose.onNodeWithText("Delete sample.apk?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Delete sample.apk?").fetchSemanticsNodes().isEmpty() }
        device.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Share file").fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        shell("screencap -p /sdcard/Download/gokapi-files-test.png")
    }

    @Test fun deepLinkResolvesMetadata() {
        val incoming = Intent(Intent.ACTION_VIEW, Uri.parse(server.url("/d?id=fixture-apk").toString()))
        compose.setContent { GokapiTheme(com.uzukip.gokapi.data.settings.Settings()) { HomeScreen(container, incoming, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Share file").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Install").assertIsDisplayed()
        compose.onNodeWithText("Save to Downloads").assertIsDisplayed()
    }

    @Test fun rejectsLinksFromAnotherServer() {
        val incoming = Intent(Intent.ACTION_VIEW, Uri.parse("https://another.example.com/d?id=fixture-apk"))
        compose.setContent { GokapiTheme(com.uzukip.gokapi.data.settings.Settings()) { HomeScreen(container, incoming, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("This link belongs to another server. Check your Server settings.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share file").assertDoesNotExist()
    }

    @Test fun encryptedCredentialsDownloadAndSaveToMediaStore() = runBlocking {
        if (Build.VERSION.SDK_INT < 29) return@runBlocking
        val stored = container.credentials.load()
        assertEquals("fixture-android-key", stored.apiKey)
        assertFalse(File(context.filesDir, "datastore/gokapi_credentials.preferences_pb").readBytes().toString(Charsets.ISO_8859_1).contains("fixture-android-key"))
        val file = container.client.get(stored, "fixture-report")
        val cached = container.transfers.download(stored, file) { _, _ -> }
        assertEquals("fixture", cached.readText())
        val saved = container.transfers.saveToDownloads(cached, file)
        try { assertEquals("fixture", context.contentResolver.openInputStream(saved)!!.bufferedReader().use { it.readText() }) }
        finally { context.contentResolver.delete(saved, null, null) }
    }

    @Test fun backgroundUploadStreamsAContentUri() = runBlocking {
        val file = File(context.cacheDir, "downloads/upload-fixture.txt").apply { parentFile?.mkdirs(); writeText("fixture") }
        val uri = container.transfers.uri(file)
        val ids = enqueueUploads(context, listOf(UploadDocument(uri, "upload-fixture.txt", 7, "text/plain")), UploadOptions(7, 0, "fixture-password"))
        val manager = WorkManager.getInstance(context)
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var result: WorkInfo? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            result = manager.getWorkInfoById(ids.single()).get(5, TimeUnit.SECONDS)
            if (result?.state?.isFinished == true) break
            SystemClock.sleep(200)
        }
        assertEquals(result?.outputData?.getString("error"), WorkInfo.State.SUCCEEDED, result?.state)
        assertEquals("fixture-report", result?.outputData?.getString("file_id"))
    }

    @Test fun packageInstallerConfirmsFirstInstallAndUpdatesSilently() = runBlocking {
        Assume.assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        shell("pm uninstall com.uzukip.gokapi.installfixture")
        shell("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow")
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        compose.setContent { GokapiTheme(com.uzukip.gokapi.data.settings.Settings()) { androidx.compose.material3.Text("Installer test") } }
        MainActivityState.foreground = true
        fun fixture(version: Int): File = File(context.cacheDir, "downloads/fixture-$version.apk").apply {
            parentFile?.mkdirs()
            instrumentation.context.assets.open("fixture-$version.apk").use { input -> outputStream().use { input.copyTo(it) } }
        }
        val first = container.installer.inspect(fixture(1))
        assertNull(first.installedVersion)
        container.installer.install(first)
        assertTrue("Android should ask before the first install", device.wait(Until.hasObject(By.text("Install")), 20_000))
        device.findObject(By.text("Install")).click()
        awaitVersion("0.0.1")
        device.findObject(By.text("Done"))?.click()
        val other = File(context.cacheDir, "downloads/fixture-other.apk").apply {
            instrumentation.context.assets.open("fixture-other.apk").use { input -> outputStream().use { input.copyTo(it) } }
        }
        val mismatched = container.installer.inspect(other)
        assertFalse(mismatched.signatureMatches)
        assertThrows(IllegalStateException::class.java) { runBlocking { container.installer.install(mismatched) } }
        val update = container.installer.inspect(fixture(2))
        assertEquals("0.0.1", update.installedVersion)
        assertTrue(update.signatureMatches)
        container.installer.install(update)
        awaitVersion("0.0.2")
        assertFalse("The update should not need another confirmation", device.hasObject(By.text("Install")))
        shell("pm uninstall com.uzukip.gokapi.installfixture")
        Unit
    }
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
    }
    @Suppress("DEPRECATION")
    private fun awaitVersion(version: String) {
        val deadline = SystemClock.elapsedRealtime() + 25_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val actual = runCatching { context.packageManager.getPackageInfo("com.uzukip.gokapi.installfixture", 0).versionName }.getOrNull()
            if (actual == version) return
            SystemClock.sleep(200)
        }
        fail("Installer did not finish version $version")
    }
}
