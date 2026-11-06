# POS Android signed releases and in-app updates

## How releases work

The Android app source and release workflow live in `tihloh/pos-source`.
Signed, public APK releases are published into **a different repository**,
`tihloh/pos`, so the installed application's update checker can find them.

1. Change `.release-version` on `pos-source/main` with a newer
   `VERSION_NAME` and strictly increasing `VERSION_CODE`. This triggers
   `Actions → Release APK`. You can also use **Run workflow** with matching
   version name/code without changing the tracked file.
2. GitHub Actions restores the existing Android signing key, compiles the
   release APK, and stores `POS-v<version>-signed-apk` as a workflow artifact.
   This artifact is retained for 30 days (subject to repository policy).
3. It authenticates to `tihloh/pos` using the GitHub Actions secret
   `POS_RELEASE_TOKEN`, then creates a public, non-prerelease GitHub Release
   containing `POS-v<version>.apk`.
4. The workflow verifies that the new release includes the APK and that the
   `/releases/latest` endpoint reports its tag. **A successful APK build
   is not proof that the release was published.**

### Android signing (one-time setup)

Installable app updates must be signed using **the same key** as the currently
installed app. Never regenerate the key to fix publishing authentication.

The `pos-source` Actions secrets are:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded Android signing keystore.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `POS_RELEASE_TOKEN`: an independent GitHub token that **can publish
  releases in `tihloh/pos`**.

Do not paste any of these secret values into code, AI memory, issues, or
GitHub workflow logs.

### Creating or renewing POS_RELEASE_TOKEN

The workflow's built-in `GITHUB_TOKEN` is scoped to `pos-source` and
cannot automatically create releases in the separate `tihloh/pos` repo.

1. In GitHub **Settings → Developer settings → Personal access tokens →
   Fine-grained tokens**, create a token owned by `tihloh`.
2. Grant repository access specifically to `tihloh/pos` with
   **Contents: Read and write**. Approve organization policies if applicable.
   Set an expiration and track the renewal date securely.
3. In `tihloh/pos-source → Settings → Secrets and variables → Actions`,
   **replace** `POS_RELEASE_TOKEN` with this token.
4. Run `Release APK` again. Never commit the token to the repository.

When a token is expired, revoked, or missing permissions, GitHub may return
HTTP 401/403. The workflow now produces a clear authentication error while
preserving the signed APK as an Actions artifact.

### If release publication fails: manual recovery

If the **signed build** and **Preserve signed APK** steps succeeded but
**Verify release publishing credentials** or **Create GitHub Release** failed:

1. Open the failed `Release APK` run under `pos-source → Actions`.
2. Download its `POS-v<version>-signed-apk` artifact and extract the ZIP.
3. In `tihloh/pos → Releases → Draft a new release`, create the tag
   `v<version>`, attach the extracted **signed**
   `POS-v<version>.apk`, do **not** select prerelease, then publish.
4. Verify that `https://api.github.com/repos/tihloh/pos/releases/latest`
   reports the new tag and includes an APK asset.
5. Open the installed POS app and check for updates. The app must have an
   older version; for example, `0.5.8` detects `v0.5.9`.

This manual recovery is not a substitute for renewing the token. Fix
`POS_RELEASE_TOKEN` so future updates publish automatically.

## Android update discovery and installation

The current app checks
`https://api.github.com/repos/tihloh/pos/releases/latest`, which must
return a **published non-prerelease** release. A source commit, Gradle build,
workflow artifact, draft, or prerelease alone is **not detectable** as the
latest stable app update.

The app compares the GitHub release tag with the installed version name.
When a newer version exists, it uses Android's PackageInstaller to stage
the signed APK internally. The app checks on startup and can run periodic
background checks subject to Android WorkManager scheduling and connectivity.
A **forced check** bypasses its 12-hour local throttle; Android may still
require confirmation for sideloaded APK installation.

For successful in-place upgrades, the APK must have the same Android package
ID (`com.tihloh.pos`), be signed by the existing key, and use a version code
greater than the installed APK. Do not uninstall the app as an update fix:
doing so can remove its local POS database.

## Common symptoms

| Symptom | What to check |
| --- | --- |
| GitHub build succeeds but Android finds no update | Did the **Release APK** job complete successfully and publish to `tihloh/pos`? |
| `Create GitHub Release` returns HTTP 401 | Renew `POS_RELEASE_TOKEN`; verify correct owner and repo permissions |
| Signed APK exists in workflow artifacts but not Releases | Manually publish the artifact to `tihloh/pos`, then repair the token |
| App says no update even when new release exists | Confirm installed version, stable `/releases/latest` tag, APK asset, network access, and GitHub API rate limits |
| Android refuses to install new APK | Compare package ID, signing certificate, and monotonic version code |

### Release verification checklist

- `pos-source` code and intended `.release-version` are on `main`.
- `Release APK` succeeded, **not only** `Build APK`.
- Signed APK file is present in **public** `tihloh/pos` release assets.
- `/releases/latest` returns the intended version tag.
- Update is detected and installed over an existing POS installation
  **without deleting local records**.
