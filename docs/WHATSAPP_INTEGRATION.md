# Paanagam POS — Free WhatsApp and SMS sharing

Status: approved design for zero-subscription messaging. Do not integrate a paid WhatsApp/SMS gateway, alternative provider, Blueticks API, or bulk messaging service in this phase.

## Decision

Use Android's native share/open flows:
- WhatsApp receipt: open the customer's WhatsApp chat with a pre-filled receipt using the official click-to-chat URL format. The cashier/customer reviews and taps Send.
- SMS fallback: open the Android SMS composer with the customer's number and pre-filled receipt. The user taps Send; carrier SMS charges, if any, follow the SIM plan.
- General sharing: offer Android Sharesheet for other supported apps.
- No automatic background sending. This is deliberate: it avoids paid gateways, API keys, Blueticks quota, and provider lock-in.

Official WhatsApp click-to-chat help:
https://faq.whatsapp.com/5913398998672934

## POS workflows

1. Customer receipt:
   - After a successful Cash/UPI sale, show WhatsApp, SMS and Share actions.
   - Customer must provide/confirm the phone number and agree to receive the receipt.
   - WhatsApp opens a chat using the official wa.me link with URL-encoded pre-filled text.
   - SMS opens the native SMS composer addressed to the customer with pre-filled text.
   - Never send without the user's final tap on Send.
   - Never block or roll back checkout if WhatsApp/SMS app is unavailable.
2. Owner alerts:
   - Build a daily sales/closing summary and low-stock/stock-out alert inside POS.
   - Offer Share/WhatsApp/SMS actions for the owner to send manually.
   - Do not auto-send alerts in the background in this zero-cost phase.
3. Supplier communication:
   - Prepare purchase order/payment reminder text as a draft.
   - Require an authorized user's review and explicit send action through WhatsApp or SMS composer.

No unsolicited promotions, broadcasts, automated campaigns or bulk messaging.

## Implementation

- Native Android app only; no WebView or external messaging SDK required.
- WhatsApp: construct official click-to-chat URI using international-format phone number (digits only, no leading plus, spaces, brackets or hyphens) and URL-encoded message. Open with Android ACTION_VIEW; allow WhatsApp or WhatsApp Business to handle it. If not installed, offer copy/share fallback.
- SMS: use Android ACTION_SENDTO with an smsto: URI and prefilled sms_body; let the default SMS app handle sending. Do not request SEND_SMS permission or send silently.
- Sharesheet: use Android Sharesheet for generic sharing and fallback.
- Never embed Blueticks credentials, SMS gateway keys or third-party messaging credentials.
- No Supabase Edge Function, webhook, Blueticks API, message queue or background worker is required for manual share/open flows.

## Reliability and privacy

- Checkout and local sale persistence happen before showing message actions.
- Message status should be labelled "Composer opened" or "Shared"; do not claim delivered/read because the POS does not receive carrier/WhatsApp delivery callbacks.
- Keep message contents concise; avoid exposing sensitive customer data.
- Customer consent is required for sending a receipt. Do not store phone numbers unless the customer explicitly asks to save them.
- Keep recipient number editable/confirmable before opening the composer.

## Future changes

Only if the user later requests automatic delivery:
- Reassess Blueticks Free account's API/webhook availability and quota, or a paid provider.
- For automated commercial SMS in India, follow TRAI TCCCPR requirements, including Principal Entity registration, header, content template and consent requirements where applicable:
  https://www.trai.gov.in/advice-to-senders
- Do not activate paid services or alternative providers without explicit approval.
