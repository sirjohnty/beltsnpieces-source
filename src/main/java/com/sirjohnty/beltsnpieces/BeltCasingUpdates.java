package com.sirjohnty.beltsnpieces;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Send casing snapshots after optional addons have finished updating their material. */
@EventBusSubscriber(modid=BeltsNPieces.ID)
public final class BeltCasingUpdates {
    private static final Set<BeltBlockEntity> PENDING=
        Collections.newSetFromMap(new IdentityHashMap<>());
    private BeltCasingUpdates() {}

    public static void defer(BeltBlockEntity belt) {
        if(belt.hasLevel() && !belt.getLevel().isClientSide) PENDING.add(belt);
    }

    @SubscribeEvent public static void flush(ServerTickEvent.Post event) {
        var updates=PENDING.toArray(BeltBlockEntity[]::new);
        PENDING.clear();
        for(var belt:updates) {
            var level=belt.getLevel();
            if(level!=null && !belt.isRemoved() && level.hasChunkAt(belt.getBlockPos())
                && level.getBlockEntity(belt.getBlockPos())==belt) belt.sendData();
        }
    }
}
