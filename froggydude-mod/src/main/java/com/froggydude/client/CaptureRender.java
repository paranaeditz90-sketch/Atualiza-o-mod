package com.froggydude.client;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.TongueHold;
import com.froggydude.entity.ai.TongueCapture;
import com.froggydude.entity.FroggyState;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ClientFling;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * (v0.7.33) A captura com a língua no cliente, como na réplica v8 que o LM aprovou (09/11; viking 11-lingua-captura.md):
 * - a língua é uma FITA de 1 px de grossura (grossa só na base, na boca), larga, vermelha, com o desenho do vídeo: o
 *   meio um tom mais claro e as beiradas (25% de cada lado) um tom mais escuras; sai da boca pra frente numa curva e
 *   entra por baixo da volta;
 * - a volta é a mesma fita dando a volta no peito e nos braços em PENTÁGONO achatado e apertado (a "opção A" do LM: o
 *   lado reto na frente, a ponta nas costas; medida no AJ 32:56,93), com o último lado descendo até o canto da frente,
 *   de onde a língua sai por baixo;
 * - as cores são as do vídeo: de cima com luz (246,96,84), de lado (222,60,50), por baixo na sombra (180,48,42), pela
 *   luz do próprio jogo em cada face;
 * - quem está presa vira o corpo pra ele (a volta encaixa), meio de lado como o AJ (22,5°);
 * - no arremesso, quem voa vai rodando mole (o "ragdoll" da réplica v3) até cair.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CaptureRender {

    private static final ResourceLocation WHITE = new ResourceLocation(ModEntityTypes.MOD_ID, "textures/entity/tongue.png");

    // as cores medidas no vídeo (AJ 32:56-33:02 e Grox 12:47-12:58)
    private static final int[][] TONE = {{180, 48, 42}, {222, 60, 50}, {246, 96, 84}};
    // a luz do jogo nos mobs (Lighting.DIFFUSE_LIGHT_0/1), pra escolher o tom de cada face
    private static final Vector3f L0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f L1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();

    // a volta em pentágono (pent_A.json do encaixe; blocos, no corpo da vítima: x = pra longe dele, z = lado)
    private static final float W = 0.6194587F;
    private static final float Y_TOP = 1.4148702F;
    private static final float PENT_R = 0.7164630F;
    private static final float PENT_K = 0.5573914F;
    private static final float PENT_ROT = 47.11531F;
    private static final float PENT_DX = 0.0640340F;
    private static final float PENT_DZ = -0.1076111F;
    private static final float DROP = 0.4191442F;
    private static final float T_IN = 0.4045326F;
    private static final float T_AZ = 22.513153F;
    private static final float T_EL = 42.777445F;
    private static final float T_DN = -0.1138125F;
    private static final float T_DY = 0.1538815F;
    private static final float THICK = 1F / 32F;

    // a fita da língua (réplica): meia largura 0,17 na boca até W/2 na vítima; 1 px de grossura, 0,2 na base
    private static final float HW_MOUTH = 0.17F;
    private static final float HT_MID = 1F / 32F;
    private static final float HT_BASE = 0.10F;
    private static final float BASE_LEN = 0.25F;
    private static final float C1 = 0.3F, C2 = 0.35F;
    private static final int SEGMENTS = 28;

    /** Quem está presa (id) -> o FroggyDude (id) e até quando vale (relógio do cliente). */
    private static final Map<Integer, int[]> HELD = new HashMap<>();
    private static int clock;
    private static boolean wasOnHold;

    private CaptureRender() {
    }

    /** O FroggyDude avisa todo tick quem a língua dele segura. */
    public static void markHeld(int victimId, int froggyId) {
        HELD.put(victimId, new int[]{froggyId, clock + 2});
    }

    @javax.annotation.Nullable
    private static FroggydudeEntity holderOf(Entity victim) {
        int[] e = HELD.get(victim.getId());
        if (e == null || e[1] < clock) return null;
        Entity f = victim.level().getEntity(e[0]);
        return f instanceof FroggydudeEntity froggy && froggy.getCaptureId() == victim.getId() ? froggy : null;
    }

    // ---------------------------------------------------------------- a língua e a volta

    /**
     * Desenha a língua e a volta. O poseStack está na posição do FroggyDude (eixos do mundo); mouth é a boca nesse
     * mesmo espaço (o renderer tira do osso da cabeça).
     */
    static void draw(PoseStack poseStack, MultiBufferSource buffers, FroggydudeEntity froggy, LivingEntity victim,
                     Vector3f mouth, float partial, int froggyLight) {
        Vec3 fp = froggy.getPosition(partial);
        Vec3 vp = victim.getPosition(partial);
        Vector3f feet = new Vector3f((float) (vp.x - fp.x), (float) (vp.y - fp.y), (float) (vp.z - fp.z));
        // o corpo dela: x pra longe dele (no chão), y pra cima, z = o lado
        Vector3f fx = new Vector3f(feet.x, 0F, feet.z);
        if (fx.lengthSquared() < 1.0E-4F) fx.set(-Mth.sin(froggy.yBodyRot * Mth.DEG_TO_RAD), 0F, Mth.cos(froggy.yBodyRot * Mth.DEG_TO_RAD));
        fx.normalize();
        Vector3f fz = new Vector3f(-fx.z, 0F, fx.x);
        Vector3f up = new Vector3f(0F, 1F, 0F);
        // meio de lado pra ele, como o AJ: a língua sai da volta direto rumo à boca
        float a = -T_AZ * Mth.DEG_TO_RAD;
        float c = Mth.cos(a), s = Mth.sin(a);
        Vector3f bx = new Vector3f(fx).mul(c).sub(new Vector3f(fz).mul(s));   // rot_y: x -> c x - s z (no mundo)
        Vector3f bz = new Vector3f(fx).mul(s).add(new Vector3f(fz).mul(c));
        Vector3f by = new Vector3f(up);
        Vector3f origin = new Vector3f(feet);
        // derrubada (o arremesso): deitada de bruços, a cabeça pra ele. A volta vai junto com o peito, com a MESMA
        // conta do jogo pra deitar o jogador (PlayerRenderer.setupRotations: com a pose de nado, desce 1 e vai 0,3 pra
        // trás, e gira -90° x o quanto já "nada" em volta do lado, nos pés)
        if (victim instanceof net.minecraft.world.entity.player.Player pl) {
            float swim = pl.getSwimAmount(partial);
            boolean pose = pl.getPose() == Pose.SWIMMING;
            if (swim > 0F || pose) {
                float f4 = -90F * swim * Mth.DEG_TO_RAD;
                float cf = Mth.cos(f4), sf = Mth.sin(f4);
                float tx = pose ? 0.3F : 0F, ty = pose ? -1.0F : 0F;
                origin.add(new Vector3f(bx).mul(tx * cf + ty * sf)).add(new Vector3f(by).mul(ty * cf - tx * sf));
                Vector3f nbx = new Vector3f(bx).mul(cf).sub(new Vector3f(by).mul(sf));
                Vector3f nby = new Vector3f(bx).mul(sf).add(new Vector3f(by).mul(cf));
                bx = nbx;
                by = nby;
            }
        }

        int victimLight = Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(victim, partial);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose pose = poseStack.last();

        // os 5 cantos (x, z no corpo dela) e o centro
        float rz = PENT_R, rx = PENT_R * PENT_K;
        float[][] vs = new float[5][];
        for (int k = 0; k < 5; k++) {
            float ang = (PENT_ROT - 72F * k) * Mth.DEG_TO_RAD;
            vs[k] = new float[]{PENT_DX - rx * Mth.cos(ang), PENT_DZ + rz * Mth.sin(ang)};
        }
        float[] cen = {PENT_DX, PENT_DZ};
        float yc = Y_TOP - W / 2F;
        // quem está presa, em 1ª pessoa, não vê a volta (é como o próprio corpo, que o jogo também não desenha): colada
        // na câmera ela tampava metade da tela e não dava pra ver ele lá embaixo. A língua subindo até o peito aparece.
        Minecraft mc = Minecraft.getInstance();
        boolean self = victim == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        for (int j = 0; j < 5 && !self; j++) {
            float[] p0 = vs[j], p1 = vs[(j + 1) % 5];
            float[] n2 = edgeNormal(p0, p1, cen);
            Vector3f A = local(origin, bx, by, bz, p0[0], yc, p0[1]);
            Vector3f B = local(origin, bx, by, bz, p1[0], yc - (j == 4 ? DROP : 0F), p1[1]);
            Vector3f d = new Vector3f(B).sub(A);
            float len = d.length();
            Vector3f uu = new Vector3f(d).div(len);
            Vector3f ww = dirLocal(bx, by, bz, n2[0], 0F, n2[1]);
            Vector3f vv = new Vector3f(ww).cross(uu).normalize();
            box(vc, pose, A, uu, vv, ww, 0F, len, -W / 2F, W / 2F, -THICK, THICK, 1, victimLight);
            // as pontas de cada lado (nos cantos do pentágono a fita dobra: sem elas ficava uma frestinha)
            int capTone = Math.max(0, tone(uu.x, uu.y, uu.z) - 1);
            quad(vc, pose, A, vv, -W / 2F, W / 2F, ww, -THICK, THICK, uu, 0F, TONE[capTone], victimLight);
            quad(vc, pose, A, vv, -W / 2F, W / 2F, ww, -THICK, THICK, uu, len, TONE[capTone], victimLight);
        }
        // de onde a língua sai: no lado da frente, por baixo da volta
        float[] v0 = vs[0];
        float ex = vs[1][0] - v0[0], ez = vs[1][1] - v0[1];
        float le = Mth.sqrt(ex * ex + ez * ez);
        float[] ne = edgeNormal(v0, vs[1], cen);
        float mxx = v0[0] + ex / le * Math.min(Math.abs(T_IN), le) + ne[0] * T_DN;
        float mzz = v0[1] + ez / le * Math.min(Math.abs(T_IN), le) + ne[1] * T_DN;
        Vector3f end = local(origin, bx, by, bz, mxx, yc - DROP + T_DY, mzz);
        float ta = T_AZ * Mth.DEG_TO_RAD, te = T_EL * Mth.DEG_TO_RAD;
        Vector3f uEnd = dirLocal(bx, by, bz, -Mth.cos(te) * Mth.cos(ta), -Mth.sin(te), Mth.cos(te) * Mth.sin(ta));

        // a fita da boca até a volta: sai da boca pra frente (a curva que o LM gostou) e chega pela direção do encaixe
        Vector3f dv = new Vector3f(end).sub(mouth);
        float D = dv.length();
        Vector3f fwd = new Vector3f(dv.x, 0F, dv.z);
        if (fwd.lengthSquared() < 1.0E-6F) fwd.set(fx);
        fwd.normalize();
        Vector3f c1 = new Vector3f(fwd).add(0F, 0.12F, 0F).mul(C1 * D).add(mouth);
        Vector3f c2 = new Vector3f(uEnd).mul(C2 * D).add(end);
        // (arremesso, réplica v3) no giro a língua é MOLE: o meio fica pra trás da vítima, mais quanto mais rápido
        if (froggy.getFroggyState() == FroggyState.TONGUE_THROW) {
            float age = froggy.getClientStateAge() + partial;
            float rate = TongueCapture.sweepRate(age, froggy.getThrowSweep());
            float lag = Math.signum(rate) * Math.min(1F, Math.abs(rate) / 40F);
            c1.add(new Vector3f(fz).mul(-lag * 0.45F * D));
            c2.add(new Vector3f(fz).mul(-lag * 0.30F * D)).add(0F, -Math.abs(lag) * 0.25F * D, 0F);
        }
        ribbon(vc, pose, mouth, c1, c2, end, fz, W / 2F, froggyLight, victimLight);
        LAST.put(froggy.getId(), new Vector3f[]{new Vector3f(end), new Vector3f(fz)});
    }

    /** A fita da língua ao longo de uma curva (boca -> ponta): larga na ponta, grossa só na base. */
    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vector3f p0, Vector3f c1, Vector3f c2, Vector3f p3,
                               Vector3f side, float hwEnd, int lightA, int lightB) {
        Vector3f prev = new Vector3f(p0), q1 = new Vector3f(), u = new Vector3f(), v = new Vector3f(), w = new Vector3f();
        for (int k = 0; k < SEGMENTS; k++) {
            bezier(p0, c1, c2, p3, (k + 1) / (float) SEGMENTS, q1);
            u.set(q1).sub(prev);
            float len = u.length();
            if (len > 1.0E-5F) {
                u.div(len);
                w.set(side).sub(u.x * side.dot(u), u.y * side.dot(u), u.z * side.dot(u));
                if (w.lengthSquared() < 1.0E-6F) w.set(0F, 1F, 0F);
                w.normalize();
                w.cross(u, v);
                float tm = (k + 0.5F) / SEGMENTS;
                float hw = HW_MOUTH + (hwEnd - HW_MOUTH) * tm;
                float ht = HT_MID + (HT_BASE - HT_MID) * Math.max(0F, 1F - tm / BASE_LEN);
                box(vc, pose, prev, u, v, w, -0.02F, len + 0.02F, -ht, ht, -hw, hw, 2, tm < 0.5F ? lightA : lightB);
            }
            prev.set(q1);
        }
    }

    /** Onde a ponta da língua estava no último quadro preso (por FroggyDude): a língua solta volta de lá. */
    private static final Map<Integer, Vector3f[]> LAST = new HashMap<>();

    /**
     * (arremesso, réplica v3, VS Every Opponent 32:43,76-32:44,10) Soltou: a língua cai numa curva e volta pra boca em
     * 0,4 s. O poseStack e a boca no espaço dele, como em draw.
     */
    static void drawLoose(PoseStack poseStack, MultiBufferSource buffers, FroggydudeEntity froggy, Vector3f mouth,
                          float partial, int light) {
        Vector3f[] last = LAST.get(froggy.getId());
        if (last == null) return;
        float k = (froggy.getClientStateAge() + partial - TongueCapture.releaseTick()) / 8F;
        if (k < 0F || k >= 1F) return;
        Vector3f d = new Vector3f(last[0]).sub(mouth);
        float L = d.length() * (float) Math.pow(1F - k, 1.4F);
        if (L < 0.05F) return;
        Vector3f fwd = d.normalize();
        float droop = Math.min(1F, k * 2.5F);
        Vector3f down = new Vector3f(0F, -1F, 0F);
        Vector3f c1 = new Vector3f(fwd).mul(0.45F * L).add(mouth);
        Vector3f c2 = new Vector3f(fwd).mul(0.6F).add(new Vector3f(down).mul(0.5F * droop)).mul(L).add(mouth);
        Vector3f end = new Vector3f(fwd).mul(0.7F - 0.4F * droop).add(new Vector3f(down).mul(0.9F * droop)).mul(L).add(mouth);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        ribbon(vc, poseStack.last(), mouth, c1, c2, end, last[1], W / 2F * (1F - 0.4F * k), light, light);
    }

    private static void bezier(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t, Vector3f out) {
        float a = 1F - t;
        float b0 = a * a * a, b1 = 3F * a * a * t, b2 = 3F * a * t * t, b3 = t * t * t;
        out.set(p0.x * b0 + p1.x * b1 + p2.x * b2 + p3.x * b3, p0.y * b0 + p1.y * b1 + p2.y * b2 + p3.y * b3,
                p0.z * b0 + p1.z * b1 + p2.z * b2 + p3.z * b3);
    }

    private static float[] edgeNormal(float[] a, float[] b, float[] c) {
        float dx = b[0] - a[0], dz = b[1] - a[1];
        float l = Mth.sqrt(dx * dx + dz * dz);
        float nx = dz / l, nz = -dx / l;
        float mx = (a[0] + b[0]) / 2F - c[0], mz = (a[1] + b[1]) / 2F - c[1];
        return nx * mx + nz * mz >= 0F ? new float[]{nx, nz} : new float[]{-nx, -nz};
    }

    private static Vector3f local(Vector3f o, Vector3f bx, Vector3f by, Vector3f bz, float x, float y, float z) {
        return new Vector3f(o).add(new Vector3f(bx).mul(x)).add(new Vector3f(by).mul(y)).add(new Vector3f(bz).mul(z));
    }

    private static Vector3f dirLocal(Vector3f bx, Vector3f by, Vector3f bz, float x, float y, float z) {
        return new Vector3f(bx).mul(x).add(new Vector3f(by).mul(y)).add(new Vector3f(bz).mul(z)).normalize();
    }

    /**
     * Uma caixa fina da fita: de o, ao longo de (a0 = comprimento, a1, a2), de lo a hi em cada eixo; widthAxis (1 ou 2)
     * é a largura e o outro, a grossura. Cortada em 25/50/25 na largura: o meio no tom da face, as beiradas um tom mais
     * escuras (o desenho do vídeo). Só as faces que aparecem: as duas da grossura de cada faixa e as duas beiradas de
     * fora (as pontas ficam tapadas pelo pedaço vizinho e as divisas entre as faixas, por dentro).
     */
    private static void box(VertexConsumer vc, PoseStack.Pose pose, Vector3f o, Vector3f a0, Vector3f a1, Vector3f a2,
                            float lo0, float hi0, float lo1, float hi1, float lo2, float hi2, int widthAxis, int light) {
        Vector3f aw = widthAxis == 1 ? a1 : a2, at = widthAxis == 1 ? a2 : a1;
        float wLo = widthAxis == 1 ? lo1 : lo2, wHi = widthAxis == 1 ? hi1 : hi2;
        float tLo = widthAxis == 1 ? lo2 : lo1, tHi = widthAxis == 1 ? hi2 : hi1;
        float c1 = wLo + (wHi - wLo) * 0.25F, c2 = wHi - (wHi - wLo) * 0.25F;
        int up = tone(at.x, at.y, at.z), down = tone(-at.x, -at.y, -at.z);
        for (int part = 0; part < 3; part++) {
            float w0 = part == 0 ? wLo : part == 1 ? c1 : c2, w1 = part == 0 ? c1 : part == 1 ? c2 : wHi;
            int shift = part == 1 ? 0 : 1;
            // em cima e embaixo da fita (a grossura)
            quad(vc, pose, o, a0, lo0, hi0, aw, w0, w1, at, tHi, TONE[Math.max(0, up - shift)], light);
            quad(vc, pose, o, a0, lo0, hi0, aw, w0, w1, at, tLo, TONE[Math.max(0, down - shift)], light);
        }
        // as duas beiradas de fora (de lado ela não some: aparece uma tirinha)
        quad(vc, pose, o, a0, lo0, hi0, at, tLo, tHi, aw, wHi, TONE[Math.max(0, tone(aw.x, aw.y, aw.z) - 1)], light);
        quad(vc, pose, o, a0, lo0, hi0, at, tLo, tHi, aw, wLo, TONE[Math.max(0, tone(-aw.x, -aw.y, -aw.z) - 1)], light);
    }

    /** 2 claro / 1 médio / 0 escuro, pela luz do jogo nos mobs (0,4 + 0,6 x as duas luzes). */
    private static int tone(float nx, float ny, float nz) {
        float d0 = Math.max(0F, nx * L0.x + ny * L0.y + nz * L0.z), d1 = Math.max(0F, nx * L1.x + ny * L1.y + nz * L1.z);
        float sh = Math.min(1F, 0.4F + 0.6F * (d0 + d1));
        return sh > 0.88F ? 2 : sh > 0.6F ? 1 : 0;
    }

    /** Um quadrado: no comprimento (a0) de l0 a h0, no eixo b de bl a bh, parado em c no eixo cAxis. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vector3f o, Vector3f a0, float l0, float h0,
                             Vector3f b, float bl, float bh, Vector3f cAxis, float c, int[] col, int light) {
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        float ox = o.x + cAxis.x * c, oy = o.y + cAxis.y * c, oz = o.z + cAxis.z * c;
        for (int i = 0; i < 4; i++) {
            float s0 = i == 0 || i == 3 ? l0 : h0, s1 = i < 2 ? bl : bh;
            // a normal "pra cima" deixa o jogo pintar a cor cheia: o tom de cada face já vem escolhido
            vc.vertex(m, ox + a0.x * s0 + b.x * s1, oy + a0.y * s0 + b.y * s1, oz + a0.z * s0 + b.z * s1)
                    .color(col[0], col[1], col[2], 255).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light).normal(nm, 0F, 1F, 0F).endVertex();
        }
    }

    // ---------------------------------------------------------------- quem está presa / voando

    private static final Map<LivingEntity, float[]> SAVED = new HashMap<>();

    /** LOWEST: se outro mod cancelar o desenho antes, nada aqui roda (o Post não viria pra desfazer o pushPose). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        float pt = event.getPartialTick();
        FroggydudeEntity holder = holderOf(e);
        ClientFling.Flight flight = holder == null ? ClientFling.get(e.getId()) : null;
        if (holder == null && flight == null) return;
        SAVED.put(e, new float[]{e.yBodyRot, e.yBodyRotO, e.yHeadRot, e.yHeadRotO});
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        if (holder != null) {
            // presa: o corpo virado pra ele, meio de lado (a volta encaixa)
            Vec3 d = holder.getPosition(pt).subtract(e.getPosition(pt));
            float toHim = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
            float body = toHim - T_AZ;
            float head = Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot);
            float rel = Mth.clamp(Mth.wrapDegrees(head - body), -70F, 70F);
            e.yBodyRot = e.yBodyRotO = body;
            e.yHeadRot = e.yHeadRotO = body + rel;
        } else {
            // voando mole: roda no ar em volta do peito, de cabeça pra baixo e de novo
            float age = ClientFling.age(flight, pt);
            Vector3f axis = new Vector3f(-flight.dz, 0F, flight.dx);
            if (axis.lengthSquared() > 1.0E-6F) {
                axis.normalize();
                ps.translate(0F, e.getBbHeight() * 0.55F, 0F);
                ps.mulPose(Axis.of(axis).rotationDegrees(-26F * age));
                ps.translate(0F, -e.getBbHeight() * 0.55F, 0F);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        float[] s = SAVED.remove(event.getEntity());
        if (s == null) return;
        LivingEntity e = event.getEntity();
        e.yBodyRot = s[0];
        e.yBodyRotO = s[1];
        e.yHeadRot = s[2];
        e.yHeadRotO = s[3];
        event.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        clock++;
        ClientFling.clientTick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            HELD.clear();
            LAST.clear();
            ClientFling.clear();
            return;
        }
        if (clock % 200 == 0) LAST.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof FroggydudeEntity f)
                || !f.getFroggyState().isCapture());
        for (Iterator<Map.Entry<Integer, int[]>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue()[1] < clock - 20) it.remove();
        }
        // voando: acaba quando cai (no chão ou na água)
        for (int id : ClientFling.ids()) {
            ClientFling.Flight f = ClientFling.get(id);
            Entity e = mc.level.getEntity(id);
            if (f == null || e == null) {
                ClientFling.stop(id);
                continue;
            }
            float age = ClientFling.age(f, 0F);
            if (!e.onGround() && age > 1) f.airborne = true;
            if (f.airborne && (e.onGround() || e.isInWater()) && age > 4) ClientFling.stop(id);
        }
        // montar no gancho mostra "aperte shift pra descer" (do barco): não é pra descer
        boolean onHold = mc.player.getVehicle() instanceof TongueHold;
        if (onHold && !wasOnHold) mc.gui.setOverlayMessage(Component.empty(), false);
        wasOnHold = onHold;
    }

    /** Quem voa em 1ª pessoa: a câmera tomba de um lado pro outro (rodar inteira enjoa). */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        ClientFling.Flight f = ClientFling.get(mc.player.getId());
        if (f == null) return;
        float age = ClientFling.age(f, (float) event.getPartialTick());
        float fade = Math.max(0F, 1F - age / f.ticks);
        event.setRoll(event.getRoll() + 28F * fade * Mth.sin(age * 0.45F));
    }

    /** A língua dele está segurando alguém agora? */
    static boolean capturing(FroggydudeEntity froggy) {
        return froggy.getCaptureId() >= 0 && froggy.getFroggyState().isCapture();
    }
}
