package com.froggydude.anticheat;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceLine;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModSounds;
import com.froggydude.world.FroggyKeeper;
import com.froggydude.world.FroggyWorldData;
import com.froggydude.world.Manhunt;
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
 * A invasão (fim do "How many days can you survive against me?", 6:57-7:33).
 * Segue o áudio original, segundo a segundo:
 * <ol>
 *   <li>ele vira pra ti e reage, falado: "What... the hell? That's cheating!
 *       You can't do that!" - e o criativo some;</li>
 *   <li>ele some, cai a noite e fica tudo quieto (no vídeo é o "I'm going to
 *       survive forever" do jogador);</li>
 *   <li>a janela mais perto estoura (o vidro do vídeo) e as tochas vão apagando;</li>
 *   <li>2 s depois começa o áudio da casa: "What the f* is that? Oh sh*t, he's
 *       in my house!", a porta abre, e a ligação pro 911 com o jogador
 *       desesperado;</li>
 *   <li>no "AHHH!" ele aparece atrás de ti e derruba, arranca o braço e, quando
 *       no vídeo a câmera fica vermelha, acabou;</li>
 *   <li>quando o áudio termina: o boletim de ocorrência e o banimento (um
 *       registro dentro do save, que o config "perdoar" apaga).</li>
 * </ol>
 * Os tempos abaixo saem do vídeo (CALL_* contam a partir do começo do clipe
 * da ligação, que começa em 8,15 s da gravação). Nada de texto no chat. Todo
 * som vai só pra quem trapaceou (num mundo offline, é o mundo todo).
 *
 * {@code test} (comando /froggydude trapaca testar): tudo igual, menos o banimento.
 */
public final class Invasion {

    public static final ResourceKey<DamageType> DEVOURED =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ModEntityTypes.MOD_ID, "devoured"));

    private static final Set<Block> LIGHTS = Set.of(Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH,
            Blocks.SOUL_WALL_TORCH, Blocks.LANTERN, Blocks.SOUL_LANTERN);
    private static final int TORCH_EVERY = 4;
    /** Depois da fala: ele some (no vídeo, o jogador fala mais 2 s). */
    private static final int AFTER_REACTION = 6;
    /** Do sumiço até o vidro estourar (6,2 s no vídeo; a fala acaba em 3,6 s). */
    private static final int REACT_TO_GLASS = 46;
    /** Do vidro até o áudio da casa (8,15 s). */
    private static final int GLASS_TO_CALL = 40;
    /** Tamanho do clipe da casa + ligação (8,15 s até o fim do vídeo, 35,3 s). */
    static final int CALL_TICKS = 543;
    /** "What? What the f* is that?" - a câmera está na porta do quarto. */
    static final int CALL_DOOR = 25;
    /** O "AHHH!" (26,96 s): ele aparece atrás de ti e derruba. */
    static final int CALL_PIN = 368;
    /** Arranca o braço (28,1 s). */
    static final int CALL_RIP = 400;
    /** A câmera fica vermelha (32,75 s): acabou. */
    static final int CALL_KILL = 492;

    private static final Map<UUID, Invasion> RUNNING = new HashMap<>();

    private final UUID playerId;
    private final String playerName;
    private final boolean test;
    private int t;
    private final List<BlockPos> lights = new ArrayList<>();
    @Nullable
    private BlockPos door;
    /** Fim da fala dele, o vidro e o começo do áudio da casa (ticks da invasão). */
    private int reactEnd = Integer.MAX_VALUE;
    private int glassAt = Integer.MAX_VALUE;
    private int callAt = Integer.MAX_VALUE;

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
        if (t == 0) react(server, p, level);
        if (t == reactEnd) {
            debug(p, "some e escurece");
            setup(server, p, level);
        }
        if (t >= glassAt) {
            int k = t - glassAt;
            if (k % TORCH_EVERY == 0 && k / TORCH_EVERY < lights.size()) {
                // apaga mais uma (das mais longe pras mais perto: o escuro vem chegando)
                BlockPos pos = lights.get(k / TORCH_EVERY);
                if (LIGHTS.contains(level.getBlockState(pos).getBlock())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.2F);
                }
            }
        }
        if (t == glassAt) {
            debug(p, "vidro");
            breakWindow(p, level);
        }
        int c = t - callAt;
        if (c == 0) {
            // a casa e a ligação pro 911 (com o pacote de voz: o áudio original do vídeo)
            debug(p, "ligacao");
            p.playNotifySound(ModSounds.INVASION_CALL.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        } else if (c == CALL_DOOR) {
            debug(p, "porta");
            openDoor(p, level);
        } else if (c == CALL_PIN) {
            debug(p, "derruba");
            FroggydudeEntity froggy = inside(server, p);
            if (froggy != null) froggy.invadePin(p);
        } else if (c == CALL_RIP) {
            debug(p, "braco");
            FroggydudeEntity froggy = FroggyKeeper.find(server);
            if (froggy != null) froggy.invadeRipArm(p);
        } else if (c == CALL_KILL) {
            debug(p, "morte");
            FroggydudeEntity froggy = FroggyKeeper.find(server);
            DamageSource devoured = new DamageSource(
                    level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DEVOURED), froggy);
            p.hurt(devoured, Float.MAX_VALUE);
        } else if (c >= CALL_TICKS + 20) {
            debug(p, "boletim");
            Component report = Component.translatable("froggydude.ban.report", playerName);
            if (test) {
                p.sendSystemMessage(report);
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

    /**
     * Pegou no flagra: o FroggyDude (o que estava te caçando; se não tiver, ele
     * aparece) vira pra ti e reage, falado - "What... the hell? That's cheating!
     * You can't do that!". O som vai só pra quem trapaceou, de onde estiver.
     */
    private void react(MinecraftServer server, ServerPlayer p, ServerLevel level) {
        // nem o criativo te salva
        if (p.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().flying = false;
        p.getAbilities().mayfly = false;
        p.onUpdateAbilities();
        p.removeAllEffects();
        p.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy == null || froggy.level() != level || froggy.distanceTo(p) > 32.0F) {
            if (froggy == null) froggy = FroggyKeeper.spawnNear(p, 5.0D, 8.0D, FroggyKeeper.Sight.IN_VIEW, -1F);
            else {
                FroggyKeeper.bringNear(froggy, p, 5.0D, 8.0D, FroggyKeeper.Sight.IN_VIEW);
                froggy = FroggyKeeper.find(server);
            }
        }
        reactEnd = VoiceLine.CHEAT_REACTION.ticks + AFTER_REACTION;
        glassAt = reactEnd + REACT_TO_GLASS;
        callAt = glassAt + GLASS_TO_CALL;
        if (froggy != null) {
            froggy.holdStill(reactEnd, p);
            froggy.speakTo(p, VoiceSituation.CHEATING);
        }
    }

    /** Depois da reação: ele some, cai a noite e começa a invasão. */
    private void setup(MinecraftServer server, ServerPlayer p, ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            long day = level.getDayTime() - level.getDayTime() % 24000L;
            server.overworld().setDayTime(day + 18000L); // meia-noite
        }
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
        // ele some da tua frente e espera lá fora, quieto
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy != null) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    froggy.getX(), froggy.getY() + 1.0D, froggy.getZ(), 25, 0.4D, 0.8D, 0.4D, 0.02D);
        }
        if (froggy == null) froggy = FroggyKeeper.spawnNear(p, 12.0D, 18.0D, FroggyKeeper.Sight.HIDDEN, -1F);
        else {
            FroggyKeeper.bringNear(froggy, p, 12.0D, 18.0D, FroggyKeeper.Sight.HIDDEN);
            froggy = FroggyKeeper.find(server);
        }
        if (froggy != null) {
            froggy.setTarget(null);
            froggy.holdStill(callAt + CALL_PIN - t + 5, null);
            // daqui pra frente quem fala é o vídeo (o "AHHH!", a música): ele fica calado
            froggy.mute(callAt + CALL_TICKS - t + 40);
        }
    }

    /** A janela mais perto estoura (vidro/vidraça); sem janela, só o barulho do vídeo. */
    private void breakWindow(ServerPlayer p, ServerLevel level) {
        BlockPos at = p.blockPosition();
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-8, -2, -8), at.offset(8, 4, 8))) {
            BlockState st = level.getBlockState(pos);
            if ((st.is(net.minecraft.tags.BlockTags.IMPERMEABLE) || st.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock
                    && st.getSoundType() == net.minecraft.world.level.block.SoundType.GLASS)
                    && (best == null || pos.distSqr(at) < best.distSqr(at))) {
                best = pos.immutable();
            }
        }
        if (best != null) level.destroyBlock(best, false);
        p.playNotifySound(ModSounds.INVASION_GLASS.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private BlockPos doorSoundPos(ServerPlayer p) {
        if (door != null) return door;
        Vec3 back = p.position().subtract(p.getViewVector(1.0F).multiply(4, 0, 4));
        return BlockPos.containing(back);
    }

    private void openDoor(ServerPlayer p, ServerLevel level) {
        if (door != null && level.getBlockState(door).getBlock() instanceof DoorBlock d) {
            d.setOpen(null, level, level.getBlockState(door), door, true);
        } else {
            level.playSound(null, doorSoundPos(p), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.9F);
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
        // o grito é o "AHHH!" do áudio da ligação; aqui só o rosnado de quem chegou
        froggy.playSound(ModSounds.CONTORT.get(), 0.8F, 1.1F);
        return froggy;
    }

    private void debug(ServerPlayer p, String stage) {
        if (Boolean.getBoolean("froggydude.debug")) {
            System.out.println("[FROGGYDEBUG] invasao t=" + t + " " + stage + " (" + p.getScoreboardName() + ")");
        }
    }

    public static void ban(MinecraftServer server, UUID id, String name) {
        FroggyWorldData data = FroggyWorldData.get(server);
        data.bans.put(id, name);
        data.setDirty();
    }
}
