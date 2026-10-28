package com.sirjohnty.bitsnbelts;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** One metre of belt, measured along the curve rather than aligned to the world grid. */
public final class BeltSelection {
    private BeltSelection() {}
    public record Frame(Vec3 topLeft,Vec3 topRight,Vec3 bottomLeft,Vec3 bottomRight) {}
    public record Section(double start,double end) { public double center() { return (start+end)*.5; } }
    /** Equal-length draggable sections with a single central section. */
    public static Section draggableSection(BeltPath path,double distance) {
        double first=1.5,last=path.length-1.5,available=last-first;
        if(available<=1e-8 || distance<first || distance>last) return null;
        int count=Math.max(1,(int)Math.floor(available));
        if(count%2==0) count--;
        double width=available/count;
        int index=Math.min(count-1,Math.max(0,(int)((distance-first)/width)));
        return new Section(first+index*width,first+(index+1)*width);
    }
    public static List<Frame> section(BeltPath path,Vec3 hit) {
        double start=Math.floor(path.nearestDistance(hit));
        double end=Math.min(path.length,start+1);
        return frames(path,start,end);
    }
    public static List<Frame> frames(BeltPath path,double start,double end) {
        List<Frame> frames=new ArrayList<>();
        for(int i=0;i<=8;i++) {
            double d=start+(end-start)*i/8;
            Vec3 p=path.atDistance(d);
            Vec3 tangent=path.atDistance(Math.min(path.length,d+.05))
                .subtract(path.atDistance(Math.max(0,d-.05)));
            double horizontal=Math.hypot(tangent.x,tangent.z);
            Vec3 normal=(d<=0||d>=path.length||horizontal<1e-8)?new Vec3(0,1,0):
                new Vec3(-tangent.x*tangent.y/horizontal,horizontal,-tangent.z*tangent.y/horizontal).normalize();
            Vec3 side=new Vec3(tangent.z,0,-tangent.x).normalize().scale(7.0/16+.002);
            Vec3 top=normal.scale(1.0/16+.002),bottom=normal.scale(-.625-.002);
            frames.add(new Frame(p.add(side).add(top),p.subtract(side).add(top),
                p.add(side).add(bottom),p.subtract(side).add(bottom)));
        }
        return frames;
    }
}
