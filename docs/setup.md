# Simple Setup

You only need to paste 3 secrets into Cloudflare:

- `POKE_API_KEY`: your real Poke API key from `https://poke.com/kitchen`
- `APP_UPLOAD_TOKEN`: a random app password
- `URL_SIGNING_SECRET`: a random backend signing secret

Do not put the Poke API key inside the phone app.

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
npx wrangler secret put POKE_API_KEY
npx wrangler secret put APP_UPLOAD_TOKEN
npx wrangler secret put URL_SIGNING_SECRET
```

When it asks for values:

- For `POKE_API_KEY`, paste your Poke API key.
- For `APP_UPLOAD_TOKEN`, paste the app token Codex gave you.
- For `URL_SIGNING_SECRET`, paste the signing secret Codex gave you.

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
