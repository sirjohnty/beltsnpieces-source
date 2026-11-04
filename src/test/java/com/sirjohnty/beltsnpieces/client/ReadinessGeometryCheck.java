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
        BeltProcessingQueueCheck.run();
        equal(BeltCargoMotion.advance(6,.133,6,12),6,"Overlapping outputs must wait, not reverse");
        equal(BeltCargoMotion.advance(6,-.133,6,12),6,"Reverse belt outputs must wait, not advance");
        var motionRandom=new Random(95172);
        for(int i=0;i<20000;i++) {
            double length=2+motionRandom.nextDouble()*62;
            double distance=-1+motionRandom.nextDouble()*(length+2);
            double speed=(motionRandom.nextBoolean()?1:-1)*motionRandom.nextDouble()*8;
            double leader=distance+motionRandom.nextDouble()*4-2;
            double next=BeltCargoMotion.advance(distance,speed,leader,length);
            double progress=(next-distance)*Math.signum(speed);
            require(progress>=-1e-12 && progress<=Math.abs(speed)+1e-12,"Cargo reversed or exceeded belt speed");
            require(next>=-1 && next<=length+1,"Cargo escaped transport bounds");
            equal(BeltCargoMotion.advance(distance,0,leader,length),distance,"Stopped belt moved cargo");
            double clearLeader=distance+Math.copySign(20,speed);
            equal(BeltCargoMotion.advance(distance,speed,clearLeader,length),
                Math.clamp(distance+speed,-1,length+1),"Unblocked cargo did not follow belt speed");
        }
        var straight=new BeltPath(BlockPos.ZERO,new BlockPos(6,0,0),Direction.EAST,Direction.EAST,3);
        var work=BeltProcessingGeometry.station(straight,new BlockPos(3,0,0));
        require(work!=null,"Flat span must support processing");
        equal(work.distance(),3,"Flat span processing centre");
        require(BeltProcessingGeometry.station(straight,new BlockPos(3,-1,0))==null,"Lower run must not become a processing surface");
        require(BeltProcessingGeometry.station(straight,new BlockPos(3,0,1))==null,"Sideways machine must not reach the belt");
        var slope=new BeltPath(BlockPos.ZERO,new BlockPos(6,4,0),Direction.EAST,Direction.EAST,3);
        require(BeltProcessingGeometry.station(slope,new BlockPos(3,2,0))==null,"Steep span must not process at the machine centre");
        var curve=new BeltPath(BlockPos.ZERO,new BlockPos(8,0,8),Direction.EAST,Direction.SOUTH,0);
        boolean curveStation=false;
        for(var pos:curve.surface().cells.keySet()) if(BeltProcessingGeometry.station(curve,pos)!=null) curveStation=true;
        require(curveStation,"A level curve crossing a machine working space must remain eligible");
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
            checkPathQueries(path);
            Set<Long> chunks=new HashSet<>();for(long chunk:path.routeChunks()) chunks.add(chunk);
            for(Vec3 point:path.samples) require(chunks.contains(ChunkPos.asLong(BlockPos.containing(point))),"Route sample chunk omitted");
            for(BlockPos cell:path.surface().cells.keySet()) require(chunks.contains(ChunkPos.asLong(cell)),"Surface chunk omitted");
            require(chunks.size()==path.routeChunks().length,"Duplicate route chunks");
            for(var cell:path.surface().cells.values()) require(Arrays.equals(cell.save(),new BeltSurface.Cell(cell.save()).save()),"Collision save round trip");
            routes++;
        }
        System.out.println("Readiness geometry PASS: drag projection/snapping/rebase, invalid inputs, palette fallbacks, transparency, "+routes+" routes/chunk caches/collision serialization");
    }
    private static void checkPathQueries(BeltPath path) {
        require(path.atExtendedDistance(-.5).equals(path.start.add(Vec3.atLowerCornerOf(path.startDirection.getNormal()).scale(-.5))),
            "Incoming cargo extension changed");
        require(path.atExtendedDistance(path.length+.5).equals(path.end.add(Vec3.atLowerCornerOf(path.endDirection.getNormal()).scale(.5))),
            "Outgoing cargo extension changed");
        for(int i=0;i<=BeltPath.SAMPLES;i++) {
            double d=path.distances[i];
            require(path.atDistance(d).distanceTo(path.samples[i])<1e-9,"Sample lookup changed");
            equal(path.parameterAtDistance(d),(double)i/BeltPath.SAMPLES,"Sample parameter changed");
            if(i==BeltPath.SAMPLES) continue;
            // An independent linear walk checks interpolation against the binary lookup.
            double between=(path.distances[i]+path.distances[i+1])*.5;
            int upper=1;
            while(upper<BeltPath.SAMPLES && path.distances[upper]<between) upper++;
            double span=path.distances[upper]-path.distances[upper-1];
            double fraction=span<1e-9?0:(between-path.distances[upper-1])/span;
            Vec3 expected=path.samples[upper-1].lerp(path.samples[upper],fraction);
            require(path.atDistance(between).equals(expected),"Interpolated position changed");
            equal(path.parameterAtDistance(between),(upper-1+fraction)/BeltPath.SAMPLES,"Interpolated parameter changed");
        }
        equal(path.sideAtSample(0).length(),7.0/16,"Start tread width changed");
        equal(path.sideAtSample(BeltPath.SAMPLES).length(),7.0/16,"End tread width changed");
        require(path.surfaceNormal(0).equals(new Vec3(0,1,0)),"Start pulley normal changed");
        require(path.surfaceNormal(path.length).equals(new Vec3(0,1,0)),"End pulley normal changed");
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
