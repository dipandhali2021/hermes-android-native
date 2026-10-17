# Attach panel (composer above the panel) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the attach `ModalBottomSheet` with an inline panel below the composer — composer stays visible above it exactly as it does over the keyboard — swap the keyboard for the panel height-for-height, and animate the `+` into a tinted `×`.

**Architecture:** The panel becomes a child of the composer surface, revealed by `AnimatedVisibility`; a pure helper computes its minimum height from the tallest remembered IME inset so the composer's top edge holds still through the swap; both hosts (chat via a new `Composer` slot, home in its bespoke composer) own panel state, launchers and dismissal.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2026.06.01, material3 1.4.0), window insets, ActivityResult contracts, JUnit4.

**Spec:** `android-native/docs/specs/2026-10-17-voice-and-attach-design.md` §7 (Round 5 amendment) — read it before starting; the plan argues from it.

## Global Constraints

- **No new dependencies; no version bumps** (see `android-native/AGENTS.md`).
- **Motion only through `HermesMotion`** — no raw `tween`; colours only from `MaterialTheme.colorScheme`.
- **Exact copy:** the toggle description flips `Add` → `Close attachments`; every round-4 pinned string is unchanged.
- **Not a sheet:** no scrim, no drag handle, no `onDismiss`.
- **The recording strip and the panel never coexist.**
- Work on branch `feat/model-picker` inside `android-native/`; CI is the source of truth; no CHANGELOG edits.

## Review Focus

1. In the frame `+` is tapped the IME is still animating down — closing on IME visibility would close the panel right back; the close signal is field **focus** only (Task 3/4).
2. The swap must not move the composer: panel min height = tallest IME inset − gesture-bar inset, with fallbacks for never-seen/partial captures (Task 2/3/4).
3. Back with the panel open closes the panel, not the screen (Task 3/4).
4. Panel and recording strip never coexist — mic tap closes the panel, visibility also requires `VoicePhase.Idle` (Task 3/4).
5. Home's `+` keeps its `!isStartingChat` gating and chat's `+` stays enabled mid-stream (Task 3/4).

---

### Task 2: Attach-panel logic (pure) — tests first

**Files:**
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/domain/composer/AttachPanelLogic.kt`
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/composer/AttachPanelLogicTest.kt`

**Interfaces (used by Tasks 3–4):**
- `const val ATTACH_ROTATION_DEGREES = 45f`
- `fun attachToggleDescription(open: Boolean): String` → `"Add"` / `"Close attachments"`.
- `fun attachPanelMinHeightPx(tallestImePx: Int, navBottomPx: Int, fallbackPx: Int, minAcceptPx: Int): Int` → `tallestImePx - navBottomPx` when that is `>= minAcceptPx`, else `fallbackPx`.

- [ ] **Step 1: Write the failing test** — `AttachPanelLogicTest.kt` (plain JUnit4):

```kotlin
package com.dipandhali.hermesnative.composer

import com.dipandhali.hermesnative.domain.composer.ATTACH_ROTATION_DEGREES
import com.dipandhali.hermesnative.domain.composer.attachPanelMinHeightPx
import com.dipandhali.hermesnative.domain.composer.attachToggleDescription
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachPanelLogicTest {
    @Test fun `toggle describes the panel state`() {
        assertEquals("Add", attachToggleDescription(false))
        assertEquals("Close attachments", attachToggleDescription(true))
    }

    @Test fun `rotation is a clean eighth turn`() {
        assertEquals(45f, ATTACH_ROTATION_DEGREES, 0f)
    }

    @Test fun `panel height follows the keyboard minus the gesture bar`() {
        // 1000px keyboard, 60px gesture bar, 840px fallback, 525px floor:
        // the panel that keeps the composer put is 940px.
        assertEquals(940, attachPanelMinHeightPx(1000, 60, 840, 525))
    }

    @Test fun `panel falls back when no keyboard has been seen`() {
        assertEquals(840, attachPanelMinHeightPx(0, 60, 840, 525))
    }

    @Test fun `panel falls back when the remembered height is a partial capture`() {
        assertEquals(840, attachPanelMinHeightPx(400, 60, 840, 525))
    }
}
```

- [ ] **Step 2: Run it and watch it fail** — `./gradlew :app:testDebugUnitTest --tests com.dipandhali.hermesnative.composer.AttachPanelLogicTest`. Expected: compilation failure (types do not exist). Record in the ledger.
- [ ] **Step 3: Implement `AttachPanelLogic.kt`** — three declarations above; KDoc the height rule (the keyboard vacates `tallestIme − navBottom`; shorter/zero measurements are partial captures, not keyboards).
- [ ] **Step 4: Rerun the test; green.** Commit: `feat(composer): attach-panel logic (rotation, description, keyboard-height rule)`.

### Task 3: `AttachPanel` + `Composer` slot + `+`→`×` — chat wiring

**Files:**
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/AttachPanel.kt`
- Delete: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/AttachSheet.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/Composer.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatScreen.kt`

**Interfaces:**
- `AttachPanel(voiceMode: VoiceInputMode, onVoiceModeChange: (VoiceInputMode) -> Unit, onPhotos: () -> Unit, onCamera: () -> Unit, onFiles: () -> Unit, modifier: Modifier = Modifier)` — body of the old sheet (tiles + `HorizontalDivider` + voice section + supporting line), no modal/sheetState/`onDismiss`; tile stays the same composable (private, renamed `AttachTile`).
- `rememberKeyboardHeight(fallback: Dp = 320.dp, minAccept: Dp = 200.dp): Dp` — in `AttachPanel.kt`; tracks the tallest `WindowInsets.ime` bottom seen, remembers `WindowInsets.safeDrawing` bottom only while the IME is down, and returns `attachPanelMinHeightPx(...)` converted to `Dp` (imports: `WindowInsets`, `LocalDensity`, `mutableIntStateOf`, `roundToPx`).
- `Composer` new params: `attachOpen: Boolean = false`, `attachPanel: (@Composable () -> Unit)? = null`.
- `ChatScreen` keeps `attachOpen` state, launchers and callbacks; hosts `AttachPanel` through the slot.

- [ ] **Step 1: Create `AttachPanel.kt`** — panel UI as above plus `rememberKeyboardHeight`:

  ```kotlin
  if (imeBottom > tallestImePx) tallestImePx = imeBottom
  // safeDrawing is the union of the IME and the system bars; while the IME
  // is up it IS the IME, so remember the gesture bar only with it down.
  if (imeBottom == 0) navBottomPx = safeBottom
  ```
- [ ] **Step 2: Rework `Composer.kt`**
  - `val focusManager = LocalFocusManager.current`; `val panelMinHeight = rememberKeyboardHeight()`.
  - TextField modifier gains `.onFocusChanged { focus -> if (focus.isFocused && attachOpen) onAttach?.invoke() }` — with the toggle semantics, that is the close on field tap (focus, never IME visibility — the IME is still animating down in the opening frame).
  - `ComposerControls` gains `attachOpen: Boolean`; the `+` tap wrapper is `{ focusManager.clearFocus(); onAttach?.invoke() }`; the mic wrapper is `{ if (attachOpen) onAttach?.invoke(); onMic?.invoke() }`.
  - The `+` icon: `progress = animateFloatAsState(if (attachOpen) 1f else 0f, HermesMotion.spatialFast())`; `tint = animateColorAsState(if (attachOpen) colorScheme.primary else colorScheme.onSurfaceVariant, HermesMotion.effects())`; `Modifier.rotate(ATTACH_ROTATION_DEGREES * progress)`; `contentDescription = attachToggleDescription(attachOpen)`; `enabled` unchanged.
  - Below the controls `AnimatedContent`, render when `attachPanel != null`:

  ```kotlin
  AnimatedVisibility(
      visible = attachOpen && voicePhase == VoicePhase.Idle,
      enter = expandVertically(HermesMotion.contentSize, expandFrom = Alignment.Top) + fadeIn(HermesMotion.effects()),
      exit = shrinkVertically(HermesMotion.contentSize, shrinkTowards = Alignment.Top) + fadeOut(HermesMotion.effects()),
      label = "attach-panel",
  ) {
      Box(Modifier.heightIn(min = panelMinHeight)) { attachPanel() }
  }
  ```
- [ ] **Step 3: Wire `ChatScreen.kt`** — rename `attachSheetOpen` → `attachOpen`; `onAttach = { attachOpen = !attachOpen }`; pass `attachOpen` + `attachPanel = { AttachPanel(voiceMode = state.voiceMode, onVoiceModeChange = viewModel::setVoiceMode, onPhotos = { attachOpen = false; attachPicker.launch(...) }, onCamera = { attachOpen = false; ...camera... }, onFiles = { attachOpen = false; filePicker.launch(arrayOf("*/*")) }) }`; delete the `if (attachSheetOpen) { AttachSheet(...) }` block and its import; add `BackHandler(enabled = attachOpen) { attachOpen = false }` (`androidx.activity.compose.BackHandler`).
- [ ] **Step 4: Compile gate + tests** — `./gradlew :app:testDebugUnitTest --console=plain`. Commit: `feat(composer): inline attach panel, animated plus-to-close toggle (chat)`.

### Task 4: Home composer parity

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/home/HomeScreen.kt`

**Interfaces:**
- `HomeComposer` local state renamed `attachSheetOpen` → `attachOpen`; panel rendered in its `Column` below the controls `AnimatedContent` with the same `AnimatedVisibility` block (content inlined — no slot); `rememberKeyboardHeight()` called in `HomeComposer` body.
- `+` button: same animated icon block as Task 3; tap = `{ focusManager.clearFocus(); attachOpen = !attachOpen }`; `enabled = !isStarting`.
- TextField gains `.onFocusChanged { focus -> if (focus.isFocused && attachOpen) attachOpen = false }` (keep `enabled = voicePhase == VoicePhase.Idle`).
- Mic click becomes `{ if (attachOpen) attachOpen = false; onMicTap() }`.
- `BackHandler(enabled = attachOpen) { attachOpen = false }` inside `HomeComposer` (composed after the screen's FAB-menu handler, so it wins while open).
- Delete the `if (attachSheetOpen) { AttachSheet(...) }` block at the end of `HomeComposer`; imports swap `AttachSheet` → `AttachPanel` (+ `rememberKeyboardHeight`, `LocalFocusManager`, `rotate`, `animateColorAsState`, `AnimatedVisibility`, `expandVertically`, `shrinkVertically`, `onFocusChanged`).

- [ ] **Step 1:** Apply the rework. **Step 2:** Compile gate + full suite (`./gradlew :app:testDebugUnitTest --console=plain`). Commit: `feat(composer): inline attach panel on home (parity)`.

### Task 5: Evidence, CI, combined branch, APK

**Files:**
- Modify: `android-native/docs/plans/2026-10-17-attach-panel.md` (tick boxes), `.superpowers/sdd/2026-10-17-attach-panel/progress.md` (ledger), `android-native/docs/HANDOFF-model-picker.md` (untracked; refresh with the round-5 device checklist). No CHANGELOG edits.

- [ ] **Step 1:** Full local run: `./gradlew :app:testDebugUnitTest --console=plain --rerun` — record test-case counts from `app/build/test-results/`.
- [ ] **Step 2:** Push `feat/model-picker`; watch CI; fetch `hermes-native-debug-apk`; record run URL + md5.
- [ ] **Step 3:** Rebuild `test/model-picker-home-send` (merge the new feat head; the resolution follows the round-4 proven union); force-push with lease; CI green; fetch APK; md5.
- [ ] **Step 4:** Device checklist (into the handoff): panel opens below the composer with the keyboard out of the way; composer does not jump through the swap; `+` rotates to a tinted `×` and back; field tap closes the panel and raises the keyboard; back closes the panel; tiles launch after closing; mic closes the panel; both composers; mid-stream attach (chat) still works.
- [ ] **Step 5:** Update ledger + handoff. No merge/release until the user says so.
