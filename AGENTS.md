# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

## What this project is

gokapi-android is a native Android client (Kotlin + Jetpack Compose, Material 3
Expressive) for the private Gokapi service in `~/dotconfig/_home/docker/files`.
`plan.md` holds the roadmap and the Gokapi API facts the app depends on.

The project shell, theme, components, and settings screen are copied from
`~/projects/_sandbox/spendr-kt`. When a shared piece needs a fix, check
whether spendr-kt has the same bug.

`desktop/` is a Compose Multiplatform desktop app for Linux and Windows. It
compiles the Gokapi client and most Compose components straight from
`app/src/main/java` (the `sharedSources` list in `desktop/build.gradle.kts`),
so keep those files free of Android APIs. When a shared file stops compiling
on desktop, drop it from the list and keep a desktop copy, as `Choices.kt`
does for a material3 API rename. Desktop-only code lives under
`desktop/src/main/kotlin/com/uzukip/gokapi/desktop/`.

The desktop app keeps running in the system tray: closing the window hides it
while the tray icon is showing, and Quit in the tray menu exits. The tray is a
StatusNotifierItem over D-Bus on Linux (`platform/LinuxSniTray.kt`) and AWT
`SystemTray` on Windows. A second launch shows the running app's window
instead of starting another (`platform/SingleInstance.kt`).

New-file notifications (`notify/`) run while the desktop app runs, tray
included. Every 5 min polls the file list. Instant also subscribes to an ntfy
topic's JSON stream and checks on each message, with 15-minute polling as a
fallback. `SeenFiles` keeps the announced IDs; the first check only records a
baseline, and the app's own uploads are marked seen.

From 840dp wide (`TwoPaneMinWidth`), tablets and desktop windows show the file
list beside a details pane (`ui/components/FileDetails.kt`, shared by both
apps). Settings switch to two columns at the same width. Narrower screens keep
the phone layout. `AppFlowsTest.tabletTapShowsDetailsThenDownloads` forces
a 1280x800dp tablet on any emulator.

Tapping or clicking a file shows its details: in the pane on wide layouts,
full screen on Android phones. The details' Download saves the file to
Downloads/Gokapi, then asks whether to open it (Install on an APK downloads,
then shows the install sheet). Downloading a file again reuses the copy
already in Downloads/Gokapi when its name and size match, across restarts
too; on Android that only covers copies this install saved. Narrow desktop windows have no pane, so a click there still
downloads right away.

The main app uses `com.uzukip.gokapi`. The dev flavor uses
`com.uzukip.gokapi.dev`, has its own Android data, and installs side by side.

## Commands

Use the justfile recipes:

- `just dev`: install and launch the mainApp debug build
- `just check`: assemble, lint, and unit-test both flavors, plus the desktop
  tests; must pass before finishing a change
- `just build-apk` / `just build-apk-dev`: release APK into `_apk/`, uploaded
  through `~/docker/files/bin/share`
- `just dev-desktop`: run the desktop app from source
- `just desktop-mock`: run the desktop app against a fake server with sample
  files and throwaway settings, for checking layouts
- `just update`: build the AppImage, stage it in `_apk/`, and install it to
  `~/apps/gokapi-desktop.AppImage` with a launcher entry (`just uninstall`
  removes it)
- `just update-exe` (Windows): build the EXE installer, stage it, and swap the
  installed app's files in place, or run the installer on first install.
  Desktop recipes pass `-PdesktopOnly=true`, so Windows needs no Android SDK

## Emulator

The headless AVD `Spendr_Headless_API_36` works for this app too; see
spendr-kt's AGENTS.md for boot, install, and screenshot commands. Screenshots
are 788x2000; scale tap coordinates by about 1.2 for the 945x2400 display.

## Conventions

- Do not commit the real tailnet domain. It belongs in `local.properties`
  (git-ignored) or in the app's runtime settings.
- Versioning: `versionCode` increments per released build; `versionName` is
  `MAJOR.MINOR.PATCH`.
