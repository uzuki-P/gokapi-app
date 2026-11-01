# Gokapi Android

A native Kotlin and Jetpack Compose client for your private Gokapi server.
The app uses Material 3 Expressive shapes, buttons, typography, and motion.

You can browse and search files, download and open them, share cached files,
save them to Downloads, and install APKs. Long-press a file for its actions.
Uploads work through the document picker or Android share sheet and continue
in WorkManager after you leave the screen. New upload notifications support
UnifiedPush through ntfy, with a 15-minute polling fallback.

## Connect your phone

1. Connect Tailscale.
2. Create a dedicated `android` API key in Gokapi. Grant VIEW and DOWNLOAD for
   browsing and opening files, UPLOAD for phone uploads, and DELETE for deletion.
   The key's Gokapi user also needs access to files uploaded by other users.
3. Enter the private HTTPS server URL and key under Settings, Server.
4. Tap Test connection. It checks the server version, VIEW, and UPLOAD access.
   DOWNLOAD and DELETE are checked when you use those actions.
5. Open an APK. If prompted, allow Gokapi under Android's Install unknown apps
   settings, return to Gokapi, and tap Install.

The first APK installation asks for Android confirmation. Compatible later
updates can install silently when Gokapi owns the installation. Android can
still require confirmation for apps owned by another installer or apps with
an older target SDK. A signature mismatch opens an explanation and a separate
uninstall action. Uninstalling removes that app's local data.

API keys and upload passwords use AES-GCM encryption with an Android Keystore
key. WorkManager receives encrypted upload passwords and loads the API key
from the credential store. Credential, push registration, and worker files are
excluded from backups and device transfers.

## Away from Tailscale

The server can publish its API, and only its API, through a relay: a
Cloudflare Worker that checks a token for each device and forwards the app's
API calls through a Cloudflare Tunnel. The tunnel has no public hostname. Each
device also gets its own Gokapi key. See `~/docker/files/README.md` for the
server setup.

1. Pair the device on the files web UI's Devices page, or run
   `just pair <device>` in `~/docker/files`. Either shows a pairing link and QR
   code. The link is a credential: don't share it.
2. In the app's Settings, under Away from Tailscale, tap Scan QR code and point
   the camera at the code. The camera permission is asked for only then.
   Alternatively, paste the link into the Pairing link field and tap Use link,
   or open the link on the phone. On desktop, paste the link into the field.
3. Check the server and relay hosts, then tap Pair. Pairing replaces the saved
   server URL and API key, then runs Test connection. On the tailnet, the test
   also checks the relay.

Requests try the tailnet URL first. When it doesn't answer within 4 seconds,
they use the relay for the next 2 minutes, then try the tailnet again. Share
links, saved downloads, and new-file tracking keep using the tailnet URL, so
links you copy never point at the public host. Turn off in Settings removes the
relay. `just unpair <device>` on the server revokes the device.

## Open shared links

Add this property to the existing, ignored `local.properties` before building:

```properties
gokapi.filesHost=files.your-private-domain
```

Use a hostname without a scheme or path. If the property is missing, the build
omits the link intent filter. Settings, Open links opens Android's Open by
default page and reports whether the files host is approved. `/d?id=...` and
`/h/...` links resolve against the server configured in the app. Links for a
different server are rejected.

## Notifications

The private ntfy companion is in `~/dotconfig/_home/docker/ntfy`.
Its README explains installation, read and write accounts, and device endpoints.
The Gokapi upload helper publishes to each registered endpoint after a
successful upload. A notification failure does not fail the upload.

Install ntfy on the phone, configure its private server and android account,
and select Instant under Gokapi's Notifications settings. Copy the device
endpoint into `~/docker/files/secrets/ntfy-endpoints`. Each line is one endpoint.
The file must have mode 0600. Android asks for notification permission when you
turn notifications on. Every 15 min uses polling without a distributor.
The first poll records existing files without notifying about the whole list.

The desktop app notifies while it runs, including from the tray. Settings,
New file notifications, offers Every 5 min (the default) or Instant. Instant
subscribes to an ntfy topic with the android account (`android:<password>`) or
an access token. Use the regular topic set as `NTFY_URL` in the files
service's `.env`, such as `https://<ntfy host>/gokapi-uploads`, or add a
desktop topic URL such as `https://<ntfy host>/up<random>` to
`ntfy-endpoints`.

## Build and validate

`mainApp` uses `com.uzukip.gokapi`. The dev flavor uses
`com.uzukip.gokapi.dev` and installs alongside it with separate Android data.

```bash
just dev
just check
```

`just check` assembles, lints, and runs JVM tests for both flavors. For the
API 36 emulator, run the instrumented tests with:

```bash
./gradlew :app:connectedDevDebugAndroidTest
```

The tests use a private in-process HTTPS fixture server with a test-only client
trust configuration. They exercise Compose file actions, links, real Keystore
storage, MediaStore, WorkManager uploads, signature mismatches, and a real first
install followed by a silent update. APK fixtures are generated under `build/`
and use `com.uzukip.gokapi.installfixture`. No real server credentials are needed.

Before publishing a release, build both release APKs and test their cold startup.
This checks the optimized APKs, including WorkManager's database initialization.
The script installs an update and preserves the emulator's app data.

```bash
./gradlew :app:assembleMainAppRelease :app:assembleDevRelease
python3 scripts/check-release-startup.py --serial emulator-5554
python3 scripts/check-release-startup.py --serial emulator-5554 --flavor dev
```

Release recipes `just build-apk` and `just build-apk-dev` stage APKs in `_apk/`
and upload them through the existing files helper. Set `keystore.properties`
for release signing. Without it, release builds use debug signing.

The roadmap and verification status are in [plan.md](plan.md).

## License

MIT, see [LICENSE](LICENSE). Bundled fonts keep their own licenses, listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
