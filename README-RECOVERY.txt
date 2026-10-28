CREATE: BITS N BELTS - RECOVERED STANDALONE SOURCE

The current mod version is defined only in build.gradle. Gradle resource processing inserts that version into META-INF/neoforge.mods.toml automatically.

The Java source was recovered by combining the current source overlay with seven classes from the older project. All 40 class files produced with Java 21 in the recovery environment matched the 0.1.63 JAR byte-for-byte. Resources were copied from the 0.1.63 JAR. This check does not verify in-game behaviour or the Gradle build.

WINDOWS SETUP
1. Extract this project directly inside your existing create-bitsnbelts backup folder, next to build036.
2. Open PowerShell in the project folder and run:
   powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-libraries.ps1
   This copies four Create dependencies from your existing backup. They are not your mod's compiled JAR and are not bundled in the output mod.
3. Open this project folder in IntelliJ as a Gradle project. Select Java 21 for Gradle and trust the project when prompted. Gradle must download its distribution, the NeoForge plugin and Minecraft dependencies on first import. Internet access is required.
4. When ready to build, run .\gradlew.bat build in IntelliJ's terminal. The JAR should appear in build\libs. This command has now completed successfully locally with Java 21, Minecraft 1.21.1, NeoForge 21.1.219 and Create 6.0.10.

This recovered source project does not depend on the old compiled mod JAR. The successful local Gradle build confirms compilation and packaging, but does not establish byte-for-byte equivalence to the old JAR or verify in-game behaviour. Local Create dependencies are compile-only; a development game run requires additional runtime setup for Create.

RENAMED SOURCE: mod ID and Java package now use bitsnbelts; belt/pulley registry IDs use reinforced names. This breaks automatic recognition of old registry IDs in existing worlds. The byte-identical recovery check described above applies to the pre-rename project; the renamed project has now built successfully through Gradle locally.
