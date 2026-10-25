package com.becomelm.mixin;

import com.becomelm.LmModes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A terra que o vanilla joga pra trás quando o jogador corre sai com 4x a velocidade
 * dele: no galope (2,5x mais rápido) ela voava em cima da câmera e virava um quadrado
 * marrom na tela. Galopando, quem levanta poeira e terra é o próprio galope (as patadas).
 */
@Mixin(Entity.class)
public abstract class EntitySprintDirtMixin {

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void becomelm$noSprintDirt(CallbackInfo ci) {
        if ((Object) this instanceof Player p && p.level().isClientSide() && LmModes.get(p).fast()) {
            ci.cancel();
        }
    }
}
