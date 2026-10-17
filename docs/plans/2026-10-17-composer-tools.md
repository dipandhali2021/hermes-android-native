# Composer tools (round 6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement
> this plan task-by-task (native execution was chosen in session per the standing round loop).
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the web composer's four remaining features — saved prompts, profiles,
workspace switcher, and the workspace file panel — to the Android app, in M3 expressive style,
integrated as rows in the `+` panel.

**Architecture:** One wire layer (`core/model/ComposerTools.kt` + extensions), one repository
per feature under `data/`, a shared `domain/composer/ComposerToolsState.kt` slice that both
hosts (ChatViewModel for chat, SessionsViewModel for home) mirror in their UiState, and
UI sheets/screens under `ui`. The `+` panel gains a chat-tools slot between tiles and voice.
Existing patterns are followed exactly: `Result` + `DataError` repositories, explicit
serializers in `HermesApi`, `ModalBottomSheet` sheets like `ModelPickerSheet`, and TDD with
MockWebServer for every wire contract.

**Tech Stack:** Kotlin 2.2.20, Compose BOM 2026.06.01, material3 1.4.0 (expressive springs via
`HermesMotion`), OkHttp + mockwebserver3, kotlinx-serialization, Hilt.

**Spec:** `android-native/docs/specs/2026-10-17-composer-tools-design.md` (binding; the
25-question answers + builder-check addenda §2.1 are the product decisions) and the standing
spec `2026-10-17-voice-and-attach-design.md` (§1–§9 constraints carry over).

**Server source of truth verified this session:** `api/routes.py`, `api/profiles.py`,
`api/workspace.py`, `api/upload.py` in the hermes-webui checkout — exact shapes quoted
in `docs/specs/2026-10-17-composer-tools-design.md` §9 and re-pinned by wire tests below.

## Global Constraints

- Compose BOM 2026.06.01 / material3 1.4.0 / Kotlin 2.2.20 pins are frozen; expressive motion
  only via `ui/theme/Motion.kt` (`HermesMotion`); no raw `tween(...)`; no `Color(0x...)`
  outside `ui/theme/Color.kt`; **no Coil** (image previews decode raw bytes with BitmapFactory).
- Protocol: never send `Origin`/`Referer`/`Sec-Fetch-*`; cookie jar persists the signed
  `hermes_profile` cookie set by `/api/profile/switch` (no manual handling); every wire field
  optional with default; server error wording surfaces verbatim via `DataError`.
- `POST /api/session/new` `worktree` is **three-value**: key absent (`null`) inherits profile
  config; explicit `true` (worktree row) hard-fails 400 on non-git; explicit `false` only for
  auto-bind/switch-replacement chats.
- `POST /api/session/update` for workspace switch sends `{session_id, workspace, model,
  model_provider}` together; blocked while a stream runs (client-side guard).
- Home sends create the chat with `{workspace}` and **no** `worktree` key.
- The file panel is **chat-only** (session-anchored endpoints); no throwaway sessions.
- Multipart `POST /api/workspace/upload` fields: `session_id`, `path` (subdirectory), `file`.
- Tests: `./gradlew :app:testDebugUnitTest`; CI green before any merge; bookkeeping scripts in
  the superpowers `executing-plans/scripts` dir; ledger at
  `android-native/.superpowers/sdd/2026-10-17-composer-tools/progress.md`.

## Review Focus

The failure modes this round most risks producing, each pinned to its owning task:

1. **`worktree` accidentally sent as explicit `false` on every home send** — a phone user with
   `worktree: true` in config would silently lose isolation. Test: `WorkspaceApiTest` asserts
   the request body has NO `worktree` key when the field is null, `false` when set false.
2. **Visibility of a server the app can't speak to yet (404 endpoints on older servers)** —
   rows must hide quietly on 404, not error-toast. Test: `PromptApiTest` maps a 404 to a
   failure whose `DataError` is `Server(404, …)`; the VM's row hidden by that result (logic
   test `ComposerToolsStateTest`).
3. **Profile switch landing in the wrong place / leaving a foreign-profile chat on the back
   stack** — switch must create the replacement chat (server body `{profile, workspace,
   worktree:false}`) and *replace* the nav entry. Pinned by `ProfileApiTest` (body fields) and
   the flow task's `ProfileSwitchFlowTest`.
4. **Workspace switch while a reply streams** — must be refused client-side with the
   `workspace_busy_switch` copy, not a broken session. Test: `WorkspaceSwitchFlowTest`.
5. **Prompt undo after delete saving a *new* id/label drift** — undo re-saves with the prompt's
   saved `label` passed explicitly, or a retried delete could orphan the wrong row. Test:
   `PromptApiTest` asserts `label` rides the POST body.
6. **File panel size cap** — `/api/file` rejects >400 000 bytes with a *message* in a 404
   body; if `mapStatus` swallowed it the user would see "no longer exists". Test:
   `StatusMappingTest` pins 404-with-message → `DataError.Server` carrying the raw message.

---

### Task 1: Message-aware error mapping (mapStatus)

**Files:**
- Modify: `app/src/main/java/com/dipandhali/hermesnative/core/net/HermesApi.kt` (`mapStatus`)
- Test: `app/src/test/java/com/dipandhali/hermesnative/net/StatusMappingTest.kt` (new)

**Interfaces:**
- Produces: `mapStatus(code: Int, body: String): DataError` — new private behavior: a 404 or
  403 response whose body parses a non-blank `{"error": "…"}` becomes
  `DataError.Server(code, message)` (verbatim wording); otherwise unchanged mapping
  (404 → `NotFound`, 403 → `Unauthorized`).
- Consumes: existing `extractMessage`.

- [ ] Step 1: write `StatusMappingTest` with three cases (build via a tiny facade test on
  HermesApi? `mapStatus` is private — assert through public surface: a GET to a returning-404
  endpoint with `{"error":"File too large (...)"}` yields failure `ApiException(DataError.Server)`
  with status 404 and that message; a 404 with no body/message yields `DataError.NotFound`; a
  403 with `{"error":"Profile is bound to the current session"}` yields `Server(403, msg)`)
- [ ] Step 2: run — expect compile fail/FAIL (message-mapping absent)
- [ ] Step 3: implement the two branches in `mapStatus`
- [ ] Step 4: run `./gradlew :app:testDebugUnitTest` — all pass
- [ ] Step 5: commit `feat(net): surface server wording on 404/403 bodies`

### Task 2: Saved-prompts client + logic

**Files:**
- Create: `app/src/main/java/com/dipandhali/hermesnative/core/model/ComposerTools.kt`
- Modify: `app/src/main/java/com/dipandhali/hermesnative/core/net/HermesApi.kt`
- Create: `app/src/main/java/com/dipandhali/hermesnative/data/prompts/PromptsRepository.kt`
- Create: `app/src/main/java/com/dipandhali/hermesnative/domain/composer/PromptAppend.kt`
- Test: `app/src/test/java/com/dipandhali/hermesnative/prompts/PromptApiTest.kt` (new)
- Test: `app/src/test/java/com/dipandhali/hermesnative/prompts/PromptAppendTest.kt` (new)

**Interfaces:**
- Produces (ComposerTools.kt):
  `@Serializable data class SavedPrompt(id: String?, label: String?, text: String?, createdAt: Double? = null)`
  (SerialNames `created_at`; `label` getter falls back "Saved prompt"),
  `PromptsResponse(val prompts: List<SavedPrompt> = emptyList())`,
  `SavePromptRequest(val text: String, val label: String? = null)` (null label omitted),
  `SavePromptResponse(val ok: Boolean = false, val prompt: SavedPrompt? = null)`.
- Produces (HermesApi):
  `suspend fun prompts(baseUrl): Result<PromptsResponse>` (GET api/prompts),
  `suspend fun savePrompt(baseUrl, text: String, label: String?): Result<SavePromptResponse>`
  (POST; body via SavePromptRequest so a null label never appears),
  `suspend fun deletePrompt(baseUrl, id: String): Result<Unit>` (POST body `{"id":...}`).
- Produces (PromptsRepository, @Singleton, api + serverState inject):
  `suspend fun list(): Result<List<SavedPrompt>>`,
  `suspend fun save(text: String, label: String? = null): Result<SavedPrompt>`,
  `suspend fun delete(id: String): Result<Unit>`.
- Produces (PromptAppend.kt): `fun composeWithPrompt(draft: String, promptText: String): String`
  — `draft.isBlank()` → promptText; else `draft + "\n\n" + promptText`.
- Wire facts pinned by tests: POST body `{"text":…}` (and `,"label":…` only when given);
  400 responses carry the server's limits copy verbatim.

- [ ] Step 1: failing `PromptApiTest` — list round trip (two rows decode, `created_at`),
  save posts text+no-label and decodes `{ok,prompt}`, save posts label too
  (`delete`→undo uses it), delete posts `{"id":"x"}` ok 200, 400 `{"error":"text too long (max 8000 chars)"}` maps to Server(400, message)
- [ ] Step 2: run — FAIL/compile error
- [ ] Step 3: implement the DTOs, the three HermesApi methods, the repository (Result
  boundary + NotHermes guard like `PanelsRepository`), `composeWithPrompt`
- [ ] Step 4: failing `PromptAppendTest` (blank draft; non-blank join with `\n\n`; draft with
  trailing whitespace keeps it — append does not trim the user's draft)
- [ ] Step 5: run — PASS; commit `feat(prompts): client + compose logic`

### Task 3: Saved-prompts UI (+ panel rows, both hosts)

**Files:**
- Create: `app/src/main/java/com/dipandhali/hermesnative/domain/composer/ComposerToolsState.kt`
- Create: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/ChatToolsRows.kt`
- Create: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/PromptsSheet.kt`
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/AttachPanel.kt`
  (add `chatTools: (@Composable ColumnScope.() -> Unit)? = null` slot: tiles → [slot] →
  divider → voice; when the slot is null, today's composition is byte-for-byte identical)
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatViewModel.kt`
  (+ ChatUiState.tools; loaders/actions below)
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatScreen.kt`

**Interfaces:**
- Produces (`ComposerToolsState.kt` — domain, host-agnostic):
  ```kotlin
  data class PromptsUiState(
      val available: Boolean? = null,   // null = not probed yet
      val loading: Boolean = false,
      val error: String? = null,
      val prompts: List<SavedPrompt> = emptyList(),
      val sheetOpen: Boolean = false,
      val recentlyDeleted: SavedPrompt? = null,
  )
  data class ComposerToolsState(val prompts: PromptsUiState = ...)
  ```
- ChatViewModel actions: `openPromptsSheet()` (opens, then first load = availability probe:
  failure `ApiException(DataError.Server(status=404))` or NotFound → `available=false`, others
  surface on the sheet), `closePrompts()`,
  `appendPromptToDraft(id)` (composeWithPrompt into `input`), `saveDraftAsPrompt(draft: String)`
  (trims; blank → no-op; success → refresh list + effect "Prompt saved"),
  `deletePrompt(id)` (optimistic remove + recentlyDeleted + server call; failure → restore +
  effect), `undoDeletePrompt()` (re-save with the saved label + refresh).
- Home (SessionsViewModel): same prompt functions except `appendPromptToDraft` routes to the
  sheet's own callback — the home composer owns its text locally, so HomeComposer renders the
  sheet and appends into its local `text`.
- `ComposerToolsRow(icon, title, trailing, onClick)` — the shared row style (icon · title ·
  trailing value · chevron, 48dp min height, radius large, ripple).
- `PromptsSheet(state, draftProvider: () -> String, onPickText(String), onSaveCurrent,
  onDelete(id), onUndo, onDismiss)` — M3 `ModalBottomSheet`; rows swipe-to-delete
  (`SwipeToDismissBox` EndToStart → Delete) with a snackbar `Prompt deleted — Undo`; bottom bar
  pinned `Save current input` (disabled when `draftProvider().isBlank()`); empty state card
  "Nothing saved yet" + save-now button.
- Panel order pinned (Q22): tiles → divider → tools slot → divider → voice.

- [ ] Step 1: build `ComposerToolsState` + VM actions TDD-light: state transitions covered by
  a small `ComposerToolsStateTest` for the availability rule (404 → hidden; other errors stay
  visible) — the VM keeps the same helper as a top-level pure
  `fun promptsAvailability(error: DataError?): Boolean?` … render-layer manual verification
- [ ] Step 2: implement state + VM + `ChatToolsRows` + `PromptsSheet` + AttachPanel slot
- [ ] Step 3: wire ChatScreen (tools slot inside `attachPanel`, sheet sibling of Scaffold,
  snackbar channel already exists) and HomeComposer (same, local text)
- [ ] Step 4: `./gradlew :app:testDebugUnitTest` green; manual check on emulator unnecessary —
  the compile is the gate (screens verified on device later)
- [ ] Step 5: commit `feat(prompts): + panel row & sheet on both composers`

### Task 4: Profiles client

**Files:**
- Modify: `app/src/main/java/com/dipandhali/hermesnative/core/model/ComposerTools.kt` (adds)
- Modify: `app/src/main/java/com/dipandhali/hermesnative/core/model/Panels.kt` (ProfileDto
  gains optional `@SerialName("is_default") is_default, @SerialName("is_active") is_active,
  path, @SerialName("has_env") hasEnv, visible, @SerialName("enabled_skills") enabledSkills,
  @SerialName("total_skills") totalSkills`; ProfilesResponse gains
  `@SerialName("single_profile_mode") singleProfileMode: Boolean? = null`)
- Create: `app/src/main/java/com/dipandhali/hermesnative/data/profiles/ProfileRepository.kt`
- Test: `app/src/test/java/com/dipandhali/hermesnative/profiles/ProfileApiTest.kt` (new)

**Interfaces:**
- Wire additions (verified against `api/profiles.py:2248..2364`, `routes.py:16307/16328/18493/18540/18580`):
  `ProfileFullResponse(profiles, active, @SerialName("single_profile_mode"))` — reuse
  ProfilesResponse + field.
  `ProfileActiveResponse(name, path, @SerialName("is_default") isDefault,
  @SerialName("default_workspace") defaultWorkspace)`.
  `ProfileSwitchRequest(name: String)`; `ProfileSwitchResponse(profiles: List<ProfileDto> =
  emptyList(), active: String? = null, @SerialName("is_default") isDefault: Boolean? = null,
  @SerialName("default_model") defaultModel: String? = null,
  @SerialName("default_model_provider") defaultModelProvider: String? = null,
  @SerialName("default_workspace") defaultWorkspace: String? = null)`.
  `ProfileCreateRequest(name, @SerialName("clone_config") cloneConfig: Boolean? = null,
  @SerialName("clone_from") cloneFrom: String? = null, @SerialName("base_url") baseUrl: String? =
  null, @SerialName("api_key") apiKey: String? = null, @SerialName("default_model") defaultModel:
  String? = null, @SerialName("model_provider") modelProvider: String? = null)`.
- HermesApi: `profiles` already exists (Panels) — the repository re-uses it plus additions:
  `activate(name)`, `active()`, `createProfile(req)`, `deleteProfile(name)`.
- Repository: `suspend fun list(): Result<ProfilesResponse>`, `active(): Result<ProfileActiveResponse>`,
  `switch(name): Result<ProfileSwitchResponse>`,
  `create(req: ProfileCreateRequest): Result<Unit>` (response {ok,profile} → unit),
  `delete(name): Result<Unit>`.

- [ ] Step 1: failing `ProfileApiTest` — switch posts `{"name":"x"}`; switch response decodes
  defaults keys (`default_model` etc); create posts full body incl. `api_key` and omits unset
  optionals (assert body has no `"api_key"` when null — **secret must not appear**); delete
  posts `{"name":"x"}`; 409 body text surfaces as Server(409, message)
- [ ] Step 2: run — FAIL/compile
- [ ] Step 3: implement DTOs + methods + repository
- [ ] Step 4: run — PASS; commit `feat(profiles): client`

### Task 5: Profile switch flows + home identity

**Files:**
- Modify: `app/src/main/java/com/dipandhali/hermesnative/data/sessions/SessionRepository.kt`
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/sessions/SessionsViewModel.kt`
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/dipandhali/hermesnative/core/model` (`NewSessionRequest`
  gains `workspace`, `worktree: Boolean?`, `profile` — all optional; `SessionUpdateRequest`
  gains optional `workspace`)
- Test: `app/src/test/java/com/dipandhali/hermesnative/profiles/ProfileSwitchFlowTest.kt` (new)

**Interfaces:**
- `SessionRepository.create(model, provider, workspace: String? = null, worktree: Boolean? =
  null, profile: String? = null)` — null worktree → key absent (three-value).
- `SessionRepository.updateContext(sessionId, workspace: String, model: String, provider:
  String?): Result<Unit>` — session/update body with all four keys.
- SessionsViewModel gains:
  `identity: ProfileIdentityState` (active name/is_default/default_workspace/single mode,
  profiles list + loading/error + sheet open flags),
  `loadIdentity()` (on init after refresh: parallel active + profiles 404-tolerant probe →
  row hidden when NotFound),
  `switchProfile(name)` — the web-parity branch table lives in a pure helper
  `profileSwitchMode(hasSessionMessages: Boolean, hasLiveStream: Boolean): SwitchMode`
  (`SwitchMode.ReplaceChat` / `.ReTagInPlace`); home has no session so switch = retarget only.
  Home-side switch just sets the active-profile-driven default workspace target + refresh list.
- ChatViewModel gains `switchProfileHere(name, onSwitched: (String) -> Unit)` for ChatScreen:
  messages/stream present → repo.switch → create replacement session
  (`create(workspace = switchResponse.default_workspace, profile = switchResponse.active, worktree = false)`)
  → effect `ChatEffect.SwitchToSession(newId)` (nav *replace* — HermesRoot intercepts) →
  snackbar `Switched to <name> — new conversation`; empty+idle → switch → `updateContext`
  with response defaults → local model fields follow. 403-bound rejection surfaces verbatim.
- Pure rule test: `ProfileSwitchFlowTest` — non-empty ⇒ ReplaceChat; empty ⇒ ReTagInPlace;
  streaming ⇒ ReplaceChat. (Wire body of replacement pinned in Task 4's tests already.)

- [ ] Step 1: failing `ProfileSwitchFlowTest`
- [ ] Step 2: run — FAIL
- [ ] Step 3: implement repo changes + states + flows (SDK field additions use the existing
  HermesApi json config: `explicitNulls=false` keeps the three-value contract)
- [ ] Step 4: run — PASS; commit `feat(profiles): switch & re-tag flows`

### Task 6: Profiles UI

**Files:**
- Create: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/ProfilePickerSheet.kt`
- Create: `app/src/main/java/com/dipandhali/hermesnative/ui/profile/ProfileManagerSheet.kt`
- Create: `app/src/main/java/com/dipandhali/hermesnative/ui/profile/ProfileCreateSheet.kt`
- Modify: `ComposerToolsState.kt`, `SessionsViewModel.kt`, `ChatViewModel.kt`, `ChatScreen.kt`,
  `HomeScreen.kt`, `AttachPanel.kt` (tools slot rows gain the Profile row)

**Interfaces:**
- Row trailing value: active profile name (`identity.name`); picker rows: name + `(default)`
  suffix + model short (`model?.substringAfter('/') ?: model`) + `enabled_skills/total_skills`
  + gateway dot; active row check.
- Footer rows in the picker: `Manage profiles` → manager sheet; manager rows: Activate /
  Delete (confirm dialog; default profile hides Delete) + `New profile` → create form
  (name field validated `^[a-z0-9][a-z0-9_-]{0,63}$`; clone-config checkbox; model pick via
  free text + the hosts' picker catalog when available — labeled with the catalog groups'
  model ids; base URL; **API key TextField with `PasswordVisualTransformation`, shown once,
  never logged, never rendered after save**). Errors from server surface verbatim in the sheet.
- `single_profile_mode == true` → panel row hidden (both hosts).

- [ ] Step 1: implement sheets + wiring (UI-only; state flows from Task 5)
- [ ] Step 2: compile + `./gradlew :app:testDebugUnitTest`
- [ ] Step 3: commit `feat(profiles): picker & manager UI`

### Task 7: Workspace client

**Files:**
- Modify: `ComposerTools.kt`, `Panels.kt` (WorkspacesResponse gains `last: String?`),
  `HermesApi.kt`
- Create: `app/src/main/java/com/dipandhali/hermesnative/data/workspace/WorkspaceRepository.kt`
- Test: `app/src/test/java/com/dipandhali/hermesnative/workspace/WorkspaceApiTest.kt` (new)

**Interfaces:**
- HermesApi additions: `workspaceSuggestions(baseUrl, prefix)`, `addWorkspace(baseUrl, path,
  name: String?, create: Boolean = false)`, `renameWorkspace(baseUrl, path, name)`,
  `removeWorkspace(baseUrl, path)` — each posts its body per §9 (`{"path":…}`, `{"path":…,
  "name":…}`); add returns `{ok, workspaces}` decoded as
  `WorkspaceMutationResponse(ok, workspaces: List<WorkspaceDto>)`.
- Repository: `list(): Result<WorkspacesResponse>`, `suggest(prefix): Result<List<String>>`,
  `add(path, name): Result<WorkspacesResponse>`, `rename(path, name)`, `remove(path)`.
- Pure helper (tested): `workspaceLabel(sessionWorkspace: String?, saved: List<WorkspaceDto>,
  fallback: String) → String` — friendly name by path match else last path segment else
  fallback.

- [ ] Step 1: failing `WorkspaceApiTest` — suggest URL `?prefix=…`; add body; rename body;
  remove body; response decode list
- [ ] Step 2: run — FAIL; Step 3: implement; Step 4: run — PASS
- [ ] Step 5: commit `feat(workspaces): client`

### Task 8: Workspace switch flows

**Files:**
- Modify: `SessionsViewModel.kt`, `ChatViewModel.kt`, `ComposerToolsState.kt` (workspace slice),
  `HomeScreen.kt` (composer target state), `ChatScreen.kt` (top-bar label row)
- Test: `app/src/test/java/com/dipandhali/hermesnative/workspace/WorkspaceSwitchFlowTest.kt` (new)

**Interfaces:**
- WorkspaceUiState: `{ available, loading, error, workspaces, last, listEditorOpen,
  pathDialogOpen + suggestion state, pickerOpen, busy }`.
- ChatViewModel `switchWorkspace(path)`: guard `isBusy || isStreaming` → effect
  `Finish or stop the reply before switching workspace.`; else
  `updateContext(sessionId, workspace=path, model=currentModel ?: remembered, provider=…)`
  → local viewModel/state.workspace update + `{ok}` effect `Workspace switched`; failure
  surfaces verbatim; `startWorktreeChat()` → `create(model/remembered/selected, workspace =
  current workspace else identity default, worktree = true)` → same OpenSession navigation as
  a fresh send.
- Home: picker tap only retargets the composer (`pendingWorkspace` in state; row shows
  pending value); create-and-send path passes `workspace = pendingWorkspace ?: identity
  default, worktree = null`.
- Pure rule test: `WorkspaceSwitchFlowTest` — `workspaceSwitchAllowed(isBusy,isStreaming)`:
  busy/streaming → blocked message; `homeWorkspaceSendBody(workspace, worktree)` null-key rule.

- [ ] Step 1–4 TDD as above; commit `feat(workspaces): in-place switch, worktree, home target`

### Task 9: Workspace UI

**Files:**
- Create: `ui/chat/components/WorkspacePickerSheet.kt`
- Create: `ui/workspace/WorkspaceManageSheet.kt`, `ui/workspace/WorkspacePathDialog.kt`
- Modify: hosts + AttachPanel tools slot (Workspace row + trailing current label)

**Interfaces:**
- Picker: search filter (client-side, name/path); rows name+path, active highlighted; footer:
  `New conversation in worktree`, `Choose workspace path…` (dialog: text field prefilled
  current path; typing queries suggest API (debounced 250ms, ≤12 suggestions); submit →
  `addWorkspace(path)`, error verbatim, on success switches), `Manage workspaces`;
  `Browse files` row ONLY when a live session exists (chat host; home hides it).
- Manager: rename dialog + swipe-remove; reorder deferred (§10 non-goal).

- [ ] implement + compile + tests green; commit `feat(workspaces): picker, manage, path add`

### Task 10: File panel client

**Files:**
- Modify: `ComposerTools.kt`, `HermesApi.kt`
- Create: `app/src/main/java/com/dipandhali/hermesnative/data/files/WorkspaceFilesRepository.kt`
- Test: `app/src/test/java/com/dipandhali/hermesnative/files/FilesApiTest.kt` (new)

**Interfaces:**
- Wire (verified `routes.py:20350/23351/23391/18382-18410/29342`; `workspace.py:1620-1838`;
  `upload.py:689-843`): `GET /api/list?session_id&path` →
  `ListDirResponse(entries, signature?, path, workspace?, workspace_recovered?)`; entry =
  `{name, path, type: dir|file|symlink, size?, mtime_ns?, is_dir? (symlinks), workspace_sort_rank}` →
  `FileEntry(name, path, type, size: Long? = null, mtimeNs: Long? = null, isDir: Boolean? = null)`
  + helper `val entryIsDir: Boolean get() = type == "dir" || isDir == true`.
  `GET /api/file` → `FileReadResponse(path, content, size, lines?)` (404 message =
  "File too large (…)" surfaces via Task 1). `GET /api/file/raw` (optional `download=1`) →
  bytes via new `downloadFile(baseUrl, sessionId, path): Result<ByteArray>`
  (executeBytes plumbing: reuse execute() flow but collect bytes; cap client-side decode at
  8 MB).
  `GET /api/git-info` → `GitInfoResponse(git: GitInfo?)`,
  `GitInfo(branch, dirty, modified, untracked, ahead, behind)`.
  POSTs: `createFile(session_id,path,content)`, `createDir`, `renameFile(path,new_name)`,
  `deleteFile(path, recursive)` → `{ok,true,path…}` = `FileOpResponse(ok, path?…)`.
  `uploadWorkspace(baseUrl, sessionId, subPath: String, fileName, mime, bytes)` —
  multipart fields `session_id`, `path`, `file` (UploadResponse reuse + optional `error`).
- Repository mirrors all with Result + repo-level guard.

- [ ] TDD: list decodes entries (dir/file/symlink + entryIsDir rule), read round-trip,
  raw-bytes fetch, create/create-dir/rename/delete bodies, upload multipart parts
  (includes `path` field), git decode with null `git`
- [ ] commit `feat(files): client`

### Task 11: Files screen + preview + ops + entries

**Files:**
- Create: `ui/files/FilesViewModel.kt`, `ui/files/FilesScreen.kt`, `ui/files/FilePreviewScreen.kt`,
  `ui/files/FileTreeUi.kt` (pure: `expand/collapse tree state`, breadcrumbs split, size label)
- Modify: `ui/nav/HermesRoot.kt` (`Routes.files(sessionId)`, `Routes.filePreview(sessionId, path)`),
  `ui/chat/ChatScreen.kt` (folder icon + workspace label in top bar → Files / picker),
  `ui/chat/components/WorkspacePickerSheet.kt` (Browse files row → Files)
- Test: `app/src/test/java/com/dipandhali/hermesnative/files/FileTreeUiTest.kt` (new)

**Interfaces:**
- `FileTreeUi`: `data class FileUiState(entries: List<FileEntry>, expanded: Set<String>,
  currentPath: String, atRoot: Boolean get = currentPath == ".")` +
  `fun toggled(existing: Set<String>, path: String): Set<String>` +
  `fun breadcrumbs(path: String): List<Pair<String,String>>` (label, rel-path; root label
  workspace name passed by caller) + `fun entrySizeLabel(size: Long?): String` (B/KB/MB).
  `lazyDirectory(path)` loads one level (≤200 entries, exact 200 → "…" hint row).
- Preview: text (`readFile`) — monospace, copy action, `Open externally` when the read is a
  404 carrying "File too large" (via `file/raw` + `download=1` through an Intent with
  FileProvider; **no in-app editor**); image — bytes from `downloadFile` capped 8 MB,
  `BitmapFactory` two-pass decode to ≤2048px; PDF/unknown → `Open externally` always.
- Ops row menu: New file / New folder / Rename / Delete (confirm; recursive for dirs) /
  Upload (system picker → `uploadWorkspace(path = currentDir)`).
- Chat top bar: workspace small label (tappable → picker) + folder IconButton → Files route
  (both only when a session exists; workspace name via `workspaceLabel` helper).

- [ ] TDD `FileTreeUiTest` (toggle, breadcrumbs, size label); implement screens; wire nav +
      top bar + Browse-files row
- [ ] commit `feat(files): tree, preview, ops, chat top-bar entries`

### Task 12: Round wrap-up

- [x] `./gradlew :app:testDebugUnitTest` full suite green; commit any doc path updates
- [x] Update `docs/HANDOFF-model-picker.md` (round 6 section, device-check list)
- [x] push feat/model-picker → CI green → merge into test/model-picker-home-send (union with
      state-driven send region) → combined CI → APK to /tmp → ledger final entry

## Non-goals (this round)

Per-session toolsets/MCP picker (parked, round-7 candidate); inline text editing in preview;
workspace drag-reorder; profile rename/edit; worktree removal UI; personalities/SOUL.md.
