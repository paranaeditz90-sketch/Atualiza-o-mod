package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModParticles;
import com.froggydude.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Vitória-régia (o fim do vídeo do LM, 95,6-98,9 s): na água ele não nada - vai de
 * folha em folha num salto de sapo rasante (o corpo deita no ar, braços pra frente),
 * pousa em pé com uma nuvem bege embaixo e já sai no próximo, um salto a cada 0,3 s.
 * Só quando tem folha à frente, no rumo de quem ele caça (ou de pra onde está indo);
 * o último salto é pra terra firme, se ela estiver no alcance.
 *
 * O salto é conduzido aqui (a cada tick o movimento leva ele ao ponto certo do arco),
 * não pela física: saindo da água a física joga ele pra lugar nenhum.
 */
public class FroggyPadHopGoal extends Goal {

    /** Ticks no ar; o ciclo (FroggyState.PAD_HOP, animação "pad_hop") é AIR + 1 em pé na folha. */
    static final int AIR = 5;
    private static final double MIN_HOP = 1.6D;
    private static final double MAX_HOP = 5.5D;
    /** Altura do arco em blocos: rasante, quase raspando a água. */
    private static final double ARC = 0.55D;
    /** Cone à frente (cos de 55°): folha fora do rumo não serve. */
    private static final double CONE = 0.57D;
    /** Perto assim de quem ele caça, quem cuida é o combate (o bote). */
    private static final double LET_COMBAT = 3.0D;

    private final FroggydudeEntity froggy;
    private Vec3 from = Vec3.ZERO;
    private Vec3 to = Vec3.ZERO;
    @Nullable
    private Vec3 next;
    private int t;

    public FroggyPadHopGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!froggy.isAlive() || froggy.isPassenger()) return false;
        FroggyState s = froggy.getFroggyState();
        if (s != FroggyState.IDLE && s != FroggyState.CHASE) return false;
        if (!overWater()) return false;
        Vec3 goal = goal();
        if (goal == null) return false;
        next = findLanding(goal);
        return next != null;
    }

    @Override
    public boolean canContinueToUse() {
        return froggy.isAlive() && froggy.getFroggyState() == FroggyState.PAD_HOP && next != null;
    }

    @Override
    public void start() {
        froggy.getNavigation().stop();
        froggy.setFroggyState(FroggyState.PAD_HOP);
        launch();
    }

    @Override
    public void stop() {
        froggy.setNoGravity(false);
        if (froggy.getFroggyState() == FroggyState.PAD_HOP) {
            froggy.setFroggyState(froggy.getTarget() != null ? FroggyState.CHASE : FroggyState.IDLE);
        }
        next = null;
    }

    @Override
    public void tick() {
        froggy.getNavigation().stop();
        t++;
        if (t <= AIR) {
            double s = (double) t / AIR;
            Vec3 want = from.lerp(to, s).add(0, ARC * 4.0D * s * (1.0D - s), 0);
            // o movimento do tick leva exatamente até o ponto do arco (bate em parede, para)
            froggy.setDeltaMovement(want.subtract(froggy.position()));
            froggy.hasImpulse = true;
            if (t == AIR) land();
            return;
        }
        // em pé na folha (1 tick): o próximo salto, ou acabou a água
        froggy.setDeltaMovement(Vec3.ZERO);
        Vec3 goal = goal();
        next = goal == null || !overWater() ? null : findLanding(goal);
        if (next != null) launch();
    }

    private void launch() {
        from = froggy.position();
        to = next;
        t = 0;
        froggy.faceDirection(to.x - from.x, to.z - from.z);
        froggy.setNoGravity(true);
        froggy.setNoFallTicks(20);
        froggy.setDeltaMovement(Vec3.ZERO);
    }

    /** Pousou: a nuvem bege da folha (no vídeo ela fica bege mesmo em cima da água) e o barulho. */
    private void land() {
        froggy.setNoGravity(false);
        Level level = froggy.level();
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ModParticles.DUST_PUFF.get(), to.x, to.y + 0.06D, to.z, 9, 0.32D, 0.02D, 0.32D, 0.025D);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, to.x, to.y + 0.05D, to.z,
                    6, 0.3D, 0.02D, 0.3D, 0.1D);
        }
        level.playSound(null, to.x, to.y, to.z, ModSounds.GALLOP.get(), SoundSource.HOSTILE, 0.6F,
                0.9F + froggy.getRandom().nextFloat() * 0.2F);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.LILY_PAD_PLACE, SoundSource.HOSTILE, 0.7F, 0.8F);
    }

    private boolean overWater() {
        if (froggy.isInWater()) return true;
        return froggy.level().getBlockState(froggy.blockPosition()).is(Blocks.LILY_PAD);
    }

    /** Pra onde ele quer ir: quem ele caça, ou o fim do caminho que estava seguindo. */
    @Nullable
    private Vec3 goal() {
        LivingEntity target = froggy.getTarget();
        if (target != null && target.isAlive()) {
            if (froggy.distanceTo(target) < LET_COMBAT) return null;
            return target.position();
        }
        BlockPos end = froggy.getNavigation().getTargetPos();
        if (end != null && !froggy.getNavigation().isDone()) return Vec3.atBottomCenterOf(end);
        return null;
    }

    /**
     * A melhor folha (ou a margem) à frente: no cone do rumo, que encurte o caminho,
     * de preferência a uns 3,5 blocos e bem na linha.
     */
    @Nullable
    private Vec3 findLanding(Vec3 goal) {
        Vec3 p = froggy.position();
        double gx = goal.x - p.x, gz = goal.z - p.z;
        double dist = Math.sqrt(gx * gx + gz * gz);
        if (dist < 1.2D) return null;
        gx /= dist;
        gz /= dist;
        Level level = froggy.level();
        BlockPos base = froggy.blockPosition();
        int r = (int) Math.ceil(MAX_HOP);
        Vec3 best = null;
        double bestScore = -1.0E9D;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    q.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    BlockState st = level.getBlockState(q);
                    boolean pad = st.is(Blocks.LILY_PAD);
                    double top;
                    if (pad) {
                        top = q.getY() + 0.09375D;
                    } else if (dryGround(level, q, st)) {
                        top = q.getY() + 1.0D;
                    } else {
                        continue;
                    }
                    double lx = q.getX() + 0.5D, lz = q.getZ() + 0.5D;
                    double ex = lx - p.x, ez = lz - p.z;
                    double h = Math.sqrt(ex * ex + ez * ez);
                    if (h < MIN_HOP || h > MAX_HOP) continue;
                    double along = ex * gx + ez * gz;
                    if (along < h * CONE) continue;
                    double rx = goal.x - lx, rz = goal.z - lz;
                    if (Math.sqrt(rx * rx + rz * rz) > dist - 0.8D) continue;
                    if (!fits(level, lx, top, lz)) continue;
                    double side = Math.abs(ex * gz - ez * gx);
                    double score = along - 0.6D * side - 0.35D * Math.abs(h - 3.5D) + (pad ? 0.6D : 0.0D);
                    if (score > bestScore) {
                        bestScore = score;
                        best = new Vec3(lx, top, lz);
                    }
                }
            }
        }
        return best;
    }

    /** Margem: bloco firme em cima, sem água em cima dele. */
    private static boolean dryGround(Level level, BlockPos q, BlockState st) {
        if (!st.getFluidState().isEmpty() || !st.isFaceSturdy(level, q, Direction.UP)) return false;
        BlockPos up = q.above();
        return level.getFluidState(up).isEmpty() && !level.getBlockState(up).is(Blocks.LILY_PAD);
    }

    private boolean fits(Level level, double x, double y, double z) {
        float w = froggy.getBbWidth() * 0.5F;
        AABB box = new AABB(x - w, y + 0.01D, z - w, x + w, y + froggy.getBbHeight(), z + w);
        return level.noCollision(froggy, box);
    }
}
