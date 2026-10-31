package com.sirjohnty.beltsnpieces;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Frequent cargo corrections, without resending unchanged geometry and surface ownership. */
public record BeltCargoPayload(BlockPos controller,CompoundTag cargo) implements CustomPacketPayload {
    public static final Type<BeltCargoPayload> TYPE=new Type<>(BeltsNPieces.id("belt_cargo"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BeltCargoPayload> CODEC=StreamCodec.of(
        (buf,p)->{buf.writeBlockPos(p.controller);buf.writeNbt(p.cargo);},
        buf->new BeltCargoPayload(buf.readBlockPos(),java.util.Objects.requireNonNull(buf.readNbt())));
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC,(payload,context)->{
            var level=context.player().level();
            if(level.hasChunkAt(payload.controller)
                && level.getBlockEntity(payload.controller) instanceof ReinforcedPulleyBlockEntity belt)
                belt.receiveCargo(payload.cargo,level.registryAccess());
        });
    }
}
