# Simple Setup

You need to paste these secrets into Cloudflare:

- `POKE_INGEST_TOKEN`: your Poke ingest bearer token
- `POKE_INGEST_ENDPOINT_ID`: your Poke ingest endpoint ID
- `APP_UPLOAD_TOKEN`: a random app password
- `URL_SIGNING_SECRET`: a random backend signing secret

If Poke only gave you one API key and no separate ingest token, use that key as `POKE_INGEST_TOKEN`.

Do not put the Poke token/API key inside the phone app.

## 1. Cloudflare Backend

Open Terminal in this repo:

```bash
cd /Users/vedanthjadhav/code/poke-phone/worker
npm install
npx wrangler login
```

Your browser opens. Log in to Cloudflare.

Then run these one by one:

```bash
npx wrangler secret put POKE_INGEST_TOKEN
npx wrangler secret put POKE_INGEST_ENDPOINT_ID
npx wrangler secret put APP_UPLOAD_TOKEN
npx wrangler secret put URL_SIGNING_SECRET
```

When it asks for values:

- For `POKE_INGEST_TOKEN`, paste your Poke ingest bearer token.
- For `POKE_INGEST_ENDPOINT_ID`, paste the ID from Poke's ingest URL.
- For `APP_UPLOAD_TOKEN`, paste the app token Codex gave you.
- For `URL_SIGNING_SECRET`, paste the signing secret Codex gave you.

Example: if Poke gives this URL:

```text
https://poke.com/api/v1/ingest/abc123
```

then `POKE_INGEST_ENDPOINT_ID` is:

```text
abc123
```

Deploy:

```bash
npx wrangler deploy
```

Copy the final Worker URL. It looks like:

```text
https://poke-phone-backend.YOURNAME.workers.dev
```

## 2. Build APK

1. Open `https://github.com/vedanth-jadhav/poke-phone`.
2. Click **Actions**.
3. Click **Build**.
4. Click **Run workflow**.
5. Wait until it finishes.
6. Download artifact **poke-phone-debug-apk**.
7. Unzip it.
8. Install `app-debug.apk` on your Android phone.

## 3. Phone App

Open **Poke Phone**.

Paste:

- **Worker URL**: the Cloudflare Worker URL from deploy.
- **Upload token**: the same `APP_UPLOAD_TOKEN` you put into Cloudflare.

Then:

1. Tap **Save settings**.
2. Tap **Test backend**.
3. Tap **Mic** and allow microphone.
4. Tap **Assistant**.
5. Set **Poke Phone** as default assistant.
6. Long-press power and speak.

## Notes

- Backend uses Cloudflare Durable Objects, not R2.
- No R2 bucket is needed.
- Audio expires after 24 hours.
- Keep voice notes short. The backend rejects audio above 20 MB.
- The backend sends JSON to Poke with `message`, `audio_url`, `mime_type`, `duration_ms`, `source`, `device`, and `expires_at`.
- The Worker sends to `https://poke.com/api/v1/ingest/{endpoint_id}`. `view-link.cx` is only Telegram's redirect wrapper, not the API URL.
