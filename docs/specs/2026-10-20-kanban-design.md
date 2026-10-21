# Kanban on Android — product and interaction design

Date: 2026-10-20
Status: Proposed — awaiting review
Surface: `android-native` app, talking to the Hermes WebUI Kanban bridge
Research input: [`2026-10-20-kanban-feature-inventory.md`](2026-10-20-kanban-feature-inventory.md)
Server contract: `hermes-webui/api/kanban_bridge.py` (`/api/kanban/*`)

---

## 0. The design in one paragraph

Kanban becomes a **full-screen, board-first workspace** with three levels: **Board**
(a horizontal pager of status columns, navigated by a status rail), **Task**
(one task, split into Overview / Activity / Runs), and **Compose** (create/edit).
The status rail is the map of the workflow and the primary way to move around;
a single extended FAB captures work; a contextual overflow holds everything else.
There is **no drag-and-drop** — status changes go through an explicit, legality-aware
"Move to…" sheet, which is faster on a phone and works with TalkBack. The WebUI's
profile *lanes* are replaced by an **assignee filter**, because seven columns times
N profiles is not a phone layout. The board is **live by default** (SSE, with a
polling fallback), **optimistic where safe**, and it **teaches the status model**
in place instead of assuming the user has read the docs.

The old read-only panel is deleted, not improved.

---

## 1. Goals, non-goals, assumptions

### Goals

1. Open the app, reach the board, and answer **"what needs me right now?"** in under
   five seconds.
2. Capture a task in **three interactions** (FAB, type a title, Create) with
   sensible defaults.
3. Make every supported task action reachable without a desktop: move, complete,
   block/unblock, archive/restore, comment, depend, inspect runs and logs.
4. Make the status model understandable: what each status means, what is legal,
   and why something is refused.
5. Feel like Material 3 Expressive: large type, soft shapes, tonal surfaces,
   spring motion, one clear action per surface — using only APIs that exist in
   material3 1.4.0.
6. Ship in slices that are independently testable on a device.

### Non-goals (v1)

- A generic Trello clone: no labels, due dates, estimates, checklists, swimlanes,
  WIP limits, manual ordering, or drag-and-drop.
- Offline editing or an offline cache. The app has none today and the bridge has
  no conflict token.
- Background notifications, badges, or widgets while the app is closed.
- Features the WebUI bridge does not expose: `review`, attachments, hard delete,
  run stop/terminate as a dedicated verb, task duplication, move between boards,
  board unarchive. (See §15.)
- Replacing the app's global navigation with a bottom bar. Kanban is reached from
  the existing FAB menu; the app shell stays as it is.

### Assumptions

- The phone is used mostly for **checking, triaging, unblocking, quick capture,
  and starting runs**, with longer authoring as a secondary case. The design
  supports full authoring, but puts it behind progressive disclosure.
- The server is the Hermes WebUI origin the app already uses (cookie auth), and
  may be older or newer than the app. Unknown fields and unknown status columns
  must degrade gracefully.
- A board can be large. The bridge returns every matching task with its full body,
  so the client must render lazily and never assume pagination.

---

## 2. Design principles

1. **One decision per surface.** Board = where work is; Task = what this work is;
   Compose = authoring. Sheets are for one decision (filter, move, complete, run).
2. **The rail is the map.** Seven statuses are too many for tabs; a scrollable
   count-bearing rail shows the whole pipeline and jumps between columns.
3. **Explicit beats gestural.** No drag. Every status move is a labelled target
   with a sentence explaining the consequence. This is better on touch, better
   with accessibility services, and better at teaching the workflow.
4. **Progressive disclosure, not omission.** Workspace, skills, runtime, parents,
   events, and logs exist — folded away until asked for.
5. **The server is the source of truth.** Optimism is a courtesy; a refusal is
   shown in the server's own words and the board reconciles.
6. **Live, but honest.** Say when updates are live, reconnecting, or polling.
   Never present stale data as fresh without a signal.
7. **No dead ends.** Every empty, loading, and error state offers the next action.

---

## 3. Information architecture and navigation

```
Home (existing)
└── FAB menu ── Kanban                         [new full-screen destination]
     │
     ├── Board  (default)
     │    ├── Board switcher            bottom sheet
     │    ├── Filter                     bottom sheet
     │    ├── Run dispatcher             bottom sheet
     │    ├── How this board works       bottom sheet
     │    ├── New task                   full screen
     │    ├── Board settings             full screen
     │    ├── New board                  full screen
     │    └── Task detail                full screen
     │         ├── Edit task             full screen
     │         └── Worker log            full screen
     └── (no drawer, no bottom navigation added)
```

### Navigation decisions

- Add `Routes.KANBAN = "kanban"` and a small set of `kanban/...` routes. The FAB
  item for `PanelKind.KANBAN` navigates there; the generic panel destination no
  longer handles Kanban. The stub `KanbanPanel`, `kanbanTasks`, and
  `KanbanTaskDto` in `Panels.kt` are deleted.
- Every surface is a real destination or a sheet, so the system back gesture
  behaves correctly. No inline detail above the board (the WebUI's biggest
  mobile mistake).
- Board state (selected column, filters, scroll positions) survives a trip to a
  task and back; the board refreshes on return.
- The FAB menu remains the entry point in v1. If Kanban becomes a daily surface,
  promoting it is a separate app-shell decision, not smuggled into this feature.

### Routes

| Route | Surface |
|---|---|
| `kanban` | Board (server current; resolved through `/boards`) |
| `kanban/new?board={slug}&status={triage\|todo\|ready}` | Create task |
| `kanban/task/{taskId}?board={slug}&cursor={cursor}` | Task detail (cursor seeds the detail SSE) |
| `kanban/task/{taskId}/edit?board={slug}` | Edit task |
| `kanban/task/{taskId}/log?board={slug}` | Worker log |
| `kanban/board/new` | Create board |
| `kanban/board/{slug}/settings` | Board settings |

Every task-scoped route carries the **board slug**, and every task API call passes
`board={slug}` explicitly. The server's active-board pointer is shared with the
CLI, the WebUI, and other devices; relying on it from a pushed detail screen can
resolve a task against the wrong board (or 404) after an external switch or a
process-death restore. Pinning the route is the fix.

Sheets are not routes: filter, board switcher, move-to, complete, block/progress,
dispatcher, assignee picker, parent picker, tenant picker, and help.

---

## 4. The Board screen

### 4.1 Structure

```
┌────────────────────────────────────────────────────┐
│ ←  ▣ Hermes Board ⌄            🔍  ⚙︎ •   ⋮        │  TopAppBar
│    12 tasks · 3 ready  ● Live                      │  subtitle
├────────────────────────────────────────────────────┤
│ [Triage 2] [Todo 4] [Scheduled 1] [Ready 3] …      │  status rail
├────────────────────────────────────────────────────┤
│  ● Only mine ×   #acme ×                           │  active filter chips
├────────────────────────────────────────────────────┤
│                                                    │
│  Ready                                    3        │  column header
│  The dispatcher can claim these.                   │
│  ┌──────────────────────────────────────────────┐  │
│  │ t_4f2a1c  P2  #acme                      ⋮   │  │
│  │ Fix the OAuth callback                      │  │  card
│  │ The state parameter is dropped when …       │  │
│  │ 👤 @default   💬 3   ↔ 1   · 2h             │  │
│  └──────────────────────────────────────────────┘  │
│  …                                                 │
│                                                    │
│                                      ╭─────────╮   │
│                                      │ + New   │   │  extended FAB
│                                      ╰─────────╯   │
└────────────────────────────────────────────────────┘
```

One column per page on phones, 100% width minus 16 dp margins. The column header
is fixed above its list so the user always knows which status they are in. The
status rail is horizontally scrollable and always visible; it is the pager's
indicator as well as its controller.

### 4.2 Top app bar

- **Leading:** back arrow (returns to the app's home screen).
- **Title:** board icon (emoji in a tonal circle, or the first letter), board name
  (`titleLarge`), and a chevron. The whole title block is one 48 dp-tall click
  target that opens the **board switcher**.
- **Subtitle:** `"{n} tasks · {ready} ready"` plus a live-state dot and label:
  - `● Live` (primary dot),
  - `● Reconnecting` (tertiary, animated only if reduced motion is off),
  - `● Polling` (tertiary),
  - `● Offline` (error).
  - When filters are active: `"{shown} of {total} tasks"`.
- **Actions:**
  - Search (magnifier): expands a pill-shaped search field below the bar.
  - Filter (funnel): opens the filter sheet; a small dot on the icon when any
    filter is active.
  - Overflow: **Run dispatcher**, **Refresh**, **How this board works**,
    **Board settings**, **New board**.
- The bar is a standard `TopAppBar`, not a large/collapsing one: the rail and
  column header already consume vertical space, and a collapsing title would hide
  the board name exactly when the user is scrolling tasks.

### 4.3 Status rail

A single horizontally scrolling row of `FilterChip`s, one per column in server
order (including `archived` when enabled). Each chip contains:

- an 8 dp status dot (semantic colour),
- the status name (`labelLarge`),
- the count (`labelLarge`, `onSurfaceVariant`).

Behaviour:

- Selected chip: `selected = true` (container + check-less styling), auto-scrolled
  into view when the pager settles.
- Tap: animate the pager to that column with `HermesMotion.spatial()` and give a
  light `TextHandleMove` haptic.
- Tap the already-selected chip: scroll that column to the top.
- Swipe the pager: the chip selection follows the settled page and the rail
  scrolls to keep it visible.
- Counts reflect **client-side search** and the loaded (server-filtered) board.
- A zero-count chip stays visible (the workflow shape is information), but its
  dot is muted.
- The rail announces itself as "Board statuses" and each chip as
  "{status}, {n} tasks, {selected}".

Default selected column on first load:

1. `ready` if it has tasks,
2. else `running` if it has tasks,
3. else `triage` if it has tasks,
4. else the first column.

This lands a morning check on the action queue, while the rail still exposes
blocked/running counts. The selection is kept for the session and per board;
switching boards recomputes it.

### 4.4 Column page

- Header:
  - Status name (`headlineSmall`) + count in a tonal pill (`titleSmall`).
  - One-line description (`bodySmall`, `onSurfaceVariant`). This is the teaching
    layer; the same text appears in the Help sheet.
  - `ready`: a trailing text button **Run** that opens the dispatcher sheet.
  - `ready`: if the oldest ready task is older than an hour, append
    "Oldest waiting 2h" to the description in `tertiary`.
- List: `LazyColumn`, 12 dp side padding, 10 dp vertical spacing, 96 dp bottom
  padding to clear the FAB. Cards are keyed by task id.
- Pull-to-refresh wraps the column list; the refresh indicator is shared across
  pages.
- Empty column: a compact tonal panel with a status icon, "Nothing in {status}",
  the one-line description, and a text button **Add task** that opens Create with
  that status preselected. (Triage/Todo/Ready only; other statuses omit Add.)
- A small "N hidden by search" line when a column has tasks but the query hides
  them; no need for a separate result page.

Status descriptions:

| Status | Line |
|---|---|
| Triage | New work. Decide where it belongs. |
| Todo | Planned, but not runnable yet. |
| Scheduled | Paused. The dispatcher skips these — there is no date or time. |
| Ready | The dispatcher can claim these. |
| Running | A worker is on it right now. |
| Blocked | Stuck. Read the reason, then unblock or reassign. |
| Done | Finished. The result lives on the task. |
| Archived | Removed from the active board. |

### 4.5 Task card

A `Surface` (not `Card`) with `surfaceContainerLow`, `MaterialTheme.shapes.large`
(22 dp), and 16 dp padding. The whole surface is clickable and opens the detail;
the overflow is a separate 40 dp target.

```
t_4f2a1c   P2   #acme                              ⋮
Fix the OAuth callback redirect
The state parameter is dropped when the provider
returns to a URL with an existing query string.
👤 @default    💬 3    ↔ 1    · 2h
```

- **Meta row:** task id (monospace, `labelSmall`, `onSurfaceVariant`), priority
  badge (`P{n}`) only when nonzero, tenant pill only when set, overflow at the end.
- **Title:** `titleMedium`, max 3 lines, ellipsis.
- **Body preview:** plain text (markdown stripped), `bodySmall`,
  `onSurfaceVariant`, max 2 lines. Omitted when empty.
- **Footer:** assignee (`👤 @name` or "Unassigned"), comment count (only > 0),
  dependency count (parents + children, only > 0), age. The age is chosen by
  status: created age for triage/todo/scheduled/ready/blocked; started age for
  running; time to complete for done. Compact form: `just now`, `12m`, `2h`,
  `3d`; older than a week shows a date.
- **Unassigned ready tasks** show `Unassigned · won't run` in `error` — this is
  the single most common reason a ready task never runs.
- **Staleness:** no coloured card borders. A small trailing pill appears only
  when stale, with an icon + text:
  - running > 10 min → "slow" (`tertiary`), > 1 h → "stale" (`error`);
  - blocked > 1 h → "blocked 2h" (`tertiary`), > 24 h → "stale" (`error`);
  - ready > 1 h → "waiting 2h" (`tertiary`).
  The same thresholds as the WebUI, expressed as words, not just colour.
- **Running cards** show a static `▶ running` marker in the meta row. No per-card
  infinite spinner: seven pulsing cards is noise and implies progress the data
  does not contain.
- **Done cards** are muted (`onSurfaceVariant` title) and show the completion age.
- **Archived cards** are muted and show a "Restore" affordance in the overflow.
- **Overflow menu** (status-aware): Open, Move to…, Complete…, Block…, Unblock,
  Archive…, Copy ID. Destructive/stop semantics are labelled explicitly:
  - Archive on a running task says **"Archive and stop worker"**.
  - Complete is only present when the server accepts it (ready/blocked).
- **Highlight after create:** the new card is scrolled into view and its container
  flashes from `primaryContainer` to `surfaceContainerLow` once over ~600 ms.
- **Content descriptions:** the card reads title, status, assignee, age; counts
  are read as "3 comments", "1 dependency"; the overflow is "Task actions".

### 4.6 Searching

- A search icon replaces the subtitle area with a pill-shaped `OutlinedTextField`
  ("Search tasks") with a leading magnifier and a trailing clear/close. It
  autofocuses, and the keyboard's search action dismisses it.
- Filtering is client-side, case-insensitive substring over id, title, body
  (plain), assignee, and tenant. It is debounced 120 ms for large boards.
- The status rail and column counts update to the match counts.
- An active search appears as an `InputChip` ("Search: foo") in the filter row so
  it is obvious why tasks are missing.
- Closing search clears the query (the WebUI keeps it; on a phone a hidden active
  search is a trap). Navigating into a task keeps it; switching boards clears it.

### 4.7 Filters

A bottom sheet, not a drawer. It edits a **draft**; nothing reloads per chip.

- **Assignee** (single choice): "Anyone" (default), "Only my profile"
  (`only_mine=1`), then each known assignee. Selecting a name
  sends `assignee`; selecting "Only my profile" sends `only_mine=1` and no
  assignee (the server resolves the active profile from the cookie). Label helper:
  "Only my profile uses the server's active profile."
- **Tenant** (single choice): "Any tenant", then the current board's tenants.
  The initial tenant comes from `/api/kanban/config`'s `default_tenant` (matching
  the WebUI); it is an active filter and appears as a chip, never a hidden default.
  The filter sheet's "Any tenant" clears it.
- **Include archived**: a switch row; adds the Archived column. Default comes from
  `/api/kanban/config` (`include_archived_by_default`).
- **Footer:** "Clear all" (text) and "Apply filters" (filled). Apply is disabled
  when the draft equals the active filters. On apply: dismiss, keep the previous
  board visible, show the thin refresh bar, reload. On failure: revert to the
  previous filters and show a snackbar.
- **Active filter chips** sit under the app bar: `Only mine ×`, `#acme ×`,
  `Archived included ×`, `Search: foo ×`, plus `Clear all` at the end. Each `×`
  applies the change immediately (and reloads for server filters).
- Filter options: assignee options come from `/api/kanban/assignees`, fetched
  when the sheet opens for the fresh names (the bridge returns no per-assignee
  counts); tenant options come from the current board payload plus the active
  tenant. The active selection is always included,
  so a filter cannot strand the user with a value they cannot see or clear. When
  the board is loaded without an assignee/tenant filter, the cached option lists
  are refreshed from that full result.
- There is no "unassigned" filter: the bridge cannot filter on it. Instead,
  unassigned ready tasks are called out on their cards and in the Ready header
  when relevant.

### 4.8 FAB and creation entry

- A single `ExtendedFloatingActionButton` ("New task", `+`). It is the only
  primary action on the board.
- On scroll down it shrinks to a standard FAB; on scroll up or at the top it
  expands. The label never animates mid-tap.
- It opens Create with the currently selected status as the initial status,
  except when that status is Running/Blocked/Done/Archived: then the default is
  Triage (you cannot create directly into those).
- After creation: pop back to the board, select the **status the server actually
  created** (not necessarily the one requested — unfinished parents land a
  "Ready" task in Todo), scroll the new card into view, highlight it, and show
  "Task created · View" in a snackbar. If the server gated the task, say so:
  "Created in Todo — waiting on its parents." If the active filters hide the new
  task, the snackbar says "Created — hidden by the current filters" and offers
  **Clear filters**.

### 4.9 Refresh and live updates

- Load sequence: `/api/kanban/boards` (resolve the current board) →
  `/api/kanban/config?board={slug}` (best-effort, but **before** the board so its
  defaults are applied to the first load) → initial filters from config
  (`tenant = default_tenant` when set, `include_archived =
  include_archived_by_default`) → `/api/kanban/board?board={slug}&…` →
  `/api/kanban/assignees?board={slug}` only when the filter sheet opens. Do
  **not** call `/stats`: the rail counts derived from the board are filter-aware,
  while `/stats` is not.
- The board payload's `latest_event_id` is the SSE cursor. Then open
  `/api/kanban/events/stream?board={slug}&since={cursor}`.
- **Compatibility contract:** `/boards`, `/board`, and the task/mutation
  endpoints are required. `/config`, `/assignees`, and the SSE/polling endpoints
  are optional:
  - config/assignees failure → defaults (no archived, no default tenant, filter
    options from the board payload); show nothing.
  - SSE 404 → try `/events` polling; if that also 404s, fall back to a 30 s
    full-board refresh and label the state "Live updates unavailable" — the board
    itself stays usable.
  - `/boards` or `/board` 404 → the setup state (§9.3), because the app cannot
    resolve a board without them.
- On an event: update the cursor and schedule a board reload, debounced 300 ms.
  Coalesce bursts. Keep the current board visible; show the 2 dp progress bar.
  If a reload returns `{changed:false}` (no `columns`), treat it as "nothing
  changed" and keep the current data.
- While a refresh is in flight, do not clear the board.
- Pull-to-refresh calls the same reload path but also refreshes `/boards` counts.
- Lifecycle: collect the stream only between `ON_START` and `ON_STOP`
  (`repeatOnLifecycle(STARTED)`). On return, reload if the last load is older
  than 30 s, then restart the stream from the last cursor.
- SSE failure policy:
  - 1–2 consecutive failures: "Reconnecting" in the subtitle; retry with 2 s,
    then 5 s.
  - 3rd failure: switch to **polling** `/api/kanban/events?since={cursor}` every
    30 s, then reload the board after each poll. Show "Polling — live updates
    paused" in the subtitle and a slim banner with **Retry**. A manual Retry
    restarts SSE.
  - 404 on both SSE and polling: 30 s full-board refresh, "Live updates
    unavailable" (not an error state).
  - `401`: stop, show session-expired handling (§9.3).
  - A 404 on the **board** endpoint: show the stale-client/setup state, not an
    endless reconnect.
- **Approved v1 simplification (2026-10-21):** Task 11 currently goes directly
  from SSE `Ended`/`Failed` to the 30 s polling fallback; the 2 s/5 s
  `Reconnecting` retries and manual **Retry** banner above are not in v1. The
  board remains usable and foreground-only. Treat the richer policy as the next
  improvement, not as shipped behavior.
- No notification or badge while backgrounded. On resume, the board may be stale
  for a moment; the subtitle says "Reconnecting" until the first load completes.

### 4.10 Board state (one immutable UI state)

```kotlin
data class KanbanBoardUiState(
    val boards: List<KanbanBoardSummary> = emptyList(),
    val board: KanbanBoardSummary? = null,
    val columns: List<KanbanColumn> = emptyList(),      // server order, authoritative
    val selectedStatus: String = "ready",
    val filters: KanbanFilters = KanbanFilters(),
    val filterOptions: KanbanFilterOptions = KanbanFilterOptions(),
    val query: String = "",
    val lastEventId: Long = 0,
    val load: LoadPhase = LoadPhase.Loading,             // Loading | Content | Empty | Error
    val refreshing: Boolean = false,
    val live: LivePhase = LivePhase.Connecting,          // Connecting | Live | Reconnecting | Polling | Offline
    val error: KanbanError? = null,
    val pendingTaskIds: Set<String> = emptySet(),
    val readOnly: Boolean = false,
)
```

- `columns` is rendered from the payload, not from a hard-coded enum. A newer
  server adding a column (e.g. `review`) renders it with neutral styling; actions
  for unknown statuses are hidden with a one-line note.
- Derived: all tasks, task lookup by id, filtered counts, effective selected
  column (if the selected column disappears, fall back to the default rule).
- The ViewModel owns the SSE job and the debounce; the screen owns pager/scroll
  state, which is remembered in the composable (`rememberSaveable` where cheap)
  and keyed by status.

---

## 5. Task detail

### 5.1 Structure

A full-screen destination. Top app bar: back, title "Task", subtitle the task id
(monospace), actions **Edit** (pencil) and overflow (Refresh, Copy ID, Archive,
How this works).

The content is one `LazyColumn`:

1. **Hero** (scrolls away)
   - Status `AssistChip` with a trailing chevron: "Ready". Tapping opens Move to….
     It is the primary status affordance, with content description
     "Status Ready. Change status."
   - Priority badge and tenant pill.
   - Title (`headlineSmall`, up to 4 lines).
   - Task id + copy icon, age, staleness pill.
2. **Action row** (`FlowRow`, wraps at large font sizes)
   - Status-specific primary + secondary actions (§5.4). At most two filled/tonal
     buttons plus one text action; everything else is in the overflow.
3. **Tabs** (`PrimaryTabRow`, sticky header in the LazyColumn)
   - Overview · Activity `{n}` · Runs `{n}`.
4. **Tab content** (see below).

The tabs are a `stickyHeader` so they remain reachable after the hero scrolls away.
The Activity composer is a `Scaffold` bottom bar only while Activity is selected.

### 5.2 Overview tab

- **Description**: unboxed Markdown, full width, under a "Description" section
  label. Empty: "No description" + an "Add description" text button that opens
  Edit. Long descriptions scroll; no card, no inner scrollbar.
- **Result** (done/blocked with a result): a tonal card with the completion
  result/summary in Markdown, completed time, and the closing run id when known.
- **Blocked callout** (status `blocked`): an `errorContainer` tonal card above
  the description with the block kind and the reason, plus an inline **Unblock**
  action. The reason is the newest `blocked` event's payload `reason`, falling
  back to the task's `last_failure_error`; when neither exists: "No reason was
  recorded." This is the "read the reason" the card promises — the board card
  never expands the reason itself.
- **Details** card: label/value rows with icons.
  - Always: Assignee, Priority, Tenant, Created.
  - "Show all" (`AnimatedVisibility`, `HermesMotion.contentSize`): Workspace
    (kind + path), Branch (when set), Skills (or "Profile defaults"), Max runtime
    (formatted from seconds), Started, Completed, Runs count, Parent/child counts.
  - Values are text; editing happens in Edit (one editor, one mental model).
- **Dependencies** card:
  - "Depends on" and "Blocks" sections. Each item is a chip with the linked
    task's id, title (if known from the board cache, else just the id), and a
    status dot. Tapping opens that task (push another detail).
  - Add: "Add dependency" → a searchable task picker sheet (current board tasks,
    excluding self and existing links). Selecting adds `parent_id = selected,
    child_id = current`; helper text spells this out: "This task will wait for
    the selected task."
  - Remove: an X on the chip; `POST /api/kanban/links/delete`; success snackbar
    with Undo (re-adds). No confirmation dialog.
  - Empty: "No dependencies. This task can run on its own."
- **Archive** at the bottom (outlined, error colour): "Archive task"; for a
  running task the copy is "Archive and stop worker". Confirmation sheet for
  running tasks only. For done/archived, the bottom shows Reopen/Restore instead.
- If the task's status is unknown to this app: the status chip is read-only, the
  action rows are hidden, and a note says "This server uses a status this app does
  not recognize. You can still edit the task."

### 5.2.1 Shared task picker (dependencies and parents)

The bridge has no task-list endpoint, and the board payload is filter-scoped, so
the pickers cannot safely reuse the visible board. Both the dependency picker and
the Create parent picker:

- fetch `GET /api/kanban/board?board={slug}` **without** filters when opened
  (a dedicated `taskIndex` repository call; no `include_archived`, since archived
  tasks should not become new parents),
- show a search field (client-side over id/title/assignee), status dot, title, and
  id; multi-select for Create parents, single-select for "add this dependency",
- exclude the current task and already-linked tasks,
- show loading/error/empty states; on error, keep the picker open with Retry,
- are fetched fresh on each open (the payload is heavy but the action is
  occasional; a stale cached index would be worse than a short spinner).

If a dependency chip's task is not in the board cache, the chip shows its id
only; tapping it still opens the detail with the pinned board slug.

### 5.3 Activity tab

- Filter chips: All · Comments `{n}` · Events `{n}` (single choice).
- A single chronological timeline (oldest first) merging comments and events.
  - **Comment**: avatar circle with the author's initial, author (or "webui"),
    relative time, Markdown body. No edit/delete/reactions.
  - **Event**: a quiet row: icon by kind, a human summary, relative time. Tap
    "Details" to expand the raw payload as formatted monospace JSON. Unknown
    kinds show the raw kind. Summaries are derived for the common kinds
    (created, status, assigned, comment, link, run started/ended, claim,
    completed, blocked, unblocked, archived, scheduled, updated) and fall back to
    the kind name.
- Empty: an illustration-free state: "Nothing here yet" + "Comments and worker
  events will appear as this task moves."
- Composer (fixed bottom bar while Activity is selected):
  - `OutlinedTextField`, 1–5 lines, placeholder "Add a comment…".
  - Send icon; disabled when blank or sending. IME action Send.
  - Author: the active Hermes profile name when known, otherwise `android`.
    A helper line says "as @profile".
  - On send: append optimistically with a subtle "Sending…" label, scroll to the
    bottom, then replace with the server's comment after a detail refresh. On
    failure: mark the bubble "Not sent" with Retry; keep the text recoverable.
  - Keyboard insets via `imePadding`; the timeline scrolls above it.

### 5.4 Runs tab

- **Worker log** card at the top:
  - "Worker log" + existence/size ("48 KB, truncated") or "No log yet".
  - **Open log** button → the log screen.
  - The log is task-level (the bridge exposes one log per task), so it is not
    nested under a run.
- **Runs** list, newest first. Each run:
  - Outcome/status chip (completed, blocked, crashed, timed_out, spawn_failed,
    gave_up, reclaimed, review_requested, changes_requested, scheduled, running).
  - `started → ended` relative times + duration.
  - Summary and error (error in `errorContainer`).
  - Profile name and run id in `labelSmall`.
  - Raw metadata in an expandable "Details".
- Empty: "No runs yet. Each dispatcher attempt will be recorded here."
- The tab has its own refresh action in the overflow; a live SSE event for this
  task refreshes the detail (debounced).

### 5.5 Worker log screen

- Top bar: back, "Worker log", actions Copy, Refresh, tail selector
  (64 KB / 256 KB / 1 MB, default 256 KB) in the overflow.
- Fetch on open with `GET /tasks/{id}/log?tail=…`; show a centered progress state,
  then:
  - `truncated`: a tonal banner "Showing the last {size}. Older output is not
    loaded." with a "Load more" action that raises the tail one step.
  - `exists == false`: "No worker log for this task yet."
  - Error: "Couldn't load the log" + Retry.
- Content: monospace `bodySmall`, selectable, vertically and horizontally
  scrollable (no wrapping — log lines are wide). ANSI escapes are shown raw, as
  in the WebUI. Bottom padding for the navigation bar.
- Copy copies the loaded text and shows a snackbar. No share (logs may contain
  secrets).

### 5.6 Action matrix (mobile)

Only actions the backend actually supports are shown. In particular, `Done` is
**not** offered for triage/todo/scheduled (the backend refuses those), and it is
**not** offered for running while a claim is live (the backend refuses without an
ownership proof the bridge does not send).

| Status | Primary | Secondary | Overflow |
|---|---|---|---|
| Triage | Move to… | — | Archive, Copy ID |
| Todo | Move to… (Ready / Scheduled) | — | Archive, Copy ID |
| Scheduled | Resume (re-gated) | Move to… | Archive, Copy ID |
| Ready | Complete… | Move to… | Block…, Archive, Copy ID |
| Running | Block… | — | Archive and stop worker, Copy ID |
| Blocked | Unblock | Complete… | Move to…, Archive, Copy ID |
| Done | Reopen… | — | Archive, Copy ID |
| Archived | Restore… | — | Copy ID |

Move-to targets (known statuses):

| From | Targets |
|---|---|
| Triage | Todo, Ready |
| Todo | Triage, Scheduled, Ready |
| Scheduled | Triage, Todo, Ready (via unblock) |
| Ready | Triage, Todo, Scheduled, Blocked (via Block sheet), Done (via Complete sheet) |
| Running | — (use Block or Archive) |
| Blocked | Triage, Todo, Scheduled, Ready (via unblock), Done (via Complete sheet) |
| Done | Reopen sheet: Triage, Todo, Ready |
| Archived | Restore sheet: Triage, Todo, Ready |

Every target row carries one line of consequence:

- Ready: "The dispatcher can claim it as soon as it is assigned."
- Scheduled: "Paused until you resume it. There is no date or time."
- Todo: "Planned, but not runnable until dependencies finish or you move it to Ready."
- Triage: "Back to the inbox; nothing will run."
- Done: "Records the result and closes the task."
- Todo from blocked/scheduled: "Resuming with unfinished parents lands in Todo."

**Dependency-aware Ready:** when the board cache knows that this task has a
parent that is not done/archived, the Ready target is disabled with "Waiting on
{n} unfinished parent task(s)." The bridge's direct status write to `ready` does
not re-check parents; the app must not silently bypass the gate. If the parent is
not in the loaded board (it was filtered out or lives on another board), the
target is allowed — the app cannot prove the gate, and the server remains the
authority. Create is stricter because its parent picker always has a fresh,
unfiltered task list (§6.1).

### 5.7 Complete sheet

- Title "Complete task".
- A multiline **Result / evidence** field, prefilled with the task's existing
  `result` when present. Helper: "Required unless this task already has a result.
  Describe what was done or where to find it."
- A warning when any parent is not done/archived: "This task has unfinished
  parent tasks and may be refused."
- Primary "Complete"; cancel. On success: close the sheet, refresh the detail and
  board, snackbar "Task completed" with **Undo** (→ Ready). On refusal: keep the
  sheet, show the server's message inline, and a "Refresh task" link.
- Not available for running tasks (backend live-claim fence). Running offers
  Block or Archive-and-stop instead, with a sentence explaining why.

### 5.8 Block / unblock

- **Block sheet**: title "Block task"; a reason field, prefilled "Blocked from
  Hermes Android" (editable, multiline); primary "Block". **If the task is
  running**, the sheet warns: "This marks the task blocked but does not stop the
  worker process. To stop it, use Archive and stop worker." On success: refresh;
  snackbar "Task blocked" with **Undo** (calls Unblock, not a raw status restore —
  the server decides whether it resumes to Ready or Todo).
- **Unblock**: immediate, no dialog. On success, use the returned task's status:
  - Ready → "Unblocked — ready to run."
  - Todo → "Unblocked — waiting on dependencies."
  - Anything else (e.g. review) → "Unblocked — now {status}."
  If the task disappears from the board (e.g. resumed to `review`), pop to the
  board and refresh; the board's empty state explains nothing was lost.

### 5.9 Archive, restore, reopen

- **Archive**:
  - Ready/triage/todo/etc.: confirmation sheet only when the task is running;
    otherwise archive immediately with an **Undo** snackbar.
  - Running: confirmation copy "This archives the task and stops the active
    worker." Primary "Archive and stop". This is the app's stop control; it is
    deliberately explicit.
  - Undo returns to the previous status if it was triage/todo/ready; otherwise
    Ready. The server may re-gate to Todo.
  - Archiving the open detail pops back to the board.
- **Restore** (archived): a sheet with Triage/Todo/Ready; same semantics as
  Reopen. On success, the task leaves the Archived column.
- **Reopen** (done): a sheet with Triage/Todo/Ready. Note: "Reopening keeps the
  existing result as history." (The backend leaves the result/completed_at; do
  not promise a reset.)

### 5.10 Edit

- `TopAppBar`: close (X), "Edit task", no top-right save.
- Fields:
  - Title (required, ≤500, autofocus off unless opened from an empty title).
  - Description (multiline, markdown; no preview toggle in v1).
  - Priority: a stepper from −100 to 100 with − / + buttons and a numeric field;
    helper shows "P0 · normal", "Higher runs first".
  - Assignee: row → assignee picker sheet (profiles with "on disk" state, plus
    "Unassigned (won't auto-run)").
  - Tenant: text field with existing tenant chips.
- Status is deliberately **not** editable here: status is workflow, edited via
  the detail's actions. This avoids the WebUI's "actual status" mapping.
- Read-only rows (from the API's patch limitations): workspace kind/path, branch
  (shown only when the task already has one — the bridge does not forward a
  branch on create), skills, max runtime, parent dependencies. Each has a quiet
  "Set at creation" caption; branch says "Set by the server or worker."
  Dependencies are edited from Overview; the rest require a new task.
- Sticky bottom bar: "Save changes" (filled), enabled only when dirty and valid;
  progress in the button. Server errors appear in an inline error card at the top;
  field errors are local.
- Back or X with unsaved changes → "Discard changes?" dialog (Discard / Keep
  editing).
- On success: pop to detail; the detail refetches and shows the updated task; the
  board cache is updated in place.

---

## 6. Create task

A full-screen destination, same visual language as Edit. Quick capture is the
common path; advanced fields are collapsed.

### 6.1 Default path (visible)

1. **Title** (autofocus, required, counter at 450+, max 500).
2. **Description** (multiline, optional, 3-line min height).
3. **Status** segmented buttons: Triage · Todo · Ready. Default **Ready**.
   Helper text changes with the selection:
   - Triage: "Captured. Nothing will run."
   - Todo: "Planned. The dispatcher won't claim it yet."
   - Ready: "The dispatcher can claim it as soon as it is assigned. If it has
     unfinished parents, it will wait in Todo."
   - With parents selected, append: "The server decides when it becomes Ready."
4. **Assignee** row: default = the active profile if it is a known assignee, else
   the first profile, else Unassigned. Opens the assignee picker. If Ready +
   Unassigned, a warning row: "Unassigned tasks never run." with an **Assign**
   action. Assignee options merge the full profile list (`/api/profiles`, with
   on-disk state) with historical board assignees (`/api/kanban/assignees`),
   deduped by name; `default` sorts first.
5. **Priority** stepper (default 0).
6. **Tenant** (optional; existing tenants as chips). Prefilled with
   `/api/kanban/config`'s `default_tenant` when set; the user can clear it.
7. **Advanced** disclosure ("More options"):
   - **Workspace**: `SingleChoiceSegmentedButtonRow` Scratch · Worktree ·
     Directory.
     - Scratch: no path.
     - Worktree / Directory: path row opens the existing workspace browser;
       required. (No branch field: the WebUI bridge does not forward
       `branch_name` on create, so offering one would silently do nothing.)
   - **Skills**: a chip input. Empty means "use the profile's default skills" and
     the field is omitted. The bridge collapses `[]` to `None`, so the UI does
     not pretend to offer a "no skills" state; helper: "Leave empty to use the
     profile's defaults."
   - **Max runtime**: minutes field with quick chips No limit / 5 / 15 / 30 / 60;
     converted to seconds for the API. No limit omits the field.
   - **Parent tasks**: "Choose" opens a multi-select task picker sheet; selected
     parents are chips. The picker fetches an **unfiltered** board (§5.2.1), so a
     task hidden by the board's filters can still be chosen. Helper: "This task
     will wait for them."
8. Sticky bottom bar: **Create task** (filled), disabled until the title is
   non-blank. If Ready + Unassigned, a confirmation dialog on first submit
   ("Create anyway?" / "Assign first").
9. On success: pop to the board; select the **actual created status** (the server
   may have placed a "Ready" task in Todo because of parents); scroll to and
   highlight the card; snackbar "Task created · View", or "Created in Todo —
   waiting on its parents."

### 6.1.1 Create semantics (no ready-window race)

The bridge's create endpoint does not accept an initial status; it creates the
task and then applies a `status` patch. That default is `ready` when parents are
satisfied (or absent) and `todo` when a parent is unfinished; `triage: true`
forces triage. A naive "create Ready, then patch" is unsafe: a dispatcher tick
can claim the task between the two writes, and patching Ready would bypass the
parent gate.

The client therefore uses this exact recipe:

| Selected | Request | Follow-up |
|---|---|---|
| Triage | `triage: true`, no `status` | none |
| Todo | `triage: true`, no `status` | `PATCH status=todo` (triage → todo; neither is claimable) |
| Ready | no `triage`, no `status` | **never** patches Ready. The server lands it in Ready only if every parent is done; otherwise Todo. |

Consequences:

- A task is never briefly claimable while the form is still finishing.
- A task with unfinished parents cannot be forced into Ready by Create; the
  server's gate wins. The form says so before submission.
- The board selects the response task's `status`, not the segmented selection.
- If the follow-up Todo patch fails, the task remains safely in Triage; show an
  error and offer "Open task" rather than silently leaving it.
- If the create succeeds but the response is missing a task (older server), reload
  the board and show "Task created — refreshing the board."

This is verified by a serialization test that asserts the create body for each
selection, and a repository test that the Ready path sends no `status` field.

### 6.2 Validation

- Title: trimmed, non-blank, ≤500.
- Priority: integer −100..100.
- Workspace: path required for worktree/dir; no path validation beyond non-blank
  (the server is authoritative).
- Max runtime: integer ≥1 minute; ≤10080.
- Tenant: ≤64; slug/board fields ≤ their server limits.
- Server errors: one inline error card at the top (with the server's wording),
  plus field-level messages for local validation. Never lose input on failure.
- Discard guard for any non-empty form.

### 6.3 Create-board and board settings

**New board** (`kanban/board/new`): name (≤64), slug (auto-suggested from the
name, editable, `[a-z0-9-]`, ≤48), description (≤200), icon (emoji/short text,
≤4, with suggestions), colour (8 accessible swatches from `ui/theme/Color.kt`),
default workdir (optional, workspace picker; helper "Used by project dashboards,
not as the task default"). Primary "Create board". Creation switches to the new
board.

**Board settings** (`kanban/board/{slug}/settings`): the same fields, slug read-only.
Archive action at the bottom:
- Disabled for the default board with "The default board can't be archived."
- Otherwise a confirmation sheet; copy "This removes the board from the switcher.
  There is no restore action in the app." (The server keeps the files.)
- If the board has `running` tasks (from its `/boards` counts or a fresh board
  load), the sheet adds a warning: "This board has {n} running worker(s).
  Archiving the board does **not** stop them; they will keep working against an
  archived board." A second confirmation ("Archive anyway") is required. This is
  the server's behavior — board archive is metadata-only — and hiding it would
  leave workers running invisibly.
- On success: return to the board; reload `/boards`; if the archived board was
  current, switch to the server's new current board.

**Board switcher sheet**:
- Header "Boards".
- Rows: icon circle, name, per-status summary ("3 ready · 2 running"), current
  check. Archived boards are hidden until **Show archived** is enabled.
- Footer: "New board" (tonal) and "Board settings" (text).
- Fetch `/api/kanban/boards` when opened; show a compact loading state; on error,
  keep the last known list with a retry row.
- Switching calls `/boards/{slug}/switch`, then reloads the board, clears
  search and the assignee/only-mine selection, and re-applies the config
  defaults for tenant and archived (the default tenant stays visible as a chip);
  the pager returns to the default column for the new board.
- Switch failure handling:
  - **Switch call fails:** keep the old board, leave the sheet open, show the
    server's message inline, and re-fetch `/boards` to reconcile `current`. Do not
    reset filters or change the visible board.
  - **Switch succeeds, board load fails:** adopt the slug returned by the switch
    (the server is authoritative), close the sheet, reset to config defaults, and
    show the board screen's error state with Retry. Do not fall back to the old
    board.
  - **Switch returns a different `current`** (the requested board vanished): use
    the returned slug, reload `/boards`, and if it is also gone, show the setup
    state.
  - On app restart/process death, `/boards` is loaded before any board-scoped
    request, so the app never pins a stale slug.

---

## 7. Dispatcher

A bottom sheet titled "Run dispatcher".

- Opens and immediately runs a **dry run** (`dry_run=1&max=8`) so the user sees
  what would happen before committing.
- Preview card: outcome rows with icons and counts, **only nonzero rows**, rendered
  from whatever the response contains rather than a fixed eight-field list. Known
  fields get human labels; unknown list/number fields are humanized
  (`snake_case` → "Snake case") so a newer dispatcher's bucket is not dropped.
  Known rows include Spawned, Promoted, Reclaimed, Skipped (unassigned, unknown
  profile, non-spawnable, profile capped), Auto-assigned, Auto-blocked, Timed
  out, Crashed, Stale, Respawn-guarded, Rate-limited, Reconciled orphans, and
  Reaped terminal workers.
- Special states take precedence over "nothing to start":
  - `skipped_locked: true` → "Another dispatcher is already running on this
    board. This tick made no changes." (Do not say "nothing to do".)
  - `memory_pressure: critical|elevated` → a warning row "Memory pressure
    ({level}) limited new workers."
  - `rate_limited` non-empty → "N task(s) returned to Ready by a provider
    rate limit."
  - Only when every counter is zero and no special state applies: "Nothing to
    start right now." The Run button becomes "Run anyway" with a caption.
- A "Details" disclosure lists the task ids behind Spawned / Skipped / Blocked
  groups (read-only, monospace), so "why didn't my task run?" is answerable
  without opening the WebUI.
- A one-line explainer: "Claims ready tasks, starts workers, promotes unblocked
  work, and reclaims stale runs. Limit 8 per run."
- Primary **Run dispatcher** in the preview and **Run again** in the result;
  a preview failure shows an inline error and a **Retry** action.
- While running: a progress row and "This can take a minute"; the sheet stays
  open and dismissible. The call runs in the board ViewModel, not the sheet, so
  dismissing does not cancel it. The client uses a 3-minute call timeout for
  dispatch.
- On success: the sheet switches to a result state ("Dispatcher result") with
  the same counts, plus **Run again**, **Refresh preview** and **Close**; the
  board refreshes and the SSE cursor is kept. If the result includes spawned
  workers, the Running column count updates live.
- On failure: error card + Retry; the board is not reloaded (or is refreshed on
  close to reconcile).
- Available from the top-bar overflow and from the Ready column's **Run** text
  button. Not available when `read_only`.

### 7.1 How this board works (help sheet)

A bottom sheet, reachable from the overflow and from empty states. It is the one
place that explains the model in full; the column headers carry the short version.

- A vertical status list with the same icons and descriptions as §4.4, plus arrows
  showing the normal flow `triage → todo → scheduled → ready → running → blocked
  → done`, with `archived` apart.
- Rules, one line each:
  - "Only the dispatcher starts tasks. You can't set Running."
  - "Unassigned tasks never run."
  - "Scheduled is a pause, not a date. Resume it when you're ready."
  - "Parent tasks gate Ready. Unfinished parents keep a task in Todo."
  - "Done needs a result. The Complete sheet asks for one."
  - "Archiving a running task stops its worker. Blocking does not."
  - "There is no delete. Archive is the reversible way to remove work from the
    board."
  - "Drag-and-drop isn't available on the phone; use Move to… on a task."
- No external link in v1; the copy is self-contained and matches this document.

---

## 8. Visual system — Material 3 Expressive

Material 3 1.4.0 is the first stable Expressive release, but
`MaterialExpressiveTheme` and `MotionScheme` remain `internal`. The design therefore
uses the **stable** expressive surface: the shape scale, tonal colour roles,
typography, and the project's `HermesMotion` springs. No API that requires 1.5.

### 8.1 Typography

| Element | Style |
|---|---|
| Board name | `titleLarge` |
| Board subtitle | `labelMedium` |
| Status chip | `labelLarge` |
| Column title | `headlineSmall` |
| Column count | `titleSmall` |
| Column description | `bodySmall` |
| Task title | `titleMedium` |
| Task body preview | `bodySmall` |
| Task meta | `labelMedium` / `labelSmall` (id monospace) |
| Detail title | `headlineSmall` |
| Section label | `titleSmall` |
| Timeline body | `bodyMedium` |
| Tab label | `titleSmall` |

No custom fonts. The existing typography is tightened for chat; the board uses it
as-is.

### 8.2 Shape and surface

- Cards: `MaterialTheme.shapes.large` (22 dp), tonal `surfaceContainerLow`, 0 dp
  elevation.
- Sheets: M3 default (extra-large top corners), `surfaceContainerLow`.
- Chips: pill; buttons: default M3; FAB: large shape.
- Nested surfaces use one tonal step (`surfaceContainerLow` on `surface`,
  `surfaceContainerHigh` inside a card only for inset elements).
- No gradients, no glass, no custom illustrations. Expressive comes from scale,
  spacing, shape, and motion.

### 8.3 Colour roles

Status is never conveyed by colour alone: every status has a name and an icon/dot.
Roles:

| Status | Dot / chip role |
|---|---|
| Triage | `outline` / `surfaceContainerHighest` |
| Todo | `secondary` / `secondaryContainer` |
| Scheduled | `tertiary` / `tertiaryContainer` |
| Ready | `primary` / `primaryContainer` |
| Running | `primary` / `primaryContainer` + static play glyph |
| Blocked | `error` / `errorContainer` |
| Done | `outline` / `surfaceContainerLow`, muted text |
| Archived | `outlineVariant` / `surfaceContainerLowest`, muted text |

Staleness: `tertiary` for warning, `error` for critical. All other colours come
from `MaterialTheme.colorScheme`; the only literal colours are the board colour
swatches in `ui/theme/Color.kt` (data accents, never text or status).

### 8.4 Motion

Use `HermesMotion` only (no raw `tween`):

- Board ↔ detail: shared-axis Z — incoming content fades in with a 0.98→1 scale
  and 8 dp rise; outgoing fades. `HermesMotion.spatial()`.
- Detail ↔ edit / log: slide up / slide from the end, `spatial()`.
- Column pager: default fling; chip-driven changes use `animateScrollToPage` with
  `spatial()`.
- Rail selection: colour and elevation via `effects()`; the indicator moves with
  the pager.
- Expand/collapse (Show all, Advanced): `contentSize`.
- FAB shrink/expand: `spatialFast()` via `animateContentSize`.
- Card highlight after create: container colour animated once with `effects()`.
- Live dot: static by default. Reconnecting may pulse once per retry; no infinite
  animation (respects reduced motion and battery).
- Navigation transitions are declared per destination in the NavHost; the system
  animator duration scale is respected automatically.
- Haptics: `TextHandleMove` on chip/rail selection and on a successful status
  move; `LongPress` only when multi-select arrives. No haptics on scroll.

### 8.5 Iconography

Rounded Material icons, 20–24 dp:
Triage `Inbox`, Todo `Checklist`, Scheduled `Schedule`, Ready `PlayArrow`,
Running `Bolt`, Blocked `Block`, Done `CheckCircle`, Archived `Archive`.
Actions: Add, Search, FilterList, MoreVert, Edit, ContentCopy, Refresh, Send,
AccountTree, ChatBubbleOutline, OpenInNew, ArrowDropDown.

---

## 9. States, feedback, and edge cases

### 9.1 Loading

- First load: a structural skeleton (rail pills, one column header, three card
  placeholders) on `surface` with `surfaceContainerLow` blocks; fade to content
  over 200 ms. No full-screen spinner once the app bar is drawn.
- Board switch / filter apply: keep the old board; 2 dp linear progress under the
  app bar.
- Detail: hero skeleton + tab skeleton. Log: centered progress.

### 9.2 Empty

| Situation | Copy | Action |
|---|---|---|
| Empty board | "Your board is empty." / "Capture the first piece of work, then let the dispatcher take it." | Create task, How it works |
| Empty column | "Nothing in {status}." + description | Add task (triage/todo/ready) |
| Search/filters hide all | "No tasks match." / "Try a different search or clear the filters." | Clear filters |
| No comments | "Nothing here yet." | — |
| No runs | "No runs yet." | — |
| No log | "No worker log yet." | Refresh |
| No dependencies | "No dependencies." | Add dependency |

### 9.3 Errors

| Error | Surface | Recovery |
|---|---|---|
| No network | Slim error banner + keep the in-memory board | Retry; mutations attempt and fail with "You're offline. Changes aren't saved." (no queue, no connectivity observer) |
| 401 | Full-screen "Session expired" | Sign in (Connect, clear back stack) |
| 403/404 with a JSON message | Inline/dialog with the server's wording | Refresh, back |
| 404 on a transition | Snackbar "This task may have moved. The board was refreshed." + refresh | automatic |
| 409 | Dialog with the server's message + Refresh | — |
| 503 / "Kanban unavailable" | Full-screen setup state, not an empty board | Retry |
| 404 on `/boards` or `/board` (unknown endpoint) | "This server's Kanban needs a newer Hermes WebUI." | Retry |
| SSE and `/events` 404 | Board stays usable; "Live updates unavailable" + 30 s refresh | Retry |
| Unknown status column | Rendered neutrally; actions hidden | — |
| `read_only` | Banner + write actions disabled | — |
| Task deleted/not found | Full-screen "This task is no longer on the board." | Back + refresh |

- Server messages are shown verbatim after a short prefix ("The server refused:"),
  because the bridge's refusals are specific and human-written.
- Toasts are not used for errors; snackbars (transient) or inline cards
  (persistent).
- Successful quick actions (complete, archive, block, unblock, dependency remove)
  use a snackbar with **Undo** where the backend allows reversing it. No undo for
  edits or comments.

### 9.4 Concurrency and optimism

- One in-flight mutation per task; disable its actions and show an inline spinner.
- Optimistic: status moves that are direct writes (triage/todo/ready), archive
  (with undo), comment append (pending style). Wait for the server for complete,
  block, unblock, edit, create, dependency changes.
- On success, replace local data with the returned task; on failure, revert and
  refresh the board/detail. Reconciliation from SSE is silent; the server wins.
- No offline queue. Mutating offline fails immediately with "You're offline.
  Changes aren't saved."
- Double-taps are prevented by disabling the action, not by debouncing.

### 9.5 Live reconciliation

- SSE events only carry ids and kinds, not task bodies. The client reloads the
  board (full payload) on a debounce. A `changed:false` response is not an empty
  board.
- The detail refetches the whole task payload on a matching event; it seeds its
  stream with the board's cursor passed in the route. If no cursor is available,
  it polls every 20 s while open (and after actions).
- Board reloads and optimistic mutations can race; the last server response wins.
  If a task's status changes under an open sheet, the sheet's action may be
  refused — the error path refreshes and explains.

---

## 10. Responsive and accessible behaviour

### 10.1 Size classes

- **Compact (< 600 dp)**: one status column per pager page; full-screen detail.
  This is the primary target.
- **Medium/expanded (≥ 600 dp)**: same rail and cards, but the board becomes a
  horizontally scrolling row of fixed-width (360 dp) columns, so more than one is
  visible. The rail scrolls the row to the selected status. Detail stays
  full-screen (no list-detail pane in v1).
- **Landscape phone**: one column, wider cards; no separate layout.
- The FAB stays bottom-end; on expanded widths it is constrained to the content
  width, not flung to the far edge of a tablet.

### 10.2 Accessibility

- Every touch target ≥ 48 dp (M3 `minimumInteractiveComponentSize` handles chips;
  the card overflow is explicitly 48).
- Status is name + dot/icon + text; never colour alone. Staleness is a worded pill.
- The card is one click target; the overflow is a separate labelled target, not a
  nested button inside a button.
- Rail: `selectableGroup` semantics; each chip has selected state and count in its
  content description.
- Pager pages announce the status heading; card order is list order.
- The live indicator has a text label, not just a coloured dot.
- Font scale 200%: no fixed-height cards or headers; the rail scrolls; the action
  row wraps (`FlowRow`); type truncates only titles/body previews, never statuses
  or action labels.
- TalkBack: after a status change, the snackbar announces it; focus returns to the
  status chip. Sheets trap focus and restore it on dismiss.
- Reduced motion: infinite animations are avoided; navigation/scale animations
  follow the system scale. Content is readable without motion.
- Contrast: theme roles only; `ColorContrastTest` remains the guard. Board accent
  colours are never used for text.

---

## 11. Architecture and data layer

### 11.1 Packages

```
ui/kanban/
  KanbanBoardScreen.kt        board + rail + pager + cards
  KanbanBoardViewModel.kt
  KanbanDetailScreen.kt
  KanbanDetailViewModel.kt
  KanbanEditorScreen.kt       create/edit (shared form)
  KanbanEditorViewModel.kt
  KanbanBoardsScreen.kt       new board / settings
  KanbanBoardsViewModel.kt
  KanbanDispatcherSheet.kt
  KanbanFilterSheet.kt
  KanbanMoveSheet.kt
  KanbanCompleteSheet.kt
  KanbanBlockSheet.kt
  KanbanBoardSwitcherSheet.kt
  KanbanHelpSheet.kt
  components/                 TaskCard, StatusRail, ColumnHeader, LiveBadge,
                              MetaRow, EmptyState, etc.
data/kanban/
  KanbanRepository.kt
core/model/Kanban.kt          all wire DTOs
domain/kanban/                pure domain: transitions, staleness, filters, format
core/net/RawSseClient.kt      generic SSE frames (chat SseClient untouched)
```

### 11.2 DTO rules

- Every wire field optional with a default; `ignoreUnknownKeys` is already set.
- Timestamps are `Double?` epoch seconds (the app's convention), converted at the
  edge. IDs: task strings; run/comment/event ids `Long?`.
- `age` / `age_seconds` is an **object**
  (`created_age_seconds`, `started_age_seconds`, `time_to_complete_seconds`), not a
  number.
- `link_counts` is `{parents, children}`; `comment_count` is an int.
- `columns` is a list of `{name, tasks}`; render the payload order. Do not
  reconstruct columns from an enum.
- `GET /board` with `since >= latest` returns `{changed:false, latest_event_id,
  read_only}` and **no columns**. The repository returns a sealed
  `BoardResult.Changed` / `BoardResult.Unchanged`.
- `read_only` is honored everywhere (hide/disable writes, show a banner).
- Unknown status strings parse to a generic status value with `known = false`;
  never throw.

### 11.3 API surface used

All on the WebUI origin, existing cookie auth, no CSRF headers:

| Call | Use |
|---|---|
| `GET /api/kanban/boards` | switcher, current board |
| `GET /api/kanban/board` | board (filters, `since`) |
| `GET /api/kanban/config` | defaults (archived, tenant) |
| `GET /api/kanban/assignees` | filter options (names only) |
| `GET /api/kanban/tasks/{id}` | detail (comments, events, links, runs) |
| `GET /api/kanban/tasks/{id}/log` | log |
| `POST /api/kanban/tasks` | create |
| `PATCH /api/kanban/tasks/{id}` | edit, direct status moves |
| `POST /api/kanban/tasks/{id}/block` | block |
| `POST /api/kanban/tasks/{id}/unblock` | unblock/resume |
| `POST /api/kanban/tasks/{id}/comments` | comment |
| `POST /api/kanban/links` | add dependency |
| `POST /api/kanban/links/delete` | remove dependency |
| `POST /api/kanban/dispatch` | dry run / run |
| `GET /api/kanban/events/stream` | live |
| `GET /api/kanban/events` | polling fallback |
| `POST /api/kanban/boards` | create board |
| `PATCH /api/kanban/boards/{slug}` | edit board |
| `DELETE /api/kanban/boards/{slug}` | archive board |
| `POST /api/kanban/boards/{slug}/switch` | switch |

Not used: `/stats` (not filter-aware), `/config` PATCH (`lane_by_profile` — the
mobile view is always consolidated), task delete (does not exist), attachments,
review.

Every board/task call sends `board={slug}` explicitly. `GET /board` is also used
as the **task index** for pickers, with no filters (§5.2.1).

**Compatibility:** `/boards` and `/board` are required; `/config`, `/assignees`,
SSE, and `/events` are optional and degrade as described in §4.9. There is no
version header; compatibility is capability-based (404 → fallback), which the
repository tests cover.

### 11.4 Repository and ViewModels

`KanbanRepository` (singleton) wraps the API, resolves `baseUrl` from
`ServerState` (same as `PanelsRepository`), and exposes:

- `boards()`, `board(board, filters, since)`, `taskIndex(board)` (unfiltered,
  for pickers), `config(board)`, `assignees(board)`
- `task(id, board)`, `log(id, board, tail)`
- `createTask(body, board)` (see the create recipe in §6.1.1), `updateTask(id, body, board)`,
  `block`, `unblock`, `comment`, `addLink`, `removeLink`
- `dispatch(board, dryRun, max)`
- `createBoard`, `updateBoard`, `archiveBoard`, `switchBoard`
- `events(board, since): Flow<KanbanStreamFrame>` (cold; wraps `RawSseClient`)
- `pollEvents(board, since)` (one page)

Board scoping is explicit on every task call; the repository never relies on the
server's current-board pointer for a pushed screen.

`config(board)` is cached in memory per board for the session, so Create can read
`default_tenant` without a second round-trip. Board lists and assignee lists are
not cached beyond the screen that owns them.

Create is a small orchestration, not one call: Triage uses `triage: true`; Todo
uses `triage: true` then PATCH `todo`; Ready sends neither and accepts the
server's ready/todo decision. The repository returns the created task plus a
`wasGated` flag computed from the response status versus the requested one.

Request bodies: nullable clears (assignee/tenant/body) must send explicit JSON
null. The app's `Json` omits nulls (`explicitNulls = false`), so build a
`JsonObject` or use a dedicated serializer for PATCH when a field is being
cleared. This is a known trap; the plan includes a test for it.

There is no connectivity observer in v1. Mutations always attempt; a transport
failure maps to the offline message, and actions are disabled only while their
own request is in flight.

`KanbanBoardViewModel`:
- Owns boards/board/config load, the debounced SSE job, polling fallback,
  optimistic mutations, and dispatch.
- UI state as in §4.10; effects for navigation (open task, session expired) and
  messages.
- Starts/stops the stream from the screen via `repeatOnLifecycle(STARTED)`;
  exposing a `Flow` from the VM that the screen collects. The VM does not hold a
  lifecycle.
- Debounce in the VM (300 ms), not in the composable.

`KanbanDetailViewModel`: taskId + cursor from `SavedStateHandle`; loads the
detail; its own SSE (or 20 s polling fallback); action methods; one in-flight
mutation at a time.

`KanbanEditorViewModel`: create vs edit from the route; form state; validation;
save; dirty tracking.

`KanbanBoardsViewModel`: board list, create/edit/archive/switch.

### 11.5 Raw SSE

A new `RawSseClient` (or a `rawStream` addition) emits
`RawFrame.Event(id, type, data)` plus the existing `HttpFailure` /
`TransportFailure` outcomes. The chat `SseClient`/`SseEventParser` is **not**
touched: the Kanban vocabulary (`hello`, `events`) is different, and a chat
regression would be far more costly than ~60 duplicated lines.

Parsing:
- `hello`: `{cursor, board}` → seed the cursor if the route did not.
- `events`: `{events:[…], cursor}` → update the cursor to `cursor` (or the max
  event id), emit a "changed" signal; ignore the event bodies for the board
  reload.
- Unknown event names: ignore (forward compatible), optionally log at debug.
- Keepalive comments are handled by OkHttp and produce nothing.
- A clean stream end is still a disconnect: reconnect with `since=cursor` after
  1 s; failures follow the backoff in §4.9.
- `Last-Event-ID` is not used; the query cursor is explicit.

### 11.6 Navigation

Add the routes in §3 to `HermesRoot`; change the FAB action for Kanban; remove
`PanelKind.KANBAN` from `panelActions` and the generic panel branch (and delete
the stub `KanbanPanel`, `PanelsRepository.kanban`, `HermesApi.kanbanTasks`,
`KanbanTaskDto`, `KanbanTasksResponse`). The board/detail transitions use
`enterTransition`/`exitTransition` with `HermesMotion`; sheets use the M3 sheet
spring.

Task routes carry both `board` and an optional `cursor`. The board slug is a
required argument for task destinations (it pins every API call); the cursor is
only a seed for the detail SSE. If the cursor is missing or zero, the detail skips
SSE and polls. After process death, the task route restores with its board slug,
so it cannot resolve against a board another client switched to in the meantime.

### 11.7 Testing

- **Domain tests** (JUnit4): status transition matrix (including dependency-aware
  Ready), staleness thresholds, age formatting, filters/search, selected-column
  fallback, board slug/name validation, dispatcher bucket labeling/counting (all
  fields, unknown keys, lock/memory special states).
- **Serialization tests**: real-shaped fixtures for board (changed and unchanged),
  detail with comments/events/links/runs, log, boards, assignees, dispatch; age
  object; unknown fields/statuses; nullable clears produce explicit null; the
  three create bodies from §6.1.1 (Triage/Todo/Ready) are exactly right.
- **Repository tests** (MockWebServer): every call's method/path/query/body
  (including `board={slug}` on task calls), error mapping (401/404/409/503),
  `changed:false` handling, SSE cursor query, dispatch timeout client, `/boards`
  404 vs SSE 404 vs `/config` 404 compatibility, switch failure paths.
- **ViewModel tests** (Turbine + fake repository): load → content/empty/error,
  config defaults before the first board, filter apply/revert, optimistic move +
  rollback, archive undo, created-status selection/gated message, SSE changed →
  debounced reload, polling fallback, detail refetch on matching event, board
  switch failure reconciliation, archive-board running-worker warning.
- **No Compose UI tests** (the project has none and CI has no emulator). A manual
  device checklist in the plan covers layout, TalkBack, font scale, rotation, and
  the live path.

---

## 12. Security and privacy

- Reuse the existing cookie jar; no new auth. Never send `Origin`, `Referer`, or
  `Sec-Fetch-*` (the existing interceptor handles this).
- Logs and task bodies may contain secrets. Do not log payloads; do not add share
  for logs; copy is explicit. No analytics.
- The board colour/icon are user data, rendered as text/swatches only.
- 401 shows the session-expired state with a **Sign in** action; the existing
  sign-out path clears the session. No silent retry loop.

---

## 13. Delivery phases

| Phase | Deliverable | Exit criteria |
|---|---|---|
| **1. Foundation** | DTOs, API methods, repository, RawSseClient, domain (transitions, staleness, filters), tests | Unit tests green; MockWebServer covers every call; no UI yet |
| **2. Board** | Route, app bar, rail, pager, cards, search, filters, refresh, live SSE/polling, empty/error states, FAB | Board loads from `/board` on a device; chip/pager sync; live updates within ~1 s; no 404s |
| **3. Task** | Detail (Overview/Activity/Runs), log, complete/block/unblock/archive/reopen, comments, dependencies, edit | Every action in §5.6 works or is correctly absent; refusals surface; undo paths work |
| **4. Create + boards + dispatcher** | Create form with advanced fields, board switcher/create/settings, dispatcher preview/run, help | Quick capture in ≤3 interactions; board create/switch/archive; dispatcher preview + run |
| **5. Polish** | Motion transitions, haptics, skeleton, tablet two-column board, accessibility audit, optional multi-select/bulk | TalkBack pass; font scale 200%; reduced motion; visual review |

Each phase is shippable behind the existing FAB entry; Phase 2 replaces the broken
stub. Multi-select/bulk is explicitly Phase 5 (the bridge supports it; it is not
required for the core loop).

---

## 14. Acceptance checklist

Functional:

- [ ] `GET /api/kanban/board` is the only list source; no `GET /api/kanban/tasks`.
- [ ] `/boards` → `/config` → `/board` order; `default_tenant` and
      `include_archived_by_default` affect the first load, and the default tenant
      is visible as an active filter chip.
- [ ] Board renders all payload columns, including an unknown/new column, in server
      order; `archived` appears only when enabled.
- [ ] Every task route carries `board={slug}`; task calls keep working after the
      server's current board is switched elsewhere.
- [ ] Status rail counts match the visible cards after search/filters; tapping and
      swiping stay in sync; re-tapping the selected chip scrolls to top.
- [ ] A ready card that is unassigned says "won't run".
- [ ] Creating a task with only a title works; it lands in the server's actual
      status and is highlighted. Triage/Todo never spend a moment in Ready, and a
      Ready task with unfinished parents lands in Todo (no bypass).
- [ ] Complete requires a result when the task has none; refusals show the server's
      words; running tasks do not offer Complete.
- [ ] Archive on a running task says it stops the worker; undo restores a legal
      status.
- [ ] Comment posting is optimistic and recoverable; dependency add/remove
      refetches; a self-link is impossible in the picker; pickers can choose tasks
      hidden by the board filters.
- [ ] Log loads on demand, truncation is explained, copy works.
- [ ] Dispatcher preview runs automatically; Run shows a result summary; all
      nonzero buckets are represented; `skipped_locked` and memory pressure are
      explained; the board refreshes.
- [ ] Filters apply as a draft (no request per chip); a failed apply reverts.
- [ ] Board switch resets search/assignee and re-applies config defaults, then
      reloads; a failed switch keeps the old board, a successful switch with a
      failed load shows the new board's error; board create/settings/archive
      work; the default board cannot be archived; archiving a board with running
      workers warns that they keep running.
- [ ] Live: an external WebUI change appears while the board is open; backgrounding
      stops the stream; three failures move to polling with a visible state; SSE
      404 falls back to polling/refresh without breaking the board.
- [ ] 401, 503, unknown-endpoint, offline, empty, and filter-empty states all have
      a next action.
- [ ] No read-only action is shown when `read_only` is true.

Design/engineering:

- [ ] Colours only from `MaterialTheme.colorScheme` (except board swatches in
      `Color.kt`); motion only from `HermesMotion`.
- [ ] All touch targets ≥ 48 dp; status never colour-only; TalkBack reaches every
      action; 200% font scale usable.
- [ ] The old Kanban stub endpoints/DTO/panel are deleted, not left dead.
- [ ] `./gradlew :app:testDebugUnitTest` passes; CI assemble is green.

---

## 15. Deliberate deviations from the WebUI, and server gaps

### Deviations (and why)

1. **No lanes-by-profile.** Seven columns × N profiles is a two-axis scroll on a
   phone. The consolidated board + assignee filter answers "what is profile X
   doing" with one sheet. `lane_by_profile` is ignored (no PATCH).
2. **No drag-and-drop.** The WebUI itself has no touch drag. An explicit Move to…
   sheet is more reliable, accessible, and teaches legality. Running is never a
   target.
3. **Full-screen detail with tabs** instead of an inline preview above the board.
   The WebUI's stack pushes the board down; on a phone the detail needs the whole
   screen.
4. **Real multi-select is deferred**, while the WebUI's bulk control is effectively
   single-task. The server's bulk endpoints are ready; this is a Phase 5 addition,
   not a parity loss.
5. **Mobile-only safety rails**: archive confirmation for running tasks, completion
   evidence sheet, undo snackbars, unassigned-ready warnings, discard guards.
6. **No stats endpoint.** Counts are derived from the board so they are
   filter-aware; `/stats` is not.
7. **Create/edit status scope**: Create offers triage/todo/ready (same as WebUI);
   Edit does not edit status at all (status lives in the workflow actions). This
   removes the WebUI's confusing "Actual status" mapping.
8. **Max runtime is entered in minutes** (converted to seconds), because a phone
   form asking for "seconds" invites errors. The API value is unchanged.
9. **Dependencies allow multiple parents** in Create (the bridge accepts a list);
   the WebUI's datalist sends one. The DAG supports it.
10. **Board colour is a fixed accessible palette**, not a free colour picker. It is
    a data accent, never a theme colour.
11. **Create never forces Ready.** Triage uses `triage: true`; Todo creates in
    triage then patches to Todo; Ready lets the server choose Ready or Todo from
    the parents. This removes a dispatcher race and the dependency bypass that a
    naive create-then-patch would introduce.
12. **Live is optional; board state is not.** `/boards` and `/board` are required,
    but a server without SSE still gets a working, periodically refreshed board
    instead of a "needs a newer WebUI" dead end.
13. **No branch field and no "no skills" state.** The bridge does not forward
    `branch_name` on create and collapses `skills: []` to `None`; the app does not
    show controls that would silently lie.
14. **Board archive warns about running workers.** The server archives board
    metadata only; the app surfaces that and requires a second confirmation when
    workers are live.

### Server gaps kept out of v1

| Gap | Consequence |
|---|---|
| `review` status not on the bridge | No Review column; tasks resumed to review disappear from the board (handled by refresh + message) |
| No hard delete | Archive only |
| No attachments | Not shown |
| No run stop verb | Archive-and-stop is the explicit stop control for running tasks |
| No task list / pagination | Board payload is the only source; lazy rendering |
| No sort controls | Server order (priority desc, created asc) |
| No schedule date/time | Scheduled is explained as a pause, not a calendar |
| No comment edit/delete | Not shown |
| No unarchive board | Settings warns |
| No filters in URL/deep links | In-app state only |
| `branch_name` not forwarded on create | No branch field; branch is read-only if a task already has one |
| `skills: []` collapses to `None` | "No skills" is not offered; empty means profile defaults |
| Board archive is metadata-only | Running workers keep working; the app warns and double-confirms |
| No board layout for lanes | Assignee filter instead (see the deviations above) |

---

## 16. Decisions taken (the design questions that were skipped)

1. Kanban stays behind the existing FAB menu, as a full-screen destination; no
   app-wide bottom navigation in this change.
2. Phone-first: one status column per pager page; tablets get a multi-column row in
   Phase 5.
3. No drag-and-drop; explicit Move to… everywhere.
4. Detail is a full screen with three tabs (Overview / Activity / Runs).
5. Lanes-by-profile is replaced by an assignee filter.
6. v1 is WebUI-bridge parity plus mobile safety rails; no review, attachments,
   hard delete, or dedicated run-stop. No bridge changes are required.
7. Live updates are foreground-only; no notifications or badges. SSE is optional:
   polling and periodic refresh keep the board usable on an older bridge.
8. Create defaults to Ready and the active profile; Ready is a **request**, not a
   force — the server gates it on parents. Triage/Todo use a race-free
   `triage: true` path and never pass through Ready.
9. Real multi-select/bulk is Phase 5.
10. The old Kanban stub is removed as part of Phase 2.
11. Task routes are pinned to a board slug; every task call sends `board=`.
12. `/config` is loaded before the first board so the server's default tenant and
    archived-default are visible filters, not silent surprises.
13. There is no connectivity observer; mutations fail fast with an offline message
    and are never queued.
14. Board archive warns about (and double-confirms) running workers, because the
    server archives metadata only.

If any of these is wrong, the plan changes in a bounded way: (1) adds app-shell
navigation work, (2) adds a tablet layout, (5) adds a lanes mode, (6) requires
bridge extensions, (9) moves bulk earlier, (11) removes the board pinning from
routes and calls.
