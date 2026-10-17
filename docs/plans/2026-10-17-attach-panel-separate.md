# Attach panel as its own card (device feedback) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the merged composer+panel surface into two cards — the composer keeps its own surface, the panel becomes a separate rounded card below it with the reference's list layout (grab handle; icon + title + supporting line rows) — without moving the composer through the keyboard swap.

**Architecture:** `Composer`'s root becomes a `Column` carrying the call-site inset modifier: the composer `Surface` first, then the panel `AnimatedVisibility` outside it. `AttachPanel` draws its own `surfaceContainer` card with an 8 dp top gap baked in, so the §7.2 min-height wrapper is untouched. Home mirrors both; its press-listener moves to the new root so panel taps close the FAB menu too.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2026.06.01, material3 1.4.0), JUnit4.

**Spec:** `android-native/docs/specs/2026-10-17-voice-and-attach-design.md` §8 (Round 5.1 amendment) — read it before starting; the plan argues from it.

## Global Constraints

- **No new dependencies; no version bumps** (see `android-native/AGENTS.md`).
- **Motion only through `HermesMotion`**; colours only from `MaterialTheme.colorScheme`.
- **Exact copy:** `Photos` / `Choose from your library`; `Files` / `Attach a document or archive`; `Camera` / `Take a photo now`. Everything else pinned in §7 stands.
- **No logic changes:** `AttachPanelLogic` / `rememberKeyboardHeight` / the toggle are untouched, and so is the dismissal set.
- Work on branch `feat/model-picker` inside `android-native/`; CI is the source of truth; no CHANGELOG edits.

## Review Focus

1. The keyboard-swap arithmetic must survive the split: the panel card's 8 dp gap lives *inside* the §7.2 min-height wrapper, so the block is still `composer + max(keyboard − gesture bar, 8 + content)` (Task 3/4).
2. The call-site modifier (insets + 12 dp margins) must move to each composer's new root `Column`, not stay on the `Surface` — otherwise the panel card loses its margins and the inset (Task 3/4).
3. Home's press-listener must cover the panel card: a tap on the panel closes the FAB menu exactly like a tap on the composer (Task 4).
4. The composer card's own look must not change: same colour, shape, internal padding (Task 3/4).

---

### Task 2: `AttachPanel` becomes its own card with the reference's list

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/AttachPanel.kt`

**Interfaces:**
- Signature unchanged: `AttachPanel(voiceMode, onVoiceModeChange, onPhotos, onCamera, onFiles, modifier = Modifier)`.
- Root becomes a `Surface(color = surfaceContainer, shape = shapes.extraLarge, modifier = modifier.fillMaxWidth().padding(top = 8.dp))` — the gap is part of the panel.
- New private `AttachRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit)`; the old `AttachTile` and its press-scale machinery (`MutableInteractionSource`, `collectIsPressedAsState`, `animateFloatAsState`, `graphicsLayer`) are deleted.
- `rememberKeyboardHeight` and the `voiceModeLabel`/`voiceModeSupportingLine` helpers are untouched.

- [x] **Step 1: Rewrite the file** — handle pill (`32x4 dp`, `CircleShape`, `outlineVariant`, centred, `padding(top = 12.dp)`), then the rows in the pinned order — Photos, Files, Camera — each title + supporting line, then `Spacer(8.dp)`, `HorizontalDivider()`, and the voice section with 20 dp horizontal padding and 20 dp bottom padding, as in §8.2.
- [x] **Step 2: Compile gate + full suite** — `./gradlew :app:testDebugUnitTest --console=plain`. Commit: `feat(composer): attach panel as its own card with list rows`.

### Task 3: `Composer` two-card restructure (chat)

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/Composer.kt`

**Interfaces:**
- No signature change. Root: `Column(modifier = modifier.fillMaxWidth())` wrapping `Surface(…, modifier = Modifier.fillMaxWidth()) { Column { tray; field; AnimatedContent(controls) } }` and, below, the `AnimatedVisibility` panel block moved *outside* the `Surface` unchanged in shape:

```kotlin
Box(Modifier.heightIn(min = panelMinHeight)) { attachPanel() }
```

- `ChatScreen.kt` does **not** change (its call-site modifier now lands on the root; the slot API is unchanged).

- [x] **Step 1:** Apply the restructure; keep the round-5 comments accurate (the inset note: the modifier wraps both cards).
- [x] **Step 2:** Compile gate + full suite. Commit: `feat(composer): two cards — panel outside the composer surface (chat)`.

### Task 4: Home composer two-card restructure

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/home/HomeScreen.kt`

**Interfaces:**
- `HomeComposer` root becomes `Column(modifier.fillMaxWidth().pointerInput(…) { … currentOnPress() … })` (the press-listener moves from the `Surface` to the root so the panel card is covered); then `Surface(…, modifier = Modifier.fillMaxWidth())` with `Column { tray; field; AnimatedContent(controls) }`, then the existing `AnimatedVisibility` panel block below the surface.

- [x] **Step 1:** Apply; the listener comment now reads: a tap anywhere in the composer *or its panel* closes the FAB menu and still lands on the control.
- [x] **Step 2:** Compile gate + full suite. Commit: `feat(composer): two cards on home`.

### Task 5: Evidence, CI, combined rebuild, APK

**Files:**
- Modify: `android-native/docs/plans/2026-10-17-attach-panel-separate.md` (tick boxes), `.superpowers/sdd/2026-10-17-attach-panel-separate/progress.md`, `android-native/docs/HANDOFF-model-picker.md` (untracked on feat; refresh as the round-5 + 5.1 handoff).

- [x] **Step 1:** Full local run with `--rerun`; record test-case counts from `app/build/test-results/`.
- [x] **Step 2:** Push `feat/model-picker`; watch CI; fetch the APK artifact; record run URL + md5.
- [x] **Step 3:** Merge the new feat head into `test/model-picker-home-send` (the round-5 merge at `03b69f3` is the base; resolution follows the proven union: take the combined side for state-driven `isStartingChat`); CI green; fetch APK; md5.
- [x] **Step 4:** Refresh the handoff with the round-5 + 5.1 device checklist (two cards; list rows; handle; swap still still; toggle unchanged; panel taps close the FAB menu on home).
- [x] **Step 5:** Close both plans' task boxes and ledgers. No merge to master, no release until the user says so.
