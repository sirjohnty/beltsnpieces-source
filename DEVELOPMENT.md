# Fast local testing

Use IntelliJ's **Client** configuration in **Debug** mode for everyday changes. It loads Create: Belts n Pieces directly from the project, without building a release JAR, incrementing the version, or copying anything into Modrinth.

`testit` launches directly into the existing development save `run/saves/New World` using Minecraft Quick Play. `/restart` returns to the same world. If that save is missing, the client opens the title screen instead.

## Setup

Use Java 21 and NeoForge 21.1.259, matching the JohntySMP Modrinth instance. The four Create compile/runtime dependencies belong in `libs/`. Optional integration dependencies belong in `dev-libs/` and are not bundled into the mod JAR or tracked by Git.

To restore the integration dependencies from the local JohntySMP profile:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-dev.ps1
```

Reload the Gradle project in IntelliJ after changing dependencies. The selected integration set contains Accessories, owo-lib, Curios, Caelus, Elytra Slot, and TFMG. Rendering/performance mods are Sodium, Sodium Extra, Reese's Sodium Options, ImmediatelyFast, and Entity Culling, using the exact versions copied from JohntySMP. Create, Flywheel, Ponder, and Registrate come from `libs/`. Shader mods are not part of this default test setup.

## Run and update

Create Transmission 1.2.2 (NeoForge, Create 6, Minecraft 1.21.1) is also included in `dev-libs`. `setup-dev.ps1` downloads this pinned dependency from Modrinth and verifies its hash. It is compile-only for the optional compatibility mixin and runtime-only for Client; it is not bundled or required in production. Transmission chains can attach to reinforced pulley shaft faces and connect sideways to reinforced pulleys, using Transmission's existing rotation rules. Belt spans do not expose shafts. Restart Client after adding this integration.

1. Select **Client** in IntelliJ's run configuration list and click **Debug** (the bug icon).
2. Client opens the existing `run/saves/New World` using Quick Play. Do not create or copy a replacement save. Development settings and logs live in `run/`, separately from JohntySMP.
3. For changes inside existing Java methods, compile with **Build Project** (Ctrl+F9), then use **Run > Debugging Actions > Reload Changed Classes** if IntelliJ does not reload automatically. Accept the reload prompt if shown.
4. If using Gradle explicitly, run `.\gradlew.bat classes`, then reload changed classes in the attached debugger. This compiles code and copies resources without packaging a JAR.
5. After rebuilding changed textures or models, press **F3+T** in Minecraft. After rebuilding recipes, tags, or loot tables, run `/reload` in the test world.
6. Restart **Client** after adding/removing fields or methods, changing registrations or mixins, or whenever HotSwap reports that a change is unsupported.

HotSwap uses the active JDK's capabilities; the current Java 21 runtime supports ordinary method-body changes. Source-level compile success alone does not prove the running game has reloaded a change.

The terminal alternative is `.\gradlew.bat runClient`, but launch through IntelliJ **Debug** when you want HotSwap or runtime inspection.

Keep using **buildit** for versioned checkpoints and full-pack testing in Modrinth. Routine development compilation should not overwrite the existing versioned JAR checkpoints in `build/libs`.

**packageit** performs the same checkpoint/install/hash-verification workflow without launching Minecraft or Modrinth App. Share the versioned JAR from `build/libs`.

## Repeatable readiness checks

Run `.\gradlew.bat check` with Java 21. This runs the casing geometry regression (876 routes at five sampling densities), drag/resource/targeting/chunk-cache checks, and a local dependency inventory check. These checks do not package a release or modify a save.

For an opt-in in-world regression, create `run/readiness-check.once` and leave the development client running in New World. `DevelopmentReadiness` consumes the marker, waits 40 active server ticks, and requires an empty loaded volume above the player. It checks placement rejection/costs, cargo simulation and extraction, save/load, dye/casing persistence, malformed elevation handling, reshaping, and one-time refunds. A short-lived fixture exercises cargo-only networking, then is removed after 100 ticks or on server shutdown. A failed empty-volume check aborts before changing blocks. It uses a fake player's inventory, never the user's inventory. Inspect `READINESS` lines in `run/logs/latest.log`. Development helper classes remain excluded from release JARs.

Run `.\gradlew.bat runReadinessServer -PminimalRuntime` for a Create-only dedicated-server bootstrap through `--initSettings`. It uses `build/readiness/server`, creates settings only, and does not accept an EULA, create a world, or prove multiplayer gameplay.

Run `.\gradlew.bat runMinimalClient -PminimalRuntime` for a Create-only title-screen smoke check in `build/readiness/minimal-client`. No user save is selected. Close this smoke-check client when finished. Regular Client launches retain the curated integrations.

## Restart from Minecraft chat

In the development client, `/restart` saves and closes Minecraft, recompiles the project, and launches it again. It is not registered in production/modpack launches. It does not change the mod version or package/install a release JAR.

The restarted client runs immediately and listens for a debugger on `127.0.0.1:5005`. Select **Attach Development Client** in IntelliJ and click **Debug** to reconnect for HotSwap/breakpoints. The original IntelliJ debugger connection ends when its JVM closes.

If restart fails, inspect `run/restart-helper.log`, `run/restart-build.log`, and `run/restart-client.log`. A compilation failure leaves Minecraft closed so an older build is not silently launched.

## Troubleshooting

## Clear the test area

JEI is included in the development dependencies for browsing items and recipes.

A one-time cleanup request is represented by `run/clearw-on-next-testit.once`. The next `testit` launch enables it, waits briefly after login for chunks to load, and runs the default `/clearw` cleanup once. The marker is renamed to `clearw-last-testit.consumed` before execution, preventing repeats. `/restart` explicitly skips this queued cleanup. It is development-only and never included in release JARs.

`/clearw` removes non-sandstone blocks and non-player entities in currently loaded chunks within an 8-chunk radius of the player, at all heights in the current dimension. `/clearw 16` changes the radius; the supported range is 1–32 chunks. It preserves solid normal, cut, chiseled, and smooth sandstone and their red equivalents. It does not generate chunks, clear unloaded regions, or clear other dimensions. Work is spread across server ticks. The command requires command permission in an integrated development world.

Development command classes (`Development*.class`), including `/restart` and `/clearw`, are excluded from release JARs and also refuse command registration in production mode.

- Sodium's bootstrap JAR is excluded from the development classpath and copied into `run/mods` by `setup-dev.ps1`, so NeoForge discovers its nested mod correctly. The remaining optional dependencies load from `dev-libs`.

- Do not put a built Create: Belts n Pieces JAR in `run/mods`: the project already supplies the mod.
- Logs: `run/logs/latest.log`; crash reports: `run/crash-reports/`.
- When Gradle resolves its cache incorrectly, set `$env:GRADLE_USER_HOME='C:\Users\mjohn\.gradle'` in the terminal first.
- Optional integrations can be removed from `dev-libs/` to test the base Create-only configuration; reload Gradle and restart Client afterward.
