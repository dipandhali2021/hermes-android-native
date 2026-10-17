# Handoff — 0.4.1 composer / FAB fixes

> **Provenance note.** This file was written at the user's request to carry work
> forward from the previous session. The assistant writing it did **not** have the
> earlier turns in context, so everything below is reconstructed from the working
> tree at commit `0821fc4` plus the user's report. Items marked *(reported)* came
> from the user; items marked *(observed)* were verified by reading code. Anything
> not marked was not verified — treat it as an open question, not a fact.
>
> **No screenshots were saved.** The user's reference images (`Image #7`,
> `Image #8`) were attachments in the previous session and are not on disk, so the
> exact intended layout must be re-confirmed with the user before implementing.

## Session 2 status — implemented, verified, released as v0.4.1

All three issues were fixed in commit `58a82a0` on branch
`fix/home-composer-fab-menu` (pushed to origin). CI run **38901878256** on that
branch is green: `:app:testDebugUnitTest` passed and the debug APK is uploaded
as the `hermes-native-debug-apk` artifact. No local build, emulator or device
run was performed (per the hard rule below).

Against the analysis below:

- Issue 1 root cause confirmed, but not where this document guessed: the
  stacking was `Scaffold(contentWindowInsets = safeDrawing)` — whose content
  padding already contains the IME and is not consumed — plus the body column's
  `imePadding()`. The chat composer was never involved; home has its own
  `HomeComposer` and no caller passes it any padding beyond its own. Fix: the
  composer moved to the Scaffold `bottomBar` with a single inset application
  (`safeDrawing.only(Bottom)`), and the body column's `imePadding()` is gone.
- Issue 2 implemented as option (b): the composer observes presses at the
  Initial pointer pass (never consumed) and closes the menu on any press, so
  the tap still focuses the field. `FabMenuScrim` remains unused — it could not
  cover the composer, which now lives in the bottom bar outside the content
  area a scrim could fill.
- Issue 3 root cause confirmed: `maxItemsHeight = 320.dp` against nine 48dp
  pills, scrolled from the top. Fix: the item stack takes `weight(1f,
  fill = false)` from whatever the parent offers, and the FabMenu is a
  bottom-end overlay above the composer, so expansion can no longer move the
  composer or shrink the recent list.

Remaining: the user installs the debug APK and checks the three behaviours on
device; on confirmation, merge to `master` and dispatch `release.yml` with
`v0.4.1` (see the release section below).

**Update — released 2026-10-16.** The user verified all three behaviours on
device. `master` was fast-forwarded `0821fc4 → 58a82a0`, and release run
38903741964 published `v0.4.1` (tag at `58a82a0`) with both APK assets; the
sha256 is in the release notes.

## Repo and state

- Repo: `dipandhali2021/hermes-android-native` (a separate git repo nested at
  `/home/dipandhali/Documents/Projects/hermes-webui/android-native`).
- HEAD: `0821fc4` — *"fix: handle the OpenSessionAndSend effect on the home screen"*,
  2026-10-16 00:20:39 +0530.
- Branch `master`, working tree **clean**, no local modifications. Five release
  tags exist remotely (`v0.1.0` … `v0.4.0`) but they are **not** present locally —
  `git tag` returns nothing until a fetch, which is why `git tag` is misleading
  here. `v0.4.0` points at the current HEAD, `0821fc4`.
- Version is **not in the source**: `versionName` comes from `-PversionName=<tag>`
  (CI passes the git tag), falling back to `"0.2.0"` locally
  (`app/build.gradle.kts:24`). So "0.4.1" is a release tag that has not been
  created yet — no `0.4.1` string exists anywhere in the tree *(observed)*.
- No `0.4.1` APK was built or installed in this session.

## Background tasks from the previous session — both killed, neither completed

1. *"Install emulator and android-36 system image"* — **stopped by the user, and
   correctly so. See the hard rule below: do not install or run an emulator.**
   It left only `~/.android` at 3.6M and **no** `system-images` directory under
   `~/Android/Sdk` *(observed)*. There is no emulator and no AVD available, and
   none should be created.
2. *"Online baseline compile + unit tests"* — **stopped by the user**.
   **No baseline build/test result exists.** Do not assume the tree compiles;
   establish that through CI (below), not by building locally.

Disk is not currently a constraint: 46G free on `/` *(observed)*, despite the
`AGENTS.md` warning that the dev machine is disk constrained.

## HARD RULE: do not build or run locally — CI does everything

**The user has explicitly and repeatedly stated this. Do not violate it.**

- **Never install an Android SDK component, system image, or emulator**, and
  **never launch an emulator or AVD** on this machine. A previous attempt to
  install an emulator and `android-36` system image was made and the user killed
  it. Do not retry it, in any form, for any reason — including "just to check the
  layout".
- **Do not run local Gradle builds** (`./gradlew :app:assembleDebug`,
  `testDebugUnitTest`, etc.). CI is the source of truth, the machine is disk
  constrained, and local runs are not wanted even when they would succeed.
- Verification evidence for these UI fixes therefore comes from **CI artifacts
  plus the user's own screenshots from their device** — not from a local
  emulator, and not from the assistant's own rendering.

### Debug build — via the `Android build` workflow

`.github/workflows/android.yml` runs automatically on **every push to any branch**
and on every pull request. It runs `:app:testDebugUnitTest`, assembles the debug
APK, and uploads:

- artifact **`hermes-native-debug-apk`** (`app/build/outputs/apk/debug/*.apk`, 30-day
  retention) — this is the APK to hand the user for on-device testing.
- artifact **`test-reports`** (test reports/results, always uploaded even on
  failure, 7-day retention) — this is the regression evidence, including whether
  `ColorContrastTest` still passes.

So the loop is: push the branch → wait for `Android build` → read the run's test
results → give the user the debug APK artifact. There is no `workflow_dispatch`
needed; pushing is enough (`workflow_dispatch` is also enabled on it).

### Release — via the `Release APK` workflow, exactly as before

Releases are **not** triggered by pushing a tag. Every prior release
(`v0.1.0`, `v0.2.0`, `v0.2.1`, `v0.3.0`, `v0.4.0`) was published by
**manually dispatching `release.yml` with the `version` input** *(observed via
`gh run list --workflow=release.yml` — all five runs are `workflow_dispatch`)*.

```
gh workflow run release.yml -f version=v0.4.1
```

`release.yml` then builds the release APK (`-PversionName=<version>`,
`-PversionCode=<run number>`), creates the tag `v0.4.1` at the current commit,
and publishes a GitHub release titled `Hermes Native v0.4.1` with **two assets**:

- `hermes-native-v0.4.1.apk` — the versioned asset.
- `hermes-native-latest.apk` — the version-free copy behind the stable URL
  `.../releases/latest/download/hermes-native-latest.apk` that the README tells
  users to bookmark.

It also prints a sha256 into the release notes. If the tag already exists, it
uploads with `--clobber` rather than failing, so re-dispatching a version is safe.

**Version note:** `v0.4.0` points at `0821fc4` — the current HEAD *(observed)*.
So `v0.4.1` must point at a commit **after** the fixes; dispatch the workflow only
once the fix commits are on the branch being released, or the tag will be created
on the wrong commit. Existing tags: `v0.1.0`, `v0.2.0`, `v0.2.1`, `v0.3.0`,
`v0.4.0`.

## The three reported issues

All three are on the **home screen** (`ui/home/HomeScreen.kt`), which is the
default route. `ChatScreen` is a separate surface and shares only `Composer.kt`.

### 1. Composer opens *above the keyboard* instead of being pushed up

Reported: tapping the composer does not raise it with the keyboard; it "goes
outside the screen to the top".

Observed in code — the home screen applies IME padding in **two** nested places,
which stack:

- `HomeScreen.kt:129` — `.imePadding()` on the outer `Column` inside the
  `Scaffold` body.
- `Composer.kt:78` — `.imePadding()` again on the `Surface` of the composer, and
  `ChatScreen` uses this same `Composer` in its `bottomBar` (`ChatScreen.kt:184`).

The likely mechanism is the scaffold inset chain combined with the duplicate pad,
but this is a **hypothesis to confirm on a device/emulator**, not a diagnosis.
Relevant facts: the manifest sets `android:windowSoftInputMode="adjustResize"`
and `MainActivity` calls `enableEdgeToEdge()` *(observed)*; every scaffold uses
`contentWindowInsets = WindowInsets.safeDrawing` *(observed)*.

**Untested design question:** Material 3 `Scaffold` on some versions consumes
window insets inconsistently with `safeDrawing`. The reliable reshape here is
probably to drop the inner `imePadding()` from `Composer.kt` and keep the single
outer one, and/or move the composer into `Scaffold`'s `bottomBar` on home to
match how `ChatScreen` already does it. Confirm which before changing both call
sites — `Composer.kt` is shared.

### 2. Opening the composer should automatically close the FAB menu

Reported: when tapping the composer, the FAB icon should auto-close.

Observed *(observed)*:

- `menuOpen` state and the `FabMenu` call: `HomeScreen.kt:84`, `195–202`.
- The **only** things that close the menu today are the FAB tap itself and the
  system back button via `BackHandler(enabled = menuOpen) { menuOpen = false }`
  (`HomeScreen.kt:86`). Nothing observes composer focus.
- `FabMenuScrim` exists in `FabMenu.kt:251` and **has zero call sites anywhere in
  the repo** — it is dead code that was presumably written for exactly this
  dismiss-on-outside-tap behaviour but never wired up.
- There is **no** `onFocusChanged` / `FocusRequester` / `LocalFocusManager` usage
  anywhere in `app/src` *(observed)*. So there is currently no mechanism at all to
  know the composer gained focus.

Two viable approaches, both requiring a decision:

- **(a)** Wire `FabMenuScrim` behind the menu so any outside tap dismisses it.
  Cheap, no focus plumbing, but dismisses on *any* outside tap rather than
  specifically on composer focus.
- **(b)** Pass an `onFocus` callback up from the composer (`onFocusChanged` on the
  `TextField`) and set `menuOpen = false`. Matches the request literally
  ("when I click on composer"), and works for both home and chat.

Recommend (b), optionally plus (a).

### 3. Expanded FAB menu clips its top items

Reported: on the default screen, expanding the `+` does not show everything —
some items are "blocked / out of vision".

Observed *(observed)*:

- `HomeScreen.kt:201` passes `maxItemsHeight = 320.dp` (the `FabMenu` default is
  `360.dp`).
- Nine panel actions are supplied from `panelActions()` in `HermesRoot.kt:108–126`.
- `FabMenu` renders the stack in a `Column` with `heightIn(max = maxItemsHeight)`
  + `verticalScroll` (`FabMenu.kt` item stack), so a too-small cap means the
  stack scrolls rather than the items being pushed off-screen — but it sits in a
  `Row` aligned to the bottom, above the composer, with a recent-sessions list
  occupying `weight(1f)` above it.

**Hypothesis to confirm visually:** the menu expands *upward* out of a
bottom-aligned `Row` and either (i) its 320dp cap is smaller than nine pills need
so items are only reachable by scrolling, or (ii) the expanded stack overflows the
available height between the top bar and the composer and gets clipped by the
parent `Box`. The fix shape — overlay/`Popup`, a taller cap, or moving the menu
out of the flow — depends on which. **Do not guess; reproduce first.**

## Constraints to respect (from `android-native/AGENTS.md`)

- **CI is the source of truth**; local builds are not required (disk constrained).
- Do **not** bump Coil / Kotlin / Compose BOM / material3 / compileSdk — each pin
  is deliberate, with the reasoning and a verification command in `AGENTS.md`.
- Colours only from `MaterialTheme.colorScheme`; durations/springs only from
  `ui/theme/Motion.kt` (no raw `tween(...)` in a component).
- Tests: `./gradlew :app:testDebugUnitTest`. `ColorContrastTest` asserts WCAG AA
  on every on/container pair — if it fails after a palette edit, the palette is
  wrong, not the test.
- Protocol rules (SSE `done` does not close the stream, never send
  `Origin`/`Referer`/`Sec-Fetch-*`, `hermes_session` is a cookie) are **not
  touched by these three fixes** — do not refactor near them.

## Suggested first steps for the next session

1. Re-confirm the intended layout from the user — **the two screenshots are
   missing** and issues 1 and 3 are both visual. Ask for them again if needed.
2. Establish the baseline **by pushing and reading the `Android build` run**, not
   by building locally. Confirm the tree compiles and `ColorContrastTest` passes
   before changing anything.
3. Decide the approach for issue 2 (scrim vs. focus callback) before editing.
4. Implement, then push — `Android build` gives the test results and a
   `hermes-native-debug-apk` for the user to install on their own device.
5. `AGENTS.md` requires before/after evidence for UI changes. Since no emulator
   may be run locally, that evidence is **the user's device screenshots plus the
   CI artifact** — ask the user to capture before/after on their phone rather
   than attempting to render it here.
6. Once the user has confirmed the fixes on device, publish with
   `gh workflow run release.yml -f version=v0.4.1`, exactly as the previous
   releases were published.
7. Open one PR per logical change, per the parent repo's contribution style.

## Open questions

- Is v0.4.1 intended to carry all three fixes, or a subset? (The user said
  "0.4.1 version should be these changes" — confirm all three are in scope.)
- Is the home screen's FAB meant to overlay the recent list (a true overlay), or
  to push it down? This determines the fix for issue 3.
- Does the composer issue also reproduce on the **chat** screen, which shares
  `Composer.kt`? That tells us whether the fix belongs in the component or only in
  `HomeScreen`.
