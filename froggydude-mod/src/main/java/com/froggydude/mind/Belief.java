package com.froggydude.mind;

import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (v0.7.68, a Mente) A crença de ONDE a pessoa provavelmente está, quando ele não está vendo: uma grade de colunas de 2x2
 * blocos com a chance de cada uma (o "mapa de ocupação" do Damián Isla). Nasce de onde ele a viu por último (e pra onde ela
 * corria), de barulhos que ouviu e de pistas; a cada segundo a chance "vaza" pras colunas vizinhas onde dá pra andar (ela
 * pode ter ido pra lá); o que ele OLHA e não vê ninguém zera. Ele procura na coluna mais provável e perto.
 */
public final class Belief {

    static final int MAX_CELLS = 220;
    static final float SPREAD = 0.3F;

    public static final class Cell {
        final int cx, cz;
        int y;
        float p;

        Cell(int cx, int cz, int y) {
            this.cx = cx;
            this.cz = cz;
            this.y = y;
        }

        public Vec3 center() {
            return new Vec3(cx * 2 + 1.0D, y, cz * 2 + 1.0D);
        }
    }

    private final Map<Long, Cell> cells = new HashMap<>();
    /** Pra cada coluna já testada: a altura onde dá pra ficar de pé (Integer.MIN_VALUE = não dá). */
    private final Map<Long, Integer> stand = new HashMap<>();

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public void clear() {
        cells.clear();
        stand.clear();
    }

    /** Somou: põe peso em volta de um ponto (raio em blocos; 0 = só a coluna). */
    public void add(Level level, Vec3 pos, float w, double radius) {
        int r = (int) Math.ceil(radius / 2.0D);
        int ccx = Mth2.floorDiv2(pos.x), ccz = Mth2.floorDiv2(pos.z);
        List<Cell> got = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                Cell c = cell(level, ccx + dx, ccz + dz, (int) Math.floor(pos.y));
                if (c != null) got.add(c);
            }
        }
        if (got.isEmpty()) {
            Cell c = new Cell(ccx, ccz, (int) Math.floor(pos.y));
            cells.put(key(ccx, ccz), c);
            got.add(c);
        }
        for (Cell c : got) c.p += w / got.size();
        normalize();
    }

    /** Viu agora: certeza ali. */
    public void collapse(Level level, Vec3 pos) {
        cells.clear();
        add(level, pos, 1.0F, 0.0D);
    }

    @Nullable
    private Cell cell(Level level, int cx, int cz, int nearY) {
        long k = key(cx, cz);
        Cell c = cells.get(k);
        if (c != null) return c;
        Integer y = stand.get(k);
        if (y == null) {
            y = standY(level, cx * 2 + 1, cz * 2 + 1, nearY);
            stand.put(k, y);
        }
        if (y == Integer.MIN_VALUE) return null;
        c = new Cell(cx, cz, y);
        cells.put(k, c);
        return c;
    }

    /** Onde dá pra ficar de pé nessa coluna perto da altura dada (3 pra cima, 3 pra baixo). */
    static int standY(Level level, int x, int z, int nearY) {
        for (int i = 0; i <= 6; i++) {
            int y = nearY + ((i & 1) == 0 ? i / 2 : -(i + 1) / 2);
            BlockPos at = new BlockPos(x, y, z);
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                    && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
                    && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Um segundo passou sem ver: a chance vaza pras vizinhas onde dá pra andar. */
    public void diffuse(Level level) {
        if (cells.isEmpty()) return;
        List<Cell> now = new ArrayList<>(cells.values());
        Map<Long, Float> add = new HashMap<>();
        for (Cell c : now) {
            if (c.p < 1.0E-4F) continue;
            float out = c.p * SPREAD;
            int n = 0;
            Cell[] nb = new Cell[8];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Cell o = cell(level, c.cx + dx, c.cz + dz, c.y);
                    if (o != null && Math.abs(o.y - c.y) <= 2) nb[n++] = o;
                }
            }
            if (n == 0) continue;
            c.p -= out;
            for (int i = 0; i < n; i++) add.merge(key(nb[i].cx, nb[i].cz), out / n, Float::sum);
        }
        for (Map.Entry<Long, Float> e : add.entrySet()) {
            Cell c = cells.get(e.getKey());
            if (c != null) c.p += e.getValue();
        }
        prune();
        normalize();
    }

    /** O que ele está olhando agora e não tem ninguém: zera (as mais prováveis primeiro, poucas por vez). */
    public void clearSeen(FroggydudeEntity f, int max) {
        if (cells.isEmpty()) return;
        List<Cell> top = new ArrayList<>(cells.values());
        top.sort((a, b) -> Float.compare(b.p, a.p));
        boolean changed = false;
        for (int i = 0; i < Math.min(max, top.size()); i++) {
            Cell c = top.get(i);
            if (Senses.canSeePoint(f, c.center().add(0, 1.0D, 0), 24.0D)) {
                c.p *= 0.02F;
                changed = true;
            }
        }
        // a chance que sobrou vai pro que ele ainda não olhou; se olhou tudo, fica fraquinha e vaza no próximo segundo
        float sum = 0F;
        for (Cell c : cells.values()) sum += c.p;
        if (changed && sum > 0.05F) normalize();
    }

    /** Onde procurar: a coluna que vale mais (chance, e perto dele conta um pouco). */
    @Nullable
    public Cell best(Vec3 from) {
        Cell best = null;
        double score = -1;
        for (Cell c : cells.values()) {
            double s = c.p / (1.0D + c.center().distanceTo(from) / 24.0D);
            if (s > score) {
                score = s;
                best = c;
            }
        }
        return best;
    }

    /** Quanto sobrou de chance (1 = normal; perto de 0 = ele olhou tudo e ela não estava em nenhum). */
    public float mass() {
        float sum = 0F;
        for (Cell c : cells.values()) sum += c.p;
        return sum;
    }

    /**
     * Ela não está em nenhum lugar que ele olhou: pode ter ido até onde dá pra correr desde que sumiu. Um anel de lugares
     * possíveis a essa distância de onde ela estava (os que ele está vendo agora saem na próxima olhada).
     */
    public void ring(Level level, Vec3 origin, double radius) {
        cells.clear();
        int n = Math.max(8, (int) (radius * 1.2D));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0D * i / n;
            Vec3 at = origin.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            Cell c = cell(level, Mth2.floorDiv2(at.x), Mth2.floorDiv2(at.z), (int) Math.floor(origin.y));
            if (c != null) c.p += 1.0F;
        }
        normalize();
    }

    /** A chance da coluna mais provável (quão certo ele está). */
    public float peak() {
        float m = 0F;
        for (Cell c : cells.values()) m = Math.max(m, c.p);
        return m;
    }

    public int size() {
        return cells.size();
    }

    private void prune() {
        if (cells.size() <= MAX_CELLS) {
            cells.values().removeIf(c -> c.p < 1.0E-5F);
            return;
        }
        List<Cell> all = new ArrayList<>(cells.values());
        all.sort((a, b) -> Float.compare(b.p, a.p));
        cells.clear();
        for (int i = 0; i < MAX_CELLS; i++) cells.put(key(all.get(i).cx, all.get(i).cz), all.get(i));
    }

    private void normalize() {
        float sum = 0F;
        for (Cell c : cells.values()) sum += c.p;
        if (sum <= 1.0E-6F) {
            cells.clear();
            return;
        }
        for (Cell c : cells.values()) c.p /= sum;
    }

    static final class Mth2 {
        static int floorDiv2(double v) {
            return Math.floorDiv((int) Math.floor(v), 2);
        }
    }
}
