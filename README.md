# Paanagam POS for Android

Offline-first Android POS starter built with React, Vite and Capacitor. The APK bundles the app UI, so the basic cashier flow works without a website connection.

## Current features

- Product menu, cart, quantities and cash/UPI payment selection
- Local sales history and daily totals
- PIN-gated admin access, menu editing and JSON backup/restore
- Local ingredient stock ledger with individual purchase lots
- FIFO lot consumption that preserves lot prices instead of averaging
- Decoction preparation workflow: 250 g coffee powder + 1,100 ml water produces approximately 1,000 ml decoction per batch
- Filter coffee recipe: 25 ml prepared decoction + 75 ml milk + 5 g sugar per cup
- Movement history for stock receipts, decoction inputs/outputs, sales consumption and manual stock-out

## Build an APK

1. Open **Actions** and choose **Build Android APK**.
2. Select **Run workflow** (or push a commit to `build-apk`).
3. Open the completed run and download the `paanagam-pos-debug-apk` artifact.
4. Extract the ZIP and install `app-debug.apk`.

## Important limitations

This is still a starter, not a complete migration of the existing Apper POS. It currently stores data in WebView local storage, so export JSON backups regularly. The stock and recipe workflow needs careful in-store verification before live use. Production upgrades still needed include SQLite-backed storage and migration, stock transfer workflows, purchase entry improvements, full recipe/inventory coverage for every menu item, food-cost and margin reports, next-day planning, robust authentication, cloud sync, printer integration, and comprehensive tests.

The stock ledger starts at zero. Record actual opening stock and costs before testing. Recipe unit must match the corresponding inventory unit. Stock shortages are flagged in the movement ledger; they must be reconciled before live business use.

Default starter admin PIN: `1234`. Change it before real use.
