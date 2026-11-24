package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModSounds;
import com.froggydude.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * (v0.7.58) O jumpscare sem saída (item 5 da fila; LM 23/11, a fortaleza do Nether do "FroggyDude VS Grox MANHUNT",
 * 18:48-19:42, o Grox no beco: "dead end, I'm cooked!"). Quando o jogo tem CERTEZA de que o jogador está num beco
 * (o corredor de 1x2 de tijolo do Nether, o buraco de 1 bloco) e que nada que ele carrega quebra as paredes a tempo:
 * <ol>
 * <li>ele vem calado e para logo depois da curva, fora da vista: o jogador só pode olhar pra curva e esperar;</li>
 * <li>quando o jogador fica um tempo olhando pra ela (ou chega perto dela, ou o tempo acaba), ele sai da curva numa
 * velocidade absurda, gritando, até a cara do jogador;</li>
 * <li>meio segundo de cara colada e corte seco: tela preta, silêncio e a tela de morte só com "Você morreu!" e os
 * botões, sem o vermelho (cliente: NoEscapeClient).</li>
 * </ol>
 * "Certeza" (o LM pediu pente fino, pra não dar bug): ver {@link #analyze}. Na dúvida, não acontece.
 */
final class NoEscape {

    /** Até onde olhar em volta do jogador (blocos, em cada eixo). */
    private static final int RADIUS = 28;
    /** Teto de células na busca (custo: só roda a cada meio segundo, com ele caçando perto). */
    private static final int MAX_NODES = 5000;
    /** Um bloco da parede que sai em até isso (ticks) com a melhor coisa que ele tem = dá pra cavar e fugir. */
    static final int DIG_TICKS = 20;
    /** O pedaço do jogador (onde ele chega antes do FroggyDude) maior que isso = tem saída. */
    private static final int OPEN_SIZE = 48;
    private static final int OPEN_DEPTH = 12;
    /** Corredor de 1 (ou 2) de largura: a 5 passos dele cabem no máximo umas 30 células; mais que isso é aberto. */
    private static final int NEAR_MAX = 30;
    /** O FroggyDude é mais rápido: só conta como "chega antes" com folga. */
    private static final double FROGGY_EDGE = 1.5D;
    /** Quantas análises seguidas (de 10 em 10 ticks) dizendo "sem saída" antes de começar. */
    private static final int SURE_CHECKS = 3;
    /** Depois de um, quanto tempo até poder de novo (ticks): 10 min. */
    private static final int COOLDOWN = 12000;
    /**
     * Olhando pra curva por tanto tempo (sorteado entre os dois), ele sai. O vídeo do LM (TikTok do Grox no beco,
     * 23/11): uns 5 s do corredor vazio na tela antes de ele aparecer.
     */
    private static final int STARE_MIN = 80;
    private static final int STARE_MAX = 120;
    /** Esperando atrás da curva no máximo isso, mesmo que o jogador não olhe. */
    private static final int WAIT_MAX = 260;
    /** Indo pra trás da curva no máximo isso. */
    private static final int APPROACH_MAX = 300;
    /** A corrida da curva até a cara: blocos por tick (no vídeo: ~10 blocos em ~0,4 s). */
    private static final double DASH_SPEED = 1.15D;
    /**
     * A cara colada antes do corte (ticks). No vídeo são 1-2 quadros, mas o cliente desliza a posição do bicho em 3 ticks:
     * com 2 o corte vinha com ele ainda a 2 blocos. Com 5, a cara enche a tela uns 2 ticks e aí o preto.
     */
    static final int FACE_TICKS = 5;

    private enum Stage { NONE, APPROACH, WAIT, DASH, FACE }

    /** O que a análise achou (também é o que o /froggydude semsaida mostra). */
    record Result(boolean trapped, String why, List<BlockPos> path, int region, int digTicks, @Nullable BlockPos digAt) {
        static Result no(String why) {
            return new Result(false, why, List.of(), 0, -1, null);
        }
    }

    private final FroggydudeEntity froggy;
    private Stage stage = Stage.NONE;
    private int stageAt;
    @Nullable
    private UUID who;
    private int sure;
    private int readyAt;
    private boolean forced;
    /** Do jogador até o FroggyDude, célula por célula (o pé). */
    private List<BlockPos> path = List.of();
    /** Onde ele espera (fora da vista) e o índice disso no caminho. */
    private int hideIdx;
    /** O último ponto da curva que o jogador enxerga (pra onde ele fica olhando). */
    @Nullable
    private Vec3 corner;
    private int stare;
    private int stareNeed;
    private double dashPos;

    NoEscape(FroggydudeEntity froggy) {
        this.froggy = froggy;
    }

    boolean active() {
        return stage != Stage.NONE;
    }

    /** Começou, ou está confirmando que é beco (aí o jumpscare raro não começa por cima). */
    boolean pending() {
        return stage != Stage.NONE || sure > 0;
    }

    /** Pode acontecer agora (fora do intervalo entre um e outro)? */
    boolean ready() {
        return stage == Stage.NONE && froggy.tickCount >= readyAt;
    }

    /** Pelo comando: a próxima vez que estiver sem saída, sem esperar o intervalo. */
    void force() {
        forced = true;
        readyAt = 0;
    }

    // ------------------------------------------------------------------ o susto

    /** Devolve true se cuidou do tick. */
    boolean tick(LivingEntity target) {
        if (!(froggy.level() instanceof ServerLevel level)) return false;
        if (stage == Stage.NONE) return maybeStart(level, target);
        if (!(target instanceof ServerPlayer p) || !p.getUUID().equals(who) || !p.isAlive() || p.isCreative()
                || p.isSpectator() || p.level() != froggy.level()) {
            stop("o jogador sumiu");
            return false;
        }
        // achou um jeito (cavou, pérola, água...): aborta e segue a caçada normal
        if ((stage == Stage.APPROACH || stage == Stage.WAIT) && froggy.tickCount % 20 == 0) {
            Result r = analyze(level, p, froggy);
            if (!r.trapped()) {
                stop("saiu do beco: " + r.why());
                return false;
            }
            if (stage == Stage.APPROACH) {
                // ela pode ter se mexido no beco: caminho e esconderijo de novo (parado esperando, fica onde está)
                path = r.path();
                pickHide(level, p);
                if (hideIdx <= 0) {
                    beginDash(p);
                    return true;
                }
            }
        }
        if (froggy.getLastHurtByMob() == p && froggy.tickCount - froggy.getLastHurtByMobTimestamp() < 3
                && stage != Stage.FACE) {
            stop("apanhou");
            return false;
        }
        int t = froggy.tickCount - stageAt;
        switch (stage) {
            case APPROACH -> {
                froggy.mute(10);
                froggy.setSilent(true); // nem os passos
                BlockPos spot = path.get(hideIdx);
                Vec3 at = Vec3.atBottomCenterOf(spot);
                if (froggy.position().distanceToSqr(at) < 0.8D) {
                    froggy.getNavigation().stop();
                    // parado não faz passo: já tira o "silencioso" (o grito preso nele não toca enquanto o cliente
                    // ainda acha que ele está silencioso)
                    froggy.setSilent(false);
                    next(Stage.WAIT);
                    stare = 0;
                    stareNeed = STARE_MIN + froggy.getRandom().nextInt(STARE_MAX - STARE_MIN + 1);
                    froggy.brainLog("sem saída: atrás da curva, esperando " + p.getScoreboardName() + " olhar");
                } else if (t > APPROACH_MAX) {
                    froggy.brainLog("sem saída: não chegou atrás da curva, vai assim mesmo");
                    beginDash(p);
                } else if (froggy.tickCount % 5 == 0 || froggy.getNavigation().isDone()) {
                    froggy.getNavigation().moveTo(at.x, at.y, at.z, FroggyCombatGoal.huntSpeed() * 0.9D);
                }
            }
            case WAIT -> {
                // parado, calado (nem passo), olhando pra onde ela está
                froggy.mute(10);
                froggy.getNavigation().stop();
                froggy.setDeltaMovement(0, froggy.getDeltaMovement().y, 0);
                froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
                if (corner != null && looking(p, corner)) stare++;
                boolean close = corner != null && p.position().distanceTo(corner) < 1.6D;
                if (stare >= stareNeed || close || t >= WAIT_MAX) {
                    froggy.brainLog(String.format(java.util.Locale.ROOT, "sem saída: sai da curva (olhou %d ticks%s)", stare,
                            close ? ", chegou na curva" : t >= WAIT_MAX ? ", cansou de esperar" : ""));
                    beginDash(p);
                }
            }
            case DASH -> {
                // da curva até a cara dela, pelo caminho, rápido demais pra reagir
                dashPos -= DASH_SPEED;
                Vec3 face = faceSpot(p);
                Vec3 at = dashPos <= 1.0D ? face : along(dashPos);
                if (dashPos <= 1.0D) dashPos = 0D;
                Vec3 dir = p.position().subtract(at).multiply(1, 0, 1);
                float yaw = dir.lengthSqr() < 1.0E-4D ? froggy.getYRot()
                        : (float) (Math.atan2(dir.z, dir.x) * (180D / Math.PI)) - 90.0F;
                froggy.moveTo(at.x, at.y, at.z, yaw, 0.0F);
                froggy.setYHeadRot(yaw);
                froggy.setYBodyRot(yaw);
                froggy.setDeltaMovement(Vec3.ZERO);
                if (dashPos <= 0D) {
                    froggy.setFroggyState(FroggyState.TAUNT);
                    froggy.openMouth(FACE_TICKS + 20);
                    ModNetwork.sendShake(p, 2.6F, 6);
                    next(Stage.FACE);
                }
            }
            case FACE -> {
                Vec3 face = faceSpot(p);
                froggy.moveTo(face.x, face.y, face.z, froggy.getYRot(), 0.0F);
                froggy.setDeltaMovement(Vec3.ZERO);
                froggy.faceTarget(p);
                if (t >= FACE_TICKS) kill(level, p);
            }
            default -> {
            }
        }
        return true;
    }

    private boolean maybeStart(ServerLevel level, LivingEntity target) {
        if (!(target instanceof ServerPlayer p) || p.isCreative() || p.isSpectator() || !p.onGround() || p.isInWater()
                || p.isPassenger() || p.isSleeping()) {
            sure = 0;
            return false;
        }
        if (froggy.tickCount < readyAt || froggy.tickCount % 10 != 0) return false;
        if (froggy.getFroggyState() != FroggyState.CHASE || froggy.isFrenzyActive() || froggy.isHolding()
                || froggy.isMocking(p) || froggy.inRage2() || froggy.distanceTo(p) > 40.0F) {
            debug("não olha: estado=" + froggy.getFroggyState() + " dist=" + (int) froggy.distanceTo(p));
            sure = 0;
            return false;
        }
        Result r = analyze(level, p, froggy);
        debug("análise: " + (r.trapped() ? "SEM SAÍDA" : r.why()) + " caminho=" + r.path().size() + " seguidas=" + sure);
        if (!r.trapped()) {
            if (sure > 0 || forced) froggy.brainLog("sem saída? não: " + r.why());
            sure = 0;
            return false;
        }
        if (++sure < SURE_CHECKS) return false;
        sure = 0;
        forced = false;
        who = p.getUUID();
        path = r.path();
        pickHide(level, p);
        froggy.stopSpeaking();
        froggy.brainLog(String.format(java.util.Locale.ROOT, "sem saída: %s no beco (%d células, parede sai em %s); caminho %d, espera no %d",
                p.getScoreboardName(), r.region(), r.digTicks() < 0 ? "nunca" : r.digTicks() + " ticks", path.size(),
                hideIdx));
        if (hideIdx <= 0) {
            beginDash(p);
        } else {
            next(Stage.APPROACH);
        }
        return true;
    }

    /**
     * Onde esperar: andando do jogador pro FroggyDude pelo caminho, o primeiro ponto (a 3+ passos) que o jogador NÃO vê,
     * nem a cabeça nem o corpo dele lá. O ponto anterior (o último que ele vê) é a curva. Corredor reto, tudo à vista: ele
     * não espera, vem direto.
     */
    private void pickHide(ServerLevel level, ServerPlayer p) {
        hideIdx = 0;
        corner = null;
        Vec3 eye = p.getEyePosition();
        for (int i = 3; i < path.size(); i++) {
            Vec3 feet = Vec3.atBottomCenterOf(path.get(i));
            if (!sees(level, eye, feet.add(0, 1.6D, 0), p) && !sees(level, eye, feet.add(0, 0.6D, 0), p)) {
                hideIdx = i;
                corner = Vec3.atBottomCenterOf(path.get(i - 1)).add(0, 1.0D, 0);
                return;
            }
        }
    }

    private static boolean sees(Level level, Vec3 from, Vec3 to, ServerPlayer p) {
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 0.04D;
    }

    /** Olhando pra curva (até ~25 graus do centro da tela). */
    private static boolean looking(ServerPlayer p, Vec3 at) {
        Vec3 to = at.subtract(p.getEyePosition());
        if (to.lengthSqr() < 1.0E-4D) return true;
        return p.getViewVector(1.0F).dot(to.normalize()) > 0.9D;
    }

    private void beginDash(ServerPlayer p) {
        froggy.getNavigation().stop();
        froggy.setSilent(false);
        // a distância que falta, medida pelo caminho a partir de onde ele está
        int i = nearestIdx();
        dashPos = Math.max(1.0D, i);
        froggy.setFroggyState(FroggyState.CHASE);
        // tocados no lugar (não presos nele): o "silencioso" da chegada ainda pode estar valendo no cliente
        froggy.stopSpeaking();
        froggy.level().playSound(null, froggy.getX(), froggy.getY() + 1.0D, froggy.getZ(), ModSounds.SCREAM.get(),
                SoundSource.HOSTILE, 8.0F, 1.0F);
        froggy.level().playSound(null, froggy.getX(), froggy.getY(), froggy.getZ(), ModSounds.LEAP.get(),
                SoundSource.HOSTILE, 3.0F, 0.8F);
        froggy.mute(60);
        next(Stage.DASH);
    }

    private int nearestIdx() {
        int best = path.size() - 1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            double d = Vec3.atBottomCenterOf(path.get(i)).distanceToSqr(froggy.position());
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** O ponto no caminho a {@code s} passos do jogador (interpolado). */
    private Vec3 along(double s) {
        int i = Math.min((int) Math.floor(s), path.size() - 1);
        int j = Math.min(i + 1, path.size() - 1);
        double f = s - Math.floor(s);
        return Vec3.atBottomCenterOf(path.get(i)).lerp(Vec3.atBottomCenterOf(path.get(j)), f);
    }

    /** Colado na cara: 0,75 bloco na frente dos olhos dela, no chão dela. */
    private Vec3 faceSpot(ServerPlayer p) {
        Vec3 look = p.getViewVector(1.0F).multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4D && path.size() > 1) look = Vec3.atBottomCenterOf(path.get(1)).subtract(p.position());
        look = look.multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 at = p.position().add(look.scale(0.75D));
        BlockPos feet = BlockPos.containing(at);
        if (!RealTactics.free(froggy.level(), feet) || !RealTactics.free(froggy.level(), feet.above())) {
            // parede na frente: o mais perto possível pelo caminho
            at = path.size() > 1 ? p.position().lerp(Vec3.atBottomCenterOf(path.get(1)), 0.7D) : p.position();
        }
        return new Vec3(at.x, p.getY(), at.z);
    }

    /** O corte: tela preta e silêncio no cliente dela (NoEscapeClient) e a morte. */
    private void kill(ServerLevel level, ServerPlayer p) {
        froggy.brainLog("sem saída: corte seco, " + p.getScoreboardName() + " morreu");
        ModNetwork.sendNoEscapeCut(p);
        p.hurt(FroggyFinisherGoal.devoured(level, froggy), Float.MAX_VALUE);
        froggy.stopSpeaking();
        readyAt = froggy.tickCount + COOLDOWN;
        stage = Stage.NONE;
        who = null;
        froggy.setFroggyState(FroggyState.CHASE);
    }

    void stop(String why) {
        if (stage != Stage.NONE) {
            froggy.brainLog("sem saída cancelado: " + why);
            froggy.setSilent(false);
            readyAt = froggy.tickCount + 600; // tenta de novo daqui a 30 s, se ainda estiver preso
        }
        stage = Stage.NONE;
        who = null;
        sure = 0;
    }

    private static void debug(String msg) {
        if (Boolean.getBoolean("froggydude.debug")) System.out.println("[FROGGYDEBUG] semsaida " + msg);
    }

    private void next(Stage s) {
        stage = s;
        stageAt = froggy.tickCount;
    }

    // ------------------------------------------------------------------ a análise

    /**
     * Sem saída de verdade? Tudo tem que bater (na dúvida, não):
     * <ol>
     * <li>os dois no chão, e o FroggyDude chega nele andando (a busca de caminho dele, de bloco em bloco, sobe 1, desce
     * até 3); sem caminho não é beco, é ele preso;</li>
     * <li>o pedaço onde o JOGADOR chega antes dele (com folga: ele é mais rápido) é pequeno e fechado: até
     * {@value #OPEN_SIZE} células e {@value #OPEN_DEPTH} passos; um desvio no corredor antes de ele chegar já é saída;
     * portal nesse pedaço ou água do lado = saída;</li>
     * <li>nenhum bloco em volta desse pedaço (paredes, teto, chão) sai em até {@value #DIG_TICKS} ticks com a melhor
     * ferramenta que ele tem no inventário (eficiência, pressa e cansaço contam); bloco inquebrável não sai nunca;</li>
     * <li>sem pérola do End nem fruta do coro.</li>
     * </ol>
     */
    static Result analyze(ServerLevel level, ServerPlayer p, FroggydudeEntity f) {
        if (!p.onGround()) return Result.no("jogador no ar");
        if (p.isInWater() || p.isInLava()) return Result.no("jogador na água/lava");
        for (ItemStack s : allItems(p)) {
            if (s.is(Items.ENDER_PEARL)) return Result.no("tem pérola do End");
            if (s.is(Items.CHORUS_FRUIT)) return Result.no("tem fruta do coro");
        }
        BlockPos pc = standing(level, p.blockPosition());
        BlockPos fc = standing(level, f.blockPosition());
        if (pc == null) return Result.no("jogador sem chão firme");
        if (fc == null) return Result.no("FroggyDude no ar");
        // 0. filtro barato (roda a cada meio segundo no celular): em volta dele, a 5 passos, cabe muita coisa = aberto
        int near = around(level, pc, 5, NEAR_MAX);
        if (near > NEAR_MAX) return Result.no("lugar aberto (" + near + "+ células a 5 passos)");
        // 1. a distância a pé do FroggyDude até cada célula
        Map<Long, Integer> df = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        df.put(fc.asLong(), 0);
        q.add(fc);
        while (!q.isEmpty() && df.size() < MAX_NODES) {
            BlockPos c = q.poll();
            int d = df.get(c.asLong());
            for (BlockPos n : moves(level, c)) {
                if (Math.abs(n.getX() - pc.getX()) > RADIUS || Math.abs(n.getZ() - pc.getZ()) > RADIUS
                        || Math.abs(n.getY() - pc.getY()) > RADIUS) {
                    continue;
                }
                if (df.putIfAbsent(n.asLong(), d + 1) == null) {
                    parent.put(n.asLong(), c.asLong());
                    q.add(n);
                }
            }
        }
        Integer dPlayer = df.get(pc.asLong());
        if (dPlayer == null) return Result.no("ele não chega andando até o jogador");
        // o caminho jogador -> FroggyDude
        List<BlockPos> path = new ArrayList<>();
        for (long k = pc.asLong(); ; ) {
            path.add(BlockPos.of(k));
            Long up = parent.get(k);
            if (up == null) break;
            k = up;
        }
        // 2. o pedaço do jogador: onde ele chega antes (com folga)
        Map<Long, Integer> dp = new HashMap<>();
        dp.put(pc.asLong(), 0);
        q.clear();
        q.add(pc);
        Set<Long> region = new HashSet<>();
        region.add(pc.asLong());
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            int d = dp.get(c.asLong());
            if (d >= OPEN_DEPTH) return Result.no("corredor longo (" + d + "+ passos sem ele chegar antes)");
            if (region.size() > OPEN_SIZE) return Result.no("lugar aberto (" + region.size() + "+ células)");
            for (BlockPos n : moves(level, c)) {
                if (dp.containsKey(n.asLong())) continue;
                Integer fd = df.get(n.asLong());
                int nd = d + 1;
                if (fd != null && nd * FROGGY_EDGE + 2 > fd) continue; // ele chega lá antes: não é saída
                if (fd == null && (Math.abs(n.getX() - pc.getX()) >= RADIUS || Math.abs(n.getZ() - pc.getZ()) >= RADIUS)) {
                    return Result.no("saída longe dele");
                }
                dp.put(n.asLong(), nd);
                region.add(n.asLong());
                q.add(n);
            }
        }
        // portal, água
        for (long k : region) {
            BlockPos c = BlockPos.of(k);
            for (BlockPos x : new BlockPos[]{c, c.above()}) {
                BlockState s = level.getBlockState(x);
                if (s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_GATEWAY)) {
                    return Result.no("portal no beco");
                }
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    if (level.getFluidState(x.relative(dir)).is(net.minecraft.tags.FluidTags.WATER)) {
                        return Result.no("água do lado");
                    }
                }
            }
        }
        // 3. as paredes: a mais fácil de quebrar
        int best = Integer.MAX_VALUE;
        BlockPos bestAt = null;
        Set<Long> walls = new HashSet<>();
        for (long k : region) {
            BlockPos c = BlockPos.of(k);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                walls.add(c.relative(dir).asLong());
                walls.add(c.above().relative(dir).asLong());
            }
            walls.add(c.above(2).asLong());
            walls.add(c.below().asLong());
        }
        for (long k : walls) {
            BlockPos w = BlockPos.of(k);
            if (RealTactics.free(level, w)) continue;
            int ticks = breakTicks(level, p, w);
            if (ticks < best) {
                best = ticks;
                bestAt = w;
            }
        }
        if (best <= DIG_TICKS) {
            BlockState s = level.getBlockState(bestAt);
            return new Result(false, "dá pra cavar: " + s.getBlock().getName().getString() + " em " + best + " ticks", path,
                    region.size(), best, bestAt);
        }
        return new Result(true, "sem saída", path, region.size(), best == Integer.MAX_VALUE ? -1 : best, bestAt);
    }

    /** Quantas células dá pra pisar a até {@code depth} passos de {@code start} (para em {@code cap}+1). */
    private static int around(Level level, BlockPos start, int depth, int cap) {
        Map<Long, Integer> seen = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.put(start.asLong(), 0);
        q.add(start);
        while (!q.isEmpty() && seen.size() <= cap) {
            BlockPos c = q.poll();
            int d = seen.get(c.asLong());
            if (d >= depth) continue;
            for (BlockPos n : moves(level, c)) {
                if (seen.putIfAbsent(n.asLong(), d + 1) == null) q.add(n);
            }
        }
        return seen.size();
    }

    /** Onde o pé fica em pé a partir de {@code pos} (o próprio, ou até 2 abaixo se estiver no ar/meio bloco). */
    @Nullable
    private static BlockPos standing(Level level, BlockPos pos) {
        for (int i = 0; i < 3; i++) {
            BlockPos c = pos.below(i);
            if (walkable(level, c)) return c;
        }
        return null;
    }

    /** Cabe em pé (2 de altura livres, sem líquido) e tem chão. */
    private static boolean walkable(Level level, BlockPos c) {
        return RealTactics.free(level, c) && RealTactics.free(level, c.above()) && !RealTactics.free(level, c.below())
                && !level.getFluidState(c.below()).is(net.minecraft.tags.FluidTags.LAVA);
    }

    /** Pra onde dá pra ir a pé de {@code c}: do lado, subindo 1 (com espaço pra pular) ou descendo até 3. */
    private static List<BlockPos> moves(Level level, BlockPos c) {
        List<BlockPos> out = new ArrayList<>(4);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = c.relative(dir);
            if (walkable(level, n)) {
                out.add(n);
            } else if (RealTactics.free(level, n) && RealTactics.free(level, n.above())) {
                for (int k = 1; k <= 3; k++) {
                    BlockPos d = n.below(k);
                    if (!RealTactics.free(level, d)) break;
                    if (walkable(level, d)) {
                        out.add(d);
                        break;
                    }
                }
            } else if (walkable(level, n.above()) && RealTactics.free(level, c.above(2))) {
                out.add(n.above());
            }
        }
        return out;
    }

    private static List<ItemStack> allItems(ServerPlayer p) {
        List<ItemStack> all = new ArrayList<>(p.getInventory().items);
        all.addAll(p.getInventory().offhand);
        return all;
    }

    /** Ticks pra quebrar {@code pos} com a melhor coisa do inventário (ou a mão). Inquebrável = MAX_VALUE. */
    static int breakTicks(Level level, ServerPlayer p, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0F) return Integer.MAX_VALUE;
        if (hardness == 0F) return 0;
        float best = 0F;
        List<ItemStack> all = allItems(p);
        all.add(ItemStack.EMPTY);
        for (ItemStack s : all) {
            float speed = s.isEmpty() ? 1.0F : s.getDestroySpeed(state);
            if (speed > 1.0F) {
                int eff = s.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY);
                if (eff > 0) speed += eff * eff + 1;
            }
            MobEffectInstance haste = p.getEffect(MobEffects.DIG_SPEED);
            if (haste != null) speed *= 1.0F + (haste.getAmplifier() + 1) * 0.2F;
            MobEffectInstance fatigue = p.getEffect(MobEffects.DIG_SLOWDOWN);
            if (fatigue != null) {
                speed *= switch (fatigue.getAmplifier()) {
                    case 0 -> 0.3F;
                    case 1 -> 0.09F;
                    case 2 -> 0.0027F;
                    default -> 8.1E-4F;
                };
            }
            boolean correct = !state.requiresCorrectToolForDrops() || !s.isEmpty() && s.isCorrectToolForDrops(state);
            float progress = speed / hardness / (correct ? 30.0F : 100.0F);
            best = Math.max(best, progress);
        }
        return best <= 0F ? Integer.MAX_VALUE : (int) Math.ceil(1.0F / best);
    }
}
