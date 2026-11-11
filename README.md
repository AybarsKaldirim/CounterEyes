# Counter Eyes

Friendly robot eyes for the tablet that sits on your counter. The eyes look at the first person in
front of them, greet them with synthesized robot chatter and on-screen captions, react with green
hearts when eye contact lasts, and — when a sale is closed in your POS — show a QR code that asks the
customer for a review.

Runs entirely on the tablet: no PC, no local server, no open port. The POS is read-only.

- Android 7.0+ (API 24), landscape, camera on the front
- Tested on Samsung Galaxy Tab A11 (SM-X130), Android 16
- Works with **Simpra out of the box**, and with **any other POS/back-office** through a generic
  HTTP/JSON adapter (bring your own "last closed sales" endpoint)

## Install (no build required)

1. Download `Counter-Eyes-<version>.apk` from [Releases](../../releases).
2. Allow installing unknown apps for your file manager/browser on the tablet.
3. Install, open, and complete the setup screen (POS type, credentials, review link).
   The settings screen is always reachable from the ⚙ button in the top-right corner.

The APK is signed with a project test key. If you rebuild with your own key, uninstall the old copy
first (data — including the stored token — is removed).

## Build from source

Requirements: JDK 11+ (`javac`, `java`, `keytool`), Android SDK with `build-tools;35.0.0` and
`platforms;android-35`, Python 3 on `PATH`. Gradle is not used.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Sdk "C:\Android\sdk"
adb install -r .\build-<timestamp>\Counter-Eyes-1.1.0.apk
```

Details and troubleshooting: [docs/BUILD.md](docs/BUILD.md).
First build creates `counter-eyes.keystore` next to `build.ps1` — keep it, otherwise updates cannot
be installed over the previous version.

## Configure

Setup screen fields (also documented with JSON examples in [docs/CONFIG.md](docs/CONFIG.md)):

| Field | Meaning |
| --- | --- |
| POS type | `Simpra`, `Generic HTTP/JSON`, or `Demo` |
| API address | Simpra endpoint (default `https://api.simpra.app/checks`) or your own JSON URL |
| Access token / header | Stored encrypted with Android Keystore; never in the APK |
| Items path | Dotted path to the array of closed sales, e.g. `data.checks` (empty = root array) |
| Closed-at field | Timestamp field; epoch seconds, milliseconds or ISO-8601 |
| Id field / amount field | Record id, optional amount (used for 0-value filtering) |
| Review link | Your Google review URL; empty = no QR step |
| Poll interval | 2–60 s, default 5 s |
| 0-value sales | Show QR for free/comped sales or skip them |

"Test connection" runs the same request once and reports how many closed records it saw.

## How it works

```
Android Activity
  └── local WebView origin (appassets.androidplatform.net)
        ├── Canvas eyes + gaze animation
        ├── MediaPipe FaceLandmarker (bundled model + wasm)
        ├── WebAudio robot voice + captions
        └── QR rendering
  └── Java poller → your POS (read-only HTTP GET) → "sale closed" event → QR
```

- Target selection keeps the first face it saw; a bigger face later cannot steal the lock.
- Eye contact for 650 ms after the initial lock shows hearts for 2 s (blinks tolerated 280 ms).
- Idle gestures (wink/curious/giggle/sleepy) pause while a face, a conversation or the QR is active.
- Audio is suspended after 0.6 s of silence.

## Privacy

- Camera frames are processed on the device; nothing is uploaded.
- No biometric identification — face landmarks are reduced to "where is the person looking".
- The POS credential and header value are encrypted with a hardware-backed Keystore key and are
  excluded from backups (`android:allowBackup="false"`).
- The app never writes to your POS: no sale creation, no cancellation.

## Known limitations

- No foreground service, boot receiver or kiosk launcher: after a reboot you must open the app.
- Long-run thermal behaviour and crowded-room target switching depend on the device.
- A local HTTP POS endpoint is allowed (cleartext) because many back-office systems run on the LAN;
  prefer HTTPS or a tunnel when the endpoint is reachable from the internet.

## License

MIT — see [LICENSE](LICENSE) and [NOTICE](NOTICE) for bundled third-party components
(MediaPipe FaceLandmarker, qrcode.js).
