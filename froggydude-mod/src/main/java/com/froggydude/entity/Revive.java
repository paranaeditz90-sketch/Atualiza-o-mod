package com.froggydude.entity;

import com.froggydude.init.ModSounds;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * (v0.7.35) O renascer com a língua (fim do vídeo vs AJTHEBOLD; réplica v3 aprovada pelo LM em 16/11, viking
 * 13-renascer-lingua.md). Em vez de morrer, às vezes (35% numa morte comum, 75% se morreu explodido) ele se despedaça:
 * os pedaços voam e param, ele fica parado 5 a 10 s e aí a cabeça, de cara no chão, ergue a testa e a língua puxa os
 * pedaços (tronco, braço, braço, perna, perna), encaixando um por um; depois dá uma flexão e um MORTAL pra trás (sem
 * espaço em cima: só levanta com a flexão e sacode a cabeça de raiva), vira seco pro jogador e estala o pescoço. Volta
 * com metade da vida. "É cinema": nada impede (invulnerável enquanto remonta). Em todos os modos (manhunt, aparece no
 * teu mundo, /summon). A 1ª vez de cada mundo é a longa; depois as puxadas levam metade do tempo.
 * <p>
 * A linha do tempo é FIXA em ticks (o servidor sabe quando acaba); quem desenha tudo é o cliente
 * (client.pieces.ReviveScene), a partir dos números sincronizados aqui ({@link Params}).
 */
public final class Revive {

    public static final float CHANCE = 0.35F;
    public static final float CHANCE_BOOM = 0.75F;
    /** Os pedaços voam, quicam e param. */
    public static final int SETTLE = 60;
    /** Parado (o LM: "sorteado, entre 5 a 10 segundos"). */
    public static final int STILL_MIN = 100;
    public static final int STILL_MAX = 200;
    /** Cada puxada (mira, testa, tiro, estica, arrasta, encaixa): a longa e a rápida (metade). */
    public static final int STEP_LONG = 84;
    public static final int STEP_FAST = 44;
    public static final int PARTS = 5;
    /** Flexão (0,5 s) + no ar (0,8 s) + a queda (0,45 s). */
    public static final int FLIP = 35;
    /** Sem espaço pro mortal: flexão, levanta e sacode a cabeça de raiva. */
    public static final int SHAKE = 62;
    /** Depois de ficar em pé: vira seco pro jogador... */
    public static final int TURN_AFTER = 7;
    /** ...estala o pescoço... */
    public static final int CRACK_AFTER = 18;
    /** ...e volta a caçar. */
    public static final int END_AFTER = 34;
    /** Da cabeça (centro) até os pés, deitado (px do modelo -> blocos, sem a escala). */
    public static final float BODY = 28F / 16F;
    /** Quanto espaço livre em cima o mortal precisa (blocos). */
    public static final double FLIP_ROOM = 3.2D;

    private Revive() {
    }

    /** Os números do renascer (sincronizados pro cliente desenhar igual em todo mundo). */
    public record Params(long t0, int seed, float yaw, double px, double py, double pz, int still, boolean fast,
                         boolean flip, boolean boom, float bodyYaw, double ex, double ey, double ez) {

        public CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putLong("T0", t0);
            t.putInt("Seed", seed);
            t.putFloat("Yaw", yaw);
            t.putDouble("PX", px);
            t.putDouble("PY", py);
            t.putDouble("PZ", pz);
            t.putInt("Still", still);
            t.putBoolean("Fast", fast);
            t.putBoolean("Flip", flip);
            t.putBoolean("Boom", boom);
            t.putFloat("BodyYaw", bodyYaw);
            t.putDouble("EX", ex);
            t.putDouble("EY", ey);
            t.putDouble("EZ", ez);
            return t;
        }

        @Nullable
        public static Params of(CompoundTag t) {
            if (t == null || !t.contains("T0")) return null;
            return new Params(t.getLong("T0"), t.getInt("Seed"), t.getFloat("Yaw"), t.getDouble("PX"), t.getDouble("PY"),
                    t.getDouble("PZ"), t.getInt("Still"), t.getBoolean("Fast"), t.getBoolean("Flip"), t.getBoolean("Boom"),
                    t.getFloat("BodyYaw"), t.getDouble("EX"), t.getDouble("EY"), t.getDouble("EZ"));
        }

        /** Pra onde o corpo montado aponta (a cabeça; os pés ficam pro lado contrário). */
        public Vec3 dir() {
            float r = yaw * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
        }

        public int step() {
            return fast ? STEP_FAST : STEP_LONG;
        }

        /** Ticks desde o começo: onde começa cada coisa. */
        public int partsStart() {
            return SETTLE + still;
        }

        public int standStart() {
            return partsStart() + PARTS * step();
        }

        public int standLen() {
            return flip ? FLIP : SHAKE;
        }

        public int turnAt() {
            return standStart() + standLen() + TURN_AFTER;
        }

        public int crackAt() {
            return standStart() + standLen() + CRACK_AFTER;
        }

        public int end() {
            return standStart() + standLen() + END_AFTER;
        }
    }

    // ---------------------------------------------------------------- servidor

    /** Morte que NÃO renasce: /kill, caiu no fundo do mundo, a borda do mundo. */
    public static boolean final_(DamageSource source) {
        return source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.OUTSIDE_BORDER);
    }

    /** Sorteia (35% / 75% explodido) e começa. Devolve true se ele vai renascer (não morre). */
    public static boolean tryStart(FroggydudeEntity froggy, DamageSource source) {
        if (froggy.level().isClientSide || froggy.isReviving() || final_(source)) return false;
        boolean boom = source.is(DamageTypeTags.IS_EXPLOSION);
        if (froggy.getRandom().nextFloat() >= (boom ? CHANCE_BOOM : CHANCE)) return false;
        return start(froggy, boom);
    }

    /** Começa agora (também pelo comando /froggydude renascer). */
    public static boolean start(FroggydudeEntity froggy, boolean boom) {
        if (!(froggy.level() instanceof ServerLevel level)) return false;
        RandomSource r = froggy.getRandom();
        Vec3 e = froggy.position();
        double ground = groundBelow(level, e);
        if (Double.isNaN(ground)) return false;               // caindo no vazio (ou entalado num bloco): morre de verdade
        float scale = FroggydudeEntity.MODEL_SCALE;
        double body = BODY * scale;
        // pra onde o corpo vai deitar: chão firme embaixo, nada no caminho e espaço pra ele ficar de pé nos pés. As 8
        // direções certinhas (retas e diagonais: num túnel de 1 de largura só a do túnel serve), a partir de uma sorteada
        int i0 = r.nextInt(8);
        float yaw = Float.NaN, best = i0 * 45F;
        int bestScore = -1;
        for (int i = 0; i < 8; i++) {
            float y = ((i0 + i) % 8) * 45F;
            int score = layScore(level, froggy, e, ground, y, body);
            if (score == Integer.MAX_VALUE) {
                yaw = y;
                break;
            }
            if (score > bestScore) {
                bestScore = score;
                best = y;
            }
        }
        Vec3 head, feet;
        if (!Float.isNaN(yaw)) {
            Vec3 dir = dirOf(yaw);
            // a cabeça para um tico pra frente de onde ele estava; os pés, um corpo pra trás
            head = new Vec3(e.x + dir.x * 0.3D, ground, e.z + dir.z * 0.3D);
            feet = new Vec3(head.x - dir.x * body, ground, head.z - dir.z * body);
        } else {
            // apertado em todo lado: os pés ficam onde ele estava (ali ele cabia em pé) e a cabeça vai pro lado mais livre
            yaw = best;
            Vec3 dir = dirOf(yaw);
            feet = new Vec3(e.x, ground, e.z);
            head = new Vec3(e.x + dir.x * body, ground, e.z + dir.z * body);
        }
        Vec3 dir = dirOf(yaw);
        boolean flip = room(level, feet, dir);
        FroggyWorldData data = FroggyWorldData.get(level);
        boolean fast = data.revives > 0;
        data.revives++;
        data.setDirty();
        int still = STILL_MIN + r.nextInt(STILL_MAX - STILL_MIN + 1);
        Params p = new Params(level.getGameTime(), r.nextInt(), yaw, head.x, head.y, head.z, still, fast, flip, boom,
                froggy.yBodyRot, e.x, e.y, e.z);
        froggy.beginRevive(p, feet);
        froggy.brainLog("despedaçado... a língua vai juntar os pedaços (" + (boom ? "explosão" : "morte comum")
                + ", parado " + still / 20 + " s, " + (fast ? "rápido" : "longo") + ", " + (flip ? "mortal" : "teto baixo: sacode")
                + ")");
        return true;
    }

    /** O tick do servidor enquanto ele remonta. */
    public static void tick(FroggydudeEntity froggy, Params p) {
        Level level = froggy.level();
        int age = (int) (level.getGameTime() - p.t0());
        froggy.setDeltaMovement(Vec3.ZERO);
        // os sons, no mesmo relógio do desenho do cliente
        if (age >= p.partsStart() && age < p.standStart()) {
            int s = (age - p.partsStart()) % p.step();
            int st = p.step();
            if (s == shotTick(st)) sound(froggy, ModSounds.TONGUE.get(), 0.9F, 1.1F);
            if (s == snapTick(st) + 2) {
                sound(froggy, ModSounds.FEAST_SQUISH.get(), 1.0F, 0.8F);
                sound(froggy, ModSounds.CRACK.get(), 0.6F, 1.3F);
            }
        }
        if (age == p.standStart() + (p.flip() ? 10 : 8)) sound(froggy, ModSounds.LEAP.get(), 0.8F, 0.9F);
        if (p.flip() && age == p.standStart() + 26) sound(froggy, ModSounds.SKY_LAND.get(), 0.9F, 1.0F);
        if (age == p.turnAt()) {
            Player near = level.getNearestPlayer(froggy, 64D);
            if (near != null) {
                froggy.faceDirection(near.getX() - froggy.getX(), near.getZ() - froggy.getZ());
            }
        }
        if (age == p.crackAt() + 4) sound(froggy, ModSounds.CRACK.get(), 1.0F, 0.8F);
        if (age >= p.end()) froggy.endRevive();
    }

    private static void sound(FroggydudeEntity froggy, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        froggy.level().playSound(null, froggy.getX(), froggy.getY(), froggy.getZ(), s, SoundSource.HOSTILE, vol, pitch);
    }

    /** Os momentos de cada puxada (frações do passo; o cliente usa os mesmos). A mira começa no 0. */
    public static int liftTick(int step) {
        return Math.round(step * 0.11F);
    }

    public static int shotTick(int step) {
        return Math.round(step * 0.16F);
    }

    public static int grabTick(int step) {
        return Math.round(step * 0.22F);
    }

    public static int pullTick(int step) {
        return Math.round(step * 0.31F);
    }

    public static int snapTick(int step) {
        return Math.round(step * 0.83F);
    }

    public static int doneTick(int step) {
        return Math.round(step * 0.92F);
    }

    private static Vec3 dirOf(float yaw) {
        float rad = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
    }

    /**
     * A altura do chão embaixo dos pés (o topo da forma do bloco: laje, caminho de terra, tapete, neve, lama...), até 24
     * blocos pra baixo; NaN se não tiver ou se os pés estão dentro de um bloco.
     */
    private static double groundBelow(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos p = BlockPos.containing(at).mutable();
        for (int i = 0; i <= 24 && p.getY() >= level.getMinBuildHeight(); i++) {
            VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
            if (!s.isEmpty()) {
                double top = p.getY() + s.max(Direction.Axis.Y);
                if (top <= at.y + 1.0E-3D) return top;
                if (p.getY() + s.min(Direction.Axis.Y) < at.y - 1.0E-3D) return Double.NaN;
            }
            p.move(0, -1, 0);
        }
        return Double.NaN;
    }

    /**
     * Quanto dá pra deitar o corpo pra esse lado: os pontos do corpo com chão firme logo embaixo e nada no caminho, mais
     * 1 se ele cabe em pé nos pés. Integer.MAX_VALUE = serve inteiro.
     */
    private static int layScore(ServerLevel level, FroggydudeEntity froggy, Vec3 e, double ground, float yaw, double body) {
        Vec3 d = dirOf(yaw);
        int ok = 0, n = 0;
        for (double k = -body; k <= 0.6D; k += 0.4D, n++) {
            double x = e.x + d.x * k, z = e.z + d.z * k;
            boolean free = level.noCollision(new AABB(x - 0.12D, ground + 0.05D, z - 0.12D, x + 0.12D, ground + 0.5D, z + 0.12D));
            boolean floor = !level.noCollision(new AABB(x - 0.12D, ground - 0.3D, z - 0.12D, x + 0.12D, ground - 0.02D, z + 0.12D));
            if (free && floor) ok++;
        }
        Vec3 feet = new Vec3(e.x + d.x * (0.3D - body), ground, e.z + d.z * (0.3D - body));
        if (level.noCollision(froggy, froggy.getDimensions(Pose.STANDING).makeBoundingBox(feet).deflate(0.01D))) ok++;
        return ok == n + 1 ? Integer.MAX_VALUE : ok;
    }

    /**
     * Espaço pro mortal: livre até FLIP_ROOM em cima dos pés e de onde ele gira (o meio do corpo). Pela forma dos blocos
     * (de pé numa laje, o bloco da laje não conta como teto).
     */
    private static boolean room(ServerLevel level, Vec3 feet, Vec3 dir) {
        for (double k = -0.4D; k <= 1.0D; k += 0.35D) {
            double x = feet.x + dir.x * k, z = feet.z + dir.z * k;
            if (!level.noCollision(new AABB(x - 0.25D, feet.y + 0.05D, z - 0.25D, x + 0.25D, feet.y + FLIP_ROOM, z + 0.25D))) {
                return false;
            }
        }
        return true;
    }
}
