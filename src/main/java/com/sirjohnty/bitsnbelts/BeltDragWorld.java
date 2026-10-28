package com.sirjohnty.bitsnbelts;

import net.minecraft.world.phys.Vec3;

/** World-space projection and height snapping for a wrench drag. */
public final class BeltDragWorld {
    private BeltDragWorld() {}
    public static double rayHeight(Vec3 eye,Vec3 look,Vec3 planePoint,Vec3 planeNormal) {
        double denominator=look.dot(planeNormal);
        if(denominator<1e-4) return Double.NaN;
        double distance=planePoint.subtract(eye).dot(planeNormal)/denominator;
        if(distance<0 || distance>128) return Double.NaN;
        return eye.y+look.y*distance;
    }
    public static double snappedDelta(double anchorHeight,double baselineRayHeight,double rayHeight) {
        if(!Double.isFinite(anchorHeight)||!Double.isFinite(baselineRayHeight)||!Double.isFinite(rayHeight)) return Double.NaN;
        double movement=rayHeight-baselineRayHeight;
        if(Math.abs(movement)<1e-4) return 0;
        long lower=(long)Math.ceil((anchorHeight-4)*16),upper=(long)Math.floor((anchorHeight+4)*16);
        long target=Math.clamp(Math.round((anchorHeight+movement)*16),lower,upper);
        return target/16.0-anchorHeight;
    }
}
