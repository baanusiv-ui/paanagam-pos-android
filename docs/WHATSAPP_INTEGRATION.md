# Paanagam POS — WhatsApp integration specification

Status: architecture approved; implementation pending Blueticks API credentials and the production Supabase project.

## Goals

Provide a reusable WhatsApp channel for the standalone native Android POS. Do not hardcode a phone number, WhatsApp account, or a single business workflow. The connected Blueticks account and recipients are configured by an authorized administrator.

## Workflows

1. Customer receipt: offer WhatsApp receipt after a completed Cash/UPI sale only when the customer explicitly opts in and provides/confirms a number. Never delay or fail checkout if WhatsApp is offline.
2. Owner alerts: configurable daily sales summary, closing report, low-stock and stock-out alerts. Recipients and schedules are configurable.
3. Supplier communication: prepare purchase orders and payment reminders as drafts. Require an authorized user's explicit approval before sending.

No unsolicited promotional/bulk messages are enabled by this integration.

## Architecture

- Android app: native settings and workflow controls; writes a durable outbound job to the local Room outbox.
- Sync worker: retries queued jobs when connectivity returns; uses stable idempotency keys so retries do not create duplicate messages.
- Supabase Edge Function: authenticated server-side broker. Validates the POS user/role, workflow permission, consent/approval, payload, and rate limits.
- Blueticks REST API: server-to-server send/schedule operations. Store the API key only in Supabase secrets; never bundle it in the APK or local database.
- Blueticks webhook -> Supabase Edge Function: verify the documented HMAC signature against the raw request body and timestamp, reject replayed/invalid events, deduplicate event IDs, and update delivery/inbound status.
- Local POS sale/stock transactions remain authoritative. WhatsApp errors are logged separately and cannot roll back a sale or stock transaction.

## Configuration

Admin-only settings:
- Integration enabled/disabled
- Blueticks connection status (connected/disconnected/needs attention)
- Workflow toggles for receipts, owner alerts, supplier drafts
- Recipient numbers per workflow in E.164 format
- Receipt consent requirement (always on)
- Supplier approval requirement (always on)
- Owner alert schedule and low-stock thresholds
- Message language and receipt format
- Test/live environment indicator

Do not expose API keys or webhook secrets in app UI. Rotate them in Supabase secrets and Blueticks dashboard.

## Reliability and safety

- Durable queue states: pending, sending, accepted, delivered, read, failed, cancelled.
- Idempotency key per business event + recipient + message type.
- Bounded exponential retry for transient failures; no automatic retry for permanent recipient/template/policy errors.
- Keep checkout independent from WhatsApp delivery.
- Audit log records actor, consent/approval, recipient, event ID, message ID, timestamps, and final status; redact message content and phone numbers where practical.
- Validate webhook signatures before processing; use event deduplication.
- Do not send customer receipts to a number unless the customer has opted in.
- No marketing campaigns or promotional broadcasts in this scope.

## Implementation gates

1. Confirm the production Supabase project and deploy the broker/webhook Edge Functions.
2. Create a Blueticks API key and webhook signing secret in the Blueticks dashboard; add them to Supabase secrets. Never paste secrets into source code or chat.
3. Verify Blueticks API plan/channel capabilities and connect the intended WhatsApp account in Blueticks.
4. Test in sandbox/test mode using non-real recipients.
5. Test real delivery only after the admin explicitly enables live mode and confirms the connected business account.
6. Add native Settings UI and queue/outbox integration; test offline recovery, duplicate prevention, permission boundaries, and delivery failure.

## API reference

Use the current official Blueticks developer documentation as the contract:
- https://dev.blueticks.co/docs
- https://dev.blueticks.co/docs/integrations
- https://dev.blueticks.co/docs/api/scheduled-messages/schedule-message

Do not assume an endpoint or webhook payload shape that is not present in the current OpenAPI/API documentation. Keep the provider behind an interface so another WhatsApp provider can be added without changing POS checkout.
