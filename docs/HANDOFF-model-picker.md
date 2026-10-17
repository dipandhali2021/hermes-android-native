# Handoff — model picker (branch `feat/model-picker`; combined test build `test/model-picker-home-send`)

## Status
Round 1 (Tasks 1–9), Round 2 device-feedback amendments (Tasks 11–12) and
Round 3 device-feedback amendments (Tasks 14–17) are implemented, CI-green on
both branches, and awaiting a device pass. No version bump this cycle (per
instruction). The mic button is intentionally absent this round — the user
asked for the research first ("find what is this then i will tell you how to
process") — see Mic research.

## What changed in Round 3
- **The model chip is a label now.** The chevron-down is gone (device
  feedback). The pill alone reads as the control that opens the picker sheet,
  and the current model is still exposed to accessibility as
  `stateDescription`.
- **Lockup render fix.** `ic_hermes_agent.xml` is flat absolute-path data — no
  `<group>` transforms — and the home lockup renders at 32dp (was 28dp).
- **Attachments, both composers.** Composer row is `[attach][chip] … [send]`
  with the staged tray above the field. Photo Picker (no storage permission),
  10 files per message, 20 MB cap (the web client's own). Uploads are
  session-scoped server-side (`POST /api/upload`, web parity):
  - chat: staged picks upload on send; failures keep them for retry, and a
    completed upload is remembered so a retried send never re-uploads;
  - home: create → upload → navigate, with the uploaded refs riding the route
    so the message and its images send as a single turn.
- **Fresh-context review follow-ups:** picker headers only offer a toggle
  where one exists (CONFIGURED and the flat MODELS section are not
  collapsible); duplicate rows are dropped before they reach `LazyColumn`, and
  section counts match what renders; the provider icon loader owns its fetches,
  so a cancelled caller can no longer poison the icon cache (regression-tested);
  the nav-seeded home message no longer re-sends after process death.

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
- Local: `./gradlew :app:testDebugUnitTest` green at each task commit.

## APK paths
- **Combined build — use this one:** `/tmp/opencode/mp-r3-combined/hermes-native-debug-apk/app-debug.apk`
  (md5 `bdd9bd561b1ef49bd3d7c7eb8f7eafcf`). The same file is also kept at the
  familiar path `/tmp/opencode/model-picker-home-send-apk/app-debug.apk`.
  Contains the model picker, the home-send fix, and all of Round 3.
- Feature-only build: `/tmp/opencode/mp-r3-feat/hermes-native-debug-apk/app-debug.apk`
  (md5 `03184dcd6af65f038daa88e390fc56dc`). Home send now works there too
  (message + attachments ride the route), but the fix/home-send UX (spinner
  reset + failure snackbar) only exists in the combined build.

## Device checklist (Round 3)
1. Install the combined APK (upgrade over the previous build is fine).
2. Chip: **no arrow** beside the model name; the pill still opens the sheet,
   and the sheet flows are unchanged (search, groups, SELECTED, Show more).
3. Lockup: the top header shows the face + HERMES AGENT lockup cleanly at
   32dp — not clipped, not stretched. Connect hero unchanged (48dp).
4. Chat attach: tap the paperclip left of the chip → pick 1–2 photos →
   thumbnails appear above the field → type text → send → the message shows
   text + images and the reply streams.
5. Attach-only send (no text) works.
6. Limits: a file over 20 MB → the send fails with an error, the picks stay
   staged, and retry works without re-uploading what already uploaded.
7. Home: attach photos on the home composer, type a message → send → the chat
   opens and the message + images send together (this is the fix path).
8. Busy state: while a reply streams, staged picks keep Send hidden on an
   empty composer (steer stays text-only); Stop behaves as before.
9. Offline icons: first open in airplane mode → monograms, no crash; icons
   that failed while offline come back after an app relaunch (the failed-id
   set is per process, by design — see the plan checklist).
10. Regression sanity: a model picked on the home chip still rides into the
    new chat; stopping the server and sending still resets the spinner and
    shows the snackbar.

## Merge order (important)
1. `fix/home-send` → master **first** (its own device pass came earlier).
2. Then `feat/model-picker` → master. Conflicts will appear on the files both
   sides rewrote — `NewSessionResponse`/`SessionRepository.create`,
   `HomeScreen` (composer send path), `ChatViewModel` (loader line + seed
   guard), `SessionsViewModel`, `HermesRoot`, `ChatScreen`. The resolution is
   already proven on `test/model-picker-home-send`, whose current build
   (`707ef24`) resolves them as the union: pick rides with `NewSessionRequest`,
   response read nested, state-driven `isStartingChat` + snackbar, and the
   Round 3 composer/attach plumbing.
3. Do **not** merge `test/model-picker-home-send` itself — it exists only as
   the combined test vehicle.

## Mic research (decision pending)
ChatGPT's app voice input is server-side transcription: the app records audio,
uploads it, and a Whisper-class model on the server returns text — the app
does no on-device recognition. The Hermes server already ships this
architecture and the web UI already uses it:

- `POST /api/transcribe` — multipart `file` (audio blob) → `{"ok": true,
  "transcript": "…"}`; 503 when STT is unconfigured on that server.
- `GET /api/transcribe/capability` — cheap probe → `{"ok": true, "available":
  bool, "provider": "…"}` (faster-whisper / OpenAI / Mistral, whichever the
  server is configured with).
- The web mic has two modes: **server STT** (default; recording → transcript
  lands in the composer, prefix preserved) and **raw audio** (record and send
  the original file to the agent, Telegram-style voice note).

So a mic is a client-side feature on an existing endpoint — no protocol work.
Pending from the user: (a) which processing path to use (transcribe-to-text
before send, raw-audio-as-attachment, or both), and (b) the recorder UX
(tap-to-start/tap-to-stop vs hold-to-talk) once that is decided.

## Release
No version bump in this cycle.
