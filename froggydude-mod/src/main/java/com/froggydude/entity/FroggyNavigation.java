package com.froggydude.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Navegação de aranha (sobe parede), igual à WallClimberNavigation do jogo,
 * com estas diferenças (as duas primeiras faziam ele "atacar do lado errado"):
 *
 * 1. A do jogo guarda o bloco do alvo do último moveTo e, mesmo depois de
 *    stop(), continua mandando o corpo andar até lá todo tick. Em cima da
 *    vítima (esmagando, devorando, arrancando o braço) ele ia andando pro
 *    canto do bloco onde ela estava, e como a distância é corrigida só pra
 *    frente/trás, ele ficava girando em volta dela. Aqui stop() esquece o alvo.
 * 2. A do jogo mira o CANTO do bloco (x e z inteiros). Aqui mira o meio, então
 *    ele sobe a torre pelo meio da face, não escorregando pra quina.
 * 3. (v0.3.6) Pensa mais longe e melhor: procura caminho num raio de 80 blocos
 *    (o atributo é 48), testa 4x mais rotas antes de desistir, abre porta de
 *    madeira e desvia de onde já se machucou (FroggyNodeEvaluator).
 */
public class FroggyNavigation extends GroundPathNavigation {

    /** Bloco do alvo quando o caminho não chega lá (torre, parede): vai reto e sobe. */
    @Nullable
    private BlockPos climbTarget;

    public FroggyNavigation(Mob mob, Level level) {
        super(mob, level);
        this.setMaxVisitedNodesMultiplier(4.0F);
        this.setCanOpenDoors(true);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new FroggyNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, Math.max(followRange, 80.0F));
    }

    @Override
    public Path createPath(BlockPos pos, int accuracy) {
        this.climbTarget = pos;
        return super.createPath(pos, accuracy);
    }

    @Override
    public Path createPath(Entity entity, int accuracy) {
        this.climbTarget = entity.blockPosition();
        return super.createPath(entity, accuracy);
    }

    @Override
    public boolean moveTo(Entity entity, double speed) {
        Path path = this.createPath(entity, 0);
        if (path != null) {
            return this.moveTo(path, speed);
        }
        this.climbTarget = entity.blockPosition();
        this.speedModifier = speed;
        return true;
    }

    @Override
    public void stop() {
        super.stop();
        this.climbTarget = null;
    }

    @Override
    public void tick() {
        if (!this.isDone()) {
            super.tick();
            return;
        }
        if (this.climbTarget == null) return;
        double reach = Math.max(this.mob.getBbWidth(), 1.0D);
        double cx = this.climbTarget.getX() + 0.5D;
        double cz = this.climbTarget.getZ() + 0.5D;
        double dx = cx - this.mob.getX();
        double dz = cz - this.mob.getZ();
        boolean arrived = this.climbTarget.closerToCenterThan(this.mob.position(), reach)
                || (this.mob.getY() > this.climbTarget.getY() && dx * dx + dz * dz < reach * reach);
        if (arrived) {
            this.climbTarget = null;
            return;
        }
        this.mob.getMoveControl().setWantedPosition(cx, this.climbTarget.getY(), cz, this.speedModifier);
    }
}
