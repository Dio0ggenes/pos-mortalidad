package com.adrian.posmortal;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** Dibuja al jefe como un jugador con la skin (y el modelo fino/ancho) de quien lo invocó. */
public class DoppelgangerRenderer extends HumanoidMobRenderer<Doppelganger, PlayerModel<Doppelganger>> {
    private final PlayerModel<Doppelganger> wide;
    private final PlayerModel<Doppelganger> slim;

    public DoppelgangerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    private static PlayerInfo info(Doppelganger e) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null || e.getOwnerId().isEmpty()) return null;
        return conn.getPlayerInfo(e.getOwnerId().get());
    }

    @Override
    public void render(Doppelganger e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        PlayerInfo pi = info(e);
        this.model = (pi != null && "slim".equals(pi.getModelName())) ? slim : wide;
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Doppelganger e) {
        PlayerInfo pi = info(e);
        if (pi != null) return pi.getSkinLocation();
        return DefaultPlayerSkin.getDefaultSkin(e.getOwnerId().orElse(e.getUUID()));
    }
}
