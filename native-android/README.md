# Paanagam Native Android

This directory contains the standalone native Android application. It is built with Kotlin, Jetpack Compose and a local Room database. It does not depend on Apper, React, Capacitor, WebView or Supabase.

## Build

Run GitHub Actions workflow **Build Native Android APK**. The debug APK is uploaded as the artifact `paanagam-native-debug-apk`.

## Implemented foundation

- Native Compose navigation for the Paanagam business modules.
- Local Room entities for catalogue items, ingredients, purchase receipts and lines, stock lots, transfers, movements, production batches, wastage, sales, recipes and SOP records.
- FIFO stock-lot consumption and transfer logic; each purchase lot retains its own cost. Purchase costs are not averaged.
- Separate Store and Kitchen stock balances.
- Configurable Store and Kitchen minimum stock levels per ingredient. Dashboard low-stock count is calculated from live Room stock balances; zero disables that location's alert.
- Purchase receipt into Store and FIFO transfer between Store and Kitchen.
- Recipe/BOM lines use scaled decimal base units.
- POS sale completion deducts configured recipe quantities from Kitchen stock atomically using FIFO lots. Coffee powder is reserved for decoction production and is rejected as a direct POS recipe ingredient; coffee POS recipes must consume prepared decoction (plus milk, sugar and other configured ingredients). A sale with a missing recipe or insufficient configured stock is rejected without saving the bill.
- Ingredient cost is saved per bill from the actual FIFO lots consumed. Sales History shows bill lines, ingredient cost and gross margin before other expenses.
- Dashboard sales, bill count and stock-alert count are calculated from local database records.
- Production and wastage workflows have initial local implementations, including recent wastage history and FIFO cost display; these still need broader validation.
- Filter-coffee SOP reference: 250 g coffee powder + 1,100 ml water produces 1,000 ml usable decoction, with 100 ml retained in grounds. One serving uses 25 ml decoction + 75 ml milk + 5 g sugar.
- Configurable WhatsApp settings UI stores local preferences only. Automatic sending remains disabled while disconnected; no credentials, number migration, API dispatch, templates, webhooks or delivery tracking are connected.
- Espresso, ivory and warm-gold visual direction.

## Remaining work before production use

- Device-level acceptance tests for staff login, PIN reset/deactivation and role-based module access.
- Printer integration and UPI QR/payment confirmation workflow.
- Device-level testing of the new offline database backup and restore flow, including recovery on a replacement device.
- Detailed sales, stock, food-cost and profit reports.
- Complete SOP authoring, approval, version history and staff checklist workflows.
- Full validation of coffee-decoction production, FIFO stock transfers, wastage, purchase corrections and reconciliation.
- Device-level acceptance tests using real menu items, recipes, ingredient quantities and opening stock.

The current APK is a development build. Do not use it for live business transactions until the remaining workflows and device-level acceptance tests are completed.

## Business rules to preserve

- Payments are Cash and UPI only; Paytm is excluded.
- Stock is maintained separately in Store and Kitchen.
- FIFO costing is mandatory. Old stock retains its original purchase cost; new stock uses its own lot cost. Never use average purchase costing.
- Coffee powder is used as a production input for decoction, never consumed directly by POS sales. Production follows the 250 g powder + 1,100 ml water → 1,000 ml usable decoction SOP. POS consumes prepared decoction in recipe quantities; coffee powder stock transfers and wastage remain in 250 g multiples.
- POS checkout must work independently of WhatsApp.
- WhatsApp integration remains paused unless explicitly resumed by the user.


## Staff access (build 0.1.3)

- First launch creates the local Admin account with a 4–8 digit PIN.
- Admin creates Manager and Cashier accounts and can reset PINs.
- PINs are stored as salted PBKDF2-HMAC-SHA256 hashes (120,000 iterations), not plaintext.
- Admin can deactivate and reactivate staff accounts.
- Admin has full module access; Manager is restricted from staff administration and selected configuration modules; Cashier is limited to Dashboard, POS, Orders and Sales History.
- Staff records are local to this device. There is no cloud account sync or remote recovery yet.
- Admin-only offline backup and restore uses Android's document picker. Backups are SQLite-integrity checked and schema-version checked before restore; the current database is preserved until validation passes. Device-level recovery testing is still required.
