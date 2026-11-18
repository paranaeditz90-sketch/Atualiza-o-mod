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
    /**
     * O mortal (How many days 0:47, 10v1 0:26.2, medidos em 60 qps): sai do monte, dá a volta
     * pra trás esticado em ~0,45 s e pousa de braços abertos ("tcharam"), que seguram ~0,7 s.
     * Por jogador, o tick em que começou (vale pra quem vê também).
     */
    private static final Map<UUID, Integer> FLIPS = new HashMap<>();
    static final int FLIP_TICKS = 9;
    /** Até quando o mortal vale (a volta, o resto do pulo e os braços abertos depois de pousar). */
    private static final int FLIP_MAX = 40;
    /** Os braços abertos: a partir do pouso, ~0,8 s. */
    private static final int TADA_TICKS = 16;
    /** Quando pousou do mortal (pra contar o tcharam a partir do chão). */
    private static final Map<UUID, Integer> LANDED = new HashMap<>();

    private LmMoves() {
    }

    static void local(LocalPlayer p, LmMove move) {
        if (move == LmMove.FLIP) {
            FLIPS.put(p.getUUID(), LmClientEvents.ticks());
            LANDED.remove(p.getUUID());
        } else {
            RECENT.put(p.getUUID(), new Entry(move, LmClientEvents.ticks()));
        }
        if (LmClientEvents.serverHasMod()) LmNetwork.CHANNEL.sendToServer(new LmNetwork.DoMove(move));
        if (move != LmMove.HOP) leapSound(p);
    }

    /** Chegou do servidor: outro jogador deu esse pulo. */
    public static void remote(UUID id, LmMove move) {
        if (move == LmMove.FLIP) {
            FLIPS.put(id, LmClientEvents.ticks());
            LANDED.remove(id);
        } else {
            RECENT.put(id, new Entry(move, LmClientEvents.ticks()));
        }
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

    /** Quanto da volta do mortal já foi (0 a 1), ou -1 se não está dando mortal. */
    static float flipTurn(Player p, float partial) {
        Integer at = FLIPS.get(p.getUUID());
        if (at == null) return -1F;
        float t = (LmClientEvents.ticks() - at + partial) / FLIP_TICKS;
        return t >= 1F ? -1F : Math.max(0F, t);
    }

    /** Esse jogador está num mortal (do pulo até os braços abertos acabarem). */
    static boolean flipping(UUID id) {
        return FLIPS.containsKey(id);
    }

    /** No mortal e ainda no ar (a volta já acabou, falta cair): segura a pose do mortal. */
    static boolean flipAir(Player p) {
        Integer at = FLIPS.get(p.getUUID());
        return at != null && !LANDED.containsKey(p.getUUID()) && LmClientEvents.ticks() - at < FLIP_MAX;
    }

    /** Pousou do mortal: os braços abertos ("tcharam"). */
    static boolean tada(Player p) {
        Integer at = LANDED.get(p.getUUID());
        return at != null && LmClientEvents.ticks() - at < TADA_TICKS;
    }

    /** Todo tick: quem pousou sai da lista (o pulo super alto pousa com estrondo). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            RECENT.clear();
            FLIPS.clear();
            LANDED.clear();
            return;
        }
        int now = LmClientEvents.ticks();
        Iterator<Map.Entry<UUID, Integer>> flips = FLIPS.entrySet().iterator();
        while (flips.hasNext()) {
            Map.Entry<UUID, Integer> en = flips.next();
            Player p = mc.level.getPlayerByUUID(en.getKey());
            Integer landed = LANDED.get(en.getKey());
            if (p == null || now - en.getValue() >= FLIP_MAX || (landed != null && now - landed >= TADA_TICKS)) {
                flips.remove();
                LANDED.remove(en.getKey());
            } else if (landed == null && now - en.getValue() >= FLIP_TICKS && (p.onGround() || p.isInWater())) {
                LANDED.put(en.getKey(), now);
            }
        }
        Iterator<Map.Entry<UUID, Entry>> it = RECENT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> en = it.next();
            Player p = mc.level.getPlayerByUUID(en.getKey());
            Entry e = en.getValue();
            if (p == null || now - e.at > 400) {
                it.remove();
            } else if ((p.onGround() || p.isInWater()) && now - e.at > 3) {
                if (e.move == LmMove.SKY && p.onGround()) {
                    skyLand(p);
                    if (p == mc.player) LmAttackClient.skyLanded(mc.player);
                }
                it.remove();
            }
        }
    }

    static void clear() {
        RECENT.clear();
        FLIPS.clear();
        LANDED.clear();
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
