package com.sirjohnty.beltsnpieces.client;

import com.sirjohnty.beltsnpieces.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Sprint jumps and deferred elytra activation from reinforced belts. */
@EventBusSubscriber(modid=BeltsNPieces.ID,value=Dist.CLIENT)
public final class BeltJumpInteraction {
    private BeltJumpInteraction() {}
    private static net.minecraft.client.player.LocalPlayer pendingElytraPlayer;
    private static int pendingElytraTicks;

    @SubscribeEvent public static void activateBeltElytra(ClientTickEvent.Post event) {
        if(pendingElytraPlayer==null) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=pendingElytraPlayer || mc.level==null || !pendingElytraPlayer.isAlive()) {
            pendingElytraPlayer=null;return;
        }
        if(mc.isPaused()) return;
        // LivingJumpEvent can run before Minecraft clears the grounded flag.
        if(!pendingElytraPlayer.onGround()) {
            var player=pendingElytraPlayer;pendingElytraPlayer=null;
            BeltElytraFlight.tryStart(player);
        } else if(--pendingElytraTicks<=0) pendingElytraPlayer=null;
    }

    @SubscribeEvent public static void sprintBeltJump(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        Minecraft mc=Minecraft.getInstance();
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)
            || player!=mc.player || !player.isSprinting() || player.getAbilities().flying
            || player.isPassenger() || player.isFallFlying()) return;
        var level=player.level();
        var feet=player.getBoundingBox();
        // Require actual collision support from a reinforced belt under the player.
        BlockPos center=BlockPos.containing(player.getX(),player.getY()-.08,player.getZ());
        if (com.simibubi.create.AllBlocks.BELT.has(level.getBlockState(center))) return;
        for (BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(feet.minX,feet.minY-.16,feet.minZ),
                BlockPos.containing(feet.maxX,feet.minY,feet.maxZ))) {
            if (!level.hasChunkAt(pos)) continue;
            var blockEntity=level.getBlockEntity(pos);
            ReinforcedPulleyBlockEntity belt=blockEntity instanceof ReinforcedBeltSpanBlockEntity span ? span.beltController()
                : blockEntity instanceof ReinforcedPulleyBlockEntity pulley ? pulley.root() : null;
            if (belt==null || belt.path()==null) continue;
            var shape=level.getBlockState(pos).getCollisionShape(level,pos).move(pos.getX(),pos.getY(),pos.getZ());
            if (net.minecraft.world.phys.shapes.Shapes.collide(Direction.Axis.Y,feet,java.util.List.of(shape),-.16)<=-.1599) continue;
            BeltPath path=belt.path();
            double distance=BeltRiding.progress(path,player.position());
            Vec3 tangent=path.atExtendedDistance(distance+.08)
                .subtract(path.atExtendedDistance(distance-.08)).normalize();
            double horizontal=Math.hypot(tangent.x,tangent.z);
            if (horizontal<1e-5) return;
            Vec3 direction=new Vec3(tangent.x/horizontal,0,tangent.z/horizontal);
            Vec3 current=player.getDeltaMovement();
            double beltSpeed=Math.clamp(belt.velocity(),-1.5,1.5);
            double sprintAlong=current.x*direction.x+current.z*direction.z;
            double uphill=Math.max(0,(sprintAlong+beltSpeed*horizontal)*tangent.y/horizontal);
            player.setDeltaMovement(current.add(tangent.x*beltSpeed,Math.min(.9,uphill),tangent.z*beltSpeed));
            if(Math.abs(beltSpeed)>1e-7) {
                pendingElytraPlayer=mc.player;pendingElytraTicks=6;
            }
            return;
        }
    }
}
