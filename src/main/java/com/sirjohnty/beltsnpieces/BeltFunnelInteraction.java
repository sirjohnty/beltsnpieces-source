package com.sirjohnty.beltsnpieces;

import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** The belt drives collection, as in Create's BeltFunnelInteractionHandler. */
final class BeltFunnelInteraction {
    private BeltFunnelInteraction() {}

    /** Returns true when an inline funnel holds the stack instead of letting it leave. */
    static boolean collect(ReinforcedPulleyBlockEntity belt,ReinforcedPulleyBlockEntity.Cargo cargo,double speed) {
        var level=belt.getLevel();
        var path=belt.path();
        for(int i=0;i<2;i++) {
            boolean start=(i==0)==(speed>0);
            double center=start?0:path.length;
            // Only visit endpoint blocks traversed during this movement, including fast belts.
            if(speed>0 ? cargo.previous>center+.5 || cargo.distance<=center-.5
                : cargo.previous<center-.5 || cargo.distance>=center+.5) continue;
            var pos=(start?belt.getBlockPos():belt.peer).above();
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof BeltFunnelBlock)) continue;
            Direction movement=start?path.startDirection:path.endDirection;
            if(speed<0) movement=movement.getOpposite();
            Direction facing=state.getValue(BeltFunnelBlock.HORIZONTAL_FACING);
            var shape=state.getValue(BeltFunnelBlock.SHAPE);
            if(facing==movement || shape==BeltFunnelBlock.Shape.PUSHING) continue;
            double entry=center-(shape==BeltFunnelBlock.Shape.EXTENDED?Math.copySign(.499,speed):0);
            // Match the endpoint output tolerance so an exact-boundary tick cannot eject first.
            if(speed>0 ? cargo.distance<entry-1e-6 : cargo.distance>entry+1e-6) continue;
            boolean blocking=facing==movement.getOpposite();
            if(blocking) cargo.distance=entry;
            if(!level.isClientSide && !state.getValue(BeltFunnelBlock.POWERED)
                && level.getBlockEntity(pos) instanceof FunnelBlockEntity funnel) {
                var inserting=funnel.getBehaviour(InvManipulationBehaviour.TYPE);
                var filtering=funnel.getBehaviour(FilteringBehaviour.TYPE);
                if(inserting!=null && (filtering==null || filtering.test(cargo.stack))) {
                    int amount=funnel.getAmountToExtract();
                    var mode=funnel.getModeToExtract();
                    boolean exact=mode!=ExtractionCountMode.UPTO;
                    if(!exact || amount<=cargo.stack.getCount()) {
                        ItemStack offered=cargo.stack.copy();
                        if(exact && amount!=-1) offered.setCount(Math.min(amount,offered.getCount()));
                        if(!exact || amount==-1 || inserting.simulate().insert(offered).isEmpty()) {
                            int offeredCount=offered.getCount();
                            ItemStack remainder=inserting.insert(offered);
                            int inserted=offeredCount-remainder.getCount();
                            if(inserted>0) {
                                funnel.flap(true);
                                funnel.onTransfer(cargo.stack.copyWithCount(inserted));
                                cargo.stack=cargo.stack.copyWithCount(cargo.stack.getCount()-inserted);
                            }
                        }
                    }
                }
            }
            if(blocking || cargo.stack.isEmpty()) return blocking;
        }
        return false;
    }
}
