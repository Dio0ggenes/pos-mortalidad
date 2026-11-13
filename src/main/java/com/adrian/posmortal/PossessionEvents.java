package com.adrian.posmortal;

import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.ChatFormatting;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PossessionEvents {

    // Datos del jugador
    private static final String KEY = "posmortal";
    private static final String GHOST = "ghost";
    private static final String POSSESSING = "possessing";
    private static final String TEMP = "temp";
    private static final String TIMER = "timer";
    private static final String BODY = "body";
    private static final String INV = "inv";
    private static final String TYPE = "type";
    private static final String FLY = "fly";
    private static final String VICTIM = "victim";
    private static final String VICTIM_TIMER = "victim_timer";
    // Datos de la entidad
    private static final String USES = "posmortal_uses";
    private static final String CORPSE = "posmortal_corpse";
    public static final String OWNER = "posmortal_owner";

    private static final int LIVE_TICKS = 5 * 20;      // mobs vivos: 5 segundos
    private static final int PLAYER_TICKS = 15 * 20;   // jugadores: 15 segundos

    private static final UUID HP_UUID = UUID.fromString("6c1f3a54-5d0e-4b57-9a0e-0f6a1b2c3d4e");
    private static final Set<String> FLYERS = Set.of("minecraft:bat", "minecraft:bee", "minecraft:parrot",
            "minecraft:allay", "minecraft:vex", "minecraft:blaze", "minecraft:ghast", "minecraft:phantom",
            "minecraft:ender_dragon", "minecraft:wither");
    private static final Set<String> NO_FALL = Set.of("minecraft:chicken", "minecraft:cat", "minecraft:ocelot",
            "minecraft:rabbit", "minecraft:silverfish", "minecraft:fox");

    // ---------------------------------------------------------------- utilidades

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(KEY)) root.put(KEY, new CompoundTag());
        return root.getCompound(KEY);
    }

    private static void msg(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s), true);
    }

    private static void sendXp(ServerPlayer p, float progress) {
        p.connection.send(new ClientboundSetExperiencePacket(progress, p.totalExperience, p.experienceLevel));
    }

    // ---------------------------------------------------------------- estados

    /** Fantasma: supervivencia, vuela, atraviesa bloques (el noclip se aplica en onLivingTick) y lleva el modelo fantasma. */
    private static void applyGhost(ServerPlayer p) {
        CompoundTag d = data(p);
        d.putBoolean(GHOST, true);
        d.putBoolean(POSSESSING, false);
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.getAbilities().setFlyingSpeed(0.03f);
        p.onUpdateAbilities();
        ModelState.set(p, "ghost");
    }

    private static void removeGhost(ServerPlayer p) {
        CompoundTag d = data(p);
        d.putBoolean(GHOST, false);
        if (!p.getAbilities().instabuild) {
            p.getAbilities().mayfly = false;
            p.getAbilities().flying = false;
        }
        p.getAbilities().setFlyingSpeed(0.05f);
        p.onUpdateAbilities();
        ModelState.set(p, "");
    }

    private static void possessMob(ServerPlayer p, LivingEntity le) {
        CompoundTag pd = le.getPersistentData();
        boolean corpse = pd.getBoolean(CORPSE);
        if (corpse) {
            int u = pd.getInt(USES);
            if (u <= 0) {
                msg(p, "Ese cuerpo está roto");
                return;
            }
            pd.putInt(USES, u - 1); // cada posesión gasta un uso
        }

        CompoundTag body = new CompoundTag();
        if (!le.save(body)) {
            msg(p, "No puedes poseer eso");
            return;
        }
        ServerLevel lvl = p.serverLevel();
        double x = le.getX(), y = le.getY(), z = le.getZ();
        float yaw = le.getYRot();
        float maxHp = le.getMaxHealth();
        float hp = le.getHealth();
        String id = EntityType.getKey(le.getType()).toString();
        int left = pd.getInt(USES);

        le.discard();

        CompoundTag d = data(p);
        d.put(BODY, body);
        d.putBoolean(POSSESSING, true);
        d.putBoolean(TEMP, !corpse);
        d.putInt(TIMER, LIVE_TICKS);
        d.putString(TYPE, id);

        // guarda tu inventario real y pone el del mob
        ListTag inv = new ListTag();
        p.getInventory().save(inv);
        d.put(INV, inv);
        p.getInventory().clearContent();
        switch (id) {
            case "minecraft:skeleton", "minecraft:stray" -> {
                p.getInventory().add(new ItemStack(Items.BOW));
                p.getInventory().add(new ItemStack(Items.ARROW, 32));
            }
            case "minecraft:wither_skeleton" -> p.getInventory().add(new ItemStack(Items.STONE_SWORD));
            case "minecraft:pillager" -> p.getInventory().add(new ItemStack(Items.CROSSBOW));
            default -> { }
        }

        p.teleportTo(lvl, x, y, z, yaw, 0f);

        AttributeInstance mh = p.getAttribute(Attributes.MAX_HEALTH);
        mh.removeModifier(HP_UUID);
        mh.addPermanentModifier(new AttributeModifier(HP_UUID, "posmortal_hp", maxHp - 20.0, AttributeModifier.Operation.ADDITION));
        p.setHealth(Math.max(1f, Math.min(hp, maxHp)));

        boolean fly = FLYERS.contains(id);
        d.putBoolean(FLY, fly);
        p.getAbilities().setFlyingSpeed(0.05f);
        p.getAbilities().mayfly = fly || p.getAbilities().instabuild;
        p.getAbilities().flying = false;
        p.onUpdateAbilities();

        ModelState.set(p, id); // cambia modelo y hitbox

        msg(p, corpse ? "Cuerpo poseído (usos restantes: " + left + "). /posmortal salir"
                : "Tienes 5 s en este cuerpo");
        if (!corpse) sendXp(p, 1f);
    }

    private static void possessPlayer(ServerPlayer ghost, ServerPlayer victim) {
        if (ghost == victim) return;
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PLAYER_TICKS, 255, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.JUMP, PLAYER_TICKS, 128, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, PLAYER_TICKS, 0, false, false));
        CompoundTag d = data(ghost);
        d.putUUID(VICTIM, victim.getUUID());
        d.putInt(VICTIM_TIMER, PLAYER_TICKS);
        ghost.setCamera(victim);
        msg(ghost, "Estás poseyendo a " + victim.getName().getString());
    }

    private static void endPlayerPossession(ServerPlayer ghost) {
        CompoundTag d = data(ghost);
        if (!d.hasUUID(VICTIM)) return;
        ServerPlayer v = ghost.server.getPlayerList().getPlayer(d.getUUID(VICTIM));
        if (v != null) {
            v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            v.removeEffect(MobEffects.JUMP);
            v.removeEffect(MobEffects.BLINDNESS);
        }
        ghost.setCamera(ghost);
        d.remove(VICTIM);
        d.putInt(VICTIM_TIMER, 0);
    }

    /** Sale del cuerpo y vuelve a fantasma. restoreBody=false: el cuerpo murió. */
    public static void exitPossession(ServerPlayer p, boolean restoreBody) {
        CompoundTag d = data(p);
        if (!d.getBoolean(POSSESSING)) return;
        ServerLevel lvl = p.serverLevel();

        if (restoreBody) {
            Optional<Entity> opt = EntityType.create(d.getCompound(BODY), lvl);
            if (opt.isPresent()) {
                Entity e = opt.get();
                e.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
                CompoundTag ed = e.getPersistentData();
                boolean broken = ed.getBoolean(CORPSE) && ed.getInt(USES) <= 0;
                if (broken) {
                    lvl.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.5, p.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
                    msg(p, "Tu cuerpo se ha roto");
                } else {
                    lvl.addFreshEntity(e);
                }
            }
        }

        d.putBoolean(POSSESSING, false);
        d.putBoolean(TEMP, false);
        d.remove(BODY);

        p.getAttribute(Attributes.MAX_HEALTH).removeModifier(HP_UUID);
        p.getInventory().clearContent();
        p.getInventory().load(d.getList(INV, 10));
        d.remove(INV);
        p.removeAllEffects();
        p.clearFire();
        applyGhost(p);
        p.setHealth(p.getMaxHealth());
        sendXp(p, p.experienceProgress);
    }

    public static void useAbility(ServerPlayer p) {
        CompoundTag d = data(p);
        if (d.getBoolean(POSSESSING)) Abilities.use(p, d.getString(TYPE), p.isShiftKeyDown());
    }

    // ---------------------------------------------------------------- eventos (ambos lados)

    /** Noclip del fantasma. Se hace aquí porque Player.tick() reinicia noPhysics justo antes. */
    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent e) {
        if (e.getEntity() instanceof Player p && ModelState.isGhost(p.getUUID())) {
            p.noPhysics = true;
            p.fallDistance = 0f;
        }
    }

    /** Hitbox según el modelo. */
    @SubscribeEvent
    public void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String m = ModelState.MODELS.get(p.getUUID());
        if (m == null || m.isEmpty()) return;
        if (m.equals("ghost")) {
            e.setNewSize(EntityDimensions.scalable(0.5f, 1.3f), true);
            return;
        }
        ResourceLocation rl = new ResourceLocation(m);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return;
        e.setNewSize(ForgeRegistries.ENTITY_TYPES.getValue(rl).getDimensions(), true);
    }

    // ---------------------------------------------------------------- eventos (servidor)

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.contains(KEY)) e.getEntity().getPersistentData().put(KEY, old.getCompound(KEY).copy());
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag d = data(p);
        d.putBoolean(POSSESSING, false);
        if (d.getBoolean(GHOST)) applyGhost(p);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag d = data(p);
        if (d.getBoolean(POSSESSING)) ModelState.set(p, d.getString(TYPE));
        else if (d.getBoolean(GHOST)) applyGhost(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ModelState.MODELS.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer viewer) {
            String m = ModelState.MODELS.get(t.getUUID());
            if (m != null) {
                Net.CH.send(PacketDistributor.PLAYER.with(() -> viewer), new Net.ModelMsg(t.getUUID(), m));
            }
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Entity target = e.getTarget();
        ItemStack held = p.getMainHandItem();
        CompoundTag d = data(p);

        // 1) Tótem sobre un mob: lo marca (usos = los del tótem)
        if (held.is(Posmortal.TOTEM.get())) {
            if (!(target instanceof Mob mob)) return;
            int n = TotemItem.uses(held);
            if (mob.getType().is(Tags.EntityTypes.BOSSES)) n = 1;
            mob.getPersistentData().putInt(USES, n);
            ((ServerLevel) mob.level()).sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 30, 0.4, 0.4, 0.4, 0.2);
            if (!p.getAbilities().instabuild) held.shrink(1);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // 2) Fantasma con la mano vacía sobre una entidad: poseer
        if (d.getBoolean(GHOST) && !d.getBoolean(POSSESSING) && held.isEmpty() && target instanceof LivingEntity le) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (le instanceof ServerPlayer tp) possessPlayer(p, tp);
            else possessMob(p, le);
        }
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !e.getEntity().level().isClientSide) {
            CompoundTag d = data(p);
            if (d.getBoolean(GHOST) && !d.getBoolean(POSSESSING)
                    && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                e.setCanceled(true); // el fantasma no recibe daño
            }
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide) return;

        if (le instanceof ServerPlayer sp) {
            CompoundTag sd = data(sp);
            // muere dentro de un cuerpo: el cuerpo muere, vuelve a fantasma
            if (sd.getBoolean(POSSESSING)) {
                e.setCanceled(true);
                exitPossession(sp, false);
                return;
            }
            // muere con el tótem en una mano: se salva y se convierte en fantasma
            if (!sd.getBoolean(GHOST) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                ItemStack main = sp.getMainHandItem();
                ItemStack off = sp.getOffhandItem();
                ItemStack totem = main.is(Posmortal.TOTEM.get()) ? main : off.is(Posmortal.TOTEM.get()) ? off : null;
                if (totem != null) {
                    totem.shrink(1);
                    e.setCanceled(true);
                    sp.setHealth(sp.getMaxHealth());
                    sp.removeAllEffects();
                    sp.clearFire();
                    applyGhost(sp);
                    msg(sp, "El tótem te ha convertido en fantasma");
                }
            }
            return;
        }

        // mob marcado: no muere, queda como cuerpo rojo
        CompoundTag pd = le.getPersistentData();
        if (pd.contains(USES) && !pd.getBoolean(CORPSE) && le instanceof Mob mob) {
            e.setCanceled(true);
            pd.putBoolean(CORPSE, true);
            mob.setHealth(mob.getMaxHealth());
            mob.setInvulnerable(true);
            mob.setNoAi(true);
            Scoreboard sb = mob.level().getScoreboard();
            PlayerTeam t = sb.getPlayerTeam("posmortal_corpse");
            if (t == null) {
                t = sb.addPlayerTeam("posmortal_corpse");
                t.setColor(ChatFormatting.RED);
            }
            sb.addPlayerToTeam(mob.getStringUUID(), t);
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        }
    }

    /** Un Wither invocado por un jugador no ataca a quien lo invocó. */
    @SubscribeEvent
    public void onTarget(LivingChangeTargetEvent e) {
        LivingEntity t = e.getNewTarget();
        CompoundTag pd = e.getEntity().getPersistentData();
        if (t != null && pd.hasUUID(OWNER) && pd.getUUID(OWNER).equals(t.getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag d = data(p);
        if (d.getBoolean(GHOST) && !d.getBoolean(POSSESSING)) {
            e.setCanceled(true);
        } else if (d.getBoolean(POSSESSING) && (d.getBoolean(FLY) || NO_FALL.contains(d.getString(TYPE)))) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        CompoundTag d = data(p);

        if (d.getBoolean(POSSESSING)) {
            Abilities.passive(p, d.getString(TYPE));
            if (d.getBoolean(TEMP)) {
                int t = d.getInt(TIMER) - 1;
                d.putInt(TIMER, t);
                sendXp(p, Math.max(t, 0) / (float) LIVE_TICKS);
                if (t <= 0) {
                    exitPossession(p, true);
                    msg(p, "Te han expulsado del cuerpo");
                }
            }
        } else if (d.getBoolean(GHOST) && p.tickCount % 40 == 0 && !p.getAbilities().mayfly) {
            applyGhost(p);
        }

        if (d.hasUUID(VICTIM)) {
            int t = d.getInt(VICTIM_TIMER) - 1;
            d.putInt(VICTIM_TIMER, t);
            if (t <= 0) endPlayerPossession(p);
        }
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("posmortal")
                .then(Commands.literal("salir").executes(c -> {
                    exitPossession(c.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("fantasma").requires(s -> s.hasPermission(2)).executes(c -> {
                    applyGhost(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("humano").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    exitPossession(p, true);
                    removeGhost(p);
                    return 1;
                })));
    }
}
