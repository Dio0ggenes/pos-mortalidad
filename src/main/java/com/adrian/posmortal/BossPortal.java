package com.adrian.posmortal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Portal a la boss fight: una cruz de redstone en el suelo (cruz cristiana) con un marco con tótem en cada
 * esquina. Al completarla aparece una estrella del Nether; al hacerle clic derecho (siendo fantasma) viajas a la arena.
 *
 *   . R .      R = polvo de redstone
 *   R R R      T = marco en el suelo con un tótem (cualquier giro de 90 grados vale)
 *   . R .
 *   . R .
 *   . R .
 *  y los marcos T están en las 4 esquinas de ese rectángulo de 3x5.
 */
public class BossPortal {
    public static final String STAR = "posmortal_star";
    public static final String CORNERS = "posmortal_corners";

    private static final int[][] RED = {{0, 0}, {-1, 1}, {0, 1}, {1, 1}, {0, 2}, {0, 3}, {0, 4}};
    private static final int[][] CORNER = {{-1, 0}, {1, 0}, {-1, 4}, {1, 4}};

    private static int[] rot(int r, int x, int z) {
        return switch (r) {
            case 0 -> new int[]{x, z};
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            default -> new int[]{z, -x};
        };
    }

    /** Vale el tótem de la posmortalidad o el tótem de la inmortalidad de vanilla. */
    static boolean isTotem(ItemStack s) {
        return s.is(Posmortal.TOTEM.get()) || s.is(Items.TOTEM_OF_UNDYING);
    }

    private static ItemFrame frameAt(ServerLevel l, BlockPos pos) {
        List<ItemFrame> fs = l.getEntitiesOfClass(ItemFrame.class, new AABB(pos).inflate(0.2),
                f -> f.getPos().equals(pos) && f.getDirection() == Direction.UP && isTotem(f.getItem()));
        return fs.isEmpty() ? null : fs.get(0);
    }

    /** Busca marcos con tótem cerca del jugador y comprueba si forman el portal. */
    public static void scan(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (PossessionEvents.inArena(p)) return;
        for (ItemFrame f : l.getEntitiesOfClass(ItemFrame.class, p.getBoundingBox().inflate(8),
                x -> x.getDirection() == Direction.UP && isTotem(x.getItem()))) {
            if (tryActivate(l, f.getPos())) return;
        }
    }

    private static boolean tryActivate(ServerLevel l, BlockPos fp) {
        for (int r = 0; r < 4; r++) {
            for (int[] c : CORNER) {
                int[] o = rot(r, c[0], c[1]);
                BlockPos origin = fp.offset(-o[0], 0, -o[1]);
                if (!matches(l, origin, r)) continue;
                int[] cc = rot(r, 0, 1);
                BlockPos center = origin.offset(cc[0], 0, cc[1]);
                spawnStar(l, origin, r, center);
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ServerLevel l, BlockPos origin, int r) {
        for (int[] cell : RED) {
            int[] q = rot(r, cell[0], cell[1]);
            if (!l.getBlockState(origin.offset(q[0], 0, q[1])).is(Blocks.REDSTONE_WIRE)) return false;
        }
        for (int[] c : CORNER) {
            int[] q = rot(r, c[0], c[1]);
            if (frameAt(l, origin.offset(q[0], 0, q[1])) == null) return false;
        }
        return true;
    }

    private static void spawnStar(ServerLevel l, BlockPos origin, int r, BlockPos center) {
        if (!l.getEntitiesOfClass(Interaction.class, new AABB(center).inflate(1.5),
                i -> i.getPersistentData().getBoolean(STAR)).isEmpty()) return; // ya hay una estrella

        double x = center.getX() + 0.5, y = center.getY(), z = center.getZ() + 0.5;

        ItemEntity star = new ItemEntity(l, x, y + 0.9, z, new ItemStack(Items.NETHER_STAR));
        star.setNoGravity(true);
        star.setDeltaMovement(Vec3.ZERO);
        star.setPickUpDelay(32767);
        star.setUnlimitedLifetime();
        l.addFreshEntity(star);

        Interaction it = EntityType.INTERACTION.create(l);
        if (it == null) return;
        it.moveTo(x, y, z, 0f, 0f);
        it.setWidth(1.2f);
        it.setHeight(1.8f);
        it.getPersistentData().putBoolean(STAR, true);
        it.getPersistentData().putUUID("star", star.getUUID());
        long[] corners = new long[CORNER.length];
        for (int i = 0; i < CORNER.length; i++) {
            int[] q = rot(r, CORNER[i][0], CORNER[i][1]);
            corners[i] = origin.offset(q[0], 0, q[1]).asLong();
        }
        it.getPersistentData().putLongArray(CORNERS, corners);
        l.addFreshEntity(it);

        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1, z, 60, 0.8, 0.8, 0.8, 0.3);
    }

    /** Clic derecho en la estrella. */
    public static void activate(ServerPlayer p, Interaction it) {
        if (PossessionEvents.inArena(p)) return;
        if (!PossessionEvents.isGhost(p)) {
            p.displayClientMessage(Component.literal("Solo un fantasma puede cruzar el portal"), true);
            return;
        }
        ServerLevel l = p.serverLevel();
        // el portal gasta los 4 tótems
        for (long c : it.getPersistentData().getLongArray(CORNERS)) {
            ItemFrame f = frameAt(l, BlockPos.of(c));
            if (f != null) f.setItem(ItemStack.EMPTY);
        }
        if (it.getPersistentData().hasUUID("star")) {
            Entity s = l.getEntity(it.getPersistentData().getUUID("star"));
            if (s != null) s.discard();
        }
        l.sendParticles(ParticleTypes.PORTAL, it.getX(), it.getY() + 1, it.getZ(), 80, 0.5, 1.0, 0.5, 0.8);
        it.discard();
        BossFight.enter(p);
    }
}
