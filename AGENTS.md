# Project instructions

## Mod identity and release filenames

The mod is **Create: Belts n Pieces**, with mod ID/resource namespace `beltsnpieces` and Java package `com.sirjohnty.beltsnpieces`. The Beta baseline is `0.3.0-Beta` in `build.gradle`.

Always use `beltsnpieces-<version>-<minecraft-version>-neoforge.jar` for release/checkpoint filenames. The baseline filename is `beltsnpieces-0.3.0-Beta-1.21.1-neoforge.jar`; a later patch build would be `beltsnpieces-0.3.1-Beta-1.21.1-neoforge.jar`. Preserve the capitalization of the version suffix. This applies to every future build/package request. Preserve historical checkpoint JARs under their original names.

When installing a renamed release, also recognize the predecessor mod ID `bitsnbelts` in `META-INF/neoforge.mods.toml` for old-version cleanup. This is an installer-only transition rule; do not retain the predecessor namespace in the distributed mod. Remove a predecessor JAR only after the replacement was copied and SHA-256 verified. Do not modify saves or assume registry IDs migrate automatically across the rename.

## Computer use

Tell the user before starting any computer-use automation, including clicking in Minecraft or IntelliJ. Do not begin computer use without this advance notice.

## Ponder scenes

Use Create's regular-belt Ponder scenes (`BeltScenes` in the pinned Create dependency) as the baseline for all new Ponders: staged section reveals, native section movement and belt-item instructions, continuous motion, short control demonstrations, and captions synchronized to each action. Keep tutorials concise and include production language entries for all titles and captions.

Do not create or update Ponder scenes, registrations, schematics, or tutorial text without the user's express permission for that change.

## buildit workflow

When the user says `buildit` (case-insensitive), perform all of the following without asking again for workflow confirmation:

1. Increment the patch component of `version` in `build.gradle`, preserving the major/minor components and suffix. Example: `0.3.0-Beta` becomes `0.3.1-Beta`. This is the single source of the mod version; resource processing expands it into the mod metadata. Follow the release filename scheme above.
2. Before selecting a version, check existing checkpoint JARs in `build/libs`. Never overwrite a checkpoint. If the next version already exists, advance to the next unused patch version.
3. Run `.\gradlew.bat build` using Java 21. Use the existing Gradle cache at `C:\Users\mjohn\.gradle` if the environment otherwise resolves it incorrectly. Do not run `clean` or delete previous JARs from `build/libs`.
4. Only after a successful build, install the new versioned mod JAR into:
   `C:\Users\mjohn\AppData\Roaming\ModrinthApp\profiles\JohntySMP\mods`
5. Verify the installed JAR matches the built JAR using SHA-256. After successful copy and verification, remove older versions of this mod from that profile's mods folder, leaving only the new version. Identify this mod by `modId="beltsnpieces"` in `META-INF/neoforge.mods.toml`, rather than broad filename matching. Never delete other mods.
6. Preserve every previous checkpoint in `build/libs`; the new versioned JAR there is the checkpoint for this build. Here, "libs folder" means `build/libs`, not the root `libs` directory used for external compile dependencies.
7. If building or copying fails, report the failure and preserve the previously installed mod. On a build retry, reuse the version assigned to that failed attempt if no checkpoint JAR was produced; do not increment repeatedly for the same attempt.
8. After the build, installation, hash verification, and old-version cleanup all succeed, automatically launch the JohntySMP Minecraft instance through Modrinth App. The installed launcher executable was found at `C:\Users\mjohn\AppData\Local\Modrinth App\Modrinth App.exe`. Verify the supported instance-launch mechanism and exact profile identifier before using them; do not assume an unverified command-line flag or URI works. The launcher UI may open as part of launching the game. If launching a background helper with Start-Process, use -WindowStyle Hidden.
9. Before launching, check whether Minecraft for the JohntySMP profile is already running. If it is, leave it open, do not launch a duplicate, and report that Minecraft must be restarted to load the new build. Never terminate Minecraft automatically. If the running instance cannot be identified reliably, report that limitation instead of risking a duplicate launch.
10. Do not launch after a failed build or installation. A launch failure does not invalidate the successful build/checkpoint/install: report the launch failure separately and keep the installed JAR. Verify that the instance actually started when possible; do not claim a successful game launch merely because the launcher process started.
11. Report the new version, checkpoint path, installation result, and launch result (or restart requirement). Do not commit or push unless separately requested.

The user has explicitly authorized version increments, replacement of old Create: Belts n Pieces JARs in the named profile, and automatic launch of JohntySMP after successful installation whenever they request `buildit`. Request sandbox escalation when necessary for Gradle cache access or writes to the profile; no additional user preference confirmation is needed.

## packageit workflow

When the user says `packageit` (case-insensitive), perform the `buildit` workflow without launching the game. Follow steps 1–7 and the no-commit/no-push rule from step 11: increment to an unused patch version, build with Java 21, preserve all `build/libs` checkpoints, install into the JohntySMP profile, verify SHA-256, and remove only older verified Create: Belts n Pieces JARs after successful installation. The same failure handling, retry version reuse, and sandbox escalation rules apply. The user explicitly authorizes these version increments and profile replacements for `packageit`, without further workflow confirmation.

Skip the launch steps 8–10. Do not launch Minecraft or Modrinth App, and leave any running game open. Report the new version, the shareable checkpoint JAR path in `build/libs`, and the installation/hash-verification/cleanup result. State that game launch was skipped for `packageit`.

## Verified Modrinth launch details

Modrinth App 0.22.0 identifies the profile folder JohntySMP as display name JohntyCraft, instance ID local:a0e15c04-ce63-4549-8e27-2b783b92d05f. Pass this URI as the single argument to the installed Modrinth App executable: modrinth://launch/instance/local:a0e15c04-ce63-4549-8e27-2b783b92d05f . The launch URI was verified against Modrinth v0.22.0 source apps/app/src/api/shortcuts/mod.rs. Use the folder/ID to identify the instance, rather than expecting its display name to be JohntySMP.

## Development testing

For fast iteration, use IntelliJ's Client run configuration in Debug mode. Runtime dependencies are configured in build.gradle: libs contains Create dependencies and dev-libs contains optional integration mods. setup-dev.ps1 restores the curated integration set from the JohntySMP profile. DEVELOPMENT.md documents the workflow.

Use Gradle classes (not build or clean) for routine development compilation so versioned checkpoint JARs are not overwritten. Reload changed classes through IntelliJ for ordinary method-body changes; restart Client for structural/registration/mixin changes or unsupported HotSwap changes. Rebuild resources before F3+T or /reload. Development settings, worlds, and logs live in run, separately from Modrinth.

Do not increment the version, install to Modrinth, or package a release as part of routine development testing unless requested. The buildit workflow above remains the checkpoint/install workflow. When the user requests development testing, keep a successfully launched debug client running for their use.
## testit workflow

When the user says `testit` (case-insensitive), compile the latest project code and resources using `.\gradlew.bat classes prepareClientRun`, then launch the existing IntelliJ Client run configuration in Debug mode through the IntelliJ debugger tools. Check debugger status first: if the development client is already running, reuse it and report its status rather than launching a duplicate. If changes require a restart, explain this and restart only the development client, never the Modrinth game. Check launch logs for failures and verify the debug client is running. Leave a successfully launched client open for the user.

Do not increment the mod version, build a release JAR, overwrite build/libs checkpoints, copy anything to Modrinth, or launch the Modrinth instance for `testit`. Use the existing Java 21 setup and Gradle cache at C:\Users\mjohn\.gradle. Follow the ij-debugger skill when controlling IntelliJ debug sessions. DEVELOPMENT.md contains the test environment details.
### testit world selection and /restart

The user's sole development save is run/saves/New World. testit must boot directly into this existing world, using Minecraft's --quickPlaySingleplayer argument with the folder name New World. The Client run configuration in build.gradle supplies these arguments when level.dat exists. Do not create a new world or copy/modify the Modrinth saves. If the save is missing, report it and use the title screen. The /restart development command also uses this Client configuration and should return to the same world.

/restart saves and closes the development client, then restart-dev.ps1 recompiles and relaunches it with a loopback debug listener on port 5005. The original IntelliJ debugger session ends with its JVM; use Attach Development Client to reconnect. Before testit starts another client, also check for a detached restarted development JVM to avoid a duplicate or a world-lock conflict. These are persistent user preferences.
### Automatic live reload

The user wants changes applied to a running development client automatically whenever supported. IntelliJ Settings > Build, Execution, Deployment > Debugger > HotSwap > Reload classes after compilation is set to Always. After compatible method-body edits, compile through the IntelliJ build_project tool (or IDE Build action) to trigger automatic HotSwap. A standalone Gradle classes command is not proof that the debugger reloaded changed classes. Verify reload success through IDE notifications/debugger evidence; if automatic reload does not occur, invoke Reload Changed Classes through IntelliJ. Do not claim changes are live merely because compilation passed.

Keep the client open for compatible reloads. For structural, registration, mixin, or packet-schema changes that HotSwap cannot apply, explain the need and restart only the development client, returning to New World. Reattach via Attach Development Client if it was launched by /restart. Never restart or modify the Modrinth instance as part of live development testing.
## Reinforced belt placement behavior

Initial placement is: select the start shaft > choose the horizontal endpoint > right-click to lock horizontal position > choose vertical endpoint/height > right-click to place the belt. Height selection is part of initial placement, not post-placement modification. Preserve this two-stage endpoint confirmation flow. The wrench requirement applies to adjustments after the belt has been placed; it must not remove initial height selection. Keep the height preview cuboid and keep the blue endpoint adjustment arrow removed.
### One-time cleanup queued for testit

The user requested /clearw once on the next testit login. This is armed ONLY while run/clearw-on-next-testit.once exists. The Client configuration enables beltsnpieces.clearOnce for testit, and DevelopmentClearWorld consumes the marker after login and starts the normal 8-chunk cleanup preserving solid sandstone. Do not manually run a second /clearw when the queued action succeeds, and do not recreate the marker after it has been consumed. /restart skips queued cleanup using -PskipQueuedClear. After next testit, check the consumed marker and game logs to report cleanup status. JEI is now part of setup-dev.ps1's development dependency set.
