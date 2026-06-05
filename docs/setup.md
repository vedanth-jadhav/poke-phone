# Setup Checklist

## 1. Create Poke API Key

1. Open `https://poke.com/kitchen`.
2. Go to API Keys.
3. Create a new V2 API key.
4. Keep it private. Do not paste it into the Android app.

## 2. Deploy Backend On Cloudflare Free Tier

1. Copy `worker/wrangler.toml.example` to `worker/wrangler.toml`.
2. Edit `PUBLIC_BASE_URL` after your first deploy gives you the Worker URL.
3. Keep the Durable Object binding and migration exactly as shown in the example config.
4. Set Worker secrets:

```bash
cd worker
npx wrangler secret put POKE_API_KEY
npx wrangler secret put APP_UPLOAD_TOKEN
npx wrangler secret put URL_SIGNING_SECRET
```

Use long random values for `APP_UPLOAD_TOKEN` and `URL_SIGNING_SECRET`.

5. Deploy:

```bash
npm install
npx wrangler deploy
```

6. Set the deployed Worker URL as `PUBLIC_BASE_URL` in `wrangler.toml`, then deploy again.

## 3. Build APK In GitHub Actions

1. Push this folder as its own GitHub repo.
2. Open the repository Actions tab.
3. Run the `Build` workflow.
4. Download `poke-phone-debug-apk` from the workflow artifacts.
5. Install the APK manually on your phone.

## 4. Phone Setup

1. Open Poke Phone.
2. Enter the Worker base URL, for example `https://poke-phone.yourname.workers.dev`.
3. Enter the `APP_UPLOAD_TOKEN` you created.
4. Grant microphone permission.
5. Grant notification permission if you want the fallback notification.
6. Tap `Default assistant settings`.
7. Set Poke Phone as the default digital assistant.
8. In Nothing OS settings, make sure power-button assistant invocation is enabled.

## 5. Test

1. Tap `Test backend`.
2. Tap `Start capture` inside the app.
3. Confirm Poke receives a message.
4. Long-press power while the phone is unlocked.
5. Speak, then stop using the visible stop control or silence timeout.
6. Confirm the UI shows `saving`, `sending`, then `done`.

## Notes

- The app sends raw M4A audio by URL because the Poke API docs describe JSON messages, not direct file attachments.
- Audio files expire after 24 hours using a Durable Object alarm.
- SQLite-backed Durable Objects are available on the Workers Free plan. The Worker chunks audio because a single SQLite value is limited to 2 MB.
- The Worker rejects audio above 20 MB. This is designed for short personal voice notes, not long recordings.
