pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads JDK 17 for the desktop module's toolchain when it is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "gokapi-android"
// The desktop recipes pass -PdesktopOnly=true so a Windows machine without the Android SDK can
// build the desktop app; the desktop module reads the app's shared sources by path, not through :app.
if (providers.gradleProperty("desktopOnly").orNull != "true") include(":app")
include(":desktop")
