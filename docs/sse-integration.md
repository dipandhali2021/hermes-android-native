# Talking to Hermes WebUI

The wire contract as this client depends on it. Derived from the server source
and from `hermes-webui`'s own cross-client reference
(`docs/sse-streams.md` and `docs/rfcs/session-sse-contract-v1.md`), which are
authoritative — this file is a client-side summary, not a replacement.

## Authentication

Auth is **optional and off by default**.

- `GET /api/auth/status` → `{auth_enabled, logged_in}`
- `POST /api/auth/login {"password": "…"}` → sets the cookie **`hermes_session`**
  (HttpOnly, `SameSite=Lax`, 30-day TTL)

There is **no Bearer-token auth** — a cookie jar is required.

**Send no `Origin`, `Referer` or `Sec-Fetch-*` headers.** CSRF is enforced only
on requests that look browser-issued (`_is_browser_unsafe_request` in
`api/routes.py`), so a client that omits those headers is exempt. Sending
`Sec-Fetch-Site: cross-site` is an immediate rejection. This client strips those
headers defensively rather than merely not adding them.

## Sending a turn

```
POST /api/chat/start
{ "session_id": "…", "message": "…", "model"?: "…", "model_provider"?: "…",
  "workspace"?: "…", "attachments"?: [ {name, path, mime, size, is_image} ] }
→ { "stream_id": "…", "session_id": "…", "turn_id": "…", "title": "…" }
```

Attachments are capped at 20 and the body at 20 MB. A `409` with
`{"error":"session already has an active stream","active_stream_id":"…"}` means
the session is busy — surface it as "attach to the running turn", not a dead-end
error.

## Consuming the stream

```
GET /api/chat/stream?stream_id=<id>        text/event-stream
event: <name>
data: <json>

: keepalive                                 (every 5 seconds)
```

Payloads: `token` → `{"text"}`; `reasoning` → `{"text"}`; `tool` →
`{"event_type","name","preview","args"}`; `tool_complete` → `{…,"duration","is_error"}`;
`done` → `{"session":{…},"usage":{…}}`; `stream_end` → `{"session_id"}`;
`warning` → `{"type","message"}`.

### The rule that matters most

**`done` does not close the stream.** `title` and `stream_end` follow it. The
server defines this explicitly (`api/run_journal.py`):

```python
TERMINAL_SSE_EVENTS    = frozenset({"done","cancel","apperror","error","stream_end"})
SSE_RELAY_CLOSE_EVENTS = frozenset({"stream_end","cancel","apperror","error"})
```

Closing on `done` truncates the title and any trailing events. Close only on
`stream_end`, `cancel`, `apperror`, or the legacy `error`.

### Reconnection

`/api/chat/stream`'s in-memory queue is **not** a replay source: a reconnect only
receives events produced afterwards. Check liveness with
`GET /api/chat/stream/status?stream_id=` → `{active, replay_available}`, then
recover gaps from the run-journal relay:

```
GET /api/sessions/{session_id}/events        Last-Event-ID / ?after_event_id=
```

Event ids are **opaque** (`<stream_id>:<seq>`) — `seq` is monotonic only within
one stream. Dedupe on the id; never parse it or synthesise one.

## Steering and cancelling

- `POST /api/chat/steer` — inject guidance into a live run; applied at the next
  tool-result boundary. Accepted guidance may come back as
  `pending_steer_leftover` if the run ends first.
- `GET /api/chat/cancel?stream_id=` — cancel.

## Reads

- `GET /api/sessions` → `{sessions: […], active_profile, server_time}`. Rows are
  an explicit allowlist and **exclude messages**.
- `GET /api/session?session_id=X&messages=1&msg_limit=N` → `{session: {…}}`
- `GET /api/models`, `GET /api/providers`, `GET /api/workspaces`, `GET /health`
- `GET /api/sessions/events` (SSE) → `sessions_changed`, a signal to re-read the
  list rather than a per-session feed.

## Deferred events

`approval` and `clarify` each need their own stream (`/api/approval/stream`,
`/api/clarify/stream`) and are out of scope for v1. `metering` is the one event
the server does not journal-replay, so it cannot be backfilled after a
reconnect.
