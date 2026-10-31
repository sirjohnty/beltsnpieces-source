package com.sirjohnty.beltsnpieces.mixin;

import com.sirjohnty.beltsnpieces.BeltsNPieces;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BeltInventory.class, remap = false)
public abstract class BeltInventoryVoidMixin {
    @Shadow @Final private BeltBlockEntity belt;

    @Shadow @Final private java.util.List<TransportedItemStack> items;

    @Inject(method = "tick", at = @At("TAIL"))
    private void beltsnpieces$voidOverflow(CallbackInfo ci) {
        if (belt.getLevel().isClientSide)
            return;
        float speed = belt.getDirectionAwareBeltMovementSpeed();
        if (speed == 0)
            return;
        boolean removed = false;
        var iterator = items.iterator();
        while (iterator.hasNext()) {
            TransportedItemStack item = iterator.next();
            if (item.locked || item.lockedExternally)
                continue;
            // Compare actual travel to the belt's free-flow speed, in either direction.
            if (Math.abs(item.beltPosition - item.prevBeltPosition) + 0.00001f >= Math.abs(speed))
                continue;
            int segment = (int) Math.floor(item.beltPosition);
            if (segment < 0 || segment >= belt.beltLength)
                continue;
            if (belt.getLevel().getBlockState(BeltHelper.getPositionForOffset(belt, segment).above())
                    .is(BeltsNPieces.BELT_VOID.get())) {
                iterator.remove();
                var voidPos = BeltHelper.getPositionForOffset(belt, segment).above();
                belt.getLevel().blockEvent(voidPos, BeltsNPieces.BELT_VOID.get(), 1, 0);
                removed = true;
            }
        }
        if (removed)
            belt.notifyUpdate();
    }
}
