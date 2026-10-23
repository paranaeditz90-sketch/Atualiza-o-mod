package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmMove;
import com.becomelm.network.LmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Os pulos especiais de cada jogador (o próprio e os que chegam do servidor), pra
 * animação certa enquanto ele está no ar - e o estrondo quando o pulo super alto cai.
 */
public final class LmMoves {

    private record Entry(LmMove move, int at) {
    }

    private static final Map<UUID, Entry> RECENT = new HashMap<>();

    private LmMoves() {
    }

    static void local(LocalPlayer p, LmMove move) {
        RECENT.put(p.getUUID(), new Entry(move, LmClientEvents.ticks()));
        if (LmClientEvents.serverHasMod()) LmNetwork.CHANNEL.sendToServer(new LmNetwork.DoMove(move));
        if (move != LmMove.HOP) leapSound(p);
    }

    /** Chegou do servidor: outro jogador deu esse pulo. */
    public static void remote(UUID id, LmMove move) {
        RECENT.put(id, new Entry(move, LmClientEvents.ticks()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && move != LmMove.HOP && mc.level.getPlayerByUUID(id) instanceof AbstractClientPlayer p) {
            leapSound(p);
        }
    }

    /** O pulo especial que esse jogador está dando agora (no ar), ou null. */
    @Nullable
    static LmMove airborne(Player p) {
        Entry e = RECENT.get(p.getUUID());
        if (e == null) return null;
        // os primeiros ticks ainda contam (o "no chão" dos outros chega um pouco atrasado)
        if (p.onGround() && LmClientEvents.ticks() - e.at > 3) return null;
        return e.move;
    }

    /** Todo tick: quem pousou sai da lista (o pulo super alto pousa com estrondo). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            RECENT.clear();
            return;
        }
        int now = LmClientEvents.ticks();
        Iterator<Map.Entry<UUID, Entry>> it = RECENT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> en = it.next();
            Player p = mc.level.getPlayerByUUID(en.getKey());
            Entry e = en.getValue();
            if (p == null || now - e.at > 400) {
                it.remove();
            } else if ((p.onGround() || p.isInWater()) && now - e.at > 3) {
                if (e.move == LmMove.SKY && p.onGround()) skyLand(p);
                it.remove();
            }
        }
    }

    static void clear() {
        RECENT.clear();
    }

    private static void leapSound(Player p) {
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), BecomeLM.LEAP.get(), SoundSource.PLAYERS,
                1.0F, 0.9F + p.getRandom().nextFloat() * 0.2F, false);
    }

    /** O pulo super alto chegando: estrondo, nuvem de poeira em roda e terra voando. */
    private static void skyLand(Player p) {
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), BecomeLM.SKY_LAND.get(), SoundSource.PLAYERS,
                1.0F, 0.9F + p.getRandom().nextFloat() * 0.15F, false);
        for (int i = 0; i < 22; i++) {
            double a = i / 22D * Math.PI * 2D;
            double s = 0.12D + p.getRandom().nextDouble() * 0.08D;
            p.level().addParticle(BecomeLM.DUST_PUFF.get(), p.getX() + Math.cos(a) * 0.5D, p.getY() + 0.08D,
                    p.getZ() + Math.sin(a) * 0.5D, Math.cos(a) * s, 0.01D, Math.sin(a) * s);
        }
        BlockState below = p.getBlockStateOn();
        if (below.isAir()) return;
        BlockParticleOption crumbs = new BlockParticleOption(ParticleTypes.BLOCK, below);
        for (int i = 0; i < 16; i++) {
            p.level().addParticle(crumbs, p.getX() + (p.getRandom().nextDouble() - 0.5D) * 1.2D, p.getY() + 0.1D,
                    p.getZ() + (p.getRandom().nextDouble() - 0.5D) * 1.2D,
                    (p.getRandom().nextDouble() - 0.5D) * 0.4D, 0.3D, (p.getRandom().nextDouble() - 0.5D) * 0.4D);
        }
    }
}
