package com.sirjohnty.beltsnpieces;

import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DragBeltPayload(BlockPos span,double center,double height,double x,double z) implements CustomPacketPayload {
    public static final Type<DragBeltPayload> TYPE=new Type<>(BeltsNPieces.id("drag_belt"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DragBeltPayload> CODEC=StreamCodec.of(
        (buf,p)->{buf.writeBlockPos(p.span);buf.writeDouble(p.center);buf.writeDouble(p.height);buf.writeDouble(p.x);buf.writeDouble(p.z);},
        buf->new DragBeltPayload(buf.readBlockPos(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble()));
    public static void register(RegisterPayloadHandlersEvent event) { event.registrar("1").playToServer(TYPE,CODEC,DragBeltPayload::handle); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void handle(DragBeltPayload payload,IPayloadContext ctx) {
        var player=ctx.player();var level=player.level();
        if(!player.mayBuild() || !AllItems.WRENCH.isIn(player.getMainHandItem())
            || !Double.isFinite(payload.center) || !Double.isFinite(payload.height) || !Double.isFinite(payload.x) || !Double.isFinite(payload.z)
            || payload.center<=.02 || payload.center>=.98 || Math.abs(payload.height)>4
            || Math.hypot(payload.x,payload.z)>4.000001
            || !level.hasChunkAt(payload.span) || player.distanceToSqr(payload.span.getCenter())>64
            || !(level.getBlockEntity(payload.span) instanceof ReinforcedBeltSpanBlockEntity span)) return;
        var owner=span.beltController();
        if(owner==null || owner.path()==null || !owner.path().canGrab(payload.center)) return;
        if(owner.elevationEdits.size()>=BeltsNPiecesConfig.maxBeltAdjustments()) {
            player.displayClientMessage(Component.literal("Belt adjustment limit reached ("+BeltsNPiecesConfig.maxBeltAdjustments()+")."),true);return;
        }
        if(!owner.adjustElevation(player,payload.center,payload.height,payload.x,payload.z))
            player.displayClientMessage(Component.literal("Cannot reshape belt here; check clearance, loaded chunks and sharp bends."),true);
    }
}
