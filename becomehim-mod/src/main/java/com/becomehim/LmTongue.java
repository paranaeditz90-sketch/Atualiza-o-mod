package com.becomehim;

import com.becomehim.network.LmNetwork;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A língua do FroggyDude no jogador (Become HIM...), do lado do servidor: a captura e o arremesso do mod da IA
 * (TongueCapture, 0.7.33, as réplicas que o LM aprovou), com o tempo de segurar na mão de quem joga.
 *
 * - Atacou: a língua sai (0,3 s), pega e ERGUE a vítima até 4 blocos, na frente, a 2,4 blocos (até 1,0 s).
 * - Segurando: ela fica pendurada na frente, balançando de leve, e vai junto pra onde o jogador anda e olha.
 * - Atacou de novo: puxa pra boca (0,4 s) e morde.
 * - Pulou: pega impulso pro outro lado e chicoteia (0,6 s); solta e ela voa uns 40 blocos pro lado sem parede.
 * - Nada em 10 s: solta onde estiver.
 * Tudo em ticks (20 por segundo), contados da idade do golpe. Nada entra em bloco: se o próximo ponto bate, ela fica
 * onde está (e se ficar presa longe, solta).
 */
public final class LmTongue {

    enum Step { SHOOTING, HOLDING, PULLED, RELEASED, LOST }

    /** A língua chega nela. */
    public static final int HIT = 6;
    /** Já está lá em cima. */
    public static final int LIFT_END = 20;
    /** O puxão até a boca. */
    public static final int PULL = 8;
    /** Arremesso (a partir de quando pulou): o impulso, o chicote e a soltura, e quando o corpo termina de desvirar. */
    public static final int T_WIND = 4;
    public static final int T_REL = 12;
    public static final int T_END = 32;

    /** Até onde a língua vai (blocos). */
    public static final double RANGE = 8.0D;
    private static final double HOLD_DIST = 2.4D;
    private static final double LIFT = 4.0D;
    private static final double PULL_DIST = 0.9D;
    private static final double PULL_UP = 0.4D;
    private static final int SWAY_TICKS = 26;
    private static final double FLING_OUT = 3.85D;
    private static final double FLING_UP = 0.85D;
    public static final int FLING_TICKS = 45;
    private static final float BODY_SHARE = 0.54F;
    private static final float BODY_END = 0.69F;
    private static final float WIND = 30F;
    private static final float[] SWEEPS = {130F, 115F, 145F, 100F, 160F, 85F, 175F, 70F, 190F};
    /** Presa longe de onde devia (parede no caminho) por mais que isso: solta. */
    private static final int STUCK = 10;

    private final ServerPlayer owner;
    private LivingEntity victim;
    private TongueHold hold;
    private double startDist;
    private double startY;
    private double lift;
    private Vec3 last;
    private int stuck;
    private int pullAt = -1;
    private double pullFromY;
    // arremesso
    private int throwAt = -1;
    private Vec3 base;
    private float yaw0;
    private float sweep;
    private boolean dropOnly;
    private Vec3 flingVel;
    private LivingEntity flung;

    LmTongue(ServerPlayer owner) {
        this.owner = owner;
    }

    LivingEntity victim() {
        return victim;
    }

    float sweep() {
        return sweep;
    }

    boolean pulling() {
        return pullAt >= 0;
    }

    /** Ainda tem alguém na língua? */
    boolean holding() {
        return hold != null && victim != null && !hold.isRemoved() && victim.getVehicle() == hold && victim.isAlive();
    }

    // ---------------------------------------------------------------- pegar

    /** A língua chegou: prende no gancho. false = não deu (errou). */
    boolean grab(LivingEntity target) {
        release();
        Vec3 to = flat(target.position().subtract(owner.position()));
        startDist = Math.max(HOLD_DIST, to.length());
        startY = target.getY();
        lift = freeLift(target, at(HOLD_DIST, owner.getY()));
        hold = TongueHold.grab(owner, target);
        if (hold == null) return false;
        victim = target;
        last = target.position();
        target.fallDistance = 0F;
        if (target instanceof ServerPlayer p) p.setSprinting(false);
        return true;
    }

    /** Atacou segurando: puxa pra boca (só depois de erguer, uma vez). */
    boolean pull(int age) {
        if (age < LIFT_END || pullAt >= 0 || throwAt >= 0 || !holding()) return false;
        pullAt = age;
        pullFromY = last.y;
        return true;
    }

    /**
     * Pulou segurando: escolhe o lado do arremesso (o da réplica primeiro). Sem lado livre, gira e só larga (não joga
     * contra a parede). false = não dá agora.
     */
    boolean startThrow(int age) {
        if (age < LIFT_END || pullAt >= 0 || throwAt >= 0 || !holding()) return false;
        base = owner.position();
        yaw0 = owner.getYRot();
        sweep = findSweep();
        dropOnly = sweep == 0F;
        if (dropOnly) sweep = owner.getRandom().nextBoolean() ? 100F : -100F;
        throwAt = 0;
        return true;
    }

    // ---------------------------------------------------------------- cada tick

    /** Segurando (a idade do golpe). */
    Step tick(int age) {
        if (age < HIT) return Step.SHOOTING;
        if (!holding()) {
            release();
            return Step.LOST;
        }
        Vec3 want;
        if (pullAt >= 0) {
            double k = Mth.clamp((age - pullAt) / (double) PULL, 0D, 1D);
            k = k * k;
            want = atY(HOLD_DIST - (HOLD_DIST - PULL_DIST) * k, pullFromY + (owner.getY() + PULL_UP - pullFromY) * k);
        } else if (age <= LIFT_END) {
            double k = easeOut((age - HIT) / (double) (LIFT_END - HIT));
            double top = owner.getY() + lift;
            want = atY(startDist + (HOLD_DIST - startDist) * k, startY + (top - startY) * k);
        } else {
            double s = Math.sin((age - LIFT_END) * Math.PI * 2.0D / SWAY_TICKS);
            want = atY(HOLD_DIST + 0.05D * s, owner.getY() + lift + 0.10D * s);
        }
        follow(want);
        if (stuck > STUCK) {
            release();
            return Step.LOST;
        }
        if (pullAt >= 0 && age - pullAt >= PULL) return Step.PULLED;
        return Step.HOLDING;
    }

    /** No arremesso (idade contada de quando pulou). */
    Step tickThrow(int k) {
        if (k == T_REL + 1) fling();
        if (k > T_REL) return Step.RELEASED;
        if (!holding()) {
            release();
            return Step.LOST;
        }
        float kk = Math.min(1F, k / (float) T_REL);
        Vec3 want = round(yaw0 + sweepAngle(k, sweep), HOLD_DIST + 0.25D * kk * kk, base.y + lift + 0.2D * kk * kk);
        follow(want);
        if (k >= T_REL) letGo();
        return Step.HOLDING;
    }

    private void follow(Vec3 want) {
        if (free(victim, want)) last = want;
        double dx = want.x - last.x, dy = want.y - last.y, dz = want.z - last.z;
        stuck = dx * dx + dy * dy + dz * dz > 9D ? stuck + 1 : 0;
        hold.moveVictim(last);
        victim.fallDistance = 0F;
    }

    /** Solta no ponto da volta; a velocidade vai no tick seguinte (depois de descer do gancho). */
    private void letGo() {
        flung = victim;
        Vec3 d = dirOf(yaw0 + sweep + 90F * Math.signum(sweep));
        flingVel = new Vec3(d.x * FLING_OUT, FLING_UP, d.z * FLING_OUT);
        Vec3 at = last;
        release();
        if (flung instanceof ServerPlayer p) p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
    }

    private void fling() {
        LivingEntity v = flung;
        flung = null;
        if (v == null || !v.isAlive() || dropOnly) return;
        v.fallDistance = 0F;
        v.setDeltaMovement(flingVel);
        if (v instanceof ServerPlayer p) {
            // direto pro cliente dela: pelo caminho normal o servidor aplica um tick de atrito antes e ela voa menos
            p.connection.send(new ClientboundSetEntityMotionPacket(p.getId(), flingVel));
        } else {
            v.hasImpulse = true;
            v.hurtMarked = true;
        }
        LmNetwork.sendFling(v, FLING_TICKS, (float) flingVel.x, (float) flingVel.z);
    }

    /** Solta quem estiver na língua (onde estiver) e desfaz o gancho. */
    void release() {
        if (victim != null) {
            if (hold != null && victim.getVehicle() == hold) victim.stopRiding();
            victim.fallDistance = 0F;
        }
        if (hold != null) hold.discard();
        hold = null;
        victim = null;
    }

    // ---------------------------------------------------------------- o lado do arremesso

    private float findSweep() {
        float first = owner.getRandom().nextBoolean() ? 1F : -1F;
        for (float s : SWEEPS) {
            for (float sg : new float[]{first, -first}) {
                if (throwClear(s * sg)) return s * sg;
            }
        }
        return 0F;
    }

    private boolean throwClear(float s) {
        float sg = Math.signum(s);
        double y = base.y + lift + 0.2D;
        for (float a = -sg * WIND; sg > 0 ? a <= s : a >= s; a += sg * 10F) {
            if (!free(victim, round(yaw0 + a, HOLD_DIST + 0.25D, y))) return false;
        }
        Vec3 pos = round(yaw0 + s, HOLD_DIST + 0.25D, y);
        Vec3 d = dirOf(yaw0 + s + 90F * sg);
        Vec3 v = new Vec3(d.x * FLING_OUT, FLING_UP, d.z * FLING_OUT);
        double flat = 0;
        for (int tick = 0; tick < 60; tick++) {
            for (int sub = 1; sub <= 4; sub++) {
                Vec3 p = pos.add(v.scale(sub / 4.0D));
                if (!free(victim, p)) {
                    boolean floor = v.y < 0 && free(victim, p.add(0, 0.6D, 0));
                    return floor && flat + Math.sqrt(v.x * v.x + v.z * v.z) * sub / 4.0D >= 18.0D;
                }
            }
            flat += Math.sqrt(v.x * v.x + v.z * v.z);
            pos = pos.add(v);
            v = new Vec3(v.x * 0.91D, (v.y - 0.08D) * 0.98D, v.z * 0.91D);
        }
        return true;
    }

    // ---------------------------------------------------------------- os ângulos (servidor e cliente)

    /** Onde ela está na volta (graus a partir da frente), com o impulso e o chicote; k = ticks desde que pulou. */
    public static float sweepAngle(float k, float sweep) {
        float sg = Math.signum(sweep);
        if (k <= 0F) return 0F;
        if (k <= T_WIND) return -sg * WIND * (float) smooth(k / T_WIND);
        float t = Math.min(1F, (k - T_WIND) / (T_REL - T_WIND));
        return -sg * WIND + (sweep + sg * WIND) * t * t;
    }

    /** O corpo inteiro no arremesso (graus a partir da frente); a cintura faz o resto (waistTwist). */
    public static float bodyAngle(float k, float sweep) {
        if (k <= T_WIND) return 0F;
        if (k <= T_REL) {
            float t = (k - T_WIND) / (T_REL - T_WIND);
            return BODY_SHARE * sweep * t * t;
        }
        float t = (float) smooth(Math.min(1.0D, (k - T_REL) / (double) (T_END - 8 - T_REL)));
        return sweep * (BODY_SHARE + (BODY_END - BODY_SHARE) * t);
    }

    /** Quanto a cintura gira por cima do corpo (graus; + = pra direita dele). */
    public static float waistTwist(float k, float sweep) {
        if (sweep == 0F || k <= 0F) return 0F;
        float sg = Math.signum(sweep);
        if (k <= T_WIND) return -sg * WIND * (float) smooth(k / T_WIND);
        if (k <= T_REL) {
            float t = (k - T_WIND) / (T_REL - T_WIND);
            float around = -sg * WIND + (sweep + sg * WIND) * t * t;
            return around - BODY_SHARE * sweep * t * t;
        }
        float t = (float) smooth(Math.min(1.0D, (k - T_REL) / (double) (T_END - 8 - T_REL)));
        return (1F - BODY_SHARE) * sweep * (1F - t);
    }

    /** Quantos graus por tick ela anda na volta agora (a língua mole fica pra trás). */
    public static float sweepRate(float k, float sweep) {
        if (sweep == 0F || k <= 0F || k >= T_REL) return 0F;
        float sg = Math.signum(sweep);
        if (k <= T_WIND) {
            float t = k / T_WIND;
            return -sg * WIND * 6F * t * (1F - t) / T_WIND;
        }
        float t = (k - T_WIND) / (T_REL - T_WIND);
        return (sweep + sg * WIND) * 2F * t / (T_REL - T_WIND);
    }

    // ---------------------------------------------------------------- geometria

    private double freeLift(LivingEntity target, Vec3 at) {
        for (double h = LIFT; h >= 0.0D; h -= 0.25D) {
            AABB box = boxAt(target, at.add(0, h, 0));
            if (target.level().noCollision(target, box.expandTowards(0, -h, 0))) return h;
        }
        return 0.0D;
    }

    private static boolean free(LivingEntity e, Vec3 feet) {
        return e.level().noCollision(e, boxAt(e, feet));
    }

    private static AABB boxAt(LivingEntity e, Vec3 feet) {
        return e.getBoundingBox().move(feet.subtract(e.position()));
    }

    /** Na frente de quem segura (pra onde olha), a d blocos, na altura y. */
    private Vec3 atY(double d, double y) {
        Vec3 dir = dirOf(owner.getYRot());
        return new Vec3(owner.getX() + dir.x * d, y, owner.getZ() + dir.z * d);
    }

    private Vec3 at(double d, double y) {
        return atY(d, y);
    }

    private Vec3 round(float yaw, double d, double y) {
        Vec3 v = dirOf(yaw);
        return new Vec3(base.x + v.x * d, y, base.z + v.z * d);
    }

    private static Vec3 flat(Vec3 v) {
        return new Vec3(v.x, 0, v.z);
    }

    /** Direção do yaw do jogo (0 = sul, 90 = oeste). */
    private static Vec3 dirOf(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    private static double easeOut(double k) {
        k = Mth.clamp(k, 0.0D, 1.0D);
        return 1.0D - Math.pow(1.0D - k, 2.2D);
    }

    private static double smooth(double k) {
        k = Mth.clamp(k, 0.0D, 1.0D);
        return k * k * (3.0D - 2.0D * k);
    }
}
