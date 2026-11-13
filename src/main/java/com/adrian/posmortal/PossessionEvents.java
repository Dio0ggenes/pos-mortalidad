package com.adrian.posmortal;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PossessionEvents {

    // Claves de datos
    private static final String KEY = "posmortal";
    private static final String BLESSED = "blessed";       // recibió el tótem: al morir será fantasma
    private static final String GHOST = "ghost";           // es un fantasma (aunque esté dentro de un cuerpo)
    private static final String POSSESSING = "possessing"; // ahora mismo está dentro de un cuerpo
    private static final String TEMP = "temp";             // posesión temporal (con cuenta atrás)
    private static final String KILLED = "killed";         // ya mató algo: se acaba la cuenta atrás
    private static final String TIMER = "timer";
    private static final String BODY = "body";
    private static final String TYPE = "type";
    private static final String FLY = "fly";
    private static final String VICTIM = "victim";
    private static final String VICTIM_TIMER = "victim_timer";
    // Datos en la entidad (cuerpo)
    private static final String USES = "posmortal_uses";
    private static final String CORPSE = "posmortal_corpse";

    // ===== AJUSTES =====
    private static final int POSSESS_TICKS = 15 * 20;
    private static final int BODY_USES = 3;
    private static final int BOSS_USES = 1;

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

    private static String typeId(Entity e) {
        return EntityType.getKey(e.getType()).toString();
    }

    private static void msg(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s), true);
    }

    private static void sendXp(ServerPlayer p, float progress) {
        p.connection.send(new ClientboundSetExperiencePacket(progress, p.totalExperience, p.experienceLevel));
    }

    private static void ghostEffects(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MobEffectInstance.INFINITE_DURATION, 1, false, false));
    }

    // ---------------------------------------------------------------- estados

    private static void makeGhost(ServerPlayer p) {
        data(p).putBoolean(GHOST, true);
        p.setGameMode(GameType.SPECTATOR);
        ghostEffects(p);
    }

    private static void possessMob(ServerPlayer p, LivingEntity le) {
        CompoundTag pd = le.getPersistentData();
        boolean corpse = pd.getBoolean(CORPSE);

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
        String id = typeId(le);
        boolean boss = le.getType().is(Tags.EntityTypes.BOSSES);

        le.discard(); // el cuerpo queda guardado en el NBT del jugador

        CompoundTag d = data(p);
        d.put(BODY, body);
        d.putBoolean(POSSESSING, true);
        d.putBoolean(TEMP, !corpse);
        d.putBoolean(KILLED, false);
        d.putInt(TIMER, POSSESS_TICKS);
        d.putString(TYPE, id);

        p.setGameMode(GameType.SURVIVAL);
        p.teleportTo(lvl, x, y, z, yaw, 0f);
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);

        // vida del mob
        AttributeInstance mh = p.getAttribute(Attributes.MAX_HEALTH);
        mh.removeModifier(HP_UUID);
        mh.addPermanentModifier(new AttributeModifier(HP_UUID, "posmortal_hp", maxHp - 20.0, AttributeModifier.Operation.ADDITION));
        p.setHealth(Math.max(1f, Math.min(hp, maxHp)));

        // inventario base
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

        // vuelo
        boolean fly = FLYERS.contains(id);
        d.putBoolean(FLY, fly);
        p.getAbilities().mayfly = fly;
        if (!fly) p.getAbilities().flying = false;
        p.onUpdateAbilities();

        msg(p, corpse ? "Has poseído el cuerpo. /posmortal salir para salir"
                : "Tienes 15 s: mata a una entidad o serás expulsado");
        if (!corpse) sendXp(p, 1f);
    }

    private static void possessPlayer(ServerPlayer ghost, ServerPlayer victim) {
        if (ghost == victim) return;
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, POSSESS_TICKS, 255, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.JUMP, POSSESS_TICKS, 128, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, POSSESS_TICKS, 0, false, false));
        CompoundTag d = data(ghost);
        d.putUUID(VICTIM, victim.getUUID());
        d.putInt(VICTIM_TIMER, POSSESS_TICKS);
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

    /** Sale del cuerpo. restoreBody=false significa que el cuerpo murió. */
    private static void exitPossession(ServerPlayer p, boolean restoreBody) {
        CompoundTag d = data(p);
        if (!d.getBoolean(POSSESSING)) return;
        ServerLevel lvl = p.serverLevel();

        if (restoreBody) {
            Optional<Entity> opt = EntityType.create(d.getCompound(BODY), lvl);
            if (opt.isPresent()) {
                Entity e = opt.get();
                e.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
                boolean alive = true;
                CompoundTag ed = e.getPersistentData();
                if (ed.getBoolean(CORPSE)) {
                    int uses = ed.getInt(USES);
                    if (uses <= 0) alive = false;
                    else ed.putInt(USES, uses - 1);
                }
                if (alive) {
                    lvl.addFreshEntity(e);
                } else {
                    lvl.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.5, p.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
                    msg(p, "Tu cuerpo se ha roto");
                }
            }
        }

        d.putBoolean(POSSESSING, false);
        d.putBoolean(TEMP, false);
        d.remove(BODY);

        AttributeInstance mh = p.getAttribute(Attributes.MAX_HEALTH);
        mh.removeModifier(HP_UUID);
        p.getInventory().clearContent();
        p.setGameMode(GameType.SPECTATOR); // resetea el vuelo
        p.setHealth(p.getMaxHealth());
        ghostEffects(p);
        sendXp(p, p.experienceProgress);
    }

    // ---------------------------------------------------------------- eventos

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.contains(KEY)) e.getEntity().getPersistentData().put(KEY, old.getCompound(KEY).copy());
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag d = data(p);
        if (d.getBoolean(BLESSED)) {
            d.putBoolean(BLESSED, false);
            d.putBoolean(GHOST, true);
        }
        d.putBoolean(POSSESSING, false);
        if (d.getBoolean(GHOST)) makeGhost(p);
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Entity target = e.getTarget();
        ItemStack held = p.getMainHandItem();
        CompoundTag d = data(p);

        // 1) Tótem sobre una entidad
        if (held.is(Posmortal.TOTEM.get())) {
            if (target instanceof ServerPlayer tp) {
                data(tp).putBoolean(BLESSED, true);
                msg(p, "Si " + tp.getName().getString() + " muere, volverá como fantasma");
            } else if (target instanceof Mob mob) {
                boolean boss = mob.getType().is(Tags.EntityTypes.BOSSES);
                mob.getPersistentData().putInt(USES, boss ? BOSS_USES : BODY_USES);
                ((ServerLevel) mob.level()).sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 30, 0.4, 0.4, 0.4, 0.2);
            } else {
                return;
            }
            if (!p.getAbilities().instabuild) held.shrink(1);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // 2) Fantasma poseyendo
        if (p.isSpectator() && d.getBoolean(GHOST) && target instanceof LivingEntity le) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (le instanceof ServerPlayer tp) {
                possessPlayer(p, tp);
                return;
            }
            CompoundTag ld = le.getPersistentData();
            if (ld.getBoolean(CORPSE) && ld.getInt(USES) < 0) {
                msg(p, "Ese cuerpo está roto");
                return;
            }
            possessMob(p, le);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide) return;

        // matar algo con una posesión temporal detiene la cuenta atrás
        if (e.getSource().getEntity() instanceof ServerPlayer k) {
            CompoundTag kd = data(k);
            if (kd.getBoolean(POSSESSING) && kd.getBoolean(TEMP) && !kd.getBoolean(KILLED)) {
                kd.putBoolean(KILLED, true);
                sendXp(k, k.experienceProgress);
                msg(k, "Cuerpo asegurado");
            }
        }

        // el jugador muere dentro de un cuerpo: vuelve a ser fantasma
        if (le instanceof ServerPlayer sp) {
            if (data(sp).getBoolean(POSSESSING)) {
                e.setCanceled(true);
                exitPossession(sp, false);
            }
            return;
        }

        // mob marcado con tótem: no muere, se queda como cuerpo rojo
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

    @SubscribeEvent
    public void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag d = data(p);
        if (d.getBoolean(POSSESSING) && (d.getBoolean(FLY) || NO_FALL.contains(d.getString(TYPE)))) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        CompoundTag d = data(p);

        if (d.getBoolean(POSSESSING) && d.getBoolean(TEMP) && !d.getBoolean(KILLED)) {
            int t = d.getInt(TIMER) - 1;
            d.putInt(TIMER, t);
            sendXp(p, Math.max(t, 0) / (float) POSSESS_TICKS);
            if (t <= 0) {
                exitPossession(p, true);
                msg(p, "Te han expulsado del cuerpo");
            }
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
                    makeGhost(c.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
