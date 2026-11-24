package com.froggydude.entity;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.ai.FroggyFinisherGoal;
import com.froggydude.init.ModSounds;
import com.froggydude.network.ModNetwork;
import com.froggydude.network.SkyRagePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
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
 * (v0.7.63, LM 24/11) Com a voz de "profecia" dele (o vídeo de referência do LM, ~17 s): a voz começa na morte; ele
 * fica reto, tomba de costas, sobe um pouco e GIRA deitado em volta do próprio eixo (~5,5 voltas) até a voz acabar; aí
 * dispara pro céu e vem o aviso. E o mergulho dá pra DESVIAR (LM: "não matar o jogador logo de cara, ficaria
 * injogável").
 * (v0.7.64, LM 24/11, o vídeo do deserto) Sem a sombra no chão: a subida depois da voz e o mergulho são em ONDA (sobe uns
 * 3-4 blocos e volta, sem parar de vir), e ele fica lá em cima NA FRENTE de quem matou, pra dar pra ver ele vindo. O
 * mergulho vem em linha reta na pessoa, ondulando; a 5 blocos ele trava a mira: quem já saiu do caminho escapa (ele passa
 * reto e bate no chão: cratera pequena e tonto ~4 s, apanhando). Quem não saiu: a cara enche a tela, a câmera treme e o
 * vermelho vai subindo (~1,2 s) até a tela de morte.
 * Só com céu aberto em cima dele e num mundo com céu (no Nether não); quem matou tem que ser jogador (sobrevivência),
 * a até 64 blocos. Na dúvida, morre normal. Se quem matou sumir (saiu, mudou de mundo, foi longe), ele desce e caça normal.
 */
public final class SkyRage {

    /**
     * Parado antes de subir: o tombo do "sky_tip" (LM 24/11: reto ~2,8 s, depois o corpo cai de costas em ~0,9 s). Sobe
     * quando ele acaba de deitar.
     */
    static final int STILL = 74;
    /** A subida devagar do vídeo (~3 blocos em 3 s); depois fica parado no ar girando até a voz acabar. */
    static final int RISE_SLOW = 60;
    private static final double SLOW_SPEED = 0.05D;
    /** A voz de "profecia" (froggydude.sky_prophecy, 17,1 s): o giro dura isso; acabou, ele dispara pro céu. */
    static final int PROPHECY = 342;
    /** O giro deitado (graus/tick): ~5,5 voltas do fim do tombo até o fim da voz (LM: "umas 5-6 voltas"). */
    private static final float SPIN_RATE = 360F * 5.5F / (PROPHECY - STILL);
    /** Do aviso "RUN FOR YOUR LIFE" até o mergulho: ~10 s. */
    static final int RUN_TICKS = 200;
    /** Depois acelera até isso (blocos/tick) até chegar lá em cima (devagar o bastante pra onda aparecer). */
    private static final double RISE_MAX = 0.6D;
    private static final double RISE_ACCEL = 0.03D;
    /** Lá em cima: um pontinho ~32 blocos acima e ~24 na frente de quem matou (dá pra ver ele vindo). */
    private static final double TOP = 32.0D;
    private static final double HOVER_DIST = 24.0D;
    /** Indo pro lugar lá em cima e acompanhando quem matou (blocos/tick). */
    private static final double FOLLOW = 0.8D;
    /** A onda (LM: "uns 3-4 blocos subindo e voltando"): altura e período (ticks). */
    private static final double WAVE_H = 3.5D;
    private static final int WAVE_T = 12;
    /** O mergulho começa aqui (ticks desde a morte): a voz e depois ~10 s de corrida. */
    static final int DIVE_AT = PROPHECY + RUN_TICKS;
    /** A esta distância ele trava a mira (LM: "precisa desviar antes de 5 blocos"). */
    static final double LOCK_DIST = 5.0D;
    /** Chegando no ponto travado: quem está a mais que isso dele (na horizontal) escapou. */
    static final double HIT_R = 1.0D;
    /** Tonto depois de errar. */
    static final int STUN_TICKS = 80;
    /** Mergulho: blocos/tick (~40 blocos em ~2 s, ondulando). */
    private static final double DIVE_SPEED = 1.0D;
    private static final int DIVE_MAX = 160;
    /**
     * Quanto ele afunda no chão no baque. (v0.7.64) Medido na gravação com a cara segurada ~1,3 s (o galope curvado parado):
     * 0,68 mostrava o alto da cabeça, 0,38 a coroa, 0,20 a cara; 0,15 põe os olhos na altura do olho.
     */
    private static final double SINK = 0.15D;
    /** A cara colada, a câmera tremendo e o vermelho subindo, antes de morrer (ticks). */
    private static final int FACE_TICKS = 24;
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
        /** Já mandou o aviso "RUN FOR YOUR LIFE" (no fim da voz). */
        boolean warned;
        /** A altura "sem a onda" da subida depois da voz (NaN = ainda não). */
        double baseY = Double.NaN;
        /** De que lado de quem matou ele fica lá em cima (na horizontal, pra onde ela olhava no aviso). */
        Vec3 side = new Vec3(0, 0, 1);
        /** O mergulho: o ponto na linha reta (a onda vai por cima disso) e a direção. */
        @Nullable
        Vec3 base;
        Vec3 dir = new Vec3(0, -1, 0);
        /** A mira travada (a 5 blocos; null = ainda seguindo a pessoa). */
        @Nullable
        Vec3 lock;
        /** Já decidiu: pegou (vai pra cara) ou errou (passa reto e bate no chão). */
        boolean committed;
        boolean missing;

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
                // a voz de profecia presa na pessoa (segue ela, não some se correr)
                sp.connection.send(new ClientboundSoundEntityPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                        ModSounds.SKY_PROPHECY.get()), SoundSource.HOSTILE, sp, 1.0F, 1.0F, froggy.getRandom().nextLong()));
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
        if (run.hitAt < 0 && run.warned) stamina(level, run);
        FroggyState s = froggy.getFroggyState();
        if (!valid && run.hitAt < 0 && !run.aborting) {
            run.aborting = true;
            froggy.brainLog("raiva do céu: quem me matou sumiu; desço");
            for (UUID id : run.runners) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p == null) continue;
                ModNetwork.sendSkyRage(p, SkyRagePacket.END, 0);
                p.connection.send(new ClientboundStopSoundPacket(ModSounds.SKY_PROPHECY.get().getLocation(), SoundSource.HOSTILE));
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
            } else if (t < PROPHECY) {
                ny = at.y; // parado no ar, girando, até a voz acabar
            } else {
                if (!run.warned) warn(froggy, run, killer, level);
                if (Double.isNaN(run.baseY)) run.baseY = at.y;
                // sobe em ONDA (o vídeo do deserto: sobe, volta um pouco, sobe) até o lugar lá em cima, na frente dela
                Vec3 hover = hoverSpot(killer, run, level);
                run.vy = Math.min(RISE_MAX, Math.max(run.vy, SLOW_SPEED) + RISE_ACCEL);
                run.baseY = Math.min(hover.y, run.baseY + run.vy);
                ny = run.baseY + wave(t - PROPHECY);
                Vec3 h = new Vec3(hover.x - at.x, 0, hover.z - at.z);
                double len = h.length();
                if (len > 0.05D) {
                    double step = Math.min(len, FOLLOW * Math.min(1.0D, (t - PROPHECY) / 40.0D));
                    nx += h.x / len * step;
                    nz += h.z / len * step;
                }
            }
            // deitado, girando em volta do próprio eixo durante a voz (como um espeto); subindo, meio girando, devagar
            float spin = Mth.wrapDegrees(froggy.getYRot() + (t < PROPHECY ? SPIN_RATE : 1.6F));
            froggy.moveTo(nx, ny, nz, spin, 0F);
            froggy.setYHeadRot(spin);
            froggy.setYBodyRot(spin);
            if (t >= DIVE_AT) beginDive(froggy, run, killer);
            return;
        }
        if (s == FroggyState.SKY_STUN) {
            if (froggy.stateTicks() >= STUN_TICKS) froggy.endSkyRage();
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
            int dt = froggy.tickCount - run.phaseAt;
            if (run.base == null) run.base = froggy.position();
            if (run.missing) {
                // passou reto (a pessoa saiu do caminho): segue na mesma direção até bater no chão
                Vec3 n = run.base.add(run.dir.scale(DIVE_SPEED * 1.5D));
                BlockPos bp = BlockPos.containing(n);
                boolean ground = !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()
                        || n.y <= level.getMinBuildHeight() + 1;
                if (ground || dt > DIVE_MAX) {
                    crash(froggy, run, run.base, level);
                    return;
                }
                run.base = n;
                froggy.moveTo(n.x, n.y, n.z, froggy.getYRot(), 0F);
                return;
            }
            if (run.committed) {
                // pegou: vai pra cara dela (curvado, a cara na altura do olho)
                Vec3 face = faceSpot(killer);
                Vec3 d = face.subtract(run.base);
                double len = d.length();
                if (len <= DIVE_SPEED * 1.5D || dt > DIVE_MAX) {
                    froggy.moveTo(face.x, face.y, face.z, yawTo(face, killer), 0F);
                    froggy.setYHeadRot(froggy.getYRot());
                    froggy.setYBodyRot(froggy.getYRot());
                    run.arrivedAt = froggy.tickCount;
                    return;
                }
                run.base = run.base.add(d.scale(DIVE_SPEED * 1.5D / len));
                froggy.moveTo(run.base.x, run.base.y, run.base.z, yawTo(run.base, killer), 0F);
                froggy.setYHeadRot(froggy.getYRot());
                return;
            }
            Vec3 chest = killer.position().add(0, 1.0D, 0);
            if (run.lock == null && run.base.distanceTo(chest) <= LOCK_DIST) {
                run.lock = chest; // a 5 blocos: trava a mira aqui
                froggy.brainLog("raiva do céu: travei a mira em " + killer.getScoreboardName());
            }
            Vec3 aim = run.lock != null ? run.lock : chest;
            Vec3 d = aim.subtract(run.base);
            double len = d.length();
            if (run.lock != null && len <= DIVE_SPEED * 1.5D) {
                // chegou no ponto travado: ainda está ali? pega. Saiu? passa reto
                Vec3 k = killer.position();
                double off = Math.sqrt((k.x - aim.x) * (k.x - aim.x) + (k.z - aim.z) * (k.z - aim.z));
                if (off <= HIT_R && Math.abs(k.y + 1.0D - aim.y) < 2.5D) {
                    run.committed = true;
                    run.preLanded = true;
                    froggy.setFroggyState(FroggyState.CHASE); // o galope curvado da fúria: a cara na altura do olho
                    froggy.setRunningFlag(true);
                    froggy.setMovingFlag(true); // segura a corrida curvada (a cara na altura do olho) o tempo todo
                    ModNetwork.sendSkyRage(killer, SkyRagePacket.FACE, 0); // a câmera dela vem pra cara dele, tremendo
                } else {
                    run.missing = true;
                    froggy.brainLog(String.format(java.util.Locale.ROOT, "raiva do céu: %s desviou (%.1f blocos)",
                            killer.getScoreboardName(), off));
                }
                return;
            }
            if (len > 1.0E-4D) run.dir = d.scale(1.0D / len);
            Vec3 n = run.base.add(run.dir.scale(Math.min(len, DIVE_SPEED)));
            run.base = n;
            // a onda por cima da linha reta, sumindo perto da trava (chega reto na mira)
            double taper = Mth.clamp((n.distanceTo(chest) - LOCK_DIST) / 10.0D, 0.0D, 1.0D);
            double y = n.y + wave(dt) * taper;
            froggy.moveTo(n.x, y, n.z, yawTo(n, killer), 0F);
            froggy.setYHeadRot(froggy.getYRot());
            // atravessando teto/árvore: o estrondo dos blocos (sem quebrar nada)
            BlockPos bp = BlockPos.containing(n.x, y + 1.0D, n.z);
            BlockState bs = level.getBlockState(bp);
            if (!bs.isAir() && !bs.getCollisionShape(level, bp).isEmpty()) {
                level.levelEvent(2001, bp, Block.getId(bs));
            }
            return;
        }
    }

    /** A onda: sobe até WAVE_H e volta, a cada WAVE_T ticks. */
    private static double wave(int k) {
        double sn = Math.sin(Math.PI * k / WAVE_T);
        return WAVE_H * sn * sn;
    }

    /** Lá em cima: na frente de quem matou (do lado que ela olhava no aviso), alto. */
    private static Vec3 hoverSpot(ServerPlayer killer, Run run, ServerLevel level) {
        double y = Math.min(killer.getY() + TOP, level.getMaxBuildHeight() - 8);
        return new Vec3(killer.getX() + run.side.x * HOVER_DIST, y, killer.getZ() + run.side.z * HOVER_DIST);
    }

    /** Acabou a voz: o aviso "RUN FOR YOUR LIFE" e a corrida sem cansar (~10 s) até o mergulho. */
    private static void warn(FroggydudeEntity froggy, Run run, ServerPlayer killer, ServerLevel level) {
        run.warned = true;
        Vec3 look = killer.getViewVector(1.0F).multiply(1, 0, 1);
        run.side = look.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : look.normalize();
        for (UUID id : run.runners) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) ModNetwork.sendSkyRage(p, SkyRagePacket.RUN, RUN_TICKS);
        }
        froggy.brainLog("raiva do céu: acabou a profecia; subo e mergulho daqui a " + RUN_TICKS / 20 + " s");
    }

    /** Errou: bate no chão onde passou, cratera pequena, quem estava perto é jogado, e fica tonto. */
    private static void crash(FroggydudeEntity froggy, Run run, Vec3 at, ServerLevel level) {
        BlockPos base = BlockPos.containing(at.x, at.y + 0.5D, at.z);
        for (int i = 0; i < 6 && level.getBlockState(base.below()).getCollisionShape(level, base.below()).isEmpty(); i++) {
            base = base.below();
        }
        if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            // a cratera: uma bacia rasa (raio 2, 1 de fundo; 2 no meio), só terra/pedra/areia (nada com conteúdo, nada duro)
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > 5) continue;
                    for (int dy = 1; dy <= (d2 <= 1 ? 2 : 1); dy++) {
                        BlockPos q = base.offset(dx, -dy, dz);
                        BlockState bs = level.getBlockState(q);
                        float hard = bs.getDestroySpeed(level, q);
                        if (bs.isAir() || hard < 0F || hard > 3.0F || bs.hasBlockEntity() || !bs.getFluidState().isEmpty()) continue;
                        level.destroyBlock(q, false, froggy);
                    }
                }
            }
        }
        base = base.below();
        while (base.getY() > level.getMinBuildHeight()
                && level.getBlockState(base.below()).getCollisionShape(level, base.below()).isEmpty()) {
            base = base.below();
        }
        froggy.moveTo(at.x, base.getY(), at.z, froggy.getYRot(), 0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, base.getY() + 0.5D, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, base.getY() + 0.3D, at.z, 30, 1.5D, 0.2D, 1.5D, 0.02D);
        level.playSound(null, at.x, base.getY(), at.z, ModSounds.SKY_LAND.get(), SoundSource.HOSTILE, 4.0F, 0.8F);
        level.playSound(null, at.x, base.getY(), at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0F, 0.7F);
        for (Player p : level.players()) {
            double d = p.position().distanceTo(at);
            if (d < 6.0D && !p.isSpectator()) {
                Vec3 push = p.position().subtract(at).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : push.normalize();
                p.push(push.x * 0.9D, 0.45D, push.z * 0.9D);
                p.hurtMarked = true;
            }
        }
        for (UUID id : run.runners) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) ModNetwork.sendSkyRage(p, SkyRagePacket.END, 0);
        }
        run.hitAt = froggy.tickCount; // a corrida sem cansar acaba
        froggy.setSnap(false);
        froggy.setRunningFlag(false);
        froggy.noPhysics = false;
        froggy.setNoGravity(false);
        froggy.setFroggyState(FroggyState.SKY_STUN);
        froggy.brainLog("raiva do céu: errei, bati no chão; tonto");
    }

    private static void beginDive(FroggydudeEntity froggy, Run run, ServerPlayer killer) {
        run.phaseAt = froggy.tickCount;
        run.base = new Vec3(froggy.getX(), Double.isNaN(run.baseY) ? froggy.getY() : run.baseY, froggy.getZ());
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
        froggy.setMovingFlag(false);
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
