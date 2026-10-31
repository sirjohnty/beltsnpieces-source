package com.sirjohnty.beltsnpieces;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Optional integrations use registry names, without linking to addon classes. */
public final class BeltCasings {
    private static final Map<ResourceLocation,Material> MATERIALS=new LinkedHashMap<>();
    public static final ResourceLocation ANDESITE=ResourceLocation.parse("create:andesite_casing");
    public static final ResourceLocation BRASS=ResourceLocation.parse("create:brass_casing");
    public record Material(ResourceLocation id,ResourceLocation texture,boolean beltAtlas) {
        public boolean available() { return BuiltInRegistries.BLOCK.containsKey(id); }
        public BlockState state() { return BuiltInRegistries.BLOCK.get(id).defaultBlockState(); }
    }
    static {
        add(ANDESITE.toString(),"create:block/belt/andesite_belt_casing",true);
        add(BRASS.toString(),"create:block/belt/brass_belt_casing",true);
        for(String color:List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray",
                "light_gray","cyan","purple","blue","brown","green","red","black","rgb")) {
            for(String metal:List.of("","brass_","copper_")) {
                String texture=color+"_"+metal+"belt_casing";
                // These four filenames use a different word order in Extra Casing's assets.
                if(!metal.isEmpty() && (color.equals("light_blue") || color.equals("light_gray")))
                    texture=color+"_belt_"+metal+"casing";
                add("create_extra_casing:"+color+"_"+metal+"casing",
                    "create_extra_casing:block/"+texture,true);
            }
        }
        add("tfmg:steel_casing","beltsnpieces:block/belt/steel_belt_casing",true);
    }
    private BeltCasings() {}
    private static void add(String block,String texture,boolean beltAtlas) {
        ResourceLocation id=ResourceLocation.parse(block);
        MATERIALS.put(id,new Material(id,ResourceLocation.parse(texture),beltAtlas));
    }
    public static Material find(ResourceLocation id) { return id==null?null:MATERIALS.get(id); }
    public static Material fromItem(ItemStack stack) {
        if(stack.isEmpty()) return null;
        Material material=find(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return material!=null && material.available()?material:null;
    }
}
