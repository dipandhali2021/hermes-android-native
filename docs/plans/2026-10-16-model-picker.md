# Chat Model Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a model picker to the chat screen — composer chip, expanded bottom-sheet with full web parity (search, custom ID, CONFIGURED + portal groups), LobeHub provider icons, per-conversation persistence plus remembered new-chat default, and the Hermes Agent mark replacing the "Hermes" wordmark in headers.

**Architecture:** Port the web's model-identity rules (normalization, configured dedupe/hoist, cross-provider explicit-pick guard) as pure Kotlin functions with fixture tests, then wire them through the existing wire/repository/ViewModel layers. Icons load from a pinned jsdelivr URL through a hand-rolled OkHttp + LruCache + disk-cache loader (no new dependencies). The sheet and chip are the composable surface; all state lives in `ChatViewModel`.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2026.06.01, material3 1.4.0), Hilt, kotlinx.serialization, OkHttp 5.3.0, DataStore, JUnit4 + MockWebServer + Robolectric + Turbine.

**Spec:** `android-native/docs/specs/2026-10-16-model-picker-design.md` (read it before starting; the plan argues from it).

## Global Constraints

- **No new dependencies.** Coil is deliberately absent (kotlin-stdlib 2.4.0 conflict) and `androidsvg` is not allowed. Use OkHttp + `BitmapFactory` + `android.util.LruCache`.
- **No version/toolchain bumps** (Kotlin 2.2.20, AGP 8.13.2, compileSdk 36, Gradle 8.14.3). See `android-native/AGENTS.md`.
- **material3 1.4.0:** `MaterialExpressiveTheme`/`MotionScheme` are `internal` — do not reference them. Motion comes from `ui/theme/Motion.kt` (`HermesMotion`); durations/springs never raw. Colors only from `MaterialTheme.colorScheme`; literals only in `ui/theme/Color.kt`.
- **Wire tolerance:** every new DTO field optional with a default; unknown JSON keys ignored (the `Json { ignoreUnknownKeys = true }` in `HermesApi` already achieves this).
- **Never send `Origin`, `Referer`, `Sec-Fetch-*`** — reuse the existing `@Named("api")` OkHttpClient; do not build custom headers.
- **Exact copy strings:**
  - Pick toast/snackbar: `Applies to this conversation from your next message.`
  - Session-update failure snackbar: `Couldn't save model for this conversation — it will still apply to your next message here.`
  - Scope note in sheet: `Applies to this conversation from your next message.`
- **Icon CDN (pinned):** `https://cdn.jsdelivr.net/gh/lobehub/lobe-icons@v6.0.0/packages/static-png/{light|dark}/<id>.png` — verified live for openai, anthropic, google, gemini, deepseek, mistral, xai, grok, qwen, moonshot, zhipu, zai, minimax, upstage, openrouter, groq, ollama, together, cohere, perplexity, meta, nvidia, nousresearch, hermesagent.
- **CI is the source of truth** (`.github/workflows/android.yml` runs on any branch push: unit tests + debug APK). Local test runs need JDK 17 + Android SDK 36; if unavailable, push and use the workflow run as the verification record. No release this cycle; no CHANGELOG edits.
- Work on branch `feat/model-picker` inside `android-native/`.

## Review Focus

Spec-implied failure modes a reasonable user would hit; each gets its test pinned in the owning task.

1. **An older server without `groups`/`configured_model_badges`** must still render a usable flat list, not an empty-looking sheet (Task 3).
2. **A pick made before the catalog has ever loaded (or after a failed fetch)** must still be sent as an explicit pick — the guard cannot depend on catalog presence (Task 4).
3. **`/api/session/update` failing (offline)** must keep the local pick and still ride the next send (Task 8).
4. **Real payloads carry 2–3 spellings per configured model** — duplicates must collapse and SELECTED must match whichever spelling the server stored (Tasks 2, 3).
5. **Icon fetch failure/404/unknown id** must end in the monogram, never a crash or permanent blank (Task 5).

---

### Task 1: Wire DTOs, `session/update` endpoint, repository methods

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/core/model/Wire.kt` (around `ModelsResponse`, L149–163)
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/core/net/HermesApi.kt` (near `models()` L116)
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/data/sessions/SessionRepository.kt` (L61–66)
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/sessions/SessionRepositoryModelsTest.kt`

**Interfaces:**
- Produces (used by Tasks 3, 4, 7, 8):
  - `ModelsResponse(models: List<ModelDto>, defaultModel: String? = null, activeProvider: String? = null, configuredModelBadges: Map<String, ConfiguredBadgeDto> = emptyMap(), groups: List<ModelGroupDto> = emptyList(), extraModels: List<ModelDto> = emptyList())`
  - `ModelDto(id, label: String? , name: String?, provider: String?, supportedEfforts: List<String>?)` with `val displayLabel: String` (`label ?: name ?: id ?: "Unknown model"`) — replaces the current `label` getter.
  - `ConfiguredBadgeDto(provider: String? = null, role: String? = null, label: String? = null)`
  - `ModelGroupDto(provider: String? = null, providerId: String? = null, models: List<ModelDto> = emptyList(), extraModels: List<ModelDto> = emptyList(), modelsEndpointError: JsonElement? = null)` — the server's `models_endpoint_error` can be a string **or** an object, so it must be decoded as `JsonElement?`; otherwise one failing provider endpoint would fail the whole response decode. The catalog builder converts it to a display string.
  - `SessionUpdateRequest(sessionId: String, model: String, modelProvider: String? = null)` with `@SerialName` keys `session_id`, `model`, `model_provider`.
  - `HermesApi.updateSession(baseUrl, sessionId, model, modelProvider): Result<Unit>` (`postRaw`, path `api/session/update`).
  - `SessionRepository.models(): Result<ModelsResponse>` (was `Result<List<ModelDto>>`; no UI callers exist yet).
  - `SessionRepository.updateSession(sessionId: String, model: String, provider: String?): Result<Unit>`.

- [ ] **Step 1: Create the branch and commit the design docs**

`master` is at `58a82a0` (== `origin/master`); the `fix/home-send` branch is separate and merges later — branch from `master`.

```bash
cd android-native && git checkout master && git checkout -b feat/model-picker
git add docs/specs/2026-10-16-model-picker-design.md docs/plans/2026-10-16-model-picker.md
git commit -m "docs(model-picker): design and implementation plan"
```

- [ ] **Step 2: Write the failing test** — `SessionRepositoryModelsTest.kt`, two tests against MockWebServer (pattern: `SessionRepositoryCreateTest.kt`):

```kotlin
private val PAYLOAD = """
{
  "active_provider": "nous",
  "default_model": "solar-mini4:free",
  "configured_model_badges": {"solar-mini4:free": {"role": "primary", "label": "Primary", "provider": "nous"}},
  "groups": [
    {"provider": "Nous (1 of 2)", "provider_id": "nous",
     "models": [{"id": "@nous:solar-mini4:free", "label": "Solar Mini4 (free)"}]},
    {"provider": "OpenRouter", "provider_id": "openrouter",
     "models": [{"id": "openrouter/solar-pro4", "label": "Solar Pro4"}]}
  ]
}
"""

@Test fun `models maps groups and badges from a real payload`() = runTest {
    server.enqueue(MockResponse.Builder().code(200).body(PAYLOAD).build())
    val response = repository().models().getOrThrow()
    assertEquals("/api/models", server.takeRequest().path)
    assertEquals("nous", response.activeProvider)
    assertEquals("solar-mini4:free", response.defaultModel)
    assertEquals(2, response.groups.size)
    assertEquals("nous", response.configuredModelBadges["solar-mini4:free"]?.provider)
}

@Test fun `updateSession posts model and provider`() = runTest {
    server.enqueue(MockResponse.Builder().code(200).body("""{"session":{}}""").build())
    val result = repository().updateSession("sess-1", "@nous:solar-mini4:free", "nous")
    assertTrue(result.isSuccess)
    val body = server.takeRequest().body?.utf8() ?: ""
    assertTrue(body.contains(""""session_id":"sess-1""""))
    assertTrue(body.contains(""""model":"@nous:solar-mini4:free""""))
    assertTrue(body.contains(""""model_provider":"nous""""))
}
```

- [ ] **Step 3: Run to verify failure**

Run: `cd android-native && ./gradlew :app:testDebugUnitTest --tests "com.dipandhali.hermesnative.sessions.SessionRepositoryModelsTest"`
Expected: FAIL — `models()` returns `List<ModelDto>`, `updateSession` unresolved.

(If no local JDK 17 + SDK 36: push the branch and read the `android.yml` run — a red unit-test job is the equivalent RED record.)

- [ ] **Step 4: Implement** — extend `Wire.kt` per the Interfaces block; add `updateSession` to `HermesApi` (model it on `renameSession`, using `json.encodeToString(SessionUpdateRequest.serializer(), ...)`); update `SessionRepository` (`models()` returns the whole response; `updateSession` delegates). Also update the existing `ModelDto` `label` getter usage (grep for `.label` on ModelDto — the only consumer is the getter itself; rename it `displayLabel`).

- [ ] **Step 5: Run to verify pass** — same command as Step 3. Expected: PASS. (Also run the full existing suite: `./gradlew :app:testDebugUnitTest` — nothing else may regress.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dipandhali/hermesnative/core/model/Wire.kt \
        app/src/main/java/com/dipandhali/hermesnative/core/net/HermesApi.kt \
        app/src/main/java/com/dipandhali/hermesnative/data/sessions/SessionRepository.kt \
        app/src/test/java/com/dipandhali/hermesnative/sessions/SessionRepositoryModelsTest.kt
git commit -m "feat(model-picker): wire group/badge DTOs and session/update"
```

---

### Task 2: Model identity port (pure)

**Files:**
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/domain/modelpicker/ModelIdentity.kt`
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/modelpicker/ModelIdentityTest.kt`

**Interfaces:**
- Produces (used by Tasks 3, 4, 9):
  - `object ModelIdentity`
  - `fun normalizeConfiguredKey(modelId: String): String`
  - `fun providerFromModelValue(value: String): String?`
  - `fun optionIdentity(modelId: String, providerId: String?): String`
- These are direct ports of `static/ui.js`: `_normalizeConfiguredModelKey` (L4426–4458), `_providerFromModelValue` (L3363–3385), `_modelPickerOptionIdentity` (L3389–3411). Read those functions before implementing; the test cases below encode their observed behavior.

- [ ] **Step 1: Write the failing tests** — exact cases:

```kotlin
@Test fun `normalize strips at-provider, first segment, and dashes`() {
    assertEquals("solar.mini4:free", ModelIdentity.normalizeConfiguredKey("solar-mini4:free"))
    assertEquals("gpt.5.4.mini", ModelIdentity.normalizeConfiguredKey("nous/gpt-5.4-mini"))
    assertEquals("gpt.5.4.mini", ModelIdentity.normalizeConfiguredKey("@nous:gpt-5.4-mini"))
    assertEquals("gpt.5.4.mini", ModelIdentity.normalizeConfiguredKey("@nous:openai/gpt-5.4-mini"))
    // URI-scheme ids keep their slashes (#3429).
    assertEquals("gpt://folder/model", ModelIdentity.normalizeConfiguredKey("gpt://folder/model"))
    // Colon-qualified prefix before the first slash is stripped (#3360).
    assertEquals("deepseek.v4.pro", ModelIdentity.normalizeConfiguredKey("custom:llm-proxy/opencode-go/deepseek-v4-pro"))
}

@Test fun `providerFromModelValue parses at-prefixes and custom host ports`() {
    assertEquals("nous", ModelIdentity.providerFromModelValue("@nous:solar-mini4:free"))
    assertNull(ModelIdentity.providerFromModelValue("openai/gpt-5.4-mini"))
    assertEquals("custom:my-proxy", ModelIdentity.providerFromModelValue("@custom:my-proxy:model-a"))
    assertEquals("custom:localhost:11434", ModelIdentity.providerFromModelValue("@custom:localhost:11434:llama3"))
}

@Test fun `optionIdentity matches the at-spelling against the bare spelling`() {
    assertEquals(
        ModelIdentity.optionIdentity("@nous:solar-mini4:free", "nous"),
        ModelIdentity.optionIdentity("solar-mini4:free", "nous"),
    )
    assertEquals(
        ModelIdentity.optionIdentity("@openai:gpt-5.4-mini", "openai"),
        ModelIdentity.optionIdentity("gpt-5.4-mini", "openai"),
    )
    // Note: a "<provider>/<model>" spelling is NOT collapsed by this helper
    // (ui.js only strips '@provider:' here); dedupe handles that spelling pair
    // via normalizeConfiguredKey in Task 3.
    assertNotEquals(
        ModelIdentity.optionIdentity("nous/solar-mini4:free", "nous"),
        ModelIdentity.optionIdentity("@nous:solar-mini4:free", "nous"),
    )
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dipandhali.hermesnative.modelpicker.ModelIdentityTest"`
Expected: FAIL — `ModelIdentity` unresolved.

- [ ] **Step 3: Implement** `ModelIdentity.kt` as Kotlin ports of the three ui.js functions (same branch order, same special cases: `@custom:` host:port split, URI-scheme skip, `-`→`.`). Keep the port's comments short and reference the ui.js line numbers.

- [ ] **Step 4: Run to verify pass** — same command. Expected: PASS.

- [ ] **Step 5: Commit** — `feat(model-picker): port web model-identity rules`

---

### Task 3: Picker catalog build (pure, fixture-tested)

**Files:**
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/domain/modelpicker/PickerModel.kt`
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/modelpicker/PickerCatalogTest.kt`
- Test fixture: `android-native/app/src/test/java/com/dipandhali/hermesnative/modelpicker/ModelsFixtures.kt`

**Interfaces:**
- Produces (used by Tasks 4, 8, 9):
  - `data class ConfiguredBadge(provider: String? = null, role: String? = null, label: String? = null)`
  - `data class ModelSelection(val model: String, val provider: String? = null)`
  - `data class ModelRow(model: String, displayName: String, providerId: String?, badgeRole: String? = null, badgeLabel: String? = null)`
  - `data class ModelGroup(key: String, label: String, rows: List<ModelRow>, hidden: List<ModelRow>, endpointError: String? = null)`
  - `data class PickerCatalog(configured: List<ModelRow>, groups: List<ModelGroup>, flat: List<ModelRow>, defaultModel: String?, activeProvider: String?)`
  - `object PickerCatalogBuilder { fun build(response: ModelsResponse): PickerCatalog }`
  - `fun configuredBadgeFor(badges: Map<String, ConfiguredBadge>, value: String, providerId: String?): Pair<String, ConfiguredBadge>?` (rawKey to badge — port of `_getConfiguredModelBadge` L4511–4531)
- Behavior comes from `static/ui.js`: badge map keys → rows (`_modelData` build L4903–4998), configured dedupe + display priority (`_configuredProviderKey/_configuredModelKey/_configuredDisplayPriority`, L5225–5258), ranking (`_configuredRank`, L5022–5030), hoisting + recounted headings (L5260–5330), and badge-only additions (L4990–4998).

**Rules the tests pin:**
- Badge lookup: exact map key first (respecting provider when both sides have one); else normalized-key matches; if provider given and multiple normalized matches without a provider match → no badge; if exactly one normalized match → that badge.
- Configured section: rows = all rows (grouped + badge-only appended) with a badge; dedupe by `providerLower(badge.provider ?: providerFromModelValue(value) ?: "") + "::" + normalizeConfiguredKey(value)`; on collision keep the highest display priority (`@`-prefixed=0, contains `/`=1, bare=2) — i.e. prefer the bare spelling; sort by badge rank (primary=0, `Fallback N`→N, other fallback=999, other=500) then displayName.
- `badgeLabel` = badge map raw key + `" (" + providerShort + ")"` where `providerShort = provider.removePrefix("custom:").split("/").first()` (web shows the config ID + provider, L5274–5285).
- Group bodies exclude configured winners by model value; hidden (`extra_models`) lists exclude them by configured semantic key; group label = server label with a trailing `"(N of M)"` stripped; group rows keep their own badge when `configuredBadgeFor` matches.
- `hidden` = `extra_models` rows converted with `hiddenByDefault`, each also badge-matched.
- Badge-only additions (badge map entries whose spelling is equivalent to no existing row) append rows with `providerId = badge.provider` (web `selectFromDropdown` falls back to the badge provider, L5290).
- No `groups` (or all empty) but `models` present → `flat` = models + extraModels as rows, groups empty, configured built from badges alone (badge-only additions). **This is the older-server path (Review Focus #1).**
- `models_endpoint_error` on a group → `endpointError` display string on `ModelGroup` (from the `JsonElement`: a `JsonPrimitive` string is used verbatim, any other non-null shape becomes `"Models endpoint unreachable"`); group still listed.

- [ ] **Step 1: Write the fixture** — `ModelsFixtures.kt` with `val MODELS_JSON = """ ... """` exactly:

```json
{
  "active_provider": "nous",
  "default_model": "solar-mini4:free",
  "configured_model_badges": {
    "solar-mini4:free": {"role": "primary", "label": "Primary", "provider": "nous"},
    "@nous:solar-mini4:free": {"role": "primary", "label": "Primary", "provider": "nous"},
    "nous/solar-mini4:free": {"role": "primary", "label": "Primary", "provider": "nous"},
    "openai/gpt-5.4": {"role": "fallback", "label": "Fallback 1", "provider": "openai"}
  },
  "groups": [
    {"provider": "Nous (1 of 3)", "provider_id": "nous",
     "models": [{"id": "@nous:solar-mini4:free", "label": "Solar Mini4 (free)"}],
     "extra_models": [{"id": "@nous:anthropic/claude-sonnet-4.6", "label": "Claude Sonnet 4.6"},
                      {"id": "@nous:moonshot/kimi-k2.5", "label": "Kimi K2.5"}]},
    {"provider": "OpenRouter", "provider_id": "openrouter",
     "models": [{"id": "openrouter/solar-pro4", "label": "Solar Pro4"}]}
  ],
  "models": [], "extra_models": []
}
```

- [ ] **Step 2: Write the failing tests** — `PickerCatalogTest.kt`:

```kotlin
@Test fun `three badge spellings collapse to one configured row`() {
    val catalog = PickerCatalogBuilder.build(json.decodeFromString(MODELS_JSON))
    assertEquals(2, catalog.configured.size)
    val solar = catalog.configured.first()
    assertEquals("@nous:solar-mini4:free", solar.model)      // the row's own spelling wins
    assertEquals("nous", solar.providerId)
    assertEquals("@nous:solar-mini4:free (nous)", solar.badgeLabel)
    assertEquals("Primary", solar.badgeRole)
    assertEquals("openai/gpt-5.4", catalog.configured[1].model)  // badge-only addition
    assertEquals("Fallback 1", catalog.configured[1].badgeRole)
}

@Test fun `configured models are hoisted out of groups`() {
    val catalog = PickerCatalogBuilder.build(...)
    val nous = catalog.groups.first()
    assertEquals("Nous", nous.label)                          // "(1 of 3)" stripped
    assertTrue(nous.rows.isEmpty())                           // solar hoisted
    assertEquals(listOf("Claude Sonnet 4.6", "Kimi K2.5"), nous.hidden.map { it.displayName })
    assertEquals(1, catalog.groups[1].rows.size)              // openrouter row survives
}

@Test fun `payload without groups yields a flat catalog`() {
    val catalog = PickerCatalogBuilder.build(json.decodeFromString("""{"default_model":"m1","models":[{"id":"m1","label":"Model One"}]}"""))
    assertEquals(listOf("m1"), catalog.flat.map { it.model })
    assertTrue(catalog.groups.isEmpty())
    assertTrue(catalog.configured.isEmpty())
}

@Test fun `badge requires provider agreement when several matches exist`() {
    val badges = mapOf("a/x" to ConfiguredBadge("p1"), "b/x" to ConfiguredBadge("p2"))
    val pair = configuredBadgeFor(badges, "x", "p9")   // ambiguous, no provider match
    assertNull(pair)
}
```

- [ ] **Step 3: Run to verify failure** — `--tests "com.dipandhali.hermesnative.modelpicker.PickerCatalogTest"`. Expected: FAIL.

- [ ] **Step 4: Implement** `PickerModel.kt` (types + `PickerCatalogBuilder.build` + `configuredBadgeFor`). Follow the ui.js line references in the Interfaces block; port `_vendorPrefix`-style helpers only if needed for displayName (displayName = `ModelDto.displayLabel`, with `@custom:` ids left as-is).

- [ ] **Step 5: Run to verify pass.** Expected: PASS.

- [ ] **Step 6: Commit** — `feat(model-picker): build picker catalog from /api/models`

---

### Task 4: Picker items, chip label, open resolution, explicit-pick guard (pure)

**Files:**
- Modify (append to): `android-native/app/src/main/java/com/dipandhali/hermesnative/domain/modelpicker/PickerModel.kt`
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/modelpicker/PickerItemsTest.kt`
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/modelpicker/ModelSendLogicTest.kt`

**Interfaces:**
- Produces (used by Tasks 8, 9):
  - `sealed interface PickerItem { data class Header(key: String, label: String, count: Int); data class Row(row: ModelRow); data class ShowMore(groupKey: String, hiddenCount: Int) }`
  - `fun pickerItems(catalog: PickerCatalog, query: String, expanded: Set<String>, revealed: Set<String>): List<PickerItem>`
  - `fun pickerRowSelected(row: ModelRow, selection: ModelSelection?): Boolean` (port of `_isSelectedModelRow` L5036–5053: `optionIdentity(row.model, row.providerId) == optionIdentity(selection.model, selection.provider)` **and** `row.providerId == selection.provider`)
  - `fun chipLabel(selection: ModelSelection?, catalog: PickerCatalog?): String?` (catalog lookup by model value → `displayName`; else compact the id: strip `@provider:` prefix, then drop the first `/`-segment; web `_compactComposerModelChipLabel` L4534–4562)
  - `fun resolveOpen(detailModel: String?, detailProvider: String?, remembered: ModelSelection?, defaultModel: String?, activeProvider: String?): OpenResolution` where `data class OpenResolution(val selection: ModelSelection?, val writeRemembered: Boolean)` — rules: detail wins (no write); else remembered (write=true); else defaultModel with activeProvider (no write); else null.
  - `fun explicitPickForSend(pending: ModelSelection?, selection: ModelSelection?, defaultModel: String?, activeProvider: String?): Boolean` — `pending == selection` (both fields) **or** the cross-provider condition: `selection != null && selection.provider != null && defaultModel != null && activeProvider != null && selection.model != defaultModel && selection.provider != activeProvider` (web `messages.js` L1829–1835).

**Search semantics (pin these):** a row matches when `displayName` or `model` contains the lowercased query, or when both folded forms match (fold = lowercase + remove `[\\s._-]`, web L5207–5216). When the query is non-empty: all groups with matches are expanded regardless of `expanded`, hidden rows are included, `ShowMore` items are not emitted, empty sections are dropped; the CONFIGURED section only shows when it has matches. When the query is empty: configured + groups render; expanded groups show rows (revealed shows hidden rows plus no `ShowMore`; not revealed and `hidden.size > 0` shows `ShowMore(groupKey, hidden.size)`); collapsed groups render only a Header. Header count = rows + hidden size for that group after hoisting/search. **Flat catalogs** (`groups` empty, `flat` non-empty): render `Header("__flat__", "MODELS", count)` followed by the flat rows, filtered by search exactly like group rows, with no collapse/expand.

- [ ] **Step 1: Write failing tests** — build the catalog from the Task 3 fixture; exact assertions:

```kotlin
@Test fun `collapsed catalog renders headers only`() {
    val items = pickerItems(catalog, "", expanded = emptySet(), revealed = emptySet())
    assertEquals(
        listOf("CONFIGURED", "Solar Mini4 (free)", "openai/gpt-5.4", "Nous (2)", "OpenRouter (1)"),
        items.map { it.title() },   // helper: Header.label / Row.displayName
    )
}

@Test fun `expanded group reveals show-more then hidden rows`() {
    val expanded = pickerItems(catalog, "", expanded = setOf("nous"), revealed = emptySet())
    assertTrue(expanded.any { it is PickerItem.ShowMore && it.hiddenCount == 2 })
    val revealedItems = pickerItems(catalog, "", expanded = setOf("nous"), revealed = setOf("nous"))
    assertEquals(listOf("Claude Sonnet 4.6", "Kimi K2.5"), revealedItems.rowsIn("nous"))
}

@Test fun `search matches hidden rows and expands groups`() {
    val items = pickerItems(catalog, "kimi", expanded = emptySet(), revealed = emptySet())
    assertEquals(listOf("Nous (1)", "Kimi K2.5"), items.map { it.title() })
}

@Test fun `search folds separators`() {
    assertTrue(pickerItems(catalog, "solar pro", emptySet(), emptySet()).any { it.title() == "Solar Pro4" })
}

@Test fun `flat catalog renders a single models section`() {
    val flat = PickerCatalogBuilder.build(json.decodeFromString("""{"default_model":"m1","models":[{"id":"m1","label":"Model One"}]}"""))
    val items = pickerItems(flat, "", emptySet(), emptySet())
    assertEquals(listOf("MODELS (1)", "Model One"), items.map { it.title() })
}

@Test fun `selected matching requires the same identity and provider`() {
    val row = catalog.groups[1].rows.first()   // openrouter/solar-pro4
    assertTrue(pickerRowSelected(row, ModelSelection("openrouter/solar-pro4", "openrouter")))
    assertFalse(pickerRowSelected(row, ModelSelection("openrouter/solar-pro4", "nous")))
    assertFalse(pickerRowSelected(row, ModelSelection("solar-pro4", "openrouter")))  // slash spelling is not collapsed here
}
```

```kotlin
// ModelSendLogicTest
@Test fun `pending pick matches itself`() {
    val pick = ModelSelection("@nous:x", "nous")
    assertTrue(explicitPickForSend(pick, pick, null, null))            // catalog absent — Review Focus #2
}
@Test fun `cross-provider selection is explicit even without a pending pick`() {
    assertTrue(explicitPickForSend(null, ModelSelection("m", "openrouter"), "default-m", "openai"))
}
@Test fun `same-provider or default selection is not explicit`() {
    assertFalse(explicitPickForSend(null, ModelSelection("m", "openai"), "m", "openai"))
    assertFalse(explicitPickForSend(null, ModelSelection("m", "openai"), null, "openai"))
}
@Test fun `open resolves detail over remembered over default`() {
    assertEquals(
        OpenResolution(ModelSelection("d", "p"), writeRemembered = false),
        resolveOpen("d", "p", ModelSelection("r", "p2"), "def", "act"),
    )
    assertEquals(
        OpenResolution(ModelSelection("r", "p2"), writeRemembered = true),
        resolveOpen(null, null, ModelSelection("r", "p2"), "def", "act"),
    )
    assertEquals(
        OpenResolution(ModelSelection("def", "act"), writeRemembered = false),
        resolveOpen(null, null, null, "def", "act"),
    )
}
@Test fun `chip label falls back to a compacted id`() {
    assertEquals("Solar Mini4 (free)", chipLabel(ModelSelection("@nous:solar-mini4:free", "nous"), catalog))
    assertEquals("solar-pro4", chipLabel(ModelSelection("openrouter/solar-pro4", "openrouter"), null))
}
```

- [ ] **Step 2: Run to verify failure** — both new test classes. Expected: FAIL.

- [ ] **Step 3: Implement** the appended functions in `PickerModel.kt`.

- [ ] **Step 4: Run to verify pass.** Expected: PASS.

- [ ] **Step 5: Commit** — `feat(model-picker): picker items, chip label, send resolution`

---

### Task 5: Provider icons — resolver, URL builder, cached loader, composable

**Files:**
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/core/icons/ProviderIcons.kt`
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/core/icons/ProviderIconLoader.kt`
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/components/ProviderIcon.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/theme/Theme.kt`
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/modelpicker/ProviderIconsTest.kt`
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/icons/ProviderIconLoaderTest.kt` (Robolectric)

**Interfaces:**
- Produces (used by Tasks 9):
  - `enum class ProviderIconVariant(val dirName: String) { LIGHT("light"), DARK("dark") }`
  - `object ProviderIcons { const val CDN_BASE = "https://cdn.jsdelivr.net/gh/lobehub/lobe-icons@v6.0.0/packages/static-png"; fun iconId(modelId: String?, providerId: String?): String?; fun url(iconId: String, variant: ProviderIconVariant, base: String = CDN_BASE): String; fun monogramText(modelId: String?, providerId: String?, fallback: String?): String }`
  - `class ProviderIconLoader` with `@Inject constructor(@ApplicationContext context, @Named("api") client)` and an internal `constructor(context, client, baseUrl)` for tests; `suspend fun load(iconId: String, variant: ProviderIconVariant): ImageBitmap?` — null on any failure.
  - `val LocalProviderIconVariant = staticCompositionLocalOf { ProviderIconVariant.LIGHT }` provided by `HermesNativeTheme` from its resolved `dark` flag.
  - `@Composable fun ProviderIcon(modelId: String?, providerId: String?, label: String?, size: Dp, modifier: Modifier = Modifier)` — image when loaded, monogram otherwise, crossfade via `HermesMotion`.
- Resolver precedence (spec §5): vendor from the model id (`vendor/rest` after stripping `@...:` tags; alias-normalized; known only) → provider id (alias-normalized) → null (monogram). Alias tables:
  - provider aliases: `nous→nousresearch`, `hermes→hermesagent`, `moonshotai→moonshot`, `z-ai→zai`
  - vendor aliases: `x-ai→xai`, `mistralai→mistral`, `meta-llama→meta`, `z-ai→zai`, `google→google`, `grok→xai`
  - `KNOWN = setOf(...)` — start from the verified list in Global Constraints; step 1 re-verifies any id you add beyond it.

- [ ] **Step 1: Verify the icon ids you will ship** (one command; ids not in the verified list must 200 before they land in `KNOWN`):

```bash
for id in openai anthropic google gemini deepseek mistral xai grok qwen moonshot zhipu zai minimax upstage openrouter groq ollama together cohere perplexity meta nvidia nousresearch hermesagent; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "https://cdn.jsdelivr.net/gh/lobehub/lobe-icons@v6.0.0/packages/static-png/dark/$id.png"); echo "$id -> $code";
done
```

- [ ] **Step 2: Write the failing tests** — resolver cases (no Robolectric):

```kotlin
@Test fun `vendor beats provider, provider beats monogram`() {
    assertEquals("openai", ProviderIcons.iconId("@nous:openai/gpt-5.4-mini", "nous"))
    assertEquals("nousresearch", ProviderIcons.iconId("solar-mini4:free", "nous"))
    assertNull(ProviderIcons.iconId("mystery-model", "mystery"))
}
@Test fun `url pins the tag and the variant directory`() {
    assertEquals(
        "https://cdn.jsdelivr.net/gh/lobehub/lobe-icons@v6.0.0/packages/static-png/dark/openai.png",
        ProviderIcons.url("openai", ProviderIconVariant.DARK),
    )
}
@Test fun `monogram falls back through model, provider, label`() {
    assertEquals("S", ProviderIcons.monogramText("solar-mini4:free", null, null))
    assertEquals("N", ProviderIcons.monogramText(null, "nous", null))
    assertEquals("?", ProviderIcons.monogramText(null, null, null))
}
```

loader cases (Robolectric + MockWebServer; `PNG_1PX` = the bytes of any valid small PNG — a 1×1 transparent PNG as a `ByteArray` constant in the test file):

```kotlin
@Test fun `loads once and serves the second call from cache`() = runBlocking {
    server.enqueue(
        MockResponse.Builder().code(200).addHeader("Content-Type", "image/png")
            .body(okio.Buffer().write(PNG_1PX)).build(),
    )
    val loader = ProviderIconLoader(context, OkHttpClient(), server.url("/").toString())
    assertNotNull(loader.load("openai", ProviderIconVariant.DARK))
    assertNotNull(loader.load("openai", ProviderIconVariant.DARK))
    assertEquals(1, server.requestCount)
}
@Test fun `failure returns null and never throws`() = runBlocking {
    server.enqueue(MockResponse.Builder().code(404).build())
    assertNull(ProviderIconLoader(context, OkHttpClient(), server.url("/").toString()).load("nope", ProviderIconVariant.LIGHT))
}
```

Use a fresh loader per test (memory cache is per-instance); the disk cache under `context.cacheDir` must be unique per test (Robolectric gives a per-test temp dir; still, key by `baseUrl.hashCode()` in the cache path so two servers cannot collide).

- [ ] **Step 3: Run to verify failure** — both classes. Expected: FAIL (unresolved).

- [ ] **Step 4: Implement** the four files. Loader details:
  - memory: `object : LruCache<String, ImageBitmap>(64) {}` keyed `"${variant.dirName}/$iconId"`.
  - disk: `File(context.cacheDir, "provider-icons/${baseKey}/${variant.dirName}/$iconId.png")`; write the fetched bytes directly; read via `BitmapFactory.decodeFile`.
  - in-flight dedupe: `Mutex` + `MutableMap<String, Deferred<ImageBitmap?>>`.
  - negative cache: in-memory `MutableSet<String>` of failed ids so a recomposition does not refetch.
  - fetch through the injected OkHttp client (it carries the cookie jar and no forbidden headers).
  - `ProviderIcon` composable: `produceState<ImageBitmap?>(initialValue = null, iconId, variant)`; monogram = first letter of `monogramText`, `MaterialTheme.colorScheme.secondaryContainer` circle + `onSecondaryContainer` text, deterministic container-role rotation by `abs(iconId.hashCode()) % 3` across secondary/tertiary/surfaceVariant containers; crossfade with `HermesMotion.spatialFast()` (mirror the `AnimatedContent` style in `Composer.kt` L155–162).
  - `Theme.kt`: wrap `MaterialTheme` in `CompositionLocalProvider(LocalProviderIconVariant provides if (dark) ProviderIconVariant.DARK else ProviderIconVariant.LIGHT)`.

- [ ] **Step 5: Run to verify pass.** Expected: PASS.

- [ ] **Step 6: Commit** — `feat(model-picker): provider icons with cache and monogram fallback`

---

### Task 6: Hermes Agent mark in headers

**Files:**
- Create: `android-native/app/src/main/res/drawable/ic_hermes_agent.xml`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/home/HomeScreen.kt` (title block L125–131)
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/connect/ConnectScreen.kt` (hero L96–100)

**Interfaces:**
- Produces: `R.drawable.ic_hermes_agent` — a tintable 24×24-viewBox vector (single path, `android:fillType="evenOdd"`), used with `Icon(painterResource(...), tint = MaterialTheme.colorScheme.primary)`.

- [ ] **Step 1: Generate the vector** — the source SVG is one `<path>`:

```bash
curl -s "https://cdn.jsdelivr.net/npm/@lobehub/icons-static-svg@latest/icons/hermesagent.svg"
```

Write `ic_hermes_agent.xml` with `android:width/height="24dp"`, `viewportWidth/Height="24"`, one `<path android:fillColor="#FF000000" android:fillType="evenOdd" android:pathData="<path d verbatim>"/>`. Do not edit the path data.

- [ ] **Step 2: Swap the wordmarks.** `HomeScreen`: replace the `Text("Hermes", titleLarge, primary)` with `Icon(painterResource(R.drawable.ic_hermes_agent), contentDescription = "Hermes", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))`. `ConnectScreen`: same icon at `48.dp` centered in place of the `displaySmall` text. Keep both import lists honest.

- [ ] **Step 3: Build check** (no unit test — resource-level change): `./gradlew :app:assembleDebug` locally, or push and confirm the CI APK job goes green; the drawable must compile (`vectorDrawables { useSupportLibrary = true }` is already set).

- [ ] **Step 4: Commit** — `feat(model-picker): Hermes Agent mark in app headers`

---

### Task 7: Send the model on `startTurn`

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/data/chat/ChatRepository.kt` (L32–49)
- Test: `android-native/app/src/test/java/com/dipandhali/hermesnative/chat/ChatRepositoryStartTest.kt`

**Interfaces:**
- Produces (used by Task 8):
  - `ChatRepository.startTurn(message: String, attachments: List<AttachmentDto> = emptyList(), model: String? = null, modelProvider: String? = null, explicitPick: Boolean? = null): Result<String>` — fills `ChatStartRequest.model/modelProvider/explicitModelPick`. Pass `true` or `null` only, never `false` — `null` serializes as `"explicit_model_pick":null`, which the server treats as falsey, matching the web's `|| undefined`.

- [ ] **Step 1: Write the failing test** (MockWebServer; construct `ChatRepository(HermesApi(client), SseClient(client), ServerState().apply { setActiveSession("sess-1") })` — mirror how `ServerState` is set in `SessionRepositoryCreateTest`):

```kotlin
@Test fun `startTurn posts model provider and explicit flag`() = runTest {
    server.enqueue(MockResponse.Builder().code(200).body("""{"stream_id":"st-1"}""").build())
    val result = repository().startTurn("hi", model = "@nous:solar-mini4:free", modelProvider = "nous", explicitPick = true)
    assertTrue(result.isSuccess)
    val body = server.takeRequest().body?.utf8() ?: ""
    assertTrue(body.contains(""""model":"@nous:solar-mini4:free""""))
    assertTrue(body.contains(""""model_provider":"nous""""))
    assertTrue(body.contains(""""explicit_model_pick":true"""))
}
@Test fun `startTurn omits model fields when unknown`() = runTest {
    server.enqueue(MockResponse.Builder().code(200).body("""{"stream_id":"st-1"}""").build())
    repository().startTurn("hi")
    val body = server.takeRequest().body?.utf8() ?: ""
    assertFalse(body.contains("explicit_model_pick"))
    assertFalse(body.contains(""""model""""))
}
```

- [ ] **Step 2: Run to verify failure.** Expected: FAIL (params unresolved).

- [ ] **Step 3: Implement** — thread the three params into `ChatStartRequest`; nothing else in `startTurn` changes. Existing call site (`ChatViewModel.send`) keeps compiling via defaults.

- [ ] **Step 4: Run to verify pass.** Expected: PASS.

- [ ] **Step 5: Commit** — `feat(model-picker): send model selection with chat start`

---

### Task 8: ChatViewModel — model state, pick flow, open resolution, catalog

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatViewModel.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatScreen.kt` (effects only if a new effect type is needed — keep `ChatEffect.ShowMessage`)

**Interfaces:**
- Consumes: `SessionRepository.models()/updateSession` (Task 1), `ChatRepository.startTurn(...)` (Task 7), `resolveOpen`/`explicitPickForSend`/`PickerCatalogBuilder` (Tasks 3–4), `SettingsStore.model`/`setModel` (exists).
- Produces (used by Task 9) — new members on `ChatUiState` and `ChatViewModel`:
  - `ChatUiState.modelProvider: String? = null`
  - `ChatUiState.picker: ModelPickerUiState = ModelPickerUiState()` where `data class ModelPickerUiState(val open: Boolean = false, val catalog: PickerCatalog? = null, val isLoading: Boolean = false, val error: String? = null)`
  - `ChatViewModel.openPicker()`, `closePicker()`, `loadModels()`, `pickModel(model: String, provider: String?)`
  - Constructor gains `private val sessions: SessionRepository` and `private val providerIconLoader` is **not** needed here (UI-only).

**Behavior to implement (from spec §6 and `static/ui.js`):**
- `openSession` success: after publishing the snapshot, apply `resolveOpen(detail.model, detail.modelProvider, remembered, catalog?.defaultModel, catalog?.activeProvider)` where `remembered` is read once per open from `settings.model.first()` — but **only when `pendingPick == null`** (a pick made while the transcript was still loading must not be clobbered by the late resolution). If `writeRemembered` → fire-and-forget `settings.setModel(sel.model, sel.provider)` + `sessions.updateSession(sessionId, ...)` (silent on failure). Then `loadModels()` if the catalog was never loaded this ViewModel.
- `loadModels()`: guard against concurrent runs (`isLoading`); on success `catalog = PickerCatalogBuilder.build(response)`; on failure set `picker.error` (a retry button calls it again). Called from `openSession` once per ViewModel, and again from `openPicker()` when `catalog == null && !isLoading`.
- `openPicker()` = `picker.copy(open = true)` + the ensure-load above; `closePicker()` = `open = false`.
- `pickModel(model, provider)`: `pendingPick = ModelSelection(model, provider)`; `state.model = model`; `state.modelProvider = provider`; `picker.open = false`; `_effects.trySend(ShowMessage("Applies to this conversation from your next message."))`; launch `settings.setModel(model, provider)` and — when `sessionId != null` — `sessions.updateSession(sessionId, model, provider)` whose failure sends `ShowMessage("Couldn't save model for this conversation — it will still apply to your next message here.")`.
- `send()`: `val selection = state.model?.let { ModelSelection(it, state.modelProvider) }`; `val explicit = explicitPickForSend(pendingPick, selection, catalog?.defaultModel, catalog?.activeProvider)`; call `chat.startTurn(body, model = selection?.model, modelProvider = selection?.provider, explicitPick = explicit.takeIf { it })`; on success and after the start accepted, `pendingPick = null`. On `SessionBusy` (attach path) leave `pendingPick` intact.
- `ChatEvent.RuntimeModel` keeps updating `model` only (provider untouched).
- Top-of-file KDoc unchanged; add a one-line comment to `pendingPick` explaining it mirrors the web's `_pendingPickMatch` (consumed only after a successful kickoff).

- [ ] **Step 1: Implement the state + wiring** (no unit test — the pure decisions are already tested in Task 4; the codebase has no ViewModel tests, and this change is composition).
- [ ] **Step 2: Compile check** — `./gradlew :app:compileDebugKotlin` locally or push and read CI. Expected: compiles; full unit suite still green (no default-parameter breakage).
- [ ] **Step 3: Commit** — `feat(model-picker): model state, pick flow and catalog in ChatViewModel`

---

### Task 9: Chip, picker sheet, screen wiring

**Files:**
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/Composer.kt` (row L105–134)
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/ModelChip.kt`
- Create: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/ModelPickerSheet.kt`
- Modify: `android-native/app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatScreen.kt`

**Interfaces:**
- Consumes: `ChatUiState.picker`, `ChatViewModel.openPicker/closePicker/pickModel/loadModels`, `PickerItem`/`pickerItems`/`pickerRowSelected`/`chipLabel` (Tasks 3–4), `ProviderIcon` (Task 5).
- Produces:
  - `Composer(..., leadingAccessory: (@Composable () -> Unit)? = null)` — slot rendered before the attach button.
  - `@Composable fun ModelChip(selection: ModelSelection?, catalog: PickerCatalog?, onClick: () -> Unit, modifier: Modifier = Modifier)` — `AssistChip`-based: `leadingIcon` = `ProviderIcon(modelId, providerId, label, 18.dp)`, label = `chipLabel(...) ?: "Model"` capped at `widthIn(max = 160.dp)` with ellipsis, `trailingIcon` = `Icons.Rounded.ArrowDropDown`; `contentDescription = "Model and options"`.
  - `@Composable fun ModelPickerSheet(catalog: PickerCatalog?, isLoading: Boolean, error: String?, selection: ModelSelection?, onPick: (String, String?) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit)`.

**Sheet construction (spec §4.2):**
- `ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true))` with `@OptIn(ExperimentalMaterial3Api::class)`.
- Local `remember`ed state: `query`, `expanded: Set<String>` (initialize empty; the sheet renders `pickerItems(catalog, query, expanded, revealed)`), `revealed: Set<String>`, `customId: String`.
- Fixed header: `Text("Model", titleMedium)` + scope note `bodySmall/onSurfaceVariant`; search `OutlinedTextField` with leading `Icons.Rounded.Search`, trailing clear when non-empty, `singleLine`, no autofocus; custom-ID row `OutlinedTextField("Custom model ID")` + trailing `IconButton(Icons.Rounded.Add)` enabled when `customId.isNotBlank() && no rendered row has model == customId` → `onPick(customId.trim(), null)`.
- Body: `LazyColumn` over `pickerItems`; `Header` rows = `labelMedium` + `onSurfaceVariant` + `w600` emphasis; `Row` = `Row(Modifier.selectable(selected, onClick = { onPick(row.model, row.providerId) }))` with `ProviderIcon(row.model, row.providerId, row.displayName, 32.dp)`, name `bodyLarge`, id `bodySmall/onSurfaceVariant`, `RadioButton(selected)` trailing, badge pill between name and radio when `badgeRole != null` (`Surface(color = tertiaryContainer, shape = shapes.small)` + `labelSmall` text `badgeLabel`); selected container `secondaryContainer`, shape `shapes.large`; `ShowMore` = text button `Show more (N)` appending `groupKey` to `revealed`; error state = message + `TextButton("Retry")` calling `onRetry`; loading state = `LinearProgressIndicator` under the header when `isLoading && catalog == null`.
- `ChatScreen`: pass `leadingAccessory = { ModelChip(state.model?.let { ModelSelection(it, state.modelProvider) }, state.picker.catalog, viewModel::openPicker) }` to `Composer`; below the `Scaffold` (sibling), `if (state.picker.open) ModelPickerSheet(...)` bound to the state and the ViewModel callbacks; `onOpenSettings` on the composer stays.

- [ ] **Step 1: Implement** the four files (this is composition over already-tested logic).
- [ ] **Step 2: Compile + suite** — `./gradlew :app:testDebugUnitTest` local, or push + CI. Expected: green.
- [ ] **Step 3: Commit** — `feat(model-picker): composer chip, picker sheet and screen wiring`

---

### Task 10: Evidence and device handoff

**Files:**
- Create: `android-native/docs/HANDOFF-model-picker.md` (untracked working note, like the home-send one)

- [ ] **Step 1: Push and get CI green**

```bash
cd android-native && git push -u origin feat/model-picker
```

Record the `android.yml` run URL; all suites `failures="0"`; APK artifact downloaded.

- [ ] **Step 2: Write the handoff note** — what changed, the branch, the CI run link, the APK location, and the device checklist:
  - chip shows the session's model (or remembered/default) with a provider icon;
  - sheet: search filters (incl. hidden/overflow rows), custom ID adds+selects, CONFIGURED shows Primary/Fallback badges + SELECTED, groups expand, "Show more" reveals;
  - pick → snackbar copy above; reopen the chat → chip keeps the pick; new chat starts on it;
  - airplane mode first-open → monograms, no crash; back online → icons appear;
  - Home + Connect headers show the Hermes mark (no "Hermes" text).
- [ ] **Step 3: Commit** — `docs(model-picker): device verification handoff` (docs only; the handoff note itself stays untracked until the device pass, matching the home-send cycle).

---

## Self-Review (completed)

- **Spec coverage:** chip entry (§4.1 → T9), sheet content/§4.2 (T9), icons (§5 → T5), behavior/§6 (T3–T4 pure + T7–T8 wiring), theme/§7 (T5 variant local, T9 shapes/typography via existing theme), headers/§4.3 (T6), tests/§9 (each task's test steps + T10 CI), degradation path (T3 flat test), custom ID (T9), `extra_models` "Show more" (T4/T9).
- **Type consistency:** `ModelSelection`, `ModelRow`, `ModelGroup`, `PickerCatalog`, `PickerItem`, `OpenResolution`, `resolveOpen`, `explicitPickForSend`, `pickerItems`, `pickerRowSelected`, `chipLabel`, `configuredBadgeFor`, `ProviderIcons`, `ProviderIconVariant`, `ProviderIconLoader.load`, `Composer.leadingAccessory`, `ModelPickerSheet`/`ModelChip` parameters — used consistently across tasks.
- **Review Focus → tests:** #1 T3, #2 T4 (`pending pick matches itself` with null catalog), #3 T8 (failure path; no unit test by design — flagged for device pass), #4 T2+T3, #5 T5.
- **Proportion:** plan ≈ 2.5× spec, mostly signatures and assertions; no full file bodies except the fixture.

---

## Round 2 — device feedback amendments (2026-10-16)

Device pass on the Task 10 APK returned five changes; Tasks 11–13 below implement and evidence them, and the spec's §13 records the design override.

### Task 11: Round 2 — home composer chip, create-with-model, tune removal, inset parity

**Context:** the picker was chat-only; the web sends its picker value in the `/api/session/new` body (`static/sessions.js` L2139: `reqBody.model = newModelState.model`), and the server stores it (`api/routes.py` new-session path: `_session_model_state_from_request` → `new_session(model=..., model_provider=...)`). A new chat therefore carries the pick **from creation** — no open-time override is needed, which is why this supersedes any "seeded session" special case.

**Files:**
- Modify: `app/src/main/java/com/dipandhali/hermesnative/core/model/Wire.kt` — add `NewSessionRequest`.
- Modify: `app/src/main/java/com/dipandhali/hermesnative/core/net/HermesApi.kt` — `newSession(baseUrl, request)`.
- Modify: `app/src/main/java/com/dipandhali/hermesnative/data/sessions/SessionRepository.kt` — `create(model, provider)`.
- Create: `app/src/main/java/com/dipandhali/hermesnative/domain/modelpicker/ModelPickerUiState.kt`.
- Modify: `app/src/main/java/com/dipandhali/hermesnative/domain/modelpicker/PickerModel.kt` — `displaySelection`.
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatViewModel.kt` — move the state type out; make `loadModels` idempotent.
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/sessions/SessionsViewModel.kt` — model state, picker state, pick flow, create carries the pick.
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/components/Composer.kt` — drop `onOpenSettings`, the Tune button, and the internal `imePadding()`.
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/chat/ChatScreen.kt` — inset parity, `displaySelection` for chip + sheet, stop passing `onOpenSettings` to the composer.
- Modify: `app/src/main/java/com/dipandhali/hermesnative/ui/home/HomeScreen.kt` — `HomeComposer(leadingAccessory = …)`, chip + sheet host, `loadModels()` on entry.
- Create: `app/src/test/java/com/dipandhali/hermesnative/sessions/SessionRepositoryCreateModelTest.kt`; extend `app/src/test/java/com/dipandhali/hermesnative/modelpicker/ModelSendLogicTest.kt`.

**Interfaces:**
- `SessionRepository.create(model: String? = null, provider: String? = null)` — posts `{"model":…,"model_provider":…}`, both omitted when null (bare `create()` still posts `{}`).
- `displaySelection(selection: ModelSelection?, catalog: PickerCatalog?): ModelSelection?` — the non-empty selection, else the catalog default (+ active provider), else null.
- `ModelPickerUiState(open, catalog, isLoading, error)` moves to `domain/modelpicker/` (shared by both ViewModels).
- `SessionsUiState.model/modelProvider` mirror `SettingsStore.model`; `SessionsViewModel.loadModels()/openPicker()/closePicker()/pickModel(model, provider)` — a home pick is remembered-only (no session to update, no snackbar; the chip updating is the feedback).
- `ModelPickerSheet(..., scopeNote: String = "Applies to this conversation from your next message.")` — home passes `"Applies to new conversations."`.
- `ChatViewModel.loadModels()` no-ops when `catalog != null || isLoading`; `catalogRequested` is deleted; `openSession` calls it unconditionally.

**Steps:**
- [ ] **Step 1: RED** — `SessionRepositoryCreateModelTest` (model+provider posted; omitted when null) and `displaySelection` cases in `ModelSendLogicTest` (null→catalog default, session wins, empty model→default, no catalog→null).
- [ ] **Step 2: GREEN** — implement; full suite green.
- [ ] **Step 3: Commit** — `feat(model-picker): home chip, session create carries the model`.

### Task 12: Round 2 — chip pill redesign and combined header lockup

**Files:**
- Regenerate: `app/src/main/res/drawable/ic_hermes_agent.xml` as the **combined** lockup, mirroring upstream `HermesAgent.Combine` math (icon 56 ⇒ text 0.85×56, gap 0.2×56): face (Mono artwork, 24×24, 3 paths) scaled 56/24, wordmark (`hermesagent-text.svg`, 52×24, one path) scaled 47.6/24 at x=67.2 y=4.2; viewport 170.3333×56 (ratio 73:24), `fillType=evenOdd` throughout. `hermesagent-text.svg` alone is the wordmark only — the face must be composed in from the Mono mark.
- Modify: `ModelChip.kt`, `HomeScreen.kt` (title), `ConnectScreen.kt` (hero).

**Requirements:**
- Chip: `Surface(onClick, shape = shapes.extraLarge, color = surfaceContainerHigh, contentColor = onSurface)`; row = label (`labelLarge`, ellipsis, max 160dp) + `Icons.Rounded.KeyboardArrowDown` (18dp); no border, no leading icon; keep `contentDescription = "Model and options"`.
- Headers: `Image(painterResource(R.drawable.ic_hermes_agent), contentDescription = "Hermes Agent", colorFilter = tint(primary))` — home `height(28.dp)`, Connect `height(48.dp)`, both `.aspectRatio(73f / 24f)`.

**Steps:**
- [ ] **Step 1: Implement** both.
- [ ] **Step 2:** `./gradlew :app:assembleDebug` + full suite green.
- [ ] **Step 3: Commit** — `feat(model-picker): pill chip and combined Hermes Agent lockup`.

### Task 13: Round 2 — evidence, combined test APK, branch review

**Files:**
- Update: `android-native/docs/HANDOFF-model-picker.md` (untracked).

**Steps:**
- [ ] **Step 1:** Push `feat/model-picker`; CI green (record run URL + APK).
- [ ] **Step 2:** Combined device build: `git checkout -b test/model-picker-home-send feat/model-picker && git merge fix/home-send`; resolve conflicts keeping both intents (nested `NewSessionResponse` from the fix; picker state from this branch; home snackbar + `isStartingChat` from the fix); push the test branch; CI green; download APK. The home pick's end-to-end path (create carries the model; seed sends after the transcript settles) is only verifiable on this combined build.
- [ ] **Step 3:** Fresh-context branch review over `58a82a0..HEAD` including the plan's Review Focus items; fix Critical/Important findings and re-run CI.
- [ ] **Step 4:** Handoff note: what changed, CI links, APK paths, device checklist, and the merge-order note (`fix/home-send` first, then `feat/model-picker`).
