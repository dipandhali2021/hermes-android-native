# Agent instructions for Hermes Native (Android)

## Build

CI is the source of truth. Push and let `.github/workflows/android.yml` build —
local builds are not required and this project's own dev machine is disk
constrained.

If you must build locally: JDK 17, an Android SDK with platform 36 and
build-tools 36.0.0, and `local.properties` containing `sdk.dir=...`. Then
`./gradlew :app:assembleDebug`.

## Version constraints — do not bump casually

Two pins look out of date and are not:

- **Coil is absent.** Every 3.4+ release requires `kotlin-stdlib` 2.4.0, whose
  class metadata this project's Kotlin compiler (2.2.x, reads up to 2.3.0)
  refuses. Bumping Kotlin or adding Coil without changing both will fail the
  build with "Module was compiled with an incompatible version of Kotlin".
- **Compose BOM 2026.06.01** is the newest BOM that pins `ui` 1.11.4. Newer BOMs
  pin `ui` 1.12.x, which requires `compileSdk 37` and therefore AGP 9.1+.
- **material3 1.4.0** exposes `MaterialExpressiveTheme` and `MotionScheme` as
  `internal`. They become public in 1.5.0-beta01, which needs AGP 9.1 and
  compileSdk 37. Until that is in a BOM, expressive motion lives in
  `ui/theme/Motion.kt` against Compose's stable spring API.
- **build-tools 36.0.0 / platform android-36** and **Gradle 8.14.3** match what
  CI installs.

Before raising any of these, verify the artifact's own constraints rather than
trusting a changelog:

```bash
curl -s <artifact>.aar | python3 -c "import sys,zipfile,io; \
  z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())); \
  print(z.read('META-INF/com/android/build/gradle/aar-metadata.properties').decode())"
```

## Protocol rules that are not negotiable

- **`done` does not close the SSE stream.** `title` and `stream_end` follow it.
  `ChatEvent.isClose` is the only place that decides, and
  `SseEventParser.isCloseName` mirrors the server's own close set. Do not add a
  second close path.
- **Never send `Origin`, `Referer` or `Sec-Fetch-*`.** The server exempts
  non-browser clients from CSRF only when those are absent, and
  `Sec-Fetch-Site: cross-site` is an outright rejection.
- **`hermes_session` is a cookie, not a token.** WebUI mode authenticates with
  that cookie only. The jar is cleared whenever the server URL changes.
- **Direct-agent mode is Bearer only.** It sends `Authorization: Bearer
  <API_SERVER_KEY>` from a separate OkHttp client that has no cookie jar; a
  WebUI cookie must never ride an agent request and a key must never ride a
  WebUI request. The key lives only in `SecretStore`
  (`EncryptedSharedPreferences`), never DataStore, prefs, or logs.
- **Agent run events are unnamed frames.** `/v1/runs/{id}/events` writes
  `data: {...}` with `event`/`type` inside the payload, so that field wins over
  the SSE name. The deployed agent exposes no steer, upload, audio, or archive
  endpoint; those controls are hidden in direct mode rather than failing.
- **The server may be older or newer than this app.** Every wire field is
  optional with a default; unknown event names are carried as `Unknown` rather
  than dropped, so a protocol addition is visible in logs.

## Design rules

- Colours come from `MaterialTheme.colorScheme`. No `Color(0x...)` outside
  `ui/theme/Color.kt`.
- Duration, spring and easing values come from `ui/theme/Motion.kt`. No raw
  `tween(...)` in a component.
- Assistant prose is unboxed full width; only user messages are bubbles. Long
  markdown in a tinted bubble fights code blocks and syntax colour.
- Tool traces are metadata: one quiet line until opened.
- `ColorContrastTest` asserts every on/container pair clears WCAG AA. If you edit
  the fallback palette and it fails, the palette is wrong, not the test.

## Testing

`./gradlew :app:testDebugUnitTest`. The valuable tests here drive the SSE parser
with the server's real documented vocabulary and the reducer with real event
sequences; fixtures invented from this client's assumptions would test nothing.
