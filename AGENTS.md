# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

## What this project is

gokapi-android is a native Android client (Kotlin + Jetpack Compose, Material 3
Expressive) for the private Gokapi service in `~/dotconfig/_home/docker/files`.
`plan.md` holds the roadmap and the Gokapi API facts the app depends on.

The project shell, theme, components, and settings screen are copied from
`~/projects/_sandbox/spendr-kt`. When a shared piece needs a fix, check
whether spendr-kt has the same bug.

The main app uses `com.uzukip.gokapi`. The dev flavor uses
`com.uzukip.gokapi.dev`, has its own Android data, and installs side by side.

## Commands

Use the justfile recipes:

- `just dev`: install and launch the mainApp debug build
- `just check`: assemble, lint, and unit-test both flavors; must pass before
  finishing a change
- `just build-apk` / `just build-apk-dev`: release APK into `_apk/`, uploaded
  through `~/docker/files/bin/share`

## Emulator

The headless AVD `Spendr_Headless_API_36` works for this app too; see
spendr-kt's AGENTS.md for boot, install, and screenshot commands. Screenshots
are 788x2000; scale tap coordinates by about 1.2 for the 945x2400 display.

## Conventions

- Do not commit the real tailnet domain. It belongs in `local.properties`
  (git-ignored) or in the app's runtime settings.
- Versioning: `versionCode` increments per released build; `versionName` is
  `MAJOR.MINOR.PATCH`.
