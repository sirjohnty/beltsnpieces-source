package com.sirjohnty.beltsnpieces;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Extra material information layered onto Create's normal belt casing state. */
public interface SteelBeltCasing {
    ResourceLocation BLOCK=ResourceLocation.parse("tfmg:steel_casing");
    ResourceLocation TEXTURE=BeltsNPieces.id("block/belt/steel_belt_casing");
    ResourceLocation COVER_TEXTURE=BeltsNPieces.id("block/belt/steel_belt_cover");
    ResourceLocation FRAME_TEXTURE=BeltsNPieces.id("block/belt/steel_funnel_frame");
    ModelProperty<Boolean> MODEL_PROPERTY=new ModelProperty<>();
    boolean beltsnpieces$isSteel();
    void beltsnpieces$applySteel();
}
