package com.sirjohnty.beltsnpieces.mixin;

import com.sirjohnty.beltsnpieces.SteelBeltCasing;
import com.simibubi.create.content.kinetics.belt.BeltModel;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=BeltModel.class,remap=false)
public abstract class BeltModelSteelMixin {
    @Inject(method="getQuads",at=@At("RETURN"),cancellable=true)
    private void beltsnpieces$steelQuads(BlockState state,Direction side,RandomSource random,ModelData data,
            RenderType renderType,CallbackInfoReturnable<List<BakedQuad>> result) {
        if(!Boolean.TRUE.equals(data.get(SteelBeltCasing.MODEL_PROPERTY))) return;
        List<BakedQuad> quads=new ArrayList<>(result.getReturnValue());
        var atlas=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        for(int i=0;i<quads.size();i++) {
            BakedQuad quad=quads.get(i);
            var source=quad.getSprite();
            String name=source.contents().name().toString();
            ResourceLocation target=switch(name) {
                case "create:block/belt/brass_belt_casing" -> SteelBeltCasing.TEXTURE;
                case "create:block/brass_belt_cover" -> SteelBeltCasing.COVER_TEXTURE;
                case "create:block/funnel/brass_funnel_frame" -> SteelBeltCasing.FRAME_TEXTURE;
                default -> null;
            };
            if(target==null) continue;
            var sprite=atlas.apply(target);
            int[] sourceVertices=quad.getVertices();
            int[] vertices=java.util.Arrays.copyOf(sourceVertices,sourceVertices.length);
            for(int vertex=0;vertex<4;vertex++) {
                BakedQuadHelper.setU(vertices,vertex,sprite.getU(source.getUOffset(BakedQuadHelper.getU(vertices,vertex))));
                BakedQuadHelper.setV(vertices,vertex,sprite.getV(source.getVOffset(BakedQuadHelper.getV(vertices,vertex))));
            }
            quads.set(i,new BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),sprite,quad.isShade(),quad.hasAmbientOcclusion()));
        }
        result.setReturnValue(quads);
    }
    @Inject(method="getParticleIcon",at=@At("HEAD"),cancellable=true)
    private void beltsnpieces$steelParticle(ModelData data,CallbackInfoReturnable<TextureAtlasSprite> result) {
        if(Boolean.TRUE.equals(data.get(SteelBeltCasing.MODEL_PROPERTY)))
            result.setReturnValue(Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.parse("tfmg:block/steel_casing")));
    }
}
