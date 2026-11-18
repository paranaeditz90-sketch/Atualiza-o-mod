package com.becomehim.client;

import com.becomehim.LmAttacks;
import com.becomehim.BecomeHim;
import com.becomehim.LmAttack;
import com.becomehim.LmTongue;
import com.becomehim.TongueHold;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (Become HIM...) A língua do FroggyDude no jogador, no cliente: a mesma fita e a mesma volta em pentágono da réplica
 * v8 do mod da IA (CaptureRender, 0.7.33), saindo da boca do boneco. Também: quem está presa vira o corpo pra quem
 * segura, quem é arremessado voa rodando mole e a câmera dele tomba no voo.
 *
 * Em 3ª pessoa (e pros outros) a boca vem do osso da cabeça (LmRenderer); em 1ª pessoa o próprio boneco não é
 * desenhado, então a língua sai de um tico abaixo e na frente do olho.
 */
@Mod.EventBusSubscriber(modid = BecomeHim.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmTongueRender {

    private static final ResourceLocation WHITE = BecomeHim.id("textures/misc/white.png");

    // as cores medidas no vídeo (AJ 32:56-33:02 e Grox 12:47-12:58)
    private static final int[][] TONE = {{180, 48, 42}, {222, 60, 50}, {246, 96, 84}};
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

    private static final float HW_MOUTH = 0.17F;
    private static final float HT_MID = 1F / 32F;
    private static final float HT_BASE = 0.10F;
    private static final float BASE_LEN = 0.25F;
    private static final float C1 = 0.3F, C2 = 0.35F;
    private static final int SEGMENTS = 28;

    /**
     * A boca em 1ª pessoa: abaixo e na frente do olho (blocos), e a base fina (larga e colada na câmera ela tampava
     * a tela inteira no teste).
     */
    private static final float FP_DOWN = 0.45F, FP_FWD = 0.55F, FP_BASE = 0.35F;
    /** Quanto da largura/grossura da base vale agora (1 = a da réplica; menor em 1ª pessoa). */
    private static float baseScale = 1F;

    /** Onde a ponta da língua estava no último quadro preso (por jogador): a língua solta volta de lá. */
    private static final Map<Integer, Vector3f[]> LAST = new HashMap<>();
    private static final Map<Integer, Flight> FLYING = new HashMap<>();
    private static final Map<LivingEntity, float[]> SAVED = new HashMap<>();
    private static long clock;
    private static boolean wasOnHold;

    private static final class Flight {
        final long start;
        final int ticks;
        final float dx, dz;
        boolean airborne;

        Flight(long start, int ticks, float dx, float dz) {
            this.start = start;
            this.ticks = ticks;
            this.dx = dx;
            this.dz = dz;
        }

        float age(float partial) {
            return clock - start + partial;
        }
    }

    private LmTongueRender() {
    }

    /** O servidor avisou: esse voa rodando. */
    public static void fling(int entityId, int ticks, float dx, float dz) {
        FLYING.put(entityId, new Flight(clock, ticks, dx, dz));
    }

    // ---------------------------------------------------------------- quando desenhar

    /** Esse jogador está com a língua pra fora agora (atirando, segurando, arremessando ou recolhendo)? */
    static boolean tongueOut(Player p) {
        LmActsClient.Act a = LmActsClient.get(p);
        if (a == null) return false;
        if (a.kind == LmAttack.GRAB) return true;
        if (a.kind == LmAttack.STEAL) return a.age < LmAttacks.STEAL_TICKS;
        return a.kind == LmAttack.THROW && a.age < LmTongue.T_REL + 8;
    }

    /**
     * Desenha a língua de quem segura. O poseStack está nos pés dele (eixos do mundo); mouth é a boca nesse espaço.
     */
    static void draw(PoseStack poseStack, MultiBufferSource buffers, Player holder, Vector3f mouth, float partial,
                     int light) {
        LmActsClient.Act a = LmActsClient.get(holder);
        if (a == null) return;
        Entity e = holder.level().getEntity(a.target);
        LivingEntity victim = e instanceof LivingEntity le ? le : null;
        float age = a.age + partial;
        if (a.kind == LmAttack.STEAL) {
            // o roubo: a língua vai até a mão e volta com a arma
            if (victim == null) return;
            float k = age < LmAttacks.STEAL_HIT ? age / LmAttacks.STEAL_HIT
                    : 1F - (age - LmAttacks.STEAL_HIT) / (LmAttacks.STEAL_TICKS - LmAttacks.STEAL_HIT);
            drawShot(poseStack, buffers, holder, victim, mouth, k, partial, light);
            return;
        }
        if (a.kind == LmAttack.THROW && age >= LmTongue.T_REL) {
            drawLoose(poseStack, buffers, holder, mouth, age - LmTongue.T_REL, light);
            return;
        }
        if (victim == null) return;
        if (a.kind == LmAttack.GRAB && age < LmTongue.HIT) {
            drawShot(poseStack, buffers, holder, victim, mouth, age / LmTongue.HIT, partial, light);
            return;
        }
        drawWrap(poseStack, buffers, holder, a, victim, mouth, partial, light);
    }

    /** A língua indo: reta, da boca até o peito dela, crescendo. */
    private static void drawShot(PoseStack poseStack, MultiBufferSource buffers, Player holder, LivingEntity victim,
                                 Vector3f mouth, float k, float partial, int light) {
        Vec3 fp = holder.getPosition(partial);
        Vec3 vp = victim.getPosition(partial).add(0, victim.getBbHeight() * 0.6D, 0);
        Vector3f chest = new Vector3f((float) (vp.x - fp.x), (float) (vp.y - fp.y), (float) (vp.z - fp.z));
        Vector3f end = new Vector3f(chest).sub(mouth).mul(Mth.clamp(k, 0F, 1F)).add(mouth);
        Vector3f d = new Vector3f(end).sub(mouth);
        if (d.lengthSquared() < 1.0E-4F) return;
        Vector3f side = new Vector3f(-d.z, 0F, d.x);
        if (side.lengthSquared() < 1.0E-6F) side.set(1F, 0F, 0F);
        side.normalize();
        Vector3f c1 = new Vector3f(d).mul(0.33F).add(mouth);
        Vector3f c2 = new Vector3f(d).mul(0.66F).add(mouth);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        ribbon(vc, poseStack.last(), mouth, c1, c2, end, side, HW_MOUTH * 1.2F, light, light);
    }

    /** Pegou: a fita até a volta no peito dela (a réplica v8). */
    private static void drawWrap(PoseStack poseStack, MultiBufferSource buffers, Player holder, LmActsClient.Act a,
                                 LivingEntity victim, Vector3f mouth, float partial, int holderLight) {
        Vec3 fp = holder.getPosition(partial);
        Vec3 vp = victim.getPosition(partial);
        Vector3f feet = new Vector3f((float) (vp.x - fp.x), (float) (vp.y - fp.y), (float) (vp.z - fp.z));
        Vector3f fx = new Vector3f(feet.x, 0F, feet.z);
        if (fx.lengthSquared() < 1.0E-4F) {
            fx.set(-Mth.sin(holder.yBodyRot * Mth.DEG_TO_RAD), 0F, Mth.cos(holder.yBodyRot * Mth.DEG_TO_RAD));
        }
        fx.normalize();
        Vector3f fz = new Vector3f(-fx.z, 0F, fx.x);
        float ang = -T_AZ * Mth.DEG_TO_RAD;
        float c = Mth.cos(ang), s = Mth.sin(ang);
        Vector3f bx = new Vector3f(fx).mul(c).sub(new Vector3f(fz).mul(s));
        Vector3f bz = new Vector3f(fx).mul(s).add(new Vector3f(fz).mul(c));
        Vector3f by = new Vector3f(0F, 1F, 0F);
        // bicho pequeno ou grande: a volta acompanha a altura do peito (a réplica é de um jogador, 1,8)
        float scale = Mth.clamp(victim.getBbHeight() / 1.8F, 0.35F, 1.6F);

        int victimLight = Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(victim, partial);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose pose = poseStack.last();
        float w = W * Math.min(1F, scale + 0.25F);

        float rz = PENT_R * scale, rx = PENT_R * PENT_K * scale;
        float[][] vs = new float[5][];
        for (int k = 0; k < 5; k++) {
            float an = (PENT_ROT - 72F * k) * Mth.DEG_TO_RAD;
            vs[k] = new float[]{PENT_DX * scale - rx * Mth.cos(an), PENT_DZ * scale + rz * Mth.sin(an)};
        }
        float[] cen = {PENT_DX * scale, PENT_DZ * scale};
        float yc = (Y_TOP - W / 2F) * scale;
        Minecraft mc = Minecraft.getInstance();
        // quem está presa, em 1ª pessoa, não vê a volta (colada na câmera tampava a tela)
        boolean self = victim == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        for (int j = 0; j < 5 && !self; j++) {
            float[] p0 = vs[j], p1 = vs[(j + 1) % 5];
            float[] n2 = edgeNormal(p0, p1, cen);
            Vector3f A = local(feet, bx, by, bz, p0[0], yc, p0[1]);
            Vector3f B = local(feet, bx, by, bz, p1[0], yc - (j == 4 ? DROP * scale : 0F), p1[1]);
            Vector3f d = new Vector3f(B).sub(A);
            float len = d.length();
            if (len < 1.0E-4F) continue;
            Vector3f uu = new Vector3f(d).div(len);
            Vector3f ww = dirLocal(bx, by, bz, n2[0], 0F, n2[1]);
            Vector3f vv = new Vector3f(ww).cross(uu).normalize();
            box(vc, pose, A, uu, vv, ww, 0F, len, -w / 2F, w / 2F, -THICK, THICK, 1, victimLight);
            int capTone = Math.max(0, tone(uu.x, uu.y, uu.z) - 1);
            quad(vc, pose, A, vv, -w / 2F, w / 2F, ww, -THICK, THICK, uu, 0F, TONE[capTone], victimLight);
            quad(vc, pose, A, vv, -w / 2F, w / 2F, ww, -THICK, THICK, uu, len, TONE[capTone], victimLight);
        }
        float[] v0 = vs[0];
        float ex = vs[1][0] - v0[0], ez = vs[1][1] - v0[1];
        float le = Mth.sqrt(ex * ex + ez * ez);
        float[] ne = edgeNormal(v0, vs[1], cen);
        float mxx = v0[0] + ex / le * Math.min(Math.abs(T_IN * scale), le) + ne[0] * T_DN * scale;
        float mzz = v0[1] + ez / le * Math.min(Math.abs(T_IN * scale), le) + ne[1] * T_DN * scale;
        Vector3f end = local(feet, bx, by, bz, mxx, yc - (DROP - T_DY) * scale, mzz);
        float ta = T_AZ * Mth.DEG_TO_RAD, te = T_EL * Mth.DEG_TO_RAD;
        Vector3f uEnd = dirLocal(bx, by, bz, -Mth.cos(te) * Mth.cos(ta), -Mth.sin(te), Mth.cos(te) * Mth.sin(ta));

        Vector3f dv = new Vector3f(end).sub(mouth);
        float D = dv.length();
        Vector3f fwd = new Vector3f(dv.x, 0F, dv.z);
        if (fwd.lengthSquared() < 1.0E-6F) fwd.set(fx);
        fwd.normalize();
        Vector3f c1 = new Vector3f(fwd).add(0F, 0.12F, 0F).mul(C1 * D).add(mouth);
        Vector3f c2 = new Vector3f(uEnd).mul(C2 * D).add(end);
        if (a.kind == LmAttack.THROW) {
            // (réplica v3) no giro a língua é MOLE: o meio fica pra trás da vítima, mais quanto mais rápido
            float rate = LmTongue.sweepRate(a.age + partial, a.sweep);
            float lag = Math.signum(rate) * Math.min(1F, Math.abs(rate) / 40F);
            c1.add(new Vector3f(fz).mul(-lag * 0.45F * D));
            c2.add(new Vector3f(fz).mul(-lag * 0.30F * D)).add(0F, -Math.abs(lag) * 0.25F * D, 0F);
        }
        ribbon(vc, pose, mouth, c1, c2, end, fz, w / 2F, holderLight, victimLight);
        LAST.put(holder.getId(), new Vector3f[]{new Vector3f(end), new Vector3f(fz)});
    }

    /** Soltou no arremesso: a língua cai numa curva e volta pra boca em 0,4 s. */
    private static void drawLoose(PoseStack poseStack, MultiBufferSource buffers, Player holder, Vector3f mouth,
                                  float since, int light) {
        Vector3f[] last = LAST.get(holder.getId());
        if (last == null) return;
        float k = since / 8F;
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

    // ---------------------------------------------------------------- 1ª pessoa

    /** Em 1ª pessoa o boneco não é desenhado: a língua sai de perto do olho. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || !mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != p || LmCamera.active()
                || !tongueOut(p)) {
            return;
        }
        float partial = event.getPartialTick();
        Camera cam = event.getCamera();
        Vec3 feet = p.getPosition(partial);
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        Vec3 c = cam.getPosition();
        ps.translate(feet.x - c.x, feet.y - c.y, feet.z - c.z);
        Vec3 look = p.getViewVector(partial);
        Vector3f mouth = new Vector3f((float) (look.x * FP_FWD), (float) (p.getEyeHeight() - FP_DOWN + look.y * FP_FWD),
                (float) (look.z * FP_FWD));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int light = mc.getEntityRenderDispatcher().getPackedLightCoords(p, partial);
        baseScale = FP_BASE;
        try {
            draw(ps, buffers, p, mouth, partial, light);
        } finally {
            baseScale = 1F;
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(WHITE));
        ps.popPose();
    }

    // ---------------------------------------------------------------- quem está presa / voando

    @Nullable
    private static Player holderOf(LivingEntity victim) {
        Player h = LmActsClient.tongueHolder(victim);
        if (h == null) return null;
        LmActsClient.Act a = LmActsClient.get(h);
        if (a == null) return null;
        if (a.kind == LmAttack.GRAB) return a.age >= LmTongue.HIT ? h : null;
        return a.kind == LmAttack.THROW && a.age < LmTongue.T_REL ? h : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        float pt = event.getPartialTick();
        Player holder = holderOf(e);
        Flight flight = holder == null ? FLYING.get(e.getId()) : null;
        if (holder == null && flight == null) return;
        SAVED.put(e, new float[]{e.yBodyRot, e.yBodyRotO, e.yHeadRot, e.yHeadRotO});
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        if (holder != null) {
            // presa: o corpo virado pra quem segura, meio de lado (a volta encaixa)
            Vec3 d = holder.getPosition(pt).subtract(e.getPosition(pt));
            float toHim = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
            float body = toHim - T_AZ;
            float head = Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot);
            float rel = Mth.clamp(Mth.wrapDegrees(head - body), -70F, 70F);
            e.yBodyRot = e.yBodyRotO = body;
            e.yHeadRot = e.yHeadRotO = body + rel;
        } else {
            // voando mole: roda no ar em volta do peito
            float age = flight.age(pt);
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            LAST.clear();
            FLYING.clear();
            return;
        }
        if (mc.isPaused()) return;
        clock++;
        if (clock % 200 == 0) LAST.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player pl) || !tongueOut(pl));
        List<Integer> done = new ArrayList<>();
        for (Map.Entry<Integer, Flight> en : FLYING.entrySet()) {
            Flight f = en.getValue();
            Entity e = mc.level.getEntity(en.getKey());
            float age = f.age(0F);
            if (e == null || age > f.ticks) {
                done.add(en.getKey());
                continue;
            }
            if (!e.onGround() && age > 1) f.airborne = true;
            if (f.airborne && (e.onGround() || e.isInWater()) && age > 4) done.add(en.getKey());
        }
        done.forEach(FLYING::remove);
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
        Flight f = FLYING.get(mc.player.getId());
        if (f == null) return;
        float age = f.age((float) event.getPartialTick());
        float fade = Math.max(0F, 1F - age / f.ticks);
        event.setRoll(event.getRoll() + 28F * fade * Mth.sin(age * 0.45F));
    }

    // ---------------------------------------------------------------- desenho (o mesmo do mod da IA)

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
                float hw0 = HW_MOUTH * baseScale;
                float hw = hw0 + (hwEnd - hw0) * tm;
                float ht = HT_MID + (HT_BASE * baseScale - HT_MID) * Math.max(0F, 1F - tm / BASE_LEN);
                box(vc, pose, prev, u, v, w, -0.02F, len + 0.02F, -ht, ht, -hw, hw, 2, tm < 0.5F ? lightA : lightB);
            }
            prev.set(q1);
        }
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
            quad(vc, pose, o, a0, lo0, hi0, aw, w0, w1, at, tHi, TONE[Math.max(0, up - shift)], light);
            quad(vc, pose, o, a0, lo0, hi0, aw, w0, w1, at, tLo, TONE[Math.max(0, down - shift)], light);
        }
        quad(vc, pose, o, a0, lo0, hi0, at, tLo, tHi, aw, wHi, TONE[Math.max(0, tone(aw.x, aw.y, aw.z) - 1)], light);
        quad(vc, pose, o, a0, lo0, hi0, at, tLo, tHi, aw, wLo, TONE[Math.max(0, tone(-aw.x, -aw.y, -aw.z) - 1)], light);
    }

    private static int tone(float nx, float ny, float nz) {
        float d0 = Math.max(0F, nx * L0.x + ny * L0.y + nz * L0.z), d1 = Math.max(0F, nx * L1.x + ny * L1.y + nz * L1.z);
        float sh = Math.min(1F, 0.4F + 0.6F * (d0 + d1));
        return sh > 0.88F ? 2 : sh > 0.6F ? 1 : 0;
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vector3f o, Vector3f a0, float l0, float h0,
                             Vector3f b, float bl, float bh, Vector3f cAxis, float c, int[] col, int light) {
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        float ox = o.x + cAxis.x * c, oy = o.y + cAxis.y * c, oz = o.z + cAxis.z * c;
        for (int i = 0; i < 4; i++) {
            float s0 = i == 0 || i == 3 ? l0 : h0, s1 = i < 2 ? bl : bh;
            vc.vertex(m, ox + a0.x * s0 + b.x * s1, oy + a0.y * s0 + b.y * s1, oz + a0.z * s0 + b.z * s1)
                    .color(col[0], col[1], col[2], 255).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light).normal(nm, 0F, 1F, 0F).endVertex();
        }
    }
}
