package com.becomelm;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * (v0.3.9, LM 03/11: "a da lava, que é a mesma de correr, levemente mais lento, sem queimar", nos dois mods) Tu anda
 * e galopa POR CIMA da lava, como o FroggyDude do vídeo (o pé fica na superfície: LivingEntityWallClimbMixin,
 * canStandOnFluid), sem pegar fogo e uns 15% mais devagar. A animação é a de sempre (o galope).
 */
public final class LmLava {

    private static final UUID SLOW_ID = UUID.fromString("a3d9c1e7-4b2f-4f86-9e15-7c0b2d8a6f41");
    private static final AttributeModifier SLOW =
            new AttributeModifier(SLOW_ID, "becomelm lava", -0.15D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private LmLava() {
    }

    /** Pisando na lava (em cima dela) ou dentro. */
    public static boolean onLava(Player p) {
        return p.isInLava() || p.level().getFluidState(BlockPos.containing(p.getX(), p.getY() - 0.05D, p.getZ()))
                .is(FluidTags.LAVA);
    }

    /** Servidor, todo tick: sem fogo e mais devagar em cima da lava. */
    public static void tick(Player p) {
        boolean on = !p.isSpectator() && onLava(p);
        if (on) p.clearFire();
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(SLOW_ID) != null;
        if (on && !has) speed.addTransientModifier(SLOW);
        else if (!on && has) speed.removeModifier(SLOW_ID);
    }
}
