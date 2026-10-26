package com.becomelm.mixin;

import com.becomelm.LmModes;
import com.becomelm.LmMovement;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Correndo no chão o vanilla cobra a fome pela distância (0,1 por bloco). O galope anda 2,5x
 * mais que um jogador correndo e a barra sumia (LM: "a fome é infinita"). Galopando (e na
 * fase 2) paga no máximo o que um jogador correndo normal pagaria no mesmo tempo.
 */
@Mixin(Player.class)
public abstract class PlayerGallopHungerMixin {

    /** A 4ª cobrança de checkMovementStatistics é a de correr no chão (1.20.1). */
    @ModifyArg(method = "checkMovementStatistics", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V", ordinal = 3))
    private float becomelm$gallopHunger(float exhaustion) {
        if (LmModes.get((Player) (Object) this).fast()) return Math.min(exhaustion, LmMovement.SPRINT_HUNGER_TICK);
        return exhaustion;
    }
}
