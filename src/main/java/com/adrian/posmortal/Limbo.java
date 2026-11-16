package com.adrian.posmortal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.Random;

/**
 * El limbo: una dimensión blanca y angelical (islas flotantes de mármol y nubes) a la que vas SIEMPRE que mueres
 * sin tótem. Allí te espera un fantasma con 50 de vida: si lo matas suelta Spectralium y hierro, y vuelves al mundo.
 */
public class Limbo {
    public static final ResourceKey<Level> LIMBO =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Posmortal.MODID, "limbo"));

    static final int BASE = 64;   // altura de la superficie de la isla principal

    public static boolean inLimbo(Entity e) {
        return e.level().dimension().equals(LIMBO);
    }

    // ---------------------------------------------------------------- isla

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Colinas suaves: llano en el centro, ondulado alrededor, bajando hacia el borde. */
    static int hill(int x, int z) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        double w = smooth((26 - d) / 12.0) * smooth((d - 8) / 8.0);
        double v = (Math.sin(x * 0.23 + 1) + Math.sin(z * 0.19 + 2) + Math.sin((x + z) * 0.11) + 3) / 6.0;
        return (int) Math.round(w * v * 4.0);
    }

    static int groundY(int x, int z) {
        return BASE + 1 + hill(x, z);
    }

    private static void put(ServerLevel a, int x, int y, int z, BlockState st) {
        a.setBlock(new BlockPos(x, y, z), st, 18);
    }

    private static void islet(ServerLevel a, int cx, int cy, int cz, int radius, int depth, boolean marbleTop) {
        BlockState marble = Posmortal.HALO_MARBLE.get().defaultBlockState();
        BlockState cloud = Posmortal.CLOUD_BLOCK.get().defaultBlockState();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > radius) continue;
                int dep = (int) (depth * Math.pow(1 - (d * d) / (radius * radius), 0.7)) + 1;
                for (int y = cy - dep; y <= cy; y++) {
                    put(a, cx + x, y, cz + z, (y >= cy - 1 && marbleTop) ? marble : cloud);
                }
            }
        }
    }

    private static void pillar(ServerLevel a, int x, int baseY, int z, int height) {
        BlockState bricks = Posmortal.HALO_BRICKS.get().defaultBlockState();
        BlockState col = Posmortal.HALO_PILLAR.get().defaultBlockState();
        BlockState light = Posmortal.HALO_LIGHT.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(a, x + dx, baseY, z + dz, bricks);
        for (int y = baseY + 1; y <= baseY + height; y++) put(a, x, y, z, col);
        int top = baseY + height + 1;
        put(a, x, top, z, bricks);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if ((dx == 0) != (dz == 0)) put(a, x + dx, top, z + dz, bricks);
        }
        put(a, x, top + 1, z, light);
    }

    private static void build(ServerLevel a) {
        if (a.getBlockState(new BlockPos(0, BASE + 1, 0)).is(Posmortal.HALO_BRICKS.get())) return;
        BlockState marble = Posmortal.HALO_MARBLE.get().defaultBlockState();
        BlockState cloud = Posmortal.CLOUD_BLOCK.get().defaultBlockState();
        BlockState bricks = Posmortal.HALO_BRICKS.get().defaultBlockState();
        BlockState light = Posmortal.HALO_LIGHT.get().defaultBlockState();
        int R = 30;

        // isla principal flotante con la parte de abajo de nubes
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R) continue;
                int top = BASE + hill(x, z);
                int dep = (int) (14 * Math.pow(1 - (d * d) / (R * R), 0.7)) + 2;
                for (int y = top - dep; y <= top; y++) put(a, x, y, z, y >= top - 1 ? marble : cloud);
            }
        }
        // tarima central con un anillo de luz
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 6.2) continue;
                put(a, x, BASE + 1, z, (d >= 3.6 && d < 4.7) ? light : bricks);
            }
        }
        // seis pilares
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3 + 0.3;
            int x = (int) Math.round(Math.cos(ang) * 19), z = (int) Math.round(Math.sin(ang) * 19);
            pillar(a, x, groundY(x, z), z, 7 + (i % 3) * 3);
        }
        // halo flotante sobre la tarima
        for (int i = 0; i < 90; i++) {
            double ang = i * Math.PI * 2 / 90;
            put(a, (int) Math.round(Math.cos(ang) * 8), BASE + 17, (int) Math.round(Math.sin(ang) * 8), light);
        }
        // islotes flotantes alrededor
        Random r = new Random(7771L);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + r.nextDouble() * 0.4;
            double rad = 40 + r.nextDouble() * 18;
            int cx = (int) Math.round(Math.cos(ang) * rad), cz = (int) Math.round(Math.sin(ang) * rad);
            int cy = BASE + r.nextInt(21) - 8;
            islet(a, cx, cy, cz, 4 + r.nextInt(4), 6 + r.nextInt(4), true);
            if (r.nextBoolean()) pillar(a, cx, cy + 1, cz, 3 + r.nextInt(4));
            else put(a, cx, cy + 1, cz, light);
        }
    }

    // ---------------------------------------------------------------- flujo

    private static void goHome(ServerPlayer p) {
        CompoundTag d = PossessionEvents.data(p);
        ServerLevel home = null;
        String dim = d.getString("limbo_dim");
        if (!dim.isEmpty()) {
            home = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));
        }
        if (home == null) home = p.server.overworld();
        double x, y, z;
        if (d.contains("limbo_x")) {
            x = d.getDouble("limbo_x");
            y = d.getDouble("limbo_y");
            z = d.getDouble("limbo_z");
        } else {
            BlockPos sp = home.getSharedSpawnPos();
            x = sp.getX() + 0.5;
            y = sp.getY();
            z = sp.getZ() + 0.5;
        }
        p.teleportTo(home, x, y, z, p.getYRot(), 0f);
    }

    /** Llamado justo después de reaparecer tras morir. */
    public static void enter(ServerPlayer p) {
        ServerLevel limbo = p.server.getLevel(LIMBO);
        if (limbo == null) return;
        CompoundTag d = PossessionEvents.data(p);
        d.putString("limbo_dim", p.level().dimension().location().toString());
        d.putDouble("limbo_x", p.getX());
        d.putDouble("limbo_y", p.getY());
        d.putDouble("limbo_z", p.getZ());
        d.putLong("limbo_return", 0L);

        build(limbo);
        p.teleportTo(limbo, 0.5, groundY(0, 14), 14.5, 180f, 0f);

        LimboGhost g = Posmortal.LIMBO_GHOST.get().create(limbo);
        if (g != null) {
            g.moveTo(0.5, groundY(0, -12) + 2, -12.5, 0f, 0f);
            limbo.addFreshEntity(g);
        }
        p.displayClientMessage(Component.literal("Has muerto... un fantasma te espera en el limbo"), true);
    }

    /** El fantasma murió: los jugadores que haya en el limbo vuelven al mundo en 5 segundos. */
    public static void onGhostDeath(ServerLevel level) {
        long when = level.getGameTime() + 100;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (!inLimbo(p)) continue;
            PossessionEvents.data(p).putLong("limbo_return", when);
            p.sendSystemMessage(Component.literal("Has vencido al fantasma. Vuelves al mundo en 5 segundos."));
        }
    }

    public static void leave(ServerPlayer p) {
        PossessionEvents.data(p).putLong("limbo_return", 0L);
        goHome(p);
    }

    /** Llamado cada segundo para cada jugador. */
    public static void tick(ServerPlayer p) {
        if (!inLimbo(p)) return;
        CompoundTag d = PossessionEvents.data(p);
        long ret = d.getLong("limbo_return");
        if (ret > 0 && p.serverLevel().getGameTime() >= ret) {
            leave(p);
            return;
        }
        double dist = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
        if (p.getY() < BASE - 40 || dist > 75) {
            p.teleportTo(p.serverLevel(), 0.5, groundY(0, 14), 14.5, 180f, 0f);
        }
    }

    /** Si ya no queda nadie en el limbo, se retiran los fantasmas que sobren. */
    public static void cleanupIfEmpty(MinecraftServer server) {
        ServerLevel limbo = server.getLevel(LIMBO);
        if (limbo == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (inLimbo(p) && p.isAlive()) return;
        }
        for (LimboGhost g : limbo.getEntities(EntityTypeTest.forClass(LimboGhost.class), x -> true)) g.discard();
    }
}
