package com.becomelm.mixin;

import com.becomelm.LmWallClimb;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (v0.3.5) De 4, encostado de frente numa parede, a parede vira "escada" (LmWallClimb): o vanilla cuida de
 * escorregar devagar e de grudar agachado, e a animação de subir entra sozinha.
 *
 * A velocidade de subida entra no começo do travel, depois de o jogo ler os botões desse tick: no evento do
 * tick do jogador o "pra frente" ainda era o do tick anterior, e soltar levava ele 1,2 bloco a mais pra cima.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityWallClimbMixin {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void becomelm$wallClimb(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && LmWallClimb.onWall(p)) cir.setReturnValue(true);
    }

    /** (v0.3.9) Em cima da lava: o pé fica na superfície (como o strider), pra galopar por cima dela (LmLava). */
    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void becomelm$lavaWalk(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && !p.isSpectator() && fluid.is(FluidTags.LAVA)) cir.setReturnValue(true);
    }

    /** Quem move o jogador é o cliente dele: a subida e a beirada só no próprio jogador. */
    @Inject(method = "travel", at = @At("HEAD"))
    private void becomelm$wallClimbMove(Vec3 travel, CallbackInfo ci) {
        if ((Object) this instanceof Player p && p.isLocalPlayer()) LmWallClimb.move(p);
    }
}
