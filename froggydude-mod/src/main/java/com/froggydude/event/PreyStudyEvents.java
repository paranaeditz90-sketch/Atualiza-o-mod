package com.froggydude.event;

import com.froggydude.brain.PreyStudy;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.player.Paranoia;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O estudo da presa (IA Real): anota a rotina de cada jogador - onde ele guarda
 * coisa (bau, barril), onde dorme, onde trabalha (bancada, fornalha), onde
 * minera - e mede a paranoia dele (Paranoia). Anota sempre (é barato); quem usa
 * é a IA Real (FroggyStakeoutGoal, RealTease).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PreyStudyEvents {

    private PreyStudyEvents() {
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return; // o evento vem uma vez por mão
        BlockPos pos = event.getPos();
        BlockState st = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        PreyStudy.Kind kind;
        if (be instanceof AbstractFurnaceBlockEntity || st.getBlock() instanceof CraftingTableBlock) {
            kind = PreyStudy.Kind.WORK;
        } else if (be instanceof Container) {
            kind = PreyStudy.Kind.STORAGE;
        } else if (st.getBlock() instanceof BedBlock) {
            kind = PreyStudy.Kind.BED;
            if (st.getValue(BedBlock.PART) == BedPart.FOOT) pos = pos.relative(st.getValue(BedBlock.FACING));
        } else {
            return;
        }
        record(p, level, kind, pos);
    }

    /** Minerando debaixo da terra: o lugar onde ela passa o tempo (a caverna). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos at = p.blockPosition();
        if (level.canSeeSky(at.above()) || at.getY() > level.getSeaLevel()) return;
        record(p, level, PreyStudy.Kind.MINE, at);
    }

    private static void record(ServerPlayer p, ServerLevel level, PreyStudy.Kind kind, BlockPos pos) {
        FroggyWorldData data = FroggyWorldData.get(level);
        boolean roofed = !level.canSeeSky(pos.above());
        data.brain.memory(p.getUUID(), p.getScoreboardName()).study.visit(kind, pos, level.dimension().location().toString(),
                level.getGameTime(), roofed);
        data.setDirty();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        boolean near = p.tickCount % 20 == 0 ? nearFroggy(p) : NEAR.getOrDefault(p.getUUID(), false);
        if (p.tickCount % 20 == 0) NEAR.put(p.getUUID(), near);
        Paranoia.tick(p, near);
    }

    private static final java.util.Map<java.util.UUID, Boolean> NEAR = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean nearFroggy(ServerPlayer p) {
        return !p.level().getEntitiesOfClass(FroggydudeEntity.class, p.getBoundingBox().inflate(64.0D), e -> e.isAlive()).isEmpty();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Paranoia.forget(event.getEntity());
        NEAR.remove(event.getEntity().getUUID());
    }
}
