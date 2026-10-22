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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

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

    private final FroggydudeEntity froggy;
    @Nullable
    private ServerPlayer threat;
    private int ticks;
    private int unseen;
    private int readyAt;
    @Nullable
    private Vec3 spot;

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
        return threat != null && threat.isAlive() && ticks < GIVE_UP && !froggy.isFrenzyActive()
                && !(froggy.distanceTo(threat) >= SAFE_DIST && unseen >= SAFE_UNSEEN);
    }

    @Override
    public void start() {
        ticks = 0;
        unseen = 0;
        spot = null;
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
        boolean arrived = spot == null || froggy.getNavigation().isDone()
                || froggy.position().distanceToSqr(spot) < 4.0D;
        if (arrived || ticks % 30 == 0 && froggy.distanceTo(p) < 10.0F) {
            spot = hideout(p);
            if (spot != null) froggy.getNavigation().moveTo(spot.x, spot.y, spot.z, FroggyCombatGoal.huntSpeed());
        }
        froggy.getLookControl().setLookAt(spot != null ? spot : p.position());
    }

    @Override
    public void stop() {
        ServerPlayer p = threat;
        boolean escaped = p != null && froggy.distanceTo(p) >= SAFE_DIST && unseen >= SAFE_UNSEEN;
        readyAt = froggy.tickCount + 300;
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
    }

    /**
     * Pra onde correr: uns pontos longe dela (de 16 a 28 blocos), e onde tem bicho
     * pra comer (até 48 blocos dele), e o melhor é o que fica mais longe, fora da
     * vista dela, com cobertura (copa de árvore em cima, água, breu) e comida perto.
     */
    @Nullable
    private Vec3 hideout(ServerPlayer p) {
        Level level = froggy.level();
        Vec3 best = null;
        double bestScore = -1.0E9D;
        java.util.List<Vec3> spots = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Vec3 c = DefaultRandomPos.getPosAway(froggy, 32, 7, p.position());
            if (c != null) spots.add(c);
        }
        // é pra comer que ele foge: onde tem bicho longe dela também é candidato
        for (var a : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,
                froggy.getBoundingBox().inflate(48.0D, 8.0D, 48.0D), e -> e.isAlive() && e.distanceTo(p) > 20.0F)) {
            spots.add(a.position());
            if (spots.size() >= 16) break;
        }
        for (Vec3 c : spots) {
            BlockPos at = BlockPos.containing(c);
            double score = c.distanceTo(p.position());
            if (c.distanceTo(p.position()) < froggy.distanceTo(p)) score -= 30.0D; // não passa por ela
            if (covered(level, at)) score += 8.0D;
            if (!level.getFluidState(at).isEmpty()) score += 5.0D;
            if (level.getMaxLocalRawBrightness(at.above()) <= PlayerSight.DARK) score += 6.0D;
            if (!sees(p, c)) score += 12.0D;
            // foge pra onde tem o que comer (é pra isso que ele está fugindo)
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, new net.minecraft.world.phys.AABB(at).inflate(14.0D),
                    e -> e.isAlive()).isEmpty()) score += 10.0D;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
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
