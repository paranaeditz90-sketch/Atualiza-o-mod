package com.froggydude.client.cutscene;

import com.froggydude.config.FroggyClientConfig;
import com.froggydude.entity.Finisher;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.CutscenePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * No cliente: as cutscenes do golpe final que estão rolando (de qualquer
 * jogador por perto - todo mundo vê a vítima deitada perdendo os braços e a
 * cabeça; só a vítima ganha a câmera de cinema). Tudo é contado a partir do
 * tick de início que veio do servidor, com a fração do quadro: anda liso a
 * 60 fps (ou o que a tela der), sem esperar pacote.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientFinisher {

    public static final class Entry {
        public final int froggyId;
        public final int victimId;
        public final long start;
        public final Vec3 anchor;
        public final float yaw;
        public final boolean skipLeft;
        public final Vec3 h;
        public final Vec3 l;
        long endedAt = -1;
        /** O áudio da zebra já foi decidido pra essa cutscene (FinisherAudio). */
        boolean audio;

        Entry(int froggyId, int victimId, long start, Vec3 anchor, float yaw, boolean skipLeft) {
            this.froggyId = froggyId;
            this.victimId = victimId;
            this.start = start;
            this.anchor = anchor;
            this.yaw = yaw;
            this.skipLeft = skipLeft;
            this.h = Finisher.dir(yaw);
            this.l = Finisher.left(h);
        }

        /** O tick da tabela agora (com a fração do quadro). */
        public float t(float partial) {
            ClientLevel level = Minecraft.getInstance().level;
            long now = level == null ? start : level.getGameTime();
            return Finisher.timeline(now, start, partial, skipLeft);
        }

        public boolean ended() {
            return endedAt >= 0;
        }

        public Vec3 at(double along, double side, double up) {
            return Finisher.at(anchor, h, along, side, up);
        }
    }

    private static final Map<Integer, Entry> BY_VICTIM = new HashMap<>();

    private ClientFinisher() {
    }

    public static void onPacket(byte kind, int froggyId, int victimId, long start, double x, double y, double z,
                                float yaw, boolean skipLeft) {
        Minecraft mc = Minecraft.getInstance();
        if (kind == CutscenePacket.START) {
            Entry e = new Entry(froggyId, victimId, start, new Vec3(x, y, z), yaw, skipLeft);
            BY_VICTIM.put(victimId, e);
            if (mc.player != null && mc.player.getId() == victimId) CinematicCamera.begin(e);
        } else {
            Entry e = BY_VICTIM.get(victimId);
            if (e != null) {
                e.endedAt = mc.level == null ? 0 : mc.level.getGameTime();
                // a pose de deitado acaba com a morte (o corpo some / vira o cadáver do Extremo)
            }
            // a câmera de quem morreu fica (atrás da tela de morte); quem decide é o CinematicCamera
        }
    }

    /** Todas as cutscenes que o cliente conhece (rolando ou acabando). */
    public static Iterable<Entry> all() {
        return BY_VICTIM.values();
    }

    public static Entry forVictim(int entityId) {
        return BY_VICTIM.get(entityId);
    }

    public static Entry forFroggy(int entityId) {
        for (Entry e : BY_VICTIM.values()) {
            if (e.froggyId == entityId && !e.ended()) return e;
        }
        return null;
    }

    /** Partes que já saíram (1 braço esquerdo, 2 direito, 4 cabeça). Desligado: nada sai (a tela escurece). */
    public static int missing(Entry e, float partial) {
        if (!FroggyClientConfig.gore().dismember()) return 0;
        float t = e.t(partial);
        int m = 0;
        if (Finisher.leftGone(t, e.skipLeft)) m |= 1;
        if (Finisher.rightGone(t)) m |= 2;
        if (Finisher.headGone(t)) m |= 4;
        return m;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BY_VICTIM.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BY_VICTIM.clear();
            return;
        }
        long now = mc.level.getGameTime();
        // a de quem está jogando fica até renascer (a câmera ainda mostra o corpo atrás da tela de morte)
        int me = mc.player == null ? -1 : mc.player.getId();
        boolean meDead = mc.player != null && mc.player.isDeadOrDying();
        BY_VICTIM.values().removeIf(e -> !(e.victimId == me && meDead)
                && ((e.ended() && now - e.endedAt > 40) || now - e.start > Finisher.END + 480));
    }

    /**
     * Renasceu: o jogador novo tem o MESMO número de entidade do que morreu, então
     * a cutscene dele tem que sair na hora - senão o corpo novo era desenhado
     * deitado, sem braços e sem cabeça (ou nem era desenhado).
     */
    public static void forgetVictim(int victimId) {
        BY_VICTIM.remove(victimId);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BY_VICTIM.clear();
        CinematicCamera.end();
    }
}
