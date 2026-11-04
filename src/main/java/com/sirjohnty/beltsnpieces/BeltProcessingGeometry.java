package com.sirjohnty.beltsnpieces;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** A machine's standard belt-height working space, independent of machine or recipe type. */
public final class BeltProcessingGeometry {
    public record Station(double distance,Vec3 position) {}
    private static final Map<BeltPath,Map<BlockPos,Optional<Station>>> CACHE=
        Collections.synchronizedMap(new WeakHashMap<>());
    private BeltProcessingGeometry() {}

    public static Station station(BeltPath path,BlockPos pos) {
        synchronized(CACHE) {
            return CACHE.computeIfAbsent(path,key->new HashMap<>())
                .computeIfAbsent(pos.immutable(),key->Optional.ofNullable(find(path,key))).orElse(null);
        }
    }

    private static Station find(BeltPath path,BlockPos pos) {
        Vec3 target=Vec3.atLowerCornerOf(pos).add(.5,BeltPath.SURFACE_HEIGHT,.5);
        double distance=path.nearestDistance(target);
        Vec3 point=path.atDistance(distance);
        if(!BlockPos.containing(point).equals(pos) || Math.abs(point.y-target.y)>1.0/16
            || Math.hypot(point.x-target.x,point.z-target.z)>.25) return null;
        // A locally level quarter-block working area keeps the machine head on the upper tread.
        for(double offset:new double[]{-.25,-.125,.125,.25})
            if(Math.abs(path.atDistance(distance+offset).y-point.y)>1.0/64) return null;
        // A loop passing through the working space twice has no unambiguous processing station.
        for(int i=0;i<=BeltPath.SAMPLES;i++) {
            Vec3 other=path.samples[i];
            if(Math.abs(path.distances[i]-distance)>1 && Math.abs(other.y-target.y)<=1.0/16
                && Math.hypot(other.x-target.x,other.z-target.z)<=.25) return null;
        }
        return new Station(distance,point);
    }

    /** Include every traversed sample cell, so fast items cannot skip a machine. */
    static Set<BlockPos> traversed(BeltPath path,double from,double to) {
        int first=Math.max(0,(int)Math.floor(path.parameterAtDistance(Math.min(from,to))*BeltPath.SAMPLES)-1);
        int last=Math.min(BeltPath.SAMPLES,(int)Math.ceil(path.parameterAtDistance(Math.max(from,to))*BeltPath.SAMPLES)+1);
        Set<BlockPos> positions=new HashSet<>();
        for(int i=first;i<=last;i++) positions.add(BlockPos.containing(path.samples[i]));
        return positions;
    }
}
