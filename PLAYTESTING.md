# Playtest readiness

This records verification coverage, not a guarantee that every modpack or multiplayer setup works. Use a backed-up world for external testing.

## Completed checks

- Java 21 compilation and Gradle verification tasks.
- 876 casing routes, with five geometry sampling densities, including the captured casing-fold regression.
- 48 route/chunk-cache/collision-serialization cases and 2,000 deterministic targeting rays compared with the previous intersection algorithm. Separate upper/lower-run and untargetable-gap checks.
- Drag projection, quarter-block snapping, rebase continuity, limits, nonfinite inputs, and reversal after rebasing.
- Steel palette fallbacks for empty, transparent, warm-only, and ordinary textures; preserved output transparency.
- In-world development checks using a fake inventory: obstructed/unaffordable placement rejection, placement costs, simulated and actual cargo operations, save/load, dye/casing persistence, malformed base elevations, successful/rejected reshaping, repeated dismantling, refunds, and orphan-span cleanup.
- Create-only dedicated-server bootstrap through `--initSettings`. This validates startup/class loading only; it does not run a server world or establish multiplayer compatibility.
- Local core dependency inventory and duplicate declared mod-ID detection.

## External playtest priorities, in order

1. **Save and chunk boundaries:** build loaded routes across chunks; unload either endpoint and middle chunks independently; break loaded endpoints/spans; return, save, and restart. Check exact refunds, cargo counts, restored shafts, and removal of orphan collision. Test saves made with the current registry namespace; earlier namespaces are not automatically migrated.
2. **Two players:** place, reshape, recolour, case, and dismantle while another player watches. Try concurrent edits, reconnecting, entering range, protected areas/spawn protection, and different latencies. Both players must use the same mod build. Record desynchronization or rubber-banding.
3. **Transport:** normal Create → reinforced → normal Create and reinforced → reinforced; full receivers, stopped/reversed/overstressed belts, stacks with components, dropped items, and chunk unload/reload. Count all items before and after.
4. **Controls:** main/offhand, changing slots, dropping the selected stack, cancelling at each placement stage, source removal, dimension changes, reconnecting, steep aiming, moving during wrench drag, and hitting both adjustment limits. Preserve the two-stage endpoint confirmation and initial height selection.
5. **Movement and geometry:** straight/curved/U-turn/rising/falling belts; tight bends, joins, belt edges, walking/sprinting/crouching/jumping, high speeds, and handoffs. Compare collision, picking, and rendered treads.
6. **Visuals and compatibility:** all five quality levels and distance transitions, resource reloads, custom/animated textures, dyes, and andesite/brass/steel/Extra Casing variants. Test optional integrations present and absent. Try Transmission chains on pulley shaft faces; spans must not expose shafts.
7. **Recipes:** confirm iron-sheet assembly without an andesite-sheet provider, and the andesite-sheet assembly when a provider fills the supported tags. Inspect JEI stages, intermediate item, and output count.
8. **Performance:** compare frame time and server tick time in the same populated belt scene before/after, including full cargo and many riders. The ownership microbenchmark is not an FPS or server-load benchmark.

## Ponder ownership

Existing scene order is placement followed by adjustments. The reinforced belt is indexed using Create's existing kinetic-relay and logistics categories. Scene titles, overlay text, and schematics are reserved for the author's manual editing and remain unchanged in this pass.

## Reporting

Supply exact mod versions, client and dedicated-server logs, reproduction steps, and a small backed-up example save when useful. Include inventory counts for loss/duplication, chunk-loading conditions, and whether restarting reproduces it. Rendering reports should identify visual quality, resource packs, shaders, and performance mods.
