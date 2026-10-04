package com.froggydude.player;

import com.froggydude.init.ModSounds;
import com.froggydude.network.ModNetwork;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

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

    private static final DustParticleOptions BLOOD =
            new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.1F);
    private static final BlockParticleOption DROP =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());

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
            server.sendParticles(BLOOD, at.x, at.y, at.z, 40, 0.25D, 0.25D, 0.25D, 0.0D);
            server.sendParticles(DROP, at.x, at.y, at.z, 25, 0.2D, 0.2D, 0.2D, 0.25D);
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

        if (t % 2 == 0) {
            server.sendParticles(BLOOD, at.x, at.y, at.z, 2, 0.06D, 0.06D, 0.06D, 0.0D);
        }
        if (t % 5 == 0) {
            // gotas que caem e espirram no chão
            server.sendParticles(DROP, at.x, at.y - 0.1D, at.z, 1, 0.04D, 0.0D, 0.04D, 0.02D);
        }
        if (t % SPURT_EVERY == 0) {
            // esguicho pra fora do ombro, no ritmo do coração
            Vec3 out = side(player).scale(0.25D);
            server.sendParticles(BLOOD, at.x + out.x, at.y, at.z + out.z, 8, 0.12D, 0.1D, 0.12D, 0.0D);
            server.sendParticles(DROP, at.x, at.y, at.z, 3, 0.05D, 0.05D, 0.05D, 0.18D);
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
