package com.froggydude.client;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.UUID;

/**
 * Desenha o Froggy (GeckoLib) e duas coisas que não são cubos do modelo:
 * - a língua: uma pirâmide grossa na boca e fina na ponta (no vídeo contra o
 *   AJ ela é assim, e não uma barra quadrada);
 * - o braço arrancado atravessado na boca (como cachorro com osso), com a
 *   skin de quem perdeu o braço; vai sumindo enquanto ele come.
 */
public class FroggydudeRenderer extends GeoEntityRenderer<FroggydudeEntity> {

    // Língua, em pixels do modelo (coordenadas do GeckoLib: x invertido em relação ao Blockbench)
    private static final float TONGUE_HALF_W = 1.25F;
    private static final float TONGUE_HALF_H = 0.8F;
    private static final float TONGUE_Y = 26.0F;
    private static final float TONGUE_Z = -4.0F;   // frente do rosto (pivô do osso)
    // Um pixel rosa da skin (o prepare_skins.py pinta essa área de língua)
    private static final float TONGUE_U = 58.5F / 64F;
    private static final float TONGUE_V = 17.0F / 64F;

    // Braço na boca: centro, no espaço do osso head (boca na frente do rosto, embaixo)
    private static final float MOUTH_Y = 26.0F;
    private static final float MOUTH_Z = -4.5F;

    public FroggydudeRenderer(EntityRendererProvider.Context context) {
        super(context, new FroggydudeModel());
        this.shadowRadius = 0.5F;
        this.withScale(FroggydudeEntity.MODEL_SCALE);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, FroggydudeEntity animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (!isReRender) {
            String name = bone.getName();
            if ("tongue".equals(name) && bone.getScaleZ() > 0.05F) {
                poseStack.pushPose();
                RenderUtils.prepMatrixForBone(poseStack, bone);
                drawTongue(poseStack, buffer, packedLight, packedOverlay);
                poseStack.popPose();
            } else if ("head".equals(name) && animatable.getHeldArmOwner().isPresent()) {
                poseStack.pushPose();
                RenderUtils.prepMatrixForBone(poseStack, bone);
                drawHeldArm(poseStack, animatable, bufferSource, packedLight);
                poseStack.popPose();
                // o braço usa outra textura: volta pro buffer do Froggy antes de continuar
                buffer = bufferSource.getBuffer(renderType);
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    // ---------------------------------------------------------------- língua

    /**
     * Pirâmide de 1 pixel de comprimento, apontando pra frente. O osso "tongue"
     * é esticado no eixo Z pela animação (FroggydudeModel), então a ponta vai
     * até o alvo e a base continua do tamanho da boca.
     */
    private void drawTongue(PoseStack poseStack, VertexConsumer vc, int light, int overlay) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float x0 = -TONGUE_HALF_W / 16F, x1 = TONGUE_HALF_W / 16F;
        float y0 = (TONGUE_Y - TONGUE_HALF_H) / 16F, y1 = (TONGUE_Y + TONGUE_HALF_H) / 16F;
        float zb = TONGUE_Z / 16F;
        float tipX = 0F, tipY = TONGUE_Y / 16F, tipZ = (TONGUE_Z - 1F) / 16F;

        // em cima mais clara, embaixo mais escura (igual ao vídeo)
        tri(vc, m, n, light, overlay, 1.00F, x0, y1, zb, x1, y1, zb, tipX, tipY, tipZ, 0, 1, -0.3F);
        tri(vc, m, n, light, overlay, 0.70F, x1, y0, zb, x0, y0, zb, tipX, tipY, tipZ, 0, -1, -0.3F);
        tri(vc, m, n, light, overlay, 0.85F, x1, y1, zb, x1, y0, zb, tipX, tipY, tipZ, 1, 0, -0.3F);
        tri(vc, m, n, light, overlay, 0.85F, x0, y0, zb, x0, y1, zb, tipX, tipY, tipZ, -1, 0, -0.3F);
    }

    /** Um triângulo (quad com o último vértice repetido): rosa da textura, com sombra por face. */
    private static void tri(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int overlay, float shade,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float nx, float ny, float nz) {
        Vector3f nn = new Vector3f(nx, ny, nz).normalize();
        vertex(vc, m, n, ax, ay, az, shade, shade, shade, TONGUE_U, TONGUE_V, overlay, light, nn);
        vertex(vc, m, n, bx, by, bz, shade, shade, shade, TONGUE_U, TONGUE_V, overlay, light, nn);
        vertex(vc, m, n, cx, cy, cz, shade, shade, shade, TONGUE_U, TONGUE_V, overlay, light, nn);
        vertex(vc, m, n, cx, cy, cz, shade, shade, shade, TONGUE_U, TONGUE_V, overlay, light, nn);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                               float r, float g, float b, float u, float v, int overlay, int light, Vector3f nn) {
        vc.vertex(m, x, y, z).color(r, g, b, 1.0F).uv(u, v).overlayCoords(overlay).uv2(light)
                .normal(n, nn.x(), nn.y(), nn.z()).endVertex();
    }

    // ---------------------------------------------------------------- braço arrancado

    /**
     * O braço esquerdo de quem foi pego, atravessado na boca dele como um
     * pedaço de carne (as mãos seguram as pontas). Vai diminuindo enquanto ele come.
     */
    private void drawHeldArm(PoseStack poseStack, FroggydudeEntity froggy, MultiBufferSource buffers, int light) {
        UUID owner = froggy.getHeldArmOwner().orElse(null);
        if (owner == null) return;
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin(owner);
        boolean slim = "slim".equals(DefaultPlayerSkin.getSkinModelName(owner));
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(owner);
        if (info != null) {
            skin = info.getSkinLocation();
            slim = "slim".equals(info.getModelName());
        }

        // comendo: o braço encurta até sobrar um toco
        float left = 1.0F;
        if (froggy.getFroggyState() == FroggyState.ARM_EAT) {
            left = 1.0F - 0.75F * Mth.clamp(froggy.getClientStateAge() / 80F, 0F, 1F);
        }
        float len = 12F * left;
        float w = slim ? 3F : 4F;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        PoseStack.Pose pose = poseStack.last();
        // atravessado na boca: comprimento no eixo X (de lado a lado do rosto)
        float x0 = -len / 2F / 16F, x1 = len / 2F / 16F;
        float y0 = (MOUTH_Y - 2F) / 16F, y1 = (MOUTH_Y + 2F) / 16F;
        float z0 = (MOUTH_Z - w / 2F) / 16F, z1 = (MOUTH_Z + w / 2F) / 16F;
        drawArmBox(vc, pose, x0, x1, y0, y1, z0, z1, w, left, light);
    }

    /**
     * Caixa com a textura do braço esquerdo da skin (64x64, a partir de 32,48).
     * O "comprimento" do braço na skin (12 px, vertical) vai no eixo X da caixa.
     * A ponta do ombro fica vermelha (é onde foi arrancado).
     */
    private static void drawArmBox(VertexConsumer vc, PoseStack.Pose pose, float x0, float x1, float y0, float y1,
                                   float z0, float z1, float w, float left, int light) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int ov = OverlayTexture.NO_OVERLAY;
        float u = 32F, v = 48F, d = 4F;
        float vTop = v + d;
        float vBot = v + d + 12F * left; // a parte comida some da textura também

        // 4 lados compridos: frente, direita, trás, esquerda (u = largura, v = comprimento)
        face(vc, m, n, light, ov, 1F, 1F, 1F, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0,
                u + d, vTop, u + d + w, vBot, 0, 0, -1);
        face(vc, m, n, light, ov, 1F, 1F, 1F, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
                u + d + w, vTop, u + 2 * d + w, vBot, 0, 1, 0);
        face(vc, m, n, light, ov, 1F, 1F, 1F, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                u + 2 * d + w, vTop, u + 2 * d + 2 * w, vBot, 0, 0, 1);
        face(vc, m, n, light, ov, 1F, 1F, 1F, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                u, vTop, u + d, vBot, 0, -1, 0);
        // ponta do ombro (arrancada): vermelho sangue
        face(vc, m, n, light, ov, 0.55F, 0.05F, 0.05F, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1,
                u + d, v, u + d + w, v + d, -1, 0, 0);
        // ponta da mão (ou o toco que sobrou, se já comeu)
        float r = left < 0.99F ? 0.55F : 1F, gb = left < 0.99F ? 0.05F : 1F;
        face(vc, m, n, light, ov, r, gb, gb, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0,
                u + d + w, v, u + d + 2 * w, v + d, 1, 0, 0);
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int overlay,
                             float r, float g, float b,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        Vector3f nn = new Vector3f(nx, ny, nz);
        float s = 1F / 64F;
        vertex(vc, m, n, ax, ay, az, r, g, b, u0 * s, v0 * s, overlay, light, nn);
        vertex(vc, m, n, bx, by, bz, r, g, b, u0 * s, v1 * s, overlay, light, nn);
        vertex(vc, m, n, cx, cy, cz, r, g, b, u1 * s, v1 * s, overlay, light, nn);
        vertex(vc, m, n, dx, dy, dz, r, g, b, u1 * s, v0 * s, overlay, light, nn);
    }
}
