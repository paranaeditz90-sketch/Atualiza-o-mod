package com.becomehim.client;

import net.minecraft.world.phys.Vec3;
import com.becomehim.LmTongue;
import com.becomehim.LmAttack;
import com.becomehim.BecomeHim;
import com.becomehim.LmMode;
import com.becomehim.LmModes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationProcessor;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * O modelo do FroggyDude (o mesmo do Froggydude). Ele usa o mapa de skin de jogador
 * (64x64, com as camadas de cima), então a skin de qualquer jogador cai certinho nele.
 */
public class LmModel extends GeoModel<LmAnimatable> {

    private static final ResourceLocation MODEL = BecomeHim.id("geo/lm.geo.json");
    private static final ResourceLocation ANIMATIONS = BecomeHim.id("animations/lm.animation.json");

    @Override
    public ResourceLocation getModelResource(LmAnimatable animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LmAnimatable animatable) {
        AbstractClientPlayer p = LmRenderer.rendering;
        return p != null ? p.getSkinTextureLocation() : DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    public ResourceLocation getAnimationResource(LmAnimatable animatable) {
        return ANIMATIONS;
    }

    @Override
    public RenderType getRenderType(LmAnimatable animatable, ResourceLocation texture) {
        // igual ao jogador do vanilla (a camada de cima da skin pode ter pixel meio transparente)
        return RenderType.entityTranslucent(texture);
    }

    /**
     * O relógio da animação de cada jogador. O do GeckoLib, pra quem não é entidade (o nosso
     * FroggyDude é um só pra todos), anda de tick em tick (fica travado a 20 quadros) e é um
     * relógio só pro modelo inteiro: com dois jogadores na tela um empurrava o tempo do
     * outro. Aqui cada jogador anda no próprio tempo, liso, com o pedaço de tick do quadro.
     * O pedaço de tick é o do desenho (congela na pausa); o do timer (getFrameTime) continua
     * girando de 0 a 1 com o jogo pausado e a corrida seguia atrás do menu (bug do LM).
     */
    @Override
    public void handleAnimations(LmAnimatable animatable, long instanceId, AnimationState<LmAnimatable> state) {
        Minecraft mc = Minecraft.getInstance();
        AnimatableManager<LmAnimatable> manager = animatable.getAnimatableInstanceCache().getManagerForId(instanceId);
        Entity e = state.getData(DataTickets.ENTITY);
        double now = (e != null ? e.tickCount : 0) + state.getPartialTick();
        if (manager.getFirstTickTime() == -1D) manager.startedAt(now);
        double t = now - manager.getFirstTickTime();
        if (!mc.isPaused()) manager.updatedAt(t);
        state.animationTick = t;
        AnimationProcessor<LmAnimatable> processor = getAnimationProcessor();
        processor.preAnimationSetup(animatable, t);
        if (!processor.getRegisteredBones().isEmpty()) {
            processor.tickAnimation(animatable, this, manager, t, state, crashIfBoneMissing());
        }
        setCustomAnimations(animatable, instanceId, state);
    }

    @Override
    public void setCustomAnimations(LmAnimatable animatable, long instanceId, AnimationState<LmAnimatable> state) {
        Entity e = state.getData(DataTickets.ENTITY);
        if (!(e instanceof AbstractClientPlayer p)) return;
        LmMode mode = LmModes.get(p);
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        // a cabeça segue o olhar só em pé (de quatro a animação já aponta a cabeça pra frente;
        // somar o olhar ali tombava a cabeça - o bug dos "tiques" do Froggydude)
        // no meio de um golpe, deitado ou se transformando: só a animação manda
        boolean busy = LmActsClient.busy(p) || LmKnockClient.lying(p) || LmTransformClient.busy(p);
        if (head != null && data != null && mode == LmMode.STAND && p.onGround() && !busy) {
            head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -50F, 50F) * Mth.DEG_TO_RAD);
            head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -40F, 45F) * Mth.DEG_TO_RAD);
            // sem isso o GeckoLib não volta a cabeça pra pose e o olhar ia somando a cada quadro
            head.resetStateChanges();
        }
        if (mode == LmMode.STAND && !busy) playerMoves(p, state);
        tongue(p, state);
    }

    /**
     * (Become HIM...) A língua: segurando, a cabeça olha pra vítima lá em cima (até 50°, como o mod da IA); no
     * arremesso, a cintura gira por cima do corpo (o impulso e o chicote, LmTongue.waistTwist).
     */
    private void tongue(AbstractClientPlayer p, AnimationState<LmAnimatable> state) {
        LmActsClient.Act a = LmActsClient.get(p);
        if (a == null || (a.kind != LmAttack.GRAB && a.kind != LmAttack.THROW)) return;
        float pt = state.getPartialTick();
        if (a.kind == LmAttack.THROW) {
            CoreGeoBone waist = getAnimationProcessor().getBone("waist");
            if (waist == null) return;
            waist.setRotY(waist.getRotY() + LmTongue.waistTwist(a.age + pt, a.sweep) * Mth.DEG_TO_RAD);
            waist.resetStateChanges();
            return;
        }
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        Entity v = p.level().getEntity(a.target);
        if (head == null || v == null) return;
        Vec3 eye = p.getPosition(pt).add(0, p.getBbHeight() * 0.85D, 0);
        Vec3 chest = v.getPosition(pt).add(0, v.getBbHeight() * 0.65D, 0);
        double dy = chest.y - eye.y;
        double dh = Math.sqrt((chest.x - eye.x) * (chest.x - eye.x) + (chest.z - eye.z) * (chest.z - eye.z));
        float up = (float) Mth.clamp(Mth.atan2(dy, Math.max(dh, 0.3D)) * Mth.RAD_TO_DEG, -30D, 50D);
        // vai subindo junto com a língua (a pegada é em 0,3 s)
        float k = Mth.clamp((a.age + pt) / LmTongue.HIT, 0F, 1F);
        // (no teste com "-" a cabeça baixava: neste modelo rotX + levanta a cara)
        head.setRotX(head.getRotX() + up * k * Mth.DEG_TO_RAD);
        head.resetStateChanges();
    }

    /** Em pé: o agachar e o soco/pôr bloco por cima da animação, como o jogador. */
    private void playerMoves(AbstractClientPlayer p, AnimationState<LmAnimatable> state) {
        boolean crouch = p.isCrouching();
        float swing = p.getAttackAnim(state.getPartialTick());
        if (!crouch && swing <= 0F) return;
        CoreGeoBone root = getAnimationProcessor().getBone("root");
        CoreGeoBone waist = getAnimationProcessor().getBone("waist");
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        CoreGeoBone right = getAnimationProcessor().getBone("right_arm");
        CoreGeoBone left = getAnimationProcessor().getBone("left_arm");
        if (root == null || waist == null || head == null || right == null || left == null) return;
        if (crouch) {
            waist.setRotX(waist.getRotX() + 0.5F);
            head.setRotX(head.getRotX() - 0.5F);
            root.setPosY(root.getPosY() - 3.5F);
            right.setRotX(right.getRotX() + 0.4F);
            left.setRotX(left.getRotX() + 0.4F);
        }
        if (swing > 0F) {
            float s = Mth.sin(swing * Mth.PI);
            right.setRotX(right.getRotX() - s * 1.5F - Mth.sin(Mth.sqrt(swing) * Mth.PI) * 0.4F);
            right.setRotY(right.getRotY() + Mth.sin(Mth.sqrt(swing) * Mth.TWO_PI) * 0.2F);
        }
        root.resetStateChanges();
        waist.resetStateChanges();
        head.resetStateChanges();
        right.resetStateChanges();
        left.resetStateChanges();
    }
}
