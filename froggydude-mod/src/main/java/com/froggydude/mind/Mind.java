package com.froggydude.mind;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.player.ArmLoss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (v0.7.68) A Mente do FroggyDude, etapa 1 (LM 24/11: "uma IA que realmente pensa e não faz nada sem sentido; age como um
 * agente"). Perceber -> acreditar -> agir:
 * <ul>
 * <li>ele só SABE de alguém que viu (cone, luz, linha de visão; a certeza sobe aos poucos: antes de ter certeza ele vira a
 * cabeça pro que viu de relance), ouviu (passos, porta, baú, bloco quebrando, comendo: {@link Hearing}) ou que o machucou;</li>
 * <li>quando perde de vista, não sabe mais onde ela está: tem uma CRENÇA ({@link Belief}) que nasce de onde a viu por
 * último e pra onde corria, e vaza com o tempo; procura ali ({@code FroggySearchGoal}), olha em volta, e pode perder de
 * verdade e desistir;</li>
 * <li>pistas de longe (o cheiro de sangue de quem está sem braço, a "bússola" do manhunt, o diretor) dão só a direção/a
 * região, com erro proporcional à distância, nunca o lugar exato.</li>
 * </ul>
 * Cada mudança deixa um motivo em texto ({@link #thought()}; /froggydude pensamento e o log do mundo -dev).
 */
public final class Mind {

    /** Depois de perder de vista, ainda corre pra onde ela foi por esse tempo (dobrou a esquina agora). */
    public static final int CHASE_MEMORY = 40;
    /** Procurando sem achar nada por esse tempo: desiste (barulho sozinho: menos). */
    public static final int GIVE_UP = 900;
    public static final int GIVE_UP_NOISE = 400;
    /** A bússola do manhunt (LM 24/11: só a direção, de tempos em tempos). */
    static final int COMPASS_EVERY = 200;

    private final FroggydudeEntity f;
    private final Map<UUID, Float> aware = new HashMap<>();
    private final Belief belief = new Belief();
    /** Em quem ele está pensando (o alvo, ou quem ele ouviu e foi ver). */
    @Nullable
    private UUID focus;
    private long seenAt = -100000L;
    private Vec3 seenPos = Vec3.ZERO;
    private Vec3 seenVel = Vec3.ZERO;
    private Vec3 prevPos = null;
    /** Começou a procurar (sem ver) em; -1 = não está procurando. */
    private long searchSince = -1L;
    /** Só ouviu (nunca viu nessa procura): desiste mais cedo. */
    private boolean noiseOnly;
    private long lastCompass = -100000L;
    private long lastHeard = -100000L;
    private long lastRumor = -100000L;
    private Vec3 lastClue = Vec3.ZERO;
    private String thought = "calmo";

    public Mind(FroggydudeEntity f) {
        this.f = f;
    }

    private long now() {
        return f.level().getGameTime();
    }

    public String thought() {
        return thought;
    }

    private void think(String t) {
        if (!t.equals(thought)) {
            thought = t;
            f.brainLog("mente: " + t);
        }
    }

    @Nullable
    public UUID focus() {
        return focus;
    }

    @Nullable
    public Player subject() {
        if (focus == null) return null;
        Player p = f.level().getPlayerByUUID(focus);
        return p != null && p.isAlive() && !p.isSpectator() ? p : null;
    }

    public Belief belief() {
        return belief;
    }

    public long searchSince() {
        return searchSince;
    }

    /** A certeza (0..1) de que viu essa pessoa; 1 = viu mesmo. */
    public float awareness(Player p) {
        return aware.getOrDefault(p.getUUID(), 0F);
    }

    /** Viu de verdade (pra escolher alvo). */
    public boolean hasSpotted(LivingEntity e) {
        return !(e instanceof Player p) || awareness(p) >= 1.0F;
    }

    /**
     * Ele PERCEBE essa pessoa agora: viu há pouco (até 2 s depois de sumir ainda corre pra onde ela foi) ou está colado
     * nela. Fora disso, ele não sabe onde ela está: procura.
     */
    public boolean perceives(LivingEntity t) {
        if (!(t instanceof Player p)) return true;
        if (f.distanceTo(p) <= Senses.TOUCH + 0.5D) return true;
        return p.getUUID().equals(focus) && now() - seenAt <= CHASE_MEMORY;
    }

    /** A cada tick (no servidor), fora das cenas. */
    public void tick() {
        if (!(f.level() instanceof ServerLevel sl)) return;
        long t = now();
        if (f.tickCount % 2 == 0) look(sl, t);
        Player s = subject();
        if (s == null) {
            if (focus != null && searchSince >= 0) forget("sumiu");
        } else if (!perceives(s)) {
            if (searchSince < 0) startSearch(sl, s, t);
            if (f.tickCount % 10 == 0) belief.clearSeen(f, 6);
            if (f.tickCount % 20 == 0) {
                belief.diffuse(sl);
                // olhou tudo em volta e nada: ela já correu pra longe (até ~4 blocos/s desde que sumiu)
                if (belief.isEmpty() || belief.mass() < 0.2F) {
                    double r = Math.min(56.0D, 4.0D + 0.2D * (t - Math.max(seenAt, searchSince)));
                    belief.ring(sl, origin(), r);
                    think("não está aqui: pode ter ido até " + (int) r + " blocos de " + blk(origin()));
                }
            }
        }
        if (f.tickCount % 40 == 0) clues(sl, t);
    }

    /** Olhar: quem está no cone, na luz, sem nada na frente. A certeza sobe aos poucos. */
    private void look(ServerLevel sl, long t) {
        for (ServerPlayer p : sl.players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
                aware.remove(p.getUUID());
                continue;
            }
            double d = f.distanceTo(p);
            float a = aware.getOrDefault(p.getUUID(), 0F);
            if (d < 100.0D && Senses.canSee(f, p)) {
                a = Math.min(1.0F, a + Senses.gain(f, p));
                if (a >= 1.0F) {
                    saw(sl, p, t);
                } else if (a >= 0.3F && (!p.getUUID().equals(focus) || t - seenAt > CHASE_MEMORY)) {
                    // viu alguma coisa de relance: vira a cabeça pra lá, antes de ter certeza
                    f.getLookControl().setLookAt(p.getX(), p.getEyeY(), p.getZ(), 30.0F, 30.0F);
                    if (focus == null) {
                        focus = p.getUUID();
                        belief.add(sl, p.position(), 0.6F, 2.0D);
                    }
                    think("vi alguma coisa ali, perto de " + blk(p.position()));
                }
            } else {
                a = Math.max(0F, a - (p.getUUID().equals(focus) && t - seenAt < 200 ? 0.01F : 0.04F));
            }
            if (a <= 0F) aware.remove(p.getUUID());
            else aware.put(p.getUUID(), a);
        }
    }

    private void saw(ServerLevel sl, Player p, long t) {
        boolean wasLost = searchSince >= 0 || !p.getUUID().equals(focus);
        Vec3 pos = p.position();
        seenVel = prevPos != null && t - seenAt <= 4 ? pos.subtract(prevPos).scale(1.0D / Math.max(1, t - seenAt)) : seenVel;
        if (seenVel.length() > 0.4D) seenVel = seenVel.normalize().scale(0.4D); // teleporte/pérola não é "correr pra lá"
        prevPos = pos;
        focus = p.getUUID();
        seenAt = t;
        seenPos = pos;
        searchSince = -1L;
        noiseOnly = false;
        belief.collapse(sl, pos);
        if (wasLost) think("vendo " + p.getScoreboardName() + " em " + blk(pos));
    }

    private void startSearch(ServerLevel sl, Player p, long t) {
        searchSince = t;
        if (t - seenAt < 600) {
            // perdeu de vista agora: ela estava ali e corria pra lá
            Vec3 v = seenVel.multiply(1, 0, 1);
            belief.clear();
            belief.add(sl, seenPos, 0.35F, 2.0D);
            if (v.lengthSqr() > 1.0E-4D) {
                belief.add(sl, seenPos.add(v.scale(20)), 0.35F, 3.0D);
                belief.add(sl, seenPos.add(v.scale(45)), 0.3F, 5.0D);
            }
            think("perdi " + p.getScoreboardName() + " de vista: ela foi pra " + blk(seenPos.add(v.scale(20))));
        } else {
            think("procurando " + p.getScoreboardName() + " (" + (noiseOnly ? "ouvi" : "pista") + ")");
        }
    }

    /** Desistiu: perdeu de verdade. */
    public void giveUp() {
        Player s = subject();
        think("desisti de procurar" + (s != null ? " " + s.getScoreboardName() : "") + ": perdi");
        if (f.getTarget() == s && s != null) f.setTarget(null);
        forget("desisti");
    }

    private void forget(String why) {
        focus = null;
        searchSince = -1L;
        noiseOnly = false;
        belief.clear();
        if (!"desisti".equals(why)) think("calmo (" + why + ")");
    }

    /** De onde a procura partiu: onde viu por último (ou a última pista). */
    private Vec3 origin() {
        return now() - seenAt < 1200 ? seenPos : lastClue;
    }

    public boolean shouldGiveUp() {
        if (searchSince < 0) return false;
        long t = now() - searchSince;
        if (manhunt()) return false; // no manhunt a bússola sempre traz de volta
        return t > (noiseOnly ? GIVE_UP_NOISE : GIVE_UP);
    }

    private boolean manhunt() {
        return f.level() instanceof ServerLevel sl && com.froggydude.world.FroggyWorldData.get(sl).manhuntActive;
    }

    /**
     * Ouviu um barulho de alguém (Hearing). Perto e alto: sabe mais ou menos onde; longe: a região. Vira a cabeça pra lá.
     */
    public void hear(ServerPlayer p, Vec3 pos, double radius, String what) {
        if (!(f.level() instanceof ServerLevel sl)) return;
        long t = now();
        if (perceives(p) && p.getUUID().equals(focus)) return;
        if (focus != null && !p.getUUID().equals(focus) && t - lastHeard < 100) return; // já está atrás de outro barulho
        double d = f.position().distanceTo(pos);
        double err = 1.0D + d * 0.12D;
        if (focus == null || !p.getUUID().equals(focus)) {
            focus = p.getUUID();
            belief.clear();
            searchSince = t;
            noiseOnly = true;
        }
        lastHeard = t;
        lastClue = pos;
        belief.add(sl, jitter(pos, err), 0.8F, err);
        f.getLookControl().setLookAt(pos.x, pos.y + 1.0D, pos.z, 30.0F, 30.0F);
        think("ouvi " + what + " perto de " + blk(pos));
    }

    /** Uma pista de longe (só a região, com erro que cresce com a distância). */
    public void rumor(Player p, double minErr, String why) {
        if (!(f.level() instanceof ServerLevel sl) || perceives(p)) return;
        if (now() - lastRumor < 100 && p.getUUID().equals(focus)) return; // pista nova no máximo a cada 5 s
        lastRumor = now();
        double d = f.distanceTo(p);
        double err = Math.max(minErr, d * 0.25D);
        if (focus == null || !p.getUUID().equals(focus)) {
            focus = p.getUUID();
            belief.clear();
            searchSince = now();
            noiseOnly = false;
        }
        lastClue = jitter(p.position(), err * 0.6D);
        belief.add(sl, lastClue, 0.7F, err);
        think(why + " (pra " + blk(p.position()) + ", +-" + (int) err + " blocos)");
    }

    /** O faro de perto (FroggyTrackGoal): está aqui em volta, uns 2-3 blocos de erro. */
    public void smell(Player p, String how) {
        if (!(f.level() instanceof ServerLevel sl) || perceives(p)) return;
        if (focus == null || !p.getUUID().equals(focus)) {
            focus = p.getUUID();
            belief.clear();
            noiseOnly = false;
        }
        searchSince = now();
        lastClue = jitter(p.position(), 2.0D);
        belief.add(sl, lastClue, 0.8F, 3.0D);
        think(how + ": " + p.getScoreboardName() + " por aqui");
    }

    /** Machucou ele: sabe de onde veio (perto: quem; longe: a direção). */
    public void hurtBy(Player p) {
        if (!(f.level() instanceof ServerLevel sl)) return;
        double d = f.distanceTo(p);
        if (d <= 8.0D) {
            aware.put(p.getUUID(), 1.0F);
            saw(sl, p, now());
        } else {
            rumor(p, 3.0D, "me acertaram de longe");
            f.getLookControl().setLookAt(p.getX(), p.getEyeY(), p.getZ(), 30.0F, 30.0F);
        }
    }

    /** Pistas de tempos em tempos: o cheiro de sangue, a bússola do manhunt. */
    private void clues(ServerLevel sl, long t) {
        Player s = subject();
        if (s != null && perceives(s)) return;
        // sangue: quem está sem braço deixa cheiro forte (LM: "ele sente o cheiro de longe") - só a região
        double far = 64.0D * FroggyConfig.ai().sense;
        for (ServerPlayer p : sl.players()) {
            if (p.isCreative() || p.isSpectator() || !ArmLoss.isArmless(p) || f.distanceTo(p) > far) continue;
            if (focus == null || p.getUUID().equals(focus)) {
                rumor(p, 4.0D, "cheiro de sangue de " + p.getScoreboardName());
                break;
            }
        }
        if (manhunt() && t - lastCompass >= COMPASS_EVERY) {
            ServerPlayer near = null;
            for (ServerPlayer p : sl.players()) {
                if (p.isCreative() || p.isSpectator()) continue;
                if (near == null || p.distanceTo(f) < near.distanceTo(f)) near = p;
            }
            if (near != null) {
                lastCompass = t;
                compass(sl, near);
            }
        }
    }

    /** A bússola: só a direção (uma linha de pontos pra lá, nunca o lugar). */
    private void compass(ServerLevel sl, Player p) {
        Vec3 dir = p.position().subtract(f.position()).multiply(1, 0, 1);
        double d = dir.length();
        if (d < 1.0E-3D) return;
        dir = dir.scale(1.0D / d);
        if (focus == null || !p.getUUID().equals(focus)) {
            focus = p.getUUID();
            belief.clear();
            searchSince = now();
            noiseOnly = false;
        }
        double reach = Math.min(d + 16.0D, 64.0D);
        lastClue = f.position().add(dir.scale(Math.min(d, 32.0D)));
        for (double k = 12.0D; k <= reach; k += 10.0D) {
            belief.add(sl, f.position().add(dir.scale(k)), 0.25F, 3.0D + k * 0.08D);
        }
        think("olhei a bússola: " + p.getScoreboardName() + " pra " + compassWord(dir));
    }

    private Vec3 jitter(Vec3 pos, double err) {
        double a = f.getRandom().nextDouble() * Math.PI * 2.0D;
        double r = f.getRandom().nextDouble() * err;
        return pos.add(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    static String blk(Vec3 v) {
        return (int) Math.floor(v.x) + " " + (int) Math.floor(v.y) + " " + (int) Math.floor(v.z);
    }

    static String compassWord(Vec3 dir) {
        double ang = Math.toDegrees(Math.atan2(-dir.x, dir.z));
        String[] n = {"sul", "sudoeste", "oeste", "noroeste", "norte", "nordeste", "leste", "sudeste"};
        int i = (int) Math.round(((ang % 360) + 360) % 360 / 45.0D) % 8;
        return n[i];
    }

    /** Pro comando: o que ele está pensando agora. */
    public String describe() {
        Player s = subject();
        StringBuilder b = new StringBuilder(thought);
        if (s != null) {
            b.append(" | foco: ").append(s.getScoreboardName()).append(", certeza ").append(Math.round(awareness(s) * 100)).append('%');
            if (searchSince >= 0) {
                b.append(", procurando há ").append((now() - searchSince) / 20).append(" s, ").append(belief.size())
                        .append(" lugares possíveis (o mais provável ").append(Math.round(belief.peak() * 100)).append("%)");
            }
        }
        return b.toString();
    }
}
