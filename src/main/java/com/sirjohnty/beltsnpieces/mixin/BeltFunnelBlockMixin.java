package com.sirjohnty.beltsnpieces.mixin;

import com.sirjohnty.beltsnpieces.ReinforcedPulleyBlockEntity;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use Create's inline/retracted funnel shape on aligned reinforced endpoints. */
@Mixin(value = BeltFunnelBlock.class, remap = false)
public abstract class BeltFunnelBlockMixin {
    @Inject(method = "getShapeForPosition", at = @At("HEAD"), cancellable = true)
    private static void beltsnpieces$alignFunnel(BlockGetter level, BlockPos pos,
            Direction facing, boolean pushing, CallbackInfoReturnable<BeltFunnelBlock.Shape> result) {
        if (!(level.getBlockEntity(pos.below()) instanceof ReinforcedPulleyBlockEntity belt)
            || belt.peer == null) return;
        Direction travel = belt.controller ? belt.startDirection : belt.endDirection;
        // Exactly the same axis comparison Create uses for ordinary belts.
        if (travel.getAxis() == facing.getAxis())
            result.setReturnValue(BeltFunnelBlock.Shape.RETRACTED);
    }
}
