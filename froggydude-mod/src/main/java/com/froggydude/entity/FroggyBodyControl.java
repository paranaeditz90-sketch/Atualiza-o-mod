package com.froggydude.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

/**
 * Pra onde o CORPO dele aponta (o modelo inteiro gira junto com isso).
 *
 * O do jogo deixa o corpo de um mob parado até 75 graus fora da cabeça e só
 * alinha depois de meio segundo - num zumbi passa, mas no Froggy dava a
 * cabeça torcida olhando pra você com o corpo de lado, e nos ataques (que
 * são poses inteiras: socar, morder, montar) o golpe saía pro lado.
 *
 * Aqui:
 * - em qualquer ação (ataque, em cima da vítima, contorção, comendo...) o
 *   corpo aponta exatamente pra onde o servidor virou ele (faceTarget, que vai
 *   sincronizado na hora em DATA_FACE_YAW);
 * - andando, o corpo vai na direção do movimento (igual ao jogo);
 * - parado, o corpo inteiro vira atrás da cabeça, rápido, sem torcer o
 *   pescoço mais que um tiquinho.
 */
public class FroggyBodyControl extends BodyRotationControl {

    /** Quanto a cabeça pode ficar virada em relação ao corpo, parado. */
    private static final float NECK_SLACK = 12.0F;
    /** Quanto o corpo gira por tick pra acompanhar a cabeça. */
    private static final float TURN_SPEED = 24.0F;

    private final FroggydudeEntity froggy;

    public FroggyBodyControl(FroggydudeEntity froggy) {
        super(froggy);
        this.froggy = froggy;
    }

    @Override
    public void clientTick() {
        FroggyState state = froggy.getFroggyState();
        boolean scripted = (state != FroggyState.IDLE && state != FroggyState.CHASE) || froggy.isClimbing();
        double dx = froggy.getX() - froggy.xo;
        double dz = froggy.getZ() - froggy.zo;
        boolean moving = dx * dx + dz * dz > 2.5E-7D;

        float face = froggy.getFaceYaw();
        if (scripted && !Float.isNaN(face)) {
            // ataque: o corpo vai direto pra onde o servidor virou ele (sem esperar a rotação chegar)
            froggy.yBodyRot = face;
            froggy.yHeadRot = Mth.rotateIfNecessary(froggy.yHeadRot, face, 0.0F);
            return;
        }
        if (scripted || moving) {
            froggy.yBodyRot = froggy.getYRot();
            froggy.yHeadRot = Mth.rotateIfNecessary(froggy.yHeadRot, froggy.yBodyRot, scripted ? 0.0F : 30.0F);
            return;
        }
        float diff = Mth.wrapDegrees(froggy.yHeadRot - froggy.yBodyRot);
        if (Math.abs(diff) > NECK_SLACK) {
            float turn = diff - Math.copySign(NECK_SLACK, diff);
            froggy.yBodyRot += Mth.clamp(turn, -TURN_SPEED, TURN_SPEED);
        }
    }
}
