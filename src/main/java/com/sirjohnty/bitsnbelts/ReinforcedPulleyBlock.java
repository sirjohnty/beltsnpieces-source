package com.sirjohnty.bitsnbelts;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

public final class ReinforcedPulleyBlock extends RotatedPillarKineticBlock implements IBE<ReinforcedPulleyBlockEntity> {
    public ReinforcedPulleyBlock(Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }
    @Override public BlockState getRotatedBlockState(BlockState state, Direction face) { return state; }
    @Override public Class<ReinforcedPulleyBlockEntity> getBlockEntityClass() { return ReinforcedPulleyBlockEntity.class; }
    @Override public BlockEntityType<? extends ReinforcedPulleyBlockEntity> getBlockEntityType() { return BitsNBelts.PULLEY_ENTITY.get(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(AXIS)==Direction.Axis.X ? box(0,2,2,16,13,14) : box(2,2,0,14,13,16);
    }
    @Override public InteractionResult onSneakWrenched(BlockState state,UseOnContext ctx) {
        Level level=ctx.getLevel();BlockPos pos=ctx.getClickedPos();
        if(ctx.getPlayer()==null||!ctx.getPlayer().mayBuild()||!level.mayInteract(ctx.getPlayer(),pos)) return InteractionResult.FAIL;
        if(!(level.getBlockEntity(pos) instanceof ReinforcedPulleyBlockEntity be)||be.peer==null) return InteractionResult.FAIL;
        if(!level.isClientSide) {be.removeFromSpan(ctx.getPlayer());IWrenchable.playRemoveSound(level,pos);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if(!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ReinforcedPulleyBlockEntity be) be.dismantle();
        super.onRemove(state,level,pos,next,moving);
    }
}
