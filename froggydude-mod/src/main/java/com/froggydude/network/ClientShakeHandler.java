package com.froggydude.network;

/**
 * Guarda, no cliente, o tremor de câmera e o "preso no chão" pendentes.
 * Sem nenhuma classe de cliente do Minecraft aqui, então é seguro carregar
 * no servidor também (o pacote só chama isso no cliente).
 */
public final class ClientShakeHandler {

    private static float intensity = 0F;
    private static int shakeTicksLeft = 0;
    private static int pinTicksLeft = 0;
    private static int pinTotal = 1;

    private ClientShakeHandler() {
    }

    public static void start(float newIntensity, int durationTicks) {
        intensity = newIntensity;
        shakeTicksLeft = durationTicks;
    }

    public static void pin(int ticks) {
        pinTicksLeft = ticks;
        pinTotal = Math.max(1, ticks);
    }

    public static boolean isPinned() {
        return pinTicksLeft > 0;
    }

    public static void clientTick() {
        if (shakeTicksLeft > 0) shakeTicksLeft--;
        if (pinTicksLeft > 0) pinTicksLeft--;
    }

    /** Força do tremor agora (cai até zero). */
    public static float getShakeAmount() {
        if (shakeTicksLeft <= 0) return 0F;
        return intensity * (shakeTicksLeft / 20F);
    }

    /** 0 a 1: quão "deitado no chão" a câmera está (entra e sai suave). */
    public static float getPinBlend() {
        if (pinTicksLeft <= 0) return 0F;
        float in = Math.min(1F, (pinTotal - pinTicksLeft) / 5F);
        float out = Math.min(1F, pinTicksLeft / 8F);
        return Math.min(in, out);
    }
}
