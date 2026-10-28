package com.sirjohnty.bitsnbelts;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative settings, synchronized to clients by NeoForge. */
public final class BitsNBeltsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    private static final ModConfigSpec.IntValue MAX_REINFORCED_BELT_LENGTH;
    private static final ModConfigSpec.IntValue MAX_CHAIN_CONVEYOR_ANGLE;
    private static final ModConfigSpec.IntValue MAX_BELT_ADJUSTMENTS;
    public static final int ABSOLUTE_MAX_BELT_ADJUSTMENTS=512;
    public enum BeltQuality { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH }
    private static final ModConfigSpec.EnumValue<BeltQuality> REINFORCED_BELT_VISUAL_DETAIL;
    public enum PreviewStyle { PLACEMENT_VALID, PLACEMENT_INVALID, PLACEMENT_SOURCE, WRENCH_PREVIEW, WRENCH_DIRECTION, WRENCH_HOVER, SELECTION }
    public static int previewColor(PreviewStyle style) {return switch(style) {
        case PLACEMENT_VALID, WRENCH_PREVIEW, WRENCH_HOVER -> 0x70E99B;
        case PLACEMENT_INVALID -> 0xEF6262;
        case PLACEMENT_SOURCE -> 0xE8BA54;
        case WRENCH_DIRECTION -> 0x51CFFF;
        case SELECTION -> 0x668EFF;
    };}
    public static float previewOpacity(PreviewStyle style) {return style==PreviewStyle.SELECTION?.65f:1f;}

    static {
        var builder=new ModConfigSpec.Builder();
        MAX_REINFORCED_BELT_LENGTH=builder.comment("Set the maximum length a reinforced belt can be. Behaves similarly to regular belts")
            .translation("bitsnbelts.config.maximumBeltLength")
            .defineInRange("maxReinforcedBeltLength",20,3,Integer.MAX_VALUE);
        MAX_BELT_ADJUSTMENTS=builder.comment("How many times a reinforced belt can be adjusted with the wrench before blocking new adjustments")
            .translation("bitsnbelts.config.beltAdjustmentLimit")
            .defineInRange("maxReinforcedBeltAdjustments",64,0,64);
        MAX_CHAIN_CONVEYOR_ANGLE=builder.comment("How steep a chain conveyor can get before it cuts off and you can't place it")
            .translation("bitsnbelts.config.chainConveyorCutoffAngle")
            .defineInRange("maxChainConveyorAngle",45,0,90);
        SPEC=builder.build();
        var client=new ModConfigSpec.Builder();
        REINFORCED_BELT_VISUAL_DETAIL=client.comment("How detailed the curves for reinforced belts are")
            .translation("bitsnbelts.config.beltQuality")
            .defineEnum("reinforcedBeltVisualDetail",BeltQuality.MEDIUM);
        CLIENT_SPEC=client.build();
    }
    private BitsNBeltsConfig() {}
    public static int maxBeltAdjustments() { return MAX_BELT_ADJUSTMENTS.get(); }
    public static int reinforcedBeltVisualDetail() { return REINFORCED_BELT_VISUAL_DETAIL.get().ordinal()-1; }
    public static int maxChainConveyorAngle() { return MAX_CHAIN_CONVEYOR_ANGLE.get(); }
    public static int maxReinforcedBeltLength() {
        return MAX_REINFORCED_BELT_LENGTH.get();
    }
}
