# Build

No Gradle: `build.ps1` drives `aapt2`, `javac`, `d8`, `zipalign` and `apksigner` directly.

## Requirements

| Tool | Version | Notes |
| --- | --- | --- |
| Android SDK | build-tools 35.0.0 + platforms/android-35 | `sdkmanager "build-tools;35.0.0" "platforms;android-35"` |
| JDK | 11 or newer | `javac`, `java`, `keytool` on `PATH` |
| Python | 3.x | used only to inject `classes.dex` into the APK |
| adb | any recent | optional, for installing |

## Build

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Sdk "C:\Android\sdk"
```

Optional parameters: `-VersionName 1.2.0 -VersionCode 11 -OutName Counter-Eyes`.

Output:

```
build-<yyyyMMdd-HHmmss-fff>/Counter-Eyes-1.1.0.apk
SHA256=...
```

If `-Sdk` is omitted the script tries `%ANDROID_HOME%`, `%LOCALAPPDATA%\Android\Sdk`,
`C:\Android\sdk`.

## Signing

- First build generates `counter-eyes.keystore` (alias `countereyes`, store/key password
  `counter-eyes`) if missing. **Keep this file.** Android only accepts an update if the signature
  matches; losing it means uninstall + data loss.
- The keystore and all `*.apk` files are git-ignored. Never commit them.
- For a real release, replace the test key with your own and publish the SHA256 of the APK.

## Install / update

```bash
adb install -r build-<timestamp>/Counter-Eyes-1.1.0.apk
adb shell am start -n com.countereyes.app/.MainActivity
```

`install -r` keeps settings and the stored POS token. `-r -d` downgrades, `uninstall` wipes data
(including the Keystore-encrypted credential).

## Reboot behaviour

The app has no boot receiver, foreground service or kiosk mode. Configure your tablet to reopen it
after a reboot if you need unattended operation (e.g. a launcher, Tasker, or device-owner kiosk
tools). That behaviour is intentionally not bundled.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| `Eksik araç: .../aapt2.exe` | wrong `-Sdk`, or build-tools 35 not installed |
| `javac: invalid target release` | JDK older than 11 |
| App shows setup screen on every launch | `CE.save` failed — check the message shown under the buttons |
| QR never appears | review link empty, or no closed sale matched the generic JSON mapping |
| Eyes idle, no camera | camera permission denied; grant it in Android settings |
