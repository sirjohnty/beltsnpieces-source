import com.sirjohnty.beltsnpieces.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import java.util.*;

class CasingGeometryCheck {
    static Vec3 normal(BeltPath p,double d) {
        if(d<=0||d>=p.length)return new Vec3(0,1,0);
        Vec3 t=p.atDistance(Math.min(p.length,d+.05)).subtract(p.atDistance(Math.max(0,d-.05)));
        double h=Math.hypot(t.x,t.z);return h<1e-8?new Vec3(0,1,0):new Vec3(-t.x*t.y/h,h,-t.z*t.y/h).normalize();
    }
    static Vec3[] frame(BeltPath p,int i) {
        double d=p.distances[i];boolean start=d<=p.length/2;double e=start?d:p.length-d;
        double blend=Math.clamp((e-.5)/.5,0,1);blend=blend*blend*(3-2*blend);
        Vec3 f=Vec3.atLowerCornerOf((start?p.startDirection:p.endDirection).getNormal());
        Vec3 anchor=p.samples[start?0:BeltPath.SAMPLES].add(f.scale(start?e:-e));
        Vec3 t=i==0?Vec3.atLowerCornerOf(p.startDirection.getNormal()):i==BeltPath.SAMPLES?Vec3.atLowerCornerOf(p.endDirection.getNormal()):p.samples[Math.min(BeltPath.SAMPLES,i+2)].subtract(p.samples[Math.max(0,i-2)]);
        Vec3 side=new Vec3(t.z,0,-t.x).normalize().scale(7.0/16);
        return new Vec3[]{anchor.lerp(p.samples[i],blend),new Vec3(f.z,0,-f.x).scale(7.0/16).lerp(side,blend),new Vec3(0,1,0).lerp(normal(p,d),blend)};
    }
    static double scale(BeltPath p) {
        double scale=1;
        for(int i=2;i<=BeltPath.SAMPLES;i+=2){var a=frame(p,i-2);var b=frame(p,i);Vec3 along=p.samples[i].subtract(p.samples[i-2]);
            double shift=b[2].subtract(a[2]).scale(13.0/16).dot(along);
            if(shift<=1e-12)continue;
            for(int side:new int[]{-1,1}){double advance=b[0].add(b[1].scale(side*8.0/7)).subtract(a[0].add(a[1].scale(side*8.0/7))).dot(along);scale=Math.min(scale,Math.max(0,advance*.85/shift));}
        }
        return scale;
    }
    static double min(BeltPath p,double scale) {
        double min=Double.POSITIVE_INFINITY;
        for(int i=2;i<=BeltPath.SAMPLES;i+=2){var a=frame(p,i-2);var b=frame(p,i);Vec3 along=p.samples[i].subtract(p.samples[i-2]);
            for(int side:new int[]{-1,1}){Vec3 na=new Vec3(0,1,0).lerp(a[2],scale),nb=new Vec3(0,1,0).lerp(b[2],scale);Vec3 va=a[0].add(a[1].scale(side*8.0/7)).subtract(na.scale(13.0/16)),vb=b[0].add(b[1].scale(side*8.0/7)).subtract(nb.scale(13.0/16));min=Math.min(min,vb.subtract(va).dot(along));}
        }
        return min;
    }
    static Vec3[] safeFrame(BeltPath p,int i) {
        Vec3 t=i==0?Vec3.atLowerCornerOf(p.startDirection.getNormal()):i==BeltPath.SAMPLES?Vec3.atLowerCornerOf(p.endDirection.getNormal()):p.samples[Math.min(BeltPath.SAMPLES,i+2)].subtract(p.samples[Math.max(0,i-2)]);
        Vec3 side=new Vec3(t.z,0,-t.x).normalize().scale(7.0/16);
        return com.sirjohnty.beltsnpieces.client.BeltCasingGeometry.frame(p,Vec3.ZERO,p.distances[i],p.samples[i],side,normal(p,p.distances[i]));
    }
    static void check(BeltPath p) {
        for(int stride:new int[]{2,4,8,16,32})for(int i=stride;i<=BeltPath.SAMPLES;i+=stride){var a=safeFrame(p,i-stride);var b=safeFrame(p,i);Vec3 along=p.samples[i].subtract(p.samples[i-stride]);
            for(int side:new int[]{-1,1}) for(double depth:new double[]{2.0/16,13.0/16}) {
                Vec3 va=a[0].add(a[1].scale(side*8.0/7)).subtract(a[2].scale(depth));
                Vec3 vb=b[0].add(b[1].scale(side*8.0/7)).subtract(b[2].scale(depth));
                if(vb.subtract(va).dot(along)<-1e-8)throw new AssertionError("Fold: "+p.start+" -> "+p.end+" style="+p.curveStyle+" stride="+stride+" dot="+vb.subtract(va).dot(along));
            }
        }
        var first=safeFrame(p,0);var last=safeFrame(p,BeltPath.SAMPLES);
        if(first[2].distanceTo(new Vec3(0,1,0))>1e-10||last[2].distanceTo(new Vec3(0,1,0))>1e-10)throw new AssertionError("Pulley join changed");
    }
    public static void main(String[] args) {
        var regression=new BeltPath(new BlockPos(94,56,31),new BlockPos(92,58,31),Direction.WEST,Direction.WEST,0);
        if(Math.abs(regression.length-3.0425006290989844)>1e-9)throw new AssertionError("Runtime fixture differs");
        if(min(regression,1)>=0)throw new AssertionError("Regression no longer reproduces old fold");
        double fixed=com.sirjohnty.beltsnpieces.client.BeltCasingGeometry.deformation(regression);check(regression);
        System.out.printf(Locale.ROOT,"Captured runtime belt: old minimum=%g, corrected minimum=%g, deformation=%.6f%n",min(regression,1),min(regression,fixed),fixed);
        int checked=0;
        for(Direction direction:Direction.Plane.HORIZONTAL)for(int length:new int[]{2,3,4,6,8,12})for(int height:new int[]{-6,-4,-2,-1,0,1,2,4,6})for(int style=0;style<4;style++) {
            BlockPos end=new BlockPos(direction.getStepX()*length,height,direction.getStepZ()*length);
            var p=new BeltPath(BlockPos.ZERO,end,direction,direction,style);check(p);checked++;
            if(height==0 && com.sirjohnty.beltsnpieces.client.BeltCasingGeometry.deformation(p)!=1)throw new AssertionError("Flat casing altered");
        }
        for(int style=0;style<4;style++)for(int height:new int[]{-2,0,2}) {
            var p=new BeltPath(BlockPos.ZERO,new BlockPos(6,height,6),Direction.EAST,Direction.SOUTH,style);check(p);checked++;
        }
        System.out.println("Passed "+checked+" routes across all horizontal headings, all curve styles, rising/falling/flat paths, and five sampling densities.");
    }
}