package com.sirjohnty.bitsnbelts.mixin;

import com.sirjohnty.bitsnbelts.*;
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
    @Unique private boolean bitsnbelts$steel;
    @Unique private BeltBlockEntity bitsnbelts$self() { return (BeltBlockEntity)(Object)this; }
    @Override public boolean bitsnbelts$isSteel() { return bitsnbelts$steel; }
    @Unique private void bitsnbelts$notify() {
        var self=bitsnbelts$self();
        self.notifyUpdate();
        if(self.hasLevel() && self.getLevel().isClientSide) {
            self.requestModelDataUpdate();
            self.getLevel().sendBlockUpdated(self.getBlockPos(),self.getBlockState(),self.getBlockState(),16);
        }
    }
    @Override public void bitsnbelts$applySteel() {
        var self=bitsnbelts$self();
        if(bitsnbelts$steel) return;
        // Let Create remove the previous material and establish the casing block state.
        self.setCasingType(BeltBlockEntity.CasingType.NONE);
        self.setCasingType(BeltBlockEntity.CasingType.BRASS);
        bitsnbelts$steel=true;
        bitsnbelts$notify();
    }
    @Inject(method="write",at=@At("TAIL"))
    private void bitsnbelts$write(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci) {
        tag.putBoolean("BitsNBeltsSteelCasing",bitsnbelts$steel);
    }
    @Inject(method="read",at=@At("RETURN"))
    private void bitsnbelts$read(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci) {
        boolean previous=bitsnbelts$steel;
        var material=BeltCasings.find(BLOCK);
        bitsnbelts$steel=tag.getBoolean("BitsNBeltsSteelCasing")
            && bitsnbelts$self().casing!=BeltBlockEntity.CasingType.NONE && material.available();
        if(previous!=bitsnbelts$steel && bitsnbelts$self().hasLevel() && bitsnbelts$self().getLevel().isClientSide)
            bitsnbelts$notify();
    }
    @Inject(method="getModelData",at=@At("RETURN"),cancellable=true)
    private void bitsnbelts$modelData(CallbackInfoReturnable<ModelData> result) {
        result.setReturnValue(result.getReturnValue().derive().with(MODEL_PROPERTY,bitsnbelts$steel).build());
    }
    @Inject(method="setCasingType",at=@At("HEAD"))
    private void bitsnbelts$replaceSameType(BeltBlockEntity.CasingType type,CallbackInfo ci) {
        var self=bitsnbelts$self();
        // Applying brass to steel otherwise hits Create's same-material early return.
        if(bitsnbelts$steel && type==self.casing) {
            if(!self.getLevel().isClientSide)
                self.getLevel().levelEvent(2001,self.getBlockPos(),Block.getId(BeltCasings.find(BLOCK).state()));
            bitsnbelts$steel=false;
            bitsnbelts$notify();
        }
    }
    @ModifyArg(method="setCasingType",at=@At(value="INVOKE",
        target="Lnet/minecraft/world/level/block/Block;getId(Lnet/minecraft/world/level/block/state/BlockState;)I"),index=0)
    private BlockState bitsnbelts$steelBreakEffects(BlockState original) {
        return bitsnbelts$steel?BeltCasings.find(BLOCK).state():original;
    }
    @Inject(method="setCasingType",at=@At("RETURN"))
    private void bitsnbelts$clearSteel(BeltBlockEntity.CasingType type,CallbackInfo ci) {
        if(bitsnbelts$steel) {
            bitsnbelts$steel=false;
            bitsnbelts$notify();
        }
    }
}
