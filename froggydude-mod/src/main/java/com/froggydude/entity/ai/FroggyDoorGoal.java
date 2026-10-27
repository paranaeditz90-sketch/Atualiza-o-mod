package com.froggydude.entity.ai;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;

/**
 * Porta, portão de cerca e alçapão no caminho: abre ou arromba NA HORA, sem parar
 * (v0.7.15, LM: "às vezes ele não sabe abrir uma porta, ficando preso").
 *
 * Antes era o OpenDoorGoal do jogo, que só olha a porta quando o mob segue um caminho
 * calculado E já bateu nela. Correndo reto atrás dela (beeline, colado) não tinha
 * caminho e ele ficava empurrando a porta fechada; portão de cerca e alçapão ele nem
 * via; porta de ferro era parede - ele ia pro cerco, pra língua na parede, pra torre.
 *
 * Agora olha pra onde está indo (o ponto que o MoveControl quer, ou o embalo), até
 * ~1,6 bloco à frente, todo tick:
 * - porta/portão/alçapão de madeira fechado: abre (caçando, às vezes arromba no tranco,
 *   como antes: 4 em 10; com raiva quase sempre; no massacre da vila, 6 em 10);
 * - de ferro: três pancadas (o soco do braço direito) e voa, ~0,6 s;
 * - ela escondida embaixo de um alçapão (buraco no chão): abre/arromba em cima e cai.
 * Arrombar respeita a config "arrombar" e o mobGriefing; sem isso, ferro continua parede.
 * De mansinho (calado, de tocaia) só abre, quieto.
 *
 * FroggyNodeEvaluator usa {@link #passable} pra o caminho calculado passar por portão e
 * porta de ferro (o do jogo só passa por porta de madeira).
 */
public class FroggyDoorGoal extends Goal {

    /** Ferro: pancadas antes de voar (uma a cada {@link #BASH_GAP} ticks). */
    private static final int BASH_HITS = 3;
    private static final int BASH_GAP = 6;

    private final FroggydudeEntity froggy;
    @Nullable
    private BlockPos door;
    private int bashTicks;

    public FroggyDoorGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!froggy.isAlive() || froggy.isPassenger() || froggy.isNoAi()) return false;
        door = doorAhead();
        return door != null;
    }

    @Override
    public boolean canContinueToUse() {
        // só a porta de ferro leva tempo; a de madeira é na hora (start)
        return door != null && bashTicks > 0 && closed(froggy.level(), door)
                && froggy.position().distanceTo(Vec3.atCenterOf(door)) < 3.0D;
    }

    @Override
    public void start() {
        BlockPos pos = door;
        if (pos == null) return;
        Level level = froggy.level();
        BlockState st = level.getBlockState(pos);
        bashTicks = 0;
        if (!openable(st)) {
            // ferro: só se pode arrombar (senão o caminho nem passa por aqui)
            if (canBreak(froggy)) {
                bashTicks = 1;
                froggy.brainLog("porta de ferro em " + pos.toShortString() + ": arromba no soco");
            } else {
                door = null;
            }
            return;
        }
        if (shouldBreak()) {
            if (level instanceof ServerLevel server && ForgeEventFactory.onEntityDestroyBlock(froggy, pos, st)) {
                server.levelEvent(LevelEvent.SOUND_ZOMBIE_DOOR_CRASH, pos, 0);
                destroyNoDrops(level, pos, froggy);
                froggy.swing(InteractionHand.MAIN_HAND);
                froggy.brainLog("arrombou " + st.getBlock().getName().getString() + " em " + pos.toShortString());
            }
        } else {
            open(level, pos, st, froggy);
        }
        door = null;
    }

    @Override
    public void tick() {
        BlockPos pos = door;
        if (pos == null || bashTicks <= 0) return;
        Level level = froggy.level();
        froggy.faceDirection(pos.getX() + 0.5D - froggy.getX(), pos.getZ() + 0.5D - froggy.getZ());
        froggy.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        int t = bashTicks++ - 1;
        if (t % BASH_GAP != 0) return;
        int hit = t / BASH_GAP;
        froggy.swing(InteractionHand.MAIN_HAND);
        if (hit < BASH_HITS) {
            level.levelEvent(LevelEvent.SOUND_ZOMBIE_IRON_DOOR, pos, 0);
            level.destroyBlockProgress(froggy.getId(), pos, (hit + 1) * 3);
            return;
        }
        level.destroyBlockProgress(froggy.getId(), pos, -1);
        BlockState st = level.getBlockState(pos);
        if (ForgeEventFactory.onEntityDestroyBlock(froggy, pos, st)) {
            level.levelEvent(LevelEvent.SOUND_ZOMBIE_DOOR_CRASH, pos, 0);
            destroyNoDrops(level, pos, froggy);
            froggy.brainLog("arrombou " + st.getBlock().getName().getString() + " em " + pos.toShortString());
        }
        bashTicks = 0;
        door = null;
    }

    @Override
    public void stop() {
        if (door != null && bashTicks > 0) froggy.level().destroyBlockProgress(froggy.getId(), door, -1);
        door = null;
        bashTicks = 0;
    }

    // ---------------------------------------------------------------- o que tem na frente

    /** Porta/portão/alçapão fechado logo à frente de onde ele está indo (ou embaixo, com ela lá dentro). */
    @Nullable
    private BlockPos doorAhead() {
        Level level = froggy.level();
        BlockPos under = trapdoorUnder(level);
        if (under != null) return under;
        Vec3 dir = heading();
        if (dir == null) return null;
        double half = froggy.getBbWidth() / 2.0D;
        for (double h : new double[]{0.4D, 1.4D}) {
            for (double s = half + 0.15D; s <= half + 1.6D; s += 0.3D) {
                BlockPos pos = BlockPos.containing(froggy.getX() + dir.x * s, froggy.getY() + h, froggy.getZ() + dir.z * s);
                if (closed(level, pos) && reachable(level, pos)) return bottom(level, pos);
                // parede de verdade antes da porta: a porta não está no caminho
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) break;
            }
        }
        return doorBetween(level);
    }

    /**
     * Ela logo atrás da porta: perto assim ele para de andar (o combate segura a 2 blocos)
     * e ficava parado do lado de fora, sem nunca "ir" pra porta. A porta entre os dois conta.
     */
    @Nullable
    private BlockPos doorBetween(Level level) {
        LivingEntity t = froggy.getTarget();
        if (t == null || froggy.distanceToSqr(t) > 36.0D || froggy.hasLineOfSight(t)) return null;
        for (double h : new double[]{0.4D, 1.4D}) {
            Vec3 from = froggy.position().add(0, h, 0);
            Vec3 to = t.position().add(0, Math.min(h, t.getBbHeight() - 0.2D), 0);
            var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, froggy));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(from) > 9.0D) continue;
            BlockPos pos = hit.getBlockPos();
            if (closed(level, pos) && reachable(level, pos)) return bottom(level, pos);
        }
        return null;
    }

    /** Pra onde ele está indo: o ponto do MoveControl, senão o embalo, senão (encostado) pra onde olha. */
    @Nullable
    private Vec3 heading() {
        var move = froggy.getMoveControl();
        if (move.hasWanted()) {
            Vec3 d = new Vec3(move.getWantedX() - froggy.getX(), 0, move.getWantedZ() - froggy.getZ());
            if (d.lengthSqr() > 0.01D) return d.normalize();
        }
        Vec3 v = froggy.getDeltaMovement().multiply(1, 0, 1);
        if (v.lengthSqr() > 4.0E-4D) return v.normalize();
        if (froggy.horizontalCollision) return Vec3.directionFromRotation(0, froggy.getYRot());
        return null;
    }

    /** Ela no buraco logo embaixo dele e um alçapão fechado tampando (ele em cima). */
    @Nullable
    private BlockPos trapdoorUnder(Level level) {
        LivingEntity t = froggy.getTarget();
        if (t == null || !froggy.onGround() || t.getY() > froggy.getY() - 1.0D) return null;
        double dx = t.getX() - froggy.getX(), dz = t.getZ() - froggy.getZ();
        if (dx * dx + dz * dz > 6.25D) return null;
        BlockPos below = BlockPos.containing(froggy.getX(), froggy.getY() - 0.2D, froggy.getZ());
        for (BlockPos pos : new BlockPos[]{below, below.above()}) {
            if (level.getBlockState(pos).getBlock() instanceof TrapDoorBlock && closed(level, pos)) return pos;
        }
        return null;
    }

    /** Dá pra mexer nela (a de ferro, só se pode arrombar). */
    private boolean reachable(Level level, BlockPos pos) {
        return openable(level.getBlockState(pos)) || canBreak(froggy);
    }

    // ---------------------------------------------------------------- abrir / arrombar

    private boolean shouldBreak() {
        if (!canBreak(froggy)) return false;
        LivingEntity target = froggy.getTarget();
        if (target == null && !froggy.isMassacring()) return false;
        // com raiva (fase 2, o warden que jogou ele longe) quase sempre; no massacre da
        // vila, mais da metade das casas; caçando, 4 em 10
        float chance = froggy.isFrenzyActive() || froggy.isEnraged() ? 0.85F : froggy.isMassacring() ? 0.6F : 0.4F;
        return froggy.getRandom().nextFloat() < chance;
    }

    /** Pode arrombar agora: config "arrombar", mobGriefing, caçando (ou no massacre), sem estar de mansinho. */
    static boolean canBreak(FroggydudeEntity froggy) {
        if (!(froggy.level() instanceof ServerLevel level) || !FroggyConfig.ai().breakIn) return false;
        if (froggy.isMuted() || froggy.isStakingOut()) return false;
        if (froggy.getTarget() == null && !froggy.isMassacring()) return false;
        return ForgeEventFactory.getMobGriefingEvent(level, froggy);
    }

    /**
     * Pro caminho calculado (FroggyNodeEvaluator): portão fechado ele abre, porta de ferro
     * ele arromba (se pode) - então passa por ali como por uma porta de madeira.
     */
    public static boolean passable(FroggydudeEntity froggy, BlockGetter level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        Block block = st.getBlock();
        if (block instanceof FenceGateBlock) {
            // portão com muro/cerca em cima não é passagem
            BlockPos up = pos.above();
            return !st.getValue(FenceGateBlock.OPEN) && level.getBlockState(up).getCollisionShape(level, up).isEmpty();
        }
        if (block instanceof DoorBlock d) {
            return !st.getValue(DoorBlock.OPEN) && (d.type().canOpenByHand() || canBreak(froggy));
        }
        return false;
    }

    private static boolean isDoorLike(BlockState st) {
        Block b = st.getBlock();
        return b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock;
    }

    /** Porta, alçapão ou portão fechado (o que tapa a passagem). */
    private static boolean closed(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        return isDoorLike(st) && st.hasProperty(BlockStateProperties.OPEN) && !st.getValue(BlockStateProperties.OPEN);
    }

    /** Abre como a mão de um jogador abriria (madeira; portão sempre). */
    private static boolean openable(BlockState st) {
        Block b = st.getBlock();
        if (b instanceof DoorBlock d) return d.type().canOpenByHand();
        if (b instanceof TrapDoorBlock) return st.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS);
        return b instanceof FenceGateBlock;
    }

    /** A metade de baixo da porta (é ela que manda nas duas). */
    private static BlockPos bottom(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return pos.below().immutable();
        return pos.immutable();
    }

    private void open(Level level, BlockPos pos, BlockState st, Entity who) {
        Block b = st.getBlock();
        if (b instanceof DoorBlock d) {
            d.setOpen(who, level, st, pos, true);
        } else {
            level.setBlock(pos, st.setValue(BlockStateProperties.OPEN, true), Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            var sound = b instanceof TrapDoorBlock ? net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_OPEN
                    : net.minecraft.sounds.SoundEvents.FENCE_GATE_OPEN;
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
            level.gameEvent(who, GameEvent.BLOCK_OPEN, pos);
        }
        froggy.brainLog("abriu " + st.getBlock().getName().getString() + " em " + pos.toShortString());
    }

    /**
     * Quebra sem deixar item. Porta tem duas metades: quebrando uma, o jogo
     * derrubava a outra como item (a vítima ganhava a porta de volta).
     */
    static void destroyNoDrops(Level level, BlockPos pos, Entity who) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof DoorBlock) {
            BlockPos other = st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            if (level.getBlockState(other).is(st.getBlock())) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        level.destroyBlock(pos, false, who);
    }
}
