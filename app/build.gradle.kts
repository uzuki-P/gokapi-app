import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localConfig = Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.exists()) configFile.inputStream().use(::load)
}
val filesHost = localConfig.getProperty("gokapi.filesHost", "").trim()
require(filesHost.isEmpty() || filesHost.matches(Regex("[A-Za-z0-9.-]+"))) { "gokapi.filesHost must be a hostname." }
val linkFilter = if (filesHost.isEmpty()) "" else """
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="$filesHost" />
        <data android:pathPrefix="/d" />
        <data android:pathPrefix="/h/" />
    </intent-filter>
""".trimIndent()
val generatedManifest = layout.buildDirectory.file("generated/links/AndroidManifest.xml").get().asFile
val generateLinkManifest = tasks.register("generateLinkManifest") {
    inputs.file("src/main/AndroidManifest.xml")
    inputs.property("filesHost", filesHost)
    outputs.file(generatedManifest)
    doLast {
        generatedManifest.parentFile.mkdirs()
        generatedManifest.writeText(file("src/main/AndroidManifest.xml").readText().replace("<!-- FILE_LINK_FILTER -->", linkFilter))
    }
}

android {
    namespace = "com.uzukip.gokapi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.uzukip.gokapi"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 36
        buildConfigField("String", "FILES_HOST", "\"$filesHost\"")
        versionCode = 3
        versionName = "0.1.2"
    }

    sourceSets.getByName("main").manifest.srcFile(generatedManifest)

    flavorDimensions += "distribution"
    productFlavors {
        create("mainApp") {
            dimension = "distribution"
        }
        create("dev") {
            dimension = "distribution"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }

// Release signing: a user-provided keystore via keystore.properties at the repo
// root (git-ignored). When absent, release builds fall back to debug signing so
// `assembleRelease` still produces an installable artifact for local testing.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
    val releaseSigning = keystoreProperties["storeFile"]?.let { storeFilePath ->
        signingConfigs.create("release") {
            storeFile = rootProject.file(storeFilePath)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseConfig = releaseSigning
            if (releaseConfig != null) {
                signingConfig = releaseConfig
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // The UI is built on the M3 Expressive components, which are
        // still experimental; opt in once here instead of per call site.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.work)
    implementation(libs.unifiedpush)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material.icons.extended)
    implementation(libs.material.kolor)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.okhttp.tls)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

tasks.matching { it.name == "preBuild" || (it.name.startsWith("process") && it.name.endsWith("MainManifest")) }.configureEach { dependsOn(generateLinkManifest) }

// Generate two signed, code-free APKs for real PackageInstaller tests.
val fixtureAssets = layout.buildDirectory.dir("generated/installFixtures")
android.sourceSets.getByName("androidTest").assets.srcDir(fixtureAssets.get().asFile)
val generateInstallFixtures = tasks.register("generateInstallFixtures") {
    inputs.file("src/androidTest/fixtures/AndroidManifest.xml")
    outputs.dir(fixtureAssets)
    doLast {
        val sdk = androidComponents.sdkComponents.sdkDirectory.get().asFile
        val toolsDir = sdk.resolve("build-tools/${android.buildToolsVersion}")
        val output = fixtureAssets.get().asFile.apply { mkdirs() }
        val temporary = layout.buildDirectory.dir("tmp/installFixtures").get().asFile.apply { mkdirs() }
        for (version in 1..2) {
            val manifest = temporary.resolve("manifest-$version.xml")
            manifest.writeText(file("src/androidTest/fixtures/AndroidManifest.xml").readText().replace("FIXTURE_VERSION", version.toString()))
            val unsigned = temporary.resolve("unsigned-$version.apk")
            val link = listOf(toolsDir.resolve("aapt2").path, "link", "--manifest", manifest.path, "-I", androidComponents.sdkComponents.bootClasspath.get().first { it.asFile.name == "android.jar" }.asFile.path, "-o", unsigned.path)
            val linkProcess = ProcessBuilder(link).redirectErrorStream(true).start()
            val linkOutput = linkProcess.inputStream.bufferedReader().readText()
            check(linkProcess.waitFor() == 0) { linkOutput }
            val sign = listOf(toolsDir.resolve("apksigner").path, "sign", "--ks", android.signingConfigs.getByName("debug").storeFile!!.path,
                "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", output.resolve("fixture-$version.apk").path, unsigned.path)
            val signProcess = ProcessBuilder(sign).redirectErrorStream(true).start()
            val signOutput = signProcess.inputStream.bufferedReader().readText()
            check(signProcess.waitFor() == 0) { signOutput }
        }
        val otherKey = temporary.resolve("fixture-other.jks")
        if (!otherKey.exists()) {
            val keyCommand = listOf(File(System.getProperty("java.home"), "bin/keytool").path, "-genkeypair", "-keystore", otherKey.path,
                "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=GokapiFixture", "-storepass", "android", "-keypass", "android", "-validity", "365")
            val keyProcess = ProcessBuilder(keyCommand).redirectErrorStream(true).start()
            val keyOutput = keyProcess.inputStream.bufferedReader().readText()
            check(keyProcess.waitFor() == 0) { keyOutput }
        }
        val otherSign = listOf(toolsDir.resolve("apksigner").path, "sign", "--ks", otherKey.path, "--ks-key-alias", "fixture",
            "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", output.resolve("fixture-other.apk").path, temporary.resolve("unsigned-2.apk").path)
        val otherProcess = ProcessBuilder(otherSign).redirectErrorStream(true).start()
        val otherOutput = otherProcess.inputStream.bufferedReader().readText()
        check(otherProcess.waitFor() == 0) { otherOutput }
    }
}
tasks.matching { it.name.startsWith("pre") && it.name.endsWith("AndroidTestBuild") }.configureEach { dependsOn(generateInstallFixtures) }
