package com.sirjohnty.bitsnbelts.client;

import com.sirjohnty.bitsnbelts.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;

/** Target both visible runs even when the lower run extends below a span's block. */
public final class BeltPicking {
    private BeltPicking() {}

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
            for(int i=0;i<BeltPath.SAMPLES;i++) {
                Vec3 a=path.samples[i],b=path.samples[i+1];
                Vec3 tangent=b.subtract(a).normalize();
                Vec3 side=new Vec3(tangent.z,0,-tangent.x).normalize();
                if(side.lengthSqr()<.5) continue;
                Vec3 normal=tangent.cross(side).normalize();
                Vec3 localEye=local(eye.subtract(a),side,normal,tangent);
                Vec3 localEnd=local(end.subtract(a),side,normal,tangent);
                // Two separate strips: the opening between the runs stays untargetable.
                for(double top:new double[]{0,-.5}) {
                    var hit=new AABB(-7.0/16,top-.125,0,7.0/16,top,a.distanceTo(b))
                        .inflate(.003).clip(localEye,localEnd);
                    if(hit.isEmpty()) continue;
                    Vec3 p=hit.get();
                    Vec3 world=a.add(side.scale(p.x)).add(normal.scale(p.y)).add(tangent.scale(p.z));
                    double distance=eye.distanceTo(world);
                    if(distance>=limit-1e-6) continue;
                    // Route normal block interactions to a real span belonging to this belt.
                    BlockPos target=entry.getValue().stream()
                        .min(Comparator.comparingDouble(pos->Vec3.atCenterOf(pos).distanceToSqr(world))).orElseThrow();
                    Vec3 facing=eye.subtract(world);
                    result=new BlockHitResult(world,Direction.getNearest(facing.x,facing.y,facing.z),target,false);
                    limit=distance;
                }
            }
        }
        return result;
    }

    private static Vec3 local(Vec3 p,Vec3 side,Vec3 normal,Vec3 tangent) {
        return new Vec3(p.dot(side),p.dot(normal),p.dot(tangent));
    }
}
