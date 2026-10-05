package com.froggydude.entity.ai;

import com.froggydude.brain.Scent;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.init.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * O faro: sem ver ninguém, ele sente o rastro de cheiro (brain/Scent) e vai
 * atrás, de quatro, farejando. Onde o rastro termina (a vítima se escondeu,
 * entrou na água, sumiu de vista) começa a VARREDURA: para, fareja, olha em
 * volta; de perto (até 14 blocos) ele sente de onde vem o cheiro, com um erro
 * que diminui quanto mais perto chega; de longe, varre em círculos em volta de
 * onde o rastro acabou. A 5 blocos ele sabe exatamente onde a vítima está,
 * mesmo atrás da parede: vira o alvo, e o combate arromba (língua na parede,
 * come o telhado). Se ela se mexer e deixar cheiro novo, ele volta pro rastro.
 */
public class FroggyTrackGoal extends Goal {

    /** Sente o rastro de qualquer um a até 24 blocos. */
    private static final double SMELL_RANGE = 24.0D;
    /** O da presa que acabou de sumir ele sente de mais longe. */
    private static final double LOST_PREY_RANGE = 40.0D;
    /** Andando no rastro: a marca mais nova até essa distância é o próximo passo. */
    private static final double TRAIL_STEP = 14.0D;
    /** Perto assim ele sente de onde vem o cheiro (com erro). */
    private static final double SNIFF_RANGE = 14.0D;
    /** Perto assim ele sabe exatamente onde está, mesmo atrás da parede. */
    private static final double FOUND_RANGE = 5.0D;
    private static final int TRACK_TICKS = 1800;
    private static final int SEARCH_TICKS = 600;
    private static final int SNIFF_EVERY = 40;
    private static final int SNIFF_PAUSE = 12;

    private enum Mode { TRAIL, SEARCH }

    private final FroggydudeEntity froggy;
    @Nullable
    private UUID prey;
    private String preyName = "?";
    private Mode mode = Mode.TRAIL;
    private int ticks;
    private int modeTicks;
    private int pauseTicks;
    private int sweep;
    private Vec3 searchCenter = Vec3.ZERO;
    /** Hora da marca mais nova quando a varredura começou: cheiro mais novo = ela se mexeu. */
    private long searchMarkTime;
    @Nullable
    private Vec3 lastGoal;
    private boolean sensedDirection;
    private boolean found;
    private double bestTrailDist = Double.MAX_VALUE;
    private int trailStuck;
    private double bestSearchDist = Double.MAX_VALUE;
    private int searchStuck;
    @Nullable
    private Vec3 farGoal;
    /** Pra onde vai depois de farejar parado. */
    @Nullable
    private Vec3 pendingGoal;
    private int farGoalAt;

    public FroggyTrackGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (froggy.tickCount % 10 != 0 || !(froggy.level() instanceof ServerLevel level)) return false;
        if (!free()) return false;
        UUID lost = froggy.getRecentlyLostPrey();
        Scent.Sniff s = lost == null ? null : Scent.freshest(level, froggy.position(), LOST_PREY_RANGE, lost);
        if (s == null || !valid(player(s.player()))) s = Scent.freshest(level, froggy.position(), SMELL_RANGE, null);
        if (s == null) return false;
        ServerPlayer p = player(s.player());
        if (!valid(p)) return false;
        prey = s.player();
        preyName = p.getScoreboardName();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (prey == null || found || !free() || !valid(player(prey))) return false;
        return ticks < TRACK_TICKS && (mode != Mode.SEARCH || modeTicks < SEARCH_TICKS);
    }

    /** Sem alvo, sem estar preso a nada (invasão, fase 2, comendo). */
    private boolean free() {
        if (froggy.getTarget() != null || froggy.isHolding() || froggy.isFrenzyActive()) return false;
        FroggyState st = froggy.getFroggyState();
        return st == FroggyState.CHASE || st == FroggyState.IDLE;
    }

    @Nullable
    private ServerPlayer player(@Nullable UUID id) {
        if (id == null || froggy.getServer() == null) return null;
        return froggy.getServer().getPlayerList().getPlayer(id);
    }

    private boolean valid(@Nullable ServerPlayer p) {
        return p != null && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.level() == froggy.level()
                && !froggy.isFedOn(p);
    }

    @Override
    public void start() {
        ticks = 0;
        modeTicks = 0;
        pauseTicks = 0;
        sweep = 0;
        mode = Mode.TRAIL;
        lastGoal = null;
        sensedDirection = false;
        found = false;
        bestTrailDist = Double.MAX_VALUE;
        trailStuck = 0;
        farGoal = null;
        pendingGoal = null;
        froggy.setTracking(true);
        long now = froggy.level().getGameTime();
        Scent.Mark end = Scent.newest(prey, now);
        froggy.brainLog("farejando o rastro de " + preyName
                + (end == null ? "" : " (cheiro de " + (now - end.time()) / 20 + " s atrás)"));
        sniff();
    }

    @Override
    public void stop() {
        froggy.setTracking(false);
        froggy.getNavigation().stop();
        if (!found && prey != null) froggy.brainLog("perdi o rastro de " + preyName);
        prey = null;
    }

    @Override
    public void tick() {
        if (found) return; // já achou: o combate assume no próximo tique
        ticks++;
        modeTicks++;
        ServerPlayer p = player(prey);
        if (p == null) return;
        ServerLevel level = (ServerLevel) froggy.level();
        long now = level.getGameTime();

        double d = froggy.distanceTo(p);
        // viu: acabou a procura
        if (d < 48.0D && froggy.getSensing().hasLineOfSight(p)) {
            found(p, "vi");
            return;
        }
        // tão perto que o cheiro entrega onde está, mesmo atrás da parede
        if (d <= FOUND_RANGE) {
            found(p, "cheiro");
            return;
        }
        if (pauseTicks > 0) {
            // parado farejando, olhando em volta; depois vai pro ponto que farejou
            pauseTicks--;
            froggy.getNavigation().stop();
            if (pauseTicks == 0 && pendingGoal != null) {
                froggy.getNavigation().moveTo(pendingGoal.x, pendingGoal.y, pendingGoal.z,
                        FroggyCombatGoal.huntSpeed() * 0.7D);
                pendingGoal = null;
            }
            return;
        }

        if (mode == Mode.TRAIL) {
            if (ticks % 50 == 0) sniff();
            if (ticks % 10 != 0) return;
            // o próximo passo: a marca mais nova por perto; se não tem nenhuma perto,
            // a mais nova que ele sente de longe (foi ela que ele farejou)
            Scent.Sniff next = Scent.freshest(level, froggy.position(), TRAIL_STEP, prey);
            Scent.Mark end = Scent.newest(prey, now);
            if (next == null) {
                Scent.Sniff far = Scent.freshest(level, froggy.position(), LOST_PREY_RANGE, prey);
                if (far != null) {
                    // de longe o cheiro só dá a direção: vai pra uma área em volta
                    // (erro de 1/4 da distância, sorteado de novo a cada 2 s)
                    if (farGoal == null || ticks - farGoalAt >= 40) {
                        double dist = far.mark().pos().distanceTo(froggy.position());
                        double err = dist * 0.25D;
                        farGoal = far.mark().pos().add(froggy.getRandom().nextGaussian() * err, 0,
                                froggy.getRandom().nextGaussian() * err);
                        farGoalAt = ticks;
                    }
                    lastGoal = farGoal;
                    froggy.getNavigation().moveTo(farGoal.x, farGoal.y, farGoal.z, FroggyCombatGoal.huntSpeed() * 0.8D);
                    froggy.getLookControl().setLookAt(farGoal.x, farGoal.y + 0.5D, farGoal.z);
                    return;
                }
            }
            farGoal = null;
            if (next == null) {
                // o rastro sumiu (água, muito velho): procura em volta de onde estava indo
                startSearch(lastGoal != null ? lastGoal : froggy.position(), end);
                return;
            }
            Vec3 at = next.mark().pos();
            double hd = horizontal(froggy.position(), at);
            if (hd < 2.5D) {
                // chegou na marca mais nova que dá pra sentir daqui: é o fim do rastro
                // (ou um buraco nele - teleporte, pérola)
                startSearch(at, end);
                return;
            }
            // não chega mais perto (a marca está dentro de uma casa fechada, num buraco):
            // dali ele varre
            if (hd < bestTrailDist - 0.5D) {
                bestTrailDist = hd;
                trailStuck = 0;
            } else if ((trailStuck += 10) >= 60 && hd < 10.0D) {
                startSearch(at, end);
                return;
            }
            if (lastGoal == null || lastGoal.distanceToSqr(at) > 1.0D) {
                bestTrailDist = hd;
                trailStuck = 0;
            }
            lastGoal = at;
            float fresh = next.mark().strength(now);
            double speed = fresh > 0.75F ? FroggyCombatGoal.huntSpeed() : FroggyCombatGoal.huntSpeed() * 0.8D;
            froggy.getNavigation().moveTo(at.x, at.y, at.z, speed);
            froggy.getLookControl().setLookAt(at.x, at.y + 0.5D, at.z);
            return;
        }

        // ------------------------------------------------ varredura
        if (d <= SNIFF_RANGE) {
            // o cheiro é forte, mas não dá pra chegar mais perto (ela está trancada no
            // meio de uma casa grande): ele já sabe onde é - arromba
            if (d < bestSearchDist - 0.5D) {
                bestSearchDist = d;
                searchStuck = 0;
            } else if (++searchStuck > 100) {
                found(p, "cheiro forte, atrás da parede");
                return;
            }
        }
        if (modeTicks % 10 == 0) {
            Scent.Mark end = Scent.newest(prey, now);
            if (end != null && end.time() > searchMarkTime + 40
                    && Scent.freshest(level, froggy.position(), TRAIL_STEP, prey) != null) {
                // ela se mexeu e deixou cheiro novo por perto: de volta pro rastro
                froggy.brainLog("cheiro novo de " + preyName + ": voltando pro rastro");
                mode = Mode.TRAIL;
                modeTicks = 0;
                bestTrailDist = Double.MAX_VALUE;
                trailStuck = 0;
                return;
            }
        }
        if (modeTicks % SNIFF_EVERY != 1) return;
        pauseTicks = SNIFF_PAUSE;
        froggy.getNavigation().stop();
        sniff();
        lookAround();
        Vec3 goal;
        if (d <= SNIFF_RANGE) {
            // sente de onde vem o cheiro: quanto mais perto, mais certeiro
            double err = d * 0.3D;
            goal = p.position().add(froggy.getRandom().nextGaussian() * err, 0, froggy.getRandom().nextGaussian() * err);
            if (!sensedDirection) {
                sensedDirection = true;
                froggy.brainLog("o cheiro de " + preyName + " vem dali: varrendo perto de "
                        + (int) goal.x + ", " + (int) goal.z);
            }
        } else {
            // varre em volta de onde o rastro acabou: um anel de 6 blocos, depois de 11
            double ang = sweep * 2.39996D;
            double r = sweep < 6 ? 6.0D : 11.0D;
            sweep++;
            goal = searchCenter.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
        }
        lastGoal = goal;
        pendingGoal = goal;
    }

    private void startSearch(Vec3 center, @Nullable Scent.Mark end) {
        mode = Mode.SEARCH;
        modeTicks = 0;
        sweep = 0;
        bestSearchDist = Double.MAX_VALUE;
        searchStuck = 0;
        searchCenter = center;
        searchMarkTime = end == null ? 0L : end.time();
        froggy.getNavigation().stop();
        froggy.brainLog("o rastro de " + preyName + " acabou em " + (int) center.x + ", " + (int) center.y + ", "
                + (int) center.z + ": varredura");
        // ele sabe que tu está por perto: "you can run, but you can't hide"
        froggy.speak(VoiceSituation.SEARCH);
    }

    private void found(ServerPlayer p, String how) {
        found = true;
        froggy.brainLog("achei " + preyName + " (" + how + ") em " + p.blockPosition().toShortString());
        froggy.setTracking(false);
        froggy.setTarget(p); // a risadinha de quem achou de novo / "hey, humans!" (setTarget)
    }

    private void sniff() {
        froggy.playSound(ModSounds.SNIFF.get(), 1.3F, 0.85F + froggy.getRandom().nextFloat() * 0.3F);
    }

    private void lookAround() {
        double a = froggy.getRandom().nextDouble() * Math.PI * 2.0D;
        froggy.getLookControl().setLookAt(froggy.getX() + Math.cos(a) * 4.0D, froggy.getEyeY(),
                froggy.getZ() + Math.sin(a) * 4.0D);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
