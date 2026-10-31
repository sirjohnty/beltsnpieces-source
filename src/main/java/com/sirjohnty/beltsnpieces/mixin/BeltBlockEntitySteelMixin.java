package com.sirjohnty.beltsnpieces.mixin;

import com.sirjohnty.beltsnpieces.*;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=BeltBlockEntity.class,remap=false)
public abstract class BeltBlockEntitySteelMixin implements SteelBeltCasing {
    @Unique private boolean beltsnpieces$steel;
    @Unique private boolean beltsnpieces$applyingSteel;
    @Unique private BeltBlockEntity beltsnpieces$self() { return (BeltBlockEntity)(Object)this; }
    @Override public boolean beltsnpieces$isSteel() { return beltsnpieces$steel; }
    @Redirect(method="setCasingType",at=@At(value="INVOKE",
        target="Lcom/simibubi/create/content/kinetics/belt/BeltBlockEntity;sendData()V"))
    private void beltsnpieces$deferCasingPacket(BeltBlockEntity belt) {
        // Addons commonly set BRASS first, then store their actual material.
        // Sending here exposes that temporary state to the client renderer.
        BeltCasingUpdates.defer(belt);
    }
    @Unique private void beltsnpieces$notify() {
        var self=beltsnpieces$self();
        self.notifyUpdate();
        if(self.hasLevel() && self.getLevel().isClientSide) {
            self.requestModelDataUpdate();
            self.getLevel().sendBlockUpdated(self.getBlockPos(),self.getBlockState(),self.getBlockState(),16);
        }
    }
    @Override public void beltsnpieces$applySteel() {
        var self=beltsnpieces$self();
        if(beltsnpieces$steel) return;
        // Let Create remove the previous material and establish the casing block state.
        self.setCasingType(BeltBlockEntity.CasingType.NONE);
        // Create synchronizes inside setCasingType, so its first brass-shaped
        // casing update must already carry the steel material flag.
        beltsnpieces$steel=true;
        beltsnpieces$applyingSteel=true;
        try {
            self.setCasingType(BeltBlockEntity.CasingType.BRASS);
        } finally {
            beltsnpieces$applyingSteel=false;
        }
        beltsnpieces$notify();
    }
    @Inject(method="write",at=@At("TAIL"))
    private void beltsnpieces$write(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci) {
        tag.putBoolean("BeltsNPiecesSteelCasing",beltsnpieces$steel);
    }
    @Inject(method="read",at=@At("RETURN"))
    private void beltsnpieces$read(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci) {
        boolean previous=beltsnpieces$steel;
        var material=BeltCasings.find(BLOCK);
        beltsnpieces$steel=tag.getBoolean("BeltsNPiecesSteelCasing")
            && beltsnpieces$self().casing!=BeltBlockEntity.CasingType.NONE && material.available();
        if(previous!=beltsnpieces$steel && beltsnpieces$self().hasLevel() && beltsnpieces$self().getLevel().isClientSide)
            beltsnpieces$notify();
    }
    @Inject(method="getModelData",at=@At("RETURN"),cancellable=true)
    private void beltsnpieces$modelData(CallbackInfoReturnable<ModelData> result) {
        result.setReturnValue(result.getReturnValue().derive().with(MODEL_PROPERTY,beltsnpieces$steel).build());
    }
    @Inject(method="setCasingType",at=@At("HEAD"))
    private void beltsnpieces$replaceSameType(BeltBlockEntity.CasingType type,CallbackInfo ci) {
        var self=beltsnpieces$self();
        // Applying brass to steel otherwise hits Create's same-material early return.
        if(!beltsnpieces$applyingSteel && beltsnpieces$steel && type==self.casing) {
            if(!self.getLevel().isClientSide)
                self.getLevel().levelEvent(2001,self.getBlockPos(),Block.getId(BeltCasings.find(BLOCK).state()));
            beltsnpieces$steel=false;
            beltsnpieces$notify();
        }
    }
    @ModifyArg(method="setCasingType",at=@At(value="INVOKE",
        target="Lnet/minecraft/world/level/block/Block;getId(Lnet/minecraft/world/level/block/state/BlockState;)I"),index=0)
    private BlockState beltsnpieces$steelBreakEffects(BlockState original) {
        return beltsnpieces$steel?BeltCasings.find(BLOCK).state():original;
    }
    @Inject(method="setCasingType",at=@At("RETURN"))
    private void beltsnpieces$clearSteel(BeltBlockEntity.CasingType type,CallbackInfo ci) {
        if(!beltsnpieces$applyingSteel && beltsnpieces$steel) {
            beltsnpieces$steel=false;
            beltsnpieces$notify();
        }
    }
}
