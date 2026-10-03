# POS Android

Local-first Android POS and inventory app focused on fast scanner-driven retail workflows.

## Current features

- Kotlin + Jetpack Compose
- PIN + biometric/device-credential lock
- Camera barcode/QR scanning with CameraX + ML Kit
- Open Food Facts + Open Products Facts lookup
- Product catalog with one-to-many / many-to-many supplier links
- Ledger-based inventory with beginning, additions, and ending balances
- Sales history with Today / 7 Days / 30 Days / All scopes, receipt search, transaction count, and totals
- Checkout with blank payment entry plus one-tap **Exact** amount
- Network ESC/POS receipt printer support (TCP, typically port 9100)
- Receipt reprint from Sales and print immediately after checkout
- Local Room database remains the operational source of truth
- Optional central-server snapshot sync
- Mandatory update check when entering the app
- Internal APK installer with no residual APK in Downloads
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

`Beginning + Added → Ending`

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

The app force-checks the latest GitHub Release and prevents dismissing an available update. Installation is streamed to Android's package installer; the APK is not saved in Downloads.

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
