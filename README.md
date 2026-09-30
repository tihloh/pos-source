# POS Android

Offline-first Android POS and inventory app focused on fast scanner-driven workflows.

## Current foundation

- Kotlin + Jetpack Compose
- PIN-protected startup
- Android biometric/device-credential unlock
- Camera barcode/QR scanning with CameraX + ML Kit
- Room database foundation
- Product, supplier, sales, payment, inventory-ledger and inventory-period entities
- Open Food Facts and Open Products Facts lookup service
- ESC/POS printer abstraction ready for Bluetooth, USB and LAN/TCP implementations
- GitHub Actions debug APK builds
- One-click manual **Release APK** workflow
- Signed APK release support
- Automatic GitHub Release update check in the app

## Main navigation

- POS
- Sales
- Inventory
- Products
- More

The scanner action is contextual: POS scanning, inventory scanning and product scanning use the same scanner foundation.

## Inventory principle

Inventory is ledger based. Sales, receiving, returns, damages and adjustments should create inventory transactions instead of silently overwriting a product quantity. `stockCache` is only a performance cache; the ledger is the auditable source of truth.

## Planned implementation order

1. Product create/edit + barcode lookup review screen
2. POS cart and quantity handling
3. Checkout/payment types/change calculation
4. Atomic sale + stock deduction transaction
5. Sales history and receipt detail/reprint
6. ESC/POS printer implementations
7. Scanner-first stock receiving and adjustment
8. Physical inventory counting and variance approval
9. Flexible inventory periods
10. Supplier receiving flow
11. Backup/restore, reports and settings

## Build

The GitHub workflow builds the debug APK automatically on pushes and pull requests. You can also use **Actions → Build APK → Run workflow**.

The project intentionally uses Gradle from the GitHub workflow, so a Gradle wrapper binary is not required in the initial repository.

## Release APK

See [docs/RELEASE.md](docs/RELEASE.md).

Once signing secrets are configured, release flow is:

`Actions → Release APK → Run workflow → enter version → signed APK + GitHub Release`

## Update checker

The release workflow passes the actual GitHub repository name into `BuildConfig.GITHUB_REPO`. The installed app checks:

`GET https://api.github.com/repos/<owner>/<repo>/releases/latest`

If the latest release tag is newer than the installed app version, it suggests an update and opens the release APK.

For this direct GitHub update design, releases should be publicly readable. Do not embed a GitHub personal access token in the APK.

## Package

`com.tihloh.pos`

Initial version: `0.1.0` / version code `1`.
