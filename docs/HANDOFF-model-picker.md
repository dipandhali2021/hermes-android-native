# Handoff — model picker (branch `feat/model-picker`; combined test build `test/model-picker-home-send`)

## Status
Rounds 1–3 (model picker, home-send fix, chip/lockup/attachments), Round 4
(voice: auto-send, both modes), Round 5 + 5.1 (the `+` attach panel), 5.2
(tile restyle), Round 6 (composer power tools: saved prompts, agent
profiles, workspace switcher, workspace files panel), the 6.1 device
feedback fixes, **Round 7 (context window meter)** with its **7.1 restyle
(bottom-sheet card, digit-free ring)**, **Round 8 (sheet detents +
sub-flow navigation + path-entry sheet)**, and **Round 9 (chat media:
image thumbnails, a speed-controlled audio player, attachments wired to
the server store; transcript expressive pass)** are implemented. Latest:
`feat/chat-media-expressive`, merged into the combined
`test/model-picker-home-send` build; device passes for Rounds 3–6.1c and
the attachment-send fix remain unreported. **No version bump this cycle**
(per instruction); merge to master only on the user's say-so.

## What changed in Round 6
Four new tool rows live in the `+` attachment panel's tools section
(between the tiles and the voice divider), on **both** the chat and home
composers:

- **Saved prompts** — save the current draft as a named prompt from the
  composer, view the library in a sheet, and *compose with* a prompt: the
  draft (if any) is kept and the prompt text fills/extends the field. Delete
  uses an **inline undo banner inside the sheet** (not a snack — the sheet
  scrim sits over the host's snackbar); undo restores the row.
- **Profile** — full parity with the web's profile tools:
  - *Picker*: server rows with the model short-name and the
    `enabled/total skills` supporting line, active row checked, manager and
    create reachable from it; hidden entirely in `single_profile_mode`.
  - *Switching* (idle chat): non-empty chats get a **replacement
    conversation** (`session/new` with the switched profile's defaults,
    back-stack entry REPLACED, "Switched to X" banner); empty chats are
    **re-tagged in place** (`session/update`), silent. A 409 mid-run
    surfaces the server's refusal wording verbatim.
  - *Manage*: activate from a row menu, delete with a confirm dialog (the
    default profile is never deletable), "New profile" at the bottom.
  - *Create*: the web's four fields — lowercase name (server regex checked
    client-side too), clone-config checkbox (default on), optional model id
    with a catalog dropdown (or free text — the server splits the provider
    pair), base URL + password-dot API key. Unset extras serialize to
    absent keys, matching the web (`clone_from` is never sent).
  - API-key typed on the phone never logs; server failures keep the form
    open and surface verbatim.
- **Workspace** —
  - *Picker*: saved workspaces (name + path), the session's current one
    highlighted, a client-side filter, and three escalations:
    *New conversation in worktree* (chat + home; pushes a new chat — back
    returns), *Choose workspace path…* (typed path with the server's
    trusted-roots typeahead behind a 250 ms debounce — server wording
    verbatim for duplicates/bad paths), and *Manage workspaces*.
  - *In-chat switch*: picks re-point the session via `session/update`
    (current model pair kept) with a "Workspace switched" notice. Busy or
    streaming refuses client-side: "*Finish or stop the reply before
    switching workspace.*"
  - *Home*: a pick only **retargets the next chat** (row trailing shows the
    pending target; the profile default applies when nothing is pending).
    Home sends omit `worktree` (three-value contract).
  - *Manage*: tap to rename, swipe to remove — state re-seeds from the
    server's returned list.
- **Files** (chat host only; reachable via the top-bar folder button, the
  tappable workspace label → picker → Browse files) — view-first browse of
  the session's workspace: breadcrumbs to jump, one-level directory
  expansion against the server's 200-entry page cap (exact-200 shows a hint
  row), failed expansions retry inline; text preview monospace with copy;
  images decode two-pass to ≤2048 px under an 8 MB guard; PDF/unknown files
  (and text reads the server capped at 400 KB "*File too large*" via
  `/api/file`) open externally through the cache `previews/` +
  FileProvider → ACTION_VIEW. Ops: new file / new folder (menu), upload via
  system picker into the focused directory, per-row rename + delete
  (confirm; recursive for dirs). Symlink rows render with the escape flag
  honored (no navigation through escape-target links).
- **Round 6.1 (device feedback, `2ffb5d7`+):** swipe delete never deleted — the
  client POSTed `{id}` where the server's *save* handler lives; delete is the
  HTTP **DELETE** verb (fixed plumbing, verb pinned in the test). Prompt rows
  gained an explicit `×` delete beside the swipe, and a row whose label is the
  server-derived copy of its own text renders the text **once** (the "2×
  text" report). Chat tools became tonal tiles matching the Photos/Camera/
  Files card. **Long-press the send button saves the draft as a prompt**
  (both hosts; send cannot also fire — the gesture is a combinedClickable on
  the filled circle, colours from the real defaults).
- **Availability rule (rows)**: a bare 404 hides a row quietly; every other
  failure keeps the row and surfaces on tap. Profile rows additionally hide
  under `single_profile_mode`.
- `/api/list`, `/api/file`, `/api/file/raw` byte plumbing, `/api/git-info`
  (null-git tolerated), `/api/file/{create,create-dir,rename,delete}`,
  `/api/workspace/upload` (multipart `session_id` + `path` + `file`) got
  wire-level tests; round totals: 12 tasks, all committed individually with
  green suites.

## What changed in Round 7 — context window meter (composer)
Ported from the web client (`static/index.html` `#ctxIndicator` +
`static/ui.js` `_syncCtxIndicator`), per the user's screenshots: the ring
button and the usage card.

- **The ring is the button; no digits inside** (7.1 — the 8 sp centre digits
  were unreadable). A 24 dp gauge arc over a quiet track, between the mic and
  send (the web's `composer-right` order). No static glyph: the gauge itself
  answers "how full is the context?" at a glance. Colour bands as the web
  bands — quiet `onSurfaceVariant` under half, `tertiary` past 50%, `error`
  past 75%. The control renders only once a usage snapshot exists (the web
  hides its wrap until data too).
- **Tap opens the card as a small bottom sheet** (7.1 — the mid-screen
  anchored `DropdownMenu` read cramped). It takes the attach panel's
  contract: focus clears first so the sheet owns the freed space. Layout
  follows the user's reference: "Context" left with `112,900 / 128,000` in
  the amber accent right; a used-over-track bar with a thin mark at the
  auto-compress point; dot rows — Used (last prompt) / Headroom /
  Auto-compress (`800,000 (80%)`); then the session's cumulative counters
  (Input / Output / Cache read / Cache write / Cost) below a divider.
  Figures are grouped exact (`112,900`); the web tooltip's compact `1.5M`
  form stays for the accessible label and the no-window fallback.
- **The reference's per-category breakdown does not exist on the server.**
  Checked: `context_status` is prefill metadata (message/char counts of the
  injected workspace context), `context_engine_state` is persisted but never
  populated with token parts, and no endpoint reports system-prompt / tools /
  files / conversation splits. The card reports what is real — window pair,
  headroom, threshold, session totals — and invents no categories.
- **Rules copied deliberately** (web CHANGELOG #1436): the percent divides
  **last_prompt_tokens** — never the cumulative input counter; no real
  `context_length` means **no percent at all** (the app refuses the web's
  legacy 128K default, which the web's own changelog calls misleading); a
  `post_compression_context_tokens_estimate` overrides the prompt figure and
  relabels to "Estimated next model context".
- **Data:** `TokenUsage` gains `context_length`, `threshold_tokens`,
  `last_prompt_tokens`, `post_compression_context_tokens_estimate`;
  `metering` stops being a bare tick and surfaces its usage snapshot (an
  older server's bare tick still parses); `done` updates the same state
  field. `ChatUiState.usage` is cleared on session open and seeded from the
  session payload's persisted counters (`models.py compact()`), so the ring
  survives a reopen exactly like the web after a reload.
- **Out of scope:** the web's compress button (≥50% states) — the app has no
  slash-command surface; the threshold line still names the auto-compress
  point. Home composer has no session, so no meter there.

## What changed in Round 8 — sheet detents + sub-flow navigation (device feedback)
- **Sheets open at the Material 3 partial detent** (about half the screen)
  and drag up to the height they used to open at — the two-detent pattern
  common in map/mail/store apps. Prompts, profile picker/manager, workspace
  picker/manage and the model picker all take the change. The forced heights
  stay on purpose: they are now the expanded state, so pinned footers and
  list scrolling behave exactly as before. The profile-create form keeps
  opening fully (fields + keyboard need the room); the context card sheet
  stays content-sized.
- **Back from a sub-sheet lands on its picker.** Workspace Manage, Profile
  Manage, New profile and the path entry now open *stacked over* their
  picker, which stays put underneath — back, a scrim tap or a drag-down
  reveals the picker with the fresh list instead of dropping out to the
  transcript. The view-model state machine is unchanged; the screens simply
  stop closing the parent first.
- **Picker footer labels** shortened to "Choose path" / "Manage" with centred
  text and ellipsis safety — the old labels overflowed the half-width
  buttons, and `maxLines = 1` with the default `Clip` overflow left the text
  stuck against the left edge (the "Manage is not centered" report).
- **The "Choose workspace path" dialog is a sheet now**: sheet typography for
  the title + supporting line, a folder-icon field, suggestion rows styled
  like the picker's rows, an inline progress line while the add round-trips,
  and a Cancel / Add workspace action row. Adding a path closes the sheet
  onto the picker with the new path highlighted (chat: switched; home:
  pending).

## What changed in Round 9 — chat media + expressive pass
User ask: image thumbnails, an audio player with playback-speed control, and
a modern expressive M3 pass — properly wired to the backend.

- **Attachments render as media inside user bubbles** instead of one text
  line: image thumbnails (bounded 640 px decode, tiny in-memory cache, sized
  224×168 single / 140×105 two-up), an inline player for audio, and file rows
  that tap through to the file preview. Caption-less sends are media-only —
  the marker line parses away.
- **Data comes from the server store.** `MessageDto.attachments` (the wire
  array the webui persists) wins when present; otherwise the send-side marker
  text (`\n\n[Attached files: …]` or the no-caption `I've uploaded N
  file(s): …`) is parsed — so sessions sent before this build render media
  too. Fetch name is always the basename, exactly like the web client; bytes
  go through `ChatViewModel.loadAttachmentBytes` →
  `WorkspaceFilesRepository` (session cookie, never from a composable).
- **Audio player** (framework `MediaPlayer` — no new dependency): play/pause,
  a seek bar, `m:ss / m:ss`, and a speed pill cycling 1× → 1.5× → 2×
  (`PlaybackParams`, applied before play only — assigning params on a paused
  player starts it). Local voice notes play in place; stored clips are
  fetched into `cacheDir/attachment-audio/` first.
- **File preview gains AUDIO** using the same player, with "Open externally"
  still in the top bar. The EXTERNAL card's stuck top progress line was fixed
  along the way.
- **Expressive pass, chat-first:** transcript rows spring with the Motion.kt
  tokens (`animateItem`), media blocks fade in, and the new components key
  their colours off `LocalContentColor` so they read correctly inside the
  bubble and standalone. No palette change — `ColorContrastTest` is untouched.
- **Composer tray** reuses the shared bounded decoder, and optimistic bubbles
  classify picks by the device-reported mime (photo-picker URIs carry no
  extension).

Files: `ui/chat/Attachments.kt` and `ui/components/AttachmentMedia.kt` (new),
plus `Wire.kt`, `Transcript.kt`, `MessageBubble.kt`, `ChatScreen.kt`,
`ChatViewModel.kt`, `HermesRoot.kt`, `FilePreviewScreen.kt`,
`AttachmentTray.kt`. Tests: `UserAttachmentParsingTest`,
`AttachmentPlaybackLogicTest`, `ThumbnailSampleTest`, `FilePreviewKindTest`.

## Round 4 + attach panel (recap)
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
- Round 6 feature build: [run 39046779434](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39046779434) — green (`afdd508`).
- Round 6 combined build: [run 39047481179](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39047481179) — green (merge `0fd8f0b`).
- Round 6.1 combined build: [run 39050864981](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39050864981) — green (merge `39dbd68`).
- Round 6.1b combined build: [run 39052164280](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39052164280) — green (merge `ae6eb77`; adds the tile-label wrap).
- Round 6.1c build: [run 39053012328](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39053012328) — green (`5fe8342`; centres wrapped tile labels).
- Round 7 feature build: [run 39064092240](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39064092240) — green (`f0aed62`; context ring + usage card).
- Round 7 combined build: [run 39064203183](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39064203183) — green (merge `2a4d340`).
- Round 7.1 + 8 feature build: [run 39107762404](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39107762404) — green (`a598a79`; context sheet restyle, sheet detents, flow navigation, path sheet).
- Round 7.1 + 8 combined build: [run 39107821114](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39107821114) — green (merge `a3ec6e7`; APK artifact md5 `94b1123beedd3c0c9ba67570205788b9`).
- Local: `./gradlew :app:testDebugUnitTest` green at each Round 7.1/8 commit
  (feat) and on the merged test tree.
- Local: `./gradlew :app:testDebugUnitTest` green at each Round 7 commit
  (11 new tests: 3 parser fixtures from the server's payload shape, 8 meter
  cases).
- Local: `./gradlew :app:testDebugUnitTest` green at each task commit (12
  separate task-done runs; new suites: profiles, workspaces, switch rules,
  files client, file tree UI).

## APK paths
- **Use this one — round 7.1 + 8 build:** `/tmp/opencode/mp-r8-sheets/hermes-native-debug-apk/app-debug.apk`
  (md5 `94b1123beedd3c0c9ba67570205788b9`, merge `a3ec6e7`). Contains the
  model picker, the home-send fix, Rounds 3–5.2, Round 6, the 6.1 feedback
  fixes, the Round 7 context meter with its 7.1 restyle (bottom-sheet card,
  digit-free ring), and Round 8's sheet detents + sub-flow navigation +
  path-entry sheet.
- Round 7 build (superseded by 7.1 + 8): `/tmp/opencode/mp-r7-context/hermes-native-debug-apk/app-debug.apk`
  (md5 `fcb9308c3da4a027ac6240e3e17404b0`, merge `2a4d340`).
- Round 6.1c build (superseded by 7.1 + 8): `/tmp/opencode/mp-r61c-combined/hermes-native-debug-apk/app-debug.apk`
  (md5 `f6659564fa36478e62508a50f3a6c5d2`, `5fe8342`).
- Round 6.1b combined build (superseded by 6.1c): `/tmp/opencode/mp-r61b-combined/hermes-native-debug-apk/app-debug.apk`
  (md5 `1295b4bd5bc0a0c88cdd6fd282696e5d`, merge `ae6eb77`).
- Round 6.1 combined build (superseded by 6.1b): `/tmp/opencode/mp-r61-combined/hermes-native-debug-apk/app-debug.apk`
  (md5 `07fcb3c32e6c76062f17d0848cd2b3f6`, merge `39dbd68`).
- Round 6 combined build (superseded by 6.1): `/tmp/opencode/mp-r6-combined/hermes-native-debug-apk/app-debug.apk`
  (md5 `8405e8b2622710424f16c8fd8a603272`, merge commit `0fd8f0b`).

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

### Round 6 device checks
11. **Saved prompts:** enter a draft, `+` → *Saved prompts* → save with a
    name (row appears); open again, *Compose with* fills/extends the field
    (draft preserved); delete a row → inline undo banner inside the sheet →
    undo restores the row.
12. **Profile picker & switch (idle):** rows show model short-name +
    skills line; active row is checked. Switch on a non-empty chat REPLACES
    it with a "**Switched to X**" banner (back must NOT return to the old
    chat); switch on a fresh empty chat re-tags in place silently (no nav,
    no banner).
13. **Profile switch (mid-stream):** start a reply, then switch — the
    server's refusal wording appears verbatim; the chat keeps streaming.
14. **Profile manage & create:** Manage activates (check moves) and deletes
    with a confirm (the default profile's delete is not offered); Create
    requires a lowercase name (typing auto-lowercases, regex enforced),
    clone-config defaults checked, model dropdown lists the catalog, API
    key is password-dotted; a server failure keeps the form and shows the
    message verbatim.
15. **Workspace picker & switch (idle):** current workspace is highlighted
    (check); pick switches with "*Workspace switched*".
16. **Workspace switch (busy/streaming):** tap a row mid-run →
    "*Finish or stop the reply before switching workspace.*" — no network
    call.
17. **Home workspace retarget:** pick on home sets the row trailing label;
    the next send's conversation opens in that workspace; nothing pending →
    profile default.
18. **Path dialog:** typing shows ≤12 suggestions after a pause; submitting
    an already-listed path shows "*Workspace already in list*" verbatim.
19. **Manage workspaces:** rename by row tap; swipe removes (list refreshes
    from the server).
20. **Worktree:** *New conversation in worktree* (panel) opens a NEW chat;
    back returns to the original.
21. **Files:** folder button in the chat top bar opens the panel; Browse
    files from the workspace picker too (home shows neither). Breadcrumbs
    jump back; folders expand one level; ≥200-entry folders show the hint;
    text file preview is monospace with a copy action; image previews
    render; a >400 KB text file offers external open ("*File too large
    (… max 400000)*"); PDF/unknown always external; symlink rows never
    navigate outside the workspace.
22. **Files ops:** new file / new folder create server-side; rename (also
    inside the confirm rename dialog) reflects in place; delete asks and
    recurses into folders; upload stages through the document picker and
    lands in the focused directory.

### Round 6.1 device checks
23. **Swipe delete works:** swiping a prompt row fully left deletes it
    (optimistic) with the inline undo banner; undo restores the row.
24. **× delete:** the small ✕ at the end of each prompt row deletes with the
    same undo banner; rows without an id show no ✕.
25. **One text:** an unlabeled saved prompt shows its text a single time
    (server-derived label suppressed in the row).
26. **Long-press send:** holding the send button (non-empty field) saves the
    draft as a prompt and shows "Prompt saved"; it does NOT also send. The
    row appears in Saved prompts with an auto-derived name. Stop/Steer/Queue
    states and an empty field have no such gesture.
27. **Tools tiles:** chat tools look like the Photos/Camera/Files tiles —
    same card, equal widths, press-scale, active profile / workspace as the
    small line under the label; the label itself may wrap to two lines
    ("Saved" / "prompts"), each line centred, rather than ellipsize.

### Round 7 device checks (revised for the 7.1 restyle)
28. **Ring appears with data:** after a turn that reports usage, a gauge ring
    sits between the mic and send — arc over a quiet track, **no digits**.
    Before any usage arrives there is no ring — the row never carries a dead
    gauge.
29. **Card is a bottom sheet, reference layout:** tap the ring → the keyboard
    drops and the card opens attached to the bottom of the screen. "Context"
    left, `used / window` in amber right (grouped numbers); the
    used-over-track bar with the auto-compress mark; dot rows Used (last
    prompt) / Headroom / Auto-compress (`800,000 (80%)`); session totals
    (Input / Output / Cache read / Cache write / Cost) under a divider.
    Numbers must match the web tooltip for the same session.
30. **Honest fallback:** a session where the web shows only "X tokens used"
    (no window pair) → the card shows "X used" in the header, the line
    "No context window reported for this session.", and the session totals;
    never a fabricated percent. No per-category rows are shown — the server
    reports no system-prompt / tools / files split (see the Round 7 section).
31. **Survives reopen; no leaks:** reopen a session that has usage — the card
    shows the persisted values immediately, before sending anything (same as
    the web after a reload). Switching to another session must not show the
    previous session's numbers.

### Round 8 device checks
32. **Detents:** open Saved prompts / Profile / Workspace (also the manager
    sheets and the model picker) — each opens at about half the screen;
    dragging the sheet up expands it to the previous full height; dragging
    down again collapses; swiping down from half dismisses.
33. **Manage returns to the picker:** workspace picker → Manage → back (or
    drag the manage sheet down) → the chat-workspace picker is back
    underneath — not the transcript. Same for Profile → Manage, and
    Profile → Manage → New profile → back.
34. **Footer buttons centred:** the picker's two footer buttons read
    "Choose path" and "Manage", with the text centred in both.
35. **Path entry is a sheet:** "Choose path" opens a sheet (proper title,
    supporting line, folder-icon field) instead of a centred dialog; typing
    shows suggestion rows; Cancel closes back onto the picker; adding a path
    closes onto the picker with the new path highlighted.

### Round 9 device checks
36. **Image with caption:** send a photo + text — the bubble shows the
    thumbnail with the text; reopen the session — the thumbnail returns.
37. **Image without caption:** send a photo with an empty field — the bubble
    is media-only (no "I've uploaded…" line); reopen — still clean.
38. **Voice note:** record and send (with and without caption) — the bubble
    shows a player; play/pause works; the bar seeks; the speed pill cycles
    1× / 1.5× / 2× and audibly changes rate; after it ends, play restarts
    from the beginning.
39. **Old messages:** a session whose media was sent before this build shows
    thumbnails/players after reload.
40. **File rows:** a sent PDF opens the file preview when tapped.
41. **Preview audio:** Files panel → an audio file → the in-app player
    renders; "Open externally" still available.
42. **Animation:** rows spring into place while a reply streams.

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
