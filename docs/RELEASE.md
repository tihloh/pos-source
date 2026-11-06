# POS Android: Signed releases and automatic updates

## Release architecture

The POS uses two public GitHub repositories:

- **`tihloh/pos-source`** — Kotlin/Android source, release signing, and source release assets.
- **`tihloh/pos`** — the public release channel used by installed POS apps.

Each repository publishes with **its own GitHub Actions `GITHUB_TOKEN`**.
There is **no cross-repository personal access token requirement**.

### 1. Build and publish a signed source release

On `tihloh/pos-source/main`, update `.release-version` with a higher
`VERSION_NAME` and an increasing Android `VERSION_CODE`. This runs
`Actions → Release APK`. Changes to `.github/workflows/release.yml` also
trigger the workflow; you can manually use **Run workflow** for a release
using explicit version inputs.

The release workflow:

1. Reads the intended version and restores the **existing** signing keystore.
2. Compiles the signed `POS-v<version>.apk` (package `com.tihloh.pos`).
3. Computes the file's `POS-v<version>.apk.sha256` checksum.
4. Retains both files in an Actions artifact named
   `POS-v<version>-signed-apk` (30 days, subject to repository limits).
5. Publishes both files as a **release in `tihloh/pos-source`** using its
   own short-lived workflow token.
6. Verifies the source release contains the signed APK and checksum.

For a stable public update, leave `prerelease` disabled. The public mirror
reads only the source repository's `/releases/latest` stable release.

### 2. Mirror the signed APK to the public POS release channel

The workflow
[`tihloh/pos/.github/workflows/mirror-release.yml`](https://github.com/tihloh/pos/blob/main/.github/workflows/mirror-release.yml)
runs when manually dispatched and twice per hour, at minutes 07 and 37 UTC.
It also runs when the mirror workflow itself changes on `main`.

It reads the latest published stable source release, checks that its APK and
SHA-256 checksum assets exist, downloads them, and verifies the checksum.
Then it uses the **`pos` repository's own `GITHUB_TOKEN`** to publish the
same signed APK as a `tihloh/pos` GitHub Release.

The mirror is idempotent, skips downgrades, and verifies that the Android
update endpoint `https://api.github.com/repos/tihloh/pos/releases/latest`
returns the mirrored tag and an APK asset.

**Do not add the old `POS_RELEASE_TOKEN` back to the workflow** merely to
restore cross-repository publishing. This architecture no longer uses it.
The unused secret may be retired through repository Settings.

### 3. Verify that users can detect the update

After the mirror workflow succeeds:

- Check [public POS Releases](https://github.com/tihloh/pos/releases).
- Check `https://api.github.com/repos/tihloh/pos/releases/latest`.
  Its `tag_name` must equal the new version, e.g. `v0.5.9`.
- Confirm the release is published, **not a draft or prerelease**, and
  includes `POS-v<version>.apk`.
- Test an installed earlier version via **POS → Settings → Check for app update**.
  The app can show an Android installer confirmation.

The current installed app uses `UpdateChecker.kt` to compare the public
`/releases/latest` tag with its installed `BuildConfig.VERSION_NAME`.
It checks on startup and periodically using WorkManager subject to network
and Android scheduling; forced checks bypass the 12-hour local cache.
A source commit or successful Gradle build **alone** does not create a
detectable app update.

## Signing credentials (source repository only)

Keep the following `pos-source → Settings → Secrets and variables → Actions`
secrets securely configured:

- `ANDROID_KEYSTORE_BASE64`: Existing release keystore, Base64 encoded.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

**Never replace the Android signing key to repair a GitHub publication error.**
Android updates require the same package name and signing identity, plus
a higher version code. Do not uninstall POS to fix an update: local inventory,
customer and sales data may be removed along with the app.

## Troubleshooting

| Symptom | Diagnosis / action |
| --- | --- |
| Release APK fails during signing | Inspect `pos-source → Actions → Release APK` and verify the four signing secrets |
| Signed source APK release exists but `pos` is still older | Run `pos → Actions → Mirror signed POS release → Run workflow` and inspect that job |
| Public mirror fails with 403 permissions | Check `tihloh/pos` Actions workflow permissions; the mirror requires `contents: write` in its **own** repo |
| Mirror fails on APK verification | Check source release assets and checksum; do not publish an unverified APK |
| App fails to detect published update | Confirm `pos/releases/latest`, an attached APK asset, installed version name, and GitHub API connectivity |
| Android refuses in-place installation | Check signing key, package ID, version code, and system install permission |

### Emergency manual publication

If the source signed release exists but the public mirror is unavailable:

1. Download **`POS-v<version>.apk`** from `tihloh/pos-source` Releases
   (or the signed Actions artifact). Do **not** use a debug APK.
2. In `tihloh/pos → Releases → Draft a new release`, use the same
   `v<version>` tag, attach the signed APK, and publish a **stable** release.
3. Confirm the `pos/releases/latest` endpoint exposes the tag and APK.
4. Fix the mirror workflow for subsequent automatic releases.

## History / recovery

- **v0.5.8**, 2026-10-09: Published successfully using the former
  cross-repository `POS_RELEASE_TOKEN` mechanism.
- **v0.5.9**, 2026-11-06: The original signed build succeeded, but
  cross-repository publication failed with **HTTP 401** at
  `Create GitHub Release`. The new source-release + mirror process
  then successfully published v0.5.9 to the public `pos` release channel.

Never regard a successful compilation alone as a complete deployment.
Verify both the **source release** and **public release** before telling
users an Android update is available.
