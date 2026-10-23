package com.froggydude.entity.ai;

import com.froggydude.brain.PreyStudy;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.init.ModSounds;
import com.froggydude.network.ModNetwork;
import com.froggydude.player.PlayerSight;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * O jumpscare raro (v0.7.7, ideia do LM): tu perde ele de vista e ele SOME. Silêncio.
 * Atrás de ti: "I SEE YOU! HIHIHI... you can't hide from me!". Quando tu vira (ou a
 * fala acaba), ele já está na tua cara e se joga em ti - a cutscene curta (~5 s, sem
 * HUD, faixas pretas) dele em cima de ti descendo os braços (o esmagamento). Depois
 * ele sai de cima e a luta segue: tu começa perdendo, mas dá pra sobreviver.
 * No máximo 1 a cada 3 dias do jogo por jogador (escolha do LM: "bem raro").
 */
final class JumpScare {

    /** 3 dias do jogo entre um e outro. */
    private static final long EVERY = 72000L;
    /** O primeiro: em algum momento entre 1 e 3 dias de caçada. */
    private static final long FIRST_MIN = 24000L;
    private static final int FIRST_RANDOM = 48000;
    /** Na hora que tu perde ele de vista, a chance (pra não ser sempre a primeira vez). */
    private static final float CHANCE = 0.4F;
    /** Sumido e calado antes da fala. */
    private static final int GONE_MIN = 30;
    private static final int GONE_MAX = 110;
    /** A fala inteira ("I see you! hihihi... you can't hide from me!"). */
    private static final int VOICE_MAX = 147;
    /** O jogo vira a tua câmera pra ele (LM: "o jogo deveria forçar o jogador a olhar pra trás"). */
    private static final int TURN_TICKS = 6;
    /** Se jogando na tua cara antes de cair em cima. */
    private static final int LEAP_TICKS = 6;
    /** A cutscene em cima de ti (o esmagamento), em ticks. */
    static final int SMASH_TICKS = 100;

    private enum Stage { NONE, GONE, VOICE, TURN, LEAP }

    private final FroggydudeEntity froggy;
    private final FroggyCombatGoal combat;
    private final RealTactics tactics;

    private Stage stage = Stage.NONE;
    private int stageAt;
    @Nullable
    private UUID who;
    private boolean spoke;
    private int startedAt;

    JumpScare(FroggydudeEntity froggy, FroggyCombatGoal combat, RealTactics tactics) {
        this.froggy = froggy;
        this.combat = combat;
        this.tactics = tactics;
    }

    boolean active() {
        return stage != Stage.NONE;
    }

    /** Devolve true se cuidou do tick. */
    boolean tick(LivingEntity target, double dist) {
        if (stage == Stage.NONE) return maybeStart(target, dist);
        if (!(target instanceof ServerPlayer p) || !p.getUUID().equals(who) || !p.isAlive() || froggy.isFrenzyActive()) {
            stop();
            return false;
        }
        // levou pancada no meio (alguém achou ele): aparece e briga normal
        if (froggy.getLastHurtByMobTimestamp() > startedAt && froggy.tickCount - froggy.getLastHurtByMobTimestamp() < 5) {
            froggy.brainLog("jumpscare cortado: apanhou");
            stop();
            return false;
        }
        int t = froggy.tickCount - stageAt;
        switch (stage) {
            case GONE -> {
                // sumido e calado (nem os passos), chegando pelas tuas costas
                froggy.setInvisible(true);
                froggy.setSilent(true);
                if (t < GONE_MIN - 6) froggy.mute(6);
                if (froggy.tickCount % 5 == 0) tactics.moveTo(tactics.blindSpot(p, 2.5D), FroggyCombatGoal.huntSpeed() * 1.1D);
                if (t >= GONE_MIN && dist < 6.0D || t >= GONE_MAX) {
                    if (dist >= 6.0D) behind(p);
                    next(Stage.VOICE);
                    spoke = false;
                }
            }
            case VOICE -> {
                froggy.setInvisible(true);
                if (!spoke && !froggy.isMuted()) {
                    // atrás de ti, colado: o som vem das tuas costas
                    froggy.setSilent(false);
                    spoke = froggy.speak(VoiceSituation.SEE_YOU, true);
                    if (!spoke) froggy.playSound(ModSounds.AMBIENT.get(), 1.4F, 0.75F); // sem o pacote de voz
                    froggy.brainLog("jumpscare em " + p.getScoreboardName() + ": \"I see you\" nas costas");
                    stageAt = froggy.tickCount;
                    t = 0;
                }
                if (froggy.tickCount % 5 == 0) tactics.moveTo(tactics.blindSpot(p, 2.2D), FroggyCombatGoal.huntSpeed());
                froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
                // tu vira pra ver de onde vem a voz (ou a fala acaba): já é tarde
                if (t >= 30 && facing(p) || t >= VOICE_MAX) strike(p);
            }
            case TURN -> {
                // a tua câmera gira à força pra cara dele; ele parado te encarando
                froggy.getNavigation().stop();
                froggy.faceTarget(p);
                froggy.getLookControl().setLookAt(p, 90.0F, 90.0F);
                if (t >= TURN_TICKS) {
                    froggy.setFroggyState(FroggyState.JUMP_PIN);
                    froggy.playSound(ModSounds.LEAP.get(), 2.0F, 0.9F);
                    froggy.playSound(ModSounds.SMASH.get(), 2.0F, 0.7F);
                    next(Stage.LEAP);
                }
            }
            case LEAP -> {
                froggy.faceTarget(p);
                if (t == 1) {
                    Vec3 to = p.position().subtract(froggy.position()).multiply(1, 0, 1);
                    to = to.lengthSqr() < 1.0E-4D ? Vec3.ZERO : to.normalize().scale(0.35D);
                    froggy.setDeltaMovement(to.x, 0.25D, to.z);
                    froggy.hasImpulse = true;
                }
                if (t >= LEAP_TICKS) {
                    stop();
                    combat.jumpscareSmash(p);
                    return true;
                }
            }
            default -> {
            }
        }
        return true;
    }

    private boolean maybeStart(LivingEntity target, double dist) {
        if (!(target instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return false;
        if (!(froggy.level() instanceof ServerLevel level)) return false;
        if (froggy.getFroggyState() != FroggyState.CHASE || froggy.isFrenzyActive() || froggy.isHolding()
                || froggy.isMocking(p) || combat.scaring()) {
            return false;
        }
        PreyStudy st = FroggyWorldData.get(level).brain.memory(p.getUUID(), p.getScoreboardName()).study;
        long now = level.getGameTime();
        if (st.jumpscareAt == 0L) {
            st.jumpscareAt = now + FIRST_MIN + froggy.getRandom().nextInt(FIRST_RANDOM);
            FroggyWorldData.get(level).setDirty();
        }
        boolean forced = froggy.takeForcedJumpscare();
        if (!forced) {
            if (now < st.jumpscareAt || dist < 6.0D || dist > 48.0D || !p.onGround()) return false;
            // acabou de perder ele de vista (o cliente conta: 1,5 s sem ver)
            if (PlayerSight.unseenFor(p, froggy) != 30) return false;
            if (froggy.getRandom().nextFloat() > CHANCE) return false;
        }
        st.jumpscareAt = now + EVERY;
        FroggyWorldData.get(level).setDirty();
        who = p.getUUID();
        startedAt = froggy.tickCount;
        next(Stage.GONE);
        froggy.setInvisible(true);
        froggy.getNavigation().stop();
        froggy.brainLog("jumpscare em " + p.getScoreboardName() + ": sumiu" + (forced ? " (comando)" : ""));
        return true;
    }

    private void next(Stage s) {
        stage = s;
        stageAt = froggy.tickCount;
    }

    /** Ela olhando pra ele (até 55 graus do centro da tela)? */
    private boolean facing(ServerPlayer p) {
        Vec3 look = p.getViewVector(1.0F).multiply(1, 0, 1);
        Vec3 to = froggy.position().subtract(p.position()).multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4D || to.lengthSqr() < 1.0E-4D) return false;
        return look.normalize().dot(to.normalize()) > 0.57D;
    }

    /** Longe demais pra chegar andando a tempo: aparece (invisível) atrás dela. */
    private void behind(ServerPlayer p) {
        Vec3 at = tactics.blindSpot(p, 2.5D);
        BlockPos feet = BlockPos.containing(at.x, p.getY(), at.z);
        if (RealTactics.free(froggy.level(), feet) && RealTactics.free(froggy.level(), feet.above())
                && !RealTactics.free(froggy.level(), feet.below())) {
            froggy.teleportTo(at.x, p.getY(), at.z);
        }
    }

    /**
     * Já é tarde: ele aparece colado nas tuas costas (de onde veio a voz) e o jogo vira a
     * tua câmera pra cara dele - aí ele se joga. Se tu já virou sozinho, ele fica onde está.
     */
    private void strike(ServerPlayer p) {
        if (!facing(p)) {
            Vec3 look = p.getViewVector(1.0F).multiply(1, 0, 1);
            look = look.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : look.normalize();
            for (double r : new double[]{1.8D, 1.3D}) {
                Vec3 at = p.position().subtract(look.scale(r));
                BlockPos feet = BlockPos.containing(at.x, p.getY(), at.z);
                if (RealTactics.free(froggy.level(), feet) && RealTactics.free(froggy.level(), feet.above())) {
                    froggy.teleportTo(at.x, p.getY(), at.z);
                    break;
                }
            }
        }
        froggy.stopSpeaking();
        froggy.setInvisible(false);
        froggy.setSilent(false);
        froggy.faceTarget(p);
        froggy.getNavigation().stop();
        // em pé atrás de ti, como gente (o rosto na altura do teu olho quando a câmera chega nele)
        froggy.setFroggyState(FroggyState.TAUNT);
        ModNetwork.sendJumpscare(p, TURN_TICKS + LEAP_TICKS + SMASH_TICKS, froggy.getId(), TURN_TICKS);
        froggy.brainLog("jumpscare: vira a câmera de " + p.getScoreboardName() + " pra ele");
        next(Stage.TURN);
    }

    void stop() {
        if (stage != Stage.NONE) {
            froggy.setInvisible(false);
            froggy.setSilent(false);
        }
        stage = Stage.NONE;
        who = null;
    }
}
