package com.sirjohnty.beltsnpieces;

import com.simibubi.create.AllBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Limited sharing for a belt's small edge overlap with an encased chain drive. */
public final class SharedBeltSpace {
    private SharedBeltSpace() {}
    // At most one eighth of the cell's horizontal footprint. A full belt is not
    // allowed to pass through a drive; this is for incidental edge clipping only.
    public static final int MAX_COLUMNS=32;
    public static boolean smallOverlap(BeltSurface.Cell cell) {
        return cell!=null && cell.footprintColumns()>0 && cell.footprintColumns()<=MAX_COLUMNS;
    }
    public static boolean isDrive(BlockState state) {
        return (AllBlocks.ENCASED_CHAIN_DRIVE.has(state)||AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT.has(state))
            && state.getFluidState().isEmpty();
    }
    public static BlockState placementState(BlockPlaceContext ctx) {
        ItemStack stack=ctx.getItemInHand();
        return stack.getItem() instanceof BlockItem item ? item.getBlock().getStateForPlacement(ctx) : null;
    }
    public static boolean compatible(BlockGetter level,BlockPos pos,BlockState state,BeltSurface.Cell cell) {
        if(state==null||state.isAir()||!state.getFluidState().isEmpty()||!smallOverlap(cell)) return false;
        VoxelShape block=state.getCollisionShape(level,pos);
        return allowsShape(cell,block,isDrive(state));
    }
    /** Kept independent of world and registries so geometric policy can be tested. */
    public static boolean allowsShape(BeltSurface.Cell cell,VoxelShape block,boolean encasedDrive) {
        if(!smallOverlap(cell)) return false;
        if(encasedDrive) return true;
        // Keep a real solid cube exclusive, and accept partial blocks only if
        // their physical geometry does not intersect the belt's upper run.
        if(!Shapes.joinIsNotEmpty(Shapes.block(),block,BooleanOp.ONLY_FIRST)) return false;
        return !Shapes.joinIsNotEmpty(cell.shape(),block,BooleanOp.AND);
    }
}
