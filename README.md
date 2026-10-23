# Paanagam POS for Android

An initial offline-first Android POS starter built with React, Vite and Capacitor. The user interface and local data run from the APK; no website connection is required for the bundled app.

## Build an APK
1. Open **Actions** and enable workflows if GitHub asks.
2. Open **Build Android APK** and choose **Run workflow**.
3. Open the completed run and download the `paanagam-pos-debug-apk` artifact.
4. Extract the ZIP and install `app-debug.apk` on your Android phone. You may need to allow installation from your browser/files app.

## Current scope
This starter provides a local menu, cart, cash/UPI checkout, sales history, admin PIN gate, menu editing, and JSON backup/restore. It stores data in WebView local storage. Export backups regularly. This is not yet a migration of the full Apper POS: recipe-based stock consumption, purchasing, transfers, food-cost calculations, robust SQLite storage, cloud sync, printer integration, and production-grade authentication remain to be added and tested before live business use.

Default starter admin PIN: `1234`. Change it from Admin settings before using the app.
