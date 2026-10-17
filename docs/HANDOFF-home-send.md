# Handoff — home send spins forever (branch `fix/home-send`)

## Symptom
Type a prompt on the home composer, tap send → the button spins forever,
nothing else happens.

## Root causes (three defects + one latent race, all on the same path)
1. **Session creation always failed.** `NewSessionResponse` parsed a
   top-level `session_id`; the server nests it —
   `{"session": {"session_id": ...}}` (`api/routes.py`, POST
   `/api/session/new`; `static/sessions.js` reads `data.session`). So
   `SessionRepository.create()` always failed with `Malformed("no
   session_id")`, the effect was ignored on home, and no navigation happened.
2. **The message was dropped even on success.** `OpenSessionAndSend` was
   handled as `onOpenSession(effect.sessionId)`; `HermesRoot` navigated to
   `Routes.chat(sessionId)` without the text.
3. **No failure feedback.** `HomeComposer.isStarting` was local state with no
   reset path, and `SessionsEffect.Message` was ignored on home.
4. **Latent race (fixed while there).** `ChatViewModel` opened the session and
   sent the seed concurrently; the transcript load replaces `items`, so it
   could erase the optimistic user message.

## Fix (7 files)
- `Wire.kt`: `NewSessionResponse(val session: SessionDetail? = null)`;
  `SessionRepository.create()` reads `session.sessionId`.
- `home/HomeScreen.kt`: `onOpenSessionAndSend(sessionId, message)` callback;
  snackbar host + `Message` effect handling; composer `isStarting` now a
  parameter fed by `SessionsUiState.isStartingChat`.
- `nav/HermesRoot.kt`: navigates `Routes.chat(sessionId, message)`.
- `ui/sessions/SessionsViewModel.kt`: `isStartingChat` in state, set on tap
  (guard against double-tap), cleared on success and failure.
- `ui/chat/ChatViewModel.kt`: `openSession(sessionId, initialMessage)` sends
  the seed only after the transcript load settles; `loadJob` cancels a stale
  load (also fixes cross-session overwrite); `sendInitialMessage` now private.
- `ui/chat/ChatScreen.kt`: single `openSession(sessionId, initialMessage)`
  from the `LaunchedEffect`.

## Evidence
- **RED**: run `38908131659` — `SessionRepositoryCreateTest > create returns
  the id nested under session FAILED` (`AssertionError` at
  `SessionRepositoryCreateTest.kt:43`, `create()` returned null).
- **GREEN**: run `38908591106` — unit tests + `assembleDebug` pass; every test
  suite `failures="0"`; artifacts `test-reports`, `hermes-native-debug-apk`.
- APK fetched to `/tmp/opencode/home-send-apk/app-debug.apk`.

## Device checklist
1. Install the debug APK.
2. Home → type a prompt → send → brief spinner, chat opens, the message is
   there and the reply streams in.
3. Send another message from inside that chat.
4. Failure path (optional): stop the server / airplane mode, send from home →
   spinner stops and a snackbar shows the error instead of spinning forever.

## Release
Not released — per instruction, waiting for device confirmation before any
version bump or release run.

Commits: `464dbc6` (test, RED) → `26741d6` (fix, GREEN) on `fix/home-send`.
