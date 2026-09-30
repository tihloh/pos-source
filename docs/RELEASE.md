# Release setup

The repository includes **Actions → Release APK → Run workflow** as the release button.

## One-time signing setup

Android updates must be signed with the same key as the installed app. Keep the keystore permanently and back it up securely.

Generate a key locally:

```bash
keytool -genkeypair -v -keystore pos-release.jks -alias pos -keyalg RSA -keysize 4096 -validity 10000
```

Create these GitHub repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

### Encode the keystore on PowerShell

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("pos-release.jks")) | Set-Clipboard
```

Paste the clipboard value into `ANDROID_KEYSTORE_BASE64`.

## Release

1. Open **Actions**.
2. Select **Release APK**.
3. Click **Run workflow**.
4. Enter `versionName`, for example `0.1.1`.
5. Enter an increasing `versionCode`, for example `2`.
6. Run it.

The workflow builds a signed APK and creates a GitHub Release containing `POS-v<version>.apk`.

## In-app updates

The app checks the repository's latest public GitHub Release at startup, throttled to once every 12 hours. If the release tag is newer than the installed `versionName`, the user sees an update prompt. Tapping **Update** opens the APK asset (or the release page if no APK asset exists).

The app does not silently install updates. Android requires user approval for sideloaded APK installation.
