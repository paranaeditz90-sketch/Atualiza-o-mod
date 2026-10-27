package com.froggydude.entity.ai;

import com.froggydude.brain.PreyStudy;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.player.PlayerSight;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * IA Real (v0.6), o estudo da presa: ele sabe onde ela guarda as coisas, dorme,
 * trabalha e minera, e de quanto em quanto tempo volta em cada lugar
 * (brain/PreyStudy). Longe dela, em vez de ir atrás, às vezes ele vai pro lugar
 * que ela está pra voltar e ESPERA lá dentro: grudado no canto do teto (casa) ou
 * parado no breu (caverna). O Grox ("Minecraft but I have RABIES", 0:58-1:23)
 * passa o tempo fora, volta pra guardar as coisas e ele já estava lá.
 *
 * Quando ela chega, espera ela abrir o baú (tela aberta) ou chegar bem embaixo sem
 * ver e cai em cima dela (o bote termina devorando). Se ela ver ele antes, é na hora.
 * (Até a v0.7.14, brincando com ela, ele ria e sumia: saiu com o "viu, vai".)
 */
public class FroggyStakeoutGoal extends Goal {

    /** Espera no máximo 5 minutos lá. */
    private static final int MAX_WAIT = 6000;
    /** Pensa em ir de tocaia a cada 2 s, sem alvo. */
    private static final int THINK_EVERY = 40;
    private static final double MAX_TRAVEL = 200.0D;
    /** O teto a 1,95 bloco dos pés (o topo da caixa dele encosta no teto; ver ceiling_hang). */
    private static final double HANG_DROP = 1.95D;

    private enum Stage { GO, CLIMB, WAIT, REVEAL, DONE }

    private final FroggydudeEntity froggy;
    @Nullable
    private UUID prey;
    private String preyName = "?";
    @Nullable
    private PreyStudy.Place place;
    private Stage stage = Stage.DONE;
    private int stageAt;
    private int startedAt;
    private int readyAt;
    /** O seletor só pergunta canUse a cada 2 ticks (e não no tickCount dele): conta pelo relógio. */
    private int nextThink;
    /** Ficar em pé aqui (o breu) ou embaixo do canto (antes de subir). */
    @Nullable
    private Vec3 stand;
    /** Pendurado: onde fica a entidade (o topo da caixa no teto) e pra onde olha (pra fora do canto). */
    @Nullable
    private Vec3 hang;
    private float hangYaw;
    @Nullable
    private Vec3 climbFrom;
    private int unseenNear;
    /** Comando de teste (/froggydude estudo tocaia): vai agora, mesmo fora de hora. */
    private boolean forced;

    public FroggyStakeoutGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Teste: vai de tocaia já (no lugar mais usado dela, mesmo que não seja hora). */
    public void forceNext() {
        forced = true;
        readyAt = 0;
    }

    @Override
    public boolean canUse() {
        if (!forced && (froggy.tickCount < nextThink || froggy.tickCount < readyAt)) return false;
        nextThink = froggy.tickCount + THINK_EVERY;
        if (!FroggyConfig.ai().real || !(froggy.level() instanceof ServerLevel level) || !free()) return false;
        ServerPlayer p = pickPrey(level);
        if (p == null) return false;
        PreyStudy study = FroggyWorldData.get(level).brain.memory(p.getUUID(), p.getScoreboardName()).study;
        long now = level.getGameTime();
        String dim = level.dimension().location().toString();
        PreyStudy.Place best = forced ? mostUsed(study, dim, p) : study.stakeout(dim, p.blockPosition(), now);
        if (best == null || !level.isLoaded(best.pos) || best.pos.distSqr(froggy.blockPosition()) > MAX_TRAVEL * MAX_TRAVEL) {
            forced = false;
            return false;
        }
        // imprevisível: nem sempre (às vezes vai atrás pelo faro, às vezes espera)
        if (!forced && froggy.getRandom().nextFloat() > 0.55F) {
            readyAt = froggy.tickCount + 600;
            return false;
        }
        forced = false;
        prey = p.getUUID();
        preyName = p.getScoreboardName();
        place = best;
        return true;
    }

    @Nullable
    private static PreyStudy.Place mostUsed(PreyStudy study, String dim, ServerPlayer p) {
        PreyStudy.Place best = null;
        for (PreyStudy.Place q : study.places) {
            if (!q.dim.equals(dim) || q.pos.distSqr(p.blockPosition()) < 16 * 16) continue;
            if (best == null || q.weight > best.weight) best = q;
        }
        return best;
    }

    /** Sem alvo e sem estar preso a nada (comendo, fase 2, fugindo pra se curar). */
    private boolean free() {
        if (froggy.getTarget() != null || froggy.isHolding() || froggy.isFrenzyActive() || FroggyRetreatGoal.mustFlee(froggy)) {
            return false;
        }
        FroggyState st = froggy.getFroggyState();
        return st == FroggyState.CHASE || st == FroggyState.IDLE;
    }

    @Nullable
    private ServerPlayer pickPrey(ServerLevel level) {
        ServerPlayer best = null;
        UUID lost = froggy.getRecentlyLostPrey();
        for (ServerPlayer p : level.players()) {
            if (!valid(p)) continue;
            if (p.getUUID().equals(lost)) return p;
            if (best == null || froggy.distanceToSqr(p) < froggy.distanceToSqr(best)) best = p;
        }
        return best;
    }

    private boolean valid(@Nullable ServerPlayer p) {
        return p != null && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.level() == froggy.level()
                && !froggy.isFedOn(p) && !froggy.underTruce(p) && !froggy.hidingFrom(p);
    }

    @Nullable
    private ServerPlayer player() {
        if (prey == null || froggy.getServer() == null) return null;
        ServerPlayer p = froggy.getServer().getPlayerList().getPlayer(prey);
        return valid(p) ? p : null;
    }

    @Override
    public boolean canContinueToUse() {
        if (stage == Stage.DONE || player() == null || FroggyRetreatGoal.mustFlee(froggy) || froggy.isFrenzyActive()) return false;
        // bateram nele (o alvo vem do HurtByTarget) ou já caiu em cima dela: o combate assume
        return froggy.getTarget() == null;
    }

    @Override
    public void start() {
        stage = Stage.GO;
        stageAt = froggy.tickCount;
        startedAt = froggy.tickCount;
        stand = null;
        hang = null;
        unseenNear = 0;
        froggy.setStakingOut(true);
        PreyStudy.Place pl = place;
        if (pl != null) {
            froggy.brainLog(String.format(java.util.Locale.ROOT, "vai esperar %s %s em %d, %d, %d", preyName, label(pl.kind),
                    pl.pos.getX(), pl.pos.getY(), pl.pos.getZ()));
            go(pl.pos);
        }
    }

    private static String label(PreyStudy.Kind kind) {
        return switch (kind) {
            case STORAGE -> "no baú";
            case BED -> "na cama";
            case WORK -> "na bancada";
            case MINE -> "na mina";
        };
    }

    @Override
    public void stop() {
        unhang();
        froggy.setStakingOut(false);
        froggy.getNavigation().stop();
        if (stage != Stage.DONE && froggy.getTarget() == null) {
            froggy.brainLog("largou a tocaia de " + preyName);
        }
        stage = Stage.DONE;
        readyAt = froggy.tickCount + 1200 + froggy.getRandom().nextInt(1200);
        prey = null;
        place = null;
    }

    @Override
    public void tick() {
        ServerPlayer p = player();
        PreyStudy.Place pl = place;
        if (p == null || pl == null) {
            stage = Stage.DONE;
            return;
        }
        double dist = froggy.distanceTo(p);
        boolean seen = PlayerSight.sees(p, froggy);
        switch (stage) {
            case GO -> tickGo(p, pl, dist, seen);
            case CLIMB -> tickClimb();
            case WAIT -> tickWait(p, dist, seen);
            case REVEAL -> drop(p);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- ir e se esconder

    private void tickGo(ServerPlayer p, PreyStudy.Place pl, double dist, boolean seen) {
        // ela voltou antes dele chegar (e viu ele no caminho): a tocaia não deu
        if (seen && dist < 24.0D || froggy.tickCount - stageAt > 2400) {
            froggy.brainLog(seen ? preyName + " me viu indo pra tocaia" : "não chegou na tocaia");
            stage = Stage.DONE;
            return;
        }
        if (stand == null && froggy.position().distanceTo(Vec3.atBottomCenterOf(pl.pos)) < 7.0D) {
            choose(pl);
            if (stand == null) {
                stage = Stage.DONE;
                return;
            }
            go(BlockPos.containing(stand));
        }
        Vec3 to = stand != null ? stand : Vec3.atBottomCenterOf(pl.pos);
        if (froggy.tickCount % 30 == 0) go(BlockPos.containing(to));
        if (stand != null && horizontal(froggy.position(), stand) < 0.7D && Math.abs(froggy.getY() - stand.y) < 1.2D) {
            froggy.getNavigation().stop();
            if (hang != null) {
                stage = Stage.CLIMB;
                stageAt = froggy.tickCount;
                climbFrom = froggy.position();
                froggy.setNoGravity(true);
                froggy.setFroggyState(FroggyState.CEILING_HANG);
            } else {
                stage = Stage.WAIT;
                stageAt = froggy.tickCount;
                froggy.brainLog("no breu esperando " + preyName);
            }
        }
    }

    /** Sobe pro canto do teto em 8 ticks (o pulo de aranha). */
    private void tickClimb() {
        Vec3 h = hang, from = climbFrom;
        if (h == null || from == null) {
            stage = Stage.DONE;
            return;
        }
        float a = Math.min(1.0F, (froggy.tickCount - stageAt) / 8.0F);
        Vec3 at = from.lerp(h, a);
        holdAt(at);
        if (a >= 1.0F) {
            stage = Stage.WAIT;
            stageAt = froggy.tickCount;
            froggy.brainLog("no canto do teto esperando " + preyName);
        }
    }

    private void tickWait(ServerPlayer p, double dist, boolean seen) {
        froggy.mute(10);
        if (hang != null) {
            holdAt(hang);
        } else {
            froggy.getNavigation().stop();
            if (dist < 20.0D) froggy.getLookControl().setLookAt(p, 10.0F, 10.0F);
        }
        if (froggy.tickCount - stageAt > MAX_WAIT) {
            froggy.brainLog(preyName + " não veio: larga a tocaia");
            unhang();
            stage = Stage.DONE;
            return;
        }
        boolean distracted = PlayerSight.distracted(p);
        unseenNear = !seen && dist < 4.0D ? unseenNear + 1 : 0;
        boolean go = seen && dist < 16.0D
                || dist < 5.0D && distracted
                || hang == null && dist < 4.5D
                || unseenNear > 30;
        if (!go) return;
        stage = Stage.REVEAL;
        stageAt = froggy.tickCount;
        froggy.brainLog(distracted ? preyName + " abriu o baú: cai em cima" : seen ? preyName + " me viu: pra cima"
                : preyName + " bem embaixo: cai em cima");
    }

    /**
     * Caçando: se solta em cima dela e o combate termina (o bote acaba devorando). Do
     * teto é o bote do canto (LM): se joga nela, empurra ou já derruba montado.
     */
    private void drop(ServerPlayer p) {
        boolean ceiling = hang != null;
        unhang();
        froggy.setStakingOut(false);
        froggy.forceFinish(FroggyState.PIN_HOLD, 120);
        froggy.setTarget(p);
        if (ceiling) {
            froggy.ceilingDive(p);
        } else {
            Vec3 to = p.position().subtract(froggy.position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 1.0E-4D) froggy.setDeltaMovement(to.normalize().scale(0.35D).add(0, 0.1D, 0));
        }
        stage = Stage.DONE;
    }

    private void holdAt(Vec3 at) {
        froggy.setPos(at.x, at.y, at.z);
        froggy.setDeltaMovement(Vec3.ZERO);
        froggy.setYRot(hangYaw);
        froggy.yBodyRot = hangYaw;
        froggy.yHeadRot = hangYaw;
        froggy.getNavigation().stop();
    }

    private void unhang() {
        if (froggy.isNoGravity()) froggy.setNoGravity(false);
        if (froggy.getFroggyState() == FroggyState.CEILING_HANG) froggy.setFroggyState(FroggyState.IDLE);
    }

    private void go(BlockPos to) {
        froggy.getNavigation().moveTo(to.getX() + 0.5D, to.getY(), to.getZ() + 0.5D, FroggyCombatGoal.huntSpeed() * 0.85D);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    // ---------------------------------------------------------------- onde esperar

    /**
     * Casa (baú, cama, bancada): o canto do teto mais escuro a 2-6 blocos do lugar,
     * num cômodo de 3 de altura ou mais (fica acima da cabeça dela). Caverna, ou
     * casa sem canto que sirva: o canto mais escuro do chão perto do lugar.
     */
    private void choose(PreyStudy.Place pl) {
        Level level = froggy.level();
        hang = null;
        stand = null;
        if (pl.kind != PreyStudy.Kind.MINE && FroggyConfig.ai().real) findCorner(level, pl.pos);
        if (stand == null) findNook(level, pl.pos);
    }

    private void findCorner(Level level, BlockPos around) {
        double bestScore = -1.0E9D;
        for (BlockPos c : BlockPos.betweenClosed(around.offset(-6, -1, -6), around.offset(6, 5, 6))) {
            // c: o ar logo abaixo do teto
            if (!air(level, c) || !solid(level, c.above())) continue;
            if (!air(level, c.below()) || !air(level, c.below(2))) continue;
            int height = 3;
            while (height <= 5 && air(level, c.below(height))) height++;
            if (height > 5 || !solid(level, c.below(height))) continue;
            Direction wx = solid(level, c.east()) ? Direction.EAST : solid(level, c.west()) ? Direction.WEST : null;
            Direction wz = solid(level, c.south()) ? Direction.SOUTH : solid(level, c.north()) ? Direction.NORTH : null;
            if (wx == null || wz == null) continue;
            double d = Math.sqrt(c.distSqr(around));
            if (d < 1.5D || d > 7.0D) continue;
            int light = level.getMaxLocalRawBrightness(c);
            double score = 10.0D - Math.abs(d - 4.0D) - light * 0.4D + (height >= 4 ? 1.0D : 0.0D);
            if (score <= bestScore) continue;
            BlockPos floor = c.below(height - 1);
            if (!reaches(floor)) continue;
            bestScore = score;
            stand = Vec3.atBottomCenterOf(floor);
            hang = new Vec3(c.getX() + 0.5D, c.getY() + 1.0D - HANG_DROP, c.getZ() + 0.5D);
            // de costas pro canto, olhando pro meio do cômodo
            double ox = -wx.getStepX(), oz = -wz.getStepZ();
            hangYaw = (float) (Mth.atan2(oz, ox) * Mth.RAD_TO_DEG) - 90.0F;
        }
    }

    /** O lugar em pé mais escuro e mais "canto" (paredes em volta) a 2-8 blocos. */
    private void findNook(Level level, BlockPos around) {
        double bestScore = -1.0E9D;
        for (BlockPos f : BlockPos.betweenClosed(around.offset(-8, -3, -8), around.offset(8, 3, 8))) {
            if (!air(level, f) || !air(level, f.above()) || !solid(level, f.below())) continue;
            double d = Math.sqrt(f.distSqr(around));
            if (d < 2.0D || d > 8.0D) continue;
            int walls = 0;
            for (Direction dir : Direction.Plane.HORIZONTAL) if (solid(level, f.relative(dir))) walls++;
            int light = level.getMaxLocalRawBrightness(f);
            double score = walls * 1.5D - light * 0.6D - Math.abs(d - 5.0D) * 0.3D;
            if (score <= bestScore) continue;
            if (!reaches(f)) continue;
            bestScore = score;
            stand = Vec3.atBottomCenterOf(f);
        }
    }

    private boolean reaches(BlockPos feet) {
        net.minecraft.world.level.pathfinder.Path path = froggy.getNavigation().createPath(feet, 0);
        return path != null && path.canReach();
    }

    private static boolean air(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        return st.getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    private static boolean solid(Level level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)
                || level.getBlockState(pos).isFaceSturdy(level, pos, Direction.DOWN);
    }
}
