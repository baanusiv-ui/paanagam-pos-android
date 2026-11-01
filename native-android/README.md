# Paanagam Native Android

This directory is the separate native Android application. It is Kotlin + Jetpack Compose with a Room database; it does not use Apper, React, Capacitor, WebView or Supabase.

## Build
Run GitHub Actions workflow **Build Native Android APK**. The debug APK is uploaded as artifact `paanagam-native-debug-apk`.

## Current foundation
- Native Compose navigation for all requested Paanagam modules.
- Room database entities for catalogue items, stock lots, sales and SOP documents/checks.
- FIFO lot query orders by received time.
- SOP reference screen includes the approved filter-coffee batch and serving specification.
- Dark espresso / ivory / warm-gold design direction.

## Not production complete yet
POS checkout, transactional inventory consumption, purchase/transfer/production flows, role authentication, printer/UPI QR, backup/restore, reporting, and full SOP authoring/approval/checklist workflows remain staged implementation work. Do not use this starter for live business transactions until these workflows and tests are completed.
