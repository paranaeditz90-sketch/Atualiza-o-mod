package com.becomelm.mixin;

import com.becomelm.LmLava;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (v0.3.9, LM: "sem queimar") Em pé na lava (como o strider, o pé fica um pouco dentro dela) o jogo põe fogo todo tick,
 * no servidor e no cliente. Pisando nela, o jogador fica imune a fogo: sem chama na tela e sem dano (LmLava).
 */
@Mixin(Entity.class)
public abstract class EntityLavaFireMixin {

    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void becomelm$lavaFire(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && !p.isSpectator() && LmLava.onLava(p)) cir.setReturnValue(true);
    }
}
