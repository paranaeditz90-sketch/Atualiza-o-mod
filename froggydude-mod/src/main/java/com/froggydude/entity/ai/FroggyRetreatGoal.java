package com.froggydude.entity.ai;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.player.PlayerSight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * IA Real: apanhando demais, ele foge pra se curar e volta com um plano (vs
 * Every Opponent 16:56-17:36: "Oh my god, I gotta get out of there now!",
 * "I'm very low on health, I need to get food and come up with a plan to kill
 * this thing!", "Back to full health!", "Time for the plan! Step one, I'm going
 * to smash him..."; 10v1 2:50: "Get away from me!", some no mato e volta).
 *
 * Com a vida baixa (a mesma hora em que ele vai comer pra se curar,
 * FroggydudeEntity#wantsToHeal) e quem bateu nele por perto: corre pra longe e
 * pra onde ela não vê - mato, copa de árvore, água, breu - até sumir da vista
 * dela. Aí larga o alvo e o FroggyFeedGoal come o que achar. Curado (ou depois
 * de 1 minuto), o faro leva ele de volta e o encontro começa com o plano
 * (ver FroggydudeEntity#setTarget), que já diz como o bote termina.
 *
 * Vem antes da fase 2: se ela ainda não saiu, ele se transforma em vez de fugir.
 */
public class FroggyRetreatGoal extends Goal {

    /** Longe assim e sem ser visto há 2 s: escapou. */
    private static final double SAFE_DIST = 24.0D;
    private static final int SAFE_UNSEEN = 40;
    private static final int GIVE_UP = 400;
    /** Perto dela (menos que SAFE_DIST) e 3 s sem ganhar 2 blocos de distância: encurralado. */
    private static final int CORNER_TICKS = 60;
    /** Encurralado, briga: só tenta fugir de novo depois de 20 s (40 e 60 s se repetir). */
    private static final int CORNERED_COOLDOWN = 400;
    /** Encurralado pela mesma pessoa em menos de 2 min: é o mesmo aperto. */
    private static final int SAME_CORNER_TICKS = 2400;
    /** O plano B da fuga: rumos em relação ao "pra longe dela" (graus), do melhor pro pior. */
    private static final int[] FAN = {0, 45, -45, 90, -90, 135, -135};

    /** Pra onde correr e o caminho até lá, já calculado. */
    private record Escape(Vec3 spot, Path path) {
    }

    /** Um ponto candidato e a nota dele. */
    private record Scored(Vec3 pos, double score) {
    }

    private final FroggydudeEntity froggy;
    @Nullable
    private ServerPlayer threat;
    private int ticks;
    private int unseen;
    private int readyAt;
    @Nullable
    private Vec3 spot;
    /** A distância dela no começo da janela de 3 s que mede se ele está abrindo distância. */
    private double windowDist;
    private int windowAt;
    /** Quantas vezes seguidas procurou pra onde correr e não achou, desde quando, e quando procura de novo. */
    private int tries;
    private int noSpotSince;
    private int retryAt;
    /** Sem saída: larga a fuga e briga (ver tick). */
    private boolean cornered;
    /** Quantas fugas seguidas acabaram encurralado, por quem e quando foi a última. */
    private int corneredStreak;
    @Nullable
    private UUID corneredBy;
    private int corneredAt;

    public FroggyRetreatGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Hora de fugir em vez de comer ali do lado de quem está batendo nele? */
    public static boolean mustFlee(FroggydudeEntity froggy) {
        return threat(froggy) != null && ready(froggy);
    }

    private static boolean ready(FroggydudeEntity froggy) {
        if (!FroggyConfig.ai().real || froggy.isFrenzyActive() || froggy.shouldStartPhase2()) return false;
        FroggyState st = froggy.getFroggyState();
        if (st.isCombatAction() || st.isOnVictim() || st == FroggyState.CONTORTING || st == FroggyState.ROAR
                || st == FroggyState.FINISHER || froggy.isHolding()) return false;
        return froggy.wantsToHeal();
    }

    /** Quem é o perigo: o alvo, ou quem bateu nele há pouco, a até 20 blocos. */
    @Nullable
    private static ServerPlayer threat(FroggydudeEntity froggy) {
        if (froggy.getTarget() instanceof ServerPlayer p && valid(froggy, p)) return p;
        if (froggy.getLastHurtByMob() instanceof ServerPlayer p && valid(froggy, p)
                && froggy.tickCount - froggy.getLastHurtByMobTimestamp() < 200) return p;
        return null;
    }

    private static boolean valid(FroggydudeEntity froggy, Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && froggy.distanceTo(p) < 20.0F;
    }

    @Override
    public boolean canUse() {
        if (froggy.tickCount < readyAt || !ready(froggy)) return false;
        threat = threat(froggy);
        return threat != null;
    }

    @Override
    public boolean canContinueToUse() {
        return threat != null && threat.isAlive() && ticks < GIVE_UP && !froggy.isFrenzyActive() && !cornered
                && !(froggy.distanceTo(threat) >= SAFE_DIST && unseen >= SAFE_UNSEEN);
    }

    @Override
    public void start() {
        ticks = 0;
        unseen = 0;
        spot = null;
        tries = 0;
        noSpotSince = 0;
        retryAt = 0;
        cornered = false;
        windowDist = froggy.distanceTo(threat);
        windowAt = 0;
        // (v0.7.24) larga o caminho do combate: sem esconderijo, ele seguia o caminho velho, NA DIREÇÃO dela
        froggy.getNavigation().stop();
        froggy.setFroggyState(FroggyState.CHASE); // de quatro, correndo
        froggy.speak(VoiceSituation.RETREAT, true); // "oh my god, I gotta get out of there now!"
        froggy.brainLog("vida baixa apanhando de " + threat.getScoreboardName() + ": foge pra se curar");
    }

    @Override
    public void tick() {
        ticks++;
        ServerPlayer p = threat;
        if (p == null) return;
        unseen = PlayerSight.sees(p, froggy) ? 0 : unseen + 1;
        double d = froggy.distanceTo(p);
        // longe e fora da vista: escapou (o canContinueToUse encerra a fuga)
        if (d >= SAFE_DIST && unseen >= SAFE_UNSEEN) return;
        boolean arrived = spot != null && (froggy.getNavigation().isDone() || froggy.position().distanceToSqr(spot) < 4.0D);
        // ela chegou perto e ficou entre ele e o ponto: escolhe outro (com o ponto ainda pra longe dela, segue)
        boolean cutOff = spot != null && ticks % 30 == 0 && d < 10.0D && spot.distanceTo(p.position()) < d + 2.0D;
        // sem ponto: procura de novo em 0,5 s (antes era todo tick, e ele ficava parado olhando pra ela)
        boolean retry = spot == null && ticks >= retryAt;
        // (no ar o jogo não calcula caminho: espera pôr o pé no chão)
        boolean canPath = froggy.onGround() || froggy.isInWater() || froggy.isInLava();
        if ((arrived || cutOff || retry) && canPath) {
            // 1º os esconderijos sorteados; não achou caminho pra nenhum, 2 ticks depois o plano B: até onde dá
            // pra ir pra longe dela (o que acha o túnel da caverna)
            Escape e = tries % 2 == 0 ? hideout(p) : anyWayOut(p);
            if (e != null) {
                spot = e.spot();
                tries = 0;
                froggy.getNavigation().moveTo(e.path(), FroggyCombatGoal.huntSpeed());
            } else {
                if (spot != null || tries == 0) noSpotSince = ticks;
                spot = null;
                tries++;
                // (depois do plano B falhar: escondido num beco, confere de novo de 1 em 1 s)
                retryAt = ticks + (tries % 2 == 1 ? 2 : 20);
                froggy.getNavigation().stop();
            }
        }
        // perto dela, de 3 em 3 s: abriu pelo menos 2 blocos de distância? (dando voltas, enroscado, ela acompanhando)
        boolean stalled = false;
        if (ticks - windowAt >= CORNER_TICKS) {
            stalled = spot != null && d < SAFE_DIST && d < windowDist + 2.0D;
            windowDist = d;
            windowAt = ticks;
        }
        // (v0.7.24) encurralado: ela está vendo ele (viu há menos de 0,5 s) e nem os esconderijos nem o plano B
        // acharam por onde ir (sala fechada, buraco, beco, beira do penhasco) ou faz 3 s que ele está sem rumo;
        // ou ele corre, perto dela, e não consegue abrir distância. Antes ele ficava até 20 s raspando a parede
        // ou parado olhando pra ela, sem fugir nem atacar (LM: "do nada ele para e não ataca"). Agora larga a
        // fuga e briga. Sem saída mas FORA da vista dela (o fim do túnel, atrás da quina), fica quieto ali: longe
        // o bastante, escapou; se ela achar ele, briga.
        boolean boxed = spot == null && unseen < 10 && (tries >= 2 || ticks - noSpotSince >= CORNER_TICKS);
        if (boxed || stalled) {
            cornered = true;
            return;
        }
        froggy.getLookControl().setLookAt(spot != null ? spot : p.position());
    }

    @Override
    public void stop() {
        ServerPlayer p = threat;
        boolean escaped = p != null && froggy.distanceTo(p) >= SAFE_DIST && unseen >= SAFE_UNSEEN;
        if (p != null && cornered && !escaped) {
            // encurralado de novo pela mesma pessoa em menos de 2 min: espera cada vez mais pra tentar fugir
            // (20, 40, 60 s)
            boolean again = p.getUUID().equals(corneredBy) && froggy.tickCount - corneredAt < SAME_CORNER_TICKS;
            corneredStreak = again ? Math.min(corneredStreak + 1, 3) : 1;
            corneredBy = p.getUUID();
            corneredAt = froggy.tickCount;
            readyAt = froggy.tickCount + CORNERED_COOLDOWN * corneredStreak;
            froggy.ribbit(2.5F); // encurralado: raiva
            froggy.brainLog("encurralado, sem pra onde fugir de " + p.getScoreboardName() + ": briga");
            // o perigo era só quem bateu nele há pouco (sem alvo): vira o alvo, pra brigar de verdade
            if (froggy.getTarget() == null) froggy.setTarget(p);
        } else {
            corneredStreak = 0;
            readyAt = froggy.tickCount + 300;
        }
        if (p != null && escaped) {
            froggy.retreatedFrom(p);
            froggy.setTarget(null);
            froggy.setFeedSearchCooldown(0);
            // longe e escondido: "I'm very low on health, I need food and a plan"
            froggy.speakAfter(VoiceSituation.HIDING, 10);
            froggy.brainLog("escapou de " + p.getScoreboardName() + ": vai comer e pensar num plano");
        }
        froggy.getNavigation().stop();
        threat = null;
        spot = null;
        cornered = false;
    }

    /**
     * Pra onde correr: uns pontos longe dela (de 16 a 28 blocos) e onde tem bicho pra comer (até 48 blocos
     * dele, do lado contrário ao dela); o melhor é o que fica mais longe, fora da vista dela, com cobertura
     * (copa de árvore em cima, água, breu) e comida perto.
     *
     * (v0.7.24) Só vale ponto a que ele CHEGA andando (testa os 3 melhores): antes ele mirava um ponto em cima
     * da pedra ou do outro lado da parede, raspava nela e ficava lá. Não chega no ponto, mas o caminho leva
     * pra longe dela? Serve o fim desse caminho (ver usable).
     */
    @Nullable
    private Escape hideout(ServerPlayer p) {
        Level level = froggy.level();
        Vec3 her = p.position();
        double now = froggy.distanceTo(p);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class,
                froggy.getBoundingBox().inflate(48.0D, 8.0D, 48.0D), Animal::isAlive);
        List<Vec3> spots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Vec3 c = DefaultRandomPos.getPosAway(froggy, 32, 7, her);
            if (c != null) spots.add(c);
        }
        // é pra comer que ele foge: onde tem bicho longe dela também é candidato (sem passar por ela)
        Vec3 away = froggy.position().subtract(her);
        for (Animal a : animals) {
            if (a.distanceTo(p) <= 20.0F || a.position().subtract(froggy.position()).dot(away) <= 0.0D) continue;
            spots.add(a.position());
            if (spots.size() >= 16) break;
        }
        List<Scored> scored = new ArrayList<>();
        for (Vec3 c : spots) {
            BlockPos at = BlockPos.containing(c);
            double score = c.distanceTo(her);
            if (c.distanceTo(her) < now) score -= 30.0D; // não passa por ela
            if (covered(level, at)) score += 8.0D;
            if (!level.getFluidState(at).isEmpty()) score += 5.0D;
            if (level.getMaxLocalRawBrightness(at.above()) <= PlayerSight.DARK) score += 6.0D;
            if (!sees(p, c)) score += 12.0D;
            // foge pra onde tem o que comer (é pra isso que ele está fugindo)
            for (Animal a : animals) {
                if (a.distanceToSqr(c) < 196.0D) {
                    score += 10.0D;
                    break;
                }
            }
            scored.add(new Scored(c, score));
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        for (int i = 0; i < Math.min(3, scored.size()); i++) {
            Vec3 c = scored.get(i).pos();
            Escape e = usable(froggy.getNavigation().createPath(BlockPos.containing(c), 1), c, her, now);
            if (e != null) return e;
        }
        return null;
    }

    /**
     * (v0.7.24) O plano B, quando o sorteio não achou caminho (numa caverna quase todo ponto sorteado cai dentro
     * da pedra): pede ao jogo o caminho pra 24 blocos pra longe dela, depois em leque pros lados (FAN). O jogo
     * devolve o caminho até onde dá pra chegar mais perto desse rumo - o túnel, a curva dele, a saída de lado.
     * Vale o primeiro que deixa ele mais longe dela. Nenhum: não tem saída.
     */
    @Nullable
    private Escape anyWayOut(ServerPlayer p) {
        Vec3 her = p.position();
        double now = froggy.distanceTo(p);
        Vec3 away = froggy.position().subtract(her).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : away.normalize();
        for (int deg : FAN) {
            Vec3 c = froggy.position().add(away.yRot(deg * ((float) Math.PI / 180F)).scale(24.0D));
            Escape e = usable(froggy.getNavigation().createPath(BlockPos.containing(c), 1), c, her, now);
            if (e != null) return e;
        }
        return null;
    }

    /** O caminho serve pra fugir se o fim dele (o ponto, ou até onde deu pra chegar) fica 3+ blocos mais longe dela. */
    @Nullable
    private static Escape usable(@Nullable Path path, Vec3 c, Vec3 her, double now) {
        if (path == null) return null;
        Node end = path.getEndNode();
        if (end == null) return null;
        Vec3 e = path.canReach() ? c : Vec3.atBottomCenterOf(end.asBlockPos());
        return e.distanceTo(her) > now + 3.0D ? new Escape(e, path) : null;
    }

    /** Folha ou teto nos 6 blocos acima. */
    private static boolean covered(Level level, BlockPos at) {
        for (int y = 2; y <= 6; y++) {
            BlockPos up = at.above(y);
            if (level.getBlockState(up).getBlock() instanceof LeavesBlock || !level.canSeeSky(up)) return true;
        }
        return false;
    }

    /** Ela enxerga esse ponto (linha livre dos olhos dela até a cabeça dele ali)? */
    private boolean sees(ServerPlayer p, Vec3 c) {
        Vec3 head = c.add(0, froggy.getEyeHeight(), 0);
        return froggy.level().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), head,
                net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, p))
                .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
}
