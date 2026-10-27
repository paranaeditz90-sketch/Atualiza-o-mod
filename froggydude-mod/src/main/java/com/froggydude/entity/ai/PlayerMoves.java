package com.froggydude.entity.ai;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;

/**
 * O jeito de jogador (a "IA melhorada" do LM, PLANO.md seção 10):
 * - sem caminho até a vítima por um vão (buraco, lava, água funda), põe bloco como
 *   gente: ponte agachado (olha pro lugar, põe o bloco e anda pra cima dele). Torre
 *   ele não faz mais (v0.7.15, LM: "ele demora subir uma simples parede"): parede ele
 *   sobe de quatro na hora (FroggydudeEntity#wantsToClimb);
 * - a "dancinha": agacha e levanta rápido (o shift spam), pulando e socando o ar;
 * - zombar e ir embora: matou o mesmo jogador 3 vezes num dia, ele chega perto,
 *   ri, dança na frente dele, diz "Não mexe comigo" e vai embora (um dia de trégua).
 *
 * É ajudante da FroggyCombatGoal (quem chama é o tick dela).
 */
public class PlayerMoves {

    private enum Build { NONE, BRIDGE }

    /** Um pack: depois disso ele "acabou os blocos" e volta pros outros jeitos. */
    private static final int MAX_BLOCKS = 64;
    private static final BlockState BLOCK = Blocks.COBBLESTONE.defaultBlockState();
    /** Ponte: olha pro lugar uns ticks antes de pôr (como gente mirando o bloco). */
    private static final int AIM_TICKS = 3;
    private static final double SNEAK_SPEED = 0.55D;
    private static final int DANCE_TICKS = 50;
    private static final int MOCK_DANCE_TICKS = 70;

    private final FroggydudeEntity froggy;

    private Build build = Build.NONE;
    private int buildTicks;
    private int placed;
    private int stall;
    @Nullable
    private BlockPos bridgeCell;
    private int aim;

    private int danceTicks;
    private int danceCooldown = 200;
    private int danceStyle;

    private int mockPhase;
    private int mockTicks;

    public PlayerMoves(FroggydudeEntity froggy) {
        this.froggy = froggy;
    }

    public void tickCooldowns() {
        if (danceCooldown > 0) danceCooldown--;
    }

    /** Largou tudo (a goal parou ou trocou de alvo). */
    public void reset() {
        build = Build.NONE;
        danceTicks = 0;
        mockPhase = 0;
        bridgeCell = null;
        froggy.setCrouchingPose(false);
    }

    // ------------------------------------------------------------ pôr bloco

    public boolean building() {
        return build != Build.NONE;
    }

    /**
     * Preso sem caminho até a vítima: dá pra chegar pondo bloco? Ponte, quando tem um
     * vão entre os dois.
     */
    public boolean startBuilding(LivingEntity target) {
        Level level = froggy.level();
        if (!FroggyConfig.ai().build) return false;
        if (build != Build.NONE || !froggy.onGround() || froggy.isInWater() || froggy.isInLava()) return false;
        if (!ForgeEventFactory.getMobGriefingEvent(level, froggy)) return false;
        double dy = target.getY() - froggy.getY();
        if (Math.abs(dy) < 2.5D && gapAhead(target, 4)) {
            build = Build.BRIDGE;
            begin("ponte na direção da vítima");
            return true;
        }
        return false;
    }

    private void begin(String what) {
        buildTicks = 0;
        placed = 0;
        stall = 0;
        aim = 0;
        bridgeCell = null;
        froggy.getNavigation().stop();
        hold();
        froggy.setFroggyState(FroggyState.BUILD);
        froggy.brainLog("sem caminho: põe bloco como jogador - " + what);
    }

    private void finishBuilding(String why) {
        froggy.brainLog("parou de pôr bloco (" + placed + "): " + why);
        build = Build.NONE;
        bridgeCell = null;
        froggy.setCrouchingPose(false);
        froggy.setFroggyState(FroggyState.CHASE);
    }

    /** Um tick construindo. Devolve false quando acabou (o combate volta a mandar). */
    public boolean tickBuild(LivingEntity target) {
        buildTicks++;
        froggy.getNavigation().stop();
        if (froggy.isInLava() || placed >= MAX_BLOCKS || buildTicks > 900) {
            finishBuilding(placed >= MAX_BLOCKS ? "acabaram os blocos" : "demorou demais");
            return false;
        }
        tickBridge(target);
        return build != Build.NONE;
    }

    private void tickBridge(LivingEntity target) {
        Level level = froggy.level();
        if (!froggy.onGround() && !froggy.isInWater()) {
            if (++stall > 30) finishBuilding("caiu");
            return;
        }
        if (bridgeCell == null) {
            // de tempos em tempos: já dá pra chegar andando?
            if (placed > 0 && reachable(target)) {
                finishBuilding("já dá pra chegar andando");
                return;
            }
            bridgeCell = nextBridgeCell(target);
            aim = 0;
            if (bridgeCell == null) {
                finishBuilding(placed > 0 ? "atravessou" : "sem vão na frente");
                return;
            }
        }
        BlockPos cell = bridgeCell;
        BlockPos under = cell.below();
        boolean edge = !solid(level, under);
        // agachado na beirada, olhando pro lugar do bloco (como gente fazendo ponte)
        froggy.setCrouchingPose(edge || placed > 0);
        froggy.faceDirection(cell.getX() + 0.5D - froggy.getX(), cell.getZ() + 0.5D - froggy.getZ());
        froggy.getLookControl().setLookAt(under.getX() + 0.5D, under.getY() + 1.0D, under.getZ() + 0.5D, 30.0F, 90.0F);
        if (canPlace(level, under)) {
            // parado na beirada mirando (sem o embalo do galope: antes ele caía na lava)
            hold();
            if (++aim >= AIM_TICKS) place(level, under);
            return;
        }
        if (!solid(level, under)) {
            // não dá pra pôr (tem gente/bicho em cima): espera um pouco
            if (++stall > 40) finishBuilding("lugar ocupado");
            return;
        }
        // anda pra cima do bloco, devagar (agachado não cai da beirada)
        double dx = cell.getX() + 0.5D - froggy.getX(), dz = cell.getZ() + 0.5D - froggy.getZ();
        if (dx * dx + dz * dz < 0.3D * 0.3D) {
            bridgeCell = null;
            stall = 0;
            return;
        }
        froggy.getMoveControl().setWantedPosition(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D,
                placed > 0 ? SNEAK_SPEED : 0.6D);
        if (++stall > 60) finishBuilding("não andou");
    }

    private boolean reachable(LivingEntity target) {
        Path p = froggy.getNavigation().createPath(target, 0);
        return p != null && p.canReach();
    }

    /** Tem vão (ar, água, lava) no caminho reto até a vítima, nos próximos blocos? */
    private boolean gapAhead(LivingEntity target, int cells) {
        Level level = froggy.level();
        BlockPos at = froggy.blockPosition();
        for (int i = 0; i < cells; i++) {
            BlockPos next = step(at, target);
            if (next == null) return false;
            if (!solid(level, next.below())) return true;
            at = next;
        }
        return false;
    }

    /** Um bloco na direção da vítima (o eixo mais longo primeiro), se der pra passar. */
    @Nullable
    private BlockPos step(BlockPos from, LivingEntity target) {
        Level level = froggy.level();
        double dx = target.getX() - (from.getX() + 0.5D), dz = target.getZ() - (from.getZ() + 0.5D);
        if (dx * dx + dz * dz < 1.5D * 1.5D) return null;
        Direction main = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        Direction side = Math.abs(dx) > Math.abs(dz) ? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
                : (dx > 0 ? Direction.EAST : Direction.WEST);
        for (Direction d : new Direction[]{main, side}) {
            BlockPos cell = from.relative(d);
            if (free(level, cell) && free(level, cell.above())) return cell;
        }
        return null;
    }

    /** O próximo bloco do caminho reto até a vítima (com ou sem chão: sem chão, põe bloco). */
    @Nullable
    private BlockPos nextBridgeCell(LivingEntity target) {
        return step(froggy.blockPosition(), target);
    }

    /** Para onde está: sem embalo e sem o andar que ficou pendente no controle de movimento. */
    private void hold() {
        Vec3 v = froggy.getDeltaMovement();
        froggy.setDeltaMovement(0.0D, v.y, 0.0D);
        froggy.getMoveControl().setWantedPosition(froggy.getX(), froggy.getY(), froggy.getZ(), 0.0D);
    }

    private void place(Level level, BlockPos pos) {
        level.setBlock(pos, BLOCK, 3);
        SoundType st = BLOCK.getSoundType();
        level.playSound(null, pos, st.getPlaceSound(), SoundSource.HOSTILE, (st.getVolume() + 1.0F) / 2.0F, st.getPitch() * 0.8F);
        level.gameEvent(froggy, GameEvent.BLOCK_PLACE, pos);
        froggy.swing(InteractionHand.MAIN_HAND);
        placed++;
        aim = 0;
        stall = 0;
    }

    private static boolean free(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean solid(Level level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean canPlace(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        return (st.isAir() || st.canBeReplaced()) && level.isUnobstructed(BLOCK, pos, CollisionContext.empty());
    }

    // ------------------------------------------------------------ dancinha

    public boolean dancing() {
        return danceTicks > 0;
    }

    /** "Brincando" com a vítima de longe (o modo só olhando): às vezes dança. */
    public boolean maybeDance(LivingEntity target, double dist) {
        if (danceCooldown > 0 || danceTicks > 0 || dist < 5.0D || dist > 14.0D) return false;
        if (!froggy.onGround() || !froggy.hasLineOfSight(target)) return false;
        if (froggy.getRandom().nextInt(12) != 0) return false;
        startDance(DANCE_TICKS);
        froggy.brainLog("brincando: a dancinha (agacha, levanta, pula, soca o ar)");
        return true;
    }

    public void forceDance() {
        startDance(DANCE_TICKS);
    }

    private void startDance(int ticks) {
        danceTicks = ticks;
        danceStyle = froggy.getRandom().nextInt(3);
        froggy.getNavigation().stop();
        froggy.setFroggyState(FroggyState.TAUNT);
    }

    public void tickDance(LivingEntity target) {
        int t = danceTicks--;
        froggy.getNavigation().stop();
        froggy.faceTarget(target);
        froggy.getLookControl().setLookAt(target, 60.0F, 60.0F);
        // shift spam: agacha e levanta a cada 2 ticks
        froggy.setCrouchingPose((t / 2) % 2 == 0);
        // estilo 0: pulando; 1: socando o ar; 2: os dois
        if (danceStyle != 1 && froggy.onGround() && t % 14 == 3) {
            froggy.setDeltaMovement(froggy.getDeltaMovement().x, 0.42D, froggy.getDeltaMovement().z);
            froggy.hasImpulse = true;
        }
        if (danceStyle != 0 && t % 5 == 0) froggy.swing(InteractionHand.MAIN_HAND);
        if (danceTicks <= 0) {
            froggy.setCrouchingPose(false);
            danceCooldown = 400 + froggy.getRandom().nextInt(400);
            if (froggy.getFroggyState() == FroggyState.TAUNT) froggy.setFroggyState(FroggyState.CHASE);
        }
    }

    // ------------------------------------------------------------ zombar e ir embora

    /**
     * Matou demais o mesmo jogador: chega perto (andando, em pé), ri, dança na
     * frente dele, diz "Não mexe comigo" e vai embora. Devolve false quando acabou.
     */
    public boolean tickMock(LivingEntity target, double dist) {
        mockTicks++;
        if (froggy.getFroggyState() != FroggyState.TAUNT) froggy.setFroggyState(FroggyState.TAUNT);
        switch (mockPhase) {
            case 0 -> {
                if (dist > 4.5D && mockTicks < 400) {
                    froggy.faceTarget(target);
                    froggy.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    if (mockTicks % 10 == 1) froggy.getNavigation().moveTo(target, 0.9D);
                    return true;
                }
                froggy.getNavigation().stop();
                froggy.speak(VoiceSituation.FOUND, true); // a risada
                say(target, "froggydude.chat.too_bad");
                mockPhase = 1;
                danceTicks = MOCK_DANCE_TICKS;
                danceStyle = 2;
            }
            case 1 -> {
                tickDance(target);
                if (froggy.getFroggyState() != FroggyState.TAUNT) froggy.setFroggyState(FroggyState.TAUNT);
                if (danceTicks <= 0) {
                    say(target, "froggydude.chat.dont_mess");
                    mockPhase = 2;
                    mockTicks = 0;
                }
            }
            default -> {
                froggy.faceTarget(target);
                froggy.getLookControl().setLookAt(target, 30.0F, 30.0F);
                if (mockTicks > 20) {
                    froggy.finishMock(target);
                    mockPhase = 0;
                    mockTicks = 0;
                    froggy.setCrouchingPose(false);
                    return false;
                }
            }
        }
        return true;
    }

    /** Fala no chat como se fosse um jogador: "<FroggyDude> ...". */
    private void say(LivingEntity target, String key) {
        Component line = Component.literal("<FroggyDude> ").append(Component.translatable(key));
        for (Player p : froggy.level().players()) {
            if (p instanceof ServerPlayer sp && (p == target || p.distanceTo(froggy) < 64.0F)) sp.sendSystemMessage(line);
        }
    }
}
