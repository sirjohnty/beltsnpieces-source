package com.sirjohnty.beltsnpieces.mixin;

import com.sirjohnty.beltsnpieces.client.BeltPicking;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method="pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at=@At("RETURN"),cancellable=true)
    private void beltsnpieces$pickVisibleRuns(Entity camera,double blockReach,double entityReach,float partial,
            CallbackInfoReturnable<HitResult> result) {
        result.setReturnValue(BeltPicking.pick(camera,blockReach,partial,result.getReturnValue()));
    }
}
