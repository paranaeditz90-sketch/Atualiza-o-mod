package com.froggydude.entity.ai;

import com.froggydude.brain.Intent;
import com.froggydude.brain.PreyStudy;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModSounds;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;

/**
 * (v0.7.42) Explorar (IA dinâmica, etapa 1; só na IA Real, quando o decisor escolhe EXPLORE). Entediado, sem presa nem
 * comida na mão, ele não fica parado: sai andando pelo mapa pra longe de onde já passou, parando de vez em quando pra
 * farejar e olhar em volta. Às vezes vai bisbilhotar um lugar que ele sabe que a presa usa (a cama, o baú, a mina:
 * brain.PreyStudy). Achou alguma coisa (cheiro, presa, comida), o decisor troca e ele larga.
 */
public class FroggyExploreGoal extends Goal {

    /** Uma saída: até 3 pontos, no máximo 60 s. */
    private static final int WAYPOINTS = 3;
    private static final int MAX_TICKS = 1200;
    /** Sem chegar mais perto do ponto por tanto tempo: desiste dele. */
    private static final int STUCK_TICKS = 100;
    /** Os últimos lugares por onde passou (vai pra longe deles). */
    private static final int MEMORY = 8;
    /** Bisbilhotar um lugar dela: chance por ponto e até onde vai. */
    private static final float PREY_PLACE_CHANCE = 0.35F;
    private static final double PREY_PLACE_RANGE = 96D;

    private final FroggydudeEntity froggy;
    private final Deque<BlockPos> visited = new ArrayDeque<>();
    @Nullable
    private Vec3 goal;
    private int ticks;
    private int points;
    private int bestDistAt;
    private double bestDist;
    private int pauseLeft;
    private int nextPause;
    private int readyAt;

    public FroggyExploreGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!FroggyDecider.active() || froggy.tickCount < readyAt || !free()) return false;
        return froggy.decider().intent() == Intent.EXPLORE;
    }

    @Override
    public boolean canContinueToUse() {
        return free() && froggy.decider().intent() == Intent.EXPLORE && ticks < MAX_TICKS && points <= WAYPOINTS;
    }

    private boolean free() {
        if (froggy.getTarget() != null || froggy.isHolding() || froggy.isFrenzyActive() || froggy.isStakingOut()) return false;
        FroggyState st = froggy.getFroggyState();
        return st == FroggyState.CHASE || st == FroggyState.IDLE;
    }

    @Override
    public void start() {
        ticks = 0;
        points = 0;
        pauseLeft = 0;
        nextPause = 120 + froggy.getRandom().nextInt(120);
        next();
    }

    @Override
    public void stop() {
        froggy.getNavigation().stop();
        goal = null;
        readyAt = froggy.tickCount + 40;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        if (pauseLeft > 0) {
            // parado farejando e olhando em volta
            if (--pauseLeft % 20 == 0) look();
            if (pauseLeft == 0) moveOn();
            return;
        }
        if (goal == null) {
            next();
            return;
        }
        double d = froggy.position().distanceTo(goal);
        if (d < bestDist - 0.5D) {
            bestDist = d;
            bestDistAt = ticks;
        }
        if (d < 2.5D || froggy.getNavigation().isDone() || ticks - bestDistAt > STUCK_TICKS) {
            remember(BlockPos.containing(froggy.position()));
            next();
            return;
        }
        if (ticks >= nextPause) {
            // de vez em quando para e fareja (o jeito dele de "procurar")
            nextPause = ticks + 140 + froggy.getRandom().nextInt(140);
            pauseLeft = 30 + froggy.getRandom().nextInt(30);
            froggy.getNavigation().stop();
            froggy.playSound(ModSounds.SNIFF.get(), 1.2F, 0.85F + froggy.getRandom().nextFloat() * 0.3F);
            look();
        }
    }

    private void next() {
        points++;
        if (points > WAYPOINTS) return;
        goal = pick();
        if (goal == null) {
            points = WAYPOINTS + 1; // nada pra onde ir: acaba a saída
            return;
        }
        bestDist = froggy.position().distanceTo(goal);
        bestDistAt = ticks;
        moveOn();
    }

    private void moveOn() {
        bestDistAt = ticks; // a parada pra farejar não conta como travado
        if (goal != null) froggy.getNavigation().moveTo(goal.x, goal.y, goal.z, 0.9D);
    }

    private void look() {
        double a = froggy.getRandom().nextDouble() * Math.PI * 2.0D;
        froggy.getLookControl().setLookAt(froggy.getX() + Math.cos(a) * 4.0D, froggy.getEyeY(),
                froggy.getZ() + Math.sin(a) * 4.0D);
    }

    /** O próximo ponto: às vezes um lugar dela; senão, o mais longe de onde ele já passou. */
    @Nullable
    private Vec3 pick() {
        Vec3 place = preyPlace();
        if (place != null) {
            Vec3 toward = DefaultRandomPos.getPosTowards(froggy, 24, 7, place, Math.PI / 2D);
            if (toward != null) {
                froggy.brainLog(String.format(java.util.Locale.ROOT, "explorando: vou bisbilhotar perto de %d, %d, %d",
                        (int) place.x, (int) place.y, (int) place.z));
                return toward;
            }
        }
        Vec3 best = null;
        double bestScore = -1D;
        for (int i = 0; i < 6; i++) {
            Vec3 p = LandRandomPos.getPos(froggy, 32, 8);
            if (p == null) continue;
            double s = Math.min(froggy.position().distanceTo(p), 32D);
            for (BlockPos v : visited) s += Math.min(Math.sqrt(v.distToCenterSqr(p)), 48D) * 0.25D;
            if (s > bestScore) {
                bestScore = s;
                best = p;
            }
        }
        if (best != null && points == 1) froggy.brainLog("explorando: sem nada pra fazer, saio andando pelo mapa");
        return best;
    }

    /** Um lugar que a presa usa (cama, baú, mina) a até {@link #PREY_PLACE_RANGE} blocos, de vez em quando. */
    @Nullable
    private Vec3 preyPlace() {
        if (!(froggy.level() instanceof ServerLevel level) || froggy.getRandom().nextFloat() > PREY_PLACE_CHANCE) return null;
        String dim = level.dimension().location().toString();
        FroggyWorldData data = FroggyWorldData.get(level);
        PreyStudy.Place best = null;
        double bestD = PREY_PLACE_RANGE * PREY_PLACE_RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            PreyStudy study = data.brain.memory(p.getUUID(), p.getScoreboardName()).study;
            for (PreyStudy.Place pl : study.places) {
                if (!dim.equals(pl.dim)) continue;
                double d = pl.pos.distSqr(froggy.blockPosition());
                if (d < 12D * 12D || d > bestD) continue;
                bestD = d;
                best = pl;
            }
        }
        return best == null ? null : Vec3.atCenterOf(best.pos);
    }

    private void remember(BlockPos pos) {
        visited.addLast(pos);
        while (visited.size() > MEMORY) visited.removeFirst();
    }
}
