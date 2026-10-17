# Handoff — model picker (branch `feat/model-picker`; combined test build `test/model-picker-home-send`)

## Status
Rounds 1–3 (model picker, home-send fix, chip/lockup/attachments) and Round 4
(voice: auto-send, both modes) and Round 5 + 5.1 (the `+` attach panel) are
implemented, CI-green on both branches, and awaiting a device pass. **No
version bump this cycle** (per instruction); merge to master only on the
user's say-so.

## What changed in Round 4
- **The paperclip is a `+` now** (both composers). It rotates 45° into a
  tinted `×` while the panel is open (`Add` ↔ `Close attachments`).
- **The attach panel replaces the old bottom sheet everywhere.** A rounded
  card below the composer — **a separate card, not merged into the composer's
  surface** (Round 5.1) — with a grab handle, then Photos / Files / Camera as
  list rows (icon + title + supporting line), then
- **Voice input, two modes with a pill toggle in the panel:**
  - **Transcribe** (default): record → `POST /api/transcribe` → the transcript
    **sends automatically** (no ask, no draft): text + staged images in one turn.
  - **Voice note**: record → the audio file sends automatically as an
    attachment (`voice-note.webm`, image-backed turns ride along).
  - 503 message surfaces verbatim. Mode persists per app run.
- **Keyboard swap:** opening the panel clears field focus so the keyboard
  leaves *and the panel takes its exact place* (panel minHeight = tallest
  remembered keyboard height − gesture bar; 320 dp fallback on first open).
  The `+`/`×` is boutonnière-deep: composer holds still through the swap.

## Rounds 1–3 (recap)
Model picker sheet (search, groups, SELECTED, Show more), label-only model
chip, 32dp lockup, attachments both composers (Photo Picker, 10 files, 20 MB,
session-scoped `/api/upload`, retry without re-upload), home send fix
(state-driven spinner + failure snackbar), offline icon monograms with
relaunch recovery.

## CI evidence
- Round 1: [run 38923737811](https://github.com/dipandhali2021/hermes-android-native/actions/runs/38923737811) — green (`9748423`).
- Round 2 feature build: [run 39010286205](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39010286205) — green (`991d50e`).
- Round 2 combined build: [run 39010581879](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39010581879) — green (`12d49dd`).
- Round 3 feature build: [run 39013965145](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39013965145) — green (`2cac2aa`).
- Round 3 combined build: [run 39013965156](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39013965156) — green (`707ef24`).
- One intermediate feat push failed ([run 39013171506](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39013171506)):
  the cherry-pick transplant carried fix/home-send-only identifiers into feat.
  Repaired with feat's own semantics before this evidence; see the progress
  ledger.
- Round 5 feature build: [run 39022149054](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39022149054) — green (`f1edc59`).
- Round 5.1 feature build: [run 39023202460](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39023202460) — green (`2f0fe29`).
- Local: `./gradlew :app:testDebugUnitTest` green at each task commit (110
  tests on `feat/model-picker`, 112 on the combined build).
- Round 5.1 combined build: [run 39031187257](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39031187257) — green (`1302a06`) (superseded UI).
- Round 5.2 feature build: [run 39032241812](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39032241812) — green (`b0e8bb5`).
- Round 5.2 combined build: [run 39032335261](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39032335261) — green (`37083a0`).

## APK paths
- **Combined build — use this one:** `/tmp/opencode/mp-r5-combined/hermes-native-debug-apk/app-debug.apk`
  (md5 `cc1db9722ec4190543ee0416e5fd4408`, commit `37083a0`). Contains the model
  picker, the home-send fix, and all of Rounds 3–5.2. The same file is also
  kept at the familiar path `/tmp/opencode/model-picker-home-send-apk/app-debug.apk`.

## Device checklist
1. Install the combined APK (upgrade over the previous build is fine).
2. **Two cards, not one:** tap `+` → the composer keeps its rounded card and
   the panel appears as its **own separate card below it**. Composer stays
   put through the keyboard↔panel swap — no jump, no reflow.
3. **Tile card is back (round 5.2):** the panel is the round-5 design again —
   `Photos` / `Camera` / `Files` tonal tiles with the press-scale, then the
   voice section below a divider. No list rows, no grab handle (the 5.1 list
   restyle was withdrawn at the user's request — only the separation
   stayed).
4. **Toggle:** `+` rotates into a `×` and takes the accent tint; tapping it
   again (or back, or tapping the text field) closes the panel; tapping the
   field also raises the keyboard in the same move.
4b. **Mode on the mic (round 5.2):** with Voice note selected in the panel,
   the composer's mic shows the waveform icon (Transcribe shows the mic);
   switching modes flips it live while the panel is open — both composers.
5. **Tiles→rows work the same:** Photos opens the picker; Camera captures and
   stages; Files opens the document picker; rows launch *after* the panel
   closes.
6. **Mic behavior** (round 4, still binding): mic sits between text field and
   send; tapping it with the panel open closes the panel first; recording
   strip replaces the toolbar row; **both voice modes auto-send** —
   transcribe lands as text and sends by itself, voice note sends the audio
   file. A 503 shows the server's message verbatim.
7. **First-open height:** the very first panel open uses the 320 dp fallback
   (keyboard not yet measured); subsequent opens match the keyboard height.
8. **Home:** the FAB menu opens over the composer; a tap anywhere on the
   composer **or its panel card** closes the menu; attach flow on home sends
   with the route (message + images in one turn).
9. **Chat mid-stream:** attachments still work while a reply streams (tray
   above the field, send is the stop button).
10. Regression sanity: chip label-only (no arrow) opens the picker sheet; home
    send spinner resets on failure with snackbar; locks express as before.

## Merge order (important)
1. `fix/home-send` → master **first** (its own device pass came earlier).
2. Then `feat/model-picker` → master. Conflicts will appear on the files both
   sides rewrote — `NewSessionResponse`/`SessionRepository.create`,
   `HomeScreen` (composer send path), `ChatViewModel` (loader line + seed
   guard), `SessionsViewModel`, `HermesRoot`, `ChatScreen`. The resolution is
   already proven on `test/model-picker-home-send`, whose current build
   (`37083a0`) resolves them as the union: pick rides with `NewSessionRequest`,
   response read nested, state-driven `isStartingChat` + snackbar, and the
   Round 3–5.1 composer/attach plumbing.
3. Do **not** merge `test/model-picker-home-send` itself — it exists only as
   the combined test vehicle.

## Mic research (decision pending — resolved for the client side)
The recorder (round 4): both modes implemented; pending only from the user is
the device pass. Server contract unchanged: `POST /api/transcribe` multipart
`file` → `{"ok": true, "transcript": "…"}`; `/api/upload` `session_id`-scoped;
503 message surfaced verbatim.

## Release
No version bump in this cycle. Merge to master + release only when the user
says so.
