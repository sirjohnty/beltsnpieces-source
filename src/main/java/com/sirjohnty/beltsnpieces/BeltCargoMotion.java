package com.sirjohnty.beltsnpieces;

/** Shared queue policy for inserted cargo and every machine's processed outputs. */
public final class BeltCargoMotion {
    private BeltCargoMotion() {}

    public static double advance(double distance,double speed,double leader,double length) {
        double next=Math.clamp(distance+speed,-1,length+1);
        // Recipe outputs may share a centre with a held remainder or another output.
        // Wait for forward clearance; never manufacture spacing by moving backwards.
        if(speed>0) return Math.max(distance,Math.min(next,leader-.7));
        if(speed<0) return Math.min(distance,Math.max(next,leader+.7));
        return distance;
    }
}
