package com.froggydude.client;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.HashMap;
import java.util.Map;

/**
 * Liga a entidade aos arquivos do GeckoLib.
 *
 * Textura: froggydude_<tipo>_<estágio>.png, onde tipo é normal / burned /
 * fed / smashed (ver SkinVariant) e estágio vai de 0 a 3 (sangue pela vida).
 * Se um arquivo não existe, cai no estágio anterior do mesmo tipo e, por
 * último, no tipo "normal" - então dá pra ir adicionando skins aos poucos.
 *
 * Língua: as animações esticam o osso "tongue" de 0 até 100 (100 = 100%). Aqui
 * o valor vira o comprimento real (entidade.getTongueLength(), em blocos), pra
 * língua sempre chegar até o alvo.
 */
public class FroggydudeModel extends GeoModel<FroggydudeEntity> {

    private static final ResourceLocation MODEL =
            new ResourceLocation(ModEntityTypes.MOD_ID, "geo/froggydude.geo.json");
    private static final ResourceLocation ANIMATIONS =
            new ResourceLocation(ModEntityTypes.MOD_ID, "animations/froggydude.animation.json");
    /** Só existe na máquina de teste (poses congeladas "dbg_..."); não vai no mod. */
    private static final ResourceLocation DEBUG_ANIMATIONS =
            new ResourceLocation(ModEntityTypes.MOD_ID, "animations/froggydude_debug.animation.json");

    private static final float TONGUE_ANIM_PEAK = 100F;
    private static final float RENDER_SCALE = 1.15F; // igual ao withScale do renderer

    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    @Override
    public ResourceLocation getModelResource(FroggydudeEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FroggydudeEntity animatable) {
        int stage = Math.max(0, Math.min(3, animatable.getGoreStage()));
        return resolve(animatable.getSkinVariant().id, stage);
    }

    @Override
    public ResourceLocation getAnimationResource(FroggydudeEntity animatable) {
        return animatable.getDebugAnim().startsWith("dbg_") ? DEBUG_ANIMATIONS : ANIMATIONS;
    }

    @Override
    public void setCustomAnimations(FroggydudeEntity animatable, long instanceId,
                                    AnimationState<FroggydudeEntity> animationState) {
        // a cabeça encara o alvo: soma o olhar à pose da animação
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && data != null && !animatable.getDebugAnim().startsWith("dbg_")) {
            // montado, a pose já aponta a cara pro rosto da vítima: somar o olhar
            // de novo faria ele olhar pro chão (e a vítima só veria a coroa)
            if (animatable.getFroggyState() != FroggyState.PIN_HOLD) {
                head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
            }
            head.setRotY(head.getRotY() + data.netHeadYaw() * Mth.DEG_TO_RAD);
        }

        CoreGeoBone tongue = getAnimationProcessor().getBone("tongue");
        if (tongue == null) return;
        float fraction = tongue.getScaleZ() / TONGUE_ANIM_PEAK;
        float lengthPx = animatable.getTongueLength() * 16F / RENDER_SCALE;
        tongue.setScaleZ(fraction * lengthPx);
    }

    private static ResourceLocation resolve(String variant, int stage) {
        for (int s = stage; s >= 0; s--) {
            ResourceLocation rl = texture(variant, s);
            if (exists(rl)) return rl;
        }
        if (!"normal".equals(variant)) {
            return resolve("normal", stage);
        }
        return texture("normal", 0);
    }

    private static ResourceLocation texture(String variant, int stage) {
        return new ResourceLocation(ModEntityTypes.MOD_ID,
                "textures/entity/froggydude_" + variant + "_" + stage + ".png");
    }

    private static boolean exists(ResourceLocation rl) {
        return EXISTS.computeIfAbsent(rl,
                k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
    }
}
