# Composer tools — Round 6 design (prompts · profiles · workspace switcher · workspace panel)

Status: **draft for approval** — the user's 25-question interview answers are binding; the
builder check has run; implementation follows `docs/plans/` per the repo's SDD flow.
Authority: this document and the standing spec `2026-10-17-voice-and-attach-design.md`
(that spec's §1–§9 constraints — M3 expressive, HermesMotion-only, cookie protocol rules —
carry over wholesale).

## 1. Intent

Bring the web composer's four remaining features to the Android app, in app style:

1. **Saved prompts** — the bookmark appendix: server-stored snippets, insert into the composer.
2. **Profiles** — switch the whole agent identity (isolated home per profile: model, skills,
   sessions, prompts, workspaces) with full management parity.
3. **Workspace switcher** — where on the server the agent works; switch in place, add paths,
   worktree chats, manage the list.
4. **Workspace file panel** — browse and view the workspace's files from the phone.

Home for all of it: the `+` panel (the user's explicit ask). The toolbar stays minimal.

## 2. Binding decisions (the 25-question interview)

All defaults were taken except where noted. Binding facts:

| # | Decision |
|---|----------|
| 1 | Prompts live as a **row in the + panel** (no toolbar bookmark). |
| 2 | Tapping a prompt **appends** to the draft with a `\n\n` join (web parity). |
| 3 | Save is **one tap, auto-named server-side** (`label = text[:60]`; no rename endpoint exists). |
| 4 | The sheet saves **the current draft only**; no write-from-scratch editor. |
| 5 | Delete = **swipe the row, instant, undo snackbar** (no confirm). |
| 6 | "Save current input" lives **in the sheet only**. |
| 7 | Empty state = **card + save-now button**. |
| 8 | Profile lives as a **row in the + panel** (no toolbar chip). |
| 9 | Profile rows show **name + model + `enabled/total skills`**. |
| 10 | Switch with a non-empty/streaming chat: **web parity** — chat stays in history, a new
  conversation starts under the new profile, snackbar `Switched to <name>`. |
| 11 | Switch with an **empty** chat: **re-tag in place** (same session id), silently. |
| 12 | Chat history shows **only the active profile's sessions**. |
| 13 | **Full parity** management (user override of the default): switch + delete + create with
  name, clone-config option, model pick, base URL, and **API key typed on the phone**. |
| 14 | **The workspace file panel is built in this round** (user override of the default). |
| 15 | Workspace picker = **row in the + panel**. |
| 16 | Workspace switch **in place** (same chat, next message runs there). |
| 17 | **"New conversation in worktree"** is included, polite error on non-git paths. |
| 18 | "Choose workspace path" = **server-path text field + live suggestions**, prefilled. |
| 19 | The chat top bar shows a **tappable workspace label**. |
| 20 | List editor round 1 = **rename + remove** (drag-reorder deferred). |
| 21 | Home screen shows the **profile's default workspace**; sending starts the chat there. |
| 22 | + panel order: **attach tiles → divider → Chat-tools rows (prompts, profile, workspace)
  → divider → voice modes**. |
| 23 | Toolbar: **minimal** (`+ · model chip · … · mic · send`). |
| 24 | Older servers: **rows hide quietly** when an endpoint is missing (mic 503 philosophy). |
| 25 | Build order: **prompts → profiles → workspace switcher → file panel**; file panel last
  but inside this round. |

### 2.1 Builder-check addenda (settled as binding calls, all web parity)

The fresh-builder check on this document verified the wire claims against the server and
left three guesses — each settles from parity, recorded here so you can veto while reading:

1. **File panel is chat-only.** The server anchors every file endpoint to a `session_id`,
   and the web disables its panel with no session too — so no Files entry on the home
   screen this round, and no invisible throwaway sessions for browsing.
2. **Home sends omit the `worktree` key** (web's own sends omit it, so a profile's
   config-level worktree default still applies from the phone); explicit `worktree` rides
   only the two web-parity spots: `true` for the worktree row, `false` for
   auto-bind/switch-replacement sessions.
3. **After a mid-chat profile switch** you land in the brand-new empty conversation
   immediately (created at switch time, web's replacement-session body), and the back stack
   is replaced so back never reopens the foreign-profile chat.

## 3. Chat-tools rows (shared, both composers)

`AttachPanel` gains a `ChatTools` section between the tiles and the voice section — rows styled
like §8.2 of the standing spec was (icon · title · trailing value · chevron), full-width
touch targets, ripple:

```
  [tiles: Photos · Camera · Files]
  ────────────────────────────────
  🔖  Saved prompts
  👤  Profile            default   ›
  📁  Workspace          vol       ›
  ────────────────────────────────
  [voice modes]
```

- Rows render only when their feature is available (§8 probe). Rows are inert with a subtle
  disabled tone while an agent turn is streaming where switching is forbidden (§5/§6).
- The tools section takes its state through slot parameters — the two hosts (chat, home)
  already own the screen state and provide drafts/session/callbacks. No global singleton.
- Sheets are **M3 modal bottom sheets** on `HermesMotion` springs, same glass as the model
  picker sheet; every list action ends by closing and committing its change with a snackbar.

## 4. Saved prompts

**Data.** `GET /api/prompts` → `{"prompts": [{"id","label","text","created_at"}]}`;
`POST /api/prompts {"text"}` (label derived server-side; errors: `"text too long (max 8000
chars)"` 400, `"saved prompts limit reached (max 200)"` 400); `DELETE /api/prompts {"id"}` →
`{"ok":true}`. Server-side, profile-scoped (`<profile home>/webui/saved_prompts.json`).

**Sheet.** Rows: label (bodyLarge), full text as a one-line supporting ellipsis; swipe-left
deletes (undo snackbar restores by… re-POST? no — undo must restore the same id; the server
has no undelete, so undo re-POSTs the same text and the label may differ → the undo snackbar
re-saves and relabels with the **old label passed explicitly** (`POST` accepts `label?`)). Tap
a row: append its text + `\n\n` to the composer draft (caret to end), close the sheet.
Bottom bar: `Save current input` — disabled when the draft is blank; saves the trimmed draft,
closes nothing unless saved from empty-composer? (Save keeps the draft; toast `Prompt saved`).
Empty state (Q7): icon + "Nothing saved yet" + a save-now button that mirrors the bottom bar.

**Draft ownership.** The drafts live in `HomeScreen` (home composer) and `ChatViewModel`
(chat input). Each host passes a `draftProvider: () -> String` into the prompts sheet; the
sheet never reaches outside its parameters.

## 5. Profiles

**Row/subline.** Sheet rows: status dot (`gateway_running`), name (accent when active +
check), `(default)` suffix for the base home, supporting line `model.split('/').pop() ·
`enabled/total skills`` (web parity). Cached like the web (short TTL) but always refetched on
open.

**Switch semantics (web parity, Q10/Q11).**
- Current chat has messages **or** a live stream → `POST /api/profile/switch {name}` (the
  server sets the signed `hermes_profile` cookie; the app's cookie jar persists it) then
  **immediately create the new conversation** — `POST /api/session/new {workspace: <new
  profile default workspace>, profile: <active>, worktree: false}` (the web's
  replacement-session body) — and **land in it**, *replacing* the chat entry in the back
  stack so back never reopens the now foreign-profile chat (that chat still exists in
  history for the web). Snackbar: `Switched to <name> — new conversation`, shown in the
  new chat.
- Current chat is empty (no messages, no stream) → switch, then **re-tag in place**:
  `POST /api/session/update {session_id, workspace: <new profile's default workspace>,
  model: <new profile default model>, model_provider: …}` — same session id, silently.
- On boot, resolve identity with `GET /api/profile/active` → `{name, is_default,
  default_workspace}`; drive the row's label and the home composer's workspace default. If
  switching returns 403 (`Profile is bound to the current session`, trusted auth) surface the
  server's message verbatim.

**History scope (Q12).** `/api/sessions` already filters to the active profile server-side;
the app needs no change — cross-profile open attempts return 409
`{code:"session_profile_mismatch"}`, surfaced as the server's error message.

**Manage (full parity, Q13).** The sheet's footer: `Manage profiles` → the profile manager
screen (OS sheet stack):
- List = the same rows; tap → detail actions: **Activate** (the switch flow above), **Delete**
  (`POST /api/profile/delete {"name"}`; confirm dialog; the default profile cannot be
  deleted — hide the action; active-profile delete switches to default first, server
  behavior, snackbar on 409 `agent-running`).
- **New profile** form: name (regex `^[a-z0-9][a-z0-9_-]{0,63}$`, client-validated),
  "Clone config from active profile" checkbox, model select (the model sheet's existing
  groups data, `- GET /api/models`), base URL (`https?://`), API key (**masked input, shown
  as entered dots, never logged, never echoed after save**; `POST /api/profile/create
  {name, clone_config, clone_from?, base_url?, api_key?, default_model?, model_provider?}`).
  Server validates the model against `/api/models` and rejects duplicates with its own
  message — surfaced verbatim.
- Note: there is **no profile-edit endpoint**; after creation, fixing a bad key means delete +
  recreate (the sheet's delete affordance covers it).

**Single-profile mode** (`single_profile_mode` from `GET /api/profiles`): the panel row and
manager hide themselves (web parity — my call).

## 6. Workspace switcher

**Row + value.** The panel row's trailing value = the chat's `session.workspace` (friendly
name via the list match, else last path segment) else the active profile's default workspace.
Chip title/tooltip = full path (my call, parity with web).

**Picker sheet.**
- Search field filters client-side (name/path substring) — no server round trip.
- Workspace rows (server-stored order): name + path subline; active highlighted; tap →
  switch (below).
- Footer rows: **New conversation in worktree** (`POST /api/session/new {workspace:
  <current>, worktree: true, ...}` — non-git paths 400 with the server's message; the new
  chat opens riding that session), **Choose workspace path** (dialog: text field prefilled
  with the current path; typing queries `GET /api/workspaces/suggest?prefix=` and shows up to
  12 suggestions; submit → `POST /api/workspaces/add {"path"}` → switch; server errors —
  system dirs, duplicates-with-switch — verbatim), **Manage workspaces** (manager sheet).

**Switch in place (Q16).** With a session: `POST /api/session/update {session_id, workspace,
model, model_provider}` — send all three (web parity: the model keys ride along so the
server's route-state re-derivation sees a consistent triple), preserving the session's
current model fields. Blocked while a stream runs (toast `Finish or stop the reply before
switching workspace` — web's `workspace_busy_switch`). Without a session (home path): a
picker tap only **retargets the composer**; the chat is created at send time with
`{workspace}` and **no `worktree` key** — web's user-initiated sends omit the key, so the
profile's config-level worktree default applies (an explicit `false` would silently disable
configured isolation). The app sends `worktree` explicitly only in the two web-parity spots:
`true` for the worktree row, `false` for auto-bind/profile-switch replacement sessions (Q21).

**Manager sheet (Q20).** Rows as above but actionable: **Rename** (text dialog → `POST
/api/workspaces/rename {path, name}`), **Remove** (swipe → `POST /api/workspaces/remove
{"path"}`; removing the active workspace warns if it's the open chat's). Reorder: deferred.

**Worktree facts.** Only *new* conversations get worktrees (server contract). The chat born
in a worktree shows the worktree name in the top-bar label (fallback when the friendly name
miss: last path segment). No worktree-removal UI this round.

## 7. Workspace file panel (this round, view-first)

**Entry — chat-only (builder-check call, web parity).** The web itself disables its file
panel with no session, and every file endpoint is session-anchored (`/api/list` etc. 400
without `session_id`), so the panel exists **only inside a chat**: the folder icon in the
chat top bar and the picker sheet's `Browse files` row (which shows only with a session).
On the home screen there is no Files entry this round, and no throwaway sessions are minted
for browsing — they would pollute the profile's session list. Two entries: (a) the small
**folder icon in the chat top bar** next to the workspace label; (b) from the workspace
picker sheet, a `Browse files` row. The panel is a **full-screen route**, not a drawer — the
phone wants the space; "show/hide" on web becomes "enter/leave" here.

**Screen.** Top bar: workspace name + the git line (`GET /api/git-info?session_id=` → branch ·
ahead/behind, hidden when absent), refresh action. Body: natural-path breadcrumb (tap a crumb
to jump) + `LazyColumn` tree: directories expand inline (per-directory `GET
/api/list?session_id=&path=` — server caps 200 entries per call; a `…` indicator when
truncated), files as rows with size. Hidden-file indicator not needed round 1 (my call).

**Preview.** Tap a file → preview route:
- **Text** (`GET /api/file`): monospace scroll, copy action; `File too large` (> server cap)
  falls back to **Open externally** via `GET /api/file/raw?…&download=1`.
- **Image**: decode from `/api/file/raw` bytes (no Coil — repo pin); graceful failure tile.
- **PDF/office/unknown**: `Open externally` through `/api/file/raw` (download + intent), the
  phone's own viewer does the rest.
- **No inline editing** this round (my call): preview is read + copy; editing stays web.

**Ops (the useful few).** From the tree's per-row overflow / long-press: **New file**, **New
folder**, **Rename**, **Delete** (`/api/file/create`, `/api/file/create-dir`, `/api/file/rename {new_name}`, `/api/file/delete {recursive?}`) — confirm on delete; **Upload** (system file
picker → multipart; **builder must verify the field names of `POST /api/workspace/upload`
against `api/routes.py:16774` before wiring — do not guess**).

## 8. Availability & graceful degradation (Q24)

On first use of each feature the app probes its base endpoint cheaply (the same call it would
make anyway); a 404("{\"error\":…}") or structural mismatch → the row hides and the empty
state of the panel composes without it. Session-scoped features degrade with the existing
DataError toasts using the **server's own wording** (the project rule). Nothing ever guesses
an endpoint shape not in §10.

## 9. Wire appendix (exact shapes from research; builder follows, never re-derives)

| Purpose | Call | Shape |
|---|---|---|
| Prompts list | `GET /api/prompts` | `{"prompts":[{id,label,text,created_at}]}` |
| Save prompt | `POST /api/prompts {"text","label"?}` | `{"ok":true,"prompt":{…}}` · 8 000-char / 200-item limits |
| Delete prompt | `DELETE /api/prompts {"id"}` | `{"ok":true}` |
| Profiles list | `GET /api/profiles` | `{"profiles":[{name,path,is_default,is_active,gateway_running,model,provider,has_env,visible,skill_count,enabled_skills,total_skills}],"active","single_profile_mode"}` |
| Active identity | `GET /api/profile/active` | `{"name","path","is_default","default_workspace"}` |
| Switch | `POST /api/profile/switch {"name"}` | `{"profiles","active","is_default","default_model","default_model_provider","default_workspace"}` · sets signed cookie · 403 bound-profile |
| Create | `POST /api/profile/create {name, clone_config, clone_from?, base_url?, api_key?, default_model?, model_provider?}` | `{"ok":true,"profile"}` |
| Delete | `POST /api/profile/delete {"name"}` | `{"ok":true,"name"}` · refuses default · 409 while running |
| Workspaces | `GET /api/workspaces` | `{"workspaces":[{path,name}],"last","terminal_remote_backend"}` |
| Suggest | `GET /api/workspaces/suggest?prefix=` | `{"suggestions":[str],"prefix"}` |
| Add | `POST /api/workspaces/add {"path","name"?,"create"?}` | `{"ok":true,"workspaces"}` · system roots blocked · duplicate error |
| Rename | `POST /api/workspaces/rename {"path","name"}` | `{"ok":true,"workspaces"}` · 404 unknown |
| Remove | `POST /api/workspaces/remove {"path"}` | `{"ok":true,"workspaces"}` |
| New chat | `POST /api/session/new` | gains `workspace`, `worktree` (three-value), `profile` fields |
| Retag | `POST /api/session/update` | gains `workspace` (with model keys, in-place switch) |
| Tree | `GET /api/list?session_id=&path=` | `{"entries":[…],"signature","path","workspace","workspace_recovered"}` (≤200) |
| Read | `GET /api/file?session_id=&path=` | `{"path","content","size","lines"}` · size cap → 404 |
| Raw | `GET /api/file/raw?session_id=&path=` (+`download=1`) | bytes stream |
| Git | `GET /api/git-info?session_id=` | branch/status summary |
| File ops | `POST /api/file/{save,create,create-dir,delete,rename,move}` | `{session_id, path, …}` as above |
| Panel upload | `POST /api/workspace/upload` | multipart — **verify fields at `api/routes.py:16774`** |

Protocol rules unchanged: cookie auth only (`hermes_session`; the switch cookie rides the jar),
no `Origin`/`Referer`/`Sec-Fetch-*` (app is a non-browser client and needs no CSRF token),
every unknown field tolerated, server 503/4xx messages surfaced verbatim.

## 10. Non-goals

- Per-session **toolsets/MCP picker** (parked — research exists; the web's wrench chip is an
  MCP-server allowlist via `GET /api/mcp/servers` catalog + `POST /api/session/toolsets`; can
  be round 7 if wanted).
- Inline text **editing** in the file preview; workspace **drag-reorder**; profile
  **rename/edit** (no endpoint); worktree **removal** UI; personalities/SOUL.md editing;
  cross-profile history.

## 11. Build order (CI as the source of truth)

1. **Prompts** — client endpoints + logic tests (TDD) → + panel row + sheet → chat & home.
2. **Profiles** — switch/re-tag flows, manager + create/delete, boot identity resolution.
3. **Workspace switcher** — picker, path add, manage, top-bar label, home default.
4. **File panel** — tree, preview, ops, upload.

Each step: branch `feat/model-picker` → CI green → merge into `test/model-picker-home-send`
(proven union) → combined CI → APK. No merge to master, no release until the user says so.
