package com.froggydude.client.pieces;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Um pedaço do corpo (um caco): um corpo rígido que cai, quica, tomba, rola,
 * escorrega e empilha - o jeito do Physics Mod, mas em Java puro (o Physics Mod
 * usa o PhysX da NVIDIA, que só existe pra PC e não roda no celular). Colide
 * com os blocos de verdade (a forma de colisão de cada bloco: laje, escada,
 * cerca) pelos cantos do caco, e com os outros cacos pela caixa deles.
 *
 * Sem criar objeto novo a cada passo (no celular o lixo de memória dá
 * engasgo): tudo em rascunhos reaproveitados - só roda na thread do cliente.
 *
 * Unidades: blocos, segundos, radianos. Massa = volume (densidade 1).
 */
final class Piece {

    static final float GRAVITY = 16.0F;
    private static final float RESTITUTION = 0.22F;
    private static final float FRICTION = 0.65F;
    /** Uma fresta de contato: encostado sem afundar ainda conta (atrito e repouso). */
    private static final double SKIN = 0.003D;
    private static final int MAX_CONTACTS = 64;

    // rascunhos (só a thread do cliente mexe)
    private static final Quaternionf QA = new Quaternionf(), QB = new Quaternionf();
    private static final Vector3f T1 = new Vector3f(), T2 = new Vector3f(), T3 = new Vector3f(), T4 = new Vector3f();
    private static final Vector3f R = new Vector3f(), RB = new Vector3f(), N = new Vector3f(), VP = new Vector3f();
    private static final Vector3f VB = new Vector3f(), TT = new Vector3f(), P = new Vector3f(), L = new Vector3f();
    private static final Vector3f J = new Vector3f();
    private static final float[][] CONTACTS = new float[MAX_CONTACTS][7];

    /** O que desenhar: as faces do caco, no espaço dele. */
    static final class Draw {
        final RenderType type;
        final float[] verts; // por vértice: x y z (local), r g b a, u v, nx ny nz (local)

        Draw(RenderType type, float[] verts) {
            this.type = type;
            this.verts = verts;
        }
    }

    final List<Draw> draws = new ArrayList<>();
    /** Meia-caixa (local) - a colisão entre cacos. */
    final Vector3f half;
    /** Pontos de contato (os cantos do caco), no espaço dele. */
    final List<Vector3f> samples = new ArrayList<>();
    private float[] prevPts = new float[0];
    final float radius;
    final float invMass;
    /** Inverso do momento de inércia (diagonal, no espaço do pedaço). */
    final Vector3f invInertia;

    // estado (posição do centro, rotação, velocidade linear e angular - mundo)
    double x, y, z, xo, yo, zo;
    final Quaternionf rot = new Quaternionf();
    final Quaternionf rotO = new Quaternionf();
    final Vector3f vel = new Vector3f();
    final Vector3f ang = new Vector3f();

    int age;
    int life;
    boolean asleep;
    private int still;
    /** Encostou em outro caco desde o último passo (apoiado no monte também conta pra dormir). */
    private boolean touching;
    /** Tingido de sangue (multiplica a cor da textura). */
    float tr = 1F, tg = 1F, tb = 1F;
    boolean bleeds;
    int bloodColor;
    /** Já deixou a poça embaixo? */
    boolean pooled;
    /** Bateu forte neste passo (pro respingo). */
    float lastImpact;
    /** Até quando (age) o último chute vale: o pé fica uns ticks em cima, não chuta de novo a cada tick. */
    int kickedUntil;
    /** Id do corpo de onde saiu. */
    final int body;

    Piece(int body, Vector3f half, float radius) {
        this.body = body;
        this.half = half;
        this.radius = radius;
        float a = Math.max(half.x, 0.02F) * 2F, b = Math.max(half.y, 0.02F) * 2F, c = Math.max(half.z, 0.02F) * 2F;
        float mass = Math.max(0.005F, a * b * c);
        this.invMass = 1F / mass;
        this.invInertia = new Vector3f(12F / (mass * (b * b + c * c)), 12F / (mass * (a * a + c * c)),
                12F / (mass * (a * a + b * b)));
    }

    // ---------------------------------------------------------------- utilidades

    /** I^-1 no mundo aplicado em v (resultado em out; v e out podem ser o mesmo). */
    private Vector3f invInertiaWorld(Vector3f v, Vector3f out) {
        QA.set(rot).conjugate();
        out.set(v).rotate(QA).mul(invInertia);
        return out.rotate(rot);
    }

    Vector3f worldPoint(Vector3f local, Vector3f out) {
        out.set(local).rotate(rot);
        return out.add((float) x, (float) y, (float) z);
    }

    /** Velocidade do ponto (mundo) que está em r (relativo ao centro). */
    private Vector3f pointVel(Vector3f r, Vector3f out) {
        return out.set(ang).cross(r).add(vel);
    }

    void wake() {
        asleep = false;
        still = 0;
    }

    /** Impulso j (mundo) no ponto r (relativo ao centro). */
    void applyImpulse(Vector3f r, Vector3f j) {
        vel.fma(invMass, j);
        T1.set(r).cross(j);
        ang.add(invInertiaWorld(T1, T1));
    }

    /** Quanto um impulso unitário em n no ponto r muda a velocidade ali (o "k" do impulso). */
    float effectiveInv(Vector3f r, Vector3f n) {
        T2.set(r).cross(n);
        invInertiaWorld(T2, T2).cross(r);
        return invMass + n.dot(T2);
    }

    // ---------------------------------------------------------------- passo

    /** Um passo de dt segundos contra os blocos do mundo. */
    void step(Level level, float dt) {
        if (asleep) return;
        // antes de mover: onde estava cada ponto (pra saber por qual face entrou num bloco)
        int n = samples.size();
        if (prevPts.length != n * 3) prevPts = new float[n * 3];
        for (int i = 0; i < n; i++) {
            worldPoint(samples.get(i), P);
            prevPts[i * 3] = P.x;
            prevPts[i * 3 + 1] = P.y;
            prevPts[i * 3 + 2] = P.z;
        }

        vel.y -= GRAVITY * dt;
        // um tico de resistência do ar e da rotação
        vel.mul(0.998F);
        ang.mul(0.995F);
        x += vel.x * dt;
        y += vel.y * dt;
        z += vel.z * dt;
        integrateRotation(dt);

        // contatos com os blocos
        int count = 0;
        for (int i = 0; i < n && count < MAX_CONTACTS; i++) {
            worldPoint(samples.get(i), P);
            if (contact(level, P, prevPts[i * 3], prevPts[i * 3 + 1], prevPts[i * 3 + 2], CONTACTS[count])) count++;
        }
        lastImpact = 0F;
        if (count > 0) {
            solve(count);
            correct(count);
        }
        // parado encostado em algo (chão ou monte de cacos): dorme (não gasta mais conta)
        boolean supported = count > 0 || touching;
        touching = false;
        // depois de uns segundos no chão, aceita parar com um tremor maior (lasca fina que não assenta)
        float vMax = age > 60 ? 0.22F : 0.12F, wMax = age > 60 ? 0.9F : 0.45F;
        if (supported && vel.lengthSquared() < vMax * vMax && ang.lengthSquared() < wMax * wMax) {
            if (++still > 18) {
                asleep = true;
                vel.zero();
                ang.zero();
            }
        } else {
            still = 0;
        }
        if (y < level.getMinBuildHeight() - 16) life = 0;
    }

    private void integrateRotation(float dt) {
        float wx = ang.x, wy = ang.y, wz = ang.z;
        float bx = rot.x, by = rot.y, bz = rot.z, bw = rot.w;
        float h = 0.5F * dt;
        rot.x += h * (wx * bw + wy * bz - wz * by);
        rot.y += h * (-wx * bz + wy * bw + wz * bx);
        rot.z += h * (wx * by - wy * bx + wz * bw);
        rot.w += h * (-wx * bx - wy * by - wz * bz);
        rot.normalize();
    }

    /**
     * O ponto p entrou num bloco? out = {px,py,pz, nx,ny,nz, profundidade}. A normal é
     * a face por onde ele entrou (onde estava antes do passo); se já estava dentro, a
     * saída mais curta.
     */
    private static boolean contact(Level level, Vector3f p, float qx, float qy, float qz, float[] out) {
        // o bloco do ponto e o de baixo (quem está quase encostando no chão)
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos bp = BlockPos.containing(p.x, p.y + dy * SKIN * 2, p.z);
            if (dy == -1 && bp.getY() == (int) Math.floor(p.y)) continue;
            if (!level.isLoaded(bp)) continue;
            BlockState state = level.getBlockState(bp);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, bp);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                double minX = box.minX + bp.getX(), maxX = box.maxX + bp.getX();
                double minY = box.minY + bp.getY(), maxY = box.maxY + bp.getY();
                double minZ = box.minZ + bp.getZ(), maxZ = box.maxZ + bp.getZ();
                if (p.x <= minX - SKIN || p.x >= maxX + SKIN || p.y <= minY - SKIN || p.y >= maxY + SKIN
                        || p.z <= minZ - SKIN || p.z >= maxZ + SKIN) {
                    continue;
                }
                float nx = 0, ny = 0, nz = 0;
                double depth = Double.MAX_VALUE;
                // por qual face entrou
                if (qy >= maxY - 1.0E-4D && maxY - p.y < depth) { depth = maxY - p.y; nx = 0; ny = 1; nz = 0; }
                if (qy <= minY + 1.0E-4D && p.y - minY < depth) { depth = p.y - minY; nx = 0; ny = -1; nz = 0; }
                if (qx <= minX + 1.0E-4D && p.x - minX < depth) { depth = p.x - minX; nx = -1; ny = 0; nz = 0; }
                if (qx >= maxX - 1.0E-4D && maxX - p.x < depth) { depth = maxX - p.x; nx = 1; ny = 0; nz = 0; }
                if (qz <= minZ + 1.0E-4D && p.z - minZ < depth) { depth = p.z - minZ; nx = 0; ny = 0; nz = -1; }
                if (qz >= maxZ - 1.0E-4D && maxZ - p.z < depth) { depth = maxZ - p.z; nx = 0; ny = 0; nz = 1; }
                if (depth == Double.MAX_VALUE) {
                    // já estava dentro: a saída mais curta (pra cima ganha empate)
                    double best = maxY - p.y;
                    nx = 0;
                    ny = 1;
                    nz = 0;
                    if (p.x - minX < best - 1.0E-4D) { best = p.x - minX; nx = -1; ny = 0; nz = 0; }
                    if (maxX - p.x < best - 1.0E-4D) { best = maxX - p.x; nx = 1; ny = 0; nz = 0; }
                    if (p.z - minZ < best - 1.0E-4D) { best = p.z - minZ; nx = 0; ny = 0; nz = -1; }
                    if (maxZ - p.z < best - 1.0E-4D) { best = maxZ - p.z; nx = 0; ny = 0; nz = 1; }
                    if (p.y - minY < best - 1.0E-4D) { best = p.y - minY; nx = 0; ny = -1; nz = 0; }
                    depth = best;
                }
                out[0] = p.x;
                out[1] = p.y;
                out[2] = p.z;
                out[3] = nx;
                out[4] = ny;
                out[5] = nz;
                out[6] = (float) Math.max(0D, depth);
                return true;
            }
        }
        return false;
    }

    /** Impulsos: não atravessar (com um quique pequeno) e atrito. */
    private void solve(int count) {
        for (int it = 0; it < 4; it++) {
            for (int c = 0; c < count; c++) {
                float[] k = CONTACTS[c];
                R.set(k[0] - (float) x, k[1] - (float) y, k[2] - (float) z);
                N.set(k[3], k[4], k[5]);
                pointVel(R, VP);
                float vn = VP.dot(N);
                if (vn >= 0F) continue;
                if (it == 0) lastImpact = Math.max(lastImpact, -vn);
                float e = vn < -2.0F ? RESTITUTION : 0F;
                float jn = -(1F + e) * vn / effectiveInv(R, N);
                applyImpulse(R, J.set(N).mul(jn));
                // atrito
                pointVel(R, VP);
                TT.set(VP).fma(-VP.dot(N), N);
                float vt = TT.length();
                if (vt > 1.0E-4F) {
                    TT.div(vt);
                    float jt = Math.min(vt / effectiveInv(R, TT), FRICTION * jn);
                    applyImpulse(R, J.set(TT).mul(-jt));
                }
            }
        }
    }

    /** Tira de dentro do bloco (o mais fundo em cada direção). */
    private void correct(int count) {
        float px = 0, nx = 0, py = 0, ny = 0, pz = 0, nz = 0;
        for (int c = 0; c < count; c++) {
            float[] k = CONTACTS[c];
            float d = k[6];
            px = Math.max(px, k[3] * d);
            nx = Math.min(nx, k[3] * d);
            py = Math.max(py, k[4] * d);
            ny = Math.min(ny, k[4] * d);
            pz = Math.max(pz, k[5] * d);
            nz = Math.min(nz, k[5] * d);
        }
        x += (px + nx) * 0.9F;
        y += (py + ny) * 0.9F;
        z += (pz + nz) * 0.9F;
    }

    /** Os cantos deste caco que entraram na caixa do outro: empurra os dois (e acorda). */
    void collide(Piece o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        float rr = radius + o.radius;
        if (dx * dx + dy * dy + dz * dz > rr * rr) return;
        QB.set(o.rot).conjugate();
        for (Vector3f s : samples) {
            worldPoint(s, P);
            L.set((float) (P.x - o.x), (float) (P.y - o.y), (float) (P.z - o.z)).rotate(QB);
            float ex = o.half.x - Math.abs(L.x), ey = o.half.y - Math.abs(L.y), ez = o.half.z - Math.abs(L.z);
            if (ex <= 0F || ey <= 0F || ez <= 0F) continue;
            float depth;
            if (ex <= ey && ex <= ez) {
                depth = ex;
                N.set(Math.signum(L.x), 0, 0);
            } else if (ey <= ez) {
                depth = ey;
                N.set(0, Math.signum(L.y), 0);
            } else {
                depth = ez;
                N.set(0, 0, Math.signum(L.z));
            }
            N.rotate(o.rot);
            touching = true;
            o.touching = true;
            R.set(P.x - (float) x, P.y - (float) y, P.z - (float) z);
            RB.set(P.x - (float) o.x, P.y - (float) o.y, P.z - (float) o.z);
            pointVel(R, VP);
            o.pointVel(RB, VB);
            float vn = VP.sub(VB).dot(N);
            // o outro parado segura (massa infinita) - a não ser que a pancada seja forte
            if (o.asleep && vn < -1.5F) o.wake();
            boolean both = !o.asleep;
            if (vn < 0F) {
                float k = effectiveInv(R, N) + (both ? o.effectiveInv(RB, N) : 0F);
                float j = -(1F + 0.1F) * vn / k;
                applyImpulse(R, J.set(N).mul(j));
                if (both) o.applyImpulse(RB, J.set(N).mul(-j));
            }
            float total = both ? invMass + o.invMass : invMass;
            float push = Math.min(depth, 0.08F) * 0.8F;
            x += N.x * push * invMass / total;
            y += N.y * push * invMass / total;
            z += N.z * push * invMass / total;
            if (both) {
                o.x -= N.x * push * o.invMass / total;
                o.y -= N.y * push * o.invMass / total;
                o.z -= N.z * push * o.invMass / total;
            }
        }
    }
}
