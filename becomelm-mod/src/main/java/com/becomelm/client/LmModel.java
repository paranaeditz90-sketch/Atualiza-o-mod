package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmMode;
import com.becomelm.LmModes;
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

    private static final ResourceLocation MODEL = BecomeLM.id("geo/lm.geo.json");
    private static final ResourceLocation ANIMATIONS = BecomeLM.id("animations/lm.animation.json");

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
     */
    @Override
    public void handleAnimations(LmAnimatable animatable, long instanceId, AnimationState<LmAnimatable> state) {
        Minecraft mc = Minecraft.getInstance();
        AnimatableManager<LmAnimatable> manager = animatable.getAnimatableInstanceCache().getManagerForId(instanceId);
        Entity e = state.getData(DataTickets.ENTITY);
        double now = (e != null ? e.tickCount : 0) + mc.getFrameTime();
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
        if (head != null && data != null && mode == LmMode.STAND && p.onGround()) {
            head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -50F, 50F) * Mth.DEG_TO_RAD);
            head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -40F, 45F) * Mth.DEG_TO_RAD);
            // sem isso o GeckoLib não volta a cabeça pra pose e o olhar ia somando a cada quadro
            head.resetStateChanges();
        }
        if (mode == LmMode.STAND) playerMoves(p, state);
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
