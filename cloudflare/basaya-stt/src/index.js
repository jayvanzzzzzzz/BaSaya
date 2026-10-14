/**
 * basaya-stt: POST /transcribe with a short WAV recording, returns { text }.
 *
 * Uses Workers AI Whisper large-v3-turbo with the language fixed to Tagalog.
 * The free plan includes 10,000 Neurons/day (about 200 audio minutes); past that
 * Workers AI refuses requests and the app falls back to Android speech recognition.
 *
 * Two kinds of callers:
 * - The Android app sends the X-App-Key header (when `APP_KEY` is set with
 *   `npx wrangler secret put APP_KEY`; set stt.appKey in the app's local.properties).
 * - The student website (BaSaya-web/student) can't keep a key secret, so it sends the
 *   student's Firebase sign-in token instead (Authorization: Bearer ...), from a site listed
 *   in `ALLOWED_ORIGIN`. Firestore checks the token by reading the caller's own profile, the
 *   same way basaya-tts does.
 */

const MODEL = "@cf/openai/whisper-large-v3-turbo";
const MAX_AUDIO_BYTES = 1024 * 1024; // ~30 s of 16 kHz mono PCM; word clips are far smaller

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return preflightResponse(request, env);
    }

    if (request.method !== "POST" || url.pathname !== "/transcribe") {
      return json({ error: "Not found" }, 404, request, env);
    }

    const authorization = request.headers.get("Authorization") || "";
    if (authorization.startsWith("Bearer ")) {
      if (!isAllowedOrigin(request, env)) {
        return json({ error: "Origin not allowed" }, 403, request, env);
      }
      if (!(await isSignedInUser(authorization.slice("Bearer ".length), env))) {
        return json({ error: "Unauthorized" }, 401, request, env);
      }
    } else if (env.APP_KEY && request.headers.get("X-App-Key") !== env.APP_KEY) {
      return json({ error: "Unauthorized" }, 401, request, env);
    }

    const audio = await request.arrayBuffer();
    if (audio.byteLength === 0 || audio.byteLength > MAX_AUDIO_BYTES) {
      return json({ error: "Invalid audio size" }, 400, request, env);
    }

    try {
      const result = await env.AI.run(MODEL, {
        audio: toBase64(audio),
        task: "transcribe",
        language: "tl",
      });
      return json({ text: (result?.text ?? "").trim() }, 200, request, env);
    } catch (error) {
      // Includes the daily free-allocation limit being reached.
      console.error("Whisper failed", error);
      return json({ error: "Transcription unavailable" }, 503, request, env);
    }
  },
};

/**
 * Firestore validates the Firebase sign-in token: reading users/{uid} with it only works
 * for a real, unexpired token allowed to read that profile, and the profile must exist.
 * This keeps strangers from spending the free Whisper allowance.
 */
async function isSignedInUser(token, env) {
  const payload = readJwtPayload(token);
  const uid = payload?.user_id || payload?.sub;
  if (!uid || !env.FIREBASE_PROJECT_ID) return false;

  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${encodeURIComponent(uid)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!response.ok) return false;
  const profile = await response.json();
  return Boolean(profile.fields?.role?.stringValue);
}

function readJwtPayload(token) {
  try {
    const part = token.split(".")[1];
    return JSON.parse(atob(part.replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return null;
  }
}

// ALLOWED_ORIGIN: comma-separated, e.g. "http://localhost,https://basaya.onrender.com".
function allowedOrigins(env) {
  return String(env.ALLOWED_ORIGIN || "")
    .split(",")
    .map((origin) => origin.trim().replace(/\/$/, ""))
    .filter(Boolean);
}

function isAllowedOrigin(request, env) {
  const origin = request.headers.get("Origin");
  return Boolean(origin) && allowedOrigins(env).includes(origin);
}

function corsHeaders(request, env) {
  if (!isAllowedOrigin(request, env)) return {};
  return {
    "Access-Control-Allow-Origin": request.headers.get("Origin"),
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin",
  };
}

function preflightResponse(request, env) {
  if (!isAllowedOrigin(request, env)) return new Response(null, { status: 403 });
  return new Response(null, { status: 204, headers: corsHeaders(request, env) });
}

function toBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

function json(body, status, request, env) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders(request, env) },
  });
}
