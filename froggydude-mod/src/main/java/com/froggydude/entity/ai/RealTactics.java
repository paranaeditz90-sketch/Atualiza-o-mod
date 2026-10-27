package com.froggydude.entity.ai;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.player.PlayerSight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * IA Real (config "ia" = REAL): o jeito do FroggyDude dos vídeos por cima da
 * esperteza do Máx. Tirado de "FroggyDude VS Every Opponent", "10v1 Manhunt but
 * I have RABIES", "How many days can you survive against me?" e "Minecraft
 * Manhunt but I have RABIES" (ver PLANO.md, "IA Real"):
 *
 * - Sabe o que está na tua tela (PlayerSight, pelo cliente). Chegando sem ser
 *   visto, sorteia como chegar (Approach) pesando o que já deu certo contra ti: por
 *   trás, o "I see you!... you can't hide from me!" e o bote de lado que esmaga
 *   (Grox, caverna 1:53-1:59), de mansinho calado pelas costas ou direto. Se tu virar
 *   e ver ele antes, parte pra cima na hora.
 * - "Viu, vai" (v0.7.15, escolha do LM: "fica encarando o jogador, demorando agir"):
 *   no máximo uma vez por dia do jogo, no começo da caçada, encara 2 s de longe ("I
 *   can smell the fear in you! Time to eat!") e vai (How many days, 4:36-4:45). Sem
 *   a brincadeira de minutos (RealTease), sem esperar no breu, sem a janela.
 * - Quem foge em campo aberto: corre atrás e dá o bote colado, sem pulo de
 *   longe; na fase 2, só corre (nem o bote).
 * - Trancada em casa: porta ele abre ou arromba na hora (FroggyDoorGoal); sem porta,
 *   come a parede (FroggyCombatGoal).
 * - Lava: só quando não tem outro caminho, anda por ela sem queimar (10v1 6:13).
 * - O pulo altíssimo só quando não dá pra chegar de jeito nenhum (ela lá em
 *   cima, do outro lado da lava), com a conta de verdade da física do jogo pra
 *   cair do lado dela - e raro (um a cada 2 min no máximo).
 * - Fugir pra se curar e voltar com um plano: FroggyRetreatGoal.
 * - (v0.6) Espera ela onde ela sempre volta (FroggyStakeoutGoal).
 */
final class RealTactics {

    /** Sem ser visto há 3 s: dá pra chegar de mansinho e soltar o "I see you". */
    private static final int SEE_YOU_UNSEEN = 60;
    /** O clipe tem 7,3 s: "I see you!" (até 3,3 s), a risadinha, "you can't hide from me!" acaba aos 6,9 s - o bote. */
    private static final int SEE_YOU_POUNCE = 134;
    private static final int SEE_YOU_GIVE_UP = 260;
    /** Uma vez a cada 3-4 min (é especial). */
    private static final int SEE_YOU_COOLDOWN = 3600;
    private static final double SEE_YOU_NEAR = 6.0D;
    private static final double SEE_YOU_FAR = 22.0D;
    /** Chegando de mansinho de mais longe que isso, até a distância da fala. */
    private static final double SNEAK_FROM = 48.0D;
    /** Encarar: 2 s parado (a fala "I can smell the fear in you! Time to eat!" segue com ele já indo). */
    private static final int STARE_TICKS = 40;
    /** Encarar é raro: no máximo uma vez por dia do jogo. */
    private static final long STARE_EVERY = 24000L;
    private static final int ARC_COOLDOWN = 2400;
    /** Ela lá em cima (árvore, pilar): se o pulo errar, tenta de novo bem antes. */
    private static final int ARC_COOLDOWN_HIGH = 300;

    private final FroggydudeEntity froggy;
    private final FroggyCombatGoal combat;

    @Nullable
    private UUID who;
    private boolean seen;
    private int unseen;
    private int engagedAt;
    private int seeYouAt = -1;
    private int seeYouReadyAt;
    private int stare;
    /** Dia do jogo da última encarada (uma por dia). */
    private long staredDay = Long.MIN_VALUE;
    /** Já sorteou se encara nessa caçada (o engagedAt dela). */
    private int stareRolledFor = -1;
    private int lavaCheckAt;
    private int arcReadyAt;
    private int arcCheckAt;

    RealTactics(FroggydudeEntity froggy, FroggyCombatGoal combat) {
        this.froggy = froggy;
        this.combat = combat;
    }

    boolean active() {
        return FroggyConfig.ai().real;
    }

    /** O combate parou (sem alvo): larga o que estava fazendo. */
    void reset() {
        endSequences();
        who = null;
    }

    // ---------------------------------------------------------------- perseguição

    /** Devolve true se cuidou do movimento nesse tick. */
    boolean tick(LivingEntity target, double dist, boolean elevated) {
        if (!(target instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
            endSequences();
            return false;
        }
        if (!player.getUUID().equals(who)) {
            who = player.getUUID();
            // o cliente conta há quanto tempo ela não vê ele (de antes dele mirar nela)
            unseen = Math.max(0, PlayerSight.unseenFor(player, froggy));
            engagedAt = froggy.tickCount;
            endSequences();
        }
        boolean was = seen;
        seen = PlayerSight.sees(player, froggy);
        unseen = seen ? 0 : unseen + 1;
        if (seen != was || froggy.tickCount % 20 == 0) {
            debug(String.format("%s me vê=%s (sem ver há %d ticks, %s) dist=%.1f escuro=%s distraída=%s", player.getScoreboardName(),
                    seen, unseen, PlayerSight.reporting(player) ? "pela tela dela" : "estimado", dist,
                    PlayerSight.dark(froggy), PlayerSight.distracted(player)));
        }
        // fase 2: só corre atrás, gritando (Grox 5:28)
        if (froggy.isFrenzyActive() || froggy.isMocking(target)) {
            endSequences();
            return false;
        }
        judgeEarly(player);
        // trancada/inalcançável: quem cuida é o cerco (stuck); a chegada de mansinho brigava com ele
        if (combat.stuckTicks() >= 20 && approach != Approach.ANNOUNCE) approach = null;
        if (approach == null && stare(player, dist, elevated)) return true;
        if (approach == null && stare == 0 && combat.stuckTicks() < 20) approach = decide(player, dist, elevated);
        return approach != null && runApproach(player, dist);
    }

    private void endSequences() {
        seeYouAt = -1;
        approach = null;
        stare = 0;
    }

    /**
     * O que ele pode sortear agora: na fase 2 só a mordida (corre e morde, nada de
     * pular); o pulo altíssimo comum nunca (só o calculado, sem outro jeito); quem
     * está fugindo, só a mordida (o bote colado vem do tail, de perto).
     */
    boolean allows(FroggyState s, double approach) {
        if (froggy.isFrenzyActive()) return s == FroggyState.BITE;
        if (s == FroggyState.SKY_DROP) return false;
        if (approach < -0.08D) return s == FroggyState.BITE;
        return true;
    }

    // ---------------------------------------------------------------- chegar sem ser visto

    /**
     * Como chegar em quem ainda não viu ele. É sorteado a cada chance, pesando o
     * lugar (no breu, esperar vale mais) e o que já deu certo contra ESSA pessoa
     * (amostragem de Thompson, igual aos ataques): quem sempre percebe ele chegando
     * calado começa a ouvir o "I see you", quem nunca olha pra trás leva o bote
     * calado. Imprevisível, mas aprende.
     */
    enum Approach {
        /** "I see you!... you can't hide from me!" e o bote de lado que esmaga (Grox, caverna 1:53). */
        ANNOUNCE,
        /** De mansinho pelo lado que ela não olha, calado, e o bote. */
        SILENT,
        /** Vai direto. */
        RUSH
    }

    /** A tática rodando agora (null = nenhuma). */
    @Nullable
    private Approach approach;
    private int approachAt;
    /** A tática esperando o resultado (o primeiro golpe depois dela). */
    @Nullable
    private Approach judging;
    private int judgingSince;
    private int decideReadyAt;

    /** Sorteia a tática se é hora (ela não viu ele faz 3 s e está ao alcance). O "I see you", só chegando por trás. */
    @Nullable
    private Approach decide(ServerPlayer p, double dist, boolean elevated) {
        if (froggy.tickCount < decideReadyAt || unseen < SEE_YOU_UNSEEN || elevated || froggy.isClimbing()) return null;
        if (dist < 4.5D || dist > SNEAK_FROM || Math.abs(p.getY() - froggy.getY()) > 4.0D) return null;
        double toward = combat.approachSpeed(p);
        if (toward < -0.15D) return null; // fugindo: ela já sabe que ele está ali
        boolean dark = PlayerSight.dark(froggy);
        float[] w = new float[Approach.values().length];
        w[Approach.ANNOUNCE.ordinal()] = froggy.tickCount >= seeYouReadyAt && dist >= SEE_YOU_NEAR && behind(p) ? 1.0F : 0F;
        w[Approach.SILENT.ordinal()] = 1.0F;
        w[Approach.RUSH.ordinal()] = dist < 10.0D ? 0.8F : 0.4F;
        float total = 0F;
        for (Approach a : Approach.values()) {
            if (w[a.ordinal()] <= 0F) continue;
            w[a.ordinal()] *= 0.25F + 1.5F * froggy.tacticChance(p, key(a));
            total += w[a.ordinal()];
        }
        float roll = froggy.getRandom().nextFloat() * total;
        Approach pick = Approach.RUSH;
        for (Approach a : Approach.values()) {
            roll -= w[a.ordinal()];
            if (w[a.ordinal()] > 0F && roll <= 0F) {
                pick = a;
                break;
            }
        }
        judging = pick;
        judgingSince = froggy.tickCount;
        approachAt = froggy.tickCount;
        decideReadyAt = froggy.tickCount + 200;
        froggy.brainLog(String.format("%s não me viu (%.0f blocos%s): %s", p.getScoreboardName(), dist,
                dark ? ", no escuro" : "", switch (pick) {
                    case ANNOUNCE -> "chega e solta o \"I see you\"";
                    case SILENT -> "chega calado pelas costas";
                    case RUSH -> "vai direto";
                }));
        return pick == Approach.RUSH ? null : pick;
    }

    /** Ele está nas costas dela (fora do que ela vê, com folga). */
    private boolean behind(ServerPlayer p) {
        Vec3 look = p.getViewVector(1.0F).multiply(1, 0, 1);
        Vec3 to = froggy.position().subtract(p.position()).multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4D || to.lengthSqr() < 1.0E-4D) return false;
        return look.normalize().dot(to.normalize()) < -0.3D;
    }

    private static String key(Approach a) {
        return "chegada_" + a.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Um golpe terminou (acertou ou não): era o resultado da tática. */
    void onAttackResolved(LivingEntity target, boolean hit) {
        if (judging == null) return;
        froggy.recordTactic(target, key(judging), hit);
        froggy.brainLog("tática " + judging.name().toLowerCase(java.util.Locale.ROOT) + (hit ? " deu certo" : " falhou")
                + " contra " + target.getName().getString());
        judging = null;
    }

    /** Falhou antes do golpe: ela viu ele chegando calado, bateu nele primeiro, ou demorou demais. */
    private void judgeEarly(ServerPlayer p) {
        if (judging == null) return;
        boolean hurtFirst = froggy.getLastHurtByMob() == p && froggy.getLastHurtByMobTimestamp() > judgingSince;
        boolean spotted = seen && approach != null && approach != Approach.ANNOUNCE;
        if (hurtFirst || spotted || froggy.tickCount - judgingSince > 600) {
            froggy.recordTactic(p, key(judging), false);
            froggy.brainLog("tática " + judging.name().toLowerCase(java.util.Locale.ROOT) + " falhou contra "
                    + p.getScoreboardName() + (hurtFirst ? " (bateu nele antes)" : spotted ? " (ela viu ele chegando)" : " (demorou)"));
            judging = null;
        }
    }

    private boolean runApproach(ServerPlayer p, double dist) {
        if (approach == Approach.ANNOUNCE) return announce(p, dist);
        return silent(p, dist);
    }

    /** O bote no fim de uma tática: a partir daqui é o resultado do golpe que conta. */
    private boolean pounce(ServerPlayer p, double dist, FroggyState finish) {
        froggy.forceFinish(finish, 120);
        if (!combat.realPounce(p, dist)) return false;
        approach = null;
        return true;
    }

    /** De mansinho pelo lado cego (de longe a uns 8 blocos, de perto a 5), calado; perto e livre, o bote. */
    private boolean silent(ServerPlayer p, double dist) {
        if (seen) {
            froggy.brainLog(p.getScoreboardName() + " me viu chegando: pra cima");
            approach = null;
            return false;
        }
        if (froggy.tickCount - approachAt > 400) {
            approach = null;
            return false;
        }
        if (dist <= 7.5D && pounce(p, dist, froggy.getRandom().nextBoolean() ? FroggyState.SMASH : FroggyState.PIN_HOLD)) {
            return true;
        }
        if (froggy.tickCount % 8 == 0) {
            moveTo(blindSpot(p, dist > 12.0D ? 8.0D : 4.0D), FroggyCombatGoal.huntSpeed() * (dist > 12.0D ? 0.55D : 0.45D));
        }
        froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
        froggy.mute(10);
        return true;
    }

    /** O "I see you": chega de mansinho até a distância da fala, fala, rodeia e no fim dela o bote. */
    private boolean announce(ServerPlayer p, double dist) {
        if (seeYouAt < 0) {
            if (seen) {
                approach = null;
                return false;
            }
            if (dist > SEE_YOU_FAR || !froggy.onGround() || !froggy.hasLineOfSight(p)) {
                if (froggy.tickCount - approachAt > 600) {
                    approach = null;
                    return false;
                }
                if (froggy.tickCount % 10 == 0) moveTo(blindSpot(p, 8.0D), FroggyCombatGoal.huntSpeed() * 0.55D);
                froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
                return true;
            }
            if (!froggy.speak(VoiceSituation.SEE_YOU, true)) {
                approach = Approach.SILENT; // calado à força (gritando, mudo): vai sem a fala
                return true;
            }
            seeYouAt = froggy.tickCount;
            seeYouReadyAt = froggy.tickCount + SEE_YOU_COOLDOWN + froggy.getRandom().nextInt(1200);
            froggy.brainLog("perto de " + p.getScoreboardName() + " sem ser visto: \"I see you\"");
        }
        int t = froggy.tickCount - seeYouAt;
        if (t > SEE_YOU_GIVE_UP) {
            seeYouAt = -1;
            approach = null;
            return false;
        }
        boolean spotted = seen && t > 10;
        if (t >= SEE_YOU_POUNCE || spotted) {
            if (spotted && t < SEE_YOU_POUNCE) froggy.brainLog(p.getScoreboardName() + " virou e me viu: pra cima");
            // a caverna do Grox: pula de lado e desce os punhos
            if (pounce(p, dist, FroggyState.SMASH)) {
                seeYouAt = -1;
                return true;
            }
            // longe ainda: corre e o próximo bote esmaga (se não der em 6 s, desiste)
            froggy.forceFinish(FroggyState.SMASH, 120);
            if (t >= SEE_YOU_POUNCE + 120) {
                seeYouAt = -1;
                approach = null;
            }
            return false;
        }
        if (froggy.tickCount % 8 == 0) moveTo(blindSpot(p, 5.0D), FroggyCombatGoal.huntSpeed() * 0.5D);
        froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
        return true;
    }

    /** Atrás dela e um pouco pro lado em que ele já está (sem cruzar a frente dela). */
    Vec3 blindSpot(ServerPlayer p, double r) {
        Vec3 look = p.getViewVector(1.0F).multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        if (side.dot(froggy.position().subtract(p.position())) < 0) side = side.scale(-1.0D);
        Vec3 dir = look.scale(-0.75D).add(side.scale(0.66D)).normalize();
        return p.position().add(dir.scale(r));
    }

    void moveTo(Vec3 at, double speed) {
        froggy.getNavigation().moveTo(at.x, at.y, at.z, speed);
    }

    // ---------------------------------------------------------------- encarar

    private boolean stare(ServerPlayer p, double dist, boolean elevated) {
        double approach = combat.approachSpeed(p);
        if (stare > 0) {
            stare--;
            if (stare == 0 || approach < -0.12D || approach > 0.08D || dist < 6.0D
                    || froggy.getLastHurtByMob() == p && froggy.tickCount - froggy.getLastHurtByMobTimestamp() < 20) {
                stare = 0;
                return false;
            }
            froggy.getNavigation().stop();
            froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
            froggy.faceTarget(p);
            return true;
        }
        // só no começo da caçada, com ela olhando pra ele de longe, e no máximo uma vez por dia
        long day = froggy.level().getDayTime() / STARE_EVERY;
        if (froggy.tickCount - engagedAt > 40 || day == staredDay || stareRolledFor == engagedAt) return false;
        if (!seen || elevated || dist < 10.0D || dist > 32.0D || !froggy.onGround() || approach < -0.12D) return false;
        stareRolledFor = engagedAt;
        if (froggy.getRandom().nextFloat() > 0.45F) return false;
        staredDay = day;
        stare = STARE_TICKS;
        froggy.speak(VoiceSituation.STANDOFF, true);
        froggy.brainLog("encarando " + p.getScoreboardName() + " de longe antes de ir");
        return true;
    }

    // ---------------------------------------------------------------- sem caminho

    /**
     * Ele não chega nela andando. Do outro lado da lava: atravessa. Lá em cima sem
     * jeito de subir: o pulo altíssimo calculado. Devolve true se cuidou do tick (o
     * resto - porta, comer a parede - fica pro combate).
     */
    boolean stuck(LivingEntity target, double dist, int stuckTicks) {
        if (!(target instanceof ServerPlayer p) || froggy.isFrenzyActive()) return false;
        if (froggy.isLavaWalking()) {
            // atravessando: o caminho com lava é o que a navegação já usa; acaba quando chegar
            if (!froggy.isInLava() && dist <= 6.0D) froggy.setLavaWalking(false);
            return false;
        }
        boolean partial = froggy.tickCount % 10 == 0 && !reachable();
        if ((stuckTicks >= 10 || partial) && froggy.tickCount >= lavaCheckAt && dist <= 40.0D && tryLava(p)) return true;
        // ela lá em cima de uma parede: sobe na hora, de quatro (v0.7.15, LM: "ele demora subir
        // uma simples parede" - ele ia procurar de onde pular). O pulo calculado fica pra quando
        // a parede não deixa (teto, beirada pra fora: a escalada trava e espera 5 s)
        if (high(p) && froggy.canClimbTo(p)) {
            vantage = null;
            return false;
        }
        // ela lá em cima: o pulo vem primeiro e cedo (LM, 23/10: "quebrou o bloco que eu tava, mas não
        // tentou outro jeito melhor, ex: pular alto pra me pegar"). Antes a conta só rodava com 2 s
        // preso e a língua no bloco embaixo dela (3 s) quase sempre chegava antes
        if (stuckTicks < (high(p) ? 12 : 40) || froggy.tickCount < arcReadyAt) {
            vantage = null;
            return false;
        }
        // sem caminho: o pulo calculado daqui; se a conta não passa daqui (ela lá em cima, a
        // borda no meio), procura de onde passa - sem largar o alvo - e pula de lá
        if (froggy.tickCount >= arcCheckAt && tryArc(p)) {
            vantage = null;
            return true;
        }
        if (high(p)) return seekVantage(p);
        vantage = null;
        return false;
    }

    private boolean high(LivingEntity target) {
        return target.getY() - froggy.getY() >= 2.5D;
    }

    /**
     * Ela lá em cima e o pulo ainda é uma opção (sem recarga, não desistiu de achar de onde
     * pular): a língua no bloco embaixo dela e a torre de blocos esperam o pulo.
     */
    boolean jumpFirst(LivingEntity target) {
        return target instanceof ServerPlayer && high(target) && froggy.tickCount >= arcReadyAt && !froggy.isFrenzyActive();
    }

    /** Indo pro ponto de onde o pulo calculado chega nela lá em cima; o combate não larga o alvo enquanto isso. */
    @Nullable
    private Vec3 vantage;
    private int vantageUntil;
    /** Quantos pontos de pulo ele já tentou sem conseguir pular: depois de 3, a língua/torre. */
    private int vantageTries;

    boolean planningReach() {
        return vantage != null && froggy.tickCount <= vantageUntil;
    }

    private boolean seekVantage(ServerPlayer p) {
        if (vantage == null || froggy.tickCount > vantageUntil) {
            vantage = ++vantageTries > 3 ? null : findVantage(p);
            vantageUntil = froggy.tickCount + 240;
            if (vantage == null) {
                // não tem de onde pular (ou já tentou de 3 lugares): tenta de novo depois; até lá,
                // a língua no bloco embaixo dela e a torre de blocos
                arcReadyAt = froggy.tickCount + 200;
                vantageTries = 0;
                froggy.brainLog("sem de onde pular até " + p.getScoreboardName() + ": outro jeito");
                return false;
            }
            froggy.brainLog("procurando de onde pular até " + p.getScoreboardName() + " lá em cima");
        }
        if (froggy.tickCount % 10 == 0) froggy.getNavigation().moveTo(vantage.x, vantage.y, vantage.z, FroggyCombatGoal.huntSpeed() * 0.8D);
        froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
        return true;
    }

    /**
     * Um lugar no chão, a 5-8 blocos em volta dela, de onde a conta do pulo passa limpa
     * até o lado dela e que ele alcança andando. Mais longe que isso o pulo não chega: no
     * ar o jogo freia 9% por tick (e 45% no tick da saída), então nem com 1,8 b/t de
     * impulso ele passa de uns 11 blocos.
     */
    @Nullable
    private Vec3 findVantage(ServerPlayer p) {
        Level level = froggy.level();
        Vec3 land = landingBeside(p);
        if (land == null) return null;
        List<Vec3> spots = new ArrayList<>();
        for (double r : new double[]{5.5D, 7.0D, 8.5D}) {
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r;
                for (int y = (int) Math.floor(froggy.getY()) + 4; y >= (int) Math.floor(froggy.getY()) - 6; y--) {
                    BlockPos feet = BlockPos.containing(x, y, z);
                    if (free(level, feet) && free(level, feet.above()) && !free(level, feet.below())
                            && level.getFluidState(feet.below()).isEmpty()) {
                        Vec3 at = Vec3.atBottomCenterOf(feet);
                        if (solveArc(froggy, at, land) != null) spots.add(at);
                        break;
                    }
                }
            }
        }
        spots.sort(Comparator.comparingDouble(s -> s.distanceToSqr(froggy.position())));
        int tried = 0;
        for (Vec3 s : spots) {
            if (tried++ >= 6) break;
            Path path = froggy.getNavigation().createPath(BlockPos.containing(s), 0);
            if (path != null && path.canReach()) return s;
        }
        return null;
    }

    boolean pathTo(ServerPlayer p) {
        Path path = froggy.getNavigation().createPath(p, 0);
        return path != null && path.canReach();
    }

    private boolean reachable() {
        Path path = froggy.getNavigation().getPath();
        return path != null && path.canReach();
    }

    // ---------------------------------------------------------------- lava

    private boolean tryLava(ServerPlayer p) {
        lavaCheckAt = froggy.tickCount + 40;
        if (!lavaBetween(p)) return false;
        froggy.setLavaWalking(true);
        Path path = froggy.getNavigation().createPath(p, 0);
        if (path == null || !path.canReach() || !throughLava(path)) {
            froggy.setLavaWalking(false);
            return false;
        }
        froggy.getNavigation().moveTo(path, FroggyCombatGoal.huntSpeed());
        froggy.brainLog("sem outro caminho até " + p.getScoreboardName() + ": pela lava");
        froggy.ribbit(2.0F);
        return true;
    }

    /** Tem lava entre os dois (no nível deles)? */
    private boolean lavaBetween(ServerPlayer p) {
        Level level = froggy.level();
        AABB box = froggy.getBoundingBox().minmax(p.getBoundingBox()).inflate(3.0D, 2.0D, 3.0D);
        if (box.getXsize() * box.getZsize() > 48 * 48) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (level.getFluidState(pos).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    private boolean throughLava(Path path) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node n = path.getNode(i);
            if (n.type == BlockPathTypes.LAVA || froggy.level().getFluidState(n.asBlockPos()).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- o pulo altíssimo calculado

    private boolean tryArc(ServerPlayer p) {
        arcCheckAt = froggy.tickCount + 40;
        if (!p.onGround() || p.isInLava() || p.isInWater() || !froggy.onGround() || froggy.isClimbing()
                || froggy.getAttackCooldown() > 0) {
            debug(String.format("arco não: ela no chão=%s, ele no chão=%s, subindo=%s, recarga=%d", p.onGround(),
                    froggy.onGround(), froggy.isClimbing(), froggy.getAttackCooldown()));
            // passageiro (no ar, pendurado no tronco): olha de novo logo
            arcCheckAt = froggy.tickCount + 5;
            return false;
        }
        // o caminho de agora pode ser o do ponto de onde ele vê ela: pergunta o caminho até ELA
        // (não precisa ver ela: de 5 a 8 blocos, lá embaixo, a borda da plataforma tapa a vista;
        // a IA Real sabe onde ela está e a conta do pulo é que diz se dá)
        if (pathTo(p)) {
            debug("arco não: tem caminho");
            return false;
        }
        Vec3 land = landingBeside(p);
        if (land == null) {
            debug("arco não: sem lugar pra cair do lado dela");
            return false;
        }
        // só pra um obstáculo de verdade (ela lá em cima, ou um vão largo); pulinho, não
        double h = Math.sqrt(sq(land.x - froggy.getX()) + sq(land.z - froggy.getZ()));
        if (land.y - froggy.getY() < 2.5D && h < 5.0D) return false;
        Vec3 v = solveArc(froggy, froggy.position(), land);
        if (v == null) {
            debug(String.format("arco não: nenhuma conta passa (%.1f blocos, %.1f de altura)", h, land.y - froggy.getY()));
            return false;
        }
        arcReadyAt = froggy.tickCount + (high(p) ? ARC_COOLDOWN_HIGH : ARC_COOLDOWN);
        vantageTries = 0;
        froggy.brainLog(String.format("sem jeito de chegar em %s: pulo altíssimo calculado (%.1f blocos, %.1f de altura)",
                p.getScoreboardName(), Math.sqrt(sq(land.x - froggy.getX()) + sq(land.z - froggy.getZ())), land.y - froggy.getY()));
        combat.beginArc(p, land);
        return true;
    }

    /**
     * Onde cair: do lado dela (na distância de esmagar), no chão firme dela, de preferência do
     * lado dele. Na copa de uma árvore ou num pilar de 1 bloco não tem "do lado": cai em cima dela.
     */
    @Nullable
    Vec3 landingBeside(LivingEntity target) {
        Vec3 dir = froggy.position().subtract(target.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : dir.normalize();
        for (double r : new double[]{1.5D, 1.0D, 2.2D}) {
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i % 2 == 0 ? i * 22.5D : -(i + 1) * 22.5D);
                Vec3 d = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0, dir.x * Math.sin(a) + dir.z * Math.cos(a));
                Vec3 at = target.position().add(d.scale(r));
                if (canLand(target, at)) return new Vec3(at.x, target.getY(), at.z);
            }
        }
        return canLand(target, target.position()) ? target.position() : null;
    }

    private boolean canLand(LivingEntity target, Vec3 at) {
        Level level = froggy.level();
        BlockPos feet = BlockPos.containing(at.x, target.getY() + 0.01D, at.z);
        if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) return false;
        // nunca dentro de lava, água ou em cima de magma
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()
                || level.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) return false;
        AABB box = froggy.getBoundingBox().move(at.subtract(froggy.position()).add(0, target.getY() - at.y, 0));
        return level.noCollision(froggy, box);
    }

    /**
     * A velocidade de saída pra cair em {@code land}, pela física do jogo (y += vy;
     * vy = (vy - 0,08) x 0,98; no ar x *= 0,91, no 1º tick 0,546): o menor impulso
     * que passa 1 bloco acima do ponto de pouso, sem bater em nada no caminho (teto
     * do Nether, parede, borda da plataforma). Null se não tem arco que sirva.
     */
    @Nullable
    static Vec3 solveArc(FroggydudeEntity froggy, Vec3 from, Vec3 land) {
        double dx = land.x - from.x, dz = land.z - from.z, dy = land.y - from.y;
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h < 1.0D || h > 26.0D || dy > 22.0D || dy < -8.0D) return null;
        for (double vy0 = 0.6D; vy0 <= 2.8D; vy0 += 0.05D) {
            int n = flightTicks(vy0, dy);
            if (n < 0) continue;
            double vh = h / FroggyCombatGoal.horizontalFactor(n);
            if (vh > 1.8D) continue;
            Vec3 v = new Vec3(dx / h * vh, vy0, dz / h * vh);
            if (clearArc(froggy, from, v, n)) return v;
        }
        return null;
    }

    /** Ticks até cair na altura dy (descendo), passando ao menos 1 bloco acima; -1 se não chega lá. */
    static int flightTicks(double vy0, double dy) {
        double y = 0.0D, vy = vy0, apex = 0.0D;
        for (int n = 1; n <= 160; n++) {
            y += vy;
            vy = (vy - 0.08D) * 0.98D;
            apex = Math.max(apex, y);
            if (vy < 0.0D && y <= dy) return apex >= dy + 1.0D ? n : -1;
        }
        return -1;
    }

    /** O corpo dele, ponto a ponto do arco (com sub-passos), não encosta em bloco nenhum. */
    static boolean clearArc(FroggydudeEntity froggy, Vec3 from, Vec3 v0, int n) {
        Level level = froggy.level();
        AABB base = froggy.getBoundingBox().move(from.subtract(froggy.position())).deflate(0.05D);
        double x = 0, y = 0, z = 0, vx = v0.x, vy = v0.y, vz = v0.z;
        for (int k = 0; k < n - 1; k++) {
            for (int s = 1; s <= 4; s++) {
                AABB b = base.move(x + vx * s / 4.0D, y + vy * s / 4.0D, z + vz * s / 4.0D);
                if (k == 0 && s == 1) continue; // saindo do chão
                if (!level.noCollision(froggy, b)) return false;
            }
            x += vx;
            y += vy;
            z += vz;
            double f = k == 0 ? 0.546D : 0.91D;
            vx *= f;
            vz *= f;
            vy = (vy - 0.08D) * 0.98D;
        }
        return true;
    }

    /**
     * No ar, caindo: corrige o rumo pro ponto de pouso (que segue a vítima, se ela
     * andar) com a mesma conta - a velocidade que falta pra chegar no tempo que
     * resta de queda - misturada aos poucos (sem virar de uma vez).
     */
    void steerArc(LivingEntity target, @Nullable Vec3 land) {
        Vec3 v = froggy.getDeltaMovement();
        froggy.setZza(0.0F);
        froggy.setXxa(0.0F);
        if (v.y > 0.0D || land == null) return;
        Vec3 aim = landingBeside(target);
        if (aim == null) aim = land;
        double dy = aim.y - froggy.getY();
        double y = 0.0D, vy = v.y;
        int m = 0;
        while (m < 80 && y > dy) {
            y += vy;
            vy = (vy - 0.08D) * 0.98D;
            m++;
        }
        if (m == 0) return;
        double rx = aim.x - froggy.getX(), rz = aim.z - froggy.getZ();
        double factor = (1.0D - Math.pow(0.91D, m)) / 0.09D;
        double nx = rx / factor, nz = rz / factor;
        froggy.setDeltaMovement(v.x + (nx - v.x) * 0.3D, v.y, v.z + (nz - v.z) * 0.3D);
        froggy.faceTarget(target);
    }

    private static void debug(String msg) {
        if (Boolean.getBoolean("froggydude.debug")) System.out.println("[FROGGYDEBUG] real: " + msg);
    }

    private static double sq(double d) {
        return d * d;
    }

    static boolean free(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }
}
