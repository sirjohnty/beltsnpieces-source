package com.sirjohnty.beltsnpieces.client;

import com.sirjohnty.beltsnpieces.*;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;

/** First teaching pass: scripted Ponder-only blocks, never player-world placement. */
public final class ReinforcedBeltScenes {
    private ReinforcedBeltScenes() {}

    public static void placement(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=new CreateSceneBuilder(builder);
        scene.title("reinforced_belt_placement","Placing Reinforced Belts");
        scene.configureBasePlate(0,0,9);
        scene.scaleSceneView(.65f);
        scene.setSceneOffsetY(-.5f);
        scene.showBasePlate();
        BlockPos start=util.grid().at(1,1,2),flatEnd=util.grid().at(7,1,6),end=util.grid().at(7,3,6);
        ItemStack belt=new ItemStack(BeltsNPieces.REINFORCED_BELT.get());
        Vec3 first=Vec3.atCenterOf(start),flat=Vec3.atCenterOf(flatEnd),last=Vec3.atCenterOf(end);
        scene.world().showSection(util.select().fromTo(1,1,0,1,1,2),Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(52).text("Reinforced Belts carry items around curves and between heights.")
            .pointAt(first).placeNearTarget();
        scene.idle(55);

        scene.addKeyframe();
        scene.overlay().showControls(first.add(0,.4,0),Pointing.DOWN,31).rightClick().withItem(belt);
        scene.overlay().showOutline(PonderPalette.GREEN,"reinforced_start",util.select().position(start),70);
        scene.overlay().showText(46).text("Right-click a horizontal shaft to begin.")
            .pointAt(first).placeNearTarget();
        scene.idle(52);

        BeltPath preview=new BeltPath(start,flatEnd,Direction.EAST,Direction.SOUTH,0);
        guide(scene,preview,PonderPalette.BLUE,95);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE,"reinforced_endpoint",new AABB(flatEnd).inflate(.025),95);
        scene.overlay().showText(55).text("Aim to choose the other end. Bring a shaft for a new endpoint.")
            .pointAt(flat).placeNearTarget();
        scene.idle(62);

        scene.overlay().showControls(flat.add(0,.5,0),Pointing.DOWN,38).scroll().whileSneaking().withItem(belt);
        guide(scene,new BeltPath(start,flatEnd,Direction.EAST,Direction.SOUTH,1),PonderPalette.BLUE,65);
        scene.overlay().showText(42).text("Sneak-scroll to choose a curve style.")
            .pointAt(preview.atDistance(preview.length*.5)).placeNearTarget();
        scene.idle(49);

        scene.addKeyframe();
        scene.overlay().showControls(flat.add(0,.4,0),Pointing.DOWN,31).rightClick().withItem(belt);
        scene.overlay().showText(46).text("Right-click to lock the horizontal position.")
            .colored(PonderPalette.GREEN).pointAt(flat).placeNearTarget();
        scene.idle(52);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE,"reinforced_height",
            new AABB(flatEnd.getX(),flatEnd.getY(),flatEnd.getZ(),end.getX()+1,end.getY()+1,end.getZ()+1).inflate(.035),100);
        guide(scene,new BeltPath(start,end,Direction.EAST,Direction.SOUTH,0),PonderPalette.BLUE,100);
        scene.overlay().showText(58).text("Look up or down to set the height shown by the box.")
            .pointAt(last).placeNearTarget();
        scene.idle(65);

        scene.overlay().showControls(last.add(0,.4,0),Pointing.DOWN,31).rightClick().withItem(belt);
        scene.overlay().showText(42).text("Right-click again to place the belt.")
            .colored(PonderPalette.GREEN).pointAt(last).placeNearTarget();
        place(scene,util,start,end);
        scene.idle(49);

        scene.addKeyframe();
        scene.overlay().showControls(last.add(0,.4,0),Pointing.DOWN,35).withItem(com.simibubi.create.AllBlocks.SHAFT.asStack());
        scene.overlay().showText(58).text("The crosshair shows how many belts and shafts you need.")
            .pointAt(last).placeNearTarget();
        scene.idle(65);
        scene.overlay().showText(49).text("Keep the route clear and within the length limit.")
            .colored(PonderPalette.RED).pointAt(new Vec3(4.5,2.6,3.5)).placeNearTarget();
        scene.idle(55);
        scene.markAsFinished();
    }

    public static void adjustments(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=new CreateSceneBuilder(builder);
        scene.title("reinforced_belt_adjustments","Adjusting and Driving Reinforced Belts");
        scene.configureBasePlate(0,0,9);
        scene.scaleSceneView(.65f);
        scene.setSceneOffsetY(-.5f);
        scene.showBasePlate();
        BlockPos start=util.grid().at(1,1,2),end=util.grid().at(7,3,6);
        scene.world().showSection(util.select().fromTo(1,1,0,1,1,2),Direction.DOWN);
        place(scene,util,start,end);
        scene.idle(16);
        BeltPath path=new BeltPath(start,end,Direction.EAST,Direction.SOUTH,0);
        Vec3 middle=path.point(.5);
        ItemStack wrench=AllItems.WRENCH.asStack();

        scene.overlay().showText(52).text("Use a wrench to reshape a placed belt. Endpoints stay fixed.")
            .pointAt(middle).placeNearTarget();
        scene.idle(58);
        scene.addKeyframe();
        scene.overlay().showControls(middle.add(0,.5,0),Pointing.DOWN,46).rightClick().withItem(wrench);
        scene.overlay().showText(55).text("Hold right-click with a wrench. Look up or down to change height.")
            .pointAt(middle).placeNearTarget();
        scene.idle(29);
        for(int step=1;step<=20;step++) {
            reshape(scene,util,start,end,step/20.0,0,0);
            scene.idle(2);
        }
        scene.idle(15);

        scene.overlay().showControls(middle.add(0,1.5,0),Pointing.DOWN,46).scroll().withItem(wrench);
        scene.overlay().showText(52).text("Keep holding and scroll to move the section horizontally.")
            .pointAt(middle.add(0,1,0)).placeNearTarget();
        scene.idle(26);
        for(int step=1;step<=20;step++) {
            reshape(scene,util,start,end,1,step/20.0,0);
            scene.idle(2);
        }
        scene.idle(15);
        scene.overlay().showText(46).text("Release to apply. Left-click to reset the adjustment.")
            .colored(PonderPalette.GREEN).pointAt(middle.add(1,1,0)).placeNearTarget();
        scene.idle(52);

        scene.addKeyframe();
        scene.world().setKineticSpeed(util.select().fromTo(1,1,0,1,1,2),-64);
        scene.world().setKineticSpeed(util.select().position(end),64);
        scene.overlay().showText(58).text("Power either endpoint to drive the belt.")
            .pointAt(Vec3.atCenterOf(start)).placeNearTarget();
        animateCargo(scene,start,path,60);
        scene.world().modifyBlockEntity(start,ReinforcedPulleyBlockEntity.class,pulley->pulley.cargo.clear());
        scene.overlay().showText(46).text("Leave room for the whole curve.")
            .colored(PonderPalette.RED).pointAt(middle.add(1,1,0)).placeNearTarget();
        scene.idle(52);
        scene.markAsFinished();
    }

    public static void transport(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=new CreateSceneBuilder(builder);
        scene.title("reinforced_belt_transport","Transport, Casing and Removal");
        scene.configureBasePlate(0,0,9);
        scene.scaleSceneView(.65f);
        scene.showBasePlate();
        BlockPos start=util.grid().at(1,1,2),end=util.grid().at(7,3,6);
        scene.world().showSection(util.select().fromTo(1,1,0,1,1,2),Direction.DOWN);
        place(scene,util,start,end);
        scene.idle(16);
        BeltPath path=new BeltPath(start,end,Direction.EAST,Direction.SOUTH,0);
        Vec3 middle=path.point(.5);
        scene.overlay().showText(52).text("Items follow the curve, even uphill.")
            .pointAt(middle).placeNearTarget();
        animateCargo(scene,start,path,80);
        scene.world().modifyBlockEntity(start,ReinforcedPulleyBlockEntity.class,pulley->pulley.cargo.clear());
        scene.addKeyframe();
        scene.overlay().showControls(middle.add(0,.5,0),Pointing.DOWN,35).rightClick().withItem(new ItemStack(Items.BLUE_DYE));
        scene.world().modifyBlockEntityNBT(util.select().position(start).add(util.select().position(end)),
            ReinforcedPulleyBlockEntity.class,tag->tag.putInt("BeltColor",DyeColor.BLUE.getId()));
        scene.overlay().showText(52).text("Right-click with dye to colour the whole belt.")
            .pointAt(middle).placeNearTarget();
        scene.idle(58);
        scene.overlay().showControls(middle.add(0,.5,0),Pointing.DOWN,35).rightClick()
            .withItem(com.simibubi.create.AllBlocks.BRASS_CASING.asStack());
        scene.world().modifyBlockEntityNBT(util.select().position(start).add(util.select().position(end)),
            ReinforcedPulleyBlockEntity.class,tag->tag.putString("CasingId",BeltCasings.BRASS.toString()));
        scene.overlay().showText(55).text("Apply casing to cover the belt. Wrench its side or underside to remove it.")
            .pointAt(middle).placeNearTarget();
        scene.idle(62);
        scene.addKeyframe();
        scene.overlay().showControls(Vec3.atCenterOf(end),Pointing.DOWN,38).rightClick().whileSneaking().withItem(AllItems.WRENCH.asStack());
        scene.overlay().showText(58).text("Sneak-right-click an endpoint with a wrench to dismantle and recover the belts.")
            .pointAt(Vec3.atCenterOf(end)).placeNearTarget();
        scene.idle(65);
        scene.markAsFinished();
    }

    private static void animateCargo(CreateSceneBuilder scene,BlockPos start,BeltPath path,int ticks) {
        // Retain one stack and its previous position, rather than replacing it
        // every other tick. Match the continuous transport in Create's belt Ponders.
        scene.world().modifyBlockEntity(start,ReinforcedPulleyBlockEntity.class,pulley->{
            pulley.cargo.clear();
            var cargo=new ReinforcedPulleyBlockEntity.Cargo(new ItemStack(Items.IRON_INGOT),path.length*.05,0);
            cargo.referenceHeading=ReinforcedPulleyBlockEntity.beltHeading(path,cargo.distance);
            pulley.cargo.add(cargo);
        });
        for(int tick=1;tick<=ticks;tick++) {
            final double progress=.05+.9*tick/ticks;
            scene.world().modifyBlockEntity(start,ReinforcedPulleyBlockEntity.class,pulley->{
                if(pulley.cargo.isEmpty()) return;
                var cargo=pulley.cargo.getFirst();
                double distance=pulley.path().length*progress;
                cargo.previous=cargo.distance;
                cargo.visualPrevious=cargo.visualDistance;
                cargo.distance=distance;
                cargo.visualDistance=distance;
            });
            scene.idle(1);
        }
    }
    private static void guide(CreateSceneBuilder scene,BeltPath path,PonderPalette color,int duration) {
        for(int i=0;i<BeltPath.SAMPLES;i+=8)
            scene.overlay().showLine(color,path.samples[i],path.samples[Math.min(BeltPath.SAMPLES,i+8)],duration);
    }

    private static void place(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos start,BlockPos end) {
        scene.world().setBlock(start,BeltsNPieces.PULLEY.get().defaultBlockState()
            .setValue(RotatedPillarKineticBlock.AXIS,Direction.Axis.Z),false);
        scene.world().setBlock(end,BeltsNPieces.PULLEY.get().defaultBlockState()
            .setValue(RotatedPillarKineticBlock.AXIS,Direction.Axis.X),false);
        var endpoint=scene.world().showIndependentSection(util.select().position(end),Direction.DOWN);
        scene.world().moveSection(endpoint,new Vec3(0,.5,0),0);
        scene.world().moveSection(endpoint,new Vec3(0,-.5,0),10);
        // Configure virtual endpoints directly. This never calls player placement,
        // edits a real save, consumes inventory, or builds collision blocks.
        for(BlockPos pos:new BlockPos[]{start,end}) scene.world().modifyBlockEntityNBT(util.select().position(pos),
            ReinforcedPulleyBlockEntity.class,tag->{
                tag.putLong("Peer",(pos.equals(start)?end:start).asLong());
                tag.putBoolean("Controller",pos.equals(start));
                tag.putInt("StartDirection",Direction.EAST.get3DDataValue());
                tag.putInt("EndDirection",Direction.SOUTH.get3DDataValue());
                tag.putInt("CurveStyle",0);tag.putInt("PathRevision",3);
                tag.putInt("BeltColor",-1);tag.putInt("Ratio",-1);
                tag.putInt("PaidBelts",0);tag.putLong("PlacementTime",Long.MIN_VALUE);
                tag.put("ElevationEdits",new ListTag());
            });
    }

    private static void reshape(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos start,BlockPos end,double height,double x,double z) {
        scene.world().modifyBlockEntityNBT(util.select().position(start).add(util.select().position(end)),
            ReinforcedPulleyBlockEntity.class,tag->{
                var edit=new CompoundTag();edit.putDouble("Center",.5);edit.putDouble("Height",height);
                edit.putDouble("X",x);edit.putDouble("Z",z);
                var edits=new ListTag();edits.add(edit);tag.put("ElevationEdits",edits);
            });
    }
}
