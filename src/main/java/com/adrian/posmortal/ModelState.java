package com.adrian.posmortal;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModelState {
    /** jugador -> modelo. Se rellena en el servidor y, por paquete, en los clientes. */
    public static final Map<UUID, String> MODELS = new ConcurrentHashMap<>();

    /** Cuerpos que vuelan: el jugador queda siempre volando, sin doble toque de espacio. */
    public static final Set<String> FLYERS = Set.of("minecraft:bat", "minecraft:bee", "minecraft:parrot",
            "minecraft:allay", "minecraft:vex", "minecraft:blaze", "minecraft:ghast", "minecraft:phantom",
            "minecraft:ender_dragon", "minecraft:wither");

    public static boolean isGhost(UUID id) {
        return "ghost".equals(MODELS.get(id));
    }

    public static void set(ServerPlayer p, String model) {
        String m = model == null ? "" : model;
        if (m.isEmpty()) MODELS.remove(p.getUUID());
        else MODELS.put(p.getUUID(), m);
        p.refreshDimensions();
        Net.CH.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new Net.ModelMsg(p.getUUID(), m));
    }
}
