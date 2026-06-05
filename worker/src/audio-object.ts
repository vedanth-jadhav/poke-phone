import { DurableObject } from "cloudflare:workers";
import { corsHeaders, json } from "./http";
import type { Env } from "./types";

const CHUNK_SIZE = 1_900_000;

type MetaRow = { value: string };
type ChunkRow = { idx: number; data: ArrayBuffer };

export class AudioObject extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.ctx.blockConcurrencyWhile(async () => {
      this.ctx.storage.sql.exec(
        "CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
      );
      this.ctx.storage.sql.exec(
        "CREATE TABLE IF NOT EXISTS chunks (idx INTEGER PRIMARY KEY, data BLOB NOT NULL)"
      );
    });
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "POST" && url.pathname === "/store") {
      return this.store(request);
    }

    if (request.method === "GET" && url.pathname === "/audio") {
      return this.audio();
    }

    if (request.method === "DELETE" && url.pathname === "/audio") {
      await this.ctx.storage.deleteAll();
      return json({ success: true });
    }

    return json({ success: false, error: "not found" }, { status: 404 });
  }

  async alarm(): Promise<void> {
    await this.ctx.storage.deleteAll();
  }

  private async store(request: Request): Promise<Response> {
    const bytes = await request.arrayBuffer();
    const ttlSeconds = Number.parseInt(request.headers.get("x-audio-ttl") ?? "86400", 10);
    const mimeType = request.headers.get("x-audio-mime") || "audio/mp4";
    const durationMs = request.headers.get("x-duration-ms") || "0";
    const source = request.headers.get("x-source") || "unknown";
    const device = request.headers.get("x-device") || "unknown";
    const createdAt = new Date().toISOString();
    const expiresAt = Date.now() + ttlSeconds * 1000;

    this.ctx.storage.sql.exec("DELETE FROM chunks");
    this.ctx.storage.sql.exec("DELETE FROM meta");

    const audio = new Uint8Array(bytes);
    let idx = 0;
    for (let offset = 0; offset < audio.byteLength; offset += CHUNK_SIZE) {
      const chunk = audio.slice(offset, offset + CHUNK_SIZE);
      this.ctx.storage.sql.exec(
        "INSERT INTO chunks (idx, data) VALUES (?, ?)",
        idx,
        chunk.buffer
      );
      idx += 1;
    }

    const metadata = {
      mimeType,
      durationMs,
      source,
      device,
      createdAt,
      byteLength: String(audio.byteLength),
      chunks: String(idx)
    };

    for (const [key, value] of Object.entries(metadata)) {
      this.ctx.storage.sql.exec("INSERT INTO meta (key, value) VALUES (?, ?)", key, value);
    }

    await this.ctx.storage.setAlarm(expiresAt);
    return json({ success: true, chunks: idx, byteLength: audio.byteLength });
  }

  private audio(): Response {
    const mimeType = this.readMeta("mimeType") || "audio/mp4";
    const createdAt = this.readMeta("createdAt") || new Date().toISOString();
    const byteLength = Number.parseInt(this.readMeta("byteLength") || "0", 10);

    if (!byteLength) {
      return json({ success: false, error: "audio not found" }, { status: 404 });
    }

    const output = new Uint8Array(byteLength);
    let offset = 0;
    for (const row of this.ctx.storage.sql.exec<ChunkRow>(
      "SELECT idx, data FROM chunks ORDER BY idx ASC"
    )) {
      const chunk = new Uint8Array(row.data);
      output.set(chunk, offset);
      offset += chunk.byteLength;
    }

    return new Response(output, {
      headers: {
        "content-type": mimeType,
        "content-length": String(output.byteLength),
        "cache-control": "private, max-age=30",
        "x-created-at": createdAt,
        ...corsHeaders()
      }
    });
  }

  private readMeta(key: string): string | null {
    const rows = this.ctx.storage.sql.exec<MetaRow>(
      "SELECT value FROM meta WHERE key = ? LIMIT 1",
      key
    );
    for (const row of rows) {
      return row.value;
    }
    return null;
  }
}
