# Chat model picker — Design

Date: 2026-10-16
Status: Awaiting approval. No release this cycle.
Surface: `android-native` (hermes-native). No server changes — every contract used already exists.

## 1. Intent

Give the chat screen a model picker with full parity to the web UI popup:

- A **model chip in the composer** that always shows the active model (with provider icon). Tapping it opens the picker.
- An **expanded modal bottom sheet**: search, custom model ID, CONFIGURED section with SELECTED badge, portal-grouped catalog with counts, provider icons on every row.
- Picking a model **applies to the current conversation** (persisted server-side via `POST /api/session/update`) and is **remembered as the default for new chats**.
- The **"Hermes" wordmark in app headers** is replaced by the Hermes Agent logo.
- Picker and touched chat surfaces get an **M3 expressive treatment** within stable APIs only.

Decisions from Q&A (2026-10-16): chip entry point; expanded bottom sheet; conversation + remembered default; full web parity content; brand-color PNG icons cached on device; Hermes Agent logo in the header where "Hermes" is written; expressive scope = picker + chat surfaces.

## 2. What already exists (verified in code)

So the feature is mostly UI + wiring, not new plumbing:

| Piece | Where | State |
|---|---|---|
| `ChatStartRequest.model/modelProvider/explicitModelPick` | `core/model/Wire.kt` L106–116 | Fields exist; `ChatRepository.startTurn()` does not pass them |
| `SessionDetail.model/modelProvider` | `Wire.kt` L92–93 | Parsed; `ChatViewModel` open sets `state.model = detail.model ?: state.model` |
| `state.model` live updates | `ChatViewModel` ← `ChatEvent.RuntimeModel`; shown as top-bar subtitle | Works |
| `SettingsStore` model keys + `setModel()` + `val model: Flow<Pair<String?,String?>>` | `data/settings/SettingsStore.kt` L39–40, 57–59, 73–76 | Exist; nothing calls them yet |
| `GET /api/models` + `ModelsResponse` | `HermesApi.models()` → `SessionRepository.models()` | Endpoint works; response misses `groups`/`configured_model_badges`/`extra_models`; no UI consumes it |
| `POST /api/session/update` | server `api/routes.py` ~L17695 | Not called by the app anywhere |

Missing: picker UI, chip, icon pipeline, session/update call, model in startTurn, group DTOs, header logo.

## 3. Server contracts (verified)

- `GET /api/models` returns: `models[]`, `extra_models[]`, `default_model`, `configured_model_badges` (map keyed by model id → `{provider, role: "primary"|"fallback", label}`), `groups[]` (`{provider: <display label>, provider_id, models[], extra_models[]}` — server pre-sorts: active provider first, then custom, then keyed providers, then the rest), plus `active_provider` and `aliases`. Model entries carry `id` (and label-ish fields); mutating fields (`supports_fast_tier`, etc.) must be ignored tolerantly. The app's DTOs are additive-optional by convention — unknown keys are ignored.
- `POST /api/session/update` `{session_id, workspace?, model, model_provider}` → applies from the next message. Web toast: "Applies to this conversation from your next message." Response shape not relied upon (treat 2xx as success).
- `POST /api/chat/start` already accepts `model`, `model_provider`, `explicit_model_pick`. Per `static/messages.js` L1823: without the explicit marker the server "repairs" the model back to the session default — i.e. the flag is *evidence of a deliberate user pick*, not a general override.
- Session detail (`GET /api/session/get`) includes `session.model` / `session.model_provider` when set.

## 4. UI design

### 4.1 Composer chip

Current composer row: `[attach] [tune] … [send]`. New first slot: a model chip.

```
┌──────────────────────────────────────────────┐
│  [ ( ) Solar Mini4:free  ▾ ]  📎  🎛        ➤ │
└──────────────────────────────────────────────┘
```

- AssistChip-style pill, 32dp tall: `[provider icon 18dp] [label] [expand_more 16dp]`.
- Label: short model name (current effective model); ellipsized, maxLines 1, width-capped (~160dp).
- Accessibility: contentDescription "Model and options", stateDescription = full model id.
- `Composer` stays stateless: gains a `leadingAccessory: (@Composable () -> Unit)?` slot; `ChatScreen` passes the chip.

### 4.2 Picker sheet

`ModalBottomSheet`, `skipPartiallyExpanded = true`, drag handle, spring motion from `Motion.kt`. No autofocus (don't force the keyboard to open).

```
│              ────────                        │  drag handle
│  Model                                       │  headline label
│  Applies to this conversation from your      │  scope note (bodySmall, onSurfaceVariant)
│  next message.                               │
│  ┌───────────────────────[ 🔍][ × ]────────┐ │  search (filters everything below)
│  ┌───────────────────────────[ ＋]─────────┐ │  custom model ID field + add button
│                                              │
│  CONFIGURED                                  │  section label (labelMedium, onSurfaceVariant)
│  ◉ (🔵) GPT-5.4 Mini        [PRIMARY] [SELECTED] │  selected row: secondaryContainer, radio checked
│  ○ (🟣) Claude Sonnet       [FALLBACK]       │
│                                              │
│  ▸ NOUS PORTAL (33)                          │  collapsible group header: chevron + label + count
│  ▾ OPENROUTER (120)                          │
│    ○ (🟢) Solar Mini4:free                   │  row: icon, label, id line
│         openrouter/solar-mini4:free          │
│    ○ …                                       │
│    ▸ MORE MODELS (18)                        │  extra_models as an inner collapsible
└──────────────────────────────────────────────┘
```

- **Search**: case-insensitive substring over label, id, and provider label; sections with no matches drop out; "No models match" empty state.
- **Custom model ID**: OutlinedTextField + trailing add IconButton (enabled when non-blank and not an existing id). Adding selects it immediately and it persists as a row under CONFIGURED labeled `CUSTOM`.
- **Configured rows**: from `configured_model_badges` (primary + fallbacks), rendered with the same identity/dedupe rules as the web (`static/ui.js` ~L5225–5258): the server emits up to three spellings of the same model (bare id, `@provider:id`, matched catalog spelling) — dedupe on a normalized `provider::id` key and prefer the unqualified spelling for display and selection; configured models are **hoisted out of group bodies** with group counts recounted. `label` renders as the badge; SELECTED matches on the same normalized identity.
- **Group rows**: icon + label + id (bodySmall, onSurfaceVariant); selection = radio + tonal container, whole row `selectable`.
- **States**: loading (linear progress under the header while refreshing with stale data visible), hard error (message + Retry), empty search, offline icon placeholders (monogram). **Older servers**: if `groups` is absent/empty but `models[]` is present → one flat `MODELS (N)` section; badges without groups render the CONFIGURED section above the flat list. Never an empty-looking sheet on a server that predates this feature.
- **Data loading**: the catalog loads when a chat opens (`ChatViewModel` open — this arms both the chip and the cross-provider send guard, §6) and is cached in the ViewModel; chip tap renders the cache instantly and refreshes in the background (deduped, one in-flight fetch).
- **Selection side effects on row tap**: sheet stays open until the user picks or dismisses; picking closes the sheet (web parity: select then act).

### 4.3 Header branding (the "Hermes" replacement)

- `HomeScreen` top bar L125–131: `Text("Hermes", titleLarge, primary)` → Hermes Agent mark, ~28dp, tinted `colorScheme.primary` (same visual weight; contentDescription "Hermes").
- `ConnectScreen` L96–100: hero `Text("Hermes", displaySmall, primary)` → mark ~48dp tinted primary, centered. *(Flag in review if this one should stay text.)*
- The mark is a **bundled vector drawable** converted from LobeHub's `hermesagent` SVG (single path, 24×24 viewBox) — headers must never depend on the network. Same mark is used for Hermes-owned entries in the picker if the loader maps them.

## 5. Icon pipeline

- **Source**: LobeHub `lobe-icons` via jsdelivr, pinned to tag `v6.0.0`:
  `https://cdn.jsdelivr.net/gh/lobehub/lobe-icons@v6.0.0/packages/static-png/{light|dark}/<id>.png`
  (brand-color PNGs, ~11–18KB; `light`/`dark` = glyph color for the matching theme. Verified: openai/anthropic/google/gemini/deepseek/mistral/xai/grok/qwen/moonshot/zhipu/minimax/upstage/openrouter/groq/ollama/together/cohere/perplexity/meta/nvidia/nousresearch/hermesagent all exist.)
- **Icon resolution** (`core/icons/ProviderIcons.resolve(modelId, providerId)`), precedence: (1) vendor parsed from the model id (`vendor/rest`, `@portal:vendor/model` → `openai`, `anthropic`, …) when it resolves to a known icon; (2) the owning group/badge provider id (portal logo, e.g. `openrouter`, `nousresearch`); (3) monogram (first letter, tonal circle, deterministic container role by hash). The web popup uses a generic glyph only, so there is no parity ground truth here — this rule is ours. Alias table covers provider-id fixes (`nous→nousresearch`, `hermes→hermesagent`) and vendor tokens (`gpt→openai`, `claude→anthropic`, `llama→meta`, …).
- **Loader** (`core/icons/IconLoader.kt`): OkHttp (existing client) + `BitmapFactory` with `inSampleSize` target ~96px; memory `LruCache` (~64 entries) over a disk cache (`context.cacheDir/provider-icons/{light|dark}/`); in-flight dedupe (single Mutex + map); every failure path ends at the monogram — no crash, no toast. No new dependencies (Coil is deliberately absent; see AGENTS.md).
- **Theme variant**: `Theme.kt` provides `LocalProviderIconVariant` (or equivalent CompositionLocal set from the resolved `ThemeMode`, not raw system state) so the loader picks `light`/`dark` glyphs.
- The Hermes Agent logo in rows/fallbacks uses the same pipeline (id `hermesagent`); only headers use the bundled vector.

## 6. Behavior spec

Effective model resolution (highest wins):
1. Pick made in this chat (in-memory).
2. `SessionDetail.model` (server has session truth).
3. `SettingsStore.model` (remembered pick).
4. `default_model` from `/api/models` (display only until fetch succeeds).

Actions:

- **Pick in picker** → update chip immediately; `SettingsStore.setModel(model, provider)`; if a session is open, `POST /api/session/update` (background; failure = snackbar "Couldn't save model for this conversation — it will still apply to your next message here.", local state kept); mark `explicitPickThisChat = true`; snackbar "Applies to this conversation from your next message."
- **Open a chat** → resolve effective model per above. If `detail.model == null` and a remembered pick exists, fire one `POST /api/session/update` so the server session follows the remembered default (same write the web's picker does; failure is non-blocking). Sessions that already have a model are never overridden.
- **Send** → `startTurn` sends `model` + `model_provider` = effective selection (when known). `explicit_model_pick` ports the web rule (`static/messages.js` L1823): `true` when the user picked in this chat **or** the effective model crosses the profile default's provider/family (`default_model`/`active_provider` from the catalog) — the server silently repairs non-explicit cross-provider models back to the profile default (`routes.py` `_resolve_compatible_session_model_state`), so the guard is recomputed on every send rather than held as an in-memory one-shot that dies with a recreated ViewModel. The catalog fetch on chat open arms it. Belt-and-braces: correct even if the session/update write failed.
- **Session list**: rows already render `row.model` server-side; refreshed on return to home — no extra work.

## 7. Expressive theme (this round: picker + chat surfaces)

Material3 1.4.0 keeps `MaterialExpressiveTheme`/`MotionScheme` internal (AGENTS.md), so expressive *feel* comes from stable APIs:

- Springs from `Motion.kt` for sheet content, group expand/collapse (`animateContentSize`), chip crossfade on pick.
- Shape scale: `shapes.extraLarge` sheet corners, `shapes.large` selected-row containers.
- Tonal layering: selected rows `secondaryContainer`, icons on tonal circles, badges on `tertiaryContainer`-family roles.
- Typography weight emphasis on section labels/selected rows (w600 labels), not new type styles.
- Colors only from `MaterialTheme.colorScheme` (design rule); any new literal lives in `Color.kt`.

## 8. Files touched

- `core/model/Wire.kt` — additive DTOs: group entries, configured badges, `extra_models`, `active_provider`; `SessionUpdateRequest`.
- `core/net/HermesApi.kt` — `updateSession()`.
- `data/chat/ChatRepository.kt` — `startTurn` gains model/provider/explicit params; `updateSession()` passthrough.
- `ui/chat/ChatViewModel.kt` — picker state (catalog cache, loading/error, effective selection), `pickModel()`, `loadPickerModels()`, session/update call, snackbar texts.
- `ui/chat/ChatScreen.kt` — chip wiring, sheet host, logo untouched (title is session text).
- `ui/chat/components/Composer.kt` — `leadingAccessory` slot.
- New: `ui/chat/components/ModelChip.kt`, `ui/chat/components/ModelPickerSheet.kt`, `core/icons/ProviderIcons.kt`, `core/icons/IconLoader.kt`, `ui/components/ProviderIcon.kt`.
- `ui/theme/Theme.kt` — icon-variant CompositionLocal.
- `ui/home/HomeScreen.kt`, `ui/connect/ConnectScreen.kt` — logo.
- New: `res/drawable/ic_hermes_agent.xml` (converted vector).
- `data/settings/SettingsStore.kt` — no change (keys exist).

## 9. Tests & evidence

- Unit: `ModelsResponse` parsing from a **real `/api/models` payload fixture** (groups, badges with multi-spelling entries, extra_models); configured-row identity/dedupe (three spellings → one row; SELECTED matches either spelling; groups recounted); icon resolution precedence (vendor → provider → monogram) incl. `nous→nousresearch`, `llama→meta`, unknown; icon URL builder (tag pinning, variant); `SettingsStore.setModel` roundtrip; effective-model resolution (pick > session > remembered > default); cross-provider explicit guard (matches the web rule); `ChatStartRequest` serialization with model/explicit flag; `session/update` request body; search filter matching.
- CI is the source of truth (`.github/workflows/android.yml`); debug APK artifact.
- Device verification (user): chip shows active model; sheet opens fast; search filters; pick applies, survives leaving/reopening the chat; new chat starts on remembered model; icons render, monogram in airplane mode; logo in Home header (+ Connect if approved).
- No release this cycle; work happens on a feature branch, merged only after device confirmation.

## 10. Non-goals

- Reasoning-effort picker (SettingsStore key exists; separate feature).
- Picker on the home composer.
- Whole-app retheme.
- Server changes; icon bundling for all providers; SVG rendering dependency.

## 11. Risks

- **jsdelivr availability/renames** → pinned tag, disk cache, monogram fallback; icons are decoration, never load-bearing.
- **session/update failure** → local pick still rides the next `startTurn`; snackbar is honest about it.
- **Model ids in payloads mutate** (fast-tier annotations etc.) → tolerant DTOs, additive fields only.
- **Over-eager session writes** → exactly one write per open-pick-on-modelless-session and one per user pick; never on plain opens that already have a model.

## 12. Open items for implementation plan

- Exact `workspace` value for session/update (from `SessionDetail` if present, else omit).
- `extra_models` presentation detail (verify against `static/ui.js` before building).
- Custom-id row persistence within a picker session vs device (default: device, via the same remember path).

## 13. Round 2 amendments (device feedback, 2026-10-16)

Device pass on the Task 10 APK changed six things; this section overrides conflicting earlier text (§4.1 chip drawing, §4.3 headers, and "Picker on the home composer" in §10 Non-goals):

1. **The home composer ships the picker.** The home composer gains the chip + the same sheet. A pick there updates the remembered default and closes the sheet; no snackbar on home (its effect plumbing lands with the pending `fix/home-send` branch). The sheet scope note is parameterized: chat = "Applies to this conversation from your next message.", home = "Applies to new conversations.".
2. **New sessions carry the pick (web parity).** `POST /api/session/new` accepts `model` + `model_provider` and stores them on the session (`static/sessions.js` L2139; `api/routes.py` new-session path). `SessionRepository.create(model, provider)` sends the home screen's current effective selection, so a chat created from home opens already on the pick — no open-time override ("seeded session" case) is needed.
3. **Chip redesign.** Filled pill (`surfaceContainerHigh`, `shapes.extraLarge`): label + thin chevron-down; no border, no leading provider icon (per the reference image). Provider icons remain on the sheet rows. *(The chevron was removed in Round 3 — see §14.1.)*
4. **Effective selection is displayed.** `displaySelection(selection, catalog)` — the session/remembered pick, else the catalog default — feeds both the chip label and the sheet's SELECTED marking, so a fresh chat shows the model it will actually run on.
5. **Tune icon removed; composer position parity.** The composer's `onOpenSettings`/Tune icon is gone (settings remain in both top bars). The chat composer adopts the home inset treatment (`safeDrawing` bottom + 12dp), dropping the composer-internal `imePadding()`. `ChatViewModel.loadModels()` becomes idempotent and `ModelPickerUiState` moves to `domain/modelpicker/` for reuse by `SessionsViewModel`.
6. **Header mark is the `HermesAgent.Combine` lockup.** `ic_hermes_agent.xml` is rebuilt from the pinned `hermesagent-text.svg` (one path, viewBox 52×24): face + "HERMES AGENT" wordmark, replacing the 3-path square mark in Home and Connect.

## 14. Round 3 amendments (device feedback, 2026-10-17)

Device pass on the Task 13 APK changed four things; this section overrides
conflicting earlier text (§4.1 chip drawing, §13 item 3's chevron), adds the
composer's attachment model, and records two accepted review divergences.

1. **The chip is a label, not a control.** The chevron-down is gone: on device
   it read as a second control beside the label, and the user asked for it
   removed. The pill and its tap target carry the affordance; the selection
   stays exposed to accessibility as `stateDescription`
   (`selection?.model ?: "default"`).
2. **The lockup drawable is transform-free.** `ic_hermes_agent.xml` stores
   absolute path data — no `<group>`/transform nodes. The grouped form is
   faithful (Compose composes `M = T·R·S` exactly as the SVG does; verified by
   rendering both side by side), but flat paths take the renderer out of the
   equation at zero rendering cost. The home lockup renders at 32dp (was
   28dp) for legibility; Connect keeps 48dp.
3. **Attachments ride the message from either composer.** The row is now
   `[attach][chip] … [send]` (a mic, once its processing decision lands, sits
   before send). Uploads use `POST /api/upload` — multipart `session_id` +
   `file`, the web client's own shape (`static/ui.js`) — and its response
   (`filename`, `path`, `mime`, `size`, `is_image`) is exactly the attachment
   object `/api/chat/start` accepts. Because uploads are session-scoped, the
   home flow is create → upload → navigate (refs travel in the route as JSON;
   a failure lands on the composer still holding the picks) and the chat flow
   uploads at send time (a failure keeps the picks; a completed upload is
   remembered on the staged item so a retry never re-uploads). The Photo
   Picker keeps the flow permission-free; 10 files per message; the client
   refuses files over the web client's own 20 MB cap.
4. **Accepted divergences (review M3/M5).**
   - The icon loader's failed-id set is per process by design (pinned by
     `ProviderIconLoaderTest`): icons that fail while offline return on the
     next app launch, not on reconnect. The handoff checklist says exactly
     that.
   - The catalog's configured-section dedupe picks its survivor by display
     priority only, while the web's `_deduplicateModelPickerOptions(sel,
     sel.value)` keeps the currently selected spelling. A session stored under
     a deduped-away spelling can therefore miss the SELECTED mark in the
     sheet. Selection-aware dedupe would rebuild catalog rows per selection
     for a cosmetic mark; accepted, to revisit if it shows up on a device.
