# Create: Belts n Pieces polishing plan

Reviewed: 29 October 2026

Scope: polish existing functionality, performance, reliability, visuals, and development workflows. This is a backlog, not a record of completed fixes.

Recommended order: save and item reliability first, performance second, interaction clarity third, then visual consistency and release quality.

The review covered Java code, mixins, recipes, resources, configuration, and development scripts. An earlier performance recording informs the performance priorities. Risks and inspection targets below require targeted testing; they are not all confirmed bugs.

## 1. Establish a repeatable regression baseline

Highest priority. Preserve representative cases before changing more geometry or transport code:

- Straight, curved, U-turn, rising, falling, and wrench-adjusted belts.
- Bare, dyed, and cased belts.
- Normal Create → reinforced → normal Create transfers.
- Full inventories, stopped belts, reversals, and overstressed networks.
- Belts crossing chunk boundaries, followed by unload/reload and world restart.

Measure frame times, server tick time, and item counts before and after each pass. Catch visual improvements that accidentally worsen collision, transport, or performance.

Relevant file: `tools/CasingGeometryCheck.java`. This already provides useful geometry checks but is a standalone tool rather than part of a repeatable verification workflow.

## 2. Harden placement and reshape rollback

Relevant files: `ReinforcedBeltItem.java`, `ReinforcedPulleyBlockEntity.java` under `src/main/java/com/sirjohnty/beltsnpieces/`.

Preflight checks and rollback already exist. Test failures partway through operations and ensure they restore:

- Original shafts and surrounding blocks.
- Surface ownership and collision.
- Both endpoint entities and kinetic connections.
- Cargo positions and counts.
- Belt and shaft costs, without duplicate refunds.

Placement and reshaping modify several blocks as one logical operation, so consistency takes priority.

## 3. Verify dismantling across partially loaded routes

Dismantling removes loaded owned spans, restores endpoint shafts, and refunds cargo and paid belts. Unloaded spans depend on later ownership checks.

Test breaking the controller, remote pulley, and middle span with different chunks unloaded. Confirm refunds happen once, orphan collision cells disappear, reloads cannot resurrect dismantled belts, and shared blocks survive.

This is a reliability check, not a proposal to change dismantling behavior.

## 4. Tighten saved-data validation and migration

Individual elevation edits and cargo values are validated, but base elevation values receive less validation during NBT loading. The path constructor sanitizes some values, leaving validation spread across layers.

Consolidate validation for saved geometry, endpoint directions, revisions, and cargo. Preserve old path revisions so existing belts do not silently change shape.

The 0.3.0-Beta namespace change does not migrate earlier registry IDs. Use saves made with the current namespace for playtesting.

## 5. Optimize belt targeting

Relevant file: `src/main/java/com/sirjohnty/beltsnpieces/client/BeltPicking.java`.

This searches nearby block positions, gathers belts, then tests all 192 path segments and both visible runs. The earlier recording caught this work on the render thread.

- Reject irrelevant belt bounds before detailed intersection tests.
- Cache segment bounds and local coordinate frames with the path.
- Test only segments near the ray.
- Reuse candidate collections where practical.

Preserve accurate upper/lower-run targeting and the untargetable gap between them.

## 6. Cache placement and wrench preview geometry

The client reconstructs paths and runs substantial validation during preview ticks. Placement validation includes nested sample comparisons and surface generation. Wrench previews also build and validate candidate geometry.

Cache results when the snapped endpoint, curve style, or adjustment has not changed. Separate geometry validation from world clearance checks so camera movement within one target block does not rebuild everything.

Invalidate clearance results when relevant blocks change. Obstructed placements must not remain visually valid because of stale caches.

## 7. Reduce repeated route and ownership scans

`routeLoaded()` scans path samples and surface cells during ticking and inventory readiness checks. `ownsSurfacePosition()` creates a complete managed-position set for one membership query.

- Cache unique chunks touched by a path.
- Use direct cell-map membership for ordinary ownership checks.
- Avoid repeated readiness scans within one inventory operation.
- Stagger periodic integrity checks between belts.

Keep the rule that belts do not force-load their routes.

## 8. Reduce cargo synchronization overhead

Moving belts send data every five ticks while carrying cargo. Serialization includes geometry, elevation edits, shared surface positions, and complete item stacks.

Separate mostly static belt state from frequently changing cargo state. Send geometry when it changes and make routine cargo updates smaller.

Retain periodic correction and transfer IDs for smooth handoffs. Measure this on a dedicated server with several loaded belts.

## 9. Finish optimizing the renderer

Relevant file: `src/main/java/com/sirjohnty/beltsnpieces/client/ReinforcedBeltRenderer.java`.

Cross-sections are cached and distance-based detail exists, but considerable geometry and temporary vectors are still constructed every frame.

- Cache static vertex positions, normals, and subdivision decisions.
- Share geometry that does not vary between quality levels.
- Reduce repeated lighting lookups where safe.
- Investigate segment-level visibility rejection for long belts.
- Preserve the recent adaptive tread subdivision fix.

The earlier recording also caught buffer growth and vertex submission costs. Allocation reductions alone will not eliminate those.

## 10. Avoid walking connected belts twice per rendered belt

Texture scrolling and texture phase independently traverse upstream chains, allocating lists and maps. Each traversal can visit up to 128 belts.

Compute the shared animation anchor and phase together and reuse the result until topology or motion changes. Test reversals, loops, chunk reloads, and stopped networks to preserve seam alignment.

## 11. Narrow dropped-item and rider searches

Dropped-item collection searches the whole belt bounding box. Curved routes can enclose large areas away from the tread, and candidates require nearest-path calculations.

Use smaller spatial regions or segment bounds with duplicate filtering. Apply the same principle to rider detection.

Preserve pickup at rest, temporary pickup exclusion after ejection, and current handoff behavior.

## 12. Optimize rider movement without changing its feel

Relevant file: `src/main/java/com/sirjohnty/beltsnpieces/BeltRiding.java`.

Reuse projections within a movement step, reject distant segments early, and avoid unnecessary attribute updates.

Test walking, sprinting, crouching, jumping, uphill travel, high speeds, and handoffs. Check multiplayer rubber-banding separately from single-player smoothness.

## 13. Make placement state cleanup explicit

Placement and dragging use numerous static fields in `BeltsNPiecesClient`.

Centralize reset behavior for switching items or hands, dropping or moving selected stacks, dimension changes, disconnection, source shaft removal, and cancellation.

A small explicit state model would simplify maintenance. Preserve the sequence: select start shaft → lock horizontal endpoint → select height → place.

## 14. Show the actual reason a preview is invalid

Placement validation produces specific reasons, while previews mainly communicate validity through color. Wrench rejection also combines several causes into generic feedback.

Display concise reasons such as insufficient belts, missing shaft, obstructed surface, unloaded chunks, tight curve, or adjustment limit.

Use the same validation result for preview and server feedback wherever practical. This clarifies existing mechanics.

## 15. Make controls and HUD more consistent

- Stabilize layout as counts change.
- Separate belt cost and shaft requirement clearly.
- Standardize terminology and capitalization.
- Provide short reminders appropriate to the current placement stage.
- Reduce text construction and wrapping every rendered frame.

Keep the height cuboid and keep the removed blue endpoint adjustment arrow absent.

## 16. Check collision against visible geometry

Collision uses a narrower upper ribbon than the rendered belt. This may be intentional but needs systematic checking at edges, slopes, tight bends, and pulley joins.

Compare collision, selection outlines, and rendered surfaces together. Adjust only demonstrated mismatches. Do not automatically expand collision to the full casing silhouette if that harms existing movement.

## 17. Polish LOD transitions, lighting, and joins

Compare all five quality settings and distance boundaries. Inspect:

- Shape popping between detail levels.
- Texture pinching at tight curves.
- Casing folds on steep slopes.
- Lighting discontinuities between sections.
- Pulley joins and connected belt seams.
- Lower-run visibility and overlapping faces.

These are inspection targets, not a claim that every artifact currently occurs.

## 18. Make resource-pack handling more resilient

Steel casing sprites are generated from installed textures. The palette constructor can throw when no suitable metal colors exist, while the sprite loader currently catches only I/O failures.

Add graceful fallback for unsupported palettes. Check animated textures, unusual resolutions, and transparent pixels. Generated sprites currently discard resource animation metadata.

Cache texture resolution decisions and invalidate them on resource reload.

## 19. Verify casing integrations and interaction feedback

Test switching among andesite, brass, steel, and Extra Casing materials on regular and reinforced belts.

- Same-material clicks and whole-belt application.
- Switching steel back to another brass-based casing.
- Animated RGB casing.
- Client/server material synchronization.
- Repeated placement sounds when no material changes.

Deferred casing synchronization and mixins make this an important compatibility area.

## 20. Complete localization and tooltip consistency

Move hard-coded gameplay messages, style descriptions, and the creative-tab title into translation keys. Retain parameterized counts and limits. Standardize terminology across configuration, HUD, errors, and tooltips.

The incomplete reinforced belt uses the ordinary Create belt connector image. Consider a subtle distinction so assembly progress is recognizable in inventories and JEI.

## 21. Validate crafting and JEI presentation

The iron-sheet and andesite-sheet recipes are conditionally exclusive. Verify:

- Without a compatible andesite sheet tag, the iron recipe appears.
- With compatible sheets present, the andesite recipe appears.

Confirm the complete assembly sequence, intermediate item, output count, and JEI presentation. This is a consistency pass, not a balance change.

## 22. Clarify configuration limits

Maximum belt length accepts values up to `Integer.MAX_VALUE`, while geometric sampling remains fixed at 192 segments.

Define and document a practical supported range, or add safeguards for unusually large configured routes. Keep client quality settings purely visual. Clarify adjustment limits and server-controlled settings in configuration descriptions.

## 23. Improve build and compatibility reproducibility

`build.gradle` loads every JAR in `libs` and most JARs in `dev-libs`. Accidental duplicates or extra dependencies can change the environment.

Add dependency inventory checks and record tested versions. Verify minimal Create-only client, dedicated server startup, integrations present/absent, and declared Minecraft/NeoForge/Create compatibility ranges.

The chain conveyor mixin is client-only. Specifically verify that its configured angle behavior agrees with server acceptance.

## 24. Make regression checks routine and finish documentation

Add focused checks for item conservation, failed-placement rollback, save migration, geometry folding, and packet validation. Incorporate the existing geometry tool into the workflow.

Update `DEVELOPMENT.md`, which still mentions creating a test world despite the established Quick Play workflow. Make setup scripts report duplicate or stale dependencies and ensure `/restart` reliably selects Java 21.

Update the README with existing controls, costs, configuration, integrations, and tested versions.

Audit existing Ponder scenes for accuracy. Editing scenes, schematics, registrations, or tutorial text remains a separate expressly authorized change.

## Recommended first batches

1. Items 1–4: regression baseline, rollback, dismantling, and save compatibility.
2. Items 5–8: targeting, preview caching, route checks, and cargo synchronization.
3. Items 9–12: renderer, animation traversal, entity searches, and riding.
4. Items 13–15: state cleanup, validation feedback, and HUD consistency.
5. Items 16–21: collision/visual inspection, resource resilience, integration feedback, localization, and recipes.
6. Items 22–24: configuration safeguards, reproducibility, automated checks, and documentation. Add regression checks alongside earlier changes rather than postponing all verification until this final batch.

## Constraints throughout

- Preserve existing worlds, items, and checkpoint JARs.
- Preserve two-stage endpoint confirmation and initial height selection.
- Require a wrench for post-placement adjustments.
- Preserve the height preview cuboid and absence of the blue endpoint adjustment arrow.
- Obtain express permission before changing Ponder scenes, registrations, schematics, or tutorial text.
- Use routine development compilation/testing without version increments or Modrinth installation unless `buildit` is requested.
