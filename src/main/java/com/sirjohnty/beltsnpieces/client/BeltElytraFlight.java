package com.sirjohnty.beltsnpieces.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import java.lang.reflect.Method;

/** Optional Caelus support keeps accessory flight validation with its owning mod. */
public final class BeltElytraFlight {
    private static boolean initialized;
    private static Object caelusApi,caelusPlatform;
    private static Method canFallFly,sendFlightPacket;
    private BeltElytraFlight() {}

    private static void initialize() {
        if(initialized) return;
        initialized=true;
        if(!ModList.get().isLoaded("caelus")) return;
        try {
            Class<?> api=Class.forName("com.illusivesoulworks.caelus.api.CaelusApi");
            Object instance=api.getMethod("getInstance").invoke(null);
            Method check=api.getMethod("canFallFly",LivingEntity.class);
            Class<?> services=Class.forName("com.illusivesoulworks.caelus.platform.Services");
            Object platform=services.getField("CAELUS").get(null);
            Method send=Class.forName("com.illusivesoulworks.caelus.platform.services.ICaelusPlatform")
                .getMethod("sendFlightPacket");
            caelusApi=instance;canFallFly=check;caelusPlatform=platform;sendFlightPacket=send;
        } catch(ReflectiveOperationException | LinkageError error) {
            com.mojang.logging.LogUtils.getLogger().warn("Create: Belts n Pieces could not initialize optional Caelus flight support",error);
        }
    }

    public static void tryStart(LocalPlayer player) {
        if(player.onGround() || player.isFallFlying() || player.isInWater() || player.isInLava()
            || player.isPassenger() || player.getAbilities().flying || player.isSpectator()
            || player.hasEffect(MobEffects.LEVITATION)) return;
        initialize();
        if(canFallFly!=null) {
            try {
                String permission=((Enum<?>)canFallFly.invoke(caelusApi,player)).name();
                if(permission.equals("DENY")) return;
                if(permission.equals("ALLOW")) {
                    // Caelus checks equipped accessory wings and supplies its own
                    // server-validated packet; a vanilla chest-only request is insufficient.
                    sendFlightPacket.invoke(caelusPlatform);
                    player.startFallFlying();
                    return;
                }
            } catch(ReflectiveOperationException | LinkageError error) {
                canFallFly=null;
                com.mojang.logging.LogUtils.getLogger().warn("Create: Belts n Pieces optional Caelus flight request failed",error);
            }
        }
        if(player.tryToStartFallFlying()) player.connection.send(new ServerboundPlayerCommandPacket(
            player,ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }
}
