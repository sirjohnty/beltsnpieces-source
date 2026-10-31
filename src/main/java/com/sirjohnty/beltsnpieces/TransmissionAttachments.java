package com.sirjohnty.beltsnpieces;

import com.serpenssolida.createtransmission.CTBlocks;
import com.serpenssolida.createtransmission.content.chain.AbstractTransmissionChainBlock;
import com.serpenssolida.createtransmission.content.chain.TransmissionChainHelpers.*;
import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Loaded only when Create Transmission is present. */
public final class TransmissionAttachments {
    private TransmissionAttachments() {}

    public static void connect(Level level, Player player, ItemStack belt, BlockPos oldEnd, BlockPos newStart) {
        var oldEndpoint = ReinforcedBeltItem.extensionEndpoint(level, oldEnd);
        var newEndpoint = ReinforcedBeltItem.extensionEndpoint(level, newStart);
        if (oldEndpoint == null || newEndpoint == null || oldEnd.distManhattan(newStart) != 1) return;
        var oldState = oldEndpoint.getBlockState();
        var axis = ((IRotate) oldState.getBlock()).getRotationAxis(oldState);
        if (((IRotate) newEndpoint.getBlockState().getBlock()).getRotationAxis(newEndpoint.getBlockState()) != axis) return;
        var along = Direction.getNearest(newStart.getX()-oldEnd.getX(), newStart.getY()-oldEnd.getY(), newStart.getZ()-oldEnd.getZ());
        if (along.getAxis() == axis) return;
        var chain = CTBlocks.TRANSMISSION_CHAIN.get();
        int available = 0;
        var inventory = player.getInventory();
        for (int i=0;i<inventory.getContainerSize();i++)
            if (inventory.getItem(i).is(chain.asItem())) available += inventory.getItem(i).getCount();
        if (!player.isCreative() && available < 2) {
            player.displayClientMessage(Component.literal("Two Transmission Chains are required to link the belt ends."), true);
            return;
        }
        for (var side : new Direction[]{Direction.get(Direction.AxisDirection.POSITIVE, axis), Direction.get(Direction.AxisDirection.NEGATIVE, axis)}) {
            var a = oldEnd.relative(side);
            var b = newStart.relative(side);
            var facing = side.getOpposite();
            var direction = ChainDirection.of(facing);
            var stateA = chain.defaultBlockState().setValue(AbstractTransmissionChainBlock.FACING, facing)
                .setValue(AbstractTransmissionChainBlock.CONNECTION_TYPE, ConnectionType.CHAIN)
                .setValue(AbstractTransmissionChainBlock.CONNECTION_SIDE, direction.getSideFromDirection(along));
            var stateB = stateA.setValue(AbstractTransmissionChainBlock.CONNECTION_SIDE, direction.getSideFromDirection(along.getOpposite()));
            boolean clear = true;
            for (var pos : new BlockPos[]{a,b}) {
                if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.mayInteract(player,pos) || !player.mayUseItemAt(pos,facing,belt)
                    || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()
                    || level.getBlockEntity(pos)!=null) { clear=false; break; }
            }
            if (!clear || !level.isUnobstructed(stateA,a,CollisionContext.of(player))
                || !level.isUnobstructed(stateB,b,CollisionContext.of(player))) continue;
            // Install both before notifying neighbours so neither sees a half-built pair.
            boolean placedA=level.setBlock(a,stateA,2);
            boolean placedB=placedA && level.setBlock(b,stateB,2);
            if (!placedB) {
                if (placedA) level.removeBlock(a,false);
                continue;
            }
            level.updateNeighborsAt(a,chain);
            level.updateNeighborsAt(b,chain);
            if (!player.isCreative()) {
                int remaining=2;
                for (int i=0;i<inventory.getContainerSize() && remaining>0;i++) {
                    var item=inventory.getItem(i);
                    if (!item.is(chain.asItem())) continue;
                    int count=Math.min(remaining,item.getCount());item.shrink(count);remaining-=count;
                }
                inventory.setChanged();
            }
            return;
        }
        player.displayClientMessage(Component.literal("No clear shaft-side space for Transmission Chains."),true);
    }
}
