package com.froggydude.network;

import java.util.Random;

/**
 * Guarda, no cliente, o tremor de câmera e o "derrubado e preso" pendentes.
 * Sem nenhuma classe de cliente do Minecraft aqui, então é seguro carregar
 * no servidor também (o pacote só chama isso no cliente).
 */
public final class ClientShakeHandler {

    private static final Random RANDOM = new Random();

    private static float intensity = 0F;
    private static int shakeTicksLeft = 0;
    private static int shakeTotal = 1;
    private static final float[] PHASES = new float[3];

    private static int pinTicksLeft = 0;
    private static int pinnerId = -1;

    private ClientShakeHandler() {
    }

    public static void start(float newIntensity, int durationTicks) {
        // um tremor novo mais fraco não apaga um mais forte que ainda está rolando
        if (shakeTicksLeft > 0 && getShakeAmount(0F) > newIntensity) return;
        intensity = newIntensity;
        shakeTicksLeft = durationTicks;
        shakeTotal = Math.max(1, durationTicks);
        for (int i = 0; i < PHASES.length; i++) {
            PHASES[i] = RANDOM.nextFloat() * 6.2832F;
        }
    }

    public static void pin(int ticks, int pinner) {
        pinTicksLeft = ticks;
        pinnerId = pinner;
    }

    public static void release() {
        pinTicksLeft = 0;
        pinnerId = -1;
    }

    public static boolean isPinned() {
        return pinTicksLeft > 0;
    }

    /** Derrubado no chão, com o Froggy em cima (não só segurado pela língua). */
    public static boolean isKnockedDown() {
        return pinTicksLeft > 0 && pinnerId >= 0;
    }

    /** Id da entidade do Froggy que está em cima (ou -1). */
    public static int getPinnerId() {
        return pinnerId;
    }

    public static void clientTick() {
        if (shakeTicksLeft > 0) shakeTicksLeft--;
        if (pinTicksLeft > 0 && --pinTicksLeft == 0) pinnerId = -1;
    }

    /** Força do tremor agora: começa forte e some suave até zero. */
    public static float getShakeAmount(float partialTick) {
        if (shakeTicksLeft <= 0) return 0F;
        float left = Math.max(0F, (shakeTicksLeft - partialTick) / shakeTotal);
        return intensity * left * left;
    }

    /**
     * Ruído suave (soma de senos com fases sorteadas a cada tremor), entre -1
     * e 1. Cada eixo usa uma fase e uma frequência diferente, então a câmera
     * treme de verdade em vez de só inclinar pro lado e voltar.
     */
    public static float noise(int axis, float time) {
        float p = PHASES[axis];
        return (float) (Math.sin(time * (2.1D + axis * 0.7D) + p) * 0.6D
                + Math.sin(time * (5.3D + axis * 1.1D) + p * 1.7D) * 0.4D);
    }
}
