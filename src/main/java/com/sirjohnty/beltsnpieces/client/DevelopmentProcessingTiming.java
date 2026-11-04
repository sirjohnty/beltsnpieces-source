package com.sirjohnty.beltsnpieces.client;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.sirjohnty.beltsnpieces.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** Compare release events with the pinned Create implementation, not just final item counts. */
final class DevelopmentProcessingTiming {
    private DevelopmentProcessingTiming() {}
    static void run(ReinforcedPulleyBlockEntity owner) {
        var level=owner.getLevel();
        var saved=new ArrayList<>(owner.cargo);
        BlockPos a=owner.getBlockPos(),normalStart=a.south(2),normalEnd=normalStart.east(6);
        BlockPos station=a,normalStation=normalStart;
        var shaft=AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS,Direction.Axis.Z);
        try {
            owner.cargo.clear();
            level.setBlock(normalStart,shaft,3);level.setBlock(normalEnd,shaft,3);
            BeltConnectorItem.createBelts(level,normalStart,normalEnd);
            BeltBlock.initBelt(level,normalStart);
            var normalSegment=(BeltBlockEntity)level.getBlockEntity(normalStation);
            var normal=normalSegment.getControllerBE();
            level.setBlock(station.above(2),AllBlocks.MECHANICAL_PRESS.getDefaultState(),3);
            level.setBlock(normalStation.above(2),AllBlocks.MECHANICAL_PRESS.getDefaultState(),3);
            var press=(MechanicalPressBlockEntity)level.getBlockEntity(station.above(2));
            var normalPress=(MechanicalPressBlockEntity)level.getBlockEntity(normalStation.above(2));
            double center=0;
            float normalCenter=normalSegment.index+.5f;
            owner.cargo.add(new ReinforcedPulleyBlockEntity.Cargo(new ItemStack(Items.IRON_INGOT,7),center-.1));
            var input=new TransportedItemStack(new ItemStack(Items.IRON_INGOT,7));
            input.beltPosition=normalCenter-.1f;
            normal.getInventory().addItem(input);
            int normalRelease=-1,reinforcedRelease=-1;
            for(int tick=0;tick<250;tick++) {
                owner.updateSpeed=false;owner.other().updateSpeed=false;
                press.updateSpeed=false;normalPress.updateSpeed=false;
                press.setSpeed(256);normalPress.setSpeed(256);
                press.tick();normalPress.tick();
                press.setSpeed(256);normalPress.setSpeed(256);
                owner.setSpeed(64*BeltPath.driveSign(owner.getBlockState().getValue(ReinforcedPulleyBlock.AXIS),owner.startDirection));
                normal.setSpeed(64);
                if(normal.getDirectionAwareBeltMovementSpeed()<0) normal.setSpeed(-64);
                normal.getInventory().tick();
                owner.tick();
                if(normalRelease<0) for(var cargo:normal.getInventory().getTransportedItems())
                    if(cargo.stack.is(AllItems.IRON_SHEET.get()) && !cargo.locked && cargo.beltPosition>normalCenter+1.0/64) normalRelease=tick;
                if(reinforcedRelease<0) for(var cargo:owner.cargo)
                    if(cargo.stack.is(AllItems.IRON_SHEET.get()) && cargo.distance>center+1.0/64) reinforcedRelease=tick;
            }
            LogUtils.getLogger().info("READINESS RELEASE TIMING: regular={}, reinforced={}",normalRelease,reinforcedRelease);
            if(normalRelease<0 || reinforcedRelease<0 || Math.abs(normalRelease-reinforcedRelease)>2)
                throw new AssertionError("First processed output does not match regular belt timing");
        } finally {
            for(BlockPos pos:BlockPos.betweenClosed(normalStart,normalEnd))
                if(level.getBlockEntity(pos) instanceof BeltBlockEntity belt && belt.isController()) belt.getInventory().getTransportedItems().clear();
            for(BlockPos pos:BlockPos.betweenClosed(normalStart,normalEnd)) level.removeBlock(pos,false);
            level.removeBlock(station.above(2),false);level.removeBlock(normalStation.above(2),false);
            owner.cargo.clear();owner.cargo.addAll(saved);owner.setSpeed(0);
        }
    }
}
