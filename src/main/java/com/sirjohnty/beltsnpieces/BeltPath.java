package com.sirjohnty.beltsnpieces;

import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ChunkPos;

/** Shared, deterministic cubic curve with an arc-length lookup for even item speed. */
public final class BeltPath {
    public static final int SAMPLES = 192;
    public static final double SURFACE_HEIGHT = 13.0 / 16;
    public final Vec3 start, end, control1, control2;
    public final Direction startDirection, endDirection;
    public final int curveStyle;
    public final int pathRevision;
    public final double elevationCenter, elevationHeight;
    public final java.util.List<BeltElevation.Control> elevationEdits;
    private final Vec3[] straightPoints;
    private final double[] straightDistances;
    public final Vec3[] samples = new Vec3[SAMPLES + 1];
    public final double[] distances = new double[SAMPLES + 1];
    public final double length;
    public final AABB bounds;
    private BeltSurface surface;
    private boolean placementGeometryChecked;
    private String placementGeometryError;
    /** Geometry is immutable; world clearance remains a separate live check. */
    public String placementGeometryError() {
        if(placementGeometryChecked) return placementGeometryError;
        placementGeometryChecked=true;
        for(int i=0;i<SAMPLES;i++) {
            if(i>0 && samples[i].distanceToSqr(samples[i-1])<1e-8)
                return placementGeometryError="This curve folds back on itself.";
            for(int j=i+24;j<=SAMPLES;j++)
                if(distances[j]-distances[i]>.9 && samples[i].distanceToSqr(samples[j])<.36)
                    return placementGeometryError="Curve is too tight or overlaps itself.";
        }
        return null;
    }
    private long[] routeChunks;
    /** Immutable geometry owns the cache; reshaping naturally replaces it. */
    public long[] routeChunks() {
        if(routeChunks==null) {
            var chunks=new java.util.HashSet<Long>();
            chunks.add(ChunkPos.asLong(BlockPos.containing(start)));
            chunks.add(ChunkPos.asLong(BlockPos.containing(end)));
            for(Vec3 sample:samples) chunks.add(ChunkPos.asLong(BlockPos.containing(sample)));
            for(BlockPos pos:surface().cells.keySet()) chunks.add(ChunkPos.asLong(pos));
            routeChunks=chunks.stream().mapToLong(Long::longValue).toArray();
        }
        return routeChunks;
    }
    public BeltSurface surface() {
        if (surface==null) surface=new BeltSurface(this);
        return surface;
    }

    public BeltPath(BlockPos a, BlockPos b, Direction da, Direction db) {
        this(a,b,da,db,SURFACE_HEIGHT-.5,0,1);
    }
    public BeltPath(BlockPos a,BlockPos b,Direction da,Direction db,int style) {
        this(a,b,da,db,SURFACE_HEIGHT-.5,style,3);
    }
    public BeltPath(BlockPos a,BlockPos b,Direction da,Direction db,int style,int revision) {
        this(a,b,da,db,SURFACE_HEIGHT-.5,style,revision);
    }
    public BeltPath(BlockPos a,BlockPos b,Direction da,Direction db,int style,int revision,double center,double height) {
        this(a,b,da,db,style,revision,center,height,java.util.List.of());
    }
    public BeltPath(BlockPos a,BlockPos b,Direction da,Direction db,int style,int revision,double center,double height,java.util.List<BeltElevation.Control> edits) {
        this(a,b,da,db,SURFACE_HEIGHT-.5,style,revision,center,height,edits);
    }
    // Legacy offset is retained only to locate saved pre-0.1.6 surface cells during migration.
    BeltPath(BlockPos a, BlockPos b, Direction da, Direction db,double offset) {
        this(a,b,da,db,offset,0,0);
    }
    private BeltPath(BlockPos a, BlockPos b, Direction da, Direction db,double offset,int style,int revision) {
        this(a,b,da,db,offset,style,revision,.5,0);
    }
    private BeltPath(BlockPos a, BlockPos b, Direction da, Direction db,double offset,int style,int revision,double center,double height) {
        this(a,b,da,db,offset,style,revision,center,height,java.util.List.of());
    }
    private BeltPath(BlockPos a, BlockPos b, Direction da, Direction db,double offset,int style,int revision,double center,double height,java.util.List<BeltElevation.Control> edits) {
        start = Vec3.atCenterOf(a).add(0, offset, 0);
        end = Vec3.atCenterOf(b).add(0, offset, 0);
        startDirection = da; endDirection = db;
        curveStyle=Math.clamp(style,0,3);
        pathRevision=Math.clamp(revision,0,3);
        elevationCenter=Double.isFinite(center)?Math.clamp(center,0,1):.5;
        elevationHeight=Double.isFinite(height)?height:0;
        elevationEdits=java.util.List.copyOf(edits);
        double factor=curveStyle==1?.25:curveStyle==2?.60:.42;
        Vec3 delta=end.subtract(start), exit=Vec3.atLowerCornerOf(db.getNormal()),forward=Vec3.atLowerCornerOf(da.getNormal());
        double lateralDistance=Math.abs(delta.x*forward.z-delta.z*forward.x);
        boolean uTurn=pathRevision>=2 && da==db.getOpposite() && lateralDistance>1e-6;
        double handles = Math.max(.6, Math.min(12, start.distanceTo(end) * factor));
        double firstHandle=handles,secondHandle=handles;
        if(uTurn) {
            double reach=Math.max(1.5,Math.min(12,lateralDistance*(curveStyle==1?.55:curveStyle==2?1.15:curveStyle==3?.75:.8)));
            double longitudinal=delta.dot(forward);
            firstHandle=reach+Math.max(0,longitudinal);
            secondHandle=reach+Math.max(0,-longitudinal);
        }
        control1 = start.add(forward.scale(firstHandle));
        control2 = end.subtract(exit.scale(secondHandle));
        boolean wrap=delta.dot(exit)<-1e-7;
        if(uTurn) {
            straightPoints=null;straightDistances=null;
        } else if(wrap) {
            Vec3 lateral=new Vec3(-forward.z,0,forward.x);
            double side=delta.dot(lateral)>=0?1:-1;
            lateral=lateral.scale(side);
            boolean smoothWrap=pathRevision>=3 && curveStyle!=3;
            double width=smoothWrap?(curveStyle==1?3.0:curveStyle==2?5.0:4.0):(curveStyle==1?2.0:curveStyle==2?3.25:2.5);
            double lane=Math.max(start.dot(lateral),end.dot(lateral))+width;
            double lead=smoothWrap?Math.max(2.5,Math.min(5,start.distanceTo(end)*.45)):Math.max(1.5,Math.min(3,start.distanceTo(end)*.25));
            Vec3 begin=start.add(forward.scale(lead));
            Vec3 finish=end.subtract(exit.scale(lead));
            Vec3 a1=begin.add(lateral.scale(lane-begin.dot(lateral)));
            Vec3 a2=finish.add(lateral.scale(lane-finish.dot(lateral)));
            Vec3[] corners={start,begin,a1,a2,finish,end};
            java.util.ArrayList<Vec3> points=new java.util.ArrayList<>();
            points.add(start);
            double radius=smoothWrap?(curveStyle==1?2.0:curveStyle==2?4.0:3.0)
                :curveStyle==3?(pathRevision==0?0:.75):curveStyle==1?.45:curveStyle==2?1.1:.75;
            for(int i=1;i<corners.length-1;i++) {
                Vec3 previous=corners[i-1],corner=corners[i],next=corners[i+1];
                double before=corner.distanceTo(previous),after=corner.distanceTo(next);
                double cut=Math.min(radius,Math.min(before,after)*.45);
                if(cut<1e-7) {points.add(corner);continue;}
                Vec3 entry=corner.lerp(previous,cut/before),leave=corner.lerp(next,cut/after);
                points.add(entry);
                for(int j=1;j<=5;j++) {double u=j/5.0;points.add(entry.scale((1-u)*(1-u)).add(corner.scale(2*u*(1-u))).add(leave.scale(u*u)));}
            }
            points.add(end);
            straightPoints=points.toArray(Vec3[]::new);
            straightDistances=new double[straightPoints.length];
            for(int i=1;i<straightPoints.length;i++) straightDistances[i]=straightDistances[i-1]+straightPoints[i].distanceTo(straightPoints[i-1]);
        } else if(curveStyle==3) {
            // Route along the first shaft's direction, then turn only as needed
            // to reach the second shaft in its required direction.
            Vec3 corner1,corner2;
            if(da.getAxis()!=db.getAxis()) {
                corner1=da.getAxis()==Direction.Axis.X?new Vec3(end.x,start.y,start.z):new Vec3(start.x,start.y,end.z);
                corner2=corner1;
            } else if(da.getAxis()==Direction.Axis.X) {
                double mid=(start.x+end.x)*.5;
                corner1=new Vec3(mid,start.y,start.z);corner2=new Vec3(mid,start.y,end.z);
            } else {
                double mid=(start.z+end.z)*.5;
                corner1=new Vec3(start.x,start.y,mid);corner2=new Vec3(end.x,start.y,mid);
            }
            Vec3[] raw={start,corner1,corner2,new Vec3(end.x,start.y,end.z)};
            straightPoints=pathRevision==0?raw:roundedCorners(raw,.75);
            straightDistances=new double[straightPoints.length];
            for(int i=1;i<straightPoints.length;i++) straightDistances[i]=straightDistances[i-1]+straightPoints[i].distanceTo(straightPoints[i-1]);
        } else { straightPoints=null;straightDistances=null; }
        AABB box = new AABB(start, end);
        for (int i = 0; i <= SAMPLES; i++) {
            samples[i] = point((double)i / SAMPLES);
            if (i > 0) distances[i] = distances[i-1] + samples[i].distanceTo(samples[i-1]);
            box = box.minmax(new AABB(samples[i], samples[i]));
        }
        length = distances[SAMPLES]; bounds = box.inflate(.65);
    }
    private static Vec3[] roundedCorners(Vec3[] raw,double radius) {
        java.util.ArrayList<Vec3> corners=new java.util.ArrayList<>();
        for(Vec3 point:raw) if(corners.isEmpty()||point.distanceToSqr(corners.getLast())>1e-10) corners.add(point);
        if(corners.size()<3) return corners.toArray(Vec3[]::new);
        java.util.ArrayList<Vec3> points=new java.util.ArrayList<>();
        points.add(corners.getFirst());
        for(int i=1;i<corners.size()-1;i++) {
            Vec3 previous=corners.get(i-1),corner=corners.get(i),next=corners.get(i+1);
            Vec3 in=corner.subtract(previous),out=next.subtract(corner);
            double before=in.length(),after=out.length();
            if(in.normalize().dot(out.normalize())>.9999) {points.add(corner);continue;}
            double cut=Math.min(radius,Math.min(before,after)*.45);
            Vec3 entry=corner.lerp(previous,cut/before),leave=corner.lerp(next,cut/after);
            points.add(entry);
            for(int j=1;j<=8;j++) {double u=j/8.0;points.add(entry.scale((1-u)*(1-u)).add(corner.scale(2*u*(1-u))).add(leave.scale(u*u)));}
        }
        points.add(corners.getLast());
        return points.toArray(Vec3[]::new);
    }
    public static Direction tangent(Direction.Axis axis, Vec3 displacement, boolean flip) {
        Direction d = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        if (displacement.dot(Vec3.atLowerCornerOf(d.getNormal())) < 0) d = d.getOpposite();
        return flip ? d.getOpposite() : d;
    }
    public Vec3 point(double t) {
        double y=BeltElevation.offset(t,elevationCenter,elevationHeight);
        double x=0,z=0;
        for(var edit:elevationEdits) {
            y+=BeltElevation.offset(t,edit.center(),edit.height());
            x+=BeltElevation.offset(t,edit.center(),edit.x());
            z+=BeltElevation.offset(t,edit.center(),edit.z());
        }
        return basePoint(t).add(x,y,z);
    }
    private Vec3 basePoint(double t) {
        if(straightPoints!=null) {
            double progress=Math.clamp(t,0,1)*straightDistances[straightDistances.length-1];
            for(int i=1;i<straightPoints.length;i++) {
                double span=straightDistances[i]-straightDistances[i-1];
                if(span>1e-9 && progress<=straightDistances[i]) {
                    Vec3 p=straightPoints[i-1].lerp(straightPoints[i],(progress-straightDistances[i-1])/span);
                    return new Vec3(p.x,start.y+(end.y-start.y)*Math.clamp(t,0,1),p.z);
                }
            }
            return end;
        }
        double u = 1-t;
        return start.scale(u*u*u).add(control1.scale(3*u*u*t)).add(control2.scale(3*u*t*t)).add(end.scale(t*t*t));
    }
    public Vec3 atDistance(double distance) {
        double d = Math.clamp(distance, 0, length);
        int lo = 0, hi = SAMPLES;
        while (lo+1 < hi) { int mid=(lo+hi)/2; if (distances[mid] < d) lo=mid; else hi=mid; }
        double span=distances[hi]-distances[lo];
        return samples[lo].lerp(samples[hi], span < 1e-9 ? 0 : (d-distances[lo])/span);
    }
    public double parameterAtDistance(double distance) {
        double d=Math.clamp(distance,0,length);int lo=0,hi=SAMPLES;
        while(lo+1<hi) {int mid=(lo+hi)/2;if(distances[mid]<d) lo=mid;else hi=mid;}
        double span=distances[hi]-distances[lo];
        return (lo+(span<1e-9?0:(d-distances[lo])/span))/SAMPLES;
    }
    /** Keep the pulley and the next block of belt clear of wrench grab points. */
    public boolean canGrab(double parameter) {
        if(!Double.isFinite(parameter)||parameter<=.02||parameter>=.98) return false;
        double index=parameter*SAMPLES; int lo=Math.min(SAMPLES-1,(int)index);
        double along=distances[lo]+(distances[lo+1]-distances[lo])*(index-lo);
        return along>=1.5 && length-along>=1.5;
    }
    public double nearestDistance(Vec3 p) {
        double best=Double.MAX_VALUE, along=0;
        for(int i=0; i<SAMPLES; i++) {
            Vec3 ab=samples[i+1].subtract(samples[i]); double q=ab.lengthSqr();
            double f=q < 1e-12 ? 0 : Math.clamp(p.subtract(samples[i]).dot(ab)/q,0,1);
            double ds=p.distanceToSqr(samples[i].add(ab.scale(f)));
            if(ds<best) {best=ds; along=distances[i]+f*(distances[i+1]-distances[i]);}
        }
        return along;
    }
    /** Ratio that keeps both pulley top surfaces moving along the same curve. */
    public static int driveSign(Direction.Axis axis, Direction tangent) {
        Vec3 axle = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE).getNormal());
        return axle.cross(new Vec3(0,1,0)).dot(Vec3.atLowerCornerOf(tangent.getNormal())) >= 0 ? 1 : -1;
    }
}
