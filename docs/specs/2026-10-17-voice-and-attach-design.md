# Voice input and the attach sheet — Design

Date: 2026-10-17
Status: Building (user has waived the approval gate; frontend-only round).
Surface: `android-native` (hermes-native). No server changes — every contract used already exists.

## 1. Intent

Round 4 of the chat upgrades. Two features:

1. **Voice input in both modes, with automatic send.** A mic button in both
   composers. Recording stops with a tap, and the message goes to the chat
   without a confirmation step:
   - **Transcribe** (default): the recording goes to the server's existing
     `/api/transcribe`; the transcript is appended to anything already typed and
     the message is sent automatically.
   - **Voice note**: the recording itself is sent as an audio attachment on the
     next message, also automatically.
2. **A Notion-style `+` sheet.** The paperclip/attach icon is replaced by a `+`
   that opens a keyboard-height bottom sheet with **Photos**, **Camera**,
   **Files**, and a **voice-mode toggle** (Transcribe / Voice note) that
   persists.

Decisions from the 2026-10-17 conversation (verbatim asks):

- "in audio input when i send voice transcribe or voice msg send send to chat
  without asking me."
- "currently it is recording audio… but here i need that feature in android app
  too — both mode."
- "instead of chip icon add + icon so it opens a bottom sheet exactly like the
  notion screenshot for photos, video, and files."
- "use proper material m3 expressive ui ux to build it please."

Resolved during scoping (no Q&A round; user opted out and asked for the build):
tap-to-start / tap-to-stop recording (no hold-to-talk); default mode is
**Transcribe**; the mic lives in the composer next to send (both composers), the
mode toggle lives in the `+` sheet; transcribe failure shows the server's own
error and discards the clip (no silent fallback); voice annotations in the
transcript read "Voice message", not a raw filename.

## 2. What already exists (verified in code)

| Piece | Where | State |
|---|---|---|
| `POST /api/transcribe` | `api/upload.py` `handle_transcribe` (~L513) | Multipart, **`file` part only** (no `session_id`). 200 `{"ok":true,"transcript":"…"}`; 400 missing file / bad name; 503 "Speech-to-text is unavailable on this server" (or provider "unavailable"/"not configured" wording); 400/500 `{"error": msg}` otherwise |
| `GET /api/transcribe/capability` | `handle_transcribe_capability` (~L684) | `{"ok":true,"available":bool,"provider":…}` — exists but **not used this round** (failure message covers the case; probing is a follow-up) |
| Web voice modes | `static/boot.js` `_transcribeBlob` (~L1146), `_commitTranscript` (~L1093) | Server STT path: record → POST → transcript committed to composer **appended to the typed prefix** (`base+' '+clean`), then `send()` if pending-send. Raw-audio mode sends the recording as a normal attachment |
| `POST /api/upload` + `MediaRepository.upload(uri, sessionId)` | `core/net/HermesApi.kt` L172, `data/media/MediaRepository.kt` | Session-scoped multipart upload, 20 MB cap, `AttachmentDto` back. Audio files ride this unchanged |
| Chat send pipeline with staged attachments | `ChatViewModel.send()` L316, `uploadStaged()` L398 | Upload-before-turn, retry-safe staging, empty text allowed when attachments exist |
| Home send pipeline | `SessionsViewModel.startChatWith(message, attachmentUris)` | Creates session, uploads each URI, emits `OpenSessionAndSend` |
| Composer row | `ui/chat/components/Composer.kt` | `[attach] [chip] … [send]` — attach button and row are this round's edit points |
| Attachment tray + optimistic bubble | `AttachmentTray.kt`, `MessageBubble.kt` L82 | Staged chips + "name, name" line under the user bubble text |
| Bottom-sheet pattern | `ModelPickerSheet.kt` | `ModalBottomSheet` + `skipPartiallyExpanded` idiom to copy |
| Motion tokens | `ui/theme/Motion.kt` | `HermesMotion.spatial()/spatialFast()/effects()/contentSize` |
| DataStore settings | `SettingsStore.kt` | Add the voice-mode key here |

Missing: everything voice; the `+` sheet; camera capture; FileProvider (required
for both camera output and audio the uploader can read); `RECORD_AUDIO`.

## 3. UX design

### 3.1 Composer row (both composers)

```
 idle, empty:   [ + ] [ chip ]            [ mic ] [ send ]
 typing:        [ + ] [ chip ]            [ mic ] [ send ]
 recording:     [ X ]  0:07  ▁▃▅▇▅▃▁  [ stop ]     ← replaces the row
 finishing:     [ spinner ]  Transcribing…         ← replaces the row
```

- `+` (rounded Add icon) replaces the paperclip and opens the attach sheet.
  Content description: **"Add"**.
- Mic (rounded Mic icon) sits between the model chip and send — the standing
  placement ask. Content description: **"Voice input"**.
- The mic is disabled while a run is in flight (chat) or while a chat is being
  created (home); the primary button already carries stop/steer/queue semantics
  for a live run and voice must not fight it.
- While recording, the toolbar row is replaced by a **recording strip**: cancel
  (X), elapsed time `m:ss`, a live waveform driven by `MediaRecorder
  .maxAmplitude` (24 rolling bars), and a filled stop button in the error role
  (same semantic as the existing Stop). The text field above stays visible but
  disabled, so a half-typed draft is not retyped.
- While finishing (transcribing/uploading), the strip shows a small spinner and
  the pinned label **"Transcribing…"** or **"Sending…"**; no cancel — the window
  is sub-second on LAN and cancel would race the send.

### 3.2 Auto-send semantics (no confirmation step)

- **Transcribe**: on stop, the clip is transcribed; the message sent is
  `assembleDictation(typed, transcript)` — the typed prefix preserved, joined
  with a single space, transcript trimmed (mirrors `_commitTranscript`'s append
  behavior). Staged attachments ride the same send. On failure: snackbar with
  the server's own message (`Speech-to-text is unavailable on this server` is a
  503 → surfaced verbatim) and the clip is discarded; nothing is sent.
- **Voice note**: on stop, the recording is staged as a normal attachment and
  `send()` runs immediately — message text is whatever was typed (may be
  empty), attachments are staged files plus the audio. The transcript bubble
  then shows **"Voice message"** in place of the raw filename (display mapping
  below).
- Cancel (X) deletes the clip and sends nothing.
- A stop within the first ~500 ms usually throws in `MediaRecorder.stop()`;
  that maps to **"Recording was too short — try again."**

### 3.3 The `+` sheet

Keyboard-height `ModalBottomSheet` (`skipPartiallyExpanded = true`), no title,
two zones:

```
  ┌───────────────────────────────────────────┐
  │              ╭─ drag handle ─╮            │
  │   ┌────────┐  ┌────────┐  ┌────────┐      │
  │   │  🖼     │  │  📷    │  │  📄    │      │
  │   │ Photos │  │ Camera │  │ Files  │      │
  │   └────────┘  └────────┘  └────────┘      │
  │  ─────────────────────────────────────    │
  │  Voice input                              │
  │  [ 🎤 Transcribe │ 📈 Voice note ]        │
  │  <supporting line for the mode>           │
  └───────────────────────────────────────────┘
```

- **Photos** — the existing `PickMultipleVisualMedia` launcher (up to
  `MAX_ATTACHMENTS`); sheet closes first.
- **Camera** — `ActivityResultContracts.TakePicture` into a FileProvider URI in
  `cacheDir/camera/`; the full-res photo stages like any pick. No `CAMERA`
  permission is declared (it would make the permission mandatory; the system
  camera app does the capture).
- **Files** — `ActivityResultContracts.OpenMultipleDocuments` with `*/*`; the
  existing 20 MB upload ceiling and tray cap apply.
- **Voice input** — `SingleChoiceSegmentedButtonRow`: **Transcribe** (Mic icon)
  / **Voice note** (GraphicEq icon); tap persists immediately and keeps the
  sheet open. Supporting line: Transcribe → "Your speech is converted to text
  and sent."; Voice note → "Your recording is sent as an audio message."
- Tiles are expressive M3: surface-container tiles with `shapes.large`, icon +
  label, ripple, springy press scale through `HermesMotion.spatialFast()`.
- Voices: sheet has no title (Notion reference has none); the section label
  "Voice input" is `labelLarge` onSurfaceVariant.

### 3.4 Transcript rendering

- Staged tray chip for a recording: name `voice-<millis>.m4a`, graphic-eq icon
  instead of the file icon (no bitmap decode).
- Sent-bubble attachment line maps any `voice-*` filename to **"Voice
  message"** (`displayAttachmentNames`), keeping photo/file names as-is.
- No audio playback this round (files live server-side; inline playback is a
  follow-up).

## 4. Technical design

### 4.1 New files

- `domain/voice/VoiceInput.kt` — pure logic + types:
  - `enum class VoiceInputMode(val prefValue: String) { TRANSCRIBE, VOICE_NOTE }`
    with `fromPref(raw: String?)` (default `TRANSCRIBE`).
  - `sealed interface VoicePhase { Idle; Recording; data class Finishing(label) }`.
  - `formatElapsed(ms: Long): String` (`0:07`, `1:03`).
  - `normalizeAmplitude(raw: Int): Float` (0…32767 → 0…1, sqrt curve, clamped).
  - `assembleDictation(typed: String, transcript: String): String`.
  - `displayAttachmentNames(names: List<String>): String` (`voice-*` →
    "Voice message").
- `data/voice/VoiceRecorder.kt` — `@Singleton`, `@ApplicationContext`:
  `start(): Result<Uri>` (creates `cacheDir/voice/voice-<millis>.m4a`, AAC/MPEG-4,
  44.1 kHz, 64 kbps, prunes files older than a day first), `stop(): Result<Uri>`,
  `cancel()`, `amplitude(): Int`, `elapsedMs(): Long`, `release()`. Uses
  `MediaRecorder(context)` on API 31+, the deprecated constructor below
  (`minSdk 26`).
- `data/media/UriPayload.kt` — the "read a content URI with the 20 MB cap"
  logic extracted from `MediaRepository` (bounds check before read, name/mime/
  size extraction via `OpenableColumns`), reused by both uploads and
  transcription. `MediaRepository` behavior is unchanged, its test stays green.
- `data/media/TranscriptionRepository.kt` — `transcribe(uri: Uri): Result<String>`:
  reads the payload, `POST /api/transcribe` multipart `file`, validates
  `ok && transcript` non-blank, surfaces the server's `error` string otherwise.
- `data/media/CameraCapture.kt` — `newCameraCaptureUri(context): Uri`
  (`cacheDir/camera/photo-<millis>.jpg` via FileProvider).
- `ui/chat/components/VoiceRecordingBar.kt` — the strip; polls
  `elapsedMsProvider`/`amplitudeProvider` every 100 ms while recording
  (transient UI state only; never through the ViewModel `StateFlow`, so the
  transcript never recomposes for a waveform tick).
- `ui/chat/components/AttachSheet.kt` — the sheet described in §3.3.

### 4.2 Wiring

- `HermesApi.transcribe(baseUrl, fileName, mimeType, bytes)` + `Wire.kt`
  `TranscribeResponse(ok = false, transcript = null, error = null)` (all
  optional, tolerant of an older/newer server).
- `SettingsStore`: key `voice_input_mode`, `voiceInputMode: Flow<VoiceInputMode>`
  (bad/missing values fall back to Transcribe), `setVoiceInputMode(mode)`.
- `ChatViewModel`: constructor grows `VoiceRecorder` + `TranscriptionRepository`;
  `ChatUiState` gains `voiceMode` and `voicePhase`; methods `setVoiceMode`,
  `startVoiceRecording`, `stopVoiceRecording`, `cancelVoiceRecording`,
  `voiceElapsedMs()`, `voiceAmplitude()`; `onCleared` releases the recorder.
  Transcribe success calls the existing private `send(override)` with the
  assembled text; voice-note stages via `addAttachments(listOf(uri))` then
  `send()`.
- `SessionsViewModel`: same additions; `stopVoiceRecording(dictationText,
  stagedUriStrings)` receives the composer's local text/staged URIs (home
  composer state lives in the composable) and routes to `startChatWith`.
  Trascribe failure/`Message` effects now surface as snackbars on home (the
  screen previously dropped `SessionsEffect.Message`).
- `ChatScreen`/`HomeScreen`: `RECORD_AUDIO` runtime permission via
  `rememberLauncherForActivityResult(RequestPermission())`; denied → snackbar
  **"Microphone permission is needed for voice input."**; camera + files
  launchers; sheet visibility state; `DisposableEffect` cancels an in-flight
  recording when the screen leaves composition.
- `AndroidManifest.xml`: `<uses-permission RECORD_AUDIO>`, FileProvider
  (`${applicationId}.fileprovider`, `@xml/file_paths`, non-exported,
  grantUriPermissions), `<queries>` for `IMAGE_CAPTURE`.
  `res/xml/file_paths.xml`: `cache-path` `voice/` and `camera/`.
- `Composer.kt`: `onAttach` keeps its name but now opens the sheet; new params
  `onMic: (() -> Unit)? = null`, `micEnabled: Boolean = true`,
  `voicePhase: VoicePhase = VoicePhase.Idle`, `onVoiceStop`, `onVoiceCancel`;
  row swaps to `VoiceRecordingBar` while `voicePhase !is Idle` via
  `AnimatedContent` + `SizeTransform` (same idiom as `SendButton`).
- `AttachmentTray.kt`: `voice-*` names get the GraphicEq icon in the
  null-bitmap branch.
- `MessageBubble.kt`: attachment line uses `displayAttachmentNames`.

### 4.3 Rules kept

- No new dependencies (Coil still absent; `MediaRecorder`, FileProvider,
  ActivityResult contracts are platform).
- Colors from `MaterialTheme.colorScheme` only; motion from `HermesMotion` only.
- Uploads ride the existing 20 MB / `MAX_ATTACHMENTS` caps.
- No `Origin`/`Referer`/`Sec-Fetch-*` headers; transcription goes through the
  same `HermesApi` OkHttp client.
- Cache hygiene: `VoiceRecorder.start()` prunes `cacheDir/voice` entries older
  than 24 h; clips are deleted on cancel and after a successful
  transcription (uploaded voice notes are re-uploadable on retry, so their
  files are left for the pruner).

## 5. Test plan

Pure + contract tests (JVM):

1. `VoiceInputLogicTest` — elapsed formatting (`0:00`, `0:07`, `1:03`, `59:59`,
   sub-second, negative guard), amplitude normalization (0, max, mid, clamp),
   dictation assembly (empty/typed/whitespace cases), mode parsing
   (`fromPref(null/garbage/"transcribe"/"voice_note")`), attachment-name
   mapping (voice, mixed, non-voice).
2. `TranscribeApiTest` — MockWebServer: success maps `{ok,transcript}` and
   posts exactly one multipart part named `file` with the given filename and
   content type at `/api/transcribe`; 503 surfaces the server's error text as a
   failure; 400 error maps to failure.
3. Existing suites stay green (`UploadApiTest` guards the `UriPayload`
   extraction; `ComposerActionTest` guards the five-state machine).

Platform shell (no JVM test possible): recorder start/stop/cancel, permission
flow, camera round-trip — covered by the device checklist in the plan.

## 6. Non-goals

- Server-side changes of any kind; capability probing (`/api/transcribe/
  capability`) and an "unavailable" hint in the sheet.
- Hold-to-talk; waveform playback; audio playback in the transcript.
- Voice steering of a live run.
- Transcribe failure fallback to voice note (explicitly discarded).
- Multiple simultaneous recordings; background recording service.
