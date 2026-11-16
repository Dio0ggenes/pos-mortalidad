package com.adrian.posmortal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** El fantasma del limbo: poca vida (50), flota hacia ti y ataca. Al morir suelta Spectralium y hierro. */
public class LimboGhost extends Monster {
    private int intro = 60;
    private int attackCd = 0;
    private int dashCd = 90;

    public LimboGhost(EntityType<? extends LimboGhost> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.xpReward = 10;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
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
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (intro > 0) {
            intro--;
            return;
        }
        Player t = level().getNearestPlayer(this, 48);
        if (t == null) return;

        Vec3 center = position().add(0, getBbHeight() / 2, 0);
        Vec3 to = t.getEyePosition().subtract(center);
        double d = to.length();
        if (d < 0.01) return;
        Vec3 dir = to.scale(1.0 / d);

        double speed = d > 2.0 ? 0.17 : 0.0;
        setDeltaMovement(getDeltaMovement().scale(0.6).add(dir.scale(speed)));
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * 57.29578);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;

        if (attackCd > 0) attackCd--;
        if (d < 2.6 && attackCd <= 0) {
            t.hurt(damageSources().mobAttack(this), 5f);
            t.knockback(0.6, getX() - t.getX(), getZ() - t.getZ());
            attackCd = 24;
        }
        if (--dashCd <= 0) {          // lanzada espectral
            dashCd = 90 + random.nextInt(40);
            setDeltaMovement(dir.scale(0.9));
            hurtMarked = true;
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(src, looting, recentlyHit);
        spawnAtLocation(new net.minecraft.world.item.ItemStack(Posmortal.SPECTRALIUM.get(), 1 + random.nextInt(3)));
        spawnAtLocation(new net.minecraft.world.item.ItemStack(Items.IRON_INGOT, 1 + random.nextInt(3)));
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (!level().isClientSide) Limbo.onGhostDeath((ServerLevel) level());
    }
}
