# basaya-stt

Free speech-to-text for the pronunciation activity, using Cloudflare Workers AI
(Whisper large-v3-turbo, Tagalog).

## Deploy

```sh
cd cloudflare/basaya-stt
npx wrangler login          # once
npx wrangler deploy         # prints https://basaya-stt.<your-subdomain>.workers.dev
npx wrangler secret put APP_KEY   # optional, any random string
```

## Connect the app

Add to `local.properties` in the project root (not committed):

```
stt.workerUrl=https://basaya-stt.<your-subdomain>.workers.dev
stt.appKey=<the APP_KEY value, or leave empty>
```

Rebuild the app. With no `stt.workerUrl` the app uses Android speech recognition only.

## Cost

Workers AI includes 10,000 Neurons per day free. Whisper large-v3-turbo uses about
46.63 Neurons per audio minute, so roughly 200 minutes (thousands of 2-3 s word clips)
per day. On the free plan nothing is charged; extra requests are refused and the app
falls back to Android speech recognition until the next day.

## Test

```sh
curl -X POST --data-binary @word.wav -H "Content-Type: audio/wav" \
  https://basaya-stt.<your-subdomain>.workers.dev/transcribe
```
