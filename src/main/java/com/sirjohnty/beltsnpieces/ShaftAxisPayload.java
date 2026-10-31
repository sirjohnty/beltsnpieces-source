package com.sirjohnty.beltsnpieces;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShaftAxisPayload(InteractionHand hand,BlockPos start,int axis) implements CustomPacketPayload {
    public static final Type<ShaftAxisPayload> TYPE=new Type<>(BeltsNPieces.id("shaft_axis"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ShaftAxisPayload> CODEC=StreamCodec.of(
        (buf,p)->{buf.writeBoolean(p.hand==InteractionHand.OFF_HAND);buf.writeBlockPos(p.start);buf.writeByte(p.axis);},
        buf->new ShaftAxisPayload(buf.readBoolean()?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,buf.readBlockPos(),buf.readUnsignedByte()));
    public static void register(RegisterPayloadHandlersEvent event) {event.registrar("1").playToServer(TYPE,CODEC,ShaftAxisPayload::handle);}
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    private static void handle(ShaftAxisPayload payload,IPayloadContext ctx) {
        var player=ctx.player();var stack=player.getItemInHand(payload.hand);
        if(!player.mayBuild() || !stack.is(BeltsNPieces.REINFORCED_BELT.get()) || payload.axis<1 || payload.axis>4
            || !payload.start.equals(ReinforcedBeltItem.selection(stack,player.level()))) return;
        ReinforcedBeltItem.setShaftAxisChoice(stack,payload.axis);
    }
}
