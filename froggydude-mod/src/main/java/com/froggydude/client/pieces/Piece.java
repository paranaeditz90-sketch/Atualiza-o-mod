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
 * Um pedaço do corpo: uma caixa rígida (o cubo principal da parte) que cai,
 * quica, tomba, rola, escorrega e empilha - o jeito do Physics Mod, mas em Java
 * puro (o Physics Mod usa o PhysX da NVIDIA, que só existe pra PC e não roda no
 * celular). Colide com os blocos de verdade (forma de colisão de cada bloco:
 * laje, escada, cerca) e com os outros pedaços, pelos cantos de cada cubo.
 *
 * Unidades: blocos, segundos, radianos. Massa = volume (densidade 1).
 */
final class Piece {

    static final float GRAVITY = 16.0F;
    private static final float RESTITUTION = 0.22F;
    private static final float FRICTION = 0.65F;
    /** Uma fresta de contato: encostado sem afundar ainda conta (atrito e repouso). */
    private static final double SKIN = 0.003D;

    /** O que desenhar: as faces de cada cubo da parte, no espaço do pedaço. */
    static final class Draw {
        final RenderType type;
        final float[] verts; // por vértice: x y z (local), r g b a, u v, nx ny nz (local)

        Draw(RenderType type, float[] verts) {
            this.type = type;
            this.verts = verts;
        }
    }

    final List<Draw> draws = new ArrayList<>();
    /** Meia-caixa do cubo principal. */
    final Vector3f half;
    /** Pontos de contato (os cantos de todos os cubos da parte), no espaço do pedaço. */
    final List<Vector3f> samples = new ArrayList<>();
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
    /** Tingido de sangue (multiplica a cor da textura). */
    float tr = 1F, tg = 1F, tb = 1F;
    boolean bleeds;
    int bloodColor;
    /** Já deixou a poça embaixo? */
    boolean pooled;
    /** Bateu forte neste passo (pro respingo). */
    float lastImpact;
    /** Id do corpo de onde saiu. */
    final int body;

    Piece(int body, Vector3f half, float radius) {
        this.body = body;
        this.half = half;
        this.radius = radius;
        float a = Math.max(half.x, 0.02F) * 2F, b = Math.max(half.y, 0.02F) * 2F, c = Math.max(half.z, 0.02F) * 2F;
        float mass = Math.max(0.02F, a * b * c);
        this.invMass = 1F / mass;
        this.invInertia = new Vector3f(12F / (mass * (b * b + c * c)), 12F / (mass * (a * a + c * c)),
                12F / (mass * (a * a + b * b)));
    }

    // ---------------------------------------------------------------- utilidades

    /** I^-1 no mundo aplicado em v (resultado em out). */
    private Vector3f invInertiaWorld(Vector3f v, Vector3f out) {
        Quaternionf inv = new Quaternionf(rot).conjugate();
        out.set(v).rotate(inv);
        out.mul(invInertia);
        return out.rotate(rot);
    }

    Vector3f worldPoint(Vector3f local, Vector3f out) {
        out.set(local).rotate(rot);
        return out.add((float) x, (float) y, (float) z);
    }

    /** Velocidade do ponto (mundo) que está em r (relativo ao centro). */
    private Vector3f pointVel(Vector3f r, Vector3f out) {
        return new Vector3f(ang).cross(r, out).add(vel);
    }

    void wake() {
        asleep = false;
        still = 0;
    }

    void applyImpulse(Vector3f r, Vector3f j) {
        vel.fma(invMass, j);
        Vector3f t = new Vector3f(r).cross(j);
        ang.add(invInertiaWorld(t, new Vector3f()));
    }

    /** Quanto um impulso unitário em n no ponto r muda a velocidade ali (o "k" do impulso). */
    float effectiveInv(Vector3f r, Vector3f n) {
        Vector3f rn = new Vector3f(r).cross(n);
        Vector3f w = invInertiaWorld(rn, new Vector3f()).cross(r);
        return invMass + n.dot(w);
    }

    // ---------------------------------------------------------------- passo

    /** Um passo de dt segundos contra os blocos do mundo. */
    void step(Level level, float dt) {
        if (asleep) return;
        // antes de mover: onde estava cada ponto (pra saber por qual face entrou num bloco)
        List<Vector3f> before = new ArrayList<>(samples.size());
        for (Vector3f s : samples) before.add(worldPoint(s, new Vector3f()));

        vel.y -= GRAVITY * dt;
        // um tico de resistência do ar e da rotação
        vel.mul(0.998F);
        ang.mul(0.995F);
        x += vel.x * dt;
        y += vel.y * dt;
        z += vel.z * dt;
        integrateRotation(dt);

        // contatos com os blocos
        List<float[]> contacts = new ArrayList<>();
        Vector3f p = new Vector3f();
        for (int i = 0; i < samples.size(); i++) {
            worldPoint(samples.get(i), p);
            contact(level, p, before.get(i), contacts);
        }
        lastImpact = 0F;
        if (!contacts.isEmpty()) {
            solve(contacts);
            correct(contacts);
        }
        // parado encostado em algo: dorme (não gasta mais conta)
        if (!contacts.isEmpty() && vel.lengthSquared() < 0.12F * 0.12F && ang.lengthSquared() < 0.45F * 0.45F) {
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
     * O ponto p entrou num bloco? Contato = {px,py,pz, nx,ny,nz, profundidade}. A
     * normal é a face por onde ele entrou (onde estava antes do passo); se já estava
     * dentro, a saída mais curta.
     */
    private static void contact(Level level, Vector3f p, Vector3f prev, List<float[]> out) {
        // o bloco do ponto e o de baixo (quem está quase encostando no chão)
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos bp = BlockPos.containing(p.x, p.y + dy * SKIN * 2, p.z);
            if (dy == -1 && bp.equals(BlockPos.containing(p.x, p.y, p.z))) continue;
            if (!level.isLoaded(bp)) continue;
            BlockState state = level.getBlockState(bp);
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
                if (prev.y >= maxY - 1.0E-4D && maxY - p.y < depth) { depth = maxY - p.y; nx = 0; ny = 1; nz = 0; }
                if (prev.y <= minY + 1.0E-4D && p.y - minY < depth) { depth = p.y - minY; nx = 0; ny = -1; nz = 0; }
                if (prev.x <= minX + 1.0E-4D && p.x - minX < depth) { depth = p.x - minX; nx = -1; ny = 0; nz = 0; }
                if (prev.x >= maxX - 1.0E-4D && maxX - p.x < depth) { depth = maxX - p.x; nx = 1; ny = 0; nz = 0; }
                if (prev.z <= minZ + 1.0E-4D && p.z - minZ < depth) { depth = p.z - minZ; nx = 0; ny = 0; nz = -1; }
                if (prev.z >= maxZ - 1.0E-4D && maxZ - p.z < depth) { depth = maxZ - p.z; nx = 0; ny = 0; nz = 1; }
                if (depth == Double.MAX_VALUE) {
                    // já estava dentro: a saída mais curta (pra cima ganha empate)
                    double[] d = {maxY - p.y, p.x - minX, maxX - p.x, p.z - minZ, maxZ - p.z, p.y - minY};
                    float[][] n = {{0, 1, 0}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, -1, 0}};
                    int k = 0;
                    for (int i = 1; i < 6; i++) if (d[i] < d[k] - 1.0E-4D) k = i;
                    depth = d[k];
                    nx = n[k][0];
                    ny = n[k][1];
                    nz = n[k][2];
                }
                out.add(new float[]{p.x, p.y, p.z, nx, ny, nz, (float) Math.max(0D, depth)});
                return;
            }
        }
    }

    /** Impulsos: não atravessar (com um quique pequeno) e atrito. */
    private void solve(List<float[]> contacts) {
        Vector3f r = new Vector3f(), n = new Vector3f(), vp = new Vector3f(), t = new Vector3f();
        for (int it = 0; it < 4; it++) {
            for (float[] c : contacts) {
                r.set(c[0] - (float) x, c[1] - (float) y, c[2] - (float) z);
                n.set(c[3], c[4], c[5]);
                pointVel(r, vp);
                float vn = vp.dot(n);
                if (vn >= 0F) continue;
                if (it == 0) lastImpact = Math.max(lastImpact, -vn);
                float e = vn < -2.0F ? RESTITUTION : 0F;
                float jn = -(1F + e) * vn / effectiveInv(r, n);
                applyImpulse(r, new Vector3f(n).mul(jn));
                // atrito
                pointVel(r, vp);
                t.set(vp).fma(-vp.dot(n), n);
                float vt = t.length();
                if (vt > 1.0E-4F) {
                    t.div(vt);
                    float jt = Math.min(vt / effectiveInv(r, t), FRICTION * jn);
                    applyImpulse(r, new Vector3f(t).mul(-jt));
                }
            }
        }
    }

    /** Tira de dentro do bloco (o mais fundo em cada direção). */
    private void correct(List<float[]> contacts) {
        float px = 0, nx = 0, py = 0, ny = 0, pz = 0, nz = 0;
        for (float[] c : contacts) {
            float d = c[6];
            px = Math.max(px, c[3] * d);
            nx = Math.min(nx, c[3] * d);
            py = Math.max(py, c[4] * d);
            ny = Math.min(ny, c[4] * d);
            pz = Math.max(pz, c[5] * d);
            nz = Math.min(nz, c[5] * d);
        }
        x += (px + nx) * 0.9F;
        y += (py + ny) * 0.9F;
        z += (pz + nz) * 0.9F;
    }

    /** Os cantos deste pedaço que entraram no outro: empurra os dois (e acorda). */
    void collide(Piece o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        float rr = radius + o.radius;
        if (dx * dx + dy * dy + dz * dz > rr * rr) return;
        Quaternionf inv = new Quaternionf(o.rot).conjugate();
        Vector3f p = new Vector3f(), l = new Vector3f(), n = new Vector3f();
        for (Vector3f s : samples) {
            worldPoint(s, p);
            l.set((float) (p.x - o.x), (float) (p.y - o.y), (float) (p.z - o.z)).rotate(inv);
            float ex = o.half.x - Math.abs(l.x), ey = o.half.y - Math.abs(l.y), ez = o.half.z - Math.abs(l.z);
            if (ex <= 0F || ey <= 0F || ez <= 0F) continue;
            float depth;
            if (ex <= ey && ex <= ez) {
                depth = ex;
                n.set(Math.signum(l.x), 0, 0);
            } else if (ey <= ez) {
                depth = ey;
                n.set(0, Math.signum(l.y), 0);
            } else {
                depth = ez;
                n.set(0, 0, Math.signum(l.z));
            }
            n.rotate(o.rot);
            Vector3f ra = new Vector3f(p.x - (float) x, p.y - (float) y, p.z - (float) z);
            Vector3f rb = new Vector3f(p.x - (float) o.x, p.y - (float) o.y, p.z - (float) o.z);
            Vector3f va = pointVel(ra, new Vector3f());
            Vector3f vb = o.pointVel(rb, new Vector3f());
            float vn = va.sub(vb).dot(n);
            // o outro parado segura (massa infinita) - a não ser que a pancada seja forte
            if (o.asleep && vn < -1.5F) o.wake();
            boolean both = !o.asleep;
            if (vn < 0F) {
                float k = effectiveInv(ra, n) + (both ? o.effectiveInv(rb, n) : 0F);
                float j = -(1F + 0.1F) * vn / k;
                applyImpulse(ra, new Vector3f(n).mul(j));
                if (both) o.applyImpulse(rb, new Vector3f(n).mul(-j));
            }
            float total = both ? invMass + o.invMass : invMass;
            float push = Math.min(depth, 0.08F) * 0.8F;
            x += n.x * push * invMass / total;
            y += n.y * push * invMass / total;
            z += n.z * push * invMass / total;
            if (both) {
                o.x -= n.x * push * o.invMass / total;
                o.y -= n.y * push * o.invMass / total;
                o.z -= n.z * push * o.invMass / total;
            }
        }
    }
}
