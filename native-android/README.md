# Paanagam Native Android

This directory is the separate native Android application. It is Kotlin + Jetpack Compose with a Room database; it does not use Apper, React, Capacitor, WebView or Supabase.

## Build
Run GitHub Actions workflow **Build Native Android APK**. The debug APK is uploaded as artifact `paanagam-native-debug-apk`.

## Current foundation
- Native Compose navigation for all requested Paanagam modules.
- Room database entities for catalogue items, stock lots, sales and SOP documents/checks.
- FIFO lot query orders by received time.
- SOP reference screen includes the approved filter-coffee batch and serving specification.
- Sales History reads real locally stored POS bills, shows today's bill count and sales total, and expands each bill to its item lines.
- Recipe/BOM screen maps menu items to ingredient quantities per serving, stored as scaled decimal base units.
- POS sale completion now deducts configured recipe quantities from Kitchen stock atomically using FIFO lots; insufficient configured stock rejects the bill without saving it.
- Dashboard sales and bill count are calculated from the local Room database.
- Dark espresso / ivory / warm-gold design direction.\n- Configurable WhatsApp settings screen with locally persisted provider/mode preferences and independent message workflow toggles. Automatic sending is locked while disconnected; no credentials or number migration are performed by the app screen.\n\nWhatsApp implementation status: settings UI only. Coexistence onboarding, Meta API credentials, server-side message dispatch, templates, webhooks and delivery tracking are not connected yet.

## Not production complete yet
Stock-alert thresholds, role authentication, printer/UPI QR, backup/restore, detailed reporting, and full SOP authoring/approval/checklist workflows remain staged implementation work. Recipe deduction is implemented but needs device-level acceptance testing with real configured ingredients and kitchen stock before live use. Purchase receipt, FIFO stock transfers, coffee-decoction production and wastage capture have initial local workflows but require broader validation before live use. Do not use this starter for live business transactions until these workflows and tests are completed.
