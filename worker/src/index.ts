import { AudioObject } from "./audio-object";
import { json, parsePositiveInt, requireBearer } from "./http";
import { signAudioUrl, verifyAudioSignature } from "./crypto";
import type { Env } from "./types";

export { AudioObject };

const DEFAULT_TTL_SECONDS = 86_400;
const DEFAULT_MAX_AUDIO_BYTES = 20 * 1024 * 1024;
const DEFAULT_POKE_API_URL = "https://poke.com/api/v1/inbound/api-message";
const EMPTY_M4A_BASE64 =
  "AAAAHGZ0eXBpc29tAAACAGlzb21pc28ybXA0MQAAAAhtZGF0";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "GET,POST,OPTIONS",
          "access-control-allow-headers": "authorization,content-type"
        }
      });
    }

    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/v1/health") {
      return json({
        success: true,
        storage: "durable-object-sqlite",
        maxAudioBytes: maxAudioBytes(env),
        retentionSeconds: ttlSeconds(env),
        pokeApiConfigured: Boolean(resolvePokeApiKey(env)),
        pokeApiUrl: resolvePokeApiUrl(env)
      });
    }

    if (request.method === "POST" && url.pathname === "/v1/test-message") {
      const authError = requireBearer(request, env.APP_UPLOAD_TOKEN);
      if (authError) return authError;
      const id = `test-${crypto.randomUUID()}`;
      const ttl = ttlSeconds(env);
      const expiresAtSeconds = Math.floor(Date.now() / 1000) + ttl;
      const publicBaseUrl = env.PUBLIC_BASE_URL || new URL(request.url).origin;
      const audioUrl = await signAudioUrl(publicBaseUrl, id, expiresAtSeconds, env.URL_SIGNING_SECRET);
      const audioBytes = base64ToArrayBuffer(EMPTY_M4A_BASE64);
      const audioObject = env.AUDIO_STORE.get(env.AUDIO_STORE.idFromName(id));
      const storeResponse = await audioObject.fetch("https://audio.internal/store", {
        method: "POST",
        headers: {
          "content-type": "application/octet-stream",
          "x-audio-ttl": String(ttl),
          "x-audio-mime": "audio/mp4",
          "x-duration-ms": "0",
          "x-source": "test-message",
          "x-device": "worker-test"
        },
        body: audioBytes
      });

      if (!storeResponse.ok) {
        return json(
          { success: false, error: "failed to store test audio", body: await storeResponse.text() },
          { status: 500 }
        );
      }

      const poke = await sendPoke(env, {
        message: "Manual test from Poke Phone backend. This checks the same official api-message schema as a real voice note.",
        audio_url: audioUrl,
        mime_type: "audio/mp4",
        duration_ms: "0",
        source: "worker-test",
        device: "worker-test",
        expires_at: new Date(expiresAtSeconds * 1000).toISOString()
      });
      return json({ success: poke.ok, id, audioUrl, pokeStatus: poke.status, pokeBody: poke.body }, {
        status: poke.ok ? 200 : 502
      });
    }

    if (request.method === "POST" && url.pathname === "/v1/voice-note") {
      return receiveVoiceNote(request, env);
    }

    if (request.method === "GET" && url.pathname.startsWith("/v1/audio/")) {
      return serveAudio(url, env);
    }

    return json({ success: false, error: "not found" }, { status: 404 });
  }
};

async function receiveVoiceNote(request: Request, env: Env): Promise<Response> {
  const authError = requireBearer(request, env.APP_UPLOAD_TOKEN);
  if (authError) return authError;

  const form = await request.formData();
  const audio = form.get("audio");
  if (!(audio instanceof File)) {
    return json({ success: false, error: "audio file is required" }, { status: 400 });
  }

  const maxBytes = maxAudioBytes(env);
  if (audio.size <= 0) {
    return json({ success: false, error: "audio file is empty" }, { status: 400 });
  }
  if (audio.size > maxBytes) {
    return json(
      { success: false, error: "audio file too large", maxAudioBytes: maxBytes },
      { status: 413 }
    );
  }

  const id = crypto.randomUUID();
  const ttl = ttlSeconds(env);
  const expiresAtSeconds = Math.floor(Date.now() / 1000) + ttl;
  const publicBaseUrl = env.PUBLIC_BASE_URL || new URL(request.url).origin;
  const audioUrl = await signAudioUrl(publicBaseUrl, id, expiresAtSeconds, env.URL_SIGNING_SECRET);

  const objectId = env.AUDIO_STORE.idFromName(id);
  const audioObject = env.AUDIO_STORE.get(objectId);
  const audioBytes = await audio.arrayBuffer();
  const storeResponse = await audioObject.fetch("https://audio.internal/store", {
    method: "POST",
    headers: {
      "content-type": "application/octet-stream",
      "x-audio-ttl": String(ttl),
      "x-audio-mime": audio.type || "audio/mp4",
      "x-duration-ms": String(form.get("duration_ms") || "0"),
      "x-source": String(form.get("source") || "android"),
      "x-device": String(form.get("device") || "unknown")
    },
    body: audioBytes
  });

  if (!storeResponse.ok) {
    return json(
      { success: false, error: "failed to store audio", body: await storeResponse.text() },
      { status: 500 }
    );
  }

  const poke = await sendPoke(env, {
    message: "Transcribe and process this Poke Phone voice note. The attached URL is a raw M4A audio recording from my phone.",
    audio_url: audioUrl,
    mime_type: audio.type || "audio/mp4",
    duration_ms: String(form.get("duration_ms") || "0"),
    source: String(form.get("source") || "android"),
    device: String(form.get("device") || "unknown"),
    expires_at: new Date(expiresAtSeconds * 1000).toISOString()
  });

  return json(
    {
      success: poke.ok,
      id,
      audioUrl,
      expiresAt: new Date(expiresAtSeconds * 1000).toISOString(),
      pokeStatus: poke.status,
      pokeBody: poke.body
    },
    { status: poke.ok ? 200 : 502 }
  );
}

async function serveAudio(url: URL, env: Env): Promise<Response> {
  const id = decodeURIComponent(url.pathname.replace("/v1/audio/", ""));
  const exp = Number.parseInt(url.searchParams.get("exp") ?? "", 10);
  const sig = url.searchParams.get("sig") ?? "";
  const valid = await verifyAudioSignature(id, exp, sig, env.URL_SIGNING_SECRET);
  if (!valid) {
    return json({ success: false, error: "invalid or expired audio URL" }, { status: 403 });
  }

  const objectId = env.AUDIO_STORE.idFromName(id);
  const audioObject = env.AUDIO_STORE.get(objectId);
  return audioObject.fetch("https://audio.internal/audio");
}

async function sendPoke(env: Env, body: Record<string, unknown>): Promise<{
  ok: boolean;
  status: number;
  body: string;
}> {
  const apiKey = resolvePokeApiKey(env);
  const endpoint = resolvePokeApiUrl(env);

  if (!apiKey) {
    console.error("Poke config missing: check POKE_API_KEY");
    return {
      ok: false,
      status: 500,
      body: "Missing POKE_API_KEY"
    };
  }

  const jsonBody = JSON.stringify(body);
  try {
    console.log(`POST -> ${endpoint}`);
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        authorization: `Bearer ${apiKey}`,
        "content-type": "application/json",
        "x-poke-source": "poke-phone-worker"
      },
      body: jsonBody
    });

    const responseBody = await response.text();
    if (response.ok) {
      console.log(`Poke API hit: ${endpoint} (${response.status})`);
    } else {
      console.warn(`Poke API miss: ${endpoint} -> ${response.status}: ${responseBody}`);
    }

    return {
      ok: response.ok,
      status: response.status,
      body: responseBody
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`Poke API fetch error: ${endpoint}: ${message}`);
    return { ok: false, status: 0, body: message };
  }
}

function ttlSeconds(env: Env): number {
  return parsePositiveInt(env.AUDIO_TTL_SECONDS, DEFAULT_TTL_SECONDS);
}

function maxAudioBytes(env: Env): number {
  return Math.min(parsePositiveInt(env.MAX_AUDIO_BYTES, DEFAULT_MAX_AUDIO_BYTES), DEFAULT_MAX_AUDIO_BYTES);
}

function resolvePokeApiKey(env: Env): string | undefined {
  return env.POKE_API_KEY;
}

function resolvePokeApiUrl(env: Env): string {
  return env.POKE_API_URL || DEFAULT_POKE_API_URL;
}

function base64ToArrayBuffer(value: string): ArrayBuffer {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}
