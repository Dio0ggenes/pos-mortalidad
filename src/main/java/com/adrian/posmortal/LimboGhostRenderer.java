package com.adrian.posmortal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** El fantasma enemigo usa el mismo modelo 3D que el fantasma del jugador. */
public class LimboGhostRenderer extends EntityRenderer<LimboGhost> {
    public LimboGhostRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(LimboGhost e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        BbModel g = ClientEvents.ghost();
        if (g == null) return;
        float age = e.tickCount + pt;
        boolean moving = e.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3;
        float time = moving ? (age / 20f) % g.length : 0f;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, e.yRotO, e.getYRot()) + ClientEvents.GHOST_YAW_OFFSET));
        ps.translate(0.0, 0.1 + Math.sin(age / 10f) * 0.05, 0.0);
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        g.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(ClientEvents.GHOST_TEX)), light, time);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(LimboGhost e) {
        return ClientEvents.GHOST_TEX;
    }
}
