package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ClientShakeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Efeitos de tela quando o Froggy acerta o jogador: tremor de câmera e,
 * quando ele prende o jogador no chão, trava os controles e inclina a tela.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientShakeHandler.clientTick();
        }
    }

    /** Preso: ignora andar, pular e agachar. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientShakeHandler.isPinned()) return;
        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        input.forwardImpulse = 0F;
        input.leftImpulse = 0F;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (Minecraft.getInstance().player == null) return;
        RandomSource rnd = Minecraft.getInstance().player.getRandom();

        float amount = ClientShakeHandler.getShakeAmount();
        if (amount > 0F) {
            event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * amount * 8F);
            event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * amount * 8F);
            event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5F) * amount * 10F);
        }

        float pin = ClientShakeHandler.getPinBlend();
        if (pin > 0F) {
            // tela deitada de lado, com o rosto meio pro chão
            event.setRoll(event.getRoll() + 16F * pin);
            event.setPitch(event.getPitch() + 8F * pin);
        }
    }
}
