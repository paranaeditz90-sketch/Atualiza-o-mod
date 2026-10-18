package com.froggydude.client;

import com.froggydude.client.cutscene.ClientFinisher;
import com.froggydude.client.cutscene.Stumps;
import com.froggydude.config.FroggyClientConfig;
import com.froggydude.entity.Finisher;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
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
 * - a língua: uma fita chata da largura da boca, rosa-claro na boca e mais
 *   escura na ponta redonda (vídeo do LM: "o modelo da língua é 2D, como um papel");
 * - o braço arrancado atravessado na boca (como cachorro com osso), com a
 *   skin de quem perdeu o braço; vai sumindo enquanto ele come;
 * - no golpe final, o braço ou a cabeça da vítima na boca dele.
 */
public class FroggydudeRenderer extends GeoEntityRenderer<FroggydudeEntity> {

    // Língua, em pixels do modelo (coordenadas do GeckoLib: x invertido em relação ao Blockbench)
    private static final float TONGUE_HALF_W = 2.6F;
    private static final float TONGUE_TIP_HALF_W = 2.0F;
    private static final float TONGUE_SAG = 0.7F;
    private static final int TONGUE_SEGMENTS = 12;
    /** Meia espessura (px): uma fatia fininha, "2D só que achatado" (LM). */
    private static final float TONGUE_HALF_THICK = 0.25F;
    private static final Vector3f LEFT = new Vector3f(-1, 0, 0), RIGHT = new Vector3f(1, 0, 0);
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
        addRenderLayer(new MouthStainLayer(this));
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
            } else if ("head".equals(name) && animatable.getFroggyState() == FroggyState.FINISHER) {
                // golpe final: o que está na boca vem da tabela (braço esquerdo, direito, cabeça)
                ClientFinisher.Entry e = ClientFinisher.forFroggy(animatable.getId());
                int held = e == null || !FroggyClientConfig.gore().dismember() ? 0 : Finisher.held(e.t(partialTick), e.skipLeft);
                if (held != 0) {
                    poseStack.pushPose();
                    RenderUtils.prepMatrixForBone(poseStack, bone);
                    boolean drew = drawFinisherPart(poseStack, animatable, e, held, partialTick, bufferSource, packedLight);
                    poseStack.popPose();
                    if (drew) buffer = bufferSource.getBuffer(renderType);
                }
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
     * A língua é CHATA, uma fatia fininha (LM: "2D só que achatado"), da largura
     * da boca, rosa-claro saindo da boca e mais escura na ponta, que é redonda.
     * Desenhada com 1 pixel de comprimento pra frente; o osso "tongue" é esticado
     * no eixo Z pela animação (FroggydudeModel), então a ponta vai até o alvo e a
     * largura continua a da boca. Cai um tiquinho no meio (é mole) e tem as duas
     * faces (vista de baixo também aparece).
     */
    private void drawTongue(PoseStack poseStack, VertexConsumer vc, int light, int overlay) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        Vector3f up = new Vector3f(0, 1, 0), down = new Vector3f(0, -1, 0);
        float prevF = 0F;
        for (int i = 1; i <= TONGUE_SEGMENTS; i++) {
            float f = i / (float) TONGUE_SEGMENTS;
            float wa = tongueHalfWidth(prevF) / 16F, wb = tongueHalfWidth(f) / 16F;
            float ya = tongueY(prevF) / 16F, yb = tongueY(f) / 16F;
            float za = (TONGUE_Z - prevF) / 16F, zb = (TONGUE_Z - f) / 16F;
            float sa = 1F - 0.25F * prevF, sb = 1F - 0.25F * f;
            float ga = 1F - 0.5F * prevF, gb = 1F - 0.5F * f;
            float t = TONGUE_HALF_THICK / 16F;
            // em cima
            vertex(vc, m, n, -wa, ya + t, za, sa, ga, ga, TONGUE_U, TONGUE_V, overlay, light, up);
            vertex(vc, m, n, wa, ya + t, za, sa, ga, ga, TONGUE_U, TONGUE_V, overlay, light, up);
            vertex(vc, m, n, wb, yb + t, zb, sb, gb, gb, TONGUE_U, TONGUE_V, overlay, light, up);
            vertex(vc, m, n, -wb, yb + t, zb, sb, gb, gb, TONGUE_U, TONGUE_V, overlay, light, up);
            // embaixo (um pouco mais escura)
            float d = 0.82F;
            vertex(vc, m, n, -wb, yb - t, zb, sb * d, gb * d, gb * d, TONGUE_U, TONGUE_V, overlay, light, down);
            vertex(vc, m, n, wb, yb - t, zb, sb * d, gb * d, gb * d, TONGUE_U, TONGUE_V, overlay, light, down);
            vertex(vc, m, n, wa, ya - t, za, sa * d, ga * d, ga * d, TONGUE_U, TONGUE_V, overlay, light, down);
            vertex(vc, m, n, -wa, ya - t, za, sa * d, ga * d, ga * d, TONGUE_U, TONGUE_V, overlay, light, down);
            // as bordas da fatia (de lado ela não some: aparece uma tirinha)
            float e = 0.7F;
            vertex(vc, m, n, wa, ya + t, za, sa * e, ga * e, ga * e, TONGUE_U, TONGUE_V, overlay, light, RIGHT);
            vertex(vc, m, n, wa, ya - t, za, sa * e, ga * e, ga * e, TONGUE_U, TONGUE_V, overlay, light, RIGHT);
            vertex(vc, m, n, wb, yb - t, zb, sb * e, gb * e, gb * e, TONGUE_U, TONGUE_V, overlay, light, RIGHT);
            vertex(vc, m, n, wb, yb + t, zb, sb * e, gb * e, gb * e, TONGUE_U, TONGUE_V, overlay, light, RIGHT);
            vertex(vc, m, n, -wb, yb + t, zb, sb * e, gb * e, gb * e, TONGUE_U, TONGUE_V, overlay, light, LEFT);
            vertex(vc, m, n, -wb, yb - t, zb, sb * e, gb * e, gb * e, TONGUE_U, TONGUE_V, overlay, light, LEFT);
            vertex(vc, m, n, -wa, ya - t, za, sa * e, ga * e, ga * e, TONGUE_U, TONGUE_V, overlay, light, LEFT);
            vertex(vc, m, n, -wa, ya + t, za, sa * e, ga * e, ga * e, TONGUE_U, TONGUE_V, overlay, light, LEFT);
            prevF = f;
        }
    }

    /** Meia largura (px) ao longo da língua (0 = boca, 1 = ponta): afina um pouco e arredonda no fim. */
    private static float tongueHalfWidth(float f) {
        float w = TONGUE_HALF_W + (TONGUE_TIP_HALF_W - TONGUE_HALF_W) * f;
        if (f > 0.88F) {
            float k = (f - 0.88F) / 0.12F;
            w *= (float) Math.sqrt(Math.max(0F, 1F - k * k));
        }
        return w;
    }

    /** Altura (px) ao longo da língua: mole, cai no meio. */
    private static float tongueY(float f) {
        return TONGUE_Y - TONGUE_SAG * Mth.sin(f * Mth.PI);
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

    // ---------------------------------------------------------------- golpe final

    /**
     * O pedaço da vítima na boca dele (com a skin dela, manga/chapéu inclusos):
     * o braço pendurado pela ponta do ombro, balançando, ou a cabeça presa pelo
     * pescoço, virando de lado, de topo e de costas enquanto ele sacode (no
     * vídeo da zebra é assim). Usa as partes do modelo de jogador do próprio
     * jogo, então braço fino/normal e a segunda camada saem certos.
     */
    private boolean drawFinisherPart(PoseStack ps, FroggydudeEntity froggy, ClientFinisher.Entry e, int held,
                                     float partial, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        if (!(mc.level.getEntity(e.victimId) instanceof AbstractClientPlayer victim)) return false;
        if (!(mc.getEntityRenderDispatcher().getRenderer(victim) instanceof PlayerRenderer pr)) return false;
        PlayerModel<AbstractClientPlayer> model = pr.getModel();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(victim.getSkinTextureLocation()));
        float wob = froggy.tickCount + partial;
        float inv = 1F / FroggydudeEntity.MODEL_SCALE;

        ps.translate(0F, MOUTH_Y / 16F, MOUTH_Z / 16F);
        ps.scale(inv, inv, inv);
        // o pedaço vai atrás da cabeça dele, que chicoteia na animação (braço: um lado a cada
        // 0,2 s; cabeça: a cada 0,1 s, depois de 0,4 s quase parado - vídeo da zebra, 86-88,8 s)
        float t = e.t(partial);
        boolean head = held == 3;
        float amp = head && t < Finisher.RIP_H + 12 ? 0.25F : 1F;
        float w = head ? wob * Mth.HALF_PI : wob * Mth.PI / 4F;
        ps.mulPose(Axis.YP.rotationDegrees(Mth.sin(w) * (head ? 20F : 26F) * amp));
        // daqui pra frente: espaço do modelo de jogador (y pra baixo, frente em -z)
        ps.scale(-1F, -1F, 1F);
        if (head) {
            // topo da cabeça pra frente, o pescoço dentro da boca; a cada chicotada ela vira de
            // face (de lado, de topo, de costas), como a da zebra
            ps.mulPose(Axis.XP.rotationDegrees(90F + Mth.sin(w + 1F) * 25F * amp));
            ps.mulPose(Axis.YP.rotationDegrees(180F + Mth.sin(w + 0.6F) * 100F * amp));
            ps.translate(0F, 1.5F / 16F, 0F);
            renderPart(model.head, ps, vc, light);
            renderPart(model.hat, ps, vc, light);
            Stumps.box(ps, buffers, light, -3.3F, 3.3F, -0.9F, 0.5F, -3.3F, 3.3F);
        } else {
            boolean left = held == 1;
            boolean slim = "slim".equals(victim.getModelName());
            float cx = (left ? 1F : -1F) * (slim ? 0.5F : 1F);
            // pendurado pra frente e pra baixo, balançando atrás da chicotada
            ps.mulPose(Axis.XP.rotationDegrees(-60F + Mth.sin(w + 0.8F) * 22F));
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(w + 1.6F) * 18F));
            ps.translate(-cx / 16F, 2F / 16F, 0F);
            renderPart(left ? model.leftArm : model.rightArm, ps, vc, light);
            renderPart(left ? model.leftSleeve : model.rightSleeve, ps, vc, light);
            Stumps.box(ps, buffers, light, cx - 2.3F, cx + 2.3F, -2.6F, -1.4F, -2.3F, 2.3F);
        }
        return true;
    }

    /** Desenha uma parte do modelo na pose zero (sem o que a última animação deixou nela). */
    private static void renderPart(ModelPart part, PoseStack ps, VertexConsumer vc, int light) {
        PartPose saved = part.storePose();
        boolean visible = part.visible;
        part.loadPose(PartPose.ZERO);
        part.visible = true;
        part.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        part.loadPose(saved);
        part.visible = visible;
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
