package com.sirjohnty.beltsnpieces;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** Overflow attachment with a clear item passage and solid player collision. */
public final class BeltVoidBlock extends Block implements EntityBlock {
    /** Direction in which the wide intake mouth faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public BeltVoidBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Selection and player collision follow the outer housing bounds.
        return Block.box(1, -3, 1, 15, 9, 15);
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player
            ? getShape(state, level, pos, context) : Shapes.empty();
    }

    @Override public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        return AllBlocks.BRASS_FUNNEL.get().defaultBlockState().getSoundType(level, pos, entity);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeltVoidBlockEntity(pos, state);
    }

    @Override public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int data) {
        var be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, data);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == BeltsNPieces.BELT_VOID_ENTITY.get()
            ? (world, pos, blockState, be) -> BeltVoidBlockEntity.clientTick(world, pos, blockState, (BeltVoidBlockEntity) be)
            : null;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!canSurvive(defaultBlockState(), context.getLevel(), context.getClickedPos()))
            return null;
        BlockPos below = context.getClickedPos().below();
        Direction intake = context.getLevel().getBlockState(below).getValue(BeltBlock.HORIZONTAL_FACING).getOpposite();
        if (context.getLevel().getBlockEntity(below) instanceof BeltBlockEntity belt && belt.getSpeed() != 0)
            intake = belt.getMovementFacing().getOpposite();
        return defaultBlockState().setValue(FACING, intake);
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return AllBlocks.BELT.has(level.getBlockState(pos.below()));
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (!level.isClientSide)
            level.scheduleTick(pos, this, 1);
    }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        var support = level.getBlockState(pos.below());
        Direction intake = support.getValue(BeltBlock.HORIZONTAL_FACING).getOpposite();
        if (level.getBlockEntity(pos.below()) instanceof BeltBlockEntity belt && belt.getSpeed() != 0)
            intake = belt.getMovementFacing().getOpposite();
        if (state.getValue(FACING) != intake)
            level.setBlock(pos, state.setValue(FACING, intake), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 10);
    }

    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Starts alignment checks for saved placements created before scheduled ticking was added.
        tick(state, level, pos, random);
    }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!level.isClientSide())
            level.scheduleTick(pos, this, 1);
        return direction == Direction.DOWN && !canSurvive(state, level, pos)
            ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
}
