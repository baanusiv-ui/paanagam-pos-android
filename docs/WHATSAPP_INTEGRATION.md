# Paanagam POS — Configurable WhatsApp integration

Status: approved product/technical specification. The WhatsApp integration must be configurable in the app and remain disabled until the administrator chooses a provider, completes onboarding, tests it, and explicitly enables automation.

## User decisions and current state

- The user wants automatic WhatsApp sending eventually, without a cashier tapping Send for each message.
- Preserve the user's existing WhatsApp Business number and app wherever possible.
- The installed WhatsApp Business app version reported by the user is 2.26.44.2 (Beta), and the number is connected to a Meta Business Portfolio with administrator access.
- Prefer WhatsApp Business App Coexistence (same number in the mobile Business app and Cloud API) if the account and onboarding provider are eligible.
- Do not unregister, migrate, delete, or otherwise alter the existing number during development or eligibility checks.
- No provider, API connection, paid service, message template, or automatic sending is active yet.
- Provider and sender configuration must be changeable later from POS settings without rebuilding the APK.

## Settings UI

Add Settings > Integrations > WhatsApp with these controls and states:

1. Master switch: Disabled by default. Enabling requires an administrator confirmation.
2. Connection mode:
   - Meta Cloud API — new/API-only number (show warning that it is not the selected path for the existing app number).
   - WhatsApp Business App Coexistence — preferred existing-number option, subject to eligibility and supported Embedded Signup.
   - Manual WhatsApp sharing — fallback that opens WhatsApp with a prepared message and requires a human Send tap.
3. Provider:
   - Direct Meta / eligible Tech Provider or Solution Partner for Coexistence.
   - Provider can be selected later; do not hard-code one provider.
   - Display any provider subscription or per-message fees before activation.
4. Sender number: masked display, country code, verification/connection state.
5. Connection status: Not configured, Eligibility check required, Awaiting Meta verification, Connected, Paused, Disconnected, Error.
6. Message automation toggles, each independently disabled by default:
   - Customer receipt after successful checkout.
   - Daily owner sales/closing summary.
   - Low-stock and stock-out alerts.
   - Supplier purchase-order messages.
   - Loyalty reward notifications.
7. Template manager: show template name, language, category, approval status, last updated time; support refresh/status checks. Do not send API messages using unapproved templates where a template is required.
8. Test message action: administrator selects a verified test recipient and explicitly confirms the test.
9. Usage and cost view: sent, delivered, failed, message category, Meta charges/provider fees where available.
10. Disconnect / pause controls: immediately stop future queued sends; retain audit history without retaining unnecessary message content.

## Coexistence onboarding safeguards

- Check current Meta and provider eligibility before initiating Embedded Signup.
- The provider must explicitly support WhatsApp Business App Coexistence, not merely ordinary Cloud API onboarding.
- Explain possible impacts before consent: linked companion devices may need relinking; broadcast-list behavior and some app features may differ; chat-history synchronization depends on Meta/provider support.
- Do not claim eligibility solely from app version, country, or Business Portfolio admin status.
- Do not request or store the user's WhatsApp verification OTP in chat.
- Never start API-only migration as a substitute for coexistence.
- Keep the existing WhatsApp Business app and number operational until successful coexistence connection is confirmed and the user approves activation.
- If coexistence is unavailable, leave integration unconfigured and offer manual sharing; do not migrate the number without a separate explicit decision.

## Automatic sending behavior

- After a successful sale is durably saved locally, enqueue a receipt only when the integration is Connected, the receipt toggle is enabled, the customer has explicitly consented, and a valid recipient number is confirmed.
- Do not delay, fail, or roll back checkout because WhatsApp is offline or sending fails.
- Use a durable outbox with unique idempotency keys to avoid duplicate sends after retries or offline sync.
- Retry transient failures with bounded backoff; do not retry permanent policy/template/recipient errors indefinitely.
- Show honest statuses: Queued, Sending, Sent to Meta, Delivered, Read (only when a real callback supports it), Failed. Never infer delivery from an API success response.
- Daily reports, low-stock alerts, supplier orders, and loyalty notifications are sent only when their individual toggles are enabled and the required recipient/consent/template conditions are met.
- Supplier orders and any financially consequential instruction require administrator approval before dispatch by default.
- No unsolicited promotions, broadcasts, or bulk campaigns. Marketing messages require appropriate consent and approved marketing templates.
- WhatsApp messaging must never block POS checkout or inventory operations.

## Technical architecture

- Native Android POS UI; no WebView or unofficial UI automation.
- Keep provider adapter behind a stable app interface so provider changes do not affect POS workflows.
- For Meta Cloud API, call a Supabase Edge Function; never place access tokens, app secrets, or provider credentials in the APK or local database.
- Store credentials in Supabase secrets; restrict Edge Function access and validate authenticated admin/authorized service requests.
- Use server-side webhook verification and persist message status callbacks.
- Use an outbox/queue and idempotency keys for automatic delivery; process only after local sale persistence and sync when online.
- Keep message templates and enabled workflow configuration remotely configurable, with safe local cached settings for offline display.
- Manual fallback uses Android ACTION_VIEW with official WhatsApp click-to-chat URI and prefilled text; the user must tap Send.
- Do not use Accessibility Service, UI scripting, notification scraping, or other unofficial methods to silently control the WhatsApp app.
- Do not embed Blueticks credentials or depend on Blueticks for the POS messaging path.

## Cost and compliance

- Prefer direct Meta Cloud API to avoid an additional intermediary subscription where compatible.
- Coexistence may require a supported Tech Provider/Solution Partner and may have its own fee. Show all known fees before activation.
- Meta message charges depend on recipient country, message category, and current pricing; retrieve current rate information before quoting a final cost.
- Use approved templates and applicable opt-in/consent requirements.
- Keep promotional/marketing automation off by default to control cost.
- Do not enable any paid plan or incur charges without explicit user approval.

## Reliability and privacy

- Checkout and local sale persistence happen before any message send attempt.
- Minimize customer data in message content; never include payment credentials or sensitive information.
- Keep recipient number editable/confirmable and record consent status and timestamp.
- Retain only the minimum message metadata needed for operations, support, and audit.
- Disconnecting WhatsApp must not affect POS sales, local inventory, or offline operation.

## Acceptance criteria

- Integration is visible in app settings but disabled and unconfigured on first install.
- Admin can configure provider/mode and each automation independently.
- Existing-number Coexistence is the preferred path and has explicit no-migration safeguards.
- No automatic send occurs before successful connection, template readiness, recipient consent, and admin activation.
- A failed message never affects checkout.
- Duplicate messages are prevented across retries/sync.
- Manual WhatsApp sharing remains available as fallback.
- All credentials remain server-side.
- Provider changes and settings changes do not require an APK rebuild.

## Scope note

This file is the integration specification only. It does not mean the API has been connected, coexistence eligibility has been verified, templates have been approved, or the native production APK has been updated. Implement these requirements in the Stage18 native Kotlin/Compose application, not the older Capacitor/WebView starter.
