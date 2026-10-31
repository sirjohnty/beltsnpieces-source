package com.sirjohnty.beltsnpieces;

import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Grounded entity transport, separate from inventory cargo. */
public final class BeltRiding {
    private static final String LAST_MOVED="BeltsNPiecesCarriedAt";
    private static final ResourceLocation NO_AUTO_STEP=ResourceLocation.fromNamespaceAndPath("beltsnpieces","belt_no_auto_step");
    private BeltRiding() {}

    public static Vec3 atExtendedDistance(BeltPath path,double distance) {
        if(distance<0) return path.start.add(Vec3.atLowerCornerOf(path.startDirection.getNormal()).scale(distance));
        if(distance>path.length) return path.end.add(Vec3.atLowerCornerOf(path.endDirection.getNormal()).scale(distance-path.length));
        return path.atDistance(distance);
    }
    public static double progress(BeltPath path,Vec3 feet) {
        double best=Double.MAX_VALUE,distance=0;
        for(int i=0;i<BeltPath.SAMPLES;i++) {
            Vec3 a=path.samples[i],b=path.samples[i+1];
            double dx=b.x-a.x,dz=b.z-a.z,q=dx*dx+dz*dz;
            double t=q<1e-14?0:Math.clamp(((feet.x-a.x)*dx+(feet.z-a.z)*dz)/q,0,1);
            double ex=feet.x-a.x-t*dx,ez=feet.z-a.z-t*dz,ds=ex*ex+ez*ez;
            if(ds<best) { best=ds;distance=path.distances[i]+t*(path.distances[i+1]-path.distances[i]); }
        }
        if(distance<1e-6) return Math.min(0,feet.subtract(path.start).dot(Vec3.atLowerCornerOf(path.startDirection.getNormal())));
        if(distance>path.length-1e-6) return path.length+Math.max(0,feet.subtract(path.end).dot(Vec3.atLowerCornerOf(path.endDirection.getNormal())));
        return distance;
    }
    private static Vec3 side(BeltPath path,double distance) {
        Vec3 tangent=atExtendedDistance(path,distance+.02).subtract(atExtendedDistance(path,distance-.02));
        return new Vec3(tangent.z,0,-tangent.x).normalize();
    }
    /** Draw riders towards the centre gradually, without teleporting them sideways. */
    public static Vec3 displacement(BeltPath path,Vec3 feet,double amount) {
        return displacement(path,feet,amount,.06);
    }
    public static Vec3 displacement(BeltPath path,Vec3 feet,double amount,double correctionLimit) {
        if(amount==0) return Vec3.ZERO;
        double d=progress(path,feet);
        Vec3 center=atExtendedDistance(path,d),target=atExtendedDistance(path,d+amount);
        double offset=feet.subtract(center).dot(side(path,d));
        offset-=Math.clamp(offset,-correctionLimit,correctionLimit);
        Vec3 destination=target.add(side(path,d+amount).scale(offset));
        return new Vec3(destination.x-feet.x,target.y-center.y,destination.z-feet.z);
    }
    /** Clearance for the rider's swept footprint against the belt's own rising surface. */
    public static double slopeClearance(AABB box,Vec3 movement,java.util.List<VoxelShape> beltShapes) {
        if(beltShapes.isEmpty()) return 0;
        double maximum=.75;
        AABB probe=box.expandTowards(movement.x,0,movement.z).move(0,maximum,0);
        double fall=Shapes.collide(Direction.Axis.Y,probe,beltShapes,-(maximum+.08));
        double rise=maximum+fall;
        return rise>1e-5?Math.min(maximum,rise+.002):0;
    }
    private static double risingBeltClearance(ReinforcedPulleyBlockEntity owner,LivingEntity entity,Vec3 movement,double amount) {
        BeltPath path=owner.path();double d=progress(path,entity.position());
        double ahead=Math.copySign(Math.max(.38,Math.hypot(movement.x,movement.z)),amount);
        if(atExtendedDistance(path,d+ahead).y<=atExtendedDistance(path,d).y+1e-5) return 0;
        var level=owner.getLevel();AABB box=entity.getBoundingBox();
        AABB area=box.expandTowards(movement.x,0,movement.z);
        java.util.List<VoxelShape> shapes=new java.util.ArrayList<>();
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(area.minX,box.minY-.1,area.minZ),
                BlockPos.containing(area.maxX,box.minY+.75,area.maxZ))) {
            if(!level.hasChunkAt(pos)) continue;
            var be=level.getBlockEntity(pos);
            boolean ours=be instanceof ReinforcedBeltSpanBlockEntity span&&span.belongsTo(owner.getBlockPos(),owner.peer)
                ||be instanceof ReinforcedPulleyBlockEntity pulley&&pulley.root()==owner;
            if(ours) shapes.add(level.getBlockState(pos).getCollisionShape(level,pos).move(pos.getX(),pos.getY(),pos.getZ()));
        }
        return slopeClearance(box,movement,shapes);
    }
    public static void carry(ReinforcedPulleyBlockEntity owner,double speed) {
        var level=owner.getLevel();BeltPath path=owner.path();
        for(LivingEntity entity:level.getEntitiesOfClass(LivingEntity.class,path.bounds.inflate(.5,2,.5))) {
            if(!entity.isAlive()||entity.isSpectator()||entity.isPassenger()||entity.isVehicle()
                ||entity.getDeltaMovement().y>.05||entity.isFallFlying()) continue;
            if(entity instanceof Player player) {
                // The local player's normal movement packets carry the result to the server.
                // Moving ServerPlayer as well would apply the belt twice and cause corrections.
                if(!level.isClientSide||!player.isLocalPlayer()||player.isShiftKeyDown()||player.getAbilities().flying) continue;
            } else if(level.isClientSide) continue;
            long now=level.getGameTime();
            if(entity.getPersistentData().contains(LAST_MOVED)&&entity.getPersistentData().getLong(LAST_MOVED)==now) continue;
            if(!supportedBy(owner,entity)) continue;
            entity.getPersistentData().putLong(LAST_MOVED,now);
            int steps=Math.max(1,(int)Math.min(64,Math.ceil(Math.abs(speed)/.125)));
            for(int i=0;i<steps;i++) {
                if(!supportedBy(owner,entity)) break;
                Vec3 movement=displacement(path,entity.position(),speed/steps,.06/steps);
                double lift=movement.y>1e-7?movement.y+1.0/16:0;
                lift=Math.max(lift,risingBeltClearance(owner,entity,movement,speed/steps));
                if(lift>0) entity.move(MoverType.SELF,new Vec3(0,lift,0));
                // Prevent Minecraft's automatic step-up during belt-driven movement.
                // Ordinary movement and sloped-belt lifts keep their normal behaviour.
                var step=entity.getAttribute(Attributes.STEP_HEIGHT);
                if(step!=null) step.addTransientModifier(new AttributeModifier(NO_AUTO_STEP,-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                try {
                    entity.move(MoverType.SELF,new Vec3(movement.x,0,movement.z));
                } finally {
                    if(step!=null) step.removeModifier(NO_AUTO_STEP);
                }
                entity.move(MoverType.SELF,new Vec3(0,-(lift+Math.max(0,-movement.y)+.08),0));
                if(entity.onGround()) entity.fallDistance=0;
            }
            // Normal belts and walking can leave sideways velocity behind at a handoff.
            // Remove that drift only while physically supported by this belt; retain
            // along-belt motion and vertical velocity so jumping still disengages.
            if(supportedBy(owner,entity)) {
                Vec3 sideways=side(path,progress(path,entity.position()));
                Vec3 velocity=entity.getDeltaMovement();
                entity.setDeltaMovement(velocity.subtract(sideways.scale(velocity.dot(sideways))));
            }
        }
    }
    /** A grounded rider over the ribbon can step 3/16 up onto a neighbouring casing. */
    public static boolean canBridgeSupport(BeltPath path,Vec3 feet,boolean grounded) {
        if(!grounded) return false;
        double d=progress(path,feet);
        if(d<-.5||d>path.length+.5) return false;
        Vec3 center=atExtendedDistance(path,d);
        double gap=feet.y-center.y,dx=feet.x-center.x,dz=feet.z-center.z;
        return gap>=-1e-6&&gap<=.30&&dx*dx+dz*dz<=BeltSurface.HALF_WIDTH*BeltSurface.HALF_WIDTH;
    }
    private static boolean supportedBy(ReinforcedPulleyBlockEntity owner,LivingEntity entity) {
        var level=owner.getLevel();
        // Test the block under the rider's centre, so neighbouring normal belts retain control.
        BlockPos pos=BlockPos.containing(entity.getX(),entity.getY()-.08,entity.getZ());
        if(!level.hasChunkAt(pos)) return false;
        if(com.simibubi.create.AllBlocks.BELT.has(level.getBlockState(pos))) {
            var normal=level.getBlockState(pos).getCollisionShape(level,pos).move(pos.getX(),pos.getY(),pos.getZ());
            if(Shapes.collide(Direction.Axis.Y,entity.getBoundingBox(),java.util.List.of(normal),-.08)>-.0799) return false;
        }
        var feet=entity.getBoundingBox();
        // Only widen the vertical contact check while grounded over the actual ribbon.
        // The owned collision surface must still exist below the footprint. No noclip,
        // remote path snapping or airborne attraction is used.
        double depth=canBridgeSupport(owner.path(),entity.position(),entity.onGround())?.30:.08;
        for(BlockPos support:BlockPos.betweenClosed(BlockPos.containing(feet.minX,feet.minY-depth,feet.minZ),
            BlockPos.containing(feet.maxX,feet.minY,feet.maxZ))) {
            if(!level.hasChunkAt(support)) continue;
            var be=level.getBlockEntity(support);
            boolean ours=be instanceof ReinforcedBeltSpanBlockEntity span&&span.belongsTo(owner.getBlockPos(),owner.peer)
                ||be instanceof ReinforcedPulleyBlockEntity pulley&&pulley.root()==owner;
            if(!ours) continue;
            var shape=level.getBlockState(support).getCollisionShape(level,support).move(support.getX(),support.getY(),support.getZ());
            if(Shapes.collide(Direction.Axis.Y,feet,java.util.List.of(shape),-depth)>-depth+1e-4) return true;
        }
        return false;
    }
}
