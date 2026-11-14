package com.adrian.posmortal;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Parte cliente: modelos, tecla de habilidad, vuelo permanente del fantasma e inventario. */
@Mod.EventBusSubscriber(modid = Posmortal.MODID, value = Dist.CLIENT)
public class ClientEvents {
    /** Giro extra del modelo del fantasma. Si lo ves girado al revés, prueba -90 o 180. */
    private static final float GHOST_YAW_OFFSET = 90f;

    private static final ResourceLocation GHOST_TEX = new ResourceLocation(Posmortal.MODID, "textures/entity/ghost.png");
    private static BbModel ghostModel;
    private static boolean ghostFailed;

    public static final KeyMapping ABILITY = new KeyMapping("key.posmortal.ability",
            InputConstants.KEY_V, "key.categories.posmortal");

    static final Map<UUID, Entity> PROXIES = new HashMap<>();
    private static final Map<UUID, String> PROXY_TYPE = new HashMap<>();
    private static Button exitButton;

    // ---------------------------------------------------------------- estado local

    private static boolean isGhostClient() {
        LocalPlayer lp = Minecraft.getInstance().player;
        return lp != null && ModelState.isGhost(lp.getUUID());
    }

    private static boolean possessingClient() {
        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp == null) return false;
        String m = ModelState.MODELS.get(lp.getUUID());
        return m != null && !m.isEmpty() && !m.equals("ghost");
    }

    /** Fantasma: solo se puede usar la barra de acceso rápido (slots 0-8 del inventario). */
    private static boolean locked(Slot s) {
        if (s.container instanceof Inventory) return s.getContainerSlot() >= 9;
        return true; // cuadrícula de crafteo y resultado
    }

    private static Button exitButton() {
        if (exitButton == null) {
            exitButton = Button.builder(Component.literal("X"), b -> { }).bounds(0, 0, 20, 20).build();
        }
        return exitButton;
    }

    // ---------------------------------------------------------------- modelos

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
        ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) + GHOST_YAW_OFFSET));
        ps.translate(0.0, Math.sin(age / 10f) * 0.04, 0.0);
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        g.render(ps, e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(GHOST_TEX)), e.getPackedLight(), time);
        ps.popPose();
    }

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
        boolean newTick = pr.tickCount != p.tickCount;
        pr.setPos(p.getX(), p.getY(), p.getZ());
        pr.xo = p.xo; pr.yo = p.yo; pr.zo = p.zo;
        pr.xOld = p.xOld; pr.yOld = p.yOld; pr.zOld = p.zOld;
        pr.setYRot(p.getYRot()); pr.yRotO = p.yRotO;
        pr.setXRot(p.getXRot()); pr.xRotO = p.xRotO;
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

    // ---------------------------------------------------------------- tick: habilidad y vuelo permanente

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer lp = mc.player;
        if (lp == null) return;

        while (ABILITY.consumeClick()) {
            if (mc.screen == null) Net.CH.sendToServer(new Net.AbilityMsg());
        }

        // el fantasma y los cuerpos voladores vuelan siempre, sin doble toque de espacio
        String m = ModelState.MODELS.get(lp.getUUID());
        if (m != null && (m.equals("ghost") || ModelState.FLYERS.contains(m))
                && lp.getAbilities().mayfly && !lp.getAbilities().flying) {
            lp.getAbilities().flying = true;
            lp.onUpdateAbilities();
        }
    }

    // ---------------------------------------------------------------- inventario

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!(e.getScreen() instanceof InventoryScreen inv)) return;
        if (possessingClient() && exitButton().isMouseOver(e.getMouseX(), e.getMouseY())) {
            exitButton().playDownSound(Minecraft.getInstance().getSoundManager());
            Net.CH.sendToServer(new Net.ExitMsg());
            e.setCanceled(true);
            return;
        }
        if (isGhostClient()) {
            Slot s = inv.getSlotUnderMouse();
            if (s != null && (locked(s) || Screen.hasShiftDown())) e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre e) {
        if (!(e.getScreen() instanceof InventoryScreen inv) || !isGhostClient()) return;
        Slot s = inv.getSlotUnderMouse();
        if (s != null && locked(s)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre e) {
        if (!(e.getScreen() instanceof InventoryScreen inv) || !isGhostClient()) return;
        Slot s = inv.getSlotUnderMouse();
        if (s != null && locked(s)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre e) {
        if (!(e.getScreen() instanceof InventoryScreen inv) || !isGhostClient()) return;
        Slot s = inv.getSlotUnderMouse();
        if ((s != null && locked(s))
                || Minecraft.getInstance().options.keySwapOffhand.matches(e.getKeyCode(), e.getScanCode())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof InventoryScreen inv)) return;
        GuiGraphics g = e.getGuiGraphics();

        if (isGhostClient()) {
            for (Slot s : inv.getMenu().slots) {
                if (locked(s)) {
                    int x = inv.getGuiLeft() + s.x, y = inv.getGuiTop() + s.y;
                    g.fill(x, y, x + 16, y + 16, 0xCC101010);
                }
            }
        }

        if (possessingClient()) {
            Button b = exitButton();
            b.setX(inv.getGuiLeft() + 176 + 4);
            b.setY(inv.getGuiTop() + 4);
            b.render(g, e.getMouseX(), e.getMouseY(), e.getPartialTick());
            if (b.isMouseOver(e.getMouseX(), e.getMouseY())) {
                g.renderTooltip(Minecraft.getInstance().font, Component.literal("Salir del cuerpo"), e.getMouseX(), e.getMouseY());
            }
        }
    }
}
