# POS Android

Local-first Android POS and inventory app focused on fast scanner-driven retail workflows.

## Current features

- Kotlin + Jetpack Compose
- PIN + biometric/device-credential lock
- Camera barcode/QR scanning with CameraX + ML Kit
- Open Food Facts + Open Products Facts lookup
- Product catalog with one-to-many / many-to-many supplier links
- Compact ledger-based inventory with beginning, added, removed, sold, and ending balances
- Sales history with Today / 7 Days / 30 Days / All scopes, receipt search, transaction count, and totals
- Checkout with blank payment entry plus one-tap **Exact** amount
- Network ESC/POS receipt printer support (TCP, typically port 9100)
- Receipt reprint from Sales and print immediately after checkout
- Local Room database remains the operational source of truth
- Product image picker with app-local image storage
- Product images included in central-sync payloads when locally stored
- Optional central-server snapshot sync
- Forced update gate when entering the app + hourly background update checks
- Background APK staging/install-session flow with no residual APK in Downloads
- Signed GitHub Release workflow
- Retail-focused Material 3 theme and custom POS launcher icon

## Main navigation

- POS
- Sales
- Inventory
- Products
- More

The scanner action is contextual: POS scanning, inventory scanning and product scanning use the same scanner foundation.

## Inventory model

Inventory is ledger based. Sales, receiving, physical counts and adjustments create inventory transactions. `stockCache` is only a fast current-balance cache.

The Inventory screen presents a simplified view:

`Beginning + Added − Removed − Sold → Ending`

Detailed transactions remain stored in the ledger.

## Product ↔ supplier model

Products and suppliers are linked through `ProductSupplierCrossRef`.

This supports:

- one product → one supplier
- one product → many suppliers
- one supplier → many products
- many products ↔ many suppliers

The old `ProductEntity.supplierId` is retained only as a migration source for existing installs.

## Receipt printer

Configure the printer from:

`More → Receipt printer`

Current direct printer support is raw ESC/POS over TCP/IP. Common thermal receipt printers use port `9100`.

## Local-first + central server

The app always writes to the local Room database first. A central server is optional and does not block checkout, inventory, or product management when offline.

Configure:

`More → Central sync`

When enabled, the app can POST a local snapshot to:

`POST <server>/api/v1/sync`

The request currently includes products, suppliers, and sales. The endpoint may use bearer-token authentication.

This provides the client-side foundation for a future multi-device central POS server while preserving offline operation.

## Updates

The app force-checks the latest GitHub Release and prevents use of an outdated version while a required update is available. It also checks hourly in the background when network access is available and can prepare/commit the PackageInstaller session automatically.

Android's security model may still require the system install confirmation for ordinary sideloaded apps. Fully silent installation requires managed-device/device-owner, system-app, or equivalent privileged deployment. The APK is streamed internally and is not left in Downloads.

## Build and release

Pushes and pull requests run unit tests and build a debug APK.

Releases can be triggered by updating `.release-version`:

```text
VERSION_NAME=0.4.0
VERSION_CODE=40
```

The Release APK workflow builds a signed APK and creates the matching GitHub Release.

## Package

`com.tihloh.pos`
