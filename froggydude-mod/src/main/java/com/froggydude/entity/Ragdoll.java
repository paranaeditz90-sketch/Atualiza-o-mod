package com.froggydude.entity;

import com.froggydude.brain.ModWeapons;
import com.froggydude.entity.voice.VoiceLine;
import com.froggydude.entity.voice.VoiceSituation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * (v0.7.40) O ragdoll do tiro (LM 16/11: "ragdoll é ragdoll mesmo, nada de coisa duro. Existe um mod pra isso, mas é
 * necessário fazer o nosso próprio. E a rajada só funciona se ele tiver no chão parado"; "após atirar, ele voa igual
 * nos vídeos de forma dinâmica, porém, se o jogador se aproximar com uma arma, ele fala 'Yes shoot me...' E o restante
 * do diálogo"). Medido nos vídeos do LM (ragdoll/ref v1 e v2): leva o tiro em pé, voa ~10-15 blocos em ~0,5 s girando
 * solto, quica, rola um tico e fica caído torto; o jogador chega perto com a arma ("Hey, I know you're not dead"), ele
 * fala "Yeah, I love pain", depois "Yeah, shoot me. I want to feel the bullets hit my body", e leva mais tiros caído.
 * <p>
 * Servidor (aqui): o voo é a física do próprio mob (o empurrão na direção da bala); deitado, sem IA (as Goals param
 * pelas marcas de controle), com caixa baixa e larga (dá pra acertar o corpo deitado); bala nele caído faz metade do
 * dano (em pé é um décimo) e deixa ele com mais raiva; levanta com raiva. O corpo mole (as juntas, o chão, o rolar) é do
 * cliente (client.pieces.RagdollScene), a partir dos números daqui ({@link Params}, sincronizados).
 */
public final class Ragdoll {

    /** O empurrão (blocos/tick): no chão plano ~11 blocos e ~0,55 s no ar, como no vídeo 1. */
    static final double SPEED = 1.55D;
    static final double UP = 0.46D;
    /** O levantar (ticks): o corpo mole vai pra pose de bruços (cliente), depois a animação "ragdoll_getup" (1,55 s). */
    public static final int BLEND = 8;
    public static final int GETUP = 31;
    /** Deitado: o mínimo antes de falar/levantar; sozinho (ninguém armado perto) levanta depois disso. */
    static final int MIN_LIE = 30;
    static final int ALONE = 160;
    /** Depois do "shoot me" sem ninguém atirar; depois do último tiro; e o máximo deitado. */
    static final int AFTER_TALK = 100;
    static final int AFTER_SHOT = 50;
    static final int MAX_LIE = 600;
    /** Tantas balas caído e ele já levanta (com MUITA raiva). */
    static final int SHOTS_TO_RISE = 10;
    /** Depois de levantar: um tempo sem voar de novo (a raiva). */
    static final int COOLDOWN = 400;
    /** Bala nele caído: metade do dano (LM: "da meio dano nele"); em pé continua um décimo. */
    public static final float DOWN_DAMAGE = 0.5F;
    /** A conversa: de quão longe ele fala com quem chega armado. */
    static final double TALK_FAR = 12D;
    /** Parado = andando menos que isso (blocos/tick). */
    static final double STILL = 0.06D;

    private Ragdoll() {
    }

    /**
     * Os números da cena (sincronizados). t0 = o tick do tiro; d = pra onde voa (unitário, no chão); speed/up = o
     * empurrão; lieAt = o tick em que deitou (-1 voando); upAt = o tick em que começa a levantar (-1 deitado).
     */
    public record Params(long t0, float dx, float dz, float speed, float up, int seed, long lieAt, long upAt) {

        public CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putLong("T0", t0);
            t.putFloat("DX", dx);
            t.putFloat("DZ", dz);
            t.putFloat("Speed", speed);
            t.putFloat("Up", up);
            t.putInt("Seed", seed);
            t.putLong("Lie", lieAt);
            t.putLong("Rise", upAt);
            return t;
        }

        @Nullable
        public static Params of(CompoundTag t) {
            if (t == null || !t.contains("T0")) return null;
            return new Params(t.getLong("T0"), t.getFloat("DX"), t.getFloat("DZ"), t.getFloat("Speed"), t.getFloat("Up"),
                    t.getInt("Seed"), t.getLong("Lie"), t.getLong("Rise"));
        }

        Params lie(long now) {
            return new Params(t0, dx, dz, speed, up, seed, now, upAt);
        }

        Params rise(long now) {
            return new Params(t0, dx, dz, speed, up, seed, lieAt < 0 ? now : lieAt, now);
        }

        public boolean lying() {
            return lieAt >= 0;
        }

        public boolean rising() {
            return upAt >= 0;
        }
    }

    /** O que só o servidor guarda enquanto dura (quem atirou, quantas balas, a conversa). */
    static final class Run {
        @Nullable
        UUID shooter;
        int shots;
        long lastShot = Long.MIN_VALUE;
        /** 0 = calado; 1 = "I love pain"; 2 = "shoot me". */
        int talk;
        long talkAt;
        long gunSeen = Long.MIN_VALUE;
        boolean melee;
    }

    // ---------------------------------------------------------------- começa

    /** Bala de arma de mod nele parado no chão, fora de qualquer outra coisa: voa. */
    static boolean canLaunch(FroggydudeEntity f) {
        if (f.tickCount < f.ragdollCooldownUntil()) return false;
        FroggyState s = f.getFroggyState();
        if (s != FroggyState.IDLE && s != FroggyState.CHASE && s != FroggyState.TAUNT) return false;
        if (!f.onGround() || f.isInWater() || f.isInLava() || f.isPassenger() || f.isVehicle()) return false;
        if (f.isFrenzyActive() || f.isReviving() || f.getPinnedVictim() != null || f.isClimbing()) return false;
        Vec3 v = f.getDeltaMovement();
        return v.x * v.x + v.z * v.z < STILL * STILL;
    }

    static void launch(FroggydudeEntity f, DamageSource source) {
        Vec3 dir = null;
        Entity direct = source.getDirectEntity();
        if (direct != null && direct != source.getEntity()) {
            Vec3 v = direct.getDeltaMovement();
            if (v.x * v.x + v.z * v.z > 1.0E-4D) dir = new Vec3(v.x, 0, v.z).normalize();
        }
        Entity shooter = source.getEntity();
        if (dir == null && shooter != null) {
            Vec3 d = f.position().subtract(shooter.position());
            if (d.x * d.x + d.z * d.z > 1.0E-4D) dir = new Vec3(d.x, 0, d.z).normalize();
        }
        RandomSource r = f.getRandom();
        if (dir == null) {
            float a = r.nextFloat() * Mth.TWO_PI;
            dir = new Vec3(Mth.cos(a), 0, Mth.sin(a));
        }
        float speed = (float) (SPEED * (0.9D + 0.2D * r.nextDouble()));
        float up = (float) (UP * (0.92D + 0.16D * r.nextDouble()));
        Params p = new Params(f.level().getGameTime(), (float) dir.x, (float) dir.z, speed, up, r.nextInt(), -1L, -1L);
        f.beginRagdoll(p, shooter instanceof Player pl ? pl.getUUID() : null);
    }

    /** O tiro do comando /froggydude ragdoll: pra longe de quem mandou (ou pra um lado qualquer). */
    public static boolean debugLaunch(FroggydudeEntity f, @Nullable Entity from) {
        if (f.getFroggyState() == FroggyState.RAGDOLL || f.isReviving()) return false;
        RandomSource r = f.getRandom();
        Vec3 dir = from == null ? null : f.position().subtract(from.position()).multiply(1, 0, 1);
        if (dir == null || dir.lengthSqr() < 1.0E-4D) {
            float a = r.nextFloat() * Mth.TWO_PI;
            dir = new Vec3(Mth.cos(a), 0, Mth.sin(a));
        }
        dir = dir.normalize();
        Params p = new Params(f.level().getGameTime(), (float) dir.x, (float) dir.z, (float) SPEED, (float) UP, r.nextInt(),
                -1L, -1L);
        f.beginRagdoll(p, from instanceof Player pl ? pl.getUUID() : null);
        return true;
    }

    // ---------------------------------------------------------------- apanhando

    /** Depois do super.hurt (machucou): bala nele deitado conta; golpe de perto levanta ele na hora. */
    static void onHurt(FroggydudeEntity f, DamageSource source, boolean gunshot) {
        Params p = f.ragdollParams();
        Run run = f.ragdollRun();
        if (f.getFroggyState() != FroggyState.RAGDOLL || p == null || run == null) {
            if (gunshot && canLaunch(f)) launch(f, source);
            return;
        }
        if (!p.lying() || p.rising()) return;
        long now = f.level().getGameTime();
        Entity attacker = source.getEntity();
        if (attacker instanceof Player pl) run.shooter = pl.getUUID();
        if (gunshot) {
            run.shots++;
            run.lastShot = now;
        } else if (attacker instanceof Player && source.getDirectEntity() == attacker) {
            run.melee = true;
        }
    }

    // ---------------------------------------------------------------- cada tick (servidor)

    static void tick(FroggydudeEntity f) {
        Params p = f.ragdollParams();
        Run run = f.ragdollRun();
        if (p == null || run == null) {
            f.endRagdoll(false);
            return;
        }
        f.getNavigation().stop();
        long now = f.level().getGameTime();
        if (!p.lying()) {
            // voando: cai na água/lava = levanta já (nada de corpo mole boiando); no chão = deitou
            if (f.isInWater() || f.isInLava()) {
                f.setRagdollParams(p.rise(now));
                return;
            }
            if ((f.onGround() && now - p.t0() >= 3) || now - p.t0() > 100) {
                f.setRagdollParams(p.lie(now));
                f.playSound(SoundEvents.PLAYER_BIG_FALL, 1.3F, 0.75F);
                f.brainLog(String.format("ragdoll: caí a %.1f blocos de onde levei o tiro, %d ticks no ar",
                        Math.sqrt(f.distanceToSqr(f.ragdollFrom)), now - p.t0()));
            }
            return;
        }
        if (p.rising()) {
            if (now - p.upAt() >= BLEND + GETUP) f.endRagdoll(true);
            return;
        }
        long lay = now - p.lieAt();
        Player gun = gunman(f);
        if (gun != null) run.gunSeen = now;
        boolean rise = false;
        if (lay >= MIN_LIE && gun != null && !speaking(f)) {
            // a conversa do vídeo: chegou perto armado -> "Yeah, I love pain"; uns segundos depois -> "Yeah, shoot me..."
            if (run.talk == 0) {
                if (f.speak(VoiceSituation.DOWN_LOVE_PAIN)) {
                    run.talk = 1;
                    run.talkAt = now;
                    f.brainLog("ragdoll: chegou armado - \"I love pain\"");
                }
            } else if (run.talk == 1 && now - run.talkAt >= VoiceLine.LOVE_PAIN_4.ticks + 30) {
                if (f.speak(VoiceSituation.DOWN_SHOOT_ME)) {
                    run.talk = 2;
                    run.talkAt = now;
                    f.brainLog("ragdoll: \"Yeah, shoot me...\"");
                }
            }
        }
        if (run.melee) rise = true;
        if (run.shots >= SHOTS_TO_RISE && now - run.lastShot >= 8) rise = true;
        if (run.shots > 0 && now - run.lastShot >= AFTER_SHOT && !speaking(f)) rise = true;
        if (run.talk == 2 && run.shots == 0 && now - run.talkAt >= VoiceLine.SHOOT_ME.ticks + AFTER_TALK) rise = true;
        long quiet = now - Math.max(p.lieAt(), Math.max(run.gunSeen, run.talkAt));
        if (lay >= MIN_LIE && run.shots == 0 && quiet >= ALONE) rise = true;
        if (lay >= MAX_LIE) rise = true;
        if (rise) {
            f.brainLog("ragdoll: levantando (" + run.shots + " balas caído, " + lay + " ticks deitado)");
            f.setRagdollParams(p.rise(now));
            if (run.shots > 0 || run.melee) f.ribbit(2.2F);
        }
    }

    private static boolean speaking(FroggydudeEntity f) {
        return f.vocalCooldown() > 0;
    }

    /** O jogador mais perto, de arma de fogo na mão, que ele vê (até TALK_FAR). */
    @Nullable
    private static Player gunman(FroggydudeEntity f) {
        Player best = null;
        double bestD = TALK_FAR * TALK_FAR;
        for (Player pl : f.level().players()) {
            if (pl.isSpectator() || !pl.isAlive() || !ModWeapons.isGun(pl.getMainHandItem())) continue;
            double d = pl.distanceToSqr(f);
            if (d < bestD && f.hasLineOfSight(pl)) {
                best = pl;
                bestD = d;
            }
        }
        return best;
    }

    /** Quem atirou (vivo, no mundo dele), pra caçar depois de levantar. */
    @Nullable
    static Player shooter(FroggydudeEntity f, Run run) {
        if (run.shooter == null || !(f.level() instanceof ServerLevel sl)) return null;
        Player pl = sl.getPlayerByUUID(run.shooter);
        return pl != null && pl.isAlive() && !pl.isSpectator() && !pl.isCreative() ? pl : null;
    }
}
