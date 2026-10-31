package com.sirjohnty.beltsnpieces.client;

import com.mojang.datafixers.util.Either;
import com.sirjohnty.beltsnpieces.BeltsNPieces;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

@EventBusSubscriber(modid=BeltsNPieces.ID,value=Dist.CLIENT)
public final class BeltsNPiecesTooltips {
    private BeltsNPiecesTooltips() {}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void modName(RenderTooltipEvent.GatherComponents event) {
        if(!BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getNamespace().equals(BeltsNPieces.ID)) return;
        var lines=event.getTooltipElements();
        if(lines.isEmpty()) return;
        String name=ModList.get().getModContainerById(BeltsNPieces.ID)
            .map(mod->mod.getModInfo().getDisplayName()).orElse("Create: Belts n Pieces");
        FormattedText label=Component.literal(name).withStyle(ChatFormatting.BLUE,ChatFormatting.ITALIC);
        // Match Create's tooltip order: item details (including Ponder), advanced
        // item ID, then the mod name. Preserve JEI's formatting when it supplies it.
        for(int i=lines.size()-1;i>0;i--) {
            var text=lines.get(i).left();
            if(text.isPresent() && text.get().getString().equals(name)) {
                label=text.get();lines.remove(i);
            }
        }
        lines.add(Either.left(label));
    }
}
