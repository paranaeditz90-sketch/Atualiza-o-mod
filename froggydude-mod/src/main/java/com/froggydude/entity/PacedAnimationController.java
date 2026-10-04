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
            return 0D;
        }
        if (this.lastTick < 0D || tick < this.lastTick) {
            this.lastTick = tick;
        }
        double speed = this.animationSpeedModifier.apply(this.animatable);
        this.elapsed += (tick - this.lastTick) * speed;
        this.lastTick = tick;
        return this.elapsed;
    }
}
