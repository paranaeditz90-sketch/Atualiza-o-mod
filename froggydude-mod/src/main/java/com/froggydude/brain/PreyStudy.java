package com.froggydude.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        /**
         * (v0.7.34, a rotina: "em que hora sai e quando volta pra casa, se minera de noite") Em que hora do dia ela usa
         * esse lugar: 8 faixas de 3 horas do jogo (0 = 6h-9h ... 6 = 0h-3h), contando as vezes, as antigas pesando menos.
         */
        public final float[] hours = new float[HOUR_BINS];
        /** A última faixa contada nesta estadia (não salva): cada volta conta 1, e uma estadia longa 1 por faixa. */
        int lastBin = -1;

        /** O quanto essa hora do dia é a de costume dela aqui: 0 nunca, 1 a hora em que mais vem; 0,5 sem dados. */
        public float usualAt(long dayTime) {
            float max = 0F;
            for (float h : hours) max = Math.max(max, h);
            if (max <= 0F) return 0.5F;
            return hours[hourBin(dayTime)] / max;
        }

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
            int[] h = new int[HOUR_BINS];
            for (int i = 0; i < HOUR_BINS; i++) h[i] = Math.round(hours[i] * 100F);
            t.putIntArray("Hours", h);
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
                int[] h = t.getIntArray("Hours");
                for (int i = 0; i < Math.min(h.length, HOUR_BINS); i++) p.hours[i] = h[i] / 100F;
                return p;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final int MAX_PLACES = 16;
    static final int HOUR_BINS = 8;

    /** A faixa de 3 horas do dia do jogo (o dia começa às 6h no jogo: dayTime 0). */
    static int hourBin(long dayTime) {
        return (int) Math.floorMod(dayTime, 24000L) / 3000;
    }
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
    /** O próximo jumpscare (JumpScare) pode vir a partir daqui (hora do jogo); 0 = ainda não sorteado. */
    public long jumpscareAt;

    /** Ela usou esse lugar agora (sem saber a hora do dia). */
    public void visit(Kind kind, BlockPos pos, String dim, long now, boolean roofed) {
        visit(kind, pos, dim, now, roofed, -1L);
    }

    /** Ela usou esse lugar agora; dayTime (hora do dia do jogo, -1 = não sabe) entra na rotina dela. */
    public void visit(Kind kind, BlockPos pos, String dim, long now, boolean roofed, long dayTime) {
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
        // a hora conta uma vez por visita (cada bloco minerado não é uma visita: senão minerar 100 blocos numa hora
        // apagava todas as outras), e de novo se ela fica tanto tempo que a faixa de hora muda (minerou a noite toda)
        int bin = dayTime >= 0L ? hourBin(dayTime) : -1;
        if (bin >= 0 && (since > RETURN_AFTER || bin != p.lastBin)) {
            for (int i = 0; i < HOUR_BINS; i++) p.hours[i] *= 0.95F;
            p.hours[bin] += 1F;
            p.lastBin = bin;
        }
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
        return stakeout(dim, playerAt, now, -1L);
    }

    /** Igual, pesando a hora do dia: o lugar em que ela costuma estar NESSA hora (a rotina) vale mais. */
    @Nullable
    public Place stakeout(String dim, BlockPos playerAt, long now, long dayTime) {
        Place best = null;
        float bestScore = 0F;
        for (Place p : places) {
            if (!p.dim.equals(dim) || !p.roofed || p.visits < 1) continue;
            if (p.pos.distSqr(playerAt) < 40 * 40) continue; // perto dela: não dá pra chegar antes
            float due = p.due(now);
            if (due < 0.5F) continue;
            float score = p.weight * Math.min(due, 1.5F) * (p.kind == Kind.MINE ? 0.8F : 1.0F);
            if (dayTime >= 0L) score *= 0.5F + p.usualAt(dayTime);
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

    // ----- (v0.7.34) o diretor de tensão (world.TensionDirector)
    /** O quanto cada sinal assustou ela (a paranoia subiu nos segundos depois), média que pesa mais os recentes. */
    private final Map<String, Float> signFear = new HashMap<>();
    @Nullable
    private BlockPos lastDoor;
    private String lastDoorDim = "";

    public void learnSign(String sign, float paranoiaDelta) {
        Float old = signFear.get(sign);
        signFear.put(sign, old == null ? paranoiaDelta : old * 0.6F + paranoiaDelta * 0.4F);
    }

    public float signFear(String sign) {
        return signFear.getOrDefault(sign, 0F);
    }

    /** Ela mexeu nessa porta (abriu ou fechou). */
    public void noteDoor(BlockPos pos, String dim) {
        lastDoor = pos.immutable();
        lastDoorDim = dim;
    }

    @Nullable
    public BlockPos lastDoor(String dim) {
        return dim.equals(lastDoorDim) ? lastDoor : null;
    }

    /** A casa dela nessa dimensão: a cama mais usada (ou o baú/bancada mais usado), null se não sabe. */
    @Nullable
    public BlockPos home(String dim) {
        Place best = null;
        for (Place p : places) {
            if (!p.dim.equals(dim) || p.kind == Kind.MINE) continue;
            float w = p.weight * (p.kind == Kind.BED ? 2F : 1F);
            if (best == null || w > best.weight * (best.kind == Kind.BED ? 2F : 1F)) best = p;
        }
        return best == null ? null : best.pos;
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
        t.putLong("JumpscareAt", jumpscareAt);
        CompoundTag sf = new CompoundTag();
        signFear.forEach(sf::putFloat);
        t.put("SignFear", sf);
        if (lastDoor != null) {
            t.put("LastDoor", NbtUtils.writeBlockPos(lastDoor));
            t.putString("LastDoorDim", lastDoorDim);
        }
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
        jumpscareAt = t.getLong("JumpscareAt");
        signFear.clear();
        CompoundTag sf = t.getCompound("SignFear");
        for (String k : sf.getAllKeys()) signFear.put(k, sf.getFloat(k));
        lastDoor = t.contains("LastDoor") ? NbtUtils.readBlockPos(t.getCompound("LastDoor")) : null;
        lastDoorDim = t.getString("LastDoorDim");
    }
}
