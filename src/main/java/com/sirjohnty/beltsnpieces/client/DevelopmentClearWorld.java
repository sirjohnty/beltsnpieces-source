package com.sirjohnty.beltsnpieces.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sirjohnty.beltsnpieces.BeltsNPieces;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.ArrayDeque;

/** Local development-world cleanup; excluded from distributed mod JARs. */
@EventBusSubscriber(modid=BeltsNPieces.ID,value=Dist.CLIENT)
public final class DevelopmentClearWorld {
    private record Section(LevelChunk chunk,int index) {}
    private static final ArrayDeque<Section> pending=new ArrayDeque<>();
    private static ServerLevel clearingLevel;
    private static CommandSourceStack source;
    private static int centerX,centerZ,radius,removed;
    private static net.minecraft.server.level.ServerPlayer clearOnLogin;
    private static int loginDelay;
    private DevelopmentClearWorld() {}

    @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if(FMLEnvironment.production || !Boolean.getBoolean("beltsnpieces.clearOnce")
            || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            || !player.server.isSingleplayer()) return;
        if(java.nio.file.Files.isRegularFile(java.nio.file.Path.of("clearw-on-next-testit.once"))) {
            clearOnLogin=player;loginDelay=40;
        }
    }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if(FMLEnvironment.production || event.getCommandSelection()!=Commands.CommandSelection.INTEGRATED) return;
        event.getDispatcher().register(Commands.literal("clearw").requires(s->s.hasPermission(2))
            .executes(c->begin(c.getSource(),8))
            .then(Commands.argument("radius",IntegerArgumentType.integer(1,32))
                .executes(c->begin(c.getSource(),IntegerArgumentType.getInteger(c,"radius")))));
    }

    private static boolean sandstone(BlockState state) {
        return state.is(Blocks.SANDSTONE) || state.is(Blocks.CUT_SANDSTONE)
            || state.is(Blocks.CHISELED_SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE)
            || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.CUT_RED_SANDSTONE)
            || state.is(Blocks.CHISELED_RED_SANDSTONE) || state.is(Blocks.SMOOTH_RED_SANDSTONE);
    }

    private static int begin(CommandSourceStack command,int chunkRadius) {
        if(clearingLevel!=null) {command.sendFailure(Component.literal("World cleanup is already running."));return 0;}
        if(!(command.getEntity() instanceof Player)) return 0;
        source=command;clearingLevel=command.getLevel();radius=chunkRadius;removed=0;
        centerX=BlockPos.containing(command.getPosition()).getX()>>4;
        centerZ=BlockPos.containing(command.getPosition()).getZ()>>4;
        for(int x=centerX-radius;x<=centerX+radius;x++) for(int z=centerZ-radius;z<=centerZ+radius;z++) {
            LevelChunk chunk=clearingLevel.getChunkSource().getChunkNow(x,z);
            if(chunk==null) continue;
            for(int i=0;i<chunk.getSections().length;i++) {
                var section=chunk.getSections()[i];
                if(!section.hasOnlyAir() && section.maybeHas(s->!s.isAir() && !sandstone(s))) pending.add(new Section(chunk,i));
            }
        }
        command.sendSuccess(()->Component.literal("Clearing loaded chunks within "+radius+" chunks; sandstone is preserved."),false);
        return 1;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(clearOnLogin!=null && --loginDelay<=0) {
            var player=clearOnLogin;clearOnLogin=null;
            if(player.server==event.getServer() && !player.hasDisconnected()) {
                try {
                    // Claim the one-shot before starting destructive cleanup, so
                    // reconnects and later launches cannot execute it twice.
                    java.nio.file.Files.move(java.nio.file.Path.of("clearw-on-next-testit.once"),
                        java.nio.file.Path.of("clearw-last-testit.consumed"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    begin(player.createCommandSourceStack(),8);
                } catch(java.io.IOException error) {
                    player.sendSystemMessage(Component.literal("Pending cleanup could not be claimed; /clearw was not run."));
                }
            }
        }
        if(clearingLevel==null) return;
        if(clearingLevel.getServer()!=event.getServer()) {pending.clear();clearingLevel=null;source=null;return;}
        Section section=pending.poll();
        if(section!=null) {
            LevelChunk chunk=section.chunk();
            if(clearingLevel.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk) return;
            var blocks=chunk.getSections()[section.index()];
            // Earlier sections or block callbacks may already have emptied it.
            if(blocks.hasOnlyAir() || !blocks.maybeHas(s->!s.isAir() && !sandstone(s))) return;
            int baseY=clearingLevel.getSectionYFromSectionIndex(section.index())<<4;
            BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
            for(int y=0;y<16;y++) for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
                BlockState state=blocks.getBlockState(x,y,z);
                if(state.isAir() || sandstone(state)) continue;
                pos.set(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);
                if(clearingLevel.setBlock(pos,Blocks.AIR.defaultBlockState(),2|16|32)) removed++;
            }
            return;
        }
        var entities=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        clearingLevel.getAllEntities().forEach(entities::add);
        for(var entity:entities) {
            if(entity instanceof Player) continue;
            if(Math.abs(entity.chunkPosition().x-centerX)<=radius && Math.abs(entity.chunkPosition().z-centerZ)<=radius)
                entity.discard();
        }
        int count=removed;
        source.sendSuccess(()->Component.literal("Cleared "+count+" blocks and non-player entities. Sandstone preserved."),false);
        clearingLevel=null;source=null;
    }
}
