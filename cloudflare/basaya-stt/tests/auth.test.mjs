import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../src/index.js", import.meta.url), "utf8");
const { default: worker } = await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
const origin = "https://student.example";
const token = `test.${Buffer.from(JSON.stringify({ sub: "student" })).toString("base64url")}.test`;

test("speech authentication rejects anonymous access before reading audio or invoking AI", async t => {
  const cases = [
    { name: "missing APP_KEY", status: 401 },
    { name: "empty APP_KEY", key: "", status: 401 },
    { name: "whitespace APP_KEY", key: "   ", status: 401 },
    { name: "missing key cannot be replaced by any supplied key", headers: { "X-App-Key": "anything" }, status: 401 },
    { name: "empty keys do not authenticate", key: "", headers: { "X-App-Key": "" }, status: 401 },
    { name: "configured key missing from request", key: "test-key", status: 401 },
    { name: "incorrect app key", key: "test-key", headers: { "X-App-Key": "wrong" }, status: 401 },
    { name: "matching app key", key: "test-key", headers: { "X-App-Key": "test-key" }, status: 200 },
    { name: "unrecognized authorization does not bypass authentication", headers: { Authorization: "Basic invalid" }, status: 401 },
    { name: "malformed Bearer token", headers: { Origin: origin, Authorization: "Bearer invalid" }, status: 401 },
    { name: "valid Firebase token works without APP_KEY", headers: { Origin: origin, Authorization: `Bearer ${token}` }, profile: true, status: 200, lookups: 1 },
    { name: "valid Firebase token works with APP_KEY", key: "test-key", headers: { Origin: origin, Authorization: `Bearer ${token}` }, profile: true, status: 200, lookups: 1 },
    { name: "invalid Firebase token", headers: { Origin: origin, Authorization: `Bearer ${token}` }, status: 401, lookups: 1 },
    { name: "missing Firebase profile role", headers: { Origin: origin, Authorization: `Bearer ${token}` }, profile: {}, status: 401, lookups: 1 },
    { name: "Firebase token from unapproved origin", headers: { Origin: "https://unapproved.example", Authorization: `Bearer ${token}` }, status: 403 },
    { name: "Firebase token without origin", headers: { Authorization: `Bearer ${token}` }, status: 403 },
    { name: "invalid Bearer cannot fall back to matching app key", key: "test-key", headers: { Origin: origin, Authorization: "Bearer invalid", "X-App-Key": "test-key" }, status: 401 },
    { name: "allowed CORS preflight remains public", method: "OPTIONS", headers: { Origin: origin }, status: 204 },
  ];
  for (const scenario of cases) await t.test(scenario.name, async () => {
    let aiCalls = 0, audioReads = 0, lookups = 0;
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url, options) => {
      lookups++;
      assert.equal(url, "https://firestore.googleapis.com/v1/projects/local-test/databases/(default)/documents/users/student");
      assert.equal(options.headers.Authorization, `Bearer ${token}`);
      return new Response(JSON.stringify({ fields: scenario.profile === true ? { role: { stringValue: "student" } } : {} }), {
        status: scenario.profile ? 200 : 401,
      });
    };
    try {
      const env = { ALLOWED_ORIGIN: origin, FIREBASE_PROJECT_ID: "local-test",
        AI: { run: async () => { aiCalls++; return { text: "local stub" }; } } };
      if ("key" in scenario) env.APP_KEY = scenario.key;
      const request = new Request("https://local.test/transcribe", {
        method: scenario.method || "POST", headers: scenario.headers,
        ...(scenario.method ? {} : { body: new Uint8Array([1, 2, 3, 4]) }),
      });
      const readAudio = request.arrayBuffer.bind(request);
      request.arrayBuffer = () => { audioReads++; return readAudio(); };
      const response = await worker.fetch(request, env);
      assert.equal(response.status, scenario.status);
      assert.equal(aiCalls, scenario.status === 200 ? 1 : 0);
      assert.equal(audioReads, scenario.status === 200 ? 1 : 0);
      assert.equal(lookups, scenario.lookups || 0);
      if (scenario.status === 200) assert.equal((await response.json()).text, "local stub");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});
