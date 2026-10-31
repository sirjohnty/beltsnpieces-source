package com.sirjohnty.beltsnpieces;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector3f;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Client flap motion follows transported stacks; overflow removals also send a block event. */
public final class BeltVoidBlockEntity extends BlockEntity {
    public float flap, previousFlap;
    public float exitFlap, previousExitFlap;
    private int pulseTicks;

    public BeltVoidBlockEntity(BlockPos pos, BlockState state) {
        super(BeltsNPieces.BELT_VOID_ENTITY.get(), pos, state);
    }

    @Override public boolean triggerEvent(int id, int data) {
        if (id == 1) {
            pulseTicks = 8;
            if (level != null && level.isClientSide) {
                level.playLocalSound(worldPosition.getX() + .5, worldPosition.getY() + .2,
                    worldPosition.getZ() + .5, SoundEvents.CHORUS_FRUIT_TELEPORT,
                    SoundSource.BLOCKS, .35f, 1.5f + level.random.nextFloat() * .2f, false);
            }
            return true;
        }
        return super.triggerEvent(id, data);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BeltVoidBlockEntity be) {
        // Keep idle particles local to viewers within 16 blocks.
        if (level.hasNearbyAlivePlayer(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 16)
            && level.random.nextInt(4) == 0) {
            Vector3f color = level.random.nextBoolean()
                ? new Vector3f(1f, .2f, .65f) : new Vector3f(.6f, .15f, 1f);
            level.addParticle(new DustParticleOptions(color, .8f),
                pos.getX() + .1 + level.random.nextDouble() * .8,
                pos.getY() + .4 + level.random.nextDouble() * .2,
                pos.getZ() + .1 + level.random.nextDouble() * .8, 0, .025, 0);
        }
        be.previousFlap = be.flap;
        be.previousExitFlap = be.exitFlap;
        boolean incoming = be.pulseTicks > 0;
        boolean outgoing = false;
        boolean entered = false;
        if (be.pulseTicks > 0) be.pulseTicks--;
        if (level.getBlockEntity(pos.below()) instanceof BeltBlockEntity belt && belt.getSpeed() != 0) {
            var controller = belt.getControllerBE();
            if (controller != null) {
                float mouth = belt.index + (controller.getDirectionAwareBeltMovementSpeed() > 0 ? .13f : .87f);
                float exit = belt.index + (controller.getDirectionAwareBeltMovementSpeed() > 0 ? .87f : .13f);
                for (var item : controller.getInventory().getTransportedItems()) {
                    // Trigger once on crossing, rather than every tick the flap is open.
                    if ((item.prevBeltPosition < mouth && item.beltPosition >= mouth)
                        || (item.prevBeltPosition > mouth && item.beltPosition <= mouth))
                        entered = true;
                    float min = Math.min(item.prevBeltPosition, item.beltPosition);
                    float max = Math.max(item.prevBeltPosition, item.beltPosition);
                    if (max > min && min <= mouth + .2f && max >= mouth - .2f) {
                        incoming = true;
                    }
                    if (max > min && min <= exit + .15f && max >= exit - .15f)
                        outgoing = true;
                }
            }
        }
        if (entered)
            AllSoundEvents.FUNNEL_FLAP.playAt(level, pos, 1, 1, true);
        float target = incoming ? 65 : 0;
        be.flap += (target - be.flap) * (incoming ? .65f : .22f);
        if (be.flap < .05f) be.flap = 0;
        be.exitFlap += ((outgoing ? 65 : 0) - be.exitFlap) * (outgoing ? .65f : .22f);
        if (be.exitFlap < .05f) be.exitFlap = 0;
    }
}
