package com.sirjohnty.bitsnbelts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public record CurveStylePayload(InteractionHand hand,BlockPos start) implements CustomPacketPayload {
    public static final Type<CurveStylePayload> TYPE=new Type<>(BitsNBelts.id("curve_style"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CurveStylePayload> CODEC=StreamCodec.of(
        (buf,p)->{buf.writeBoolean(p.hand==InteractionHand.OFF_HAND);buf.writeBlockPos(p.start);},
        buf->new CurveStylePayload(buf.readBoolean()?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,buf.readBlockPos()));
    public static void register(RegisterPayloadHandlersEvent event) {event.registrar("1").playToServer(TYPE,CODEC,CurveStylePayload::handle);}
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    private static void handle(CurveStylePayload p,IPayloadContext ctx) {
        var player=ctx.player();var stack=player.getItemInHand(p.hand);
        if(!player.mayBuild() || !stack.is(BitsNBelts.REINFORCED_BELT.get()) || !p.start.equals(ReinforcedBeltItem.selection(stack,player.level()))) return;
        ReinforcedBeltItem.cycleStyle(stack,player);
    }
}
