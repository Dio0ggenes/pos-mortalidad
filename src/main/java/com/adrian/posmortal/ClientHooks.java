package com.adrian.posmortal;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class ClientHooks {
    public static void setModel(UUID id, String model) {
        if (model.isEmpty()) ModelState.MODELS.remove(id);
        else ModelState.MODELS.put(id, model);
        ClientEvents.PROXIES.remove(id);
        Level l = Minecraft.getInstance().level;
        if (l != null) {
            Player p = l.getPlayerByUUID(id);
            if (p != null) p.refreshDimensions();
        }
    }
}
