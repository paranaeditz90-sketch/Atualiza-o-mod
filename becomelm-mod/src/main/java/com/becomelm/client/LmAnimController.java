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

    private static final String PILE_LIE = "pile_lie";
    private static final String SKY_SMASH = "sky_smash";

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
        // (v0.3.5) a mistura de 3 ticks pra próxima animação anda no tempo de verdade: com velocidade 0 (agarrado
        // na parede) ela nunca acabava e o corpo ficava na pose de antes
        if (getAnimationState() == State.TRANSITIONING) speed = Math.max(speed, 1D);
        this.elapsed += (tick - this.lastTick) * speed;
        this.lastTick = tick;
        // (v0.3.5) no monte o corpo anda no relógio da mordida (o ritmo da fome): sem isso cada volta do
        // loop perdia um pedacinho e a cara descia fora da hora do som e da comida
        if (p != null && getAnimationState() == State.RUNNING && this.currentAnimation != null) {
            String name = this.currentAnimation.animation().name();
            float ph = -1F;
            if (PILE_LIE.equals(name)) ph = LmActsClient.pileAnimTicks(p, Minecraft.getInstance().getFrameTime());
            // (v0.3.5) a marreta e os socos montado: o punho bate na hora da pancada (e do som de crítico)
            else if (SKY_SMASH.equals(name)) ph = LmActsClient.smashAnimTicks(p, Minecraft.getInstance().getFrameTime());
            if (ph >= 0F) this.elapsed = ph;
        }
        return this.elapsed;
    }
}
