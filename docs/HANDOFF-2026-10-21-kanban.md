# Handoff — 2026-10-21 (Kanban)

> Start with [`README.md`](README.md) for the project/documentation map. This
> file is the state of the **full-screen Kanban feature line** on
> `feat/kanban`. The integrated app baseline through Round 9 remains
> [`HANDOFF-2026-10-19.md`](HANDOFF-2026-10-19.md).

## TL;DR

The full Kanban client is implemented on `feat/kanban`: board-first navigation,
all payload columns, task detail with Overview/Activity/Runs, create/edit,
filters/search, legal status moves, comments and dependencies, worker logs,
board switching/create/settings/archive, the dispatcher preview/run sheet, and
help. It uses the existing WebUI `/api/kanban/*` bridge and the `hermes_session`
cookie; there are no backend changes.

Local unit suite: **434 tests, 0 failures**. `:app:assembleDebug` is green.
CI run for the functional head `67a2b7b`:
[39585583352](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39585583352)
— **success** (unit tests 434/0, debug APK built and uploaded). The APK
checksum is `d3230eaebafe248d17ab33e24f8282b3`.

**No device pass has been run.** There is no `adb`, emulator, or attached
Android device in this environment. Treat the UI as "implemented, unit-tested
and CI-built", not "device-verified".

## Branch and commits

| Item | Value |
|---|---|
| Branch | `feat/kanban` |
| Base | `dc350de` (`test/model-picker-home-send` docs head) |
| Functional head | `67a2b7b` `kanban: close acceptance review gaps` |
| Task 26 docs commit | this handoff/README/spec update |
| Commit range | `dc350de..feat/kanban` |

The branch is pushed to `origin/feat/kanban`. Do not merge it to `master`
without the owner's explicit decision; integration and release are separate
steps.

## What shipped

**Board**
- Full-screen board, status rail, horizontal pager, server column order,
  unknown/new columns preserved, archived only when requested.
- Search/filter draft sheet, active filter chips, counts, ready count, live
  badge, error/empty/offline states, read-only mode.
- Card actions, legal Move sheet (Scheduled/Blocked → Ready uses `unblock`,
  never a direct status patch), Complete/Block/Archive/Reopen/Restore, Undo
  where the server makes it safe.
- Create FAB with status-aware capture; Triage/Todo create through `triage:true`
  and never pass through Ready; Ready creates never patch status.
- Board switcher, archived toggle, board create/settings, guarded archive.

**Task detail**
- Overview, Activity, Runs tabs; worker log on demand; comments optimistic with
  retry; dependency add/remove; copy ID; edit/read-only handling; status-aware
  action row; archive waits for the server and pops.

**Review hardening**
- Reopen/Restore no longer bypass the dependency gate for Ready; the sheet and
  both ViewModels refuse a known unfinished-parent move.
- A board 401 replaces the workspace with a full-screen session-expired state
  and a Connect action.
- Complete/Block refusals keep the sheet open with the server message; Complete
  offers **Refresh task**.
- Stale mutation responses are ignored after switching A → B → A, and created
  tasks hidden by active filters offer **Clear filters**.

**Dispatcher and help**
- Automatic dry-run preview when the sheet opens, nonzero buckets rendered from
  the returned `JsonObject` (unknown buckets included), row details, lock and
  memory-pressure special cases, run/refresh/retry, running progress.
- A dismissed run keeps running; its result survives in the ViewModel and the
  board refreshes.
- Help sheet covers statuses, Ready gates, archive-vs-delete, running workers,
  and the v1 omissions.

**Polish**
- Shared-axis board→detail transition, slide transitions for create/edit/log,
  skeleton with rail + card placeholders, status-rail and status-chip haptics,
  confirm haptic after successful status mutations, archive reject cue,
  scroll-direction-aware FAB shrink, ≥600 dp fixed-width column row, 48 dp card
  overflow, rail/card/live-badge semantics, and content-size disclosure motion.

## Evidence

### Local (2026-10-21)

```
./gradlew :app:testDebugUnitTest :app:assembleDebug
BUILD SUCCESSFUL
tests=434 failures=0 errors=0 skipped=0
```

- `:app:lintDebug` has **no new Kanban findings**. It still fails only on the
  pre-existing `PanelScreens.kt:116` `NonObservableLocale` error.
- `git diff --check` clean.
- No raw `Color(0x...)` outside `ui/theme/Color.kt`; no raw `tween(...)` in
  `ui/kanban`.

### CI

| Branch head | Run | Result |
|---|---|---|
| `67a2b7b` (functional) | [39585583352](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39585583352) | **success**; 434/0 tests; APK uploaded |
| `2edc288` (Task 26 docs) | [39575494979](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39575494979) | **success**; docs-only |
| `87df93a` (previous functional) | [39574231251](https://github.com/dipandhali2021/hermes-android-native/actions/runs/39574231251) | **success**; 428/0 tests; APK uploaded |
| This handoff docs commit | generated after this file is committed | docs-only follow-up; app tree unchanged |

APK artifact: `hermes-native-debug-apk/app-debug.apk`, 22,706,604 bytes,
md5 `d3230eaebafe248d17ab33e24f8282b3`. Test artifact: `test-reports`
(`434 tests, 0 failures`). Both are attached to run `39585583352`; GitHub
artifact retention applies.

The workflow is `.github/workflows/android.yml`: unit tests, debug APK,
checksum and artifact upload. Do not claim a CI success that is not linked
above.

### Device and server

Not run. Recorded as unverified:

- No server version was exercised. No Kanban board shape (columns, board count,
  archived mix) was sampled against a live WebUI.
- No device/emulator was available; `adb` is not installed.
- Device checks still required: TalkBack order and announcements, accessibility
  scanner, 200% font scale, reduced motion, tablet/rotation, dark/dynamic color,
  haptics, navigation transitions, FAB shrink, and the design §14 workflow.

## Acceptance checklist status

`Verified (code+tests)` means the behavior is covered by unit tests and/or
static inspection. It is not a substitute for the device pass.

| Area | Status |
|---|---|
| `/board` is the only task-list source; no `GET /tasks` list | Verified (code+tests) |
| `/boards` → `/config` → `/board`; config defaults and tenant filter | Verified (code+tests) |
| All payload columns, unknown columns, archived gating | Verified (code+tests) |
| Every task route carries `board={slug}` | Verified (static: `HermesApi.kt`; API tests assert the query) |
| Rail counts, tap/swipe sync, reselect scroll-to-top | Verified (code) |
| Unassigned Ready says "won't run" | Verified (code+tests) |
| Create recipe and actual-status landing/highlight | Verified (tests) |
| Complete requires a result; running cannot complete | Verified (tests) |
| Running archive warning; undo restores a legal status | Verified (tests) |
| Comments optimistic/recoverable; dependency refetch; self-link blocked | Verified (tests) |
| Log on demand, truncation copy, copy ID | Verified (code+tests) |
| Dispatcher preview/run, nonzero buckets, lock/memory copy, refresh | Verified (tests) |
| Filters draft/failed-apply revert | Verified (tests) |
| Board switch/create/settings/archive edge cases | Verified (tests) |
| Acceptance review: dependency gate, 401 screen, inline refusals, stale mutations | Verified (tests) |
| Live SSE, background stop, polling fallback, read-only | Verified (code+tests); direct polling fallback, no reconnect retries (deviation below) |
| Error/empty/filter-empty states with a next action | Verified (code+tests) |
| Colours/motion tokens only | Verified (static) |
| ≥48 dp targets, status not colour-only, semantics, 200% font | Reviewed in code; **device pending** |
| Old Kanban stub endpoints/DTO/panel gone | Verified (static: 0 hits for `PanelKind.KANBAN`, `kanbanTasks`, `KanbanPanel`) |
| Unit tests + CI APK | Local green; CI linked above |

## Approved deviations and spec updates

- **Archived boards** are hidden until **Show archived** is enabled, then listed.
  The spec's "not listed" wording was updated to match the approved Task 23
  brief.
- **Dispatcher copy** follows the approved Task 24 brief: "Preview — no
  changes", "Dispatcher result", "Run dispatcher"/"Run again", "Refresh
  preview", "Retry". Spec §7 was updated to these labels.
- The help sheet landed in Task 23, not 24, because the board switcher already
  had a "How this board works" row; the ledger records the ruling.
- On expanded widths the board is a horizontally scrolling row of fixed 360 dp
  columns (spec §10.1), not a separate two-page pager.
- **Live recovery is polling-only in v1.** SSE failures go directly to the 30 s
  polling fallback; the design's 2 s/5 s `Reconnecting` retries and manual
  **Retry** banner are not implemented. This follows the approved Task 11 plan
  and is recorded in spec §4.9 as a v1 simplification, not shipped behavior.
- **Reopen/Restore Ready is dependency-gated.** The acceptance review found the
  plan's Task 13 wording allowed a direct `status=ready` write. The code and
  plan now require resolved parents and disable/refuse Ready when a parent is
  unfinished.

## Known gaps and deliberate omissions

- **No review actions, attachments, hard delete, dedicated run-stop, or bulk
  multi-select** in v1.
- **SSE is optional and recovery is polling-only**: the board works with
  polling and a visible live state, but it does not implement the design's
  reconnect/backoff banner (§4.9). There is no offline queue. Mutations fail
  fast when the server is unreachable.
- **Device accessibility and responsive checks are pending** (see above).
- **Paused STT work is untouched.** The local WebUI patch and uncommitted tests
  in `/home/dipandhali/Documents/Projects/hermes/hermes-webui` are not part of
  this branch. The Android app still uses the existing server transcription
  path.
- **`master` is unchanged.** This branch is not merged to the integration/test
  branch or `master`.

## Next actions, in order

1. Install the APK from the linked run (md5 above) on a phone/tablet and run
   the design §14 checklist plus the device-only checks above. Record the WebUI
   server version and the board shape used.
2. Fix device findings on `feat/kanban`; re-run unit tests and CI.
3. When the owner approves, merge `feat/kanban` into the integration branch with
   `--no-ff`. Do not merge the integration branch itself to `master`; releases
   remain manual and explicitly requested.
