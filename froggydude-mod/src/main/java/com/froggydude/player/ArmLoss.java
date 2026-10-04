package com.froggydude.player;


import com.froggydude.entity.BloodFx;
import com.froggydude.init.ModSounds;
import com.froggydude.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * O braço esquerdo arrancado pelo Froggy (lado do servidor).
 *
 * Sem o braço: 3 corações a menos de vida máxima, sem a mão secundária (escudo,
 * totem, tocha...) e sangrando sem parar pelo ombro. Não tem como estancar.
 * O sangramento vai tirando vida devagar, mas sozinho não mata: para em 3
 * corações. O braço só volta quando o jogador morre (a marca fica nos dados do
 * jogador, que o Forge não copia na morte).
 */
public final class ArmLoss {

    private static final String KEY = "froggydude.armless";
    private static final UUID HEALTH_ID = UUID.fromString("b3d6f1a2-4c5e-4f7a-9b8c-1d2e3f4a5b6c");
    private static final double HEALTH_LOSS = 6.0D;
    /** O sangramento tira 1 de vida a cada 8 s, mas não desce daqui. */
    private static final float BLEED_FLOOR = 6.0F;
    private static final int BLEED_DAMAGE_EVERY = 160;
    /** Esguicho mais forte a cada "batida do coração". */
    private static final int SPURT_EVERY = 24;


    private ArmLoss() {
    }

    public static boolean isArmless(Player player) {
        return player.getPersistentData().getBoolean(KEY);
    }

    /** Dá pra arrancar? (criativo e espectador não perdem nada) */
    public static boolean canLoseArm(Player player) {
        return !isArmless(player) && !player.isCreative() && !player.isSpectator() && player.isAlive();
    }

    /** Arranca o braço esquerdo agora. */
    public static void removeArm(ServerPlayer player) {
        if (!canLoseArm(player)) return;
        player.getPersistentData().putBoolean(KEY, true);
        applyHealthLoss(player);
        dropOffhand(player);
        ModNetwork.sendArmState(player, true);

        if (player.level() instanceof ServerLevel server) {
            Vec3 at = shoulder(player);
            // só pedaços (cubinhos vermelhos, como no "Eating a Zebra"), nada de névoa/pó:
            // o jato voa pra longe da câmera de quem perdeu o braço
            BloodFx.spray(server, at, side(player).add(0, 0.8D, 0), 55, 0.42F);
            BloodFx.spray(server, at, new Vec3(0, -1, 0), 12, 0.08F);
            BloodFx.pool(server, player.position(), 1.2F);
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.RIP.get(), SoundSource.HOSTILE, 1.6F, 0.9F);
        }
    }

    /** Chamado todo tick do jogador no servidor. */
    public static void tick(ServerPlayer player) {
        if (!isArmless(player) || !player.isAlive()) return;
        applyHealthLoss(player);
        dropOffhand(player);
        bleed(player);
    }

    /** A vida máxima menor (o modificador não é salvo; é recolocado aqui). */
    private static void applyHealthLoss(ServerPlayer player) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max == null || max.getModifier(HEALTH_ID) != null) return;
        max.addTransientModifier(new AttributeModifier(HEALTH_ID, "froggydude_armless",
                -HEALTH_LOSS, AttributeModifier.Operation.ADDITION));
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Sem mão esquerda: o que estiver na mão secundária vai pro inventário (ou cai no chão). */
    private static void dropOffhand(ServerPlayer player) {
        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return;
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (!player.getInventory().add(off)) {
            player.drop(off, false);
        }
    }

    private static void bleed(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel server)) return;
        Vec3 at = shoulder(player);
        int t = player.tickCount;

        if (t % 4 == 0) {
            // gotas que escorrem do ombro e caem no chão
            BloodFx.spray(server, at.add(0, -0.1D, 0), new Vec3(0, -1, 0), 1, 0.04F);
        }
        if (t % SPURT_EVERY == 0) {
            // esguicho pra fora do ombro, no ritmo do coração
            Vec3 out = side(player).scale(0.25D);
            BloodFx.spray(server, at.add(out), side(player).add(0, 0.4D, 0), 10, 0.2F);
        }
        if (t % 30 == 0 && player.onGround()) {
            // rastro: cada parada vira uma poça que vai engrossando
            BloodFx.pool(server, at.add(side(player).scale(0.15D)), 0.3F);
        }
        if (t % BLEED_DAMAGE_EVERY == 0 && player.getHealth() > BLEED_FLOOR + 0.5F) {
            player.hurt(player.damageSources().magic(), 1.0F);
        }
    }

    /** Pra fora do lado esquerdo do corpo. */
    private static Vec3 side(Player player) {
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
    }

    /** Onde ficava o ombro esquerdo (de pé, agachado ou deitado). */
    public static Vec3 shoulder(Player player) {
        Vec3 left = side(player).scale(0.36D);
        Pose pose = player.getPose();
        if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SLEEPING) {
            float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw)).scale(0.45D);
            return player.position().add(left).add(fwd).add(0.0D, 0.3D, 0.0D);
        }
        double h = pose == Pose.CROUCHING ? 1.05D : 1.32D;
        return player.position().add(left).add(0.0D, h, 0.0D);
    }
}
