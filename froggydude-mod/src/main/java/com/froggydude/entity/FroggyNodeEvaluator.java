package com.froggydude.entity;

import com.froggydude.entity.ai.FroggyDoorGoal;
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
 * Portão de cerca fechado e porta de ferro (se ele pode arrombar) contam como porta
 * de madeira: o caminho passa por eles e o FroggyDoorGoal abre/arromba no contato.
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
        // portão fechado ele abre, porta de ferro ele arromba (FroggyDoorGoal): pro caminho é porta
        if ((type == BlockPathTypes.FENCE || type == BlockPathTypes.DOOR_IRON_CLOSED) && mob instanceof FroggydudeEntity f
                && FroggyDoorGoal.passable(f, level, new BlockPos(x, y, z))) {
            type = BlockPathTypes.WALKABLE_DOOR;
        }
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
