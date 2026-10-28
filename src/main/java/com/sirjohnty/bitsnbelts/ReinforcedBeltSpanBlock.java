package com.sirjohnty.bitsnbelts;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

/** Invisible collision and selection surface; appearance stays on the pulley renderer. */
public final class ReinforcedBeltSpanBlock extends Block implements EntityBlock, IWrenchable {
    public ReinforcedBeltSpanBlock(Properties p) { super(p); }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving) {
        if(!level.isClientSide) level.scheduleTick(pos,this,100);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new ReinforcedBeltSpanBlockEntity(pos,state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity be ? be.selectionShape() : Shapes.empty();
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity be ? be.shape() : Shapes.empty();
    }
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos) { return Shapes.empty(); }
    @Override public InteractionResult onSneakWrenched(BlockState state,UseOnContext ctx) {
        Level level=ctx.getLevel();BlockPos pos=ctx.getClickedPos();
        if(ctx.getPlayer()==null||!ctx.getPlayer().mayBuild()||!level.mayInteract(ctx.getPlayer(),pos)) return InteractionResult.FAIL;
        if(!(level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span)||span.beltController()==null) return InteractionResult.FAIL;
        if(!level.isClientSide) {span.beltController().removeFromSpan(ctx.getPlayer());IWrenchable.playRemoveSound(level,pos);}
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public boolean canBeReplaced(BlockState state,BlockPlaceContext ctx) {
        return ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof ReinforcedBeltSpanBlockEntity be
            && be.canShareWith(ctx);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity be)
            if(!be.shareWith(next)) be.removeBelt();
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity be) be.checkOwner();
        if (level.getBlockState(pos).is(this)) level.scheduleTick(pos,this,100);
    }
}
