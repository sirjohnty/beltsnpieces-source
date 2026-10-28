package com.sirjohnty.bitsnbelts;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Extra material information layered onto Create's normal belt casing state. */
public interface SteelBeltCasing {
    ResourceLocation BLOCK=ResourceLocation.parse("tfmg:steel_casing");
    ResourceLocation TEXTURE=BitsNBelts.id("block/belt/steel_belt_casing");
    ResourceLocation COVER_TEXTURE=BitsNBelts.id("block/belt/steel_belt_cover");
    ResourceLocation FRAME_TEXTURE=BitsNBelts.id("block/belt/steel_funnel_frame");
    ModelProperty<Boolean> MODEL_PROPERTY=new ModelProperty<>();
    boolean bitsnbelts$isSteel();
    void bitsnbelts$applySteel();
}
