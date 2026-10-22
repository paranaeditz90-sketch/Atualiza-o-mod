package com.froggydude.entity.ai;

import com.froggydude.brain.PreyStudy;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * IA Real (v0.6, LM): ela mora numa vila e foi minerar? Ele vai até lá e come os
 * aldeões, um por um, casa por casa, antes dela voltar. Quem come é a FroggyFeedGoal
 * (o bote, a flexão, os braços e a cabeça); esta Goal só leva ele até a vila, deixa a
 * fome de aldeão acesa e vai atrás do próximo (a porta trancada ele arromba). Ela
 * voltou e apareceu: a caçada normal assume. Acabou a vila, ou passou do tempo, sai.
 */
public class FroggyMassacreGoal extends Goal {

    private static final int THINK_EVERY = 100;
    /** Até onde da casa dela conta como "a vila". */
    private static final double VILLAGE_RADIUS = 48.0D;
    private static final int MIN_VILLAGERS = 3;
    /** Ela longe assim da casa (ou embaixo da terra) = foi minerar. */
    private static final double AWAY = 64.0D;
    private static final double MAX_TRAVEL = 220.0D;
    /** Um massacre por dia (de jogo) no máximo. */
    private static final int COOLDOWN = 24000;
    private static final int MAX_TICKS = 6000;

    private final FroggydudeEntity froggy;
    @Nullable
    private BlockPos home;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private int nextThink;
    private int readyAt;
    private int startedAt;
    private int repath;
    /** Teste (/froggydude estudo massacre): vai agora, mesmo com ela em casa. */
    private boolean forced;

    public FroggyMassacreGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public void forceNext() {
        forced = true;
        readyAt = 0;
        nextThink = 0;
    }

    @Override
    public boolean canUse() {
        // no meio de um massacre (a FeedGoal parou ele pra comer): volta na hora
        if (home != null) {
            // ela apareceu e ele foi pra cima dela: o massacre acabou aqui
            if (froggy.getTarget() instanceof net.minecraft.world.entity.player.Player) {
                end("largou a vila de " + ownerName + ": caçando gente");
                return false;
            }
            return free() && froggy.level() instanceof ServerLevel level && stillOn(level);
        }
        if (!forced && (froggy.tickCount < nextThink || froggy.tickCount < readyAt)) return false;
        nextThink = froggy.tickCount + THINK_EVERY;
        if (!FroggyConfig.ai().real || !(froggy.level() instanceof ServerLevel level) || !free()) return false;
        String dim = level.dimension().location().toString();
        for (ServerPlayer p : level.players()) {
            if (!valid(p)) continue;
            PreyStudy study = FroggyWorldData.get(level).brain.memory(p.getUUID(), p.getScoreboardName()).study;
            BlockPos h = villageHome(level, study, dim);
            if (h == null || h.distSqr(froggy.blockPosition()) > MAX_TRAVEL * MAX_TRAVEL) {
                if (forced) froggy.brainLog(h == null ? "massacre: " + p.getScoreboardName() + " não mora numa vila (cama/baú com "
                        + MIN_VILLAGERS + "+ aldeões perto)" : "massacre: a vila de " + p.getScoreboardName() + " é longe demais");
                continue;
            }
            if (!forced && !away(level, p, h)) continue;
            // nem sempre: às vezes ele prefere ir atrás dela (o faro, a tocaia)
            if (!forced && froggy.getRandom().nextFloat() > 0.6F) {
                readyAt = froggy.tickCount + 1200;
                return false;
            }
            forced = false;
            home = h;
            owner = p.getUUID();
            ownerName = p.getScoreboardName();
            startedAt = froggy.tickCount;
            AABB village = new AABB(h).inflate(VILLAGE_RADIUS, 16.0D, VILLAGE_RADIUS);
            int count = level.getEntitiesOfClass(AbstractVillager.class, village, v -> v.isAlive() && !v.isBaby()).size();
            froggy.brainLog(ownerName + " foi minerar: vou na vila dela comer os aldeões (" + count + " em volta de "
                    + h.toShortString() + ")");
            return true;
        }
        forced = false;
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return home != null && free() && froggy.level() instanceof ServerLevel level && stillOn(level);
    }

    @Override
    public void start() {
        froggy.setMassacring(true);
        froggy.craveVillagers();
        repath = 0;
    }

    @Override
    public void stop() {
        froggy.getNavigation().stop();
        // a FeedGoal só interrompe pra comer: o massacre continua (home fica)
        if (home == null) froggy.setMassacring(false);
    }

    @Override
    public void tick() {
        froggy.craveVillagers();
        if (--repath > 0) return;
        repath = 20;
        AbstractVillager next = nextVillager((ServerLevel) froggy.level());
        if (next == null) {
            // ainda longe: vai pra casa dela; já lá e sem ninguém: acabou a vila
            if (froggy.blockPosition().distSqr(home) > 16 * 16) {
                froggy.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, FroggyCombatGoal.huntSpeed() * 0.9D);
            } else {
                end("a vila de " + ownerName + " acabou");
            }
            return;
        }
        froggy.getNavigation().moveTo(next, FroggyCombatGoal.huntSpeed() * 0.9D);
    }

    /** Ela voltou e está perto, ou o tempo passou: larga (a caçada normal assume). */
    private boolean stillOn(ServerLevel level) {
        ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (froggy.tickCount - startedAt > MAX_TICKS) {
            end("cansou de procurar aldeão na vila de " + ownerName);
            return false;
        }
        if (p != null && p.level() == level && p.distanceTo(froggy) < 24.0F) {
            end(ownerName + " voltou pra vila");
            return false;
        }
        return true;
    }

    private void end(String why) {
        if (home == null) return;
        froggy.brainLog(why);
        home = null;
        owner = null;
        froggy.setMassacring(false);
        readyAt = froggy.tickCount + COOLDOWN;
    }

    @Nullable
    private AbstractVillager nextVillager(ServerLevel level) {
        AABB area = new AABB(home).inflate(VILLAGE_RADIUS, 16.0D, VILLAGE_RADIUS);
        return level.getEntitiesOfClass(AbstractVillager.class, area, v -> v.isAlive() && !v.isBaby())
                .stream().min(Comparator.comparingDouble(froggy::distanceToSqr)).orElse(null);
    }

    /** O lugar dela (cama, baú, bancada) que fica numa vila: com 3+ aldeões adultos em volta. */
    @Nullable
    private static BlockPos villageHome(ServerLevel level, PreyStudy study, String dim) {
        PreyStudy.Place best = null;
        for (PreyStudy.Place q : study.places) {
            if (q.kind == PreyStudy.Kind.MINE || !q.dim.equals(dim) || !level.isLoaded(q.pos)) continue;
            if (best != null && q.weight <= best.weight) continue;
            AABB area = new AABB(q.pos).inflate(VILLAGE_RADIUS, 16.0D, VILLAGE_RADIUS);
            List<AbstractVillager> vs = level.getEntitiesOfClass(AbstractVillager.class, area, v -> v.isAlive() && !v.isBaby());
            if (vs.size() >= MIN_VILLAGERS) best = q;
        }
        return best == null ? null : best.pos;
    }

    /** Longe da vila, ou lá embaixo minerando (sem céu em cima, bem abaixo da casa). */
    private static boolean away(ServerLevel level, ServerPlayer p, BlockPos home) {
        double dx = p.getX() - home.getX(), dz = p.getZ() - home.getZ();
        double d2 = dx * dx + dz * dz;
        if (d2 > AWAY * AWAY) return true;
        return p.getY() < home.getY() - 12 && d2 > 24 * 24 && !level.canSeeSky(p.blockPosition());
    }

    private boolean valid(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && p.level() == froggy.level()
                && !froggy.underTruce(p);
    }

    /** Sem alvo e solto (comer, a fase 2, fugindo pra se curar e a tocaia passam na frente). */
    private boolean free() {
        if (froggy.getTarget() != null || froggy.isHolding() || froggy.isFrenzyActive() || froggy.isStakingOut()
                || FroggyRetreatGoal.mustFlee(froggy)) {
            return false;
        }
        FroggyState st = froggy.getFroggyState();
        return st == FroggyState.CHASE || st == FroggyState.IDLE;
    }
}
