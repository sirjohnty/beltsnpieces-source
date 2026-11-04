package com.sirjohnty.beltsnpieces;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.*;
import java.util.*;

public final class ReinforcedBeltItem extends Item {
    public static final String SELECTION="BeltsNPiecesSelection";
    public ReinforcedBeltItem(Properties p) { super(p); }
    public static BlockPos selection(ItemStack stack, Level level) {
        CompoundTag n=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!n.contains(SELECTION)) return null;
        CompoundTag s=n.getCompound(SELECTION);
        if(!s.contains("Pos") || !s.getString("Dimension").equals(level.dimension().location().toString())) return null;
        BlockPos pos=BlockPos.of(s.getLong("Pos"));
        // A removed or replaced start must not trap the item in endpoint selection.
        // Keep unloaded selections until their chunk can be checked reliably.
        return level.hasChunkAt(pos) && !isShaft(level.getBlockState(pos)) ? null : pos;
    }
    public static void clear(ItemStack stack) {
        int style=curveStyle(stack);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->{
            CompoundTag preferences=new CompoundTag();preferences.putInt("CurveStyle",style);
            n.put(SELECTION,preferences);
        });
    }
    public static int curveStyle(ItemStack stack) {
        CompoundTag n=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return Math.clamp(n.getCompound(SELECTION).getInt("CurveStyle"),0,3);
    }
    /** 0 faces the player automatically; 1–4 select east, south, west and north belt approach. */
    public static int shaftAxisChoice(ItemStack stack) {
        CompoundTag n=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return Math.clamp(n.getCompound(SELECTION).getInt("EndShaftAxis"),0,4);
    }
    /** The path tangent points into the endpoint, along the player's viewing direction. */
    public static int placementDirectionChoice(ItemStack stack,Player player) {
        return player.isShiftKeyDown()?endDirectionChoice(player.getDirection().getOpposite()):0;
    }
    public static void setShaftAxisChoice(ItemStack stack,int choice) {
        if(choice<1||choice>4) return;
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->{CompoundTag selection=n.getCompound(SELECTION);selection.putInt("EndShaftAxis",choice);n.put(SELECTION,selection);});
    }
    public static String styleName(int style) {
        return switch(style) { case 1 -> "Compact"; case 2 -> "Extended"; case 3 -> "Straight"; default -> "Default"; };
    }
    public static String styleDescription(int style) {
        return switch(style) {
            case 1 -> "Shorter and tighter curves";
            case 2 -> "Wider curves";
            case 3 -> "Keeps to a right angle when possible";
            default -> "Balanced curves";
        };
    }
    public static void cycleStyle(ItemStack stack,Player player) {
        cycleStyle(stack,player,1);
    }
    public static void cycleStyle(ItemStack stack,Player player,int direction) {
        int next=Math.floorMod(curveStyle(stack)+direction,4);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->{ CompoundTag s=n.getCompound(SELECTION);s.putInt("CurveStyle",next);n.put(SELECTION,s); });
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(!player.mayBuild() || selection(stack,level)==null) return InteractionResultHolder.pass(stack);
        if(player.isShiftKeyDown()) {
            if(!level.isClientSide) {clear(stack);player.displayClientMessage(Component.literal("Reinforced Belt selection cleared."),true);}
            return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        if(level.isClientSide) return InteractionResultHolder.success(stack);
        return InteractionResultHolder.fail(stack); // Generated endpoints use the validated confirmation payload.
    }
    public static boolean isShaft(BlockState s) { return AllBlocks.SHAFT.has(s) && s.getFluidState().isEmpty() && s.getValue(RotatedPillarKineticBlock.AXIS)!=Direction.Axis.Y; }
    /** Existing belt ends can seed a separate adjacent shaft for a new belt. */
    public static com.simibubi.create.content.kinetics.base.KineticBlockEntity extensionEndpoint(Level level,BlockPos pos) {
        if(!level.hasChunkAt(pos)) return null;
        var be=level.getBlockEntity(pos);
        if(be instanceof ReinforcedPulleyBlockEntity p && p.peer!=null) return p;
        if(be instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity b && b.beltLength>1
            && (b.getBlockState().getValue(com.simibubi.create.content.kinetics.belt.BeltBlock.PART)==com.simibubi.create.content.kinetics.belt.BeltPart.START
                || b.getBlockState().getValue(com.simibubi.create.content.kinetics.belt.BeltBlock.PART)==com.simibubi.create.content.kinetics.belt.BeltPart.END)
            && ((com.simibubi.create.content.kinetics.base.IRotate)b.getBlockState().getBlock()).getRotationAxis(b.getBlockState())!=Direction.Axis.Y) return b;
        return null;
    }
    /** Choose the side of the second shaft from the hit position. */
    public static boolean flipEnd(Level level,BlockPos first,BlockPos target,Vec3 hit) {
        BlockState state=level.getBlockState(target);
        if(!isShaft(state)) return false;
        Vec3 delta=Vec3.atCenterOf(target).subtract(Vec3.atCenterOf(first));
        Direction incoming=BeltPath.tangent(state.getValue(RotatedPillarKineticBlock.AXIS),delta,false);
        Vec3 normal=Vec3.atLowerCornerOf(incoming.getNormal());
        return hit.subtract(Vec3.atCenterOf(target)).dot(normal)>1e-5;
    }
    public static BeltPath path(Level level, BlockPos a, BlockPos b, boolean flipStart, boolean flipEnd) {
        return path(level,a,b,flipStart,flipEnd,0);
    }
    public static BeltPath path(Level level, BlockPos a, BlockPos b, boolean flipStart, boolean flipEnd,int style) {
        return pathWithTarget(level,a,b,level.getBlockState(b),flipStart,flipEnd,style);
    }
    public static BeltPath pathWithTarget(Level level,BlockPos a,BlockPos b,BlockState sb,boolean flipStart,boolean flipEnd,int style) {
        BlockState sa=level.getBlockState(a);
        if(!isShaft(sa)||!isShaft(sb)) return null;
        Vec3 delta=Vec3.atCenterOf(b).subtract(Vec3.atCenterOf(a));
        return new BeltPath(a,b,BeltPath.tangent(sa.getValue(RotatedPillarKineticBlock.AXIS),delta,flipStart),
            BeltPath.tangent(sb.getValue(RotatedPillarKineticBlock.AXIS),delta,flipEnd),style);
    }
    public static Direction selectedEndDirection(int choice) {
        return switch(choice) {case 1->Direction.EAST;case 2->Direction.SOUTH;case 3->Direction.WEST;case 4->Direction.NORTH;default->null;};
    }
    public static int endDirectionChoice(Direction d) {
        return switch(d) {case EAST->1;case SOUTH->2;case WEST->3;case NORTH->4;default->0;};
    }
    public static boolean generatedEndFlip(BlockPos first,Target target,int choice) {
        Direction desired=selectedEndDirection(choice);
        if(desired==null) return false;
        Vec3 delta=Vec3.atCenterOf(target.pos()).subtract(Vec3.atCenterOf(first));
        return BeltPath.tangent(target.shaft().getValue(RotatedPillarKineticBlock.AXIS),delta,false)!=desired;
    }
    public record Target(BlockPos pos,BlockState shaft,boolean generated) {}
    public static Target target(Level level,BlockPos first,BlockPos clicked,Direction face,int axisChoice) {
        BlockState clickedState=level.getBlockState(clicked);
        if(isShaft(clickedState)) return new Target(clicked,clickedState,false);
        if(!isShaft(level.getBlockState(first))) return null;
        return generatedTarget(level,first,generatedPosition(clicked,face),axisChoice);
    }
    public static BlockPos generatedPosition(BlockPos clicked,Direction face) {
        BlockPos adjacent=clicked.relative(face);
        return new BlockPos(adjacent.getX(),clicked.getY()+1,adjacent.getZ());
    }
    public static final double AIR_REACH=5.0;
    public static Target airTarget(Level level,BlockPos first,Player player,int axisChoice) {
        return generatedTarget(level,first,BlockPos.containing(player.getEyePosition().add(player.getViewVector(1).scale(AIR_REACH))),axisChoice);
    }
    public static Target generatedTarget(Level level,BlockPos first,BlockPos pos,int axisChoice) {
        // Reject oversized endpoints before constructing and sampling any geometry.
        if(first.distSqr(pos)>(double)maxLength()*maxLength()) return null;
        if(!level.hasChunkAt(first) || !isShaft(level.getBlockState(first))) return null;
        if(!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
        // Only replace air: this avoids deleting plants, fluids, block entities or other blocks.
        if(!level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty() || level.getBlockEntity(pos)!=null) return null;
        return new Target(pos,generatedShaft(level,first,pos,axisChoice),true);
    }
    /** Orientation only; blocked previews must not bypass generatedTarget's clearance checks. */
    public static BlockState generatedShaft(Level level,BlockPos first,BlockPos pos,int axisChoice) {
        int dx=pos.getX()-first.getX(),dz=pos.getZ()-first.getZ();
        // The axle runs across the tread, not along it. On a diagonal tie,
        // retain the source axle for a stable, straight-through approach.
        Direction.Axis axis=Math.abs(dx)==Math.abs(dz)
            ?level.getBlockState(first).getValue(RotatedPillarKineticBlock.AXIS)
            :Math.abs(dx)>Math.abs(dz)?Direction.Axis.Z:Direction.Axis.X;
        Direction chosen=selectedEndDirection(axisChoice);
        if(chosen!=null) axis=chosen.getAxis()==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X;
        return AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS,axis);
    }
    public static boolean hasShaft(Player player) {
        if(player.isCreative()) return true;
        var inventory=player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++) if(AllBlocks.SHAFT.isIn(inventory.getItem(i))) return true;
        return false;
    }
    private static void consumeShaft(Player player) {
        var inventory=player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++) if(AllBlocks.SHAFT.isIn(inventory.getItem(i))) {
            inventory.getItem(i).shrink(1);inventory.setChanged();return;
        }
    }
    /** Count block spaces on an axis-aligned route between endpoints, including both ends. */
    public static int beltCost(BeltPath path) {
        BlockPos start=BlockPos.containing(path.start),end=BlockPos.containing(path.end);
        return 1+Math.abs(end.getX()-start.getX())+Math.abs(end.getY()-start.getY())+Math.abs(end.getZ()-start.getZ());
    }
    public static int availableBelts(Player player) {
        int total=0;var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize();slot++) {
            ItemStack candidate=inventory.getItem(slot);
            if(candidate.is(BeltsNPieces.REINFORCED_BELT.get())) total+=candidate.getCount();
        }
        return total;
    }
    private static void consumeBelts(Player player,int count) {
        var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize() && count>0;slot++) {
            ItemStack candidate=inventory.getItem(slot);
            if(!candidate.is(BeltsNPieces.REINFORCED_BELT.get())) continue;
            int take=Math.min(count,candidate.getCount());candidate.shrink(take);count-=take;
        }
        inventory.setChanged();
    }
    public static int maxLength() { return BeltsNPiecesConfig.maxReinforcedBeltLength(); }
    /** Null means valid. Used identically by the server placement and ghost preview. */
    public static String invalid(Level level,BlockPos a,BlockPos b,BeltPath path) {
        if(a.equals(b)) return "Select a different shaft.";
        if(path==null) return "Select two ordinary horizontal Create shafts.";
        if(a.distSqr(b)<4) return "Shafts must be at least 2 blocks apart.";
        if(path.length>maxLength()) return "Reinforced Belt exceeds the belt length limit ("+maxLength()+").";
        String geometryError=path.placementGeometryError();
        if(geometryError!=null) return geometryError;
        for(BlockPos p:path.surface().cells.keySet()) {
            if(!level.hasChunkAt(p)) return "The full belt must be in loaded chunks.";
            if(!ReinforcedPulleyBlockEntity.canPlaceSurface(level,p,a,b,path.surface().cells.get(p))) return "Belt surface is obstructed.";
        }
        return null;
    }
    @Override public InteractionResult useOn(UseOnContext ctx) {
        var player=ctx.getPlayer(); if(player==null||!player.mayBuild()) return InteractionResult.FAIL;
        Level level=ctx.getLevel(); ItemStack stack=ctx.getItemInHand(); BlockPos pos=ctx.getClickedPos();
        if(player.isShiftKeyDown()) {
            if(!level.isClientSide) {clear(stack);player.displayClientMessage(Component.literal("Reinforced Belt selection cleared."),true);}
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos first=selection(stack,level);
        var extension=first==null?extensionEndpoint(level,pos):null;
        if(extension!=null) {
            if(level.isClientSide) return InteractionResult.SUCCESS;
            if(!hasShaft(player)) {
                player.displayClientMessage(Component.literal("A shaft and a loaded belt endpoint are required."),true);return InteractionResult.FAIL;
            }
            Direction outward;
            if(extension instanceof ReinforcedPulleyBlockEntity p) outward=p.controller?p.startDirection.getOpposite():p.endDirection;
            else {
                var belt=(com.simibubi.create.content.kinetics.belt.BeltBlockEntity)extension;
                var controller=belt.getControllerBE();
                if(controller==null) return InteractionResult.FAIL;
                BlockPos next=com.simibubi.create.content.kinetics.belt.BeltHelper.getPositionForOffset(controller,belt.index==0?1:belt.beltLength-2);
                BlockPos delta=pos.subtract(next);
                outward=Direction.getNearest(delta.getX(),0,delta.getZ());
            }
            if(outward==null) return InteractionResult.FAIL;
            BlockPos adjacent=pos.relative(outward);
            if(!level.hasChunkAt(adjacent) || level.isOutsideBuildHeight(adjacent) || !level.getWorldBorder().isWithinBounds(adjacent)
                || !level.mayInteract(player,pos) || !level.mayInteract(player,adjacent) || !player.mayUseItemAt(adjacent,outward,stack)
                || !level.getBlockState(adjacent).isAir() || !level.getFluidState(adjacent).isEmpty() || level.getBlockEntity(adjacent)!=null) {
                player.displayClientMessage(Component.literal("No clear space next to this belt endpoint."),true);return InteractionResult.FAIL;
            }
            var axle=((com.simibubi.create.content.kinetics.base.IRotate)extension.getBlockState().getBlock()).getRotationAxis(extension.getBlockState());
            BlockState newShaft=AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS,axle);
            if(!level.isUnobstructed(newShaft,adjacent,net.minecraft.world.phys.shapes.CollisionContext.of(player)) || !level.setBlock(adjacent,newShaft,3)) {
                player.displayClientMessage(Component.literal("Could not place the adjacent shaft."),true);return InteractionResult.FAIL;
            }
            if(!player.isCreative()) consumeShaft(player);
            selectStart(stack,level,adjacent);
            CustomData.update(DataComponents.CUSTOM_DATA,stack,n->n.getCompound(SELECTION).putLong("ExtensionFrom",pos.asLong()));
            level.playSound(null,adjacent,SoundEvents.CHAIN_PLACE,SoundSource.BLOCKS,.7f,1);
            return InteractionResult.SUCCESS;
        }
        if(first==null && !isShaft(level.getBlockState(pos))) {
            BlockPos placed=pos.relative(ctx.getClickedFace());
            if(!level.hasChunkAt(placed) || level.isOutsideBuildHeight(placed) || !level.getWorldBorder().isWithinBounds(placed)
                || !level.mayInteract(player,pos) || !level.mayInteract(player,placed)
                || !player.mayUseItemAt(placed,ctx.getClickedFace(),stack)
                || !level.getBlockState(placed).isAir() || !level.getFluidState(placed).isEmpty()
                || level.getBlockEntity(placed)!=null) {
                if(!level.isClientSide) player.displayClientMessage(Component.literal("No clear space for a shaft here."),true);
                return InteractionResult.FAIL;
            }
            if(!hasShaft(player)) {
                if(!level.isClientSide) player.displayClientMessage(Component.literal("A shaft is required."),true);
                return InteractionResult.FAIL;
            }
            // The belt approaches opposite the player's facing; its axle runs
            // across that approach, matching the generated endpoint shafts.
            Direction approach=player.getDirection().getOpposite();
            Direction.Axis axle=approach.getAxis()==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X;
            BlockState shaft=AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS,axle);
            if(!level.isUnobstructed(shaft,placed,net.minecraft.world.phys.shapes.CollisionContext.of(player))) {
                if(!level.isClientSide) player.displayClientMessage(Component.literal("The shaft space is obstructed."),true);
                return InteractionResult.FAIL;
            }
            if(level.isClientSide) return InteractionResult.SUCCESS;
            if(!level.setBlock(placed,shaft,3)) return InteractionResult.FAIL;
            if(!player.isCreative()) consumeShaft(player);
            selectStart(stack,level,placed);
            level.playSound(null,placed,SoundEvents.CHAIN_PLACE,SoundSource.BLOCKS,.7f,1);
            return InteractionResult.SUCCESS;
        }
        if(level.isClientSide) return InteractionResult.SUCCESS;
        if(first==null) {
            selectStart(stack,level,pos);
            return InteractionResult.SUCCESS;
        }
        if(first.equals(pos)) return InteractionResult.FAIL;
        Target target=target(level,first,pos,ctx.getClickedFace(),placementDirectionChoice(stack,player));
        if(target==null) {player.displayClientMessage(Component.literal("No clear space for a shaft here."),true);return InteractionResult.FAIL;}
        if(target.generated()) return InteractionResult.FAIL;
        return place(level,player,stack,first,target,ctx.getClickedFace(),ctx.getClickLocation());
    }
    private static void selectStart(ItemStack stack,Level level,BlockPos pos) {
        CompoundTag pick=new CompoundTag();pick.putLong("Pos",pos.asLong());pick.putString("Dimension",level.dimension().location().toString());
        pick.putInt("CurveStyle",curveStyle(stack));
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->n.put(SELECTION,pick));
    }
    public InteractionResult place(Level level,Player player,ItemStack stack,BlockPos first,Target target,Direction clickedFace,Vec3 hitLocation) {
        BlockPos pos=target.pos();
        if(first.distSqr(pos)>(double)maxLength()*maxLength()) return InteractionResult.FAIL;
        if(!level.hasChunkAt(first) || !player.mayUseItemAt(first,clickedFace,stack) ||
            !player.mayUseItemAt(pos,clickedFace,stack) || !level.mayInteract(player,first) || !level.mayInteract(player,pos)) {
            return InteractionResult.FAIL;
        }
        if(target.generated() && !hasShaft(player)) {player.displayClientMessage(Component.literal("A shaft is required."),true);return InteractionResult.FAIL;}
        if(target.generated() && !level.isUnobstructed(target.shaft(),pos,net.minecraft.world.phys.shapes.CollisionContext.of(player))) {
            player.displayClientMessage(Component.literal("The shaft space is obstructed."),true);return InteractionResult.FAIL;
        }
        BeltPath path=pathWithTarget(level,first,pos,target.shaft(),false,target.generated()?generatedEndFlip(first,target,placementDirectionChoice(stack,player)):flipEnd(level,first,pos,hitLocation),curveStyle(stack)); String error=invalid(level,first,pos,path);
        if(error!=null) {player.displayClientMessage(Component.literal(error),true);return InteractionResult.FAIL;}
        int cost=beltCost(path);
        if(!player.isCreative() && availableBelts(player)<cost) {
            player.displayClientMessage(Component.literal("Requires "+cost+" Reinforced Belts."),true);return InteractionResult.FAIL;
        }
        for(BlockPos p:path.surface().cells.keySet()) {
            if(!player.mayUseItemAt(p,Direction.UP,stack)||!level.mayInteract(player,p)) {
                player.displayClientMessage(Component.literal("Cannot build a belt here."),true);return InteractionResult.FAIL;
            }
        }
        // Surface placement may replace plants and other replaceable states.
        // Keep those states until the entire operation commits.
        Map<BlockPos,BlockState> originalSurface=new HashMap<>();
        for(BlockPos p:path.surface().cells.keySet()) originalSurface.put(p.immutable(),level.getBlockState(p));
        BlockState sa=level.getBlockState(first),oldTarget=level.getBlockState(pos),sb=target.shaft();
        var newA=BeltsNPieces.PULLEY.get().defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS,sa.getValue(RotatedPillarKineticBlock.AXIS));
        var newB=BeltsNPieces.PULLEY.get().defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS,sb.getValue(RotatedPillarKineticBlock.AXIS));
        // No source shaft inventories are replaced; only plain shafts are accepted.
        boolean placedA=level.setBlock(first,newA,3),placedB=placedA&&level.setBlock(pos,newB,3);
        if(!placedA||!placedB||!(level.getBlockEntity(first) instanceof ReinforcedPulleyBlockEntity a)||!(level.getBlockEntity(pos) instanceof ReinforcedPulleyBlockEntity b)) {
            if(placedA) level.setBlock(first,sa,3); if(placedB) level.setBlock(pos,oldTarget,3);
            player.displayClientMessage(Component.literal("Could not place Reinforced Belt."),true);return InteractionResult.FAIL;
        }
        a.connect(b,true,path,player.isCreative()?0:cost); b.connect(a,false,path,0);
        if(!a.ensureSurface()) {
            a.paidBelts=0; // The held belt has not been consumed yet.
            a.dismantle();
            originalSurface.forEach((p,state)->{if(!level.getBlockState(p).equals(state)) level.setBlock(p,state,3);});
            level.setBlock(first,sa,3);level.setBlock(pos,oldTarget,3);
            player.displayClientMessage(Component.literal("Could not create the belt surface."),true);return InteractionResult.FAIL;
        }
        var selectionData=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound(SELECTION);
        if(selectionData.contains("ExtensionFrom") && net.neoforged.fml.ModList.get().isLoaded("createtransmission"))
            TransmissionAttachments.connect(level,player,stack,BlockPos.of(selectionData.getLong("ExtensionFrom")),first);
        clear(stack); if(!player.isCreative()) {consumeBelts(player,cost);if(target.generated()) consumeShaft(player);}
        level.playSound(null,first,SoundEvents.CHAIN_PLACE,SoundSource.BLOCKS,.7f,1);
        return InteractionResult.SUCCESS;
    }
}
