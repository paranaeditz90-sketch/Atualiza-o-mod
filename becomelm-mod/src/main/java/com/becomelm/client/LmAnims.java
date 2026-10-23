package com.becomelm.client;

import com.becomelm.LmMode;
import com.becomelm.LmModes;
import com.becomelm.LmMove;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.RawAnimation;

/** Qual animação do FroggyDude o jogador faz agora, e em que velocidade. */
final class LmAnims {

    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    static final RawAnimation CROUCH_IDLE = RawAnimation.begin().thenLoop("crouch_idle");
    static final RawAnimation CLIMB = RawAnimation.begin().thenLoop("climb");
    static final RawAnimation HOP = RawAnimation.begin().thenLoop("pad_hop");
    /** Cópias dos pulos do Froggydude que já começam no ar (tools/make_anims.py). */
    static final RawAnimation LEAP = RawAnimation.begin().thenPlayAndHold("leap_air");
    static final RawAnimation SKY = RawAnimation.begin().thenPlayAndHold("sky_air");

    /** Abaixo disso (blocos por tick) ele está parado. */
    private static final double MOVING = 0.02D;

    private LmAnims() {
    }

    static RawAnimation pick(AbstractClientPlayer p) {
        LmMode mode = LmModes.get(p);
        LmMove move = LmMoves.airborne(p);
        boolean moving = groundSpeed(p) > MOVING;
        if (p.onClimbable() && !p.onGround()) return CLIMB;
        if (move == LmMove.SKY) return SKY;
        if (mode == LmMode.GALLOP) {
            if (move == LmMove.LEAP) return LEAP;
            if (move == LmMove.HOP) return HOP;
            // segurando o agachar (carregando o pulo alto) ele fica baixinho, parado
            if (moving && !p.isShiftKeyDown()) return RUN;
            return CROUCH_IDLE;
        }
        return moving ? WALK : IDLE;
    }

    /**
     * Galope: a cadência quase não muda, o que cresce é a passada (igual ao Froggydude:
     * a ~0,7 b/t a velocidade fica em ~1, passada de ~3,7 blocos). Em pé: o passo
     * acompanha a velocidade.
     */
    static double speed(AbstractClientPlayer p) {
        LmMode mode = LmModes.get(p);
        double v = groundSpeed(p);
        if (v <= MOVING || !p.onGround()) return 1D;
        if (mode == LmMode.GALLOP) return p.isShiftKeyDown() ? 1D : runSpeed(v);
        return Mth.clamp(v * 7.0D, 0.5D, 2.0D);
    }

    static double runSpeed(double v) {
        return Mth.clamp(0.3D + v * 0.95D, 0.45D, 1.3D);
    }

    /** Quanto andou no chão no último tick (serve pra todos os jogadores, até os de longe). */
    static double groundSpeed(AbstractClientPlayer p) {
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
