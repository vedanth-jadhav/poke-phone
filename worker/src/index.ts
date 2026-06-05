import { AudioObject } from "./audio-object";
import { json, parsePositiveInt, requireBearer } from "./http";
import { signAudioUrl, verifyAudioSignature } from "./crypto";
import type { Env } from "./types";

export { AudioObject };

const DEFAULT_TTL_SECONDS = 86_400;
const DEFAULT_MAX_AUDIO_BYTES = 20 * 1024 * 1024;
const DEFAULT_POKE_API_URL = "https://poke.com/api/v1/inbound/api-message";
const DEFAULT_POKE_INGEST_BASE_URL = "https://api.poke.com/v1/ingest";

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
        pokeIngestConfigured: Boolean(resolvePokeToken(env) && resolvePokeEndpoint(env))
      });
    }

    if (request.method === "POST" && url.pathname === "/v1/test-message") {
      const authError = requireBearer(request, env.APP_UPLOAD_TOKEN);
      if (authError) return authError;
      const poke = await sendPoke(env, {
        message: "Test from Poke Phone. If you see this, the backend can reach Poke."
      });
      return json({ success: poke.ok, pokeStatus: poke.status, pokeBody: poke.body }, {
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
    body: await audio.arrayBuffer()
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
  const token = resolvePokeToken(env);
  const endpoint = resolvePokeEndpoint(env);

  if (!token || !endpoint) {
    return {
      ok: false,
      status: 500,
      body: "Missing POKE_INGEST_TOKEN/POKE_API_KEY or POKE_INGEST_ENDPOINT_ID/POKE_INGEST_URL"
    };
  }

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      authorization: `Bearer ${token}`,
      "content-type": "application/json"
    },
    body: JSON.stringify(body)
  });

  return {
    ok: response.ok,
    status: response.status,
    body: await response.text()
  };
}

function ttlSeconds(env: Env): number {
  return parsePositiveInt(env.AUDIO_TTL_SECONDS, DEFAULT_TTL_SECONDS);
}

function maxAudioBytes(env: Env): number {
  return Math.min(parsePositiveInt(env.MAX_AUDIO_BYTES, DEFAULT_MAX_AUDIO_BYTES), DEFAULT_MAX_AUDIO_BYTES);
}

function resolvePokeToken(env: Env): string | undefined {
  return env.POKE_INGEST_TOKEN || env.POKE_API_KEY;
}

function resolvePokeEndpoint(env: Env): string | undefined {
  if (env.POKE_INGEST_URL) return env.POKE_INGEST_URL;
  if (env.POKE_INGEST_ENDPOINT_ID) {
    return `${DEFAULT_POKE_INGEST_BASE_URL}/${encodeURIComponent(env.POKE_INGEST_ENDPOINT_ID)}`;
  }
  return env.POKE_API_URL || DEFAULT_POKE_API_URL;
}
