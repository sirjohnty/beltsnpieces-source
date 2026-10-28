package com.sirjohnty.bitsnbelts;

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
    public static final String SELECTION="BitsNBeltsSelection";
    public ReinforcedBeltItem(Properties p) { super(p); }
    public static BlockPos selection(ItemStack stack, Level level) {
        CompoundTag n=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!n.contains(SELECTION)) return null;
        CompoundTag s=n.getCompound(SELECTION);
        return s.getString("Dimension").equals(level.dimension().location().toString()) ? BlockPos.of(s.getLong("Pos")) : null;
    }
    public static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->n.remove(SELECTION));
    }
    public static int curveStyle(ItemStack stack) {
        CompoundTag n=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return Math.clamp(n.getCompound(SELECTION).getInt("CurveStyle"),0,3);
    }
    /** 0 is automatic; 1–4 select east, south, west and north belt approach. */
    public static int shaftAxisChoice(ItemStack stack) {
        CompoundTag n=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return Math.clamp(n.getCompound(SELECTION).getInt("EndShaftAxis"),0,4);
    }
    public static void setShaftAxisChoice(ItemStack stack,int choice) {
        if(choice<1||choice>4) return;
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->{CompoundTag selection=n.getCompound(SELECTION);selection.putInt("EndShaftAxis",choice);n.put(SELECTION,selection);});
    }
    public static String styleName(int style) {
        return switch(style) { case 1 -> "Compact"; case 2 -> "Extended"; case 3 -> "Straight"; default -> "Default"; };
    }
    public static void cycleStyle(ItemStack stack,Player player) {
        int next=(curveStyle(stack)+1)%4;
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
        return generatedTarget(level,first,clicked.relative(face),axisChoice);
    }
    public static final double AIR_REACH=5.0;
    public static Target airTarget(Level level,BlockPos first,Player player,int axisChoice) {
        return generatedTarget(level,first,BlockPos.containing(player.getEyePosition().add(player.getViewVector(1).scale(AIR_REACH))),axisChoice);
    }
    public static Target generatedTarget(Level level,BlockPos first,BlockPos pos,int axisChoice) {
        if(!level.hasChunkAt(first) || !isShaft(level.getBlockState(first))) return null;
        if(!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
        // Only replace air: this avoids deleting plants, fluids, block entities or other blocks.
        if(!level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty() || level.getBlockEntity(pos)!=null) return null;
        Direction chosen=selectedEndDirection(axisChoice);
        Direction.Axis axis=chosen==null?level.getBlockState(first).getValue(RotatedPillarKineticBlock.AXIS):chosen.getAxis()==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X;
        return new Target(pos,AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS,axis),true);
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
            if(candidate.is(BitsNBelts.REINFORCED_BELT.get())) total+=candidate.getCount();
        }
        return total;
    }
    private static void consumeBelts(Player player,int count) {
        var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize() && count>0;slot++) {
            ItemStack candidate=inventory.getItem(slot);
            if(!candidate.is(BitsNBelts.REINFORCED_BELT.get())) continue;
            int take=Math.min(count,candidate.getCount());candidate.shrink(take);count-=take;
        }
        inventory.setChanged();
    }
    public static int maxLength() { return BitsNBeltsConfig.maxReinforcedBeltLength(); }
    /** Null means valid. Used identically by the server placement and ghost preview. */
    public static String invalid(Level level,BlockPos a,BlockPos b,BeltPath path) {
        if(a.equals(b)) return "Select a different shaft.";
        if(path==null) return "Select two ordinary horizontal Create shafts.";
        if(a.distSqr(b)<4) return "Shafts must be at least 2 blocks apart.";
        if(path.length>maxLength()) return "Reinforced Belt exceeds the belt length limit ("+maxLength()+").";
        // Close/self-overlapping curves do not make viable conveyor loops.
        for(int i=0;i<BeltPath.SAMPLES;i++) {
            if(i>0 && path.samples[i].distanceToSqr(path.samples[i-1])<1e-8) return "This curve folds back on itself.";
            for(int j=i+24;j<=BeltPath.SAMPLES;j++)
                if(path.distances[j]-path.distances[i]>.9 && path.samples[i].distanceToSqr(path.samples[j])<.36)
                    return "Curve is too tight or overlaps itself.";
        }
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
        if(first==null && level.getBlockEntity(pos) instanceof ReinforcedPulleyBlockEntity pulley && pulley.peer!=null) {
            if(level.isClientSide) return InteractionResult.SUCCESS;
            var owner=pulley.root();
            if(owner==null || owner.path()==null || !hasShaft(player)) {
                player.displayClientMessage(Component.literal("A shaft and a loaded belt endpoint are required."),true);return InteractionResult.FAIL;
            }
            Direction outward=pulley.controller?owner.startDirection.getOpposite():owner.endDirection;
            BlockPos adjacent=pos.relative(outward);
            if(!level.hasChunkAt(adjacent) || level.isOutsideBuildHeight(adjacent) || !level.getWorldBorder().isWithinBounds(adjacent)
                || !level.mayInteract(player,pos) || !level.mayInteract(player,adjacent) || !player.mayUseItemAt(adjacent,outward,stack)
                || !level.getBlockState(adjacent).isAir() || !level.getFluidState(adjacent).isEmpty() || level.getBlockEntity(adjacent)!=null) {
                player.displayClientMessage(Component.literal("No clear space next to this belt endpoint."),true);return InteractionResult.FAIL;
            }
            BlockState newShaft=AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS,level.getBlockState(pos).getValue(RotatedPillarKineticBlock.AXIS));
            if(!level.isUnobstructed(newShaft,adjacent,net.minecraft.world.phys.shapes.CollisionContext.of(player)) || !level.setBlock(adjacent,newShaft,3)) {
                player.displayClientMessage(Component.literal("Could not place the adjacent shaft."),true);return InteractionResult.FAIL;
            }
            if(!player.isCreative()) consumeShaft(player);
            selectStart(stack,level,adjacent);
            level.playSound(null,adjacent,SoundEvents.CHAIN_PLACE,SoundSource.BLOCKS,.7f,1);
            player.displayClientMessage(Component.literal("Shaft placed. Choose the other end of the new belt."),true);
            return InteractionResult.SUCCESS;
        }
        if(first==null && !isShaft(level.getBlockState(pos))) {
            if(!level.isClientSide) player.displayClientMessage(Component.literal("Select an ordinary horizontal Create shaft first."),true);
            return InteractionResult.FAIL;
        }
        if(level.isClientSide) return InteractionResult.SUCCESS;
        if(first==null) {
            selectStart(stack,level,pos);
            player.displayClientMessage(Component.literal("Left-click to change the curve; sneak-use to cancel."),true);
            return InteractionResult.SUCCESS;
        }
        if(first.equals(pos)) return InteractionResult.FAIL;
        Target target=target(level,first,pos,ctx.getClickedFace(),shaftAxisChoice(stack));
        if(target==null) {player.displayClientMessage(Component.literal("No clear space for a shaft here."),true);return InteractionResult.FAIL;}
        if(target.generated()) return InteractionResult.FAIL;
        return place(level,player,stack,first,target,ctx.getClickedFace(),ctx.getClickLocation());
    }
    private static void selectStart(ItemStack stack,Level level,BlockPos pos) {
        CompoundTag pick=new CompoundTag();pick.putLong("Pos",pos.asLong());pick.putString("Dimension",level.dimension().location().toString());
        CustomData.update(DataComponents.CUSTOM_DATA,stack,n->n.put(SELECTION,pick));
    }
    public InteractionResult place(Level level,Player player,ItemStack stack,BlockPos first,Target target,Direction clickedFace,Vec3 hitLocation) {
        BlockPos pos=target.pos();
        if(!level.hasChunkAt(first) || !player.mayUseItemAt(first,clickedFace,stack) ||
            !player.mayUseItemAt(pos,clickedFace,stack) || !level.mayInteract(player,first) || !level.mayInteract(player,pos)) {
            return InteractionResult.FAIL;
        }
        if(target.generated() && !hasShaft(player)) {player.displayClientMessage(Component.literal("A shaft is required."),true);return InteractionResult.FAIL;}
        if(target.generated() && !level.isUnobstructed(target.shaft(),pos,net.minecraft.world.phys.shapes.CollisionContext.of(player))) {
            player.displayClientMessage(Component.literal("The shaft space is obstructed."),true);return InteractionResult.FAIL;
        }
        BeltPath path=pathWithTarget(level,first,pos,target.shaft(),false,target.generated()?generatedEndFlip(first,target,shaftAxisChoice(stack)):flipEnd(level,first,pos,hitLocation),curveStyle(stack)); String error=invalid(level,first,pos,path);
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
        BlockState sa=level.getBlockState(first),oldTarget=level.getBlockState(pos),sb=target.shaft();
        var newA=BitsNBelts.PULLEY.get().defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS,sa.getValue(RotatedPillarKineticBlock.AXIS));
        var newB=BitsNBelts.PULLEY.get().defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS,sb.getValue(RotatedPillarKineticBlock.AXIS));
        // No source shaft inventories are replaced; only plain shafts are accepted.
        boolean placedA=level.setBlock(first,newA,3),placedB=placedA&&level.setBlock(pos,newB,3);
        if(!placedA||!placedB||!(level.getBlockEntity(first) instanceof ReinforcedPulleyBlockEntity a)||!(level.getBlockEntity(pos) instanceof ReinforcedPulleyBlockEntity b)) {
            if(placedA) level.setBlock(first,sa,3); if(placedB) level.setBlock(pos,oldTarget,3);
            player.displayClientMessage(Component.literal("Could not place Reinforced Belt."),true);return InteractionResult.FAIL;
        }
        a.connect(b,true,path,player.isCreative()?0:cost); b.connect(a,false,path,0);
        if(!a.ensureSurface()) {
            a.paidBelts=0; // The held belt has not been consumed yet.
            a.dismantle();level.setBlock(first,sa,3);level.setBlock(pos,oldTarget,3);
            player.displayClientMessage(Component.literal("Could not create the belt surface."),true);return InteractionResult.FAIL;
        }
        clear(stack); if(!player.isCreative()) {consumeBelts(player,cost);if(target.generated()) consumeShaft(player);}
        level.playSound(null,first,SoundEvents.CHAIN_PLACE,SoundSource.BLOCKS,.7f,1);
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext ctx,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.literal("Connect two horizontal Create shafts."));
        lines.add(Component.literal("Left-click to change style; sneak-use to clear."));
        lines.add(Component.literal("Cost follows the distance between shaft positions."));
    }
}
