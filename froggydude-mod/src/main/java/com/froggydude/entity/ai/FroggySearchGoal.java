package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.mind.Belief;
import com.froggydude.mind.Mind;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * (v0.7.68, a Mente) Procurar: quando ele está pensando em alguém (perdeu de vista, ouviu um barulho, teve uma pista) mas
 * não está vendo, ele vai até o lugar mais provável da crença (Belief), para, olha em volta (o que ele olha e não tem
 * ninguém sai da lista), e vai pro próximo. Acabou a lista ou o tempo: desiste (Mind.giveUp), com raiva. Achou: o combate
 * assume (ele passou a perceber).
 */
public class FroggySearchGoal extends Goal {

    private final FroggydudeEntity froggy;
    @Nullable
    private Vec3 spot;
    private int repath;
    /** Olhando em volta no lugar (ticks que faltam). */
    private int scan;
    private float scanBase;

    public FroggySearchGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean busy() {
        FroggyState s = froggy.getFroggyState();
        return s != FroggyState.CHASE && s != FroggyState.IDLE || froggy.isHolding() || froggy.inSkyRage();
    }

    @Override
    public boolean canUse() {
        if (froggy.tickCount % 5 != 0 || busy()) return false;
        Player p = froggy.mind().subject();
        if (p == null || froggy.mind().perceives(p) || froggy.mind().searchSince() < 0) return false;
        if (froggy.getTarget() != null && froggy.getTarget() != p) return false;
        return !froggy.mind().belief().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        Player p = froggy.mind().subject();
        return p != null && !froggy.mind().perceives(p) && !busy() && froggy.mind().searchSince() >= 0;
    }

    @Override
    public void start() {
        spot = null;
        repath = 0;
        scan = 0;
        if (froggy.getFroggyState() == FroggyState.IDLE) froggy.setFroggyState(FroggyState.CHASE);
    }

    @Override
    public void tick() {
        Mind m = froggy.mind();
        if (m.shouldGiveUp()) {
            froggy.ribbit(2.0F); // frustrado
            m.giveUp();
            return;
        }
        if (scan > 0) {
            scan--;
            froggy.getNavigation().stop();
            // olha pros lados, devagar: esquerda, direita, atrás
            float a = scanBase + (scan > 26 ? -70F : scan > 13 ? 70F : 180F);
            double rad = Math.toRadians(a);
            froggy.getLookControl().setLookAt(froggy.getX() - Math.sin(rad) * 6, froggy.getEyeY(), froggy.getZ() + Math.cos(rad) * 6,
                    20.0F, 20.0F);
            return;
        }
        if (spot != null && froggy.position().distanceTo(spot) < 2.2D) {
            // chegou: para e olha em volta
            scan = 40;
            scanBase = froggy.getYHeadRot();
            spot = null;
            return;
        }
        if (spot == null || --repath <= 0) {
            Belief.Cell c = m.belief().best(froggy.position());
            if (c == null) return;
            spot = c.center();
            repath = 20;
            // perdeu agora: corre; procurando faz tempo: anda olhando
            boolean fresh = froggy.level().getGameTime() - m.searchSince() < 200;
            froggy.getNavigation().moveTo(spot.x, spot.y, spot.z, fresh ? 1.6D : 1.0D);
            froggy.setTracking(fresh || froggy.getTarget() != null); // de quatro, farejando
        }
        if (spot != null) froggy.getLookControl().setLookAt(spot.x, spot.y + 1.2D, spot.z, 20.0F, 20.0F);
    }

    @Override
    public void stop() {
        froggy.setTracking(false);
        spot = null;
        scan = 0;
        froggy.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
