package com.sirjohnty.beltsnpieces.client;

import com.mojang.brigadier.Command;
import com.sirjohnty.beltsnpieces.BeltsNPieces;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import java.nio.file.Path;

@EventBusSubscriber(modid=BeltsNPieces.ID,value=Dist.CLIENT)
public final class DevelopmentRestart {
    private static boolean restarting;
    private DevelopmentRestart() {}

    @SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
        if(FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("restart").executes(context->{
            Minecraft mc=Minecraft.getInstance();
            if(restarting) return 0;
            Path script=Path.of("..","restart-dev.ps1").toAbsolutePath().normalize();
            if(!java.nio.file.Files.isRegularFile(script)) {
                context.getSource().sendFailure(Component.literal("Development restart script is missing."));return 0;
            }
            try {
                // Windows starts PowerShell hidden; it waits for this JVM to finish
                // so Minecraft can save the world and release all its files first.
                new ProcessBuilder("powershell.exe","-NoProfile","-WindowStyle","Hidden",
                    "-ExecutionPolicy","Bypass","-File",script.toString(),
                    "-GameProcessId",Long.toString(ProcessHandle.current().pid()))
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(Path.of("restart-helper.log").toFile()))
                    .redirectErrorStream(true).start();
                restarting=true;
                mc.execute(mc::stop);
                return Command.SINGLE_SUCCESS;
            } catch(java.io.IOException error) {
                context.getSource().sendFailure(Component.literal("Could not start the development restart helper."));
                com.mojang.logging.LogUtils.getLogger().error("Development restart failed",error);
                return 0;
            }
        }));
    }
}
