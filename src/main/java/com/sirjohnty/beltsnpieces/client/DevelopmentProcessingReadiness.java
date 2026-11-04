package com.sirjohnty.beltsnpieces.client;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.sirjohnty.beltsnpieces.*;
import java.util.ArrayList;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Processing fixtures use only the already-validated empty development volume. */
final class DevelopmentProcessingReadiness {
    private DevelopmentProcessingReadiness() {}
    static void run(ReinforcedPulleyBlockEntity owner) {
        var level=owner.getLevel();
        var saved=new ArrayList<>(owner.cargo);
        var end=owner.peer;
        for(int rpm:new int[]{64,480}) for(boolean reversed:new boolean[]{false,true}) for(int location=0;location<3;location++) {
            boolean atStart=location==1;
            var processingPos=location==2?owner.getBlockPos().east(3):atStart?owner.getBlockPos():end;
            var chestPos=reversed?owner.getBlockPos().west():end.east();
            var machinePos=processingPos.above(2);
            try {
                owner.cargo.clear();
                level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),3);
                level.setBlock(machinePos,AllBlocks.MECHANICAL_PRESS.getDefaultState(),3);
                var press=(MechanicalPressBlockEntity)level.getBlockEntity(machinePos);
                owner.cargo.add(new ReinforcedPulleyBlockEntity.Cargo(new ItemStack(Items.IRON_INGOT,7),reversed?owner.path().length:0));
                for(int tick=0;tick<500;tick++) {
                    owner.updateSpeed=false;owner.other().updateSpeed=false;press.updateSpeed=false;
                    owner.setSpeed((reversed?-rpm:rpm)*BeltPath.driveSign(owner.getBlockState().getValue(ReinforcedPulleyBlock.AXIS),owner.startDirection));
                    press.setSpeed(256);
                    press.tick();
                    // This synchronous fixture has no generator; kinetic validation can reset its speed.
                    // Restore the simulated drive before the belt invokes the machine callbacks.
                    press.setSpeed(256);
                    var positions=new java.util.HashMap<java.util.UUID,Double>();
                    for(var cargo:owner.cargo) positions.put(cargo.id,cargo.distance);
                    owner.tick();
                    for(var cargo:owner.cargo) {
                        Double previous=positions.get(cargo.id);
                        if(previous!=null && (reversed?-1:1)*(cargo.distance-previous)<-1e-6)
                            throw new AssertionError("Processing cargo travelled against the belt: "+cargo.stack+" "+previous+" -> "+cargo.distance);
                    }
                }
                var chest=(ChestBlockEntity)level.getBlockEntity(chestPos);
                int sheets=0,iron=0;
                for(int slot=0;slot<chest.getContainerSize();slot++) {
                    var stack=chest.getItem(slot);
                    if(stack.is(AllItems.IRON_SHEET.get())) sheets+=stack.getCount();
                    if(stack.is(Items.IRON_INGOT)) iron+=stack.getCount();
                }
                var handler=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(level,processingPos,TransportedItemStackHandlerBehaviour.TYPE);
                LogUtils.getLogger().info("READINESS PROCESSING: rpm={}, reverse={}, location={}, sheets={}, unprocessed={}, handler={}",rpm,reversed,location,sheets,iron,handler);
                if(sheets!=7 || iron!=0) throw new AssertionError("Pulley press did not process all seven ingots");
            } finally {
                var chest=level.getBlockEntity(chestPos);
                if(chest instanceof ChestBlockEntity inventory) inventory.clearContent();
                level.removeBlock(machinePos,false);level.removeBlock(chestPos,false);
                owner.cargo.clear();owner.cargo.addAll(saved);owner.setSpeed(0);
            }
        }
    }
}
