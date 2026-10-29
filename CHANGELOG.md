# Changelog

## 0.1.0-beta.1

First beta. Needs Sonolith 2.4.0 or newer with "Let other apps connect to Sonolith" on.

Minecraft 1.21.11, 26.1–26.1.2, 26.2 and 26.3 (one jar each), Fabric. Requires Fabric API and YetAnotherConfigLib
(YACL) v3; Mod Menu and OneConfig are optional.

### Features

- Connection to Sonolith's local API: approval in Sonolith, automatic reconnect, "Allow" for more permissions,
  "Disconnect from Sonolith". The access token is stored in the Secret Service (Linux) or with DPAPI (Windows).
- Now Playing HUD: album art, title, artist, album, time, progress bar, play/pause icon, synced lyrics with word
  highlighting. Every part can be switched on, off and resized; presets Minimal, Compact, Full.
- HUD editor: drag to move, scroll to scale, click a part to edit it beside the preview.
- Accent colour from the album art or picked by hand, with smooth transitions.
- Music library (J): mini player, now playing with full lyrics, queue with drag to reorder, search with suggestions,
  playlists, liked songs, albums, artists, podcasts, history, album/artist/playlist/podcast pages, context menus,
  playlist editing.
- Playback keys: play/pause, next, previous; optional keys to toggle the HUD and open its editor.
- Config with YACL through Mod Menu; with OneConfig, the config and the HUD (with its settings) show up in OneConfig.
- English and Czech.

### Known limitations

- Tested in game on Linux. Windows passes automated tests (named pipe, DPAPI) but hasn't been tried with a real
  Sonolith yet; reports welcome.
- Podcast episodes play on click; there is no episode page yet.
- Sonolith's API doesn't say where the queue comes from, so "Playing from" shows only when Sonocraft started playback.
