package com.froggydude.client.pieces;

import com.froggydude.entity.Ragdoll;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (v0.7.51, LM 23/11: "O ragdoll tá com o boneco todo duro", "o jogador ter realmente ragdoll junto com o FroggyDude")
 * o corpo MOLE de quem leva o bote com ragdoll: a mesma física do ragdoll do FroggyDude ({@link RagdollScene}: juntas com
 * inércia, varetas do tamanho dos ossos, tronco rígido, cabeça num cone, pernas em ângulos de gente, cada junta batendo
 * nos blocos), com as medidas do modelo do jogador (perna sem joelho: a perna inteira é uma vareta). As partes são as do
 * próprio modelo do jogador (com o skin e a camada de fora: chapéu, jaqueta, mangas e calça), cada uma onde a física pôs.
 * <p>
 * O corpo segue a posição do jogador (ele voa de verdade, pelo empurrão do servidor) com um puxão suave; o empurrão e o
 * rumo vêm dos números do ragdoll do FroggyDude que deu o bote (os dois levam o mesmo). Só o desenho: armadura e item na
 * mão não aparecem nesse 1,5 s.
 */
public final class PlayerRagdollScene {

    private static final Map<Integer, PlayerRagdollScene> SCENES = new HashMap<>();
    private static ClientLevel sceneLevel;

    /** O jogador é desenhado em 0,9375 (PlayerRenderer). */
    private static final float S = 0.9375F;
    private static final float PX = S / 16F;
    private static final int SUB = 4;
    private static final int ITER = 6;
    private static final float H = 1F / SUB;
    private static final float GRAV = 0.08F * H * H;
    private static final float AIR_H = (float) Math.pow(0.91D, H);
    private static final float AIR_V = (float) Math.pow(0.98D, H);
    private static final float FRICTION = 0.3F;
    private static final float BOUNCE = 0.2F;
    private static final int CATCH_UP = 60;

    static final int HIP_R = 0, HIP_L = 1, SH_R = 2, SH_L = 3, NECK = 4, FT = 5, FB = 6, BT = 7, BB = 8, HEAD = 9,
            HAND_R = 10, HAND_L = 11, FOOT_R = 12, FOOT_L = 13, N = 14;
    private static final int[] TORSO = {HIP_R, HIP_L, SH_R, SH_L, NECK, FT, FB, BT, BB};
    private static final float[] RADIUS_PX = {2.0F, 2.0F, 2.0F, 2.0F, 1.5F, 1.2F, 1.2F, 1.2F, 1.2F, 4.2F,
            2.0F, 2.0F, 2.0F, 2.0F};
    private static final float[] INV_MASS = {0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 1.0F,
            1.2F, 1.2F, 0.9F, 0.9F};
    private static final int[][] STICKS = {{NECK, HEAD}, {SH_R, HAND_R}, {SH_L, HAND_L}, {HIP_R, FOOT_R}, {HIP_L, FOOT_L}};
    private static final Vector3f UP = new Vector3f(0, 1, 0), DOWN = new Vector3f(0, -1, 0);
    private static final float HEAD_CONE = (float) Math.cos(Math.toRadians(70D));
    private static final float HEAD_SIN = (float) Math.sin(Math.toRadians(70D));

    // a geometria do modelo do jogador (px; x = lado direito dele, y = cima, -z = frente; a mesma do RagdollScene)
    private static final Vector3f[] REST = new Vector3f[N];
    private static final float[] BODY_BOX = {-4F, 12F, -2F, 4F, 24F, 2F};
    private static final Vector3f TORSO_C = new Vector3f();
    private static final Vector3f[] TORSO_R = new Vector3f[TORSO.length];
    private static final float[] LEN = new float[STICKS.length];
    private static final float REST_COM_Y;

    static {
        REST[HIP_R] = new Vector3f(1.9F, 12F, 0F);
        REST[HIP_L] = new Vector3f(-1.9F, 12F, 0F);
        REST[SH_R] = new Vector3f(5F, 22F, 0F);
        REST[SH_L] = new Vector3f(-5F, 22F, 0F);
        REST[NECK] = new Vector3f(0F, 24F, 0F);
        REST[FT] = new Vector3f(0F, 21F, -1F);
        REST[FB] = new Vector3f(0F, 15F, -1F);
        REST[BT] = new Vector3f(0F, 21F, 1F);
        REST[BB] = new Vector3f(0F, 15F, 1F);
        REST[HEAD] = new Vector3f(0F, 28F, 0F);
        REST[HAND_R] = new Vector3f(5F, 12F, 0F);
        REST[HAND_L] = new Vector3f(-5F, 12F, 0F);
        REST[FOOT_R] = new Vector3f(1.9F, 0F, 0F);
        REST[FOOT_L] = new Vector3f(-1.9F, 0F, 0F);
        for (int i : TORSO) TORSO_C.add(REST[i]);
        TORSO_C.div(TORSO.length);
        for (int k = 0; k < TORSO.length; k++) TORSO_R[k] = new Vector3f(REST[TORSO[k]]).sub(TORSO_C).mul(PX);
        for (int k = 0; k < STICKS.length; k++) LEN[k] = REST[STICKS[k][0]].distance(REST[STICKS[k][1]]) * PX;
        float sum = 0F, m = 0F;
        for (int i = 0; i < N; i++) {
            sum += REST[i].y / INV_MASS[i];
            m += 1F / INV_MASS[i];
        }
        REST_COM_Y = sum / m * PX;
    }

    private final Ragdoll.Params p;
    private final RandomSource rand;
    private final float[] x = new float[N], y = new float[N], z = new float[N];
    private final float[] px = new float[N], py = new float[N], pz = new float[N];
    private final float[] ox = new float[N], oy = new float[N], oz = new float[N];
    private final float[] nx = new float[N], ny = new float[N], nz = new float[N];
    private final float[] vx0 = new float[N], vy0 = new float[N], vz0 = new float[N];
    private final boolean[] contact = new boolean[N];
    private final float[] impact = new float[N];
    private final int[] lastThump = new int[N];
    private double baseX, baseY, baseZ;
    private final Quaternionf q = new Quaternionf(), qo = new Quaternionf();
    private long simTick = Long.MIN_VALUE;
    private final List<AABB> boxes = new ArrayList<>();
    private final Vector3f t1 = new Vector3f(), t2 = new Vector3f(), t3 = new Vector3f();
    private final Quaternionf qa = new Quaternionf(), qInv = new Quaternionf();

    private PlayerRagdollScene(Ragdoll.Params p) {
        this.p = p;
        this.rand = RandomSource.create(p.seed() * 31L + 7L);
        java.util.Arrays.fill(lastThump, -100);
    }

    /** A cena de quem voa no bote (params = os do FroggyDude que deu o bote); cria se o bote é novo. */
    public static PlayerRagdollScene of(AbstractClientPlayer player, Ragdoll.Params params) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != sceneLevel) {
            SCENES.clear();
            sceneLevel = level;
        }
        PlayerRagdollScene s = SCENES.get(player.getId());
        if (s == null || s.p.t0() != params.t0() || s.p.seed() != params.seed()) {
            s = new PlayerRagdollScene(params);
            SCENES.put(player.getId(), s);
        }
        return s;
    }

    /** Ninguém mais voando: esquece (o próximo bote começa do zero). */
    public static void end(int playerId) {
        SCENES.remove(playerId);
    }

    // ---------------------------------------------------------------- simular

    /** Começa em pé, no rumo do corpo, com o empurrão do bote: o tronco tomba pra frente e cada membro vai pra um lado. */
    private void start(AbstractClientPlayer pl, long now) {
        baseX = pl.getX();
        baseY = pl.getY();
        baseZ = pl.getZ();
        q.rotationY((180F - pl.yBodyRot) * Mth.DEG_TO_RAD);
        qo.set(q);
        long late = Math.max(0L, now - p.t0());
        boolean flying = late < 12;
        float hv = flying ? p.speed() * (float) Math.pow(0.91D, late) : 0F;
        float vy = flying ? p.up() - 0.08F * late : 0F;
        float dx = p.dx(), dz = p.dz();
        for (int i = 0; i < N; i++) {
            t1.set(REST[i]).mul(PX).rotate(q);
            x[i] = t1.x;
            y[i] = t1.y + 2.2F * PX;
            z[i] = t1.z;
            float vx = dx * hv, vyy = vy, vz = dz * hv;
            if (flying) {
                // pegou nas costas/na cintura: em cima vai mais rápido -> o corpo tomba e roda no ar
                float h = REST[i].y * PX - REST_COM_Y;
                float spin = 0.30F + 0.10F * rand.nextFloat();
                vx += dx * spin * h;
                vz += dz * spin * h;
                vyy += 0.05F * h;
                if (i >= HAND_R) {
                    vx += (rand.nextFloat() - 0.5F) * 0.26F;
                    vyy += (rand.nextFloat() - 0.5F) * 0.18F;
                    vz += (rand.nextFloat() - 0.5F) * 0.26F;
                }
            }
            px[i] = x[i] - vx * H;
            py[i] = y[i] - vyy * H;
            pz[i] = z[i] - vz * H;
        }
        if (flying) {
            float w = (rand.nextFloat() - 0.5F) * 0.35F * H;
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

    private void advance(AbstractClientPlayer pl) {
        ClientLevel level = (ClientLevel) pl.level();
        long now = level.getGameTime();
        if (simTick == Long.MIN_VALUE) start(pl, now);
        if (now - simTick > CATCH_UP) simTick = now - CATCH_UP;
        while (simTick < now) {
            simTick++;
            step(pl, level, simTick == now, now - p.t0());
        }
    }

    private void step(AbstractClientPlayer pl, ClientLevel level, boolean live, long age) {
        snapshot();
        follow(pl, pl.onGround() && age > 8);
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
        if (live) thumps(level, pl);
    }

    /** O corpo segue o jogador (que voa de verdade): um puxão no meio do corpo. */
    private void follow(AbstractClientPlayer pl, boolean lying) {
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
        float ex = (float) (pl.getX() - baseX) - cx, ez = (float) (pl.getZ() - baseZ) - cz;
        float ey = lying ? 0F : (float) (pl.getY() - baseY) + REST_COM_Y - cy;
        ey = Math.signum(ey) * Math.max(0F, Math.abs(ey) - 0.6F);
        float d2 = ex * ex + ey * ey + ez * ez;
        float k = d2 > 16F ? 1F : lying ? 0.08F : 0.12F;
        if (k < 1F) ey *= 0.5F;
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

    private Vector3f torsoCenter(Vector3f out) {
        out.zero();
        for (int i : TORSO) out.add(x[i], y[i], z[i]);
        return out.div(TORSO.length);
    }

    /** A cabeça num cone de 70°, as pernas em ângulos de gente, mão e pé fora do tronco. */
    private void limits() {
        q.conjugate(qInv);
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
        leg(HIP_R, FOOT_R, 1F, LEN[3]);
        leg(HIP_L, FOOT_L, -1F, LEN[4]);
        Vector3f c = torsoCenter(t3);
        for (int i : new int[]{HAND_R, HAND_L, FOOT_R, FOOT_L}) outsideTorso(i, c);
    }

    /** A perna inteira: pra frente até 110°, pra trás até 30°, pra fora até 50°, pra dentro até 10°. */
    private void leg(int hip, int foot, float side, float legLen) {
        t1.set(x[foot] - x[hip], y[foot] - y[hip], z[foot] - z[hip]).rotate(qInv);
        float l = t1.length();
        if (l < 1.0E-6F) return;
        t1.div(l);
        float flex = (float) Math.atan2(-t1.z, -t1.y);
        float abd = (float) Math.asin(Mth.clamp(t1.x * side, -1F, 1F));
        float f2 = Mth.clamp(flex, -30F * Mth.DEG_TO_RAD, 110F * Mth.DEG_TO_RAD);
        float a2 = Mth.clamp(abd, -10F * Mth.DEG_TO_RAD, 50F * Mth.DEG_TO_RAD);
        if (f2 != flex || a2 != abd) {
            float ca = Mth.cos(a2);
            t1.set(side * Mth.sin(a2), -ca * Mth.cos(f2), -ca * Mth.sin(f2));
            t2.set(t1).mul(legLen).rotate(q);
            x[foot] = x[hip] + t2.x;
            y[foot] = y[hip] + t2.y;
            z[foot] = z[hip] + t2.z;
        }
    }

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

    /** O tronco rígido (o mesmo encaixe de forma do RagdollScene). */
    private void matchTorso(int iters) {
        Vector3f c = torsoCenter(t3);
        Matrix3f a = new Matrix3f().zero();
        for (int k = 0; k < TORSO.length; k++) {
            int i = TORSO[k];
            float dx = x[i] - c.x, dy = y[i] - c.y, dz = z[i] - c.z;
            Vector3f r = TORSO_R[k];
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
    private void thumps(ClientLevel level, AbstractClientPlayer pl) {
        int made = 0;
        for (int i = 0; i < N && made < 3; i++) {
            if (impact[i] < 0.22F || pl.tickCount - lastThump[i] < 6) continue;
            lastThump[i] = pl.tickCount;
            made++;
            double wx = x[i] + baseX, wy = y[i] + baseY - RADIUS_PX[i] * PX - 0.05D, wz = z[i] + baseZ;
            BlockState under = level.getBlockState(BlockPos.containing(wx, wy, wz));
            SoundEvent sound = under.isAir() ? net.minecraft.sounds.SoundEvents.PLAYER_SMALL_FALL
                    : under.getSoundType().getFallSound();
            float vol = Mth.clamp(impact[i] * 1.4F, 0.25F, 1.0F);
            level.playLocalSound(wx, wy, wz, sound, SoundSource.PLAYERS, vol, 0.85F + rand.nextFloat() * 0.2F, false);
        }
    }

    // ---------------------------------------------------------------- desenhar

    /**
     * Desenha o jogador mole. O poseStack está na posição de desenho dele (eixos do mundo, sem giro), como no
     * RenderPlayerEvent.Pre.
     */
    public void render(AbstractClientPlayer pl, PlayerModel<AbstractClientPlayer> model, PoseStack ps,
                       MultiBufferSource buffers, int light, float partial) {
        advance(pl);
        Vec3 base = pl.getPosition(partial);
        float[] wx = new float[N], wy = new float[N], wz = new float[N];
        for (int i = 0; i < N; i++) {
            wx[i] = Mth.lerp(partial, ox[i], x[i]);
            wy[i] = Mth.lerp(partial, oy[i], y[i]);
            wz[i] = Mth.lerp(partial, oz[i], z[i]);
        }
        Quaternionf qt = new Quaternionf();
        qo.slerp(q, partial, qt);
        Quaternionf qInvT = new Quaternionf(qt).conjugate();
        Vector3f c = new Vector3f();
        for (int i : TORSO) c.add(wx[i], wy[i], wz[i]);
        c.div(TORSO.length);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(pl.getSkinTextureLocation()));
        int overlay = LivingEntityRenderer.getOverlayCoords(pl, 0F);
        Quaternionf r = new Quaternionf();
        Vector3f d = new Vector3f(), at = new Vector3f();
        // o tronco (e a jaqueta): o pivô do modelo é o pescoço
        at.set(REST[NECK]).sub(TORSO_C).mul(PX).rotate(qt).add(c);
        draw(ps, base, at, qt, vc, light, overlay, model.body, model.jacket);
        // a cabeça (e o chapéu), no pescoço
        limb(qt, qInvT, UP, d.set(wx[HEAD] - wx[NECK], wy[HEAD] - wy[NECK], wz[HEAD] - wz[NECK]), r);
        draw(ps, base, at.set(wx[NECK], wy[NECK], wz[NECK]), r, vc, light, overlay, model.head, model.hat);
        // os braços (e as mangas), nos ombros
        limb(qt, qInvT, DOWN, d.set(wx[HAND_R] - wx[SH_R], wy[HAND_R] - wy[SH_R], wz[HAND_R] - wz[SH_R]), r);
        draw(ps, base, at.set(wx[SH_R], wy[SH_R], wz[SH_R]), r, vc, light, overlay, model.rightArm, model.rightSleeve);
        limb(qt, qInvT, DOWN, d.set(wx[HAND_L] - wx[SH_L], wy[HAND_L] - wy[SH_L], wz[HAND_L] - wz[SH_L]), r);
        draw(ps, base, at.set(wx[SH_L], wy[SH_L], wz[SH_L]), r, vc, light, overlay, model.leftArm, model.leftSleeve);
        // as pernas (e a calça), no quadril
        limb(qt, qInvT, DOWN, d.set(wx[FOOT_R] - wx[HIP_R], wy[FOOT_R] - wy[HIP_R], wz[FOOT_R] - wz[HIP_R]), r);
        draw(ps, base, at.set(wx[HIP_R], wy[HIP_R], wz[HIP_R]), r, vc, light, overlay, model.rightLeg, model.rightPants);
        limb(qt, qInvT, DOWN, d.set(wx[FOOT_L] - wx[HIP_L], wy[FOOT_L] - wy[HIP_L], wz[FOOT_L] - wz[HIP_L]), r);
        draw(ps, base, at.set(wx[HIP_L], wy[HIP_L], wz[HIP_L]), r, vc, light, overlay, model.leftLeg, model.leftPants);
    }

    /**
     * Uma parte do modelo no pivô "at" (blocos, relativo à base da cena), girada por r (o espaço da física: y pra cima);
     * o modelo do jogador tem y pra baixo e x espelhado (o scale(-1, -1, 1) do LivingEntityRenderer).
     */
    private void draw(PoseStack ps, Vec3 base, Vector3f at, Quaternionf r, VertexConsumer vc, int light, int overlay,
                      ModelPart... parts) {
        ps.pushPose();
        ps.translate(at.x + baseX - base.x, at.y + baseY - base.y, at.z + baseZ - base.z);
        ps.mulPose(r);
        ps.scale(-S, -S, S);
        for (ModelPart part : parts) {
            if (!part.visible) continue;
            PartPose saved = part.storePose();
            part.setPos(0F, 0F, 0F);
            part.setRotation(0F, 0F, 0F);
            part.render(ps, vc, light, overlay);
            part.loadPose(saved);
        }
        ps.popPose();
    }

    private static void limb(Quaternionf parent, Quaternionf parentInv, Vector3f restAxis, Vector3f dir, Quaternionf out) {
        if (dir.lengthSquared() < 1.0E-10F) {
            out.set(parent);
            return;
        }
        dir.normalize().rotate(parentInv);
        out.rotationTo(restAxis, dir).premul(parent);
    }
}
