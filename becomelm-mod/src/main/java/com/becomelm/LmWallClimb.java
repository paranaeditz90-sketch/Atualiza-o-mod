package com.becomelm;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * (v0.3.5) Correr de 4 pela parede, como o FroggyDude nos vídeos (LM: "em todos os vídeos ele sobe em
 * linha reta"; Grox RABIES 3:29,5-3:31). Galopando reto de frente pra uma parede, ele sobe por ela no mesmo
 * galope. Medido no vídeo, com o pilar de pedra de régua: ~10 blocos/s (9,5-11), uma passada a cada ~0,24 s.
 *
 * - Só de 4 (galope) e só de frente: até 35 graus de lado. De raspão continua correndo ao lado da parede.
 * - Segurando pra frente, sobe; soltando, escorrega devagar (o vanilla da escada); agachado, fica grudado.
 * - No topo ele passa por cima da beirada e fica em cima do bloco, até num pilar 1x1. Soltar o "pra frente"
 *   no último bloco também serve: ele termina de subir em vez de escorregar de volta.
 *
 * Os dois lados fazem a mesma conta pro "está na parede" (o mixin do onClimbable); a velocidade de subida
 * e a passada da beirada são do cliente de quem sobe, que é quem move o jogador.
 */
public final class LmWallClimb {

    /** Subindo: 0,5 bloco por tick = 10 b/s (o vídeo). A escada do vanilla é 0,12 (2,35 b/s). */
    public static final double CLIMB_VY = 0.5D;
    /** Só de frente pra parede: até 35 graus de lado. */
    private static final double FACING_COS = Math.cos(Math.toRadians(35D));
    /**
     * A beirada: enquanto a parede ainda bate nas pernas ele sobe 0,25 por tick e empurra 0,14 pra frente;
     * passou do topo, um pulinho de ~0,25 bloco e ~0,8 bloco pra dentro (cabe até num pilar 1x1).
     */
    private static final double MANTLE_VY = 0.25D;
    private static final double MANTLE_PUSH = 0.14D;
    /** A beirada não dura mais que isso (bateu a cabeça, a parede sumiu...). */
    private static final int MANTLE_MAX_TICKS = 8;
    /** Soltou o "pra frente" com o topo a menos disso acima dos pés: termina de subir (o olho já passou do topo). */
    private static final double TOP_REACH = 1.5D;
    /** O quanto a caixa "encosta" pra procurar a parede. */
    private static final double PROBE = 0.06D;

    // só o cliente de quem sobe mexe nisso (move roda pro LocalPlayer); de quem é (morreu, trocou de mundo: outro)
    private static int owner = Integer.MIN_VALUE;
    private static boolean wasClimbing;
    private static int mantleTicks;
    @Nullable
    private static Direction mantleDir;

    private LmWallClimb() {
    }

    /**
     * Pode subir parede agora: de 4, nada de voar, nadar, montar ou dormir. No meio de um golpe (arrastando alguém
     * na boca, montado, no monte) ou transformando também não: arrastando, a vítima ia junto pra dentro da parede.
     */
    private static boolean canClimb(Player p) {
        if (LmModes.get(p) != LmMode.GALLOP) return false;
        if (p.isSpectator() || p.getAbilities().flying || p.isFallFlying() || p.isPassenger() || p.isSleeping()) return false;
        if (LmAttacks.busy(p) || LmTransform.locked(p)) return false;
        return !p.isInWater() && !p.isInLava();
    }

    /**
     * No ar, encostado de frente numa parede que continua acima da cintura: está na parede (o onClimbable). No chão
     * não: lá o vanilla da escada frearia o galope encostado na parede (e quem sobe é o "pra frente", no move).
     */
    public static boolean onWall(Player p) {
        return climbingWall(p) != null;
    }

    /** A parede que ele está subindo agora (o corpo vira de frente pra ela); null fora da parede. */
    @Nullable
    public static Direction climbingWall(Player p) {
        if (p.onGround() || !canClimb(p)) return null;
        return wallAboveWaist(p);
    }

    @Nullable
    private static Direction wallAboveWaist(Player p) {
        AABB box = p.getBoundingBox();
        return wallInFront(p, box.minY + 0.5D, box.maxY - 0.05D);
    }

    /** Cliente de quem sobe, no começo do movimento de cada tick (o travel): a velocidade de subida e a beirada. */
    public static void move(Player p) {
        if (p.getId() != owner) {
            owner = p.getId();
            wasClimbing = false;
            mantleTicks = 0;
        }
        boolean climbing = false;
        boolean can = canClimb(p);
        if (!can || p.onGround()) mantleTicks = 0;
        if (can) {
            AABB box = p.getBoundingBox();
            Vec3 v = p.getDeltaMovement();
            boolean forward = p.zza > 0F;
            if (mantleTicks > 0) {
                mantle(p, box, v);
            } else if (forward && wallAboveWaist(p) != null) {
                p.setDeltaMovement(v.x, CLIMB_VY, v.z);
                climbing = true;
            } else if (!p.onGround() && v.y > 0D && (forward || (wasClimbing && !p.isShiftKeyDown()))) {
                // a parede acaba logo acima (segurando: na cintura, que a parede acima dela já sumiu; soltando o
                // "pra frente": no último bloco e meio) e ele vinha subindo. Agachado é segurar na parede, não
                // terminar a subida. O "acima" é uma faixa fixa e não até a cabeça: agachado a caixa encolhe e a
                // faixa até a cabeça sumia (parecia o topo no meio da parede).
                double reach = forward ? 0.5D : TOP_REACH;
                Direction edge = wallInFront(p, box.minY + 0.01D, box.minY + reach);
                if (edge != null && (forward || wallInFront(p, box.minY + reach, box.minY + reach + 0.5D) == null)) {
                    mantleTicks = MANTLE_MAX_TICKS;
                    mantleDir = edge;
                    if (DEBUG) System.out.printf("[LMWALL] beirada em y=%.2f (%s)%n", p.getY(), forward ? "segurando" : "soltou");
                    mantle(p, box, v);
                }
            }
        }
        wasClimbing = climbing;
        if (DEBUG) debug(p, climbing);
    }

    /** A beirada: sobe enquanto a parede ainda bate nas pernas, e quando passa do topo, o último empurrão pra dentro. */
    private static void mantle(Player p, AABB box, Vec3 v) {
        Direction d = mantleDir;
        if (d == null) {
            mantleTicks = 0;
            return;
        }
        mantleTicks--;
        if (wallInFront(p, box.minY + 0.01D, box.maxY - 0.05D) != null) {
            p.setDeltaMovement(d.getStepX() * MANTLE_PUSH, MANTLE_VY, d.getStepZ() * MANTLE_PUSH);
        } else {
            // passou do topo: segue pra cima do bloco no embalo (o pulinho já está na velocidade de subida)
            p.setDeltaMovement(d.getStepX() * MANTLE_PUSH, v.y, d.getStepZ() * MANTLE_PUSH);
            mantleTicks = 0;
            if (DEBUG) mantled = true;
        }
    }

    private static final boolean DEBUG = Boolean.getBoolean("becomelm.debug");
    private static int climbTicks;
    private static double climbFromY;
    private static boolean mantled;
    private static double peakY;

    /** Pra conferir no teste: quanto subiu, em quanto tempo, e onde pousou depois da beirada. */
    private static void debug(Player p, boolean climbing) {
        if (climbing && climbTicks == 0) climbFromY = p.getY();
        if (climbing) {
            climbTicks++;
        } else if (climbTicks > 0) {
            // a conta é do começo do tick: cada tick de subida já andou CLIMB_VY quando o próximo começa
            double dy = p.getY() - climbFromY;
            System.out.printf("[LMWALL] subiu %.2f blocos em %d ticks (%.1f b/s)%n", dy, climbTicks, dy / climbTicks * 20D);
            climbTicks = 0;
        }
        if (!climbing && !p.onGround() && p.tickCount % 4 == 0 && onWall(p)) {
            System.out.printf("[LMWALL] na parede y=%.2f vy=%.3f agachado=%b%n", p.getY(), p.getDeltaMovement().y, p.isShiftKeyDown());
        }
        if (mantled) {
            peakY = Math.max(peakY, p.getY());
            if (p.onGround()) {
                System.out.printf("[LMWALL] pousou em x=%.2f y=%.2f z=%.2f (pulinho até y=%.2f)%n", p.getX(), p.getY(), p.getZ(), peakY);
                mantled = false;
                peakY = Double.NEGATIVE_INFINITY;
            }
        } else {
            peakY = Double.NEGATIVE_INFINITY;
        }
    }

    /** A parede que ele encosta de frente nessa faixa de altura, ou null. */
    @Nullable
    private static Direction wallInFront(Player p, double fromY, double toY) {
        if (toY <= fromY) return null;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        AABB box = p.getBoundingBox();
        AABB slice = new AABB(box.minX, fromY, box.minZ, box.maxX, toY, box.maxZ);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d.getStepX() * fx + d.getStepZ() * fz < FACING_COS) continue;
            AABB probe = slice.move(d.getStepX() * PROBE, 0D, d.getStepZ() * PROBE);
            for (VoxelShape s : p.level().getBlockCollisions(p, probe)) {
                if (!s.isEmpty()) return d;
            }
        }
        return null;
    }
}
