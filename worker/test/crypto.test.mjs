import assert from "node:assert/strict";
import test from "node:test";
import { signAudioUrl, verifyAudioSignature } from "../.tmp/test-build/crypto.js";

test("audio URL signatures accept valid signatures", async () => {
  const url = await signAudioUrl("https://example.com", "note-1", 2_000_000_000, "secret");
  const parsed = new URL(url);

  const valid = await verifyAudioSignature(
    "note-1",
    Number(parsed.searchParams.get("exp")),
    parsed.searchParams.get("sig") ?? "",
    "secret",
    1_999_999_999
  );

  assert.equal(valid, true);
});

test("audio URL signatures reject expired signatures", async () => {
  const url = await signAudioUrl("https://example.com", "note-1", 100, "secret");
  const parsed = new URL(url);

  const valid = await verifyAudioSignature(
    "note-1",
    Number(parsed.searchParams.get("exp")),
    parsed.searchParams.get("sig") ?? "",
    "secret",
    101
  );

  assert.equal(valid, false);
});
