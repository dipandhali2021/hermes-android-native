# Round 7 — Context window meter (composer)

Port the web client's context-window indicator (composer ring + usage card)
to the native composer, beside the mic.

## Research summary (from hermes-webui source)

- **Trigger (web):** `static/index.html` `#ctxIndicator` — a 24 px ring with
  the percent inside (`#ctxRingValue` arc + `#ctxPercent`), sitting in
  `composer-right` between the mic/tools and the send button. A tooltip
  (`#ctxTooltip`, title "Context window") opens above it. The mobile web
  layout renders the same data as a `CONTEXT` block in the composer config
  panel (`#composerMobileContextAction`).
- **Render rules (`static/ui.js` `_syncCtxIndicator`, ~7756):**
  - `last_prompt_tokens` drives the percentage; cumulative `input_tokens` must
    never be divided by the window (CHANGELOG #1436 — misleading >100%).
  - No prompt-token data → honest fallback: `"<total> tokens used"` +
    `"In: A · Out: B"` (exactly the state shown in the user's screenshot).
  - `post_compression_context_tokens_estimate` overrides the prompt figure and
    relabels to "Estimated next model context".
  - Bands: `>50%` → warning stroke, `>75%` → error stroke; ring centre clamps
    at 100; raw percent still reported ("context exceeded").
  - Threshold line only when `threshold_tokens` and `context_length` exist:
    `"Auto-compress at T (P%)"`.
  - Card geometry: min 210 px / max 250 px, 12 px radius, one title + small
    lines (the app maps this to a Material DropdownMenu with an equivalent
    width window).
  - `_fmtTokens`: `>=1e6` → `%.1fM`, `>=1e3` → `%.1fk`, else raw.
- **Wire contract:** `metering` (≈1 Hz while active) and `done` both nest the
  same `usage` object: `input_tokens`, `output_tokens`, `cache_read_tokens`,
  `cache_write_tokens`, `estimated_cost`, `context_length`,
  `threshold_tokens`, `last_prompt_tokens`,
  `post_compression_context_tokens_estimate` (`api/streaming.py`
  `_live_usage_snapshot` + metering ticker ~11960–12033, `done` session
  payload). `context_status` is prefill status, **not** window usage.
- **Cold open:** `GET /api/session` `compact()` exposes the same persisted
  fields (`api/models.py` ~2288), so the ring survives a reload in the web —
  the app seeds from the same response.
- **No 128K default:** the web keeps a legacy default + "(est. 128K)" suffix;
  its own changelog calls the resulting percentages misleading. The app shows
  a percent only from real `last_prompt_tokens` + `context_length`.

## Decisions

- **Component:** the ring button *is* the icon (live percent, no static glyph
  conveys saturation); tap opens a Material 3 `DropdownMenu` card — the
  platform-anchored popover that flips above the keyboard and dismisses on
  outside tap — already the app's popover idiom (MessageBubble menus).
- **Placement:** chat composer controls row, between mic and send (mirrors
  web `composer-right` order). Home composer has no session, so no meter.
- **Visibility:** control appears only once a usage snapshot exists
  (`ContextMeter.from(usage) == null` hides it), matching the web's
  `display:none`-until-data behaviour.
- **Scope:** read-only readout. The web's compress button (≥50%) is out —
  the app has no slash-command surface; the threshold line still names the
  auto-compress point.
- **Colours:** track `outlineVariant`; arc `onSurfaceVariant` (web `--muted`),
  `>50` → `tertiary` (web warning), `>75` → `error`. Motion: arc sweep =
  `HermesMotion.spatialFast`, stroke colour = `HermesMotion.effects`.

## Tasks

1. [x] Protocol: `TokenUsage` gains the four context fields; `metering`
   parses its usage snapshot (parser tests from real payload shapes).
2. [x] Domain: `ContextMeter` display model (pct/estimate/threshold/lines +
   `formatTokens`), unit-tested against the web's exact strings.
3. [x] UI: `ContextIndicator` (ring + card), composer threading, chat state
   `usage` field (metering + done updates), session-load seeding + reset.
4. [x] Docs: handoff round-7 section, device checks, CI evidence + APK.

## Evidence

- Commits: `37f6054` (protocol), `a2d1489` (domain), `f0aed62` (UI) on
  `feat/model-picker`; merged into `test/model-picker-home-send` as
  `2a4d340` (clean — no HomeScreen conflict; Round 7 touches none of the
  divergent lines).
- CI: feature run 39064092240 green; combined run 39064203183 green.
- Local suite green at each commit; 11 new tests.
- APK: `/tmp/opencode/mp-r7-context/hermes-native-debug-apk/app-debug.apk`
  (md5 `fcb9308c3da4a027ac6240e3e17404b0`).

## Device checks (draft)

- Ring appears beside the mic once a turn has usage; percent matches the web
  tooltip for the same session; tap opens the card above the composer; card
  shows usage/tokens/threshold lines; outside tap dismisses.
- Session with no prompt-token data → ring shows "·", card shows
  "X tokens used" + "In · Out".
- Reopen a session → ring reflects persisted values without sending.
