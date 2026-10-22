package com.froggydude.client.gore;

import com.froggydude.client.ClientBloodFx;
import com.froggydude.config.FroggyClientConfig;
import com.froggydude.config.GoreLevel;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModParticles;
import com.froggydude.network.ClientArmState;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Sangue grudado na skin dos jogadores (LM: "espirrando ... na skin do
 * player"). Cada jogador sujo ganha uma textura 64x64 viva, pintada nas
 * regiões da CAMADA DE FORA da skin (chapéu, jaqueta, mangas, calça) - assim
 * aparece por cima de qualquer skin. A camada BloodStainLayer redesenha o
 * modelo com essa textura; em primeira pessoa a manga também fica suja.
 *
 * Bateu de perto num bicho: suja a frente (cara, peito, o braço da arma).
 * Levou um golpe: suja onde levou. Sai devagar com o tempo, rápido na chuva e
 * na hora na água - menos no Extremo, que só sai na água ou na chuva (e a água
 * em volta fica vermelha). O teu fica salvo no Extremo (saiu do mundo e voltou,
 * continua sujo); morreu, corpo novo, limpo.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BloodStains {

    public enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    public enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    private static final int N = 64;
    private static final Map<UUID, Stain> STAINS = new HashMap<>();
    private static final RandomSource R = RandomSource.create();

    private static final class Stain {
        final UUID id;
        final boolean slim;
        final float[] amt = new float[N * N];
        final int[] col = new int[N * N];
        /** Quanto ainda está molhado (1 = acabou de cair): molhado escorre; seco escurece. */
        final float[] wet = new float[N * N];
        int dripCooldown;
        NativeImage img;
        DynamicTexture tex;
        ResourceLocation loc;
        boolean dirty;
        float total;
        int lastColor = 0x8E0A0A;
        int dead;

        Stain(UUID id, boolean slim) {
            this.id = id;
            this.slim = slim;
        }

        /**
         * Cria a textura. Só fora do desenho (tick do cliente): criar/enviar textura no
         * meio do desenho das entidades mexia na textura presa da unidade ativa no
         * celular (v0.5.1). A unidade 0 é a das texturas de entidade; a 1 e a 2 são
         * a do brilho de dano e a da luz - se a ativa fosse uma delas, ia o sangue nela.
         */
        void ensureTexture() {
            if (tex != null) return;
            int unit = com.mojang.blaze3d.platform.GlStateManager._getActiveTexture();
            com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            img = new NativeImage(N, N, true);
            img.fillRect(0, 0, N, N, 0);
            tex = new DynamicTexture(img);
            loc = Minecraft.getInstance().getTextureManager().register("froggydude_stain", tex);
            com.mojang.blaze3d.systems.RenderSystem.activeTexture(unit);
        }

        /** Pronta pra desenhar (o desenho nunca cria nem envia textura). */
        boolean ready() {
            return tex != null && loc != null;
        }

        void add(int x, int y, float a, int color) {
            if (x < 0 || y < 0 || x >= N || y >= N || a <= 0F) return;
            int i = y * N + x;
            float old = amt[i];
            float now = Math.min(1F, old + a);
            if (now <= old) return;
            // cor: mistura com o que já tinha (sangue de dois bichos vira um tom no meio)
            col[i] = old <= 0.01F ? color : mix(col[i], color, a / now);
            amt[i] = now;
            wet[i] = Math.min(1F, wet[i] + a * 1.5F);
            total += now - old;
            dirty = true;
            lastColor = color;
        }

        void upload() {
            if (!dirty) return;
            ensureTexture();
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    int i = y * N + x;
                    float a = amt[i];
                    if (a < 0.06F) {
                        img.setPixelRGBA(x, y, 0);
                        continue;
                    }
                    int c = col[i];
                    // seco por fora, molhado no meio: quanto mais sangue, mais escuro e opaco
                    float shade = 0.75F + 0.35F * (1F - a) + ((x * 31 + y * 17) % 7) * 0.025F;
                    // fresco é vermelho vivo e brilha; secando vai escurecendo (quase marrom)
                    shade *= 0.6F + 0.45F * wet[i];
                    int r = Mth.clamp((int) (((c >> 16) & 255) * shade), 0, 255);
                    int g = Mth.clamp((int) (((c >> 8) & 255) * shade), 0, 255);
                    int b = Mth.clamp((int) ((c & 255) * shade), 0, 255);
                    int alpha = Mth.clamp((int) (90 + a * 165), 0, 255);
                    img.setPixelRGBA(x, y, (alpha << 24) | (b << 16) | (g << 8) | r);
                }
            }
            int unit = com.mojang.blaze3d.platform.GlStateManager._getActiveTexture();
            com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            tex.upload();
            com.mojang.blaze3d.systems.RenderSystem.activeTexture(unit);
            dirty = false;
        }

        void close() {
            if (tex != null) {
                Minecraft.getInstance().getTextureManager().release(loc);
                tex = null;
                img = null;
            }
        }
    }

    private BloodStains() {
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static GoreLevel gore() {
        return FroggyClientConfig.gore();
    }

    private static boolean slim(Player p) {
        return p instanceof AbstractClientPlayer acp && "slim".equals(acp.getModelName());
    }

    /** A textura de sangue grudado de um jogador (pro cadáver), ou null se ele está limpo. */
    @javax.annotation.Nullable
    public static ResourceLocation textureFor(UUID id) {
        Stain s = STAINS.get(id);
        if (s == null || s.total < 0.5F || !gore().skinStains() || !s.ready()) return null;
        return s.loc;
    }

    private static Stain stain(Player p) {
        return STAINS.computeIfAbsent(p.getUUID(), id -> new Stain(id, slim(p)));
    }

    // ---------------------------------------------------------------- onde fica cada parte na skin

    /** Retângulo (u, v, largura, altura) de uma face da camada de fora. */
    static int[] rect(Part part, Face face, boolean slim) {
        int u, v, w, h, d;
        switch (part) {
            case HEAD -> { u = 32; v = 0; w = 8; h = 8; d = 8; }
            case BODY -> { u = 16; v = 32; w = 8; h = 12; d = 4; }
            case RIGHT_ARM -> { u = 40; v = 32; w = slim ? 3 : 4; h = 12; d = 4; }
            case LEFT_ARM -> { u = 48; v = 48; w = slim ? 3 : 4; h = 12; d = 4; }
            case RIGHT_LEG -> { u = 0; v = 32; w = 4; h = 12; d = 4; }
            default -> { u = 0; v = 48; w = 4; h = 12; d = 4; }
        }
        return switch (face) {
            case TOP -> new int[]{u + d, v, w, d};
            case BOTTOM -> new int[]{u + d + w, v, w, d};
            case RIGHT -> new int[]{u, v + d, d, h};
            case FRONT -> new int[]{u + d, v + d, w, h};
            case LEFT -> new int[]{u + d + w, v + d, d, h};
            default -> new int[]{u + 2 * d + w, v + d, w, h};
        };
    }

    /**
     * Uma mancha: borrão irregular num ponto da face (fu, fv de 0 a 1), com
     * escorrido pra baixo nas faces de lado (o sangue desce pelo corpo).
     */
    static void blot(Stain s, Part part, Face face, float fu, float fv, float radius, float amount, int color) {
        int[] r = rect(part, face, s.slim);
        float cx = r[0] + fu * r[2], cy = r[1] + fv * r[3];
        int rad = (int) Math.ceil(radius) + 1;
        for (int y = (int) cy - rad; y <= (int) cy + rad; y++) {
            for (int x = (int) cx - rad; x <= (int) cx + rad; x++) {
                if (x < r[0] || x >= r[0] + r[2] || y < r[1] || y >= r[1] + r[3]) continue;
                float dd = Mth.sqrt((x + 0.5F - cx) * (x + 0.5F - cx) + (y + 0.5F - cy) * (y + 0.5F - cy));
                float edge = radius * (0.75F + R.nextFloat() * 0.5F);
                if (dd > edge) continue;
                s.add(x, y, amount * (1F - 0.5F * dd / Math.max(edge, 0.01F)), color);
            }
        }
        boolean side = face != Face.TOP && face != Face.BOTTOM;
        if (side && R.nextFloat() < 0.65F) {
            int len = 1 + R.nextInt(Math.max(1, (int) (r[3] * 0.7F)));
            int x = Mth.clamp((int) cx + R.nextInt(3) - 1, r[0], r[0] + r[2] - 1);
            for (int k = 1; k <= len; k++) {
                int y = (int) cy + k;
                if (y >= r[1] + r[3]) break;
                s.add(x, y, amount * (1F - k / (float) (len + 2)), color);
            }
        }
    }

    private static void blots(Stain s, Part part, Face face, int n, float amount, int color) {
        for (int i = 0; i < n; i++) {
            blot(s, part, face, R.nextFloat(), R.nextFloat() * 0.85F, 0.8F + R.nextFloat() * 1.8F, amount, color);
        }
    }

    // ---------------------------------------------------------------- quem sujou onde

    /**
     * Bateu de perto: o sangue voltou na cara de quem bateu. Suja a frente
     * virada pra vítima: rosto, peito, o braço da arma (e um pouco o outro).
     */
    public static void splashFront(Player p, Vec3 source, float dmg, int color) {
        GoreLevel g = gore();
        if (!g.skinStains() || p.isInvisible()) return;
        Stain s = stain(p);
        Face face = faceToward(p, source);
        float amount = Mth.clamp(0.35F + dmg * 0.07F, 0.3F, 1F);
        int n = Math.max(1, Math.round((1F + dmg / 4F) * g.amount));
        boolean right = p.getMainArm() == HumanoidArm.RIGHT;
        blots(s, Part.HEAD, face, Math.max(1, n / 2), amount, color);
        blots(s, Part.BODY, face, n, amount, color);
        blots(s, right ? Part.RIGHT_ARM : Part.LEFT_ARM, face, n, amount, color);
        blots(s, right ? Part.RIGHT_ARM : Part.LEFT_ARM, Face.TOP, 1, amount * 0.7F, color);
        if (R.nextFloat() < 0.6F) blots(s, right ? Part.LEFT_ARM : Part.RIGHT_ARM, face, 1, amount * 0.7F, color);
        if (R.nextFloat() < 0.5F) blots(s, R.nextBoolean() ? Part.RIGHT_LEG : Part.LEFT_LEG, face, 1, amount * 0.6F, color);
    }

    /** Levou o golpe: suja em volta de onde acertou (pela altura e pelo lado). */
    public static void splashAt(Player p, Vec3 at, Vec3 dir, float dmg, int color) {
        GoreLevel g = gore();
        if (!g.skinStains() || p.isInvisible()) return;
        Stain s = stain(p);
        Face face = faceToward(p, at.subtract(dir.scale(2.0D)));
        double hy = at.y - p.getY();
        Vec3 local = toLocal(p, at);
        float amount = Mth.clamp(0.4F + dmg * 0.08F, 0.3F, 1F);
        int n = Math.max(1, Math.round((1F + dmg / 3F) * g.amount));
        Part part;
        if (hy > 1.45D) part = Part.HEAD;
        else if (hy > 0.72D) part = Math.abs(local.x) > 0.3D ? (local.x > 0 ? Part.RIGHT_ARM : Part.LEFT_ARM) : Part.BODY;
        else part = local.x > 0 ? Part.RIGHT_LEG : Part.LEFT_LEG;
        blots(s, part, face, n, amount, color);
        // o resto escorre pra baixo / respinga do lado
        blots(s, Part.BODY, face, Math.max(1, n / 2), amount * 0.7F, color);
    }

    /** Banho de sangue (do lado de um braço arrancado, comendo, a cutscene). */
    public static void soak(Player p, float amount, int color) {
        GoreLevel g = gore();
        if (!g.skinStains()) return;
        Stain s = stain(p);
        int n = Math.max(1, Math.round(amount * 6F * g.amount));
        for (Part part : Part.values()) {
            for (Face f : new Face[]{Face.FRONT, Face.RIGHT, Face.LEFT, Face.TOP, Face.BACK}) {
                if (R.nextFloat() < 0.75F) blots(s, part, f, n, Mth.clamp(amount, 0.2F, 1F), color);
            }
        }
    }

    /** De que lado do corpo dele fica esse ponto (frente, costas, direita, esquerda). */
    private static Face faceToward(Player p, Vec3 point) {
        Vec3 l = toLocal(p, point);
        if (Math.abs(l.z) >= Math.abs(l.x)) return l.z >= 0 ? Face.FRONT : Face.BACK;
        return l.x > 0 ? Face.RIGHT : Face.LEFT;
    }

    /** Ponto no "corpo" do jogador: x = direita, z = frente. */
    private static Vec3 toLocal(Player p, Vec3 point) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double rx = -fz, rz = fx;
        double dx = point.x - p.getX(), dz = point.z - p.getZ();
        return new Vec3(dx * rx + dz * rz, point.y - p.getY(), dx * fx + dz * fz);
    }

    public static boolean has(Player p) {
        Stain s = STAINS.get(p.getUUID());
        return s != null && s.total > 0.5F;
    }

    // ---------------------------------------------------------------- lavando / secando

    private static int partOf(int x, int y, boolean slim) {
        for (Part part : Part.values()) {
            for (Face f : Face.values()) {
                int[] r = rect(part, f, slim);
                if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) return part.ordinal();
            }
        }
        return -1;
    }

    private static final int[] PART_SLIM = new int[N * N];
    private static final int[] PART_WIDE = new int[N * N];

    static {
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                PART_SLIM[y * N + x] = partOf(x, y, true);
                PART_WIDE[y * N + x] = partOf(x, y, false);
            }
        }
    }

    /** Até que altura (do pé) a parte fica: a água acima disso lava ela. */
    private static float partHeight(int part) {
        return switch (Part.values()[part]) {
            case RIGHT_LEG, LEFT_LEG -> 0.15F;
            case BODY -> 0.8F;
            case RIGHT_ARM, LEFT_ARM -> 0.9F;
            default -> 1.45F;
        };
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STAINS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clearAll();
            return;
        }
        if (mc.isPaused()) return;
        GoreLevel g = gore();
        if (!g.skinStains()) {
            clearAll();
            return;
        }
        Iterator<Map.Entry<UUID, Stain>> it = STAINS.entrySet().iterator();
        while (it.hasNext()) {
            Stain s = it.next().getValue();
            Player p = mc.level.getPlayerByUUID(s.id);
            if (p == null) {
                // sumiu de vista (ou saiu): esquece depois de um tempo, menos o teu
                if (++s.dead > 600) {
                    s.close();
                    it.remove();
                }
                continue;
            }
            s.dead = 0;
            if (p.isDeadOrDying()) {
                // morreu: o corpo novo vem limpo
                s.close();
                it.remove();
                continue;
            }
            wash(mc, p, s, g);
            flow(mc, p, s);
            if (s.total < 0.05F) {
                s.close();
                it.remove();
                continue;
            }
            if (p.tickCount % 3 == 0) s.upload();
        }
    }

    // ---------------------------------------------------------------- escorrendo

    private static final Face[] SIDES = {Face.RIGHT, Face.FRONT, Face.LEFT, Face.BACK};

    /**
     * O sangue molhado escorre: em cada face de lado de cada parte, o pixel com
     * bastante sangue ainda molhado passa um pouco pro de baixo (às vezes desvia
     * um pixel pro lado), deixando rastro; no fim da parte pinga (a gota cai do
     * corpo). E vai secando: em ~15 s para de escorrer e fica escuro.
     */
    private static void flow(Minecraft mc, Player p, Stain s) {
        boolean any = false;
        for (int i = 0; i < N * N; i++) {
            if (s.wet[i] > 0F) {
                s.wet[i] = s.wet[i] < 0.01F ? 0F : s.wet[i] * 0.992F;
                any = true;
                if ((i & 7) == 0) s.dirty = true;
            }
        }
        if (s.dripCooldown > 0) s.dripCooldown--;
        if (!any || p.tickCount % 2 != 0) return;
        for (Part part : Part.values()) {
            for (Face face : SIDES) {
                int[] r = rect(part, face, s.slim);
                int bottom = r[1] + r[3] - 1;
                for (int y = bottom; y >= r[1]; y--) {
                    for (int x = r[0]; x < r[0] + r[2]; x++) {
                        int i = y * N + x;
                        float a = s.amt[i], w = s.wet[i];
                        if (w < 0.2F || a < 0.34F) continue;
                        float mv = Math.min(a - 0.3F, (0.05F + 0.12F * w) * a);
                        if (mv <= 0.005F) continue;
                        if (y == bottom) {
                            // pinga da ponta da parte
                            if (w > 0.45F && s.dripCooldown <= 0 && part != Part.HEAD) {
                                s.dripCooldown = 8;
                                dripFrom(p, part, s.col[i]);
                            }
                            s.amt[i] = a - mv * 0.5F;
                            s.dirty = true;
                            continue;
                        }
                        int tx = x;
                        if (R.nextFloat() < 0.06F) tx = Mth.clamp(x + (R.nextBoolean() ? 1 : -1), r[0], r[0] + r[2] - 1);
                        int ti = (y + 1) * N + tx;
                        float before = s.amt[ti];
                        float after = Math.min(1F, before + mv);
                        if (after > before) {
                            s.col[ti] = before <= 0.01F ? s.col[i] : mix(s.col[ti], s.col[i], (after - before) / after);
                            s.amt[ti] = after;
                        }
                        s.wet[ti] = Math.max(s.wet[ti], w * 0.96F);
                        s.amt[i] = a - mv;
                        s.dirty = true;
                    }
                }
            }
        }
    }

    /** Uma gota caindo do corpo (da ponta do braço, da barra da camisa, do pé). */
    private static void dripFrom(Player p, Part part, int color) {
        double h = switch (part) {
            case BODY -> 0.72D;
            case RIGHT_ARM, LEFT_ARM -> 0.75D;
            default -> 0.05D;
        };
        Vec3 side = switch (part) {
            case RIGHT_ARM -> new Vec3(0.35D, 0, 0);
            case LEFT_ARM -> new Vec3(-0.35D, 0, 0);
            case RIGHT_LEG -> new Vec3(0.12D, 0, 0);
            case LEFT_LEG -> new Vec3(-0.12D, 0, 0);
            default -> new Vec3((R.nextFloat() - 0.5F) * 0.4D, 0, 0);
        };
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double rx = -fz, rz = fx;
        com.froggydude.client.ClientBloodFx.droplets(p.getX() + rx * side.x + fx * 0.05D, p.getY() + h,
                p.getZ() + rz * side.x + fz * 0.05D, 1, color);
    }

    // ---------------------------------------------------------------- a gota que acertou

    /**
     * Uma gota de sangue (partícula) bateu no jogador: mancha EXATAMENTE onde bateu
     * (LM: "um cálculo de onde o sangue vai cair - muito sangue escorre, mas vai uma
     * gota na cara do player"). A parte sai da altura e do lado, a face de onde a gota
     * veio, o ponto na face da posição.
     */
    public static void dropHit(Player p, Vec3 at, Vec3 vel, int color, float size) {
        GoreLevel g = gore();
        if (!g.skinStains() || p.isInvisible()) return;
        Stain s = stain(p);
        Vec3 l = toLocal(p, at);
        Vec3 lv = toLocal(p, p.position().add(vel)).subtract(toLocal(p, p.position()));
        double scale = p.getBbHeight() / 1.8D;
        double hy = l.y / scale;
        Part part;
        double cx, top, height, width;
        if (hy > 1.42D) {
            part = Part.HEAD; cx = 0; top = 1.875D; height = 0.47D; width = 0.47D;
        } else if (hy > 0.70D) {
            if (Math.abs(l.x) > 0.24D) {
                part = l.x > 0 ? Part.RIGHT_ARM : Part.LEFT_ARM;
                cx = l.x > 0 ? 0.35D : -0.35D;
                width = s.slim ? 0.18D : 0.23D;
            } else {
                part = Part.BODY; cx = 0; width = 0.47D;
            }
            top = 1.41D; height = 0.70D;
        } else {
            part = l.x > 0 ? Part.RIGHT_LEG : Part.LEFT_LEG;
            cx = l.x > 0 ? 0.12D : -0.12D; top = 0.70D; height = 0.70D; width = 0.23D;
        }
        Face face;
        double horiz = Math.sqrt(lv.x * lv.x + lv.z * lv.z);
        if (-lv.y > horiz * 1.5D && hy > top - 0.12D) face = Face.TOP;
        else if (Math.abs(lv.z) >= Math.abs(lv.x)) face = lv.z <= 0 ? Face.FRONT : Face.BACK;
        else face = lv.x <= 0 ? Face.RIGHT : Face.LEFT;
        float fu, fv;
        double across = (l.x - cx) / width;
        double depth = l.z / 0.25D;
        switch (face) {
            case FRONT -> fu = (float) (0.5D - across);
            case BACK -> fu = (float) (0.5D + across);
            case RIGHT -> fu = (float) (0.5D + depth * 0.5D);
            case LEFT -> fu = (float) (0.5D - depth * 0.5D);
            default -> fu = (float) (0.5D - across);
        }
        fv = face == Face.TOP ? (float) (0.5D - depth * 0.5D) : (float) ((top - hy) / height);
        blot(s, part, face, Mth.clamp(fu, 0.05F, 0.95F), Mth.clamp(fv, 0.02F, 0.95F),
                0.45F + size * 7F, Mth.clamp(0.45F + size * 3F, 0.4F, 0.9F), color);
    }

    private static void wash(Minecraft mc, Player p, Stain s, GoreLevel g) {
        double water = p.isInWater() || p.isUnderWater() ? p.getFluidHeight(FluidTags.WATER) : 0D;
        if (p.isSwimming() || p.isUnderWater()) water = 3D;
        boolean rain = p.level().isRainingAt(p.blockPosition().above());
        float dry = g.stainsStick() ? 0F : 0.0007F;          // seca e descasca sozinho (menos no Extremo)
        float rainRate = rain ? 0.0035F : 0F;
        int[] parts = s.slim ? PART_SLIM : PART_WIDE;
        float washed = 0F;
        float total = 0F;
        for (int i = 0; i < N * N; i++) {
            float a = s.amt[i];
            if (a <= 0F) continue;
            int part = parts[i];
            float rate = dry + rainRate;
            if (part >= 0 && water > partHeight(part)) rate = Math.max(rate, 0.03F);
            if (rate > 0F) {
                float now = Math.max(0F, a - rate);
                washed += a - now;
                s.amt[i] = now;
                s.dirty = true;
                a = now;
            }
            total += a;
        }
        s.total = total;
        if (washed <= 0.001F) return;
        RandomSource r = p.getRandom();
        if (water > 0.1D && p.level().getFluidState(p.blockPosition()).is(FluidTags.WATER)) {
            // a água em volta fica vermelha onde o sangue foi
            int n = Mth.clamp((int) (washed * 0.6F) + (r.nextFloat() < washed * 0.6F % 1F ? 1 : 0), 0, 4);
            double top = Math.min(p.getBbHeight(), water);
            for (int k = 0; k < n; k++) {
                var particle = mc.particleEngine.createParticle(ModParticles.BLOOD_CLOUD.get(),
                        p.getX() + r.nextGaussian() * 0.25D, p.getY() + r.nextDouble() * top, p.getZ() + r.nextGaussian() * 0.25D,
                        r.nextGaussian() * 0.01D, 0.002D, r.nextGaussian() * 0.01D);
                if (particle instanceof com.froggydude.client.BloodCloudParticle cloud) cloud.tint(s.lastColor);
            }
        } else if (rain && r.nextFloat() < washed * 3F) {
            // a chuva lava: escorre e pinga do corpo, e o chão embaixo fica vermelho
            ClientBloodFx.droplets(p.getX() + r.nextGaussian() * 0.2D, p.getY() + 0.3D + r.nextDouble(),
                    p.getZ() + r.nextGaussian() * 0.2D, 1, s.lastColor);
            if (r.nextFloat() < 0.08F) ClientBloodFx.pool(p.getX(), p.getY() + 0.2D, p.getZ(), 0.3F, s.lastColor);
        }
    }

    /** Renasceu: o corpo novo vem limpo. */
    public static void forget(UUID id) {
        Stain s = STAINS.remove(id);
        if (s != null) s.close();
    }

    private static void clearAll() {
        STAINS.values().forEach(Stain::close);
        STAINS.clear();
    }

    // ---------------------------------------------------------------- salvar o teu (Extremo)

    private static File file(UUID id) {
        return new File(Minecraft.getInstance().gameDirectory, "froggydude/stains_" + id + ".dat");
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Player me = event.getPlayer();
        if (me != null && gore().stainsStick()) {
            Stain s = STAINS.get(me.getUUID());
            File f = file(me.getUUID());
            if (s != null && s.total > 0.5F) {
                f.getParentFile().mkdirs();
                try (DataOutputStream out = new DataOutputStream(new FileOutputStream(f))) {
                    for (int i = 0; i < N * N; i++) {
                        out.writeByte((int) (s.amt[i] * 255));
                        out.writeInt(s.col[i]);
                    }
                } catch (IOException e) {
                    System.err.println("[froggydude] não salvou o sangue da skin: " + e);
                }
            } else if (f.exists()) {
                f.delete();
            }
        }
        clearAll();
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Player me = event.getPlayer();
        if (me == null || !gore().stainsStick()) return;
        File f = file(me.getUUID());
        if (!f.exists()) return;
        Stain s = stain(me);
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            float total = 0F;
            for (int i = 0; i < N * N; i++) {
                s.amt[i] = (in.readByte() & 255) / 255F;
                s.col[i] = in.readInt();
                total += s.amt[i];
            }
            s.total = total;
            s.dirty = true;
        } catch (IOException e) {
            System.err.println("[froggydude] não leu o sangue da skin: " + e);
        }
    }

    // ---------------------------------------------------------------- desenho

    /** A camada: o modelo de novo, só a camada de fora, com a textura de sangue. */
    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            Stain s = STAINS.get(player.getUUID());
            if (s == null || s.total < 0.5F || player.isInvisible() || !gore().skinStains() || !s.ready()) return;
            PlayerModel<AbstractClientPlayer> m = getParentModel();
            boolean[] base = {m.head.visible, m.body.visible, m.rightArm.visible, m.leftArm.visible, m.rightLeg.visible, m.leftLeg.visible};
            boolean[] over = {m.hat.visible, m.jacket.visible, m.rightSleeve.visible, m.leftSleeve.visible, m.rightPants.visible, m.leftPants.visible};
            // só a camada de fora (a de dentro fica escondida pela jaqueta/manga das skins)
            m.hat.visible = base[0];
            m.jacket.visible = base[1];
            m.rightSleeve.visible = base[2];
            m.leftSleeve.visible = base[3] && !ClientArmState.isArmless(player.getId());
            m.rightPants.visible = base[4];
            m.leftPants.visible = base[5];
            m.head.visible = m.body.visible = m.rightArm.visible = m.leftArm.visible = m.rightLeg.visible = m.leftLeg.visible = false;
            m.renderToBuffer(ps, buffers.getBuffer(StainRenderType.stain(s.loc)), light,
                    LivingEntityRenderer.getOverlayCoords(player, 0F), 1F, 1F, 1F, 1F);
            m.head.visible = base[0];
            m.body.visible = base[1];
            m.rightArm.visible = base[2];
            m.leftArm.visible = base[3];
            m.rightLeg.visible = base[4];
            m.leftLeg.visible = base[5];
            m.hat.visible = over[0];
            m.jacket.visible = over[1];
            m.rightSleeve.visible = over[2];
            m.leftSleeve.visible = over[3];
            m.rightPants.visible = over[4];
            m.leftPants.visible = over[5];
        }
    }

    /** Desenhando a mão por dentro do próprio evento (o renderRightHand dispara o RenderArmEvent de novo). */
    private static boolean inArm;

    /** Primeira pessoa: a mão desenhada normal e, por cima, a manga suja. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderArm(RenderArmEvent event) {
        if (event.isCanceled() || inArm) return;
        AbstractClientPlayer player = event.getPlayer();
        Stain s = STAINS.get(player.getUUID());
        if (s == null || s.total < 0.5F || !gore().skinStains() || !s.ready()) return;
        EntityRenderer<?> er = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(er instanceof PlayerRenderer renderer)) return;
        event.setCanceled(true);
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        // o renderRightHand/LeftHand da Forge dispara este mesmo evento: sem a trava era
        // recursão infinita (StackOverflow) assim que a skin ficava suja com a mão vazia
        inArm = true;
        try {
            if (right) renderer.renderRightHand(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player);
            else renderer.renderLeftHand(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player);
        } finally {
            inArm = false;
        }
        ModelPart sleeve = right ? renderer.getModel().rightSleeve : renderer.getModel().leftSleeve;
        boolean vis = sleeve.visible;
        sleeve.visible = true;
        sleeve.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(StainRenderType.stain(s.loc)),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        sleeve.visible = vis;
    }
}
