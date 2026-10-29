# Architecture

How Sonocraft is put together: the build, the packages, the threads, and how data flows from Sonolith's socket to
the HUD. The Sonolith local API itself is specified in Sonolith's
[`api/SPEC.md`](https://github.com/opecko/sonolith/blob/master/api/SPEC.md); section numbers below (`SPEC §6`) refer
to it.

## Build: one source tree, four Minecraft versions

The build uses [Stonecutter](https://stonecutter.kikugie.dev/) to compile the same sources for every supported
Minecraft line:

| Node | Minecraft | Loom plugin | Java |
|---|---|---|---|
| `1.21.11` | 1.21.11 | `fabric-loom-remap` (obfuscated, Mojang mappings) | 21 |
| `26.1` | 26.1 – 26.1.2 | `fabric-loom` (not obfuscated) | 25 |
| `26.2` | 26.2.x | `fabric-loom` | 25 |
| `26.3` | 26.3.x | `fabric-loom` | 25 |

- `settings.gradle.kts` declares the nodes and picks `fabric.gradle.kts` or `fabric.obf.gradle.kts` for each.
- `buildSrc/src/main/kotlin/sonocraft-common.gradle.kts` holds what all nodes share: dependencies, Java toolchain,
  `fabric.mod.json` expansion, and two dev-only switches (`-Psonocraft.username=<name>`, `-Psonocraft.oneconfig`).
- `versions/<node>/gradle.properties` holds the per-node versions (Minecraft, Fabric API, YACL, Mod Menu).
- `stonecutter.gradle.kts` is the controller; `26.3` is the active version, so the checked-in sources are the 26.3
  variant and older variants sit in Stonecutter comments.

Version differences are kept small and in one place, see [Version differences](#version-differences).

## Packages

All code is in `dev.opecko.sonocraft`.

| Package | What it is | Minecraft imports |
|---|---|---|
| `api` | Sonolith client: method table with rate classes and scopes, errors, `SonolithApi` (typed calls), `Pager`, `Json` | no |
| `api.model` | Records for the API's data types (`Track`, `PlayerState`, `Lyrics`…); `Item`/`MediaItem` are sealed by `type` | no |
| `api.rpc` | `LineReader` (framing) and `RpcConnection` (JSON-RPC over one connection) | no |
| `api.session` | `SonolithSession`: registration, hello, reconnect, revoke; backoff, rate limiter, status types | no |
| `platform` | Discovery, transports (`UnixTransport`, `PipeTransport`), `Connector` | no |
| `platform.token` | Token stores: Secret Service, DPAPI, file, fallback | no |
| `state` | Stores fed by Sonolith events: `PlayerStore`, `LyricsStore`, `PlayingFrom` | no |
| `theme` | Colour maths (OKLab, contrast) and `ThemeManager` (accent from art or config) | no |
| `art` | Artwork: URL sizing, download, decode, palette, `ArtTextures` (LRU of textures), `ArtTracker` | partly |
| `config` | `SonocraftConfig`/`HudConfig` (the model), `ConfigStore` (JSON), `ConfigScreen` (YACL), Mod Menu entrypoint | screen only |
| `hud` | `HudLayout` (pure layout maths), `HudRenderer`, `HudEditorScreen`; `hud.editor` is the editor's side panel | yes |
| `ui` | Library screen: `LibraryScreen`, `ItemActions`, `Loader`/`PagedLoader`, `ConnectionScreen`; `ui.page` and `ui.widget` | yes |
| `compat` | Everything that differs between Minecraft versions: `Mc`, `Gfx`, `Registrations` | yes |
| `compat.oneconfig` | OneConfig HUD integration, loaded only when OneConfig is installed | yes |
| `input`, `command` | Keybinds, `/sonocraft` | yes |

Packages marked "no" must not import `net.minecraft`. That keeps them testable with plain JUnit and the same on every
Minecraft version.

`SonocraftClient` is the Fabric entrypoint. It creates `SonocraftCore`, which owns the shared services (config,
session, API, stores, theme, artwork, HUD renderer), and registers keys, the HUD element, commands and lifecycle
hooks.

## Talking to Sonolith

```
Sonolith ──socket/pipe──▶ Transport ──▶ LineReader ──▶ RpcConnection ──▶ SonolithSession ──▶ listeners
                                                        (responses)        (events)         PlayerStore, LyricsStore…
```

### Transport and discovery (SPEC §3)

- `Discovery` reads `discovery.json`: `$XDG_RUNTIME_DIR/sonolith/` on Linux (falling back to `/tmp/sonolith-<uid>/`
  after checking it is owned by the user with mode 0700), `%LOCALAPPDATA%\Sonolith\api\` on Windows. On Windows the
  pipe name is only ever taken from that file.
- `UnixTransport` uses `java.nio` Unix domain sockets. `SocketChannel` has separate read and write locks, so a blocked
  read never holds up a write.
- `PipeTransport` opens the named pipe through JNA with `FILE_FLAG_OVERLAPPED`: each direction has its own
  `OVERLAPPED`, event and native buffer, so reading and writing run at the same time. `OVERLAPPED` is passed as a raw
  `Pointer` (a JNA `Structure` would write its stale Java fields over what the kernel is filling in). Before any token
  is sent it checks `GetNamedPipeServerProcessId` against the `pid` in `discovery.json`.
- Every failure to reach Sonolith is a `NotAvailableException`. A stale socket file is never deleted.

### JSON-RPC (SPEC §4)

`RpcConnection` runs two threads per connection: a reader that only parses lines and hands them on (Sonolith drops
clients that stop reading for 10 s), and a writer fed from a queue (so `call()` never blocks). It matches responses by
id, keeps at most 16 requests in flight and queues the rest, times calls out after 35 s, and turns error objects into
`SonolithException` with the error code, `data.reason` and helpers like `retryAfterMs()`.

### Session (SPEC §6, §7, §9)

`SonolithSession` runs its loop on the `Sonocraft-IO` thread. The states (`SessionState`) and why it is in them
(`StatusReason`, used for the localised text) are published as an immutable `SessionStatus`.

```
NOT_CONNECTED ──connect()──▶ CONNECTING ──app.register──▶ REGISTERING ──approved──▶ (token saved) ─┐
      ▲                          │                             │ denied/expired → COOLDOWN          │
      │                          │ not available               │ blocked → BLOCKED                  ▼
      │                          ▼                                                      session.hello
      │                     UNAVAILABLE ◀──connection lost──── CONNECTED ◀─────────────────────────┘
      │                    (backoff 1–60 s)                        │  identityChanged → IDENTITY_PENDING
      └──── Unauthorized / NotRegistered / session.revoked / disconnect() (token deleted)
```

Rules the session keeps, all from the spec:

- It registers only after `connect()`, i.e. a user action; never on its own, never again after a revoke or denial.
- The token is saved before anything else when approval arrives; it is deleted on `Unauthorized`, `NotRegistered`,
  `Blocked`, `session.revoked` and disconnect (also when Sonolith is down).
- `call()` refuses locally what Sonolith would refuse anyway: no connection, a scope that isn't granted
  (`PermissionDenied`), or an empty rate bucket (`RateLimiter`, the spec's classes with their bursts). A
  `RateLimited` answer holds that class for `retryAfterMs`.
- Topics are subscribed from the granted scopes, again after every reconnect and when scopes are added.

Listeners (`SessionListener`) are called on the `Sonocraft-Events` thread. `disconnect()` runs on `Sonocraft-Action`.

## Threads

| Thread | Does | May block |
|---|---|---|
| Render thread | Draws HUD and screens, handles input, keybinds, ticks | never on I/O |
| `Sonocraft-IO` | Session loop: connect, register, hello, wait | yes |
| `Sonocraft-IO-Reader-N`, `-Writer-N` | One pair per connection | read/write only |
| `Sonocraft-Events` | Session events and listeners, token deletion | briefly |
| `Sonocraft-Action` | Disconnect | yes |
| `Sonocraft-Art-N`, `Sonocraft-Art-Now-N` | Artwork download and decode (the playing item has its own thread) | yes |
| `Sonocraft-RpcTimer` | Call timeouts | no |

Anything that changes Minecraft state goes back to the render thread with `Minecraft.getInstance().execute(...)`.
Stores (`PlayerStore`, `LyricsStore`) swap immutable snapshots in an `AtomicReference`, so the render thread reads
them without locks. `Loader`, `PagedLoader` and `ItemActions.call` deliver results on the render thread.

## State

- `PlayerStore`: the player from `player.stateChanged`, `player.trackChanged`, `player.position`,
  `player.volumeChanged`. The position is extrapolated locally (`positionMs + (now − positionAt) × rate` while playing),
  so time and progress move smoothly between events. The playback status is taken from `player.stateChanged` only;
  a position event sent during a seek can still say "paused".
- `LyricsStore`: synced lyrics (word or line tier) from `lyrics.changed`. The current line and word are computed from
  the extrapolated position; `lyrics.line` corrects the line for a moment, matched by its start time (Sonolith's index
  counts lines without timings, which Sonocraft drops).
- `PlayingFrom`: the collection the queue was started from, known only when Sonocraft started it.

## Artwork and colour

`ArtUrls` asks googleusercontent for the exact size as JPEG (`=wN-hN-l90-rj`), so ImageIO can decode it without a
WebP plugin; video thumbnails are cropped to a square. `ArtTextures` downloads on a pool, decodes, computes the accent
(`ArtPalette`: colour buckets scored by share and saturation, greys ignored), uploads a `DynamicTexture` on the render
thread, keeps an LRU of 192 entries and releases evicted textures. Rounded corners are separate textures made from the
kept pixels. The HttpClient has its own executor; sharing the download pool once deadlocked every download.

`ThemeManager` turns the art colour (or the configured one) into an `AccentPalette` with at least 3:1 contrast against
the dark background, and fades between colours in OKLab.

## HUD

`HudLayout` is plain maths with tests: given the config and the texts, it places every part in the panel's own
coordinates (`compute`), then places the scaled panel on screen from its anchor and offset (`place`). `HudRenderer`
draws from it every frame: background, art with crossfade, status icon, text lines that scroll when too long, progress
bar, lyrics with word highlighting. It fades in and out, rebuilds texts only when the item or the second changes, and
allocates nothing per frame on the steady path.

`HudEditorScreen` edits the HUD on screen: drag to move (stored as the nearest of nine anchors plus an offset, so the
HUD stays put when the window is resized), scroll to scale, click a part to show its settings in `hud.editor.SettingsPanel`.

## Library UI

`LibraryScreen` is one `Screen` with a sidebar, a stack of pages (`ui.page.Page`) with Back, a mini player, a
notification bar, a context menu and dialogs. Pages get what they need through `UiContext`. Lists use
`ui.widget.ScrollList` (only visible rows are drawn, more pages load near the end, rows can be dragged). Loading,
empty, error, missing-permission and signed-out states come from `Loader` / `PagedLoader`. `ItemActions` holds what
clicking an item does and its context menu; actions whose scope isn't granted are shown greyed with the reason.

The queue page loads the whole queue and refreshes it in the background on `queue.changed`. Sonolith has no "play this
entry", so a click skips forward to it (`player.next` up to 15 times), or moves it after the current entry.

## Config

`SonocraftConfig` (with `HudConfig`) is the model; `ConfigStore` reads and writes `config/sonocraft.json` with Gson,
keeps a broken file as `.broken`, and saves atomically. YACL only builds the screen (`ConfigScreen`). Position and
scale of the HUD are only edited in the HUD editors, because a YACL screen would write its stale copy back on save.
`SonocraftCore.applyConfig()` pushes config values into services that cache them (theme, subscription rate).

## OneConfig

OneConfig is optional and never a hard dependency. Its YACL compat picks up `ConfigScreen` through the Mod Menu
entrypoint. `compat.oneconfig.OneConfigHud` implements OneConfig's `OneConfigHudWrapper`, so the panel appears in
OneConfig's HUD editor (`ownsPlacement`: Sonocraft keeps the position), and `HudProperties` puts the HUD settings into
its side panel. The OneConfig API jars are extracted from the OneConfig jar at build time and used `compileOnly`; the
classes are only loaded when `FabricLoader.isModLoaded("oneconfig")`.

## Version differences

Everything that differs between Minecraft versions is in `compat`, written with Stonecutter conditions:

| What | 1.21.11 | 26.1 | 26.2 | 26.3 |
|---|---|---|---|---|
| GUI drawing | `GuiGraphics.drawString` | `GuiGraphicsExtractor.text` | ← | ← |
| HUD element / screen render | `render` | `extractRenderState` | ← | ← |
| Key mapping helper | `KeyBindingHelper` | `KeyMappingHelper` | ← | ← |
| Client commands | `ClientCommandManager` | `ClientCommands` | ← | ← |
| Toasts, screen, overlay | `Minecraft`, `Gui` | ← | `Gui` + `Hud` split | ← |
| Key and mouse codes | GLFW | ← | ← | SDL |

Key and mouse codes are always written as `InputConstants` constants (`KEY_J`, `MOUSE_BUTTON_LEFT`); javac puts the
right value into each node. 26.3 uses SDL numbers (`KEY_J` = 13, left button = 1).

## Tests

`src/test` runs with JUnit on every node, without Minecraft. `testing.FakeSonolith` is a scripted Sonolith on a Unix
socket (registration outcomes, identity changes, revokes, events); the session, RPC, framing, discovery, token store,
stores, layout, colour and artwork code are tested against it or directly. Windows-only code is tested on Windows in
CI.
