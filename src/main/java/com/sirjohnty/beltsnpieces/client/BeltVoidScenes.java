package com.sirjohnty.beltsnpieces.client;

import com.sirjohnty.beltsnpieces.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Scripted virtual demonstrations; no player inventory or save is touched. */
public final class BeltVoidScenes {
    private BeltVoidScenes() {}
    private static final BlockPos VOID = new BlockPos(4, 2, 4);
    private static final BlockPos START = new BlockPos(4, 1, 1);

    private static CreateSceneBuilder stage(SceneBuilder builder, SceneBuildingUtil util,
            String id, String title) {
        var scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.7f);
        scene.showBasePlate();
        for (int z = 1; z <= 7; z++) {
            BlockPos pos = new BlockPos(4, 1, z);
            scene.world().setBlock(pos, AllBlocks.BELT.getDefaultState()
                .setValue(BeltBlock.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BeltBlock.SLOPE, BeltSlope.HORIZONTAL)
                .setValue(BeltBlock.PART, z == 1 ? BeltPart.START : z == 7 ? BeltPart.END : BeltPart.MIDDLE), false);
            final int index = z - 1;
            scene.world().modifyBlockEntity(pos, BeltBlockEntity.class, belt -> {
                belt.setController(START);
                belt.beltLength = 7;
                belt.index = index;
            });
        }
        // Follow Create's belt scenes: reveal the line in short, staggered sections.
        for (int z = 1; z <= 7; z++) {
            scene.world().showSection(util.select().position(4, 1, z), Direction.DOWN);
            scene.idle(3);
        }
        scene.world().setKineticSpeed(util.select().fromTo(4, 1, 1, 4, 1, 7), 32);
        scene.idle(20);
        return scene;
    }

    private static void install(CreateSceneBuilder scene, SceneBuildingUtil util) {
        Vec3 target = Vec3.atCenterOf(VOID);
        scene.overlay().showControls(target, Pointing.DOWN, 40).rightClick()
            .withItem(new ItemStack(BeltsNPieces.BELT_VOID_ITEM.get()));
        scene.world().setBlock(VOID, BeltsNPieces.BELT_VOID.get().defaultBlockState()
            .setValue(BeltVoidBlock.FACING, Direction.NORTH), false);
        var housing = scene.world().showIndependentSection(util.select().position(VOID), Direction.DOWN);
        scene.world().moveSection(housing, new Vec3(0, .5, 0), 0);
        scene.world().moveSection(housing, new Vec3(0, -.5, 0), 10);
        scene.idle(45);
    }

    public static void passage(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = stage(builder, util, "belt_void_passage", "Clearing Belt Overflow");
        scene.overlay().showText(55).text("Place a Belt Void on a section of a moving Create belt.")
            .pointAt(Vec3.atCenterOf(VOID.below())).placeNearTarget();
        install(scene, util);
        scene.idle(15);
        scene.world().createItemOnBelt(new BlockPos(4, 1, 2), Direction.UP, new ItemStack(Items.IRON_INGOT));
        scene.overlay().showText(50).text("Items pass through while the output is clear.")
            .colored(PonderPalette.GREEN).pointAt(Vec3.atCenterOf(VOID)).placeNearTarget();
        scene.idle(65);
        scene.world().removeItemsFromBelt(START);
        scene.addKeyframe();
        scene.world().setBlock(new BlockPos(4, 1, 8), Blocks.STONE.defaultBlockState(), false);
        scene.world().showSection(util.select().position(4, 1, 8), Direction.DOWN);
        // Native Ponder belt elements visibly queue from the output back to the void.
        for (int z = 7; z >= 5; z--) {
            var waiting = scene.world().createItemOnBelt(new BlockPos(4, 1, z), Direction.UP,
                new ItemStack(Items.COBBLESTONE, 16));
            scene.world().stallBeltItem(waiting, true);
            scene.idle(8);
        }
        var queued = scene.world().createItemOnBelt(VOID.below(), Direction.UP, new ItemStack(Items.COBBLESTONE, 16));
        scene.world().stallBeltItem(queued, true);
        scene.overlay().showText(60).text("When the output backs up to this section, the Belt Void destroys the excess items.")
            .pointAt(Vec3.atCenterOf(VOID.below())).placeNearTarget();
        scene.idle(40);
        scene.world().changeBeltItemTo(queued, ItemStack.EMPTY);
        scene.world().modifyBlockEntity(VOID, BeltVoidBlockEntity.class, be -> be.triggerEvent(1, 0));
        scene.idle(25);
        scene.overlay().showText(45).text("Voided items are gone for good.")
            .colored(PonderPalette.RED).pointAt(Vec3.atCenterOf(VOID)).placeNearTarget();
        scene.idle(50);
        scene.addKeyframe();
        scene.world().setBlock(new BlockPos(4, 1, 8), Blocks.AIR.defaultBlockState(), false);
        scene.world().removeItemsFromBelt(START);
        scene.world().createItemOnBelt(new BlockPos(4, 1, 2), Direction.UP, new ItemStack(Items.IRON_INGOT));
        scene.overlay().showText(50).text("Clear the output and items pass through again.")
            .colored(PonderPalette.GREEN).pointAt(Vec3.atCenterOf(VOID)).placeNearTarget();
        scene.idle(65);
        scene.world().removeItemsFromBelt(START);
        scene.markAsFinished();
    }
}
