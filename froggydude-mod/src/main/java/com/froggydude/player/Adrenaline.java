package com.froggydude.player;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * (v0.7.67) A adrenalina (item 7 da fila, o sistema de fuga; LM 24/11: "vida baixa + ele perto"): com 3 corações ou menos
 * e o FroggyDude caçando a pessoa a até 10 blocos, ela ganha 4 s de velocidade forte (a tela pulsa nas bordas e o
 * coração bate, EscapeClient); depois cansa (3 s lenta). Uma vez a cada 90 s.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Adrenaline {

    static final float LOW_HEALTH = 6.0F;
    static final double NEAR = 10.0D;
    static final int BOOST = 80;
    static final int TIRED = 60;
    static final long COOLDOWN = 1800L;
    private static final String KEY = "froggydude_adrenaline";

    private Adrenaline() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 5 != 0 || !p.isAlive() || p.isCreative() || p.isSpectator()) return;
        CompoundTag tag = p.getPersistentData().getCompound(KEY);
        long now = p.level().getGameTime();
        long end = tag.getLong("end");
        if (end > 0 && now >= end) {
            // acabou o fôlego: cansa um pouco
            tag.putLong("end", 0L);
            p.getPersistentData().put(KEY, tag);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TIRED, 0, false, false, true));
            return;
        }
        if (end > 0 || p.getHealth() > LOW_HEALTH || now - tag.getLong("at") < COOLDOWN) return;
        List<FroggydudeEntity> near = p.level().getEntitiesOfClass(FroggydudeEntity.class, p.getBoundingBox().inflate(NEAR),
                f -> f.isAlive() && f.getTarget() == p && f.distanceTo(p) <= NEAR);
        if (near.isEmpty()) return;
        tag.putLong("at", now);
        tag.putLong("end", now + BOOST);
        p.getPersistentData().put(KEY, tag);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BOOST, 2, false, false, true));
        ModNetwork.sendEscape(p, (byte) 0, BOOST);
        near.get(0).brainLog("adrenalina: " + p.getScoreboardName() + " disparou (vida " + (int) p.getHealth() + ")");
    }

    /** Pra teste: a adrenalina sem esperar a recarga. */
    public static void reset(Player p) {
        p.getPersistentData().remove(KEY);
    }
}
