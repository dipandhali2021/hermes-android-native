# Direct agent backend + expressive login switch

**Status:** implemented on `feat/direct-agent-backend`; all unit tests green
(46 classes, 274 tests). Device verification against the deployed agent is
pending. Merge `--no-ff` into `test/model-picker-home-send` only after CI is
green; never merge to master.

**Goal:** Let the Android app connect either to Hermes WebUI (cookie auth) or
directly to the deployed Hermes agent API (`/v1/runs`, Bearer key), selected on
the Connect screen with an expressive M3 control. Direct mode is chat + sessions
+ tools + approvals; WebUI-only surfaces are hidden or fail closed with an
explicit "not available" error.

**Branch:** `feat/direct-agent-backend` (from `test/model-picker-home-send`),
worktree `/tmp/opencode/wt-direct-agent`. Merge with `--no-ff` into the test
branch only after CI is green. Never merge to master.

**Non-negotiables:** no new dependencies; no secrets in logs; HTTPS supported;
`done` close semantics untouched for WebUI; all wire fields optional.

## Protocol facts

- Agent auth: `Authorization: Bearer <API_SERVER_KEY>`.
- Connect probe: `GET /health` then `GET /v1/capabilities`
  (`platform == "hermes-agent"`, `auth.type == "bearer"`). `/api/status` and
  `/api/ws` do not exist on the deployed agent.
- Chat: `POST /v1/runs` with `X-Hermes-Session-Id`, `Idempotency-Key`, body
  `{input, model, session_id}` → `{run_id}`; then
  `GET /v1/runs/{run_id}/events` (SSE). Stop: `POST /v1/runs/{run_id}/stop`.
  Approval: `POST /v1/runs/{run_id}/approval` `{choice, approval_id}`.
- Sessions: `GET/POST /api/sessions`, `GET/PATCH/DELETE /api/sessions/{id}`,
  `GET /api/sessions/{id}/messages`.
- Models: `GET /v1/models` (single `hermes-agent` entry on this deployment).
- Events: `message.delta`, `reasoning.available`, `tool.started`,
  `tool.completed`, `approval.request`, `run.completed`, `run.failed`,
  `run.cancelled`, `replay.truncated`. The payload's own `event`/`type` field
  wins over the SSE event name when present.
- No audio API (`audio_api:false`), no upload endpoint, no steer endpoint on the
  deployed version. Those controls are hidden in agent mode.

## Task 1 — Agent wire + mappers (pure, test-first)

Files: `core/model/AgentWire.kt`, `core/chat/AgentEventMapper.kt`,
`data/agent/AgentSessionMapper.kt`, `core/chat/ChatEvent.kt` (approval payload),
`core/error/DataError.kt` (`InvalidApiKey`, `NotAgent`, `Unsupported`).

Tests:
- `AgentEventMapperTest`: every event above; payload `event` overriding the SSE
  name; `tool.completed` `is_error`/`status`; approval field fallbacks;
  `run.failed` error object/string; unknown name → `Unknown`.
- `AgentSessionMapperTest`: page → `SessionRow` (id, title/preview fallback,
  times, tokens, no pinned/archived/streaming); messages → `SessionDetail`
  (role/content/reasoning/tool calls/timestamps, malformed row skipped
  defensively).
- `DataErrorUserMessageTest` additions for the new cases.

## Task 2 — AgentApi (MockWebServer, test-first)

Files: `core/net/AgentApi.kt`, `core/net/AgentApiTest` under
`app/src/test/.../agent/`.

Tests:
- health/capabilities send Bearer; 401 → `InvalidApiKey`; no bearer on the wrong
  host (the client is only used for the agent base URL).
- sessions list/create/rename/delete and messages parse.
- start run: URL, `X-Hermes-Session-Id`, `Idempotency-Key`, body keys, returns
  `run_id`.
- stop and approval post the right paths/bodies.
- models mapped to `ModelsResponse`.
- every failure returns `Result.failure(ApiException(DataError...))`, never
  throws.

## Task 3 — Settings, secret storage, server state, auth (test-first)

Files: `data/settings/BackendMode.kt`, `SecretStore.kt`, `SettingsStore.kt`,
`ServerState.kt`, `data/auth/AuthRepository.kt`, `di/AppModule.kt`.

- `BackendMode { WEBUI, AGENT }`; DataStore keys `backend_mode`, `agent_url`.
- API key in `EncryptedSharedPreferences` (already a dependency), never DataStore.
- Auth agent path: normalize URL → health → capabilities → persist mode/url/key →
  `ServerState.setBackend(AGENT, url, key)`. 401 → `InvalidApiKey`, wrong
  platform/auth → `NotAgent`.
- Restore agent only when URL + key exist; otherwise return null (fail closed).
- Switch clears WebUI cookies and the previous active session; keys never go to
  the WebUI client.
- Tests: `AgentConnectionValidatorTest` (pure), `AuthRepositoryAgentTest` with
  fakes/MockWebServer (success, 401, non-agent, restore without key), and a
  `SecretStore` fake.

## Task 4 — Login switch UI (M3 expressive)

Files: `ui/connect/ConnectScreen.kt`, `ConnectViewModel.kt`.

- Expressive two-option selector (stable M3 `SingleChoiceSegmentedButtonRow` +
  `SegmentedButton`, icons, animated supporting copy) — no raw colors or tween.
- `AnimatedContent` swaps the WebUI (URL + password) and Agent (URL + API key)
  forms with `HermesMotion.spatial()`.
- Mode-specific helper copy and button label; error card; password/API-key
  visibility toggle; IME actions; 48dp targets; max width 480dp.
- ViewModel: separate `webuiUrl`/`agentUrl`, `mode`, `password`, `apiKey`;
  switching clears errors/needsPassword; submit requires URL + key in agent
  mode; prefill both from settings.
- Test: `ConnectViewModelTest` (fake AuthRepository) for mode switching,
  validation, success/failure; Compose test asserting both modes and the field
  swap (Robolectric).

## Task 5 — Repositories branch (test-first where pure)

Files: `data/sessions/SessionRepository.kt`, `data/chat/ChatRepository.kt`,
`core/net/SseClient.kt`.

- `SseClient.stream(request, parse)` keeps one SSE transport; the WebUI path
  keeps `SseEventParser`, agent path uses `AgentEventMapper`. Empty SSE type is
  allowed for the agent parser; WebUI behavior unchanged.
- SessionRepository: list/create/rename/delete via AgentApi + mapper; models via
  AgentApi; archive/updateContext → `Unsupported` in agent mode.
- ChatRepository: startTurn → AgentApi.startRun (attachments rejected);
  observeTurn → agent SSE request; cancel → stop; steer → `Unsupported`;
  loadTranscript → messages mapper.
- Tests: `ChatRepositoryAgentTest` with MockWebServer (run id, Authorization,
  idempotency, stop), `SseClientTest` for the custom parser, mapper tests.

## Task 6 — Chat approvals + capability gating

Files: `ui/chat/ChatViewModel.kt`, `ChatScreen.kt`,
`ui/chat/components/Composer.kt`, `ui/chat/components/ApprovalSheet.kt`,
`ui/home/HomeScreen.kt`, `ui/nav/HermesRoot.kt`, `ui/settings/SettingsScreen.kt`.

- New `ChatEvent.ApprovalRequested(ApprovalRequest)`; ViewModel stores
  `pendingApproval`, `respondApproval(choice)` posts and clears; `ChatScreen`
  shows a bottom sheet with the command, description, risk and the server's
  choices. Refusal is always last and always present. Never auto-approve. The
  sheet is implemented as `ModalBottomSheet` rather than `AlertDialog` so four
  choices fit and the command stays readable.
- Composer: `onAttach = null` and `onMic = null` in agent mode; attach panel
  hidden. Busy steer falls back to local queue. Model chip shows the single
  agent model.
- Home composer: hide attach/mic in agent mode; hide the power-tools FAB and the
  workspace-files action. Session archive is hidden. Settings shows mode and
  "Change connection" plus a capability note.
- Tests: `ApprovalSheetContentTest` renders the prompt, asserts the command/risk
  and that allow-once/always/deny report the exact choice. Repository approval
  route/body is covered in `AgentApiTest`. A `ChatViewModel` approval test was
  skipped because the VM has fifteen constructor dependencies; the state
  transition is two lines and the response path is tested at the repository
  boundary.

## Task 7 — Docs + verification

- Update `AGENTS.md` protocol rules (two credentials, encrypted key, unnamed run
  frames, hidden agent-mode gaps).
- Update `README.md` connection section (modes, what direct mode supports) and
  `docs/ARCHITECTURE.md` (mode branching, separate clients, secret at rest).
- Run targeted unit tests; push branch; let CI build. Merge `--no-ff` into
  `test/model-picker-home-send` only after CI green; verify test-branch CI.

## Verification actually run

```bash
./gradlew :app:testDebugUnitTest --no-daemon --console=plain
# BUILD SUCCESSFUL — 46 classes, 274 tests, 0 failures

./gradlew :app:assembleDebug --no-daemon --console=plain
# BUILD SUCCESSFUL — debug APK produced
```

Covered: agent wire shapes and event mapping, session mapping, `AgentApi`
routes/auth/errors, connection validation, Connect view-model mode isolation,
the Compose login switch, repository agent branches, the SSE custom parser,
approval sheet choices, and all pre-existing tests.

Not covered (needs the live deployment): a real connect and run, approvals
against a streaming run, and device rendering. Those require explicit approval
because they write to the deployed session database; no live run was started.
The mode-2 voice-note transcription still needs an explicit "go".

## Out of scope (explicit)

- Uploads/files/workspaces/profiles/prompts/crons/memory panels in agent mode.
- Audio transcription in agent mode (agent has no audio API).
- Mid-run steering (not exposed by the deployed agent).
- Image input via `/v1/runs` (verify against v0.19 before enabling).
- Dashboard WebSocket (`/api/ws`) parity; requires deploying the official
  dashboard.
