package com.froggydude.entity.ai;

import com.froggydude.entity.BloodFx;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModSounds;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * O banquete (v0.7, LM): matou, o corpo se desmontou em pedaços com física
 * (client.pieces) e ele deita por cima em "flexão", com a boca nos pedaços,
 * "comendo" sem engolir nada - como o FroggyDude comendo o jogador despedaçado
 * ("WHAT HAPPENED TO ME???", 2:50) e o aldeão no vídeo do Grox (RABIES 0:57).
 * 3 a 7 s, no ritmo da raiva e da fome (FroggydudeEntity#feastRate): o loop do
 * nhum nhum NHOM no cliente, a flexão e as mordidas aqui andam juntos. Se
 * apanhar, larga e revida (FroggydudeEntity#hurt).
 */
public class FroggyFeastGoal extends Goal {

    /** A boca dele fica 1 bloco na frente dos pés (rig da flexão): em cima do monte. */
    private static final double MOUTH = 1.0D;
    /** Até ~4 s andando até os pedaços (o corpo deitado do golpe final fica um pouco longe). */
    private static final int WALK_LIMIT = 80;

    private final FroggydudeEntity froggy;
    private int walked;
    private boolean lying;
    private double phase;
    private boolean finished;
    private int bites;

    public FroggyFeastGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return froggy.hasFeast() && froggy.isAlive() && !busy();
    }

    private boolean busy() {
        FroggyState s = froggy.getFroggyState();
        return s.isCombatAction() || s == FroggyState.CONTORTING || s == FroggyState.ROAR
                || s == FroggyState.FINISHER || s == FroggyState.FLUNG || s == FroggyState.ARM_RIP
                || s == FroggyState.ARM_EAT || s == FroggyState.FEEDING;
    }

    @Override
    public boolean canContinueToUse() {
        if (!froggy.isAlive() || !froggy.hasFeast()) return false;
        return lying ? froggy.getFroggyState() == FroggyState.FEAST : walked < WALK_LIMIT;
    }

    private static void debug(String msg) {
        if (Boolean.getBoolean("froggydude.debug")) System.out.println("[FROGGYDEBUG] banquete: " + msg);
    }

    @Override
    public void start() {
        debug("começou, ritmo " + String.format("%.2f", froggy.getFeastRate()) + " em " + froggy.getFeastSpot());
        walked = 0;
        lying = false;
        phase = 0.6D;
        finished = false;
        bites = 0;
    }

    @Override
    public void tick() {
        Vec3 at = froggy.getFeastSpot();
        if (at == null) return;
        Vec3 dir = froggy.position().subtract(at).multiply(1, 0, 1);
        double d = dir.length();
        if (!lying) {
            if (d > MOUTH + 0.5D && walked++ < WALK_LIMIT) {
                froggy.getNavigation().moveTo(at.x, at.y, at.z, 1.0D);
                froggy.getLookControl().setLookAt(at.x, at.y + 0.2D, at.z);
                return;
            }
            lying = true;
            froggy.getNavigation().stop();
            froggy.setFroggyState(FroggyState.FEAST);
            debug("deitou depois de " + walked + " ticks andando (dist " + String.format("%.2f", d) + ")");
        }
        froggy.getNavigation().stop();
        froggy.setDeltaMovement(0, froggy.getDeltaMovement().y, 0);
        // a boca em cima do monte
        if (d > 1.0E-3D && Math.abs(d - MOUTH) > 0.08D) {
            Vec3 spot = at.add(dir.scale(MOUTH / d));
            froggy.setPos(spot.x, froggy.getY(), spot.z);
        }
        if (froggy.getStateTicks() % 10 == 0) froggy.faceDirection(at.x - froggy.getX(), at.z - froggy.getZ());

        phase += froggy.getFeastRate() * FroggydudeEntity.FEAST_BITES_PER_TICK;
        if (phase >= 1.0D) {
            phase -= 1.0D;
            bite(at);
        }
        if (!froggy.tickFeast()) finished = true;
    }

    /** Uma mordida: dente, o molhado do sangue (sem o pacote de voz), um respingo, um tico de vida. */
    private void bite(Vec3 at) {
        float rate = froggy.getFeastRate();
        bites++;
        froggy.playSound(ModSounds.FEAST_BITE.get(), 0.75F, (0.85F + froggy.getRandom().nextFloat() * 0.2F) * rate);
        if (bites % 2 == 0 || froggy.getRandom().nextInt(3) == 0) {
            froggy.playSound(ModSounds.FEAST_SQUISH.get(), 0.9F, (0.8F + froggy.getRandom().nextFloat() * 0.25F) * rate);
        }
        froggy.heal(froggy.getMaxHealth() * 0.006F);
        Vec3 mouth = at.add(0, 0.25D, 0);
        BloodFx.spray(froggy.level(), mouth, new Vec3(froggy.getRandom().nextGaussian() * 0.3D, 1, froggy.getRandom().nextGaussian() * 0.3D),
                2 + froggy.getRandom().nextInt(2), 0.16F, froggy.getFeastColor());
        if (bites % 4 == 0) froggy.stainMouth(froggy.getFeastColor(), 0.05F);
    }

    @Override
    public void stop() {
        debug("acabou: terminou=" + finished + " deitado=" + lying + " mordidas=" + bites + " estado=" + froggy.getFroggyState());
        if (froggy.getFroggyState() == FroggyState.FEAST) {
            froggy.setFroggyState(froggy.getTarget() != null ? FroggyState.CHASE : FroggyState.IDLE);
        }
        froggy.endFeast(finished);
    }
}
