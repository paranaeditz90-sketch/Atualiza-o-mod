package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmAttack;
import com.becomelm.LmAttacks;
import com.becomelm.LmKnock;
import com.becomelm.LmMode;
import com.becomelm.LmModes;
import com.becomelm.LmMove;
import com.becomelm.client.pieces.BodyPieces;
import com.becomelm.network.LmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Os golpes do lado de quem ataca: o "atacar" vira o golpe certo pro momento (galopando
 * com um bicho na frente = bote; bem perto = morde e segura na boca), o corpo sai na hora
 * (sem esperar o servidor) e o servidor confere e dá o dano. E o barulho e a poeira quando
 * acerta, pra todo mundo.
 *
 * Pulo alto: descendo, ele vai na direção do bicho que estiver embaixo (dá pra mirar
 * olhando pra ele); caiu perto = monta, soca e morde.
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmAttackClient {

    /** Cone do bote (cosseno do ângulo com o olhar, só na horizontal): ~37° pra cada lado. */
    private static final double CONE = 0.8D;
    /** Pulinho do bote (o FroggyDude sai baixo, quase rente). */
    private static final double POUNCE_UP = 0.34D;
    private static final int COOLDOWN = 10;

    private static int lastAttack = -100;
    /** Martelando: o "soltou" já foi pro servidor. */
    private static boolean hammerStopped;
    /** O bicho que o pulo alto está mirando (gruda nele até pousar). */
    private static int skyTarget = -1;
    /** Até onde (na horizontal) o pulo alto procura alguém embaixo. */
    private static final double SKY_SEEK = 8.0D;
    /** O quanto dá pra corrigir no ar por tick (blocos). */
    private static final double SKY_STEER = 1.1D;

    private LmAttackClient() {
    }

    /** Descendo do pulo alto: corrige o rumo pra cair em cima do alvo. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused()) return;
        hammerTick(mc, p);
        if (LmMoves.airborne(p) != LmMove.SKY || p.onGround()) {
            if (LmMoves.airborne(p) != LmMove.SKY) skyTarget = -1;
            return;
        }
        Vec3 v = p.getDeltaMovement();
        // subindo ainda rápido: deixa subir
        if (v.y > 0.3D) return;
        LivingEntity t = mc.level.getEntity(skyTarget) instanceof LivingEntity l && l.isAlive() ? l : null;
        if (t == null) {
            t = pickBelow(mc, p);
            skyTarget = t == null ? -1 : t.getId();
        }
        if (t == null) return;
        double dy = p.getY() - t.getY();
        if (dy < -0.5D) return;
        int ticks = ticksToFall(v.y, Math.max(0D, dy));
        Vec3 to = new Vec3(t.getX() - p.getX(), 0D, t.getZ() - p.getZ());
        double dist = to.length();
        double want = Math.min(SKY_STEER, dist / Math.max(1, ticks));
        Vec3 h = dist < 1.0E-3D ? Vec3.ZERO : to.scale(want / dist);
        p.setDeltaMovement(h.x, v.y, h.z);
    }

    /** Quantos ticks pra cair dy blocos saindo com essa velocidade (a gravidade e o ar do jogo). */
    private static int ticksToFall(double vy, double dy) {
        double y = 0D;
        int n = 0;
        while (y < dy && n < 200) {
            vy = (vy - 0.08D) * 0.98D;
            y -= vy;
            n++;
        }
        return n;
    }

    /** O bicho embaixo (mais pro lado que se está olhando e mais perto do ponto de queda). */
    @Nullable
    private static LivingEntity pickBelow(Minecraft mc, LocalPlayer p) {
        Vec3 look = p.getViewVector(1F);
        Vec3 lookH = new Vec3(look.x, 0D, look.z);
        boolean down = look.y < -0.7D || lookH.lengthSqr() < 1.0E-4D;
        if (!down) lookH = lookH.normalize();
        LivingEntity best = null;
        double bestScore = -1.0E9D;
        AABB box = p.getBoundingBox().inflate(SKY_SEEK, 40D, SKY_SEEK);
        for (Entity e : p.level().getEntities(p, box, x -> x instanceof LivingEntity)) {
            LivingEntity le = (LivingEntity) e;
            if (!LmAttacks.canTarget(p, le) || le.getY() > p.getY() - 0.5D) continue;
            Vec3 to = new Vec3(le.getX() - p.getX(), 0D, le.getZ() - p.getZ());
            double dist = to.length();
            if (dist > SKY_SEEK) continue;
            double dot = down || dist < 1.0E-3D ? 1D : to.scale(1D / dist).dot(lookH);
            if (dot < 0.3D) continue;
            double score = dot * 2D - dist / SKY_SEEK;
            if (score > bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    /** Pousou do pulo alto: caiu perto de alguém = monta, soca e morde (longe de todos, pousa normal). */
    static void skyLanded(LocalPlayer p) {
        int id = skyTarget;
        skyTarget = -1;
        if (!LmClientEvents.serverHasMod() || LmKnockClient.lying(p) || LmTransformClient.busy(p)
                || LmActsClient.busy(p)) return;
        Minecraft mc = Minecraft.getInstance();
        LivingEntity t = mc.level != null && mc.level.getEntity(id) instanceof LivingEntity l && l.isAlive() ? l : null;
        if (t == null || !near(p, t)) {
            t = null;
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(LmAttacks.SMASH_REACH, 2D,
                    LmAttacks.SMASH_REACH), x -> x instanceof LivingEntity)) {
                if (e instanceof LivingEntity le && LmAttacks.canTarget(p, le) && near(p, le)) {
                    t = le;
                    break;
                }
            }
        }
        if (t == null) return;
        LmActsClient.startLocal(p, LmAttack.SMASH, t.getId());
        LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.SMASH, t.getId()));
    }

    private static boolean near(LocalPlayer p, LivingEntity t) {
        double dx = t.getX() - p.getX(), dz = t.getZ() - p.getZ();
        return dx * dx + dz * dz <= LmAttacks.SMASH_REACH * LmAttacks.SMASH_REACH && Math.abs(t.getY() - p.getY()) < 2.5D;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || event.isCanceled()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || !LmClientEvents.serverHasMod()) return;
        if (!LmModes.get(p).froggy() || LmKnockClient.lying(p) || LmTransformClient.busy(p) || LmActsClient.busy(p)
                || p.isSpectator()) return;
        if (LmModes.get(p) == LmMode.STAND) {
            // em pé: o bicho na mira = derruba de costas e martela com os dois braços enquanto segurar
            LivingEntity t = hammerTarget(mc, p);
            if (t != null) {
                event.setCanceled(true);
                event.setSwingHand(false);
                int now = LmClientEvents.ticks();
                if (now - lastAttack < COOLDOWN) return;
                lastAttack = now;
                hammerStopped = false;
                LmActsClient.startLocal(p, LmAttack.HAMMER, t.getId());
                LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.HAMMER, t.getId()));
                return;
            }
        }
        LivingEntity target = LmModes.get(p).fast() ? pickTarget(mc, p, LmAttacks.POUNCE_RANGE) : null;
        if (target == null) {
            // ninguém na mira e um monte de pedaços na frente: deita em cima
            if (tryPile(mc, p)) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            return;
        }
        // é bote, não soco: sem o golpe do vanilla (nem o braço balançando)
        event.setCanceled(true);
        event.setSwingHand(false);
        int now = LmClientEvents.ticks();
        if (now - lastAttack < COOLDOWN) return;
        lastAttack = now;
        if (p.distanceTo(target) <= LmAttacks.GRAB_RANGE + target.getBbWidth() * 0.5D && LmKnock.canKnock(target)) {
            grab(p, target);
        } else {
            pounce(p, target);
        }
    }

    /** Em pé: o bicho na mira, no alcance do soco, que dá pra derrubar. */
    @Nullable
    private static LivingEntity hammerTarget(Minecraft mc, LocalPlayer p) {
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity t)) return null;
        if (!t.isAlive() || !LmAttacks.canTarget(p, t) || !LmKnock.canKnock(t)) return null;
        return p.distanceTo(t) <= LmAttacks.HAMMER_REACH + t.getBbWidth() * 0.5D ? t : null;
    }

    /** Martelando: soltou o atacar (ou abriu um menu) = avisa o servidor, uma vez. */
    private static void hammerTick(Minecraft mc, LocalPlayer p) {
        LmActsClient.Act a = LmActsClient.get(p);
        if (a == null || a.kind != LmAttack.HAMMER) {
            hammerStopped = false;
            return;
        }
        if (!hammerStopped && (mc.screen != null || !mc.options.keyAttack.isDown())) {
            hammerStopped = true;
            LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.HAMMER_STOP, -1));
        }
    }

    /**
     * Deita de bruços no monte de pedaços na frente: o peito no meio do monte, virado pra ele
     * (a cara afunda nos cacos, como no 10v1 0:21 e no How many days 0:44).
     */
    private static boolean tryPile(Minecraft mc, LocalPlayer p) {
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) return false;
        Vec3 look = p.getViewVector(1F);
        Vec3 h = new Vec3(look.x, 0D, look.z);
        h = h.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0F, p.getYRot()) : h.normalize();
        Vec3 pile = BodyPieces.pileNear(p.position().add(h.scale(1.2D)), 1.8D);
        if (pile == null) return false;
        Vec3 to = new Vec3(pile.x - p.getX(), 0D, pile.z - p.getZ());
        double d = to.length();
        Vec3 dir = d < 1.0E-3D ? h : to.scale(1D / d);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F;
        // os pés ficam antes do monte (deitado, o peito fica em cima dele); sem atravessar parede
        Vec3 feet = new Vec3(pile.x, p.getY(), pile.z).subtract(dir.scale(LmActsClient.PILE_CHEST));
        AABB moved = p.getBoundingBox().move(feet.x - p.getX(), 0D, feet.z - p.getZ());
        if (p.level().noCollision(p, moved)) p.setPos(feet.x, p.getY(), feet.z);
        p.setYRot(yaw);
        p.yRotO = yaw;
        p.setYBodyRot(yaw);
        LmActsClient.startPile(p, yaw);
        LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.PILE, Math.round(Mth.wrapDegrees(yaw) * 10F)));
        return true;
    }

    /** Morde e segura na boca (o servidor confere; a pose sai na hora). */
    private static void grab(LocalPlayer p, LivingEntity target) {
        LmActsClient.startLocal(p, LmAttack.GRAB, target.getId());
        LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.GRAB, target.getId()));
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
    public static void hit(int attackerId, int targetId, LmAttack kind, boolean hurt, int n) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity a = level.getEntity(attackerId);
        Entity t = level.getEntity(targetId);
        if (kind == LmAttack.PUNCH || kind == LmAttack.BITE || kind == LmAttack.GRAB || kind == LmAttack.SHAKE) {
            if (t instanceof LivingEntity le) blow(level, le, kind, n);
            // as mordidas sujam a boca dele
            if (kind != LmAttack.PUNCH && a instanceof Player pa) LmBloodFace.bite(pa);
            return;
        }
        if (kind == LmAttack.THROW) {
            if (t != null) {
                level.playLocalSound(t.getX(), t.getY() + 0.5D, t.getZ(), BecomeLM.THROW.get(), SoundSource.PLAYERS,
                        1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F, false);
            }
            return;
        }
        if (t != null) impact(level, t, a);
        if (kind == LmAttack.POUNCE && a == mc.player && a != null) {
            // bateu e passa por cima (no vídeo ele atravessa o Grox): perde um pouco do embalo
            // e sobe o suficiente pra não ficar enfiado na cabeça de quem caiu
            Vec3 v = a.getDeltaMovement();
            a.setDeltaMovement(v.x * 0.6D, Math.max(v.y, 0.32D), v.z * 0.6D);
        }
    }

    /**
     * Um soco (os dois punhos se alternam: soco A, soco B) ou uma mordida, na cabeça da vítima
     * deitada: o som e uns farelos de poeira (e vermelho na mordida).
     */
    private static void blow(ClientLevel level, LivingEntity t, LmAttack kind, int n) {
        var rnd = level.getRandom();
        Vec3 head = LmKnockClient.eye(t, 1F);
        boolean bite = kind != LmAttack.PUNCH;
        var sound = bite ? BecomeLM.BITE.get() : (n % 2 == 0 ? BecomeLM.PUNCH_A.get() : BecomeLM.PUNCH_B.get());
        // a sacudida morde mais grave (é o tranco da cabeça)
        float pitch = (kind == LmAttack.SHAKE ? 0.8F : 0.95F) + rnd.nextFloat() * 0.1F;
        level.playLocalSound(head.x, head.y, head.z, sound, SoundSource.PLAYERS, 1.0F, pitch, false);
        boolean mine = LmCamera.active() && LmCamera.shot() == LmCamera.Shot.VICTIM;
        int count = mine ? 2 : 5;
        for (int i = 0; i < count; i++) {
            level.addParticle(BecomeLM.DUST_PUFF.get(), head.x + (rnd.nextDouble() - 0.5D) * 0.5D, head.y,
                    head.z + (rnd.nextDouble() - 0.5D) * 0.5D, (rnd.nextDouble() - 0.5D) * 0.06D, 0.02D,
                    (rnd.nextDouble() - 0.5D) * 0.06D);
        }
        if (bite) {
            DustParticleOptions red = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.2F);
            for (int i = 0; i < (mine ? 3 : 8); i++) {
                level.addParticle(red, head.x + (rnd.nextDouble() - 0.5D) * 0.4D, head.y + rnd.nextDouble() * 0.2D,
                        head.z + (rnd.nextDouble() - 0.5D) * 0.4D, 0D, 0.05D, 0D);
            }
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
