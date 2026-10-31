package com.sirjohnty.beltsnpieces;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public record CurveStylePayload(InteractionHand hand,BlockPos start,int direction) implements CustomPacketPayload {
    public CurveStylePayload(InteractionHand hand,BlockPos start) {this(hand,start,1);}
    public static final Type<CurveStylePayload> TYPE=new Type<>(BeltsNPieces.id("curve_style"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CurveStylePayload> CODEC=StreamCodec.of(
        (buf,p)->{buf.writeBoolean(p.hand==InteractionHand.OFF_HAND);buf.writeBlockPos(p.start);buf.writeByte(p.direction);},
        buf->new CurveStylePayload(buf.readBoolean()?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,buf.readBlockPos(),buf.readByte()));
    public static void register(RegisterPayloadHandlersEvent event) {event.registrar("1").playToServer(TYPE,CODEC,CurveStylePayload::handle);}
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    private static void handle(CurveStylePayload p,IPayloadContext ctx) {
        var player=ctx.player();var stack=player.getItemInHand(p.hand);
        if(!player.mayBuild() || Math.abs(p.direction)!=1 || !stack.is(BeltsNPieces.REINFORCED_BELT.get()) || !p.start.equals(ReinforcedBeltItem.selection(stack,player.level()))) return;
        ReinforcedBeltItem.cycleStyle(stack,player,p.direction);
    }
}
