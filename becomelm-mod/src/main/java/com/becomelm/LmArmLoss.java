package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
import org.joml.Vector3f;

import java.util.UUID;

/**
 * O braço esquerdo arrancado (lado do servidor), igual ao mod da IA (player.ArmLoss), escolha do LM em 17/11:
 * sem o braço, 3 corações a menos de vida máxima, sem a mão secundária e sangrando pelo ombro (tira 1 de vida a cada
 * 8 s, mas sozinho não mata: para em 3 corações). O braço só volta quando o jogador morre (a marca fica nos dados do
 * jogador, que não passam pra o jogador novo).
 */
public final class LmArmLoss {

    private static final String KEY = BecomeLM.MOD_ID + ".armless";
    private static final UUID HEALTH_ID = UUID.fromString("6f1c2b7e-93a4-4d5e-8b21-7c0d3e9a4f18");
    private static final double HEALTH_LOSS = 6.0D;
    private static final float BLEED_FLOOR = 6.0F;
    private static final int BLEED_DAMAGE_EVERY = 160;
    private static final int SPURT_EVERY = 24;
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.1F);

    private LmArmLoss() {
    }

    public static boolean isArmless(Player player) {
        return player.getPersistentData().getBoolean(KEY);
    }

    /** Dá pra arrancar? (criativo e espectador não perdem nada) */
    public static boolean canLoseArm(Player player) {
        return !isArmless(player) && !player.isCreative() && !player.isSpectator() && player.isAlive();
    }

    /** Arranca o braço esquerdo agora (com o jato de sangue e o som do rasgo). */
    public static void removeArm(ServerPlayer player) {
        if (!canLoseArm(player)) return;
        player.getPersistentData().putBoolean(KEY, true);
        applyHealthLoss(player);
        dropOffhand(player);
        LmNetwork.sendArm(player, true);
        if (player.level() instanceof ServerLevel server) {
            Vec3 at = shoulder(player);
            Vec3 out = side(player);
            server.sendParticles(RED, at.x + out.x * 0.2D, at.y + 0.2D, at.z + out.z * 0.2D, 60, 0.25D, 0.35D, 0.25D, 0.1D);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH,
                    SoundSource.PLAYERS, 1.6F, 0.6F);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), BecomeLM.CRACK.get(), SoundSource.PLAYERS,
                    1.4F, 0.8F);
        }
    }

    /** Todo tick do jogador no servidor. */
    public static void tick(ServerPlayer player) {
        if (!isArmless(player) || !player.isAlive()) return;
        applyHealthLoss(player);
        dropOffhand(player);
        bleed(player);
    }

    /**
     * O jogo troca o objeto do jogador (morreu, ou saiu do End pelo portal). Só a morte devolve o braço: saindo do End a
     * marca passa pro jogador novo (o Forge não copia os dados soltos sozinho).
     */
    public static void cloned(Player from, Player to, boolean death) {
        if (!death && isArmless(from)) to.getPersistentData().putBoolean(KEY, true);
    }

    /** Morreu: o jogador novo nasce com os dois braços (quem vê fica sabendo). */
    public static void respawned(ServerPlayer player) {
        LmNetwork.sendArm(player, isArmless(player));
    }

    private static void applyHealthLoss(ServerPlayer player) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max == null || max.getModifier(HEALTH_ID) != null) return;
        max.addTransientModifier(new AttributeModifier(HEALTH_ID, "becomelm_armless", -HEALTH_LOSS,
                AttributeModifier.Operation.ADDITION));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void dropOffhand(ServerPlayer player) {
        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return;
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (!player.getInventory().add(off)) player.drop(off, false);
    }

    private static void bleed(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel server)) return;
        Vec3 at = shoulder(player);
        int t = player.tickCount;
        if (t % 4 == 0) server.sendParticles(RED, at.x, at.y - 0.1D, at.z, 1, 0.02D, 0.0D, 0.02D, 0.0D);
        if (t % SPURT_EVERY == 0) {
            Vec3 out = side(player).scale(0.25D);
            server.sendParticles(RED, at.x + out.x, at.y + 0.05D, at.z + out.z, 9, 0.08D, 0.1D, 0.08D, 0.05D);
        }
        if (t % BLEED_DAMAGE_EVERY == 0 && player.getHealth() > BLEED_FLOOR + 0.5F) {
            player.hurt(player.damageSources().magic(), 1.0F);
        }
    }

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
