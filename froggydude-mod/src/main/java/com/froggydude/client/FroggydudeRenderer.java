package com.froggydude.client;

import com.froggydude.client.cutscene.ClientFinisher;
import com.froggydude.client.cutscene.Stumps;
import com.froggydude.config.FroggyClientConfig;
import com.froggydude.entity.Finisher;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.PacedAnimationController;
import com.froggydude.entity.ai.FroggyStealGoal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.state.BoneSnapshot;
import com.froggydude.client.pieces.RagdollScene;
import com.froggydude.client.pieces.ReviveScene;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.List;
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
    /** (v0.7.30) Meio comprimento da arma deitada na boca (px; o ícone em pé no chão, 1,4x, girado 45°). */
    private static final float STOLEN_HALF = 7.0F;
    /** A arma começa a entrar depois da dentada (tick 2 da weapon_eat). */
    private static final float SWALLOW_FROM = 2.0F;

    /** (v0.7.33) A boca neste quadro (na posição dele, eixos do mundo), tirada do osso da cabeça: a língua da captura sai daí. */
    private final Vector3f captureMouth = new Vector3f();
    private boolean captureMouthKnown;

    /**
     * (v0.7.22) Nadando: o corpo deita (como o jogador nadando no jogo) e inclina pra onde ele vai (de cabeça pra baixo
     * mergulhando, de cabeça pra cima subindo), girando no meio do corpo. As batidas de perna e a gangorra são da
     * animação "swim" (build_anims.py).
     */
    @Override
    protected void applyRotations(FroggydudeEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick) {
        // (v0.7.40) levantando do ragdoll: começa no lugar e no rumo em que o corpo mole caiu e vai pros do mob
        float[] up = RagdollScene.getup(animatable, partialTick);
        if (up != null) {
            float inv = 1F / FroggydudeEntity.MODEL_SCALE; // o poseStack já está na escala do modelo
            poseStack.translate(up[0] * inv, 0.0D, up[1] * inv);
            rotationYaw = up[2];
        }
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);
        float swim = animatable.getSwimAmount(partialTick);
        if (swim <= 0F) return;
        float tilt = (90F + animatable.getSwimPitch(partialTick)) * swim;
        poseStack.translate(0.0D, 0.9D, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(-tilt));
        poseStack.translate(0.0D, -0.9D, 0.0D);
    }

    public FroggydudeRenderer(EntityRendererProvider.Context context) {
        super(context, new FroggydudeModel());
        this.shadowRadius = 0.5F;
        this.withScale(FroggydudeEntity.MODEL_SCALE);
        addRenderLayer(new MouthStainLayer(this));
        // o corpo dele na cutscene anda no relógio da tabela, junto com o áudio e o pedaço na boca; e a flexão
        // (v0.7.18) no relógio da mordida, sem escorregar a cada volta do loop
        PacedAnimationController.externalClock = (animatable, animation, tick) -> {
            if (!(animatable instanceof FroggydudeEntity f)) return -1D;
            if ("pin_hold".equals(animation) || "feed_mob".equals(animation)) {
                return f.pushupAnimTicks(Minecraft.getInstance().getFrameTime());
            }
            // (v0.7.18) o esmagamento: o punho bate na hora do soco e do "tai" do servidor
            if ("smash".equals(animation)) return f.smashAnimTicks(Minecraft.getInstance().getFrameTime());
            // (v0.7.35) o levantar do renascer: no relógio do servidor, junto com os pedaços, os sons e a virada
            if ("revive_flip".equals(animation) || "revive_shake".equals(animation)) {
                return f.reviveStandAnimTicks(Minecraft.getInstance().getFrameTime());
            }
            // (v0.7.40) o levantar do ragdoll: no relógio do servidor, emendado na ida pra pose de bruços
            if ("ragdoll_getup".equals(animation)) return f.ragdollGetupAnimTicks(Minecraft.getInstance().getFrameTime());
            if (!animation.startsWith("finisher")) return -1D;
            ClientFinisher.Entry e = ClientFinisher.forFroggy(f.getId());
            if (e == null) return -1D;
            float partial = (float) (tick - Math.floor(tick));
            return Math.max(0D, e.t(partial) - (e.skipLeft ? Finisher.SKIP : 0));
        };
    }

    /** (v0.7.35) Renascendo, fase dos pedaços: o modelo inteiro não é desenhado (só a animação roda por baixo). */
    private boolean hideForRevive;

    @Override
    public void render(FroggydudeEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        captureMouthKnown = false;
        ReviveScene scene = ReviveScene.of(entity);
        // gore desligado: nada de corpo despedaçado - ele fica deitado de bruços, inteiro, até levantar
        boolean pieces = scene != null && scene.piecesPhase(entity.level()) && FroggyClientConfig.gore().any();
        this.shadowRadius = pieces ? 0F : 0.5F;
        if (pieces) {
            renderRevive(scene, entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        // (v0.7.40) mole depois do tiro: as partes do corpo com física (client.pieces.RagdollScene)
        RagdollScene rag = RagdollScene.of(entity);
        if (rag != null && RagdollScene.physicsPhase(entity, partialTick)) {
            renderRagdoll(rag, entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (captureMouthKnown && CaptureRender.capturing(entity)
                && entity.level().getEntity(entity.getCaptureId()) instanceof net.minecraft.world.entity.LivingEntity victim) {
            CaptureRender.draw(poseStack, bufferSource, entity, victim, captureMouth, partialTick, packedLight);
        } else if (captureMouthKnown && entity.getFroggyState() == FroggyState.TONGUE_THROW) {
            // soltou no arremesso: a língua mole cai e volta pra boca
            CaptureRender.drawLoose(poseStack, bufferSource, entity, captureMouth, partialTick, packedLight);
        }
    }

    /** (v0.7.33) Segurando alguém lá em cima: desenha se der pra ver ele OU a vítima (a língua liga os dois). */
    @Override
    public boolean shouldRender(FroggydudeEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        if (super.shouldRender(entity, frustum, camX, camY, camZ)) return true;
        if (!CaptureRender.capturing(entity)) return false;
        net.minecraft.world.entity.Entity v = entity.level().getEntity(entity.getCaptureId());
        return v != null && frustum.isVisible(entity.getBoundingBoxForCulling().minmax(v.getBoundingBox().inflate(0.8D)));
    }

    /**
     * (v0.7.35) O renascer: a animação roda normal (fica deitado de bruços por baixo, pra emendar no levantar), mas o
     * que aparece são os pedaços (client.pieces.ReviveScene), cada um com os ossos do próprio modelo na pose de descanso.
     */
    private void renderRevive(ReviveScene scene, FroggydudeEntity entity, float entityYaw, float partialTick,
                              PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(entity));
        if (!scene.hasGeometry()) scene.setGeometry(measureParts(model));
        hideForRevive = true;
        try {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            hideForRevive = false;
        }
        Vec3 base = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
        RenderType rt = getRenderType(entity, getTextureLocation(entity), bufferSource, partialTick);
        scene.render(poseStack, base, partialTick, (ps, part) -> drawRevivePart(ps, model, entity, part, rt, bufferSource,
                partialTick, packedLight), bufferSource, packedLight);
    }

    /**
     * (v0.7.40) O ragdoll: a animação roda escondida por baixo (de bruços, pra emendar no levantar) e o que aparece são as
     * partes, cada uma com os ossos do próprio modelo na pose de descanso, onde a física pôs.
     */
    private void renderRagdoll(RagdollScene rag, FroggydudeEntity entity, float entityYaw, float partialTick,
                               PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(entity));
        if (!RagdollScene.hasGeometry()) RagdollScene.geometry(model);
        hideForRevive = true;
        try {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            hideForRevive = false;
        }
        Vec3 base = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
        RenderType rt = getRenderType(entity, getTextureLocation(entity), bufferSource, partialTick);
        // a bala nele caído: o vermelho do dano, como no corpo normal
        int overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(entity.hurtTime > 0));
        rag.render(entity, poseStack, base, partialTick, (ps, part) -> drawBones(ps, model, entity, RagdollScene.bonesOf(part),
                RagdollScene.hiddenOf(part), rt, bufferSource, partialTick, packedLight, overlay));
    }

    /** Os ossos de um pedaço, na pose de descanso (guardando e devolvendo a da animação), sem os que são outro pedaço. */
    private void drawRevivePart(PoseStack ps, BakedGeoModel model, FroggydudeEntity entity, int part, RenderType rt,
                                MultiBufferSource buffers, float partialTick, int light) {
        drawBones(ps, model, entity, ReviveScene.bonesOf(part), ReviveScene.hiddenOf(part), rt, buffers, partialTick, light,
                OverlayTexture.NO_OVERLAY);
    }

    private void drawBones(PoseStack ps, BakedGeoModel model, FroggydudeEntity entity, String[] bones, String[] hide,
                           RenderType rt, MultiBufferSource buffers, float partialTick, int light, int overlay) {
        List<GeoBone> hidden = new ArrayList<>();
        for (String h : hide) {
            model.getBone(h).ifPresent(b -> {
                if (!b.isHidden()) {
                    b.setHidden(true);
                    hidden.add(b);
                }
            });
        }
        try {
            for (String name : bones) {
                GeoBone bone = model.getBone(name).orElse(null);
                if (bone == null) continue;
                List<float[]> saved = new ArrayList<>();
                List<GeoBone> order = new ArrayList<>();
                rest(bone, saved, order);
                try {
                    renderRecursively(ps, entity, bone, rt, buffers, buffers.getBuffer(rt), true, partialTick, light,
                            overlay, 1F, 1F, 1F, 1F);
                } finally {
                    restore(saved, order);
                }
            }
        } finally {
            for (GeoBone b : hidden) b.setHidden(false);
        }
    }

    /** Devolve a pose da animação aos ossos (guardada pelo rest). */
    private static void restore(List<float[]> saved, List<GeoBone> order) {
        for (int i = 0; i < order.size(); i++) {
            float[] v = saved.get(i);
            GeoBone b = order.get(i);
            b.setRotX(v[0]);
            b.setRotY(v[1]);
            b.setRotZ(v[2]);
            b.setPosX(v[3]);
            b.setPosY(v[4]);
            b.setPosZ(v[5]);
            b.setScaleX(v[6]);
            b.setScaleY(v[7]);
            b.setScaleZ(v[8]);
        }
    }

    private static void rest(GeoBone bone, List<float[]> saved, List<GeoBone> order) {
        saved.add(new float[]{bone.getRotX(), bone.getRotY(), bone.getRotZ(), bone.getPosX(), bone.getPosY(), bone.getPosZ(),
                bone.getScaleX(), bone.getScaleY(), bone.getScaleZ()});
        order.add(bone);
        BoneSnapshot s = bone.getInitialSnapshot();
        bone.setRotX(s.getRotX());
        bone.setRotY(s.getRotY());
        bone.setRotZ(s.getRotZ());
        bone.setPosX(s.getOffsetX());
        bone.setPosY(s.getOffsetY());
        bone.setPosZ(s.getOffsetZ());
        bone.setScaleX(s.getScaleX());
        bone.setScaleY(s.getScaleY());
        bone.setScaleZ(s.getScaleZ());
        for (GeoBone c : bone.getChildBones()) rest(c, saved, order);
    }

    /** Centro e meia-caixa (px) de cada pedaço, pelos cubos do modelo (a cabeça, o tronco sem cabeça/braços...). */
    private static float[][] measureParts(BakedGeoModel model) {
        float[][] out = new float[ReviveScene.partCount()][];
        for (int part = 0; part < out.length; part++) {
            float[] mn = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] mx = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            java.util.Set<String> skip = new java.util.HashSet<>(List.of(ReviveScene.hiddenOf(part)));
            for (String name : ReviveScene.bonesOf(part)) {
                model.getBone(name).ifPresent(b -> bounds(b, skip, mn, mx));
            }
            if (mn[0] > mx[0]) {
                out[part] = new float[]{0F, 16F, 0F, 2F, 2F, 2F};
                continue;
            }
            // o GeckoLib guarda os vértices em blocos (px/16)
            out[part] = new float[]{(mn[0] + mx[0]) * 8F, (mn[1] + mx[1]) * 8F, (mn[2] + mx[2]) * 8F,
                    (mx[0] - mn[0]) * 8F, (mx[1] - mn[1]) * 8F, (mx[2] - mn[2]) * 8F};
        }
        return out;
    }

    private static void bounds(GeoBone bone, java.util.Set<String> skip, float[] mn, float[] mx) {
        if (skip.contains(bone.getName())) return;
        for (GeoCube cube : bone.getCubes()) {
            if (cube.inflate() > 0.01D) continue;   // a camada de fora (o casaco) não conta na caixa
            for (GeoQuad q : cube.quads()) {
                if (q == null) continue;
                for (GeoVertex v : q.vertices()) {
                    Vector3f p = v.position();
                    mn[0] = Math.min(mn[0], p.x);
                    mn[1] = Math.min(mn[1], p.y);
                    mn[2] = Math.min(mn[2], p.z);
                    mx[0] = Math.max(mx[0], p.x);
                    mx[1] = Math.max(mx[1], p.y);
                    mx[2] = Math.max(mx[2], p.z);
                }
            }
        }
        for (GeoBone c : bone.getChildBones()) bounds(c, skip, mn, mx);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, FroggydudeEntity animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (hideForRevive) return;
        if (!isReRender && "head".equals(bone.getName())
                && (CaptureRender.capturing(animatable) || animatable.getFroggyState() == FroggyState.TONGUE_THROW)) {
            // (v0.7.33) onde a boca está no mundo (o tronco, a cabeça olhando pra cima, tudo da animação)
            poseStack.pushPose();
            RenderUtils.prepMatrixForBone(poseStack, bone);
            new Matrix4f(this.entityRenderTranslations).invert().mul(poseStack.last().pose())
                    .transformPosition(0F, TONGUE_Y / 16F, TONGUE_Z / 16F, captureMouth);
            captureMouthKnown = true;
            poseStack.popPose();
        }
        if (!isReRender) {
            String name = bone.getName();
            // segurando alguém no ar, a língua é a fita da captura (CaptureRender), não a reta do osso
            if ("tongue".equals(name) && bone.getScaleZ() > 0.05F && !CaptureRender.capturing(animatable)) {
                poseStack.pushPose();
                RenderUtils.prepMatrixForBone(poseStack, bone);
                drawTongue(poseStack, buffer, packedLight, packedOverlay);
                // (v0.7.29) a arma de mod roubada vem grudada na ponta da língua (Grox 1:17,9)
                ItemStack carried = animatable.getTongueItem();
                if (!carried.isEmpty()) {
                    poseStack.translate(0F, tongueY(1F) / 16F, (TONGUE_Z - 1F) / 16F);
                    poseStack.scale(1F, 1F, 1F / bone.getScaleZ()); // a língua esticada não estica a arma
                    drawStolenItem(poseStack, animatable, carried, bufferSource, packedLight, 1.2F);
                    buffer = bufferSource.getBuffer(renderType);
                }
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
            } else if ("head".equals(name) && !animatable.getMouthItem().isEmpty()) {
                // (v0.7.29) a arma roubada atravessada na boca enquanto ele come (Grox 1:24,0)
                poseStack.pushPose();
                RenderUtils.prepMatrixForBone(poseStack, bone);
                poseStack.translate(0F, (MOUTH_Y - 2.5F) / 16F, (MOUTH_Z - 1.0F) / 16F);
                // o ícone chato vem na diagonal; a arma desenhada só pelo mod (TaCZ) vem pela figura de inventário, já
                // deitada (StolenItemFx). As duas centradas na boca.
                ItemStack inMouth = animatable.getMouthItem();
                boolean flat = !StolenItemFx.standingGun(inMouth, animatable.level());
                // a figura de inventário vem centrada; o ícone no chão vem 2,5 px mais alto (o jogo sobe ele)
                if (!flat) poseStack.translate(0F, 2.5F / 16F, 0F);
                if (animatable.getFroggyState() == FroggyState.WEAPON_EAT) {
                    // (v0.7.30) "vai engolindo a arma": a ponta de dentro fica parada na boca e o pedaço de fora
                    // encurta enquanto a cabeça sacode, até o "glup" (FroggyStealGoal.SWALLOW)
                    float age = animatable.getClientStateAge() + partialTick;
                    float in = Mth.clamp((age - SWALLOW_FROM) / (FroggyStealGoal.SWALLOW - SWALLOW_FROM), 0F, 1F);
                    float out = 1F - 0.92F * in;
                    poseStack.translate(STOLEN_HALF / 16F * (0.15F - out), 0F, 0F);
                    poseStack.scale(out, 1F - 0.2F * in, 1F);
                }
                // deitada de lado a lado da boca: o ícone diagonal gira 45° (fica reto); a figura da arma já vem deitada
                if (flat) poseStack.mulPose(Axis.ZP.rotationDegrees(-45F));
                drawStolenItem(poseStack, animatable, inMouth, bufferSource, packedLight, flat ? 1.4F : 1.15F);
                poseStack.popPose();
                buffer = bufferSource.getBuffer(renderType);
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * (v0.7.29) A arma de mod roubada, do jeito que o jogo desenha o item largado no chão: todo mod de arma suporta
     * esse jeito (o modelo "na mão" de cada mod tem posição própria e quebraria aqui).
     * (v0.7.31) Menos a arma desenhada só pelo mod (TaCZ): no chão, cada arma tem a posição dela (do pacote de armas)
     * e a pistola ia pro queixo, o fuzil saía de lado, e no celular do LM uma foi parar em cima da cabeça. Essa vai
     * pela figura do inventário (a arma de lado, centrada, igual pra todas), no mesmo tamanho do ícone no chão.
     */
    private static void drawStolenItem(PoseStack poseStack, FroggydudeEntity froggy, ItemStack stack,
                                       MultiBufferSource buffers, int light, float size) {
        boolean gun = StolenItemFx.standingGun(stack, froggy.level());
        float s = gun ? size * 0.5F : size;
        poseStack.pushPose();
        poseStack.scale(s, s, s);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, gun ? ItemDisplayContext.GUI : ItemDisplayContext.GROUND,
                light, OverlayTexture.NO_OVERLAY, poseStack, buffers, froggy.level(), froggy.getId());
        poseStack.popPose();
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
     * o braço ATRAVESSADO na boca, de um lado ao outro do rosto, como a perna da zebra
     * no vídeo (cachorro com osso; só mexe junto com a cabeça dele), que encurta a cada
     * engolida até sumir; ou a cabeça presa pelo pescoço, que no fim desce goela
     * abaixo. Usa as partes do modelo de jogador do próprio jogo, então braço
     * fino/normal e a segunda camada saem certos.
     */
    private boolean drawFinisherPart(PoseStack ps, FroggydudeEntity froggy, ClientFinisher.Entry e, int held,
                                     float partial, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        if (mc.level.getEntity(e.victimId) instanceof AbstractVillager villager) {
            return drawVillagerPart(ps, froggy, e, held, partial, buffers, light, villager);
        }
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
        float eaten = Finisher.eaten(t, held);
        // engolindo: firme na boca, quase não balança
        float amp = !head ? 1F : t < Finisher.RIP_H + 12 ? 0.25F : t >= Finisher.GULPS_H[0][0] ? 0.4F : 1F;
        float w = wob * Mth.HALF_PI;
        // daqui pra frente: espaço do modelo de jogador (y pra baixo, frente em -z)
        ps.scale(-1F, -1F, 1F);
        if (head) {
            // topo da cabeça pra frente, o pescoço dentro da boca, presa firme: vira um pouco
            // na boca a cada chicotada (quem chicoteia é a cabeça dele) e no fim desce goela abaixo
            ps.mulPose(Axis.XP.rotationDegrees(90F + Mth.sin(w + 1F) * 12F * amp));
            ps.mulPose(Axis.YP.rotationDegrees(180F + Mth.sin(w + 0.6F) * 25F * amp));
            swallow(ps, eaten, 3F);
            ps.translate(0F, 1.5F / 16F, 0F);
            renderPart(model.head, ps, vc, light);
            renderPart(model.hat, ps, vc, light);
            Stumps.box(ps, buffers, light, -3.3F, 3.3F, -0.9F, 0.5F, -3.3F, 3.3F);
        } else {
            boolean left = held == 1;
            boolean slim = "slim".equals(victim.getModelName());
            float cx = (left ? 1F : -1F) * (slim ? 0.5F : 1F);
            // atravessado na boca (o comprimento de lado a lado do rosto), o ombro arrancado
            // pro lado de onde ele veio; encurta pelas duas pontas a cada engolida
            ps.mulPose(Axis.ZP.rotationDegrees(left ? 90F : -90F));
            ps.mulPose(Axis.XP.rotationDegrees(8F));
            float k = Math.max(0.02F, 1F - eaten);
            ps.scale(0.9F + 0.1F * k, k, 0.9F + 0.1F * k);
            ps.translate(-cx / 16F, -4F / 16F, 0F);
            renderPart(left ? model.leftArm : model.rightArm, ps, vc, light);
            renderPart(left ? model.leftSleeve : model.rightSleeve, ps, vc, light);
            Stumps.box(ps, buffers, light, cx - 2.3F, cx + 2.3F, -2.6F, -1.4F, -2.3F, 2.3F);
        }
        return true;
    }

    /**
     * Descendo goela abaixo: recua a cabeça pra dentro da boca ({@code depth} px, ao
     * longo do pescoço) e encolhe inteira, não só no comprimento (o comprimento aponta
     * pra câmera do close, que assim não via diminuir; na zebra ela fica visivelmente
     * menor a cada engolida).
     */
    private static void swallow(PoseStack ps, float eaten, float depth) {
        if (eaten <= 0F) return;
        // a cabeça sai do pescoço pra -y (o braço sai do ombro pra +y): pra dentro da boca é +y.
        // (com -y ela era empurrada pra FORA e flutuava solta em cima dele na engolida)
        ps.translate(0F, depth * eaten / 16F, 0F);
        float k = Math.max(0.02F, 1F - eaten);
        float side = 0.25F + 0.75F * k;
        ps.scale(side, k, side);
    }

    /**
     * O pedaço do aldeão na boca dele (a comida favorita, mesma sequência): os braços
     * cruzados (uma peça só, presa pelo meio) ou a cabeça com o chapéu da profissão,
     * com as mesmas camadas que o jogo pinta nele (base, bioma, profissão).
     */
    private boolean drawVillagerPart(PoseStack ps, FroggydudeEntity froggy, ClientFinisher.Entry e, int held,
                                     float partial, MultiBufferSource buffers, int light, AbstractVillager villager) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getEntityRenderDispatcher().getRenderer(villager) instanceof LivingEntityRenderer<?, ?> lr)
                || !(lr.getModel() instanceof VillagerModel<?> model)) return false;
        List<ResourceLocation> textures = villagerTextures(villager);
        float wob = froggy.tickCount + partial;
        float inv = 1F / FroggydudeEntity.MODEL_SCALE;
        ps.translate(0F, MOUTH_Y / 16F, MOUTH_Z / 16F);
        ps.scale(inv, inv, inv);
        float t = e.t(partial);
        boolean head = held == 3;
        float eaten = Finisher.eaten(t, held);
        // engolindo: firme na boca, quase não balança
        float amp = !head ? 1F : t < Finisher.RIP_H + 12 ? 0.25F : t >= Finisher.GULPS_H[0][0] ? 0.4F : 1F;
        float w = wob * Mth.HALF_PI;
        ps.scale(-1F, -1F, 1F);
        if (head) {
            ps.mulPose(Axis.XP.rotationDegrees(90F + Mth.sin(w + 1F) * 12F * amp));
            ps.mulPose(Axis.YP.rotationDegrees(180F + Mth.sin(w + 0.6F) * 25F * amp));
            swallow(ps, eaten, 3F);
            ps.translate(0F, 1.5F / 16F, 0F);
            for (ResourceLocation tex : textures) {
                renderPart(model.getHead(), ps, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light);
            }
            Stumps.box(ps, buffers, light, -3.6F, 3.6F, -0.9F, 0.5F, -3.6F, 3.6F);
        } else {
            // presos firmes pela barra do meio (y 2..6 da peça), atravessados na boca; vão
            // encolhendo pra dentro dela a cada engolida
            ps.mulPose(Axis.XP.rotationDegrees(-10F));
            if (eaten > 0F) {
                float k = Math.max(0.02F, 1F - eaten);
                ps.scale(k, k, k);
            }
            ps.translate(0F, -4F / 16F, 0F);
            ModelPart arms = model.root().getChild("arms");
            for (ResourceLocation tex : textures) {
                renderPart(arms, ps, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light);
            }
            // os ombros arrancados nas pontas de cima
            Stumps.box(ps, buffers, light, -8.3F, -3.7F, -2.6F, -1.4F, -2.3F, 2.3F);
            Stumps.box(ps, buffers, light, 3.7F, 8.3F, -2.6F, -1.4F, -2.3F, 2.3F);
        }
        return true;
    }

    /** As camadas do aldeão: corpo, roupa do bioma, roupa da profissão (o mascate tem uma só). */
    private static List<ResourceLocation> villagerTextures(AbstractVillager v) {
        if (v instanceof WanderingTrader) return List.of(new ResourceLocation("textures/entity/wandering_trader.png"));
        List<ResourceLocation> out = new ArrayList<>();
        out.add(new ResourceLocation("textures/entity/villager/villager.png"));
        if (v instanceof VillagerDataHolder holder) {
            VillagerData d = holder.getVillagerData();
            out.add(villagerLayer("type", BuiltInRegistries.VILLAGER_TYPE.getKey(d.getType())));
            if (d.getProfession() != VillagerProfession.NONE) {
                out.add(villagerLayer("profession", BuiltInRegistries.VILLAGER_PROFESSION.getKey(d.getProfession())));
            }
        }
        return out;
    }

    private static ResourceLocation villagerLayer(String kind, ResourceLocation key) {
        return new ResourceLocation(key.getNamespace(), "textures/entity/villager/" + kind + "/" + key.getPath() + ".png");
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
