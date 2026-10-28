package com.sirjohnty.bitsnbelts;


import com.simibubi.create.AllItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid=BitsNBelts.ID)
public final class BeltCasingInteraction {
    private BeltCasingInteraction() {}
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        var player=event.getEntity();var level=event.getLevel();var pos=event.getPos();
        if(player.isShiftKeyDown() || !player.mayBuild() || !level.mayInteract(player,pos)) return;
        var entity=level.getBlockEntity(pos);
        if(entity instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity regular) {
            var material=BeltCasings.fromItem(event.getItemStack());
            if(material==null || !material.id().equals(SteelBeltCasing.BLOCK)) return;
            if(!level.isClientSide) {
                ((SteelBeltCasing)regular).bitsnbelts$applySteel();
                ((com.simibubi.create.content.kinetics.belt.BeltBlock)regular.getBlockState().getBlock())
                    .updateCoverProperty(level,pos,regular.getBlockState());
                var sound=material.state().getSoundType(level,pos,player);
                level.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,
                    (sound.getVolume()+1)/2,sound.getPitch()*.8f);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
            return;
        }
        ReinforcedPulleyBlockEntity belt=entity instanceof ReinforcedBeltSpanBlockEntity span?span.beltController()
            :entity instanceof ReinforcedPulleyBlockEntity pulley?pulley.root():null;
        if(belt==null || belt.peer==null) return;
        ItemStack stack=event.getItemStack();
        boolean wrench=AllItems.WRENCH.isIn(stack);
        var material=BeltCasings.fromItem(stack);
        var previous=belt.casing();
        if(wrench && previous!=null) {
            // Create reserves the upper face for belt tools; side/bottom use removes casing.
            if(event.getFace()==Direction.UP) return;
            if(!level.isClientSide && belt.setCasing(null)) {
                var previousState=previous.state();
                level.levelEvent(2001,pos,Block.getId(previousState));
            }
        } else if(material!=null) {
            if(!level.isClientSide) {
                boolean changed=belt.setCasing(material.id());
                if(changed && previous!=null) {
                    var previousState=previous.state();
                    level.levelEvent(2001,pos,Block.getId(previousState));
                }
                var casingState=material.state();
                var sound=casingState.getSoundType(level,pos,player);
                level.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,
                    (sound.getVolume()+1)/2,sound.getPitch()*.8f);
            }
            // Default Create belts neither consume nor refund casing items.
        } else return;
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
}
