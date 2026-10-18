# Round 9 — chat media: image thumbnails, a speed-controlled audio player, backend-wired

User ask (verbatim): "render the thumbnails for images, audio sent player
similar with playback speed control and all. properly make the ui ux with
modern expressive material m3 and properly connect to the backend."

## What exists (verified against both codebases)

- User attachments render as **one plain text line** (`MessageBubble.kt:83-94`
  via `displayAttachmentNames`, `VoiceInput.kt:97`). No image, no audio, no
  file rendering anywhere in the chat.
- Stored user messages: the webui persists an `attachments` array on user
  rows — `{name, path, mime}` + optional `size`/`is_image`
  (`api/routes.py` `_checkpoint_user_message_for_eager_session_save`
  L25140-153, `_normalize_chat_attachments` L27970) — and `GET /api/session`
  returns stored dicts (L14803). This app's `MessageDto` (`Wire.kt:66`)
  currently drops the field.
- The send-side marker text also carries the paths and is the only source for
  older messages: `\n\n[Attached files: p1, p2]` (caption present) or the
  whole-text fallback `I've uploaded N file(s): p1, p2` (no caption).
  `toTranscript` strips only the bracket form today.
- Attachment bytes: `GET api/file/raw?session_id=…&path=<basename>` —
  resolves against the session workspace first, then
  `<attachment_root>/<session_id>/` (`routes.py` `_file_raw_target` L23163;
  `api/upload.py` L200-237). The web client fetches attachments by **bare
  file name** (`static/ui.js` L19292) and renders images inline / audio via
  `<audio controls>` (no speed control). MIME per extension from
  `api/config.py` MIME_MAP.
- App already has the plumbing: `HermesApi.downloadFile` →
  `api/file/raw` (`HermesApi.kt:330`), `WorkspaceFilesRepository.downloadRaw`
  (L45), cookie-auth jar on the shared OkHttp client.
- Image preview: `FilePreviewScreen` (route `file_preview/{sessionId}?path=`)
  loads raw bytes → bounded two-pass `BitmapFactory` decode (2048 px, 8 MiB) →
  `Image`. Audio extensions fall into `EXTERNAL` (hand off to another app).
- Composer tray already decodes 128 px local-URI thumbnails
  (`AttachmentTray.kt:181-195`).
- Motion tokens live in `ui/theme/Motion.kt` (`spatial()`, `spatialFast()`,
  `effects()`, `contentSize`); the transcript rows currently use **no** item
  animation.
- Pinned constraints (AGENTS.md): no Coil, no Media3/ExoPlayer, no version
  bumps, colours from the scheme only, springs from Motion.kt only.

## Decisions

1. Render media **inside the user bubble**: image thumbnails (up to 2-up),
   an inline audio player, and file rows — for both fresh sends (local
   `content://` URI, rendered immediately) and loaded transcripts (server
   fetch by basename).
2. Loaded-message attachments come from `MessageDto.attachments` when
   present, else parsed from the marker text — the second path covers every
   message sent before the server stored attachment arrays, and older
   servers. Wire entries win because they carry real `mime`/`is_image`.
3. Bytes are fetched through `ChatViewModel` → `WorkspaceFilesRepository`
   (cookie jar handled), never from composables directly. Fetch name is
   always the basename (mirrors the web client; the server resolves it
   inside the session's attachment dir).
4. Audio: framework `MediaPlayer` (no new dependency). Speed cycle
   `1× → 1.5× → 2×` via `PlaybackParams` (API 23+), applied before each
   play() so the "setting params on a paused player starts it" quirk never
   fires. Remote audio downloads to `cacheDir/attachment-audio/` first
   (cookie-authed fetch), then plays locally; local URIs play directly.
5. Images: bounded decode (max 640 px) cached in a small LruCache keyed by
   session+name; tap opens the existing `file_preview` route (full-size
   decode, its image branch already works with a bare attachment name).
6. `FilePreviewScreen` gains an `AUDIO` kind rendered with the same player;
   "open externally" stays available for audio.
7. Expressive pass, chat-first (the screens these features live in): spring
   `animateItem` on transcript rows, media blocks animate in, the composer
   tray reuses the shared decoder. Colours stay scheme-derived — the player
   and file row key off `LocalContentColor` so the same components also
   render standalone in the preview screen. No palette edits;
   `ColorContrastTest` is untouched.

## Interfaces

New — `ui/chat/Attachments.kt`:
- `enum class AttachmentKind { IMAGE, AUDIO, FILE }`
- `data class ChatAttachment(name, path = "", mime: String? = null, localUri: String? = null, kind = FILE)` with `val fetchName: String` (basename of `path` else `name`)
- `fun attachmentKind(name: String, mime: String?, isImage: Boolean = false): AttachmentKind`
- `fun parseUserMessage(content: String, wire: List<AttachmentDto> = emptyList()): ParsedUserMessage(text, attachments)`
- `fun PendingAttachment.toChatAttachment(): ChatAttachment`

New — `ui/components/AttachmentMedia.kt`:
- `PLAYBACK_SPEEDS = listOf(1f, 1.5f, 2f)`, `fun nextPlaybackSpeed(current: Float): Float`, `fun speedLabel(speed: Float): String`
- `internal fun sampleSizeFor(width: Int, height: Int, maxDim: Int): Int`
- `internal fun decodeLocalThumbnail(context, uriString, maxDim: Int): Bitmap?` (moved from the tray), `internal fun decodeBytesThumbnail(bytes: ByteArray, maxDim: Int): Bitmap?`
- `@Composable fun ChatMediaBlocks(attachments, cachePrefix, onOpen: (String) -> Unit, loadBytes: suspend (String) -> Result<ByteArray>, modifier)` — images 224×168 single / 140×105 two-up, then players, then file rows
- `@Composable fun AudioAttachmentPlayer(name, localUri: String?, fetchName: String, loadBytes, modifier)` — play/pause, seekable Slider, `m:ss / m:ss`, speed pill; spinner while preparing; inline error copy

Changed:
- `Wire.kt` — `MessageDto.attachments: List<AttachmentDto> = emptyList()`
- `Transcript.kt` — `User.attachments: List<ChatAttachment>`; `toTranscript` uses `parseUserMessage`
- `MessageBubble.kt` — `UserMessage(item, onCopy, onOpenAttachment, loadAttachmentBytes, attachmentKeyPrefix)`
- `ChatScreen.kt` — new `onOpenAttachment` param; `animateItem(spatial/effects)` on rows
- `ChatViewModel.kt` — `WorkspaceFilesRepository` injected; `suspend fun loadAttachmentBytes(name): Result<ByteArray>`; `send()` optimistic `attachments = staged.map { it.toChatAttachment() }`
- `HermesRoot.kt` — chat route: `onOpenAttachment = { navigate(filePreview(sessionId, it)) }`
- `FilePreviewScreen.kt` — `PreviewKind.AUDIO`, `previewKindOf`, VM `downloadBytes`, player branch, open-externally for audio
- `AttachmentTray.kt` — drop the private decoder, use the shared one

## Task 1 — wire field + attachment model + marker parsing

Files: `core/model/Wire.kt`, new `ui/chat/Attachments.kt`,
`ui/chat/Transcript.kt`.

Steps:
1. RED: write `app/src/test/java/com/dipandhali/hermesnative/chat/UserAttachmentParsingTest.kt`
   with cases:
   - bracket marker: `"look \n\n[Attached files: /a/b/photo.jpg, /a/b/doc.pdf]"` →
     text `"look"`, two attachments (IMAGE, FILE), `fetchName` = basenames;
   - fallback line: `"I've uploaded 1 file(s): /a/b/voice-1.m4a"` → text `""`,
     one AUDIO attachment;
   - wire list wins: `parseUserMessage(content-with-marker, wire = [photo dto
     with mime "image/png", path "/x/y/z.png"])` → uses wire name/path/kind,
     still strips marker text;
   - no marker: text unchanged, no attachments;
   - `attachmentKind`: `mime = "image/png"` with no extension → IMAGE;
     `isImage = true` alone → IMAGE; `"clip.m4a"` no mime → AUDIO; unknown →
     FILE;
   - `fetchName`: path basename when path set, else name.
   Run `./gradlew :app:testDebugUnitTest --tests '*UserAttachmentParsing*'` →
   Expected: compile failure (API missing).
2. GREEN: add `MessageDto.attachments`, write `Attachments.kt`
   (`AttachmentKind`, `ChatAttachment` + `fetchName`, `attachmentKind`,
   `ParsedUserMessage`, `parseUserMessage` with both regexes, wire-preference),
   switch `TranscriptItem.User.attachments` to `List<ChatAttachment>` and
   `toTranscript` to `parseUserMessage`.
3. Run the parser test → Expected: pass. Then full
   `./gradlew :app:testDebugUnitTest` → green (fix any construction sites the
   type change breaks: grep `TranscriptItem.User(` across main + tests).
4. Commit: `feat(chat): render-ready attachment model + marker parsing`.

## Task 2 — playback helpers (pure)

Files: extend `ui/components/AttachmentMedia.kt` (helpers only for now).

Steps:
1. RED: `chat/AttachmentPlaybackLogicTest.kt`:
   - `nextPlaybackSpeed` cycles 1→1.5→2→1; unknown value (e.g. 1.25f)
     normalises to 1f first;
   - `speedLabel(1f)="1×"`, `1.5f="1.5×"`, `2f="2×"`.
   Run `./gradlew :app:testDebugUnitTest --tests '*AttachmentPlaybackLogic*'`
   → Expected: compile failure.
2. GREEN: add `PLAYBACK_SPEEDS`, `nextPlaybackSpeed`, `speedLabel`.
3. Test → pass; suite green.
4. Commit: `feat(chat): playback-speed helpers for the audio player`.

## Task 3 — media components (thumbnails, player, file rows)

Files: `ui/components/AttachmentMedia.kt` (renderers + decoders + cache),
`ui/chat/components/AttachmentTray.kt` (use shared decoder).

Steps:
1. RED (the pure piece): extend `chat/AttachmentPlaybackLogicTest.kt` (or new
   `chat/ThumbnailSampleTest.kt`) with `sampleSizeFor` cases: 4000×3000 @640 →
   8 (downsample while >= 640); 320×240 @640 → 1; zero/negative dims → 1.
   Run → Expected: compile failure.
2. GREEN: implement in `AttachmentMedia.kt`:
   - `sampleSizeFor` (while-loop halving, mirrors FilePreviewScreen math);
   - `decodeLocalThumbnail` (moved from `AttachmentTray.kt`, paramised
     `maxDim`), `decodeBytesThumbnail` (ByteArray two-pass);
   - `AttachmentImageCache` (LruCache, KB-sized, ~8 MB cap);
   - `ChatImageAttachment` (private): placeholder icon → `bitmap` state via
     cache → local decode or `loadBytes(fetchName)` bytes decode; alpha in
     with `HermesMotion.effects()`; clip `shapes.medium`; clickable;
   - `ChatMediaBlocks`: images `chunked(2)` rows (single 224×168, two-up
     140×105, 8 dp gaps), then audio rows, then file rows;
   - `AudioAttachmentPlayer`: state machine per Decisions 4 —
     `isPreparing/isPlaying/durationMs/positionMs/speed/error`; download via
     `loadBytes` → `cacheDir/attachment-audio/<name>`; `MediaPlayer`
     prepareAsync; `applySpeed` before start; 200 ms position poll while
     playing; Slider seek (`onValueChange` local, `onValueChangeFinished`
     `seekTo`); `TextButton` speed pill with `speedLabel`; completion resets
     `isPlaying`; `DisposableEffect` releases; icons PlayArrow/Pause (36 dp),
     label `displayAttachmentNames(listOf(name))`; colours from
     `LocalContentColor` (+ `.copy(alpha)` track);
   - `FileAttachmentRow`: translucent pill (LocalContentColor at 8% alpha),
     AttachFile icon + name, clickable;
   - `AttachmentTray.kt`: delete private `decodeThumbnail`/`THUMB_PX`, call
     `decodeLocalThumbnail(context, uriString, 128)`.
3. Test → pass; suite green (main sources compile through the test task).
4. Commit: `feat(chat): image thumbnails + speed-controlled audio player components`.

## Task 4 — bubble rendering + screen wiring + row animation

Files: `ui/chat/components/MessageBubble.kt`, `ui/chat/ChatScreen.kt`,
`ui/nav/HermesRoot.kt`.

Steps:
1. `UserMessage` gains `onOpenAttachment: (String) -> Unit = {}`,
   `loadAttachmentBytes: suspend (String) -> Result<ByteArray>` (default:
   failure "Attachment loading is not wired"), `attachmentKeyPrefix: String = ""`.
   Body: text when non-blank; `ChatMediaBlocks(...)` when attachments present
   — the old one-line `displayAttachmentNames` text block goes.
2. `ChatScreen`: new `onOpenAttachment: (String) -> Unit = {}` param; pass to
   `UserMessage` (`loadAttachmentBytes = viewModel::loadAttachmentBytes`,
   `attachmentKeyPrefix = sessionId.orEmpty()`); transcript row `Box` gets
   `Modifier.animateItem(fadeInSpec = HermesMotion.effects(),
   placementSpec = HermesMotion.spatial(), fadeOutSpec = HermesMotion.effects())`.
3. `HermesRoot`: chat route passes
   `onOpenAttachment = { name -> sessionId?.let { navController.navigate(Routes.filePreview(it, name)) } }`.
4. Run full `./gradlew :app:testDebugUnitTest` → Expected: green (compiles
   all call sites).
5. Commit: `feat(chat): render attachments as thumbnails, players and file rows in bubbles`.

## Task 5 — view-model loader + optimistic attachments

Files: `ui/chat/ChatViewModel.kt`, `chat/UserAttachmentParsingTest.kt` (add
mapping cases).

Steps:
1. RED: add `PendingAttachment → ChatAttachment` cases to
   `UserAttachmentParsingTest`: uploaded image dto (`isImage=true`, no
   extension) → IMAGE + `fetchName` from path basename; local-only pick
   (`uriString` set, name `photo.jpg`) → IMAGE + `localUri` kept; voice clip
   name → AUDIO. Run → Expected: compile failure.
2. GREEN:
   - add `toChatAttachment()` extension in `Attachments.kt`;
   - `ChatViewModel`: inject `WorkspaceFilesRepository`; add
     `suspend fun loadAttachmentBytes(name: String): Result<ByteArray>`
     (session guard → `files.downloadRaw(sid, name)`); `send()` optimistic
     `attachments = staged.map { it.toChatAttachment() }`.
3. Test → pass; full suite green.
4. Commit: `feat(chat): attachment byte loader + optimistic media in the sent bubble`.

## Task 6 — audio in the file preview

Files: `ui/files/FilePreviewScreen.kt`, new
`files/FilePreviewKindTest.kt`.

Steps:
1. RED: `FilePreviewKindTest` for `previewKindOf(path)`: `"clip.m4a"`/
   `"song.mp3"`/`"a.ogg"`/`"b.opus"`/`"c.flac"` → AUDIO; `"x.png"` → IMAGE;
   `"notes.md"` → TEXT; `"blob.bin"` → EXTERNAL. Run → Expected: compile
   failure (`kindOf` is private → make `previewKindOf` internal).
2. GREEN: rename + extend; VM: store session on `maybeLoad`, add
   `suspend fun downloadBytes(path)`, `PreviewKind.AUDIO -> Unit` branch;
   screen: AUDIO renders `AudioAttachmentPlayer(name = basename, localUri =
   null, fetchName = path, loadBytes = { viewModel.downloadBytes(path) })`;
   open-externally action shown for AUDIO too; `mimeFor` AUDIO branch
   (m4a→`audio/mp4`, mp3→`audio/mpeg`, wav→`audio/x-wav`, ogg/opus→
   `audio/ogg`, else `audio/*`).
3. Test → pass; full suite green.
4. Commit: `feat(files): play audio in the preview screen with the shared player`.

## Task 7 — round wrap-up

Steps:
1. Non-ASCII scan on every touched file (repo convention).
2. Full `./gradlew :app:testDebugUnitTest` → green.
3. Update `docs/HANDOFF-model-picker.md`: Round 9 status section + device
   checks (image caption/no-caption optimistic + reopened, voice playback +
   speed, audio preview, file row → preview, marker-only legacy session).
4. Commit docs; push `feat/chat-media-expressive`; watch CI → green.

## Evidence

- `./gradlew :app:testDebugUnitTest` green on feat and on the merged tree.
- Send-path text untouched: `ChatRepositoryStartTest` (wire marker) still
  green — this round changes display only.
- CI runs on both branches; APK staged under `/tmp/opencode/` with md5 and
  the device checklist above; handoff updated.
