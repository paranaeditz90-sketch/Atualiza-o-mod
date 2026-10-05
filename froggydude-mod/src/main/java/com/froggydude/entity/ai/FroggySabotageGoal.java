package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.world.FroggyWorldData;
import com.froggydude.world.ManhuntMode;
import com.froggydude.world.Sabotage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Atrasar quem quer zerar (manhunt speedrun): longe da vítima, ele vai até o
 * portal do Nether que ela está fazendo (ou já acendeu) e come a obsidiana,
 * bloco por bloco. Se a vítima chega perto, larga o portal e ataca.
 */
public class FroggySabotageGoal extends Goal {

    /** Mais perto que isso de alguém, ele luta em vez de sabotar. */
    private static final double FIGHT_RANGE = 10.0D;
    private static final int TICKS_PER_BLOCK = 16;

    private final FroggydudeEntity froggy;
    @Nullable
    private BlockPos block;
    private int progress;
    private int timeout;
    private int ate;

    public FroggySabotageGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (froggy.tickCount % 20 != 0 || !(froggy.level() instanceof ServerLevel level)) return false;
        if (!allowed(level)) return false;
        block = Sabotage.nearest(level, froggy.position(), 64.0D);
        return block != null;
    }

    @Override
    public boolean canContinueToUse() {
        return block != null && timeout > 0 && froggy.level() instanceof ServerLevel level && allowed(level);
    }

    /** Speedrun rolando, ele solto, sem luta por perto e o mundo deixa mob quebrar bloco. */
    private boolean allowed(ServerLevel level) {
        FroggyWorldData data = FroggyWorldData.get(level);
        if (!data.manhuntActive || !data.released || data.manhuntMode != ManhuntMode.SPEEDRUN) return false;
        if (froggy.isHolding() || froggy.isFrenzyActive()) return false;
        FroggyState st = froggy.getFroggyState();
        if (st != FroggyState.CHASE && st != FroggyState.IDLE && st != FroggyState.FEEDING) return false;
        if (!ForgeEventFactory.getMobGriefingEvent(level, froggy)) return false;
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && !p.isCreative() && p.distanceTo(froggy) < FIGHT_RANGE) return false;
        }
        return true;
    }

    @Override
    public void start() {
        timeout = 900;
        progress = 0;
        ate = 0;
        froggy.brainLog("indo comer o portal do Nether em " + block.toShortString() + " (atrasar quem quer zerar)");
    }

    @Override
    public void stop() {
        froggy.level().destroyBlockProgress(froggy.getId(), block == null ? BlockPos.ZERO : block, -1);
        if (froggy.getFroggyState() == FroggyState.FEEDING) froggy.setFroggyState(FroggyState.CHASE);
        if (ate > 0) froggy.brainLog("comi " + ate + " bloco(s) do portal");
        block = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        timeout--;
        ServerLevel level = (ServerLevel) froggy.level();
        if (block == null || !Sabotage.isPortalBlock(level.getBlockState(block))) {
            block = Sabotage.nearest(level, froggy.position(), 64.0D);
            progress = 0;
            if (block == null) {
                froggy.speak(VoiceSituation.CHEWING);
                return;
            }
        }
        Vec3 c = Vec3.atCenterOf(block);
        double d = froggy.position().add(0, 1.0D, 0).distanceTo(c);
        if (d > 3.2D) {
            if (froggy.getFroggyState() == FroggyState.FEEDING) froggy.setFroggyState(FroggyState.CHASE);
            if (froggy.tickCount % 10 == 0) froggy.getNavigation().moveTo(c.x, block.getY(), c.z, 1.6D);
            froggy.getLookControl().setLookAt(c);
            return;
        }
        // na frente do portal: morde e arranca a obsidiana com os dentes
        froggy.getNavigation().stop();
        froggy.faceDirection(c.x - froggy.getX(), c.z - froggy.getZ());
        if (froggy.getFroggyState() != FroggyState.FEEDING) froggy.setFroggyState(FroggyState.FEEDING);
        progress++;
        level.destroyBlockProgress(froggy.getId(), block, Math.min(9, progress * 10 / TICKS_PER_BLOCK));
        if (progress % 6 == 0) froggy.onChew();
        if (progress >= TICKS_PER_BLOCK) {
            if (ForgeEventFactory.onEntityDestroyBlock(froggy, block, level.getBlockState(block))) {
                level.destroyBlock(block, false, froggy); // comeu: a obsidiana não cai
                ate++;
            }
            level.destroyBlockProgress(froggy.getId(), block, -1);
            block = null;
            progress = 0;
        }
    }
}
