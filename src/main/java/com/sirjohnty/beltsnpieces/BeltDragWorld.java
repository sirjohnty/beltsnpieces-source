package com.sirjohnty.beltsnpieces;

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
        return snappedDelta(anchorHeight,0,baselineRayHeight,rayHeight);
    }
    /** Keep a rebase continuous; snap subsequent movement to world quarter blocks. */
    public static double snappedDelta(double anchorHeight,double baseDelta,double baselineRayHeight,double rayHeight) {
        if(!Double.isFinite(anchorHeight)||!Double.isFinite(baseDelta)
            ||!Double.isFinite(baselineRayHeight)||!Double.isFinite(rayHeight)) return Double.NaN;
        double movement=rayHeight-baselineRayHeight;
        if(Math.abs(movement)<1e-4) return Math.clamp(baseDelta,-4,4);
        return Math.clamp(Math.round((anchorHeight+baseDelta+movement)*4)/4.0-anchorHeight,-4,4);
    }
}
