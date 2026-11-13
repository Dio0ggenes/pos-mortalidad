package com.adrian.posmortal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cargador/renderizador de modelos de Blockbench (formato libre) ya convertidos a JSON.
 * Soporta huesos anidados, cubos rotados, UV por cara y animaciones por keyframes (rotación y posición).
 */
public class BbModel {
    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    private static class Cube {
        float[] from, to, origin, rot;
        float[][] uv = new float[6][];
    }

    private static class Bone {
        String name;
        float[] pivot, rot;
        List<Cube> cubes = new ArrayList<>();
        List<Bone> children = new ArrayList<>();
    }

    private final List<Bone> roots = new ArrayList<>();
    private final Map<String, float[][]> rotTracks = new HashMap<>();
    private final Map<String, float[][]> posTracks = new HashMap<>();
    private float texW = 64, texH = 64;
    public float length = 1f;

    public static BbModel load(String resourcePath) throws IOException {
        try (InputStream in = BbModel.class.getResourceAsStream(resourcePath);
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            BbModel m = new BbModel();
            m.texW = o.get("w").getAsFloat();
            m.texH = o.get("h").getAsFloat();
            for (JsonElement e : o.getAsJsonArray("bones")) m.roots.add(parseBone(e.getAsJsonObject()));
            JsonObject a = o.getAsJsonObject("anim");
            m.length = a.get("length").getAsFloat();
            readTracks(a.getAsJsonObject("rotation"), m.rotTracks);
            readTracks(a.getAsJsonObject("position"), m.posTracks);
            return m;
        }
    }

    private static float[] arr(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static Bone parseBone(JsonObject o) {
        Bone b = new Bone();
        b.name = o.get("name").getAsString();
        b.pivot = arr(o.getAsJsonArray("pivot"));
        b.rot = arr(o.getAsJsonArray("rot"));
        for (JsonElement e : o.getAsJsonArray("cubes")) {
            JsonObject co = e.getAsJsonObject();
            Cube c = new Cube();
            c.from = arr(co.getAsJsonArray("from"));
            c.to = arr(co.getAsJsonArray("to"));
            c.origin = arr(co.getAsJsonArray("origin"));
            c.rot = arr(co.getAsJsonArray("rot"));
            JsonObject faces = co.getAsJsonObject("faces");
            for (int i = 0; i < 6; i++) {
                JsonElement f = faces.get(FACES[i]);
                c.uv[i] = (f == null || f.isJsonNull()) ? null : arr(f.getAsJsonArray());
            }
            b.cubes.add(c);
        }
        for (JsonElement e : o.getAsJsonArray("children")) b.children.add(parseBone(e.getAsJsonObject()));
        return b;
    }

    private static void readTracks(JsonObject o, Map<String, float[][]> out) {
        for (Map.Entry<String, JsonElement> en : o.entrySet()) {
            JsonArray rows = en.getValue().getAsJsonArray();
            float[][] kf = new float[rows.size()][];
            for (int i = 0; i < kf.length; i++) kf[i] = arr(rows.get(i).getAsJsonArray());
            out.put(en.getKey(), kf);
        }
    }

    private static float[] sample(float[][] kf, float t) {
        if (kf == null || kf.length == 0) return null;
        if (t <= kf[0][0]) return new float[]{kf[0][1], kf[0][2], kf[0][3]};
        for (int i = 1; i < kf.length; i++) {
            if (t <= kf[i][0]) {
                float span = kf[i][0] - kf[i - 1][0];
                float k = span <= 0 ? 1f : (t - kf[i - 1][0]) / span;
                return new float[]{
                        kf[i - 1][1] + (kf[i][1] - kf[i - 1][1]) * k,
                        kf[i - 1][2] + (kf[i][2] - kf[i - 1][2]) * k,
                        kf[i - 1][3] + (kf[i][3] - kf[i - 1][3]) * k};
            }
        }
        float[] l = kf[kf.length - 1];
        return new float[]{l[1], l[2], l[3]};
    }

    /** El PoseStack debe venir ya escalado a 1/16 (los datos del modelo están en píxeles) y con y hacia arriba. */
    public void render(PoseStack ps, VertexConsumer vc, int light, float time) {
        for (Bone b : roots) renderBone(b, ps, vc, light, time);
    }

    private static void rotate(PoseStack ps, float rx, float ry, float rz) {
        if (rz != 0) ps.mulPose(Axis.ZP.rotationDegrees(rz));
        if (ry != 0) ps.mulPose(Axis.YP.rotationDegrees(ry));
        if (rx != 0) ps.mulPose(Axis.XP.rotationDegrees(rx));
    }

    private void renderBone(Bone b, PoseStack ps, VertexConsumer vc, int light, float time) {
        ps.pushPose();
        float[] ap = sample(posTracks.get(b.name), time);
        float[] ar = sample(rotTracks.get(b.name), time);
        if (ap != null) ps.translate(ap[0], ap[1], ap[2]);
        ps.translate(b.pivot[0], b.pivot[1], b.pivot[2]);
        rotate(ps, b.rot[0] + (ar != null ? ar[0] : 0), b.rot[1] + (ar != null ? ar[1] : 0), b.rot[2] + (ar != null ? ar[2] : 0));
        ps.translate(-b.pivot[0], -b.pivot[1], -b.pivot[2]);
        for (Cube c : b.cubes) renderCube(c, ps, vc, light);
        for (Bone ch : b.children) renderBone(ch, ps, vc, light, time);
        ps.popPose();
    }

    private void renderCube(Cube c, PoseStack ps, VertexConsumer vc, int light) {
        ps.pushPose();
        ps.translate(c.origin[0], c.origin[1], c.origin[2]);
        rotate(ps, c.rot[0], c.rot[1], c.rot[2]);
        ps.translate(-c.origin[0], -c.origin[1], -c.origin[2]);
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float x0 = c.from[0], y0 = c.from[1], z0 = c.from[2];
        float x1 = c.to[0], y1 = c.to[1], z1 = c.to[2];

        face(m, n, vc, light, c.uv[0], 0, 0, -1, new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}});
        face(m, n, vc, light, c.uv[1], 1, 0, 0, new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}});
        face(m, n, vc, light, c.uv[2], 0, 0, 1, new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}});
        face(m, n, vc, light, c.uv[3], -1, 0, 0, new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}});
        face(m, n, vc, light, c.uv[4], 0, 1, 0, new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}});
        face(m, n, vc, light, c.uv[5], 0, -1, 0, new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}});
        ps.popPose();
    }

    private void face(Matrix4f m, Matrix3f n, VertexConsumer vc, int light, float[] uv,
                      float nx, float ny, float nz, float[][] v) {
        if (uv == null || uv[0] == uv[2] || uv[1] == uv[3]) return;
        float u1 = uv[0] / texW, v1 = uv[1] / texH, u2 = uv[2] / texW, v2 = uv[3] / texH;
        float[][] t = {{u1, v1}, {u1, v2}, {u2, v2}, {u2, v1}};
        for (int i = 0; i < 4; i++) {
            vc.vertex(m, v[i][0], v[i][1], v[i][2])
                    .color(255, 255, 255, 255)
                    .uv(t[i][0], t[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(n, nx, ny, nz)
                    .endVertex();
        }
    }
}
