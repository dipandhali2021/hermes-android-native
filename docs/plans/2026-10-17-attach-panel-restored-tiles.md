# Tiles restored, mode on the mic (device feedback) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Undo the §8.2 list restyle — the panel goes back to the round-5 tile composition (Photos / Camera / Files tonal tiles with press scale, divider, voice section) — while keeping §8.1's separation (the panel is its own card below the composer). Plus: the composer's mic icon follows the selected voice mode, live.

**Architecture:** `AttachPanel` reverts to the round-5 content, wrapped in its own `Surface` card (`surfaceContainer`, `shapes.extraLarge`, 8 dp top gap) — the §8.1 split. A top-level `voiceModeIcon(mode)` signs the mic in both composers; `Composer` threads the mode to `ComposerControls`.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2026.06.01, material3 1.4.0), JUnit4.

**Spec:** `android-native/docs/specs/2026-10-17-voice-and-attach-design.md` §9 (Round 5.2 amendment) — read it before starting.

## Global Constraints

- **No new dependencies; no version bumps** (see `android-native/AGENTS.md`).
- **No logic changes:** `AttachPanelLogic` / `rememberKeyboardHeight` / the toggle / dismissal set are untouched.
- **Motion only through `HermesMotion`**; colours only from `MaterialTheme.colorScheme`.
- Work on branch `feat/model-picker` inside `android-native/`; CI is the source of truth; no CHANGELOG edits.

## Review Focus

1. The tile content must be verbatim round-5 (commit `f1edc59`): same tiles, labels, press-scale, dividers, voice section — only the top divider is dropped (§9.1) and the Column gains `top = 16.dp`.
2. The two-card split must not move: panel card `Surface` with the 8 dp gap inside the unchanged min-height wrapper (§8.1).
3. The mic icon must be derived from the mode everywhere it appears — both composers — with no other mic behaviour change (§9.2).

---

### Task 2: `AttachPanel` — round-5 tiles inside its own card

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/AttachPanel.kt`

**Interfaces:**
- Signature unchanged: `AttachPanel(voiceMode, onVoiceModeChange, onPhotos, onCamera, onFiles, modifier = Modifier)`.
- Base: the `f1edc59` content (`git show f1edc59:app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/AttachPanel.kt`), with: root becomes `Surface(color = surfaceContainer, shape = shapes.extraLarge, modifier = modifier.fillMaxWidth().padding(top = 8.dp))`; the Column inside keeps `padding(horizontal = 20.dp)` and gains `top = 16.dp`; drop the top `HorizontalDivider(padding(top = 4.dp))` + `Spacer(16.dp)`. Also add top-level `voiceModeIcon(mode: VoiceInputMode): ImageVector` (Mic / GraphicEq) for §9.2.

- [x] **Step 1:** Apply the revert + card wrapper + `voiceModeIcon`.
- [x] **Step 2:** Compile gate + full suite. Commit: `feat(composer): tile panel back in its own card; mic wears the mode`.

### Task 3: The mic wears the voice mode (both composers)

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/Composer.kt`, `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatScreen.kt`, `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/home/HomeScreen.kt`

**Interfaces:**
- `Composer` gains `voiceMode: VoiceInputMode = VoiceInputMode.TRANSCRIBE`; `ComposerControls` gains it too; the mic's `Icon(Icons.Rounded.Mic, …)` becomes `Icon(voiceModeIcon(voiceMode), …)` (keep `"Voice input"`).
- `ChatScreen`: pass `voiceMode = state.voiceMode` next to the existing `attachPanel` slot wiring.
- `HomeComposer`: mic icon → `voiceModeIcon(voiceMode)` (the mode is already a parameter).

- [x] **Step 1:** Apply all three files.
- [x] **Step 2:** Compile gate + full suite. Commit: `feat(composer): the mic icon follows the voice mode`.

### Task 4: Evidence, CI, combined rebuild, APK

**Files:**
- Modify: `android-native/docs/plans/2026-10-17-attach-panel-restored-tiles.md` (tick boxes), `.superpowers/sdd/2026-10-17-attach-panel-restored-tiles/progress.md`, `android-native/docs/HANDOFF-model-picker.md`.

- [x] **Step 1:** Full local run with `--rerun`; record test-case counts from `app/build/test-results/`.
- [x] **Step 2:** Push `feat/model-picker`; watch CI; fetch the APK; record run URL + md5.
- [x] **Step 3:** Merge the new feat head into `test/model-picker-home-send` (base `3e153a1`; expected conflict only where the mic line meets the state-driven send union); compile + 112 tests; push; CI green; fetch APK; md5.
- [x] **Step 4:** Refresh the handoff (§9 rules: tiles back in their own card — no list rows, no handle; composer and panel still separate; mic icon follows the mode live; keyboard swap unchanged).
- [x] **Step 5:** Tick boxes and ledgers. No merge to master, no release until the user says so.
