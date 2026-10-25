package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmAttack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A cara suja (10v1 0:26.9-29.1, How many days 0:48 e 0:58): depois de ficar no monte de
 * pedaços, a cara do FroggyDude fica TODA vermelha de sangue e continua assim um bom tempo.
 * As mordidas sujam a boca aos poucos. Cada um que vê conta o sangue de cada jogador pelo
 * que vê ele fazendo (não precisa mandar pela rede).
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmBloodFace {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BecomeLM.MOD_ID, "textures/entity/blood_face.png");
    /** No monte: suja toda em ~6 s. */
    private static final float PILE_RATE = 1F / 120F;
    /** Cada mordida: um tanto na boca. */
    private static final float BITE = 0.12F;
    /** Fica suja 1,5 min sem sujar de novo, depois vai limpando em ~40 s. */
    private static final int KEEP = 1800;
    private static final float FADE = 1F / 800F;

    private static final class Dirt {
        float amount;
        int since;
    }

    private static final Map<UUID, Dirt> DIRT = new HashMap<>();

    private LmBloodFace() {
    }

    static float amount(Player p) {
        Dirt d = DIRT.get(p.getUUID());
        return d == null ? 0F : d.amount;
    }

    /** Uma mordida (ou a sacudida) suja a boca. */
    static void bite(Player p) {
        add(p, BITE);
    }

    private static void add(Player p, float k) {
        Dirt d = DIRT.computeIfAbsent(p.getUUID(), u -> new Dirt());
        d.amount = Math.min(1F, d.amount + k);
        d.since = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            DIRT.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (Player p : mc.level.players()) {
            LmActsClient.Act a = LmActsClient.get(p);
            if (a != null && a.kind == LmAttack.PILE) add(p, PILE_RATE);
        }
        DIRT.values().removeIf(d -> {
            if (++d.since > KEEP) d.amount -= FADE;
            return d.amount <= 0F;
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DIRT.clear();
    }

    /** Desenha o sangue por cima da skin (mesmo modelo, a textura de sangue com a transparência da sujeira). */
    static final class Layer extends GeoRenderLayer<LmAnimatable> {

        Layer(GeoRenderer<LmAnimatable> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, LmAnimatable animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay) {
            AbstractClientPlayer p = LmRenderer.rendering;
            float a = p == null ? 0F : amount(p);
            if (a < 0.02F) return;
            RenderType blood = RenderType.entityTranslucent(TEXTURE);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, blood, bufferSource.getBuffer(blood),
                    partialTick, packedLight, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, Math.min(1F, a));
        }
    }
}
