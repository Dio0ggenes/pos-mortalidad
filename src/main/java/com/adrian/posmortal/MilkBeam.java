package com.adrian.posmortal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Rayo de leche de la vaca: no hace daño, CURA a lo que toca. */
public class MilkBeam extends Projectile {
    private int life;

    public MilkBeam(EntityType<? extends MilkBeam> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 delta = getDeltaMovement();
        HitResult hr = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hr.getType() != HitResult.Type.MISS) {
            onHit(hr);
        }
        setPos(getX() + delta.x, getY() + delta.y, getZ() + delta.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5f);
        if (!level().isClientSide && ++life > 40) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        Entity t = result.getEntity();
        if (t instanceof LivingEntity le) {
            le.heal(8f);
            ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, le.getX(), le.getY() + le.getBbHeight() + 0.2, le.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide) discard();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
