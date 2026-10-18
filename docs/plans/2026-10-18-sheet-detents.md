# Round 8 — sheet detents + sub-flow navigation (device feedback)

Four device reports, one round:

1. Every sheet opened at its full forced height (saved prompts 0.8, profile
   picker 0.75, workspace picker 0.82 of the screen) — "too long by
   default"; wanted shorter on open, drag up to expand to what it is now.
2. The workspace picker's "Manage" text looked off-centre in its box.
3. Workspace picker → Manage → back closed the whole flow instead of
   returning to the workspace picker.
4. The "Choose workspace path" dialog was not modern-M3 (oversized title,
   cramped layout) — "any other way to present this".

## Research

- **Detents:** all seven modal sheets pinned `skipPartiallyExpanded = true`
  and forced a height, so M3's partial anchor (half screen) never existed —
  they always opened fully. M3's two-detent behaviour (open partial, drag up
  to expanded) is the map/mail/store-app pattern the user asked to copy.
- **Off-centre label:** the two footer buttons are half-width; the old
  labels ("Choose workspace path…", "Manage workspaces") overflowed them,
  and `maxLines = 1` with the default `TextOverflow.Clip` measured the text
  at the full available width and clipped it — the glyphs hug the left edge
  with no ellipsis. Fix: shorter labels + `textAlign = Center` + `Ellipsis`.
- **Back from Manage:** the screens called `closeWorkspacePicker()` /
  `closeProfilePicker()` / `closeProfileManager()` before opening the child,
  so back had nothing to return to. Every child-open call comes from the
  parent's own buttons (verified by call-site grep), so the children can
  open *stacked over* the parent instead — back, scrim tap or drag-down then
  lands on the fresh parent. Success paths (`submitWorkspacePath`,
  `createProfile`) only close the child, so they land on the parent too.
- **Path entry:** was an `AlertDialog` (title `headlineSmall` next to a
  `bodyLarge` field — the "no proper font sizing" report). The flow already
  has a sheet language; the path entry becomes a sheet.

## Changes

- Detent: `skipPartiallyExpanded = false` on prompts, profile
  picker/manager, workspace picker/manage, model picker (one line each; the
  forced heights stay as the expanded state, so pinned footers and list
  scrolling behave exactly as before). Profile-create form and the context
  card keep their current behaviour (the form needs the room; the card is
  content-sized).
- Screens (both chat + home): `onManage` / `onCreate` / `onBack` stop
  closing the parent first; no view-model change.
- Workspace picker footer: "Choose path" / "Manage", centred,
  ellipsis-safe.
- `WorkspacePathDialog` → `WorkspacePathSheet`: title + supporting line,
  folder-icon field, suggestion rows in the picker's row style, inline
  progress while busy, Cancel / Add workspace action row.

## Evidence

- Commits `7f10d8c` + `a598a79` on `feat/model-picker`; merged to
  `test/model-picker-home-send` as `a3ec6e7` (clean; test branch's
  state-driven `isStarting` verified intact, merged-tree suite green).
- CI: feature run 39107762404 green; combined run 39107821114 green.
- Local `./gradlew :app:testDebugUnitTest` green on feat (before both
  commits and on the merged tree); non-ASCII scan clean on all touched
  files.
- APK: `/tmp/opencode/mp-r8-sheets/hermes-native-debug-apk/app-debug.apk`
  (md5 `94b1123beedd3c0c9ba67570205788b9`).
- Device checks 32–35 in the handoff; checks 28–31 revised for the 7.1
  context-card restyle that ships in the same APK.
