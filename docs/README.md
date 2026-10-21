# Hermes Native — documentation map

> **Read this first.** This is the map of the project and its documentation.
> For the current Kanban feature line, read
> [`HANDOFF-2026-10-21-kanban.md`](HANDOFF-2026-10-21-kanban.md). The
> integrated app baseline through Round 9 remains
> [`HANDOFF-2026-10-19.md`](HANDOFF-2026-10-19.md). Last refreshed 2026-10-21.

## What this project is

A fully native **Kotlin + Jetpack Compose** Android client for Hermes WebUI. It
is not a WebView shell: the chat surface, session list, composer, markdown and
media rendering are reimplemented in Compose with Material 3 Expressive.

It is independent of the official `hermes-webui/hermes-android` WebView shell
and can be installed alongside it.

## 60-second architecture

Single `:app` module, package-by-feature. Full rationale in
[`ARCHITECTURE.md`](ARCHITECTURE.md).

```
com.dipandhali.hermesnative
├── core/
│   ├── net/       OkHttp clients, cookie jar, SSE transport
│   ├── chat/      ChatEvent + SseEventParser (the WebUI vocabulary)
│   ├── model/     @Serializable wire DTOs
│   └── error/     DataError + user-facing mapping (the error boundary)
├── data/          one repository per concern: auth, chat, sessions, files,
│                  media, profiles, prompts, workspace, settings
│   └── kanban/    board/task repository and stream parser
├── domain/        pure logic: composer state machine, picker catalog,
│                  model identity, context meter, voice rules
│   └── kanban/    status, transitions, create plan, filters, search, dispatch
└── ui/            theme, nav, connect, sessions, home, chat, files, profile,
                   workspace, panels, settings
    └── kanban/    board, task detail, create/edit, boards, sheets
```

Key ideas:

- **The repository is the error boundary.** No `IOException`, `HttpException`
  or `SerializationException` reaches a ViewModel; everything becomes a
  `DataError` with a user-facing message.
- **One immutable UiState per screen**, exposed as `StateFlow`, collected with
  `collectAsStateWithLifecycle()`. One-shot effects use a `Channel`.
- **Pure logic lives in `domain/`** so it can be unit-tested without Android.
- **Auth is a cookie, not a token.** `hermes_session` lives in the cookie jar,
  which is cleared whenever the server URL changes. There is no Bearer auth.
- **Credentials are stored with the settings store**; the password is never
  logged, and the session cookie is scoped to the configured host.

## The rules that are not negotiable

The authoritative list is [`../AGENTS.md`](../AGENTS.md). The short version:

1. **`done` does not close the SSE stream.** `title` and `stream_end` follow
   it. Only `stream_end`, `cancel`, `apperror` (and legacy `error`) close.
   `ChatEvent.isClose` is the single decision point. See
   [`sse-integration.md`](sse-integration.md).
2. **Never send `Origin`, `Referer` or `Sec-Fetch-*`.** The WebUI exempts
   non-browser clients from CSRF only when they are absent.
3. **`hermes_session` is a cookie, not a token.** There is no Bearer auth. The
   jar is cleared when the server URL changes.
4. **Every wire field is optional with a default.** The server may be older or
   newer than the app; unknown event names become `Unknown`, never dropped.
5. **Do not bump the pinned versions casually.** Kotlin 2.2.x, Compose BOM
   `2026.06.01`, material3 1.4.0, compileSdk 36, AGP/Gradle as pinned. Each pin
   has a concrete compatibility reason and a verification command in
   `AGENTS.md`.
6. **Design tokens only.** Colours from `MaterialTheme.colorScheme`; motion
   from `ui/theme/Motion.kt`. No raw colours or `tween(...)` in components.
7. **CI is the source of truth.** Do not install SDK components, system
   images or emulators on this machine without explicit permission. Push and
   read the `Android build` workflow instead.

## Documentation map

### Durable project docs

| Doc | What it answers |
|---|---|
| [`../README.md`](../README.md) | What the app is, install, connection setups, build, roadmap. |
| [`../AGENTS.md`](../AGENTS.md) | Hard rules for agents: build pins, protocol, design, tests. |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Package layout, error/state rules, design decisions. |
| [`sse-integration.md`](sse-integration.md) | The WebUI SSE contract: auth, events, close semantics, reconnect, steering. |
| [`HANDOFF-2026-10-21-kanban.md`](HANDOFF-2026-10-21-kanban.md) | **Current:** full-screen Kanban feature line, CI/device evidence, gaps. |
| [`HANDOFF-2026-10-19.md`](HANDOFF-2026-10-19.md) | Integrated app baseline through Round 9. |

### Superpowers artifacts

This repo keeps the superpowers output in `docs/plans/` and `docs/specs/`
(the sibling repos use `docs/superpowers/plans` and `docs/superpowers/specs`;
the artifacts are the same, only the path differs).

| Artifact | Location | Authority |
|---|---|---|
| **Spec / design** | [`specs/`](specs/) | The design decision for a milestone. Binding for scope and behaviour. |
| **Plan** | [`plans/`](plans/) | Task-by-task implementation plan, tests first, with interfaces and evidence. |
| **SDD ledger** | `.superpowers/sdd/<date>-<feature>/` | Local execution record: `progress.md`, task briefs, RED/GREEN logs. **Git-ignored — never on another machine.** |
| **Handoff** | [`HANDOFF-*.md`](.) | Durable snapshot written at the end of a work cycle: state, evidence, device checks, next steps. |

Newest plans worth reading:

- [`plans/2026-10-20-kanban.md`](plans/2026-10-20-kanban.md) — full-screen
  Kanban board, task detail, create/edit, boards, dispatcher and acceptance.
  Its binding design is
  [`specs/2026-10-20-kanban-design.md`](specs/2026-10-20-kanban-design.md).
- [`plans/2026-10-18-chat-media.md`](plans/2026-10-18-chat-media.md) —
  attachment media, audio player, file-preview audio.
- [`plans/2026-10-18-context-meter.md`](plans/2026-10-18-context-meter.md) —
  context-window ring and usage card.
- [`plans/2026-10-18-sheet-detents.md`](plans/2026-10-18-sheet-detents.md) —
  partial detents and stacked sub-flow navigation.
- [`plans/2026-10-17-composer-tools.md`](plans/2026-10-17-composer-tools.md) —
  saved prompts, profiles, workspaces, workspace files.
- [`plans/2026-10-16-model-picker.md`](plans/2026-10-16-model-picker.md) —
  the largest plan; the model picker and the combined-branch workflow.
- [`specs/2026-10-17-voice-and-attach-design.md`](specs/2026-10-17-voice-and-attach-design.md)
  — composer, attach panel, voice modes, pinned strings.

Handoffs, newest to oldest:

- [`HANDOFF-2026-10-21-kanban.md`](HANDOFF-2026-10-21-kanban.md) — **current:**
  full-screen Kanban feature line on `feat/kanban`.
- [`HANDOFF-2026-10-19.md`](HANDOFF-2026-10-19.md) — integrated app baseline
  through Round 9; superseded for the Kanban line by the 2026-10-21 handoff.
- [`HANDOFF-composer-fab-0.4.1.md`](HANDOFF-composer-fab-0.4.1.md) — released
  v0.4.1; home composer/inset/FAB fixes. Historical.
- [`HANDOFF-home-send.md`](HANDOFF-home-send.md) — home-send fix. Historical.
- [`HANDOFF-model-picker.md`](HANDOFF-model-picker.md) — Rounds 1–9: model
  picker, voice, attach panel, power tools, context meter, sheets, chat media.
  The detailed reference for those rounds and their device checklists.

## How work happens here

The project uses the superpowers workflow. In order:

1. **Brainstorm / define the problem** with the user. Screenshots or a device
   report are the usual starting point.
2. **Write a spec** in `docs/specs/YYYY-MM-DD-<topic>-design.md`. It fixes
   scope, behaviour, pinned copy, and what is explicitly out of scope.
3. **Write a plan** in `docs/plans/YYYY-MM-DD-<topic>.md`. Tasks are ordered so
   each one leaves a compiling tree, tests come first, and shared interfaces
   are agreed in a pre-flight section.
4. **Execute** with subagent-driven development (SDD). Each task is committed
   separately; the local ledger in `.superpowers/sdd/` records rulings,
   RED/GREEN evidence and deviations. The ledger is not committed.
5. **Push and let CI verify.** The `Android build` workflow runs unit tests and
   uploads a debug APK on every branch push. Fix findings on the feature
   branch, then merge into the integration branch with `--no-ff`.
6. **Device pass.** Install the CI APK and run the handoff's device checklist.
   UI evidence is the user's screenshots, not a local emulator.
7. **Handoff.** Update/extend the current handoff: what changed, CI runs,
   APK md5, device checks, open questions, next steps.
8. **Merge/release only on the user's explicit say-so.** Version bumps and
   releases are separate decisions; `master` is not an automatic target.

## Branches and releases

- `master` — released code. Currently `v0.4.1` (2026-10-16).
- `test/model-picker-home-send` — the long-lived **integration/test** branch.
  The WebUI feature line is integrated through Round 9 at `f791721` (a revert
  commit whose tree is identical to the Round 9 integration `8e91363`), with
  the documentation merged on top; that is the build to install. The
  direct-agent experiment was reverted and must not be reintroduced. **Never
  merge this branch itself to master**; integrate
  per feature (or on a deliberate release branch) when the user decides.
- `feat/*` — one feature each, branched from the integration branch. Merge
  back with `--no-ff` after CI is green.
- `fix/*` — targeted fixes, same rule.
- Releases are **manually dispatched**, not tag-pushed:
  `gh workflow run release.yml -f version=vX.Y.Z`. The workflow builds, tags,
  and publishes both the versioned APK and `hermes-native-latest.apk`.

## Where to start, by goal

**"I'm new to the code."**
Read this page, then `ARCHITECTURE.md`, then `ui/nav/HermesRoot.kt` →
`ui/home/HomeScreen.kt` → `ui/chat/ChatScreen.kt`. The repositories are thin;
`ChatViewModel` and `SessionsViewModel` are the two state machines that matter.

**"I'm adding a feature."**
Read the closest spec and plan first. Follow the same spec → plan → SDD loop.
Put pure logic in `domain/` and test it there; keep Android/Compose code thin.

**"The stream misbehaves."**
Read `sse-integration.md` and `AGENTS.md` protocol rules, then
`core/chat/ChatEvent.kt`, `SseEventParser.kt`, `SseClient.kt`. The close set is
the thing to check first.

**"I need to test on a phone."**
Take the artifact from a green `Android build` run on the integration branch;
install over the previous build. The signing key is stable, so upgrades keep
app state. Use the device checklist in the newest handoff.

**"I need to release."**
Get an explicit version from the user. Confirm the commits are on the intended
branch, then dispatch `release.yml` with that version. Do not invent a version
or move an existing tag.

## Local-only notes

`AGENTS.local.md` (git-ignored) holds the owner's test-server URLs, API keys and
deployment notes. **Never copy its contents into a tracked file, commit, doc,
PR or chat log.** `AGENTS.local.md` is intentionally absent from a fresh clone;
ask the owner if you need access to a test server.

`.superpowers/` is also git-ignored. Its ledgers are useful on this machine
only; the durable record is the specs, plans, commit messages and handoffs.
