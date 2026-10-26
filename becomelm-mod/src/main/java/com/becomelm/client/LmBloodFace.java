package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmAttack;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;
import java.io.InputStream;
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
    /** Textura branca: a cor de cada pingo vem do vértice (igual o sangue do mod Froggydude). */
    private static final ResourceLocation WHITE = new ResourceLocation(BecomeLM.MOD_ID, "textures/misc/white.png");
    private static final int N = 64;
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

    /** As cores do sangue (ARGB, o desenho fica na região do chapéu da skin), lidas uma vez. */
    @Nullable
    private static int[] blood;
    private static boolean bloodRead;

    @Nullable
    private static int[] blood() {
        if (bloodRead) return blood;
        bloodRead = true;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(TEXTURE);
             NativeImage img = NativeImage.read(in)) {
            if (img.getWidth() != N || img.getHeight() != N) return null;
            int[] out = new int[N * N];
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    out[y * N + x] = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
                }
            }
            blood = out;
        } catch (Exception e) {
            blood = null;
        }
        return blood;
    }

    /**
     * Desenha o sangue colado na cara. Antes era o mesmo modelo com a textura de sangue, que só
     * tinha desenho no chapéu: com o chapéu da skin vazio, o sangue boiava meio pixel pra fora
     * (a "casca" em volta da cabeça). Agora cada pixel sujo vira um quadradinho: no chapéu onde a
     * skin tem pixel ali, no rosto (a cabeça de dentro) no resto.
     */
    static final class Layer extends GeoRenderLayer<LmAnimatable> {

        private final FaceDecals decals = new FaceDecals();

        Layer(GeoRenderer<LmAnimatable> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, LmAnimatable animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay) {
            AbstractClientPlayer p = LmRenderer.rendering;
            float a = p == null ? 0F : amount(p);
            int[] colors = blood();
            if (a < 0.02F || colors == null) return;
            RenderType white = RenderType.entityTranslucent(WHITE);
            decals.begin(bufferSource.getBuffer(white), colors, Math.min(1F, a), LmSkinAlpha.of(p), packedLight);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, white, decals, partialTick,
                    packedLight, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        }
    }

    /** Recebe as faces do modelo (posição já na pose, UV de 0 a 1 no layout da skin) e cola os pingos. */
    private static final class FaceDecals implements VertexConsumer {
        /** Um fio pra fora da face, pra não brigar com o pixel da skin no mesmo lugar. */
        private static final float LIFT = 0.004F;
        private final float[] x = new float[4], y = new float[4], z = new float[4], u = new float[4], v = new float[4];
        private float nx, ny, nz;
        private int n;
        private VertexConsumer out;
        private int[] colors;
        private float amount;
        @Nullable
        private boolean[] opaque;
        private int light;

        void begin(VertexConsumer out, int[] colors, float amount, @Nullable boolean[] opaque, int light) {
            this.out = out;
            this.colors = colors;
            this.amount = amount;
            this.opaque = opaque;
            this.light = light;
            this.n = 0;
        }

        /** O sangue deste pixel da face (0 = nada): a cabeça de dentro pega o do chapéu em cima dela. */
        private int color(int tx, int ty) {
            if (ty < 0 || ty >= 16 || tx < 0 || tx >= N) return 0;
            int hat;
            if (tx < 32) {
                hat = ty * N + tx + 32;
                if (opaque != null && opaque[hat]) return 0;
            } else {
                hat = ty * N + tx;
                if (opaque == null || !opaque[hat]) return 0;
            }
            int c = colors[hat];
            int alpha = (int) ((c >>> 24) * amount);
            return alpha < 3 ? 0 : (alpha << 24) | (c & 0xFFFFFF);
        }

        @Override
        public void vertex(float px, float py, float pz, float r, float g, float b, float a, float tu, float tv,
                           int ov, int li, float qx, float qy, float qz) {
            x[n] = px;
            y[n] = py;
            z[n] = pz;
            u[n] = tu;
            v[n] = tv;
            nx = qx;
            ny = qy;
            nz = qz;
            if (++n == 4) {
                n = 0;
                face();
            }
        }

        private void face() {
            float u0 = Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3])), u1 = Math.max(Math.max(u[0], u[1]), Math.max(u[2], u[3]));
            float v0 = Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3])), v1 = Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3]));
            // só a cabeça (e o chapéu) estão na faixa de cima da skin
            if (v0 >= 16F / N) return;
            int a = corner(u0, v0), b = corner(u1, v0), c = corner(u1, v1), d = corner(u0, v1);
            if (a < 0 || b < 0 || c < 0 || d < 0) return;
            int tx0 = Math.round(u0 * N), tx1 = Math.round(u1 * N), ty0 = Math.round(v0 * N), ty1 = Math.round(v1 * N);
            if (tx1 <= tx0 || ty1 <= ty0) return;
            float w = tx1 - tx0, h = ty1 - ty0;
            for (int ty = ty0; ty < ty1; ty++) {
                for (int tx = tx0; tx < tx1; tx++) {
                    int argb = color(tx, ty);
                    if (argb == 0) continue;
                    float s0 = (tx - tx0) / w, s1 = (tx + 1 - tx0) / w, t0 = (ty - ty0) / h, t1 = (ty + 1 - ty0) / h;
                    put(a, b, c, d, s0, t0, argb);
                    put(a, b, c, d, s1, t0, argb);
                    put(a, b, c, d, s1, t1, argb);
                    put(a, b, c, d, s0, t1, argb);
                }
            }
        }

        /** Qual dos 4 cantos tem esse UV (o espelhado também acha certo). */
        private int corner(float cu, float cv) {
            for (int i = 0; i < 4; i++) {
                if (Math.abs(u[i] - cu) < 1.0E-4F && Math.abs(v[i] - cv) < 1.0E-4F) return i;
            }
            return -1;
        }

        private void put(int a, int b, int c, int d, float s, float t, int argb) {
            float px = Mth.lerp(t, Mth.lerp(s, x[a], x[b]), Mth.lerp(s, x[d], x[c])) + nx * LIFT;
            float py = Mth.lerp(t, Mth.lerp(s, y[a], y[b]), Mth.lerp(s, y[d], y[c])) + ny * LIFT;
            float pz = Mth.lerp(t, Mth.lerp(s, z[a], z[b]), Mth.lerp(s, z[d], z[c])) + nz * LIFT;
            out.vertex(px, py, pz, ((argb >> 16) & 255) / 255F, ((argb >> 8) & 255) / 255F, (argb & 255) / 255F,
                    (argb >>> 24) / 255F, 0.5F, 0.5F, OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
        }

        // o resto do VertexConsumer: o GeckoLib só usa o vertex completo acima
        @Override
        public VertexConsumer vertex(double px, double py, double pz) {
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float tu, float tv) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int ou, int ov) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int lu, int lv) {
            return this;
        }

        @Override
        public VertexConsumer normal(float qx, float qy, float qz) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
