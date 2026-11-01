# Create: Belts n Pieces

An addon for the Create Mod adding features and changes focusing on belts and logistics

## Features

1. Select a starting horizontal Create shaft with a Reinforced Belt. Clicking suitable ground can supply a starting shaft from your inventory.
2. Aim to choose the horizontal endpoint. Sneak-scroll cycles curve styles. Sneaking can also select the generated endpoint approach from your facing direction.
3. Right-click to lock the horizontal position, then look up/down to choose height. Right-click again to place. Left-click cancels the staged endpoint; sneak-right-click clears the selected start.
4. After placement, hold right-click with a Create wrench on the middle of the belt and look up/down to adjust height. Scroll during the drag to shift horizontally. Release to apply; left-click resets the current drag. Endpoints stay fixed.
5. Sneak-wrench a span or pulley to dismantle the belt and return paid belts and cargo. Its endpoint shafts remain.

## Configuration

<<<<<<< Updated upstream
Many aspects of the mod can be configured either manually or with a config GUI mod.
=======
Server/world settings are in `serverconfig/beltsnpieces-server.toml` inside the save:

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
 

>>>>>>> Stashed changes
