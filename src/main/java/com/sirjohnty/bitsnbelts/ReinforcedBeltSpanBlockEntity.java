package com.sirjohnty.bitsnbelts;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.item.context.BlockPlaceContext;

public final class ReinforcedBeltSpanBlockEntity extends BlockEntity {
    private BlockPos owner, peer;
    private BeltSurface.Cell cell=new BeltSurface.Cell();
    public ReinforcedBeltSpanBlockEntity(BlockPos pos,BlockState state) { super(BitsNBelts.SPAN_ENTITY.get(),pos,state); }
    public VoxelShape shape() { return cell.shape(); }
    public VoxelShape selectionShape() { return cell.selectionShape(); }
    public ReinforcedPulleyBlockEntity beltController() { return controller(); }
    public boolean canShareWith(BlockPlaceContext ctx) {
        var be=controller();
        return be!=null && be.path()!=null && SharedBeltSpace.compatible(level,worldPosition,
            SharedBeltSpace.placementState(ctx),be.path().surface().cells.get(worldPosition));
    }
    public boolean shareWith(BlockState replacement) {
        var be=controller();
        if(be==null||be.path()==null||!SharedBeltSpace.compatible(level,worldPosition,replacement,be.path().surface().cells.get(worldPosition))) return false;
        be.markSharedSurface(worldPosition);return true;
    }
    public boolean belongsTo(BlockPos controller,BlockPos target) { return controller.equals(owner)&&target.equals(peer); }
    public void configure(BlockPos controller,BlockPos target,BeltSurface.Cell surface) {
        owner=controller.immutable(); peer=target.immutable(); cell=surface;
        setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        level.scheduleTick(worldPosition,getBlockState().getBlock(),100);
    }
    private ReinforcedPulleyBlockEntity controller() {
        return owner!=null && level!=null && level.hasChunkAt(owner)
            && level.getBlockEntity(owner) instanceof ReinforcedPulleyBlockEntity be && be.controller
            && peer!=null && peer.equals(be.peer) ? be : null;
    }
    public void removeBelt() {
        ReinforcedPulleyBlockEntity be=controller();
        if (be!=null) be.removeFromSpan();
    }
    public void checkOwner() {
        if (owner==null) { level.removeBlock(worldPosition,false);return; }
        if (!level.hasChunkAt(owner)) return;
        ReinforcedPulleyBlockEntity be=controller();
        if (be==null || !be.ownsSurfacePosition(worldPosition)) level.removeBlock(worldPosition,false);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level!=null&&!level.isClientSide) level.scheduleTick(worldPosition,getBlockState().getBlock(),100);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        if (owner!=null) tag.putLong("Owner",owner.asLong());
        if (peer!=null) tag.putLong("Peer",peer.asLong());
        tag.putLongArray("Surface",cell.save());
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        owner=tag.contains("Owner")?BlockPos.of(tag.getLong("Owner")):null;
        peer=tag.contains("Peer")?BlockPos.of(tag.getLong("Peer")):null;
        cell=new BeltSurface.Cell(tag.getLongArray("Surface"));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
