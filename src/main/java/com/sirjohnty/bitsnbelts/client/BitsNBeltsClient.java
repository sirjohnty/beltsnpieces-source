package com.sirjohnty.bitsnbelts.client;

import com.sirjohnty.bitsnbelts.*;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.simibubi.create.AllItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class BitsNBeltsClient {
    @EventBusSubscriber(modid=BitsNBelts.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void spriteSources(net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent event) {
            event.register(BitsNBelts.id("steel_belt_casing"),SteelBeltSpriteSource.TYPE);
        }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(BitsNBelts.PULLEY_ENTITY.get(),ReinforcedBeltRenderer::new);
        }
    }
    @EventBusSubscriber(modid=BitsNBelts.ID,value=Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent public static void sprintBeltJump(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
            Minecraft mc=Minecraft.getInstance();
            if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)
                || player!=mc.player || !player.isSprinting() || player.getAbilities().flying
                || player.isPassenger() || player.isFallFlying()) return;
            var level=player.level();
            var feet=player.getBoundingBox();
            // Require actual collision support from a reinforced belt under the player.
            BlockPos center=BlockPos.containing(player.getX(),player.getY()-.08,player.getZ());
            if (com.simibubi.create.AllBlocks.BELT.has(level.getBlockState(center))) return;
            for (BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(feet.minX,feet.minY-.16,feet.minZ),
                    BlockPos.containing(feet.maxX,feet.minY,feet.maxZ))) {
                if (!level.hasChunkAt(pos)) continue;
                var blockEntity=level.getBlockEntity(pos);
                ReinforcedPulleyBlockEntity belt=blockEntity instanceof ReinforcedBeltSpanBlockEntity span ? span.beltController()
                    : blockEntity instanceof ReinforcedPulleyBlockEntity pulley ? pulley.root() : null;
                if (belt==null || belt.path()==null) continue;
                var shape=level.getBlockState(pos).getCollisionShape(level,pos).move(pos.getX(),pos.getY(),pos.getZ());
                if (net.minecraft.world.phys.shapes.Shapes.collide(Direction.Axis.Y,feet,java.util.List.of(shape),-.16)<=-.1599) continue;
                BeltPath path=belt.path();
                double distance=BeltRiding.progress(path,player.position());
                Vec3 tangent=BeltRiding.atExtendedDistance(path,distance+.08)
                    .subtract(BeltRiding.atExtendedDistance(path,distance-.08)).normalize();
                double horizontal=Math.hypot(tangent.x,tangent.z);
                if (horizontal<1e-5) return;
                Vec3 direction=new Vec3(tangent.x/horizontal,0,tangent.z/horizontal);
                Vec3 current=player.getDeltaMovement();
                double beltSpeed=Math.clamp(belt.velocity(),-1.5,1.5);
                double sprintAlong=current.x*direction.x+current.z*direction.z;
                double uphill=Math.max(0,(sprintAlong+beltSpeed*horizontal)*tangent.y/horizontal);
                player.setDeltaMovement(current.add(tangent.x*beltSpeed,Math.min(.9,uphill),tangent.z*beltSpeed));
                return;
            }
        }
        private static int placementCost,foldWarningTicks;
        private static BeltPath placementPreviewPath;
        private static ReinforcedBeltItem.Target freePreviewTarget;
        private static BlockPos stagedStart,stagedBase;
        private static InteractionHand stagedHand;
        private static float stagedPitch;
        private static double stagedX,stagedZ;
        private static BlockPos limitStart;
        private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> limitDimension;
        private static ReinforcedBeltItem.Target limitTarget;
        private static BeltPath limitPath;
        private static boolean placementAffordable,placementNeedsShaft,placementHasShaft;
        private static BlockPos dragSpan;
        private static boolean waitForUseRelease,dragNeedsRebase;
        private static double dragCenter,dragInitialHeight,dragHeight,dragBaseHeight,dragX,dragZ,dragSafeHeight,dragSafeX,dragSafeZ;
        private static Vec3 dragAnchor,dragPlaneNormal,dragEye;
        private static double dragReferenceRayY;
        @SubscribeEvent public static void wrenchDrag(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft current=Minecraft.getInstance();
            if(event.isAttack() && stagedStart!=null) {
                stagedStart=null;stagedBase=null;event.setCanceled(true);event.setSwingHand(false);return;
            }
            if(event.isUseItem() && event.getHand()==InteractionHand.MAIN_HAND && current.player!=null && current.level!=null
                && !current.player.isShiftKeyDown()) {
                if(stagedStart!=null) {
                    if(freePreviewTarget!=null) {
                        PacketDistributor.sendToServer(new PlaceGeneratedBeltPayload(stagedHand,stagedStart,freePreviewTarget.pos()));
                        stagedStart=null;stagedBase=null;
                    } else current.player.displayClientMessage(net.minecraft.network.chat.Component.literal("No clear space for a shaft here."),true);
                    event.setCanceled(true);event.setSwingHand(false);return;
                }
                InteractionHand hand=InteractionHand.MAIN_HAND;
                ItemStack held=current.player.getItemInHand(hand);
                if(!held.is(BitsNBelts.REINFORCED_BELT.get())) {hand=InteractionHand.OFF_HAND;held=current.player.getItemInHand(hand);}
                BlockPos first=held.is(BitsNBelts.REINFORCED_BELT.get())?ReinforcedBeltItem.selection(held,current.level):null;
                if(first!=null && freePreviewTarget!=null && freePreviewTarget.generated()) {
                    stagedStart=first;stagedBase=freePreviewTarget.pos();stagedHand=hand;
                    stagedPitch=current.player.getXRot();stagedX=0;stagedZ=0;
                    event.setCanceled(true);event.setSwingHand(false);return;
                }
                if(first!=null && !(current.hitResult instanceof BlockHitResult hit
                    && ReinforcedBeltItem.isShaft(current.level.getBlockState(hit.getBlockPos())))) {
                    event.setCanceled(true);event.setSwingHand(false);return;
                }
            }
            if(event.isAttack()&&dragSpan!=null) {
                dragHeight=dragInitialHeight;dragBaseHeight=dragInitialHeight;dragX=0;dragZ=0;dragSafeHeight=dragInitialHeight;dragSafeX=0;dragSafeZ=0;dragNeedsRebase=true;
                event.setCanceled(true);event.setSwingHand(false);return;
            }
            if(event.isAttack() && dragSpan==null) {
                Minecraft mc=Minecraft.getInstance();
                if(mc.player!=null && mc.level!=null) {
                    InteractionHand hand=InteractionHand.MAIN_HAND;
                    ItemStack item=mc.player.getItemInHand(hand);
                    if(!item.is(BitsNBelts.REINFORCED_BELT.get())) {hand=InteractionHand.OFF_HAND;item=mc.player.getItemInHand(hand);}
                    BlockPos first=item.is(BitsNBelts.REINFORCED_BELT.get())?ReinforcedBeltItem.selection(item,mc.level):null;
                    if(first!=null) {
                        PacketDistributor.sendToServer(new CurveStylePayload(hand,first));
                        event.setCanceled(true);event.setSwingHand(false);return;
                    }
                }
            }
            if(!event.isUseItem()||event.getHand()!=InteractionHand.MAIN_HAND) return;
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null||mc.level==null||!AllItems.WRENCH.isIn(mc.player.getMainHandItem())) return;
            if(waitForUseRelease) {event.setCanceled(true);event.setSwingHand(false);return;}
            if(mc.player.isShiftKeyDown() && dragSpan==null) return;
            if(dragSpan!=null) {event.setCanceled(true);event.setSwingHand(false);return;}
            if(!(mc.hitResult instanceof BlockHitResult hit)||!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof ReinforcedBeltSpanBlockEntity span)) return;
            var owner=span.beltController();if(owner==null||owner.path()==null) return;
            if(owner.hasCasing() && hit.getDirection()!=Direction.UP) return; // Wrench the side to remove casing.
            if(BitsNBeltsConfig.maxBeltAdjustments()==0) {
                event.setCanceled(true);event.setSwingHand(false);
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Belt modifications are disabled by the server configuration."),true);return;
            }
            var path=owner.path();var section=BeltSelection.draggableSection(path,path.nearestDistance(hit.getLocation()));
            if(section==null) {
                event.setCanceled(true);event.setSwingHand(false);
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Grab the belt further from the pulley."),true);return;
            }
            double center=path.parameterAtDistance(section.center());
            Vec3 look=mc.player.getViewVector(1),normal=new Vec3(look.x,0,look.z).normalize();
            if(normal.lengthSqr()<1e-8) return;
            Vec3 anchor=path.point(center);
            double reference=BeltDragWorld.rayHeight(mc.player.getEyePosition(),look,anchor,normal);
            if(!Double.isFinite(reference)) return;
            dragSpan=hit.getBlockPos().immutable();dragCenter=center;dragInitialHeight=0;dragHeight=0;dragX=0;dragZ=0;dragEye=mc.player.getEyePosition();
            dragAnchor=anchor;dragPlaneNormal=normal;dragReferenceRayY=reference;dragBaseHeight=0;dragSafeHeight=0;dragSafeX=0;dragSafeZ=0;dragNeedsRebase=false;
            event.setCanceled(true);event.setSwingHand(false);
        }
        @SubscribeEvent public static void dragScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null || mc.level==null) return;
            if(stagedStart!=null && !mc.player.isShiftKeyDown() && event.getScrollDeltaY()!=0) {
                Vec3 direction=scrollDirection(mc.player.getViewVector(1));
                // A generated shaft occupies a whole block: advance the
                // snapped endpoint one grid cell per scroll step.
                int dx=(int)Math.round(direction.x),dz=(int)Math.round(direction.z);
                int steps=(int)Math.copySign(Math.max(1,Math.round(Math.abs(event.getScrollDeltaY()))),event.getScrollDeltaY());
                stagedX+=dx*steps;
                stagedZ+=dz*steps;
                event.setCanceled(true);return;
            }
            if(dragSpan==null && mc.player.isShiftKeyDown() && event.getScrollDeltaY()!=0) {
                InteractionHand hand=InteractionHand.MAIN_HAND;
                ItemStack selected=mc.player.getItemInHand(hand);
                if(!selected.is(BitsNBelts.REINFORCED_BELT.get())) {hand=InteractionHand.OFF_HAND;selected=mc.player.getItemInHand(hand);}
                BlockPos first=selected.is(BitsNBelts.REINFORCED_BELT.get())?ReinforcedBeltItem.selection(selected,mc.level):null;
                if(first!=null && mc.level.hasChunkAt(first)) {
                    var target=mc.hitResult instanceof BlockHitResult hit && hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS
                        ? ReinforcedBeltItem.target(mc.level,first,hit.getBlockPos(),hit.getDirection(),ReinforcedBeltItem.shaftAxisChoice(selected))
                        : ReinforcedBeltItem.airTarget(mc.level,first,mc.player,ReinforcedBeltItem.shaftAxisChoice(selected));
                    if(target!=null && target.generated() && !first.equals(target.pos())) {
                        int choice=ReinforcedBeltItem.shaftAxisChoice(selected);
                        if(choice==0) {
                            Vec3 delta=Vec3.atCenterOf(target.pos()).subtract(Vec3.atCenterOf(first));
                            Direction current=BeltPath.tangent(target.shaft().getValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS),delta,false);
                            choice=ReinforcedBeltItem.endDirectionChoice(current);
                        }
                        int next=Math.floorMod(choice-1+(event.getScrollDeltaY()>0?1:-1),4)+1;
                        ReinforcedBeltItem.setShaftAxisChoice(selected,next);
                        PacketDistributor.sendToServer(new ShaftAxisPayload(hand,first,next));
                        event.setCanceled(true);return;
                    }
                }
            }
            if(dragSpan==null || !mc.options.keyUse.isDown()) return;
            event.setCanceled(true);
            Vec3 look=mc.player.getViewVector(1);
            double horizontal=Math.hypot(look.x,look.z),amount=event.getScrollDeltaY()/4.0;
            if(horizontal<1e-8 || !Double.isFinite(amount)) return;
            // Snap the camera direction to the nearest of sixteen directions.
            Vec3 direction=scrollDirection(look);
            double dx=direction.x*Math.signum(amount),dz=direction.z*Math.signum(amount);
            // Limit travel along that same direction without changing the other component.
            double along=dragX*dx+dragZ*dz;
            double remaining=-along+Math.sqrt(Math.max(0,along*along+16-dragX*dragX-dragZ*dragZ));
            double step=Math.min(Math.abs(amount),Math.max(0,remaining));
            dragX+=dx*step;dragZ+=dz*step;
        }
        private static net.createmod.catnip.theme.Color previewColor(BitsNBeltsConfig.PreviewStyle style) {
            return new net.createmod.catnip.theme.Color(BitsNBeltsConfig.previewColor(style),false)
                .setAlpha(BitsNBeltsConfig.previewOpacity(style));
        }
        private static Vec3 scrollDirection(Vec3 look) {
            if(Math.hypot(look.x,look.z)<1e-8) return Vec3.ZERO;
            double angle=Math.round(Math.atan2(look.z,look.x)/(Math.PI/8))*(Math.PI/8);
            return new Vec3(Math.cos(angle),0,Math.sin(angle));
        }
        @SubscribeEvent public static void beltCostHud(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
            Minecraft mc=Minecraft.getInstance();
            if(placementCost<=0 || mc.options.hideGui || mc.player==null) return;
            var gui=event.getGuiGraphics();String count=Integer.toString(placementCost);
            int width=18+mc.font.width(count)+(placementNeedsShaft?26:0);
            int x=gui.guiWidth()/2-width/2,y=gui.guiHeight()/2+12;
            gui.renderItem(new ItemStack(BitsNBelts.REINFORCED_BELT.get()),x,y);
            gui.drawString(mc.font,count,x+18,y+5,placementAffordable?0xFFFFFF:0xFF5555,true);
            if(placementNeedsShaft) {
                int shaftX=x+18+mc.font.width(count)+5;
                gui.renderItem(com.simibubi.create.AllBlocks.SHAFT.asStack(),shaftX,y);
                gui.drawString(mc.font,"1",shaftX+18,y+5,placementHasShaft?0xFFFFFF:0xFF5555,true);
            }
            if(placementPreviewPath!=null) {
                drawCurveIndicator(gui,gui.guiWidth()/2,y+25,placementPreviewPath);
                String name=ReinforcedBeltItem.styleName(placementPreviewPath.curveStyle);
                gui.drawString(mc.font,name,gui.guiWidth()/2-mc.font.width(name)/2,y+72,0xFFFFFF,true);
            }
        }
        private static void drawCurveIndicator(net.minecraft.client.gui.GuiGraphics gui,int centerX,int top,BeltPath path) {
            // Draw the actual preview from above, uniformly fitted to a small HUD box.
            double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
            double minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
            for(Vec3 point:path.samples) {
                minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);
                minZ=Math.min(minZ,point.z);maxZ=Math.max(maxZ,point.z);
            }
            double width=maxX-minX,height=maxZ-minZ;
            double scale=Math.min(width>1e-8?64/width:Double.POSITIVE_INFINITY,height>1e-8?30/height:Double.POSITIVE_INFINITY);
            if(!Double.isFinite(scale)) scale=1;
            double midX=(minX+maxX)*.5,midZ=(minZ+maxZ)*.5;
            int centerY=top+20;
            int firstX=centerX+(int)Math.round((path.start.x-midX)*scale);
            int firstY=centerY+(int)Math.round((path.start.z-midZ)*scale);
            int endX=centerX+(int)Math.round((path.end.x-midX)*scale);
            int endY=centerY+(int)Math.round((path.end.z-midZ)*scale);
            drawDot(gui,firstX,firstY,5,0xFF151515);
            drawDot(gui,endX,endY,5,0xFF151515);
            for(Vec3 point:path.samples) {
                int px=centerX+(int)Math.round((point.x-midX)*scale);
                int py=centerY+(int)Math.round((point.z-midZ)*scale);
                gui.fill(px-1,py-1,px+2,py+2,0xFFF02626);
            }
        }
        private static void drawDot(net.minecraft.client.gui.GuiGraphics gui,int x,int y,int radius,int color) {
            for(int dy=-radius;dy<=radius;dy++) {
                int half=(int)Math.floor(Math.sqrt(radius*radius-dy*dy));
                gui.fill(x-half,y+dy,x+half+1,y+dy+1,color);
            }
        }
        @SubscribeEvent public static void highlight(RenderHighlightEvent.Block event) {
            Minecraft mc=Minecraft.getInstance();
            if(mc.level==null || !(mc.level.getBlockEntity(event.getTarget().getBlockPos()) instanceof ReinforcedBeltSpanBlockEntity span)) return;
            ReinforcedPulleyBlockEntity owner=span.beltController();
            if(owner==null || owner.path()==null) return; // Fitted local box remains available if controller is unloaded.
            event.setCanceled(true);
            var path=owner.path();
            var section=BeltSelection.draggableSection(path,path.nearestDistance(event.getTarget().getLocation()));
            boolean draggable=section!=null && mc.player!=null && (!owner.hasCasing() || event.getTarget().getDirection()==Direction.UP)
                && BitsNBeltsConfig.maxBeltAdjustments()>owner.elevationEdits.size()
                && AllItems.WRENCH.isIn(mc.player.getMainHandItem());
            var frames=section==null ? BeltSelection.section(path,event.getTarget().getLocation()) : BeltSelection.frames(path,section.start(),section.end());
            PoseStack pose=event.getPoseStack();
            Vec3 camera=event.getCamera().getPosition();
            VertexConsumer lines=event.getMultiBufferSource().getBuffer(RenderType.lines());
            var outlineStyle=draggable?BitsNBeltsConfig.PreviewStyle.WRENCH_HOVER:BitsNBeltsConfig.PreviewStyle.SELECTION;
            for(int i=1;i<frames.size();i++) {
                var a=frames.get(i-1);var b=frames.get(i);
                line(pose,lines,a.topLeft(),b.topLeft(),camera,outlineStyle);
                line(pose,lines,a.topRight(),b.topRight(),camera,outlineStyle);
                line(pose,lines,a.bottomLeft(),b.bottomLeft(),camera,outlineStyle);
                line(pose,lines,a.bottomRight(),b.bottomRight(),camera,outlineStyle);
            }
            for(var frame:java.util.List.of(frames.getFirst(),frames.getLast())) {
                line(pose,lines,frame.topLeft(),frame.topRight(),camera,outlineStyle);
                line(pose,lines,frame.bottomLeft(),frame.bottomRight(),camera,outlineStyle);
                line(pose,lines,frame.topLeft(),frame.bottomLeft(),camera,outlineStyle);
                line(pose,lines,frame.topRight(),frame.bottomRight(),camera,outlineStyle);
            }
        }
        private static void line(PoseStack pose,VertexConsumer lines,Vec3 from,Vec3 to,Vec3 camera,BitsNBeltsConfig.PreviewStyle style) {
            Vec3 normal=to.subtract(from).normalize(),a=from.subtract(camera),b=to.subtract(camera);
            lines.addVertex(pose.last(),(float)a.x,(float)a.y,(float)a.z).setColor(((BitsNBeltsConfig.previewColor(style)>>16)&255)/255f,
                ((BitsNBeltsConfig.previewColor(style)>>8)&255)/255f,
                (BitsNBeltsConfig.previewColor(style)&255)/255f,
                BitsNBeltsConfig.previewOpacity(style))
                .setNormal(pose.last(),(float)normal.x,(float)normal.y,(float)normal.z);
            lines.addVertex(pose.last(),(float)b.x,(float)b.y,(float)b.z).setColor(((BitsNBeltsConfig.previewColor(style)>>16)&255)/255f,
                ((BitsNBeltsConfig.previewColor(style)>>8)&255)/255f,
                (BitsNBeltsConfig.previewColor(style)&255)/255f,
                BitsNBeltsConfig.previewOpacity(style))
                .setNormal(pose.last(),(float)normal.x,(float)normal.y,(float)normal.z);
        }
        @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
            placementCost=0;placementPreviewPath=null;freePreviewTarget=null;
            if(foldWarningTicks>0) foldWarningTicks--;
            Minecraft mc=Minecraft.getInstance();
            if(mc.level==null||mc.player==null) {dragSpan=null;waitForUseRelease=false;limitStart=null;limitTarget=null;limitPath=null;limitDimension=null;return;}
            if(mc.isPaused()) return;
            if(waitForUseRelease&&!mc.options.keyUse.isDown()) waitForUseRelease=false;
            if(dragSpan!=null) {
                if(BitsNBeltsConfig.maxBeltAdjustments()==0 || !AllItems.WRENCH.isIn(mc.player.getMainHandItem())) {dragSpan=null;return;}
                if(!mc.options.keyUse.isDown()) {
                    if(Math.abs(dragHeight-dragInitialHeight)>.01 || Math.hypot(dragX,dragZ)>1e-4) PacketDistributor.sendToServer(new DragBeltPayload(dragSpan,dragCenter,dragHeight,dragX,dragZ));
                    dragSpan=null;return;
                }
                boolean movementInput=mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                    || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()
                    || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
                if(movementInput) {
                    dragNeedsRebase=true;
                } else {
                    Vec3 look=mc.player.getViewVector(1);
                    if(dragNeedsRebase) {
                        Vec3 eye=mc.player.getEyePosition();
                        Vec3 towards=dragAnchor.add(dragX,0,dragZ).subtract(eye);
                        Vec3 normal=new Vec3(towards.x,0,towards.z).normalize();
                        double reference=BeltDragWorld.rayHeight(eye,look,dragAnchor,normal);
                        if(normal.lengthSqr()>1e-8 && Double.isFinite(reference)) {
                            dragEye=eye;dragPlaneNormal=normal;dragReferenceRayY=reference;
                            dragBaseHeight=dragHeight;dragNeedsRebase=false;
                        }
                    }
                    if(!dragNeedsRebase) {
                        double rayY=BeltDragWorld.rayHeight(dragEye,look,dragAnchor,dragPlaneNormal);
                        if(Double.isFinite(rayY)) {
                            double movement=rayY-dragReferenceRayY;
                            // Snap world height to quarter blocks, but do not jump on grab/rebase.
                            if(Math.abs(movement)<1e-4) dragHeight=dragBaseHeight;
                            else dragHeight=Math.clamp(Math.round((dragAnchor.y+dragBaseHeight+movement)*4)/4.0-dragAnchor.y,-4,4);
                        }
                    }
                }
                boolean blockedFold=false, blockedReshape=false;
                if(mc.level.getBlockEntity(dragSpan) instanceof ReinforcedBeltSpanBlockEntity span) {
                    var owner=span.beltController();
                    if(owner!=null&&owner.path()!=null) {
                        var edits=new java.util.ArrayList<>(owner.elevationEdits);
                        edits.add(new BeltElevation.Control(dragCenter,dragHeight,dragX,dragZ));
                        var p=new BeltPath(owner.getBlockPos(),owner.peer,owner.startDirection,owner.endDirection,owner.curveStyle,owner.pathRevision,owner.elevationCenter,owner.elevationHeight,edits);
                        if(!BeltGeometryValidation.returnRunDoesNotFold(p)) {
                            dragHeight=dragSafeHeight;dragX=dragSafeX;dragZ=dragSafeZ;blockedFold=true;foldWarningTicks=8;
                            edits.set(edits.size()-1,new BeltElevation.Control(dragCenter,dragHeight,dragX,dragZ));
                            p=new BeltPath(owner.getBlockPos(),owner.peer,owner.startDirection,owner.endDirection,owner.curveStyle,owner.pathRevision,owner.elevationCenter,owner.elevationHeight,edits);
                        } else {dragSafeHeight=dragHeight;dragSafeX=dragX;dragSafeZ=dragZ;}
                        blockedReshape=!owner.canPreviewReshape(mc.player,p);
                        Vec3 tangent=p.point(Math.min(1,dragCenter+.005)).subtract(p.point(Math.max(0,dragCenter-.005))).normalize();
                        Vec3 markerSide=new Vec3(tangent.z,0,-tangent.x).normalize();
                        Vec3 normal=markerSide.cross(tangent).normalize();
                        if(normal.y<0) normal=normal.scale(-1);
                        Vec3 cross=p.point(dragCenter).add(normal.scale(.08));
                        Vec3 cameraDirection=scrollDirection(mc.player.getViewVector(1));
                        if(cameraDirection.lengthSqr()>0) {
                            Vec3 start=cross.add(0,.1,0),tip=start.add(cameraDirection.scale(1.0));
                            Vec3 arrowSide=new Vec3(-cameraDirection.z,0,cameraDirection.x),base=tip.subtract(cameraDirection.scale(.25));
                            Outliner.getInstance().showLine("bitsnbelts_scroll_direction",start,tip).colored(previewColor(BitsNBeltsConfig.PreviewStyle.WRENCH_DIRECTION)).lineWidth(.08f);
                            Outliner.getInstance().showLine("bitsnbelts_scroll_direction_left",tip,base.add(arrowSide.scale(.16))).colored(previewColor(BitsNBeltsConfig.PreviewStyle.WRENCH_DIRECTION)).lineWidth(.08f);
                            Outliner.getInstance().showLine("bitsnbelts_scroll_direction_right",tip,base.subtract(arrowSide.scale(.16))).colored(previewColor(BitsNBeltsConfig.PreviewStyle.WRENCH_DIRECTION)).lineWidth(.08f);
                        }
                        var dragStyle=(foldWarningTicks>0 || blockedReshape) && (mc.level.getGameTime()/3)%2==0
                            ?BitsNBeltsConfig.PreviewStyle.PLACEMENT_INVALID:BitsNBeltsConfig.PreviewStyle.WRENCH_PREVIEW;
                        for(int i=0;i<BeltPath.SAMPLES;i+=4) {
                            Vec3 a=p.samples[i],b=p.samples[Math.min(i+4,BeltPath.SAMPLES)];
                            Vec3 side=new Vec3(b.z-a.z,0,a.x-b.x).normalize().scale(7.0/16);
                            Outliner.getInstance().showLine("bitsnbelts_drag_l_"+i,a.add(side),b.add(side)).colored(previewColor(dragStyle)).lineWidth(.06f);
                            Outliner.getInstance().showLine("bitsnbelts_drag_r_"+i,a.subtract(side),b.subtract(side)).colored(previewColor(dragStyle)).lineWidth(.06f);
                        }
                    }
                }
                Vec3 marker=dragAnchor.add(dragX,dragHeight,dragZ);
                String message=blockedFold?"Belt cannot bend any tighter here.":blockedReshape?"Cannot reshape belt here; check clearance, length and loaded chunks.":String.format(java.util.Locale.ROOT,"Belt height: Y %.4f",marker.y);
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(message),true);
                return;
            }
            ItemStack stack=mc.player.getMainHandItem();
            if(!stack.is(BitsNBelts.REINFORCED_BELT.get())) stack=mc.player.getOffhandItem();
            if(!stack.is(BitsNBelts.REINFORCED_BELT.get())) {limitStart=null;limitTarget=null;limitPath=null;return;}
            BlockPos first=ReinforcedBeltItem.selection(stack,mc.level);
            if(first==null||!mc.level.hasChunkAt(first)) {limitStart=null;limitTarget=null;limitPath=null;stagedStart=null;stagedBase=null;return;}
            if(stagedStart!=null && !stagedStart.equals(first)) {stagedStart=null;stagedBase=null;}
            if(!first.equals(limitStart) || !mc.level.dimension().equals(limitDimension)) {
                limitStart=first.immutable();limitDimension=mc.level.dimension();limitTarget=null;limitPath=null;
            }
            Outliner.getInstance().showAABB("bitsnbelts_source",new AABB(first).inflate(.03)).colored(previewColor(BitsNBeltsConfig.PreviewStyle.PLACEMENT_SOURCE)).lineWidth(1/24f);
            boolean hitBlock=mc.hitResult instanceof BlockHitResult h && h.getType()!=net.minecraft.world.phys.HitResult.Type.MISS;
            BlockHitResult hit=hitBlock?(BlockHitResult)mc.hitResult:null;
            if(stagedStart==null && hitBlock && first.equals(hit.getBlockPos())) {limitTarget=null;limitPath=null;return;}
            ReinforcedBeltItem.Target target;
            if(stagedStart!=null) {
                int x=stagedBase.getX()+(int)Math.round(stagedX),z=stagedBase.getZ()+(int)Math.round(stagedZ);
                int y=stagedBase.getY()+(int)Math.round((stagedPitch-mc.player.getXRot())/10.0);
                target=ReinforcedBeltItem.generatedTarget(mc.level,first,new BlockPos(x,y,z),ReinforcedBeltItem.shaftAxisChoice(stack));
            } else if(hitBlock && ReinforcedBeltItem.isShaft(mc.level.getBlockState(hit.getBlockPos()))) {
                target=ReinforcedBeltItem.target(mc.level,first,hit.getBlockPos(),hit.getDirection(),ReinforcedBeltItem.shaftAxisChoice(stack));
            } else {
                BlockPos aimed=hitBlock?hit.getBlockPos().relative(hit.getDirection())
                    :BlockPos.containing(mc.player.getEyePosition().add(mc.player.getViewVector(1).scale(ReinforcedBeltItem.AIR_REACH)));
                target=ReinforcedBeltItem.generatedTarget(mc.level,first,new BlockPos(aimed.getX(),first.getY(),aimed.getZ()),ReinforcedBeltItem.shaftAxisChoice(stack));
            }
            BeltPath path=target==null || first.equals(target.pos())?null:ReinforcedBeltItem.pathWithTarget(mc.level,first,target.pos(),target.shaft(),false,
                target.generated()?ReinforcedBeltItem.generatedEndFlip(first,target,ReinforcedBeltItem.shaftAxisChoice(stack)):ReinforcedBeltItem.flipEnd(mc.level,first,target.pos(),hit.getLocation()),ReinforcedBeltItem.curveStyle(stack));
            boolean overLimit=path!=null && path.length>ReinforcedBeltItem.maxLength();
            if(path!=null && !overLimit) {limitTarget=null;limitPath=null;}
            else if(overLimit && limitPath==null) {limitTarget=target;limitPath=path;}
            if(limitPath!=null && (overLimit || path==null)) {target=limitTarget;path=limitPath;overLimit=true;}
            if(path==null) return;
            freePreviewTarget=target.generated()?target:null;
            placementCost=ReinforcedBeltItem.beltCost(path);
            placementPreviewPath=path;
            placementAffordable=mc.player.isCreative() || ReinforcedBeltItem.availableBelts(mc.player)>=placementCost;
            placementNeedsShaft=target.generated();
            placementHasShaft=ReinforcedBeltItem.hasShaft(mc.player);
            boolean clearShaft=!target.generated() || (placementHasShaft
                && mc.level.isUnobstructed(target.shaft(),target.pos(),net.minecraft.world.phys.shapes.CollisionContext.of(mc.player)));
            boolean valid=!overLimit && placementAffordable && clearShaft && ReinforcedBeltItem.invalid(mc.level,first,target.pos(),path)==null;
            var placementStyle=valid?BitsNBeltsConfig.PreviewStyle.PLACEMENT_VALID:BitsNBeltsConfig.PreviewStyle.PLACEMENT_INVALID;
            int color=BitsNBeltsConfig.previewColor(placementStyle);
            Outliner.getInstance().showAABB("bitsnbelts_target",new AABB(target.pos()).inflate(.025)).colored(previewColor(placementStyle)).lineWidth(1/32f);
            if(target.generated()) {
                Vec3 center=Vec3.atCenterOf(target.pos());
                if(stagedStart!=null) {
                    Vec3 pointing=scrollDirection(mc.player.getViewVector(1)),tip=center.add(pointing.scale(1.0));
                    Vec3 wing=new Vec3(-pointing.z,0,pointing.x).scale(.16),base=tip.subtract(pointing.scale(.25));
                    Outliner.getInstance().showLine("bitsnbelts_place_scroll",center,tip).colored(previewColor(BitsNBeltsConfig.PreviewStyle.WRENCH_DIRECTION)).lineWidth(.08f);
                    Outliner.getInstance().showLine("bitsnbelts_place_scroll_l",tip,base.add(wing)).colored(previewColor(BitsNBeltsConfig.PreviewStyle.WRENCH_DIRECTION)).lineWidth(.08f);
                    Outliner.getInstance().showLine("bitsnbelts_place_scroll_r",tip,base.subtract(wing)).colored(previewColor(BitsNBeltsConfig.PreviewStyle.WRENCH_DIRECTION)).lineWidth(.08f);
                }
                Direction.Axis axis=target.shaft().getValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS);
                Vec3 extent=axis==Direction.Axis.X?new Vec3(.48,0,0):new Vec3(0,0,.48);
                Outliner.getInstance().showLine("bitsnbelts_new_shaft",center.subtract(extent),center.add(extent)).colored(previewColor(placementStyle)).lineWidth(.09f);
                Direction approach=path.endDirection;
                Vec3 pointing=Vec3.atLowerCornerOf(approach.getNormal()), tip=center.add(pointing.scale(.48));
                Vec3 wing=new Vec3(-pointing.z,0,pointing.x).scale(.13), base=tip.subtract(pointing.scale(.18));
                Outliner.getInstance().showLine("bitsnbelts_shaft_arrow",center,tip).colored(previewColor(placementStyle)).lineWidth(.045f);
                Outliner.getInstance().showLine("bitsnbelts_shaft_arrow_l",tip,base.add(wing)).colored(previewColor(placementStyle)).lineWidth(.045f);
                Outliner.getInstance().showLine("bitsnbelts_shaft_arrow_r",tip,base.subtract(wing)).colored(previewColor(placementStyle)).lineWidth(.045f);
            }
            // Thin rails preserve the complete curve and full width while Create-style
            // dust gives the guide a softer, less grid-like appearance.
            for(int i=0;i<BeltPath.SAMPLES;i+=4) {
                int j=Math.min(i+4,BeltPath.SAMPLES);
                Vec3 a=path.samples[i],b=path.samples[j];
                Vec3 sa=previewSide(path,i),sb=previewSide(path,j);
                Outliner.getInstance().showLine("bitsnbelts_left_"+i,a.add(sa),b.add(sb)).colored(previewColor(placementStyle)).lineWidth(.025f);
                Outliner.getInstance().showLine("bitsnbelts_right_"+i,a.subtract(sa),b.subtract(sb)).colored(previewColor(placementStyle)).lineWidth(.025f);
            }
            var dust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f),.7f);
            int particles=Math.min(8,Math.max(2,(int)Math.ceil(path.length/4)));
            for(int i=0;i<particles;i++) {
                double d=mc.level.random.nextDouble()*path.length;
                Vec3 p=path.atDistance(d),before=path.atDistance(Math.max(0,d-.04)),after=path.atDistance(Math.min(path.length,d+.04));
                Vec3 along=after.subtract(before),side=new Vec3(along.z,0,-along.x).normalize().scale(mc.level.random.nextBoolean()?7.0/16:-7.0/16);
                Vec3 point=p.add(side).add(0,.025,0);
                mc.level.addParticle(dust,point.x,point.y,point.z,0,0,0);
            }
            // Chevrons show the selected start-to-end direction (actual travel follows rotation).
            for(double d=1;d<path.length;d+=2) {
                Vec3 p=path.atDistance(d),before=path.atDistance(d-.3),dir=p.subtract(before).normalize();
                Vec3 side=new Vec3(dir.z,0,-dir.x).scale(.18);
                Outliner.getInstance().showLine("bitsnbelts_arrow_l_"+d,before.add(side),p).colored(previewColor(placementStyle)).lineWidth(.025f);
                Outliner.getInstance().showLine("bitsnbelts_arrow_r_"+d,before.subtract(side),p).colored(previewColor(placementStyle)).lineWidth(.025f);
            }
        }
        private static Vec3 previewSide(BeltPath path,int i) {
            Vec3 tangent=i==0?Vec3.atLowerCornerOf(path.startDirection.getNormal()):i==BeltPath.SAMPLES?Vec3.atLowerCornerOf(path.endDirection.getNormal()):
                path.samples[Math.min(BeltPath.SAMPLES,i+2)].subtract(path.samples[Math.max(0,i-2)]);
            return new Vec3(tangent.z,0,-tangent.x).normalize().scale(7.0/16);
        }
    }
}
