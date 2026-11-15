package com.adrian.posmortal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tu reflejo: jefe final. Se dibuja con la skin del jugador que lo invocó. Muy rápido, muy fuerte, 3 fases. */
public class Doppelganger extends Monster {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(Doppelganger.class, EntityDataSerializers.OPTIONAL_UUID);

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.literal("Tu reflejo"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private static class Pending {
        int t;
        final Runnable r;

        Pending(int t, Runnable r) {
            this.t = t;
            this.r = r;
        }
    }

    private final List<Pending> pending = new ArrayList<>();
    private int attackCd = 80;
    private int intro = 80;
    private int phase = 1;

    public Doppelganger(EntityType<? extends Doppelganger> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.MOVEMENT_SPEED, 0.40)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.ARMOR, 10.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
    }

    public void setOwnerId(UUID id) {
        this.entityData.set(OWNER, Optional.ofNullable(id));
    }

    public Optional<UUID> getOwnerId() {
        return this.entityData.get(OWNER);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getOwnerId().ifPresent(id -> tag.putUUID("Owner", id));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) setOwnerId(tag.getUUID("Owner"));
        intro = 0;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        bossEvent.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        bossEvent.removePlayer(p);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (intro > 0 && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.IS_LIGHTNING)) {
            return false; // sus propios ataques no le hacen daño
        }
        return super.hurt(src, amount);
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (!level().isClientSide) BossFight.onBossDeath(this);
    }

    // ---------------------------------------------------------------- IA de jefe

    private void later(int ticks, Runnable r) {
        pending.add(new Pending(ticks, r));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        float f = getHealth() / getMaxHealth();
        bossEvent.setProgress(f);

        int newPhase = f > 0.66f ? 1 : f > 0.33f ? 2 : 3;
        if (newPhase != phase) {
            phase = newPhase;
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(phase == 2 ? 0.46 : 0.56);
            ServerLevel lvl = (ServerLevel) level();
            lvl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 60, 0.6, 1.0, 0.6, 0.1);
            lvl.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 1.4f);
        }

        if (intro > 0) {
            intro--;
            return;
        }

        List<Pending> due = new ArrayList<>();
        for (Pending p : pending) if (--p.t <= 0) due.add(p);
        pending.removeAll(due);
        for (Pending p : due) p.r.run();

        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return;
        if (--attackCd <= 0) {
            attack(t);
            attackCd = (phase == 1 ? 55 : phase == 2 ? 38 : 22) + random.nextInt(15);
        }
    }

    private void attack(LivingEntity t) {
        int n = random.nextInt(phase == 1 ? 5 : phase == 2 ? 7 : 9);
        switch (n) {
            case 0 -> dashStrike(t);
            case 1 -> bladeRain(t);
            case 2 -> slam(t);
            case 3 -> darkFan(t);
            case 4 -> backstab(t);
            case 5 -> drain(t);
            case 6 -> gravityPull(t);
            case 7 -> lightning(t);
            default -> summonVexes(t);
        }
    }

    private ServerLevel lvl() {
        return (ServerLevel) level();
    }

    private void hitArea(double r, float dmg, double kb, boolean up) {
        for (LivingEntity e : lvl().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r),
                x -> x != this && !(x instanceof Doppelganger) && x.isAlive())) {
            if (distanceTo(e) > r) continue;
            e.hurt(damageSources().mobAttack(this), dmg);
            e.knockback(kb, getX() - e.getX(), getZ() - e.getZ());
            if (up) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.8, 0));
                e.hurtMarked = true;
            }
        }
    }

    /** 1) Embestida: se lanza hacia ti y barre alrededor. */
    private void dashStrike(LivingEntity t) {
        Vec3 dir = t.position().subtract(position()).normalize();
        lvl().sendParticles(ParticleTypes.CRIT, getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0.1);
        setDeltaMovement(dir.x * 2.2, 0.2, dir.z * 2.2);
        hurtMarked = true;
        later(7, () -> {
            hitArea(4.2, 11f, 1.3, false);
            lvl().sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1, getZ(), 6, 1.2, 0.3, 1.2, 0);
            lvl().playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1f, 0.8f);
        });
    }

    /** 2) Lluvia de flechas sobre tu posición (se marca antes con partículas). */
    private void bladeRain(LivingEntity t) {
        final Vec3 c = t.position();
        for (int i = 0; i < 10; i++) {
            double ox = random.nextDouble() * 6 - 3, oz = random.nextDouble() * 6 - 3;
            lvl().sendParticles(ParticleTypes.ENCHANTED_HIT, c.x + ox, c.y + 0.2, c.z + oz, 4, 0.1, 0.1, 0.1, 0);
        }
        later(18, () -> {
            for (int i = 0; i < 22; i++) {
                Arrow a = new Arrow(lvl(), this);
                a.setPos(c.x + random.nextDouble() * 6 - 3, c.y + 10, c.z + random.nextDouble() * 6 - 3);
                a.setDeltaMovement(0, -1.8, 0);
                a.setBaseDamage(2.4);
                a.pickup = AbstractArrow.Pickup.DISALLOWED;
                lvl().addFreshEntity(a);
            }
        });
    }

    /** 3) Salto y aplastamiento: onda de choque que te lanza por los aires. */
    private void slam(LivingEntity t) {
        setDeltaMovement(0, 1.1, 0);
        hurtMarked = true;
        later(22, () -> {
            hitArea(7.5, 14f, 1.5, true);
            lvl().sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 12, 2.5, 0.2, 2.5, 0);
            lvl().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.7f);
        });
    }

    /** 4) Abanico de dardos oscuros con efecto Wither. */
    private void darkFan(LivingEntity t) {
        Vec3 base = t.getEyePosition().subtract(getEyePosition()).normalize();
        for (int i = -3; i <= 3; i++) {
            double ang = Math.toRadians(i * 11);
            double dx = base.x * Math.cos(ang) - base.z * Math.sin(ang);
            double dz = base.x * Math.sin(ang) + base.z * Math.cos(ang);
            Arrow a = new Arrow(lvl(), this);
            a.setPos(getX() + dx, getEyeY() - 0.2, getZ() + dz);
            a.shoot(dx, base.y, dz, 2.6f, 0f);
            a.setBaseDamage(2.2);
            a.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
            a.setSecondsOnFire(100);
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            lvl().addFreshEntity(a);
        }
        lvl().playSound(null, blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1f, 1.3f);
    }

    /** 5) Se teletransporta a tu espalda y te apuñala. */
    private void backstab(LivingEntity t) {
        Vec3 look = t.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-6) flat = new Vec3(0, 0, 1);
        Vec3 behind = t.position().subtract(flat.normalize().scale(1.6));
        lvl().sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 40, 0.3, 0.8, 0.3, 0.5);
        teleportTo(behind.x, t.getY(), behind.z);
        lvl().playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1.2f);
        later(8, () -> {
            if (distanceTo(t) < 4.0) {
                t.hurt(damageSources().mobAttack(this), 14f);
                lvl().sendParticles(ParticleTypes.DAMAGE_INDICATOR, t.getX(), t.getY() + 1, t.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
            }
        });
    }

    /** 6) Drenaje: te quita vida y se cura (3 pulsos). */
    private void drain(LivingEntity t) {
        for (int i = 0; i < 3; i++) {
            later(i * 10 + 1, () -> {
                if (!t.isAlive() || distanceTo(t) > 18) return;
                t.hurt(damageSources().magic(), 6f);
                heal(10f);
                Vec3 a = getEyePosition(), b = t.getEyePosition();
                for (int k = 0; k <= 10; k++) {
                    Vec3 p = a.lerp(b, k / 10.0);
                    lvl().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            });
        }
    }

    /** 7) Gravedad: te arrastra hacia él y golpea. */
    private void gravityPull(LivingEntity t) {
        Vec3 dir = position().subtract(t.position()).normalize();
        t.setDeltaMovement(dir.x * 1.6, 0.35, dir.z * 1.6);
        t.hurtMarked = true;
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
        later(12, () -> hitArea(4.0, 12f, 1.0, false));
    }

    /** 8) Tormenta: tres rayos sobre tu posición. */
    private void lightning(LivingEntity t) {
        final Vec3 c = t.position();
        lvl().sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.5, c.z, 30, 1.5, 0.2, 1.5, 0.1);
        for (int i = 0; i < 5; i++) {
            final int k = i;
            later(16 + i * 7, () -> {
                LightningBolt lb = EntityType.LIGHTNING_BOLT.create(lvl());
                if (lb == null) return;
                lb.moveTo(c.x + (k == 0 ? 0 : random.nextDouble() * 6 - 3), c.y, c.z + (k == 0 ? 0 : random.nextDouble() * 6 - 3));
                lvl().addFreshEntity(lb);
            });
        }
    }

    /** 9) Eclipse: invoca vex que te persiguen. */
    private void summonVexes(LivingEntity t) {
        for (int i = 0; i < 4; i++) {
            Vex v = EntityType.VEX.create(lvl());
            if (v == null) continue;
            v.moveTo(getX() + random.nextDouble() * 4 - 2, getY() + 1.5, getZ() + random.nextDouble() * 4 - 2, getYRot(), 0f);
            v.setLimitedLife(20 * 20);
            v.setTarget(t);
            lvl().addFreshEntity(v);
        }
        lvl().sendParticles(ParticleTypes.POOF, getX(), getY() + 1.5, getZ(), 25, 0.8, 0.8, 0.8, 0.05);
    }
}
