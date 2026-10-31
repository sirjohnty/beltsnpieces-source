package com.sirjohnty.beltsnpieces.client;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.sirjohnty.beltsnpieces.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in checks in an empty, already-loaded volume; never shipped in release JARs. */
@EventBusSubscriber(modid=BeltsNPieces.ID)
public final class DevelopmentReadiness {
    private static int delay;
    private static Runnable cleanup;
    private static int cleanupDelay;
    private static ReinforcedPulleyBlockEntity networkFixture;
    private static final Path MARKER=Path.of("readiness-check.once");
    private static void require(boolean value,String message) {if(!value) throw new AssertionError(message);}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(cleanup!=null) {
            if(--cleanupDelay<=0) {cleanup.run();cleanup=null;networkFixture=null;LogUtils.getLogger().info("READINESS fixtures cleaned");}
            else if(cleanupDelay%10==0 && networkFixture!=null) {
                var handler=networkFixture.itemHandler();
                handler.extractItem(0,64,false);
                handler.insertItem(0,new ItemStack(Items.DIAMOND,cleanupDelay/10),false);
            }
        }
        if(FMLEnvironment.production || !Files.isRegularFile(MARKER)
            || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        if(++delay<40) return;
        delay=0;
        try {
            Files.move(MARKER,Path.of("readiness-check.consumed"),StandardCopyOption.REPLACE_EXISTING);
            run(event.getServer().getPlayerList().getPlayers().getFirst());
        } catch(Throwable error) {LogUtils.getLogger().error("READINESS FAILED",error);}
    }
    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        if(cleanup!=null) {cleanup.run();cleanup=null;networkFixture=null;}
    }

    private static void run(net.minecraft.server.level.ServerPlayer realPlayer) {
        var level=realPlayer.serverLevel();
        // Stay inside the player's loaded chunk; never load/generate another chunk.
        int x=(realPlayer.blockPosition().getX()>>4)*16+3;
        int z=(realPlayer.blockPosition().getZ()>>4)*16+7;
        int y=Math.min(level.getMaxBuildHeight()-12,realPlayer.blockPosition().getY()+32);
        BlockPos a=new BlockPos(x,y,z),b=a.offset(6,0,0);
        AABB region=new AABB(Vec3.atLowerCornerOf(a.offset(-2,-5,-3)),Vec3.atLowerCornerOf(b.offset(3,6,4)));
        List<BlockPos> volume=new ArrayList<>();
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(region.minX,region.minY,region.minZ),
                BlockPos.containing(region.maxX,region.maxY,region.maxZ))) {
            require(level.hasChunkAt(p)&&level.getBlockState(p).isAir()&&level.getFluidState(p).isEmpty(),"Readiness volume must be loaded empty air: "+p);
            volume.add(p.immutable());
        }
        require(level.getEntities(null,region).isEmpty(),"Readiness volume contains entities");
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("0669b257-d024-433d-b15e-3b78fc7d6883"),"BeltsPiecesTests"));
        player.getInventory().clearContent();
        player.setPos(x,y+1,z);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var shaft=AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS,Direction.Axis.Z);
        var item=BeltsNPieces.REINFORCED_BELT.get();
        boolean passed=false;
        try {
            level.setBlock(a,shaft,3);level.setBlock(b,shaft,3);
            var stack=new ItemStack(item,32);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var target=new ReinforcedBeltItem.Target(b,shaft,false);
            var path=new BeltPath(a,b,Direction.EAST,Direction.EAST,0);
            int cost=ReinforcedBeltItem.beltCost(path);
            BlockPos obstacle=a.offset(3,0,0);level.setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);
            require(item.place(level,player,stack,a,target,Direction.UP,Vec3.atCenterOf(b)).consumesAction()==false,"Obstructed placement accepted");
            require(stack.getCount()==32&&level.getBlockState(a).equals(shaft),"Rejected placement changed inventory/source");
            level.removeBlock(obstacle,false);
            stack.setCount(1);
            require(!item.place(level,player,stack,a,target,Direction.UP,Vec3.atCenterOf(b)).consumesAction(),"Unaffordable placement accepted");
            stack.setCount(32);
            require(item.place(level,player,stack,a,target,Direction.UP,Vec3.atCenterOf(b)).consumesAction(),"Valid placement rejected");
            require(stack.getCount()==32-cost,"Incorrect belt cost");
            var owner=(ReinforcedPulleyBlockEntity)level.getBlockEntity(a);
            require(owner!=null&&owner.routeLoaded()&&owner.other()!=null,"Endpoints not connected");
            BlockPos owned=owner.path().surface().cells.keySet().iterator().next();
            long before=System.nanoTime();int membership=0;
            for(int i=0;i<10000;i++) if(new HashSet<>(owner.path().surface().cells.keySet()).contains(owned)) membership++;
            long oldNanos=System.nanoTime()-before;before=System.nanoTime();
            for(int i=0;i<10000;i++) if(owner.ownsSurfacePosition(owned)) membership++;
            long newNanos=System.nanoTime()-before;
            require(membership==20000,"Ownership query mismatch");
            LogUtils.getLogger().info("READINESS ownership benchmark (10000 queries): set-copy={}us, direct={}us",oldNanos/1000,newNanos/1000);
            var handler=owner.itemHandler();
            require(handler.insertItem(0,new ItemStack(Items.DIAMOND,11),true).isEmpty()&&owner.cargo.isEmpty(),"Simulated insert mutated cargo");
            require(handler.insertItem(0,new ItemStack(Items.DIAMOND,11),false).isEmpty(),"Cargo insertion failed");
            require(handler.extractItem(0,4,true).getCount()==4&&owner.cargo.getFirst().stack.getCount()==11,"Simulated extraction mutated cargo");
            require(handler.extractItem(0,4,false).getCount()==4&&owner.cargo.getFirst().stack.getCount()==7,"Cargo extraction lost items");
            require(owner.setBeltColor(5)&&owner.setCasing(BeltCasings.BRASS),"Dye/casing application failed");
            var saved=owner.saveWithoutMetadata(level.registryAccess());
            owner.loadWithComponents(saved,level.registryAccess());
            require(owner.cargo.size()==1&&owner.cargo.getFirst().stack.getCount()==7&&owner.paidBelts==cost,"Save/load changed cargo/refund");
            require(owner.beltColor()==5&&owner.hasCasing(),"Save/load lost dye/casing");
            var malformed=saved.copy();malformed.putDouble("ElevationCenter",Double.NaN);malformed.putDouble("ElevationHeight",Double.POSITIVE_INFINITY);
            owner.loadWithComponents(malformed,level.registryAccess());
            require(Double.isFinite(owner.elevationCenter)&&Double.isFinite(owner.elevationHeight),"Nonfinite saved elevation accepted");
            owner.loadWithComponents(saved,level.registryAccess());
            player.setItemInHand(InteractionHand.MAIN_HAND,AllItems.WRENCH.asStack());
            require(owner.adjustElevation(player,.5,.5,0,0),"Clear reshape rejected");
            require(owner.cargo.getFirst().stack.getCount()==7,"Reshape lost cargo");
            int edits=owner.elevationEdits.size();
            require(!owner.adjustElevation(player,.5,100,0,0),"Out-of-range adjustment accepted");
            require(owner.elevationEdits.size()==edits,"Rejected reshape mutated geometry");
            owner.removeFromSpan(player);
            owner.dismantle(); // A repeated callback must never refund a second time.
            int belts=0,diamonds=0;
            for(int i=0;i<player.getInventory().getContainerSize();i++) {
                var s=player.getInventory().getItem(i);
                if(s.is(item)) belts+=s.getCount();if(s.is(Items.DIAMOND)) diamonds+=s.getCount();
            }
            require(belts==cost&&diamonds==7,"Dismantling refund duplicated/lost items");
            require(level.getBlockState(a).equals(shaft)&&level.getBlockState(b).equals(shaft),"Dismantling did not restore shafts");
            for(BlockPos p:volume) require(!level.getBlockState(p).is(BeltsNPieces.SPAN.get()),"Orphan span: "+p);
            LogUtils.getLogger().info("READINESS PASS: rejected placement, costs, simulated/actual cargo operations, save/load, reshape, dismantle/refund idempotence");
            // Keep a short-lived fixture so the real client receives both full and cargo-only updates.
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,cost));
            require(item.place(level,player,player.getMainHandItem(),a,target,Direction.UP,Vec3.atCenterOf(b)).consumesAction(),"Network fixture placement failed");
            networkFixture=(ReinforcedPulleyBlockEntity)level.getBlockEntity(a);
            networkFixture.paidBelts=0;
            passed=true;
        } finally {
            Runnable removeFixtures=()->{
            // Disarm refunds before removing fixtures; only positions proven empty are touched.
            for(BlockPos p:volume) if(level.getBlockEntity(p) instanceof ReinforcedPulleyBlockEntity be) {be.paidBelts=0;be.cargo.clear();}
            for(BlockPos p:volume) if(!level.getBlockState(p).isAir()) level.removeBlock(p,false);
            for(var entity:level.getEntitiesOfClass(ItemEntity.class,region)) entity.discard();
            player.getInventory().clearContent();
            };
            if(passed) {cleanup=removeFixtures;cleanupDelay=100;}
            else removeFixtures.run();
        }
    }
}
