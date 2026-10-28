package com.sirjohnty.bitsnbelts.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.AllPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import com.sirjohnty.bitsnbelts.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

public final class ReinforcedBeltRenderer implements BlockEntityRenderer<ReinforcedPulleyBlockEntity> {
    private static final ResourceLocation TEXTURE=BitsNBelts.id("textures/block/reinforced_belt.png");
    private final java.util.Map<ReinforcedPulleyBlockEntity,Integer> lastTextureDirection=new java.util.WeakHashMap<>();
    private final java.util.Map<BeltPath,RenderGeometry[]> geometryCache=new java.util.WeakHashMap<>();
    private final java.util.Map<ReinforcedPulleyBlockEntity,Integer> distanceBands=new java.util.WeakHashMap<>();
    private static final double[] LOD_DISTANCES={16,32,64,96};
    public ReinforcedBeltRenderer(BlockEntityRendererProvider.Context ctx) {}
    // A controller draws the whole belt, including sections beyond its own chunk section.
    @Override public boolean shouldRenderOffScreen(ReinforcedPulleyBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 128; }
    @Override public boolean shouldRender(ReinforcedPulleyBlockEntity be,Vec3 camera) {
        var box=be.getRenderBoundingBox();
        double x=Math.clamp(camera.x,box.minX,box.maxX),y=Math.clamp(camera.y,box.minY,box.maxY),z=Math.clamp(camera.z,box.minZ,box.maxZ);
        return camera.distanceToSqr(new Vec3(x,y,z))<128*128;
    }
    @Override public void render(ReinforcedPulleyBlockEntity be,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if(be.getLevel()==null) return;
        var frustum=Minecraft.getInstance().levelRenderer.getFrustum();
        if(frustum!=null && !frustum.isVisible(be.getRenderBoundingBox())) return;
        // The regular Create pulley contains both its wooden roller and shaft.
        var axis=be.getBlockState().getValue(ReinforcedPulleyBlock.AXIS);
        Direction pulleyDirection=axis==Direction.Axis.X?Direction.EAST:axis==Direction.Axis.Z?Direction.SOUTH:Direction.UP;
        var pulley=CachedBuffers.partialDirectional(AllPartialModels.BELT_PULLEY,be.getBlockState(),pulleyDirection,()->{
            PoseStack transform=new PoseStack();transform.translate(.5,.5,.5);
            if(axis==Direction.Axis.X) transform.mulPose(Axis.YP.rotationDegrees(90));
            if(axis==Direction.Axis.Y) transform.mulPose(Axis.XP.rotationDegrees(90));
            transform.mulPose(Axis.XP.rotationDegrees(90));transform.translate(-.5,-.5,-.5);return transform;
        });
        KineticBlockEntityRenderer.standardKineticRotationTransform(pulley,be,light).renderInto(poses,buffers.getBuffer(RenderType.solid()));
        if(!be.controller||be.path()==null) return;
        BeltPath path=be.path();Vec3 origin=Vec3.atLowerCornerOf(be.getBlockPos());
        int color=be.beltColor();
        ResourceLocation texture=color<0?TEXTURE:BitsNBelts.id("textures/block/reinforced_belt/"+net.minecraft.world.item.DyeColor.byId(color).getName()+".png");
        VertexConsumer vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        var casing=be.casing();
        // Use the block atlas so addon animations (including RGB casing) keep working.
        VertexConsumer casingVertices=null;
        if(casing!=null) {
            var sprite=Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                .apply(casing.texture());
            if(sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
                // Some Extra Casing releases misspell the light blue/gray metal sprite registrations.
                var textureFile=ResourceLocation.fromNamespaceAndPath(casing.texture().getNamespace(),
                    "textures/"+casing.texture().getPath()+".png");
                casingVertices=buffers.getBuffer(RenderType.entityCutoutNoCull(textureFile));
            } else casingVertices=sprite.wrap(buffers.getBuffer(RenderType.entityCutoutNoCull(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)));
        }
        VertexConsumer casingGear=casing==null?null:buffers.getBuffer(RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath("create","textures/block/gearbox.png")));
        double velocity=be.velocity();
        int textureDirection=velocity>1e-7?1:velocity< -1e-7?-1:lastTextureDirection.getOrDefault(be,1);
        if(Math.abs(velocity)>1e-7) lastTextureDirection.put(be,textureDirection);
        // Match Create's horizontal belt animation clock and UV rate.
        // Item transport uses RPM / 480, while Create scrolls at RPM / 504.
        double scroll=net.createmod.catnip.animation.AnimationTickHolder.getRenderTime(be.getLevel())*velocity*(480.0/504.0);
        double texturePhase=texturePhase(be);
        int detail=distanceDetail(be,BitsNBeltsConfig.reinforcedBeltVisualDetail());
        RenderGeometry[] levels=geometryCache.computeIfAbsent(path,p->new RenderGeometry[5]);
        int detailIndex=detail+1;
        RenderGeometry geometry=levels[detailIndex];
        if(geometry==null) levels[detailIndex]=geometry=new RenderGeometry(path,detail);
        // Each cross-section is shared by its neighbours, preventing gaps at bends.
        for(int segment=1;segment<geometry.indices.length;segment++) {
            int i=geometry.indices[segment-1],j=geometry.indices[segment];
            Vec3 a=path.samples[i].subtract(origin),b=path.samples[j].subtract(origin);
            int segmentLight=LevelRenderer.getLightColor(be.getLevel(),BlockPos.containing(path.samples[i].lerp(path.samples[j],.5).add(0,.2,0)));
            Vec3 sideA=geometry.sides[i],sideB=geometry.sides[j];
            if(sideA.lengthSqr()<.01||sideB.lengthSqr()<.01) continue;
            float va=(float)((path.distances[i]-scroll)*textureDirection+texturePhase),vb=(float)((path.distances[j]-scroll)*textureDirection+texturePhase);
            // Reverse the animation on the return run while retaining its mirrored texture orientation.
            float returnVa=(float)(-va-2*scroll*textureDirection),returnVb=(float)(-vb-2*scroll*textureDirection);
            Vec3 al=a.add(sideA),ar=a.subtract(sideA),bl=b.add(sideB),br=b.subtract(sideB);
            Vec3 normalA=geometry.normals[i],normalB=geometry.normals[j];
            Vec3 thickA=normalA.scale(-.125),thickB=normalB.scale(-.125);
            quad(poses,vertices,ar,br,bl,al,va,vb,segmentLight,1);
            // Create belts have two exposed runs, each two pixels thick.
            quad(poses,vertices,al.add(thickA),bl.add(thickB),br.add(thickB),ar.add(thickA),va,vb,segmentLight,.6f);
            sideQuad(poses,vertices,al.add(thickA),bl.add(thickB),bl,al,va,vb,segmentLight,.8f,0,2f/16);
            sideQuad(poses,vertices,ar,br,br.add(thickB),ar.add(thickA),va,vb,segmentLight,.8f,14f/16,1);
            // Keep the two runs and their thickness perpendicular to the local slope.
            Vec3 ral=al.add(normalA.scale(-.5)),rar=ar.add(normalA.scale(-.5));
            Vec3 rbl=bl.add(normalB.scale(-.5)),rbr=br.add(normalB.scale(-.5));
            quad(poses,vertices,rar,rbr,rbl,ral,returnVa,returnVb,segmentLight,.65f);
            quad(poses,vertices,ral.add(thickA),rbl.add(thickB),rbr.add(thickB),rar.add(thickA),returnVa,returnVb,segmentLight,.5f);
            sideQuad(poses,vertices,ral.add(thickA),rbl.add(thickB),rbl,ral,returnVa,returnVb,segmentLight,.8f,0,2f/16);
            sideQuad(poses,vertices,rar,rbr,rbr.add(thickB),rar.add(thickA),returnVa,returnVb,segmentLight,.8f,14f/16,1);
            if(casingVertices!=null) {
                // Follow the belt frame, leaving the upper tread and items visible.
                double from=path.distances[i],to=path.distances[j],lo=Math.max(from,.5),hi=Math.min(to,path.length-.5);
                if(hi>lo+1e-8) {
                    double t0=(lo-from)/(to-from),t1=(hi-from)/(to-from);
                    Vec3[] frame0=casingFrame(path,origin,lo,a.lerp(b,t0),sideA.lerp(sideB,t0),normalA.lerp(normalB,t0));
                    Vec3[] frame1=casingFrame(path,origin,hi,a.lerp(b,t1),sideA.lerp(sideB,t1),normalA.lerp(normalB,t1));
                    if(!casing.beltAtlas()) renderSolidCasingSide(poses,casingVertices,frame0,frame1,lo,hi,segmentLight);
                    else renderCasingSide(poses,casingVertices,frame0[0],frame1[0],
                        frame0[1],frame1[1],frame0[2],frame1[2],lo,hi,segmentLight);
                }
            }
        }
        for(boolean start:new boolean[]{true,false}) {
            if(casingVertices!=null) {
                if(casing.beltAtlas()) renderPulleyCasing(poses,casingVertices,casingGear,path,origin,start,light);
                else renderSolidPulleyCasing(poses,casingVertices,casingGear,path,origin,start,light);
            }
            if(connected(be,path,start)) renderConnection(poses,vertices,path,origin,start,scroll,textureDirection,texturePhase,light,.5);
            else renderEnd(poses,vertices,path,origin,start,scroll,textureDirection,texturePhase,light);
        }
        for(var c:be.cargo) {
            double distance=c.visualPrevious+(c.visualDistance-c.visualPrevious)*partial;
            Vec3 normal=surfaceNormal(path,distance);
            // Keep the item above the local belt surface, including steep slopes.
            double clampedDistance=Math.clamp(distance,0,path.length);
            int sideIndex=(int)Math.round(path.parameterAtDistance(clampedDistance)*BeltPath.SAMPLES);
            Vec3 side=beltSide(path,sideIndex).normalize().scale(c.sideOffset);
            Vec3 p=itemPosition(path,distance).subtract(origin).add(normal.scale(.125)).add(side);
            var renderer=Minecraft.getInstance().getItemRenderer();
            var model=renderer.getModel(c.stack,be.getLevel(),null,0);
            boolean upright=BeltHelper.isItemUpright(c.stack);
            poses.pushPose();poses.translate(p.x,p.y,p.z);
            poses.mulPose(new org.joml.Quaternionf().rotationTo(0,1,0,(float)normal.x,(float)normal.y,(float)normal.z));
            if(upright) {
                Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                Vec3 world=p.add(origin),relative=world.subtract(camera);
                poses.mulPose(Axis.YP.rotation((float)(Math.atan2(relative.x,relative.z)+Math.PI)));
                poses.translate(0,.09375,.0625);
            }
            int itemLight=LevelRenderer.getLightColor(be.getLevel(),BlockPos.containing(p.add(origin).add(0,.2,0)));
            var player=Minecraft.getInstance().player;
            int extra=player!=null && player.getEyePosition(1).distanceTo(p.add(origin))<16
                ? net.minecraft.util.Mth.log2(c.stack.getCount())/2 : 0;
            var random=new java.util.Random(c.angle);
            boolean packageItem=com.simibubi.create.content.logistics.box.PackageItem.isPackage(c.stack);
            for(int copy=0;copy<=extra;copy++) {
                poses.pushPose();
                poses.mulPose(Axis.YP.rotationDegrees(ReinforcedPulleyBlockEntity.cargoAngle(path,c,distance)));
                if(!model.isGui3d()&&!upright) {
                    poses.translate(0,-.09375,0);
                    poses.mulPose(Axis.XP.rotationDegrees(90));
                }
                if(model.isGui3d()&&!packageItem)
                    poses.translate(random.nextFloat()*.0625f*copy,0,random.nextFloat()*.0625f*copy);
                if(packageItem) {poses.translate(0,.25,0);poses.scale(1.5f,1.5f,1.5f);}
                else poses.scale(.5f,.5f,.5f);
                renderer.render(c.stack,ItemDisplayContext.FIXED,false,poses,buffers,itemLight,OverlayTexture.NO_OVERLAY,model);
                poses.popPose();
                if(upright) poses.translate(0,0,-.0625);
                else {
                    if(!model.isGui3d()) poses.mulPose(Axis.YP.rotationDegrees(10));
                    poses.translate(0,model.isGui3d()?.015625:.0625,0);
                }
            }
            poses.popPose();
        }
    }
    // Join the curved shell to the exact horizontal edge of each pulley casing.
    // Use the same selected cross-sections as the tread at every quality level.
    private static Vec3[] casingFrame(BeltPath path,Vec3 origin,double distance,Vec3 position,Vec3 side,Vec3 normal) {
        boolean start=distance<=path.length/2;
        double endDistance=start?distance:path.length-distance;
        double blend=Math.clamp((endDistance-.5)/.5,0,1);
        blend=blend*blend*(3-2*blend);
        Vec3 forward=Vec3.atLowerCornerOf((start?path.startDirection:path.endDirection).getNormal());
        Vec3 anchor=path.samples[start?0:BeltPath.SAMPLES].subtract(origin)
            .add(forward.scale(start?endDistance:-endDistance));
        Vec3 horizontalSide=new Vec3(forward.z,0,-forward.x).scale(7.0/16);
        return new Vec3[]{anchor.lerp(position,blend),horizontalSide.lerp(side,blend),new Vec3(0,1,0).lerp(normal,blend)};
    }
    private static void renderCasingSide(PoseStack poses,VertexConsumer vertices,Vec3 a,Vec3 b,Vec3 sideA,Vec3 sideB,
            Vec3 normalA,Vec3 normalB,double from,double to,int light) {
        if(to-from<1e-8) return;
        double current=from;
        while(current<to-1e-8) {
            double end=Math.min(to,Math.floor(current+1e-7)+1);
            double t0=(current-from)/(to-from),t1=(end-from)/(to-from);
            Vec3 p=a.lerp(b,t0),q=a.lerp(b,t1);
            Vec3 s0=sideA.lerp(sideB,t0).scale(8.0/7),s1=sideA.lerp(sideB,t1).scale(8.0/7);
            Vec3 n0=normalA.lerp(normalB,t0),n1=normalA.lerp(normalB,t1);
            float u0=(float)((4+(current-Math.floor(current+1e-7))*4)/16);
            float u1=(float)(u0+(end-current)*4/16);
            for(int sign:new int[]{-1,1}) {
                Vec3 top0=p.add(s0.scale(sign)).add(n0.scale(-2.0/16));
                Vec3 top1=q.add(s1.scale(sign)).add(n1.scale(-2.0/16));
                Vec3 bottom0=p.add(s0.scale(sign)).add(n0.scale(-13.0/16));
                Vec3 bottom1=q.add(s1.scale(sign)).add(n1.scale(-13.0/16));
                Vec3 normal=s0.add(s1).normalize().scale(sign);
                vertex(poses,vertices,top0,u0,0,light,.8f,normal);
                vertex(poses,vertices,top1,u1,0,light,.8f,normal);
                vertex(poses,vertices,bottom1,u1,2.75f/16,light,.8f,normal);
                vertex(poses,vertices,bottom0,u0,2.75f/16,light,.8f,normal);
                // Inner wall one pixel in from the outer side forms a solid rim.
                Vec3 inset0=s0.scale(sign*7.0/8),inset1=s1.scale(sign*7.0/8);
                Vec3 insideNormal=normal.scale(-1);
                vertex(poses,vertices,p.add(inset0).add(n0.scale(-2.0/16)),u0,0,light,.8f,insideNormal);
                vertex(poses,vertices,q.add(inset1).add(n1.scale(-2.0/16)),u1,0,light,.8f,insideNormal);
                vertex(poses,vertices,q.add(inset1).add(n1.scale(-13.0/16)),u1,2.75f/16,light,.8f,insideNormal);
                vertex(poses,vertices,p.add(inset0).add(n0.scale(-13.0/16)),u0,2.75f/16,light,.8f,insideNormal);
            }
            Vec3 topL0=p.add(s0).add(n0.scale(-2.0/16)),topR0=p.subtract(s0).add(n0.scale(-2.0/16));
            Vec3 topL1=q.add(s1).add(n1.scale(-2.0/16)),topR1=q.subtract(s1).add(n1.scale(-2.0/16));
            Vec3 bottomL0=p.add(s0).add(n0.scale(-13.0/16)),bottomR0=p.subtract(s0).add(n0.scale(-13.0/16));
            Vec3 bottomL1=q.add(s1).add(n1.scale(-13.0/16)),bottomR1=q.subtract(s1).add(n1.scale(-13.0/16));
            // Match Create's middle casing top tile, rather than the full belt atlas.
            float topV0=(float)((2.75+(current-Math.floor(current+1e-7))*2)/16);
            float topV1=(float)(topV0+(end-current)*2/16);
            Vec3 up=n0.add(n1).normalize();
            vertex(poses,vertices,topR0,0,topV0,light,.8f,up);
            vertex(poses,vertices,topR1,0,topV1,light,.8f,up);
            vertex(poses,vertices,topL1,4f/16,topV1,light,.8f,up);
            vertex(poses,vertices,topL0,4f/16,topV0,light,.8f,up);
            // Create's underside tile has its frame along the belt sides.
            // Map its U axis across the belt and V axis along its length.
            float bottomV0=(float)((12+(current-Math.floor(current+1e-7))*4)/16);
            float bottomV1=(float)(bottomV0+(end-current)*4/16);
            Vec3 down=n0.add(n1).normalize().scale(-1);
            vertex(poses,vertices,bottomL0,8f/16,bottomV0,light,.65f,down);
            vertex(poses,vertices,bottomL1,8f/16,bottomV1,light,.65f,down);
            vertex(poses,vertices,bottomR1,12f/16,bottomV1,light,.65f,down);
            vertex(poses,vertices,bottomR0,12f/16,bottomV0,light,.65f,down);
            current=end;
        }
    }
    private static void renderPulleyCasing(PoseStack poses,VertexConsumer v,VertexConsumer gear,BeltPath path,Vec3 origin,boolean start,int light) {
        int index=start?0:BeltPath.SAMPLES;
        Vec3 center=path.samples[index].subtract(origin);
        Vec3 forward=Vec3.atLowerCornerOf((start?path.startDirection:path.endDirection).getNormal());
        Vec3 side=new Vec3(forward.z,0,-forward.x);
        // Create's pulley casing spans one block horizontally and Y=0..11.
        for(int sign:new int[]{-1,1}) {
            Vec3 out=side.scale(sign*.5),normal=side.scale(sign);
            // Create's 16x11 face uses an alpha-cutout pulley frame from the
            // casing atlas; its transparent centre exposes the inset gearbox.
            casingRect(poses,v,center,forward,out,normal,-.5,.5,-13.0/16,-2.0/16,0,4f/16,0,2.75f/16,light);
            casingRect(poses,gear,center,forward,side.scale(sign*7.0/16),normal,-.25,.25,-8.0/16,-2.0/16,4f/16,12f/16,5f/16,11f/16,light);
        }
        // Recessed front/back walls of Create's pulley opening (Y=5..11).
        for(int sign:new int[]{-1,1}) {
            Vec3 wallCenter=center.add(forward.scale(sign*.25));
            casingRect(poses,v,wallCenter,side,Vec3.ZERO,forward.scale(-sign),
                -.5,.5,-8.0/16,-2.0/16,4f/16,8f/16,3.25f/16,4.75f/16,light);
        }
        // Ceiling inside that opening, joining its two recessed walls.
        Vec3 innerA=center.subtract(forward.scale(.25)).add(0,-8.0/16,0);
        Vec3 innerB=center.add(forward.scale(.25)).add(0,-8.0/16,0);
        Vec3 innerSide=side.scale(.5),innerDown=new Vec3(0,-1,0);
        vertex(poses,v,innerA.add(innerSide),0,2.75f/16,light,.65f,innerDown);
        vertex(poses,v,innerB.add(innerSide),0,4.75f/16,light,.65f,innerDown);
        vertex(poses,v,innerB.subtract(innerSide),4f/16,4.75f/16,light,.65f,innerDown);
        vertex(poses,v,innerA.subtract(innerSide),4f/16,2.75f/16,light,.65f,innerDown);
        Vec3 a=center.subtract(forward.scale(.5)),b=center.add(forward.scale(.5));
        Vec3 left=side.scale(.5);
        Vec3 up=new Vec3(0,1,0);
        vertex(poses,v,a.subtract(left).add(0,-2.0/16,0),0,4f/16,light,.8f,up);
        vertex(poses,v,b.subtract(left).add(0,-2.0/16,0),4f/16,4f/16,light,.8f,up);
        vertex(poses,v,b.add(left).add(0,-2.0/16,0),4f/16,8f/16,light,.8f,up);
        vertex(poses,v,a.add(left).add(0,-2.0/16,0),0,8f/16,light,.8f,up);
        Vec3 down=new Vec3(0,-1,0);
        vertex(poses,v,a.add(left).add(0,-13.0/16,0),8f/16,12f/16,light,.65f,down);
        vertex(poses,v,b.add(left).add(0,-13.0/16,0),8f/16,1,light,.65f,down);
        vertex(poses,v,b.subtract(left).add(0,-13.0/16,0),12f/16,1,light,.65f,down);
        vertex(poses,v,a.subtract(left).add(0,-13.0/16,0),12f/16,12f/16,light,.65f,down);
        Vec3 outward=forward.scale(start?-1:1);
        Vec3 end=center.add(outward.scale(.5));
        Vec3 endL=end.add(left),endR=end.subtract(left);
        vertex(poses,v,endL.add(0,-2.0/16,0),4f/16,5.25f/16,light,.8f,outward);
        vertex(poses,v,endR.add(0,-2.0/16,0),8f/16,5.25f/16,light,.8f,outward);
        vertex(poses,v,endR.add(0,-13.0/16,0),8f/16,8f/16,light,.8f,outward);
        vertex(poses,v,endL.add(0,-13.0/16,0),4f/16,8f/16,light,.8f,outward);
    }
    /** Plain block textures need full-face UVs rather than Create's belt casing atlas. */
    private static void renderSolidCasingSide(PoseStack poses,VertexConsumer v,Vec3[] first,Vec3[] last,
            double from,double to,int light) {
        double current=from;
        while(current<to-1e-8) {
            double end=Math.min(to,Math.floor(current+1e-7)+1);
            Vec3[] left=new Vec3[4],right=new Vec3[4];
            for(int k=0;k<2;k++) {
                double t=((k==0?current:end)-from)/(to-from);
                Vec3 p=first[0].lerp(last[0],t),s=first[1].lerp(last[1],t).scale(8.0/7);
                Vec3 n=first[2].lerp(last[2],t);
                left[k]=p.add(s).add(n.scale(-2.0/16));
                right[k]=p.subtract(s).add(n.scale(-2.0/16));
                left[k+2]=p.add(s).add(n.scale(-13.0/16));
                right[k+2]=p.subtract(s).add(n.scale(-13.0/16));
            }
            float u0=(float)(current-Math.floor(current+1e-7)),u1=u0+(float)(end-current);
            plainQuad(poses,v,left[0],left[1],left[3],left[2],u0,u1,light,.8f);
            plainQuad(poses,v,right[0],right[1],right[3],right[2],u0,u1,light,.8f);
            plainQuad(poses,v,left[0],left[1],right[1],right[0],u0,u1,light,.8f);
            plainQuad(poses,v,left[2],left[3],right[3],right[2],u0,u1,light,.65f);
            current=end;
        }
    }
    private static void renderSolidPulleyCasing(PoseStack poses,VertexConsumer v,VertexConsumer gear,
            BeltPath path,Vec3 origin,boolean start,int light) {
        Vec3 center=path.samples[start?0:BeltPath.SAMPLES].subtract(origin);
        Vec3 forward=Vec3.atLowerCornerOf((start?path.startDirection:path.endDirection).getNormal());
        Vec3 side=new Vec3(forward.z,0,-forward.x);
        for(int sign:new int[]{-1,1}) {
            Vec3 out=side.scale(sign*.5),normal=side.scale(sign);
            // Build the shaft opening from faces, since a solid steel texture has no alpha cutout.
            casingRect(poses,v,center,forward,out,normal,-.5,-.25,-13.0/16,-2.0/16,0,.25f,0,1,light);
            casingRect(poses,v,center,forward,out,normal,.25,.5,-13.0/16,-2.0/16,.75f,1,0,1,light);
            casingRect(poses,v,center,forward,out,normal,-.25,.25,-13.0/16,-8.0/16,.25f,.75f,6f/11,1,light);
            casingRect(poses,gear,center,forward,side.scale(sign*7.0/16),normal,
                -.25,.25,-8.0/16,-2.0/16,4f/16,12f/16,5f/16,11f/16,light);
            casingRect(poses,v,center.add(forward.scale(sign*.25)),side,Vec3.ZERO,forward.scale(-sign),
                -.5,.5,-8.0/16,-2.0/16,0,1,0,1,light);
        }
        Vec3 a=center.subtract(forward.scale(.5)),b=center.add(forward.scale(.5)),s=side.scale(.5);
        for(double y:new double[]{-2.0/16,-13.0/16})
            plainQuad(poses,v,a.add(s).add(0,y,0),b.add(s).add(0,y,0),
                b.subtract(s).add(0,y,0),a.subtract(s).add(0,y,0),0,1,light,.8f);
        Vec3 outward=forward.scale(start?-1:1),end=center.add(outward.scale(.5));
        casingRect(poses,v,end,side,Vec3.ZERO,outward,-.5,.5,-13.0/16,-2.0/16,0,1,0,1,light);
        Vec3 innerA=center.subtract(forward.scale(.25)).add(0,-8.0/16,0);
        Vec3 innerB=center.add(forward.scale(.25)).add(0,-8.0/16,0);
        plainQuad(poses,v,innerA.add(s),innerB.add(s),innerB.subtract(s),innerA.subtract(s),0,1,light,.65f);
    }
    private static void plainQuad(PoseStack poses,VertexConsumer v,Vec3 a,Vec3 b,Vec3 c,Vec3 d,
            float u0,float u1,int light,float shade) {
        Vec3 normal=b.subtract(a).cross(d.subtract(a)).normalize();
        vertex(poses,v,a,u0,0,light,shade,normal);vertex(poses,v,b,u1,0,light,shade,normal);
        vertex(poses,v,c,u1,1,light,shade,normal);vertex(poses,v,d,u0,1,light,shade,normal);
    }
    private static void casingRect(PoseStack poses,VertexConsumer v,Vec3 center,Vec3 forward,Vec3 side,Vec3 normal,
            double from,double to,double low,double high,float u0,float u1,float v0,float v1,int light) {
        Vec3 a=center.add(side).add(forward.scale(from)),b=center.add(side).add(forward.scale(to));
        vertex(poses,v,a.add(0,high,0),u0,v0,light,.8f,normal);
        vertex(poses,v,b.add(0,high,0),u1,v0,light,.8f,normal);
        vertex(poses,v,b.add(0,low,0),u1,v1,light,.8f,normal);
        vertex(poses,v,a.add(0,low,0),u0,v1,light,.8f,normal);
    }
    private int distanceDetail(ReinforcedPulleyBlockEntity be,int maximum) {
        Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var box=be.getRenderBoundingBox();
        double dx=Math.max(0,Math.max(box.minX-camera.x,camera.x-box.maxX));
        double dy=Math.max(0,Math.max(box.minY-camera.y,camera.y-box.maxY));
        double dz=Math.max(0,Math.max(box.minZ-camera.z,camera.z-box.maxZ));
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        Integer previous=distanceBands.get(be);
        int band=previous==null?0:previous;
        // Two-block hysteresis avoids flickering and repeated changes at a boundary.
        while(band<LOD_DISTANCES.length && distance>LOD_DISTANCES[band]+(previous==null?0:2)) band++;
        while(band>0 && distance<LOD_DISTANCES[band-1]-(previous==null?0:2)) band--;
        distanceBands.put(be,band);
        return Math.max(-1,maximum-band);
    }
    private static Vec3 surfaceNormal(BeltPath path,double distance) {
        if(distance<=0 || distance>=path.length) return new Vec3(0,1,0);
        Vec3 tangent=path.atDistance(Math.min(path.length,distance+.05)).subtract(path.atDistance(Math.max(0,distance-.05)));
        double horizontal=Math.hypot(tangent.x,tangent.z);
        if(horizontal<1e-8) return new Vec3(0,1,0);
        return new Vec3(-tangent.x*tangent.y/horizontal,horizontal,-tangent.z*tangent.y/horizontal).normalize();
    }
    private static Vec3 itemPosition(BeltPath path,double distance) {
        if(distance<0) return path.start.add(Vec3.atLowerCornerOf(path.startDirection.getNormal()).scale(distance));
        if(distance>path.length) return path.end.add(Vec3.atLowerCornerOf(path.endDirection.getNormal()).scale(distance-path.length));
        return path.atDistance(distance);
    }
    private record PhaseLink(ReinforcedPulleyBlockEntity upstream,double offset) {}
    /** Align texture coordinates across belts sharing a direction and speed. */
    private static double texturePhase(ReinforcedPulleyBlockEntity belt) {
        if(Math.abs(belt.velocity())<1e-7 || belt.path()==null) return 0;
        var chain=new java.util.ArrayList<ReinforcedPulleyBlockEntity>();
        var links=new java.util.ArrayList<Double>();
        var indices=new java.util.HashMap<BlockPos,Integer>();
        ReinforcedPulleyBlockEntity current=belt;
        int anchor=-1;
        while(chain.size()<128) {
            Integer repeated=indices.get(current.getBlockPos());
            if(repeated!=null) {
                // A loop cannot always align at every join; choose a stable seam.
                anchor=repeated;
                for(int i=repeated+1;i<chain.size();i++)
                    if(chain.get(i).getBlockPos().compareTo(chain.get(anchor).getBlockPos())<0) anchor=i;
                break;
            }
            indices.put(current.getBlockPos(),chain.size());chain.add(current);
            PhaseLink link=upstreamPhaseLink(current);
            if(link==null) {anchor=chain.size()-1;break;}
            links.add(link.offset());current=link.upstream();
        }
        if(anchor<0) anchor=chain.size()-1;
        double phase=0;
        for(int i=0;i<anchor;i++) phase+=links.get(i);
        // Texture repeats once per block; keeping only the fraction avoids large UVs.
        return phase-Math.floor(phase);
    }
    private static PhaseLink upstreamPhaseLink(ReinforcedPulleyBlockEntity current) {
        BeltPath path=current.path();double velocity=current.velocity();
        if(path==null || Math.abs(velocity)<1e-7 || current.getLevel()==null) return null;
        boolean enteringStart=velocity>0;
        BlockPos endpoint=enteringStart?current.getBlockPos():current.peer;
        if(endpoint==null) return null;
        Direction outward=enteringStart?path.startDirection.getOpposite():path.endDirection;
        BlockPos adjacent=endpoint.relative(outward);
        var level=current.getLevel();
        if(!level.hasChunkAt(adjacent) || !(level.getBlockEntity(adjacent) instanceof ReinforcedPulleyBlockEntity other)) return null;
        ReinforcedPulleyBlockEntity upstream=other.root();
        if(upstream==null || upstream==current || upstream.path()==null) return null;
        double otherVelocity=upstream.velocity();
        if(Math.abs(otherVelocity)<1e-7 || Math.abs(Math.abs(velocity)-Math.abs(otherVelocity))>1e-6) return null;
        Direction otherOutward=other.controller?upstream.startDirection.getOpposite():upstream.endDirection;
        if(otherOutward!=outward.getOpposite() || (other.controller?otherVelocity>=0:otherVelocity<=0)) return null;
        double sourcePosition=other.controller?-.5:upstream.path().length+.5;
        double targetPosition=enteringStart?-.5:path.length+.5;
        double offset=Math.signum(otherVelocity)*sourcePosition-Math.signum(velocity)*targetPosition;
        return new PhaseLink(upstream,offset);
    }
    private static boolean connected(ReinforcedPulleyBlockEntity be,BeltPath path,boolean start) {
        BlockPos endpoint=start?be.getBlockPos():be.peer;
        Direction outward=start?path.startDirection.getOpposite():path.endDirection;
        BlockPos adjacent=endpoint.relative(outward);
        var level=be.getLevel();
        if(!level.hasChunkAt(adjacent)) return false;
        var neighbour=level.getBlockEntity(adjacent);
        if(neighbour instanceof ReinforcedPulleyBlockEntity other && other.peer!=null) {
            Direction otherOutward=other.controller?other.startDirection.getOpposite():other.endDirection;
            return otherOutward==outward.getOpposite();
        }
        var state=level.getBlockState(adjacent);
        return neighbour instanceof BeltBlockEntity && state.hasProperty(BeltBlock.SLOPE) && state.hasProperty(BeltBlock.HORIZONTAL_FACING)
            && state.getValue(BeltBlock.SLOPE)==BeltSlope.HORIZONTAL
            && state.getValue(BeltBlock.HORIZONTAL_FACING).getAxis()==outward.getAxis();
    }
    private static void renderConnection(PoseStack poses,VertexConsumer v,BeltPath path,Vec3 origin,boolean start,double scroll,int textureDirection,double texturePhase,int light,double extension) {
        int i=start?0:BeltPath.SAMPLES;
        Vec3 p=path.samples[i].subtract(origin),side=beltSide(path,i);
        Vec3 outward=Vec3.atLowerCornerOf((start?path.startDirection.getOpposite():path.endDirection).getNormal());
        Vec3 q=p.add(outward.scale(extension));
        float a=(float)((path.distances[i]-scroll)*textureDirection+texturePhase),b=a+(float)((start?-extension:extension)*textureDirection);
        float sideA=(float)path.distances[i],sideB=sideA+(float)(start?-extension:extension);
        // Walk from start to end on both connections so their normals match the belt.
        if(start) {Vec3 swap=p;p=q;q=swap;float uv=a;a=b;b=uv;uv=sideA;sideA=sideB;sideB=uv;}
        float returnA=(float)(-a-2*scroll*textureDirection),returnB=(float)(-b-2*scroll*textureDirection);
        Vec3 pl=p.add(side),pr=p.subtract(side),ql=q.add(side),qr=q.subtract(side);
        quad(poses,v,pr,qr,ql,pl,a,b,light,1);
        sideQuad(poses,v,pl.add(0,-.125,0),ql.add(0,-.125,0),ql,pl,a,b,light,.8f,0,2f/16);
        sideQuad(poses,v,pr,qr,qr.add(0,-.125,0),pr.add(0,-.125,0),a,b,light,.8f,14f/16,1);
        quad(poses,v,pl.add(0,-.125,0),ql.add(0,-.125,0),qr.add(0,-.125,0),pr.add(0,-.125,0),a,b,light,.6f);
        Vec3 ral=pl.add(0,-.5,0),rar=pr.add(0,-.5,0),rbl=ql.add(0,-.5,0),rbr=qr.add(0,-.5,0);
        quad(poses,v,rar,rbr,rbl,ral,returnA,returnB,light,.65f);
        quad(poses,v,ral.add(0,-.125,0),rbl.add(0,-.125,0),rbr.add(0,-.125,0),rar.add(0,-.125,0),returnA,returnB,light,.5f);
        sideQuad(poses,v,ral.add(0,-.125,0),rbl.add(0,-.125,0),rbl,ral,returnA,returnB,light,.8f,0,2f/16);
        sideQuad(poses,v,rar,rbr,rbr.add(0,-.125,0),rar.add(0,-.125,0),returnA,returnB,light,.8f,14f/16,1);
    }
    private static void renderEnd(PoseStack poses,VertexConsumer vertices,BeltPath path,Vec3 origin,boolean start,double scroll,int textureDirection,double texturePhase,int light) {
        // Match Create's end model: both treads reach 15/16 of the end block,
        // while the inset end band wraps between them from 14.05/16 to 16.05/16.
        renderConnection(poses,vertices,path,origin,start,scroll,textureDirection,texturePhase,light,7.0/16);
        int index=start?0:BeltPath.SAMPLES;
        Vec3 outward=Vec3.atLowerCornerOf((start?path.startDirection.getOpposite():path.endDirection).getNormal());
        Vec3 center=path.samples[index].subtract(origin);
        Vec3 side=beltSide(path,index).scale(13.8/14.0);
        Vec3 outer=center.add(outward.scale(8.05/16)),inner=center.add(outward.scale(6.05/16));
        // The band spans Y=4..12 while the two treads span Y=11..13 and 3..5.
        Vec3 otl=outer.add(side).add(0,-1.0/16,0),otr=outer.subtract(side).add(0,-1.0/16,0);
        Vec3 obl=otl.add(0,-8.0/16,0),obr=otr.add(0,-8.0/16,0);
        Vec3 itl=inner.add(side).add(0,-1.0/16,0),itr=inner.subtract(side).add(0,-1.0/16,0);
        Vec3 ibl=itl.add(0,-8.0/16,0),ibr=itr.add(0,-8.0/16,0);
        // Explicit outward normals keep the end lighting correct at both ends.
        // Continue the scrolling tread down the exposed pulley end face.
        float endTop=(float)((path.distances[index]+(start?-.5:.5)-scroll)*textureDirection+texturePhase);
        float endBottom=endTop+(start?-8f/16:8f/16)*textureDirection;
        vertex(poses,vertices,otr,1f/16,endTop,light,.8f,outward);
        vertex(poses,vertices,obr,1f/16,endBottom,light,.8f,outward);
        vertex(poses,vertices,obl,15f/16,endBottom,light,.8f,outward);
        vertex(poses,vertices,otl,15f/16,endTop,light,.8f,outward);
        float bandInner=(float)((path.distances[index]+(start?-6.05/16:6.05/16)-scroll)*textureDirection+texturePhase);
        float bandOuter=(float)((path.distances[index]+(start?-8.05/16:8.05/16)-scroll)*textureDirection+texturePhase);
        sideQuad(poses,vertices,itl,otl,obl,ibl,bandInner,bandOuter,light,.8f,0,2f/16);
        sideQuad(poses,vertices,otr,itr,ibr,obr,bandOuter,bandInner,light,.8f,14f/16,1);
        quad(poses,vertices,itr,otr,otl,itl,bandInner,bandOuter,light,.8f);
        float lowerInner=(float)(-bandInner-2*scroll*textureDirection),lowerOuter=(float)(-bandOuter-2*scroll*textureDirection);
        quad(poses,vertices,ibl,obl,obr,ibr,lowerInner,lowerOuter,light,.65f);

        // Create caps each two-pixel tread at 15/16; the outer band itself
        // stays at Y=4..12. Do not extend the band to the full belt height.
        Vec3 edge=center.add(outward.scale(7.0/16));
        Vec3 fullSide=beltSide(path,index);
        Vec3 topL=edge.add(fullSide),topR=edge.subtract(fullSide);
        Vec3 topBottomL=topL.add(0,-2.0/16,0),topBottomR=topR.add(0,-2.0/16,0);
        Vec3 lowL=topL.add(0,-8.0/16,0),lowR=topR.add(0,-8.0/16,0);
        Vec3 lowBottomL=topL.add(0,-10.0/16,0),lowBottomR=topR.add(0,-10.0/16,0);
        float edgeTop=(float)((path.distances[index]+(start?-7.0/16:7.0/16)-scroll)*textureDirection+texturePhase);
        float edgeBottom=(float)(-edgeTop-2*scroll*textureDirection);
        vertex(poses,vertices,topR,1f/16,edgeTop,light,.8f,outward);
        vertex(poses,vertices,topBottomR,1f/16,edgeTop,light,.8f,outward);
        vertex(poses,vertices,topBottomL,15f/16,edgeTop,light,.8f,outward);
        vertex(poses,vertices,topL,15f/16,edgeTop,light,.8f,outward);
        vertex(poses,vertices,lowR,1f/16,edgeBottom,light,.8f,outward);
        vertex(poses,vertices,lowBottomR,1f/16,edgeBottom,light,.8f,outward);
        vertex(poses,vertices,lowBottomL,15f/16,edgeBottom,light,.8f,outward);
        vertex(poses,vertices,lowL,15f/16,edgeBottom,light,.8f,outward);
        // The inner face of Create's end band is visible from inside the loop.
        Vec3 inward=outward.scale(-1);
        vertex(poses,vertices,itl,1f/16,endTop,light,.8f,inward);
        vertex(poses,vertices,ibl,1f/16,endBottom,light,.8f,inward);
        vertex(poses,vertices,ibr,15f/16,endBottom,light,.8f,inward);
        vertex(poses,vertices,itr,15f/16,endTop,light,.8f,inward);

    }
    private static Vec3 beltSide(BeltPath path,int index) {
        Vec3 tangent;
        if(index==0) tangent=Vec3.atLowerCornerOf(path.startDirection.getNormal());
        else if(index==BeltPath.SAMPLES) tangent=Vec3.atLowerCornerOf(path.endDirection.getNormal());
        else tangent=path.samples[Math.min(BeltPath.SAMPLES,index+2)].subtract(path.samples[Math.max(0,index-2)]);
        return new Vec3(tangent.z,0,-tangent.x).normalize().scale(7.0/16);
    }
    /** Reuse path cross-sections and merge spans only where the visible shape stays close. */
    private static final class RenderGeometry {
        private final double positionTolerance,frameTolerance,maxSpan;
        final Vec3[] sides=new Vec3[BeltPath.SAMPLES+1],normals=new Vec3[BeltPath.SAMPLES+1];
        final Vec3[] casingLeft=new Vec3[BeltPath.SAMPLES+1],casingRight=new Vec3[BeltPath.SAMPLES+1];
        final int[] indices;
        RenderGeometry(BeltPath path,int detail) {
            positionTolerance=detail<0?.08:detail==0?.04:detail==1?.02:detail==2?.008:.003;
            frameTolerance=detail<0?.14:detail==0?.08:detail==1?.04:detail==2?.015:.007;
            maxSpan=detail<0?4:detail==0?3:detail==1?2:detail==2?1:.5;
            for(int i=0;i<=BeltPath.SAMPLES;i+=2) {
                sides[i]=beltSide(path,i);
                normals[i]=surfaceNormal(path,path.distances[i]);
                Vec3[] casing=casingFrame(path,Vec3.ZERO,path.distances[i],path.samples[i],sides[i],normals[i]);
                Vec3 bottom=casing[0].add(casing[2].scale(-13.0/16));
                casingLeft[i]=bottom.add(casing[1].scale(8.0/7));
                casingRight[i]=bottom.subtract(casing[1].scale(8.0/7));
            }
            var selected=new java.util.ArrayList<Integer>();selected.add(0);
            int first=0;
            while(first<BeltPath.SAMPLES) {
                int last=first+2;
                for(int candidate=first+4;candidate<=BeltPath.SAMPLES;candidate+=2) {
                    if(path.distances[candidate]-path.distances[first]>maxSpan || !fits(path,first,candidate)) break;
                    last=candidate;
                }
                selected.add(last);first=last;
            }
            indices=selected.stream().mapToInt(Integer::intValue).toArray();
        }
        private boolean fits(BeltPath path,int first,int last) {
            double length=path.distances[last]-path.distances[first];
            if(length<1e-9) return false;
            for(int k=first+2;k<last;k+=2) {
                double t=(path.distances[k]-path.distances[first])/length;
                if(path.samples[k].distanceToSqr(path.samples[first].lerp(path.samples[last],t))>positionTolerance*positionTolerance
                    || casingLeft[k].distanceToSqr(casingLeft[first].lerp(casingLeft[last],t))>positionTolerance*positionTolerance
                    || casingRight[k].distanceToSqr(casingRight[first].lerp(casingRight[last],t))>positionTolerance*positionTolerance
                    || sides[k].distanceToSqr(sides[first].lerp(sides[last],t))>frameTolerance*frameTolerance
                    || normals[k].distanceToSqr(normals[first].lerp(normals[last],t))>frameTolerance*frameTolerance) return false;
            }
            return true;
        }
    }
    private static void quad(PoseStack ps,VertexConsumer v,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float va,float vb,int light,float shade) {
        Vec3 n=b.subtract(a).cross(d.subtract(a)).normalize();
        vertex(ps,v,a,1f/16,va,light,shade,n);vertex(ps,v,b,1f/16,vb,light,shade,n);
        vertex(ps,v,c,15f/16,vb,light,shade,n);vertex(ps,v,d,15f/16,va,light,shade,n);
    }
    private static void sideQuad(PoseStack ps,VertexConsumer v,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float va,float vb,int light,float shade,float u0,float u1) {
        Vec3 n=b.subtract(a).cross(d.subtract(a)).normalize();
        vertex(ps,v,a,u0,va,light,shade,n);vertex(ps,v,b,u0,vb,light,shade,n);
        vertex(ps,v,c,u1,vb,light,shade,n);vertex(ps,v,d,u1,va,light,shade,n);
    }
    private static void vertex(PoseStack ps,VertexConsumer v,Vec3 p,float u,float textureV,int light,float shade,Vec3 n) {
        v.addVertex(ps.last(),(float)p.x,(float)p.y,(float)p.z).setColor(shade,shade,shade,1)
            .setUv(u,textureV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(ps.last(),(float)n.x,(float)n.y,(float)n.z);
    }
}
