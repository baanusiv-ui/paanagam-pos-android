# Paanagam POS for Android

Offline-first Android POS starter built with React, Vite and Capacitor. The APK bundles the app UI, so the basic cashier flow works without a website connection.

## Current features

- Product menu, cart, quantities and cash/UPI payment selection
- Local sales history and daily totals
- PIN-gated admin access, menu editing and JSON backup/restore
- Offline-first local WebView storage, plus configurable automatic cloud backup/restore over a secured HTTPS endpoint when configured
- Cloud backup status and manual sync/restore controls
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

This is an offline-first trial build, not yet a complete migration of every previously planned Apper POS module. Local data is stored in WebView local storage and survives normal app restarts, but it is not SQLite. Export JSON backups regularly. Cloud sync is configurable but is NOT active until a secured HTTPS backup endpoint and bearer token are configured. The endpoint must accept POST JSON `{ app, schema, deviceId, updatedAt, payload }` and GET with `?deviceId=...`, returning the saved payload. Do not put Supabase service-role keys in the APK or use a public database policy. Before production, complete the secured cloud endpoint, SQLite migration, store-to-kitchen transfers, full recipe coverage, food-cost/margin and next-day planning, robust role-based authentication, printer integration, and full end-to-end tests.

The stock ledger starts at zero. Record actual opening stock and costs before testing. Recipe unit must match the corresponding inventory unit. Stock shortages are flagged in the movement ledger; they must be reconciled before live business use.

Default starter admin PIN: `1234`. Change it before real use.
