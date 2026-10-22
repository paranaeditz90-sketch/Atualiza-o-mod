package com.froggydude.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * O estudo da presa (IA Real, v0.6): o que ele sabe da ROTINA de um jogador -
 * os lugares que ela usa (bau, cama, bancada/fornalha, onde minera), de quanto
 * em quanto tempo volta em cada - e o "tempero": o quanto de medo ela já
 * acumulou (0-100). Salvo no mundo junto com o resto do cérebro (PlayerMemory).
 *
 * "O FroggyDude estuda a presa e vê o que mais assusta ou deixa o jogador
 * paranoico, pra deixar a carne mais saborosa" (LM). O Grox (0:56-1:23) se tranca
 * na casa, fica de janela em janela, e ele já estava lá dentro, no canto do teto.
 */
public class PreyStudy {

    public enum Kind { STORAGE, BED, WORK, MINE }

    /** Um lugar que ela usa. */
    public static final class Place {
        public final Kind kind;
        public BlockPos pos;
        public String dim;
        /** Última vez que ela esteve aqui (gameTime). */
        public long last;
        /** Média de ticks entre uma volta e outra (0 = ainda não voltou). */
        public float gap;
        public int visits;
        /** Quanto ela usa (decai com o tempo). */
        public float weight;
        /** Tem teto em cima (casa, caverna): dá pra esperar ela lá dentro. */
        public boolean roofed;

        Place(Kind kind, BlockPos pos, String dim) {
            this.kind = kind;
            this.pos = pos;
            this.dim = dim;
        }

        /** O quanto ela está "na hora" de voltar: 0 acabou de sair, 1 é o tempo de costume. */
        public float due(long now) {
            return gap <= 0F ? 0F : Math.min(3F, (now - last) / gap);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Kind", kind.name());
            t.put("Pos", NbtUtils.writeBlockPos(pos));
            t.putString("Dim", dim);
            t.putLong("Last", last);
            t.putFloat("Gap", gap);
            t.putInt("Visits", visits);
            t.putFloat("Weight", weight);
            t.putBoolean("Roofed", roofed);
            return t;
        }

        @Nullable
        static Place load(CompoundTag t) {
            try {
                Place p = new Place(Kind.valueOf(t.getString("Kind")), NbtUtils.readBlockPos(t.getCompound("Pos")), t.getString("Dim"));
                p.last = t.getLong("Last");
                p.gap = t.getFloat("Gap");
                p.visits = t.getInt("Visits");
                p.weight = t.getFloat("Weight");
                p.roofed = t.getBoolean("Roofed");
                return p;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final int MAX_PLACES = 16;
    /** Volta: passou mais que isso longe do lugar (1 min). */
    private static final int RETURN_AFTER = 1200;
    /** Medo cai pela metade a cada 5 minutos sem susto. */
    private static final float FEAR_HALF_LIFE = 6000F;

    public final List<Place> places = new ArrayList<>();
    private float fear;
    private long fearAt;
    /** Até quando (gameTime) ele só brinca com ela, sem atacar. */
    public long teaseUntil;
    /** Quando foi a última brincadeira (pra não recomeçar toda hora). */
    public long teaseStarted = Long.MIN_VALUE / 2;

    /** Ela usou esse lugar agora. */
    public void visit(Kind kind, BlockPos pos, String dim, long now, boolean roofed) {
        int merge = kind == Kind.MINE ? 12 : 4;
        Place p = null;
        for (Place q : places) {
            if (q.kind == kind && q.dim.equals(dim) && q.pos.distSqr(pos) <= merge * merge) {
                p = q;
                break;
            }
        }
        if (p == null) {
            if (places.size() >= MAX_PLACES) places.remove(places.stream().min(Comparator.comparingDouble(q -> q.weight)).orElseThrow());
            p = new Place(kind, pos.immutable(), dim);
            p.last = now;
            places.add(p);
        }
        long since = now - p.last;
        if (since > RETURN_AFTER) {
            // voltou: aprende de quanto em quanto tempo ela volta
            p.gap = p.gap <= 0F ? since : p.gap * 0.7F + since * 0.3F;
            p.visits++;
            p.weight += 1.0F;
        } else {
            p.weight += 0.15F;
        }
        if (kind == Kind.MINE) p.pos = pos.immutable(); // segue o túnel
        p.last = now;
        p.roofed = roofed;
        for (Place q : places) q.weight *= 0.999F;
    }

    /**
     * O melhor lugar pra esperar ela agora: um que ela usa bastante, com teto,
     * que já está na hora dela voltar e de onde ela está longe.
     */
    @Nullable
    public Place stakeout(String dim, BlockPos playerAt, long now) {
        Place best = null;
        float bestScore = 0F;
        for (Place p : places) {
            if (!p.dim.equals(dim) || !p.roofed || p.visits < 1) continue;
            if (p.pos.distSqr(playerAt) < 40 * 40) continue; // perto dela: não dá pra chegar antes
            float due = p.due(now);
            if (due < 0.5F) continue;
            float score = p.weight * Math.min(due, 1.5F) * (p.kind == Kind.MINE ? 0.8F : 1.0F);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    public float fear(long now) {
        if (now > fearAt) {
            fear *= (float) Math.pow(0.5D, (now - fearAt) / FEAR_HALF_LIFE);
            fearAt = now;
        }
        return fear;
    }

    public void addFear(float amount, long now) {
        fear = Math.max(0F, Math.min(100F, fear(now) + amount));
    }

    public boolean teasing(long now) {
        return now < teaseUntil;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Place p : places) l.add(p.save());
        t.put("Places", l);
        t.putFloat("Fear", fear);
        t.putLong("FearAt", fearAt);
        t.putLong("TeaseUntil", teaseUntil);
        t.putLong("TeaseStarted", teaseStarted);
        return t;
    }

    public void load(CompoundTag t) {
        places.clear();
        for (Tag e : t.getList("Places", Tag.TAG_COMPOUND)) {
            Place p = Place.load((CompoundTag) e);
            if (p != null) places.add(p);
        }
        fear = t.getFloat("Fear");
        fearAt = t.getLong("FearAt");
        teaseUntil = t.getLong("TeaseUntil");
        teaseStarted = t.contains("TeaseStarted") ? t.getLong("TeaseStarted") : Long.MIN_VALUE / 2;
    }
}
