# Paanagam POS — Native Android

Paanagam (பானகம்) is being developed as a standalone Android application using Kotlin, Jetpack Compose and Room (SQLite). The native app lives in `native-android/`; it does not require Apper, React, Capacitor, a hosted website or a WebView to run.

## Build and install

1. Open the repository's **Actions** tab.
2. Select **Build Native Android APK**.
3. Run the workflow on the `main` branch, or push a change under `native-android/`.
4. Download the `paanagam-native-debug-apk` artifact from a successful run.
5. Extract `app-debug.apk` and install it on an Android device. Android may ask you to allow installation from the file manager or browser.

The APK produced by this workflow is a **debug build**, not a signed production release.

## Native implementation

The app uses a local Room database and includes native screens and workflows for POS, sales history, menu/recipes, purchase entry, Store and Kitchen stock, FIFO transfers, production, wastage, stock reconciliation, suppliers, cash shifts, loyalty, staff access, reports, next-day planning, combos and Kitchen KDS.

Important rules:
- Payments: Cash and UPI only. Paytm is excluded.
- Store and Kitchen stock are maintained separately.
- FIFO costing preserves each purchase lot's original cost; purchase costs are never averaged.
- Coffee powder stock movement follows 250 g multiples.
- POS recipe consumption is based on configured recipes and Kitchen stock.
- Filter coffee reference: 250 g powder + 1,100 ml water yields 1,000 ml usable decoction (100 ml retained in grounds); one serving uses 25 ml decoction + 75 ml milk + 5 g sugar.
- POS checkout must work offline and must not depend on WhatsApp or another external service.

## Before live business use

This project is still under development. A successful APK build confirms compilation, not end-to-end business correctness. Device-level acceptance testing, backup/recovery validation, printer and UPI payment confirmation, and completion/verification of the remaining operational workflows are still required. Do not use a development APK as the sole production record until those checks are complete.

See [native-android/README.md](native-android/README.md) for implementation notes and known limitations.
