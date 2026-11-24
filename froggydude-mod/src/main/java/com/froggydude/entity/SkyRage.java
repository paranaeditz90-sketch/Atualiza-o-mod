package com.froggydude.entity;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.ai.FroggyFinisherGoal;
import com.froggydude.init.ModSounds;
import com.froggydude.network.ModNetwork;
import com.froggydude.network.SkyRagePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * (v0.7.62) A raiva do céu (item 6 da fila; vídeo do LM "MediaFire 13:44", 25-45 s). Quem matou ele, ele não morre:
 * <ol>
 * <li>fica mole no chão um instante e começa a SUBIR flutuando, devagar (no vídeo: ~3 blocos em ~3 s, o corpo mole, as
 * pernas penduradas), e depois cada vez mais rápido até virar um pontinho no céu (~50 blocos);</li>
 * <li>quem matou (e quem está perto) lê o aviso de conquista vermelho com sangue escorrendo "RUN FOR YOUR LIFE!!!! NOW!!" e
 * corre sem cansar até acabar (~10 s);</li>
 * <li>lá do alto ele segue por cima de quem matou e, aos 10 s, MERGULHA reto e muito rápido (no vídeo: do pontinho até a
 * cara em ~0,5 s), de pé e de braços abertos, até colar na cara; o grito rouco no mergulho;</li>
 * <li>a cara enche a tela, a tela fica vermelha e a pessoa morre (o mergulho mata na hora; LM 24/11);</li>
 * <li>ele fica na pose "()" da raiva uns 2 s e parte pro próximo. Volta com metade da vida.</li>
 * </ol>
 * Só com céu aberto em cima dele e num mundo com céu (no Nether não); quem matou tem que ser jogador (sobrevivência),
 * a até 64 blocos. Na dúvida, morre normal. Se quem matou sumir (saiu, mudou de mundo, foi longe), ele desce e caça normal.
 */
public final class SkyRage {

    /**
     * Parado antes de subir: o tombo do "sky_tip" (LM 24/11: reto ~2 s, depois o corpo cai de costas em ~0,9 s). Sobe
     * quando ele acaba de deitar.
     */
    static final int STILL = 58;
    /** A subida devagar do vídeo (~3 blocos em 3 s). */
    static final int RISE_SLOW = 60;
    private static final double SLOW_SPEED = 0.06D;
    /** Depois acelera até isso (blocos/tick) até chegar lá em cima. */
    private static final double RISE_MAX = 1.1D;
    private static final double RISE_ACCEL = 0.04D;
    /** Lá em cima: um pontinho (~50 blocos acima de quem matou). */
    private static final double TOP = 50.0D;
    /** Seguindo por cima de quem matou (blocos/tick). */
    private static final double FOLLOW = 0.55D;
    /** O mergulho começa aqui (ticks desde a morte): ~10 s de corrida, como pediu o LM. */
    static final int DIVE_AT = 200;
    /** Mergulho: blocos/tick (50 blocos em ~0,5 s). */
    private static final double DIVE_SPEED = 4.5D;
    private static final int DIVE_MAX = 40;
    /** Quanto ele afunda no chão no baque (medido na gravação: sem isso a câmera via a gola e a gravata). */
    private static final double SINK = 0.68D;
    /** A cara colada antes do vermelho (ticks). */
    private static final int FACE_TICKS = 2;
    /** Na pose "()" depois de matar. */
    static final int LAND_TICKS = 40;
    /** Fôlego infinito: a fome mínima pra poder correr. */
    private static final int SPRINT_FOOD = 7;

    public static final class Run {
        final UUID killer;
        final int startedAt;
        final List<UUID> runners;
        int phaseAt;
        double vy;
        boolean yelled;
        /** -1 = ainda não acertou. */
        int hitAt = -1;
        /** Chegou colado na cara (-1 = ainda não). */
        int arrivedAt = -1;
        /** Já trocou pra de pé e puxou a câmera (uns ticks antes de chegar). */
        boolean preLanded;
        /** Desceu sem matar (quem matou sumiu). */
        boolean aborting;

        Run(UUID killer, int startedAt, List<UUID> runners) {
            this.killer = killer;
            this.startedAt = startedAt;
            this.runners = runners;
        }
    }

    private SkyRage() {
    }

    /** Na morte: sorteia e, se der, começa (devolve true = não morre). */
    public static boolean tryStart(FroggydudeEntity froggy, DamageSource source) {
        if (!(froggy.level() instanceof ServerLevel level)) return false;
        if (!(source.getEntity() instanceof ServerPlayer killer) || killer.isCreative() || killer.isSpectator()) return false;
        if (froggy.getRandom().nextInt(100) >= FroggyConfig.get(FroggyConfig.RAIVA_CEU_CHANCE)) return false;
        return start(froggy, killer, false);
    }

    /** Começa agora contra {@code killer} (também pelo /froggydude ceu, que ignora a chance). */
    public static boolean start(FroggydudeEntity froggy, ServerPlayer killer, boolean command) {
        if (!(froggy.level() instanceof ServerLevel level) || killer.level() != level) return false;
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            froggy.brainLog("raiva do céu: sem céu aqui");
            return false;
        }
        if (!level.canSeeSky(froggy.blockPosition().above(2))) {
            froggy.brainLog("raiva do céu: teto em cima dele");
            return false;
        }
        if (froggy.distanceTo(killer) > 64.0F) return false;
        List<UUID> runners = new ArrayList<>();
        for (Player p : level.players()) {
            if (p instanceof ServerPlayer sp && !sp.isSpectator() && (sp == killer || sp.distanceTo(froggy) <= 64.0F)) {
                runners.add(sp.getUUID());
                ModNetwork.sendSkyRage(sp, SkyRagePacket.RUN, DIVE_AT);
            }
        }
        Run run = new Run(killer.getUUID(), froggy.tickCount, runners);
        run.phaseAt = froggy.tickCount;
        froggy.beginSkyRage(run);
        froggy.level().playSound(null, froggy.getX(), froggy.getY() + 1.0D, froggy.getZ(), ModSounds.CONTORT_CRACK.get(),
                SoundSource.HOSTILE, 3.0F, 0.8F);
        froggy.brainLog("raiva do céu: " + killer.getScoreboardName() + " me matou; subo e mergulho nele"
                + (command ? " (comando)" : ""));
        return true;
    }

    public static void tick(FroggydudeEntity froggy, Run run) {
        if (!(froggy.level() instanceof ServerLevel level)) return;
        int t = froggy.tickCount - run.startedAt;
        ServerPlayer killer = level.getServer().getPlayerList().getPlayer(run.killer);
        boolean valid = killer != null && killer.isAlive() && killer.level() == level && !killer.isCreative()
                && !killer.isSpectator() && killer.distanceTo(froggy) < 160.0F;
        if (run.hitAt < 0) stamina(level, run);
        FroggyState s = froggy.getFroggyState();
        if (!valid && run.hitAt < 0 && !run.aborting) {
            run.aborting = true;
            froggy.brainLog("raiva do céu: quem me matou sumiu; desço");
            for (UUID id : run.runners) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) ModNetwork.sendSkyRage(p, SkyRagePacket.END, 0);
            }
        }
        froggy.setDeltaMovement(Vec3.ZERO);
        froggy.fallDistance = 0F;
        if (run.aborting) {
            // desce devagar até o chão e volta a caçar
            Vec3 at = froggy.position();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
            double y = Math.max(ground, at.y - 0.9D);
            froggy.moveTo(at.x, y, at.z, froggy.getYRot(), 0F);
            if (y <= ground + 0.01D) froggy.endSkyRage();
            return;
        }
        if (s == FroggyState.SKY_RISE) {
            Vec3 at = froggy.position();
            if (t < STILL) return;
            double nx = at.x, nz = at.z, ny;
            if (t < STILL + RISE_SLOW) {
                ny = at.y + SLOW_SPEED;
            } else {
                run.vy = Math.min(RISE_MAX, Math.max(run.vy, SLOW_SPEED) + RISE_ACCEL);
                double top = Math.min(killer.getY() + TOP, level.getMaxBuildHeight() - 4);
                ny = Math.min(top, at.y + run.vy);
                // por cima de quem matou (um pontinho bem em cima de onde ele está)
                Vec3 h = new Vec3(killer.getX() - at.x, 0, killer.getZ() - at.z);
                double len = h.length();
                if (len > 0.05D) {
                    double step = Math.min(len, FOLLOW * Math.min(1.0D, (t - STILL - RISE_SLOW) / 40.0D));
                    nx += h.x / len * step;
                    nz += h.z / len * step;
                }
            }
            float spin = froggy.getYRot() + 1.6F; // meio girando, devagar, como um corpo pendurado
            froggy.moveTo(nx, ny, nz, spin, 0F);
            froggy.setYHeadRot(spin);
            froggy.setYBodyRot(spin);
            if (t >= DIVE_AT) beginDive(froggy, run, killer);
            return;
        }
        if (s == FroggyState.SKY_LAND) {
            if (froggy.stateTicks() >= LAND_TICKS) froggy.endSkyRage();
            return;
        }
        if (s == FroggyState.SKY_DIVE || run.preLanded) {
            if (run.hitAt >= 0) {
                // acertou: na cara (a tela dela fica vermelha) e depois desce pro chão na pose "()"
                if (froggy.tickCount - run.hitAt >= 6) land(froggy, level);
                return;
            }
            if (run.arrivedAt >= 0) {
                // colado na cara: o cliente (sem deslize) mostra ele aqui no tick seguinte; a cara enche a tela uns 2
                // ticks e aí vem o vermelho
                Vec3 face = faceSpot(killer);
                froggy.moveTo(face.x, face.y, face.z, yawTo(face, killer), 0F);
                froggy.setYHeadRot(froggy.getYRot());
                froggy.setYBodyRot(froggy.getYRot());
                if (Boolean.getBoolean("froggydude.debug")) {
                    System.out.println(String.format(java.util.Locale.ROOT,
                            "[FROGGYDEBUG] ceu cara t=%d froggy y=%.2f jogador y=%.2f olho=%.2f dist=%.2f estado=%s furia=%s",
                            froggy.tickCount - run.arrivedAt, froggy.getY(), killer.getY(), killer.getEyeY(),
                            froggy.position().subtract(killer.position()).horizontalDistance(), froggy.getFroggyState(),
                            froggy.isFrenzyActive()));
                }
                if (froggy.tickCount - run.arrivedAt >= FACE_TICKS) hit(froggy, run, killer, level);
                return;
            }
            Vec3 at = froggy.position();
            Vec3 face = faceSpot(killer);
            Vec3 d = face.subtract(at);
            double len = d.length();
            int dt = froggy.tickCount - run.phaseAt;
            if (len <= DIVE_SPEED || dt > DIVE_MAX) {
                froggy.moveTo(face.x, face.y, face.z, yawTo(face, killer), 0F);
                froggy.setYHeadRot(froggy.getYRot());
                froggy.setYBodyRot(froggy.getYRot());
                run.arrivedAt = froggy.tickCount;
                if (!run.preLanded) {
                    run.preLanded = true;
                    froggy.setFroggyState(FroggyState.CHASE); // aterrissou: curvado, a cara na altura do olho dela
                    ModNetwork.sendSkyRage(killer, SkyRagePacket.FACE, 0);
                } // a câmera dela vem pra frente, na cara dele
                return;
            }
            if (!run.preLanded && len < DIVE_SPEED * 3.0D) {
                // ~3 ticks antes de chegar: já na pose curvada da fúria (o CHASE em fase 2) e a câmera dela vem pra frente.
                // Medido: de pé (TAUNT) ou esticado (o mergulho) o modelo é bem mais alto que a caixa e a câmera via o
                // peito; curvado, a cara dele fica na altura do olho dela (como no susto do beco, que chega correndo)
                run.preLanded = true;
                froggy.setFroggyState(FroggyState.CHASE);
                froggy.setRunningFlag(true); // o galope curvado da fúria (a corrida da fase 2), como no susto do beco
                ModNetwork.sendSkyRage(killer, SkyRagePacket.FACE, 0);
            }
            Vec3 n = at.add(d.scale(DIVE_SPEED / len));
            froggy.moveTo(n.x, n.y, n.z, yawTo(n, killer), 0F);
            froggy.setYHeadRot(froggy.getYRot());
            // atravessando teto/árvore: o estrondo dos blocos (sem quebrar nada)
            BlockPos bp = BlockPos.containing(n.x, n.y + 1.0D, n.z);
            BlockState bs = level.getBlockState(bp);
            if (!bs.isAir() && !bs.getCollisionShape(level, bp).isEmpty()) {
                level.levelEvent(2001, bp, Block.getId(bs));
            }
            return;
        }
    }

    private static void beginDive(FroggydudeEntity froggy, Run run, ServerPlayer killer) {
        run.phaseAt = froggy.tickCount;
        froggy.setSnap(true); // o cliente sem deslize: senão o mergulho de 4,5 blocos/tick chegava atrasado
        froggy.setFroggyState(FroggyState.SKY_DIVE);
        froggy.openMouth(60);
        // em fúria (a fase 2, sem a contorção): curvado, a cara dele fica na altura do olho de quem ele encara (igual ao
        // susto do beco); em pé normal o modelo é bem mais alto que a caixa e a câmera via o peito. E é a raiva que fica
        // quando ele parte pro próximo
        if (!froggy.isFrenzyActive()) froggy.startFrenzy(400);
        // o grito rouco no ouvido de quem matou (2 cópias: alto), e no lugar dele lá em cima
        long seed = froggy.getRandom().nextLong();
        for (int k = 0; k < 2; k++) {
            killer.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.FRENZY_RUN.get()),
                    SoundSource.HOSTILE, killer.getX(), killer.getEyeY(), killer.getZ(), 1.0F, 1.0F, seed));
        }
        froggy.level().playSound(null, froggy.getX(), froggy.getY(), froggy.getZ(), ModSounds.FRENZY_RUN.get(),
                SoundSource.HOSTILE, 8.0F, 1.0F);
        froggy.brainLog("raiva do céu: mergulho em " + killer.getScoreboardName());
    }

    private static void hit(FroggydudeEntity froggy, Run run, ServerPlayer killer, ServerLevel level) {
        run.hitAt = froggy.tickCount;
        ModNetwork.sendSkyRage(killer, SkyRagePacket.HIT, 0);
        for (UUID id : run.runners) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null && p != killer) ModNetwork.sendSkyRage(p, SkyRagePacket.END, 0);
        }
        level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), ModSounds.SKY_LAND.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
        level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), ModSounds.CRACK.get(), SoundSource.HOSTILE, 2.5F, 0.9F);
        killer.hurt(FroggyFinisherGoal.devoured(level, froggy), Float.MAX_VALUE);
        froggy.brainLog("raiva do céu: matei " + killer.getScoreboardName());
    }

    /** Desce pro chão embaixo de onde está e fica na pose "()". */
    private static void land(FroggydudeEntity froggy, ServerLevel level) {
        Vec3 at = froggy.position();
        BlockPos p = BlockPos.containing(at.x, at.y + 0.5D, at.z);
        for (int i = 0; i < 6 && level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty(); i++) {
            p = p.below();
        }
        froggy.moveTo(at.x, p.getY(), at.z, froggy.getYRot(), 0F);
        froggy.setSnap(false);
        froggy.setRunningFlag(false);
        froggy.setFroggyState(FroggyState.SKY_LAND);
    }

    /** Fôlego infinito pra quem foi avisado: sem gastar fome e com fome pra correr. */
    private static void stamina(ServerLevel level, Run run) {
        for (UUID id : run.runners) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive()) continue;
            var food = p.getFoodData();
            food.addExhaustion(-food.getExhaustionLevel());
            if (food.getFoodLevel() < SPRINT_FOOD) food.setFoodLevel(SPRINT_FOOD);
        }
    }

    /**
     * Colado na cara: 0,45 bloco na frente dela, na horizontal, nos pés dela, um tico à esquerda; de pé (TAUNT) a cara dele
     * fica na altura do olho dela (igual ao susto do beco). Na chegada a câmera dela é puxada pra frente
     * (SkyRagePacket.FACE): olhando pro pontinho lá em cima, ela via o corpo dele por baixo e não a cara.
     */
    static Vec3 faceSpot(ServerPlayer p) {
        Vec3 flat = p.getViewVector(1.0F).multiply(1, 0, 1);
        flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 left = new Vec3(flat.z, 0, -flat.x);
        Vec3 at = p.position().add(flat.scale(0.45D)).add(left.scale(0.12D));
        // o baque afunda ele um tico no chão: a cabeça dele (o modelo é bem mais alto que a caixa) desce até o olho dela
        return new Vec3(at.x, p.getY() - SINK, at.z);
    }

    private static float yawTo(Vec3 from, ServerPlayer p) {
        Vec3 d = p.position().subtract(from);
        if (d.horizontalDistanceSqr() < 1.0E-4D) return p.getYRot() + 180.0F;
        return (float) (Math.atan2(d.z, d.x) * (180D / Math.PI)) - 90.0F;
    }

    /** Pra mensagens de depuração. */
    @Nullable
    static String phase(FroggydudeEntity f) {
        return f.getFroggyState().name();
    }
}
