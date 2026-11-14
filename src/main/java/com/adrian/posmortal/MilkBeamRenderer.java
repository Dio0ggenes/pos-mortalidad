package com.adrian.posmortal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class MilkBeamRenderer extends EntityRenderer<MilkBeam> {
    private static final ResourceLocation TEX = new ResourceLocation(Posmortal.MODID, "textures/entity/milk_beam.png");
    private static BbModel model;
    private static boolean failed;

    public MilkBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    private static BbModel model() {
        if (model == null && !failed) {
            try {
                model = BbModel.load("/assets/posmortal/models/entity/milk_beam.json");
            } catch (Exception ex) {
                failed = true;
                ex.printStackTrace();
            }
        }
        return model;
    }

    @Override
    public void render(MilkBeam e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        BbModel m = model();
        if (m == null) return;
        ps.pushPose();
        ps.translate(0.0, e.getBbHeight() / 2.0, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, e.yRotO, e.getYRot()) + 180f));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
        ps.scale(1.5f / 16f, 1.5f / 16f, 1.5f / 16f);
        ps.translate(0.0, -6.0, 0.0); // el centro del modelo está en y=6
        float time = ((e.tickCount + pt) / 20f) % m.length;
        m.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), 15728880, time);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(MilkBeam e) {
        return TEX;
    }
}
