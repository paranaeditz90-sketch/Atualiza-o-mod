package com.froggydude.world;

import com.froggydude.brain.PlayerMemory;
import com.froggydude.brain.PreyStudy;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.init.ModDamageTypes;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModSounds;
import com.froggydude.entity.BloodFx;
import com.froggydude.player.Paranoia;
import com.froggydude.player.PlayerSight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * (v0.7.34) O diretor de tensão (PLANO.md 5.5; respostas do LM de 10/11: só no "aparece no teu mundo", calmaria de 3 a
 * 6 minutos, todos os sinais, sempre fora da vista). Depois que ele chega, a caçada vira um RITMO, porque o que separa
 * terror de comédia é o tempo: ele aparecendo a cada 30 s vira piada na terceira vez.
 *
 *   CAÇADA (o de sempre: I SEE YOU, bote, língua...; no máximo 4 min) -> ele RECUA e some (falso alívio) ->
 *   CALMARIA (3-6 min, nada acontece; mas se ela for DORMIR ali, ele volta pela cama) ->
 *   SINAIS (1-3 min: ribbit longe, passos atrás que param quando ela vira, bicho meio comido, pegada de sangue, tocha
 *   apagada, a porta que ela fechou aberta, placa "I SEE YOU"; todos fora da vista dela) ->
 *   VISLUMBRE (ele parado longe, olhando; some quando ela olha, chega mais perto quando ela vira as costas) -> CAÇADA...
 *
 * Nunca o mesmo sinal duas vezes seguidas; o que mais assustou (a paranoia dela subiu, Paranoia) aparece mais, e fica
 * guardado no estudo dela (PreyStudy). Fora do "aparece no teu mundo" (manhunt, /summon) nada disso roda.
 * Enquanto ele "não está" (calmaria, sinais), o FroggyDude sai do mundo guardado inteiro (vida, barriga com o que ele
 * comeu, fase 2) e volta o mesmo.
 */
public final class TensionDirector {

    public enum Phase { OFF, CALM, SIGNS, GLIMPSE, HUNT }

    /** Os sinais, do mais leve (1) ao mais forte (3). */
    public enum Sign {
        RIBBIT(1), BLOOD(1), STEPS(2), EATEN(2), TORCH(2), DOOR(3), PLACARD(3);

        public final int level;

        Sign(int level) {
            this.level = level;
        }
    }

    /** Só pra testar o ciclo inteiro rápido (froggydude.directorScale, -PdirectorScale=0.1): encolhe os tempos. */
    private static final double SCALE = Math.max(0.02D, Double.parseDouble(System.getProperty("froggydude.directorScale", "1")));
    private static final int CALM_MIN = scaled(3 * 60 * 20);
    private static final int CALM_RANDOM = scaled(3 * 60 * 20);
    private static final int SIGNS_MIN = scaled(60 * 20);
    private static final int SIGNS_RANDOM = scaled(2 * 60 * 20);
    private static final int FIRST_SIGN = scaled(12 * 20);
    private static final int SIGN_GAP_MIN = scaled(20 * 20);
    private static final int SIGN_GAP_RANDOM = scaled(20 * 20);
    private static final int GLIMPSE_MAX = 45 * 20;
    private static final int HUNT_MAX = scaled(4 * 60 * 20);
    /** Falso alívio: dormir logo depois dele ir embora não conta (é ela correndo pra cama); depois disso, ele volta. */
    private static final int TRAP_DELAY = scaled(30 * 20);

    private static int scaled(int ticks) {
        return Math.max(20, (int) Math.round(ticks * SCALE));
    }
    /** Dormindo há tanto (a noite pula aos 100 ticks): ele chega antes. */
    private static final int TRAP_SLEEP = 30;
    /** Vislumbre: ela olhou DIRETO pra ele (a mira a menos de 12° dele) tanto tempo, ele some. */
    private static final int LOOKED_TO_VANISH = 8;
    private static final double LOOK_CONE = Math.cos(Math.toRadians(12.0D));
    /** Vislumbre: tanto tempo sem ela olhar, ele chega mais perto (pelas costas dela). */
    private static final int UNSEEN_TO_HOP = 60;
    /** Depois de sumir no vislumbre, a caçada vem em 3-6 s. */
    private static final int VANISH_TO_HUNT = 60;
    /** Recuando: some quando ela não vê; se ficar olhando, some do mesmo jeito depois disso. */
    private static final int RETREAT_MAX = 30 * 20;

    // o que dura segundos fica só na memória (recarregou o mundo no meio do vislumbre: recomeça os sinais)
    private static int seenTicks;
    private static int unseenTicks;
    private static int hops;
    private static long vanishedAt = -1L;
    private static long retreatSince = -1L;
    private static boolean walkedOff;
    private static long trapFrom = -1L;
    private static int sleepTicks;
    @Nullable
    private static Sign measuring;
    private static long measureAt;
    private static float paranoiaBefore;
    private static final List<Step> STEPS = new ArrayList<>();

    private record Step(long at, UUID player, Vec3 pos) {
    }

    private TensionDirector() {
    }

    /** Vale agora? Só com ele já no mundo, no "aparece no teu mundo" (ligado na config), sem manhunt. */
    public static boolean active(FroggyWorldData data) {
        return FroggyConfig.invasion() && Invasion.hunting(data) && !data.manhuntActive;
    }

    /** No vislumbre agora (o FroggyDude parado por ele): fora disso ele se solta sozinho (freezeForGlimpse). */
    public static boolean glimpsing(MinecraftServer server) {
        FroggyWorldData data = FroggyWorldData.get(server);
        return active(data) && phase(data) == Phase.GLIMPSE;
    }

    public static Phase phase(FroggyWorldData data) {
        Phase[] all = Phase.values();
        return data.dirPhase >= 0 && data.dirPhase < all.length ? all[data.dirPhase] : Phase.OFF;
    }

    // ---------------------------------------------------------------- relógio

    /** A cada segundo (Invasion.tick, estágio "caçando"). */
    public static void second(MinecraftServer server, FroggyWorldData data, long now) {
        ServerPlayer prey = prey(server, data);
        if (prey == null) return;
        switch (phase(data)) {
            case OFF -> {
                // ele acabou de chegar caçando (a chegada é a primeira caçada)
                FroggydudeEntity f = FroggyKeeper.find(server);
                if (f != null && f.getTarget() instanceof ServerPlayer t) setPrey(data, t);
                enter(data, Phase.HUNT, now, now + HUNT_MAX);
            }
            case HUNT -> huntSecond(server, data, prey, now);
            case CALM -> calmSecond(server, data, prey, now);
            case SIGNS -> signsSecond(server, data, prey, now);
            case GLIMPSE -> {
                if (now >= data.dirUntil) startHunt(server, data, prey, now, "o vislumbre acabou");
            }
        }
    }

    /** A cada tick do servidor: o vislumbre, os passos e a medida do susto. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        tickSteps(server, now);
        FroggyWorldData data = FroggyWorldData.get(server);
        if (!active(data)) return;
        ServerPlayer prey = prey(server, data);
        if (prey == null) return;
        if (measuring != null && now >= measureAt) {
            float delta = Paranoia.score(prey) - paranoiaBefore;
            study(data, prey).learnSign(measuring.name(), delta);
            data.setDirty();
            log(server, "sinal " + measuring.name() + ": a paranoia mudou " + String.format(Locale.ROOT, "%+.0f", delta));
            measuring = null;
        }
        if (phase(data) == Phase.GLIMPSE) glimpseTick(server, data, prey, now);
    }

    // ---------------------------------------------------------------- fases

    private static void huntSecond(MinecraftServer server, FroggyWorldData data, ServerPlayer prey, long now) {
        FroggydudeEntity f = FroggyKeeper.find(server);
        if (f == null && !data.froggyAlive && data.parkedFroggy == null) {
            // morreu caçando (ela matou ele): ela acha que acabou... e se for dormir, ele volta
            calm(data, now, server.overworld().random, true, "ele morreu na caçada");
            return;
        }
        if (f == null && data.parkedFroggy != null) {
            // ainda guardado (não tinha lugar quando a caçada começou): põe ele de volta, pelas costas dela
            f = place(server, data, prey, 24.0D, 40.0D, FroggyKeeper.Sight.HIDDEN, false);
            if (f != null) {
                wake(f);
                endTruce(data, prey);
                f.setTarget(prey);
            }
            return;
        }
        if (now >= data.dirUntil) {
            if (f == null) {
                calm(data, now, server.overworld().random, true, "a caçada acabou (ele não estava perto)");
            } else if (f.isFightingNow()) {
                data.dirUntil = now + 200; // no meio de um golpe: espera acabar
            } else {
                f.retreatFrom(prey);
                retreatSince = now;
                calm(data, now, server.overworld().random, true, "a caçada acabou: ele recua (falso alívio)");
            }
            return;
        }
        Manhunt.keepOnTrail(server, data, now);
    }

    private static void calmSecond(MinecraftServer server, FroggyWorldData data, ServerPlayer prey, long now) {
        FroggydudeEntity f = FroggyKeeper.find(server);
        if (f != null) {
            if (f.getTarget() == prey && !f.underTruce(prey)) {
                // ela mexeu com ele no recuo (a trégua quebrou): briga de novo
                enter(data, Phase.HUNT, now, now + HUNT_MAX / 2);
                data.dirTrap = false;
                retreatSince = -1L;
                log(server, "ela mexeu com ele no recuo: a caçada volta");
                return;
            }
            // ainda aqui (recuando, comendo quem matou): some quando ela não estiver vendo; se ela ficar olhando, ele
            // vai embora andando (nunca some na cara dela, a não ser que fique 90 s encarando). No meio de um golpe ou
            // comendo, nunca: espera acabar (sumir com alguém preso na língua ou no chão corta o ataque na cara dela)
            boolean busy = f.isFightingNow() || f.isEating();
            boolean seen = prey.level() == f.level() && f.distanceTo(prey) < 64.0F
                    && FroggyKeeper.visible(prey.serverLevel(), prey, f.blockPosition());
            if (retreatSince < 0) retreatSince = now;
            long since = now - retreatSince;
            if (!busy && (!seen || since > RETREAT_MAX * 3L)) {
                park(server, data, f);
                retreatSince = -1L;
                walkedOff = false;
                // a calmaria conta de quando ele some (não de quando a caçada acabou e ele ainda estava indo embora)
                data.dirUntil = now + CALM_MIN + server.overworld().random.nextInt(CALM_RANDOM);
                if (data.dirTrap) trapFrom = now + TRAP_DELAY;
                data.setDirty();
                log(server, "sumiu do mundo (guardado inteiro): calmaria de " + (data.dirUntil - now) / 20 + " s");
            } else if (!busy && seen && since > RETREAT_MAX && !walkedOff) {
                f.retreatFrom(prey);
                walkedOff = true;
            }
            return;
        }
        // falso alívio: ela foi dormir achando que acabou
        if (data.dirTrap && trapFrom < 0) trapFrom = now + TRAP_DELAY; // o mundo foi recarregado no meio
        if (data.dirTrap && now >= trapFrom) {
            if (prey.isSleeping()) {
                sleepTicks += 20;
                if (sleepTicks >= TRAP_SLEEP) {
                    sleepTicks = 0;
                    springTrap(server, data, prey, now);
                    return;
                }
            } else {
                sleepTicks = 0;
            }
        }
        if (now >= data.dirUntil) {
            enter(data, Phase.SIGNS, now, now + SIGNS_MIN + server.overworld().random.nextInt(SIGNS_RANDOM));
            data.dirNextSign = now + FIRST_SIGN;
            data.dirTrap = false;
            log(server, "a calmaria acabou: começam os sinais");
        }
    }

    private static void signsSecond(MinecraftServer server, FroggyWorldData data, ServerPlayer prey, long now) {
        if (now >= data.dirUntil) {
            startGlimpse(server, data, prey, now);
            return;
        }
        if (now < data.dirNextSign) return;
        RandomSource r = server.overworld().random;
        // os sinais vão ficando mais fortes: no começo os de longe, perto do fim os da casa dela (pelo tamanho que a fase
        // sorteou: contando pelo máximo, uma fase curta já começava "no fim" e pulava os de longe)
        float progress = data.dirSince > 0L && data.dirUntil > data.dirSince
                ? (now - data.dirSince) / (float) (data.dirUntil - data.dirSince)
                : 1F - (data.dirUntil - now) / (float) (SIGNS_MIN + SIGNS_RANDOM);
        progress = Mth.clamp(progress, 0F, 1F);
        Sign done = null;
        for (Sign s : ranked(data, prey, progress, r)) {
            if (doSign(server, data, prey, s)) {
                done = s;
                break;
            }
        }
        data.dirNextSign = now + SIGN_GAP_MIN + r.nextInt(SIGN_GAP_RANDOM);
        if (done != null) {
            data.dirLastSign = done.name();
            data.setDirty();
            measuring = done;
            measureAt = now + 160;
            paranoiaBefore = Paranoia.score(prey);
            log(server, "sinal: " + done.name());
        }
    }

    /** Os sinais na ordem de tentar: peso pelo que já assustou ela, nunca o último, e mais fortes no fim. */
    private static List<Sign> ranked(FroggyWorldData data, ServerPlayer prey, float progress, RandomSource r) {
        PreyStudy st = study(data, prey);
        List<Sign> out = new ArrayList<>();
        Map<Sign, Float> w = new EnumMap<>(Sign.class);
        for (Sign s : Sign.values()) {
            if (s.name().equals(data.dirLastSign)) continue;
            float want = progress < 0.4F ? (s.level == 1 ? 1.6F : s.level == 2 ? 1.0F : 0.4F)
                    : progress < 0.75F ? (s.level == 2 ? 1.5F : 1.0F) : (s.level == 3 ? 1.8F : s.level == 2 ? 1.2F : 0.6F);
            w.put(s, want * (1F + Math.max(-0.5F, st.signFear(s.name()) / 20F)));
        }
        while (!w.isEmpty()) {
            float total = 0F;
            for (float v : w.values()) total += v;
            float roll = r.nextFloat() * total;
            Sign pick = null;
            for (Map.Entry<Sign, Float> e : w.entrySet()) {
                roll -= e.getValue();
                if (roll <= 0F) {
                    pick = e.getKey();
                    break;
                }
            }
            if (pick == null) pick = w.keySet().iterator().next();
            out.add(pick);
            w.remove(pick);
        }
        return out;
    }

    // ---------------------------------------------------------------- vislumbre

    private static void startGlimpse(MinecraftServer server, FroggyWorldData data, ServerPlayer prey, long now) {
        FroggydudeEntity f = place(server, data, prey, 26.0D, 40.0D, FroggyKeeper.Sight.SIDE_VIEW, true);
        if (f == null) {
            startHunt(server, data, prey, now, "sem lugar pro vislumbre");
            return;
        }
        f.freezeForGlimpse(true);
        f.setTarget(null);
        face(f, prey);
        seenTicks = 0;
        unseenTicks = 0;
        hops = 0;
        vanishedAt = -1L;
        enter(data, Phase.GLIMPSE, now, now + GLIMPSE_MAX);
        log(server, "vislumbre: parado a " + Math.round(f.distanceTo(prey)) + " blocos, olhando");
    }

    private static void glimpseTick(MinecraftServer server, FroggyWorldData data, ServerPlayer prey, long now) {
        if (vanishedAt >= 0) {
            if (now - vanishedAt >= VANISH_TO_HUNT) startHunt(server, data, prey, now, "sumiu quando ela olhou: agora vem");
            return;
        }
        FroggydudeEntity f = FroggyKeeper.find(server);
        if (f == null) {
            startHunt(server, data, prey, now, "o vislumbre sumiu");
            return;
        }
        if (f.hurtTime > 0 || f.level() != prey.level()) {
            startHunt(server, data, prey, now, "mexeram com ele no vislumbre");
            return;
        }
        face(f, prey);
        if (PlayerSight.sees(prey, f)) {
            // na tela: fica parado olhando; quando ela olhar DIRETO pra ele, some (sem fumaça, sem som)
            unseenTicks = 0;
            Vec3 to = f.getEyePosition().subtract(prey.getEyePosition());
            boolean looking = to.lengthSqr() > 1.0E-4D && to.normalize().dot(prey.getViewVector(1F)) > LOOK_CONE;
            seenTicks = looking ? seenTicks + 1 : Math.max(0, seenTicks - 1);
            if (seenTicks >= LOOKED_TO_VANISH) {
                park(server, data, f);
                vanishedAt = now;
                log(server, "vislumbre: ela olhou, ele sumiu");
            }
            return;
        }
        seenTicks = 0;
        if (++unseenTicks < UNSEEN_TO_HOP) return;
        unseenTicks = 0;
        double d = f.distanceTo(prey);
        if (hops >= 2 || d < 16.0D) {
            startHunt(server, data, prey, now, "chegou perto pelas costas dela");
            return;
        }
        // ela virou as costas: ele chega mais perto, onde ela não está olhando agora
        BlockPos spot = FroggyKeeper.findSpot(prey.serverLevel(), prey, Math.max(12.0D, d * 0.55D), Math.max(14.0D, d * 0.7D),
                FroggyKeeper.Sight.HIDDEN, prey.getRandom());
        if (spot == null) return;
        f.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        face(f, prey);
        hops++;
        log(server, "vislumbre: ela virou as costas, ele chegou a " + Math.round(f.distanceTo(prey)) + " blocos");
    }

    private static void face(FroggydudeEntity f, ServerPlayer prey) {
        f.faceDirection(prey.getX() - f.getX(), prey.getZ() - f.getZ());
        double dy = prey.getEyeY() - f.getEyeY();
        double flat = Math.sqrt(f.distanceToSqr(prey.getX(), f.getY(), prey.getZ()));
        f.setXRot((float) (-(Mth.atan2(dy, flat) * Mth.RAD_TO_DEG)));
    }

    // ---------------------------------------------------------------- caçada / calmaria

    private static void startHunt(MinecraftServer server, FroggyWorldData data, ServerPlayer prey, long now, String why) {
        FroggydudeEntity f = FroggyKeeper.find(server);
        if (f == null) f = place(server, data, prey, 24.0D, 40.0D, FroggyKeeper.Sight.HIDDEN, false);
        enter(data, Phase.HUNT, now, now + HUNT_MAX);
        vanishedAt = -1L;
        if (f == null) {
            log(server, "caçada: " + why + " (sem lugar pra ele agora: o rastro traz)");
            return;
        }
        wake(f);
        endTruce(data, prey);
        f.setTarget(prey);
        // a risada antes da corrida (PLANO 5.5: "Perseguição: risada, corrida de quatro...")
        f.speak(VoiceSituation.FOUND, true);
        log(server, "caçada: " + why);
    }

    /** Falso alívio que deu certo: ela dormiu, ele volta pela cama. */
    private static void springTrap(MinecraftServer server, FroggyWorldData data, ServerPlayer prey, long now) {
        data.dirTrap = false;
        FroggydudeEntity f = place(server, data, prey, 10.0D, 16.0D, FroggyKeeper.Sight.ANY, false);
        prey.stopSleepInBed(true, true);
        enter(data, Phase.HUNT, now, now + HUNT_MAX);
        if (f == null) {
            log(server, "falso alívio: ela dormiu, mas não achou lugar perto (o rastro traz)");
            return;
        }
        wake(f);
        // a trégua do recuo (60 s) ainda pode estar valendo: sem tirar, setTarget larga ela e ele fica parado na cama
        endTruce(data, prey);
        f.setTarget(prey);
        prey.serverLevel().playSound(null, f.getX(), f.getY(), f.getZ(), ModSounds.RIBBIT.get(), SoundSource.HOSTILE, 1.6F, 0.8F);
        log(server, "falso alívio: ela foi dormir, ele voltou");
    }

    private static void calm(FroggyWorldData data, long now, RandomSource r, boolean trap, String why) {
        enter(data, Phase.CALM, now, now + CALM_MIN + r.nextInt(CALM_RANDOM));
        data.dirTrap = trap;
        trapFrom = trap ? now + TRAP_DELAY : -1L;
        sleepTicks = 0;
        STEPS.clear();
        lastLog = why;
    }

    private static void enter(FroggyWorldData data, Phase phase, long now, long until) {
        data.dirPhase = phase.ordinal();
        data.dirSince = now;
        data.dirUntil = until;
        data.setDirty();
    }

    /** Solta ele pra agir (o vislumbre deixou parado). */
    private static void wake(FroggydudeEntity f) {
        f.freezeForGlimpse(false);
        f.setNoAi(false);
    }

    /** A caçada que o diretor começa vale mesmo com a trégua do recuo ou da morte (senão setTarget larga ela). */
    private static void endTruce(FroggyWorldData data, ServerPlayer prey) {
        PlayerMemory m = data.brain.find(prey.getUUID());
        if (m != null && m.truceUntil != 0L) {
            m.truceUntil = 0L;
            data.setDirty();
        }
    }

    /** Ele matou alguém (Invasion): come, se gaba e some; depois a calmaria. Renascendo perto, ele não vem de novo. */
    public static void onKill(MinecraftServer server, FroggyWorldData data, ServerPlayer victim) {
        if (!active(data)) return;
        long now = server.overworld().getGameTime();
        retreatSince = now;
        data.brain.memory(victim.getUUID(), victim.getScoreboardName()).truceUntil = now + com.froggydude.brain.PlayerMemory.TRUCE_MIN;
        calm(data, now, server.overworld().random, false, "ele te pegou: some até a próxima");
        log(server, lastLog);
    }

    // ---------------------------------------------------------------- dentro e fora do mundo

    /** Tira ele do mundo, guardado inteiro (vida, barriga, fase 2), sem largar nada no chão. */
    private static void park(MinecraftServer server, FroggyWorldData data, FroggydudeEntity f) {
        CompoundTag tag = new CompoundTag();
        wake(f);
        f.saveWithoutId(tag);
        data.parkedFroggy = tag;
        data.froggyHealth = f.getHealth();
        data.setDirty();
        f.leaveQuietly();
    }

    /**
     * Põe ele no mundo perto da presa: o mesmo de antes (guardado) ou, se morreu, um novo. Devolve null se não achou
     * lugar agora.
     */
    @Nullable
    private static FroggydudeEntity place(MinecraftServer server, FroggyWorldData data, ServerPlayer prey, double minD,
                                          double maxD, FroggyKeeper.Sight sight, boolean dark) {
        ServerLevel level = prey.serverLevel();
        BlockPos spot = null;
        if (dark) {
            // o vislumbre: no escuro, se tiver (no claro também vale)
            for (int i = 0; i < 6 && spot == null; i++) {
                BlockPos s = FroggyKeeper.findSpot(level, prey, minD, maxD, sight, level.random);
                if (s != null && level.getMaxLocalRawBrightness(s.above()) <= 7) spot = s;
            }
        }
        if (spot == null) spot = FroggyKeeper.findSpot(level, prey, minD, maxD, sight, level.random);
        if (spot == null) return null;
        FroggydudeEntity f = null;
        if (data.parkedFroggy != null) {
            f = ModEntityTypes.FROGGYDUDE.get().create(level);
            if (f != null) {
                f.load(data.parkedFroggy);
                f.setDeltaMovement(Vec3.ZERO);
                f.fallDistance = 0F;
            }
        }
        if (f == null) {
            f = ModEntityTypes.FROGGYDUDE.get().create(level);
            if (f == null) return null;
            DifficultyInstance diff = level.getCurrentDifficultyAt(spot);
            f.finalizeSpawn(level, diff, MobSpawnType.COMMAND, null, null);
        }
        f.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0F, 0F);
        f.faceDirection(prey.getX() - f.getX(), prey.getZ() - f.getZ());
        if (!level.addFreshEntity(f)) return null;
        data.parkedFroggy = null;
        data.setDirty();
        return FroggyKeeper.find(server);
    }

    // ---------------------------------------------------------------- os sinais

    /** Faz o sinal perto da presa (fora da vista dela). Devolve false se não deu agora (sem lugar, sem bicho...). */
    public static boolean doSign(MinecraftServer server, FroggyWorldData data, ServerPlayer prey, Sign sign) {
        ServerLevel level = prey.serverLevel();
        RandomSource r = level.random;
        return switch (sign) {
            case RIBBIT -> {
                Vec3 at = around(prey, 36.0D, 52.0D, true, r);
                level.playSound(null, at.x, at.y, at.z, ModSounds.RIBBIT.get(), SoundSource.HOSTILE, 3.5F,
                        0.8F + r.nextFloat() * 0.15F);
                yield true;
            }
            case STEPS -> {
                // passos atrás dela, chegando, que param se ela virar
                Vec3 back = prey.getViewVector(1F).multiply(-1, 0, -1);
                back = back.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : back.normalize();
                long now = level.getGameTime();
                int n = 4 + r.nextInt(3);
                for (int i = 0; i < n; i++) {
                    double d = 10.0D - i * 1.1D;
                    Vec3 p = prey.position().add(back.scale(d)).add(back.z * (i % 2 == 0 ? 0.3D : -0.3D), 0,
                            -back.x * (i % 2 == 0 ? 0.3D : -0.3D));
                    STEPS.add(new Step(now + 10L * i, prey.getUUID(), p));
                }
                yield true;
            }
            case EATEN -> eatAnimal(level, prey, r);
            case BLOOD -> bloodTrail(level, prey, r);
            case TORCH -> torchOut(level, data, prey, r);
            case DOOR -> openDoor(level, data, prey, r);
            case PLACARD -> placard(level, data, prey, r);
        };
    }

    private static void tickSteps(MinecraftServer server, long now) {
        for (Iterator<Step> it = STEPS.iterator(); it.hasNext(); ) {
            Step s = it.next();
            if (s.at > now) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(s.player);
            if (p == null) continue;
            ServerLevel level = p.serverLevel();
            BlockPos ground = BlockPos.containing(s.pos).below();
            // ela virou e olhou pra onde vinham os passos: param (os próximos também)
            if (FroggyKeeper.visible(level, p, ground.above())) {
                STEPS.removeIf(o -> o.player.equals(s.player));
                return;
            }
            BlockState below = level.getBlockState(ground);
            SoundType st = below.isAir() ? SoundType.GRASS : below.getSoundType(level, ground, p);
            level.playSound(null, s.pos.x, s.pos.y, s.pos.z, st.getStepSound(), SoundSource.HOSTILE,
                    st.getVolume() * 0.8F, st.getPitch());
        }
    }

    /** Um bicho, longe da vista dela, morre comido: se desmonta em pedaços numa poça de sangue. */
    private static boolean eatAnimal(ServerLevel level, ServerPlayer prey, RandomSource r) {
        AABB box = prey.getBoundingBox().inflate(48.0D, 12.0D, 48.0D);
        List<Animal> found = level.getEntitiesOfClass(Animal.class, box, a -> edible(a)
                && a.distanceTo(prey) > 14.0F && !FroggyKeeper.visible(level, prey, a.blockPosition()));
        if (found.isEmpty()) return false;
        Animal a = found.get(r.nextInt(found.size()));
        where = a.blockPosition();
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModDamageTypes.DEVOURED);
        Vec3 at = a.position();
        BloodFx.pool(level, at.add(0, 0.2D, 0), 1.8F);
        BloodFx.spray(level, at.add(0, a.getBbHeight() * 0.6D, 0), new Vec3(0, 1, 0), 18, 0.3F);
        a.hurt(new DamageSource(type), Float.MAX_VALUE);
        if (a.isAlive()) a.kill();
        return true;
    }

    /**
     * Bicho que dá pra comer de sinal: nada que seja de alguém (com nome, domado - cachorro, gato, cavalo, burro, mula,
     * lhama, que podem estar com baú -, na corda, de sela, montado ou levando alguém).
     */
    private static boolean edible(Animal a) {
        if (!a.isAlive() || a.hasCustomName() || a.isLeashed() || a.isVehicle() || a.isPassenger()) return false;
        if (a instanceof TamableAnimal t && t.isTame()) return false;
        if (a instanceof AbstractHorse h && h.isTamed()) return false;
        return !(a instanceof Saddleable s && s.isSaddled());
    }

    /**
     * Pegadas de sangue cruzando o chão atrás dela, a poucos passos (somem em uns 3 minutos): uma fila de 12, esquerda e
     * direita, como de alguém que passou ali agora - e ela não viu ninguém passar.
     */
    private static boolean bloodTrail(ServerLevel level, ServerPlayer prey, RandomSource r) {
        Vec3 mid = around(prey, 4.0D, 6.5D, true, r);
        Vec3 toHer = prey.position().subtract(mid).multiply(1, 0, 1);
        if (toHer.lengthSqr() < 1.0E-4D) return false;
        toHer = toHer.normalize();
        // cruza as costas dela de lado (quase perpendicular a ela)
        double a = Math.toRadians(r.nextBoolean() ? 80 : -80);
        Vec3 dir = new Vec3(toHer.x * Math.cos(a) - toHer.z * Math.sin(a), 0, toHer.x * Math.sin(a) + toHer.z * Math.cos(a));
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 start = mid.subtract(dir.scale(5.5D * 0.75D));
        int put = 0;
        for (int i = 0; i < 12; i++) {
            Vec3 p = start.add(dir.scale(i * 0.75D)).add(side.scale(i % 2 == 0 ? 0.2D : -0.2D));
            BlockPos ground = groundAt(level, BlockPos.containing(p));
            if (ground == null || FroggyKeeper.visible(level, prey, ground)) continue;
            BloodFx.pool(level, new Vec3(p.x, ground.getY() + 0.1D, p.z), 0.45F);
            put++;
        }
        return put >= 6;
    }

    /** Uma tocha dela, longe da vista, se apaga (some com uma fumacinha). */
    private static boolean torchOut(ServerLevel level, FroggyWorldData data, ServerPlayer prey, RandomSource r) {
        BlockPos center = prey.blockPosition();
        List<BlockPos> torches = new ArrayList<>();
        for (BlockPos p : findBlocks(level, center, 24, 8, st -> st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH))) {
            double d = Math.sqrt(p.distSqr(center));
            if (d < 6.0D || FroggyKeeper.visible(level, prey, p.below())) continue;
            torches.add(p);
        }
        if (torches.isEmpty()) return false;
        BlockPos t = torches.get(r.nextInt(torches.size()));
        level.removeBlock(t, false);
        where = t;
        level.sendParticles(ParticleTypes.SMOKE, t.getX() + 0.5D, t.getY() + 0.6D, t.getZ() + 0.5D, 8, 0.05D, 0.1D, 0.05D, 0.01D);
        level.playSound(null, t, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.2F, 1.4F);
        return true;
    }

    /** A porta que ela fechou (ou uma da casa dela), longe da vista, aparece aberta: range. */
    private static boolean openDoor(ServerLevel level, FroggyWorldData data, ServerPlayer prey, RandomSource r) {
        PreyStudy st = study(data, prey);
        List<BlockPos> doors = new ArrayList<>();
        BlockPos last = st.lastDoor(level.dimension().location().toString());
        if (last != null && closedDoor(level, last) && !FroggyKeeper.visible(level, prey, last)
                && last.distSqr(prey.blockPosition()) < 96 * 96) {
            doors.add(last);
        } else {
            BlockPos center = prey.blockPosition();
            for (BlockPos p : findBlocks(level, center, 28, 6, TensionDirector::closedDoor)) {
                if (p.distSqr(center) < 36 || FroggyKeeper.visible(level, prey, p)) continue;
                doors.add(p);
            }
        }
        if (doors.isEmpty()) return false;
        BlockPos d = doors.get(r.nextInt(doors.size()));
        BlockState state = level.getBlockState(d);
        if (!(state.getBlock() instanceof DoorBlock door)) return false;
        door.setOpen(null, level, state, d, true);
        where = d;
        return true;
    }

    private static boolean closedDoor(ServerLevel level, BlockPos p) {
        return closedDoor(level.getBlockState(p));
    }

    private static boolean closedDoor(BlockState st) {
        return st.getBlock() instanceof DoorBlock && st.is(BlockTags.WOODEN_DOORS)
                && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && !st.getValue(DoorBlock.OPEN);
    }

    /**
     * Os blocos que passam no teste numa caixa em volta de c (rh pros lados, rv pra cima e pra baixo), só nos pedaços do
     * mapa já carregados, pulando cada pedaço de 16x16x16 que nem tem um bloco assim (a paleta dele diz na hora). No
     * celular isso importa: varrer bloco por bloco eram ~40 mil leituras por sinal, no mesmo tick.
     */
    private static List<BlockPos> findBlocks(ServerLevel level, BlockPos c, int rh, int rv, Predicate<BlockState> test) {
        List<BlockPos> out = new ArrayList<>();
        int x0 = c.getX() - rh, x1 = c.getX() + rh, z0 = c.getZ() - rh, z1 = c.getZ() + rh;
        int y0 = Math.max(level.getMinBuildHeight(), c.getY() - rv), y1 = Math.min(level.getMaxBuildHeight() - 1, c.getY() + rv);
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
                    int idx = chunk.getSectionIndexFromSectionY(sy);
                    if (idx < 0 || idx >= chunk.getSectionsCount()) continue;
                    LevelChunkSection sec = chunk.getSection(idx);
                    if (sec.hasOnlyAir() || !sec.maybeHas(test)) continue;
                    int bx = cx << 4, by = sy << 4, bz = cz << 4;
                    for (int x = Math.max(x0, bx); x <= Math.min(x1, bx + 15); x++) {
                        for (int y = Math.max(y0, by); y <= Math.min(y1, by + 15); y++) {
                            for (int z = Math.max(z0, bz); z <= Math.min(z1, bz + 15); z++) {
                                if (test.test(sec.getBlockState(x & 15, y & 15, z & 15))) out.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Uma placa "I SEE YOU" (letra vermelha que brilha no escuro), perto da casa dela ou no caminho, longe da vista. */
    private static boolean placard(ServerLevel level, FroggyWorldData data, ServerPlayer prey, RandomSource r) {
        PreyStudy st = study(data, prey);
        BlockPos home = st.home(level.dimension().location().toString());
        List<BlockPos> tries = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            BlockPos base = home != null && home.distSqr(prey.blockPosition()) < 64 * 64 && i < 12 ? home
                    : prey.blockPosition();
            double a = r.nextDouble() * Math.PI * 2.0D;
            double d = (base == home ? 3.0D : 7.0D) + r.nextDouble() * 8.0D;
            BlockPos ground = groundAt(level, base.offset(Mth.floor(Math.cos(a) * d), 0, Mth.floor(Math.sin(a) * d)));
            if (ground != null) tries.add(ground);
        }
        for (BlockPos p : tries) {
            if (!level.getBlockState(p).isAir() || FroggyKeeper.visible(level, prey, p)) continue;
            float toHer = (float) (Mth.atan2(prey.getZ() - (p.getZ() + 0.5D), prey.getX() - (p.getX() + 0.5D))
                    * Mth.RAD_TO_DEG) - 90F;
            BlockState sign = Blocks.DARK_OAK_SIGN.defaultBlockState()
                    .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(toHer));
            if (!sign.canSurvive(level, p)) continue;
            level.setBlock(p, sign, 3);
            where = p;
            if (level.getBlockEntity(p) instanceof SignBlockEntity be) {
                SignText text = new SignText().setMessage(1, Component.literal("I SEE YOU")).setColor(DyeColor.RED)
                        .setHasGlowingText(true);
                be.setText(text, true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(p, sign, sign, 3);
            }
            return true;
        }
        return false;
    }

    /** O chão (ar com bloco firme embaixo) perto de p, subindo/descendo até 6 blocos. */
    @Nullable
    private static BlockPos groundAt(ServerLevel level, BlockPos p) {
        for (int dy = 4; dy >= -6; dy--) {
            BlockPos q = p.above(dy);
            if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP)) {
                return q;
            }
        }
        return null;
    }

    /** Um ponto a min-max blocos dela, de preferência nas costas (fora da vista). */
    private static Vec3 around(ServerPlayer prey, double min, double max, boolean behind, RandomSource r) {
        Vec3 look = prey.getViewVector(1F).multiply(1, 0, 1);
        double base = look.lengthSqr() < 1.0E-4D ? r.nextDouble() * Math.PI * 2.0D : Math.atan2(look.z, look.x);
        double a = behind ? base + Math.PI + (r.nextDouble() - 0.5D) * Math.PI * 0.8D : r.nextDouble() * Math.PI * 2.0D;
        double d = min + r.nextDouble() * (max - min);
        return new Vec3(prey.getX() + Math.cos(a) * d, prey.getY() + 1.0D, prey.getZ() + Math.sin(a) * d);
    }

    // ---------------------------------------------------------------- quem é a presa

    @Nullable
    private static ServerPlayer prey(MinecraftServer server, FroggyWorldData data) {
        ServerPlayer kept = data.dirTarget == null ? null : server.getPlayerList().getPlayer(data.dirTarget);
        if (kept != null && kept.isAlive() && !kept.isSpectator() && !kept.isCreative()) return kept;
        List<ServerPlayer> runners = Invasion.runners(server);
        if (runners.isEmpty()) return null;
        ServerPlayer p = runners.get(server.overworld().random.nextInt(runners.size()));
        setPrey(data, p);
        return p;
    }

    private static void setPrey(FroggyWorldData data, ServerPlayer p) {
        data.dirTarget = p.getUUID();
        data.setDirty();
    }

    private static PreyStudy study(FroggyWorldData data, ServerPlayer p) {
        return data.brain.memory(p.getUUID(), p.getScoreboardName()).study;
    }

    // ---------------------------------------------------------------- teste e conversa

    private static String lastLog = "";

    private static void log(MinecraftServer server, String msg) {
        lastLog = msg;
        FroggydudeEntity f = FroggyKeeper.find(server);
        if (f != null) f.brainLog("diretor: " + msg);
        else if (Boolean.getBoolean("froggydude.debug")) System.out.println("[FROGGYDEBUG] diretor: " + msg);
    }

    /** /froggydude diretor: em que fase está e quanto falta. */
    public static Component status(MinecraftServer server) {
        FroggyWorldData data = FroggyWorldData.get(server);
        long now = server.overworld().getGameTime();
        Phase p = active(data) ? phase(data) : Phase.OFF;
        long left = Math.max(0L, data.dirUntil - now) / 20L;
        return Component.translatable("froggydude.cmd.director.status",
                Component.translatable("froggydude.cmd.director.phase." + p.name().toLowerCase(Locale.ROOT)),
                left / 60L, String.format(Locale.ROOT, "%02d", left % 60L),
                data.dirLastSign.isEmpty() ? "-" : data.dirLastSign.toLowerCase(Locale.ROOT));
    }

    /** Onde foi o último sinal que mexe num lugar (placa, porta, tocha, bicho): só pro log de teste. */
    @Nullable
    private static BlockPos where;

    /** /froggydude diretor sinal X: faz esse sinal agora perto de quem pediu (teste; vale fora do diretor também). */
    public static boolean forceSign(MinecraftServer server, ServerPlayer p, Sign sign) {
        FroggyWorldData data = FroggyWorldData.get(server);
        where = null;
        long t0 = System.nanoTime();
        boolean ok = doSign(server, data, p, sign);
        if (Boolean.getBoolean("froggydude.debug")) {
            String ms = String.format(Locale.ROOT, "%.2f ms", (System.nanoTime() - t0) / 1.0E6D);
            System.out.println("[FROGGYDEBUG] diretor: sinal " + sign.name() + (where != null ? " em " + where.getX() + " "
                    + where.getY() + " " + where.getZ() : ok ? "" : ": não deu") + " (" + ms + ")");
        }
        if (ok) {
            data.dirLastSign = sign.name();
            data.setDirty();
        }
        return ok;
    }

    /** /froggydude diretor fase X: pula pra essa fase agora (teste). */
    public static boolean force(MinecraftServer server, Phase phase) {
        FroggyWorldData data = FroggyWorldData.get(server);
        if (!active(data)) return false;
        ServerPlayer prey = prey(server, data);
        if (prey == null) return false;
        long now = server.overworld().getGameTime();
        FroggydudeEntity f = FroggyKeeper.find(server);
        switch (phase) {
            case CALM -> {
                if (f != null) park(server, data, f);
                calm(data, now, server.overworld().random, true, "teste: calmaria");
                trapFrom = now + 40L;
            }
            case SIGNS -> {
                if (f != null) park(server, data, f);
                enter(data, Phase.SIGNS, now, now + SIGNS_MIN + server.overworld().random.nextInt(SIGNS_RANDOM));
                data.dirNextSign = now + 40L;
                data.dirTrap = false;
            }
            case GLIMPSE -> {
                if (f != null) park(server, data, f);
                startGlimpse(server, data, prey, now);
            }
            case HUNT -> startHunt(server, data, prey, now, "teste");
            default -> {
                return false;
            }
        }
        log(server, "teste: fase " + phase.name());
        return true;
    }

    /**
     * O ciclo acabou de fora (o dragão morreu, começou um manhunt, o comando pulou pra chegada ou sorteou de novo): tudo
     * volta pro começo. Se ele estava guardado fora do mundo: keep põe ele de volta perto de near (o mesmo, com a
     * barriga); sem keep, ele devolve ali o que roubou e não volta mais. E nunca fica congelado de um vislumbre.
     */
    public static void stop(MinecraftServer server, FroggyWorldData data, @Nullable ServerPlayer near, boolean keep) {
        FroggydudeEntity live = FroggyKeeper.find(server);
        if (live != null) live.freezeForGlimpse(false);
        if (data.parkedFroggy != null) {
            if (near == null) {
                List<ServerPlayer> all = server.getPlayerList().getPlayers();
                near = all.isEmpty() ? null : all.get(0);
            }
            if (near != null) {
                FroggydudeEntity back = keep ? place(server, data, near, 16.0D, 32.0D, FroggyKeeper.Sight.HIDDEN, false) : null;
                if (back != null) {
                    wake(back);
                } else {
                    FroggydudeEntity ghost = ModEntityTypes.FROGGYDUDE.get().create(near.serverLevel());
                    if (ghost != null) {
                        ghost.load(data.parkedFroggy);
                        ghost.moveTo(near.getX(), near.getY(), near.getZ());
                        ghost.returnStolen();
                    }
                    data.parkedFroggy = null;
                }
            }
        }
        data.dirPhase = Phase.OFF.ordinal();
        data.dirSince = 0L;
        data.dirUntil = 0L;
        data.dirNextSign = 0L;
        data.dirTrap = false;
        data.dirTarget = null;
        data.setDirty();
        reset();
    }

    public static void reset() {
        STEPS.clear();
        measuring = null;
        vanishedAt = -1L;
        retreatSince = -1L;
        walkedOff = false;
        trapFrom = -1L;
        sleepTicks = 0;
        lastLog = "";
    }

}
