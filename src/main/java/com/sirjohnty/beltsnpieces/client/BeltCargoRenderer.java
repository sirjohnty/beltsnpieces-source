package com.sirjohnty.beltsnpieces.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sirjohnty.beltsnpieces.*;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Cargo presentation, independent of the belt and casing mesh. */
final class BeltCargoRenderer {
    private BeltCargoRenderer() {}
    static void render(ReinforcedPulleyBlockEntity be,BeltPath path,Vec3 origin,double revealed,
            float partial,PoseStack poses,MultiBufferSource buffers) {
        for(var c:be.cargo) {
            double distance=c.visualPrevious+(c.visualDistance-c.visualPrevious)*partial;
            // The reveal limit only applies while a newly placed belt is building.
            // Cargo can travel beyond a completed pulley while its handoff packet
            // is in flight; keep drawing it until the server removes it here.
            if(revealed<path.length && distance>revealed) continue;
            Vec3 normal=path.surfaceNormal(distance);
            // Keep the item above the local belt surface, including steep slopes.
            double clampedDistance=Math.clamp(distance,0,path.length);
            int sideIndex=(int)Math.round(path.parameterAtDistance(clampedDistance)*BeltPath.SAMPLES);
            Vec3 side=path.sideAtSample(sideIndex).normalize().scale(c.sideOffset);
            Vec3 p=path.atExtendedDistance(distance).subtract(origin).add(normal.scale(.125)).add(side);
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
}
