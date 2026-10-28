package com.sirjohnty.bitsnbelts;

/** Smooth vertical adjustment around a selected point, with fixed pulley heights. */
public final class BeltElevation {
    private BeltElevation() {}
    public record Control(double center,double height,double x,double z) {
        public Control(double center,double height) { this(center,height,0,0); }
    }

    /** Arguments are normalised distances along the belt, and a height in blocks. */
    public static double offset(double position, double selected, double height) {
        if (!Double.isFinite(position) || !Double.isFinite(selected) || !Double.isFinite(height)) return 0;
        double t=Math.clamp(position,0,1), center=Math.clamp(selected,0,1);
        if(center<=0 || center>=1 || t<=0 || t>=1) return 0;
        // Smoothstep independently on both sides: exact selected height, fixed ends,
        // and zero gradient where the adjustment meets either pulley.
        double ratio=t<center?t/center:(1-t)/(1-center);
        return height*ratio*ratio*(3-2*ratio);
    }
}
