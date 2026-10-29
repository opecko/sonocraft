# Sonocraft

A Fabric mod that brings [Sonolith](https://github.com/opecko/sonolith), a desktop YouTube Music client for Linux and
Windows, into Minecraft: a customisable Now Playing HUD with synced lyrics, playback keys, and an in-game music
library.

Sonocraft uses Sonolith's local API. It is not made or endorsed by Sonolith.

## Features

- **Now Playing HUD**: album art, title, artist, album, time, progress bar and a play/pause icon. Every part can be
  switched on or off and resized. The panel has presets (Minimal, Compact, Full), anchors, and a HUD editor: drag the
  panel, scroll to scale it, click any part to edit it.
- **Synced lyrics** in the HUD, highlighted word by word when the lyrics have word timings.
- **Accent colour** taken from the album art (or picked by hand), used across the HUD and the library.
- **Playback keys**: play/pause, next, previous, and optional keys to toggle the HUD or open its editor.
- **Music library** (press `J`): mini player with seek and volume, now playing with full lyrics, queue (drag to
  reorder), search with suggestions, your playlists, liked songs, albums, artists, podcasts, history, and album,
  artist and playlist pages. Right-click anything for its actions: play next, add to queue, add to a playlist, like.
- **Config** with [YACL](https://modrinth.com/mod/yacl), through Mod Menu. With [OneConfig](https://polyfrost.org)
  installed, the config shows up in OneConfig, and the HUD can be placed and edited in OneConfig's HUD editor.
- English and Czech.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.11, 26.1–26.1.2, 26.2, 26.3 (one jar per line) |
| Loader | Fabric Loader 0.20.1+ |
| Required mods | Fabric API, YetAnotherConfigLib (YACL) v3 |
| Optional | Mod Menu, OneConfig |
| Sonolith | 2.4.0 or newer, with Settings → Connected apps → "Let other apps connect to Sonolith" on |

## Connecting to Sonolith

1. Start Sonolith and turn on "Let other apps connect to Sonolith" (Settings → Connected apps).
2. In Minecraft, open Sonocraft's config → Connection → **Manage connection…** → **Connect to Sonolith** (or type
   `/sonocraft connect`).
3. Sonolith asks whether to allow Sonocraft. Optional permissions can be unticked; Sonocraft hides what it may not use
   and offers **Allow** later.

Sonocraft connects again on its own when the game starts. **Disconnect from Sonolith** (same screen) removes it from
Sonolith and forgets its access.

### Which program Sonolith sees

Sonolith identifies apps by the program that connects. For Sonocraft that is the Java runtime your launcher starts
Minecraft with (`java` / `javaw.exe`). Instances that use a different Java (Minecraft 1.21.11 runs on Java 21, 26.x
on Java 25) look like a moved app to Sonolith: it asks you once to confirm "Sonocraft was updated or moved", and then
the same access works from there too.

### Where the access is stored

Sonolith gives Sonocraft a token once, on approval. Sonocraft keeps it for your user, not per instance, so all your
instances share one connection:

- Linux: the Secret Service (via `secret-tool`), or `~/.local/share/sonocraft/token` (mode 0600) when there is none.
- Windows: `%APPDATA%\Sonocraft\token.dpapi`, encrypted with DPAPI for your Windows user.

The token is never written to logs or shown anywhere.

## Keys

| Action | Default (Czech QWERTZ) | Same key on US QWERTY |
|---|---|---|
| Play / pause | `=` | `-` |
| Next | `)` | `]` |
| Previous | `ú` | `[` |
| Open the library | `J` | `J` |
| Show / hide HUD, open HUD editor | unbound | |

Change them in Options → Controls → Key Binds → Sonocraft.

## Commands

`/sonocraft` (status), `/sonocraft connect`, `/sonocraft disconnect`, `/sonocraft cancel`, `/sonocraft config`,
`/sonocraft connection`, `/sonocraft hud` (HUD editor), `/sonocraft library`.

## Building

Needs JDK 21 and JDK 25 (Gradle finds installed JDKs, or downloads them).

```
./gradlew build                       # all Minecraft versions, with tests
./gradlew :26.3:runClient             # a development client
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code works and [CONTRIBUTING.md](CONTRIBUTING.md) for
how it is written.

## Credits

- [Sonolith](https://github.com/opecko/sonolith) and its local API.
- UI icons based on [pixelarticons](https://github.com/halfmage/pixelarticons) by Gerrit Halfmann (MIT, see
  `src/main/resources/assets/sonocraft/textures/gui/LICENSE-pixelarticons.txt`).

## License

MIT, see [LICENSE](LICENSE).
