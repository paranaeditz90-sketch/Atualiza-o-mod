package com.froggydude.entity.ai;

import com.froggydude.entity.BloodFx;
import com.froggydude.entity.Finisher;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.PlayerCorpse;
import com.froggydude.entity.voice.VoiceSituation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Extremo, depois da cutscene: ele come o corpo de quem matou (LM: "comendo o
 * corpo do player e achando delicioso e até brincando com o corpo"). Uma
 * rodada: vai até o corpo, MORDE (arranca pedaço, sangue, "mmmm"), às vezes
 * JOGA pro alto e ri, às vezes pega na boca e SACODE como cachorro. Entre uma
 * coisa e outra, "that was so delicious", "very delicious". Acaba quando comeu
 * tudo (uns 25-35 s) ou quando alguém mexe com ele.
 */
public class FroggyCorpseGoal extends Goal {

    private static final int SHAKE_TICKS = 34;

    private final FroggydudeEntity froggy;
    private PlayerCorpse corpse;
    private int bites;
    private int shakeLeft;
    private int repath;
    private boolean talked;

    public FroggyCorpseGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (froggy.getTarget() != null || froggy.isFinishing()) return false;
        corpse = froggy.getCorpseTarget();
        return corpse != null;
    }

    @Override
    public boolean canContinueToUse() {
        return corpse != null && corpse.isAlive() && froggy.getTarget() == null && !froggy.isFinishing();
    }

    @Override
    public void start() {
        bites = 0;
        shakeLeft = 0;
        repath = 0;
        talked = false;
    }

    @Override
    public void stop() {
        if (corpse != null) {
            corpse.setHeldBy(null);
            if (!corpse.isAlive()) froggy.forgetCorpse();
        }
        FroggyState s = froggy.getFroggyState();
        if (s == FroggyState.CORPSE_EAT || s == FroggyState.CORPSE_TOSS || s == FroggyState.CORPSE_SHAKE) {
            froggy.setFroggyState(FroggyState.IDLE);
        }
        corpse = null;
    }

    @Override
    public void tick() {
        if (corpse == null) return;
        FroggyState s = froggy.getFroggyState();
        double d = froggy.distanceTo(corpse);
        if (shakeLeft > 0) {
            shake();
            return;
        }
        if (s == FroggyState.CORPSE_EAT) {
            eat();
            return;
        }
        if (s == FroggyState.CORPSE_TOSS) {
            toss();
            return;
        }
        // espera o corpo cair (jogou pro alto) e vai pro LADO dele, na altura da barriga (em pé
        // em cima do corpo, curvado, o tronco dele tampava tudo - ninguém via o que ele comia)
        Vec3 spot = besideCorpse();
        double ds = Math.sqrt(Math.pow(spot.x - froggy.getX(), 2) + Math.pow(spot.z - froggy.getZ(), 2));
        if (!corpse.onGround() || ds > 0.55D) {
            froggy.getLookControl().setLookAt(corpse, 30F, 30F);
            if (ds < 2.5D) {
                froggy.getNavigation().stop();
                froggy.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.8D);
            } else if (--repath <= 0) {
                repath = 6;
                froggy.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.1D);
            }
            return;
        }
        froggy.getNavigation().stop();
        froggy.faceDirection(corpse.getX() - froggy.getX(), corpse.getZ() - froggy.getZ());
        // o que fazer agora: quase sempre comer; às vezes brincar
        int roll = froggy.getRandom().nextInt(10);
        if (bites >= 2 && roll < 2) {
            froggy.setFroggyState(FroggyState.CORPSE_TOSS);
        } else if (bites >= 1 && roll < 4) {
            shakeLeft = SHAKE_TICKS;
            corpse.setHeldBy(froggy);
            froggy.openMouth(SHAKE_TICKS);
            froggy.setFroggyState(FroggyState.CORPSE_SHAKE);
        } else {
            froggy.setFroggyState(FroggyState.CORPSE_EAT);
        }
    }

    /** Onde ficar pra comer: 1 bloco pro lado do corpo (do lado que estiver mais perto dele). */
    private Vec3 besideCorpse() {
        Vec3 h = Finisher.dir(corpse.getHeadYaw());
        Vec3 side = Finisher.left(h);
        Vec3 c = corpse.position();
        Vec3 a = c.add(side), b = c.subtract(side);
        return froggy.distanceToSqr(a) <= froggy.distanceToSqr(b) ? a : b;
    }

    /** A animação "feed" (1 s em loop): morde aos 0,34 s de cada volta. */
    private void eat() {
        int t = froggy.getStateTicks();
        froggy.getNavigation().stop();
        froggy.faceDirection(corpse.getX() - froggy.getX(), corpse.getZ() - froggy.getZ());
        if (t % 20 == 7) {
            corpse.bite(0.07F);
            froggy.onChew();
            froggy.addGore(0.15F);
            bites++;
            if (!talked && bites == 2) talked = froggy.speak(VoiceSituation.CHEWING, true);
            if (bites % 4 == 0) froggy.speakAfter(VoiceSituation.ATE, 12);
        }
        if (t >= FroggyState.CORPSE_EAT.durationTicks) froggy.setFroggyState(FroggyState.IDLE);
    }

    /** Joga pro alto e olha subir (ri), e depois vai buscar. */
    private void toss() {
        int t = froggy.getStateTicks();
        froggy.getNavigation().stop();
        if (t == 8 && corpse.distanceTo(froggy) < 2.4D) {
            float yaw = froggy.getYRot() * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 side = new Vec3(fwd.z, 0, -fwd.x).scale((froggy.getRandom().nextFloat() - 0.5F) * 0.3F);
            corpse.toss(fwd.scale(0.18D).add(side).add(0, 0.85D + froggy.getRandom().nextFloat() * 0.25D, 0),
                    16F + froggy.getRandom().nextFloat() * 14F, (froggy.getRandom().nextFloat() - 0.5F) * 18F);
            BloodFx.burst(froggy.level(), corpse.torso(), new Vec3(0, 1, 0), 24, 0.3F, BloodFx.RED, corpse);
            froggy.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.7F);
        }
        if (t > 8) froggy.getLookControl().setLookAt(corpse, 60F, 60F);
        if (t == 12) froggy.speak(VoiceSituation.FOUND, true); // a risadinha
        if (t >= FroggyState.CORPSE_TOSS.durationTicks) froggy.setFroggyState(FroggyState.IDLE);
    }

    /** Na boca, sacudindo forte pros lados (como cachorro com brinquedo). */
    private void shake() {
        shakeLeft--;
        froggy.getNavigation().stop();
        float yaw = froggy.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        // a animação corpse_shake vira a cabeça ±55° a cada 0,2 s: a boca vai junto pros lados
        float wag = Mth.sin(shakeLeft * Mth.PI / 4F);
        Vec3 mouth = froggy.position().add(fwd.scale(0.46D)).add(right.scale(wag * 0.38D)).add(0, 1.5D, 0);
        // preso pelo toco do pescoço: o corpo pendurado pra longe dele, 60° em pé, a ponta das pernas no chão
        Vec3 h = fwd.scale(-1D);
        float headYaw = (float) (Mth.atan2(h.z, h.x) * Mth.RAD_TO_DEG) - 90F;
        Vec3 neckDir = h.scale(0.5D).add(0, 0.866D, 0);
        Vec3 center = mouth.subtract(neckDir.scale(0.9D));
        corpse.hold(headYaw, wag * 35F, 60F + wag * 8F);
        corpse.setPos(center.x, Math.max(center.y, froggy.getY()), center.z);
        corpse.setDeltaMovement(Vec3.ZERO);
        if (shakeLeft % 4 == 0) {
            froggy.spawnMouthBlood(4);
            froggy.openMouth(4);
        }
        if (shakeLeft == 0) {
            // larga (às vezes arremessa)
            corpse.toss(fwd.scale(0.25D).add(0, 0.35D, 0), 10F, (froggy.getRandom().nextFloat() - 0.5F) * 20F);
            corpse.bite(0.05F);
            bites++;
            froggy.setFroggyState(FroggyState.IDLE);
        }
    }
}
