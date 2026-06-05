const textEncoder = new TextEncoder();

export async function hmacHex(secret: string, payload: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    textEncoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, textEncoder.encode(payload));
  return [...new Uint8Array(signature)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

export async function signAudioUrl(
  baseUrl: string,
  id: string,
  expiresAtSeconds: number,
  secret: string
): Promise<string> {
  const payload = `${id}.${expiresAtSeconds}`;
  const sig = await hmacHex(secret, payload);
  const url = new URL(`/v1/audio/${encodeURIComponent(id)}`, baseUrl);
  url.searchParams.set("exp", String(expiresAtSeconds));
  url.searchParams.set("sig", sig);
  return url.toString();
}

export async function verifyAudioSignature(
  id: string,
  expiresAtSeconds: number,
  sig: string,
  secret: string,
  nowSeconds = Math.floor(Date.now() / 1000)
): Promise<boolean> {
  if (!Number.isFinite(expiresAtSeconds) || expiresAtSeconds < nowSeconds) return false;
  const expected = await hmacHex(secret, `${id}.${expiresAtSeconds}`);
  return timingSafeEqual(expected, sig);
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let result = 0;
  for (let i = 0; i < a.length; i += 1) {
    result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return result === 0;
}
