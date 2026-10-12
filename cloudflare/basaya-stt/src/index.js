/**
 * basaya-stt: POST /transcribe with a short WAV recording, returns { text }.
 *
 * Uses Workers AI Whisper large-v3-turbo with the language fixed to Tagalog.
 * The free plan includes 10,000 Neurons/day (about 200 audio minutes); past that
 * Workers AI refuses requests and the app falls back to Android speech recognition.
 *
 * Optional: `npx wrangler secret put APP_KEY` makes the Worker require the same
 * value in the X-App-Key header (set stt.appKey in the app's local.properties).
 */

const MODEL = "@cf/openai/whisper-large-v3-turbo";
const MAX_AUDIO_BYTES = 1024 * 1024; // ~30 s of 16 kHz mono PCM; word clips are far smaller

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method !== "POST" || url.pathname !== "/transcribe") {
      return json({ error: "Not found" }, 404);
    }

    if (env.APP_KEY && request.headers.get("X-App-Key") !== env.APP_KEY) {
      return json({ error: "Unauthorized" }, 401);
    }

    const audio = await request.arrayBuffer();
    if (audio.byteLength === 0 || audio.byteLength > MAX_AUDIO_BYTES) {
      return json({ error: "Invalid audio size" }, 400);
    }

    try {
      const result = await env.AI.run(MODEL, {
        audio: toBase64(audio),
        task: "transcribe",
        language: "tl",
      });
      return json({ text: (result?.text ?? "").trim() });
    } catch (error) {
      // Includes the daily free-allocation limit being reached.
      console.error("Whisper failed", error);
      return json({ error: "Transcription unavailable" }, 503);
    }
  },
};

function toBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
