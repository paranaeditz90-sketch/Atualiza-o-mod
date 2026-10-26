package com.becomelm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

/**
 * O controlador do corpo (um por jogador). O tempo da animação é somado aos poucos, cada
 * pedacinho na velocidade daquele momento (como o PacedAnimationController do
 * Froggydude): o galope acelera e freia liso, junto com a corrida de verdade.
 */
final class LmAnimController extends AnimationController<LmAnimatable> {

    private double lastTick = -1D;
    private double elapsed = 0D;

    LmAnimController(LmAnimatable animatable) {
        super(animatable, "main", 3, state -> {
            Entity e = state.getData(DataTickets.ENTITY);
            if (!(e instanceof AbstractClientPlayer p)) return PlayState.STOP;
            return state.setAndContinue(LmAnims.pick(p));
        });
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
        if (this.lastTick < 0D || tick < this.lastTick || Minecraft.getInstance().isPaused()) {
            // pausado (mundo sozinho), nada anda
            this.lastTick = tick;
        }
        AbstractClientPlayer p = LmRenderer.rendering;
        double speed = p == null ? 1D : LmAnims.speed(p);
        this.elapsed += (tick - this.lastTick) * speed;
        this.lastTick = tick;
        return this.elapsed;
    }
}
