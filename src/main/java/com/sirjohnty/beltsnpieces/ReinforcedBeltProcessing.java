package com.sirjohnty.beltsnpieces;

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.sirjohnty.beltsnpieces.ReinforcedPulleyBlockEntity.Cargo;

/** Native Create processing on connected pulleys and geometrically eligible belt spans. */
final class ReinforcedBeltProcessing {
    private static final double EPSILON=1e-6;
    private static final Map<Cargo,State> STATES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class State {
        TransportedItemStack item;
        BlockPos stationPos;
        boolean held;
        State(Cargo cargo) {item=new TransportedItemStack(cargo.stack);item.angle=cargo.angle;}
    }
    private static final class Remainders {
        static final Set<TransportedItemStack> ITEMS=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    }
    private ReinforcedBeltProcessing() {}

    static TransportedItemStackHandlerBehaviour handler(SmartBlockEntity belt) {
        return new TransportedItemStackHandlerBehaviour(belt,(radius,callback)->process(belt,radius,callback))
            .withStackPlacement(item->{
                var owner=owner(belt);
                var station=owner==null?null:station(owner,belt);
                return station==null?Vec3.atLowerCornerOf(belt.getBlockPos()).add(.5,BeltPath.SURFACE_HEIGHT,.5):station.position();
            });
    }

    private static State state(Cargo cargo) {
        State state=STATES.computeIfAbsent(cargo,State::new);
        state.item.stack=cargo.stack;
        state.item.beltPosition=state.item.prevBeltPosition=.5f;
        return state;
    }

    private static boolean centered(SmartBlockEntity belt,Cargo cargo,double radius) {
        var owner=owner(belt);
        if(owner==null || owner.path()==null || cargo.stack.isEmpty() || radius<=0) return false;
        var station=station(owner,belt);
        if(station==null) return false;
        double center=station.distance();
        return Math.abs(cargo.distance-center)<=EPSILON && Math.abs(cargo.sideOffset)<=EPSILON;
    }

    private static void process(SmartBlockEntity belt,float radius,
            Function<TransportedItemStack,TransportedResult> callback) {
        var owner=owner(belt);
        if(owner==null || owner.getLevel()==null || owner.getLevel().isClientSide || !owner.routeLoaded()) return;
        for(Cargo cargo:List.copyOf(owner.cargo)) {
            if(!centered(belt,cargo,radius)) continue;
            State state=state(cargo);
            ItemStack before=cargo.stack.copy();
            var result=callback.apply(state.item);
            cargo.stack=state.item.stack;
            if(result!=null && !result.doesNothing() && !result.didntChangeFrom(before)) {
                List<TransportedItemStack> outputs=new ArrayList<>();
                boolean hasHeld=result.hasHeldOutput() && !result.getHeldOutput().stack.isEmpty();
                if(hasHeld) outputs.add(result.getHeldOutput());
                outputs.addAll(result.getOutputs().stream().filter(item->!item.stack.isEmpty()).toList());
                boolean first=true;
                for(var output:outputs) {
                    Cargo target=first?cargo:new Cargo(output.stack,cargo.distance,output.angle);
                    State targetState=first?state:new State(target);
                    target.stack=output.stack;
                    target.angle=output.angle;
                    target.referenceHeading=ReinforcedPulleyBlockEntity.beltHeading(owner.path(),target.distance);
                    target.sideOffset=0;
                    targetState.item=output;
                    targetState.stationPos=belt.getBlockPos().immutable();
                    targetState.held=output.locked;
                    BeltProcessingQueue.remainder(target,first && hasHeld);
                    if(first && hasHeld) Remainders.ITEMS.add(output);
                    STATES.put(target,targetState);
                    if(!first) owner.cargo.add(target);
                    first=false;
                }
                if(first) {cargo.stack=ItemStack.EMPTY;state.held=false;BeltProcessingQueue.remainder(cargo,false);}
                owner.setChanged();owner.syncCargo();
            } else if(!ItemStack.matches(before,cargo.stack)) {owner.setChanged();owner.syncCargo();}
        }
    }

    private static ReinforcedPulleyBlockEntity owner(SmartBlockEntity belt) {
        return belt instanceof ReinforcedPulleyBlockEntity pulley?pulley.root()
            :belt instanceof ReinforcedBeltSpanBlockEntity span?span.beltController():null;
    }

    private static BeltProcessingGeometry.Station station(ReinforcedPulleyBlockEntity owner,SmartBlockEntity belt) {
        if(owner.path()==null || owner(belt)!=owner) return null;
        if(belt instanceof ReinforcedPulleyBlockEntity pulley) {
            double distance=pulley.controller?0:owner.path().length;
            return new BeltProcessingGeometry.Station(distance,owner.path().atDistance(distance));
        }
        return BeltProcessingGeometry.station(owner.path(),belt.getBlockPos());
    }

    private static SmartBlockEntity support(ReinforcedPulleyBlockEntity owner,BlockPos pos) {
        if(!owner.getLevel().hasChunkAt(pos)) return null;
        return owner.getLevel().getBlockEntity(pos) instanceof SmartBlockEntity belt && owner(belt)==owner?belt:null;
    }

    private record Candidate(SmartBlockEntity belt,BeltProcessingGeometry.Station station) {}

    /** The caller advances cargo first; a machine may hold it at its validated working position. */
    static boolean advance(ReinforcedPulleyBlockEntity owner,Cargo cargo,double speed) {
        State state=STATES.get(cargo);
        if(state!=null && !state.held && state.stationPos!=null) {
            var belt=support(owner,state.stationPos);
            var station=belt==null?null:station(owner,belt);
            if(station==null || Math.abs(cargo.previous-station.distance())>.51) state.stationPos=null;
        }
        if(state!=null && !state.held && BeltProcessingQueue.isRemainder(cargo)) {
            var belt=state.stationPos==null?null:support(owner,state.stationPos);
            var station=belt==null?null:station(owner,belt);
            var machine=station==null?null:BlockEntityBehaviour.get(owner.getLevel(),state.stationPos.above(2),BeltProcessingBehaviour.TYPE);
            if(machine==null || BeltProcessingBehaviour.isBlocked(owner.getLevel(),state.stationPos)) {
                BeltProcessingQueue.remainder(cargo,false);state.stationPos=null;return false;
            }
            if(owner.getLevel().isClientSide || speed==0 || Math.signum(speed)*(cargo.distance-station.distance())<=EPSILON) {
                cargo.distance=station.distance();return true;
            }
            BeltProcessingQueue.remainder(cargo,false);
            Remainders.ITEMS.remove(state.item);
            return invoke(owner,belt,cargo,state,machine,false);
        }
        if(state!=null && state.held && state.stationPos!=null) {
            var belt=support(owner,state.stationPos);
            var station=belt==null?null:station(owner,belt);
            if(station==null) {state.held=false;state.item.locked=false;return false;}
            cargo.distance=station.distance();
            if(owner.getLevel().isClientSide) return true;
            var machine=BlockEntityBehaviour.get(owner.getLevel(),state.stationPos.above(2),BeltProcessingBehaviour.TYPE);
            if(machine==null || BeltProcessingBehaviour.isBlocked(owner.getLevel(),state.stationPos)) {
                state.held=false;state.item.locked=false;owner.syncCargo();return false;
            }
            return invoke(owner,belt,cargo,state,machine,true);
        }
        if(speed==0 || owner.getLevel().isClientSide) return false;
        List<Candidate> candidates=new ArrayList<>();
        for(var pos:BeltProcessingGeometry.traversed(owner.path(),cargo.previous,cargo.distance)) {
            if(state!=null && pos.equals(state.stationPos)) continue;
            // Most cells have no machine: avoid geometry work and handler lookups for them.
            if(BlockEntityBehaviour.get(owner.getLevel(),pos.above(2),BeltProcessingBehaviour.TYPE)==null) continue;
            var belt=support(owner,pos);
            var station=belt==null?null:station(owner,belt);
            if(station!=null) candidates.add(new Candidate(belt,station));
        }
        candidates.sort(speed>0?Comparator.comparingDouble(candidate->candidate.station().distance())
            :Comparator.comparingDouble((Candidate candidate)->candidate.station().distance()).reversed());
        for(var candidate:candidates) {
            var belt=candidate.belt();
            var pos=belt.getBlockPos();
            double center=candidate.station().distance();
            if(speed>0 ? cargo.previous>center+EPSILON || cargo.distance<center-EPSILON
                : cargo.previous<center-EPSILON || cargo.distance>center+EPSILON) continue;
            if(BeltProcessingBehaviour.isBlocked(owner.getLevel(),pos)) continue;
            var machine=BlockEntityBehaviour.get(owner.getLevel(),pos.above(2),BeltProcessingBehaviour.TYPE);
            if(machine==null) continue;
            state=state(cargo);
            state.stationPos=pos.immutable();
            if(invoke(owner,belt,cargo,state,machine,false)) return true;
        }
        return false;
    }
    private static boolean invoke(ReinforcedPulleyBlockEntity owner,SmartBlockEntity belt,
            Cargo cargo,State state,BeltProcessingBehaviour machine,boolean held) {
        var handler=belt.getBehaviour(TransportedItemStackHandlerBehaviour.TYPE);
        if(handler==null) return false;
        var item=state.item;
        item.stack=cargo.stack;
        double next=cargo.distance;
        // Machine callbacks address this stack through its local belt handler.
        cargo.distance=station(owner,belt).distance();
        double side=cargo.sideOffset;cargo.sideOffset=0;
        var result=held?machine.handleHeldItem(item,handler):machine.handleReceivedItem(item,handler);
        if(state.item!=item) return state.held || BeltProcessingQueue.isRemainder(cargo);
        cargo.stack=item.stack;
        if(result==ProcessingResult.REMOVE) {cargo.stack=ItemStack.EMPTY;state.held=false;BeltProcessingQueue.remainder(cargo,false);}
        else state.held=result==ProcessingResult.HOLD;
        item.locked=state.held;
        if(!state.held && BeltProcessingQueue.isRemainder(cargo)) {
            owner.setChanged();owner.syncCargo();return true;
        }
        if(!state.held) {cargo.distance=next;cargo.sideOffset=side;}
        if(held!=state.held || cargo.stack.isEmpty()) {owner.setChanged();owner.syncCargo();}
        return state.held;
    }

    static void stationary(ReinforcedPulleyBlockEntity owner) {
        for(Cargo cargo:List.copyOf(owner.cargo)) advance(owner,cargo,0);
        if(owner.cargo.removeIf(cargo->cargo.stack.isEmpty())) {owner.setChanged();owner.syncCargo();}
    }

    static void write(Cargo cargo,CompoundTag tag,HolderLookup.Provider registries) {
        State state=STATES.get(cargo);
        if(state==null) return;
        state.item.stack=cargo.stack;
        tag.put("ProcessingItem",state.item.serializeNBT(registries));
        if(state.stationPos!=null) tag.putLong("ProcessingPulley",state.stationPos.asLong());
        tag.putBoolean("ProcessingHeld",state.held);
        tag.putBoolean("ProcessingRetry",Remainders.ITEMS.contains(state.item));
        tag.putBoolean("ProcessingRemainder",BeltProcessingQueue.isRemainder(cargo));
    }

    static void read(Cargo cargo,CompoundTag tag,HolderLookup.Provider registries) {
        if(!tag.contains("ProcessingItem")) return;
        State state=new State(cargo);
        state.item=TransportedItemStack.read(tag.getCompound("ProcessingItem"),registries);
        state.item.stack=cargo.stack;
        if(tag.contains("ProcessingPulley")) state.stationPos=BlockPos.of(tag.getLong("ProcessingPulley"));
        state.held=tag.getBoolean("ProcessingHeld") && state.stationPos!=null;
        if(tag.getBoolean("ProcessingRetry")) Remainders.ITEMS.add(state.item);
        BeltProcessingQueue.remainder(cargo,tag.contains("ProcessingRemainder")
            ?tag.getBoolean("ProcessingRemainder"):tag.getBoolean("ProcessingRetry"));
        if(BeltProcessingQueue.isRemainder(cargo) && !state.item.locked) state.held=false;
        STATES.put(cargo,state);
    }
}
