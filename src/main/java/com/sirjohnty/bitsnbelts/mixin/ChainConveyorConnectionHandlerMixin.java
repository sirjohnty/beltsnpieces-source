package com.sirjohnty.bitsnbelts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.sirjohnty.bitsnbelts.BitsNBeltsConfig;

/**
 * Changes only the local value used by Create's two inclination checks.
 * Real positions, lengths, costs, packets and movement vectors stay untouched.
 * No Minecraft or Create symbols need remapping: the owner and method are
 * Create's own stable names, and the handler signature contains only a double.
 */
@Mixin(targets = "com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionHandler", remap = false)
public abstract class ChainConveyorConnectionHandlerMixin {
    @ModifyVariable(
        method = "validateAndConnect",
        at = @At("STORE"),
        ordinal = 0,
        require = 1,
        allow = 1,
        remap = false
    )
    private static double bitsnbelts$configuredInclination(double horizontalDistance) {
        int degrees=BitsNBeltsConfig.maxChainConveyorAngle();
        if(degrees==90) return Double.POSITIVE_INFINITY;
        if(horizontalDistance<=0 || degrees==45) return horizontalDistance;
        if(degrees==0) return Double.MIN_NORMAL;
        return horizontalDistance/Math.tan(Math.toRadians(degrees));
    }
}
