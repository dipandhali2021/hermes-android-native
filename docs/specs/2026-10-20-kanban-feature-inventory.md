# Kanban — WebUI feature inventory and Android use cases

Date: 2026-10-20
Status: Research input for the Android design. **No product decisions are made here.**
Design: [`2026-10-20-kanban-design.md`](2026-10-20-kanban-design.md) — the decisions taken from this input.
Scope: what the Hermes WebUI Kanban does today, how it is built, which server
contract an Android client can rely on, and what that means for a phone-first UI.

Sources (verified 2026-10-20 against the local trees):

| Layer | Files |
|---|---|
| WebUI UI | `hermes-webui/static/panels.js`, `static/index.html`, `static/style.css`, `static/boot.js`, `static/i18n.js`, `hermes-webui/docs/kanban.md` |
| WebUI bridge | `hermes-webui/api/kanban_bridge.py`, `api/routes.py` |
| Agent backend | `hermes-agent/hermes_cli/kanban_db*.py`, `kanban_workflow.py`, `hermes-agent/plugins/kanban/dashboard/plugin_api.py`, `website/docs/user-guide/features/kanban*.md` |
| Android app | `android-native/app/src/main/java/...`, `docs/ARCHITECTURE.md`, `docs/README.md` |

Line numbers below are indicative; symbols and file paths are the durable
reference.

---

## 1. What Kanban is

Kanban is the work queue for Hermes agents. A **board** holds **tasks**. Each
task is assigned to a **Hermes profile** (a worker identity). A **dispatcher**
claims tasks that are `ready`, spawns an agent worker for the assigned profile,
and the worker moves the task through its lifecycle. Humans create, order,
unblock, inspect, and archive the work; the agents do it.

This is not a generic Trello clone. The statuses encode **who is allowed to do
what**: only the dispatcher can move a task to `running`, and several
transitions are only legal through specific backend verbs.

```
create ──► triage ──► todo ──► ready ──► running ──► done
               │         │         │           │
               │         │         │           ├──► blocked ──► ready (unblock)
               │         │         │           └──► review  (agent only)
               └─────────┴─────────┴──► scheduled ──► ready (unblock)
                                     └──► archived (from anywhere)
```

- **Parent dependencies** gate readiness: a task with an unfinished parent is
  held in `todo`; when its parents finish, it becomes `ready`.
- **Scheduled** is a paused/delayed status. It has **no date or time**: the
  dispatcher simply will not claim it while it sits there.
- **Review** exists in the agent backend but is **not a WebUI column** (see
  §6.3 for the consequence).
- **Archived** is a terminal status, hidden unless explicitly requested.

---

## 2. Data model

### 2.1 Task fields

| Field | Type | Notes |
|---|---|---|
| `id` | string | `t_` + 8 lowercase hex characters |
| `title` | string | required, non-empty |
| `body` | string \| null | Markdown description |
| `status` | enum | see §3.4 |
| `assignee` | string \| null | Hermes profile name; null = unassigned (won't auto-run) |
| `priority` | int | default `0`; higher sorts first |
| `tenant` | string \| null | soft namespace within a board |
| `created_by` | string \| null | WebUI writes `"webui"` |
| `created_at`, `started_at`, `completed_at` | epoch seconds | integers |
| `workspace_kind` | enum | `scratch` (default), `worktree`, `dir` |
| `workspace_path` | string \| null | absolute for `dir`; optional for the others |
| `branch_name` | string \| null | only valid with `worktree` |
| `skills` | string[] \| null | `null` = defaults, `[]` = none |
| `max_runtime_seconds` | int \| null | per-task runtime cap; null = unlimited |
| `result` | string \| null | completion output |
| `model_override`, `provider_override`, `reasoning_effort` | strings | agent plugin surface; not editable in WebUI |
| `parents` / `children` | link rows | dependencies, not columns on the task |
| `claim_lock`, `claim_expires`, `worker_pid`, `current_run_id`, `last_heartbeat_at` | runtime | dispatcher-owned; read-only to clients |
| `consecutive_failures`, `last_failure_error`, `block_kind`, `block_recurrences` | runtime | circuit-breaker/block metadata |

Derived fields added by the WebUI bridge to board rows:

- `age` and `age_seconds`: **the same object**, not a number:
  `{created_age_seconds, started_age_seconds, time_to_complete_seconds}`.
- `link_counts`: `{parents: int, children: int}`.
- `comment_count`: int.
- `progress`: always `null` on the WebUI bridge.

### 2.2 Related objects

| Object | Fields | Where exposed |
|---|---|---|
| Run | `id`, `task_id`, `profile`, `status`, `outcome`, `started_at`, `ended_at`, `summary`, `error`, `metadata`, `worker_pid` | task detail (`runs[]`) |
| Comment | `id`, `task_id`, `author`, `body`, `created_at` | task detail + count on cards |
| Event | `id`, `task_id`, `run_id`, `kind`, `payload`, `created_at` | task detail + SSE |
| Link | `parent_id`, `child_id` | task detail `links` |
| Attachment | `id`, `task_id`, `filename`, `stored_path`, `content_type`, `size`, `uploaded_by`, `created_at` | **agent plugin only**; not on the WebUI bridge |
| Board | `slug`, `name`, `description`, `icon`, `color`, `default_workdir`, `project_id`, `archived`, counts | board endpoints |

### 2.3 Run outcomes

`completed`, `blocked`, `crashed`, `timed_out`, `spawn_failed`, `gave_up`,
`reclaimed`, `review_requested`, `changes_requested`, `scheduled`. Run statuses
include `running`, `done`, `blocked`, `crashed`, `timed_out`, `failed`,
`released`, plus `review` and `scheduled` written by specific verbs.

---

## 3. WebUI feature inventory

### 3.1 Entry points and navigation

- Desktop: an icon rail button opens the Kanban panel.
- Mobile/narrow: the same item lives in the drawer.
- Deep link: `?panel=kanban` is recognized at boot, applied after session
  restore, then removed from the URL.
- There is **no** URL for a board (`#kanban/board/...`) or a task
  (`#kanban/task/...`); browser Back does not close the task detail or switch
  boards.
- The panel has two simultaneous surfaces:
  1. **Sidebar panel**: title, New task, Refresh, search, filters, stats, bulk
     bar, quick-add, flat task list.
  2. **Main surface**: board title, board switcher, view toggle, dispatcher
     actions, inline task preview, horizontal board.

### 3.2 Boards

| Capability | Behavior |
|---|---|
| Active board | Exactly one active board per server/shared state; server pointer is authoritative (`kanban/current` on disk) and the UI keeps a validated local hint |
| Switcher | Always visible (even for a single board); menu lists non-archived boards with totals; current is checked/bold |
| Create | Name (≤64), slug (≤48, auto-suggested), description (≤200), default workspace path (≤255, datalist), icon (≤4 chars), color (default `#7aa2ff`); creating switches to the new board |
| Edit/settings | Name, description, icon, color, default workdir; slug immutable; archive action lives here |
| Archive | Soft archive; tasks remain under `kanban/boards/_archived/`; default board cannot be archived; **no unarchive UI**; no hard delete in the UI |
| Switching | Persists the server pointer; resets the event cursor and reopens live updates; if the server switch fails the local switch still stands |
| Default workdir | Board metadata; consumed by a project dashboard, **not** used as the task-create default in the Kanban UI |

### 3.3 Board views

| Mode | Behavior |
|---|---|
| **Lanes by profile** (default) | One horizontal 7-column board per assignee lane. Lane order: `default` first, other assignees alphabetically, an explicit "unassigned" lane last. Only lanes with tasks render. |
| **Consolidated** | One 7-column board; all assignees merged. |
| Toggle | Header icon; PATCHes `lane_by_profile`; persisted **globally** in `config.yaml` (`dashboard.kanban.lane_by_profile`), not per board. |

There are no swimlanes by tenant/priority, no column collapsing, no WIP limits,
and no manual card ordering.

### 3.4 Columns and statuses

Board columns, fixed order:

```
triage → todo → scheduled → ready → running → blocked → done
```

`archived` is appended only when "include archived" is on. `review` is a valid
agent status but is not a WebUI column.

WebUI status surfaces:

| Surface | Statuses offered |
|---|---|
| Drag card to column (desktop HTML5 drag only) | every visible column except `running`; `archived` when shown |
| Task detail buttons | `triage`, `todo`, `scheduled`, `ready`, `blocked`, `done`, `archived`, plus Block / Unblock |
| Bulk status select | `scheduled`, `ready`, `blocked`, `done`, `archived` |
| Create modal | `triage`, `todo`, `ready` (default Ready) |
| Edit modal | `triage`, `todo`, `ready`; any other real status displays as Triage with an "Actual status: …" hint and is preserved on save unless changed |
| Dispatcher | `ready → running` only |

Manual move matrix (parentless task; individual verbs add more gates):

| From | Allowed targets |
|---|---|
| `triage` | `todo`, `ready` |
| `todo` | `triage`, `scheduled`, `ready` |
| `scheduled` | `triage`, `todo`, `ready` |
| `ready` | `triage`, `todo`, `scheduled`, `blocked`, `review`, `done` |
| `running` | dispatcher-owned; direct PATCH to `running` is rejected |
| `blocked` | `triage`, `todo`, `scheduled`, `ready`, `done` |
| `review` | `triage`, `todo`, `ready`, `done` (WebUI cannot set `review`) |
| `done` | `triage`, `todo`, `ready` |
| `archived` | `triage`, `todo`, `ready` |

Semantics worth preserving in the mobile design:

- **Blocking** from the WebUI uses a fixed reason `"blocked from WebUI"`; there
  is no prompt for a reason. Unblock calls the agent's `unblock_task`, which
  re-gates parents: a child whose parent is unfinished lands in `todo`, not
  `ready`.
- **Scheduled** is entered with `schedule_task` and left with `unblock_task`;
  `done → scheduled` is refused. There is no schedule date/time.
- **Done** requires evidence (a result/summary) unless the task came from
  review; the WebUI's 404/refusal behavior is covered in §5.4.
- **Running** is never user-set. The bridge rejects it with HTTP 400.

### 3.5 Task cards

Displayed on each card:

- Task ID (monospace) + priority badge `P{n}` when nonzero + tenant badge.
- Title.
- Markdown body, visually clamped to 3 lines.
- Meta row: `@assignee` or "unassigned", comment count `💬 n`,
  parent+child link count `↔ n`, age.
- Quick actions: **Complete** (→ done) and **Archive** (→ archived), hidden when
  already in that state.

Behavior:

- Whole card opens the task detail; Enter/Space does the same (desktop).
- Card is the drag handle (desktop HTML5 drag only). Drop calls a status PATCH
  with the detail preview closed.
- Staleness borders: red for `running` > 1 h or `blocked` > 24 h; amber for
  `running` > 10 min, `ready` > 1 h, or `blocked` > 1 h.
- No labels/tags, due dates, estimates, checklists, model badge, or live
  progress indicator on the card.

### 3.6 Creating tasks

Two paths:

1. **Quick add** (sidebar): sends `{title}` only; backend defaults apply
   (including status selection and assignee).
2. **Full modal**: shared create/edit dialog.

| Field | Create behavior |
|---|---|
| Title | required, ≤500 |
| Description | textarea; sent only when non-blank |
| Status | `triage` / `todo` / `ready`; **default Ready** |
| Priority | number −100…100, default 0; sent only when nonzero |
| Assignee | profile list (`default` first, alphabetical) + historical assignees + explicit "Unassigned (won't auto-run)"; default = first profile |
| Tenant | free text ≤64 with datalist of current-board tenants |
| Workspace kind | `scratch` / `worktree` / `directory`; default Scratch |
| Workspace path | ≤255 with datalist; row hidden for Scratch; required for non-scratch on create |
| Skills | comma-separated, ≤255; sent as a trimmed array |
| Max runtime | integer ≥1 seconds; empty = unlimited; strict integer validation |
| Parent dependencies | single task ID from a datalist (titles shown); sent as `parents: [id]` |

Warnings/validation:

- Ready + Unassigned shows a one-time soft warning; a second submit proceeds.
- API errors render in one shared inline error line, not per field.
- The edit mode disables workspace kind/path, skills, max runtime, and parents
  (the backend does not patch them).

### 3.7 Editing tasks

| Field | Edit behavior |
|---|---|
| Title, description, priority | editable; always sent (body empty clears it, priority blank = 0) |
| Assignee | editable; blank sends `null` |
| Tenant | editable; blank sends `null` |
| Status | only sent when the select differs from the displayed value; non-editable statuses display as Triage with a hint |
| Workspace kind/path, skills, max runtime, parents | disabled, not sent |

Consequence of the "display as Triage" mapping: a Blocked/Scheduled/Running/
Done/Archived task cannot be changed to Triage through the modal without first
choosing another value; the detail buttons are the direct path.

### 3.8 Task detail

The detail is an **inline preview above the board**, not a page, sheet, or
modal. The board remains below it. There is no history entry.

Contents, in order:

1. Header: Back to board, title, Edit task.
2. Description (Markdown, "No description" fallback).
3. Meta: assignee, tenant, priority (`P{n}` whenever the field exists, including
   P0), comment count, children count.
4. Status buttons: Triage, Todo, Scheduled, Ready, Blocked, Done, Archived,
   Block, Unblock. No Running, no current-status highlight, no confirmation.
5. **Comments (n)**: author, localized timestamp, Markdown body; composer with
   "Add comment"; author defaults to `webui`; empty comments ignored. No edit,
   delete, or reactions.
6. **Events (n)**: summary, raw payload JSON, timestamp.
7. **Links**: parents and children ID chips with remove; input + datalist +
   "Add dependency". Self-links are rejected; "Add dependency" means *this task
   depends on the entered task* (`parent_id = entered`, `child_id = current`).
8. **Runs (n)**: id/status, error/summary/log tail, started → finished.
9. **Worker log**: raw log tail (fetched with `tail=65536`).

The detail refreshes when a live event for the open task arrives. It has no
delete, stop/retry, attachments, workspace display, schedule time, model
override, copy-ID, duplicate, or move-to-board action.

### 3.9 Dependencies

- Add/remove parent-child links from the task detail.
- The WebUI shows only IDs plus an add-field; there is no graph view and no
  dependency picker beyond a datalist.
- A child whose parents are unfinished is held in `todo`; `unblock_task`
  applies the same gate when leaving `blocked`/`scheduled`.

### 3.10 Comments

- Plain text/Markdown body, author string, server timestamp.
- Available on the detail only; count on the card.
- No edit/delete/reactions/mentions.

### 3.11 Events, runs, and worker log

- Events are a raw audit feed: kind + payload + timestamp, displayed as JSON.
- Runs are attempt history: status, outcome, summary/error, log tail, timing.
- Worker log is the agent worker's raw stdout/stderr, truncated to the requested
  byte tail.
- None of these are searchable, filterable, or paginated in the WebUI.

### 3.12 Dispatcher

Two actions in the main header and the sidebar:

| Action | Behavior |
|---|---|
| **Preview** | `POST /api/kanban/dispatch?dry_run=1&max=8`; shows a toast with what would happen; 6 s |
| **Run dispatcher** | Confirmation dialog, then `POST /api/kanban/dispatch?max=8`; toast for 8 s; works on the default board too |

Result fields shown: spawned, promoted, reclaimed, skipped (unassigned),
skipped (unknown profile), auto-blocked, timed out, crashed. All dispatcher
buttons disable while a call is in flight. There is no per-task dispatch, no
per-task max, and no progress screen.

One tick may also reclaim stale claims, reconcile orphans, promote `todo →
ready`, fill profile caps, and reserve review capacity. The endpoint's `max` is
clamped 1–100 (default 8); the UI always sends 8.

### 3.13 Search, filters, and stats

| Control | Behavior |
|---|---|
| Search | Client-side, immediate; lowercase substring over `id`, `title`, `body`, `assignee`, `tenant` |
| Assignee filter | Server-side (`assignee=`); options = board assignees |
| Tenant filter | Server-side (`tenant=`); options = board tenants |
| Include archived | Server-side; appends the Archived column |
| Only mine | Server-side; resolves the active Hermes profile |
| Clear filters | Only inside the "Tasks hidden by filters" empty state; resets search + all filters |
| Stats | `/api/kanban/stats`; status counts, oldest ready age; **not filter-aware**; sorted alphabetically; failures silent |

- No sorting controls. Backend order is `priority DESC, created_at ASC`
  (`done` is newest-completed first in the agent plugin, not the WebUI bridge).
- No pagination, infinite scroll, or virtualization. `/board` returns every
  matching task with its full body.
- Filters are not persisted and are not in the URL.
- Server config can set `default_tenant` and `include_archived_by_default`.

### 3.14 Bulk actions (current reality)

- The UI has a bulk status select and "Bulk action" button, but **no
  multi-select**. The selection is the currently open card, else the last opened
  task; otherwise the action silently does nothing.
- It PATCHes `/api/kanban/tasks/bulk` with `{ids, status}`.
- The backend endpoint also supports `archive`, `assignee`, and `priority`;
  the UI exposes only `status`.

### 3.15 Live updates

- Primary: SSE at `/api/kanban/events/stream` with `since` or `Last-Event-ID`.
- On an event: update the cursor and debounce a full board reload at 250 ms;
  refresh the open detail if the event belongs to it.
- Ignore events while the user is on another panel; stop the stream when
  leaving Kanban.
- Fallback: after three SSE failures, close SSE and poll
  `/api/kanban/events?since=` every 30 s, followed by a board load.
- Server side: 0.3 s DB poll, batches up to 200 events, 15 s keepalive.
- No optimistic updates, no offline queue, no badge/notification while away.

### 3.16 States and feedback

| State | WebUI |
|---|---|
| Loading | plain "Loading…" text, no skeleton/spinner |
| Empty board | "No Kanban data" + work-queue hint |
| Filters hide everything | "Tasks hidden by filters" + Clear filters |
| Empty column | dashed "Empty" box |
| Board/list failure | `Kanban unavailable: {raw error}` |
| Stale client | "Kanban needs a hard refresh" + hard-refresh button |
| Task detail failure | toast only; old content stays |
| Dialog failure | inline shared error region |
| Confirmations | dispatcher run and board archive only |
| Toasts | dispatcher preview/run, bulk action, view toggle, board archived, dependency self-link, some failures |

No confirmation for completing, archiving, moving, blocking, bulk status,
comments, dependency changes, or closing dirty dialogs.

### 3.17 Persistence

| Thing | Persisted? |
|---|---|
| Active board | Server pointer + `localStorage` hint |
| Lane vs consolidated | Global `config.yaml` setting |
| Search / assignee / tenant / include-archived / only-mine | No |
| Open task / scroll positions | No |
| Per-board view preferences | No |
| Task/board deep links | No |

### 3.18 Desktop-only affordances

- Card drag is HTML5 drag: **no touch drag-and-drop** (documented explicitly).
- Keyboard: card opens on Enter/Space; modals trap focus and close on Escape;
  task create submits on Enter outside textareas; board switcher has no arrow-key
  navigation; there is no Kanban global shortcut.
- Accessibility gaps that a mobile rebuild should not copy: nested interactive
  controls inside a `role="button"` card, no `aria-selected`, no current-status
  indication on detail buttons, no live region for the board, tiny touch targets
  (10–11 px labels, 2–6 px padding).

### 3.19 Mobile behavior already present in the WebUI

- Board: horizontal scroll with `scroll-snap`, columns ~82vw wide, one column
  plus a peek of the next; each column scrolls vertically.
- Detail: stacks vertically above the board; it pushes the board down instead of
  overlaying.
- Filters: live in the drawer/sidebar, so on a phone they require opening the
  drawer.
- Modals: viewport-capped height with internal scroll and safe centering.
- No swipe actions, long-press menus, pull-to-refresh, or haptics.

---

## 4. Use cases

| # | Use case | What the user does | Capabilities required |
|---|---|---|---|
| 1 | **Morning check** | Opens the board to see what ran overnight, what is blocked, and what is ready | Board load, status counts, staleness/age, live refresh |
| 2 | **Quick capture** | Adds a task from the phone in a few seconds | Title-first quick add, sensible defaults |
| 3 | **Full task authoring** | Creates a task with description, assignee, priority, workspace, skills, runtime, parent | Full create form with progressive disclosure |
| 4 | **Triage** | Moves new work from `triage` to `todo` or `ready` | Status change (direct write) |
| 5 | **Assign and run** | Picks a profile, then runs the dispatcher | Assignee edit, dispatcher preview/run, result summary |
| 6 | **Inspect a failure** | Opens a blocked/crashed task, reads the run error and worker log, unblocks or edits | Task detail, runs, log, block/unblock |
| 7 | **Review finished work** | Checks `done` tasks and their results | Done column, result/summary, comments |
| 8 | **Organize by profile** | Switches between lanes or consolidated to see who is doing what | Lane/consolidated view (or a mobile equivalent) |
| 9 | **Pause / resume** | Moves a task to Scheduled, later back to Ready | `schedule_task` / `unblock_task` semantics |
| 10 | **Sequence work** | Adds a dependency so a task waits for another | Links add/remove, parent re-gating |
| 11 | **Leave context** | Adds a comment for the next human or worker | Comment composer |
| 12 | **Tidy the board** | Completes/archives one task or a group | Complete/archive; real multi-select is a new capability |
| 13 | **Switch projects** | Moves between boards | Board list, switch, create/edit/archive |
| 14 | **Filter to my work** | "Only mine" and assignee/tenant filters | Server-side filters, profile identity |

Primary actors: the board owner (human) creates/routes/inspects; agent workers
consume tasks and write events/comments/results. The phone is more likely to be
used for **checking, triaging, unblocking, and quick capture** than for
authoring long task descriptions, but that is an assumption to validate with
the user.

---

## 5. Server contract available to the Android client

The Android app already talks to the **WebUI** origin and authenticates with the
`hermes_session` cookie. The WebUI Kanban bridge (`/api/kanban/*`) is therefore
the natural target. The Hermes Agent's dashboard plugin
(`/api/plugins/kanban/*`) is richer, but it is a different service surface with
different auth and is not exposed by the WebUI origin.

### 5.1 Transport and auth

- Base path: `/api/kanban/...` on the configured WebUI base URL.
- Auth: the existing `hermes_session` cookie; 401 `{"error":"Authentication
  required"}` when unauthenticated.
- CSRF: only applies when the request carries `Origin`/`Referer`. The Android
  client already strips those and `Sec-Fetch-*`; no CSRF token is needed.
- Errors: `{"error": "<message>"}` with 400/401/403/404/409/429/503.
- Profile scoping: the `hermes_profile` cookie selects the active profile;
  `only_mine=1` uses it. Kanban data itself is profile-agnostic.
- Timestamps: integer epoch **seconds**.
- IDs: task IDs are strings; run/comment/event IDs are 64-bit integers.

### 5.2 Endpoints

| Method | Path | Purpose / key parameters | Response |
|---|---|---|---|
| GET | `/api/kanban/boards` | list boards (`include_archived`) | `{boards[], current, read_only}` |
| GET | `/api/kanban/board` | full board (`board`, `tenant`, `assignee`, `include_archived`, `only_mine`, `since`) | `{columns[], tenants[], assignees[], latest_event_id, changed, read_only, filters}` |
| GET | `/api/kanban/config` | board config (`board`) | `{columns, assignees, default_tenant, lane_by_profile, include_archived_by_default, render_markdown, read_only}` |
| GET | `/api/kanban/stats` | board counts (`board`) | `{by_status, by_assignee, oldest_ready_age_seconds, now}` |
| GET | `/api/kanban/assignees` | profile assignees + counts (`board`) | `{assignees:[{name,on_disk,counts}]}` |
| GET | `/api/kanban/events` | event page (`board`, `since`, `limit` 1–200) | `{events[], cursor, latest_event_id, read_only}` |
| GET | `/api/kanban/events/stream` | SSE live feed (`board`, `since`/`Last-Event-ID`) | `text/event-stream` |
| GET | `/api/kanban/tasks/{id}` | task detail (`board`) | `{task, comments[], events[], links{parents,children}, runs[], read_only}` |
| GET | `/api/kanban/tasks/{id}/log` | worker log (`board`, `tail` bytes 1–2,000,000) | `{task_id, path, exists, size_bytes, content, truncated}` |
| POST | `/api/kanban/boards` | create board (`slug,name,description,icon,color,default_workdir,switch`) | `{board, current, read_only}` |
| POST | `/api/kanban/boards/{slug}/switch` | set active board | `{current, read_only}` |
| PATCH | `/api/kanban/boards/{slug}` | edit board (`name,description,icon,color,default_workdir,archived`) | `{board, read_only}` |
| DELETE | `/api/kanban/boards/{slug}` | archive (`delete=1` hard-deletes) | `{result, current, read_only}` |
| POST | `/api/kanban/tasks` | create task (`title, body, assignee, tenant, priority, parents, triage, workspace_kind, workspace_path, max_runtime_seconds, skills, status, ...`) | `{task, read_only}` |
| PATCH | `/api/kanban/tasks/{id}` | patch task (`title, body, tenant, priority, assignee, status, result, summary, block_reason, reason`) | `{task, read_only}` |
| POST | `/api/kanban/tasks/{id}/patch` | same as PATCH | `{task, read_only}` |
| POST | `/api/kanban/tasks/bulk` | bulk (`ids, status, archive, assignee, priority`) | `{results:[{id,ok,error?}], read_only}` |
| POST | `/api/kanban/tasks/{id}/block` | block (`reason`/`block_reason`) | `{task, read_only}` |
| POST | `/api/kanban/tasks/{id}/unblock` | unblock (re-gates parents) | `{task, read_only}` |
| POST | `/api/kanban/tasks/{id}/comments` | add comment (`body`, `author?`) | `{ok, comment_id, read_only}` |
| POST | `/api/kanban/links` | add dependency (`parent_id, child_id`) | `{ok, parent_id, child_id, read_only}` |
| POST | `/api/kanban/links/delete` | remove dependency | `{ok, changed, parent_id, child_id, read_only}` |
| DELETE | `/api/kanban/links` | remove dependency (JSON body) | same |
| POST | `/api/kanban/dispatch` | dispatcher (`board`, `dry_run`, `max` 1–100) | `DispatchResult` |
| PATCH | `/api/kanban/config` | view mode (`lane_by_profile` bool) | config payload |

### 5.3 Payload and protocol quirks

- `GET /board` with `since >= latest_event_id` returns **only**
  `{changed:false, latest_event_id, read_only}` — no `columns` key. Clients must
  handle this as "nothing changed", not as an empty board.
- SSE without `since` replays from event 0. Start from the `latest_event_id`
  returned by the board load.
- SSE frames: `hello` with `{cursor, board}`, then `events` with
  `{events[], cursor}`; keepalive comments every 15 s.
- `read_only` exists on WebUI responses and is always `false` today.
- `age`/`age_seconds` is an object (see §2.1).
- The WebUI bridge has **no task list endpoint**. `GET /api/kanban/tasks`
  returns 404 "unknown Kanban endpoint"; the board endpoint is the only list
  source.
- The WebUI bridge has **no task delete**, no attachments, no review status, no
  run termination/diagnostics, no projects, and no model options.
- Refused transitions are inconsistent: some return 404 (treated as "not
  found"), some 400/409. Clients must surface the server message.
- Create with an invalid/refused `status` can leave the task created; PATCH
  applies title/body/tenant/priority before assignee/status, so a failed status
  change can leave earlier fields applied. There is no conditional update token.
- `POST /dispatch` on the WebUI bridge does not pass the board into the
  dispatcher's lock/hook key; the endpoint still operates on the board-scoped
  connection, but clients should not assume per-board isolation in the
  dispatcher lock.

### 5.4 SSE / polling behavior the client owns

- Connect on entering Kanban; close on leaving.
- Debounce reloads (~250 ms is the WebUI's choice).
- Fall back to polling after repeated SSE failures.
- No server guarantees about notifications while the app is backgrounded; Android
  needs its own foreground/background policy.

---

## 6. Gaps and quirks in the WebUI feature set

These are **not** implemented in the WebUI, even where the agent backend
supports them:

1. Task **delete** (hard) — no UI or bridge route.
2. Run **stop / retry / terminate**; reclaim; diagnostics.
3. **Attachments** (upload/list/download/delete) — agent plugin only.
4. **Review** status/column and the review workflow — agent plugin only; the
   bridge rejects `review` and drops review tasks from the board.
5. **Task model/provider/reasoning overrides** — agent plugin only.
6. **Schedule date/time** — Scheduled is status-only.
7. **Real multi-select** — the bulk bar acts on zero or one task.
8. **Bulk assignee/priority** in the UI (backend supports them).
9. **Sorting, pagination, virtualization**.
10. **Touch drag-and-drop** and keyboard drag.
11. **Task move between boards**, duplicate/clone, copy ID.
12. **Board unarchive/restore**, hard delete, duplicate, export/import.
13. **Labels/tags, due dates, estimates, checklists/subtasks.**
14. **Comment edit/delete/reactions/mentions.**
15. **Deep links** to a board or task; back-button integration.
16. **Per-board filters/view preferences**; saved filter sets.
17. **Notifications/badges** for board changes while away.
18. **Read-only enforcement** — the banner exists but `read_only` is always false.

A mobile client that mirrors the WebUI should treat these as **out of scope for
v1** unless the user explicitly wants new server capability. Some of them
(review, attachments, delete, stop) would require extending the WebUI bridge or
calling the agent plugin API.

---

## 7. Current Android state

- Kanban already appears in the app's FAB menu
  (`ui/nav/HermesRoot.kt`, `PanelKind.KANBAN`) and opens a **read-only vertical
  list** (`ui/panels/PanelScreens.kt`, `KanbanPanel`).
- That panel calls `GET api/kanban/tasks`
  (`core/net/HermesApi.kt`, `kanbanTasks`) and expects
  `{tasks:[{id,title,description,status,assignee,priority}]}`
  (`core/model/Panels.kt`, `KanbanTaskDto`).
- The current WebUI bridge serves **neither** that path nor that shape. The real
  list is `GET /api/kanban/board` with `columns[].tasks[]`, and the task body
  field is `body`, not `description`; `priority` is an integer, not a string.
  In practice the existing panel fails its request and shows the panel error
  state. Treat it as a stub to be replaced, not as a foundation.
- The app has no board selector, no task mutations, no filters, no live
  updates, and no Kanban DTOs beyond the stub.
- Architecture constraints that will apply to the new feature:
  - Single `:app` module, package-by-feature.
  - Hilt ViewModels; one immutable `UiState` per screen; `Channel` effects.
  - OkHttp + kotlinx.serialization; all wire fields optional with defaults.
  - Cookie auth (`hermes_session`); no bearer token.
  - No offline cache (a deliberate project decision).
  - Material3 1.4.0; `MaterialExpressiveTheme`/`MotionScheme` are `internal`, so
    expressive motion is implemented with the project's own
    `ui/theme/Motion.kt` tokens.
  - Colors only from `MaterialTheme.colorScheme`; motion only from `Motion.kt`.
  - No bottom navigation/rail/drawer today; panels are reached from a FAB menu.
  - No Compose UI tests; unit tests use JUnit4 + MockWebServer.

---

## 8. Mobile implications (observations, not decisions)

1. **The board is the product.** A phone cannot show 7 columns side by side.
   The WebUI already uses horizontal snap-scrolling; a native design can choose
   a column pager, a single-column focus mode, or a different structure.
2. **Detail is where most actions live.** Comments, events, runs, logs,
   dependencies, and status moves all live there. On a phone this wants a
   full-height surface (screen or sheet), not an inline block above the board.
3. **Status legality matters.** The UI must offer only legal moves, never
   `running`, and must explain `scheduled`/`blocked`/`done` behavior. A mobile
   "Move to…" control is more discoverable than drag and works with
   accessibility.
4. **Filters need a first-class home.** In the WebUI they are in the sidebar;
   on a phone they should be reachable from the board header without opening a
   drawer.
5. **Bulk actions are not real yet.** Multi-select would be a new capability;
   if included, it needs a selection mode and a contextual action bar.
6. **Live updates are available.** SSE works on the same origin; the Android app
   already has an SSE client. Polling is the fallback, not the design center.
7. **Payloads are heavy.** Every board load includes full task bodies and every
   detail load includes all comments/events/runs. No pagination exists, so a
   busy board will need client-side restraint (lazy rendering, collapsed
   sections) rather than assumptions about server limits.
8. **Some features are missing server-side.** A design that depends on review,
   attachments, delete, or stop must either extend the bridge or explicitly
   defer them.
9. **The app's navigation is FAB-first.** Kanban currently lives one level deep
   behind the FAB menu. If Kanban becomes a daily surface, its entry point
   deserves a structural decision (e.g. a primary destination), not just a
   prettier panel.
10. **Material 3 Expressive is available but constrained.** The project can use
    expressive shapes, color, and custom springs via `Motion.kt`, but cannot use
    the `MaterialExpressiveTheme`/`MotionScheme` APIs until material3 1.5.

---

## 9. Open questions for the design conversation

These change what gets built; they are not answerable from the code:

1. **Primary phone use**: is the phone mainly for *monitoring and steering*
   agent work, or for *creating and organizing* tasks? The board/detail/home
   emphasis depends on it.
2. **v1 scope**: full WebUI parity, or a focused subset with progressive
   disclosure for advanced fields (workspace, skills, runtime, dependencies,
   events/logs)?
3. **Navigation placement**: keep Kanban behind the existing FAB menu, or
   promote it to a primary destination with its own navigation model?
4. **Missing capabilities**: should v1 stay at WebUI parity (no review,
   attachments, delete, stop), or is any of that required now?
5. **Live behavior**: should the board stream while the app is foregrounded
   only, or also show background notifications/badges?

The answers feed `docs/specs/2026-10-20-kanban-design.md`.
