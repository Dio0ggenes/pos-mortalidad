package com.adrian.posmortal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/** Mascotas del jugador (vex del evocador, zombis aliados): siguen al dueño, atacan hostiles y los vex se pueden montar. */
public class Pets {
    public static boolean owned(Entity e, ServerPlayer p) {
        CompoundTag t = e.getPersistentData();
        return t.hasUUID(PossessionEvents.OWNER) && t.getUUID(PossessionEvents.OWNER).equals(p.getUUID());
    }

    public static int count(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64), e -> owned(e, p)).size();
    }

    public static boolean mountNearest(ServerPlayer p) {
        List<Vex> list = p.serverLevel().getEntitiesOfClass(Vex.class, p.getBoundingBox().inflate(8), v -> owned(v, p));
        Vex v = list.stream().min(Comparator.comparingDouble(x -> x.distanceToSqr(p))).orElse(null);
        if (v == null) return false;
        p.startRiding(v, true);
        return true;
    }

    public static void tick(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();

        if (p.getVehicle() instanceof Vex vex && owned(vex, p)) {
            vex.setNoAi(true);
            vex.setNoGravity(true);
            Vec3 look = p.getLookAngle();
            Vec3 left = new Vec3(look.z, 0, -look.x);
            if (left.lengthSqr() > 1.0E-6) left = left.normalize();
            Vec3 mv = look.scale(p.zza * 0.6).add(left.scale(p.xxa * 0.6));
            vex.setDeltaMovement(mv);
            vex.setYRot(p.getYRot());
            vex.hurtMarked = true;
        }

        if (p.tickCount % 10 != 0) return;
        for (LivingEntity pet : lvl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48), e -> owned(e, p))) {
            if (pet == p.getVehicle()) continue;
            if (pet instanceof Vex v) v.setNoAi(false);
            if (pet instanceof net.minecraft.world.entity.Mob mob) {
                if (mob.distanceToSqr(p) > 144) {
                    mob.getMoveControl().setWantedPosition(p.getX(), p.getY() + 1, p.getZ(), 1.4);
                }
                if (mob.getTarget() == null) {
                    LivingEntity foe = lvl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12),
                                    x -> x instanceof Enemy && x.isAlive() && !owned(x, p))
                            .stream().min(Comparator.comparingDouble(x -> x.distanceToSqr(p))).orElse(null);
                    if (foe != null) mob.setTarget(foe);
                }
            }
        }
    }
}
