# Poke Phone

Personal Android capture app for sending raw voice rambles to Poke.

The v1 app is built for a Nothing Phone 3a and uses Android's default assistant path: set this app as the default digital assistant, then long-press power to launch capture. The app records an M4A audio note, uploads it to a Cloudflare Worker/Durable Objects backend, and the backend sends Poke a JSON message with an expiring audio URL.

## What Is Included

- Native Kotlin Android app using Jetpack Compose.
- Assistant launch service for power-button long press.
- Quick Settings tile and ready notification fallback.
- Cloudflare Worker backend with Durable Objects storage and signed audio URLs.
- GitHub Actions build that produces a debug APK artifact.

## What You Need

- A Poke V2 API key from Kitchen.
- A free Cloudflare account with Workers and Durable Objects enabled.
- GitHub Actions enabled for this repo.
- Manual APK install on the phone, since no USB cable is available.

## Local Status

This machine does not currently have a Java runtime or Android SDK visible in the shell, so Android builds are expected to run in GitHub Actions. The Worker can be developed locally with Node.

## Setup

See [docs/setup.md](docs/setup.md) for the exact deployment and phone setup checklist.

## Storage Choice

This uses SQLite-backed Durable Objects instead of R2 because R2 can ask for card verification. Audio is chunked under Cloudflare's 2 MB SQLite value limit, and the Worker rejects files above 20 MB so it stays realistic on the free tier.

## Important Reality Check

Android does not let a normal app freely own the power button. This app uses the official assistant route: it must be selected as the default digital assistant, and the phone must have power-button assistant invocation enabled. If Nothing OS blocks that path, use the Quick Settings tile fallback.
