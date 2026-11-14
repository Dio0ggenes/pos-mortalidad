package com.adrian.posmortal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Habilidades de cada cuerpo. V = principal, Shift+V = alternativa (si no hay alternativa, usa la principal).
 * TODAS las entidades tienen habilidad: las que no están en la tabla usan una por categoría.
 */
public class Abilities {
    private interface Run {
        void run(ServerPlayer p, ServerLevel lvl, Vec3 look);
    }

    private record Def(int cd, Run run) {
    }

    private static final Map<String, Def> MAIN = new HashMap<>();
    private static final Map<String, Def> ALT = new HashMap<>();
    private static final Map<String, Long> CD = new HashMap<>();
    private static final Random RNG = new Random();

    private static final Set<String> BURNERS = Set.of("minecraft:zombie", "minecraft:skeleton", "minecraft:stray",
            "minecraft:drowned", "minecraft:zombie_villager");
    private static final Set<String> FIREPROOF = Set.of("minecraft:blaze", "minecraft:ghast", "minecraft:wither_skeleton",
            "minecraft:strider", "minecraft:magma_cube", "minecraft:wither", "minecraft:zombified_piglin",
            "minecraft:ender_dragon");
    private static final Set<String> WATER = Set.of("minecraft:squid", "minecraft:glow_squid", "minecraft:cod",
            "minecraft:salmon", "minecraft:pufferfish", "minecraft:tropical_fish", "minecraft:dolphin",
            "minecraft:guardian", "minecraft:elder_guardian", "minecraft:drowned", "minecraft:axolotl",
            "minecraft:turtle");

    // ---------------------------------------------------------------- helpers

    private static void main(int cd, Run r, String... ids) {
        Def d = new Def(cd, r);
        for (String id : ids) MAIN.put("minecraft:" + id, d);
    }

    private static void alt(int cd, Run r, String... ids) {
        Def d = new Def(cd, r);
        for (String id : ids) ALT.put("minecraft:" + id, d);
    }

    private static void msg(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s), true);
    }

    private static DamageSource src(ServerPlayer p) {
        return p.serverLevel().damageSources().playerAttack(p);
    }

    private static List<LivingEntity> around(ServerPlayer p, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e != p && e.isAlive() && !Pets.owned(e, p));
    }

    private static void aoe(ServerPlayer p, double r, float dmg, double kb, Consumer<LivingEntity> extra) {
        for (LivingEntity t : around(p, r)) {
            if (p.distanceTo(t) > r) continue;
            if (dmg > 0) t.hurt(src(p), dmg);
            if (kb > 0) t.knockback(kb, p.getX() - t.getX(), p.getZ() - t.getZ());
            if (extra != null) extra.accept(t);
        }
    }

    private static void ahead(ServerPlayer p, Vec3 look, double reach, float dmg, double kb, Consumer<LivingEntity> extra) {
        Vec3 eye = p.getEyePosition();
        for (LivingEntity t : around(p, reach)) {
            Vec3 to = t.getEyePosition().subtract(eye);
            double d = to.length();
            if (d > reach || d < 0.01 || to.normalize().dot(look) < 0.5) continue;
            if (dmg > 0) t.hurt(src(p), dmg);
            if (kb > 0) t.knockback(kb, p.getX() - t.getX(), p.getZ() - t.getZ());
            if (extra != null) extra.accept(t);
        }
    }

    private static LivingEntity target(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestD = range + 1;
        for (LivingEntity t : around(p, range)) {
            Vec3 to = t.getEyePosition().subtract(eye);
            double d = to.length();
            if (d <= range && d > 0.01 && to.normalize().dot(look) > 0.93 && d < bestD) {
                best = t;
                bestD = d;
            }
        }
        return best;
    }

    private static void dash(ServerPlayer p, Vec3 look, double power, double up) {
        p.setDeltaMovement(look.x * power, look.y * power * 0.5 + up, look.z * power);
        p.hurtMarked = true;
    }

    private static void buff(LivingEntity e, MobEffect eff, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(eff, ticks, amp, false, true));
    }

    private static void healAround(ServerPlayer p, double r, float amount) {
        p.heal(amount);
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                x -> x != p && x.isAlive() && (x instanceof ServerPlayer || Pets.owned(x, p)))) {
            e.heal(amount);
        }
        p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.5, p.getZ(), 8, 0.5, 0.5, 0.5, 0.02);
    }

    private static void sound(ServerPlayer p, SoundEvent s) {
        p.serverLevel().playSound(null, p.blockPosition(), s, SoundSource.PLAYERS, 1f, 1f);
    }

    private static void puff(ServerPlayer p, net.minecraft.core.particles.ParticleOptions o, int n) {
        p.serverLevel().sendParticles(o, p.getX(), p.getY() + 1, p.getZ(), n, 0.6, 0.6, 0.6, 0.05);
    }

    private static void launch(ServerLevel lvl, ServerPlayer p, Entity proj, Vec3 look) {
        proj.setPos(p.getX() + look.x * 1.5, p.getEyeY() - 0.2 + look.y * 1.5, p.getZ() + look.z * 1.5);
        lvl.addFreshEntity(proj);
    }

    /** Gasta una flecha del inventario del jugador (el creativo no gasta). */
    private static boolean takeArrow(ServerPlayer p) {
        if (p.getAbilities().instabuild) return true;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(Items.ARROW)) {
                s.shrink(1);
                return true;
            }
        }
        msg(p, "Necesitas flechas en el inventario");
        return false;
    }

    private static void shootArrow(ServerPlayer p, ServerLevel lvl, boolean fire, MobEffect effect) {
        if (!takeArrow(p)) return;
        Arrow a = new Arrow(lvl, p);
        a.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 3f, 1f);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        if (fire) a.setSecondsOnFire(100);
        if (effect != null) a.addEffect(new MobEffectInstance(effect, 200, 0));
        lvl.addFreshEntity(a);
        sound(p, SoundEvents.SKELETON_SHOOT);
    }

    private static boolean teleport(ServerPlayer p, double range) {
        HitResult hr = p.pick(range, 1.0f, false);
        Vec3 pos = hr.getLocation();
        if (hr instanceof BlockHitResult bh) {
            Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
            pos = pos.add(n.scale(0.6));
        }
        p.teleportTo(pos.x, pos.y, pos.z);
        sound(p, SoundEvents.ENDERMAN_TELEPORT);
        return true;
    }

    private static void giveOrDrop(ServerPlayer p, ItemStack st) {
        if (!p.getInventory().add(st)) p.drop(st, false);
    }

    private static boolean takeItem(ServerPlayer p, net.minecraft.world.item.Item item) {
        if (p.getAbilities().instabuild) return true;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static CompoundTag body(ServerPlayer p) {
        return PossessionEvents.bodyTag(p);
    }

    // ---------------------------------------------------------------- tabla de habilidades

    static {
        // ===== animales =====
        main(100, (p, l, look) -> healAround(p, 6, 4f), "allay");
        alt(40, (p, l, look) -> {
            for (Entity e : l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(12))) {
                e.setDeltaMovement(p.position().add(0, 1, 0).subtract(e.position()).scale(0.25));
            }
        }, "allay");

        main(300, (p, l, look) -> {
            buff(p, MobEffects.REGENERATION, 160, 2);
            buff(p, MobEffects.DAMAGE_RESISTANCE, 160, 1);
        }, "axolotl");
        alt(40, (p, l, look) -> dash(p, look, 1.2, 0.1), "axolotl");

        main(200, (p, l, look) -> {
            for (LivingEntity t : around(p, 24)) buff(t, MobEffects.GLOWING, 200, 0);
            sound(p, SoundEvents.BAT_TAKEOFF);
        }, "bat");

        main(50, (p, l, look) -> {
            dash(p, look, 1.4, 0.1);
            ahead(p, look, 3, 3f, 0.3, t -> buff(t, MobEffects.POISON, 200, 0));
        }, "bee");
        alt(80, (p, l, look) -> {
            for (BlockPos bp : BlockPos.betweenClosed(p.blockPosition().offset(-4, -1, -4), p.blockPosition().offset(4, 1, 4))) {
                net.minecraft.world.item.BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), l, bp.immutable());
            }
            puff(p, ParticleTypes.HAPPY_VILLAGER, 20);
        }, "bee");

        main(60, (p, l, look) -> dash(p, look, 2.0, 0.2), "camel", "horse", "donkey", "mule", "skeleton_horse", "zombie_horse");
        alt(120, (p, l, look) -> {
            buff(p, MobEffects.MOVEMENT_SPEED, 120, 2);
            buff(p, MobEffects.JUMP, 120, 2);
        }, "horse", "skeleton_horse", "zombie_horse", "camel");
        alt(60, (p, l, look) -> aoe(p, 3.5, 5f, 1.2, null), "donkey", "mule");

        main(100, (p, l, look) -> aoe(p, 8, 0f, 1.0, t -> buff(t, MobEffects.MOVEMENT_SLOWDOWN, 60, 1)), "cat");
        alt(600, (p, l, look) -> {
            net.minecraft.world.item.Item[] g = {Items.STRING, Items.FEATHER, Items.RABBIT_HIDE, Items.PHANTOM_MEMBRANE, Items.RABBIT_FOOT, Items.COOKED_CHICKEN};
            p.spawnAtLocation(g[RNG.nextInt(g.length)]);
            sound(p, SoundEvents.CAT_PURREOW);
        }, "cat");

        main(30, (p, l, look) -> {
            ThrownEgg egg = new ThrownEgg(l, p);
            egg.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 1.5f, 1f);
            l.addFreshEntity(egg);
            sound(p, SoundEvents.CHICKEN_EGG);
        }, "chicken");
        alt(200, (p, l, look) -> {
            p.spawnAtLocation(Items.EGG);
            sound(p, SoundEvents.CHICKEN_EGG);
        }, "chicken");

        main(40, (p, l, look) -> dash(p, look, 1.6, 0.05), "cod", "salmon", "tropical_fish", "tadpole", "silverfish");
        main(80, (p, l, look) -> {
            aoe(p, 3, 4f, 0.8, t -> buff(t, MobEffects.POISON, 120, 1));
            puff(p, ParticleTypes.BUBBLE_POP, 30);
        }, "pufferfish");
        main(60, (p, l, look) -> {
            buff(p, MobEffects.DOLPHINS_GRACE, 200, 1);
            dash(p, look, 1.8, 0.1);
        }, "dolphin");
        alt(80, (p, l, look) -> buff(p, MobEffects.JUMP, 80, 3), "salmon", "cod", "tropical_fish");

        // vaca: rayo de leche que CURA / ordeñarse
        main(40, (p, l, look) -> {
            MilkBeam b = new MilkBeam(Posmortal.MILK_BEAM.get(), l);
            b.setOwner(p);
            b.setPos(p.getX() + look.x * 1.2, p.getEyeY() - 0.2 + look.y * 1.2, p.getZ() + look.z * 1.2);
            b.shoot(look.x, look.y, look.z, 1.2f, 0f);
            l.addFreshEntity(b);
            sound(p, SoundEvents.COW_AMBIENT);
        }, "cow", "mooshroom");
        alt(20, (p, l, look) -> {
            if (takeItem(p, Items.BUCKET)) {
                giveOrDrop(p, new ItemStack(Items.MILK_BUCKET));
                sound(p, SoundEvents.COW_MILK);
            } else {
                msg(p, "Necesitas un cubo vacío para ordeñarte");
            }
        }, "cow");
        alt(20, (p, l, look) -> {
            if (takeItem(p, Items.BOWL)) {
                giveOrDrop(p, new ItemStack(Items.MUSHROOM_STEW));
                sound(p, SoundEvents.MOOSHROOM_MILK);
            } else {
                msg(p, "Necesitas un cuenco para ordeñarte");
            }
        }, "mooshroom");

        main(80, (p, l, look) -> {
            dash(p, look, 1.5, 0.35);
            ahead(p, look, 3.5, 4f, 0.5, null);
        }, "fox", "wolf", "cave_spider", "ocelot");
        alt(300, (p, l, look) -> buff(p, MobEffects.INVISIBILITY, 100, 0), "fox", "ocelot");
        alt(200, (p, l, look) -> {
            for (LivingEntity t : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), x -> x == p || x instanceof ServerPlayer || Pets.owned(x, p))) {
                buff(t, MobEffects.DAMAGE_BOOST, 200, 0);
                buff(t, MobEffects.MOVEMENT_SPEED, 200, 0);
            }
            sound(p, SoundEvents.WOLF_AMBIENT);
        }, "wolf");

        main(60, (p, l, look) -> {
            LivingEntity t = target(p, 8);
            if (t != null) {
                t.setDeltaMovement(p.position().subtract(t.position()).normalize().scale(1.2));
                t.hurtMarked = true;
                t.hurt(src(p), 2f);
                sound(p, SoundEvents.FROG_TONGUE);
            }
        }, "frog");
        alt(40, (p, l, look) -> {
            p.setDeltaMovement(look.x * 0.8, 1.0, look.z * 0.8);
            p.hurtMarked = true;
        }, "frog", "rabbit", "slime", "magma_cube");

        main(120, (p, l, look) -> {
            for (LivingEntity t : around(p, 10)) buff(t, MobEffects.GLOWING, 160, 0);
            puff(p, ParticleTypes.GLOW_SQUID_INK, 40);
        }, "glow_squid");
        main(100, (p, l, look) -> {
            for (LivingEntity t : around(p, 6)) {
                buff(t, MobEffects.BLINDNESS, 80, 0);
                buff(t, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
            }
            puff(p, ParticleTypes.SQUID_INK, 40);
            dash(p, look.scale(-1), 1.4, 0.1);
        }, "squid");

        main(60, (p, l, look) -> {
            dash(p, look, 2.0, 0.15);
            ahead(p, look, 4, 7f, 1.5, null);
        }, "goat", "hoglin", "zoglin", "polar_bear", "vindicator", "piglin_brute");
        alt(100, (p, l, look) -> aoe(p, 6, 0f, 1.6, null), "goat");

        main(60, (p, l, look) -> {
            LivingEntity t = target(p, 15);
            if (t != null) {
                t.hurt(src(p), 6f);
                sound(p, SoundEvents.GUARDIAN_ATTACK);
                l.sendParticles(ParticleTypes.BUBBLE, t.getX(), t.getY() + 1, t.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
            }
        }, "guardian");
        main(160, (p, l, look) -> {
            for (LivingEntity t : around(p, 24)) buff(t, MobEffects.DIG_SLOWDOWN, 400, 2);
            sound(p, SoundEvents.ELDER_GUARDIAN_CURSE);
        }, "elder_guardian");
        alt(60, (p, l, look) -> {
            LivingEntity t = target(p, 15);
            if (t != null) t.hurt(src(p), 6f);
        }, "elder_guardian");

        main(100, (p, l, look) -> {
            aoe(p, 5, 8f, 1.0, t -> t.setDeltaMovement(t.getDeltaMovement().add(0, 0.7, 0)));
            puff(p, ParticleTypes.CLOUD, 30);
            sound(p, SoundEvents.IRON_GOLEM_ATTACK);
        }, "iron_golem");
        alt(100, (p, l, look) -> healAround(p, 6, 4f), "iron_golem");

        main(50, (p, l, look) -> {
            LlamaSpit s = new LlamaSpit(EntityType.LLAMA_SPIT, l);
            s.setOwner(p);
            s.setPos(p.getX() + look.x, p.getEyeY() - 0.2, p.getZ() + look.z);
            s.shoot(look.x, look.y + 0.1, look.z, 1.5f, 5f);
            l.addFreshEntity(s);
            sound(p, SoundEvents.LLAMA_SPIT);
        }, "llama", "trader_llama");

        main(80, (p, l, look) -> {
            dash(p, look, 1.2, 0.2);
            ahead(p, look, 3.5, 8f, 1.2, null);
        }, "panda");
        alt(120, (p, l, look) -> {
            aoe(p, 3, 0f, 1.0, null);
            p.spawnAtLocation(Items.SLIME_BALL);
        }, "panda");

        main(60, (p, l, look) -> dash(p, look, 2.2, 0.3), "parrot", "pig", "sniffer");
        alt(120, (p, l, look) -> aoe(p, 8, 0f, 0.5, t -> buff(t, MobEffects.WEAKNESS, 100, 0)), "parrot");
        alt(400, (p, l, look) -> {
            net.minecraft.world.item.Item[] g = {Items.CARROT, Items.POTATO, Items.BEETROOT};
            p.spawnAtLocation(g[RNG.nextInt(g.length)]);
        }, "pig");
        alt(300, (p, l, look) -> {
            net.minecraft.world.item.Item[] g = {Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD};
            p.spawnAtLocation(g[RNG.nextInt(g.length)]);
        }, "sniffer");

        main(60, (p, l, look) -> {
            p.setDeltaMovement(look.x * 1.0, 1.1, look.z * 1.0);
            p.hurtMarked = true;
        }, "rabbit");

        // oveja: explota y lanza lana de su color
        main(100, (p, l, look) -> {
            int c = body(p).getByte("Color");
            DyeColor color = DyeColor.byId(c);
            Block wool = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(color.getName() + "_wool"));
            if (wool == null || wool == Blocks.AIR) wool = Blocks.WHITE_WOOL;
            double x = p.getX(), y = p.getY(), z = p.getZ();
            BlockPos pos = BlockPos.containing(x, y + 1, z);
            for (int i = 0; i < 12; i++) {
                FallingBlockEntity fb = FallingBlockEntity.fall(l, pos, wool.defaultBlockState());
                fb.dropItem = false;
                fb.setHurtsEntities(2f, 40);
                fb.setPos(x, y + 1.2, z);
                double a = RNG.nextDouble() * Math.PI * 2;
                fb.setDeltaMovement(Math.cos(a) * 0.5, 0.5 + RNG.nextDouble() * 0.4, Math.sin(a) * 0.5);
            }
            PossessionEvents.exitPossession(p, false); // el cuerpo explota
            p.setInvulnerable(true);
            l.explode(p, x, y, z, 2f, Level.ExplosionInteraction.NONE);
            p.setInvulnerable(false);
        }, "sheep");
        alt(200, (p, l, look) -> {
            int c = body(p).getByte("Color");
            Block wool = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(DyeColor.byId(c).getName() + "_wool"));
            if (wool != null) p.spawnAtLocation(wool.asItem(), 1);
            sound(p, SoundEvents.SHEEP_SHEAR);
        }, "sheep");

        main(100, (p, l, look) -> {
            buff(p, MobEffects.FIRE_RESISTANCE, 400, 0);
            buff(p, MobEffects.MOVEMENT_SPEED, 400, 1);
        }, "strider");

        main(200, (p, l, look) -> {
            buff(p, MobEffects.DAMAGE_RESISTANCE, 80, 3);
            buff(p, MobEffects.MOVEMENT_SLOWDOWN, 80, 2);
        }, "turtle");

        main(40, (p, l, look) -> {
            Snowball s = new Snowball(l, p);
            s.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 1.5f, 1f);
            l.addFreshEntity(s);
        }, "snow_golem");
        alt(120, (p, l, look) -> aoe(p, 6, 0f, 0.3, t -> {
            buff(t, MobEffects.MOVEMENT_SLOWDOWN, 100, 1);
            t.setTicksFrozen(t.getTicksRequiredToFreeze() + 60);
        }), "snow_golem");

        main(600, (p, l, look) -> buff(p, MobEffects.HERO_OF_THE_VILLAGE, 1200, 0), "villager");
        alt(100, (p, l, look) -> healAround(p, 6, 4f), "villager");
        main(300, (p, l, look) -> {
            buff(p, MobEffects.INVISIBILITY, 300, 0);
            buff(p, MobEffects.MOVEMENT_SPEED, 300, 0);
        }, "wandering_trader");

        // ===== no muertos =====
        main(300, (p, l, look) -> {
            if (Pets.count(p) >= 6) {
                msg(p, "Ya tienes demasiadas mascotas");
                return;
            }
            for (int i = 0; i < 2; i++) {
                net.minecraft.world.entity.monster.Zombie z = EntityType.ZOMBIE.create(l);
                if (z == null) continue;
                z.moveTo(p.getX() + RNG.nextDouble() * 3 - 1.5, p.getY(), p.getZ() + RNG.nextDouble() * 3 - 1.5, p.getYRot(), 0f);
                z.getPersistentData().putUUID(PossessionEvents.OWNER, p.getUUID());
                z.setPersistenceRequired();
                l.addFreshEntity(z);
            }
            sound(p, SoundEvents.ZOMBIE_AMBIENT);
        }, "zombie", "zombie_villager");
        alt(60, (p, l, look) -> ahead(p, look, 3.5, 5f, 0.6, t -> buff(t, MobEffects.HUNGER, 200, 1)), "zombie", "zombie_villager", "husk");
        main(120, (p, l, look) -> aoe(p, 6, 0f, 0.4, t -> {
            buff(t, MobEffects.HUNGER, 200, 1);
            buff(t, MobEffects.BLINDNESS, 80, 0);
        }), "husk");
        main(60, (p, l, look) -> {
            ThrownTrident tr = new ThrownTrident(l, p, new ItemStack(Items.TRIDENT));
            tr.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 2.5f, 1f);
            tr.pickup = AbstractArrow.Pickup.DISALLOWED;
            l.addFreshEntity(tr);
        }, "drowned");
        main(300, (p, l, look) -> {
            buff(p, MobEffects.DAMAGE_BOOST, 200, 1);
            buff(p, MobEffects.MOVEMENT_SPEED, 200, 1);
        }, "zombified_piglin");
        main(100, (p, l, look) -> {
            if (takeItem(p, Items.GOLD_INGOT)) {
                net.minecraft.world.item.Item[] g = {Items.ENDER_PEARL, Items.IRON_NUGGET, Items.OBSIDIAN, Items.GRAVEL, Items.LEATHER, Items.STRING, Items.FIRE_CHARGE, Items.SPECTRAL_ARROW};
                p.spawnAtLocation(g[RNG.nextInt(g.length)]);
            } else {
                msg(p, "Necesitas un lingote de oro para el trueque");
            }
        }, "piglin");
        alt(300, (p, l, look) -> {
            buff(p, MobEffects.DAMAGE_BOOST, 200, 0);
            buff(p, MobEffects.DAMAGE_RESISTANCE, 200, 0);
        }, "vindicator", "piglin_brute", "piglin");

        main(20, (p, l, look) -> shootArrow(p, l, false, null), "skeleton", "pillager");
        main(20, (p, l, look) -> shootArrow(p, l, false, MobEffects.MOVEMENT_SLOWDOWN), "stray");
        main(20, (p, l, look) -> shootArrow(p, l, true, MobEffects.WITHER), "wither_skeleton");
        alt(100, (p, l, look) -> {
            for (int i = -1; i <= 1; i++) {
                if (!takeArrow(p)) break;
                Arrow a = new Arrow(l, p);
                a.shootFromRotation(p, p.getXRot(), p.getYRot() + i * 10f, 0f, 3f, 1f);
                a.pickup = AbstractArrow.Pickup.DISALLOWED;
                l.addFreshEntity(a);
            }
        }, "skeleton", "stray", "pillager", "wither_skeleton");

        // ===== monstruos =====
        main(60, (p, l, look) -> {
            double x = p.getX(), y = p.getY(), z = p.getZ();
            PossessionEvents.exitPossession(p, false); // el cuerpo explota, tú sigues vivo como fantasma
            p.setInvulnerable(true);
            l.explode(p, x, y, z, 3f, Level.ExplosionInteraction.MOB);
            p.setInvulnerable(false);
        }, "creeper");

        main(20, (p, l, look) -> teleport(p, 32), "enderman");
        alt(40, (p, l, look) -> {
            p.teleportTo(p.getX() + RNG.nextInt(17) - 8, p.getY() + 1, p.getZ() + RNG.nextInt(17) - 8);
            sound(p, SoundEvents.ENDERMAN_TELEPORT);
        }, "enderman", "endermite");
        main(40, (p, l, look) -> {
            p.teleportTo(p.getX() + RNG.nextInt(13) - 6, p.getY(), p.getZ() + RNG.nextInt(13) - 6);
            sound(p, SoundEvents.ENDERMAN_TELEPORT);
        }, "endermite");

        main(60, (p, l, look) -> teleport(p, 32), "shulker");
        alt(40, (p, l, look) -> {
            LivingEntity tgt = l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), x -> x != p && !Pets.owned(x, p))
                    .stream().min(Comparator.comparingDouble(x -> x.distanceToSqr(p))).orElse(null);
            if (tgt == null) return;
            ShulkerBullet b = new ShulkerBullet(l, p, tgt, Direction.Axis.Y);
            b.setPos(p.getX(), p.getEyeY(), p.getZ());
            l.addFreshEntity(b);
        }, "shulker");

        main(40, (p, l, look) -> launch(l, p, new DragonFireball(l, p, look.x, look.y, look.z), look), "ender_dragon");
        alt(100, (p, l, look) -> {
            ahead(p, look, 8, 6f, 0.5, t -> t.setSecondsOnFire(4));
            for (int i = 1; i <= 8; i++) {
                l.sendParticles(ParticleTypes.DRAGON_BREATH, p.getX() + look.x * i, p.getEyeY() + look.y * i, p.getZ() + look.z * i, 6, 0.3, 0.3, 0.3, 0.02);
            }
            sound(p, SoundEvents.ENDER_DRAGON_GROWL);
        }, "ender_dragon");

        main(10, (p, l, look) -> launch(l, p, new WitherSkull(l, p, look.x, look.y, look.z), look), "wither");
        alt(1200, (p, l, look) -> {
            WitherBoss w = EntityType.WITHER.create(l);
            if (w == null) return;
            w.moveTo(p.getX() + look.x * 4, p.getY(), p.getZ() + look.z * 4, p.getYRot(), 0f);
            w.getPersistentData().putUUID(PossessionEvents.OWNER, p.getUUID());
            w.makeInvulnerable();
            l.addFreshEntity(w);
        }, "wither");

        main(100, (p, l, look) -> {
            Vec3 eye = p.getEyePosition();
            for (LivingEntity le : around(p, 15)) {
                Vec3 to = le.getEyePosition().subtract(eye);
                double dist = to.length();
                if (dist < 0.1 || dist > 15 || to.normalize().dot(look) < 0.9) continue;
                le.hurt(l.damageSources().sonicBoom(p), 10f);
                buff(le, MobEffects.DARKNESS, 200, 0);
                buff(le, MobEffects.BLINDNESS, 100, 0);
            }
            for (int i = 1; i <= 15; i++) {
                l.sendParticles(ParticleTypes.SONIC_BOOM, eye.x + look.x * i, eye.y + look.y * i, eye.z + look.z * i, 1, 0, 0, 0, 0);
            }
            l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3f, 1f);
        }, "warden");
        alt(300, (p, l, look) -> {
            for (LivingEntity t : around(p, 32)) buff(t, MobEffects.GLOWING, 200, 0);
            sound(p, SoundEvents.WARDEN_SNIFF);
        }, "warden");

        main(10, (p, l, look) -> launch(l, p, new SmallFireball(l, p, look.x, look.y, look.z), look), "blaze");
        main(40, (p, l, look) -> launch(l, p, new LargeFireball(l, p, look.x, look.y, look.z, 2), look), "ghast");

        // ravager (el grande de cuatro patas): vapor que daña más cuanto más cerca del centro
        main(120, (p, l, look) -> {
            double r = 8;
            for (LivingEntity t : around(p, r)) {
                double d = p.distanceTo(t);
                if (d > r) continue;
                float dmg = (float) (14.0 * (1.0 - d / r));
                if (dmg > 0.5f) t.hurt(src(p), dmg);
                t.knockback(1.0, p.getX() - t.getX(), p.getZ() - t.getZ());
                buff(t, MobEffects.CONFUSION, 100, 0);
            }
            l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 120, 2.5, 0.6, 2.5, 0.08);
            AreaEffectCloud cloud = new AreaEffectCloud(l, p.getX(), p.getY(), p.getZ());
            cloud.setOwner(p);
            cloud.setRadius(4f);
            cloud.setDuration(80);
            cloud.setParticle(ParticleTypes.CLOUD);
            l.addFreshEntity(cloud);
            sound(p, SoundEvents.RAVAGER_ROAR);
        }, "ravager");
        alt(60, (p, l, look) -> {
            dash(p, look, 2.0, 0.15);
            ahead(p, look, 4, 12f, 1.8, null);
        }, "ravager");

        main(300, (p, l, look) -> {
            if (Pets.count(p) >= 6) {
                msg(p, "Ya tienes demasiadas mascotas");
                return;
            }
            for (int i = 0; i < 3; i++) {
                Vex v = EntityType.VEX.create(l);
                if (v == null) continue;
                v.moveTo(p.getX() + RNG.nextDouble() * 3 - 1.5, p.getY() + 1, p.getZ() + RNG.nextDouble() * 3 - 1.5, p.getYRot(), 0f);
                v.getPersistentData().putUUID(PossessionEvents.OWNER, p.getUUID());
                v.setPersistenceRequired();
                l.addFreshEntity(v);
            }
            puff(p, ParticleTypes.POOF, 20);
            sound(p, SoundEvents.EVOKER_PREPARE_SUMMON);
        }, "evoker");
        alt(20, (p, l, look) -> {
            if (!Pets.mountNearest(p)) msg(p, "No hay ningún vex tuyo cerca");
        }, "evoker");

        main(80, (p, l, look) -> {
            ThrownPotion tp = new ThrownPotion(l, p);
            tp.setItem(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.HARMING));
            tp.shootFromRotation(p, p.getXRot(), p.getYRot(), -10f, 0.8f, 1f);
            l.addFreshEntity(tp);
        }, "witch");
        alt(200, (p, l, look) -> {
            p.heal(10f);
            puff(p, ParticleTypes.HEART, 8);
        }, "witch");

        main(300, (p, l, look) -> {
            for (LivingEntity t : around(p, 10)) buff(t, MobEffects.BLINDNESS, 100, 0);
            buff(p, MobEffects.INVISIBILITY, 100, 0);
        }, "illusioner");

        main(50, (p, l, look) -> {
            dash(p, look, 1.8, 0.1);
            ahead(p, look, 3.5, 5f, 0.5, null);
        }, "vex", "phantom");
        alt(100, (p, l, look) -> buff(p, MobEffects.NIGHT_VISION, 600, 0), "phantom");

        main(80, (p, l, look) -> {
            aoe(p, 3.5, 6f, 1.0, t -> t.setSecondsOnFire(4));
            p.setDeltaMovement(0, 0.8, 0);
            p.hurtMarked = true;
        }, "magma_cube");
        main(60, (p, l, look) -> {
            p.setDeltaMovement(look.x * 0.5, 0.9, look.z * 0.5);
            p.hurtMarked = true;
            aoe(p, 3, 2f, 0.8, null);
        }, "slime");
        alt(400, (p, l, look) -> {
            if (Pets.count(p) >= 6) return;
            for (int i = 0; i < 2; i++) {
                net.minecraft.world.entity.monster.Slime s = EntityType.SLIME.create(l);
                if (s == null) continue;
                s.setSize(1, true);
                s.moveTo(p.getX() + i, p.getY(), p.getZ(), 0f, 0f);
                s.getPersistentData().putUUID(PossessionEvents.OWNER, p.getUUID());
                s.setPersistenceRequired();
                l.addFreshEntity(s);
            }
        }, "slime");

        main(100, (p, l, look) -> {
            LivingEntity t = target(p, 12);
            BlockPos bp = t != null ? t.blockPosition() : p.blockPosition().offset((int) Math.round(look.x * 3), 0, (int) Math.round(look.z * 3));
            if (l.getBlockState(bp).isAir()) l.setBlockAndUpdate(bp, Blocks.COBWEB.defaultBlockState());
        }, "spider");
        alt(60, (p, l, look) -> {
            p.setDeltaMovement(look.x * 1.2, 0.7, look.z * 1.2);
            p.hurtMarked = true;
        }, "spider", "cave_spider");

        main(100, (p, l, look) -> {
            aoe(p, 6, 10f, 1.5, null);
            puff(p, ParticleTypes.CLOUD, 40);
            sound(p, SoundEvents.IRON_GOLEM_ATTACK);
        }, "giant");
    }

    // ---------------------------------------------------------------- uso

    public static boolean has(String id) {
        return true; // todas las entidades tienen habilidad
    }

    /** Habilidad por defecto para lo que no está en la tabla (también entidades de otros mods). */
    private static Def fallback(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        MobCategory cat = ForgeRegistries.ENTITY_TYPES.containsKey(rl)
                ? ForgeRegistries.ENTITY_TYPES.getValue(rl).getCategory() : MobCategory.MISC;
        if (cat == MobCategory.MONSTER) {
            return new Def(100, (p, l, look) -> {
                aoe(p, 5, 4f, 1.0, t -> buff(t, MobEffects.WEAKNESS, 100, 0));
                puff(p, ParticleTypes.CLOUD, 20);
            });
        }
        return new Def(60, (p, l, look) -> {
            dash(p, look, 1.8, 0.2);
            ahead(p, look, 3.5, 4f, 0.8, null);
        });
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
            if (FIREPROOF.contains(id)) buff(p, MobEffects.FIRE_RESISTANCE, 100, 0);
            if (WATER.contains(id)) buff(p, MobEffects.WATER_BREATHING, 100, 0);
        }
    }

    /** Siempre devuelve true: todas las entidades tienen habilidad. */
    public static boolean use(ServerPlayer p, String id, boolean sneak) {
        ServerLevel lvl = p.serverLevel();
        boolean usingAlt = sneak && ALT.containsKey(id);
        Def d = usingAlt ? ALT.get(id) : MAIN.get(id);
        if (d == null) d = fallback(id);

        long now = lvl.getGameTime();
        String ck = p.getUUID() + id + usingAlt;
        Long until = CD.get(ck);
        if (until != null && now < until) return true;

        d.run().run(p, lvl, p.getLookAngle());
        if (d.cd() > 0) CD.put(ck, now + d.cd());
        return true;
    }
}
