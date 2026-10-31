package com.sirjohnty.beltsnpieces.client;

import com.sirjohnty.beltsnpieces.*;
import net.minecraft.core.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Pure geometry/resource regressions; no game save or running client required. */
public final class ReadinessGeometryCheck {
    private static void require(boolean value,String message) {if(!value) throw new AssertionError(message);}
    private static void equal(double actual,double expected,String message) {require(Math.abs(actual-expected)<1e-9,message+": "+actual);}
    public static void main(String[] args) {
        var straight=new BeltPath(BlockPos.ZERO,new BlockPos(6,0,0),Direction.EAST,Direction.EAST,3);
        double top=straight.start.y;
        require(BeltPicking.intersect(straight,new Vec3(3,top-.06,2),new Vec3(3,top-.06,-2))!=null,"Upper run target");
        require(BeltPicking.intersect(straight,new Vec3(3,top-.56,2),new Vec3(3,top-.56,-2))!=null,"Lower run target");
        require(BeltPicking.intersect(straight,new Vec3(3,top-.3,2),new Vec3(3,top-.3,-2))==null,"Gap must stay untargetable");
        var random=new Random(5819);
        for(int style=0;style<4;style++) {
            var route=new BeltPath(BlockPos.ZERO,new BlockPos(8,3,6),Direction.EAST,Direction.SOUTH,style);
            for(int i=0;i<500;i++) {
                Vec3 eye=new Vec3(random.nextDouble()*16-4,random.nextDouble()*10-3,random.nextDouble()*14-4);
                Vec3 end=new Vec3(random.nextDouble()*16-4,random.nextDouble()*10-3,random.nextDouble()*14-4);
                Vec3 expected=referenceIntersection(route,eye,end),actual=BeltPicking.intersect(route,eye,end);
                require((expected==null)==(actual==null),"Targeting broadphase changed a hit");
                if(expected!=null) require(expected.distanceTo(actual)<1e-7,"Targeting changed nearest hit");
            }
        }
        equal(BeltDragWorld.snappedDelta(10.8125,4,4),0,"Grab must not jump");
        equal(BeltDragWorld.snappedDelta(10.8125,4,4.3),.1875,"World quarter-block snapping");
        equal(BeltDragWorld.snappedDelta(10.8125,.9375,4,4),.9375,"Rebase must not jump");
        equal(BeltDragWorld.snappedDelta(10.8125,.9375,4,4.3),1.1875,"Rebased quarter-block snapping");
        equal(BeltDragWorld.snappedDelta(10.8125,0,100),4,"Upper drag limit");
        equal(BeltDragWorld.snappedDelta(10.8125,0,-100),-4,"Lower drag limit");
        equal(BeltDragWorld.snappedDelta(10.8125,3.9,0,-100),-4,"Reverse after rebase reaches lower limit");
        equal(BeltDragWorld.snappedDelta(10.8125,-3.9,0,100),4,"Reverse after rebase reaches upper limit");
        require(Double.isNaN(BeltDragWorld.snappedDelta(Double.NaN,0,0)),"Reject NaN");
        require(Double.isNaN(BeltDragWorld.rayHeight(Vec3.ZERO,new Vec3(0,1,0),new Vec3(4,0,0),new Vec3(1,0,0))),"Parallel ray");
        require(Double.isNaN(BeltDragWorld.rayHeight(Vec3.ZERO,new Vec3(-1,0,0),new Vec3(4,0,0),new Vec3(1,0,0))),"Backward ray");
        equal(BeltDragWorld.rayHeight(new Vec3(0,2,0),new Vec3(1,.5,0),new Vec3(4,0,0),new Vec3(1,0,0)),4,"Ray projection");
        for(int[] material:new int[][]{{},{0},{0xff1020e0},{0xff303030,0xffaaaaaa}}) {
            var palette=new SteelCasingPalette(material);
            int transparent=palette.recolor(0x00123456,0xff808080);
            require((transparent>>>24)==0,"Palette must preserve transparency");
            require((palette.recolor(0xff123456,0xff808080)>>>24)==255,"Opaque fallback");
        }
        int routes=0;
        for(Direction direction:Direction.Plane.HORIZONTAL) for(int style=0;style<4;style++) for(int height:new int[]{-4,0,4}) {
            BlockPos end=new BlockPos(direction.getStepX()*12,height,direction.getStepZ()*12);
            var path=new BeltPath(new BlockPos(-17,30,-17),end.offset(-17,30,-17),direction,direction,style);
            require(Double.isFinite(path.length)&&path.length>0,"Finite route");
            Set<Long> chunks=new HashSet<>();for(long chunk:path.routeChunks()) chunks.add(chunk);
            for(Vec3 point:path.samples) require(chunks.contains(ChunkPos.asLong(BlockPos.containing(point))),"Route sample chunk omitted");
            for(BlockPos cell:path.surface().cells.keySet()) require(chunks.contains(ChunkPos.asLong(cell)),"Surface chunk omitted");
            require(chunks.size()==path.routeChunks().length,"Duplicate route chunks");
            for(var cell:path.surface().cells.values()) require(Arrays.equals(cell.save(),new BeltSurface.Cell(cell.save()).save()),"Collision save round trip");
            routes++;
        }
        System.out.println("Readiness geometry PASS: drag projection/snapping/rebase, invalid inputs, palette fallbacks, transparency, "+routes+" routes/chunk caches/collision serialization");
    }
    // Frozen pre-optimization intersection algorithm: guards broadphase/cache equivalence.
    private static Vec3 referenceIntersection(BeltPath path,Vec3 eye,Vec3 end) {
        Vec3 result=null;double limit=eye.distanceTo(end);
        for(int i=0;i<BeltPath.SAMPLES;i++) {
            Vec3 a=path.samples[i],b=path.samples[i+1],tangent=b.subtract(a).normalize();
            Vec3 side=new Vec3(tangent.z,0,-tangent.x).normalize();if(side.lengthSqr()<.5) continue;
            Vec3 normal=tangent.cross(side).normalize(),e=eye.subtract(a),f=end.subtract(a);
            Vec3 localEye=new Vec3(e.dot(side),e.dot(normal),e.dot(tangent));
            Vec3 localEnd=new Vec3(f.dot(side),f.dot(normal),f.dot(tangent));
            for(double top:new double[]{0,-.5}) {
                var hit=new net.minecraft.world.phys.AABB(-7.0/16,top-.125,0,7.0/16,top,a.distanceTo(b)).inflate(.003).clip(localEye,localEnd);
                if(hit.isEmpty()) continue;
                Vec3 p=hit.get(),world=a.add(side.scale(p.x)).add(normal.scale(p.y)).add(tangent.scale(p.z));
                double distance=eye.distanceTo(world);if(distance>=limit-1e-6) continue;
                result=world;limit=distance;
            }
        }
        return result;
    }
}
