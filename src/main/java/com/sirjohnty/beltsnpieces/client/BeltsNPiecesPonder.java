package com.sirjohnty.beltsnpieces.client;

import com.sirjohnty.beltsnpieces.BeltsNPieces;
import net.createmod.ponder.api.registration.*;
import net.minecraft.resources.ResourceLocation;

/** Item tutorials, available through Ponder's configured key binding. */
public final class BeltsNPiecesPonder implements PonderPlugin {
    @Override public String getModId() { return BeltsNPieces.ID; }
    @Override public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.addTagToComponent(BeltsNPieces.id("reinforced_belt"),com.simibubi.create.infrastructure.ponder.AllCreatePonderTags.KINETIC_RELAYS);
        helper.addTagToComponent(BeltsNPieces.id("reinforced_belt"),com.simibubi.create.infrastructure.ponder.AllCreatePonderTags.LOGISTICS);
        helper.addTagToComponent(BeltsNPieces.id("belt_void"),com.simibubi.create.infrastructure.ponder.AllCreatePonderTags.LOGISTICS);
    }
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(BeltsNPieces.id("reinforced_belt"),"reinforced_belt",ReinforcedBeltScenes::placement);
        helper.addStoryBoard(BeltsNPieces.id("reinforced_belt"),"reinforced_belt",ReinforcedBeltScenes::adjustments);
        helper.addStoryBoard(BeltsNPieces.id("reinforced_belt"),"reinforced_belt",ReinforcedBeltScenes::transport);
        helper.addStoryBoard(BeltsNPieces.id("belt_void"),"reinforced_belt",BeltVoidScenes::passage);
    }
}
