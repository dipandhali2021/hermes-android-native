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

The app is a client; it needs a running Hermes WebUI server. Three setups, in
order of preference:

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

## Roadmap

- [x] M0 — project skeleton, M3 Expressive theme, CI producing an APK
- [ ] M1 — navigation graph and full theme tokens
- [ ] M2 — settings persistence (server URL, theme mode)
- [ ] M3 — networking core and the connect screen
- [ ] M4 — session list
- [ ] M5 — transcript rendering (markdown, code blocks)
- [ ] M6 — streaming chat over SSE
- [ ] M7 — composer state machine (send / stop / steer)
- [ ] M8 — reasoning disclosure, tool cards, error retry

Explicitly **not** in v1: offline caching, cron, kanban, extensions, voice
input, and Play Store submission. These are deferred deliberately rather than
forgotten.

## License

MIT. See [LICENSE](LICENSE).

The interface is inspired by [assistant-ui Elements](https://www.assistant-ui.com/elements);
no code is taken from it.
