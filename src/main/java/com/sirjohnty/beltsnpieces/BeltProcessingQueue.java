package com.sirjohnty.beltsnpieces;

import java.util.*;
import com.sirjohnty.beltsnpieces.ReinforcedPulleyBlockEntity.Cargo;

/** Finished products lead their held remainder, matching Create's upstream remainder ordering. */
public final class BeltProcessingQueue {
    private static final Set<Cargo> REMAINDERS=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private BeltProcessingQueue() {}

    static void remainder(Cargo cargo,boolean remaining) {
        if(remaining) REMAINDERS.add(cargo);else REMAINDERS.remove(cargo);
    }

    static boolean isRemainder(Cargo cargo) {return REMAINDERS.contains(cargo);}
    static int priority(Cargo cargo) {return isRemainder(cargo)?1:0;}

    static Comparator<Cargo> order(double speed) {
        Comparator<Cargo> position=Comparator.comparingDouble(cargo->cargo.distance);
        if(speed>0) position=position.reversed();
        return position.thenComparingInt(cargo->isRemainder(cargo)?1:0);
    }

    static double advance(Cargo cargo,double speed,double leader,double length) {
        // Native belt remainders re-enter the machine only after its finished product clears a block.
        double effectiveLeader=isRemainder(cargo)?leader-Math.copySign(.3,speed):leader;
        return BeltCargoMotion.advance(cargo.distance,speed,effectiveLeader,length);
    }
}
