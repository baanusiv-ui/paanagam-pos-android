# Paanagam POS for Android

Initial offline-first Android POS starter built with React, Vite and Capacitor. The interface is bundled inside the APK and does not need the live website to open.

## Build the APK

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Tap **Run workflow** and select branch `main`.
4. When the run completes successfully, open it and download the `paanagam-pos-debug-apk` artifact.
5. Extract the ZIP and install `app-debug.apk` on your Android device. Android may ask you to allow installation from the app you use to open the file.

The workflow also runs on pushes to relevant source files.

## Starter features

- Local menu grid with sample Paanagam menu items
- Cart quantity controls and totals
- Cash/UPI payment method selection
- Local sales history and daily totals
- PIN-gated admin menu/price editor
- JSON backup/restore
- Basic manual loyalty-visit counter

Default starter admin PIN: `1234`. Change it before use.

## Important limitations

This starter is **not yet a complete migration** of the current Apper Paanagam POS and must not be treated as production-ready. It currently stores state in browser/WebView local storage, which can be cleared by Android/app data resets. Export backups regularly.

Not yet implemented or verified: recipe-based inventory deduction, store/kitchen transfers, purchase entry, production batches, stock reconciliation, food-cost/pricing engine, printer integration, secure role management, SQLite-based resilient persistence, cloud backup/synchronization, and migration of the existing Apper database. The sample menu is placeholder data and should be replaced with the verified menu/prices.
