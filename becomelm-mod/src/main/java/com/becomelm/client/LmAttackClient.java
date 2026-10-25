package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmAttack;
import com.becomelm.LmAttacks;
import com.becomelm.LmMode;
import com.becomelm.LmModes;
import com.becomelm.LmMove;
import com.becomelm.network.LmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Os golpes do lado de quem ataca: o "atacar" vira o golpe certo pro momento (galopando
 * com um bicho na frente = bote), o corpo sai na hora (sem esperar o servidor) e o
 * servidor confere e dá o dano. E o barulho e a poeira quando acerta, pra todo mundo.
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmAttackClient {

    /** Cone do bote (cosseno do ângulo com o olhar, só na horizontal): ~37° pra cada lado. */
    private static final double CONE = 0.8D;
    /** Pulinho do bote (o FroggyDude sai baixo, quase rente). */
    private static final double POUNCE_UP = 0.34D;
    private static final int COOLDOWN = 10;

    private static int lastAttack = -100;

    private LmAttackClient() {
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || event.isCanceled()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || !LmClientEvents.serverHasMod()) return;
        if (LmModes.get(p) != LmMode.GALLOP || LmKnockClient.lying(p) || p.isSpectator()) return;
        LivingEntity target = pickTarget(mc, p, LmAttacks.POUNCE_RANGE);
        if (target == null) return;
        // é bote, não soco: sem o golpe do vanilla (nem o braço balançando)
        event.setCanceled(true);
        event.setSwingHand(false);
        int now = LmClientEvents.ticks();
        if (now - lastAttack < COOLDOWN) return;
        lastAttack = now;
        pounce(p, target);
    }

    /** O bicho na mira; se não tiver, o melhor no cone da frente (mais no meio e mais perto). */
    @Nullable
    static LivingEntity pickTarget(Minecraft mc, LocalPlayer p, double range) {
        if (mc.crosshairPickEntity instanceof LivingEntity le && LmAttacks.canTarget(p, le)
                && p.distanceTo(le) <= range) {
            return le;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1F);
        Vec3 lookH = new Vec3(look.x, 0D, look.z);
        if (lookH.lengthSqr() < 1.0E-4D) return null;
        lookH = lookH.normalize();
        AABB box = p.getBoundingBox().inflate(range);
        LivingEntity best = null;
        double bestScore = -1.0E9D;
        for (Entity e : p.level().getEntities(p, box, x -> x instanceof LivingEntity)) {
            LivingEntity le = (LivingEntity) e;
            if (!LmAttacks.canTarget(p, le)) continue;
            Vec3 to = le.position().add(0D, le.getBbHeight() * 0.5D, 0D).subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.05D || Math.abs(to.y) > 3.5D) continue;
            Vec3 toH = new Vec3(to.x, 0D, to.z);
            double dot = toH.lengthSqr() < 1.0E-4D ? 1D : toH.normalize().dot(lookH);
            if (dot < CONE || !p.hasLineOfSight(le)) continue;
            double score = dot * 2D - dist / range;
            if (score > bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    /**
     * Se joga no bicho: a velocidade é a que chega lá em ~8 ticks (o ar freia 9% por tick:
     * a distância percorrida é ~5,9x a velocidade inicial).
     */
    private static void pounce(LocalPlayer p, LivingEntity target) {
        Vec3 d = target.position().subtract(p.position());
        Vec3 dirH = new Vec3(d.x, 0D, d.z);
        double dist = dirH.length();
        dirH = dist < 1.0E-3D ? Vec3.directionFromRotation(0F, p.getYRot()) : dirH.scale(1D / dist);
        double v = Mth.clamp(dist / 5.9D + 0.12D, 0.4D, 1.3D);
        p.setDeltaMovement(dirH.x * v, Math.max(p.getDeltaMovement().y, POUNCE_UP), dirH.z * v);
        p.hasImpulse = true;
        // o corpo vira pro bicho (a câmera continua onde a pessoa olha)
        p.setYBodyRot((float) (Mth.atan2(dirH.z, dirH.x) * Mth.RAD_TO_DEG) - 90F);
        LmMoves.local(p, LmMove.POUNCE);
        LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.POUNCE, target.getId()));
    }

    /** Chegou do servidor: o golpe encostou. */
    public static void hit(int attackerId, int targetId, LmAttack kind, boolean hurt) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity a = level.getEntity(attackerId);
        Entity t = level.getEntity(targetId);
        if (t != null) impact(level, t, a);
        if (a == mc.player && a != null) {
            // bateu e passa por cima (no vídeo ele atravessa o Grox): perde um pouco do embalo
            // e sobe o suficiente pra não ficar enfiado na cabeça de quem caiu
            Vec3 v = a.getDeltaMovement();
            a.setDeltaMovement(v.x * 0.6D, Math.max(v.y, 0.32D), v.z * 0.6D);
        }
    }

    /** A pancada: o som, poeira em roda e terra voando do chão embaixo da vítima. */
    private static void impact(ClientLevel level, Entity t, @Nullable Entity a) {
        var rnd = level.getRandom();
        level.playLocalSound(t.getX(), t.getY(), t.getZ(), BecomeLM.POUNCE_HIT.get(), SoundSource.PLAYERS,
                1.0F, 0.9F + rnd.nextFloat() * 0.2F, false);
        for (int i = 0; i < 14; i++) {
            double ang = i / 14D * Math.PI * 2D;
            double s = 0.08D + rnd.nextDouble() * 0.06D;
            level.addParticle(BecomeLM.DUST_PUFF.get(), t.getX() + Math.cos(ang) * 0.4D, t.getY() + 0.1D,
                    t.getZ() + Math.sin(ang) * 0.4D, Math.cos(ang) * s, 0.02D, Math.sin(ang) * s);
        }
        BlockState below = t.getBlockStateOn();
        if (below.isAir()) return;
        // câmera de cinema colada na vítima: a terra na lente vira quadrado marrom
        if (LmCamera.active() && a == Minecraft.getInstance().player) return;
        BlockParticleOption crumbs = new BlockParticleOption(ParticleTypes.BLOCK, below);
        for (int i = 0; i < 10; i++) {
            level.addParticle(crumbs, t.getX() + (rnd.nextDouble() - 0.5D) * 0.8D, t.getY() + 0.1D,
                    t.getZ() + (rnd.nextDouble() - 0.5D) * 0.8D,
                    (rnd.nextDouble() - 0.5D) * 0.3D, 0.25D, (rnd.nextDouble() - 0.5D) * 0.3D);
        }
    }
}
