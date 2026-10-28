Create: Bits n Belts

Adds reinforced belts and other items focused on logistics, amongst other quality of life changes

## Development requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.219 (configured in `build.gradle`)
- Create 6.0.10 and its compile dependencies

The mod version is defined in `build.gradle` and inserted into `META-INF/neoforge.mods.toml` during resource processing.

## Build

The Gradle wrapper is included. External dependency JARs are intentionally excluded from Git. Place these files in `libs/`:

- `create-1.21.1-6.0.10.jar`
- `ponder-neoforge-1.0.82+mc1.21.1.jar`
- `flywheel-neoforge-1.21.1-1.0.6.jar`
- `Registrate-MC1.21-1.3.0+67.jar`

If the original backup exists at `../build036/deps/create-libs`, the local helper can copy them:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-libraries.ps1
```

Build on Windows:

```powershell
.\gradlew.bat build
```

On macOS or Linux, run `sh ./gradlew build` after supplying the same dependencies. Gradle needs internet access to download its distribution and build dependencies. Built JARs appear in `build/libs/`.

Dependencies are currently compile-only. Development game runs require additional runtime setup for Create.

## Compatibility and recovery notes

The mod ID and Java package use `bitsnbelts`. Belt and pulley registry IDs use reinforced names; old registry IDs are not automatically migrated in existing worlds.

See [README-RECOVERY.txt](README-RECOVERY.txt) for source recovery provenance and earlier validation details. AI was used during the creation of this mod.

## License

[MIT](LICENSE), copyright 2026 SirJohnty.
