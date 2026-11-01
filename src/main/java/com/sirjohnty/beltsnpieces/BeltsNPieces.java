package com.sirjohnty.beltsnpieces;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;

@Mod(BeltsNPieces.ID)
public final class BeltsNPieces {
    public static final String ID = "beltsnpieces";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredBlock<ReinforcedPulleyBlock> PULLEY = BLOCKS.register("reinforced_pulley",
        () -> new ReinforcedPulleyBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<BeltVoidBlock> BELT_VOID = BLOCKS.register("belt_void",
        () -> new BeltVoidBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK)
            .strength(1.5f).sound(SoundType.METAL).noOcclusion().dynamicShape().randomTicks()));
    public static final DeferredItem<BlockItem> BELT_VOID_ITEM = ITEMS.register("belt_void",
        () -> new BlockItem(BELT_VOID.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltVoidBlockEntity>> BELT_VOID_ENTITY = ENTITIES.register("belt_void",
        () -> BlockEntityType.Builder.of(BeltVoidBlockEntity::new, BELT_VOID.get()).build(null));
    public static final DeferredItem<ReinforcedBeltItem> REINFORCED_BELT = ITEMS.register("reinforced_belt", () -> new ReinforcedBeltItem(new Item.Properties()));
    public static final DeferredItem<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem> INCOMPLETE_REINFORCED_BELT = ITEMS.register("incomplete_reinforced_belt", () -> new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new Item.Properties()));
    public static final DeferredBlock<ReinforcedBeltSpanBlock> SPAN = BLOCKS.register("reinforced_belt_span",
        () -> new ReinforcedBeltSpanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f)
            .sound(SoundType.METAL).noOcclusion().noLootTable().dynamicShape()
            .isValidSpawn((s,l,p,t)->false)
            .isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false)
            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReinforcedBeltSpanBlockEntity>> SPAN_ENTITY = ENTITIES.register("reinforced_belt_span",
        () -> BlockEntityType.Builder.of(ReinforcedBeltSpanBlockEntity::new, SPAN.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReinforcedPulleyBlockEntity>> PULLEY_ENTITY = ENTITIES.register("reinforced_pulley",
        () -> BlockEntityType.Builder.of(ReinforcedPulleyBlockEntity::new, PULLEY.get()).build(null));
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    static {
        TABS.register("beltsnpieces", () -> CreativeModeTab.builder().title(net.minecraft.network.chat.Component.literal("Create: Belts n Pieces"))
            .icon(() -> new ItemStack(REINFORCED_BELT.get())).displayItems((p,o) -> { o.accept(REINFORCED_BELT.get()); o.accept(BELT_VOID_ITEM.get()); }).build());
    }
    public BeltsNPieces(IEventBus bus,net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,BeltsNPiecesConfig.SPEC,"beltsnpieces-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,BeltsNPiecesConfig.CLIENT_SPEC);
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        bus.addListener(BeltsNPieces::capabilities);
        bus.addListener(DragBeltPayload::register);
        bus.addListener(BeltCargoPayload::register);
        bus.addListener(ShaftAxisPayload::register);
        bus.addListener(CurveStylePayload::register);
        bus.addListener(PlaceGeneratedBeltPayload::register);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PULLEY_ENTITY.get(), (be, side) -> be.itemHandler());
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
