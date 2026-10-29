# Sonocraft — plán implementace

**Sonocraft** (velké S): takhle se mod jmenuje všude, kde ho vidí uživatel (`name` ve `fabric.mod.json`, název
v consent screenu Sonolithu, kategorie keybindů, titulky obrazovek, lang soubory, Modrinth). Malé `sonocraft` se
použije jen tam, kde to technicky vyžaduje Fabric, tedy mod id (`[a-z0-9_-]`), namespace assetů
(`assets/sonocraft/…`) a název configu.

| Údaj | Hodnota |
|---|---|
| Název | **Sonocraft** |
| Mod id | `sonocraft` (jen technicky, nikde se nezobrazuje) |
| Sonolith `appId` | `dev.opecko.sonocraft` |
| Java balíček | `dev.opecko.sonocraft` (stejné jako appId) |
| Publisher | `opecko` |
| Licence | MIT |

Fabric mod pro Minecraft, plnohodnotný klient pro **Sonolith local API v1.0** (desktopový YouTube Music klient,
https://github.com/opecko/sonolith). V tomhle souboru je jen plán, kód zatím neexistuje.

> Průzkum proběhl 2026-10-29. Verze knihoven a MC se mění, takže **na začátku implementační session ověř aktuální
> verze** (Fabric meta, Modrinth, Polyfrost repo) a uprav tabulky níž.

---

## 0. Shrnutí požadavků

| # | Požadavek | Kde v plánu |
|---|---|---|
| 1 | Fabric, MC **1.21.11, 26.1.x, 26.2.x, 26.3.x** (všechny patch verze, latest = 26.3) | §2 |
| 2 | Cross-platform Windows + Linux | §3.1, §3.4 |
| 3 | Plnohodnotný klient celého Sonolith API | §3, §6 |
| 4 | Customizovatelný „Now Playing“ HUD (art, artist, track, album, délka, progress bar, lyrics, ikona play/pause, velikosti) | §5 |
| 5 | Keybind (nastavitelný v Controls) otevře UI knihovny, ovládání myší | §6 |
| 6 | In-game keybindy: play/pause `=`, next `)`, prev `ú` (česká klávesnice), library `J` | §7 |
| 7 | Accent color: ruční, nebo automaticky z album artu, aplikovaná v celém modu | §8 |
| 8 | Config knihovna, která se integruje do OneConfig, ale funguje i bez něj | §9 |

---

## 1. Klíčová rozhodnutí (TL;DR)

- **Jazyk modu: Java** (žádný Kotlin v modu, aby nebyla nutná Fabric Language Kotlin). Gradle skripty jsou
  v Kotlin DSL (`*.gradle.kts`), protože Stonecutter je na ně stavěný a vzorový OneConfig setup se dá převzít
  skoro 1:1.
- **Multi-version build: Stonecutter** (kikugie, 0.9.x). Stejný nástroj používá OneConfig pro stejnou sadu verzí.
- **Mapování: Mojang (official) všude.** 26.x je neobfuskovaná a používá Mojang názvy, takže i 1.21.11 se mapuje
  přes `loom.officialMojangMappings()`. Zdrojový kód je pak mezi verzemi skoro stejný a Stonecutter podmínky se týkají
  jen skutečných změn API.
- **Config: YetAnotherConfigLib (YACL) v3** + ModMenu entrypoint. OneConfig v1 má vestavěnou YACL kompatibilitu
  (`YACLCompat.kt`) a vlastní shim ModMenu API. Když hráč OneConfig má, náš config se automaticky objeví v OneConfig
  GUI. Když ho nemá, otevře se YACL screen z ModMenu nebo z našeho UI. Detaily v §9.
- **Transport:** Linux přes `java.nio` `UnixDomainSocketAddress` (Java 16+, žádná nativní knihovna). Windows přes
  named pipe s overlapped I/O (viz §3.1).
- **JSON: Gson** (je v MC). **HTTP pro artwork:** `java.net.http.HttpClient`.
- **Žádný OneConfig v build classpath** jako hard dependency. Případná přímá integrace jen `compileOnly` a za
  `FabricLoader.isModLoaded("oneconfig")`.

---

## 2. Verze a build

### 2.1 Cílové verze (stav k 2026-10-29)

Mojang manifest: releasy `26.3`, `26.2`, `26.1.2`, `26.1.1`, `26.1`, `1.21.11`. Latest release je **26.3** (zatím
bez patche, snapshot 26.4-snapshot-6).

| Stonecutter node | Pokrývá (`fabric.mod.json` `depends.minecraft`) | Loom plugin | Java |
|---|---|---|---|
| `1.21.11` | `1.21.11` | `net.fabricmc.fabric-loom-remap` (obfuskovaná verze) | 21 |
| `26.1` | `>=26.1 <26.2` (26.1, 26.1.1, 26.1.2) | `net.fabricmc.fabric-loom` (neobfuskovaná) | 25 |
| `26.2` | `>=26.2 <26.3` | `net.fabricmc.fabric-loom` | 25 |
| `26.3` | `>=26.3 <26.4` (budoucí 26.3.x automaticky) | `net.fabricmc.fabric-loom` | 25 |

**Ověřeno 2026-10-29 (fáze 1):** Gradle 9.8.2, Loom 1.19.3, Stonecutter 0.9.8, Loader 0.20.1, JUnit 6.1.3.

| Node | Fabric API | YACL | ModMenu |
|---|---|---|---|
| `1.21.11` | 0.141.6+1.21.11 | 3.8.2+1.21.11-fabric | 17.0.1 |
| `26.1` | 0.155.3+26.1.2 (podporuje 26.1–26.1.2) | 3.9.7+26.1-fabric | 18.0.2 |
| `26.2` | 0.161.0+26.2 | 3.9.7+26.2-fabric | 20.0.3 |
| `26.3` | 0.165.0+26.3 | 3.9.7+26.3-fabric | 21.0.0 |

- Jeden jar na minor verzi. Patch verze sdílí jar, ale **každou patch verzi je potřeba otestovat** (runClient s
  přepsanou verzí, viz §12). Pokud patch rozbije API, přidá se nový node (např. `26.1.2`).
- Když vyjde nová patch verze (26.3.1…), stačí ji otestovat. Rozsah `<26.4` ji už pokrývá.
- Loader: aktuální stable 0.20.1 (min. v `fabric.mod.json` nastavit na nejnižší, který funguje na všech nodech).
- Loom 1.17+ / Gradle 9.6+ (podle Fabric oznámení k 26.3). Ověřit nejnovější.
- Fabric API: verze s příponou `+<mc>` pro každý node. Dohledat přesné verze na Modrinthu.

### 2.2 Struktura projektu (návrh)

```
sonocraft/
  settings.gradle.kts         # Stonecutter: versions 1.21.11, 26.1, 26.2, 26.3; buildscript podle obfuskace
  stonecutter.gradle.kts      # controller: constants/replacements (např. GuiGraphics → GuiGraphicsExtractor)
  build.gradle.kts            # prázdné (nody používají fabric*.gradle.kts)
  buildSrc/src/main/kotlin/sonocraft-common.gradle.kts  # společná logika (deps, Java toolchain, fabric.mod.json)
  fabric.gradle.kts           # pro >=26.1 (fabric-loom)
  fabric.obf.gradle.kts       # pro 1.21.11 (fabric-loom-remap + officialMojangMappings)
  gradle.properties           # společné verze (loader, junit), mod.*
  versions/<node>/gradle.properties  # per node: deps.minecraft, mod.mc_range, mod.java, fabric_api, yacl, modmenu
  src/main/java/dev/opecko/sonocraft/
    api/        # čistý Sonolith klient bez MC importů (testovatelný jednotkovými testy)
    platform/   # transport (unix/pipe), token store (secret service / DPAPI / file)
    state/      # PlayerStore, QueueStore, LyricsStore, interpolace pozice
    art/        # stahování, dekódování, cache textur, extrakce barvy
    hud/        # Now Playing HUD, layout, editor
    ui/         # Library screen, widgety
    input/      # keybindy
    config/     # model configu + YACL screen + ModMenu entrypoint
  src/main/resources/
    fabric.mod.json, sonocraft.mixins.json (jen pokud bude potřeba), assets/sonocraft/{lang,textures}
  src/test/java/…  # testy api/ proti fake serveru
```

- Balíček `api/` a `platform/` **nesmí importovat net.minecraft**. Pak jde testovat bez hry a skoro se nemění mezi
  verzemi.
- Vzor Stonecutter setupu pro přesně tyhle verze: `Polyfrost/OneConfig` větev `v1`, soubory `settings.gradle.kts`,
  `minecraft/stonecutter.gradle.kts`, `gradle/fabric/*.versions.toml`.

### 2.3 Známé rozdíly mezi verzemi (ověřit a doplnit při implementaci)

- **26.2+ používá nativní Vulkan renderer.** Nepoužívat raw GL ani `RenderSystem` přímo. Kreslit jen přes
  `GuiGraphics` / GUI render API a `DynamicTexture`/`TextureManager`. To minimalizuje rozbití.
- **26.3: tlačítka myši mají SDL čísla** (`InputConstants.MOUSE_BUTTON_LEFT` = 1, dřív 0). Nikdy nepsat literál 0/1,
  vždy konstantu (javac ji dosadí per node). Stejně tak klávesy (`KEY_J` = 13 v 26.3).
- **26.3: GLFW nahrazeno SDL** pro okno a klávesy. Používat `InputConstants`, nikdy `GLFW.*` konstanty. Mouse
  button konstanty se změnily. Vlastní textová pole musí hlásit focus `TextInputManager`
  (`Minecraft#onTextInputFocusChange`), jinak nefunguje psaní (týká se search pole v §6).
- 26.3 odstranila některá Fabric API rozhraní (registry pro compost/fuel/strippable…), pro nás nepodstatné.
- HUD: Fabric `HudElementRegistry` (fabric-rendering-v1) existuje od 1.21.6. Ověřit, že je ve všech nodech stejné.
- Keybind kategorie: v 1.21.9+ se `KeyMapping.Category` změnila na registrovaný objekt. Ověřit API pro každý node.

---

## 3. Sonolith API klient (vrstva `api/` + `platform/`)

Spec: `api/SPEC.md`, souhrn `api/llms.txt`, strojově `api/openrpc.json` + `api/schema/*.json` v repu Sonolith.
Referenční klient se všemi funkcemi: `api/examples/python-tui`. **Při implementaci ho projít.**

### 3.1 Transport

**Discovery:**
- Linux: `$XDG_RUNTIME_DIR/sonolith/discovery.json`, fallback `/tmp/sonolith-<uid>/discovery.json`. Pokud
  discovery chybí, je povoleno zkusit rovnou `…/api.sock`. U `/tmp` fallbacku ověřit, že adresář patří našemu uid a
  má mód 0700 (`Files.getAttribute(path, "unix:uid")` a `PosixFilePermissions`). uid zjistit přes
  `Files.getAttribute(Path.of("/proc/self"), "unix:uid")`.
- Windows: `%LOCALAPPDATA%\Sonolith\api\discovery.json`, pole `path` je jméno pipe. **Nikdy ho neskládat ručně.**
- Discovery soubor ignoruje neznámá pole. Volitelně sledovat adresář pomocí `WatchService` místo pollingu.

**Linux:** `SocketChannel.open(StandardProtocolFamily.UNIX)` + `UnixDomainSocketAddress.of(path)`. Blokující
čtecí vlákno, zápis serializovaný přes lock nebo frontu.

**Windows named pipe:**
- `RandomAccessFile` na `\\.\pipe\…` **nepoužívat**. Synchronní handle serializuje čtení a zápis, takže blokující
  read v čtecím vlákně zablokuje všechny zápisy.
- Varianta A (preferovaná): **JNA** (MC ji přibaluje, ověřit `jna` + `jna-platform` ve všech verzích):
  `CreateFile(..., FILE_FLAG_OVERLAPPED)`, overlapped `ReadFile`/`WriteFile`. Plus
  `GetNamedPipeServerProcessId` a porovnat s `pid` z discovery **před odesláním tokenu** (SHOULD ve spec).
- Varianta B: `AsynchronousFileChannel.open(Path.of(pipeName), READ, WRITE)` (overlapped I/O pod kapotou). PID
  kontrolu pak dělat přes JNA zvlášť.
- Transport schovat za rozhraní `Transport { connect(); readLine(); writeLine(); close(); }`, implementace
  `UnixTransport`, `PipeTransport`.

**Framing:** jedna JSON zpráva na řádek (`\n`), UTF-8, max **1 MiB**. Čtečka musí zahodit/ukončit spojení při
překročení, ignorovat prázdné řádky a `\r`.

### 3.2 JSON-RPC vrstva

- `RpcClient`: id generátor (int), mapa `id → CompletableFuture<JsonElement>`, params vždy jako objekt.
- Max **16 requestů in-flight** na spojení. Implementovat semafor a frontu.
- Notifikace (zprávy bez `id`) jdou do `EventBus`. Neznámé notifikace a neznámá pole ignorovat.
- Chyby převádět na typovanou `SonolithException(code, name, reason, data)`, kde enum pokrývá všech 13 kódů
  (-32700…-32013).
- Timeout 30 s na odpověď (server odpovídá do 30 s).
- **Čtení nesmí stát:** server odpojí klienta, který 10 s nečte nebo nechá >4 MiB nepřečtených. Čtecí vlákno
  proto jen parsuje a předává dál, nikdy nečeká na herní vlákno.
- Všechny callbacky do MC stavu předávat přes `Minecraft.getInstance().execute(...)` nebo do thread-safe storů
  čtených z render vlákna.

### 3.3 Životní cyklus spojení (stavový automat)

```
DISCONNECTED_NO_TOKEN ──(uživatel klikne "Připojit k Sonolithu")──> REGISTERING (pending, max 5 min, držet spojení!)
REGISTERING ── approved ──> uložit token ──> CONNECTING
            ── denied   ──> COOLDOWN(retryAfterMs, zobrazit odpočet) ──> DISCONNECTED_NO_TOKEN
            ── blocked  ──> BLOCKED (hláška: odblokovat v Sonolith → Settings → Connected apps)
            ── expired  ──> DISCONNECTED_NO_TOKEN (nabídnout znovu)
CONNECTING  ── session.hello OK ──> CONNECTED (subscribe, reset backoff)
            ── endpoint neexistuje ──> UNAVAILABLE (backoff 1 s → 60 s, ±20 % jitter)
            ── Unauthorized/NotRegistered ──> smazat token ──> DISCONNECTED_NO_TOKEN ("Odpojeno od Sonolithu")
            ── RegistrationPending(identityChanged) ──> IDENTITY_PENDING (držet spojení, čekat na kind=identity, pak hello znovu)
            ── UnsupportedVersion ──> ERROR (aktualizujte Sonolith/mod)
CONNECTED   ── session.revoked ──> smazat token ──> DISCONNECTED_NO_TOKEN
            ── spojení spadlo ──> UNAVAILABLE (backoff, po reconnectu znovu subscribe; eventy se nereplayují)
```

- **Nikdy automaticky neregistrovat.** Registrace, `app.requestScopes` a reconnect po cooldownu jen po akci
  uživatele (tlačítko v UI nebo v configu).
- Tlačítko **„Odpojit od Sonolithu“** (povinné): `app.unregister` a smazat token, i když Sonolith neběží.
- Text „Uses Sonolith“ s odkazem na https://github.com/opecko/sonolith vedle tlačítka Disconnect (v configu i v
  Library UI). Nikde nenaznačovat, že mod dělá nebo schvaluje Sonolith.
- Stav spojení zobrazovat v HUD (malá ikonka nebo text, konfigurovatelné) a v Library UI. Např. „Sonolith neběží,
  nebo je vypnuté 'Let other apps connect to Sonolith' v Settings → Connected apps.“
- Sonolith nikdy nespouštět sami. Volitelné tlačítko „Spustit Sonolith“ max. jednou na kliknutí (nízká priorita).
- Spojení se otevírá při startu klienta (pokud je token). Hra nesmí čekat na I/O.

### 3.4 Registrace a identita

- `appId`: **`dev.opecko.sonocraft`** (potvrzeno, po vydání se nesmí měnit).
- `name` **"Sonocraft"**, `publisher` **"opecko"**, `description` jednořádkově bez řídicích znaků, `homepage` = repo
  modu.
- `icon`: PNG data URI ≤ 65 536 znaků (ikona modu zmenšená na 64×64).
- `apiVersion: "1.0"`.
- Scopes (reason = jedna srozumitelná věta):

| Scope | required | Účel v modu |
|---|---|---|
| `player:read` | ano | HUD |
| `player:control` | ano | keybindy, ovládání |
| `lyrics:read` | ne | lyrics v HUD/UI |
| `queue:read` | ne | fronta v UI |
| `queue:write` | ne | úpravy fronty |
| `library:read` | ne | knihovna |
| `search` | ne | vyhledávání, detail alba/umělce |
| `library:write` (sensitive) | ne | like, playlisty |

  Nepovolené scopes: funkci skrýt nebo zašednout a nabídnout „Povolit“ (`app.requestScopes` po kliknutí).
  Reagovat na `session.scopesChanged` a `PermissionDenied`.
- **Identita = `exe:<cesta k java/javaw>`.** Java není v seznamu interpretů. Důsledky:
  - Různé MC instance s různým JRE (launcher runtime pro 1.21.11 = Java 21, pro 26.x = Java 25) mají **různé
    identity**. Při přepnutí instance Sonolith zobrazí „Sonocraft was updated or moved“.
  - Mod musí stav `IDENTITY_PENDING` zvládnout uživatelsky přívětivě: toast „Potvrď Sonocraft v Sonolithu“ a
    držet spojení.
  - Token ukládat **globálně pro uživatele, ne per instance** (viz §3.5). Jinak by každá instance registrovala
    zvlášť a další registrace by invalidovala předchozí token („tokens end when the app registers again“).
  - Zdokumentovat v README. Ověřit chování v praxi s Prism/Modrinth/oficiálním launcherem.

### 3.5 Úložiště tokenu (MUST: nečitelné pro ostatní uživatele, nikdy nelogovat ani nezobrazovat)

- Rozhraní `TokenStore { load(); save(); delete(); }`, umístění globální:
  - Linux: `$XDG_DATA_HOME/sonocraft/` (fallback `~/.local/share/sonocraft/`)
  - Windows: `%APPDATA%\Sonocraft\`
- Windows: **DPAPI** (`Crypt32Util.cryptProtectData` z jna-platform, user scope) a zašifrovaný blob v souboru.
- Linux: **Secret Service**. Možnosti: (a) `secret-tool store/lookup`, token se předává přes **stdin**, nikdy v
  argumentech; (b) D-Bus knihovna (těžká, nechceme). Pokud `secret-tool` není nebo selže, fallback na soubor s
  módem **0600** (vytvořit atomicky s `PosixFilePermissions`).
- Token mazat po `app.unregister`, `session.revoked`, `Unauthorized`, `NotRegistered`.
- Logger: nikdy nelogovat `session.hello` params ani `registration.resolved` params.

### 3.6 Eventy a stav

- Po hello subscribe: `player.state`, `player.track`, `player.position` (`positionHz` 4 jen při viditelném
  progress baru, jinak 1), `player.volume`, `account`, podle scopes `queue`, `lyrics`, `lyrics.line`
  (`lyrics.word` jen když je zapnutý word-level karaoke).
- Po subscribe chodí snapshot každého topicu. Netřeba volat `getState`, ale volat ho při reconnectu je OK.
- **Pozice se interpoluje lokálně:** `positionMs + (now - positionAt) * rate`, jen když `status == playing`.
  Clamp na `durationMs`. Pozor na posun hodin: `positionAt` je epoch ms ze stejného stroje, takže OK.
- Lyrics: při `lyrics.changed` uložit celé lyrics. Aktuální řádek a slovo počítat lokálně z interpolované pozice
  (plynulé). `lyrics.line` a `lyrics.word` brát jako korekci.
- Stores jsou immutable snapshoty vyměňované atomicky (`AtomicReference`), takže render vlákno čte bez zámků.

### 3.7 Rate limity (klient je musí respektovat)

- Třídy: local 50/s, control 10/s, network 2/s, account 1 za 2 s, auth 1/s.
- Klientský token bucket per třída. Search: debounce psaní (~400 ms) a suggestions max 2/s. Seek tažením myši:
  posílat jen při puštění, případně throttle.
- `RateLimited` → počkat `data.retryAfterMs`. `Unavailable` / `Timeout` → retry s backoffem, kde to dává smysl.
- Klasifikaci metod do tříd vzít ze SPEC §12.1.

### 3.8 Typy

- Java `record`y podle schémat (Track, Episode, Album, Playlist, Artist, Podcast, PlayerState, QueueEntry, Lyrics…).
  `MediaItem` a `Item` jsou sealed interface podle `type`.
- Vše nullable podle schématu. `*Text` pole jen zobrazovat, nikdy neparsovat. Vždy držet `type` spolu s `id`.
- Paging helper: `Pager<T>` (cursor, limit 50, `nextCursor == null` = konec, `cursorExpired` = restart).
- Zvážit generování recordů z `api/schema/*.json` nebo `openrpc.json`. Ručně je to ale ~30 typů, dá se zvládnout.

---

## 4. Artwork (album art)

- `thumbnailUrl` je HTTPS (lh3.googleusercontent…) a typicky končí `=w544-h544`. Velikost jde měnit přepisem
  sufixu podle velikosti HUD (např. `=w256-h256`). Fallback na původní URL. Nepoužívat URL jako id.
- Formát bývá JPEG nebo WebP. `NativeImage.read` umí jen PNG, proto:
  - dekódovat přes `javax.imageio.ImageIO` (JPEG/PNG), pak převést pixely do `NativeImage`, a nebo
  - požádat googleusercontent o PNG (sufix `-rp`, ověřit) a číst přímo přes `NativeImage`.
  - WebP: ImageIO ho nativně neumí. Vynutit JPEG/PNG sufixem a mít fallback „bez artu“.
- Stahování asynchronně (`HttpClient.sendAsync`). Registrace `DynamicTexture` na render vlákně.
- LRU cache textur (např. 32 položek). Při eviction `close()` / release textury. Placeholder textura, když art není.
- Diskový cache volitelně (`.minecraft/cache/sonocraft/`), nízká priorita.
- Současně s dekódováním spočítat **paletu** pro accent color (§8), ať se obrázek nečte dvakrát.

---

## 5. Now Playing HUD

### 5.1 Prvky (každý jde zapnout/vypnout a má vlastní velikost)

| Prvek | Nastavení |
|---|---|
| Album art | zap/vyp, velikost v px (16–128), zaoblení (aproximace), stín |
| Track name | zap/vyp, škála textu, max šířka, marquee scroll dlouhých názvů (rychlost) |
| Artist(s) | zap/vyp, škála, spojení více artistů („, “) |
| Album | zap/vyp, škála (u epizod: podcast) |
| Délka / čas | zap/vyp, formát: `elapsed / total`, `elapsed / -remaining`, jen total |
| Progress bar | zap/vyp, šířka (nebo „podle panelu“), výška, barva = accent |
| Ikona stavu | zap/vyp, velikost. `playing` → ikona **playing**, `paused` → ikona **pause**, `buffering` → spinner, `stopped` → stop |
| Lyrics | zap/vyp, počet řádků (aktuální ± předchozí/další), škála, zvýraznění aktuálního slova (word tier), fade |
| Explicit/like badge | zap/vyp (nízká priorita) |
| Stav spojení | kdy zobrazit (nikdy / jen při chybě / vždy) |

Ikonu play/pause doporučuju kreslit podle **skutečného stavu** (hraje → ikona „playing“, pauza → ikona „paused“),
přesně jak bylo zadáno. Textury v `assets/sonocraft/textures/gui/` (vlastní pixel-art, 16×16 a 32×32).

### 5.2 Layout

- Pozice: kotva (9 rohů a hran obrazovky) + offset X/Y v px, globální scale, rozložení horizontální/vertikální,
  zarovnání textu, padding, pozadí (barva + průhlednost, nebo accent s alfou), rámeček.
- **HUD editor screen** (otevře se z configu i z Library UI): přetahování panelu myší, scroll = scale, snapping
  k okrajům a středu, náhled s ukázkovými daty, když nic nehraje.
- Presety: Minimal (ikona + název), Compact (art + název + artist + bar), Full (vše + lyrics).
- Viditelnost: skrýt při F1, při otevřeném debug screenu, když nic nehraje, po N s pauzy (konfigurovatelné),
  volitelně skrýt v menu nebo jiných obrazovkách.
- Plynulé animace: přechody při změně tracku (crossfade artu), interpolovaný progress bar, fade lyrics.
- Registrace přes Fabric HUD API (`HudElementRegistry`), vykreslení po vanilla HUD.
- Výkon: žádné alokace v render loopu. Text layout cacheovat při změně tracku nebo configu.

---

## 6. Library UI (Screen)

Otevírá se keybindem (§7). Celé ovladatelné myší (+ klávesnice: Esc, šipky, Enter, Ctrl+F na search).

### 6.1 Rozvržení

```
┌─────────────┬──────────────────────────────────────────────┐
│ Sidebar     │ Obsah (podle záložky, scrollovatelné)        │
│ ▸ Now Playing│                                              │
│ ▸ Queue     │                                              │
│ ▸ Search    │                                              │
│ ▸ Playlists │                                              │
│ ▸ Liked     │                                              │
│ ▸ Albums    │                                              │
│ ▸ Artists   │                                              │
│ ▸ Podcasts  │                                              │
│ ▸ For later │                                              │
│ ▸ History   │                                              │
│ ⚙ Settings  │                                              │
├─────────────┴──────────────────────────────────────────────┤
│ Mini přehrávač: art | název/artist | ⏮ ⏯ ⏭ | seek bar | 🔀 🔁 | 🔊 | ♥ │
└────────────────────────────────────────────────────────────┘
```

### 6.2 Funkce → API metody (cíl: pokrýt celé API)

| Oblast | UI | Metody |
|---|---|---|
| Přehrávání | play/pause/next/prev, seek tažením, volume slider + mute, shuffle, repeat (off/all/one), rychlost u epizod | `player.*` (všechny) |
| Now Playing | velký art, metadata, lyrics full view (sync, klik na řádek = seek, je-li čas) | `player.getState`, `lyrics.get`, eventy |
| Fronta | seznam, drag&drop přesun, odebrat, vyčistit, autoplay toggle, klik = přehrát od položky | `queue.get/move/remove/clear/setAutoplay`, `player.playItem` (startId) |
| Kontextové akce (pravé tlačítko / „⋯“) | Přehrát, Přehrát další, Přidat do fronty, Přidat do playlistu, Like/Dislike, Otevřít album/umělce | `player.playItem`, `queue.playNext`, `queue.add`, `playlist.addTracks`, `rating.set` |
| Search | pole + našeptávač, filtr typu (all/songs/videos/albums/artists/playlists/podcasts/episodes), top result | `search.query`, `search.suggestions` |
| Knihovna | playlists, liked songs, albums, artists, podcasts, episodes for later, history (sekce podle `sectionText`) | `library.*` |
| Detaily | album (tracky, přehrát vše/shuffle), artist (shelves), playlist (stránkování), podcast + epizody, epizoda | `browse.*`, `playlist.get` |
| Playlisty | vytvořit, přejmenovat, smazat (potvrzovací dialog), odebrat položky | `playlist.create/rename/delete/removeTracks` |
| Účet | hláška „Přihlas se v Sonolithu“ při `NotSignedIn` / `account.signedIn=false` | `account` topic |
| Připojení | Připojit / Odpojit / Povolit další oprávnění / stav, „Uses Sonolith“ | `app.*`, `session.*` |

- Seznamy: virtualizované (kreslit jen viditelné řádky), infinite scroll s `Pager`, thumbnaily lazy přes art cache
  (menší velikost, např. `=w60-h60`).
- Stavy obrazovek: loading skeleton, prázdno, chyba s „Zkusit znovu“, chybějící scope s tlačítkem „Povolit“.
- Navigace: historie zpět (Backspace / tlačítko ←), breadcrumb.
- `queue.changed` s `revision`: při změně zahodit lokální kopii a reloadnout viditelnou stránku.
- Widget toolkit: vlastní lehké komponenty nad `GuiGraphics` (Button, IconButton, Slider, ScrollList, TextField,
  ContextMenu, Tabs, Dialog) stylované accent barvou. Vanilla `EditBox` použít pro textová pole, kvůli IME a
  TextInputManageru v 26.3.
- Obrazovka nepauzuje hru v multiplayeru (`isPauseScreen` konfigurovatelně, default false).

---

## 7. Keybindy

Registrace přes Fabric `KeyBindingHelper`, vlastní kategorie **„Sonocraft“**. Vše přenastavitelné v Options →
Controls.

| Akce | Default (CZ klávesnice) | GLFW/InputConstants kód | Pozn. |
|---|---|---|---|
| Play/Pause | `=` | `KEY_MINUS` | Na CZ QWERTZ je `=` (vedle `é`) fyzicky na pozici US `-`. Ve vanille volné. |
| Next | `)` | `KEY_RIGHT_BRACKET` | Na CZ QWERTZ je `)` fyzicky na pozici US `]`. |
| Previous | `ú` | `KEY_LEFT_BRACKET` | Na CZ QWERTZ je `ú` fyzicky na pozici US `[`. |
| Otevřít Library UI | `J` | `KEY_J` | Ve vanille volné. |
| Toggle HUD, Like, Shuffle, Repeat, Volume ±, Seek ±10 s, Otevřít HUD editor | nenastaveno | – | volitelné |

- **Ověřit na české klávesnici:** GLFW kódy jsou poziční (US layout), takže `-`/`[`/`]` by měly odpovídat
  `=`/`ú`/`)`. Ve hře zkontrolovat, že Controls ukazuje správné znaky a že nic nekoliduje s vanillou ani s
  běžnými mody.
  **V 26.3 je místo GLFW SDL.** Je potřeba zkontrolovat, jestli `InputConstants` mapuje scancode (poziční) nebo
  keycode (podle layoutu). Kdyby podle layoutu, default pro 26.3 nastavit jinak (Stonecutter podmínka).
- Keybindy fungují jen ve hře (žádný otevřený screen, žádný chat), to KeyMapping řeší sám. V Library UI fungují
  přes vlastní handling.
- Rate limit control 10/s: držení klávesy neposílat opakovaně (jen při stisku).
- Feedback: krátký toast nebo overlay „⏸ Paused“ / „⏭ Next: …“ (konfigurovatelné).

---

## 8. Accent color

- Režimy (config): **Manual** (color picker v YACL) / **From album art** / **From album art, fallback manual**.
- Extrakce z artu: downscale na ~32×32, kvantizace (median cut nebo k-means, k≈5), skóre = zastoupení × sytost,
  penalizace téměř černé, bílé a šedé. Pak úprava jasu, aby byl dostatečný kontrast proti pozadí HUD/UI (cíl
  ≥ 3:1).
- Varianty z jedné barvy: `accent`, `accentDim` (pozadí), `accentBright` (hover), `onAccent` (text černý nebo bílý
  podle luminance).
- Plynulý přechod při změně tracku (lerp v OKLab nebo HSV, ~400 ms).
- Kde se aplikuje: progress bar, ikona stavu, zvýraznění aktuálního řádku a slova v lyrics, pozadí HUD (volitelně
  tónované), hover/selected v Library UI, slidery, focus rámečky.
- `ThemeManager` drží aktuální paletu jako immutable snapshot (čte ho HUD i UI).

---

## 9. Config (YACL + OneConfig)

### 9.1 Proč YACL

Zadání: knihovna, která se integruje do OneConfig, ale funguje i bez OneConfig. Zjištěno ze zdrojáků
`Polyfrost/OneConfig` (větev `v1`, 2026-10):

- OneConfig v1 obsahuje compat vrstvy, které **automaticky převedou cizí configy do OneConfig GUI**: YACL
  (`internal/compat/YACLCompat.kt`, mixin na `YetAnotherConfigLibImpl#generateScreen`), Cloth Config, MidnightLib,
  MoulConfig, Resourceful Config a další.
- YACL compat je zapnutá pro Fabric **1.21.11, 26.1, 26.2 i 26.3** (`gradle/fabric/<ver>.versions.toml`
  obsahují `yacl` v `compat`). Přesně naše verze.
- OneConfig si configy hledá přes **ModMenu entrypointy**. Má vlastní shim ModMenu API (`ModMenuShimLoader`), takže
  to funguje i bez nainstalovaného ModMenu.
- YACL je dostupné na Modrinthu pro 1.21.11, 26.1(.1/.2), 26.2, 26.3, je aktivně vyvíjené a umí color picker,
  slidery, enumy, skupiny a obrázky v popisech. Cloth Config je ve feature freeze a MidnightLib je příliš
  jednoduchá (chybí color picker s alfou, kategorie se skupinami).

**Rozhodnutí:** YACL (required dependency, ne JiJ, standardní praxe) + `modmenu` entrypoint (ModMenu jen
`compileOnly`, volitelný). Výsledek:
- Bez OneConfig: config přes ModMenu tlačítko, nebo přes ⚙ v Library UI (otevře YACL screen přímo).
- S OneConfig: config se zobrazí v OneConfig menu automaticky (jejich YACL compat).
- **Ověřit při implementaci:** že OneConfig compat převede všechny použité typy controllerů (color s alfou, slider,
  enum cycling, button pro Connect/Disconnect a HUD editor). Pokud něco nepřevede, varianta B: nativní OneConfig
  `ConfigEntrypoint` (`compileOnly`, načtený jen když `isModLoaded("oneconfig")`), který staví strom z našeho
  config modelu. Varianta B je fallback, ne výchozí cesta.

### 9.2 Architektura configu

- Vlastní POJO `SonocraftConfig` jako zdroj pravdy, JSON přes YACL `ConfigClassHandler` (GSON serializer) do
  `config/sonocraft.json`. Žádné tajné údaje v configu (token je jinde, §3.5).
- YACL screen se staví z modelu ručně (builder API), ne přes anotace. Kvůli kontrole nad skupinami, náhledy a
  tlačítky.
- Změny configu se aplikují live (HUD se překreslí okamžitě) a ukládají se při zavření screenu.
- Kategorie:
  1. **Connection**: stav, Connect / Disconnect / Request permissions, „Uses Sonolith“ odkaz, positionHz,
     notifikace o stavu.
  2. **HUD**: zap/vyp, preset, tlačítko „Otevřít HUD editor“, kotva/offset/scale, pozadí, viditelnostní pravidla.
  3. **HUD prvky**: skupina pro každý prvek z §5.1.
  4. **Lyrics**: počet řádků, word highlight, fade, zarovnání.
  5. **Theme**: accent mode, manuální barva, síla tónování pozadí, rychlost přechodu.
  6. **Library UI**: pauza hry, velikost thumbnailů, chování dvojkliku (play vs. play next).
  7. **Controls**: odkaz do vanilla Controls a feedback toasty pro keybindy.
  8. **Advanced**: debug log (bez tokenu!), art cache size, vynutit transport.

---

## 10. Lokalizace

- `assets/sonocraft/lang/en_us.json` + `cs_cz.json`.
- Texty povinné ze spec („Disconnected from Sonolith“ apod.) lokalizovat, ale zachovat smysl.

---

## 11. Bezpečnost a robustnost (checklist ze spec)

1. Discovery a PID kontrola na Windows. Na Linuxu kontrola vlastnictví a módu u `/tmp` fallbacku.
2. Nikdy nemazat socket soubor, ani stale. „Connection refused“ znamená nedostupné.
3. Token nikdy do logu, UI, URL, env, argv.
4. Registrace jen po akci uživatele. Nikdy znovu automaticky.
5. Cooldown po zamítnutí přesně podle `retryAfterMs`, nehardcodovat.
6. Výjimky z I/O vláken nesmí shodit hru. Vše zachytit, zalogovat a přejít do stavu UNAVAILABLE.
7. Při odchodu ze hry zavřít spojení čistě (shutdown hook / `ClientLifecycleEvents.CLIENT_STOPPING`).
8. Vlákna jako daemon s názvy `Sonocraft-IO`, `Sonocraft-Art`.

---

## 12. Testování

- **Unit testy `api/`** (JUnit 5, bez MC): framing (1 MiB limit, `\r\n`, prázdné řádky), RPC párování id mimo
  pořadí, in-flight limit, mapování chyb, stavový automat registrace a hello (approved/denied/blocked/expired/
  identityChanged/revoked), interpolace pozice, výpočet aktuálního lyrics řádku a slova, paging + cursorExpired,
  rate limiter, extrakce barvy.
- **Fake Sonolith server** v testech: unix socket server + skriptované odpovědi. Ukázkové JSON zprávy vzít ze
  SPEC a `api/tests/` v repu Sonolith.
- **Manuální matice** (spec checklist §16–17): Sonolith neběží / API vypnuté / Sonolith skončí uprostřed /
  zamítnutí / blokace / expirace / odškrtnutý optional scope / odebrání v Sonolith za běhu / Disconnect / změna
  identity (jiná Java).
- **Verze:** `runClient` pro každý node + ruční test na každé patch verzi (1.21.11, 26.1, 26.1.1, 26.1.2, 26.2,
  26.3) přes launcher.
- **Platformy:** Linux (X11 i Wayland) + Windows 10/11. Test české klávesnice pro keybindy (§7).
- **Kompatibilita:** s OneConfig i bez něj, s ModMenu i bez něj, se Sodium/Iris (HUD a textury).
- CI: GitHub Actions, build všech nodů + unit testy na `ubuntu-latest` a `windows-latest`.

---

## 13. Fáze implementace (pořadí pro další session)

1. ✅ **Scaffold** (2026-10-29): Stonecutter projekt, 4 nody, Mojang mapování, prázdný mod se načte ve všech
   verzích (`runClient` ověřen na 1.21.11, 26.1, 26.2, 26.3). CI (`.github/workflows/build.yml`) zatím nespuštěno.
2. ✅ **API vrstva bez MC** (2026-10-29): typy, framing, RpcClient, transporty (Unix, Windows pipe), discovery, token
   store, stavový automat. Unit testy + fake server (47 testů, zelené na všech 4 nodech).
   Jak to dopadlo (odchylky od §3):
   - `platform.Transport` má `read/write/close` (framing je v `api.rpc.LineReader`), ne `readLine/writeLine`.
   - `api.rpc.RpcConnection`: vlákna `Sonocraft-IO-Reader-N` a `Sonocraft-IO-Writer-N`, `call()` nikdy neblokuje,
     timeout 35 s (server odpovídá do 30 s).
   - `api.session.SonolithSession`: smyčka `Sonocraft-IO`, události a posluchači `Sonocraft-Events`, odpojení
     `Sonocraft-Action`. Stavy `SessionState` (NOT_CONNECTED místo DISCONNECTED_NO_TOKEN, STOPPED místo ERROR),
     důvod `StatusReason` pro lokalizaci (`sonocraft.status.<name>`).
   - `api.SonolithApi`: typovaná volání všech metod, `api.Pager` pro stránkování.
   - Windows pipe (`PipeTransport`, JNA overlapped I/O) a DPAPI jsou napsané, ale **zatím neotestované** (žádný
     Windows stroj). `secret-tool` store/lookup proti skutečné klíčence taky neotestováno.
3. ✅ **Integrace do hry** (2026-10-29): lifecycle, stores, keybindy (play/pause/next/prev), jednoduchý textový
   HUD. *Milník splněn:* na 26.3 proti Sonolithu 2.4.0 ověřena registrace, HUD, klávesy, reconnect, odpojení
   online i při vypnutém Sonolithu. Ostatní nody zatím jen build + testy.
   - Dočasně se připojuje příkazem `/sonocraft connect|disconnect|cancel|status` (tlačítka až ve fázi 4).
   - Rozdíly verzí jsou jen v `compat/` (`Mc`, `Gfx`, `Registrations`), přes Stonecutter `//? if`:
     26.1+ `GuiGraphicsExtractor.text` a `HudElement.extractRenderState`, `KeyMappingHelper`, `ClientCommands`;
     26.2+ `Gui` rozdělené na `Gui` + `Hud` (`gui.toastManager()`, `gui.hud.setOverlayMessage`, `hud.isHidden()`);
     26.3 `InputConstants.Type.KEYBOARD` a SDL scancody (`KEY_J = 13`), názvy konstant ale stejné.
4. ✅ **Config (YACL + ModMenu entrypoint)**, Connect/Disconnect UI, toasty stavu. Test s OneConfig (2026-10-29,
   26.3 + OneConfig 1.5.2: YACL compat převede vše včetně tlačítek; žádná varianta B není potřeba).
   - Odchylka od §9.2: config ukládá vlastní Gson (`config.ConfigStore`), ne YACL `ConfigClassHandler`; YACL jen
     staví obrazovku. Model je testovatelný a nezávislý na YACL.
   - `ui.ConnectionScreen` (vanilla Screen) pro živý stav připojení; YACL na ni jen odkazuje tlačítkem.
     Tlačítka v YACL se vrací na `Mc.screen()`, ne na screen z YACL (pod OneConfig to není otevřená obrazovka).
   - YACL z Modrinth Mavenu (`maven.modrinth:yacl`), ModMenu `modCompileOnly` + `modLocalRuntime`.
   - Dev přepínače: `-Psonocraft.username=<jméno>`, `-Psonocraft.oneconfig` (jen 26.3, verze v
     `versions/26.3/gradle.properties`).
5. ✅ **Artwork pipeline + accent color** (cache, dekódování, paleta, ThemeManager) (2026-10-29, ověřeno na 26.3).
   Art se žádá jako JPEG (`=wN-hN-l90-rj`), takže WebP dekodér není potřeba; i.ytimg thumbnaily se ořezávají na
   čtverec. Textury beze změn API ve všech nodech (`NativeImage#setPixel` ARGB, `DynamicTexture`,
   `RenderPipelines.GUI_TEXTURED`).
6. ✅ **Plný HUD** (všechny prvky, layout, presety) + **HUD editor** (2026-10-29, ověřeno na 26.3 i s OneConfig).
   - `hud.HudLayout` (čistá Java, testy) počítá layout i umístění; `hud.HudRenderer` kreslí; `hud.HudEditorScreen`.
   - Config HUD v `config.HudConfig`; pozice/měřítko jen v editorech (YACL by je při uložení přepsal).
   - OneConfig: `compat.oneconfig.OneConfigHud implements OneConfigHudWrapper` (panel je v jejich HUD editoru,
     `ownsPlacement` = náš config) + `HudProperties` (nastavení v HUD Design Studio). API jen `compileOnly`,
     vytažené z jaru OneConfig (`extractOneConfigApi`). Názvy předávat jako překladové klíče (registrace je před
     načtením jazyků), dropdown labely líně.
   - Odloženo: explicit/like badge (nízká priorita).
7. ✅ **Lyrics** (store, sync, word highlight) v HUD (2026-10-29, ověřeno na 26.3).
   - `state.LyricsStore`: jen synced (word/line tier); řádek a slovo lokálně z interpolované pozice.
     `lyrics.line` jen krátká korekce, páruje se podle `startMs` (index Sonolithu počítá i řádky bez času);
     `lineIndex: null` (mezi řádky) se ignoruje. `lyrics.word` se neodebírá (slova se časují lokálně), pozice 2 Hz.
   - Dlouhý řádek: word tier sleduje zpívané slovo, line tier se posouvá podle času řádku (drží 15 % na krajích).
8. ✅ **Library UI**: toolkit widgetů → mini přehrávač → Now Playing → Queue → Search → Library sekce → Detaily →
   Playlisty a kontextové menu. (2026-10-29, ověřeno na 26.3)
   - `ui.LibraryScreen` (klávesa J, `/sonocraft library`), stránky v `ui.page`, widgety v `ui.widget`.
   - Ikony: pixelarticons (MIT, licence v `textures/gui/LICENSE-pixelarticons.txt`), 12 px; transport, ⋯ a úchyt
     ručně kreslené na střed.
   - Fronta: Sonolith neumí „přehraj tuto položku“ → skok dopředu přes `player.next` ×N (max 15), jinak přesun
     za aktuální + next. „Playing from“ jen když přehrávání spustil Sonocraft (API zdroj fronty neuvádí).
   - Stav přehrávání jen z `player.stateChanged` (`player.position` při seeku hlásí `paused`).
   - Art: HttpClient nesmí sdílet executor se stahováním (deadlock); aktuální položka má vlastní prioritní vlákno.
   - Odloženo: detail epizody (klik epizodu přehraje). Známý flaky test `revokedDeletesTokenAndStops` pod zátěží.
9. **Doladění:** (2026-10-29: všechny 4 nody ověřené ve hře na Linuxu; YACL z maven.isxander.dev, protože
   Modrinth POM neuvádí přibalené knihovny a dev klient 1.21.11 bez nich padá) lokalizace, animace, výkon, edge cases, test matice, README (vč. vysvětlení identity a Javy).
10. **Release:** Modrinth/CurseForge, jar per node, changelog.

---

## 14. Rozhodnuto s uživatelem (2026-10-29)

1. `appId` = `dev.opecko.sonocraft` (= Java balíček).
2. Library UI = `J`.
3. Play/Pause = `=` (CZ), tedy `KEY_MINUS`. Konflikt s `P` tím odpadá.
4. Publisher = `opecko`, licence = MIT.
5. Název = **Sonocraft** (velké S), viz hlavička.

Otevřené: zatím nic.

## 15. Zdroje

- Sonolith API: https://github.com/opecko/sonolith/tree/master/api (`SPEC.md`, `llms.txt`, `openrpc.json`,
  `schema/`, `examples/python-tui`)
- OneConfig v1 zdrojáky (compat + Stonecutter vzor): https://github.com/Polyfrost/OneConfig/tree/v1
- OneConfig docs: https://docsv1.polyfrost.org/
- YACL: https://modrinth.com/mod/yacl
- Fabric 26.3 oznámení (SDL místo GLFW, Loom): https://fabricmc.net/2026/09/15/263.html
- Fabric Loom docs (`fabric-loom` vs `fabric-loom-remap`): https://docs.fabricmc.net/develop/loom/
- Stonecutter: https://stonecutter.kikugie.dev/
