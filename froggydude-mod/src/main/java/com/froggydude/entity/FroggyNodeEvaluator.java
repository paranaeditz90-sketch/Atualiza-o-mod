package com.froggydude.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Como ele enxerga o chão na hora de planejar o caminho: igual a qualquer mob,
 * mais o que ELE aprendeu - bloco onde já se machucou (lava escondida, TNT,
 * armadilha de flecha, cacto) vira "perigoso" e o caminho dá a volta
 * (custo alto, ver FroggydudeEntity: malus de DANGER_OTHER). Se não tiver outro
 * jeito, ainda passa: melhor arriscar do que ficar parado.
 */
public class FroggyNodeEvaluator extends WalkNodeEvaluator {

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z, Mob mob) {
        BlockPathTypes type = super.getBlockPathType(level, x, y, z, mob);
        if (type.getMalus() >= 0.0F && type != BlockPathTypes.DANGER_OTHER
                && mob instanceof FroggydudeEntity froggy && froggy.remembersDanger(x, y, z)) {
            return BlockPathTypes.DANGER_OTHER;
        }
        return type;
    }
}
