package com.froggydude.world;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Um FroggyDude por mundo (Parte 4). Quem nasce por último fica: o /summon
 * "chama" ele pra perto (o antigo some); um FroggyDude velho que aparece ao
 * carregar um pedaço do mapa, se não for o do registro, some sozinho.
 */
public final class FroggyKeeper {

    /** Onde ele pode aparecer em relação à vista do jogador. */
    public enum Sight {
        /** Qualquer lugar. */
        ANY,
        /** Fora da vista (atrás de alguma coisa ou nas costas): ele não "brota" na tua cara. */
        HIDDEN,
        /** Na tua frente, à vista (o aviso do anti-trapaça: ele ali, te olhando). */
        IN_VIEW
    }

    private FroggyKeeper() {
    }

    /** Um FroggyDude entrou no mundo: devolve false se ele não deve ficar. */
    public static boolean onJoin(FroggydudeEntity froggy, boolean loadedFromDisk) {
        MinecraftServer server = froggy.getServer();
        if (server == null) return true;
        FroggyWorldData data = FroggyWorldData.get(server);
        if (froggy.getUUID().equals(data.froggyId)) {
            data.froggyAlive = true;
            data.setDirty();
            return true;
        }
        if (loadedFromDisk && data.froggyId != null) {
            // sobra de antes (o registro já é de outro): some
            return false;
        }
        FroggydudeEntity old = find(server);
        if (old != null && old != froggy) old.discard();
        data.froggyId = froggy.getUUID();
        data.froggyAlive = true;
        data.froggyHealth = froggy.getHealth();
        data.respawnAt = -1;
        data.setDirty();
        return true;
    }

    /** Morreu: no manhunt ele volta depois de um tempo. */
    public static void onDeath(FroggydudeEntity froggy) {
        MinecraftServer server = froggy.getServer();
        if (server == null) return;
        FroggyWorldData data = FroggyWorldData.get(server);
        if (!froggy.getUUID().equals(data.froggyId)) return;
        data.froggyAlive = false;
        data.respawnAt = data.manhuntActive ? server.overworld().getGameTime() + 20L * com.froggydude.config.FroggyConfig
                .get(com.froggydude.config.FroggyConfig.VOLTA_SEGUNDOS) : -1;
        data.setDirty();
    }

    /** O FroggyDude do registro, se estiver carregado em alguma dimensão. */
    @Nullable
    public static FroggydudeEntity find(MinecraftServer server) {
        FroggyWorldData data = FroggyWorldData.get(server);
        if (data.froggyId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(data.froggyId);
            if (e instanceof FroggydudeEntity f && f.isAlive()) return f;
        }
        return null;
    }

    /**
     * Cria o FroggyDude perto do jogador (o que existir em outro lugar some: um
     * por mundo). {@code health} <= 0 = vida cheia.
     */
    @Nullable
    public static FroggydudeEntity spawnNear(ServerPlayer player, double minD, double maxD, Sight sight, float health) {
        ServerLevel level = player.serverLevel();
        BlockPos spot = findSpot(level, player, minD, maxD, sight, level.random);
        if (spot == null) return null;
        FroggydudeEntity froggy = ModEntityTypes.FROGGYDUDE.get().create(level);
        if (froggy == null) return null;
        froggy.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, level.random.nextFloat() * 360F, 0F);
        froggy.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.COMMAND, null, null);
        if (health > 0F) froggy.setHealth(Math.min(froggy.getMaxHealth(), health));
        froggy.faceDirection(player.getX() - froggy.getX(), player.getZ() - froggy.getZ());
        level.addFreshEntity(froggy);
        return froggy;
    }

    /** Leva o FroggyDude pra perto do jogador (outra dimensão também). */
    public static boolean bringNear(FroggydudeEntity froggy, ServerPlayer player, double minD, double maxD, Sight sight) {
        ServerLevel level = player.serverLevel();
        BlockPos spot = findSpot(level, player, minD, maxD, sight, level.random);
        if (spot == null) return false;
        Vec3 to = new Vec3(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        froggy.getNavigation().stop();
        if (froggy.level() == level) {
            froggy.teleportTo(to.x, to.y, to.z);
        } else {
            froggy.changeDimension(level, new ITeleporter() {
                @Override
                public PortalInfo getPortalInfo(Entity entity, ServerLevel dest, Function<ServerLevel, PortalInfo> vanilla) {
                    return new PortalInfo(to, Vec3.ZERO, entity.getYRot(), entity.getXRot());
                }

                @Override
                public boolean isVanilla() {
                    return false;
                }
            });
        }
        return true;
    }

    /**
     * Um lugar pra ele aparecer entre minD e maxD do jogador: chão firme, 3
     * blocos de ar, sem água/lava, respeitando a vista do jogador ({@link Sight}).
     * Se não achar nenhum assim, aceita qualquer um.
     */
    @Nullable
    public static BlockPos findSpot(ServerLevel level, ServerPlayer player, double minD, double maxD, Sight sight,
                                    RandomSource random) {
        for (int pass = 0; pass < 2; pass++) {
            Sight want = pass == 0 ? sight : Sight.ANY;
            for (int i = 0; i < 40; i++) {
                double a;
                if (want == Sight.IN_VIEW) {
                    // num leque de ~70 graus na frente do jogador
                    a = Math.toRadians(player.getYRot() + 90.0F) + (random.nextDouble() - 0.5D) * 1.2D;
                } else {
                    a = random.nextDouble() * Math.PI * 2.0D;
                }
                double d = minD + random.nextDouble() * (maxD - minD);
                int x = Mth.floor(player.getX() + Math.cos(a) * d);
                int z = Mth.floor(player.getZ() + Math.sin(a) * d);
                BlockPos found = groundAt(level, x, Mth.floor(player.getY()), z);
                if (found == null) continue;
                if (want == Sight.HIDDEN && visible(level, player, found)) continue;
                if (want == Sight.IN_VIEW && !visible(level, player, found)) continue;
                return found;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos groundAt(ServerLevel level, int x, int y0, int z) {
        if (!level.hasChunkAt(new BlockPos(x, y0, z))) return null;
        for (int dy = 10; dy >= -14; dy--) {
            BlockPos p = new BlockPos(x, y0 + dy, z);
            if (!level.isInWorldBounds(p)) continue;
            if (free(level, p) && free(level, p.above()) && free(level, p.above(2))
                    && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                    && level.getFluidState(p.below()).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty();
    }

    /**
     * O jogador enxerga esse ponto? Na frente dele e sem nada no caminho - vidro
     * e vidraça não tapam (o aviso do anti-trapaça: ele te olhando pela janela).
     */
    public static boolean visible(ServerLevel level, ServerPlayer player, BlockPos p) {
        Vec3 eye = player.getEyePosition();
        Vec3 at = new Vec3(p.getX() + 0.5D, p.getY() + 1.4D, p.getZ() + 0.5D);
        Vec3 to = at.subtract(eye);
        double len = to.length();
        if (len < 1.0E-3D) return true;
        if (to.scale(1.0D / len).dot(player.getViewVector(1.0F)) < 0.2D) return false; // nas costas dele
        BlockPos last = null;
        for (double d = 0.3D; d < len - 0.3D; d += 0.25D) {
            BlockPos b = BlockPos.containing(eye.add(to.scale(d / len)));
            if (b.equals(last)) continue;
            last = b;
            BlockState st = level.getBlockState(b);
            if (st.getCollisionShape(level, b).isEmpty()) continue;
            if (st.is(BlockTags.IMPERMEABLE) || st.getBlock() instanceof IronBarsBlock) continue; // vidro, vidraça
            return false;
        }
        return true;
    }
}
