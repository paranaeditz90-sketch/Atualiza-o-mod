package com.froggydude.entity;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationController;

/**
 * Controlador do GeckoLib que aceita velocidade variando o tempo todo.
 *
 * O original multiplica o tempo TOTAL da animação pela velocidade atual, então
 * qualquer mudança de velocidade faz a animação pular pra frente ou pra trás.
 * Aqui o tempo é somado aos poucos (cada pedacinho com a velocidade daquele
 * momento): o galope acelera e desacelera liso, junto com a corrida de verdade.
 */
public class PacedAnimationController<T extends GeoAnimatable> extends AnimationController<T> {

    /** Só pra gravar quadro a quadro (froggydude.capture): o tempo da animação, em segundos, no último quadro desenhado. */
    public static volatile double debugLastSeconds = -1D;
    private static final boolean CAPTURE = Boolean.getBoolean("froggydude.capture");

    /**
     * Relógio de fora (o cliente liga; null no servidor): pra uma animação que tem que
     * andar junto com outra coisa, devolve o tempo dela em ticks, ou negativo pra seguir
     * o relógio próprio. A cutscene do golpe final usa: o áudio da zebra, a câmera e o
     * pedaço na boca seguem a tabela (Finisher), e o GeckoLib só começa a contar depois
     * da transição (3 ticks) e de o estado chegar no cliente - o corpo dele andava uns
     * 5 ticks atrás do resto (v0.4.5).
     */
    public interface Clock {
        double ticks(GeoAnimatable animatable, String animation, double tick);
    }

    public static volatile Clock externalClock;

    private double lastTick = -1D;
    private double elapsed = 0D;

    public PacedAnimationController(T animatable, String name, int transitionTicks,
                                    AnimationStateHandler<T> handler) {
        super(animatable, name, transitionTicks, handler);
    }

    @Override
    protected double adjustTick(double tick) {
        if (this.shouldResetTick) {
            if (getAnimationState() != State.STOPPED) {
                this.tickOffset = tick;
            }
            this.shouldResetTick = false;
            this.lastTick = tick;
            this.elapsed = 0D;
            if (CAPTURE) debugLastSeconds = 0D;
            return 0D;
        }
        if (this.lastTick < 0D || tick < this.lastTick) {
            this.lastTick = tick;
        }
        double speed = this.animationSpeedModifier.apply(this.animatable);
        this.elapsed += (tick - this.lastTick) * speed;
        this.lastTick = tick;
        Clock clock = externalClock;
        if (clock != null && getAnimationState() == State.RUNNING && this.currentAnimation != null) {
            double ext = clock.ticks(this.animatable, this.currentAnimation.animation().name(), tick);
            // segue daqui se o relógio de fora sumir (fim da cutscene), sem voltar a animação
            if (ext >= 0D) this.elapsed = ext;
        }
        if (CAPTURE) debugLastSeconds = this.elapsed / 20D;
        return this.elapsed;
    }
}
