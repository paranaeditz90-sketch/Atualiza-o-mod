package com.becomelm;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * Velocidade e passo do FroggyDude. Roda igual nos dois lados (o cliente de quem anda
 * não espera o servidor pra acelerar; o servidor confirma o mesmo valor).
 *
 * Galope correndo: ~14 blocos/s (o FroggyDude caçando, medido nos vídeos, faz ~15);
 * galope sem correr: ~6,5 b/s; em pé: velocidade normal de jogador. De quatro ele
 * sobe 1 bloco sem pular (o galope não trava em cada degrau).
 */
public final class LmMovement {

    private static final UUID SPEED_ID = UUID.fromString("5b3c41a2-6d0e-4c8e-9a51-2f7d0c6b8e11");
    private static final UUID STEP_ID = UUID.fromString("a1f0c2d4-3b5e-4f60-8a71-9c2b3d4e5f62");

    /** Multiplica a velocidade toda (o vanilla já dá +30% correndo). */
    static final double TROT = 0.5D;
    static final double GALLOP = 1.5D;
    /** Soma no passo (o normal é 0,6). */
    static final double STEP = 0.5D;

    private LmMovement() {
    }

    public static void tick(Player player, LmMode mode) {
        double speed = mode == LmMode.GALLOP ? (player.isSprinting() ? GALLOP : TROT) : 0D;
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "BecomeLM galope", speed, AttributeModifier.Operation.MULTIPLY_TOTAL);
        set(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP_ID, "BecomeLM passo",
                mode == LmMode.GALLOP ? STEP : 0D, AttributeModifier.Operation.ADDITION);
    }

    private static void set(Player player, Attribute attribute, UUID id, String name, double amount,
                            AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier now = inst.getModifier(id);
        if (now != null && now.getAmount() == amount) return;
        if (now != null) inst.removeModifier(id);
        if (amount != 0D) inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }
}
