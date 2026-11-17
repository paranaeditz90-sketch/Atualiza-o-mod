package com.froggydude.client.pieces;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.Ragdoll;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (v0.7.40) O corpo MOLE do ragdoll do tiro (entity.Ragdoll; LM: "ragdoll é ragdoll mesmo, nada de coisa duro").
 * <p>
 * Física de verdade, feita aqui (Java puro, roda no celular): as juntas são pontos com inércia (Verlet, 4 passos por
 * tick), ligados por varetas do tamanho dos ossos do modelo: o tronco é rígido (casamento de forma), o pescoço, os
 * ombros e o quadril giram soltos dentro de limites de gente (a cabeça até 70° do tronco, a coxa não vai pra trás, o
 * joelho só dobra pra trás), e nada atravessa o tronco. Cada ponto bate nos blocos (a caixa de colisão de verdade de
 * cada bloco), com atrito e um quique pequeno; batida forte faz o barulho do bloco. Daí sai a pose de cada parte (cabeça,
 * tronco, braços, coxas, canelas), desenhada com os ossos do próprio modelo (como os pedaços do renascer).
 * <p>
 * O servidor manda só o tiro (direção, força) e as fases; o corpo segue a posição do servidor (o mob voa de verdade lá)
 * com um puxão suave. Pra levantar: o corpo vai em {@link Ragdoll#BLEND} ticks pra pose de bruços (a do "revive_lie"),
 * e o modelo normal assume com a animação "ragdoll_getup", girando do rumo em que caiu pro rumo do mob.
 */
public final class RagdollScene {

    private static final Map<Integer, RagdollScene> SCENES = new HashMap<>();
    private static ClientLevel sceneLevel;

    private static final float S = FroggydudeEntity.MODEL_SCALE;
    private static final float PX = S / 16F;
    private static final int SUB = 4;
    private static final int ITER = 6;
    private static final float H = 1F / SUB;
    /** A gravidade do mob (0,08 bloco/tick²) por passo; o arrasto do ar dele (0,91 no chão, 0,98 na vertical) por passo. */
    private static final float GRAV = 0.08F * H * H;
    private static final float AIR_H = (float) Math.pow(0.91D, H);
    private static final float AIR_V = (float) Math.pow(0.98D, H);
    private static final float FRICTION = 0.3F;
    private static final float BOUNCE = 0.2F;
    /** Quantos ticks ele simula de uma vez quando aparece na tela atrasado (o resto é pulado). */
    private static final int CATCH_UP = 60;

    // as juntas
    static final int HIP_R = 0, HIP_L = 1, SH_R = 2, SH_L = 3, NECK = 4, FT = 5, FB = 6, BT = 7, BB = 8, HEAD = 9,
            HAND_R = 10, HAND_L = 11, KNEE_R = 12, KNEE_L = 13, FOOT_R = 14, FOOT_L = 15, N = 16;
    private static final int[] TORSO = {HIP_R, HIP_L, SH_R, SH_L, NECK, FT, FB, BT, BB};
    /** Raio (px do modelo) e inverso da massa de cada junta. */
    private static final float[] RADIUS_PX = {2.0F, 2.0F, 2.0F, 2.0F, 1.5F, 1.2F, 1.2F, 1.2F, 1.2F, 4.2F,
            2.0F, 2.0F, 2.0F, 2.0F, 2.0F, 2.0F};
    private static final float[] INV_MASS = {0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 1.0F,
            1.2F, 1.2F, 1.0F, 1.0F, 1.0F, 1.0F};
    /** As varetas (os ossos soltos). */
    private static final int[][] STICKS = {{NECK, HEAD}, {SH_R, HAND_R}, {SH_L, HAND_L}, {HIP_R, KNEE_R},
            {KNEE_R, FOOT_R}, {HIP_L, KNEE_L}, {KNEE_L, FOOT_L}};

    private static final Vector3f UP = new Vector3f(0, 1, 0), DOWN = new Vector3f(0, -1, 0);
    private static final float HEAD_CONE = (float) Math.cos(Math.toRadians(70D));
    private static final float HEAD_SIN = (float) Math.sin(Math.toRadians(70D));

    /** As partes desenhadas: os ossos de cada uma (o que é outra parte fica escondido). */
    private enum Part {
        TORSO(new String[]{"body"}, new String[]{"head", "right_arm", "left_arm"}),
        HEAD(new String[]{"head"}, new String[0]),
        RARM(new String[]{"right_arm"}, new String[0]),
        LARM(new String[]{"left_arm"}, new String[0]),
        RTHIGH(new String[]{"right_leg"}, new String[]{"right_shin"}),
        RSHIN(new String[]{"right_shin"}, new String[0]),
        LTHIGH(new String[]{"left_leg"}, new String[]{"left_shin"}),
        LSHIN(new String[]{"left_shin"}, new String[0]);

        final String[] bones;
        final String[] hide;

        Part(String[] bones, String[] hide) {
            this.bones = bones;
            this.hide = hide;
        }
    }

    // ---------------------------------------------------------------- a geometria (do modelo, uma vez)

    /** As juntas na pose de descanso (px do modelo, espaço do GeckoLib). */
    private static final Vector3f[] REST = new Vector3f[N];
    /** O meio do tronco (px) e onde cada junta do tronco fica em volta dele (blocos, na escala). */
    private static final Vector3f TORSO_C = new Vector3f();
    private static final Vector3f[] TORSO_R = new Vector3f[TORSO.length];
    /** A caixa do tronco (px). */
    private static final float[] BODY_BOX = new float[6];
    private static final float[] LEN = new float[STICKS.length];
    private static float sideR = 1F;
    private static float restComY;
    private static boolean geometry;

    public static boolean hasGeometry() {
        return geometry;
    }

    /** Lê as juntas dos ossos do modelo do GeckoLib (o renderer chama na primeira vez). */
    public static void geometry(BakedGeoModel model) {
        if (geometry) return;
        float[] body = box(model, "body");
        float[] head = box(model, "head");
        float[] rArm = box(model, "right_arm");
        float[] lArm = box(model, "left_arm");
        float[] rShin = box(model, "right_shin");
        float[] lShin = box(model, "left_shin");
        Vector3f hipR = pivot(model, "right_leg", 1.9F, 12F), hipL = pivot(model, "left_leg", -1.9F, 12F);
        Vector3f shR = pivot(model, "right_arm", 5F, 22F), shL = pivot(model, "left_arm", -5F, 22F);
        Vector3f kneeR = pivot(model, "right_shin", 1.9F, 6F), kneeL = pivot(model, "left_shin", -1.9F, 6F);
        Vector3f neck = pivot(model, "head", 0F, 24F);
        float cx = (body[0] + body[3]) / 2F, cy = (body[1] + body[4]) / 2F, cz = (body[2] + body[5]) / 2F;
        float dy = (body[4] - body[1]) * 0.25F;
        REST[HIP_R] = hipR;
        REST[HIP_L] = hipL;
        REST[SH_R] = shR;
        REST[SH_L] = shL;
        REST[NECK] = neck;
        REST[FT] = new Vector3f(cx, cy + dy, cz - 1F);
        REST[FB] = new Vector3f(cx, cy - dy, cz - 1F);
        REST[BT] = new Vector3f(cx, cy + dy, cz + 1F);
        REST[BB] = new Vector3f(cx, cy - dy, cz + 1F);
        REST[HEAD] = new Vector3f(neck.x, (head[1] + head[4]) / 2F, (head[2] + head[5]) / 2F);
        REST[HAND_R] = new Vector3f(shR.x, rArm[1], shR.z);
        REST[HAND_L] = new Vector3f(shL.x, lArm[1], shL.z);
        REST[KNEE_R] = kneeR;
        REST[KNEE_L] = kneeL;
        REST[FOOT_R] = new Vector3f(kneeR.x, rShin[1], kneeR.z);
        REST[FOOT_L] = new Vector3f(kneeL.x, lShin[1], kneeL.z);
        System.arraycopy(body, 0, BODY_BOX, 0, 6);
        TORSO_C.zero();
        for (int i : TORSO) TORSO_C.add(REST[i]);
        TORSO_C.div(TORSO.length);
        for (int k = 0; k < TORSO.length; k++) TORSO_R[k] = new Vector3f(REST[TORSO[k]]).sub(TORSO_C).mul(PX);
        for (int k = 0; k < STICKS.length; k++) LEN[k] = REST[STICKS[k][0]].distance(REST[STICKS[k][1]]) * PX;
        sideR = Math.signum(hipR.x - hipL.x);
        if (sideR == 0F) sideR = 1F;
        float sum = 0F, m = 0F;
        for (int i = 0; i < N; i++) {
            sum += REST[i].y / INV_MASS[i];
            m += 1F / INV_MASS[i];
        }
        restComY = sum / m * PX;
        geometry = true;
    }

    private static Vector3f pivot(BakedGeoModel model, String name, float x, float y) {
        GeoBone b = model.getBone(name).orElse(null);
        return b == null ? new Vector3f(x, y, 0F) : new Vector3f(b.getPivotX(), b.getPivotY(), b.getPivotZ());
    }

    /** A caixa (px) dos cubos do próprio osso (sem os filhos e sem a camada de fora). */
    private static float[] box(BakedGeoModel model, String name) {
        float[] out = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        GeoBone b = model.getBone(name).orElse(null);
        if (b != null) {
            for (GeoCube cube : b.getCubes()) {
                if (cube.inflate() > 0.01D) continue;
                for (GeoQuad q : cube.quads()) {
                    if (q == null) continue;
                    for (GeoVertex v : q.vertices()) {
                        Vector3f p = v.position();
                        out[0] = Math.min(out[0], p.x * 16F);
                        out[1] = Math.min(out[1], p.y * 16F);
                        out[2] = Math.min(out[2], p.z * 16F);
                        out[3] = Math.max(out[3], p.x * 16F);
                        out[4] = Math.max(out[4], p.y * 16F);
                        out[5] = Math.max(out[5], p.z * 16F);
                    }
                }
            }
        }
        if (out[0] > out[3]) return new float[]{-4F, 12F, -2F, 4F, 24F, 2F};
        return out;
    }

    public static String[] bonesOf(int part) {
        return Part.values()[part].bones;
    }

    public static String[] hiddenOf(int part) {
        return Part.values()[part].hide;
    }

    // ---------------------------------------------------------------- a cena de cada FroggyDude

    private final int entityId;
    private final Ragdoll.Params p;
    private final RandomSource rand;
    private final float[] x = new float[N], y = new float[N], z = new float[N];
    private final float[] px = new float[N], py = new float[N], pz = new float[N];
    private final float[] ox = new float[N], oy = new float[N], oz = new float[N];
    private final float[] nx = new float[N], ny = new float[N], nz = new float[N];
    /** O deslocamento de cada junta no passo, antes das varetas e do chão (a velocidade "de verdade" contra o chão). */
    private final float[] vx0 = new float[N], vy0 = new float[N], vz0 = new float[N];
    private final boolean[] contact = new boolean[N];
    private final float[] impact = new float[N];
    private final int[] lastThump = new int[N];
    /** A origem (blocos do mundo) dos números acima: perto dele, pra float não perder precisão longe do 0. */
    private double baseX, baseY, baseZ;
    private final Quaternionf q = new Quaternionf(), qo = new Quaternionf();
    private long simTick = Long.MIN_VALUE;
    private int still;
    private boolean asleep;
    private final List<AABB> boxes = new ArrayList<>();
    // o levantar: a pose de bruços (calculada uma vez quando começa)
    private boolean proneKnown;
    private float proneYaw;
    private double proneX, proneY, proneZ;
    private final Quaternionf proneQ = new Quaternionf();
    // rascunhos
    private final Vector3f t1 = new Vector3f(), t2 = new Vector3f(), t3 = new Vector3f();
    private final Quaternionf qa = new Quaternionf(), qb = new Quaternionf(), qInv = new Quaternionf();

    private RagdollScene(FroggydudeEntity f, Ragdoll.Params p) {
        this.entityId = f.getId();
        this.p = p;
        this.rand = RandomSource.create(p.seed());
        java.util.Arrays.fill(lastThump, -100);
    }

    /** A cena dele (cria se começou um ragdoll novo); null se não está mole. */
    @Nullable
    public static RagdollScene of(FroggydudeEntity f) {
        if (f.getFroggyState() != FroggyState.RAGDOLL) {
            SCENES.remove(f.getId());
            return null;
        }
        Ragdoll.Params rp = f.ragdollParams();
        if (rp == null) return null;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != sceneLevel) {
            SCENES.clear();
            sceneLevel = level;
        }
        RagdollScene s = SCENES.get(f.getId());
        if (s == null || s.p.t0() != rp.t0() || s.p.seed() != rp.seed()) {
            s = new RagdollScene(f, rp);
            SCENES.put(f.getId(), s);
        }
        return s;
    }

    /** A fase em que o corpo mole aparece (o voo, deitado e a ida pra pose de bruços); depois é o modelo normal. */
    public static boolean physicsPhase(FroggydudeEntity f, float partial) {
        Ragdoll.Params rp = f.ragdollParams();
        if (rp == null || !rp.rising()) return true;
        return f.level().getGameTime() + partial - rp.upAt() < Ragdoll.BLEND;
    }

    /** Bala nele caído: um tranco no corpo (a cabeça, o tronco ou um braço), pra longe de quem atirou. */
    public static void hit(FroggydudeEntity f, @Nullable Entity from) {
        RagdollScene s = SCENES.get(f.getId());
        Ragdoll.Params cur = f.ragdollParams();
        if (s == null || s.simTick == Long.MIN_VALUE || cur == null || cur.rising()) return;
        Vec3 d = from == null ? null : f.position().subtract(from.position());
        float dx, dz;
        if (d == null || d.x * d.x + d.z * d.z < 1.0E-4D) {
            float a = s.rand.nextFloat() * Mth.TWO_PI;
            dx = Mth.cos(a);
            dz = Mth.sin(a);
        } else {
            float l = (float) Math.sqrt(d.x * d.x + d.z * d.z);
            dx = (float) d.x / l;
            dz = (float) d.z / l;
        }
        int[] targets = {HEAD, NECK, FT, BT, HAND_R, HAND_L, SH_R, SH_L};
        for (int k = 0; k < 2; k++) {
            int i = targets[s.rand.nextInt(targets.length)];
            float kick = (0.10F + 0.08F * s.rand.nextFloat()) * H;
            s.px[i] -= dx * kick;
            s.pz[i] -= dz * kick;
            s.py[i] -= 0.06F * H;
        }
        s.asleep = false;
        s.still = 0;
    }

    // ---------------------------------------------------------------- simular

    /** Começa em pé (a pose de descanso, no rumo do corpo dele) com o empurrão do tiro e o giro pra trás. */
    private void start(FroggydudeEntity f, long now) {
        baseX = f.getX();
        baseY = f.getY();
        baseZ = f.getZ();
        q.rotationY((180F - f.yBodyRot) * Mth.DEG_TO_RAD);
        qo.set(q);
        long late = Math.max(0L, now - p.t0());
        Ragdoll.Params cur = f.ragdollParams();
        boolean flying = (cur == null || !cur.lying()) && late < 12;
        // o empurrão que ainda sobra (o pacote chegou uns ticks depois do tiro)
        float hv = flying ? p.speed() * (float) Math.pow(0.91D, late) : 0F;
        float vy = flying ? p.up() - 0.08F * late : 0F;
        float dx = p.dx(), dz = p.dz();
        for (int i = 0; i < N; i++) {
            t1.set(REST[i]).mul(PX).rotate(q);
            x[i] = t1.x;
            y[i] = t1.y + 2.2F * PX; // os pés (bolinhas de 2 px) não nascem dentro do chão
            z[i] = t1.z;
            float vx = dx * hv, vyy = vy, vz = dz * hv;
            if (flying) {
                // a bala no peito: em cima vai mais rápido que embaixo -> cambalhota pra trás
                float h = REST[i].y * PX - restComY;
                float spin = 0.28F + 0.08F * rand.nextFloat();
                vx += dx * spin * h;
                vz += dz * spin * h;
                vyy += 0.05F * h;
                if (i >= HAND_R) {
                    // braços e pernas soltos: cada um pra um lado
                    vx += (rand.nextFloat() - 0.5F) * 0.22F;
                    vyy += (rand.nextFloat() - 0.5F) * 0.16F;
                    vz += (rand.nextFloat() - 0.5F) * 0.22F;
                }
            }
            px[i] = x[i] - vx * H;
            py[i] = y[i] - vyy * H;
            pz[i] = z[i] - vz * H;
        }
        if (flying) {
            // um tico de giro de lado (nunca cai igual)
            float w = (rand.nextFloat() - 0.5F) * 0.3F * H;
            for (int i = 0; i < N; i++) {
                px[i] += z[i] * w;
                pz[i] -= x[i] * w;
            }
        }
        snapshot();
        simTick = now;
    }

    private void snapshot() {
        System.arraycopy(x, 0, ox, 0, N);
        System.arraycopy(y, 0, oy, 0, N);
        System.arraycopy(z, 0, oz, 0, N);
        qo.set(q);
    }

    /** Anda até o tick de agora (o render chama; fora da tela não gasta nada). */
    private void advance(FroggydudeEntity f) {
        ClientLevel level = (ClientLevel) f.level();
        long now = level.getGameTime();
        if (simTick == Long.MIN_VALUE) start(f, now);
        if (now - simTick > CATCH_UP) simTick = now - CATCH_UP;
        while (simTick < now) {
            simTick++;
            // levantando: o corpo para onde está (a ida pra pose de bruços é no desenho)
            Ragdoll.Params cur = f.ragdollParams();
            if (cur != null && cur.rising() && simTick > cur.upAt()) {
                snapshot();
                continue;
            }
            step(f, level, simTick == now);
        }
    }

    private void step(FroggydudeEntity f, ClientLevel level, boolean live) {
        snapshot();
        Ragdoll.Params cur = f.ragdollParams();
        boolean lying = cur != null && cur.lying();
        follow(f, lying);
        if (asleep) return;
        gatherBoxes(level);
        java.util.Arrays.fill(impact, 0F);
        for (int s = 0; s < SUB; s++) {
            integrate();
            java.util.Arrays.fill(contact, false);
            for (int it = 0; it < ITER; it++) {
                sticks();
                limits();
                matchTorso(3);
                collide();
            }
            friction();
        }
        if (live) thumps(level, f);
        // parado de vez: dorme (acorda com bala)
        float move = 0F;
        for (int i = 0; i < N; i++) {
            move = Math.max(move, Math.abs(x[i] - ox[i]) + Math.abs(y[i] - oy[i]) + Math.abs(z[i] - oz[i]));
        }
        still = move < 0.003F ? still + 1 : 0;
        if (lying && still > 20) asleep = true;
    }

    /** O corpo segue o mob do servidor (que voa de verdade): um puxão no meio do corpo, sem mexer na velocidade. */
    private void follow(FroggydudeEntity f, boolean lying) {
        float cx = 0F, cy = 0F, cz = 0F, m = 0F;
        for (int i = 0; i < N; i++) {
            float w = 1F / INV_MASS[i];
            cx += x[i] * w;
            cy += y[i] * w;
            cz += z[i] * w;
            m += w;
        }
        cx /= m;
        cy /= m;
        cz /= m;
        float ex = (float) (f.getX() - baseX) - cx, ez = (float) (f.getZ() - baseZ) - cz;
        // a altura só quando foge muito (a caixa do mob em pé e o corpo deitado no ar nunca batem certinho)
        float ey = lying ? 0F : (float) (f.getY() - baseY) + restComY - cy;
        ey = Math.signum(ey) * Math.max(0F, Math.abs(ey) - 0.6F);
        float d2 = ex * ex + ey * ey + ez * ez;
        float k;
        if (d2 > 16F) {
            k = 1F; // longe demais (teleporte, chunk que voltou): vai direto
            asleep = false;
        } else {
            k = lying ? 0.08F : 0.12F;
            if (lying) ey = 0F;
            else ey *= 0.5F;
        }
        if (d2 < 1.0E-6F) return;
        for (int i = 0; i < N; i++) {
            x[i] += ex * k;
            px[i] += ex * k;
            y[i] += ey * k;
            py[i] += ey * k;
            z[i] += ez * k;
            pz[i] += ez * k;
            ox[i] += ex * k;
            oy[i] += ey * k;
            oz[i] += ez * k;
        }
        if (k >= 1F || (lying && d2 > 0.25F)) asleep = false;
    }

    private void integrate() {
        for (int i = 0; i < N; i++) {
            float vx = (x[i] - px[i]) * AIR_H, vy = (y[i] - py[i]) * AIR_V, vz = (z[i] - pz[i]) * AIR_H;
            px[i] = x[i];
            py[i] = y[i];
            pz[i] = z[i];
            x[i] += vx;
            y[i] += vy - GRAV;
            z[i] += vz;
            vx0[i] = vx;
            vy0[i] = vy - GRAV;
            vz0[i] = vz;
        }
    }

    private void sticks() {
        for (int k = 0; k < STICKS.length; k++) {
            int a = STICKS[k][0], b = STICKS[k][1];
            float dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a];
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1.0E-6F) continue;
            float wa = INV_MASS[a], wb = INV_MASS[b];
            float c = (d - LEN[k]) / (d * (wa + wb));
            x[a] += dx * c * wa;
            y[a] += dy * c * wa;
            z[a] += dz * c * wa;
            x[b] -= dx * c * wb;
            y[b] -= dy * c * wb;
            z[b] -= dz * c * wb;
        }
    }

    /** O meio do tronco agora (blocos, relativo à base). */
    private Vector3f torsoCenter(Vector3f out) {
        out.zero();
        for (int i : TORSO) out.add(x[i], y[i], z[i]);
        return out.div(TORSO.length);
    }

    /** Os limites de gente: a cabeça num cone, coxa e joelho em ângulos de verdade, nada dentro do tronco. */
    private void limits() {
        q.conjugate(qInv);
        // a cabeça: até 70° do eixo do tronco
        t1.set(x[HEAD] - x[NECK], y[HEAD] - y[NECK], z[HEAD] - z[NECK]).rotate(qInv);
        float len = t1.length();
        if (len > 1.0E-6F && t1.y / len < HEAD_CONE) {
            float h = (float) Math.sqrt(t1.x * t1.x + t1.z * t1.z);
            if (h > 1.0E-6F) {
                t1.set(t1.x / h * HEAD_SIN, HEAD_CONE, t1.z / h * HEAD_SIN).mul(LEN[0]).rotate(q);
                x[HEAD] = x[NECK] + t1.x;
                y[HEAD] = y[NECK] + t1.y;
                z[HEAD] = z[NECK] + t1.z;
            }
        }
        leg(HIP_R, KNEE_R, FOOT_R, sideR, LEN[3], LEN[4]);
        leg(HIP_L, KNEE_L, FOOT_L, -sideR, LEN[5], LEN[6]);
        Vector3f c = torsoCenter(t3);
        for (int i : new int[]{HAND_R, HAND_L, KNEE_R, KNEE_L, FOOT_R, FOOT_L}) outsideTorso(i, c);
    }

    /** A coxa: pra frente até 115°, pra trás até 30°, pra fora até 50°, pra dentro até 10°. O joelho: só pra trás, até 150°. */
    private void leg(int hip, int knee, int foot, float side, float thighLen, float shinLen) {
        t1.set(x[knee] - x[hip], y[knee] - y[hip], z[knee] - z[hip]).rotate(qInv);
        float l = t1.length();
        if (l < 1.0E-6F) return;
        t1.div(l);
        float flex = (float) Math.atan2(-t1.z, -t1.y);
        float abd = (float) Math.asin(Mth.clamp(t1.x * side, -1F, 1F));
        float f2 = Mth.clamp(flex, -30F * Mth.DEG_TO_RAD, 115F * Mth.DEG_TO_RAD);
        float a2 = Mth.clamp(abd, -10F * Mth.DEG_TO_RAD, 50F * Mth.DEG_TO_RAD);
        if (f2 != flex || a2 != abd) {
            float ca = Mth.cos(a2);
            t1.set(side * Mth.sin(a2), -ca * Mth.cos(f2), -ca * Mth.sin(f2));
            t2.set(t1).mul(thighLen).rotate(q);
            x[knee] = x[hip] + t2.x;
            y[knee] = y[hip] + t2.y;
            z[knee] = z[hip] + t2.z;
        }
        // o joelho, no espaço da coxa
        qa.rotationTo(DOWN, t1).premul(q);
        qa.conjugate(qb);
        t2.set(x[foot] - x[knee], y[foot] - y[knee], z[foot] - z[knee]).rotate(qb);
        float ls = t2.length();
        if (ls < 1.0E-6F) return;
        t2.div(ls);
        float kf = (float) Math.atan2(t2.z, -t2.y);
        float ka = (float) Math.asin(Mth.clamp(t2.x * side, -1F, 1F));
        float kf2 = Mth.clamp(kf, 0F, 150F * Mth.DEG_TO_RAD);
        float ka2 = Mth.clamp(ka, -8F * Mth.DEG_TO_RAD, 8F * Mth.DEG_TO_RAD);
        if (kf2 != kf || ka2 != ka) {
            float ca = Mth.cos(ka2);
            t2.set(side * Mth.sin(ka2), -ca * Mth.cos(kf2), ca * Mth.sin(kf2)).mul(shinLen).rotate(qa);
            x[foot] = x[knee] + t2.x;
            y[foot] = y[knee] + t2.y;
            z[foot] = z[knee] + t2.z;
        }
    }

    /** A junta i não entra no tronco: sai pelo lado mais perto. */
    private void outsideTorso(int i, Vector3f c) {
        float r = RADIUS_PX[i] * 0.5F;
        t1.set(x[i] - c.x, y[i] - c.y, z[i] - c.z).rotate(qInv).div(PX).add(TORSO_C);
        float x0 = BODY_BOX[0] - r, x1 = BODY_BOX[3] + r, y0 = BODY_BOX[1] - r, y1 = BODY_BOX[4], z0 = BODY_BOX[2] - r,
                z1 = BODY_BOX[5] + r;
        if (t1.x <= x0 || t1.x >= x1 || t1.y <= y0 || t1.y >= y1 || t1.z <= z0 || t1.z >= z1) return;
        float best = t1.x - x0;
        int axis = 0;
        if (x1 - t1.x < best) { best = x1 - t1.x; axis = 1; }
        if (t1.z - z0 < best) { best = t1.z - z0; axis = 2; }
        if (z1 - t1.z < best) { best = z1 - t1.z; axis = 3; }
        if (t1.y - y0 < best) { axis = 4; }
        switch (axis) {
            case 0 -> t1.x = x0;
            case 1 -> t1.x = x1;
            case 2 -> t1.z = z0;
            case 3 -> t1.z = z1;
            default -> t1.y = y0;
        }
        t1.sub(TORSO_C).mul(PX).rotate(q).add(c);
        x[i] = t1.x;
        y[i] = t1.y;
        z[i] = t1.z;
    }

    /**
     * O tronco rígido: a rotação que melhor encaixa as juntas dele na pose de descanso (Müller et al. 2016, "A Robust
     * Method to Extract the Rotational Part of Deformations"), e as juntas vão pra lá.
     */
    private void matchTorso(int iters) {
        Vector3f c = torsoCenter(t3);
        Matrix3f a = new Matrix3f().zero();
        for (int k = 0; k < TORSO.length; k++) {
            int i = TORSO[k];
            float dx = x[i] - c.x, dy = y[i] - c.y, dz = z[i] - c.z;
            Vector3f r = TORSO_R[k];
            // a += d * r^T (coluna j = d * r_j)
            a.m00 += dx * r.x; a.m01 += dy * r.x; a.m02 += dz * r.x;
            a.m10 += dx * r.y; a.m11 += dy * r.y; a.m12 += dz * r.y;
            a.m20 += dx * r.z; a.m21 += dy * r.z; a.m22 += dz * r.z;
        }
        Matrix3f rm = new Matrix3f();
        Vector3f col = new Vector3f(), ac = new Vector3f(), omega = new Vector3f();
        for (int it = 0; it < iters; it++) {
            rm.set(q);
            omega.zero();
            float dot = 0F;
            for (int j = 0; j < 3; j++) {
                rm.getColumn(j, col);
                a.getColumn(j, ac);
                omega.add(col.y * ac.z - col.z * ac.y, col.z * ac.x - col.x * ac.z, col.x * ac.y - col.y * ac.x);
                dot += col.dot(ac);
            }
            omega.div(Math.abs(dot) + 1.0E-9F);
            float w = omega.length();
            if (w < 1.0E-7F) break;
            qa.rotationAxis(w, omega.x / w, omega.y / w, omega.z / w);
            q.premul(qa).normalize();
        }
        for (int k = 0; k < TORSO.length; k++) {
            int i = TORSO[k];
            t1.set(TORSO_R[k]).rotate(q);
            x[i] = c.x + t1.x;
            y[i] = c.y + t1.y;
            z[i] = c.z + t1.z;
        }
    }

    /** As caixas de colisão dos blocos em volta do corpo (uma vez por tick). */
    private void gatherBoxes(ClientLevel level) {
        boxes.clear();
        float mnx = Float.MAX_VALUE, mny = Float.MAX_VALUE, mnz = Float.MAX_VALUE;
        float mxx = -Float.MAX_VALUE, mxy = -Float.MAX_VALUE, mxz = -Float.MAX_VALUE;
        for (int i = 0; i < N; i++) {
            float vx = Math.abs(x[i] - px[i]) * SUB, vy = Math.abs(y[i] - py[i]) * SUB, vz = Math.abs(z[i] - pz[i]) * SUB;
            mnx = Math.min(mnx, x[i] - vx);
            mny = Math.min(mny, y[i] - vy);
            mnz = Math.min(mnz, z[i] - vz);
            mxx = Math.max(mxx, x[i] + vx);
            mxy = Math.max(mxy, y[i] + vy);
            mxz = Math.max(mxz, z[i] + vz);
        }
        AABB area = new AABB(mnx + baseX, mny + baseY, mnz + baseZ, mxx + baseX, mxy + baseY, mxz + baseZ).inflate(0.6D);
        if (area.getSize() > 24D) area = area.deflate((area.getSize() - 24D) / 2D);
        for (VoxelShape shape : level.getBlockCollisions(null, area)) {
            for (AABB b : shape.toAabbs()) {
                boxes.add(b.move(-baseX, -baseY, -baseZ));
                if (boxes.size() > 400) return;
            }
        }
    }

    /** Cada junta (uma bolinha) sai de dentro dos blocos pela saída mais curta. */
    private void collide() {
        for (int i = 0; i < N; i++) {
            float r = RADIUS_PX[i] * PX;
            for (AABB b : boxes) {
                if (x[i] + r <= b.minX || x[i] - r >= b.maxX || y[i] + r <= b.minY || y[i] - r >= b.maxY
                        || z[i] + r <= b.minZ || z[i] - r >= b.maxZ) {
                    continue;
                }
                float cx = (float) Mth.clamp(x[i], b.minX, b.maxX), cy = (float) Mth.clamp(y[i], b.minY, b.maxY);
                float cz = (float) Mth.clamp(z[i], b.minZ, b.maxZ);
                float dx = x[i] - cx, dy = y[i] - cy, dz = z[i] - cz;
                float d2 = dx * dx + dy * dy + dz * dz;
                float nX, nY, nZ, pen;
                if (d2 > 1.0E-10F) {
                    if (d2 >= r * r) continue;
                    float d = (float) Math.sqrt(d2);
                    nX = dx / d;
                    nY = dy / d;
                    nZ = dz / d;
                    pen = r - d;
                } else {
                    // o centro entrou: a face mais perto (pra cima ganha empate)
                    pen = (float) (b.maxY - y[i]);
                    nX = 0; nY = 1; nZ = 0;
                    float e;
                    if ((e = (float) (x[i] - b.minX)) < pen) { pen = e; nX = -1; nY = 0; nZ = 0; }
                    if ((e = (float) (b.maxX - x[i])) < pen) { pen = e; nX = 1; nY = 0; nZ = 0; }
                    if ((e = (float) (z[i] - b.minZ)) < pen) { pen = e; nX = 0; nY = 0; nZ = -1; }
                    if ((e = (float) (b.maxZ - z[i])) < pen) { pen = e; nX = 0; nY = 0; nZ = 1; }
                    if ((e = (float) (y[i] - b.minY)) < pen) { pen = e; nX = 0; nY = -1; nZ = 0; }
                    pen += r;
                }
                x[i] += nX * pen;
                y[i] += nY * pen;
                z[i] += nZ * pen;
                contact[i] = true;
                nx[i] = nX;
                ny[i] = nY;
                nz[i] = nZ;
            }
        }
    }

    /**
     * Encostado: escorrega pouco (atrito) e quica um tico. A velocidade contra o chão é a de antes da saída (sair de dentro
     * do bloco não pode virar um pulo). Guarda a força da batida (pro barulho).
     */
    private void friction() {
        for (int i = 0; i < N; i++) {
            if (!contact[i]) continue;
            float vx = x[i] - px[i], vy = y[i] - py[i], vz = z[i] - pz[i];
            float vn = vx * nx[i] + vy * ny[i] + vz * nz[i];
            float tx = vx - vn * nx[i], ty = vy - vn * ny[i], tz = vz - vn * nz[i];
            float before = vx0[i] * nx[i] + vy0[i] * ny[i] + vz0[i] * nz[i];
            if (before < 0F) {
                impact[i] = Math.max(impact[i], -before * SUB);
                vn = -before * BOUNCE;
            } else {
                vn = Math.min(vn, before);
            }
            float keep = 1F - FRICTION;
            px[i] = x[i] - (tx * keep + vn * nx[i]);
            py[i] = y[i] - (ty * keep + vn * ny[i]);
            pz[i] = z[i] - (tz * keep + vn * nz[i]);
        }
    }

    /** O barulho de cada batida forte no chão (o som de queda do próprio bloco). */
    private void thumps(ClientLevel level, FroggydudeEntity f) {
        int made = 0;
        for (int i = 0; i < N && made < 3; i++) {
            if (impact[i] < 0.22F || f.tickCount - lastThump[i] < 6) continue;
            lastThump[i] = f.tickCount;
            made++;
            double wx = x[i] + baseX, wy = y[i] + baseY - RADIUS_PX[i] * PX - 0.05D, wz = z[i] + baseZ;
            BlockState under = level.getBlockState(BlockPos.containing(wx, wy, wz));
            SoundEvent sound = under.isAir() ? net.minecraft.sounds.SoundEvents.PLAYER_SMALL_FALL
                    : under.getSoundType().getFallSound();
            float vol = Mth.clamp(impact[i] * 1.4F, 0.25F, 1.0F);
            level.playLocalSound(wx, wy, wz, sound, SoundSource.HOSTILE, vol, 0.75F + rand.nextFloat() * 0.2F, false);
        }
    }

    // ---------------------------------------------------------------- desenhar

    /** Quem desenha os ossos de uma parte (o renderer, com o modelo do GeckoLib na pose de descanso). */
    public interface BoneDrawer {
        void draw(PoseStack poseStack, int part);
    }

    /**
     * Desenha as partes. O poseStack está na posição de desenho da entidade (eixos do mundo, sem giro); base = essa
     * posição no mundo.
     */
    public void render(FroggydudeEntity f, PoseStack ps, Vec3 base, float partial, BoneDrawer drawer) {
        if (!geometry) return;
        advance(f);
        float[] wx = new float[N], wy = new float[N], wz = new float[N];
        for (int i = 0; i < N; i++) {
            wx[i] = Mth.lerp(partial, ox[i], x[i]);
            wy[i] = Mth.lerp(partial, oy[i], y[i]);
            wz[i] = Mth.lerp(partial, oz[i], z[i]);
        }
        Quaternionf qt = new Quaternionf();
        qo.slerp(q, partial, qt);
        // o levantar: da pose mole pra de bruços
        float k = 0F;
        Ragdoll.Params cur = f.ragdollParams();
        if (cur != null && cur.rising()) {
            if (!proneKnown) prone(f);
            k = smooth((f.level().getGameTime() - cur.upAt() + partial) / Ragdoll.BLEND);
        }
        Vector3f c = new Vector3f();
        for (int i : TORSO) c.add(wx[i], wy[i], wz[i]);
        c.div(TORSO.length);
        Quaternionf qInvT = new Quaternionf(qt).conjugate();
        Vector3f d = new Vector3f();
        Quaternionf r = new Quaternionf(), rThigh = new Quaternionf();
        for (Part part : Part.values()) {
            Vector3f anchorPx;
            Vector3f at = new Vector3f();
            switch (part) {
                case TORSO -> {
                    anchorPx = TORSO_C;
                    at.set(c);
                    r.set(qt);
                }
                case HEAD -> {
                    anchorPx = REST[NECK];
                    at.set(wx[NECK], wy[NECK], wz[NECK]);
                    limb(qt, qInvT, UP, d.set(wx[HEAD] - wx[NECK], wy[HEAD] - wy[NECK], wz[HEAD] - wz[NECK]), r);
                }
                case RARM, LARM -> {
                    int sh = part == Part.RARM ? SH_R : SH_L, hand = part == Part.RARM ? HAND_R : HAND_L;
                    anchorPx = REST[sh];
                    at.set(wx[sh], wy[sh], wz[sh]);
                    limb(qt, qInvT, DOWN, d.set(wx[hand] - wx[sh], wy[hand] - wy[sh], wz[hand] - wz[sh]), r);
                }
                case RTHIGH, LTHIGH -> {
                    int hip = part == Part.RTHIGH ? HIP_R : HIP_L, knee = part == Part.RTHIGH ? KNEE_R : KNEE_L;
                    anchorPx = REST[hip];
                    at.set(wx[hip], wy[hip], wz[hip]);
                    limb(qt, qInvT, DOWN, d.set(wx[knee] - wx[hip], wy[knee] - wy[hip], wz[knee] - wz[hip]), r);
                }
                default -> {
                    int hip = part == Part.RSHIN ? HIP_R : HIP_L, knee = part == Part.RSHIN ? KNEE_R : KNEE_L;
                    int foot = part == Part.RSHIN ? FOOT_R : FOOT_L;
                    anchorPx = REST[knee];
                    at.set(wx[knee], wy[knee], wz[knee]);
                    limb(qt, qInvT, DOWN, d.set(wx[knee] - wx[hip], wy[knee] - wy[hip], wz[knee] - wz[hip]), rThigh);
                    Quaternionf inv = new Quaternionf(rThigh).conjugate();
                    limb(rThigh, inv, DOWN, d.set(wx[foot] - wx[knee], wy[foot] - wy[knee], wz[foot] - wz[knee]), r);
                }
            }
            double ax = at.x + baseX, ay = at.y + baseY, az = at.z + baseZ;
            if (k > 0F) {
                Vector3f pa = new Vector3f(anchorPx).mul(PX).rotate(proneQ);
                ax = Mth.lerp(k, ax, proneX + pa.x);
                ay = Mth.lerp(k, ay, proneY + pa.y + 2F * PX);
                az = Mth.lerp(k, az, proneZ + pa.z);
                r.slerp(proneQ, k);
            }
            ps.pushPose();
            ps.translate(ax - base.x, ay - base.y, az - base.z);
            ps.mulPose(r);
            ps.scale(S, S, S);
            ps.translate(-anchorPx.x / 16F, -anchorPx.y / 16F, -anchorPx.z / 16F);
            drawer.draw(ps, part.ordinal());
            ps.popPose();
        }
    }

    /** A pose de uma parte presa no pai: a rotação do pai vezes o giro mais curto do eixo de descanso pro de agora. */
    private static void limb(Quaternionf parent, Quaternionf parentInv, Vector3f restAxis, Vector3f dir, Quaternionf out) {
        if (dir.lengthSquared() < 1.0E-10F) {
            out.set(parent);
            return;
        }
        dir.normalize().rotate(parentInv);
        out.rotationTo(restAxis, dir).premul(parent);
    }

    /**
     * A pose de bruços pra onde o corpo vai ao levantar: a cabeça pro lado em que o tronco está deitado, o meio do tronco
     * em cima de onde ele está agora, no chão do mob (a mesma do "revive_lie": raiz tombada 90° e 2 px pra cima).
     */
    private void prone(FroggydudeEntity f) {
        proneKnown = true;
        t1.set(0, 1, 0).rotate(q);
        float hx = t1.x, hz = t1.z;
        if (hx * hx + hz * hz < 0.04F) {
            t1.set(0, 0, -1).rotate(q);
            hx = t1.x;
            hz = t1.z;
        }
        proneYaw = (float) (Math.atan2(-hx, hz) * Mth.RAD_TO_DEG);
        proneQ.rotationY((180F - proneYaw) * Mth.DEG_TO_RAD).rotateX(-Mth.HALF_PI);
        Vector3f c = torsoCenter(new Vector3f());
        Vector3f off = new Vector3f(TORSO_C).mul(PX).rotate(proneQ);
        proneX = c.x + baseX - off.x;
        proneZ = c.z + baseZ - off.z;
        proneY = f.getY();
    }

    /**
     * O modelo normal levantando ("ragdoll_getup"): {deslocamento x, deslocamento z, rumo} pro renderer, do lugar e do
     * rumo em que caiu até os do mob. null = nada a mudar.
     */
    @Nullable
    public static float[] getup(FroggydudeEntity f, float partial) {
        if (f.getFroggyState() != FroggyState.RAGDOLL) return null;
        RagdollScene s = SCENES.get(f.getId());
        Ragdoll.Params cur = f.ragdollParams();
        if (s == null || cur == null || !cur.rising() || !s.proneKnown) return null;
        float t = f.level().getGameTime() - cur.upAt() + partial - Ragdoll.BLEND;
        if (t < 0F) return null;
        double ex = Mth.lerp(partial, f.xo, f.getX()), ez = Mth.lerp(partial, f.zo, f.getZ());
        float kOff = 1F - smooth((t - 10F) / 18F);
        float kYaw = smooth((t - 14F) / 17F);
        float body = Mth.rotLerp(partial, f.yBodyRotO, f.yBodyRot);
        float yaw = s.proneYaw + Mth.wrapDegrees(body - s.proneYaw) * kYaw;
        return new float[]{(float) (s.proneX - ex) * kOff, (float) (s.proneZ - ez) * kOff, yaw};
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0F, 1F);
        return k * k * (3F - 2F * k);
    }

    public static void clear() {
        SCENES.clear();
    }
}
