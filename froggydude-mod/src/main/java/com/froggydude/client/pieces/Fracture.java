package com.froggydude.client.pieces;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estilhaça uma parte do corpo em cacos tortos, como o Physics Mod no vídeo do
 * FroggyDude ("WHAT HAPPENED TO ME???", 2:50): a parte é cortada por planos
 * aleatórios em células convexas (cunhas, lascas, triângulos) e cada cubo dela
 * (a parte, a camada de cima da skin, a armadura) é recortado pelas mesmas
 * células. As faces de fora levam a textura de verdade; o corte leva a textura
 * do lado mais parecido, esticada e mais escura - no vídeo o caco é skin por
 * fora e "a mesma skin, vermelha" por dentro.
 *
 * Tudo no espaço local da parte (centro do cubo principal na origem).
 */
final class Fracture {

    private static final int V = ModelCapture.V;

    private Fracture() {
    }

    /** Um polígono convexo: vértices com V floats (pos, cor, uv, normal). */
    static final class Poly {
        final List<float[]> v;
        final boolean cap;

        Poly(List<float[]> v, boolean cap) {
            this.v = v;
            this.cap = cap;
        }
    }

    /** Um caco pronto: o desenho por RenderType e os cantos (pra colisão), local da parte. */
    static final class Shard {
        final Map<RenderType, List<Poly>> polys = new LinkedHashMap<>();
        final List<Vector3f> points = new ArrayList<>();
    }

    /** O cubo como sólido (6 faces de 4 vértices). quads: 24 vértices x V, já no espaço local. */
    static List<Poly> solid(float[] quads) {
        List<Poly> out = new ArrayList<>(6);
        for (int f = 0; f < 6; f++) {
            List<float[]> vs = new ArrayList<>(4);
            for (int k = 0; k < 4; k++) {
                float[] v = new float[V];
                System.arraycopy(quads, (f * 4 + k) * V, v, 0, V);
                vs.add(v);
            }
            out.add(new Poly(vs, false));
        }
        return out;
    }

    /**
     * Corta o sólido convexo pelo plano n.p = d e fica com o lado n.p <= d.
     * O corte vira uma face nova (cap) com normal n.
     */
    static List<Poly> clip(List<Poly> solid, float nx, float ny, float nz, float d) {
        List<Poly> out = new ArrayList<>(solid.size() + 1);
        List<float[]> cut = new ArrayList<>();
        for (Poly p : solid) {
            List<float[]> kept = new ArrayList<>(p.v.size() + 2);
            int n = p.v.size();
            for (int i = 0; i < n; i++) {
                float[] a = p.v.get(i), b = p.v.get((i + 1) % n);
                float da = a[0] * nx + a[1] * ny + a[2] * nz - d;
                float db = b[0] * nx + b[1] * ny + b[2] * nz - d;
                if (da <= 0F) kept.add(a);
                if ((da < 0F && db > 0F) || (da > 0F && db < 0F)) {
                    float t = da / (da - db);
                    float[] m = new float[V];
                    for (int k = 0; k < V; k++) m[k] = a[k] + (b[k] - a[k]) * t;
                    kept.add(m);
                    cut.add(m);
                }
            }
            if (kept.size() >= 3) out.add(new Poly(kept, p.cap));
        }
        if (cut.size() >= 3) {
            List<float[]> ring = ring(cut, nx, ny, nz);
            if (ring.size() >= 3) out.add(new Poly(ring, true));
        }
        return out;
    }

    /** Os pontos do corte em volta do centro (sem repetidos), com a normal do plano. */
    private static List<float[]> ring(List<float[]> pts, float nx, float ny, float nz) {
        List<float[]> uniq = new ArrayList<>();
        for (float[] p : pts) {
            boolean dup = false;
            for (float[] q : uniq) {
                float dx = p[0] - q[0], dy = p[1] - q[1], dz = p[2] - q[2];
                if (dx * dx + dy * dy + dz * dz < 1.0E-8F) {
                    dup = true;
                    break;
                }
            }
            if (!dup) uniq.add(p.clone());
        }
        if (uniq.size() < 3) return uniq;
        float cx = 0, cy = 0, cz = 0;
        for (float[] p : uniq) {
            cx += p[0];
            cy += p[1];
            cz += p[2];
        }
        cx /= uniq.size();
        cy /= uniq.size();
        cz /= uniq.size();
        Vector3f n = new Vector3f(nx, ny, nz);
        Vector3f t1 = Math.abs(nx) < 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        t1.cross(n).normalize();
        Vector3f t2 = new Vector3f(n).cross(t1);
        final float fx = cx, fy = cy, fz = cz;
        uniq.sort((a, b) -> Float.compare(
                (float) Math.atan2((a[0] - fx) * t2.x + (a[1] - fy) * t2.y + (a[2] - fz) * t2.z,
                        (a[0] - fx) * t1.x + (a[1] - fy) * t1.y + (a[2] - fz) * t1.z),
                (float) Math.atan2((b[0] - fx) * t2.x + (b[1] - fy) * t2.y + (b[2] - fz) * t2.z,
                        (b[0] - fx) * t1.x + (b[1] - fy) * t1.y + (b[2] - fz) * t1.z)));
        for (float[] p : uniq) {
            p[9] = nx;
            p[10] = ny;
            p[11] = nz;
        }
        return uniq;
    }

    /** Volume de um sólido convexo (tetraedros a partir do centro). */
    static float volume(List<Poly> solid) {
        float cx = 0, cy = 0, cz = 0;
        int n = 0;
        for (Poly p : solid) {
            for (float[] v : p.v) {
                cx += v[0];
                cy += v[1];
                cz += v[2];
                n++;
            }
        }
        if (n == 0) return 0F;
        cx /= n;
        cy /= n;
        cz /= n;
        float vol = 0F;
        Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
        for (Poly p : solid) {
            float[] v0 = p.v.get(0);
            for (int i = 1; i + 1 < p.v.size(); i++) {
                float[] v1 = p.v.get(i), v2 = p.v.get(i + 1);
                a.set(v0[0] - cx, v0[1] - cy, v0[2] - cz);
                b.set(v1[0] - cx, v1[1] - cy, v1[2] - cz);
                c.set(v2[0] - cx, v2[1] - cy, v2[2] - cz);
                vol += Math.abs(a.dot(b.cross(c, new Vector3f()))) / 6F;
            }
        }
        return vol;
    }

    private static float[] centroid(List<Poly> solid) {
        float cx = 0, cy = 0, cz = 0;
        int n = 0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (Poly p : solid) {
            for (float[] v : p.v) {
                cx += v[0];
                cy += v[1];
                cz += v[2];
                n++;
                minX = Math.min(minX, v[0]);
                minY = Math.min(minY, v[1]);
                minZ = Math.min(minZ, v[2]);
                maxX = Math.max(maxX, v[0]);
                maxY = Math.max(maxY, v[1]);
                maxZ = Math.max(maxZ, v[2]);
            }
        }
        return new float[]{cx / n, cy / n, cz / n, maxX - minX, maxY - minY, maxZ - minZ};
    }

    /** Uma célula: o sólido (pra medir e cortar de novo) e os planos que a fecham. */
    private static final class Cell {
        final List<Poly> solid;
        final List<float[]> planes;

        Cell(List<Poly> solid, List<float[]> planes) {
            this.solid = solid;
            this.planes = planes;
        }
    }

    /**
     * Estilhaça a parte em n cacos. cubes = os cubos da parte (o principal primeiro),
     * cada um {RenderType, quads locais}; small = os penduricalhos (nariz, focinho):
     * vão inteiros pro caco onde caem.
     */
    static List<Shard> shatter(List<RenderType> types, List<float[]> cubes, List<RenderType> smallTypes,
                               List<float[]> small, int n, RandomSource r) {
        // a célula inicial: a caixa que cobre todos os cubos da parte (com folga)
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] q : cubes) {
            for (int i = 0; i < q.length; i += V) {
                minX = Math.min(minX, q[i]);
                minY = Math.min(minY, q[i + 1]);
                minZ = Math.min(minZ, q[i + 2]);
                maxX = Math.max(maxX, q[i]);
                maxY = Math.max(maxY, q[i + 1]);
                maxZ = Math.max(maxZ, q[i + 2]);
            }
        }
        float e = 0.01F;
        minX -= e;
        minY -= e;
        minZ -= e;
        maxX += e;
        maxY += e;
        maxZ += e;
        List<float[]> boxPlanes = new ArrayList<>();
        boxPlanes.add(new float[]{1, 0, 0, maxX});
        boxPlanes.add(new float[]{-1, 0, 0, -minX});
        boxPlanes.add(new float[]{0, 1, 0, maxY});
        boxPlanes.add(new float[]{0, -1, 0, -minY});
        boxPlanes.add(new float[]{0, 0, 1, maxZ});
        boxPlanes.add(new float[]{0, 0, -1, -minZ});
        List<Poly> box = boxSolid(minX, minY, minZ, maxX, maxY, maxZ);
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell(box, boxPlanes));
        // corta sempre a maior célula, por um plano torto perto do meio dela
        int guard = 0;
        while (cells.size() < n && guard++ < n * 6) {
            int big = 0;
            float bigVol = -1F;
            for (int i = 0; i < cells.size(); i++) {
                float vol = volume(cells.get(i).solid);
                if (vol > bigVol) {
                    bigVol = vol;
                    big = i;
                }
            }
            Cell c = cells.get(big);
            float[] cen = centroid(c.solid);
            Vector3f nrm = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian());
            if (nrm.lengthSquared() < 1.0E-4F) continue;
            nrm.normalize();
            float px = cen[0] + (r.nextFloat() - 0.5F) * cen[3] * 0.5F;
            float py = cen[1] + (r.nextFloat() - 0.5F) * cen[4] * 0.5F;
            float pz = cen[2] + (r.nextFloat() - 0.5F) * cen[5] * 0.5F;
            float d = nrm.x * px + nrm.y * py + nrm.z * pz;
            List<Poly> a = clip(c.solid, nrm.x, nrm.y, nrm.z, d);
            List<Poly> b = clip(c.solid, -nrm.x, -nrm.y, -nrm.z, -d);
            if (a.size() < 4 || b.size() < 4) continue;
            List<float[]> pa = new ArrayList<>(c.planes);
            pa.add(new float[]{nrm.x, nrm.y, nrm.z, d});
            List<float[]> pb = new ArrayList<>(c.planes);
            pb.add(new float[]{-nrm.x, -nrm.y, -nrm.z, -d});
            cells.set(big, new Cell(a, pa));
            cells.add(new Cell(b, pb));
        }
        // cada célula recorta cada cubo da parte (um caco por célula, na mesma ordem)
        List<Shard> shards = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            Shard s = new Shard();
            shards.add(s);
            for (int ci = 0; ci < cubes.size(); ci++) {
                float[] q = cubes.get(ci);
                List<Poly> frag = solid(q);
                for (float[] pl : cell.planes) {
                    frag = clip(frag, pl[0], pl[1], pl[2], pl[3]);
                    if (frag.size() < 4) break;
                }
                if (frag.size() < 4) continue;
                List<Poly> faces = new ArrayList<>(frag.size());
                for (Poly p : frag) faces.add(p.cap ? texturedCap(p, q) : p);
                s.polys.computeIfAbsent(types.get(ci), t -> new ArrayList<>()).addAll(faces);
                for (Poly p : faces) for (float[] v : p.v) addPoint(s.points, v);
            }
        }
        // os penduricalhos: inteiros, no caco onde o centro deles cai
        for (int si = 0; si < small.size(); si++) {
            float[] q = small.get(si);
            float cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < q.length; i += V) {
                cx += q[i];
                cy += q[i + 1];
                cz += q[i + 2];
            }
            cx /= q.length / V;
            cy /= q.length / V;
            cz /= q.length / V;
            // a célula que contém o centro dele (ou a de centro mais perto)
            int home = 0;
            float bestD = Float.MAX_VALUE;
            for (int ci = 0; ci < cells.size(); ci++) {
                float out = 0F;
                for (float[] pl : cells.get(ci).planes) {
                    out = Math.max(out, pl[0] * cx + pl[1] * cy + pl[2] * cz - pl[3]);
                }
                if (out < bestD) {
                    bestD = out;
                    home = ci;
                }
            }
            Shard s = shards.get(home);
            List<Poly> faces = solid(q);
            s.polys.computeIfAbsent(smallTypes.get(si), t -> new ArrayList<>()).addAll(faces);
            for (Poly p : faces) for (float[] v : p.v) addPoint(s.points, v);
        }
        shards.removeIf(sh -> sh.points.isEmpty());
        return shards;
    }

    private static void addPoint(List<Vector3f> pts, float[] v) {
        for (Vector3f p : pts) {
            float dx = p.x - v[0], dy = p.y - v[1], dz = p.z - v[2];
            if (dx * dx + dy * dy + dz * dz < 1.0E-5F) return;
        }
        pts.add(new Vector3f(v[0], v[1], v[2]));
    }

    private static List<Poly> boxSolid(float x0, float y0, float z0, float x1, float y1, float z1) {
        float[][] c = {
                {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
                {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        int[][] f = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {3, 7, 6, 2}, {0, 4, 7, 3}, {1, 2, 6, 5}};
        List<Poly> out = new ArrayList<>(6);
        for (int[] face : f) {
            List<float[]> vs = new ArrayList<>(4);
            for (int i : face) {
                float[] v = new float[V];
                v[0] = c[i][0];
                v[1] = c[i][1];
                v[2] = c[i][2];
                vs.add(v);
            }
            out.add(new Poly(vs, false));
        }
        return out;
    }

    /**
     * O corte com a textura do lado do cubo mais parecido com ele (o que tem a normal
     * mais alinhada), projetada nesse lado e presa dentro dele - a skin "continua"
     * pra dentro. Mais escuro: carne.
     */
    private static Poly texturedCap(Poly cap, float[] q) {
        float nx = cap.v.get(0)[9], ny = cap.v.get(0)[10], nz = cap.v.get(0)[11];
        int best = 0;
        float bestDot = -1F;
        for (int f = 0; f < 6; f++) {
            int o = f * 4 * V;
            float dot = Math.abs(q[o + 9] * nx + q[o + 10] * ny + q[o + 11] * nz);
            if (dot > bestDot) {
                bestDot = dot;
                best = f;
            }
        }
        int o0 = best * 4 * V, o1 = o0 + V, o3 = o0 + 3 * V;
        float e1x = q[o1] - q[o0], e1y = q[o1 + 1] - q[o0 + 1], e1z = q[o1 + 2] - q[o0 + 2];
        float e2x = q[o3] - q[o0], e2y = q[o3 + 1] - q[o0 + 1], e2z = q[o3 + 2] - q[o0 + 2];
        float l1 = e1x * e1x + e1y * e1y + e1z * e1z, l2 = e2x * e2x + e2y * e2y + e2z * e2z;
        List<float[]> out = new ArrayList<>(cap.v.size());
        for (float[] p : cap.v) {
            float[] v = p.clone();
            float dx = v[0] - q[o0], dy = v[1] - q[o0 + 1], dz = v[2] - q[o0 + 2];
            float s = l1 < 1.0E-8F ? 0F : clamp01((dx * e1x + dy * e1y + dz * e1z) / l1);
            float t = l2 < 1.0E-8F ? 0F : clamp01((dx * e2x + dy * e2y + dz * e2z) / l2);
            // um tico pra dentro da borda: não pega o pixel do vizinho no atlas
            s = 0.04F + s * 0.92F;
            t = 0.04F + t * 0.92F;
            v[7] = q[o0 + 7] + s * (q[o1 + 7] - q[o0 + 7]) + t * (q[o3 + 7] - q[o0 + 7]);
            v[8] = q[o0 + 8] + s * (q[o1 + 8] - q[o0 + 8]) + t * (q[o3 + 8] - q[o0 + 8]);
            v[3] = q[o0 + 3] * 0.72F;
            v[4] = q[o0 + 4] * 0.66F;
            v[5] = q[o0 + 5] * 0.66F;
            v[6] = q[o0 + 6];
            out.add(v);
        }
        return new Poly(out, true);
    }

    private static float clamp01(float x) {
        return x < 0F ? 0F : Math.min(1F, x);
    }

    /**
     * Polígonos -> quads. Todo polígono aqui é convexo, então vai em leque de
     * quadriláteros (0, i, i+1, i+2) - o jogo desenha (0,i,i+1) e (i+1,i+2,0); a sobra
     * repete o último vértice. Metade dos vértices do leque de triângulos.
     */
    static float[] toQuads(List<Poly> polys) {
        int quads = 0;
        for (Poly p : polys) quads += Math.max(0, p.v.size() - 1) / 2;
        float[] out = new float[quads * 4 * V];
        int o = 0;
        for (Poly p : polys) {
            int n = p.v.size();
            if (n < 3) continue;
            float[] a = p.v.get(0);
            for (int i = 1; i + 1 < n; i += 2) {
                float[] b = p.v.get(i), c = p.v.get(i + 1), d = p.v.get(Math.min(i + 2, n - 1));
                System.arraycopy(a, 0, out, o, V);
                System.arraycopy(b, 0, out, o + V, V);
                System.arraycopy(c, 0, out, o + 2 * V, V);
                System.arraycopy(d, 0, out, o + 3 * V, V);
                o += 4 * V;
            }
        }
        return out;
    }
}
