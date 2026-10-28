package com.sirjohnty.bitsnbelts;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid=BitsNBelts.ID)
public final class BeltDyeInteraction {
    private BeltDyeInteraction() {}
    @SubscribeEvent public static void dye(PlayerInteractEvent.RightClickBlock event) {
        var player=event.getEntity();var level=event.getLevel();var pos=event.getPos();
        if(player.isShiftKeyDown() || !player.mayBuild() || !level.mayInteract(player,pos)) return;
        var be=level.getBlockEntity(pos);
        ReinforcedPulleyBlockEntity belt=be instanceof ReinforcedBeltSpanBlockEntity span?span.beltController()
            :be instanceof ReinforcedPulleyBlockEntity pulley?pulley.root():null;
        if(belt==null || belt.peer==null) return;
        var stack=event.getItemStack();
        DyeColor dye=stack.is(Tags.Items.DYES)?DyeColor.getColor(stack):null;
        boolean water=dye==null && GenericItemEmptying.emptyItem(level,stack,true).getFirst().getFluid().isSame(Fluids.WATER);
        if(dye==null && !water) return;
        if(!belt.setBeltColor(dye==null?-1:dye.getId())) return;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
