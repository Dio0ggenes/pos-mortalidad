package com.adrian.posmortal;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Habilidades pasivas y activas (tecla V; con Shift = habilidad alternativa) de cada mob. */
public class Abilities {
    private static final Map<String, Long> CD = new HashMap<>();

    private static final Set<String> ACTIVE = Set.of("minecraft:enderman", "minecraft:shulker", "minecraft:ender_dragon",
            "minecraft:wither", "minecraft:warden", "minecraft:blaze", "minecraft:ghast", "minecraft:skeleton",
            "minecraft:stray", "minecraft:pillager", "minecraft:chicken", "minecraft:snow_golem");

    private static final Set<String> BURNERS = Set.of("minecraft:zombie", "minecraft:skeleton", "minecraft:stray",
            "minecraft:drowned", "minecraft:zombie_villager");
    private static final Set<String> FIREPROOF = Set.of("minecraft:blaze", "minecraft:ghast", "minecraft:wither_skeleton",
            "minecraft:strider", "minecraft:magma_cube", "minecraft:wither", "minecraft:zombified_piglin",
            "minecraft:ender_dragon");
    private static final Set<String> WATER = Set.of("minecraft:squid", "minecraft:glow_squid", "minecraft:cod",
            "minecraft:salmon", "minecraft:pufferfish", "minecraft:tropical_fish", "minecraft:dolphin",
            "minecraft:guardian", "minecraft:elder_guardian", "minecraft:drowned", "minecraft:axolotl",
            "minecraft:turtle");

    public static boolean has(String id) {
        return ACTIVE.contains(id);
    }

    public static void passive(ServerPlayer p, String id) {
        ServerLevel lvl = p.serverLevel();
        int t = p.tickCount;
        if (BURNERS.contains(id) && t % 20 == 0) {
            if (lvl.isDay() && lvl.canSeeSky(p.blockPosition()) && !p.isInWaterRainOrBubble()
                    && p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                p.setSecondsOnFire(6);
            }
        }
        if (id.equals("minecraft:enderman") && t % 10 == 0 && p.isInWaterRainOrBubble()) {
            p.hurt(lvl.damageSources().magic(), 1f);
        }
        if (t % 40 == 0) {
            if (FIREPROOF.contains(id)) p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, false, false));
            if (WATER.contains(id)) p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, false, false));
        }
    }

    /** Devuelve true si este cuerpo tiene alguna habilidad activa (aunque esté en recarga). */
    public static boolean use(ServerPlayer p, String id, boolean sneak) {
        if (!has(id)) return false;
        ServerLevel lvl = p.serverLevel();
        long now = lvl.getGameTime();
        String ck = p.getUUID() + id + sneak;
        Long until = CD.get(ck);
        if (until != null && now < until) return true;

        Vec3 look = p.getLookAngle();
        int cd = 0;
        switch (id) {
            case "minecraft:enderman" -> cd = teleport(p) ? 20 : 0;
            case "minecraft:shulker" -> {
                if (sneak) cd = shulkerBullet(p) ? 40 : 0;
                else cd = teleport(p) ? 60 : 0;
            }
            case "minecraft:ender_dragon" -> {
                launch(lvl, p, new DragonFireball(lvl, p, look.x, look.y, look.z), look);
                cd = 40;
            }
            case "minecraft:wither" -> {
                if (sneak) {
                    WitherBoss w = EntityType.WITHER.create(lvl);
                    if (w != null) {
                        w.moveTo(p.getX() + look.x * 4, p.getY(), p.getZ() + look.z * 4, p.getYRot(), 0f);
                        w.getPersistentData().putUUID(PossessionEvents.OWNER, p.getUUID());
                        w.makeInvulnerable();
                        lvl.addFreshEntity(w);
                    }
                    cd = 1200;
                } else {
                    launch(lvl, p, new WitherSkull(lvl, p, look.x, look.y, look.z), look);
                    cd = 10;
                }
            }
            case "minecraft:warden" -> {
                sonicBoom(p, lvl, look);
                cd = 100;
            }
            case "minecraft:blaze" -> {
                launch(lvl, p, new SmallFireball(lvl, p, look.x, look.y, look.z), look);
                cd = 10;
            }
            case "minecraft:ghast" -> {
                launch(lvl, p, new LargeFireball(lvl, p, look.x, look.y, look.z, 2), look);
                cd = 40;
            }
            case "minecraft:skeleton", "minecraft:stray", "minecraft:pillager" -> {
                Arrow a = new Arrow(lvl, p);
                a.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 3f, 1f);
                a.pickup = AbstractArrow.Pickup.DISALLOWED;
                lvl.addFreshEntity(a);
                lvl.playSound(null, p.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.PLAYERS, 1f, 1f);
                cd = 20;
            }
            case "minecraft:chicken" -> {
                p.spawnAtLocation(Items.EGG);
                lvl.playSound(null, p.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 1f);
                cd = 200;
            }
            case "minecraft:snow_golem" -> {
                Snowball s = new Snowball(lvl, p);
                s.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 1.5f, 1f);
                lvl.addFreshEntity(s);
                cd = 8;
            }
            default -> {
                return false;
            }
        }
        if (cd > 0) CD.put(ck, now + cd);
        return true;
    }

    private static void launch(ServerLevel lvl, ServerPlayer p, Entity proj, Vec3 look) {
        proj.setPos(p.getX() + look.x * 1.5, p.getEyeY() - 0.2 + look.y * 1.5, p.getZ() + look.z * 1.5);
        lvl.addFreshEntity(proj);
    }

    private static boolean teleport(ServerPlayer p) {
        HitResult hr = p.pick(32.0, 1.0f, false);
        Vec3 pos = hr.getLocation();
        if (hr instanceof BlockHitResult bh) {
            Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
            pos = pos.add(n.scale(0.6));
        }
        p.teleportTo(pos.x, pos.y, pos.z);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        return true;
    }

    private static boolean shulkerBullet(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        LivingEntity tgt = lvl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), x -> x != p)
                .stream().min(Comparator.comparingDouble(x -> x.distanceToSqr(p))).orElse(null);
        if (tgt == null) return false;
        ShulkerBullet b = new ShulkerBullet(lvl, p, tgt, Direction.Axis.Y);
        b.setPos(p.getX(), p.getEyeY(), p.getZ());
        lvl.addFreshEntity(b);
        return true;
    }

    private static void sonicBoom(ServerPlayer p, ServerLevel lvl, Vec3 look) {
        Vec3 eye = p.getEyePosition();
        for (LivingEntity le : lvl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(15), x -> x != p)) {
            Vec3 to = le.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist < 0.1 || dist > 15) continue;
            if (to.normalize().dot(look) < 0.9) continue;
            le.hurt(lvl.damageSources().sonicBoom(p), 10f);
            le.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
            le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        }
        for (int i = 1; i <= 15; i++) {
            lvl.sendParticles(ParticleTypes.SONIC_BOOM, eye.x + look.x * i, eye.y + look.y * i, eye.z + look.z * i, 1, 0, 0, 0, 0);
        }
        lvl.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 1f);
    }
}
