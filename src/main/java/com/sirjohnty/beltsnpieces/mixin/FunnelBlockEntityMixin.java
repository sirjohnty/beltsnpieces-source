package com.sirjohnty.beltsnpieces.mixin;

import com.sirjohnty.beltsnpieces.ReinforcedPulleyBlockEntity;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolve inline funnel direction for reinforced belts, as Create does for its belts. */
@Mixin(value = FunnelBlockEntity.class, remap = false)
public abstract class FunnelBlockEntityMixin {
    @Inject(method = "supportsAmountOnFilter", at = @At("RETURN"), cancellable = true)
    private void beltsnpieces$reinforcedAmount(CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue()) return;
        var self = (BlockEntity) (Object) this;
        var level = self.getLevel();
        if (level != null && self.getBlockState().getBlock() instanceof BeltFunnelBlock
            && level.getBlockEntity(self.getBlockPos().below()) instanceof ReinforcedPulleyBlockEntity belt
            && belt.peer != null)
            result.setReturnValue(true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "determineCurrentMode", at = @At("RETURN"), cancellable = true)
    private void beltsnpieces$reinforcedMode(CallbackInfoReturnable<Object> result) {
        // Mode is package-private in Create. Resolve its existing enum constants
        // without copying or exposing Create classes in our distributed namespace.
        if (!(result.getReturnValue() instanceof Enum<?> mode) || !mode.name().equals("INVALID")) return;
        var self = (BlockEntity) (Object) this;
        var level = self.getLevel();
        var state = self.getBlockState();
        if (level == null || !(state.getBlock() instanceof BeltFunnelBlock)
            || !(level.getBlockEntity(self.getBlockPos().below()) instanceof ReinforcedPulleyBlockEntity belt)
            || belt.peer == null || belt.root() == null) return;
        var travel = belt.controller ? belt.startDirection : belt.endDirection;
        if (belt.root().velocity() < 0) travel = travel.getOpposite();
        String resolved = travel == state.getValue(BeltFunnelBlock.HORIZONTAL_FACING)
            ? "PUSHING_TO_BELT" : "TAKING_FROM_BELT";
        result.setReturnValue(Enum.valueOf((Class) mode.getDeclaringClass(), resolved));
    }
}
