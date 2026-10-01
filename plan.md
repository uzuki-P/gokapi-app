# Plan: gokapi-android

A small native Android client for the private Gokapi service in
`~/dotconfig/_home/docker/files`. It replaces the current flow for files that
agents share:

> tap link → browser → Gokapi download page → Downloads → file manager → installer

with:

> tap link (or notification) → app → one confirm tap (or zero, for reinstalls)

The app also uploads files from the phone and gets a push notification when a
new file lands.

Status: M1 is partly done. The project builds, and the home screen opens a
settings screen with the Appearance, Theme color, and Haptics groups. Server
setup and credential storage are still to do.

## Base: copy spendr-kt, keep the shell, drop the domain

The project follows `~/projects/_sandbox/spendr-kt`: same toolchain, same
module layout, same flavors, same justfile, same expressive Material 3 look.
Copy files from spendr-kt and rename `com.spendr.app.kt` / `Spendr*`. Do not
add a git submodule or shared library. The two apps can drift later.

### Toolchain (pin to spendr-kt's versions at copy time)

From `spendr-kt/gradle/libs.versions.toml` and `app/build.gradle.kts`:

- Gradle 9.8.0 wrapper, AGP 9.4.1, Kotlin 2.4.20
- compileSdk 37, targetSdk 36, minSdk 26, Java/JVM 17
- Compose BOM 2026.09.00, material3 `1.5.0-alpha29`, with the compiler
  opt-ins for `ExperimentalMaterial3Api` and `ExperimentalMaterial3ExpressiveApi`
  in `kotlin { compilerOptions }`
- activity-compose, lifecycle-viewmodel-compose, navigation-compose,
  datastore-preferences, work-runtime-ktx, material-icons-extended,
  material-kolor
- JUnit + Robolectric + androidx.test core for JVM tests

Drop Room, KSP, and the vision-api `.env` plumbing. Add only what the
milestones below need: OkHttp for HTTP, kotlinx.serialization for the Gokapi
JSON, and the UnifiedPush connector in M5.

### Identity and flavors (spendr-kt ADR-0009 pattern)

| | mainApp | dev |
| --- | --- | --- |
| applicationId | `com.uzukip.gokapi` | `com.uzukip.gokapi.dev` |
| Launcher name | Gokapi | Gokapi (dev) |
| Icon | normal | DEV badge, as in spendr-kt `src/dev/res` |

The package name is a placeholder. Change it before the first install, since
changing it later loses data and installer ownership (see M3).

Release signing works like spendr-kt's: `keystore.properties` at the repo root
(git-ignored), with a debug-signing fallback.

### What to copy from spendr-kt

| spendr-kt file | Becomes | Changes |
| --- | --- | --- |
| `ui/theme/Theme.kt` | `ui/theme/Theme.kt` | Rename `SpendrTheme` → `GokapiTheme`. Remove `SpendrColors` (expense/savings are Spendr concepts). Keep `DEFAULT_SEED`, `PRESET_SEEDS`, the expressive `Shapes`, `MotionScheme.expressive()`, the wallpaper/seed branch, and the comment about keeping composable call structure independent of settings. Choose a new default seed. |
| `ui/theme/Type.kt` + `res/font/google_sans_flex.ttf` + `assets/licenses/google_sans_flex_OFL.txt` | same | Rename the families |
| `ui/components/Expressive.kt` | same | Keep `Corners`, `segmentCorners`, `SegmentedGroup`, `MorphSurface`, `MorphShape`, `MorphingBadge`, `EmptyState`. Drop `AnimatedAmount`. |
| `ui/components/Bouncy.kt`, `PressScale.kt`, `TopBar.kt` | same | Rename `SpendrTopBar` → `GokapiTopBar` |
| `ui/components/CategoryIconBadge.kt` + `MciGlyphs.kt` + `res/font/mci.ttf` | `ui/components/IconBadge.kt` | Keep `MciIcon` and the badge. Rename `CategoryIconBadge` → `IconBadge`, drop the category-entity overload and `SectionHeader` unless a screen needs it. |
| `ui/settings/SettingsComponents.kt` | same | Unchanged (`GroupLabel`, `SettingsRow`) |
| `ui/settings/SettingsScreen.kt` | same | Keep the Appearance group (`ConnectedChoices` for ThemeMode), Theme color group (ColorSource + `SwatchGrid` with the morphing cookie badge), Haptics group, and `VibrationStrengthDialog` with its throttled preview. Drop Spending, Data, Developer. Add the groups listed under M1 and later milestones. |
| `data/settings/SettingsRepository.kt` | same | Keep `ThemeMode`, `ColorSource`, `VibrationStrength` and their keys. Drop backup fields. Rename the store to `gokapi_settings`. |
| `platform/SpendrVibrator.kt` | `platform/Haptics.kt` | Rename the class; keep `Settings.vibrationMs` |
| `MainActivity.kt` | same | Keep `LocalVibrate`, `enableEdgeToEdge`, `requestPeakRefreshRate`, `singleTask`, and the `onNewIntent` deep-link handoff |
| `AppContainer.kt`, `SpendrApplication.kt` | `AppContainer.kt`, `GokapiApplication.kt` | Manual DI (spendr-kt ADR-0005). Holds settings, haptics, the Gokapi client, and the credential store. |
| `justfile` | `justfile` | Already done; see below |
| `AGENTS.md` | `AGENTS.md` | Rewrite for this app; keep the emulator workflow and validation sections |

Copy spendr-kt's ADRs 0005 (manual DI), 0006 (dynamic color), and 0009
(flavors) as this project's 0001 to 0003, reworded for this app.

## Gokapi facts the app depends on

Checked against the running instance's OpenAPI spec
(`http://127.0.0.1:53842/apidocumentation/openapi.json`, Gokapi v2.2.4):

- Auth is an `apikey` header. Each key has its own permissions:
  `PERM_VIEW`, `PERM_UPLOAD`, `PERM_EDIT`, `PERM_DELETE`, `PERM_REPLACE`,
  `PERM_MANAGE_FILE_REQUESTS`, `PERM_MANAGE_LOGS`, `PERM_MANAGE_USERS`,
  `PERM_API_MOD`.
- `GET /api/files/list` returns all unexpired files (or `null` when empty).
  It needs VIEW.
- `GET /api/files/list/{id}` returns one file's metadata.
- `GET /api/files/download/{id}` downloads the file without counting it as a
  download by default. It can also return a pre-signed URL that is valid for
  30 seconds. The spec says it needs a DOWNLOAD permission that the
  permission enum doesn't list. Check this against a real key in M2.
- `POST /api/files/add` is a single-request upload (what `bin/share` uses).
- `POST /api/chunk/add` + `POST /api/chunk/complete` is chunked upload, with
  a 5 MB minimum chunk size except for the last chunk. `complete` takes
  `uuid`, `filename`, `filesize`, `contenttype`, `allowedDownloads`,
  `expiryDays`, `password`, `nonblocking`.
- `GET /api/info/config` returns server upload limits (needs UPLOAD).
- `DELETE /api/files/delete` deletes a file (needs DELETE).
- File fields the UI uses: `Id`, `Name`, `Size`, `SizeBytes`, `ContentType`,
  `UploadDate`, `ExpireAt`, `UnlimitedTime`, `DownloadsRemaining`,
  `UnlimitedDownloads`, `HotlinkId`, `UrlDownload`, `UrlHotlink`.
- Share links have the form `https://files.<domain>/d?id=<Id>`, and image
  hotlinks have the form `/h/<HotlinkId>`.

Network boundary: the `files.` host is only reachable on the tailnet, so the
phone needs Tailscale connected. The app should say so plainly when a request
fails with a DNS or connect error instead of showing a generic error.

The dotconfig README says not to commit the real tailnet domain. The app
must not commit it either. Users enter the server URL at setup, and the
deep-link host comes from `local.properties` (see M4).

## Milestones

Each milestone is a vertical slice you can install and use. Validate both
flavors with `just check` before closing one.

| # | Milestone | Blocked by |
| --- | --- | --- |
| M1 | Skeleton: theme, settings (Appearance, Theme color, Haptics), server setup | none (partly done: server setup left) |
| M2 | File list + open/download | M1 |
| M3 | APK install via PackageInstaller, silent reinstall | M2 |
| M4 | Open agent links in the app | M2 |
| M5 | Push notifications for new uploads | M2, dotconfig ntfy service |
| M6 | Upload from the phone (picker + share sheet) | M2 |
| M7 | Polish: delete, expiry display, image previews, self-update | M2 to M6 |

### M1: Skeleton

- Gradle project, flavors, signing, icons, and the copied theme and
  components from the table above.
- Navigation: one `NavHost` with `files` (an empty state until M2) and
  `settings`.
- Settings screen, in this order:
  1. **Server** (new): server URL and API key, with a "Test connection" button
     that calls `GET /api/info/version` and then `GET /api/files/list`, and
     reports which permissions worked.
  2. **Appearance**: System / Light / Dark connected toggle buttons, as in
     spendr-kt.
  3. **Theme color**: Default / Wallpaper / Custom, plus the swatch grid,
     as in spendr-kt.
  4. **Haptics**: vibration strength row + dialog, as in spendr-kt.
  5. Version footer: `gokapi-android v<versionName>`.
- Credential storage: keep the API key out of plain DataStore. Encrypt it
  with an AES-GCM key held in Android Keystore and store the ciphertext in
  DataStore. Don't use `EncryptedSharedPreferences` (deprecated). Exclude the
  file from backup with `dataExtractionRules`.
- Key: create a dedicated `android` key in the Gokapi admin UI. It starts
  with VIEW (+ DOWNLOAD if M2 needs it). Add UPLOAD in M6 and DELETE in M7.
  Never reuse the agent key from `~/docker/files/secrets/api-key`.

Done when: both flavors install side by side, all three spendr-kt settings
groups work and persist, and "Test connection" passes on the phone over
Tailscale.

### M2: File list + open

- `GokapiClient` (OkHttp + kotlinx.serialization) with `list()`, `get(id)`,
  `download(id, to)`. Unit-test the JSON parsing against a captured
  `files/list` response with secrets and names scrubbed.
- Files screen: newest first, pull to refresh. Each row shows a type icon
  (`MciIcon` by content type or extension), the name, size, upload age, and
  the expiry ("expires in 6 d", "unlimited"). Use `SegmentedGroup` and
  `MorphSurface` rows so the list matches the settings look.
- Tap a row: download into `cacheDir/downloads/<Id>/<Name>` with a
  determinate progress indicator, then:
  - `.apk` → M3 path (until M3 lands, use `ACTION_VIEW` with
    `application/vnd.android.package-archive`)
  - anything else → `ACTION_VIEW` through a `FileProvider` URI with the
    file's `ContentType`, falling back to a chooser
- Long-press: Share (`ACTION_SEND` of the cached file), Copy link, Save to
  Downloads (MediaStore).
- Clear the cache on a size cap (for example 500 MB, oldest first) when the
  app starts.

### M3: APK install

- `REQUEST_INSTALL_PACKAGES`. On first use, if
  `packageManager.canRequestPackageInstalls()` is false, open
  `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package and explain
  why in one sentence.
- Install with a `PackageInstaller` session, not `ACTION_VIEW`. Stream the
  cached APK into the session, then commit with a `PendingIntent` that
  receives the status. Handle `STATUS_PENDING_USER_ACTION` by launching the
  confirm intent.
- On API 31+, call `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`. Android
  skips the confirm dialog when this app is the installer of record for an
  already-installed package. Starting with Android 14, update ownership also
  matters. Effect: the first install of `spendr-kt` dev asks for confirmation,
  and every rebuild after that installs silently.
- Before installing, read the APK's package name and version with
  `getPackageArchiveInfo`. Show "Update Spendr (dev) 0.4.1 → 0.4.2" or
  "Install …". If the signing certificate doesn't match the installed
  package, show "signature differs, uninstall first?" instead of letting the
  installer fail with a cryptic error. This happens with spendr-kt release
  builds when `keystore.properties` is missing, because those builds fall
  back to debug signing.
- Post an "Installed X" notification, or report the error, from the status
  receiver so the user sees the result if they leave the app.

Done when: a `just build-apk-dev` from spendr-kt can be installed from the
list in one tap, and the second build installs with no dialog.

### M4: Open agent links in the app

- Intent filter for `https://<files host>/d` with `BROWSABLE`. The host comes
  from `local.properties` (`gokapi.filesHost=files.…`) through a manifest
  placeholder, so the domain stays out of git. If the property is missing,
  the build should skip the filter.
- Without App Links verification, Android 12+ won't route the link to the
  app until the user approves it under the app's "Open by default" → "Add
  links". The setup screen should detect this with
  `DomainVerificationManager.getDomainVerificationUserState` and deep-link the
  user to that settings page.
- Optional: serve `/.well-known/assetlinks.json` from the dev-router Caddy
  route for `files.` so the link verifies automatically. This is a dotconfig
  change and needs the release signing fingerprint. Skip it unless the manual
  toggle turns out to be annoying.
- On a link: parse `id`, call `get(id)`, show a bottom sheet with name, size,
  and an Open/Install button. Also handle `/h/<hotlinkId>` by matching it
  against the list.

### M5: Push notifications

Gokapi has no webhooks, so the notification has to come from whatever does
the upload. Today that's `bin/share`.

Recommended design: self-hosted ntfy + UnifiedPush.

- **Server (dotconfig change).** Add an ntfy Quadlet next to Gokapi
  (`~/dotconfig/_home/docker/ntfy/`), bound to loopback and routed through
  dev-router as `ntfy.` on the tailnet, following the `files/` layout:
  pinned image, `justfile`, README, and a `just route` recipe. Use access
  control with a write token for `bin/share` and a read token for the phone.
- **Publisher.** After a successful upload, `bin/share` POSTs to the topic
  (for example `gokapi-uploads`) with a JSON body containing `Id`, `Name`,
  `Size`, `ContentType`, and the download URL. A failed publish must not
  fail the upload. Gate it on `NTFY_URL` being set in `.env` so the helper
  still works without ntfy.
- **Phone.** Install the ntfy Android app as the UnifiedPush distributor and
  point it at the self-hosted server. It keeps the connection open, so this
  app doesn't need a foreground service. This app uses
  `org.unifiedpush.android:connector`, registers for the topic, and turns
  each message into a notification:
  - title: file name; text: size + "from agent"
  - tap: open the M4 bottom sheet for that `Id`
  - action button: "Install" for APKs (runs M3 directly) or "Open"
- **Fallback.** If no distributor is installed, a `PeriodicWorkRequest`
  (15-minute minimum) diffs `files/list` against the last seen `Id`s and
  notifies about new files. This is slow but needs no server change. Setting:
  Notifications → "Instant (ntfy)" / "Every 15 min" / "Off".
- Ask for `POST_NOTIFICATIONS` on Android 13+ when the user turns
  notifications on, not on first launch.
- One notification channel per kind: "New uploads", "Install results",
  "Upload progress", so each can be muted on its own.

Not FCM. It would need Firebase, a Google project, and a public endpoint,
which goes against keeping this tailnet-only.

Open question: should uploads from the phone (M6) also publish? Probably
not, since you already know about them.

### M6: Upload from the phone

- Key needs `PERM_UPLOAD`. The "Test connection" check reports when it's
  missing.
- Entry points:
  - FAB on the files screen → `ActivityResultContracts.OpenMultipleDocuments`
  - Share sheet target: `ACTION_SEND` and `ACTION_SEND_MULTIPLE` for `*/*`,
    on a lightweight `ShareActivity` that shows a confirm sheet and closes
- Confirm sheet: file name(s), total size, expiry days (default from
  settings, 7 like `bin/share`), allowed downloads (default unlimited),
  optional password. Use spendr-kt-style connected toggle buttons for expiry
  presets: 1 d / 7 d / 30 d / Never.
- Transfer: chunked upload (`/api/chunk/add` with 5 MB+ chunks, then
  `/api/chunk/complete`). Chunks give real progress and avoid any request
  body limit in Caddy. Run it in a WorkManager job with
  `setForeground` and a progress notification so it survives leaving the
  app. Read the content URI as a stream; don't copy it to cache first.
- When it finishes: a notification with "Copy link" and "Share link"
  actions, and the file at the top of the list. Copy the `/h/` hotlink for
  images and the `/d?id=` link for everything else, using the same rule as
  `bin/share`.
- Settings group **Uploads**: default expiry days, default allowed
  downloads, "copy link automatically when done".

### M7: Polish

- Delete (swipe or long-press, needs DELETE permission) with a confirm
  dialog, because deletion is permanent.
- Image rows show the hotlink as a thumbnail.
- Search/filter by name and by type chips (APK, images, documents).
- Self-update: the app can install new versions of itself through M3, since
  `just build-apk` uploads to Gokapi. Show "Update available" when the list
  holds a newer `gokapi-android-*.apk` than the running `versionName`.
- Haptics: call `LocalVibrate` on install success, upload finished, and pull
  to refresh, the way spendr-kt uses it on the keypad and charts.

## Justfile

`justfile` is already in this folder. It mirrors spendr-kt's:

- `dev`: install mainApp debug and launch it
- `check`: assemble, lint, and unit-test both flavors
- `build-apk` / `build-apk-dev`: release build copied into `_apk/` with a
  timestamped name, then uploaded with `~/docker/files/bin/share`

Gradle tasks and the package name in the recipes match the identity table
above. Update both together if the package name changes.

## Risks and open questions

- **DOWNLOAD permission.** The OpenAPI spec mentions it, but the enum doesn't
  list it. If `/api/files/download/{id}` rejects the key, fall back to the
  public `/d?id=` flow: fetch the page and follow its download URL. Confirm
  this in M2 before building on either path.
- **Silent reinstall depends on installer of record.** APKs already
  installed through the browser are owned by the package installer, so the
  first install through this app still prompts. On Android 14+, an app with
  update ownership set by another installer prompts every time until it is
  reinstalled through this app.
- **Tailscale off.** Every feature depends on the tailnet. The UI needs a
  clear "Can't reach files host. Is Tailscale on?" state.
- **Gokapi upgrades.** `~/docker/files` pins v2.2.4. Check the API spec
  again when bumping the image, because field names have changed between
  major versions.
- **Package name.** `com.uzukip.gokapi` is a placeholder. Decide before the
  first install.
