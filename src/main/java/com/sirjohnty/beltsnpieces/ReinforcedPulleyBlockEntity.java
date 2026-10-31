package com.sirjohnty.beltsnpieces;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.kinetics.base.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.*;

public final class ReinforcedPulleyBlockEntity extends KineticBlockEntity {
    public static final int CAPACITY=16;
    public BlockPos peer;
    public boolean controller;
    public long placementTime=Long.MIN_VALUE;
    public Direction startDirection=Direction.EAST,endDirection=Direction.EAST;
    private int beltColor=-1;
    private net.minecraft.resources.ResourceLocation casingId;
    public BeltCasings.Material casing() {
        var material=BeltCasings.find(casingId);
        return material!=null && material.available()?material:null;
    }
    public boolean hasCasing() { return casing()!=null; }
    public boolean setCasing(net.minecraft.resources.ResourceLocation id) {
        var owner=root();
        var material=BeltCasings.find(id);
        if(owner==null || owner.peer==null || Objects.equals(owner.casingId,id)
            || (id!=null && (material==null || !material.available()))) return false;
        if(!owner.level.isClientSide) { owner.casingId=id;owner.setChanged();owner.sendData(); }
        return true;
    }
    public int beltColor() { return beltColor; }
    public boolean setBeltColor(int color) {
        ReinforcedPulleyBlockEntity owner=root();
        if(owner==null || owner.peer==null || color < -1 || color > 15 || owner.beltColor==color) return false;
        if(!owner.getLevel().isClientSide) {owner.beltColor=color;owner.setChanged();owner.sendData();}
        return true;
    }
    public int curveStyle;
    public int pathRevision;
    public double elevationCenter=.5,elevationHeight;
    public final List<BeltElevation.Control> elevationEdits=new ArrayList<>();
    public int ratio=1,paidBelts;
    public final List<Cargo> cargo=new ArrayList<>();
    private BeltPath cachedPath;
    private double previousTextureTravel,textureTravel;
    public double textureTravel(float partialTick) {
        return previousTextureTravel+(textureTravel-previousTextureTravel)*partialTick;
    }
    private boolean dismantling;
    private boolean surfaceBuilt;
    private final Set<BlockPos> sharedSurface=new HashSet<>();
    public void markSharedSurface(BlockPos pos) {
        if(sharedSurface.add(pos.immutable())) setChanged();
    }
    private static final int SURFACE_VERSION=1;
    private int surfaceVersion;
    private BeltSurface legacySurface;
    private BeltSurface legacySurface() {
        if(legacySurface==null) legacySurface=new BeltPath(controller?worldPosition:peer,controller?peer:worldPosition,
            startDirection,endDirection,.32).surface();
        return legacySurface;
    }
    private Set<BlockPos> managedSurfacePositions() {
        Set<BlockPos> positions=new HashSet<>(path().surface().cells.keySet());
        if(surfaceBuilt&&surfaceVersion<SURFACE_VERSION) positions.addAll(legacySurface().cells.keySet());
        return positions;
    }
    public boolean ownsSurfacePosition(BlockPos pos) {
        return peer!=null && (path().surface().cells.containsKey(pos)
            || (surfaceBuilt&&surfaceVersion<SURFACE_VERSION&&legacySurface().cells.containsKey(pos)));
    }
    /** Best-effort local preview of the same clearance checks used by reshaping. */
    public boolean canPreviewReshape(net.minecraft.world.entity.player.Player player, BeltPath next) {
        if (!controller || peer==null || level==null || !routeLoaded() || other()==null || !sharedSurface.isEmpty()
            || elevationEdits.size()>=BeltsNPiecesConfig.maxBeltAdjustments()
            || next.length>ReinforcedBeltItem.maxLength() || next.length<1e-6
            || !BeltGeometryValidation.returnRunDoesNotFold(next)) return false;
        var previous=path();
        var oldCells=previous.surface().cells; var newCells=next.surface().cells;
        var positions=new java.util.HashSet<BlockPos>(oldCells.keySet()); positions.addAll(newCells.keySet());
        for (BlockPos pos:positions) {
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.mayInteract(player,pos)
                || !player.mayUseItemAt(pos,Direction.UP,player.getMainHandItem())) return false;
            if (oldCells.containsKey(pos) && !(level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span
                && span.belongsTo(worldPosition,peer))) return false;
            if (newCells.containsKey(pos) && !oldCells.containsKey(pos) && !level.getBlockState(pos).isAir()) return false;
        }
        var sorted=cargo.stream().map(c->c.distance/previous.length*next.length).sorted().toList();
        for (int i=1;i<sorted.size();i++) if (sorted.get(i)-sorted.get(i-1)<.7-1e-7) return false;
        return true;
    }
    /** Apply a validated reshape without discarding cargo or adjacent blocks. */
    public boolean adjustElevation(net.minecraft.world.entity.player.Player player,double center,double height,double x,double z) {
        if(!player.mayBuild() || !Double.isFinite(center) || center<=.02 || center>=.98
            || !Double.isFinite(height) || Math.abs(height)>4 || !Double.isFinite(x) || !Double.isFinite(z)
            || Math.hypot(x,z)>4.000001) return false;
        if(elevationEdits.size()>=BeltsNPiecesConfig.maxBeltAdjustments()) return false;
        var edits=new ArrayList<>(elevationEdits);edits.add(new BeltElevation.Control(center,height,x,z));
        return applyElevation(player,elevationCenter,elevationHeight,edits);
    }
    private boolean applyElevation(net.minecraft.world.entity.player.Player player,double baseCenter,double baseHeight,java.util.List<BeltElevation.Control> edits) {
        if(!controller||peer==null||level==null||level.isClientSide||!routeLoaded()||other()==null||!sharedSurface.isEmpty()) return false;
        var previous=path();var target=other();
        var next=new BeltPath(worldPosition,peer,startDirection,endDirection,curveStyle,pathRevision,baseCenter,baseHeight,edits);
        if(next.length>ReinforcedBeltItem.maxLength()||next.length<1e-6 || !BeltGeometryValidation.returnRunDoesNotFold(next)) return false;
        var oldCells=previous.surface().cells;var newCells=next.surface().cells;
        var positions=new java.util.HashSet<BlockPos>(oldCells.keySet());positions.addAll(newCells.keySet());
        for(BlockPos pos:positions) {
            if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.UP,player.getMainHandItem())) return false;
            var entity=level.getBlockEntity(pos);
            if(oldCells.containsKey(pos) && !(entity instanceof ReinforcedBeltSpanBlockEntity span && span.belongsTo(worldPosition,peer))) return false;
            if(newCells.containsKey(pos) && !oldCells.containsKey(pos) && !level.getBlockState(pos).isAir()) return false;
        }
        var sorted=cargo.stream().map(c->c.distance/previous.length*next.length).sorted().toList();
        for(int i=1;i<sorted.size();i++) if(sorted.get(i)-sorted.get(i-1)<.7-1e-7) return false;
        var original=new java.util.HashMap<BlockPos,BlockState>();
        for(BlockPos pos:positions) original.put(pos,level.getBlockState(pos));
        var oldEdits=new ArrayList<>(elevationEdits);
        double oldCenter=elevationCenter,oldHeight=elevationHeight;
        boolean success=false;dismantling=true;
        try {
            for(var entry:newCells.entrySet()) {
                BlockPos pos=entry.getKey();
                if(!oldCells.containsKey(pos) && !level.setBlock(pos,BeltsNPieces.SPAN.get().defaultBlockState(),3)) return false;
                if(!(level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity)) return false;
            }
            elevationCenter=baseCenter;elevationHeight=baseHeight;elevationEdits.clear();elevationEdits.addAll(edits);cachedPath=next;invalidateRenderBoundingBox();
            target.elevationCenter=baseCenter;target.elevationHeight=baseHeight;target.elevationEdits.clear();target.elevationEdits.addAll(edits);target.cachedPath=null;target.invalidateRenderBoundingBox();
            for(var entry:newCells.entrySet()) ((ReinforcedBeltSpanBlockEntity)level.getBlockEntity(entry.getKey())).configure(worldPosition,peer,entry.getValue());
            for(BlockPos pos:oldCells.keySet()) if(!newCells.containsKey(pos) && !level.removeBlock(pos,false)) return false;
            for(Cargo c:cargo) {c.distance=c.distance/previous.length*next.length;c.previous=c.distance;}
            setChanged();sendData();target.setChanged();target.sendData();success=true;return true;
        } finally {
            if(!success) {
                elevationCenter=oldCenter;elevationHeight=oldHeight;elevationEdits.clear();elevationEdits.addAll(oldEdits);cachedPath=previous;invalidateRenderBoundingBox();
                target.elevationCenter=oldCenter;target.elevationHeight=oldHeight;target.elevationEdits.clear();target.elevationEdits.addAll(oldEdits);target.cachedPath=null;target.invalidateRenderBoundingBox();
                for(BlockPos pos:positions) {
                    if(!level.getBlockState(pos).equals(original.get(pos))) level.setBlock(pos,original.get(pos),3);
                    if(oldCells.containsKey(pos) && level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span)
                        span.configure(worldPosition,peer,oldCells.get(pos));
                }
            }
            dismantling=false;
        }
    }
    private final IItemHandler handler=new EndpointHandler();
    private record VisualTransfer(BlockPos owner,Vec3 position,Vec3 previous,long tick) {}
    private static final Map<net.minecraft.world.level.Level,Map<UUID,VisualTransfer>> VISUAL_TRANSFERS=new WeakHashMap<>();
    public static final class Cargo {
        public ItemStack stack;
        public double distance,previous;
        public double visualDistance,visualPrevious;
        public double sideOffset;
        public UUID id=UUID.randomUUID();
        public int angle;
        public double referenceHeading;
        public Cargo(ItemStack stack,double distance) { this(stack,distance,new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(stack).angle); }
        public Cargo(ItemStack stack,double distance,int angle) { this.stack=stack;this.distance=distance;this.previous=distance;this.visualDistance=distance;this.visualPrevious=distance;this.angle=angle; }
    }
    /** Horizontal heading in the same convention as a Minecraft Y rotation. */
    public static double beltHeading(BeltPath path,double distance) {
        Vec3 tangent;
        if(distance<=0) tangent=Vec3.atLowerCornerOf(path.startDirection.getNormal());
        else if(distance>=path.length) tangent=Vec3.atLowerCornerOf(path.endDirection.getNormal());
        else tangent=path.atDistance(Math.min(path.length,distance+.025)).subtract(path.atDistance(Math.max(0,distance-.025)));
        return Math.toDegrees(Math.atan2(-tangent.z,tangent.x));
    }
    public static float cargoAngle(BeltPath path,Cargo item,double distance) {
        return (float)(item.angle+beltHeading(path,distance)-item.referenceHeading);
    }
    private Cargo newCargo(ItemStack stack,double distance,int angle) {
        Cargo item=new Cargo(stack,distance,angle);item.referenceHeading=beltHeading(path(),distance);return item;
    }
    private Cargo newCargo(ItemStack stack,double distance) {
        return newCargo(stack,distance,new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(stack).angle);
    }
    public ReinforcedPulleyBlockEntity(BlockPos pos,BlockState state) { super(BeltsNPieces.PULLEY_ENTITY.get(),pos,state); }
    @Override public void addBehaviours(List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // Create passes the source belt's movement direction, not the receiving face.
        // Keep this behaviour present even when blocked: Create then queues cargo
        // on its belt instead of ejecting it as a loose entity.
        behaviours.add(new DirectBeltInputBehaviour(this)
            .allowingBeltFunnelsWhen(() -> peer != null)
            .onlyInsertWhen(this::acceptsBeltDirection)
            .setInsertionHandler((transported, direction, simulate) ->
                acceptsBeltDirection(direction)
                    ? ((EndpointHandler)handler).insertTransported(transported, direction, simulate)
                    : transported.stack));
    }
    private boolean acceptsBeltDirection(Direction direction) {
        return peer != null && direction == (controller ? startDirection : endDirection.getOpposite());
    }
    public IItemHandler itemHandler() { return handler; }
    public void connect(ReinforcedPulleyBlockEntity other,boolean isController,BeltPath path,int paid) {
        peer=other.getBlockPos().immutable();controller=isController;
        placementTime=level.getGameTime();
        surfaceBuilt=false;
        sharedSurface.clear();
        surfaceVersion=SURFACE_VERSION;legacySurface=null;
        startDirection=path.startDirection;endDirection=path.endDirection;curveStyle=path.curveStyle;pathRevision=path.pathRevision;paidBelts=paid;
        elevationCenter=path.elevationCenter;elevationHeight=path.elevationHeight;
        elevationEdits.clear();elevationEdits.addAll(path.elevationEdits);
        Direction.Axis a=(isController?getBlockState():other.getBlockState()).getValue(ReinforcedPulleyBlock.AXIS);
        Direction.Axis b=(isController?other.getBlockState():getBlockState()).getValue(ReinforcedPulleyBlock.AXIS);
        ratio=BeltPath.driveSign(a,startDirection)*BeltPath.driveSign(b,endDirection);
        cachedPath=null;invalidateRenderBoundingBox();setChanged();notifyUpdate();
        // Both converted shafts finish setup before the next kinetic tick.
        detachKinetics();updateSpeed=true;
        level.invalidateCapabilities(worldPosition);
    }
    public ReinforcedPulleyBlockEntity other() {
        return level!=null && peer!=null && level.hasChunkAt(peer) && level.getBlockEntity(peer) instanceof ReinforcedPulleyBlockEntity b
            && worldPosition.equals(b.peer) ? b : null;
    }
    public ReinforcedPulleyBlockEntity root() { return controller?this:other(); }
    public BeltPath path() {
        if(peer==null) return null;
        if(cachedPath==null) cachedPath=new BeltPath(controller?worldPosition:peer,controller?peer:worldPosition,startDirection,endDirection,curveStyle,pathRevision,elevationCenter,elevationHeight,elevationEdits);
        return cachedPath;
    }
    public double velocity() {
        if(!controller || isOverStressed()) return 0;
        return getSpeed()/480.0 * BeltPath.driveSign(getBlockState().getValue(ReinforcedPulleyBlock.AXIS),startDirection);
    }
    @Override public float calculateStressApplied() {
        lastStressApplied=1;
        return lastStressApplied;
    }
    @Override public List<BlockPos> addPropagationLocations(IRotate block,BlockState state,List<BlockPos> neighbours) {
        if(peer!=null) neighbours.add(peer);
        return super.addPropagationLocations(block,state,neighbours);
    }
    @Override public boolean isCustomConnection(KineticBlockEntity target,BlockState state,BlockState otherState) {
        return peer!=null && peer.equals(target.getBlockPos()) && target instanceof ReinforcedPulleyBlockEntity b && worldPosition.equals(b.peer);
    }
    @Override public float propagateRotationTo(KineticBlockEntity target,BlockState from,BlockState to,BlockPos diff,boolean shafts,boolean cogs) {
        return isCustomConnection(target,from,to)?ratio:super.propagateRotationTo(target,from,to,diff,shafts,cogs);
    }
    public boolean routeLoaded() {
        if(peer==null || level==null || !level.hasChunkAt(peer)) return false;
        for(long chunk:path().routeChunks())
            if(!level.hasChunk(ChunkPos.getX(chunk),ChunkPos.getZ(chunk))) return false;
        return true;
    }
    public static boolean canPlaceSurface(net.minecraft.world.level.Level level,BlockPos pos,BlockPos owner,BlockPos target,BeltSurface.Cell cell) {
        if(level.getBlockState(pos).is(BeltsNPieces.SPAN.get()))
            return level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span && span.belongsTo(owner,target);
        if(SharedBeltSpace.compatible(level,pos,level.getBlockState(pos),cell)) return true;
        return level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty() && level.getBlockEntity(pos)==null;
    }
    /** Preflight the entire surface before placing anything; existing obstacles are preserved. */
    public boolean ensureSurface() {
        if(!controller||peer==null||level==null||level.isClientSide) return false;
        var cells=path().surface().cells;
        boolean migrating=surfaceBuilt&&surfaceVersion<SURFACE_VERSION;
        if(surfaceBuilt) {
            // A span can be broken while the controller chunk is unloaded.
            // Detect the cut when the complete route is available, rather than rebuilding it.
            var existing=migrating?legacySurface().cells:cells;
            for(BlockPos pos:existing.keySet()) if(!level.hasChunkAt(pos)) return false;
            for(BlockPos pos:existing.keySet()) {
                if(sharedSurface.contains(pos)) {
                    // The drive is a real Create block. Never replace it or remove
                    // the belt merely because that cell no longer holds a span BE.
                    if(SharedBeltSpace.compatible(level,pos,level.getBlockState(pos),existing.get(pos))) continue;
                    if(level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span && span.belongsTo(worldPosition,peer)) {
                        sharedSurface.remove(pos);setChanged();continue;
                    }
                    // A removed drive restores the original surface once the cell is empty.
                    if(level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
                        && level.getBlockEntity(pos)==null) {
                        if(!level.setBlock(pos,BeltsNPieces.SPAN.get().defaultBlockState(),3)
                            || !(level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity restored)) return false;
                        restored.configure(worldPosition,peer,existing.get(pos));
                        sharedSurface.remove(pos);setChanged();continue;
                    }
                    // A different block now occupies the position: preserve both
                    // the machine and the belt data and retry if it becomes clear.
                    return false;
                }
                if(!level.getBlockState(pos).is(BeltsNPieces.SPAN.get())
                    || !(level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span)
                    || !span.belongsTo(worldPosition,peer)) {
                    removeFromSpan();return false;
                }
            }
            if(!migrating) return true;
        }
        for(BlockPos pos:cells.keySet())
            if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!canPlaceSurface(level,pos,worldPosition,peer,cells.get(pos))) return false;
        for(var entry:cells.entrySet()) {
            BlockPos pos=entry.getKey();
            if(SharedBeltSpace.compatible(level,pos,level.getBlockState(pos),entry.getValue())) {
                markSharedSurface(pos);continue;
            }
            if(level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span && span.belongsTo(worldPosition,peer)) {
                if(migrating) span.configure(worldPosition,peer,entry.getValue());
                continue;
            }
            if(!level.setBlock(pos,BeltsNPieces.SPAN.get().defaultBlockState(),3)) return false;
            if(!(level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span)) return false;
            span.configure(worldPosition,peer,entry.getValue());
        }
        if(migrating) {
            dismantling=true; // Removing obsolete surface cells is not player dismantling.
            try {
                for(BlockPos pos:legacySurface().cells.keySet()) if(!cells.containsKey(pos)
                    && level.getBlockState(pos).is(BeltsNPieces.SPAN.get())
                    && level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span
                    && span.belongsTo(worldPosition,peer)) level.removeBlock(pos,false);
            } finally { dismantling=false; }
        }
        surfaceBuilt=true;surfaceVersion=SURFACE_VERSION;legacySurface=null;setChanged();
        return true;
    }
    public void removeFromSpan() {
        removeFromSpan(null);
    }
    public void removeFromSpan(net.minecraft.world.entity.player.Player player) {
        if(dismantling||level==null||level.isClientSide) return;
        dismantle(player);
        level.setBlock(worldPosition,AllBlocks.SHAFT.getDefaultState()
            .setValue(ReinforcedPulleyBlock.AXIS,getBlockState().getValue(ReinforcedPulleyBlock.AXIS)),3);
    }
    @Override public void tick() {
        super.tick();
        previousTextureTravel=textureTravel;
        if(peer==null||level==null) return;
        if(!level.isClientSide && level.getGameTime()%20==0 && level.hasChunkAt(peer) && other()==null) {
            dismantle();level.setBlock(worldPosition,AllBlocks.SHAFT.getDefaultState().setValue(ReinforcedPulleyBlock.AXIS,getBlockState().getValue(ReinforcedPulleyBlock.AXIS)),3);return;
        }
        if(!controller || !routeLoaded() || other()==null) return;
        if(!level.isClientSide && level.getGameTime()%20==0) {
            ensureSurface();
            if(peer==null) return;
        }
        BeltPath p=path();double speed=velocity();
        if(!level.isClientSide && placementTime!=Long.MIN_VALUE) {
            long age=level.getGameTime()-placementTime;
            double rate=Math.max(1,p.length/30.0);
            if(age>0 && age<=Math.ceil(p.length/rate)) {
                int before=(int)Math.floor(Math.min(p.length,(age-1)*rate));
                int after=(int)Math.ceil(Math.min(p.length,Math.floor(age*rate)));
                for(int piece=before;piece<after;piece++) {
                    Vec3 point=p.atDistance(Math.min(p.length,piece+.5));
                    ((net.minecraft.server.level.ServerLevel)level).sendParticles(
                        new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.35f,.9f,.55f),.5f),
                        point.x,point.y+.08,point.z,2,.12,.035,.12,.01);
                    level.playSound(null,point.x,point.y,point.z,net.minecraft.sounds.SoundEvents.CHAIN_PLACE,
                        net.minecraft.sounds.SoundSource.BLOCKS,.16f,1.25f+level.random.nextFloat()*.2f);
                }
            }
        }
        if(level.isClientSide) textureTravel+=speed;
        for(Cargo c:cargo) {c.previous=c.distance;if(level.isClientSide) c.visualPrevious=c.visualDistance;}
        if(Math.abs(speed)<1e-7) {
            if(level.isClientSide) smoothCargo();
            // Create belts accept resting items without needing rotational power.
            if(!level.isClientSide && collectDroppedItems()) {
                setChanged();syncCargo();
            }
            return;
        }
        BeltRiding.carry(this,speed);
        // Process the leading stack first so queued items never overtake it.
        cargo.sort(speed>0?Comparator.comparingDouble((Cargo c)->c.distance).reversed():Comparator.comparingDouble(c->c.distance));
        // Create inserts incoming belt items at the boundary of its end block,
        // half a block beyond our pulley centre.
        Direction outputDirection=speed>0?endDirection:startDirection.getOpposite();
        BlockPos outputPos=(speed>0?peer:worldPosition).relative(outputDirection);
        boolean createReceiver=level.hasChunkAt(outputPos)
            && level.getBlockEntity(outputPos) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
        double exitDistance=createReceiver?.5:0;
        double leader=speed>0?p.length+exitDistance+.7:-exitDistance-.7;
        Iterator<Cargo> it=cargo.iterator();
        boolean removed=false;
        while(it.hasNext()) {
            Cargo c=it.next(); double next=Math.clamp(c.distance+speed,-1,p.length+1);
            next=speed>0?Math.min(next,Math.max(-1,leader-.7)):Math.max(next,Math.min(p.length+1,leader+.7));
            c.distance=next;leader=next;
            if(!level.isClientSide && (speed>0?next>=p.length+exitDistance-1e-6:next<=-exitDistance+1e-6)) {
                if(output(c,speed>0)) {it.remove();removed=true;leader=speed>0?p.length+exitDistance+.7:-exitDistance-.7;}
            }
        }
        if(level.isClientSide) smoothCargo();
        if(!level.isClientSide) {
            boolean collected=collectDroppedItems();
            if(removed || collected || !cargo.isEmpty()) setChanged();
            if(removed || collected || (!cargo.isEmpty() && level.getGameTime()%5==0)) syncCargo();
        }
    }
    private void smoothCargo() {
        for(Cargo c:cargo) {
            double predicted=c.visualDistance+(c.distance-c.previous);
            c.visualDistance=predicted+(c.distance-predicted)*.25;
        }
        rememberVisuals();
    }
    private static Vec3 visualPoint(BeltPath path,double distance) {
        if(distance<0) return path.start.add(Vec3.atLowerCornerOf(path.startDirection.getNormal()).scale(distance));
        if(distance>path.length) return path.end.add(Vec3.atLowerCornerOf(path.endDirection.getNormal()).scale(distance-path.length));
        return path.atDistance(distance);
    }
    private void rememberVisuals() {
        if(level==null || !level.isClientSide || path()==null) return;
        long tick=level.getGameTime();
        Map<UUID,VisualTransfer> memories=VISUAL_TRANSFERS.computeIfAbsent(level,k->new HashMap<>());
        memories.entrySet().removeIf(entry->tick-entry.getValue().tick()>40);
        for(Cargo c:cargo) memories.put(c.id,new VisualTransfer(worldPosition,visualPoint(path(),c.visualDistance),visualPoint(path(),c.visualPrevious),tick));
    }
    private double projectEntry(BeltPath path,Vec3 pos,double serverDistance) {
        if(serverDistance<0) return Math.clamp(pos.subtract(path.start).dot(Vec3.atLowerCornerOf(path.startDirection.getNormal())),-1,0);
        if(serverDistance>path.length) return path.length+Math.clamp(pos.subtract(path.end).dot(Vec3.atLowerCornerOf(path.endDirection.getNormal())),0,1);
        return path.nearestDistance(pos);
    }
    private boolean output(Cargo c,boolean atEnd) {
        BlockPos endpoint=atEnd?peer:worldPosition;
        Direction direction=atEnd?endDirection:startDirection.getOpposite();
        BlockPos destination=endpoint.relative(direction);
        if(!level.hasChunkAt(destination)) return false;
        if(level.getBlockEntity(destination) instanceof ReinforcedPulleyBlockEntity receiver) {
            if(!receiver.acceptsBeltDirection(direction)) return false;
            c.stack=((EndpointHandler)receiver.handler).insertFromReinforced(c.stack,Math.round(cargoAngle(path(),c,c.distance)),c.id,c.sideOffset,false);
            return c.stack.isEmpty();
        }
        DirectBeltInputBehaviour beltInput=BlockEntityBehaviour.get(level,destination,DirectBeltInputBehaviour.TYPE);
        if(beltInput!=null) {
            // Use Create's transport callback before generic inventory insertion.
            // A stopped, reversed or occupied receiver keeps the cargo here;
            // never fall back to loose-item ejection when a receiver is present.
            if(!beltInput.canInsertFromSide(direction)) return false;
            var transported=new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(c.stack);
            transported.angle=Math.round(cargoAngle(path(),c,c.distance));
            Vec3 tangent=Vec3.atLowerCornerOf((atEnd?path().endDirection:path().startDirection).getNormal());
            Vec3 worldSide=new Vec3(tangent.z,0,-tangent.x).scale(c.sideOffset);
            if(level.getBlockEntity(destination) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity belt) {
                var facing=belt.getBlockState().getValue(com.simibubi.create.content.kinetics.belt.BeltBlock.HORIZONTAL_FACING);
                transported.sideOffset=(float)(facing.getAxis()==Direction.Axis.Z?worldSide.x:-worldSide.z);
            }
            c.stack=beltInput.handleInsertion(transported,direction,false);
            return c.stack.isEmpty();
        }
        IItemHandler inventory=level.getCapability(Capabilities.ItemHandler.BLOCK,destination,direction.getOpposite());
        if(inventory!=null) {
            for(int i=0;i<inventory.getSlots()&&!c.stack.isEmpty();i++) c.stack=inventory.insertItem(i,c.stack,false);
            return c.stack.isEmpty(); // Full inventories stop items instead of dumping them.
        }
        Vec3 where=(atEnd?path().end:path().start).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(.7)).add(0,.2,0);
        ItemEntity e=new ItemEntity(level,where.x,where.y,where.z,c.stack.copy());
        // Follow the outgoing belt tangent, including its slope. Sample a short
        // section to avoid unstable directions at a flattened pulley endpoint.
        BeltPath route=path();
        double sample=Math.min(.5,route.length);
        Vec3 tangent=atEnd?route.atDistance(route.length).subtract(route.atDistance(route.length-sample))
            :route.atDistance(0).subtract(route.atDistance(sample));
        if(tangent.lengthSqr()<1e-8) tangent=Vec3.atLowerCornerOf(direction.getNormal());
        Vec3 launch=tangent.normalize().scale(Math.max(.125,Math.abs(velocity()))).add(0,.125,0);
        e.setDeltaMovement(launch);
        e.hurtMarked=true;
        e.getPersistentData().putLong("BeltsNPiecesIgnoreUntil",level.getGameTime()+30);
        e.setDefaultPickUpDelay();
        if(level.addFreshEntity(e)) {c.stack=ItemStack.EMPTY;return true;}
        return false;
    }
    private boolean roomAt(double distance) {
        return cargo.size()<CAPACITY && cargo.stream().noneMatch(c->Math.abs(c.distance-distance)<.7);
    }
    private boolean collectDroppedItems() {
        if(cargo.size()>=CAPACITY) return false;
        boolean collected=false;
        BeltPath p=path();
        for(ItemEntity e:level.getEntitiesOfClass(ItemEntity.class,p.bounds.inflate(.2,.5,.2))) {
            if(!e.isAlive()||e.getItem().isEmpty()||e.getDeltaMovement().y>0 || e.getPersistentData().getLong("BeltsNPiecesIgnoreUntil")>level.getGameTime()) continue;
            double distance=p.nearestDistance(e.position()); Vec3 nearest=p.atDistance(distance);
            // Create picks up items whose entity bounds overlap a belt block.
            // Test the dropped item's bounds against the curved belt width too,
            // rather than requiring its centre to lie inside that width.
            var box=e.getBoundingBox();
            double dx=Math.max(0,Math.max(box.minX-nearest.x,nearest.x-box.maxX));
            double dz=Math.max(0,Math.max(box.minZ-nearest.z,nearest.z-box.maxZ));
            if(box.maxY<nearest.y-.12 || box.minY>nearest.y+.55
                || Math.hypot(dx,dz)>7.0/16 || !roomAt(distance)) continue;
            cargo.add(newCargo(e.getItem().copy(),distance));e.discard();collected=true;
        }
        return collected;
    }
    public void dismantle() {
        dismantle(null);
    }
    private void dismantle(net.minecraft.world.entity.player.Player player) {
        if(dismantling||level==null||level.isClientSide) return;
        dismantling=true;
        ReinforcedPulleyBlockEntity remote=other();
        ReinforcedPulleyBlockEntity owner=controller?this:remote;
        if(owner!=null) {
            owner.dismantling=true; // Prevent span-removal callbacks from dismantling twice.
            for(BlockPos pos:owner.managedSurfacePositions()) {
                if(level.hasChunkAt(pos) && level.getBlockState(pos).is(BeltsNPieces.SPAN.get())
                    && level.getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span
                    && span.belongsTo(owner.worldPosition,owner.peer)) level.removeBlock(pos,false);
            }
            for(Cargo c:owner.cargo) returnOrDrop(player,c.stack,owner.path().atDistance(c.distance));
            owner.cargo.clear();
            owner.casingId=null; // Casings are free to apply and never refunded.
            int remaining=owner.paidBelts;
            while(remaining>0) {
                int count=Math.min(remaining,BeltsNPieces.REINFORCED_BELT.get().getDefaultMaxStackSize());
                returnOrDrop(player,new ItemStack(BeltsNPieces.REINFORCED_BELT.get(),count),Vec3.atCenterOf(worldPosition));
                remaining-=count;
            }
            owner.paidBelts=0;owner.setChanged();
        }
        detachKinetics();peer=null;cachedPath=null;setChanged();
        if(remote!=null) {
            remote.detachKinetics();remote.peer=null;remote.cachedPath=null;remote.setChanged();
            level.setBlock(remote.worldPosition,AllBlocks.SHAFT.getDefaultState().setValue(ReinforcedPulleyBlock.AXIS,remote.getBlockState().getValue(ReinforcedPulleyBlock.AXIS)),3);
        }
        level.invalidateCapabilities(worldPosition);
    }
    private void returnOrDrop(net.minecraft.world.entity.player.Player player,ItemStack stack,Vec3 pos) {
        if(player==null) {drop(stack,pos);return;}
        // Minecraft's inventory helper merges stacks, syncs slots and drops overflow.
        player.getInventory().placeItemBackInInventory(stack.copy());
    }
    private void drop(ItemStack stack,Vec3 pos) {
        if(stack.isEmpty()) return;
        ItemEntity e=new ItemEntity(level,pos.x,pos.y,pos.z,stack.copy());
        e.getPersistentData().putLong("BeltsNPiecesIgnoreUntil",level.getGameTime()+30);level.addFreshEntity(e);
    }
    @Override protected AABB createRenderBoundingBox() { return !controller||path()==null?new AABB(worldPosition):path().bounds.inflate(1); }
    @Override protected void write(CompoundTag n,HolderLookup.Provider registries,boolean clientPacket) {
        super.write(n,registries,clientPacket);
        n.putLong("PlacementTime",placementTime);
        if(peer!=null) n.putLong("Peer",peer.asLong());
        n.putBoolean("Controller",controller);n.putInt("StartDirection",startDirection.get3DDataValue());n.putInt("EndDirection",endDirection.get3DDataValue());
        n.putInt("BeltColor",beltColor);
        if(casingId!=null) n.putString("CasingId",casingId.toString());
        n.putInt("CurveStyle",curveStyle);
        n.putInt("PathRevision",pathRevision);
        n.putDouble("ElevationCenter",elevationCenter);n.putDouble("ElevationHeight",elevationHeight);
        ListTag bends=new ListTag();for(var edit:elevationEdits) {CompoundTag e=new CompoundTag();e.putDouble("Center",edit.center());e.putDouble("Height",edit.height());e.putDouble("X",edit.x());e.putDouble("Z",edit.z());bends.add(e);}n.put("ElevationEdits",bends);
        n.putInt("Ratio",ratio);n.putInt("PaidBelts",paidBelts);
        n.putBoolean("SurfaceBuilt",surfaceBuilt);
        n.putInt("SurfaceVersion",surfaceVersion);
        n.putLongArray("SharedSurface",sharedSurface.stream().mapToLong(BlockPos::asLong).toArray());
        writeCargo(n,registries);
    }
    private void writeCargo(CompoundTag n,HolderLookup.Provider registries) {
        ListTag items=new ListTag();
        for(Cargo c:cargo) {CompoundTag t=new CompoundTag();t.put("Stack",c.stack.save(registries));t.putDouble("Distance",c.distance);t.putUUID("Id",c.id);t.putInt("Angle",c.angle);t.putDouble("ReferenceHeading",c.referenceHeading);t.putDouble("SideOffset",c.sideOffset);items.add(t);}
        n.put("Cargo",items);
    }
    @Override protected void read(CompoundTag n,HolderLookup.Provider registries,boolean clientPacket) {
        BlockPos oldPeer=peer;boolean oldController=controller;
        Direction oldStart=startDirection,oldEnd=endDirection;
        int oldStyle=curveStyle,oldRevision=pathRevision;
        double oldCenter=elevationCenter,oldHeight=elevationHeight;
        var oldEdits=List.copyOf(elevationEdits);
        super.read(n,registries,clientPacket);
        placementTime=n.contains("PlacementTime")?n.getLong("PlacementTime"):Long.MIN_VALUE;
        peer=n.contains("Peer")?BlockPos.of(n.getLong("Peer")):null;controller=n.getBoolean("Controller");
        startDirection=Direction.from3DDataValue(n.getInt("StartDirection"));endDirection=Direction.from3DDataValue(n.getInt("EndDirection"));
        beltColor=n.contains("BeltColor")?Math.clamp(n.getInt("BeltColor"),-1,15):-1;
        if(n.contains("CasingId")) casingId=net.minecraft.resources.ResourceLocation.tryParse(n.getString("CasingId"));
        else casingId=switch(n.getInt("CasingType")) {
            case 1 -> BeltCasings.ANDESITE;
            case 2 -> BeltCasings.BRASS;
            default -> null;
        };
        curveStyle=Math.clamp(n.getInt("CurveStyle"),0,3);
        pathRevision=Math.clamp(n.getInt("PathRevision"),0,3);
        double savedCenter=n.contains("ElevationCenter")?n.getDouble("ElevationCenter"):.5;
        double savedHeight=n.getDouble("ElevationHeight");
        elevationCenter=Double.isFinite(savedCenter)?Math.clamp(savedCenter,0,1):.5;
        elevationHeight=Double.isFinite(savedHeight)?Math.clamp(savedHeight,-4,4):0;
        elevationEdits.clear();ListTag bends=n.getList("ElevationEdits",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(BeltsNPiecesConfig.ABSOLUTE_MAX_BELT_ADJUSTMENTS,bends.size());i++) {var e=bends.getCompound(i);double c=e.getDouble("Center"),h=e.getDouble("Height"),x=e.getDouble("X"),z=e.getDouble("Z");if(Double.isFinite(c)&&c>0&&c<1&&Double.isFinite(h)&&Math.abs(h)<=4&&Double.isFinite(x)&&Double.isFinite(z)&&Math.hypot(x,z)<=4.000001) elevationEdits.add(new BeltElevation.Control(c,h,x,z));}
        ratio=n.getInt("Ratio")==-1?-1:1;paidBelts=Math.max(0,n.getInt("PaidBelts"));
        boolean shapeChanged=!Objects.equals(oldPeer,peer)||oldController!=controller||oldStart!=startDirection||oldEnd!=endDirection
            ||oldStyle!=curveStyle||oldRevision!=pathRevision||Double.compare(oldCenter,elevationCenter)!=0
            ||Double.compare(oldHeight,elevationHeight)!=0||!oldEdits.equals(elevationEdits);
        if(shapeChanged) {cachedPath=null;invalidateRenderBoundingBox();}
        surfaceBuilt=n.getBoolean("SurfaceBuilt");
        surfaceVersion=n.getInt("SurfaceVersion");legacySurface=null;
        sharedSurface.clear();for(long pos:n.getLongArray("SharedSurface")) sharedSurface.add(BlockPos.of(pos));
        readCargo(n,registries,clientPacket);
    }
    private void syncCargo() {
        if(level instanceof net.minecraft.server.level.ServerLevel server) {
            CompoundTag update=new CompoundTag();writeCargo(update,level.registryAccess());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(server,new ChunkPos(worldPosition),new BeltCargoPayload(worldPosition,update));
        }
    }
    public void receiveCargo(CompoundTag n,HolderLookup.Provider registries) {
        if(level!=null && level.isClientSide && controller && peer!=null) readCargo(n,registries,true);
    }
    private void readCargo(CompoundTag n,HolderLookup.Provider registries,boolean clientPacket) {
        Map<UUID,Cargo> displayed=new HashMap<>();
        if(clientPacket) {rememberVisuals();for(Cargo old:cargo) displayed.put(old.id,old);}
        cargo.clear(); ListTag list=n.getList("Cargo",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(CAPACITY,list.size());i++) {CompoundTag t=list.getCompound(i);ItemStack s=ItemStack.parseOptional(registries,t.getCompound("Stack"));
            double d=t.getDouble("Distance");if(!s.isEmpty()&&Double.isFinite(d)) {
                double at=path()==null?0:Math.clamp(d,-1,path().length+1);
                Cargo item=new Cargo(s,at,t.getInt("Angle"));
                double side=t.getDouble("SideOffset");item.sideOffset=Double.isFinite(side)?Math.clamp(side,-.5,.5):0;
                if(t.hasUUID("Id")) item.id=t.getUUID("Id");
                Cargo old=displayed.get(item.id);
                if(old!=null) {item.visualDistance=old.visualDistance;item.visualPrevious=old.visualPrevious;}
                else if(clientPacket && level!=null && path()!=null) {
                    VisualTransfer transfer=VISUAL_TRANSFERS.getOrDefault(level,Map.of()).get(item.id);
                    if(transfer!=null && !transfer.owner().equals(worldPosition) && level.getGameTime()-transfer.tick()<=10
                        && transfer.position().distanceToSqr(visualPoint(path(),at))<4) {
                        item.visualDistance=projectEntry(path(),transfer.position(),at);
                        item.visualPrevious=projectEntry(path(),transfer.previous(),at);
                    } else if(at<0 || at>path().length) {
                        // On a newly received stack, retain the preceding tick of
                        // motion instead of rendering its first frame at rest.
                        double previous=Math.clamp(at-velocity(),-1,path().length+1);
                        item.visualPrevious=previous;
                    }
                }
                double heading=t.contains("ReferenceHeading")?t.getDouble("ReferenceHeading"):(path()==null?0:beltHeading(path(),at));
                item.referenceHeading=Double.isFinite(heading)?heading:0;cargo.add(item);
            }}
        if(clientPacket) invalidateRenderBoundingBox();
    }
    private final class EndpointHandler implements IItemHandler {
        private ReinforcedPulleyBlockEntity ready() {
            ReinforcedPulleyBlockEntity r=root();return r!=null&&r.peer!=null&&r.routeLoaded()&&r.other()!=null?r:null;
        }
        private boolean near(Cargo c,ReinforcedPulleyBlockEntity r) { return controller?c.distance<.65:c.distance>r.path().length-.65; }
        private List<Cargo> atEnd(ReinforcedPulleyBlockEntity r) { return r.cargo.stream().filter(c->near(c,r)).toList(); }
        @Override public int getSlots() { return CAPACITY; }
        @Override public ItemStack getStackInSlot(int slot) {
            ReinforcedPulleyBlockEntity r=ready();if(r==null||slot<0||slot>=CAPACITY) return ItemStack.EMPTY;
            List<Cargo> items=atEnd(r);return slot<items.size()?items.get(slot).stack.copy():ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
            if(slot!=0) return stack;
            int angle=new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(stack).angle;
            return insert(stack,simulate,0,angle,null,0);
        }
        private ItemStack insertTransported(com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack transported,Direction direction,boolean simulate) {
            double entryOffset=.75,side=0;
            var source=level.getBlockEntity(worldPosition.relative(direction.getOpposite()));
            if(source instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity belt) {
                var root=belt.getControllerBE();
                if(root!=null && Float.isFinite(transported.beltPosition) && Float.isFinite(transported.sideOffset)) {
                    Vec3 sourcePoint=com.simibubi.create.content.kinetics.belt.BeltHelper.getVectorForOffset(root,transported.beltPosition);
                    Vec3 endpoint=controller?path().start:path().end;
                    entryOffset=Math.clamp(endpoint.subtract(sourcePoint).dot(Vec3.atLowerCornerOf(direction.getNormal())),0,1);
                    Vec3 sourceSide=root.getBlockState().getValue(com.simibubi.create.content.kinetics.belt.BeltBlock.HORIZONTAL_FACING).getAxis()==Direction.Axis.Z
                        ?new Vec3(transported.sideOffset,0,0):new Vec3(0,0,-transported.sideOffset);
                    Vec3 tangent=Vec3.atLowerCornerOf((controller?startDirection:endDirection).getNormal());
                    side=Math.clamp(sourceSide.dot(new Vec3(tangent.z,0,-tangent.x)),-.5,.5);
                }
            }
            return insert(transported.stack,simulate,entryOffset,transported.angle,null,side);
        }
        private ItemStack insertFromReinforced(ItemStack stack,int angle,UUID id,double side,boolean simulate) {
            return insert(stack,simulate,1,angle,id,side);
        }
        private ItemStack insert(ItemStack stack,boolean simulate,double entryOffset,int angle,UUID transferId,double side) {
            ReinforcedPulleyBlockEntity r=ready();if(r==null||stack.isEmpty()) return stack;
            // Only accept at the input end while moving; accept either end at rest.
            double v=r.velocity();if(controller?v<0:v>0) return stack;
            double distance=controller?-entryOffset:r.path().length+entryOffset;if(!r.roomAt(distance)) return stack;
            int count=Math.min(stack.getCount(),stack.getMaxStackSize());
            if(!simulate) {
                Cargo item=r.newCargo(stack.copyWithCount(count),distance,angle);
                item.sideOffset=side;
                if(transferId!=null && count==stack.getCount()) item.id=transferId;
                r.cargo.add(item);r.setChanged();r.syncCargo();
            }
            return stack.getCount()==count?ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-count);
        }
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate) {
            ReinforcedPulleyBlockEntity r=ready();if(r==null||slot<0||slot>=CAPACITY||amount<=0) return ItemStack.EMPTY;
            List<Cargo> items=atEnd(r);if(slot>=items.size()) return ItemStack.EMPTY;
            Cargo c=items.get(slot);int count=Math.min(amount,c.stack.getCount());ItemStack result=c.stack.copyWithCount(count);
            if(!simulate) {c.stack.shrink(count);if(c.stack.isEmpty())r.cargo.remove(c);r.setChanged();r.syncCargo();}
            return result;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot,ItemStack stack) { return slot==0&&!stack.isEmpty(); }
    }
}
