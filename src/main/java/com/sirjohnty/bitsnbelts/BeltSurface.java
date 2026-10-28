package com.sirjohnty.bitsnbelts;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Local 1/16-block collision voxels for the rendered upper belt run. */
public final class BeltSurface {
    public static final double HALF_WIDTH = .34;
    public static final double THICKNESS = .14;
    public final Map<BlockPos, Cell> cells;

    public BeltSurface(BeltPath path) {
        Map<BlockPos, Cell> result = new LinkedHashMap<>();
        BlockPos first = BlockPos.containing(path.start), last = BlockPos.containing(path.end);
        for (int i = 0; i < BeltPath.SAMPLES; i++) {
            Vec3 a = path.samples[i], b = path.samples[i+1];
            double dx=b.x-a.x, dz=b.z-a.z, horizontal=dx*dx+dz*dz;
            if (horizontal < 1e-14) continue;
            int minX=(int)Math.floor((Math.min(a.x,b.x)-HALF_WIDTH)*16);
            int maxX=(int)Math.floor((Math.max(a.x,b.x)+HALF_WIDTH)*16);
            int minZ=(int)Math.floor((Math.min(a.z,b.z)-HALF_WIDTH)*16);
            int maxZ=(int)Math.floor((Math.max(a.z,b.z)+HALF_WIDTH)*16);
            for (int x=minX; x<=maxX; x++) for (int z=minZ; z<=maxZ; z++) {
                double cx=(x+.5)/16, cz=(z+.5)/16;
                double t=((cx-a.x)*dx+(cz-a.z)*dz)/horizontal;
                if (t<0 || t>1) continue;
                double ex=cx-a.x-t*dx, ez=cz-a.z-t*dz;
                if (ex*ex+ez*ez > HALF_WIDTH*HALF_WIDTH) continue;
                double top=a.y+(b.y-a.y)*t;
                int bottomY=(int)Math.floor((top-THICKNESS)*16);
                int topY=(int)Math.ceil(top*16-1e-7)-1;
                for (int y=bottomY; y<=topY; y++) {
                    BlockPos pos=new BlockPos(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16));
                    if (pos.equals(first)||pos.equals(last)) continue; // Pulley supplies its own collision.
                    result.computeIfAbsent(pos, p->new Cell()).fill(Math.floorMod(x,16),Math.floorMod(y,16),Math.floorMod(z,16));
                }
            }
        }
        cells=Collections.unmodifiableMap(result);
    }

    public static final class Cell {
        private final BitSet bits;
        private VoxelShape shape;
        private VoxelShape selection;
        public Cell() { bits=new BitSet(4096); }
        public Cell(long[] data) { bits=BitSet.valueOf(Arrays.copyOf(data,Math.min(data.length,64))); }
        private void fill(int x,int y,int z) { bits.set((x*16+y)*16+z); shape=null; }
        public long[] save() { return bits.toLongArray(); }
        /** Count horizontal 1/16-block columns, ignoring the thickness of the belt. */
        public int footprintColumns() {
            BitSet columns=new BitSet(256);
            for(int i=bits.nextSetBit(0);i>=0;i=bits.nextSetBit(i+1)) columns.set((i/256)*16+i%16);
            return columns.cardinality();
        }
        public VoxelShape selectionShape() {
            if(selection==null) {
                if(shape().isEmpty()) return Shapes.empty();
                AABB b=shape().bounds();
                // Cover the visible upper and lower runs, with forgiving lateral padding.
                selection=Shapes.box(Math.max(0,b.minX-.08),Math.max(0,b.minY-.38),Math.max(0,b.minZ-.08),
                    Math.min(1,b.maxX+.08),Math.min(1,b.maxY+.06),Math.min(1,b.maxZ+.08));
            }
            return selection;
        }
        public VoxelShape shape() {
            if (shape==null) {
                BitSetDiscreteVoxelShape discrete=new BitSetDiscreteVoxelShape(16,16,16);
                for (int i=bits.nextSetBit(0); i>=0; i=bits.nextSetBit(i+1)) discrete.fill(i/256,(i/16)%16,i%16);
                shape=new SurfaceShape(discrete).optimize();
            }
            return shape;
        }
    }
    private static final class SurfaceShape extends ArrayVoxelShape {
        private static double[] coordinates() {
            double[] values=new double[17];
            for (int i=0;i<=16;i++) values[i]=i/16.0;
            return values;
        }
        SurfaceShape(DiscreteVoxelShape discrete) { super(discrete,coordinates(),coordinates(),coordinates()); }
    }
}
