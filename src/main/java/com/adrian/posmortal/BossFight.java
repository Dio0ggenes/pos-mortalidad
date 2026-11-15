package com.adrian.posmortal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;
import java.util.UUID;

/** La dimensión de la arena y todo lo que pasa al entrar, ganar o perder. */
public class BossFight {
    public static final ResourceKey<Level> ARENA =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Posmortal.MODID, "arena"));

    private static List<? extends Doppelganger> bosses(ServerLevel arena, UUID owner) {
        return arena.getEntities(EntityTypeTest.forClass(Doppelganger.class),
                b -> b.getOwnerId().isPresent() && b.getOwnerId().get().equals(owner));
    }

    public static boolean hasBoss(ServerPlayer p) {
        ServerLevel arena = p.server.getLevel(ARENA);
        return arena != null && !bosses(arena, p.getUUID()).isEmpty();
    }

    private static void removeBosses(ServerPlayer p) {
        ServerLevel arena = p.server.getLevel(ARENA);
        if (arena == null) return;
        for (Doppelganger b : bosses(arena, p.getUUID())) b.discard();
    }

    // ---------------------------------------------------------------- terreno de la arena

    /** Radio del que no se puede salir (un fantasma volando fuera vuelve al centro). */
    public static final double LIMIT = 54;
    public static final double CEILING = 75;

    private static double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    private static double noise(double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(x0, z0), b = hash(x0 + 1, z0), c = hash(x0, z0 + 1), d = hash(x0 + 1, z0 + 1);
        double top = a + (b - a) * fx, bot = c + (d - c) * fx;
        return top + (bot - top) * fz;
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Altura del terreno sobre el suelo base (0 = llano). El centro es llano; hacia fuera sube en montañas irregulares. */
    static int height(int x, int z) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        double n = noise(x / 14.0 + 500, z / 14.0 + 500) * 0.6 + noise(x / 7.0 + 90, z / 7.0 + 90) * 0.3
                + noise(x / 3.5 + 20, z / 3.5 + 20) * 0.1;
        double ridge = Math.abs(noise(x / 9.0 + 300, z / 9.0 + 300) - 0.5) * 2.0;
        double rise = smooth((d - 17) / 34.0);
        double h = rise * (8 + 22 * n + 8 * ridge);
        h += smooth((d - 10) / 10.0) * (noise(x / 5.0, z / 5.0) * 2.6);   // ondulaciones suaves en la zona de combate
        return (int) Math.round(h);
    }

    static int groundY(int x, int z) {
        return 4 + height(x, z);
    }

    private static void put(ServerLevel a, int x, int y, int z, net.minecraft.world.level.block.state.BlockState st) {
        a.setBlock(new BlockPos(x, y, z), st, 18);
    }

    /** Restos de la arena antigua (paredes invisibles y pilares negros) para que no estorben. */
    private static void cleanLegacy(ServerLevel a) {
        if (!a.getBlockState(new BlockPos(24, 10, 0)).is(Blocks.BARRIER)) return;
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= 23.5 && d <= 24.5) for (int y = 4; y <= 40; y++) put(a, x, y, z, Blocks.AIR.defaultBlockState());
                if (d <= 24.5) put(a, x, 40, z, Blocks.AIR.defaultBlockState());
            }
        }
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            int px = (int) Math.round(Math.cos(ang) * 16), pz = (int) Math.round(Math.sin(ang) * 16);
            for (int y = 4; y <= 12; y++) put(a, px, y, pz, Blocks.AIR.defaultBlockState());
        }
    }

    private static void pillar(ServerLevel a, int x, int z, int height, boolean crystalTop) {
        int base = groundY(x, z);
        var bricks = Posmortal.SPECTRAL_BRICKS.get().defaultBlockState();
        var col = Posmortal.SPECTRAL_PILLAR.get().defaultBlockState();
        var crystal = Posmortal.SOUL_CRYSTAL.get().defaultBlockState();
        // base ancha de ladrillos
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gy = groundY(x + dx, z + dz);
                for (int y = gy - 1; y <= base + 1; y++) put(a, x + dx, y, z + dz, bricks);
            }
        }
        for (int y = base + 2; y < base + 2 + height; y++) put(a, x, y, z, col);
        int top = base + 2 + height;
        put(a, x, top, z, bricks);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx == 0) != (dz == 0)) put(a, x + dx, top, z + dz, bricks); // capitel en cruz
            }
        }
        if (crystalTop) {
            put(a, x, top + 1, z, crystal);
            put(a, x, top + 2, z, crystal);
        }
    }

    private static void spike(ServerLevel a, java.util.Random r, int x, int z) {
        var crystal = Posmortal.SOUL_CRYSTAL.get().defaultBlockState();
        int base = groundY(x, z);
        int h = 2 + r.nextInt(4);
        for (int y = base; y < base + h; y++) put(a, x, y, z, crystal);
        for (int i = 0; i < 3; i++) {
            int dx = r.nextInt(3) - 1, dz = r.nextInt(3) - 1;
            int gy = groundY(x + dx, z + dz);
            int hh = 1 + r.nextInt(2);
            for (int y = gy; y < gy + hh; y++) put(a, x + dx, y, z + dz, crystal);
        }
    }

    /**
     * Cráter de roca espectral: llano en el centro (con una tarima de ladrillos) y montañas irregulares alrededor,
     * con pilares, columnas rotas, obeliscos y cristales de almas. Se construye una sola vez.
     */
    private static void buildArena(ServerLevel a) {
        if (a.getBlockState(new BlockPos(0, 4, 0)).is(Posmortal.SPECTRAL_BRICKS.get())) return;
        cleanLegacy(a);

        var floor = Posmortal.SPECTRAL_FLOOR.get().defaultBlockState();
        var stone = Posmortal.SPECTRAL_STONE.get().defaultBlockState();
        var bricks = Posmortal.SPECTRAL_BRICKS.get().defaultBlockState();
        var crystal = Posmortal.SOUL_CRYSTAL.get().defaultBlockState();
        int R = 52;

        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                if (x * x + z * z > R * R) continue;
                int h = height(x, z);
                int top = 3 + h;
                for (int y = 3; y <= top; y++) put(a, x, y, z, y == top ? floor : stone);
            }
        }

        java.util.Random r = new java.util.Random(20261115L);

        // tarima central de ladrillos con un anillo de cristal incrustado
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 7.2) continue;
                put(a, x, 4, z, (d >= 4.4 && d < 5.6) ? crystal : bricks);
            }
        }

        // anillo de 8 grandes pilares
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + 0.2;
            pillar(a, (int) Math.round(Math.cos(ang) * 21), (int) Math.round(Math.sin(ang) * 21), 9 + r.nextInt(8), true);
        }
        // columnas rotas más cerca del centro
        for (int i = 0; i < 10; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 11 + r.nextDouble() * 6;
            int x = (int) Math.round(Math.cos(ang) * rad), z = (int) Math.round(Math.sin(ang) * rad);
            int base = groundY(x, z);
            int hh = 2 + r.nextInt(5);
            for (int y = base; y < base + hh; y++) put(a, x, y, z, y == base ? bricks : Posmortal.SPECTRAL_PILLAR.get().defaultBlockState());
        }
        // obeliscos en las laderas
        for (int i = 0; i < 5; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 30 + r.nextDouble() * 12;
            pillar(a, (int) Math.round(Math.cos(ang) * rad), (int) Math.round(Math.sin(ang) * rad), 12 + r.nextInt(10), true);
        }
        // cristales por todo el terreno
        for (int i = 0; i < 90; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 12 + r.nextDouble() * 38;
            spike(a, r, (int) Math.round(Math.cos(ang) * rad), (int) Math.round(Math.sin(ang) * rad));
        }
    }

    /** Mantiene a los jugadores dentro del cráter. */
    public static void keepInside(ServerPlayer p) {
        double d = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
        if (d > LIMIT || p.getY() > CEILING) {
            p.teleportTo(p.serverLevel(), 0.5, groundY(0, 20), 20.5, 180f, 0f);
            p.displayClientMessage(Component.literal("No puedes salir de la arena"), true);
        }
    }

    public static void enter(ServerPlayer p) {
        ServerLevel arena = p.server.getLevel(ARENA);
        if (arena == null) {
            p.displayClientMessage(Component.literal("La dimensión de la arena no está cargada"), false);
            return;
        }
        CompoundTag d = PossessionEvents.data(p);
        d.putString("home_dim", p.level().dimension().location().toString());
        d.putDouble("home_x", p.getX());
        d.putDouble("home_y", p.getY());
        d.putDouble("home_z", p.getZ());

        buildArena(arena);
        PossessionEvents.arenaPowers(p, true);
        p.teleportTo(arena, 0.5, groundY(0, 20), 20.5, 180f, 0f);

        Doppelganger b = Posmortal.DOPPELGANGER.get().create(arena);
        if (b != null) {
            b.moveTo(0.5, groundY(0, -9), -8.5, 0f, 0f);
            b.setOwnerId(p.getUUID());
            arena.addFreshEntity(b);
        }
        p.displayClientMessage(Component.literal("Tu reflejo despierta..."), true);
    }

    private static void goHome(ServerPlayer p) {
        CompoundTag d = PossessionEvents.data(p);
        ServerLevel home = null;
        String dim = d.getString("home_dim");
        if (!dim.isEmpty()) {
            home = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));
        }
        if (home == null) home = p.server.overworld();
        double x, y, z;
        if (d.contains("home_x")) {
            x = d.getDouble("home_x");
            y = d.getDouble("home_y");
            z = d.getDouble("home_z");
        } else {
            BlockPos sp = home.getSharedSpawnPos();
            x = sp.getX() + 0.5;
            y = sp.getY();
            z = sp.getZ() + 0.5;
        }
        p.teleportTo(home, x, y, z, p.getYRot(), 0f);
    }

    /** El jugador murió en la arena: vuelve a casa, sigue siendo fantasma. */
    public static void fail(ServerPlayer p) {
        removeBosses(p);
        PossessionEvents.arenaPowers(p, false);
        p.removeAllEffects();
        p.clearFire();
        PossessionEvents.applyGhost(p);
        p.setHealth(p.getMaxHealth());
        goHome(p);
        p.sendSystemMessage(Component.literal("Tu reflejo ha sido más fuerte... Necesitarás otro portal."));
    }

    public static void onBossDeath(Doppelganger b) {
        if (b.getServer() == null || b.getOwnerId().isEmpty()) return;
        ServerPlayer p = b.getServer().getPlayerList().getPlayer(b.getOwnerId().get());
        if (p != null && PossessionEvents.inArena(p)) victory(p);
    }

    /** Has matado a tu reflejo: vuelves a ser humano y recibes un tercio de estrella del Nether (la única forma de conseguirlo). */
    public static void victory(ServerPlayer p) {
        removeBosses(p);
        PossessionEvents.arenaPowers(p, false);
        PossessionEvents.removeGhost(p);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        ItemStack third = new ItemStack(Posmortal.STAR_THIRD.get());
        if (!p.getInventory().add(third)) p.drop(third, false);
        goHome(p);
        p.sendSystemMessage(Component.literal("Has vencido a tu reflejo. Vuelves a ser humano y recibes un tercio de estrella del Nether."));
    }
}
