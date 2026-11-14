package com.adrian.posmortal;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Posmortal.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent e) {
        e.register(ClientEvents.ABILITY);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Posmortal.MILK_BEAM.get(), MilkBeamRenderer::new);
        e.registerEntityRenderer(Posmortal.DOPPELGANGER.get(), DoppelgangerRenderer::new);
    }
}
