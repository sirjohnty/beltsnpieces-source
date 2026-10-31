package com.sirjohnty.beltsnpieces;

import net.minecraft.world.phys.Vec3;

/** Check that the visible return run does not fold back at a tight vertical bend. */
public final class BeltGeometryValidation {
    private BeltGeometryValidation() {}
    private static Vec3 surfaceNormal(BeltPath path,double distance) {
        if(distance<=0 || distance>=path.length) return new Vec3(0,1,0);
        Vec3 tangent=path.atDistance(Math.min(path.length,distance+.05)).subtract(path.atDistance(Math.max(0,distance-.05)));
        double horizontal=Math.hypot(tangent.x,tangent.z);
        if(horizontal<1e-8) return new Vec3(0,1,0);
        return new Vec3(-tangent.x*tangent.y/horizontal,horizontal,-tangent.z*tangent.y/horizontal).normalize();
    }
    public static boolean returnRunDoesNotFold(BeltPath path) {
        Vec3 previous=path.samples[0].add(surfaceNormal(path,0).scale(-.5));
        for(int i=1;i<=BeltPath.SAMPLES;i++) {
            Vec3 current=path.samples[i].add(surfaceNormal(path,path.distances[i]).scale(-.5));
            Vec3 upperDelta=path.samples[i].subtract(path.samples[i-1]);
            // A reversal means the offset curve has formed a cusp or crossed itself.
            if(upperDelta.lengthSqr()>1e-12 && current.subtract(previous).dot(upperDelta)<=0) return false;
            previous=current;
        }
        return true;
    }
}
