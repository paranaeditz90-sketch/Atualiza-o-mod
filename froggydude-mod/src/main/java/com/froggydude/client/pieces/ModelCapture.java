package com.froggydude.client.pieces;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Desenha o corpo da vítima uma vez "de mentira" (num MultiBufferSource que só
 * anota) e separa o que saiu em cubos do modelo - cada ModelPart.Cube vira 6
 * faces seguidas (Cube.compile). Pega tudo que o desenho de verdade pega: a
 * skin, a armadura, a roupa do aldeão, o toco do braço arrancado, a pose do
 * corpo deitado. Depois junta os cubos da mesma parte (cabeça + chapéu da skin,
 * cabeça + nariz do aldeão, perna + armadura) num pedaço só.
 */
final class ModelCapture {

    /** Uma face: 4 vértices x (pos 3, cor 4, uv 2, normal 3). */
    static final int V = 12;

    private ModelCapture() {
    }

    /** Um cubo do modelo: as 6 faces, no mundo (relativo aos pés da vítima). */
    static final class Cube {
        final RenderType type;
        final float[] quads; // 6 faces x 4 vértices x V
        final Vec3 center;
        final Vec3[] axes = new Vec3[3]; // unitários
        final double[] half = new double[3];
        final Vec3[] corners = new Vec3[8];

        Cube(RenderType type, float[] quads, Vec3 center, Vec3 a0, Vec3 a1, Vec3 a2, double h0, double h1, double h2) {
            this.type = type;
            this.quads = quads;
            this.center = center;
            axes[0] = a0;
            axes[1] = a1;
            axes[2] = a2;
            half[0] = h0;
            half[1] = h1;
            half[2] = h2;
            int i = 0;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        corners[i++] = center.add(a0.scale(sx * h0)).add(a1.scale(sy * h1)).add(a2.scale(sz * h2));
                    }
                }
            }
        }

        double volume() {
            return 8.0D * Math.max(half[0], 0.01D) * Math.max(half[1], 0.01D) * Math.max(half[2], 0.01D);
        }

        /** O ponto está dentro (com folga)? */
        boolean contains(Vec3 p, double margin) {
            Vec3 d = p.subtract(center);
            for (int k = 0; k < 3; k++) {
                if (Math.abs(d.dot(axes[k])) > half[k] + margin) return false;
            }
            return true;
        }
    }

    /** Anota os vértices de um RenderType. */
    private static final class Sink implements VertexConsumer {
        final List<float[]> verts = new ArrayList<>();
        private float x, y, z, r = 1, g = 1, b = 1, a = 1, u, v, nx, ny = 1, nz;

        @Override
        public VertexConsumer vertex(double px, double py, double pz) {
            x = (float) px;
            y = (float) py;
            z = (float) pz;
            return this;
        }

        @Override
        public VertexConsumer color(int cr, int cg, int cb, int ca) {
            r = cr / 255F;
            g = cg / 255F;
            b = cb / 255F;
            a = ca / 255F;
            return this;
        }

        @Override
        public VertexConsumer uv(float tu, float tv) {
            u = tu;
            v = tv;
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
            nx = qx;
            ny = qy;
            nz = qz;
            return this;
        }

        @Override
        public void endVertex() {
            verts.add(new float[]{x, y, z, r, g, b, a, u, v, nx, ny, nz});
        }

        @Override
        public void vertex(float px, float py, float pz, float cr, float cg, float cb, float ca, float tu, float tv,
                           int overlay, int light, float qx, float qy, float qz) {
            verts.add(new float[]{px, py, pz, cr, cg, cb, ca, tu, tv, qx, qy, qz});
        }

        @Override
        public void defaultColor(int dr, int dg, int db, int da) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    /**
     * Os cubos do corpo dessa entidade agora (relativos à posição dela). Vazio se o
     * renderer não é de modelo de cubos (GeckoLib, mod estranho) - aí não despedaça.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static List<Cube> capture(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<? super LivingEntity> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer)) return List.of();
        Map<RenderType, Sink> sinks = new LinkedHashMap<>();
        // texto do nome, sombra, fogo: vão pro lixo
        Sink trash = new Sink();
        MultiBufferSource source = type -> type.format() == DefaultVertexFormat.NEW_ENTITY
                ? sinks.computeIfAbsent(type, t -> new Sink()) : trash;
        PoseStack ps = new PoseStack();
        Vec3 off = renderer.getRenderOffset(entity, 1.0F);
        ps.translate(off.x, off.y, off.z);
        // de pé como estava (sem o tombo da morte nem o vermelho de apanhar)
        int death = entity.deathTime;
        int hurt = entity.hurtTime;
        entity.deathTime = 0;
        entity.hurtTime = 0;
        try {
            ((EntityRenderer) renderer).render(entity, entity.getYRot(), 1.0F, ps, source, 0xF000F0);
        } catch (RuntimeException e) {
            return List.of();
        } finally {
            entity.deathTime = death;
            entity.hurtTime = hurt;
        }
        List<Cube> cubes = new ArrayList<>();
        for (Map.Entry<RenderType, Sink> e : sinks.entrySet()) {
            List<float[]> vs = e.getValue().verts;
            int quads = vs.size() / 4;
            int q = 0;
            while (q + 6 <= quads) {
                Cube c = box(e.getKey(), vs, q);
                if (c != null) {
                    cubes.add(c);
                    q += 6;
                } else {
                    q++;
                }
            }
        }
        return cubes;
    }

    /** As 6 faces a partir da face q formam uma caixa? */
    private static Cube box(RenderType type, List<float[]> vs, int q) {
        List<Vec3> unique = new ArrayList<>(8);
        for (int i = q * 4; i < (q + 6) * 4; i++) {
            float[] v = vs.get(i);
            Vec3 p = new Vec3(v[0], v[1], v[2]);
            boolean seen = false;
            for (Vec3 u : unique) {
                if (u.distanceToSqr(p) < 1.0E-8D) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                unique.add(p);
                if (unique.size() > 8) return null;
            }
        }
        if (unique.size() != 8 && unique.size() != 4) return null;
        Vec3 c = Vec3.ZERO;
        for (Vec3 u : unique) c = c.add(u);
        c = c.scale(1.0D / unique.size());
        // os eixos saem da 1ª face (2 arestas) e da distância até a face oposta
        float[] v0 = vs.get(q * 4), v1 = vs.get(q * 4 + 1), v3 = vs.get(q * 4 + 3);
        Vec3 p0 = new Vec3(v0[0], v0[1], v0[2]);
        Vec3 e1 = new Vec3(v1[0], v1[1], v1[2]).subtract(p0);
        Vec3 e2 = new Vec3(v3[0], v3[1], v3[2]).subtract(p0);
        double l1 = e1.length(), l2 = e2.length();
        if (l1 < 1.0E-5D && l2 < 1.0E-5D) return null;
        Vec3 a1 = l1 < 1.0E-5D ? perpendicular(e2.normalize()) : e1.scale(1.0D / l1);
        Vec3 a2 = l2 < 1.0E-5D ? a1.cross(perpendicular(a1)).normalize() : e2.scale(1.0D / l2);
        // aresta degenerada ou não ortogonal: não é caixa
        if (Math.abs(a1.dot(a2)) > 0.02D) return null;
        Vec3 a3 = a1.cross(a2).normalize();
        // profundidade: o centro da face 0 até o centro da caixa, x2
        Vec3 f0 = Vec3.ZERO;
        for (int i = 0; i < 4; i++) {
            float[] v = vs.get(q * 4 + i);
            f0 = f0.add(v[0], v[1], v[2]);
        }
        f0 = f0.scale(0.25D);
        double depth = Math.abs(c.subtract(f0).dot(a3));
        // as faces da caixa: todos os cantos dentro dos 3 eixos
        double h1 = l1 * 0.5D, h2 = l2 * 0.5D, h3 = depth;
        for (Vec3 u : unique) {
            Vec3 d = u.subtract(c);
            if (Math.abs(d.dot(a1)) > h1 + 1.0E-3D || Math.abs(d.dot(a2)) > h2 + 1.0E-3D
                    || Math.abs(d.dot(a3)) > h3 + 1.0E-3D) {
                return null;
            }
        }
        float[] quads = new float[24 * V];
        for (int i = 0; i < 24; i++) System.arraycopy(vs.get(q * 4 + i), 0, quads, i * V, V);
        return new Cube(type, quads, c, a1, a2, a3, h1, h2, h3);
    }

    private static Vec3 perpendicular(Vec3 a) {
        Vec3 t = Math.abs(a.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return a.cross(t).normalize();
    }

    /**
     * Junta os cubos da mesma parte, como o Physics Mod deixa: a camada de cima vai
     * junto com o que cobre (chapéu da skin + cabeça, jaqueta + tronco, armadura,
     * a roupa da profissão do aldeão, o tronco dentro da túnica) e o que é minúsculo
     * e encosta (nariz do aldeão, focinho, chifre, toco do braço). Pernas, braços, a
     * aba do chapéu: soltos. O maior manda (caixa de colisão); vem primeiro na lista.
     */
    static List<List<Cube>> group(List<Cube> cubes) {
        List<Cube> sorted = new ArrayList<>(cubes);
        sorted.sort((x, y) -> Double.compare(y.volume(), x.volume()));
        List<List<Cube>> groups = new ArrayList<>();
        for (Cube c : sorted) {
            List<Cube> home = null;
            for (List<Cube> g : groups) {
                Cube main = g.get(0);
                double ratio = c.volume() / main.volume();
                if (ratio > 0.35D && main.contains(c.center, 0.02D)) {
                    home = g;
                    break;
                }
                if (ratio < 0.05D && touches(c, main)) {
                    home = g;
                    break;
                }
            }
            if (home == null) {
                home = new ArrayList<>();
                groups.add(home);
            }
            home.add(c);
        }
        return groups;
    }

    private static boolean touches(Cube small, Cube big) {
        for (Vec3 k : small.corners) {
            if (big.contains(k, 0.03D)) return true;
        }
        return big.contains(small.center, 0.03D);
    }

    /** Rotação (colunas = eixos do cubo principal) pra um quaternion. */
    static org.joml.Quaternionf rotation(Cube main) {
        Matrix3f m = new Matrix3f(
                (float) main.axes[0].x, (float) main.axes[0].y, (float) main.axes[0].z,
                (float) main.axes[1].x, (float) main.axes[1].y, (float) main.axes[1].z,
                (float) main.axes[2].x, (float) main.axes[2].y, (float) main.axes[2].z);
        return new org.joml.Quaternionf().setFromNormalized(m).normalize();
    }

    static Vector3f toVec(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }
}
