package com.sirjohnty.bitsnbelts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlaceGeneratedBeltPayload(InteractionHand hand,BlockPos start,BlockPos end) implements CustomPacketPayload {
    public static final Type<PlaceGeneratedBeltPayload> TYPE=new Type<>(BitsNBelts.id("place_generated_belt"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PlaceGeneratedBeltPayload> CODEC=StreamCodec.of(
        (buf,p)->{buf.writeBoolean(p.hand==InteractionHand.OFF_HAND);buf.writeBlockPos(p.start);buf.writeBlockPos(p.end);},
        buf->new PlaceGeneratedBeltPayload(buf.readBoolean()?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,buf.readBlockPos(),buf.readBlockPos()));
    public static void register(RegisterPayloadHandlersEvent event) {event.registrar("1").playToServer(TYPE,CODEC,PlaceGeneratedBeltPayload::handle);}
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    private static void handle(PlaceGeneratedBeltPayload payload,IPayloadContext ctx) {
        var player=ctx.player();var level=player.level();var stack=player.getItemInHand(payload.hand);
        if(!player.mayBuild() || !(stack.getItem() instanceof ReinforcedBeltItem item)
            || !payload.start.equals(ReinforcedBeltItem.selection(stack,level))
            || player.distanceToSqr(Vec3.atCenterOf(payload.end))>144) return;
        var target=ReinforcedBeltItem.generatedTarget(level,payload.start,payload.end,ReinforcedBeltItem.shaftAxisChoice(stack));
        if(target==null) return;
        item.place(level,player,stack,payload.start,target,Direction.UP,Vec3.atCenterOf(payload.end));
    }
}
