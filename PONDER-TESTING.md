# Ponder test pass

The plugin registers four storyboards using the existing self-contained `reinforced_belt.nbt` stage. Belt Void scenes build their ordinary Create belt inside that virtual stage. No player inventory, placement code, or real-world blocks are used.

## Mouseover checks

Check all three registered items in the creative inventory and JEI, with advanced tooltips (F3+H) both off and on:

| Item | Expected details |
| --- | --- |
| Reinforced Belt | Name, Ponder hint/details, advanced information when enabled, mod-name footer |
| Belt Void | Name, Ponder hint/details, advanced information when enabled, mod-name footer |
| Incomplete Reinforced Belt | Name, Create assembly/progress details where applicable, advanced information when enabled, mod-name footer; no Ponder hint |

`BeltsNPiecesTooltips` handles the entire namespace. It preserves all vanilla/Create/other-mod detail order and JEI footer formatting, removes duplicate copies of our mod-name line, and appends one footer. Future items inherit this without an item-specific list. Resource IDs appear only when supplied by advanced tooltips; the handler does not manufacture them.

## Storyboards

| Item | Scene | Coverage |
| --- | --- | --- |
| Reinforced Belt | Placing Reinforced Belts | Starting shaft, horizontal endpoint, curve selection, horizontal confirmation, height cuboid, final confirmation, materials and clearance |
| Reinforced Belt | Adjusting and Driving Reinforced Belts | Wrench-only adjustment after placement, height and horizontal movement, release/cancel, endpoint drive |
| Reinforced Belt | Transport, Casing and Removal | Animated cargo on a rising curve, dye, brass casing, casing removal controls, dismantling and one-time refund |
| Belt Void | Clearing Belt Overflow | Placement on a moving Create belt, normal pass-through, backed-up output, permanent disposal, restored flow |

Use the configured Ponder key over either finished item. Check every scene, keyframe skipping, replay, and scene switching. Verify captions fit, virtual belts render and animate, cargo is visible, the void flaps move, and no scene leaves items from an earlier replay.

The disposal demonstration explicitly stalls/removes a virtual Ponder item: it teaches the overflow rule rather than testing server-side inventory logic. It does not establish that the live overflow mixin works. Test real belt backpressure separately.

Compilation is verified with `gradlew classes prepareClientRun`; use `testit` for a fresh debug client. New scene methods/classes and plugin registrations require restarting the development client if one was already running. No release packaging or version increment is part of this pass.
