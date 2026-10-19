# Architecture

Single `:app` module, organised package-by-feature. Multi-module was considered
and rejected for v1: with one developer and no downstream consumers, each extra
module costs a build file, a namespace and a resource merge while buying nothing.
The benefit it would buy — enforced API boundaries — is available for free from
`internal` visibility and package layout, and extraction to modules later is a
`git mv`, not a rewrite.

```
com.dipandhali.hermesnative
├── HermesApp.kt          @HiltAndroidApp
├── MainActivity.kt       edge-to-edge host
├── di/                   Hilt modules
├── core/
│   ├── net/              OkHttp clients, cookie jar, SSE transport
│   ├── error/            DataError + mapping (the error boundary)
│   ├── model/            @Serializable wire DTOs
│   └── ext/              small shared helpers
├── data/                 repositories, one per concern
├── domain/composer/      pure composer-action state machine
└── ui/
    ├── theme/            colour, type, shape, motion
    ├── nav/              NavHost + routes
    ├── components/       shared Material 3 composables
    ├── connect/          connect screen + view model
    ├── sessions/         session list + view model
    ├── chat/             chat screen, view model, message components
    └── settings/         settings screen + view model
```

## Rules

**The repository is the error boundary.** Repositories return a sealed result
and never let `IOException`, `HttpException` or `SerializationException` escape
into a ViewModel. Platform failures are mapped once, in `core/error`, so UI
state is exhaustive and compiler-checked rather than a pile of `catch` blocks.

**One immutable `UiState` per screen**, exposed as `StateFlow` and collected
with `collectAsStateWithLifecycle()`. Screens with rich input slice that state
into four buckets — editable input, derived values as class properties (never
constructor parameters, so no caller can `copy()` into an inconsistent state),
persisted snapshots, and transient UI-only flags.

**One-shot events go on a `Channel`, not a `SharedFlow`.** A `SharedFlow` with
no replay drops an event emitted while the UI is detached, which is the classic
"the snackbar sometimes doesn't appear after rotation" bug. Effects are consumed
in a `LaunchedEffect` scoped with `repeatOnLifecycle`.

**The streaming contract is not negotiable.** `done` does **not** end a turn —
`title` and `stream_end` follow it. Only `stream_end`, `cancel` and `apperror`
close the stream. See [sse-integration.md](sse-integration.md).

## Design decisions

**No offline cache.** The agent runs on the server, so the app is useless
without a connection — there is nothing to do offline. A cached transcript you
can read but not reply to is a worse experience than an honest offline state,
and it would cost a Room schema plus a second source of truth for messages.
Revisit only when a concrete offline need appears.

**Cleartext HTTP is permitted**, with the reasoning in
`app/src/main/res/xml/network_security_config.xml`. The short version: Android's
network security config matches domains, not CIDR ranges, so a user's arbitrary
LAN or tailnet address cannot be a scoped exception. The protection moves to
recommending HTTPS, storing credentials encrypted, and scoping the session
cookie to the configured host.

**A fixed signing key is committed.** CI generates a fresh debug keystore on
every run, and Android refuses to install an APK over a package with a different
signature — so without this, every update would require uninstalling and losing
app state. The key signs sideloadable builds only.
