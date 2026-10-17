# Handoff — model picker (branch `feat/model-picker`; combined test build `test/model-picker-home-send`)

## Status
Rounds 1–3 (model picker, home-send fix, chip/lockup/attachments), Round 4
(voice: auto-send, both modes), Round 5 + 5.1 (the `+` attach panel), 5.2
(tile restyle), and **Round 6 (composer power tools: saved prompts, agent
profiles, workspace switcher, workspace files panel)** are implemented.
Round 6 is implemented and CI-green on `feat/model-picker` (`afdd508`) and on
the combined `test/model-picker-home-send` (merge `0fd8f0b`); device passes for
Rounds 3–5.2 remain unreported. **No version bump this
cycle** (per instruction); merge to master only on the user's say-so.

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
- Local: `./gradlew :app:testDebugUnitTest` green at each task commit (12
  separate task-done runs; new suites: profiles, workspaces, switch rules,
  files client, file tree UI).

## APK paths
- **Round 6 combined build — use this one:** `/tmp/opencode/mp-r6-combined/hermes-native-debug-apk/app-debug.apk`
  (md5 `8405e8b2622710424f16c8fd8a603272`, merge commit `0fd8f0b`). Contains the
  model picker, the home-send fix, Rounds 3–5.2, and all of Round 6.

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
    small line under the label.

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
