# Contributing

How code in Sonocraft is written. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) explains how it fits together.

## Language

- Everything in the repository is English: code, comments, Javadoc, test names, build scripts, docs, commit
  messages.
- Texts the player sees go through translation keys. Every key is added to both `en_us.json` and `cs_cz.json`.
- Texts for Sonolith's consent screen (`SonocraftRegistration`) stay English, single-line, plain: Sonolith shows them as
  they are.

## Java and versions

- Java 21 language level: the 1.21.11 node compiles with `--release 21`. Records, sealed types and pattern matching
  are fine; nothing newer.
- One source tree for all nodes. Code that differs between Minecraft versions goes into `compat` (`Mc`, `Gfx`,
  `Registrations`) using Stonecutter conditions; the rest of the code calls those helpers. Write the active (26.3)
  variant in plain code and older variants in `/* … */` blocks:

  ```java
  //? if >=26.2 {
  mc.gui.setScreen(screen);
  //?} else {
  /*mc.setScreen(screen);
  *///?}
  ```

- Never write key or mouse button numbers. Use `InputConstants.KEY_*` and `InputConstants.MOUSE_BUTTON_*`: 26.3 uses
  SDL numbers, so a literal `0` for the left button breaks there.
- Check an API exists on every node before using it (`javap` on the merged jars in `.gradle/loom-cache`), and build all
  nodes (`./gradlew build`) before committing.

## Layers

- `api`, `api.*`, `platform`, `platform.token`, `state`, `theme` must not import `net.minecraft`. Keep logic there
  where possible, so it can be unit tested.
- Optional mods (Mod Menu, OneConfig) are `compileOnly`. Code that touches their classes lives in its own class and
  runs only after `FabricLoader.isModLoaded(...)`.

## Threads

- Never block the render thread: no socket, file, process or HTTP work there. Use the session's threads or an executor.
- Results that touch Minecraft state go back with `Minecraft.getInstance().execute(...)` (or `whenCompleteAsync(…,
  Minecraft.getInstance())`).
- Share state through immutable snapshots in `AtomicReference` or `volatile` fields, not locks the render thread could
  wait on.
- Name threads (`Sonocraft-…`) and make them daemon threads.
- An exception on an I/O thread must not crash the game: catch it, log it, move to a state the user can see.

## Sonolith API rules

The client must follow Sonolith's integration rules (SPEC §18). In particular:

- Register only after a user action; never automatically, never again after a revoke or a denial.
- Never log, show or pass around the token (no `toString` that prints it, no command lines, no environment).
- Ignore unknown fields, notifications and enum values (Gson gives `null` for unknown enum names; handle it).
- Respect scopes and rate limits before calling; keep `ApiMethods` in sync with the spec.
- Keep "Uses Sonolith" next to Disconnect and in the config's About, and nowhere suggest Sonocraft is made or endorsed
  by Sonolith.

## Rendering

- No allocations per frame on the HUD's steady path: cache texts and layouts, rebuild them when the item, the second
  or the config changes.
- Colours are ARGB; text with alpha 0 is not drawn.
- Draw icons at their native size (12 px for UI icons) so pixels stay square.

## Style

- 4 spaces, lines up to 120 characters, braces on every block.
- Comments say why, not what; keep them short. Public classes get a Javadoc sentence about what they are for.
  Reference the spec or this repo's docs where a rule comes from (`SPEC §6.4`).
- Prefer records and small final classes. Constants for magic numbers, with a word about where the number comes from.
- Name things after what they mean to the user or the API (`playingFrom`, `retryAfterMs`), not after how they are
  implemented.

## Tests

- New logic in the Minecraft-free packages gets a JUnit test. The session and RPC are tested against
  `testing.FakeSonolith`; extend it rather than mocking.
- Tests that wait for something use `testing.Wait` with a generous timeout: CI machines can be slow.
- Platform-specific tests use `@EnabledOnOs`.

## Commits

- One topic per commit, imperative summary line, a short body with what and why.
- `./gradlew build` passes on all nodes.
