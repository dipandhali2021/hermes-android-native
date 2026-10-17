# Voice input and attach sheet — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Voice input in both modes (transcribe-to-text and voice note) that auto-sends, plus a `+` attach sheet (Photos / Camera / Files / voice-mode toggle) replacing the paperclip in both composers — M3 expressive, frontend only.

**Architecture:** Pure voice logic (`domain/voice/VoiceInput.kt`) unit-tested with fixtures; a thin `VoiceRecorder` platform shell around `MediaRecorder` + FileProvider; a `TranscriptionRepository` over the existing multipart `/api/transcribe` contract; both ViewModels orchestrate auto-send through their existing send pipelines (`ChatViewModel.send(override)`, `SessionsViewModel.startChatWith`), so no new send path exists. UI: `VoiceRecordingBar` strip + `AttachSheet` composable, composed from the existing M3 idiom.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2026.06.01, material3 1.4.0), Hilt, kotlinx.serialization, OkHttp 5.3.0, DataStore, JUnit4 + MockWebServer. Platform: `MediaRecorder`, `FileProvider`, ActivityResult contracts.

**Spec:** `android-native/docs/specs/2026-10-17-voice-and-attach-design.md` (read it before starting; the plan argues from it).

## Global Constraints

- **No new dependencies.** Coil is deliberately absent; `MediaRecorder`/FileProvider/ActivityResult are platform. No version bumps (see `android-native/AGENTS.md`).
- **material3 1.4.0:** `MaterialExpressiveTheme`/`MotionScheme` are `internal` — do not reference them. Motion only through `HermesMotion`; no raw `tween`. Colors only from `MaterialTheme.colorScheme` (literals only in `ui/theme/Color.kt`).
- **Wire tolerance:** every new DTO field optional with a default; unknown JSON keys ignored.
- **Never send `Origin`, `Referer`, `Sec-Fetch-*`;** reuse the existing OkHttp client inside `HermesApi`.
- **Transcribe contract (verified):** `POST /api/transcribe`, multipart with exactly one `file` part, **no session_id**. 200 `{"ok":true,"transcript":…}`; 503 = "speech-to-text unavailable"; error text is in `{"error": …}` — surface it verbatim.
- **Exact copy strings:**
  - Strip labels: `Transcribing…`, `Sending…` (U+2026).
  - Permission snackbar: `Microphone permission is needed for voice input.`
  - Too-short clip: `Recording was too short — try again.`
  - Sheet: `Photos`, `Camera`, `Files`, `Voice input`, `Transcribe`, `Voice note`, `Your speech is converted to text and sent.`, `Your recording is sent as an audio message.`
  - Content descriptions: `Add`, `Voice input`, `Cancel recording`, `Send recording`.
  - Transcript attachment line for clips: `Voice message`.
- **A recording never replaces a send path:** transcribe calls `send(override)`, voice note stages + `send()` / `startChatWith`. A stop within ~500 ms maps to the pinned too-short message and discards the clip.
- **`MediaRecorder` API:** `MediaRecorder(context)` on API 31+, deprecated no-arg below (minSdk 26) — suppression is scoped, not file-wide.
- **CI is the source of truth** (`.github/workflows/android.yml`). Local `./gradlew :app:testDebugUnitTest` may be run when the SDK is present. No CHANGELOG edits; no release this cycle.
- Work on branch `feat/model-picker` inside `android-native/` (rounds stack there; `test/model-picker-home-send` is rebuilt at the end).

## Review Focus

Failure modes a reasonable user hits; each gets its pin in the owning task.

1. **A stop on a ~300 ms tap** throws in `MediaRecorder.stop()` — must become the pinned message, never a crash or a zero-byte send (Task 5/8).
2. **Transcribe on a server without STT** (503) must show the server's own "unavailable" wording and send nothing (Task 4/8).
3. **Voice note with empty text** must still send (the chat pipeline already allows empty text with attachments; the home pipeline must too — `startChatWith("", uris)`) (Task 9).
4. **The waveform must not stream through ViewModel state** — 10 Hz state would recompose the transcript; polling stays inside the strip (Task 6).
5. **`UriPayload` extraction must not change `MediaRepository` behavior** — same cap, same messages; `UploadApiTest` stays green (Task 4).
6. **A denied mic permission must not start anything** and must explain itself once per tap (Task 8).

---

### Task 2: Voice domain logic (pure) — tests first

**Files:**
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/domain/voice/VoiceInput.kt`
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/voice/VoiceInputLogicTest.kt`

**Interfaces (used by Tasks 3, 6, 7, 8, 9; guards Task 10):**
- `enum class VoiceInputMode(val prefValue: String) { TRANSCRIBE("transcribe"), VOICE_NOTE("voice_note") }` + `companion fun fromPref(raw: String?): VoiceInputMode` (anything unknown/null → `TRANSCRIBE`).
- `sealed interface VoicePhase { data object Idle; data object Recording; data class Finishing(val label: String) }`.
- `fun formatElapsed(ms: Long): String` — `m:ss`, hours only if reached (`61_000 → "1:01"`), negative → `"0:00"`.
- `fun normalizeAmplitude(raw: Int): Float` — `sqrt(raw / 32767.0)` coerced to `0f..1f`, `raw <= 0 → 0f`.
- `fun assembleDictation(typed: String, transcript: String): String` — both trimmed; empty pieces drop out; joined with one space.
- `fun displayAttachmentNames(names: List<String>): String` — each `voice-*` name → `Voice message`, joined `", "`.

- [x] **Step 1: Write the failing test** — `VoiceInputLogicTest.kt` (plain JUnit4, no Robolectric):

```kotlin
package com.dipandhali.hermesnative.voice

import com.dipandhali.hermesnative.domain.voice.VoiceInputMode
import com.dipandhali.hermesnative.domain.voice.assembleDictation
import com.dipandhali.hermesnative.domain.voice.displayAttachmentNames
import com.dipandhali.hermesnative.domain.voice.formatElapsed
import com.dipandhali.hermesnative.domain.voice.normalizeAmplitude
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputLogicTest {
    @Test fun `elapsed formats minutes and seconds`() {
        assertEquals("0:00", formatElapsed(0))
        assertEquals("0:07", formatElapsed(7_400))
        assertEquals("1:03", formatElapsed(63_000))
        assertEquals("59:59", formatElapsed(3_599_000))
    }

    @Test fun `elapsed guards negative input`() {
        assertEquals("0:00", formatElapsed(-5))
    }

    @Test fun `amplitude normalizes to a clamped unit curve`() {
        assertEquals(0f, normalizeAmplitude(0), 0.001f)
        assertEquals(1f, normalizeAmplitude(32_767), 0.001f)
        assertEquals(1f, normalizeAmplitude(80_000), 0.001f)
        assertEquals(0.5f, normalizeAmplitude(8_191), 0.02f) // sqrt(0.25)
    }

    @Test fun `dictation joins typed prefix and transcript`() {
        assertEquals("hello world", assembleDictation("hello", " world "))
        assertEquals("world", assembleDictation("", "world"))
        assertEquals("hello", assembleDictation("hello", "  "))
        assertEquals("", assembleDictation(" ", " "))
    }

    @Test fun `mode parses persisted values and falls back`() {
        assertEquals(VoiceInputMode.VOICE_NOTE, VoiceInputMode.fromPref("voice_note"))
        assertEquals(VoiceInputMode.TRANSCRIBE, VoiceInputMode.fromPref("transcribe"))
        assertEquals(VoiceInputMode.TRANSCRIBE, VoiceInputMode.fromPref(null))
        assertEquals(VoiceInputMode.TRANSCRIBE, VoiceInputMode.fromPref("garbage"))
    }

    @Test fun `voice attachments display as Voice message`() {
        assertEquals("Voice message", displayAttachmentNames(listOf("voice-1690000000000.m4a")))
        assertEquals(
            "photo.jpg, Voice message",
            displayAttachmentNames(listOf("photo.jpg", "voice-1690000000000.m4a")),
        )
    }
}
```

- [x] **Step 2: Run it and watch it fail** — `./gradlew :app:testDebugUnitTest --tests com.dipandhali.hermesnative.voice.VoiceInputLogicTest`. Expected: compilation failure (types do not exist). Record the failure in the ledger line.
- [x] **Step 3: Implement `VoiceInput.kt`** to satisfy the tests exactly (KDoc explains the sqrt curve: speech amplitude is perceptually logarithmic; a linear bar spends most of its time near zero).
- [x] **Step 4: Rerun the test; green.** Commit: `feat(voice): pure voice-input logic (modes, elapsed, amplitude, dictation)`.

### Task 3: Persist the voice mode

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/data/settings/SettingsStore.kt`

**Interfaces:**
- `val voiceInputMode: Flow<VoiceInputMode>` (key `voice_input_mode`, decode via `VoiceInputMode.fromPref`, `runCatching` like `themeMode`).
- `suspend fun setVoiceInputMode(mode: VoiceInputMode)`.

- [x] **Step 1:** Add key + flow + setter, mirroring `themeMode`'s shape exactly.
- [x] **Step 2:** `./gradlew :app:compileDebugKotlin` (compile gate; decoding is already covered by Task 2's `fromPref` tests). Commit: `feat(voice): persist voice input mode`.

### Task 4: Transcribe endpoint + payload extraction + repository — tests first

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/core/model/Wire.kt` (add `TranscribeResponse` near `UploadResponse`)
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/core/net/HermesApi.kt` (after `upload()` L186)
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/data/media/UriPayload.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/data/media/MediaRepository.kt` (use `readUriPayload`; delete its private duplicate)
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/data/media/TranscriptionRepository.kt`
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/media/TranscribeApiTest.kt`

**Interfaces:**
- `TranscribeResponse(ok: Boolean = false, transcript: String? = null, error: String? = null)`.
- `HermesApi.transcribe(baseUrl: String, fileName: String, mimeType: String?, bytes: ByteArray): Result<TranscribeResponse>` — multipart, exactly one `file` part.
- `UriPayload(name: String, mime: String?, size: Long?, bytes: ByteArray)` + `internal fun readUriPayload(resolver: ContentResolver, uri: Uri): Result<UriPayload>` + moved `MAX_UPLOAD_BYTES` (20 MB) — MediaRepository keeps identical behavior and messages.
- `TranscriptionRepository.transcribe(uri: Uri): Result<String>` — `NotHermes` when no base URL; failure when `!ok || transcript.isNullOrBlank()` carrying `resp.error` (via `ApiException(DataError.Unknown(...))`).

- [x] **Step 1: Write the failing test** — `TranscribeApiTest.kt`, mirroring `UploadApiTest.kt` (MockWebServer):

```kotlin
@Test fun `transcribe posts a single file part and maps the transcript`() = runTest {
    server.enqueue(MockResponse.Builder().code(200)
        .body("""{"ok":true,"transcript":"hello world"}""").build())
    val result = HermesApi(OkHttpClient()).transcribe(
        server.url("/").toString(), "voice-1.m4a", "audio/mp4", byteArrayOf(1, 2, 3))
    assertEquals("hello world", result.getOrThrow().transcript)
    val recorded = server.takeRequest()
    assertEquals("/api/transcribe", recorded.target)
    val body = recorded.body?.utf8() ?: ""
    assertTrue(body.contains("name=\"file\"; filename=\"voice-1.m4a\""))
    assertFalse(body.contains("name=\"session_id\""))   // contract: file is the only part
}

@Test fun `transcribe surfaces an unavailable server as a failure`() = runTest {
    server.enqueue(MockResponse.Builder().code(503)
        .body("""{"error":"Speech-to-text is unavailable on this server"}""").build())
    val result = HermesApi(OkHttpClient()).transcribe(
        server.url("/").toString(), "voice-1.m4a", null, byteArrayOf(1))
    assertTrue(result.isFailure)
}
```

- [x] **Step 2: Run and watch it fail** (types/functions absent), record.
- [x] **Step 3: Implement**: `TranscribeResponse`, `HermesApi.transcribe`, `UriPayload.kt`, MediaRepository refactor, `TranscriptionRepository` (delete the clip file is the caller's job — the VM owns lifecycle).
- [x] **Step 4: Green** — run `TranscribeApiTest` **and** `UploadApiTest` (guards the extraction). Commit: `feat(voice): transcribe endpoint, URI payload extraction, transcription repository`.

### Task 5: VoiceRecorder shell, camera URI helper, manifest

**Files:**
- Create: `android-native/app/src/main/res/xml/file_paths.xml`
- Modify: `android-native/app/src/main/AndroidManifest.xml`
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/data/voice/VoiceRecorder.kt`
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/data/media/CameraCapture.kt`

**Interfaces (used by Tasks 6, 8, 9):**
- `VoiceRecorder` (`@Singleton`): `start(): Result<Uri>`, `stop(): Result<Uri>`, `cancel()`, `amplitude(): Int`, `elapsedMs(): Long`, `release()`, `isRecording: Boolean`. File: `cacheDir/voice/voice-<millis>.m4a` (AAC, MPEG-4, 44.1 kHz, 64 kbps). Short-stop (`RuntimeException` from `stop()`) → `Result.failure(IllegalStateException("Recording was too short — try again."))` with the clip deleted and released. `start()` prunes `voice/` entries older than 24 h. All recorder calls wrapped so a dead recorder degrades to 0/empty, never a crash.
- `fun newCameraCaptureUri(context: Context): Uri` — `cacheDir/camera/photo-<millis>.jpg` via `FileProvider.getUriForFile(context, "<packageName>.fileprovider", file)`.
- Manifest: `RECORD_AUDIO`; FileProvider `<provider>` with `${applicationId}.fileprovider`, `@xml/file_paths`, `exported=false`, `grantUriPermissions=true`; `<queries>` with `android.media.action.IMAGE_CAPTURE`.
- `file_paths.xml`: `<cache-path name="voice" path="voice/" />` and `<cache-path name="camera" path="camera/" />`.

- [x] **Step 1:** Write manifest + `file_paths.xml` + `CameraCapture.kt` + `VoiceRecorder.kt`.
- [x] **Step 2:** Compile gate: `./gradlew :app:compileDebugKotlin` (platform shell has no JVM test; the pinned short-stop message is asserted indirectly by Task 2's string being a shared constant if practical — otherwise it lives here verbatim). Commit: `feat(voice): recorder shell, file provider, camera capture uri`.

### Task 6: Composer row — `+`, mic, recording strip

**Files:**
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/VoiceRecordingBar.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/Composer.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/AttachmentTray.kt` (GraphicEq icon for `voice-*`)

**Interfaces:**
- `VoiceRecordingBar(phase, elapsedMsProvider: () -> Long, amplitudeProvider: () -> Int, onCancel, onStop, modifier)` — while `Recording`, polls every 100 ms into local state only (24 rolling bars, `animateDpAsState` with `HermesMotion.spatialFast()`), timer via `formatElapsed`, cancel = `IconButton(Icons.Rounded.Close)`, stop = `FilledIconButton` with error container (same semantics as the existing Stop). While `Finishing`, spinner + label, no buttons.
- `Composer` new params: `onMic: (() -> Unit)? = null`, `micEnabled: Boolean = true`, `voicePhase: VoicePhase = VoicePhase.Idle`, `onVoiceStop: () -> Unit = {}`, `onVoiceCancel: () -> Unit = {}`, `voiceElapsedMs: () -> Long = { 0L }`, `voiceAmplitude: () -> Int = { 0 }`. Attach icon becomes `Icons.Rounded.Add` (`contentDescription = "Add"`); mic (`Icons.Rounded.Mic`, `"Voice input"`) renders between the spacer and `SendButton` when `onMic != null` and is `enabled = enabled && micEnabled && voicePhase == Idle`; the toolbar row swaps to `VoiceRecordingBar` while `voicePhase !is Idle` behind `AnimatedContent` + `SizeTransform(clip = false)` with `HermesMotion.spatialFast()`.

- [x] **Step 1:** Implement the bar + composer changes (text field stays, disabled during recording).
- [x] **Step 2:** Compile gate + rerun `ComposerActionTest` (five-state machine untouched). Commit: `feat(voice): composer plus-button, mic, recording strip`.

### Task 7: AttachSheet

**Files:**
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/AttachSheet.kt`

**Interfaces:**
- `AttachSheet(voiceMode: VoiceInputMode, onVoiceModeChange: (VoiceInputMode) -> Unit, onPhotos, onCamera, onFiles, onDismiss)` — `ModalBottomSheet` + `rememberModalBottomSheetState(skipPartiallyExpanded = true)` (ModelPickerSheet idiom). Tiles: `PhotoLibrary`/`PhotoCamera`/`FolderOpen` in equal-weight `Surface`s (`shapes.large`, `surfaceContainerHigh`), label under icon; press scale via `graphicsLayer` + `animateFloatAsState(HermesMotion.spatialFast())`. Voice section below a `HorizontalDivider`: `Voice input` label, `SingleChoiceSegmentedButtonRow` (`SegmentedButton`, Mic/GraphicEq icons), supporting line per mode. Bottom padding respects `WindowInsets.navigationBars`.

- [x] **Step 1:** Implement the sheet with the pinned strings/descriptions.
- [x] **Step 2:** Compile gate. Commit: `feat(voice): attach sheet with photos, camera, files, voice mode`.

### Task 8: Chat wiring (ViewModel + screen)

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatViewModel.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatScreen.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/MessageBubble.kt` (`displayAttachmentNames`)

**Interfaces:**
- `ChatUiState` += `voiceMode: VoiceInputMode = VoiceInputMode.TRANSCRIBE`, `voicePhase: VoicePhase = VoicePhase.Idle`; constructor += `VoiceRecorder`, `TranscriptionRepository`; init collects `settings.voiceInputMode`.
- Methods: `setVoiceMode(mode)` (state + persist), `startVoiceRecording()` (no-op while busy; maps failure to a `ShowMessage` effect), `stopVoiceRecording()` (transcribe → `Finishing("Transcribing…")` → `send(assembleDictation(input, transcript))`; failure → effect with `error.toMessage()`; voice note → `addAttachments(listOf(uri))` + `send()`), `cancelVoiceRecording()` (delete clip, idle), `voiceElapsedMs()`, `voiceAmplitude()`. `onCleared()` → `recorder.release()`.
- `ChatScreen`: `RequestPermission` launcher (grant → `startVoiceRecording()`, deny → snackbar `Microphone permission is needed for voice input.`), camera (`TakePicture`) + files (`OpenMultipleDocuments`, `["*/*"]`) launchers, `attachSheetOpen` state, sheet hosting with the mode from state, `Composer` receives mic/phase/callbacks, `DisposableEffect` cancels an in-flight recording on dispose. Mic `enabled` gated by `!state.isBusy`.

- [x] **Step 1:** VM changes. **Step 2:** Screen changes. **Step 3:** Compile gate + full `:app:testDebugUnitTest`. Commit: `feat(voice): chat composer voice input with auto-send`.

### Task 9: Home wiring (ViewModel + composer + snackbar)

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/sessions/SessionsViewModel.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/home/HomeScreen.kt`

**Interfaces:**
- `SessionsUiState`/`SessionsState` += `voiceMode`, `voicePhase`; constructor += `VoiceRecorder`, `TranscriptionRepository` (keep `SettingsStore` if already present for the mode).
- `setVoiceMode`, `startVoiceRecording()`, `cancelVoiceRecording()`, `stopVoiceRecording(dictationText: String, stagedUriStrings: List<String>)` — transcribe → `startChatWith(assembleDictation(dictationText, transcript), stagedUriStrings)`; voice note → `startChatWith(dictationText, stagedUriStrings + uri.toString())`; `onCleared()` releases.
- `HomeScreen`: `SnackbarHost` added; `SessionsEffect.Message` becomes `snackbar.showSnackbar(...)` (was dropped); permission + camera + files launchers; sheet hosted from `HomeComposer`; `HomeComposer` gains mic + strip + sheet; send button behavior unchanged.

- [x] **Step 1:** VM. **Step 2:** Screen/composer. **Step 3:** Compile gate + full suite. Commit: `feat(voice): home composer voice input and attach sheet`.

### Task 10: Evidence, docs, CI, combined branch, APK

**Files:**
- Modify: `android-native/docs/plans/2026-10-17-voice-and-attach.md` (this file, tick boxes), `.superpowers/sdd/2026-10-17-voice-and-attach/progress.md` (ledger), `android-native/docs/HANDOFF-model-picker.md` (refresh, untracked)
- No CHANGELOG edits.

- [x] **Step 1:** Full local run: `./gradlew :app:testDebugUnitTest` — record output. If no local SDK, push and use CI as the record.
- [x] **Step 2:** Push `feat/model-picker` with `--force-with-lease`; watch CI (unit tests + debug APK); fetch the `hermes-native-debug-apk` artifact; record run URL + md5.
- [x] **Step 3:** Rebuild `test/model-picker-home-send` (`707ef24` + merge new feat head; resolve conflicts as the proven union; force-push with lease; CI green; fetch APK; md5).
- [x] **Step 4:** Device checklist (into the handoff): transcribe auto-send; voice-note auto-send (empty text); cancel; too-short clip; 503 server message; permission denial; + sheet Photos/Camera/Files; mode persists across restart; home voice flows; mic disabled mid-run; older-device pass (minSdk 26 path for `MediaRecorder`).
- [x] **Step 5:** Update ledger + handoff. No merge/release until the user says so.
