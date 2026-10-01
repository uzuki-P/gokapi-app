# List available recipes
default:
    @just --list

# Build, install, and launch the debug app on the connected emulator/device
dev:
    ./gradlew :app:installMainAppDebug
    adb shell monkey -p com.uzukip.gokapi 1

# Full validation: debug APK, lint, and JVM unit tests
check:
    ./gradlew :app:assembleMainAppDebug :app:assembleDevDebug :app:lintMainAppDebug :app:lintDevDebug :app:testMainAppDebugUnitTest :app:testDevDebugUnitTest

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
