# On Windows, backticks and recipes run in Windows PowerShell (as in metrolist-desktop).
set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

# Desktop app (desktop/), for Linux and Windows. Every desktop recipe passes
# -PdesktopOnly=true so Gradle skips the Android module and needs no Android SDK.
desktop_id := "gokapi-desktop"
desktop_name := "Gokapi"
# `<versionBase>.<commit count>`, matching desktop/build.gradle.kts.
desktop_version := replace_regex(read("desktop/build.gradle.kts"), '(?s).*\nval versionBase = "([^"]*)".*', '$1') + "." + `git rev-list --count HEAD`
appimage_task := ":desktop:packageAppImageFile"
appimage_output := "desktop/build/compose/binaries/main/appimage/gokapi-desktop-" + desktop_version + ".AppImage"
desktop_icon := "desktop/assets/gokapi-desktop.png"
exe_output := "desktop/build/compose/binaries/main/exe/gokapi-desktop-" + desktop_version + ".exe"

# List available recipes
default:
    @just --list

# Build, install, and launch the debug app on the connected emulator/device
dev:
    ./gradlew :app:installMainAppDebug
    adb shell monkey -p com.uzukip.gokapi 1

# Full validation: debug APK, lint, and JVM unit tests (Android and desktop)
check:
    ./gradlew :app:assembleMainAppDebug :app:assembleDevDebug :app:lintMainAppDebug :app:lintDevDebug :app:testMainAppDebugUnitTest :app:testDevDebugUnitTest :desktop:test

# Build the signed release APK into _apk/gokapi-android-<version>-<d.mmm-HH-MM>.apk and upload it
build-apk:
    #!/usr/bin/env bash
    set -euo pipefail
    ./gradlew :app:assembleMainAppRelease
    mkdir -p _apk
    ver="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)"
    stamp="$(date +"%-d.%b-%H-%M" | tr 'A-Z' 'a-z')"
    apk="_apk/gokapi-android-${ver}-${stamp}.apk"
    cp app/build/outputs/apk/mainApp/release/app-mainApp-release.apk "$apk"
    echo "Built $apk"
    ~/docker/files/bin/share "$(pwd)/$apk"

# Build the dev release APK into _apk/DEV-gokapi-android-<version>-dev_<dd-mmm_HH-MM>.apk and upload it
build-apk-dev:
    #!/usr/bin/env bash
    set -euo pipefail
    ./gradlew :app:assembleDevRelease
    mkdir -p _apk
    ver="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)"
    stamp="$(date +"%d-%b_%H-%M" | tr 'A-Z' 'a-z')"
    apk="_apk/DEV-gokapi-android-${ver}-dev_${stamp}.apk"
    cp app/build/outputs/apk/dev/release/app-dev-release.apk "$apk"
    echo "Built $apk"
    ~/docker/files/bin/share "$(pwd)/$apk"

# Linux recipes drop LD_LIBRARY_PATH: terminals started from AppImage-hosted editors
# (e.g. T3 Code) inject their mount's lib directory, which can crash the Compose launcher.

# Run the desktop app from source.
[linux]
dev-desktop:
    env -u LD_LIBRARY_PATH ./gradlew -PdesktopOnly=true :desktop:run

# Run the desktop app from source.
[windows]
dev-desktop:
    .\gradlew.bat -PdesktopOnly=true :desktop:run

# Run the desktop app against a fake server with sample files (throwaway settings, no network).
[linux]
desktop-mock:
    env -u LD_LIBRARY_PATH ./gradlew -PdesktopOnly=true :desktop:mockApp

# Build and stage the Linux AppImage in _apk/.
[linux]
build-appimage:
    env -u LD_LIBRARY_PATH ./gradlew -PdesktopOnly=true {{appimage_task}}
    bash scripts/stage-artifact '{{appimage_output}}' '{{desktop_id}}' '{{desktop_version}}' _apk

# Ensure the AppImage exists and install it to ~/apps with a launcher entry.
[linux]
install:
    env -u LD_LIBRARY_PATH ./gradlew -PdesktopOnly=true {{appimage_task}}
    bash scripts/install-appimage '{{appimage_output}}' '{{desktop_id}}' '{{desktop_name}}' '{{desktop_icon}}'

# Remove the installed AppImage, launcher entry, and icon (settings and API key stay).
[linux]
uninstall:
    #!/usr/bin/env bash
    set -euo pipefail
    data_dir=${XDG_DATA_HOME:-$HOME/.local/share}
    rm -f -- "$HOME/apps/{{desktop_id}}.AppImage" "$data_dir/applications/{{desktop_id}}.desktop" "$data_dir/icons/hicolor/512x512/apps/{{desktop_id}}.png"
    echo "Removed {{desktop_id}}; settings in ${XDG_CONFIG_HOME:-$HOME/.config}/{{desktop_id}} are kept."

# Build, stage, and install the Linux AppImage.
[linux]
update:
    #!/usr/bin/env bash
    set -euo pipefail
    env -u LD_LIBRARY_PATH ./gradlew -PdesktopOnly=true {{appimage_task}}
    staged="$(bash scripts/stage-artifact '{{appimage_output}}' '{{desktop_id}}' '{{desktop_version}}' _apk)"
    bash scripts/install-appimage "$staged" '{{desktop_id}}' '{{desktop_name}}' '{{desktop_icon}}'

# On Windows: build the EXE installer and stage it in _apk/.
[windows]
[script("powershell.exe", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File")]
[extension(".ps1")]
build-exe:
    $ErrorActionPreference = "Stop"
    # packageExe alone runs jpackage from the jars; update-exe swaps in the
    # app image that only createDistributable writes.
    .\gradlew.bat -PdesktopOnly=true :desktop:createDistributable :desktop:packageExe
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $stamp = (Get-Date).ToString("dd-MMM_HH-mm", [Globalization.CultureInfo]::InvariantCulture).ToLower()
    New-Item -ItemType Directory -Force _apk | Out-Null
    $target = "_apk/{{desktop_id}}-{{desktop_version}}_$stamp.exe"
    Copy-Item -LiteralPath "{{exe_output}}" -Destination $target -Force
    (Resolve-Path $target).Path

# On Windows: build and stage the installer, then swap the installed app's files in place
# (or run the installer once when the app is not installed). Settings and the API key stay.
[windows]
[script("powershell.exe", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File")]
[extension(".ps1")]
update-exe:
    $ErrorActionPreference = "Stop"
    # Run build-exe without a pipe or capture: the Gradle and Kotlin daemons it starts
    # inherit the pipe's write end, so Tee-Object or $(...) would wait until they exit.
    & "{{just_executable()}}" build-exe
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    # The newest staged copy of this version is the one build-exe just wrote.
    $staged = Get-ChildItem -LiteralPath _apk -Filter "{{desktop_id}}-{{desktop_version}}_*.exe" | Sort-Object CreationTime | Select-Object -Last 1
    if (-not $staged) { throw "build-exe left no staged installer for {{desktop_version}} in _apk." }
    & .\scripts\update-installed-exe.ps1 -Installer $staged.FullName -Version "{{desktop_version}}"

# Build the EXE on GitHub Actions for the pushed HEAD, save it in _apk/, and share it via Gokapi.
[linux]
ci-exe:
    bash scripts/ci-exe
