# Create: Belts n Pieces

Use `beltsnpieces-source` for the source checkout and `create-beltsnpieces` for its local container. Open the checkout folder itself in IntelliJ. The Gradle project name is `BeltsNPieces`, the mod/resource ID is `beltsnpieces`, and Java sources use `com.sirjohnty.beltsnpieces`.

`gradlew classes check` verifies the source identity as well as the development checks. Historical checkpoints keep their original filenames. Git may show deleted paths from before the rename until the changes are committed; those are history, not active source files.

The current Beta baseline is **0.3.0-Beta**. Release filenames follow `beltsnpieces-<version>-1.21.1-neoforge.jar`, starting with `beltsnpieces-0.3.0-Beta-1.21.1-neoforge.jar`.

Registry IDs now use the `beltsnpieces` namespace (for example, `beltsnpieces:reinforced_belt`). This is a namespace-breaking rename: earlier blocks/items are not automatically migrated. Keep earlier saves backed up and use a save made for this namespace for playtesting. Remove the predecessor mod JAR before installing the renamed release.

Reinforced belts follow curved and elevated routes between Create shafts, carry items, and transmit rotation. This is an alpha playtest build: back up valuable worlds before testing.

## Playtest installation

Use Minecraft **1.21.1**, Java **21**, NeoForge **21.1.259**, and Create **6.0.10** for the tested baseline. The development environment uses Flywheel **1.0.6**, Ponder **1.0.82+mc1.21.1**, and Registrate **MC1.21-1.3.0+67**. Use the dependencies supplied or required by your Create distribution; avoid duplicate JARs.

Install the same Create: Belts n Pieces build on clients and servers. Remove older Create: Belts n Pieces JARs first. Remove **Unlimited Chain Conveyors**: its feature is included here and the two mods are declared incompatible.

## Controls and costs

1. Select a starting horizontal Create shaft with a Reinforced Belt. Clicking suitable ground can supply a starting shaft from your inventory.
2. Aim to choose the horizontal endpoint. Sneak-scroll cycles curve styles. Sneaking can also select the generated endpoint approach from your facing direction.
3. Right-click to lock the horizontal position, then look up/down to choose height. Right-click again to place. Left-click cancels the staged endpoint; sneak-right-click clears the selected start.
4. After placement, hold right-click with a Create wrench on the middle of the belt and look up/down to adjust height. Scroll during the drag to shift horizontally. Release to apply; left-click resets the current drag. Endpoints stay fixed.
5. Sneak-wrench a span or pulley to dismantle the belt and return paid belts and cargo. Its endpoint shafts remain.

Placement costs one Reinforced Belt per block in the endpoints' Manhattan distance, plus one for the starting position. Each newly supplied shaft costs one shaft. Creative placement consumes nothing and does not refund free belts. Casing application is free and does not produce casing refunds.

The crosshair HUD shows belt and shaft requirements, placement stage, and invalid-preview reasons. Hold W over the Reinforced Belt in an inventory for its Ponder scenes; it is also indexed under Create's kinetic-relay and logistics categories.

## Configuration

Server/world settings are in `serverconfig/beltsnpieces-reinforced-belts.toml` inside the save:

| Setting | Default | Supported range |
| --- | --- | --- |
| `maxReinforcedBeltLength` | 20 blocks of path length | 3–64 |
| `maxReinforcedBeltAdjustments` | 64 applied adjustments | 0–64; zero disables new wrench adjustments |
| `maxChainConveyorAngle` | 45 degrees | 0–90 |

Existing belts are retained when the placement length limit is lowered. Each wrench adjustment is limited to four blocks vertically and four blocks horizontally; height movement snaps to world quarter blocks. Entire routes must be loaded for transport and editing; belts do not force-load chunks.

The client visual-quality setting has five levels and changes rendering detail only.

# Integrations

Reinforced plates will use andesite sheets if an installed mod adds them, falling back to iron sheets if not

Create: Extra Casing
  Reinforced belts can accept casings added by this mod
Create: The Factory Must Grow
  Allows regular and reinforced belts to use Steel Casing

Create Transmission **1.2.2** (NeoForge/Create 6): chains can attach to reinforced pulley shaft faces and connect sideways to reinforced pulleys. Spans do not expose shafts. Optional integration mods are not bundled into this JAR.

## Reporting a playtest issue

See [PLAYTESTING.md](PLAYTESTING.md) for verification coverage and scenarios still needing external testing. Include the Create: Belts n Pieces version, complete mod list, client/server logs or crash report, reproduction steps, and screenshots of both endpoints and the affected route. For item-loss/duplication reports, record counts before and after, whether chunks unloaded, and whether a save/restart occurred. Report visual quality, resource packs, shaders, and performance mods for rendering issues.
 

