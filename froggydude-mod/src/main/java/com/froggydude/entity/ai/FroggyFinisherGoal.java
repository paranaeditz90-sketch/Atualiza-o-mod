package com.froggydude.entity.ai;

import com.froggydude.entity.BloodFx;
import com.froggydude.entity.FeedLock;
import com.froggydude.entity.Finisher;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.PinTracker;
import com.froggydude.entity.SkinVariant;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.init.ModDamageTypes;
import com.froggydude.init.ModSounds;
import com.froggydude.network.CutscenePacket;
import com.froggydude.network.ModNetwork;
import com.froggydude.player.ArmLoss;
import com.froggydude.player.FinisherTracker;
import com.froggydude.player.GorePrefs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * O golpe final (a cutscene): quando um golpe dele mataria o jogador, em vez
 * de morrer na hora a vítima cai de costas e ele arranca o braço esquerdo, o
 * direito e a cabeça, nessa ordem (ver entity.Finisher pra tabela e a
 * geometria). Aqui é o lado do servidor: põe os dois no lugar, faz cada parte
 * sair no tick certo (com o sangue jorrando dos tocos, as falas e os sons) e
 * mata no fim. A câmera, a pose deitada e a cabeça na boca dele são do
 * cliente (client.cutscene), contando a partir do mesmo tick.
 *
 * Aldeão (LM: "comida favorita dele: players e aldeões, mesmas animações"):
 * a mesma tabela, sem câmera de cinema (quem está perto vê de fora). Os braços
 * do aldeão são uma peça só, cruzada: saem juntos, no tempo do braço direito
 * (a tabela começa do jeito de quem já estava sem o esquerdo). Não sobra corpo.
 */
public class FroggyFinisherGoal extends Goal {

    /** Extremo: quanto tempo a vítima assiste ele comer o corpo antes de morrer (6,5 s). */
    public static final int CORPSE_WATCH = 130;

    private final FroggydudeEntity froggy;
    private LivingEntity victim;
    private Vec3 anchor;
    private Vec3 h;
    private float yaw;
    private long start;
    private boolean skipLeft;
    private int lastT = -1;
    private boolean anyLimb;
    private boolean done;

    public FroggyFinisherGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean canUse() {
        LivingEntity p = froggy.takePendingFinisher();
        if (p == null) return false;
        if (!p.isAlive() || p.level() != froggy.level() || p.isSpectator()
                || p instanceof Player pl && pl.isCreative()) {
            if (p instanceof Player pl) FinisherTracker.clear(pl);
            if (!(p instanceof Player)) FeedLock.release(p);
            return false;
        }
        victim = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !done;
    }

    @Override
    public void start() {
        done = false;
        lastT = -1;
        anyLimb = false;
        ServerPlayer player = victim instanceof ServerPlayer sp ? sp : null;
        skipLeft = player == null || ArmLoss.isArmless(player);
        ServerLevel level = (ServerLevel) froggy.level();
        // ele vem por trás dos pés: a cabeça da vítima aponta pra longe dele
        Vec3 d = victim.position().subtract(froggy.position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 1.0E-3D) d = Finisher.dir(froggy.getYRot());
        h = d.normalize();
        yaw = (float) (Mth.atan2(h.z, h.x) * Mth.RAD_TO_DEG) - 90F;
        anchor = ground(level, victim.position());
        start = level.getGameTime();

        victim.stopRiding();
        victim.stopUsingItem();
        victim.setSprinting(false);
        if (player != null) {
            PinTracker.release(player);
            FinisherTracker.mark(player, froggy.getId());
        } else {
            // o aldeão não foge nem reage (a IA volta se ele escapar vivo: abort)
            FeedLock.lock(victim);
        }
        froggy.beginFinisher(victim, skipLeft);
        place();
        // a vítima deitada olha pro céu; o corpo no servidor fica parado onde caiu
        holdVictim();

        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        anchor.x, anchor.y, anchor.z, 128.0D, level.dimension())),
                new CutscenePacket(CutscenePacket.START, froggy.getId(), victim.getId(), start,
                        anchor.x, anchor.y, anchor.z, yaw, skipLeft));
        froggy.speak(VoiceSituation.PIN, true);
        level.playSound(null, anchor.x, anchor.y, anchor.z, ModSounds.PIN.get(), SoundSource.HOSTILE, 1.2F, 0.9F);
        froggy.brainLog("golpe final em " + victim.getScoreboardName() + (skipLeft ? " (já sem o braço esquerdo)" : ""));
    }

    /** A vítima parada no lugar onde caiu (o jogador pelo teleporte, senão o cliente dele a move). */
    private void holdVictim() {
        float vYaw = yaw + 180F;
        if (victim instanceof ServerPlayer sp) {
            sp.connection.teleport(anchor.x, anchor.y, anchor.z, vYaw, 25F);
            sp.hurtMarked = true;
        } else {
            victim.moveTo(anchor.x, anchor.y, anchor.z, vYaw, 0F);
            victim.setYBodyRot(vYaw);
        }
        victim.setDeltaMovement(Vec3.ZERO);
    }

    private boolean gone(ServerLevel level) {
        return victim == null || victim.isRemoved() || !victim.isAlive() || victim.level() != level
                || victim instanceof ServerPlayer sp && sp.hasDisconnected();
    }

    /** Chão embaixo de onde a vítima caiu (até 6 blocos); no ar/na água fica onde está. */
    private static Vec3 ground(ServerLevel level, Vec3 pos) {
        var hit = level.clip(new ClipContext(pos.add(0, 0.5D, 0), pos.add(0, -6D, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        if (hit.getType() == HitResult.Type.BLOCK && level.getFluidState(BlockPos.containing(pos)).isEmpty()) {
            return hit.getLocation();
        }
        return pos;
    }

    /** Ele em pé por cima do quadril dela, virado pra cabeça. */
    private void place() {
        Vec3 spot = Finisher.froggySpot(anchor, h);
        froggy.setPos(spot.x, anchor.y, spot.z);
        froggy.setDeltaMovement(Vec3.ZERO);
        froggy.getNavigation().stop();
        froggy.faceDirection(h.x, h.z);
        froggy.setNoFallTicks(20);
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) froggy.level();
        if (gone(level)) {
            abort(false);
            return;
        }
        int t = (int) (level.getGameTime() - start) + (skipLeft ? Finisher.SKIP : 0);
        place();
        if (victim.position().distanceToSqr(anchor) > 0.04D) holdVictim();
        victim.setDeltaMovement(Vec3.ZERO);
        // eventos que caem entre o último tick e este (o servidor pode pular tick)
        for (int k = lastT + 1; k <= t; k++) event(level, k);
        lastT = t;
        if (t >= Finisher.END) finish(level);
    }

    private void event(ServerLevel level, int t) {
        Vec3 ls = Finisher.leftShoulder(anchor, h), rs = Finisher.rightShoulder(anchor, h), nk = Finisher.neck(anchor, h);
        Vec3 l = Finisher.left(h);
        Vec3 up = new Vec3(0, 1, 0);
        // as duas mordidas nos braços e a da cabeça: crava, sacode rasgando, arranca
        if (!skipLeft) bite(level, t, Finisher.BITE_L, Finisher.RIP_L, ls, l);
        bite(level, t, Finisher.BITE_R, Finisher.RIP_R, rs, l.scale(-1));
        bite(level, t, Finisher.BITE_H, Finisher.RIP_H, nk, h);

        if (!skipLeft && t == Finisher.RIP_L) {
            if (victim instanceof ServerPlayer sp) ArmLoss.removeArmQuiet(sp);
            anyLimb = true;
            rip(level, ls, l, 0.34F, Finisher.CUT_1 - Finisher.RIP_L);
        }
        if (t == Finisher.RIP_R) {
            anyLimb = true;
            rip(level, rs, l.scale(-1), 0.34F, Finisher.STILL - Finisher.RIP_R + 6);
        }
        if (t == Finisher.RIP_H) {
            anyLimb = true;
            rip(level, nk, h, 0.42F, Finisher.SWALLOW - Finisher.RIP_H);
            level.playSound(null, nk.x, nk.y, nk.z, ModSounds.CRACK.get(), SoundSource.HOSTILE, 1.5F, 0.8F);
        }
        // sacudindo o pedaço na boca: jorra da boca dele
        boolean shaking = (!skipLeft && t > Finisher.RIP_L && t < Finisher.CUT_1)
                || (t > Finisher.RIP_R && t < Finisher.STILL) || (t > Finisher.RIP_H && t < Finisher.SWALLOW);
        if (shaking && t % 2 == 0) froggy.openMouth(4);
        // pedaço voando da boca: pouco de cada vez (no close a câmera está perto e tampava tudo)
        if (shaking && t % 4 == 0) froggy.spawnMouthBlood(3);
        // parado mastigando o braço; e o "mmmm"
        if (t > Finisher.STILL && t < Finisher.BEND_H && (t - Finisher.STILL) % 14 == 0) froggy.onChew();
        if (t == Finisher.STILL + 6) froggy.speak(VoiceSituation.CHEWING, true);
        if (t == Finisher.BEND_H - 18) froggy.speak(VoiceSituation.LAST_BITE, true);
        if (t > Finisher.RIP_H && t < Finisher.SWALLOW && (t - Finisher.RIP_H) % 10 == 0) froggy.onChew();
        if (t == Finisher.SWALLOW) {
            level.playSound(null, froggy.getX(), froggy.getY(), froggy.getZ(), ModSounds.EAT.get(), SoundSource.HOSTILE, 1.4F, 0.8F);
        }
    }

    /** Crava (som + sangue), sacode rasgando a cada 3 ticks até arrancar. */
    private void bite(ServerLevel level, int t, int biteAt, int ripAt, Vec3 at, Vec3 out) {
        if (t == biteAt) {
            froggy.onBiteHit();
            froggy.stainMouth(victim, 0.35F);
            froggy.openMouth(ripAt - biteAt + 4);
            BloodFx.spray(level, at, out.add(0, 0.8D, 0), 10, 0.22F);
            BloodFx.hit(victim, froggy, at, out.scale(-1), 3F, BloodFx.RED, BloodFx.HIT_MARKS);
        } else if (t > biteAt && t < ripAt && (t - biteAt) % 3 == 0) {
            BloodFx.spray(level, at, out.add(0, 1.0D, 0), 5, 0.2F);
        }
    }

    /** Arrancou: estoura, o toco jorra (até ele largar o pedaço), poça grande, o "rasgo". */
    private void rip(ServerLevel level, Vec3 at, Vec3 out, float speed, int ticks) {
        Vec3 jet = out.scale(0.35D).add(0, 1, 0);
        BloodFx.burst(level, at, jet, 24, 0.42F, BloodFx.RED, victim);
        BloodFx.streamAt(level, at, jet, speed, ticks, BloodFx.RED);
        BloodFx.pool(level, at, 1.8F);
        level.playSound(null, at.x, at.y, at.z, ModSounds.RIP.get(), SoundSource.HOSTILE, 1.8F, 0.85F + froggy.getRandom().nextFloat() * 0.15F);
        froggy.addGore(0.5F);
        // quem está assistindo (e a câmera da vítima) treme
        for (ServerPlayer p : level.players()) {
            double dd = p.distanceToSqr(anchor);
            if (dd < 20 * 20) ModNetwork.sendShake(p, (float) (1.2D * (1D - Math.sqrt(dd) / 20D)) + 0.2F, 14);
        }
    }

    private void finish(ServerLevel level) {
        if (done) return;
        done = true;
        if (!(victim instanceof ServerPlayer p)) {
            // aldeão: engoliu a cabeça, acabou (não sobra corpo)
            sendEnd(level);
            froggy.endFinisher(true);
            froggy.heal(froggy.getMaxHealth() * 0.2F);
            froggy.setSkinVariant(SkinVariant.FED);
            froggy.addGore(1.0F);
            froggy.villagerEaten();
            victim.hurt(devoured(level, froggy), Float.MAX_VALUE);
            froggy.speakAfter(VoiceSituation.ATE, 15);
            return;
        }
        boolean corpse = GorePrefs.of(p).corpse();
        sendEnd(level);
        froggy.endFinisher(true);
        froggy.heal(froggy.getMaxHealth() * 0.25F);
        froggy.setSkinVariant(SkinVariant.FED);
        froggy.addGore(1.5F);
        if (corpse) {
            // Extremo: o corpo fica e ele come/brinca. Quem morreu assiste isso pela câmera
            // de cinema antes da tela de morte - um jogador morto sai do mundo em 1 s e o
            // cliente dele para de ver as entidades, então a morte de verdade vem depois
            froggy.leaveCorpse(p, anchor, yaw);
            FinisherTracker.scheduleDeath(p, level.getGameTime() + CORPSE_WATCH);
        } else {
            FinisherTracker.clear(p);
            p.hurt(devoured(level, froggy), Float.MAX_VALUE);
        }
    }

    /** Acabou antes da hora (ele morreu, a vítima saiu): se já tinha arrancado algo, ela não sobrevive. */
    private void abort(boolean byDeath) {
        if (done) return;
        done = true;
        if (victim != null) {
            if (victim instanceof ServerPlayer sp) FinisherTracker.clear(sp);
            else FeedLock.release(victim);
            if (froggy.level() instanceof ServerLevel level) {
                sendEnd(level);
                boolean here = !(victim instanceof ServerPlayer sp) || !sp.hasDisconnected();
                if (anyLimb && victim.isAlive() && here) {
                    victim.hurt(devoured(level, froggy), Float.MAX_VALUE);
                }
            }
        }
        froggy.endFinisher(false);
    }

    /** O dano do fim (passa por armadura, totem e invulnerabilidade; ver ModDamageTypes). */
    public static DamageSource devoured(ServerLevel level, @Nullable net.minecraft.world.entity.Entity froggy) {
        // sem ele (sumiu) a mensagem "despedaçado por %2$s" ficaria sem nome: morte comum de /kill
        if (froggy == null) return level.damageSources().genericKill();
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModDamageTypes.DEVOURED);
        return new DamageSource(type, froggy);
    }

    private void sendEnd(ServerLevel level) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        anchor.x, anchor.y, anchor.z, 160.0D, level.dimension())),
                new CutscenePacket(CutscenePacket.END, froggy.getId(), victim.getId(), start,
                        anchor.x, anchor.y, anchor.z, yaw, skipLeft));
    }

    @Override
    public void stop() {
        if (!done) abort(!froggy.isAlive());
        victim = null;
    }
}
