# Hermes Native for Android

A native **Kotlin + Jetpack Compose** client for
[Hermes WebUI](https://github.com/nesquena/hermes-webui) — Material 3
Expressive, Material You dynamic colour, and a chat surface built for a phone.

> **Not the official app.** Hermes WebUI already ships an Android app
> ([`hermes-webui/hermes-android`](https://github.com/hermes-webui/hermes-android)),
> which is a hardened **WebView shell** around the web UI. This project is the
> opposite choice: a fully native client that reimplements the interface in
> Compose. They are independent and can be installed side by side.

## Status

Early. The project currently builds and installs a themed shell; the chat
surface is being built milestone by milestone (see [Roadmap](#roadmap)).

## Install

Downloads are produced by CI and attached to
[Releases](../../releases/latest). From your phone, open:

```
https://github.com/dipandhali2021/hermes-android-native/releases/latest/download/hermes-native-latest.apk
```

You will need to allow installing from unknown sources. Requires **Android 8.0
(API 26)** or newer. Material You dynamic colour needs Android 12+; older
devices fall back to a palette generated from the Hermes gold seed.

Updates install over the previous version — a fixed signing key is committed so
the signature stays stable across builds.

## Connecting it to your server

The app can talk to either the Hermes WebUI or the agent's own API, and you pick
which on the Connect screen.

### Hermes WebUI (full client)

Point it at a running Hermes WebUI server. Three setups, in order of preference:

**1. Tailscale Serve (recommended).** Keeps the server on loopback while giving
your tailnet a real HTTPS hostname:

```bash
tailscale serve --bg 8787
```

Use the printed `https://…ts.net` URL. This is the only option where your
password and session cookie are not sent in cleartext.

**2. Direct tailnet IP.** Binds beyond loopback, so **always set a password**:

```bash
HERMES_WEBUI_HOST=0.0.0.0 HERMES_WEBUI_PASSWORD=your-secret ./start.sh
```

Then use `http://<tailscale-ip>:8787`.

**3. Localhost, same machine (emulator or on-device server).**
`http://localhost:8787` works with no configuration.

### Direct agent (chat, sessions, tools, approvals)

The same switch can bypass the WebUI and talk to the agent's own API server
(usually port 8642) with the key it was started with:

```bash
API_SERVER_KEY=your-secret
```

Enter the agent's address and that key on the Connect screen. Direct mode covers
chat, session history, tool calls and approval prompts. Uploads, workspace files,
voice input, profiles, prompts and the model catalog are WebUI surfaces, so those
controls are hidden in direct mode. The key is stored in the Android Keystore
(`EncryptedSharedPreferences`), never in plain preferences, and each mode uses a
separate HTTP client so a cookie and a Bearer key never cross.

### A note on transport security

Over `http://`, the password and the `hermes_session` cookie travel in
cleartext. That is why Tailscale Serve is listed first: it gives you a real
certificate and an end-to-end WireGuard-encrypted path. The app permits
cleartext because Android's network security config matches domains rather than
address ranges, so an arbitrary LAN or tailnet IP cannot be expressed as a
scoped exception — see `app/src/main/res/xml/network_security_config.xml` for
the full reasoning.

## Building

CI is the source of truth for buildability:

- **`.github/workflows/android.yml`** — unit tests, then a debug APK artifact on
  every push and pull request.
- **`.github/workflows/release.yml`** — on a `v*` tag (or manual dispatch), a
  signed release APK attached to a GitHub Release.

To build locally you need JDK 17 and an Android SDK with platform 36 and
build-tools 36.0.0:

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

## Architecture

Single `:app` module, package-by-feature — see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## What works

- **Connect** to a self-hosted server, with a connection test that distinguishes
  an unreachable host, a wrong address, a TLS problem and a bad password. The
  same screen switches to the agent's own API with a Bearer key.
- **Sessions** — list, search, create, rename, delete, with a live indicator on
  any session currently answering. Archive is a WebUI-only action.
- **Streaming chat** — tokens, model reasoning, tool calls with results and
  failures, and context-compression notices.
- **Composer** — send, stop, or `/queue` a message; mid-run steering is available
  in WebUI mode. Direct mode also surfaces tool-approval prompts.
- **Material 3 Expressive** — Material You dynamic colour on Android 12+, a
  generated Hermes-gold fallback below that, expressive shape and spring motion,
  and light/dark/system themes that change live.

## Roadmap

- [x] M0 — skeleton, theme, CI producing an APK
- [x] M1 — navigation, theme tokens, motion
- [x] M2 — settings persistence
- [x] M3 — networking core and the connect screen
- [x] M4 — session list
- [x] M5 — transcript rendering with markdown and code blocks
- [x] M6 — streaming chat over SSE
- [x] M7 — composer state machine (send / stop / steer / queue)
- [x] M8 — reasoning disclosure, tool cards, error retry
- [x] M9 — direct agent backend and the two-mode Connect switch
- [ ] Model and reasoning-effort pickers
- [ ] Image attachments
- [ ] Reconnect and gap recovery after backgrounding

Explicitly **not** here: offline caching (the agent runs on the server, so there
is nothing useful to do offline), cron, kanban, extensions, voice input, and Play
Store submission. Deferred deliberately, not forgotten.

## License

MIT. See [LICENSE](LICENSE).

The interface is inspired by [assistant-ui Elements](https://www.assistant-ui.com/elements);
no code is taken from it.

This is an independent client. It is not affiliated with, and shares no code
with, the official `hermes-webui/hermes-android` app or the Hermes desktop apps —
all of which are separate projects with their own maintainers.
