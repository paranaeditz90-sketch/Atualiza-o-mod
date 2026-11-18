package com.froggydude.world;

import com.froggydude.init.ModDamageTypes;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.AntiCheatPacket;
import com.froggydude.network.ModNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * (v0.7.42) O anti-trapaça (LM 16/11). Na tela de criar mundo tem "Trapaças?": Sim (vermelho) ou Não (verde, o padrão).
 * Com Não, quem entrar no criativo ou no espectador (comando, F3+F4, LAN, ou um mundo criado/entrado nesses modos) não
 * entra: volta pro sobrevivência, vê o vídeo dele (cliente: client.anticheat.CheatVideoScreen) e morre com "Você talvez
 * esqueceu de uma coisa... Eu odeio TRAPAÇAS.". E não renasce nunca mais nesse mundo: o botão de renascer some, sair e
 * voltar cai na mesma tela, e se renascer de outro jeito morre de novo. Só criativo e espectador acionam (LM 17/11);
 * comandos, /give e o resto não. Mundo antigo (sem a escolha) fica desligado.
 * <p>
 * No hardcore o espectador é o "morto" do próprio jogo (o botão "Ver o mundo"): ali só o criativo aciona.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AntiCheat {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final byte UNSET = -1;
    public static final byte FORBIDDEN = 0;
    public static final byte ALLOWED = 1;

    /**
     * Sem o "acabou" do cliente (mod mexido, vídeo travado), mata assim mesmo: 3 min pra carregar o mundo e começar
     * (celular lento), e 90 s depois que o cliente avisa que começou (o vídeo tem 34 s; recomeçou, o prazo também).
     */
    private static final int VIDEO_TIMEOUT = 3600;
    private static final int VIDEO_RUN_TIMEOUT = 1800;
    /** Renasceu (ou entrou vivo) estando condenado: morre de novo depois disso. */
    private static final int REKILL_DELAY = 10;

    /**
     * A escolha da tela de criar mundo (cliente, mesmo processo do servidor integrado). O próximo mundo novo usa e
     * volta pro padrão; servidor dedicado (sem tela) fica no padrão, Não.
     */
    private static volatile byte pendingChoice = FORBIDDEN;
    /** Quem está vendo o vídeo agora -> tick do servidor em que acaba o prazo. Ninguém encosta neles enquanto isso. */
    private static final Map<UUID, Long> watching = new HashMap<>();
    /** Condenado vivo -> tick do servidor em que morre de novo. */
    private static final Map<UUID, Long> rekill = new HashMap<>();

    private AntiCheat() {
    }

    /** A tela de criar mundo mudou o botão (true = Sim, pode trapacear). */
    public static void choose(boolean allowed) {
        pendingChoice = allowed ? ALLOWED : FORBIDDEN;
    }

    /** O anti-trapaça está ligado nesse mundo? */
    public static boolean active(MinecraftServer server) {
        return FroggyWorldData.get(server).cheats == FORBIDDEN;
    }

    /** Muda a escolha do mundo (o comando). */
    public static void set(MinecraftServer server, boolean allowed) {
        FroggyWorldData data = FroggyWorldData.get(server);
        data.cheats = allowed ? ALLOWED : FORBIDDEN;
        data.setDirty();
    }

    /** Perdoa (o comando): tira da lista e o cliente libera o renascer. */
    public static boolean pardon(MinecraftServer server, UUID id) {
        FroggyWorldData data = FroggyWorldData.get(server);
        boolean was = data.condemned.remove(id) | data.executed.remove(id);
        watching.remove(id);
        rekill.remove(id);
        data.setDirty();
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) send(p, AntiCheatPacket.CLEAR);
        return was;
    }

    /** Esse jogador está vendo o vídeo (nada machuca ele até acabar)? */
    public static boolean watching(ServerPlayer p) {
        return watching.containsKey(p.getUUID());
    }

    /** O modo conta como trapaça? Criativo sempre; espectador, menos no hardcore (lá é o "morto"). */
    private static boolean cheatMode(MinecraftServer server, GameType mode) {
        return mode == GameType.CREATIVE || mode == GameType.SPECTATOR && !server.isHardcore();
    }

    // ----- o mundo novo recebe a escolha da tela

    /** Só roda na criação do mundo (o jogo procura onde o jogador nasce pela primeira vez). */
    @SubscribeEvent
    public static void onNewWorld(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        FroggyWorldData data = FroggyWorldData.get(level.getServer());
        data.cheats = pendingChoice;
        data.setDirty();
        pendingChoice = FORBIDDEN;
        LOGGER.info("[FroggyDude] mundo novo: trapacas {}", data.cheats == ALLOWED ? "sim" : "nao");
    }

    // ----- o gatilho: entrar no criativo ou no espectador

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        MinecraftServer server = p.getServer();
        if (!cheatMode(server, event.getNewGameMode())) return;
        FroggyWorldData data = FroggyWorldData.get(server);
        // condenado não sai do sobrevivência (nem mesmo num mundo que depois virou "Sim")
        if (data.condemned.contains(p.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (data.cheats != FORBIDDEN) return;
        event.setCanceled(true);
        condemn(p, data);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        FroggyWorldData data = FroggyWorldData.get(p.getServer());
        UUID id = p.getUUID();
        if (data.executed.contains(id)) {
            send(p, AntiCheatPacket.CONDEMNED);
            if (p.isAlive()) scheduleRekill(p);
        } else if (data.condemned.contains(id)) {
            // saiu no meio do vídeo: vê de novo, do começo
            if (cheatMode(p.getServer(), p.gameMode.getGameModeForPlayer())) p.setGameMode(GameType.SURVIVAL);
            startVideo(p);
        } else {
            checkMode(p, data);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        watching.remove(event.getEntity().getUUID());
        rekill.remove(event.getEntity().getUUID());
    }

    /** Renasceu de algum jeito (cliente mexido, outro mod): morre de novo. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null || event.isEndConquered()) return;
        if (FroggyWorldData.get(p.getServer()).executed.contains(p.getUUID())) {
            send(p, AntiCheatPacket.CONDEMNED);
            scheduleRekill(p);
        }
    }

    /** Vendo o vídeo, nada machuca (só o dano do fim). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && watching(p)
                && !event.getSource().is(ModDamageTypes.ANTICHEAT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Long>> it = watching.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (now < e.getValue()) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) execute(p);
        }
        for (Iterator<Map.Entry<UUID, Long>> it = rekill.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (now < e.getValue()) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && p.isAlive()) kill(p);
        }
        // a rede de segurança: um jeito de mudar de modo que não passa pelo evento, ou condenado vivo
        if (now % 20 != 0) return;
        FroggyWorldData data = FroggyWorldData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (data.condemned.contains(id)) {
                // condenado não fica no criativo/espectador nem por um caminho que pula o evento (outro mod)
                if (cheatMode(server, p.gameMode.getGameModeForPlayer())) p.setGameMode(GameType.SURVIVAL);
                if (data.executed.contains(id) && p.isAlive() && !rekill.containsKey(id)) scheduleRekill(p);
            } else {
                checkMode(p, data);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        watching.clear();
        rekill.clear();
    }

    /** Está no criativo/espectador com o anti-trapaça ligado: volta pro sobrevivência e é condenado. */
    private static void checkMode(ServerPlayer p, FroggyWorldData data) {
        MinecraftServer server = p.getServer();
        if (server == null || data.cheats != FORBIDDEN || !cheatMode(server, p.gameMode.getGameModeForPlayer())) return;
        p.setGameMode(GameType.SURVIVAL);
        condemn(p, data);
    }

    private static void condemn(ServerPlayer p, FroggyWorldData data) {
        data.condemned.add(p.getUUID());
        data.setDirty();
        LOGGER.info("[FroggyDude] anti-trapaca: {} entrou no criativo/espectador", p.getScoreboardName());
        startVideo(p);
    }

    private static void startVideo(ServerPlayer p) {
        if (p.getServer() == null) return;
        watching.put(p.getUUID(), (long) p.getServer().getTickCount() + VIDEO_TIMEOUT);
        send(p, AntiCheatPacket.PLAY);
    }

    /** O cliente avisou que o vídeo começou a passar: o prazo conta daqui. */
    public static void videoStarted(ServerPlayer p) {
        if (p.getServer() != null && watching.containsKey(p.getUUID())) {
            watching.put(p.getUUID(), (long) p.getServer().getTickCount() + VIDEO_RUN_TIMEOUT);
        }
    }

    /** O cliente avisou que o vídeo acabou (ou que não tem vídeo no jar). */
    public static void videoDone(ServerPlayer p) {
        if (watching.remove(p.getUUID()) != null) execute(p);
    }

    private static void execute(ServerPlayer p) {
        if (p.getServer() == null) return;
        FroggyWorldData data = FroggyWorldData.get(p.getServer());
        if (!data.condemned.contains(p.getUUID())) return; // perdoado no meio do vídeo
        data.executed.add(p.getUUID());
        data.setDirty();
        send(p, AntiCheatPacket.CONDEMNED); // antes da morte: o cliente já abre a tela certa e trava o renascer
        kill(p);
    }

    private static void scheduleRekill(ServerPlayer p) {
        if (p.getServer() != null) rekill.put(p.getUUID(), (long) p.getServer().getTickCount() + REKILL_DELAY);
    }

    private static void kill(ServerPlayer p) {
        if (!p.isAlive()) return;
        var type = p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ModDamageTypes.ANTICHEAT);
        DamageSource src = new DamageSource(type);
        p.hurt(src, Float.MAX_VALUE);
        if (p.isAlive()) {
            // algum outro mod segurou o dano: morre do mesmo jeito
            p.getCombatTracker().recordDamage(src, Float.MAX_VALUE); // a mensagem da morte é a dele
            p.setHealth(0F);
            p.die(src);
        }
    }

    private static void send(ServerPlayer p, byte action) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new AntiCheatPacket(action));
    }
}
