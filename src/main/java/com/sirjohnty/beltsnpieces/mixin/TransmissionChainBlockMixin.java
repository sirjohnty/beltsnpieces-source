package com.sirjohnty.beltsnpieces.mixin;

import com.serpenssolida.createtransmission.content.chain.AbstractTransmissionChainBlock;
import com.serpenssolida.createtransmission.content.chain.TransmissionChainHelpers.ChainDirection;
import com.serpenssolida.createtransmission.content.chain.TransmissionChainHelpers.ChainSide;
import com.serpenssolida.createtransmission.content.chain.TransmissionChainHelpers.ConnectionType;
import com.sirjohnty.beltsnpieces.ReinforcedPulleyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional integration: only endpoint pulleys expose shafts, never belt spans. */
@Pseudo
@Mixin(targets = "com.serpenssolida.createtransmission.content.chain.AbstractTransmissionChainBlock", remap = false)
public abstract class TransmissionChainBlockMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void beltsnpieces$attachToPulley(BlockState state, LevelReader world, BlockPos pos,
                                         CallbackInfoReturnable<Boolean> result) {
        var facing = state.getValue(AbstractTransmissionChainBlock.FACING);
        var supportPos = pos.relative(facing);
        var support = world.getBlockState(supportPos);
        if (support.getBlock() instanceof ReinforcedPulleyBlock pulley
            && pulley.hasShaftTowards(world, supportPos, support, facing.getOpposite()))
            result.setReturnValue(true);
    }

    @Inject(method = "queryWorldForConnection", at = @At("HEAD"), cancellable = true)
    private static void beltsnpieces$connectToPulley(LevelAccessor world, BlockPos pos, BlockState state,
                                                  ChainSide side, CallbackInfoReturnable<ConnectionType> result) {
        var facing = ChainDirection.of(state.getValue(AbstractTransmissionChainBlock.FACING));
        var direction = facing.getDirectionFromSide(side);
        var neighbourPos = pos.relative(direction);
        var neighbour = world.getBlockState(neighbourPos);
        if (neighbour.getBlock() instanceof ReinforcedPulleyBlock pulley
            && pulley.hasShaftTowards(world, neighbourPos, neighbour, direction.getOpposite()))
            result.setReturnValue(ConnectionType.BELT);
    }
}
