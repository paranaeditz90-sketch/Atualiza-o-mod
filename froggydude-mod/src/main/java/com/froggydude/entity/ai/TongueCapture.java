package com.froggydude.entity.ai;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.TongueHold;
import com.froggydude.network.ModNetwork;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (v0.7.33) A captura e o arremesso com a língua, do lado do servidor: o caminho da vítima presa no gancho
 * (TongueHold) e, no arremesso, o giro do corpo dele e a soltura.
 *
 * Os tempos e as medidas são os das réplicas aprovadas pelo LM em 09/11 (viking 11-lingua-captura.md):
 * - captura (v8): mira 0-0,2 s | estalo e pega 0,3 | ERGUE 0,3-1,0 (os pés a 4 blocos, chega a 2,4 dele) |
 *   segura 1,0-2,9 balançando de leve | puxa 2,9-3,3 até a boca -> morde, arranca o braço ou derruba;
 * - arremesso (v3, bem raro): pega 0,3 | DERRUBA e arrasta 0,3-0,9 (deitada, até 2,9 dele) | ergue 0,9-1,6 |
 *   segura 1,6-3,6 | vira o tronco 3,6-4,2 (pega impulso 30° pro outro lado e chicoteia) | solta 4,2: voa uns
 *   40 blocos, de 4 a 10 de altura, pro lado que não tem parede, árvore nem teto (LM: "ele mira em uma direção
 *   que não tenha obstáculos que possam prender o jogador").
 * Tudo em ticks (20 por segundo). Nada entra em bloco: se o próximo ponto bate, ela fica onde está.
 */
public final class TongueCapture {

    enum Step { HOLDING, PULLED, RELEASED, LOST }

    static final int HIT = 6;
    // captura
    private static final int LIFT_END = 20;
    private static final int PULL_START = 58;
    private static final int PULL_END = 66;
    static final int RELEASE = 67;
    // arremesso
    private static final int T_DOWN = 18;
    private static final int T_LIFT = 32;
    private static final int T_HOLD = 72;
    private static final int T_WIND = 76;
    static final int T_REL = 84;
    private static final int T_UNWIND = 100;

    private static final double HOLD_DIST = 2.4D;
    private static final double LIFT = 4.0D;
    private static final double LYING_DIST = 2.9D;
    private static final double PULL_DIST = 0.9D;
    private static final double PULL_UP = 0.4D;
    /** O balanço pendurado: 1,3 s por volta, 0,05 pra frente e 0,10 pra cima (réplica). */
    private static final int SWAY_TICKS = 26;
    /**
     * A soltura (física do jogo: no ar a velocidade cai 9% por tick e a queda é (vy - 0,08) x 0,98). 3,85 blocos
     * por tick pra fora e 0,85 pra cima: sobe ~4,6 blocos acima de onde soltou (uns 9 acima do chão) e cai a uns
     * 40 blocos. O jogo não manda mais que 3,9 por eixo pro cliente.
     */
    private static final double FLING_OUT = 3.85D;
    private static final double FLING_UP = 0.85D;
    /** Quanto tempo o cliente deixa ela mole rodando no ar (o "ragdoll"), no máximo. */
    static final int FLING_TICKS = 45;
    /** Quanto do giro é o corpo (o resto é a cintura, no cliente): na réplica, -70 de -130. */
    private static final float BODY_SHARE = 0.54F;
    private static final float BODY_END = 0.69F;
    /** O impulso pro outro lado antes do chicote. */
    private static final float WIND = 30F;
    /** Giros testados pro arremesso (graus), o da réplica primeiro. */
    private static final float[] SWEEPS = {130F, 115F, 145F, 100F, 160F, 85F, 175F, 70F, 190F};

    private static final boolean DEBUG = Boolean.getBoolean("froggydude.debug");

    private final FroggydudeEntity froggy;
    private TongueHold hold;
    private LivingEntity victim;
    private boolean throwing;
    private Vec3 base;
    private Vec3 dir;
    private float yaw0;
    private double startDist;
    private double startY;
    private double lift;
    private Vec3 last;
    private boolean stoodUp;
    // arremesso
    private float sweep;
    /** Na hora de pegar não tinha mais lado livre: no fim do giro ele só larga (não joga contra a parede). */
    private boolean dropOnly;
    private Vec3 flingVel;
    private int flingAt = -1;
    private LivingEntity flung;

    TongueCapture(FroggydudeEntity froggy) {
        this.froggy = froggy;
    }

    LivingEntity victim() {
        return victim;
    }

    float sweep() {
        return sweep;
    }

    /** Ainda tem alguém na língua? */
    boolean holding() {
        return hold != null && victim != null && hold.isHolding() && victim.getVehicle() == hold && victim.isAlive();
    }

    // ---------------------------------------------------------------- plano

    private void frame(LivingEntity target) {
        base = froggy.position();
        Vec3 to = target.position().subtract(base);
        to = new Vec3(to.x, 0, to.z);
        dir = to.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0F, froggy.getYRot()).multiply(1, 0, 1).normalize()
                : to.normalize();
        yaw0 = yawOf(dir);
        startDist = Math.max(HOLD_DIST, Math.sqrt(to.x * to.x + to.z * to.z));
        startY = target.getY();
    }

    /**
     * Arremesso: acha um giro que leva ela pra um lado sem nada no caminho (a volta em torno dele lá em cima e o
     * voo de ~40 blocos). Sem lado livre, não tem arremesso (vira captura).
     */
    boolean planThrow(LivingEntity target) {
        frame(target);
        lift = freeLift(target, at(HOLD_DIST, 0));
        if (lift < 2.5D) return false;
        sweep = findSweep(target);
        return sweep != 0F;
    }

    /** O giro que leva ela pra um lado livre (o da réplica primeiro), ou 0 se não tem. */
    private float findSweep(LivingEntity target) {
        float first = froggy.getRandom().nextBoolean() ? 1F : -1F;
        for (float s : SWEEPS) {
            for (float sg : new float[]{first, -first}) {
                if (throwClear(target, s * sg)) return s * sg;
            }
        }
        return 0F;
    }

    private boolean throwClear(LivingEntity target, float s) {
        float sg = Math.signum(s);
        double y = startY + lift + 0.2D;
        // a volta lá em cima: do impulso pro outro lado até o ponto de soltar
        for (float a = -sg * WIND; sg > 0 ? a <= s : a >= s; a += sg * 10F) {
            if (!free(target, round(yaw0 + a, HOLD_DIST + 0.25D, y))) return false;
        }
        Vec3 pos = round(yaw0 + s, HOLD_DIST + 0.25D, y);
        Vec3 v = flingVelocity(s);
        double flat = 0;
        for (int tick = 0; tick < 60; tick++) {
            for (int sub = 1; sub <= 4; sub++) {
                Vec3 p = pos.add(v.scale(sub / 4.0D));
                if (!free(target, p)) {
                    // bateu embaixo, caindo: é o chão (vale se já foi longe); de lado ou em cima: parede/teto/árvore
                    boolean floor = v.y < 0 && free(target, p.add(0, 0.6D, 0));
                    return floor && flat + Math.sqrt(v.x * v.x + v.z * v.z) * sub / 4.0D >= 18.0D;
                }
            }
            flat += Math.sqrt(v.x * v.x + v.z * v.z);
            pos = pos.add(v);
            v = new Vec3(v.x * 0.91D, (v.y - 0.08D) * 0.98D, v.z * 0.91D);
        }
        return true;
    }

    private Vec3 flingVelocity(float s) {
        // tangente da volta no ponto de soltar: no sentido do giro
        Vec3 d = dirOf(yaw0 + s + 90F * Math.signum(s));
        return new Vec3(d.x * FLING_OUT, FLING_UP, d.z * FLING_OUT);
    }

    // ---------------------------------------------------------------- pegou

    /** A língua pegou: prende no gancho. Devolve false se não deu (aí conta como errou). */
    boolean grab(LivingEntity target, boolean throwIt) {
        release();
        throwing = throwIt;
        // de onde ela está AGORA (no arremesso o plano foi 0,3 s antes: ela pode ter corrido pro lado)
        frame(target);
        lift = freeLift(target, at(HOLD_DIST, 0));
        dropOnly = false;
        if (throwIt && (lift < 2.5D || !throwClear(target, sweep))) {
            float again = lift < 2.5D ? 0F : findSweep(target);
            if (again != 0F) {
                sweep = again;
                froggy.setThrowSweep(again);
            } else {
                dropOnly = true;
            }
        }
        hold = TongueHold.grab(froggy, target);
        if (hold == null) return false;
        victim = target;
        last = target.position();
        stoodUp = !throwIt;
        froggy.setCaptureId(target.getId());
        target.fallDistance = 0F;
        if (target instanceof ServerPlayer p) {
            p.setSprinting(false);
            if (throwIt) {
                // derrubada: deitada, a câmera virada pra cara dele, até começar a erguer
                p.setForcedPose(Pose.SWIMMING);
                ModNetwork.sendEffects(p, 0.9F, 12, T_DOWN - HIT, froggy.getId());
            } else {
                ModNetwork.sendEffects(p, 0.6F, 14, RELEASE - HIT + 2, -1);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- cada tick

    Step tick(int t) {
        if (flingAt >= 0) {
            if (t >= flingAt) fling();
            froggy.setFaceYawSynced(yaw0 + bodyAngle(t));
            if (DEBUG && flung != null && t % 2 == 0) {
                System.out.printf("[FROGGYDEBUG] voo t=%d pos=%.2f %.2f %.2f vel=%.2f %.2f %.2f chao=%s%n", t, flung.getX(),
                        flung.getY() - startY, flung.getZ(), flung.getDeltaMovement().x, flung.getDeltaMovement().y,
                        flung.getDeltaMovement().z, flung.onGround());
            }
            return Step.RELEASED;
        }
        if (!holding()) {
            release();
            return Step.LOST;
        }
        // empurrado no meio (espada com repulsão...): ela vai junto, na mesma distância dele
        base = froggy.position();
        if (!stoodUp && t >= T_DOWN) {
            stoodUp = true;
            if (victim instanceof ServerPlayer p) {
                // levantou do chão: fica em pé de novo, só os controles presos
                p.setForcedPose(null);
                ModNetwork.sendEffects(p, 0.5F, 8, T_REL - t + 2, -1);
            }
        }
        Vec3 want = throwing ? throwPath(t) : capturePath(t);
        if (free(victim, want)) last = want;
        hold.moveVictim(last);
        if (DEBUG && (t % 5 == 0 || t == HIT + 1)) {
            System.out.printf("[FROGGYDEBUG] lingua t=%d pes=%.2f acima do chao, %.2f dele (quer %.2f/%.2f) vitima=%.2f %.2f%n",
                    t, last.y - startY, horizontal(last), want.y - startY, horizontal(want), victim.getY() - startY,
                    horizontal(victim.position()));
        }
        victim.fallDistance = 0F;
        // olha o peito dela (a cabeça sobe até -50° no cliente; o resto do ângulo é a língua)
        froggy.getLookControl().setLookAt(victim.getX(), victim.getY() + victim.getBbHeight() * 0.65D, victim.getZ(),
                90F, 90F);
        if (throwing) {
            froggy.setFaceYawSynced(yaw0 + bodyAngle(t));
            if (t >= T_REL) {
                letGo(t);
                return Step.RELEASED;
            }
        } else {
            froggy.setFaceYawSynced(yaw0);
            if (t >= RELEASE) return Step.PULLED;
        }
        return Step.HOLDING;
    }

    private Vec3 capturePath(int t) {
        if (t <= LIFT_END) {
            double k = easeOut((t - HIT) / (double) (LIFT_END - HIT));
            return at(startDist + (HOLD_DIST - startDist) * k, lift * k);
        }
        if (t <= PULL_START) return sway(t - LIFT_END);
        double k = easeIn(Math.min(1.0D, (t - PULL_START) / (double) (PULL_END - PULL_START)));
        double top = startY + lift;
        double end = base.y + PULL_UP;
        return atY(HOLD_DIST - (HOLD_DIST - PULL_DIST) * k, top + (end - top) * k);
    }

    private Vec3 throwPath(int t) {
        if (t <= T_DOWN) {
            // o tranco tira ela do chão um tico, ela cai e é arrastada até ele
            double k = (t - HIT) / (double) (T_DOWN - HIT);
            double kf = Math.min(1.0D, k / 0.75D);
            double hop = 0.35D * Math.sin(Math.PI * kf) + (k > 0.75D ? 0.12D * Math.sin(Math.PI * (k - 0.75D) / 0.25D) : 0);
            return at(startDist + (LYING_DIST - startDist) * smooth(kf), hop);
        }
        if (t <= T_LIFT) {
            double k = easeOut((t - T_DOWN) / (double) (T_LIFT - T_DOWN));
            return at(LYING_DIST + (HOLD_DIST - LYING_DIST) * k, lift * k);
        }
        if (t <= T_HOLD) return sway(t - T_LIFT);
        double k = Math.min(1.0D, (t - T_HOLD) / (double) (T_REL - T_HOLD));
        return round(yaw0 + sweepAngle(t), HOLD_DIST + 0.25D * k * k, startY + lift + 0.2D * k * k);
    }

    private Vec3 sway(int ticks) {
        double s = Math.sin(ticks * Math.PI * 2.0D / SWAY_TICKS);
        return at(HOLD_DIST + 0.05D * s, lift + 0.10D * s);
    }

    /** Arremesso: onde ela está na volta em torno dele (graus a partir de onde estava), com o impulso e o chicote. */
    private float sweepAngle(int t) {
        float sg = Math.signum(sweep);
        if (t <= T_HOLD) return 0F;
        if (t <= T_WIND) return -sg * WIND * (float) smooth((t - T_HOLD) / (double) (T_WIND - T_HOLD));
        float k = Math.min(1F, (t - T_WIND) / (float) (T_REL - T_WIND));
        return -sg * WIND + (sweep + sg * WIND) * k * k;
    }

    /** O corpo dele no arremesso (graus a partir de virado pra ela): a cintura (cliente) faz o resto. */
    private float bodyAngle(int t) {
        if (t <= T_WIND) return 0F;
        if (t <= T_REL) {
            float k = (t - T_WIND) / (float) (T_REL - T_WIND);
            return BODY_SHARE * sweep * k * k;
        }
        float k = (float) smooth(Math.min(1.0D, (t - T_REL) / (double) (T_UNWIND - T_REL)));
        return sweep * (BODY_SHARE + (BODY_END - BODY_SHARE) * k);
    }

    /** Arremesso: solta a língua no ponto da volta; a velocidade vai no tick seguinte (depois de descer do gancho). */
    private void letGo(int t) {
        flung = victim;
        flingVel = flingVelocity(sweep);
        flingAt = t + 1;
        Vec3 at = last;
        release();
        if (flung instanceof ServerPlayer p) {
            p.setForcedPose(null);
            // descer do gancho só muda a posição no servidor: o cliente ficaria onde ele achava que estava (um tico
            // atrás na volta) e voaria de lá. Teleporta pro ponto da soltura; a velocidade vai no tick seguinte.
            p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        }
    }

    private void fling() {
        LivingEntity v = flung;
        flingAt = Integer.MAX_VALUE;
        if (v == null || !v.isAlive() || dropOnly) return;
        v.fallDistance = 0F;
        v.setDeltaMovement(flingVel);
        if (v instanceof ServerPlayer p) {
            // direto pro cliente dela: pelo caminho normal (hurtMarked) o servidor ainda aplica um tick de atrito do ar
            // antes de mandar, e ela voava ~10% menos
            p.connection.send(new ClientboundSetEntityMotionPacket(p.getId(), flingVel));
        } else {
            v.hasImpulse = true;
            v.hurtMarked = true;
        }
        ModNetwork.sendFling(v, FLING_TICKS, (float) flingVel.x, (float) flingVel.z);
    }

    // ---------------------------------------------------------------- soltar

    /** Solta quem estiver na língua (onde estiver) e desfaz o gancho. */
    void release() {
        if (froggy.getCaptureId() >= 0) froggy.setCaptureId(-1);
        if (victim != null) {
            if (victim.getVehicle() == hold && hold != null) victim.stopRiding();
            victim.fallDistance = 0F;
            if (victim instanceof ServerPlayer p) {
                if (p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
                ModNetwork.sendRelease(p);
            }
        }
        if (hold != null) hold.discard();
        hold = null;
        victim = null;
    }

    /** Fim do ataque: solta tudo e esquece o arremesso. */
    void reset() {
        release();
        flingAt = -1;
        flung = null;
        throwing = false;
        dropOnly = false;
        sweep = 0F;
    }

    // ---------------------------------------------------------------- geometria

    /** Quanto dá pra erguer (até 4 blocos) no ponto onde ela vai ficar pendurada, sem bater em teto. */
    private double freeLift(LivingEntity target, Vec3 at) {
        for (double h = LIFT; h >= 0.0D; h -= 0.25D) {
            AABB box = boxAt(target, at.add(0, h, 0));
            if (target.level().noCollision(target, box.expandTowards(0, -h, 0))) return h;
        }
        return 0.0D;
    }

    private boolean free(LivingEntity e, Vec3 feet) {
        Level level = e.level();
        return level.noCollision(e, boxAt(e, feet));
    }

    private static AABB boxAt(LivingEntity e, Vec3 feet) {
        return e.getBoundingBox().move(feet.subtract(e.position()));
    }

    /** Ponto na linha dele até a vítima: d blocos dele, y acima de onde ela estava no chão. */
    private Vec3 at(double d, double up) {
        return new Vec3(base.x + dir.x * d, startY + up, base.z + dir.z * d);
    }

    private Vec3 atY(double d, double y) {
        return new Vec3(base.x + dir.x * d, y, base.z + dir.z * d);
    }

    /** Ponto em volta dele: na direção yaw (graus do jogo), a d blocos, na altura y. */
    private Vec3 round(float yaw, double d, double y) {
        Vec3 v = dirOf(yaw);
        return new Vec3(base.x + v.x * d, y, base.z + v.z * d);
    }

    private double horizontal(Vec3 p) {
        double dx = p.x - base.x, dz = p.z - base.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Direção do yaw do jogo (0 = sul, 90 = oeste). */
    private static Vec3 dirOf(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    private static float yawOf(Vec3 d) {
        return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
    }

    private static double easeOut(double k) {
        k = Mth.clamp(k, 0.0D, 1.0D);
        return 1.0D - Math.pow(1.0D - k, 2.2D);
    }

    private static double easeIn(double k) {
        k = Mth.clamp(k, 0.0D, 1.0D);
        return k * k;
    }

    private static double smooth(double k) {
        k = Mth.clamp(k, 0.0D, 1.0D);
        return k * k * (3.0D - 2.0D * k);
    }

    // ---------------------------------------------------------------- cliente (a cintura no arremesso)

    /**
     * Quanto a cintura gira por cima do corpo no arremesso (graus, com o sinal do jogo: + = pra direita dele), pela
     * idade do estado. O corpo quem gira é o servidor (bodyAngle); os dois juntos dão a volta da vítima.
     */
    public static float waistTwist(float age, float sweep) {
        if (sweep == 0F || age <= T_HOLD) return 0F;
        float sg = Math.signum(sweep);
        if (age <= T_WIND) return -sg * WIND * (float) smooth((age - T_HOLD) / (T_WIND - T_HOLD));
        if (age <= T_REL) {
            float k = (age - T_WIND) / (T_REL - T_WIND);
            float around = -sg * WIND + (sweep + sg * WIND) * k * k;
            return around - BODY_SHARE * sweep * k * k;
        }
        float k = (float) smooth(Math.min(1.0D, (age - T_REL) / (double) (T_UNWIND - T_REL)));
        return (1F - BODY_SHARE) * sweep * (1F - k);
    }

    /** Cliente: a língua já soltou a vítima do arremesso? (idade do estado) */
    public static boolean thrownAt(float age) {
        return age >= T_REL;
    }

    /** Cliente: o tick em que a língua solta no arremesso (pra língua solta voltar pra boca). */
    public static int releaseTick() {
        return T_REL;
    }

    /**
     * Cliente: quantos graus por tick a vítima do arremesso anda em volta dele agora (com sinal), pra língua mole
     * ficar pra trás no giro.
     */
    public static float sweepRate(float age, float sweep) {
        if (sweep == 0F || age <= T_HOLD || age >= T_REL) return 0F;
        float sg = Math.signum(sweep);
        if (age <= T_WIND) {
            float k = (age - T_HOLD) / (T_WIND - T_HOLD);
            return -sg * WIND * 6F * k * (1F - k) / (T_WIND - T_HOLD);
        }
        float k = (age - T_WIND) / (T_REL - T_WIND);
        return (sweep + sg * WIND) * 2F * k / (T_REL - T_WIND);
    }
}
