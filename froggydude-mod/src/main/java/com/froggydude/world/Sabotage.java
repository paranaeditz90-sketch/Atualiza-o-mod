package com.froggydude.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * O que o jogador está construindo pra zerar o jogo e que ele pode destruir:
 * o portal do Nether ("He ate my Nether portal", vs Grox 3:54). Ele "sabe" que
 * o portal está saindo quando o jogador começa a pôr obsidiana, e também acha
 * portal aceso perto de quem ele caça. Fica só na memória (o mapa é a prova).
 */
public final class Sabotage {

    /** Blocos de portal por dimensão: obsidiana que um jogador pôs + portal aceso. */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PORTALS = new HashMap<>();

    private Sabotage() {
    }

    public static void onPlace(ServerPlayer player, BlockPos pos, BlockState placed) {
        if (placed.is(Blocks.OBSIDIAN) || placed.is(Blocks.CRYING_OBSIDIAN)) {
            PORTALS.computeIfAbsent(player.level().dimension(), k -> new HashSet<>()).add(pos.immutable());
        }
    }

    /** Portal aceso (ou achado perto de alguém): guarda a moldura toda. */
    public static void onPortal(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = PORTALS.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -3, -3), pos.offset(3, 3, 3))) {
            if (isPortalBlock(level.getBlockState(p))) set.add(p.immutable());
        }
    }

    /** De vez em quando: procura portal aceso perto de cada jogador (achou = guarda). */
    public static void scanNear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-10, -5, -10), at.offset(10, 5, 10))) {
            if (level.getBlockState(p).is(Blocks.NETHER_PORTAL)) {
                onPortal(level, p);
                return;
            }
        }
    }

    /** O bloco de portal mais perto (até maxDist), limpando o que já não existe. */
    @Nullable
    public static BlockPos nearest(ServerLevel level, Vec3 from, double maxDist) {
        Set<BlockPos> set = PORTALS.get(level.dimension());
        if (set == null || set.isEmpty()) return null;
        set.removeIf(p -> level.isLoaded(p) && !isPortalBlock(level.getBlockState(p)));
        BlockPos best = null;
        double bestD = maxDist * maxDist;
        for (BlockPos p : set) {
            double d = p.distToCenterSqr(from);
            if (d < bestD && level.isLoaded(p)) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static boolean isPortalBlock(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.NETHER_PORTAL);
    }

    public static void clear() {
        PORTALS.clear();
    }
}
