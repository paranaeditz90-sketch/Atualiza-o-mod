package com.froggydude.anticheat;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModSounds;
import com.froggydude.world.FroggyKeeper;
import com.froggydude.world.FroggyWorldData;
import com.froggydude.world.Manhunt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A invasão (fim do "How many days can you survive against me?", 6:42-7:50):
 * tira o criativo, vira noite, as tochas da casa apagam uma a uma, batem na
 * porta, a porta abre, a ligação pro 911... e ele está dentro da casa. Derruba,
 * arranca o braço e acabou. Depois, o boletim de ocorrência e o banimento
 * (um registro dentro do save, que o config "perdoar" apaga).
 *
 * {@code test} (comando /froggydude trapaca testar): tudo igual, menos o banimento.
 */
public final class Invasion {

    public static final ResourceKey<DamageType> DEVOURED =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ModEntityTypes.MOD_ID, "devoured"));

    private static final Set<Block> LIGHTS = Set.of(Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH,
            Blocks.SOUL_WALL_TORCH, Blocks.LANTERN, Blocks.SOUL_LANTERN);
    private static final int TORCH_EVERY = 6;

    private static final Map<UUID, Invasion> RUNNING = new HashMap<>();

    private final UUID playerId;
    private final String playerName;
    private final boolean test;
    private int t;
    private final List<BlockPos> lights = new ArrayList<>();
    @Nullable
    private BlockPos door;
    /** Quando começam as batidas (depois da última tocha). */
    private int knockAt;

    private Invasion(ServerPlayer player, boolean test) {
        this.playerId = player.getUUID();
        this.playerName = player.getScoreboardName();
        this.test = test;
    }

    public static void start(ServerPlayer player, boolean test) {
        if (RUNNING.containsKey(player.getUUID())) return;
        RUNNING.put(player.getUUID(), new Invasion(player, test));
    }

    public static boolean isRunning(ServerPlayer player) {
        return RUNNING.containsKey(player.getUUID());
    }

    public static boolean anyRunning() {
        return !RUNNING.isEmpty();
    }

    /** Saiu do jogo no meio da invasão pra fugir: o banimento vale do mesmo jeito. */
    public static void onLogout(ServerPlayer player) {
        Invasion inv = RUNNING.remove(player.getUUID());
        if (inv != null && !inv.test && player.getServer() != null) {
            ban(player.getServer(), player.getUUID(), inv.playerName);
        }
    }

    public static void tickAll(MinecraftServer server) {
        Iterator<Invasion> it = RUNNING.values().iterator();
        while (it.hasNext()) {
            Invasion inv = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(inv.playerId);
            if (p == null || !inv.tick(server, p)) it.remove();
        }
    }

    /** Devolve false quando acabou. */
    private boolean tick(MinecraftServer server, ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        if (t == 0) setup(server, p, level);
        int k = t - 20;
        if (k >= 0 && k % TORCH_EVERY == 0 && k / TORCH_EVERY < lights.size()) {
            // apaga mais uma (das mais longe pras mais perto: o escuro vem chegando)
            BlockPos pos = lights.get(k / TORCH_EVERY);
            if (LIGHTS.contains(level.getBlockState(pos).getBlock())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.2F);
            }
        }
        int s = t - knockAt;
        if (s == 0 || s == 12 || s == 24) {
            // toc, toc, toc
            level.playSound(null, knockPos(p), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 0.7F, 1.4F);
        } else if (s == 50) {
            openDoor(p, level);
        } else if (s == 60) {
            say(p, Component.translatable("froggydude.invasion.calling").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else if (s == 80) {
            say(p, Component.translatable("froggydude.invasion.operator", Component.translatable("froggydude.invasion.911_1")));
        } else if (s == 110) {
            say(p, Component.literal("<" + playerName + "> ").append(Component.translatable("froggydude.invasion.victim")));
        } else if (s == 125) {
            FroggydudeEntity froggy = inside(server, p);
            if (froggy != null) froggy.invadePin(p);
        } else if (s == 150) {
            FroggydudeEntity froggy = FroggyKeeper.find(server);
            if (froggy != null) froggy.invadeRipArm(p);
        } else if (s == 200) {
            FroggydudeEntity froggy = FroggyKeeper.find(server);
            DamageSource devoured = new DamageSource(
                    level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DEVOURED), froggy);
            p.hurt(devoured, Float.MAX_VALUE);
        } else if (s == 225) {
            say(p, Component.translatable("froggydude.invasion.operator", Component.translatable("froggydude.invasion.911_2")));
        } else if (s == 255) {
            say(p, Component.translatable("froggydude.invasion.operator", Component.translatable("froggydude.invasion.911_3")));
        } else if (s >= 285) {
            Component report = Component.translatable("froggydude.ban.report", playerName);
            if (test) {
                say(p, report);
                Manhunt.title(p, Component.translatable("froggydude.ban.title"), Component.translatable("froggydude.ban.test"));
            } else {
                ban(server, playerId, playerName);
                p.connection.disconnect(report);
            }
            return false;
        }
        t++;
        return true;
    }

    private void setup(MinecraftServer server, ServerPlayer p, ServerLevel level) {
        // nem o criativo te salva
        if (p.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().flying = false;
        p.getAbilities().mayfly = false;
        p.onUpdateAbilities();
        p.removeAllEffects();
        if (level.dimension() == Level.OVERWORLD) {
            long day = level.getDayTime() - level.getDayTime() % 24000L;
            server.overworld().setDayTime(day + 18000L); // meia-noite
        }
        p.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
        BlockPos at = p.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-12, -5, -12), at.offset(12, 6, 12))) {
            BlockState st = level.getBlockState(pos);
            if (LIGHTS.contains(st.getBlock())) lights.add(pos.immutable());
            if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && (door == null || pos.distSqr(at) < door.distSqr(at))) {
                door = pos.immutable();
            }
        }
        lights.sort(Comparator.comparingDouble(pos -> -pos.distSqr(at)));
        if (lights.size() > 48) lights.subList(48, lights.size()).clear();
        knockAt = 20 + lights.size() * TORCH_EVERY + 20;
        // ele espera lá fora, quieto
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy == null) froggy = FroggyKeeper.spawnNear(p, 12.0D, 18.0D, FroggyKeeper.Sight.HIDDEN, -1F);
        else if (froggy.level() != level || froggy.distanceTo(p) > 24.0F) {
            FroggyKeeper.bringNear(froggy, p, 12.0D, 18.0D, FroggyKeeper.Sight.HIDDEN);
            froggy = FroggyKeeper.find(server);
        }
        if (froggy != null) {
            froggy.setTarget(null);
            froggy.holdStill(knockAt + 125, null);
        }
    }

    private BlockPos knockPos(ServerPlayer p) {
        if (door != null) return door;
        Vec3 back = p.position().subtract(p.getViewVector(1.0F).multiply(4, 0, 4));
        return BlockPos.containing(back);
    }

    private void openDoor(ServerPlayer p, ServerLevel level) {
        if (door != null && level.getBlockState(door).getBlock() instanceof DoorBlock d) {
            d.setOpen(null, level, level.getBlockState(door), door, true);
        } else {
            level.playSound(null, knockPos(p), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.9F);
        }
    }

    /** Ele já está dentro: atrás de ti, a um passo. */
    @Nullable
    private static FroggydudeEntity inside(MinecraftServer server, ServerPlayer p) {
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy == null) return null;
        Vec3 look = p.getViewVector(1.0F).multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 behind = p.position().subtract(look.scale(1.6D));
        if (froggy.level() != p.level()) {
            FroggyKeeper.bringNear(froggy, p, 2.0D, 3.0D, FroggyKeeper.Sight.ANY);
            froggy = FroggyKeeper.find(server);
            if (froggy == null) return null;
        }
        froggy.holdStill(0, null);
        froggy.teleportTo(behind.x, p.getY(), behind.z);
        froggy.playSound(ModSounds.SCREAM.get(), 1.5F, 1.0F);
        return froggy;
    }

    private static void say(ServerPlayer p, Component msg) {
        p.sendSystemMessage(msg);
    }

    public static void ban(MinecraftServer server, UUID id, String name) {
        FroggyWorldData data = FroggyWorldData.get(server);
        data.bans.put(id, name);
        data.setDirty();
    }
}
