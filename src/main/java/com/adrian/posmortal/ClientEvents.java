package com.adrian.posmortal;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dibuja al jugador con el modelo de la entidad que posee (sirve para TODAS las entidades). */
@Mod.EventBusSubscriber(modid = Posmortal.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static final ResourceLocation GHOST_TEX = new ResourceLocation(Posmortal.MODID, "textures/entity/ghost.png");
    private static BbModel ghostModel;
    private static boolean ghostFailed;

    private static BbModel ghost() {
        if (ghostModel == null && !ghostFailed) {
            try {
                ghostModel = BbModel.load("/assets/posmortal/models/entity/ghost.json");
            } catch (Exception ex) {
                ghostFailed = true;
                ex.printStackTrace();
            }
        }
        return ghostModel;
    }

    private static void renderGhost(Player p, RenderPlayerEvent.Pre e) {
        BbModel g = ghost();
        if (g == null) return;
        e.setCanceled(true);
        PoseStack ps = e.getPoseStack();
        float pt = e.getPartialTick();
        float age = p.tickCount + pt;
        double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
        boolean moving = dx * dx + dy * dy + dz * dz > 1.0E-4;
        float time = moving ? (age / 20f) % g.length : 0f;

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot)));
        ps.translate(0.0, Math.sin(age / 10f) * 0.04, 0.0);
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        g.render(ps, e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(GHOST_TEX)), e.getPackedLight(), time);
        ps.popPose();
    }

    public static final KeyMapping ABILITY = new KeyMapping("key.posmortal.ability",
            InputConstants.KEY_V, "key.categories.posmortal");

    static final Map<UUID, Entity> PROXIES = new HashMap<>();
    private static final Map<UUID, String> PROXY_TYPE = new HashMap<>();

    private static Entity proxyFor(Player p, String typeId, Minecraft mc) {
        Entity cached = PROXIES.get(p.getUUID());
        if (cached != null && typeId.equals(PROXY_TYPE.get(p.getUUID())) && cached.level() == mc.level) return cached;
        ResourceLocation rl = new ResourceLocation(typeId);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) return null;
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (t == EntityType.PLAYER) return null;
        Entity en;
        try {
            en = t.create(mc.level);
        } catch (Exception ex) {
            return null;
        }
        if (en == null) return null;
        PROXIES.put(p.getUUID(), en);
        PROXY_TYPE.put(p.getUUID(), typeId);
        return en;
    }

    private static void sync(Player p, Entity pr) {
        pr.setPos(p.getX(), p.getY(), p.getZ());
        pr.xo = p.xo; pr.yo = p.yo; pr.zo = p.zo;
        pr.xOld = p.xOld; pr.yOld = p.yOld; pr.zOld = p.zOld;
        pr.setYRot(p.getYRot()); pr.yRotO = p.yRotO;
        pr.setXRot(p.getXRot()); pr.xRotO = p.xRotO;
        boolean newTick = pr.tickCount != p.tickCount;
        pr.tickCount = p.tickCount;
        pr.setPose(p.getPose());
        pr.setOnGround(p.onGround());
        if (pr instanceof LivingEntity l) {
            l.yBodyRot = p.yBodyRot; l.yBodyRotO = p.yBodyRotO;
            l.yHeadRot = p.yHeadRot; l.yHeadRotO = p.yHeadRotO;
            if (newTick) l.walkAnimation.update(p.walkAnimation.speed(), 1f);
            l.swinging = p.swinging; l.swingTime = p.swingTime;
            l.attackAnim = p.attackAnim; l.oAttackAnim = p.oAttackAnim;
            l.hurtTime = p.hurtTime;
            l.setSprinting(p.isSprinting());
            l.setItemSlot(EquipmentSlot.MAINHAND, p.getMainHandItem());
        }
    }

    @SubscribeEvent
    public static void onRender(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        String m = ModelState.MODELS.get(p.getUUID());
        if (m == null || m.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (m.equals("ghost")) {
            renderGhost(p, e);
            return;
        }
        Entity proxy = proxyFor(p, m, mc);
        if (proxy == null) return;
        sync(p, proxy);
        e.setCanceled(true);
        float pt = e.getPartialTick();
        EntityRenderer<? super Entity> r = mc.getEntityRenderDispatcher().getRenderer(proxy);
        r.render(proxy, Mth.lerp(pt, p.yRotO, p.getYRot()), pt, e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (ABILITY.consumeClick()) {
            if (mc.player != null && mc.screen == null) Net.CH.sendToServer(new Net.AbilityMsg());
        }
    }
}
