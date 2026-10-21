import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Every build is versioned `<versionBase>.<commit count>`, as in metrolist-desktop, so each
// commit has a higher version. Windows Installer compares only major.minor.build and refuses
// to reinstall an equal version. Falls back to 0 without git. The justfile reads versionBase
// from this line.
val versionBase = "0.1"
val commitCount: Int =
    runCatching {
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().toInt()
    }.getOrDefault(0)
version = "$versionBase.$commitCount"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Same opt-ins as the Android app: the shared components use M3 Expressive.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

// jpackage builds installers only for the OS it runs on, so the Windows EXE is built on Windows.
val buildingOnWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

// The Gokapi client and the Compose components have no Android dependencies, so the desktop
// app compiles them from the Android app's sources instead of keeping a copy. Add a file here
// only when it compiles against Compose Multiplatform unchanged.
val sharedSources = listOf(
    "com/uzukip/gokapi/data/server/GokapiClient.kt",
    "com/uzukip/gokapi/data/server/GokapiFile.kt",
    "com/uzukip/gokapi/ui/components/Bouncy.kt",
    "com/uzukip/gokapi/ui/components/Expressive.kt",
    "com/uzukip/gokapi/ui/components/FileDetails.kt",
    "com/uzukip/gokapi/ui/components/MciGlyphs.kt",
    "com/uzukip/gokapi/ui/components/Pills.kt",
    "com/uzukip/gokapi/ui/components/PressScale.kt",
    "com/uzukip/gokapi/ui/components/ThemedTextField.kt",
    "com/uzukip/gokapi/ui/components/TopBar.kt",
    "com/uzukip/gokapi/ui/settings/SettingsComponents.kt",
)
val syncSharedSources = tasks.register<Sync>("syncSharedSources") {
    from(rootProject.file("app/src/main/java")) { include(sharedSources) }
    into(layout.buildDirectory.dir("generated/shared/kotlin"))
}
sourceSets.main { kotlin.srcDir(syncSharedSources) }

// The Android app's fonts, so both apps render the same type and icons (ui/theme/Type.kt),
// with the font license the OFL requires to travel with the font.
val syncSharedFonts = tasks.register<Sync>("syncSharedFonts") {
    from(rootProject.file("app/src/main/res/font")) { include("google_sans_flex.ttf", "mci.ttf"); into("font") }
    from(rootProject.file("app/src/main/assets/licenses")) { into("licenses") }
    into(layout.buildDirectory.dir("generated/shared/resources"))
}
sourceSets.main { resources.srcDir(syncSharedFonts) }

// The Settings screen shows the build's version (platform/AppVersion.kt reads this resource).
val generateVersionResource = tasks.register("generateVersionResource") {
    val output = layout.buildDirectory.dir("generated/version")
    val appVersion = project.version.toString()
    inputs.property("version", appVersion)
    outputs.dir(output)
    doLast {
        val file = output.get().asFile.resolve("gokapi-desktop/version.properties")
        file.parentFile.mkdirs()
        file.writeText("version=$appVersion\n")
    }
}
sourceSets.main { resources.srcDir(generateVersionResource) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.desktop.material3)
    implementation(libs.compose.desktop.material.icons.extended)
    implementation(libs.material.kolor)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jna.platform)
    // The Linux tray: a StatusNotifierItem on the session bus (platform/LinuxSniTray.kt).
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport.unixsocket)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

// Pin packaging to JDK 17 so the `java` binary copied into the AppImage (see
// packageAppImageFile) matches the jlink runtime it runs.
val packagingJdk = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }

compose.desktop {
    application {
        mainClass = "com.uzukip.gokapi.desktop.MainKt"
        javaHome = packagingJdk.get().metadata.installationPath.asFile.absolutePath
        // Lets platform/WmClass.kt set WM_CLASS so KDE matches the launcher entry.
        // Windows' java.desktop has no sun.awt.X11, and the JVM warns about the flag there.
        if (!buildingOnWindows) jvmArgs += listOf("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
        jvmArgs += listOf("-Xms32m", "-Xmx384m", "-XX:MinHeapFreeRatio=20", "-XX:MaxHeapFreeRatio=40")

        nativeDistributions {
            // crypto.ec: TLS to the files host; jdk.unsupported: Skiko and coroutines;
            // security.auth: dbus-java's unix-socket auth for the Linux tray.
            modules("java.naming", "jdk.crypto.ec", "jdk.security.auth", "jdk.unsupported")
            targetFormats(TargetFormat.AppImage, TargetFormat.Exe)
            // Windows shows packageName as the installed app's name, Start menu entry, EXE,
            // and install folder. Linux keeps the ID, which AppRun, WM_CLASS, and the
            // launcher entry use.
            packageName = if (buildingOnWindows) "Files - Gokapi Desktop" else "gokapi-desktop"
            packageVersion = project.version.toString()
            description = "Desktop client for a private Gokapi file server"
            copyright = "Copyright © 2026 Uzuki-P"

            linux {
                iconFile.set(rootProject.file("desktop/assets/gokapi-desktop.png"))
            }
            windows {
                iconFile.set(rootProject.file("desktop/assets/gokapi-desktop.ico"))
                menuGroup = "Gokapi"
                // Fixed for every release so a newer installer replaces the installed app.
                upgradeUuid = "5b0f6f1e-7a43-4d0c-9a51-2f7c6f0f3c1d"
                // Per-user install into %LOCALAPPDATA%\Files - Gokapi Desktop, as metrolist-desktop does:
                // no admin prompt, and `just update-exe` can swap the files in place.
                perUserInstall = true
                shortcut = true
                menu = true
                dirChooser = true
            }
        }
    }
}

// Compose's packageAppImage task creates a jpackage app directory. Wrap it with appimagetool
// to produce the portable .AppImage (adapted from metrolist-desktop).
//
// AppRun does not use jpackage's native launcher: on Fedora 44 (glibc 2.43) it segfaults in
// setenv once the launcher config passes about 50 classpath entries (metrolist-desktop's
// docs/decisions.md). AppRun runs the bundled runtime's `java` with the classpath and options
// from the generated launcher config.
tasks.register("packageAppImageFile") {
    group = "distribution"
    description = "Packages the Linux AppImage into build/compose/binaries/main/appimage/."
    dependsOn("packageAppImage")
    val appBundle = layout.buildDirectory.dir("compose/binaries/main/app/gokapi-desktop")
    val appDir = layout.buildDirectory.dir("compose/binaries/main/appimage/gokapi-desktop.AppDir")
    val outputDir = layout.buildDirectory.dir("compose/binaries/main/appimage")
    val toolFile = File(System.getProperty("user.home"), ".cache/gokapi-desktop/tools/appimagetool-x86_64.AppImage")
    val iconFile = rootProject.layout.projectDirectory.file("desktop/assets/gokapi-desktop.png")
    val appVersion = project.version.toString()
    val outputFile = outputDir.get().file("gokapi-desktop-$appVersion.AppImage")

    // Track the jpackage bundle so repeat runs skip the appimagetool repackage when nothing changed.
    inputs.dir(appBundle)
    outputs.file(outputFile)

    doLast {
        if (!toolFile.exists()) {
            toolFile.parentFile.mkdirs()
            URI("https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage")
                .toURL().openStream().use { input -> toolFile.outputStream().use(input::copyTo) }
            toolFile.setExecutable(true)
        }

        val directory = appDir.get().asFile
        if (directory.exists()) check(directory.deleteRecursively()) { "Could not clear $directory" }
        val usr = File(directory, "usr")
        check(usr.mkdirs()) { "Could not create $usr" }
        val copy = ProcessBuilder("cp", "-a", "${appBundle.get().asFile.absolutePath}/.", usr.absolutePath)
            .inheritIO().start()
        check(copy.waitFor() == 0) { "Could not copy the app bundle into $usr" }

        val javaBinary = File(usr, "lib/runtime/bin/java")
        javaBinary.parentFile.mkdirs()
        packagingJdk.get().executablePath.asFile.copyTo(javaBinary, overwrite = true)
        javaBinary.setExecutable(true)

        val config = File(usr, "lib/app/gokapi-desktop.cfg").readLines()
        val mainClass = config.first { it.startsWith("app.mainclass=") }.substringAfter("=")
        val classpath = config.filter { it.startsWith("app.classpath=") }
            .joinToString(":") { it.substringAfter("=").replace("\$APPDIR", "\$APP") }
        val javaOptions = config.filter { it.startsWith("java-options=") }
            .joinToString(" ") { "\"" + it.substringAfter("=").replace("\$APPDIR", "\$APP") + "\"" }
        File(directory, "AppRun").apply {
            writeText(
                "#!/bin/sh\n" +
                    "HERE=\"\$(dirname \"\$(readlink -f \"\$0\")\")\"\n" +
                    "APP=\"\$HERE/usr/lib/app\"\n" +
                    "export MALLOC_ARENA_MAX=\"\${MALLOC_ARENA_MAX:-2}\"\n" +
                    "exec \"\$HERE/usr/lib/runtime/bin/java\" $javaOptions -cp \"$classpath\" $mainClass \"\$@\"\n",
            )
            setExecutable(true)
        }
        File(directory, "gokapi-desktop.desktop").writeText(
            "[Desktop Entry]\nType=Application\nName=Files - Gokapi Desktop\nComment=Private file sharing\nExec=gokapi-desktop\nIcon=gokapi-desktop\nCategories=Network;FileTransfer;\nTerminal=false\n",
        )
        iconFile.asFile.copyTo(File(directory, "gokapi-desktop.png"), overwrite = true)

        val output = outputFile.asFile
        output.parentFile.mkdirs()
        if (output.exists()) check(output.delete()) { "Could not replace $output" }
        val packageProcess = ProcessBuilder(
            toolFile.absolutePath,
            "--appimage-extract-and-run",
            "--no-appstream",
            directory.absolutePath,
            output.absolutePath,
        ).apply {
            environment()["ARCH"] = "x86_64"
            inheritIO()
        }.start()
        check(packageProcess.waitFor() == 0) { "appimagetool failed" }
        println("AppImage written to ${output.absolutePath}")
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The real app against a fake server with sample files (src/test/.../MockApp.kt).
// Usage: ./gradlew :desktop:mockApp
tasks.register<JavaExec>("mockApp") {
    group = "application"
    description = "Runs the desktop app against a fake Gokapi server in a throwaway config directory."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.uzukip.gokapi.desktop.MockAppKt")
    javaLauncher.set(packagingJdk)
    if (!buildingOnWindows) jvmArgs("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
}
