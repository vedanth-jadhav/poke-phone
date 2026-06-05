# Architecture

## Android

The Android app is native Kotlin because the trigger depends on Android system services. The core flow is:

1. Long-press power invokes the current default assistant.
2. `PokeVoiceInteractionService` creates a voice session.
3. The voice session launches `CaptureActivity`.
4. `CaptureActivity` records M4A audio with `MediaRecorder`.
5. The app uploads the file to the backend as multipart form data.
6. The backend sends Poke a JSON message containing an expiring audio URL.

Fallback triggers:

- Quick Settings tile.
- Ready notification.
- Manual `Start capture` button in the main app.

## Backend

The Cloudflare Worker owns the Poke API key and upload storage.

- `POST /v1/voice-note` accepts audio from the phone.
- A SQLite-backed Durable Object stores each raw M4A file in chunks with a 24 hour alarm.
- `GET /v1/audio/:id` serves signed temporary URLs.
- The Worker posts a Poke API message with the audio URL.
- The Durable Object alarm deletes old audio after 24 hours.

## Security

- Poke API key is never stored on the phone.
- Phone uploads should use `APP_UPLOAD_TOKEN`.
- Audio URLs are signed and time-limited.
- Audio is automatically removed after the retention window.
- Durable Object SQLite values are chunked below 2 MB, and large recordings are rejected at 20 MB.
