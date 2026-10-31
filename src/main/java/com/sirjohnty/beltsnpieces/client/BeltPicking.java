package com.sirjohnty.beltsnpieces.client;

import com.sirjohnty.beltsnpieces.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;

/** Target both visible runs even when the lower run extends below a span's block. */
public final class BeltPicking {
    private BeltPicking() {}
    private record Segment(Vec3 origin,Vec3 side,Vec3 normal,Vec3 tangent,AABB bounds,AABB upper,AABB lower) {}
    private static final Map<BeltPath,Segment[]> GEOMETRY=new WeakHashMap<>();
    private static Segment[] geometry(BeltPath path) {
        return GEOMETRY.computeIfAbsent(path,p->{
            Segment[] result=new Segment[BeltPath.SAMPLES];
            for(int i=0;i<result.length;i++) {
                Vec3 a=p.samples[i],b=p.samples[i+1],tangent=b.subtract(a).normalize();
                Vec3 side=new Vec3(tangent.z,0,-tangent.x).normalize();
                if(side.lengthSqr()<.5) continue;
                result[i]=new Segment(a,side,tangent.cross(side).normalize(),tangent,new AABB(a,b).inflate(.8),
                    new AABB(-7.0/16,-.125,0,7.0/16,0,a.distanceTo(b)).inflate(.003),
                    new AABB(-7.0/16,-.625,0,7.0/16,-.5,a.distanceTo(b)).inflate(.003));
            }
            return result;
        });
    }

    public static HitResult pick(Entity camera,double reach,float partial,HitResult original) {
        Vec3 eye=camera.getEyePosition(partial);
        double limit=reach;
        if(original.getType()!=HitResult.Type.MISS)
            limit=Math.min(limit,eye.distanceTo(original.getLocation()));
        Vec3 end=eye.add(camera.getViewVector(partial).scale(limit));
        // Span blocks occupy the upper run, which can be above the targeting ray.
        AABB search=new AABB(eye,end).inflate(1);
        Map<ReinforcedPulleyBlockEntity,List<BlockPos>> belts=new HashMap<>();
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(search.minX,search.minY,search.minZ),
                BlockPos.containing(search.maxX,search.maxY,search.maxZ))) {
            if(!camera.level().hasChunkAt(pos)) continue;
            if(!(camera.level().getBlockEntity(pos) instanceof ReinforcedBeltSpanBlockEntity span)) continue;
            var owner=span.beltController();
            if(owner!=null && owner.path()!=null)
                belts.computeIfAbsent(owner,k->new ArrayList<>()).add(pos.immutable());
        }
        HitResult result=original;
        for(var entry:belts.entrySet()) {
            BeltPath path=entry.getKey().path();
            Vec3 world=intersect(path,eye,end);
            if(world==null) continue;
            double distance=eye.distanceTo(world);
            if(distance>=limit-1e-6) continue;
            BlockPos target=entry.getValue().stream()
                .min(Comparator.comparingDouble(pos->Vec3.atCenterOf(pos).distanceToSqr(world))).orElseThrow();
            Vec3 facing=eye.subtract(world);
            result=new BlockHitResult(world,Direction.getNearest(facing.x,facing.y,facing.z),target,false);
            limit=distance;
        }
        return result;
    }

    static Vec3 intersect(BeltPath path,Vec3 eye,Vec3 end) {
            var bounds=path.bounds.inflate(.8);
            if(!bounds.contains(eye) && bounds.clip(eye,end).isEmpty()) return null;
            double limit=eye.distanceTo(end);
            Vec3 result=null;
            for(Segment segment:geometry(path)) {
                if(segment==null || (!segment.bounds.contains(eye)&&segment.bounds.clip(eye,end).isEmpty())) continue;
                Vec3 a=segment.origin,side=segment.side,normal=segment.normal,tangent=segment.tangent;
                Vec3 localEye=local(eye.subtract(a),side,normal,tangent);
                Vec3 localEnd=local(end.subtract(a),side,normal,tangent);
                // Two separate strips: the opening between the runs stays untargetable.
                for(int run=0;run<2;run++) {
                    var hit=(run==0?segment.upper:segment.lower).clip(localEye,localEnd);
                    if(hit.isEmpty()) continue;
                    Vec3 p=hit.get();
                    Vec3 world=a.add(side.scale(p.x)).add(normal.scale(p.y)).add(tangent.scale(p.z));
                    double distance=eye.distanceTo(world);
                    if(distance>=limit-1e-6) continue;
                    result=world;
                    limit=distance;
                }
            }
        return result;
    }

    private static Vec3 local(Vec3 p,Vec3 side,Vec3 normal,Vec3 tangent) {
        return new Vec3(p.dot(side),p.dot(normal),p.dot(tangent));
    }
}
