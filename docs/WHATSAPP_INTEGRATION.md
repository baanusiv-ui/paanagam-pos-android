# Paanagam POS — Blueticks Free Plan WhatsApp integration

Status: free-plan architecture approved; implementation awaits an administrator-created Blueticks API key and confirmation of the connected WhatsApp account. No paid upgrade or alternative provider is in scope.

## Verified free-plan constraints

Checked against current official Blueticks documentation on 2 November 2026:
- Free plan: maximum 3 scheduled messages waiting at one time.
- Free-plan sending uses the Chrome extension/browser connection; WhatsApp Web must remain open in Chrome and the computer awake for sending.
- REST API and hosted MCP share a quota of 5 requests per 6-hour window. Each API request or MCP tool call consumes one request; excess requests return HTTP 429 until the next window.
- The quota windows are fixed UTC: 00:00–06:00, 06:00–12:00, 12:00–18:00, 18:00–24:00.
- Free plan does not include the always-on Gateway. Do not promise 24/7 background sending or delivery while the computer is off.
- Blueticks documentation describes REST API and webhooks; actual API key access and webhook registration must be verified in the user's free account before relying on them.
- Blueticks Free campaigns are listed as available, but the POS integration will not use promotional/bulk campaigns.

Official references:
- https://blueticks.co/guides/faq
- https://blueticks.co/guides/features
- https://dev.blueticks.co/docs/integrations
- https://dev.blueticks.co/docs/integrations/make

## Goals and workflows

Provide a reusable WhatsApp channel for the standalone native Android POS. Do not hardcode a phone number, WhatsApp account, or single workflow. The connected Blueticks account and recipients are configured by an authorized administrator.

1. Customer receipt: offer WhatsApp receipt after completed Cash/UPI checkout only when the customer explicitly opts in and confirms a number. Checkout never waits for WhatsApp.
2. Owner alerts: configurable daily sales/closing summary and low-stock/stock-out alerts. Batch events into a digest to conserve the five-request quota; no high-frequency polling.
3. Supplier communication: prepare purchase orders and payment reminders as drafts. Require explicit authorized approval before sending.

No unsolicited promotional messages, bulk marketing, or auto-follow-up campaigns.

## Free-plan operating mode

- Prefer a single batched digest for owner alerts rather than one API call per stock event.
- Keep the POS outbox locally and queue messages until the Blueticks browser connection is online and quota is available.
- Maintain a quota budget of no more than 5 REST/MCP requests in each 6-hour window, shared with any other Blueticks API/MCP usage on the account. Do not assume other Blueticks use is zero.
- Keep at most 3 scheduled messages pending in Blueticks at any time. Queue additional messages locally; do not attempt to schedule a fourth.
- Avoid short-interval polling. Use webhooks only if the free account permits registration and the documented event subscription works; otherwise provide manual status refresh.
- Show the operator a clear status: queued locally, waiting for browser, waiting for quota window, submitted, delivered/read/failed (only when confirmed by Blueticks).
- The operator must keep Chrome, WhatsApp Web and the computer awake for free-plan sends. The app must not claim unattended 24/7 sending.
- When limits are reached, retain the job and explain when it can be retried; never silently drop a receipt or supplier draft.

## Architecture

- Android app: native Settings and workflow controls; writes a durable outbound job to local Room outbox.
- Sync worker: retries only when the user/device is online and the Blueticks connection/quota state allows it; uses stable idempotency keys to prevent duplicate sends.
- Supabase Edge Function (optional broker): authenticated server-side broker validates POS user/role, workflow permission, consent/approval, payload and quota budget. Blueticks key is stored only in Supabase secrets, never in APK or local DB.
- Blueticks REST API: use only documented endpoints, subject to free-plan quota and browser-connection availability.
- Webhooks: register only after confirming the feature is available on the free account. Verify signatures using the exact documented signing scheme and raw body; deduplicate event IDs. Do not invent payload fields or assume webhook registration is quota-free.
- Local POS sale/stock transactions remain authoritative. WhatsApp failures are separate and cannot roll back a sale or stock transaction.

## Configuration

Admin-only settings:
- Integration enabled/disabled
- Blueticks account connection status (connected/disconnected/needs browser)
- Workflow toggles for receipts, owner digest, supplier drafts
- Recipient numbers per workflow in E.164 format
- Customer receipt consent (always required)
- Supplier approval (always required)
- Owner digest time and low-stock thresholds
- Message language and receipt format
- Free-plan quota counter/window and pending scheduled-message count
- Browser readiness indicator and manual retry action

Never show API keys or webhook secrets in the app UI.

## Reliability and safety

- Durable queue states: pending, waiting_for_browser, waiting_for_quota, sending, accepted, delivered, read, failed, cancelled.
- Idempotency key per business event + recipient + message type.
- Bounded retry for transient errors; no automatic retry for permanent recipient/policy errors.
- Respect 429 quota responses and the documented UTC reset windows.
- Keep checkout independent from WhatsApp delivery.
- Audit actor, consent/approval, recipient, event/message IDs and status; redact message content and phone numbers where practical.
- Do not send customer receipts without opt-in.
- No marketing campaigns or promotional broadcasts in this scope.

## Implementation gates

1. Confirm the production Supabase project and whether a server-side broker is required for the deployed POS.
2. In the user's Blueticks account, verify that API key creation is available on Free and create the key there. Add it directly to Supabase secrets; never paste it into chat or source control.
3. Verify the connected WhatsApp account and browser-based free-plan sending in Chrome.
4. Verify API quota with a single ping/test request and record usage; do not exhaust the 5-call window during setup.
5. Verify webhook registration/event delivery only if needed and permitted on the free account. If unavailable, use manual status refresh and queued sending.
6. Test with an explicitly approved test recipient; no real customer or supplier messages during setup.
7. Add native Settings UI and Room outbox; test offline queueing, duplicate prevention, 3-message scheduling cap, 5-requests/6-hours cap, browser-offline behavior and recovery.

## API reference

Use current official docs as the contract:
- https://dev.blueticks.co/docs
- https://dev.blueticks.co/docs/integrations
- https://dev.blueticks.co/docs/api

Keep the provider behind an interface. Do not add another provider or paid service. Do not assume endpoints, webhook payload shapes, or free-plan capabilities that are not confirmed by the current Blueticks docs/account.
