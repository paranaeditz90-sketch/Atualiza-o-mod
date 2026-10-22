package com.froggydude.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Como ele enxerga o chão na hora de planejar o caminho: igual a qualquer mob,
 * mais o que ELE aprendeu - bloco onde já se machucou (lava escondida, TNT,
 * armadilha de flecha, cacto) vira "perigoso" e o caminho dá a volta
 * (custo alto, ver FroggydudeEntity: malus de DANGER_OTHER). Se não tiver outro
 * jeito, ainda passa: melhor arriscar do que ficar parado.
 *
 * IA Real atravessando lava (FroggydudeEntity#setLavaWalking): ele anda POR CIMA
 * dela (como o strider), então o ar em cima de lava parada vira chão perigoso
 * (DANGER_FIRE, com custo) e dentro da lava é parede - sem isso o caminho descia
 * pro fundo da lava e ele afundava.
 */
public class FroggyNodeEvaluator extends WalkNodeEvaluator {

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z, Mob mob) {
        BlockPathTypes type = super.getBlockPathType(level, x, y, z, mob);
        if (mob instanceof FroggydudeEntity f && f.isLavaWalking()) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).is(FluidTags.LAVA)) return BlockPathTypes.BLOCKED;
            FluidState below = level.getFluidState(pos.below());
            if (type == BlockPathTypes.OPEN && below.is(FluidTags.LAVA) && below.isSource()) return BlockPathTypes.DANGER_FIRE;
        }
        if (type.getMalus() >= 0.0F && type != BlockPathTypes.DANGER_OTHER
                && mob instanceof FroggydudeEntity froggy && froggy.remembersDanger(x, y, z)) {
            return BlockPathTypes.DANGER_OTHER;
        }
        return type;
    }
}
