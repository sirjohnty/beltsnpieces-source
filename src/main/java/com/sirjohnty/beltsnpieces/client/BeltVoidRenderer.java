package com.sirjohnty.beltsnpieces.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllBlocks;
import com.sirjohnty.beltsnpieces.BeltVoidBlock;
import com.sirjohnty.beltsnpieces.BeltVoidBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

public final class BeltVoidRenderer implements BlockEntityRenderer<BeltVoidBlockEntity> {
    // Roof underside is y=7.75. Recess the hinge by .25 pixels so the seam
    // stays covered while rotating; keep the closed lower edge at y=-3.
    private static final double HINGE_Y = 8.0 / 16;
    private static final float FLAP_HEIGHT_SCALE = 11f / 10;
    public BeltVoidRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(BeltVoidBlockEntity be, float partial, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        float yaw = switch (be.getBlockState().getValue(BeltVoidBlock.FACING)) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        };
        float angle = Mth.lerp(partial, be.previousFlap, be.flap);
        poses.pushPose();
        poses.translate(.5, 0, .5);
        poses.mulPose(Axis.YP.rotationDegrees(yaw));
        poses.translate(-.5, 0, -.5);
        for (int i = 0; i < 4; i++) {
            poses.pushPose();
            poses.translate((3.8 + 2.8 * i) / 16, HINGE_Y, 2.1 / 16);
            poses.mulPose(Axis.XP.rotationDegrees(-angle));
            poses.scale(2.6f / 3.05f, FLAP_HEIGHT_SCALE, .7f);
            // Create's flap partial is centered at x=12.5, hinged at y=10, z=9.5.
            poses.translate(-12.5 / 16, -10.0 / 16, -9.5 / 16);
            CachedBuffers.partial(AllPartialModels.FUNNEL_FLAP, AllBlocks.BRASS_FUNNEL.getDefaultState())
                .light(light).renderInto(poses, buffers.getBuffer(RenderType.solid()));
            poses.popPose();
        }
        // The exit is outside the intake's complete swing envelope.
        float exitAngle = Mth.lerp(partial, be.previousExitFlap, be.exitFlap);
        for (int i = 0; i < 4; i++) {
            poses.pushPose();
            poses.translate((3.8 + 2.8 * i) / 16, HINGE_Y, 13.9 / 16);
            poses.mulPose(Axis.XP.rotationDegrees(-exitAngle));
            poses.scale(2.6f / 3.05f, FLAP_HEIGHT_SCALE, .7f);
            poses.translate(-12.5 / 16, -10.0 / 16, -9.5 / 16);
            CachedBuffers.partial(AllPartialModels.FUNNEL_FLAP, AllBlocks.BRASS_FUNNEL.getDefaultState())
                .light(light).renderInto(poses, buffers.getBuffer(RenderType.solid()));
            poses.popPose();
        }
        poses.popPose();
    }
}
