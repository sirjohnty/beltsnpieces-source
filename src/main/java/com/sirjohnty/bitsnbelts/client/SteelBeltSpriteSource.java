package com.sirjohnty.bitsnbelts.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.sirjohnty.bitsnbelts.SteelBeltCasing;
import java.io.IOException;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;

/** Build the belt layout from installed resources, preserving Create's pulley cutouts. */
public final class SteelBeltSpriteSource implements SpriteSource {
    public static final SpriteSourceType TYPE=new SpriteSourceType(MapCodec.unit(SteelBeltSpriteSource::new));
    @Override public SpriteSourceType type() { return TYPE; }
    @Override public void run(ResourceManager resources,Output output) {
        var steel=resources.getResource(ResourceLocation.parse("tfmg:textures/block/steel_casing.png"));
        if(steel.isEmpty()) return;
        add(resources,output,steel.get(),SteelBeltCasing.TEXTURE,"belt/brass_belt_casing","belt/andesite_belt_casing");
        add(resources,output,steel.get(),SteelBeltCasing.COVER_TEXTURE,"brass_belt_cover","andesite_belt_cover");
        add(resources,output,steel.get(),SteelBeltCasing.FRAME_TEXTURE,"funnel/brass_funnel_frame","funnel/andesite_funnel_frame");
    }
    private static void add(ResourceManager resources,Output output,Resource steel,ResourceLocation target,
            String brass,String andesite) {
        var template=resources.getResource(ResourceLocation.parse("create:textures/block/"+brass+".png"));
        var reference=resources.getResource(ResourceLocation.parse("create:textures/block/"+andesite+".png"));
        if(template.isEmpty() || reference.isEmpty()) return;
        output.add(target,loader->{
            try(var steelStream=steel.open();var templateStream=template.get().open();var referenceStream=reference.get().open();
                    NativeImage material=NativeImage.read(steelStream);NativeImage layout=NativeImage.read(templateStream);
                    NativeImage trim=NativeImage.read(referenceStream)) {
                int[] colors=new int[material.getWidth()*material.getHeight()];
                for(int y=0;y<material.getHeight();y++) for(int x=0;x<material.getWidth();x++)
                    colors[y*material.getWidth()+x]=material.getPixelRGBA(x,y);
                var palette=new SteelCasingPalette(colors);
                NativeImage image=new NativeImage(layout.getWidth(),layout.getHeight(),false);
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int sx=x*trim.getWidth()/image.getWidth(),sy=y*trim.getHeight()/image.getHeight();
                    image.setPixelRGBA(x,y,palette.recolor(layout.getPixelRGBA(x,y),trim.getPixelRGBA(sx,sy)));
                }
                return new SpriteContents(target,new FrameSize(image.getWidth(),image.getHeight()),
                    image,ResourceMetadata.EMPTY);
            } catch(IOException error) {
                LogUtils.getLogger().error("Could not generate steel belt texture {}",target,error);
                return null;
            }
        });
    }
}
