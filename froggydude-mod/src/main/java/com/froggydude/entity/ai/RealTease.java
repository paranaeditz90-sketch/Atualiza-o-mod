package com.froggydude.entity.ai;

import com.froggydude.brain.PreyStudy;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.init.ModSounds;
import com.froggydude.player.ArmLoss;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * O "tempero" (IA Real, v0.6): antes de comer, ele brinca com a presa uns minutos -
 * "o FroggyDude estuda a presa e vê o que mais assusta ou deixa o jogador
 * paranoico, pra deixar a carne mais saborosa" (LM). Um susto, um tempo sumido,
 * outro susto. Cada susto é medido (ScareJudge: ela ficou paranoica? saiu
 * correndo?) e o que funciona com ESSA pessoa ele repete mais (Thompson, igual
 * aos ataques). Quando o medo dela chega lá (PreyStudy#fear) ou o tempo acaba:
 * "I can smell the fear in you! Time to eat!" e aí é caçada.
 *
 * Acaba na hora se ela bater nele ou fugir pra longe (LM: "brinca um tempo e
 * ataca"). Uma vez por dia do jogo, no máximo.
 */
final class RealTease {

    enum Act {
        /** Aparece parado de longe onde ela vai ver, encara e some. */
        PEEK,
        /** Chega calado atrás dela, ri baixinho no ouvido e sai correndo quando ela vira. */
        BEHIND,
        /** Ela trancada em casa: aparece na janela olhando pra dentro. */
        WINDOW,
        /** Sem aparecer: passos, ribbit, a risadinha, de lugares diferentes atrás dela. */
        NOISE,
        /** Come um bicho perto dela, onde ela vê. */
        EAT
    }

    /** Brinca de 2 a 4 minutos. */
    private static final int TEASE_MIN = 2400;
    private static final int TEASE_RANDOM = 2400;
    /** Uma brincadeira por dia do jogo. */
    private static final long TEASE_EVERY = 24000L;
    /** Medo bom pra comer. */
    private static final float FEAR_READY = 70.0F;
    /** Fugiu pra mais longe que isso: acabou a brincadeira. */
    private static final double FLED_FAR = 48.0D;
    /** Lá em cima onde ele não chega andando há esse tempo: acabou a brincadeira. */
    private static final int UP_HIGH = 160;
    /** Sumido entre um susto e outro. */
    private static final int CALM_MIN = 300;
    private static final int CALM_RANDOM = 400;

    private final FroggydudeEntity froggy;
    private final RealTactics tactics;
    private final FroggyCombatGoal combat;
    private final ScareJudge judge;

    private boolean active;
    private int activeSince;
    /** Onde ela estava quando a brincadeira começou (ou voltou): fugir é ela sair de perto daqui. */
    @Nullable
    private Vec3 anchor;
    @Nullable
    private Act act;
    @Nullable
    private Act lastAct;
    private int actAt;
    private int stage;
    private int stageAt;
    private int seenTicks;
    private int noises;
    private int nextNoise;
    private int calmUntil;
    @Nullable
    private Vec3 spot;
    private int spotAt;
    private int upHighTicks;

    RealTease(FroggydudeEntity froggy, RealTactics tactics, FroggyCombatGoal combat) {
        this.froggy = froggy;
        this.tactics = tactics;
        this.combat = combat;
        this.judge = new ScareJudge(froggy);
    }

    boolean active() {
        return active;
    }

    /** Começou a caçada contra ela: é hora de brincar antes? */
    void begin(ServerPlayer p) {
        active = false;
        act = null;
        spot = null;
        judge.cancel();
        if (!(froggy.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        PreyStudy st = study(level, p);
        if (st.teasing(now)) {
            // voltou depois de um susto (ou da tocaia): um tempo sumido antes do próximo
            resume(p, "continua brincando com ");
            calmUntil = froggy.tickCount + 200 + froggy.getRandom().nextInt(200);
            return;
        }
        // sangrando sem braço, ferido querendo comer, fase 2: nada de brincadeira
        if (ArmLoss.isArmless(p) || froggy.wantsToHeal() || froggy.isFrenzyActive()) return;
        if (now - st.teaseStarted < TEASE_EVERY || froggy.getRandom().nextFloat() > 0.85F) return;
        st.teaseStarted = now;
        st.teaseUntil = now + TEASE_MIN + froggy.getRandom().nextInt(TEASE_RANDOM);
        FroggyWorldData.get(level).setDirty();
        resume(p, "vai brincar (o tempero) com ");
    }

    private void resume(ServerPlayer p, String what) {
        active = true;
        activeSince = froggy.tickCount;
        anchor = p.position();
        calmUntil = 0;
        upHighTicks = 0;
        if (froggy.level() instanceof ServerLevel level) {
            PreyStudy st = study(level, p);
            long now = level.getGameTime();
            froggy.brainLog(String.format(java.util.Locale.ROOT, "%s%s antes de comer: medo %.0f, mais %d s", what,
                    p.getScoreboardName(), st.fear(now), Math.max(0L, st.teaseUntil - now) / 20));
        }
    }

    void reset() {
        active = false;
        act = null;
        spot = null;
        judge.cancel();
    }

    /** Devolve true se cuidou do tick (brincando: nada de atacar). */
    boolean tick(ServerPlayer p, double dist, boolean seen) {
        judge.tick(p);
        if (!active || !(froggy.level() instanceof ServerLevel level)) return false;
        PreyStudy st = study(level, p);
        long now = level.getGameTime();
        if (froggy.getLastHurtByMob() == p && froggy.getLastHurtByMobTimestamp() > activeSince) {
            end(level, st, p, "me bateu: acabou a brincadeira", false);
            return false;
        }
        // fugiu: longe dele E foi ELA que andou (antes era só a distância entre os dois, e ele mesmo
        // indo se esconder longe "acabava" a brincadeira); ou ele perdeu ela de vez
        boolean sheLeft = anchor != null && p.position().distanceTo(anchor) > FLED_FAR * 0.5D;
        if (dist > FLED_FAR && sheLeft || dist > FLED_FAR * 1.5D) {
            end(level, st, p, "fugiu pra longe: acabou a brincadeira", false);
            return false;
        }
        // subiu onde ele não chega andando (árvore, pilar, telhado): fugir pra cima também é fugir.
        // Antes ele seguia brincando e ficava rodando embaixo tentando chegar no ouvido dela (o
        // susto pelas costas) - "não sabe o que fazer" (print do LM, 22/10). A caçada sabe subir:
        // língua nas folhas, escalar, o pulo calculado.
        if (froggy.tickCount % 20 == 0) upHighTicks = upHigh(p) ? upHighTicks + 20 : 0;
        if (upHighTicks >= UP_HIGH) {
            end(level, st, p, "subiu onde eu não chego: acabou a brincadeira", true);
            return false;
        }
        if (st.fear(now) >= FEAR_READY || !st.teasing(now)) {
            end(level, st, p, st.fear(now) >= FEAR_READY ? "o medo chegou no ponto" : "cansou de brincar", true);
            return false;
        }
        froggy.mute(10);
        seenTicks = seen ? seenTicks + 1 : 0;
        if (act == null) {
            if (froggy.tickCount < calmUntil) {
                calm(p, dist, seen);
                return true;
            }
            act = pick(p);
            lastAct = act;
            actAt = froggy.tickCount;
            stage = 0;
            stageAt = froggy.tickCount;
            spot = null;
            noises = 0;
            judge.start(p, act.name().toLowerCase(java.util.Locale.ROOT));
            froggy.brainLog("susto em " + p.getScoreboardName() + ": " + switch (act) {
                case PEEK -> "aparece de longe e some";
                case BEHIND -> "chega calado e ri no ouvido";
                case WINDOW -> "aparece na janela";
                case NOISE -> "barulho sem aparecer";
                case EAT -> "come um bicho na frente dela";
            });
        }
        boolean going = switch (act) {
            case PEEK -> peek(p, dist);
            case BEHIND -> behind(p, dist, seen);
            case WINDOW -> window(p);
            case NOISE -> noise(p, seen);
            case EAT -> eat(p);
        };
        if (!going || froggy.tickCount - actAt > 900) {
            // comer demora: o veredito espera ela ver
            judge.closeIn(act == Act.EAT ? 300 : 120);
            act = null;
            spot = null;
            calmUntil = froggy.tickCount + CALM_MIN + froggy.getRandom().nextInt(CALM_RANDOM);
        }
        return true;
    }

    /** Ela está acima dele e não tem caminho andando até ela. */
    private boolean upHigh(ServerPlayer p) {
        return p.getY() - froggy.getY() > 2.5D && !tactics.pathTo(p);
    }

    private void end(ServerLevel level, PreyStudy st, ServerPlayer p, String why, boolean ready) {
        long now = level.getGameTime();
        st.teaseUntil = now;
        FroggyWorldData.get(level).setDirty();
        active = false;
        act = null;
        froggy.brainLog(String.format(java.util.Locale.ROOT, "%s (medo de %s: %.0f): hora de comer", why, p.getScoreboardName(), st.fear(now)));
        // "I can smell the fear in you! Time to eat!" - encara e vai (How many days, 4:36)
        if (ready) tactics.standoff(p);
    }

    // ---------------------------------------------------------------- o susto da vez

    private Act pick(ServerPlayer p) {
        boolean enclosed = tactics.enclosure(p) > 12;
        LivingEntity meal = tactics.findMeal(p);
        boolean mealNear = meal != null && meal.distanceTo(p) < 20.0F;
        float[] w = new float[Act.values().length];
        w[Act.PEEK.ordinal()] = 1.0F;
        // lá em cima ele não chega no ouvido dela
        w[Act.BEHIND.ordinal()] = upHighTicks > 0 ? 0F : enclosed ? 0.2F : 1.0F;
        w[Act.WINDOW.ordinal()] = enclosed ? 2.0F : 0F;
        w[Act.NOISE.ordinal()] = 0.9F;
        w[Act.EAT.ordinal()] = mealNear ? 0.8F : 0F;
        float total = 0F;
        for (Act a : Act.values()) {
            if (w[a.ordinal()] <= 0F) continue;
            if (a == lastAct) w[a.ordinal()] *= 0.35F; // não repete o mesmo susto em seguida
            w[a.ordinal()] *= 0.25F + 1.5F * froggy.tacticChance(p, "susto_" + a.name().toLowerCase(java.util.Locale.ROOT));
            total += w[a.ordinal()];
        }
        float roll = froggy.getRandom().nextFloat() * total;
        for (Act a : Act.values()) {
            roll -= w[a.ordinal()];
            if (w[a.ordinal()] > 0F && roll <= 0F) return a;
        }
        return Act.PEEK;
    }

    /** Sumido entre um susto e outro: longe (18-28 blocos), onde ela não vê, parado olhando. */
    private void calm(ServerPlayer p, double dist, boolean seen) {
        // troca de esconderijo só se ela viu, ou se ficou perto/longe demais: antes ele trocava a cada
        // 6 s sempre "pra longe dela" a partir de onde estava e ia se afastando até perder ela
        boolean off = dist < 14.0D || dist > 34.0D;
        if (spot == null || seen && seenTicks == 1 || froggy.tickCount - spotAt > 120 && off) {
            spot = hideSpot(p, 18.0D, 28.0D);
            // longe demais e sem esconderijo bom: volta chegando pelo lado que ela não olha
            if (spot == null && dist > 34.0D) spot = tactics.blindSpot(p, 23.0D);
            spotAt = froggy.tickCount;
            if (spot != null) tactics.moveTo(spot, FroggyCombatGoal.huntSpeed() * (seen ? 0.9D : 0.6D));
        }
        if (spot == null || froggy.getNavigation().isDone()) {
            froggy.getNavigation().stop();
            froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
        }
    }

    /** Aparece de longe onde ela vai ver, fica parado encarando e some. */
    private boolean peek(ServerPlayer p, double dist) {
        switch (stage) {
            case 0 -> {
                if (spot == null) {
                    spot = peekSpot(p);
                    if (spot == null) return false;
                    tactics.moveTo(spot, FroggyCombatGoal.huntSpeed() * 0.8D);
                }
                if (froggy.tickCount % 20 == 0) tactics.moveTo(spot, FroggyCombatGoal.huntSpeed() * 0.8D);
                if (froggy.position().distanceTo(spot) < 1.6D || seenTicks > 5) next();
                return froggy.tickCount - stageAt < 300;
            }
            case 1 -> {
                froggy.getNavigation().stop();
                froggy.faceTarget(p);
                froggy.getLookControl().setLookAt(p.getX(), p.getEyeY(), p.getZ(), 30.0F, 30.0F);
                boolean coming = dist < 9.0D && combat.approachSpeed(p) > 0.08D;
                if (seenTicks >= 20 || coming || froggy.tickCount - stageAt > 200) {
                    if (seenTicks >= 20 && froggy.getRandom().nextBoolean()) froggy.speak(VoiceSituation.FOUND, true);
                    flee(p);
                }
                return true;
            }
            default -> {
                return fleeing();
            }
        }
    }

    /** Calado pelas costas até uns 3 blocos, ri no ouvido; quando ela vira e vê, sai correndo. */
    private boolean behind(ServerPlayer p, double dist, boolean seen) {
        switch (stage) {
            case 0 -> {
                if (seen && dist < 14.0D) {
                    // pego no flagra: congela um instante (ela vê ele parado) e foge
                    stage = 1;
                    stageAt = froggy.tickCount - 70;
                    return true;
                }
                if (froggy.tickCount % 8 == 0) tactics.moveTo(tactics.blindSpot(p, 2.6D), FroggyCombatGoal.huntSpeed() * 0.45D);
                froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
                if (dist <= 3.6D) {
                    next();
                    froggy.getNavigation().stop();
                    if (froggy.getRandom().nextFloat() < 0.6F) {
                        froggy.speak(VoiceSituation.FOUND, true); // a risadinha no ouvido
                    } else {
                        froggy.playSound(ModSounds.AMBIENT.get(), 1.4F, 0.8F); // ribbit baixo, de raiva
                    }
                }
                return froggy.tickCount - stageAt < 400;
            }
            case 1 -> {
                froggy.getNavigation().stop();
                froggy.faceTarget(p);
                froggy.getLookControl().setLookAt(p.getX(), p.getEyeY(), p.getZ(), 30.0F, 30.0F);
                // deixa ela ver ele colado (8 ticks) e some; não virou em 4 s: some do mesmo jeito
                if (seenTicks >= 8 || froggy.tickCount - stageAt > 80) flee(p);
                return true;
            }
            default -> {
                return fleeing();
            }
        }
    }

    /** Ela trancada em casa: na janela, olhando pra dentro, e some quando ela vê. */
    private boolean window(ServerPlayer p) {
        switch (stage) {
            case 0 -> {
                if (spot == null) {
                    BlockPos w = tactics.findWindow(p);
                    if (w == null) return false;
                    spot = Vec3.atBottomCenterOf(w);
                }
                if (froggy.tickCount % 10 == 0) tactics.moveTo(spot, FroggyCombatGoal.huntSpeed() * 0.7D);
                if (froggy.position().distanceTo(spot) < 1.2D) {
                    next();
                    froggy.speak(VoiceSituation.FOUND, true);
                }
                return froggy.tickCount - stageAt < 300;
            }
            case 1 -> {
                froggy.getNavigation().stop();
                froggy.faceTarget(p);
                froggy.getLookControl().setLookAt(p.getX(), p.getEyeY(), p.getZ(), 30.0F, 30.0F);
                if (seenTicks >= 30 || froggy.tickCount - stageAt > 240) flee(p);
                return true;
            }
            default -> {
                return fleeing();
            }
        }
    }

    /** Barulho atrás dela sem aparecer: passos, ribbit e, no fim, a risadinha. */
    private boolean noise(ServerPlayer p, boolean seen) {
        if (stage < 2 && seenTicks >= 4) {
            flee(p);
            return true;
        }
        switch (stage) {
            case 0 -> {
                if (froggy.tickCount % 10 == 0) tactics.moveTo(tactics.blindSpot(p, 11.0D), FroggyCombatGoal.huntSpeed() * 0.5D);
                froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
                if (froggy.distanceTo(p) < 14.0F && froggy.tickCount - stageAt > 40 || froggy.tickCount - stageAt > 160) {
                    next();
                    nextNoise = froggy.tickCount;
                }
                return true;
            }
            case 1 -> {
                if (froggy.tickCount % 12 == 0) tactics.moveTo(tactics.blindSpot(p, 10.0D), FroggyCombatGoal.huntSpeed() * 0.4D);
                if (froggy.tickCount >= nextNoise) {
                    makeNoise(froggy, noises++);
                    nextNoise = froggy.tickCount + 35 + froggy.getRandom().nextInt(30);
                }
                if (noises >= 4) flee(p);
                return true;
            }
            default -> {
                return fleeing();
            }
        }
    }

    /** Um barulho sem aparecer: passos, ribbit, passos e, no 4º, a risadinha (também no susto de longe). */
    static void makeNoise(FroggydudeEntity froggy, int n) {
        Level level = froggy.level();
        if (n >= 3) {
            froggy.speak(VoiceSituation.FOUND, true);
            return;
        }
        if (n == 1) {
            froggy.playSound(ModSounds.AMBIENT.get(), 1.2F, 0.75F + froggy.getRandom().nextFloat() * 0.2F);
            return;
        }
        // passos: três, rápidos, no chão em que ele está
        BlockPos below = froggy.blockPosition().below();
        SoundType st = level.getBlockState(below).getSoundType(level, below, froggy);
        for (int i = 0; i < 3; i++) {
            level.playSound(null, froggy.getX(), froggy.getY(), froggy.getZ(), st.getStepSound(), SoundSource.HOSTILE,
                    st.getVolume() * 0.9F, st.getPitch() * (0.9F + 0.1F * i));
        }
    }

    /** Come um bicho perto dela (o comer cuida do resto; ela vê ele de boca suja). */
    private boolean eat(ServerPlayer p) {
        LivingEntity meal = tactics.findMeal(p);
        if (meal != null && meal.distanceTo(p) < 24.0F) {
            froggy.setSiegeMeal(meal);
            froggy.brainLog("come " + meal.getName().getString() + " onde " + p.getScoreboardName() + " vê");
        }
        return false;
    }

    // ---------------------------------------------------------------- sumir

    private void next() {
        stage++;
        stageAt = froggy.tickCount;
    }

    /** Sai correndo pra onde ela não vê. */
    private void flee(ServerPlayer p) {
        stage = 9;
        stageAt = froggy.tickCount;
        spot = hideSpot(p, 20.0D, 30.0D);
        if (spot == null) {
            Vec3 away = froggy.position().subtract(p.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : away.normalize();
            spot = froggy.position().add(away.scale(20.0D));
        }
        tactics.moveTo(spot, FroggyCombatGoal.huntSpeed() * 1.05D);
        judge.closeIn(120);
    }

    private boolean fleeing() {
        if (froggy.tickCount - stageAt > 30 && froggy.getNavigation().isDone()) return false;
        return froggy.tickCount - stageAt < 100;
    }

    /** Um lugar a min-max blocos dela, longe do jeito dela, que ela não vê (atrás de árvore, morro). */
    @Nullable
    private Vec3 hideSpot(ServerPlayer p, double min, double max) {
        Vec3 best = null;
        double bestScore = -1.0E9D;
        for (int i = 0; i < 14; i++) {
            Vec3 c = DefaultRandomPos.getPosAway(froggy, (int) max, 7, p.position());
            if (c == null) continue;
            double d = c.distanceTo(p.position());
            if (d < min - 4.0D || d > max + 6.0D) continue;
            double score = (visibleFrom(p, c) ? 0.0D : 12.0D) - Math.abs(d - (min + max) * 0.5D) * 0.3D
                    - c.distanceTo(froggy.position()) * 0.05D;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** Um lugar a 13-20 blocos dela, mais ou menos na frente (até 50 graus), de onde ela vê ele. */
    @Nullable
    private Vec3 peekSpot(ServerPlayer p) {
        Level level = froggy.level();
        Vec3 look = p.getViewVector(1.0F).multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : look.normalize();
        for (int i = 0; i < 20; i++) {
            double ang = Math.toRadians((froggy.getRandom().nextDouble() * 2.0D - 1.0D) * 50.0D);
            double r = 13.0D + froggy.getRandom().nextDouble() * 7.0D;
            double x = look.x * Math.cos(ang) - look.z * Math.sin(ang);
            double z = look.x * Math.sin(ang) + look.z * Math.cos(ang);
            BlockPos base = BlockPos.containing(p.getX() + x * r, p.getY(), p.getZ() + z * r);
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos feet = base.above(dy);
                if (!RealTactics.free(level, feet) || !RealTactics.free(level, feet.above()) || RealTactics.free(level, feet.below())) continue;
                Vec3 at = Vec3.atBottomCenterOf(feet);
                if (visibleFrom(p, at)) return at;
                break;
            }
        }
        return null;
    }

    /** Dos olhos dela até a cabeça dele parado em {@code at}: nada no caminho (folha conta). */
    private boolean visibleFrom(ServerPlayer p, Vec3 at) {
        Vec3 head = at.add(0, 1.6D, 0);
        return froggy.level().clip(new ClipContext(p.getEyePosition(), head, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    private static PreyStudy study(ServerLevel level, ServerPlayer p) {
        return FroggyWorldData.get(level).brain.memory(p.getUUID(), p.getScoreboardName()).study;
    }
}
