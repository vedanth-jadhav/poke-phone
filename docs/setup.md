# Simple Setup

Poke Phone uses the official Poke API:

```text
POST https://poke.com/api/v1/inbound/api-message
```

The body is JSON. It includes the temporary `audio_url`, so Poke can fetch the raw M4A recording.

## 1. Cloudflare Secrets

You only need these Cloudflare secrets:

- `POKE_API_KEY`: your V2 Poke API key from Kitchen.
- `APP_UPLOAD_TOKEN`: the app password used by your phone.
- `URL_SIGNING_SECRET`: backend signing secret for temporary audio links.

Do **not** put `POKE_API_KEY` in the phone app.

Run:

```bash
cd /Users/vedanthjadhav/code/poke-phone/worker
npm install
npx wrangler login
npx wrangler secret put POKE_API_KEY
npx wrangler secret put APP_UPLOAD_TOKEN
npx wrangler secret put URL_SIGNING_SECRET
npx wrangler deploy
```

## 2. Phone App

Paste this into the phone app:

- **Worker URL**: `https://poke-phone-backend.vedanthjadhav2009.workers.dev`
- **Upload token**: same value as `APP_UPLOAD_TOKEN`

Then:

1. Tap **Save settings**.
2. Tap **Test backend**. This sends `hello from poke phone` to Poke.
3. Tap **Mic** and allow microphone.
4. Tap **Assistant**.
5. Set **Poke Phone** as default assistant.
6. Long-press power and speak.

## 3. Build APK

1. Open `https://github.com/vedanth-jadhav/poke-phone`.
2. Click **Actions**.
3. Click **Build**.
4. Click **Run workflow**.
5. Download artifact **poke-phone-debug-apk**.
6. Install `app-debug.apk`.

## Notes

- Backend uses Cloudflare Durable Objects, not R2.
- No R2 bucket is needed.
- Audio expires after 24 hours.
- Keep voice notes short. The backend rejects audio above 20 MB.
- Test backend is text-only. Real voice notes still include a temporary `audio_url`.
