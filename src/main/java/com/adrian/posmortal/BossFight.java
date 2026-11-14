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

    /** Suelo en y=3 (la capa de piedra negra), pared invisible de radio 24 y unos pilares. Solo se construye una vez. */
    private static void buildArena(ServerLevel a) {
        if (a.getBlockState(new BlockPos(24, 10, 0)).is(Blocks.BARRIER)) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= 23.5 && d <= 24.5) {
                    for (int y = 4; y <= 40; y++) a.setBlock(m.set(x, y, z), Blocks.BARRIER.defaultBlockState(), 2);
                }
                if (d <= 24.5) a.setBlock(m.set(x, 40, z), Blocks.BARRIER.defaultBlockState(), 2);
            }
        }
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            int px = (int) Math.round(Math.cos(ang) * 16), pz = (int) Math.round(Math.sin(ang) * 16);
            for (int y = 4; y < 12; y++) a.setBlock(m.set(px, y, pz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
            a.setBlock(m.set(px, 12, pz), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
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
        p.teleportTo(arena, 0.5, 4.0, 20.5, 180f, 0f);

        Doppelganger b = Posmortal.DOPPELGANGER.get().create(arena);
        if (b != null) {
            b.moveTo(0.5, 4.0, -8.5, 0f, 0f);
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

    /** Has matado a tu reflejo: vuelves a ser humano y recibes el token del Fornite. */
    public static void victory(ServerPlayer p) {
        removeBosses(p);
        PossessionEvents.arenaPowers(p, false);
        PossessionEvents.removeGhost(p);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        ItemStack token = new ItemStack(Posmortal.FORNITE_TOKEN.get());
        if (!p.getInventory().add(token)) p.drop(token, false);
        goHome(p);
        p.sendSystemMessage(Component.literal("Has vencido a tu reflejo. Vuelves a ser humano y recibes el Token del Fornite."));
    }
}
