package com.froggydude.world;

import com.froggydude.brain.PlayerMemory;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * (v0.7.25) A caçada fora do manhunt não acaba quando a vítima vai longe (LM: "às vezes ele some do nada. Se eu
 * for longe dele, simplesmente acaba a caçada porque ele não é mais carregado/não sabe mais onde tô").
 *
 * Antes, sem o manhunt ou a invasão (que já têm o Manhunt.keepOnTrail), ele só te achava pela vista e pelo
 * cheiro: a mais de ~60 blocos, noutra dimensão, de pérola, de elytra ou com ele descarregado, ele te perdia pra
 * sempre. Agora o mundo lembra quem ele está caçando (10 min desde a última vez que viu a pessoa, salvo no
 * froggydude.dat) e, se ela ficar longe de 1 a 3 min, ele reaparece ESCONDIDO a 28-44 blocos dela, fora da
 * vista, e vem atrás - quem achou que despistou leva o susto pelas costas.
 *
 * Acaba quando ele mata a pessoa ou morre, e no manhunt/invasão (que têm o deles); espera (sem contar) enquanto ele
 * come, se esconde pra se curar, está de tocaia ou no massacre da vila (o lugar pra onde ela vai voltar), briga com
 * outro, ou ela está morta, fora do jogo ou no Pacífico.
 *
 * (v0.7.26) A trégua (ele zombou e sumiu) só pausa a caçada: quando acaba (1 a 2 min), ele volta na hora, escondido
 * pelas costas dela ({@link #ambush}).
 */
public final class Hunt {

    /** Ele lembra da caçada por 10 min depois da última vez que viu a pessoa. */
    public static final long MEMORY = 12000L;
    /** Mais longe que isso (ou noutra dimensão, ou ele descarregado): ela sumiu. */
    private static final double FAR = 72.0D;
    /** Quanto tempo ela fica sumida antes dele reaparecer: de 1 a 3 min (ticks). */
    private static final int DELAY_MIN = 1200;
    private static final int DELAY_RANDOM = 2400;

    private static int lostTicks;
    private static int lostDelay = -1;
    /** Quando o .dat foi marcado pra salvar a última vez (não precisa ser todo segundo). */
    private static long dirtyAt;

    private Hunt() {
    }

    /** Ele está caçando e vendo essa pessoa (a cada segundo, no FroggydudeEntity): renova a caçada. */
    public static void saw(MinecraftServer server, ServerPlayer p) {
        FroggyWorldData data = FroggyWorldData.get(server);
        if (data.manhuntActive || Invasion.hunting(data)) return; // esses têm o deles
        long now = server.overworld().getGameTime();
        boolean changed = !p.getUUID().equals(data.huntPlayer);
        data.huntPlayer = p.getUUID();
        data.huntUntil = now + MEMORY;
        if (changed) {
            lostTicks = 0;
            lostDelay = -1;
            data.huntAmbush = false; // a volta pelas costas era pra outra pessoa
        }
        // (salva no máximo a cada 30 s: no celular cada gravação do .dat pesa)
        if (changed || now - dirtyAt >= 600L) {
            dirtyAt = now;
            data.setDirty();
        }
    }

    /** Servidor parou (saiu do mundo): o relógio não passa pro próximo mundo. */
    public static void reset() {
        lostTicks = 0;
        lostDelay = -1;
        dirtyAt = 0L;
    }

    /** Acabou a caçada dessa pessoa (ele matou ela). */
    public static void forget(MinecraftServer server, UUID player) {
        FroggyWorldData data = FroggyWorldData.get(server);
        if (!player.equals(data.huntPlayer)) return;
        data.huntPlayer = null;
        data.huntAmbush = false;
        data.setDirty();
        lostTicks = 0;
        lostDelay = -1;
    }

    /**
     * (v0.7.26) Ele zombou dela e sumiu, com trégua até {@code truceUntil}: a caçada continua e, quando a trégua
     * acabar, ele volta escondido pelas costas (antes era um dia inteiro de paz, 20 min reais, e ele "parava do
     * nada").
     */
    public static void ambush(MinecraftServer server, UUID player, long truceUntil) {
        FroggyWorldData data = FroggyWorldData.get(server);
        if (data.manhuntActive || Invasion.hunting(data)) return; // esses têm o deles
        data.huntPlayer = player;
        data.huntUntil = truceUntil + MEMORY;
        data.huntAmbush = true;
        data.setDirty();
        lostTicks = 0;
        lostDelay = -1;
    }

    /** Ela mexeu com ele na trégua: já está brigando ali, não precisa voltar escondido (só se a volta era pra ela). */
    public static void cancelAmbush(MinecraftServer server, UUID player) {
        FroggyWorldData data = FroggyWorldData.get(server);
        if (!data.huntAmbush || !player.equals(data.huntPlayer)) return;
        data.huntAmbush = false;
        data.setDirty();
    }

    /** A cada segundo (FroggyServerEvents). */
    public static void tick(MinecraftServer server) {
        FroggyWorldData data = FroggyWorldData.get(server);
        // manhunt e invasão já têm o deles (Manhunt.keepOnTrail): a caçada daqui some, pra não voltar depois deles
        if (data.manhuntActive || Invasion.hunting(data)) {
            if (data.huntPlayer != null) {
                data.huntPlayer = null;
                data.huntAmbush = false;
                data.setDirty();
            }
            lostTicks = 0;
            return;
        }
        if (data.huntPlayer == null) {
            lostTicks = 0;
            return;
        }
        long now = server.overworld().getGameTime();
        if (now > data.huntUntil || !data.froggyAlive) {
            // passou o tempo da caçada, ou ele morreu
            data.huntPlayer = null;
            data.huntAmbush = false;
            data.setDirty();
            lostTicks = 0;
            lostDelay = -1;
            return;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(data.huntPlayer);
        if (p == null || !p.isAlive() || p.isCreative() || p.isSpectator()) return; // fora do jogo ou morta: espera
        // no Pacífico o jogo some com ele na hora: nada de ficar criando outro
        if (p.level().getDifficulty() == Difficulty.PEACEFUL) return;
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy != null) data.froggyHealth = froggy.getHealth(); // pra voltar com a vida que tinha, se descarregar
        // de trégua com ela (zombou e sumiu): deixa em paz até acabar, sem contar o tempo de sumida
        PlayerMemory m = data.brain.find(p.getUUID());
        if (m != null && m.underTruce(now)) {
            lostTicks = 0;
            lostDelay = -1;
            return;
        }
        boolean ambush = data.huntAmbush; // a trégua acabou: volta agora, esteja onde estiver
        boolean lost = froggy == null || froggy.level() != p.level() || froggy.distanceTo(p) > FAR;
        if (!lost && !ambush) {
            lostTicks = 0;
            lostDelay = -1;
            return;
        }
        if (froggy != null) {
            // comendo, se escondendo pra se curar, de tocaia ou no massacre (esperando ela voltar), brigando com
            // outro, no meio de um golpe: espera
            FroggyState st = froggy.getFroggyState();
            LivingEntity other = froggy.getTarget();
            if (froggy.isFedOn(p) || froggy.isHiding() || froggy.isHolding() || froggy.getFeedTarget() != null
                    || froggy.isStakingOut() || froggy.isMassacring() || other != null && other != p && other.isAlive()
                    || st != FroggyState.CHASE && st != FroggyState.IDLE) return;
        }
        if (!ambush) {
            if (lostDelay < 0) lostDelay = DELAY_MIN + server.overworld().random.nextInt(DELAY_RANDOM);
            lostTicks += 20;
            if (lostTicks < lostDelay) return;
        }
        lostTicks = 0;
        lostDelay = -1;
        FroggydudeEntity back;
        if (froggy != null && !lost) {
            // (só na volta da trégua) perto o bastante: vem de onde está, pelas costas se ela não estiver olhando
            // (RealTactics). Teleportar aqui dava "pop" na frente dela ou podia levar ele pra mais longe.
            back = froggy;
        } else if (froggy != null) {
            // trocar de dimensão cria outra instância: procura de novo depois
            back = FroggyKeeper.bringNear(froggy, p, 28.0D, 44.0D, FroggyKeeper.Sight.HIDDEN) ? FroggyKeeper.find(server) : null;
        } else {
            // vivo, mas num pedaço do mapa que não está carregado: um novo no lugar (o velho some quando carregar)
            back = FroggyKeeper.spawnNear(p, 28.0D, 44.0D, FroggyKeeper.Sight.HIDDEN, data.froggyHealth);
        }
        if (back == null) return; // sem lugar agora: tenta de novo depois do próximo intervalo (na volta, no próximo segundo)
        if (ambush) {
            data.huntAmbush = false;
            data.setDirty();
        }
        back.setTarget(p); // sabe onde ela está: vem atrás, pelas costas (RealTactics)
        back.brainLog(!ambush ? p.getScoreboardName() + " foi longe: reapareci escondido perto dela"
                : back == froggy && !lost ? "acabou a trégua com " + p.getScoreboardName() + ": volto a caçar daqui"
                : "acabou a trégua com " + p.getScoreboardName() + ": voltei escondido pelas costas");
    }
}
