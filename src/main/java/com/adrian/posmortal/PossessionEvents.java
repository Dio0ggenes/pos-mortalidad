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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
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

    private static final UUID ARENA_ATK_UUID = UUID.fromString("3f9a1c22-7b0e-4d11-9c55-2e6a8d4b7f01");
    private static final String MODEL = "model";
    private static final UUID HP_UUID = UUID.fromString("6c1f3a54-5d0e-4b57-9a0e-0f6a1b2c3d4e");
    static final UUID GHOST_HP_UUID = UUID.fromString("0b7d5e61-2a34-4c8f-8e1d-5a9c7f3b2d10");
    private static final Set<String> NO_FALL = Set.of("minecraft:chicken", "minecraft:cat", "minecraft:ocelot",
            "minecraft:rabbit", "minecraft:silverfish", "minecraft:fox");

    // ---------------------------------------------------------------- utilidades

    static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(KEY)) root.put(KEY, new CompoundTag());
        return root.getCompound(KEY);
    }

    /** NBT del cuerpo que posee el jugador (para habilidades que dependen de él, p. ej. el color de la oveja). */
    public static CompoundTag bodyTag(ServerPlayer p) {
        return data(p).getCompound(BODY);
    }

    static void msg(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s), true);
    }

    private static void sendXp(ServerPlayer p, float progress) {
        p.connection.send(new ClientboundSetExperiencePacket(progress, p.totalExperience, p.experienceLevel));
    }

    static void setHpMod(ServerPlayer p, UUID id, Double amount) {
        AttributeInstance mh = p.getAttribute(Attributes.MAX_HEALTH);
        mh.removeModifier(id);
        if (amount != null) {
            mh.addPermanentModifier(new AttributeModifier(id, "posmortal_hp", amount, AttributeModifier.Operation.ADDITION));
        }
    }

    private static boolean hostileType(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return false;
        return ForgeRegistries.ENTITY_TYPES.getValue(rl).getCategory() == MobCategory.MONSTER;
    }

    /** Deja a una entidad como cuerpo rojo: brillo rojo, sin IA, invulnerable. */
    private static void markCorpse(Entity e) {
        e.getPersistentData().putBoolean(CORPSE, true);
        e.setInvulnerable(true);
        if (e instanceof Mob m) {
            m.setNoAi(true);
            m.setHealth(m.getMaxHealth());
        }
        if (e instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        }
        Scoreboard sb = e.level().getScoreboard();
        PlayerTeam t = sb.getPlayerTeam("posmortal_corpse");
        if (t == null) {
            t = sb.addPlayerTeam("posmortal_corpse");
            t.setColor(ChatFormatting.RED);
        }
        sb.addPlayerToTeam(e.getStringUUID(), t);
    }

    private static void leaveTeam(Entity e) {
        Scoreboard sb = e.level().getScoreboard();
        PlayerTeam old = sb.getPlayersTeam(e.getStringUUID());
        if (old != null) sb.removePlayerFromTeam(e.getStringUUID(), old);
    }

    // ---------------------------------------------------------------- estados

    /** Fantasma: supervivencia, vuela siempre, mitad de vida, modelo fantasma. NO atraviesa bloques. */
    static void applyGhost(ServerPlayer p) {
        CompoundTag d = data(p);
        d.putBoolean(GHOST, true);
        d.putBoolean(POSSESSING, false);
        setHpMod(p, HP_UUID, null);
        setHpMod(p, GHOST_HP_UUID, -10.0);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.getAbilities().setFlyingSpeed(0.03f);
        p.onUpdateAbilities();
        ModelState.set(p, "ghost");
    }

    static void removeGhost(ServerPlayer p) {
        CompoundTag d = data(p);
        d.putBoolean(GHOST, false);
        setHpMod(p, GHOST_HP_UUID, null);
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
        ServerLevel lvl = p.serverLevel();

        // cuerpo sin usos: se rompe al intentar entrar
        if (corpse && pd.getInt(USES) <= 0) {
            lvl.sendParticles(ParticleTypes.POOF, le.getX(), le.getY() + 0.5, le.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
            leaveTeam(le);
            le.discard();
            msg(p, "El cuerpo se ha roto");
            return;
        }

        CompoundTag body = new CompoundTag();
        if (!le.save(body)) {
            msg(p, "No puedes poseer eso");
            return;
        }
        double x = le.getX(), y = le.getY(), z = le.getZ();
        float yaw = le.getYRot();
        float maxHp = le.getMaxHealth();
        float hp = le.getHealth();
        String id = EntityType.getKey(le.getType()).toString();
        int left = pd.getInt(USES);
        // variantes que el cliente no conoce: tamaño del slime / cubo de magma y si es bebé
        int size = (id.equals("minecraft:slime") || id.equals("minecraft:magma_cube")) ? body.getInt("Size") + 1 : 0;
        boolean baby = body.getInt("Age") < 0 || body.getBoolean("IsBaby");
        String model = (size > 0 || baby) ? id + "#" + size + "#" + (baby ? 1 : 0) : id;

        leaveTeam(le);
        le.discard();

        CompoundTag d = data(p);
        d.put(BODY, body);
        d.putBoolean(POSSESSING, true);
        d.putBoolean(TEMP, !corpse);
        d.putInt(TIMER, LIVE_TICKS);
        d.putString(TYPE, id);

        p.teleportTo(lvl, x, y, z, yaw, 0f);

        setHpMod(p, GHOST_HP_UUID, null);
        setHpMod(p, HP_UUID, maxHp - 20.0);
        p.setHealth(Math.max(1f, Math.min(hp, maxHp)));

        boolean fly = ModelState.FLYERS.contains(id);
        d.putBoolean(FLY, fly);
        p.getAbilities().setFlyingSpeed(0.05f);
        p.getAbilities().mayfly = fly || p.getAbilities().instabuild;
        p.getAbilities().flying = fly;
        p.onUpdateAbilities();

        d.putString(MODEL, model);
        ModelState.set(p, model); // cambia modelo y hitbox

        String hint = " | V: habilidad (Shift+V: otra)";
        msg(p, (corpse ? "Cuerpo poseído (usos restantes: " + left + ")" : "Tienes 5 s en este cuerpo") + hint);
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
        String note = null;

        if (restoreBody) {
            Optional<Entity> opt = EntityType.create(d.getCompound(BODY), lvl);
            if (opt.isPresent()) {
                Entity e = opt.get();
                e.setUUID(UUID.randomUUID()); // evita choques con el UUID del cuerpo original
                e.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
                CompoundTag ed = e.getPersistentData();
                if (ed.getBoolean(CORPSE)) {
                    int left = Math.max(0, ed.getInt(USES) - 1); // cada salida gasta un uso
                    ed.putInt(USES, left);
                    markCorpse(e);
                    note = "Cuerpo guardado (usos restantes: " + left + ")";
                }
                lvl.addFreshEntity(e);
            }
        }

        d.putBoolean(POSSESSING, false);
        d.putBoolean(TEMP, false);
        d.remove(BODY);

        setHpMod(p, HP_UUID, null);
        p.removeAllEffects();
        p.clearFire();
        applyGhost(p);
        p.setHealth(p.getMaxHealth());
        sendXp(p, p.experienceProgress);
        if (note != null) msg(p, note);
    }

    public static boolean inArena(Entity e) {
        return e.level().dimension().equals(BossFight.ARENA);
    }

    /** Fantasma "puro" (no dentro de un cuerpo). */
    public static boolean isGhost(Player p) {
        CompoundTag d = data(p);
        return d.getBoolean(GHOST) && !d.getBoolean(POSSESSING);
    }

    /** Token del Fornite: alterna entre humano y fantasma. Devuelve true si cambió algo. */
    public static boolean toggleGhost(ServerPlayer p) {
        CompoundTag d = data(p);
        if (d.getBoolean(POSSESSING)) {
            msg(p, "Sal del cuerpo primero");
            return false;
        }
        if (inArena(p)) return false;
        if (d.getBoolean(GHOST)) removeGhost(p);
        else applyGhost(p);
        return true;
    }

    /** Dentro de la arena el fantasma tiene vida completa y pega más fuerte. */
    static void arenaPowers(ServerPlayer p, boolean on) {
        AttributeInstance atk = p.getAttribute(Attributes.ATTACK_DAMAGE);
        atk.removeModifier(ARENA_ATK_UUID);
        if (on) {
            atk.addPermanentModifier(new AttributeModifier(ARENA_ATK_UUID, "posmortal_arena", 8.0, AttributeModifier.Operation.ADDITION));
            setHpMod(p, GHOST_HP_UUID, null);
            p.setHealth(p.getMaxHealth());
        }
    }

    public static void useAbility(ServerPlayer p) {
        CompoundTag d = data(p);
        if (!d.getBoolean(POSSESSING)) {
            if (d.getBoolean(GHOST) && inArena(p)) Abilities.ghostUse(p, p.isShiftKeyDown());
            return;
        }
        if (!Abilities.use(p, d.getString(TYPE), p.isShiftKeyDown())) {
            msg(p, "Este cuerpo no tiene habilidad activa");
        }
    }

    // ---------------------------------------------------------------- eventos (ambos lados)

    /** Hitbox y altura de ojos (punto de vista) según el modelo. */
    @SubscribeEvent
    public void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String m = ModelState.MODELS.get(p.getUUID());
        if (m == null || m.isEmpty()) return;
        if (m.equals("ghost")) {
            e.setNewSize(EntityDimensions.scalable(0.5f, 1.3f), false);
            e.setNewEyeHeight(1.1f);
            return;
        }
        String base = ModelState.baseId(m);
        ResourceLocation rl = new ResourceLocation(base);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return;
        EntityDimensions dims = ForgeRegistries.ENTITY_TYPES.getValue(rl).getDimensions();
        int sz = ModelState.size(m);
        if (sz > 0) dims = dims.scale(0.255f * sz);   // slime / cubo de magma: hitbox según su tamaño real
        if (ModelState.baby(m)) dims = dims.scale(0.5f);
        e.setNewSize(dims, false);
        e.setNewEyeHeight(dims.height * 0.85f);
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
        // morir sin tótem: reapareces en el limbo y tienes que pelear con el fantasma
        if (!e.isEndConquered()) {
            if (d.getBoolean("limbo_dead")) d.putBoolean("limbo_dead", false); // moriste EN el limbo: vuelves al mundo normal
            else Limbo.enter(p);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag d = data(p);
        if (inArena(p) && !d.getBoolean(POSSESSING) && !BossFight.hasBoss(p)) {
            BossFight.fail(p); // te desconectaste y el jefe ya no está
            return;
        }
        if (d.getBoolean(POSSESSING)) {
            String mm = d.getString(MODEL);
            ModelState.set(p, mm.isEmpty() ? d.getString(TYPE) : mm);
        }
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

        // 0) Estrella del portal a la boss fight
        if (target instanceof net.minecraft.world.entity.Interaction it && it.getPersistentData().getBoolean(BossPortal.STAR)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            BossPortal.activate(p, it);
            return;
        }

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

        // 2) Fantasma (mano vacía o agachado) sobre una entidad: poseer
        if (d.getBoolean(GHOST) && !d.getBoolean(POSSESSING) && !inArena(p) && (held.isEmpty() || p.isShiftKeyDown())
                && target instanceof LivingEntity le) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (le instanceof ServerPlayer tp) possessPlayer(p, tp);
            else possessMob(p, le);
        }
    }

    /** El fantasma no puede pegar a nadie. */
    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            CompoundTag d = data(p);
            if (d.getBoolean(GHOST) && !d.getBoolean(POSSESSING) && !inArena(p)) e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide) return;

        if (le instanceof ServerPlayer sp) {
            CompoundTag sd = data(sp);
            // muere en el limbo: reaparece en el mundo normal (sin volver al limbo)
            if (Limbo.inLimbo(sp)) {
                sd.putBoolean("limbo_dead", true);
                Limbo.cleanupIfEmpty(sp.server);
                return;
            }
            // muere en la arena: pierde el combate y vuelve a casa como fantasma
            if (sd.getBoolean(GHOST) && !sd.getBoolean(POSSESSING) && inArena(sp)) {
                e.setCanceled(true);
                BossFight.fail(sp);
                return;
            }
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
                    sp.removeAllEffects();
                    sp.clearFire();
                    applyGhost(sp);
                    sp.setHealth(sp.getMaxHealth());
                    msg(sp, "El tótem te ha convertido en fantasma");
                }
            }
            return;
        }

        // mob marcado: no muere, queda como cuerpo rojo
        CompoundTag pd = le.getPersistentData();
        if (pd.contains(USES) && !pd.getBoolean(CORPSE) && le instanceof Mob mob) {
            e.setCanceled(true);
            markCorpse(mob);
        }
    }

    @SubscribeEvent
    public void onTarget(LivingChangeTargetEvent e) {
        LivingEntity t = e.getNewTarget();
        if (t == null) return;
        CompoundTag pd = e.getEntity().getPersistentData();
        // un Wither invocado no ataca a quien lo invocó
        if (pd.hasUUID(OWNER) && pd.getUUID(OWNER).equals(t.getUUID())) {
            e.setCanceled(true);
            return;
        }
        // si posees un mob hostil, los otros hostiles te toman por uno de los suyos
        if (t instanceof ServerPlayer sp && e.getEntity() instanceof Enemy) {
            CompoundTag d = data(sp);
            if (d.getBoolean(POSSESSING) && hostileType(d.getString(TYPE))) e.setCanceled(true);
        }
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
        Pets.tick(p);
        if (p.tickCount % 20 == 0) {
            BossPortal.scan(p);
            Limbo.tick(p);
            if (inArena(p)) BossFight.keepInside(p);
        }

        if (d.getBoolean(POSSESSING)) {
            String type = d.getString(TYPE);
            Abilities.passive(p, type);
            // los golems de hierro sí atacan a quien posee un hostil
            if (p.tickCount % 20 == 0 && hostileType(type)) {
                for (IronGolem g : p.serverLevel().getEntitiesOfClass(IronGolem.class, p.getBoundingBox().inflate(16))) {
                    if (g.getTarget() == null) g.setTarget(p);
                }
            }
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
                .then(Commands.literal("limbo").requires(s -> s.hasPermission(2)).executes(c -> {
                    Limbo.leave(c.getSource().getPlayerOrException());
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
