package com.froggydude.client.cutscene;

import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Os tocos (carne vermelha com o osso no meio) de onde saiu um braço, a
 * cabeça ou uma perna. Desenhados no espaço do corpo do modelo de jogador
 * (pixels, y cresce pra baixo, origem no pescoço), depois do
 * {@code body.translateAndRotate}.
 */
public final class Stumps {

    public static final ResourceLocation TEXTURE =
            new ResourceLocation(ModEntityTypes.MOD_ID, "textures/entity/arm_stump.png");

    private Stumps() {
    }

    public static void leftShoulder(PoseStack ps, MultiBufferSource buffers, int light) {
        // ombro esquerdo do modelo: x 4..7,5, y 0..3, z -1,8..1,8
        box(ps, buffers, light, 4.0F, 7.6F, -0.2F, 3.0F, -1.8F, 1.8F);
    }

    public static void rightShoulder(PoseStack ps, MultiBufferSource buffers, int light) {
        box(ps, buffers, light, -7.6F, -4.0F, -0.2F, 3.0F, -1.8F, 1.8F);
    }

    /** O pescoço: um toco mais estreito em cima do tronco, com a ponta da coluna. */
    public static void neck(PoseStack ps, MultiBufferSource buffers, int light) {
        box(ps, buffers, light, -2.6F, 2.6F, -1.2F, 0.4F, -1.9F, 1.9F);
    }

    /** Quadril (perna comida): x do lado dela, embaixo do tronco. */
    public static void hip(PoseStack ps, MultiBufferSource buffers, int light, boolean left) {
        float x0 = left ? 0.1F : -3.9F;
        box(ps, buffers, light, x0, x0 + 3.8F, 11.6F, 13.0F, -1.9F, 1.9F);
    }

    /** Caixa em pixels do modelo. */
    public static void box(PoseStack ps, MultiBufferSource buffers, int light,
                           float x0, float x1, float y0, float y1, float z0, float z1) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        box(vc, ps.last().pose(), ps.last().normal(), light,
                x0 / 16F, x1 / 16F, y0 / 16F, y1 / 16F, z0 / 16F, z1 / 16F);
    }

    public static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, int light,
                           float x0, float x1, float y0, float y1, float z0, float z1) {
        quad(vc, m, n, light, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
        quad(vc, m, n, light, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1);
        quad(vc, m, n, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0);
        quad(vc, m, n, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0);
        quad(vc, m, n, light, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0);
        quad(vc, m, n, light, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int light,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz) {
        int ov = OverlayTexture.NO_OVERLAY;
        vc.vertex(m, ax, ay, az).color(1F, 1F, 1F, 1F).uv(0F, 0F).overlayCoords(ov).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, bx, by, bz).color(1F, 1F, 1F, 1F).uv(1F, 0F).overlayCoords(ov).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, cx, cy, cz).color(1F, 1F, 1F, 1F).uv(1F, 1F).overlayCoords(ov).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, dx, dy, dz).color(1F, 1F, 1F, 1F).uv(0F, 1F).overlayCoords(ov).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
    }
}
