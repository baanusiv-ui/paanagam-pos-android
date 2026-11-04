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
- Staff login now persists failed-attempt counters per staff account. Five incorrect PIN attempts trigger a 30-second lockout, with exponential increases up to 15 minutes; a successful login clears the lockout state.
- Admin can deactivate and reactivate staff accounts.
- Admin has full module access; Manager is restricted from staff administration and selected configuration modules; Cashier is limited to Dashboard, POS, Orders and Cash Shift. Sales History, inventory and financial reporting remain Admin-only.
- Staff records are local to this device. There is no cloud account sync or remote recovery yet.
- An authenticated staff session is forced back to the PIN login screen after the app has remained in the background for five minutes. Returning sooner preserves the active session.
- Admin-only offline backup and restore uses Android's document picker. Backups are SQLite-integrity checked and schema-version checked before restore; the current database is preserved until validation passes. Device-level recovery testing is still required.


## Audit hardening started (0.2.5 / schema 23)

- Coffee loyalty classification is an explicit menu-item flag, not a name substring. Admin/menu setup must mark qualifying paid coffee products as eligible; unrelated products with names containing "coffee" no longer earn rewards.
- Room schema migration 22 → 23 adds the explicit coffee flag with a safe default of false for existing menu rows.
- Sensitive inventory, purchase, stock-transfer, kitchen-stock, sales-history, reports, food-cost, finance, stock/sales reconciliation and supplier views are restricted to Admin. Cashier/Manager module customization cannot grant these protected views.
- Existing Cashier/Manager module assignments remain local and PIN hashes remain PBKDF2-based.

## Security and production hardening still in progress

- The audit branch now integrates SQLCipher for the live Room database and includes a plaintext-to-encrypted migration. CI build/unit tests pass; physical-device migration and recovery acceptance tests are still required. The unmerged main branch remains unchanged.
- Device binding uses the app-scoped Android ID and local admin approval after a restored database is opened on a different device. This is not a server-managed device registry and Android ID is not a MAC address.
- Persistent PIN throttling is implemented: five failed attempts trigger a 30-second lockout with exponential increases capped at 15 minutes; successful login clears the counter.
- A five-minute background session timeout is implemented and returns staff to PIN login. Device-level verification of lockout timing, process recreation and session expiry remains required.
- Live SQLCipher integration and encrypted portable backup/restore are implemented in this audit branch. Device-level backup recovery and migration tests remain required.
- Device-level tests for role navigation, database migration, FIFO accounting, sales rollback, backup/restore, offline operation, UPI and printer hardware remain required.


## Page-logic corrections in progress (0.2.6 / Room schema 24)

- POS now validates that every bill line belongs to the sale, has a positive quantity, has a mathematically correct line total, and that all line totals equal the bill total before FIFO consumption.
- Combo creation validates component existence and recipe readiness, uses decimal rupee pricing, and saves the menu item and component rows in one Room transaction to avoid orphan combo items.
- Inventory balances now observe stock-ledger movements so purchase receipts, transfers, production, wastage and reconciliation refresh displayed Store/Kitchen balances without waiting for ingredient-master edits.
- Daily sales report groups by the device's current local calendar date rather than freezing midnight at screen creation.
- Cash shift expected drawer balance now includes opening float + cash sales + cash-in − cash-out/refunds/withdrawals. Every drawer adjustment requires a positive amount and reason and is tied to the currently open shift.
- Room migration 23 → 24 creates the cash-movement ledger without deleting existing shift or sales data.

Remaining audit items requiring additional work/device verification: partial refunds; loyalty reversal on refund; configurable POS tax; real printer/UPI QR integrations; full statutory accounting; on-device SQLCipher migration/recovery acceptance; complete business workflows for placeholder operational modules. Full-bill refunds require an explicit stock disposition: only unopened, safe returns are restocked; served/opened/unsafe items are recorded as wastage. UPI reversal must still be performed in the UPI app. The app remains a local single-device POS; no multi-device sync is claimed.


## 360° CCTV and POS cancellation audit

- Added an administrator-only native CCTV screen with RTSP live playback using Android Media3.
- Added pinch/drag and on-screen digital zoom controls (up to 5×) for the live preview.
- Camera host, RTSP path, username and password are stored as AES-GCM ciphertext using an Android Keystore key; camera credentials are not stored in Room or printed to logs.
- Added Room schema 27 and a cancellation/refund audit event table. Completed-sale refunds write a CCTV timestamp bookmark, bill ID, refund reason, amount and signed-in staff identity in the same Room transaction as payment and stock disposition records.
- Full-bill refund now requires an explicit stock disposition. Safe, unopened returned stock is restored to its original FIFO lot; served, opened or unsafe food remains consumed and is recorded as wastage. Refund remains atomic with cash ledger, sale status, KDS cancellation and CCTV bookmark.
- Added local-network permissions and restricted the CCTV module to Admin.
- Device dependency: generic RTSP is a video transport, not a universal record/clip-control API. This build creates a durable local timestamp bookmark; automatic camera-side clip retrieval/recording trigger and ONVIF PTZ/fisheye dewarping require the selected camera/NVR's supported ONVIF recording profile or vendor API. Verify the exact model before enabling that claim in production.
- Pre-completion cart cancellation now records a durable local audit bookmark with cashier identity, required reason, amount and timestamp before the cart is cleared. Individual cart-line removals are also logged. These are local timestamp bookmarks, not proof that camera-side recording or clip retrieval was triggered.
- CI build verification now passes for the audit branch. Physical camera stream, recording trigger, clip retrieval and PTZ/fisheye testing remain outstanding. Do not treat local bookmarks as production CCTV evidence until the selected camera/NVR is validated.

- Fresh Admin PIN re-authentication is required before opening Staff & HR, Data Backup/Restore, and CCTV & Audit. Re-authentication has its own persistent failed-attempt counter and escalating lockout, separate from the normal staff-login counter. This is an in-app gate; device-level verification and review of every sensitive write action remain required.


## Admin re-authentication hardening (0.2.9)

- Opening Staff & HR, Data Backup, and CCTV & Audit requires a fresh Admin PIN verification.
- Re-authentication failures use a separate persistent per-Admin counter and lockout deadline; the staff login counter is not reused.
- Lockout timing is centralized in `PinLockoutPolicy` and covered by JVM tests for threshold, exponential backoff, cap, deadline arithmetic and overflow.
- CI has passed for the current Admin re-authentication implementation and its follow-up documentation commit. Device-level verification is still required.

## Live database encryption — CI build verified; device acceptance pending

The development branch now wires Room to SQLCipher Android 4.20.0. The live database key is generated randomly, wrapped by an Android Keystore AES-256-GCM key, and the wrapped key alone is persisted in app-private preferences. Existing plaintext SQLite files are copied to a new encrypted temporary database using `sqlcipher_export()`; the temporary database is reopened with the key and checked for SQLCipher status, SQLite integrity and Room schema version before the file switch. A separate recovery filename is retained across the file-swap boundary so an interrupted switch can restore the original file.

Portable backups remain independent of the per-device database key. Backup export creates a temporary plaintext SQLite copy from the encrypted database, then encrypts it with the user's AES-256-GCM passphrase envelope. Restore decrypts and validates that portable SQLite file, re-encrypts it with the receiving device's Keystore-protected key, and only then replaces the live database. This preserves cross-device restore.

The following are not yet production-verified:
1. Physical-device migration of a populated Paanagam database, preserving sales, sale lines, FIFO lots/movements, staff, cash shifts, refunds, loyalty and CCTV audit records.
2. Process-kill tests at each migration/swap stage, low-storage handling, wrong-key handling, and recovery on a real Android device.
3. Cross-device portable backup/restore and Room schema migration from older database versions.
4. Confirmation that the temporary plaintext export and migration recovery copy are removed after successful completion. Flash storage does not guarantee physical secure erasure after file deletion.

Do not merge this work to main or distribute it as a production release until CI and physical-device migration/restore acceptance tests pass. The debug build is not a substitute for those checks.

## Portable backup encryption (0.2.10)

- Exported portable backups now use a versioned AES-256-GCM authenticated-encryption envelope.
- A 256-bit key is derived from a user-held passphrase with PBKDF2-HMAC-SHA256 (310,000 iterations), a random 16-byte salt and a random 12-byte GCM nonce.
- Restore authenticates/decrypts into a staging file, then checks SQLite integrity, Room schema version and required Paanagam tables before swapping it into place.
- Passphrases are not persisted. Losing the passphrase means the encrypted backup cannot be recovered.
- JVM tests cover round-trip recovery, wrong passphrases, ciphertext tampering and minimum passphrase length.
- Portable backup encryption is separate from the live database's SQLCipher encryption. Backup files can be restored on another device because the receiving installation re-encrypts the restored database with its own Android Keystore-protected key.
- Existing raw .db backups from earlier builds are not encrypted-format backups; retain the old app build and the original backup until legacy restore compatibility is explicitly validated. Do not delete older backup copies.


## Standalone APK boundary (confirmed 2026-11-04)

- The deliverable is the native Android APK in this repository. Apper is not the application host, UI runtime, authentication provider, database, backend, or deployment target.
- Apper may be consulted only as a read-only reference source to retrieve existing business data (for example catalogue/menu items, recipes, ingredient definitions and opening reference values) for review and controlled import into the native app.
- The APK must remain fully usable if Apper is disconnected, deleted, unavailable or its project changes. Do not add Apper SDKs, webviews, API runtime dependencies, login dependencies, or automatic bidirectional sync.
- Any reference-data import must be explicit, validated and auditable: preview records, detect duplicates, validate units and relationships, then import into the local Room database. Never overwrite live sales, stock movements, FIFO lots, staff PINs, cash shifts or audit history from Apper reference data.
- The local Room database remains the operational source of truth. Offline checkout, inventory accounting, refunds, wastage and reports must execute locally. Future optional sync, if separately approved, must use a backend independent of Apper.
