package com.sirjohnty.beltsnpieces;

/** Prevent a missing front item from being treated as an obstruction at either endpoint. */
public final class BeltProcessingQueueCheck {
    private BeltProcessingQueueCheck() {}
    public static void run() {
        if(BeltCargoMotion.advance(6,.133,Double.POSITIVE_INFINITY,6)<=6)
            throw new AssertionError("Unobstructed endpoint remainder must re-enter processing");
        if(BeltCargoMotion.advance(0,-.133,Double.NEGATIVE_INFINITY,6)>=0)
            throw new AssertionError("Reverse endpoint remainder must re-enter processing");
        for(double direction:new double[]{-1,1}) {
            if(BeltCargoMotion.advance(3,direction*.133,3,6)!=3)
                throw new AssertionError("Remainder must wait while its product still occupies the station");
        }
    }
}
