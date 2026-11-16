package com.adrian.posmortal;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Parte cliente: modelos, tecla de habilidad, vuelo permanente del fantasma e inventario. */
@Mod.EventBusSubscriber(modid = Posmortal.MODID, value = Dist.CLIENT)
public class ClientEvents {
    /** Giro extra del modelo del fantasma. Si lo ves girado al revés, prueba -90 o 180. */
    static final float GHOST_YAW_OFFSET = 90f;

    static final ResourceLocation GHOST_TEX = new ResourceLocation(Posmortal.MODID, "textures/entity/ghost.png");
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

    static BbModel ghost() {
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

    private static Entity proxyFor(Player p, String model, Minecraft mc) {
        Entity cached = PROXIES.get(p.getUUID());
        if (cached != null && model.equals(PROXY_TYPE.get(p.getUUID())) && cached.level() == mc.level) return cached;
        ResourceLocation rl = new ResourceLocation(ModelState.baseId(model));
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
        // el tamaño del slime / cubo de magma y el ser bebé no viajan solos al cliente: se aplican aquí
        int size = ModelState.size(model);
        if (size > 0 && en instanceof net.minecraft.world.entity.monster.Slime sl) sl.setSize(size, false);
        if (ModelState.baby(model)) {
            if (en instanceof net.minecraft.world.entity.AgeableMob am) am.setBaby(true);
            else if (en instanceof net.minecraft.world.entity.monster.Zombie z) z.setBaby(true);
        }
        PROXIES.put(p.getUUID(), en);
        PROXY_TYPE.put(p.getUUID(), model);
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
        List<Saved> saved = null;
        if ((ModelState.baseId(m).equals("minecraft:cow") || ModelState.baseId(m).equals("minecraft:mooshroom")) && r instanceof LivingEntityRenderer<?, ?> lr) {
            saved = uprightCow(lr.getModel());
        }
        try {
            r.render(proxy, Mth.lerp(pt, p.yRotO, p.getYRot()), pt, e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight());
        } finally {
            if (saved != null) for (Saved sv : saved) sv.restore();
        }
    }

    // ---------------------------------------------------------------- partes del modelo (vaca a dos patas, patas como brazos)

    /** Guarda la pose de una pieza del modelo para devolverla después (el modelo lo comparten TODAS las vacas del mundo). */
    private static final class Saved {
        final ModelPart part;
        final float x, y, z, xr, yr, zr;

        Saved(ModelPart p) {
            part = p;
            x = p.x; y = p.y; z = p.z;
            xr = p.xRot; yr = p.yRot; zr = p.zRot;
        }

        void restore() {
            part.setPos(x, y, z);
            part.xRot = xr; part.yRot = yr; part.zRot = zr;
        }
    }

    private static void setPose(ModelPart p, float x, float y, float z, float xr, float yr, float zr) {
        p.setPos(x, y, z);
        p.xRot = xr; p.yRot = yr; p.zRot = zr;
    }

    private static ModelPart findPart(Object model, String... names) {
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (String n : names) {
                try {
                    Field f = ObfuscationReflectionHelper.findField(c, n);
                    Object v = f.get(model);
                    if (v instanceof ModelPart mp) return mp;
                } catch (Exception ex) {
                    // no está en esta clase
                }
            }
        }
        return null;
    }

    /** Pone el modelo de la vaca de pie sobre las patas traseras; las delanteras quedan como brazos. */
    private static List<Saved> uprightCow(Object model) {
        ModelPart head = findPart(model, "head");
        ModelPart body = findPart(model, "body");
        ModelPart rh = findPart(model, "rightHindLeg");
        ModelPart lh = findPart(model, "leftHindLeg");
        ModelPart rf = findPart(model, "rightFrontLeg");
        ModelPart lf = findPart(model, "leftFrontLeg");
        if (head == null || body == null || rh == null || lh == null || rf == null || lf == null) return null;
        List<Saved> list = new ArrayList<>();
        for (ModelPart mp : new ModelPart[]{head, body, rh, lh, rf, lf}) list.add(new Saved(mp));
        setPose(body, 0f, 4f, 0f, 0f, 0f, 0f);
        setPose(head, 0f, -10f, 1f, 0f, 0f, 0f);
        setPose(rh, -3f, 12f, -2f, 0f, 0f, 0f);
        setPose(lh, 3f, 12f, -2f, 0f, 0f, 0f);
        setPose(rf, -9f, -2f, -2f, 0f, 0f, 0f);
        setPose(lf, 9f, -2f, -2f, 0f, 0f, 0f);
        return list;
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
        if (m != null && (m.equals("ghost") || ModelState.FLYERS.contains(ModelState.baseId(m)))
                && lp.getAbilities().mayfly && !lp.getAbilities().flying) {
            lp.getAbilities().flying = true;
            lp.onUpdateAbilities();
        }
    }

    // ---------------------------------------------------------------- mano en primera persona

    /** Fantasma: sin mano. Dentro de un mob humanoide: se ve el brazo del mob. Otros mobs: sin mano. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer lp = mc.player;
        if (lp == null) return;
        String m = ModelState.MODELS.get(lp.getUUID());
        if (m == null || m.isEmpty()) return;
        e.setCanceled(true); // oculta la mano de tu skin
        if (m.equals("ghost") || e.getHand() != InteractionHand.MAIN_HAND) return;
        try {
            renderMobArm(lp, m, e, mc);
        } catch (Exception ex) {
            // si algo falla simplemente no se dibuja brazo
        }
    }

    @SuppressWarnings("unchecked")
    private static void renderMobArm(LocalPlayer lp, String m, RenderHandEvent e, Minecraft mc) {
        Entity proxy = proxyFor(lp, m, mc);
        if (proxy == null) return;
        EntityRenderer<? super Entity> r = mc.getEntityRenderDispatcher().getRenderer(proxy);
        if (!(r instanceof LivingEntityRenderer<?, ?> lr)) return;
        sync(lp, proxy);
        ResourceLocation tex = ((EntityRenderer<Entity>) r).getTextureLocation(proxy);
        Object model = lr.getModel();

        if (model instanceof HumanoidModel<?> hm0) {
            HumanoidModel<LivingEntity> hm = (HumanoidModel<LivingEntity>) hm0;
            hm.attackTime = 0f;
            hm.crouching = false;
            hm.swimAmount = 0f;
            hm.setupAnim((LivingEntity) proxy, 0f, 0f, 0f, 0f, 0f);
            hm.rightArm.xRot = 0f;
            drawArm(e, hm.rightArm, tex);
            return;
        }

        // sin brazos (vaca, creeper, gallina...): se ve la pata delantera como si fuera el brazo
        ModelPart leg = findPart(model, "rightFrontLeg", "rightArm", "rightLeg", "rightHindLeg");
        if (leg == null) return;
        Saved sv = new Saved(leg);
        try {
            setPose(leg, -5f, 2f, 0f, 0f, 0f, 0f);
            drawArm(e, leg, tex);
        } finally {
            sv.restore();
        }
    }

    private static void drawArm(RenderHandEvent e, ModelPart arm, ResourceLocation tex) {
        PoseStack ps = e.getPoseStack();
        float swing = e.getSwingProgress();
        float equip = e.getEquipProgress();
        float f1 = Mth.sqrt(swing);
        float f2 = -0.3f * Mth.sin(f1 * (float) Math.PI);
        float f3 = 0.4f * Mth.sin(f1 * ((float) Math.PI * 2f));
        float f4 = -0.4f * Mth.sin(swing * (float) Math.PI);
        float f5 = Mth.sin(swing * swing * (float) Math.PI);
        float f6 = Mth.sin(f1 * (float) Math.PI);

        ps.pushPose();
        ps.translate(f2 + 0.64000005f, f3 - 0.6f + equip * -0.6f, f4 - 0.71999997f);
        ps.mulPose(Axis.YP.rotationDegrees(45f));
        ps.mulPose(Axis.YP.rotationDegrees(f6 * 70f));
        ps.mulPose(Axis.ZP.rotationDegrees(f5 * -20f));
        ps.translate(-1f, 3.6f, 3.5f);
        ps.mulPose(Axis.ZP.rotationDegrees(120f));
        ps.mulPose(Axis.XP.rotationDegrees(200f));
        ps.mulPose(Axis.YP.rotationDegrees(-135f));
        ps.translate(5.6f, 0f, 0f);
        arm.render(ps, e.getMultiBufferSource().getBuffer(RenderType.entitySolid(tex)),
                e.getPackedLight(), OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    // ---------------------------------------------------------------- inventario y cualquier interfaz con contenedor

    /** El fantasma solo puede usar su barra de acceso rápido en CUALQUIER interfaz (inventario, mesa de crafteo, cofres...). */
    private static boolean lockableScreen(Screen s) {
        return s instanceof AbstractContainerScreen<?> && !(s instanceof CreativeModeInventoryScreen);
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre e) {
        if (possessingClient() && e.getScreen() instanceof InventoryScreen
                && exitButton().isMouseOver(e.getMouseX(), e.getMouseY())) {
            exitButton().playDownSound(Minecraft.getInstance().getSoundManager());
            Net.CH.sendToServer(new Net.ExitMsg());
            e.setCanceled(true);
            return;
        }
        if (isGhostClient() && lockableScreen(e.getScreen())) {
            Slot s = ((AbstractContainerScreen<?>) e.getScreen()).getSlotUnderMouse();
            if (s == null || locked(s) || Screen.hasShiftDown()) e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre e) {
        if (!isGhostClient() || !lockableScreen(e.getScreen())) return;
        Slot s = ((AbstractContainerScreen<?>) e.getScreen()).getSlotUnderMouse();
        if (s == null || locked(s)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre e) {
        if (!isGhostClient() || !lockableScreen(e.getScreen())) return;
        Slot s = ((AbstractContainerScreen<?>) e.getScreen()).getSlotUnderMouse();
        if (s == null || locked(s)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre e) {
        if (!isGhostClient() || !lockableScreen(e.getScreen())) return;
        Slot s = ((AbstractContainerScreen<?>) e.getScreen()).getSlotUnderMouse();
        if ((s != null && locked(s))
                || Minecraft.getInstance().options.keySwapOffhand.matches(e.getKeyCode(), e.getScanCode())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post e) {
        GuiGraphics g = e.getGuiGraphics();

        if (isGhostClient() && lockableScreen(e.getScreen())) {
            AbstractContainerScreen<?> scr = (AbstractContainerScreen<?>) e.getScreen();
            for (Slot s : scr.getMenu().slots) {
                if (locked(s)) {
                    int x = scr.getGuiLeft() + s.x, y = scr.getGuiTop() + s.y;
                    g.fill(x, y, x + 16, y + 16, 0xCC101010);
                }
            }
        }

        if (possessingClient() && e.getScreen() instanceof InventoryScreen inv) {
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
