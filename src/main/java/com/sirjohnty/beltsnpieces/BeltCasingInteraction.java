package com.sirjohnty.beltsnpieces;


import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid=BeltsNPieces.ID)
public final class BeltCasingInteraction {
    private BeltCasingInteraction() {}
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        var player=event.getEntity();var level=event.getLevel();var pos=event.getPos();
        if(!player.mayBuild() || !level.mayInteract(player,pos)) return;
        var entity=level.getBlockEntity(pos);
        if(player.isShiftKeyDown()) {
            if(!(entity instanceof BeltBlockEntity regular) || BeltCasings.fromItem(event.getItemStack())==null) return;
            if(!level.isClientSide) encaseSegment(event,regular);
            event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
            return;
        }
        if(entity instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity regular) {
            var material=BeltCasings.fromItem(event.getItemStack());
            if(material==null || !material.id().equals(SteelBeltCasing.BLOCK)) return;
            if(!level.isClientSide) {
                applySteel(regular);
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
    private static void applySteel(BeltBlockEntity belt) {
        ((SteelBeltCasing)belt).beltsnpieces$applySteel();
        ((com.simibubi.create.content.kinetics.belt.BeltBlock)belt.getBlockState().getBlock())
            .updateCoverProperty(belt.getLevel(),belt.getBlockPos(),belt.getBlockState());
    }
    private static void encaseSegment(PlayerInteractEvent.RightClickBlock event,BeltBlockEntity clicked) {
        var player=event.getEntity();var level=event.getLevel();var stack=event.getItemStack();
        var material=BeltCasings.fromItem(stack);
        var controller=clicked.getControllerBE();
        if(material==null || controller==null || controller.beltLength<=0) return;
        var segments=new java.util.ArrayList<BeltBlockEntity>();
        // Validate the whole controller-owned segment before applying any casing.
        for(int i=0;i<controller.beltLength;i++) {
            var pos=BeltHelper.getPositionForOffset(controller,i);
            if(!level.hasChunkAt(pos) || !level.mayInteract(player,pos)
                || !player.mayUseItemAt(pos,event.getFace(),stack)) return;
            var segment=BeltHelper.getSegmentBE(level,pos);
            if(segment==null || !controller.getBlockPos().equals(segment.getController())) return;
            segments.add(segment);
        }
        // Create's normal casing interaction ignores sneaking. Temporarily bypass
        // that gate, restoring the player's state even if an addon interaction fails.
        boolean sneaking=player.isShiftKeyDown();
        try {
            player.setShiftKeyDown(false);
            for(var segment:segments) {
                var pos=segment.getBlockPos();
                if(material.id().equals(SteelBeltCasing.BLOCK)) {
                    applySteel(segment);
                } else {
                    // Use the block interaction so optional casing addons retain
                    // their own material handling rather than becoming plain brass.
                    segment.getBlockState().useItemOn(stack,level,player,event.getHand(),
                        new BlockHitResult(Vec3.atCenterOf(pos),event.getFace(),pos,false));
                }
            }
        } finally {
            player.setShiftKeyDown(sneaking);
        }
        var sound=material.state().getSoundType(level,event.getPos(),player);
        level.playSound(null,event.getPos(),sound.getPlaceSound(),SoundSource.BLOCKS,
            (sound.getVolume()+1)/2,sound.getPitch()*.8f);
    }
}
