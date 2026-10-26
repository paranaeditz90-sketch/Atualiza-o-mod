package com.becomelm.client;

import com.becomelm.LmAttack;
import com.becomelm.LmAttacks;
import com.becomelm.LmFinish;
import com.becomelm.LmMode;
import com.becomelm.LmModes;
import com.becomelm.LmMove;
import com.becomelm.LmTransform;
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
    /** Transformação (as do Froggydude: a contorção acertada com o vídeo). */
    static final RawAnimation CONTORT = RawAnimation.begin().thenPlayAndHold("contort");
    static final RawAnimation CONTORT_HOLD = RawAnimation.begin().thenLoop("contort_hold");
    static final RawAnimation ROAR = RawAnimation.begin().thenPlayAndHold("roar");
    /** Montado socando (0,5 s = 2 socos: 1 a cada 0,25 s, como no vídeo) e as mordidas. */
    static final RawAnimation SMASH = RawAnimation.begin().thenLoop("sky_smash");
    static final RawAnimation BITES = RawAnimation.begin().thenLoop("feed_mob");
    /** Na boca: o bote curto da mordida, segurando (parado / andando), a sacudida e o arremesso. */
    static final RawAnimation GRAB_START = RawAnimation.begin().thenPlayAndHold("grab_start");
    static final RawAnimation GRAB_HOLD = RawAnimation.begin().thenLoop("grab_hold");
    static final RawAnimation GRAB_WALK = RawAnimation.begin().thenLoop("grab_walk");
    static final RawAnimation GRAB_SHAKE = RawAnimation.begin().thenLoop("grab_shake");
    static final RawAnimation GRAB_THROW = RawAnimation.begin().thenPlayAndHold("grab_throw");
    private static final int GRAB_START_TICKS = 6;
    /** No monte de pedaços: deitando, deitado (a cabeça esfrega, afunda e olha) e levantando. */
    static final RawAnimation PILE_DROP = RawAnimation.begin().thenPlayAndHold("pile_drop");
    static final RawAnimation PILE_LIE = RawAnimation.begin().thenLoop("pile_lie");
    static final RawAnimation PILE_RISE = RawAnimation.begin().thenPlayAndHold("pile_rise");
    /** As mordidas do vídeo: a cabeça mergulha a cada 0,24 s (o feed_mob tem 0,6 s). */
    private static final double BITE_SPEED = 0.6D / 0.24D;
    /** Finalização (LmFinish): monta, cava, abre, come e encara. */
    static final RawAnimation FIN_MOUNT = RawAnimation.begin().thenPlayAndHold("fin_mount");
    static final RawAnimation FIN_DIG = RawAnimation.begin().thenLoop("fin_dig");
    static final RawAnimation FIN_OPEN = RawAnimation.begin().thenPlayAndHold("fin_open");
    static final RawAnimation FIN_FEED = RawAnimation.begin().thenLoop("fin_feed");
    static final RawAnimation FIN_STARE = RawAnimation.begin().thenLoop("fin_stare");
    /** Fase 2: correndo em pé curvado, desesperado. */
    static final RawAnimation FRENZY_RUN = RawAnimation.begin().thenLoop("run_frenzy");
    static final RawAnimation FRENZY_IDLE = RawAnimation.begin().thenLoop("frenzy_idle");

    /** Abaixo disso (blocos por tick) ele está parado. */
    private static final double MOVING = 0.02D;

    private LmAnims() {
    }

    static RawAnimation pick(AbstractClientPlayer p) {
        LmActsClient.Act act = LmActsClient.get(p);
        if (act != null) {
            if (act.kind == LmAttack.SMASH) return act.age < LmAttacks.BITE_FROM ? SMASH : BITES;
            if (act.kind == LmAttack.GRAB) {
                if (act.shake >= 0) return GRAB_SHAKE;
                if (act.age < GRAB_START_TICKS) return GRAB_START;
                return groundSpeed(p) > MOVING ? GRAB_WALK : GRAB_HOLD;
            }
            if (act.kind == LmAttack.THROW) return GRAB_THROW;
            if (act.kind == LmAttack.PILE) return act.age < LmActsClient.PILE_DROP ? PILE_DROP : PILE_LIE;
            if (act.kind == LmAttack.RISE) return PILE_RISE;
            if (act.kind == LmAttack.FINISH) {
                return switch (LmFinish.phase(act.age)) {
                    case MOUNT -> FIN_MOUNT;
                    case DIG -> FIN_DIG;
                    case OPEN -> FIN_OPEN;
                    case FEED -> FIN_FEED;
                    case STARE -> FIN_STARE;
                };
            }
        }
        LmTransform.Phase tp = LmTransformClient.phase(p);
        if (tp != null) {
            return switch (tp) {
                case CONTORT -> CONTORT;
                case HOLD -> CONTORT_HOLD;
                case ROAR -> ROAR;
            };
        }
        LmMode mode = LmModes.get(p);
        LmMove move = LmMoves.airborne(p);
        boolean moving = groundSpeed(p) > MOVING;
        if (p.onClimbable() && !p.onGround()) return CLIMB;
        if (move == LmMove.SKY) return SKY;
        if (move == LmMove.POUNCE) return LEAP;
        if (mode == LmMode.GALLOP) {
            if (move == LmMove.LEAP) return LEAP;
            if (move == LmMove.HOP) return HOP;
            // segurando o agachar (carregando o pulo alto) ele fica baixinho, parado
            if (moving && !p.isShiftKeyDown()) return RUN;
            return CROUCH_IDLE;
        }
        if (mode == LmMode.FRENZY) return moving ? FRENZY_RUN : FRENZY_IDLE;
        return moving ? WALK : IDLE;
    }

    /**
     * Galope: a cadência quase não muda, o que cresce é a passada (igual ao Froggydude:
     * a ~0,7 b/t a velocidade fica em ~1, passada de ~3,7 blocos). Em pé: o passo
     * acompanha a velocidade.
     */
    static double speed(AbstractClientPlayer p) {
        LmActsClient.Act act = LmActsClient.get(p);
        if (act != null) {
            if (act.kind == LmAttack.SMASH && act.age >= LmAttacks.BITE_FROM) return BITE_SPEED;
            // arrastando: o passo acompanha a velocidade
            if (act.kind == LmAttack.GRAB && act.shake < 0) return Mth.clamp(groundSpeed(p) * 8.0D, 0.5D, 1.6D);
            return 1D;
        }
        LmMode mode = LmModes.get(p);
        double v = groundSpeed(p);
        if (v <= MOVING || !p.onGround()) return 1D;
        if (mode == LmMode.GALLOP) return p.isShiftKeyDown() ? 1D : runSpeed(v);
        if (mode == LmMode.FRENZY) return runSpeed(v);
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
