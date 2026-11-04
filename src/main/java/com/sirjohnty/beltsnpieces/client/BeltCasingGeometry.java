package com.sirjohnty.beltsnpieces.client;

import com.sirjohnty.beltsnpieces.BeltPath;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps the deeper casing shell from forming a cusp inside short, steep bends. */
public final class BeltCasingGeometry {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final double DEPTH=13.0/16;
    private static final Map<BeltPath,Double> DEFORMATION=new WeakHashMap<>();
    private BeltCasingGeometry() {}

    public static Vec3[] frame(BeltPath path,Vec3 origin,double distance,Vec3 position,Vec3 side,Vec3 normal) {
        Vec3[] frame=rawFrame(path,origin,distance,position,side,normal);
        // A shallower tilt inside a tight bend preserves both the top route and
        // the horizontal pulley joins, without changing the actual conveyor.
        frame[2]=UP.lerp(frame[2],deformation(path));
        return frame;
    }

    public static double deformation(BeltPath path) {
        return DEFORMATION.computeIfAbsent(path,BeltCasingGeometry::calculateDeformation);
    }

    private static double calculateDeformation(BeltPath path) {
        double factor=1;
        Vec3[] previous=sample(path,0);
        for(int i=2;i<=BeltPath.SAMPLES;i+=2) {
            Vec3[] next=sample(path,i);
            Vec3 along=path.samples[i].subtract(path.samples[i-2]);
            double displacement=next[2].subtract(previous[2]).scale(DEPTH).dot(along);
            if(displacement>1e-12) for(int sign:new int[]{-1,1}) {
                Vec3 a=previous[0].add(previous[1].scale(sign*8.0/7));
                Vec3 b=next[0].add(next[1].scale(sign*8.0/7));
                double advance=b.subtract(a).dot(along);
                // Retain a margin for interpolated cross-sections and coarser LODs.
                factor=Math.min(factor,Math.max(0,advance*.85/displacement));
            }
            previous=next;
        }
        return factor;
    }

    private static Vec3[] sample(BeltPath path,int i) {
        Vec3 side=path.sideAtSample(i);
        double distance=path.distances[i];
        Vec3 normal=path.surfaceNormal(distance);
        return rawFrame(path,Vec3.ZERO,distance,path.samples[i],side,normal);
    }

    private static Vec3[] rawFrame(BeltPath path,Vec3 origin,double distance,Vec3 position,Vec3 side,Vec3 normal) {
        boolean start=distance<=path.length/2;
        double endDistance=start?distance:path.length-distance;
        double blend=Math.clamp((endDistance-.5)/.5,0,1);
        blend=blend*blend*(3-2*blend);
        Vec3 forward=Vec3.atLowerCornerOf((start?path.startDirection:path.endDirection).getNormal());
        Vec3 anchor=path.samples[start?0:BeltPath.SAMPLES].subtract(origin)
            .add(forward.scale(start?endDistance:-endDistance));
        Vec3 horizontalSide=new Vec3(forward.z,0,-forward.x).scale(7.0/16);
        return new Vec3[]{anchor.lerp(position,blend),horizontalSide.lerp(side,blend),UP.lerp(normal,blend)};
    }
}
