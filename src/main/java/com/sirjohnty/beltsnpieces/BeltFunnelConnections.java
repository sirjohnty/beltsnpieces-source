package com.sirjohnty.beltsnpieces;

import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import net.minecraft.core.Direction;

/** Re-run Create's support update after a pulley gains its belt connection. */
final class BeltFunnelConnections {
    private BeltFunnelConnections() {}

    static void refresh(ReinforcedPulleyBlockEntity pulley) {
        var level=pulley.getLevel();
        if(level==null || level.isClientSide || pulley.peer==null) return;
        var pos=pulley.getBlockPos().above();
        if(!level.hasChunkAt(pos)) return;
        var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof AbstractFunnelBlock)) return;
        // Native conversion preserves facing, power, waterlogging, filters and extended shapes.
        var updated=state.updateShape(Direction.DOWN,pulley.getBlockState(),level,pos,pulley.getBlockPos());
        if(!updated.equals(state)) level.setBlock(pos,updated,3);
    }
}
