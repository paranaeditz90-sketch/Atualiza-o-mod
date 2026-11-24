package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmFinish;
import com.becomelm.LmKnock;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * A câmera da finalização, pra quem está finalizando (escolha do LM: automática, com planos).
 * Corta de plano em plano no ritmo da cena, cada um com um empurrãozinho de câmera; sem HUD
 * e sem mão (o LmCamera esconde enquanto isto dirige).
 *
 * Os planos (ticks): 0 aberto de lado (monta) · 8 por cima do ombro dele (cava) · 38 da cabeça
 * dela olhando a cara dele (cava) · 68 de cima, na barriga (abre) · 86 de lado, perto (come) ·
 * 128 da cabeça dela de novo (come) · 166 girando em volta (come) · 196 na cara dele (encara).
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmFinishClient {

    private enum Shot {
        WIDE(0), SHOULDER(8), FROM_HEAD(38), TOP(68), SIDE(86), FROM_HEAD_CLOSE(128), ORBIT(166), FACE(196);

        final int from;

        Shot(int from) {
            this.from = from;
        }

        static Shot at(int age) {
            Shot[] all = values();
            for (int i = all.length - 1; i > 0; i--) {
                if (age >= all[i].from) return all[i];
            }
            return WIDE;
        }

        int length() {
            Shot[] all = values();
            return (ordinal() + 1 < all.length ? all[ordinal() + 1].from : LmFinish.TICKS) - from;
        }
    }

    private static final Vec3 UP = new Vec3(0D, 1D, 0D);
    /** Depois que ela morre: mais 1,5 s de câmera aberta subindo, pro corpo quebrar na tela. */
    private static final int AFTER = 30;
    private static final boolean DEBUG = Boolean.getBoolean("becomelm.debug");

    /** O último quadro da cena (a barriga, pra onde a cabeça dela deitou e o lado): o plano do fim usa. */
    @Nullable
    private static Vec3 lastBelly;
    private static Vec3 lastD = new Vec3(0D, 0D, 1D);
    private static int afterStart = -1;
    @Nullable
    private static Shot lastShot;

    private LmFinishClient() {
    }

    /** A câmera agora é a da cena (o próprio jogador finalizando alguém, ou o plano do fim). */
    static boolean directing() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.getCameraEntity() != p) return false;
        if (LmActsClient.finishing(p) != null && victim(p) != null) return true;
        return after(0F) >= 0F;
    }

    /** Eu sou a vítima de uma finalização (vejo dos meus olhos, sem HUD). */
    static boolean victimView() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && LmActsClient.finisher(p) != null;
    }

    /** O nome flutuando em cima da vítima estraga o plano. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !directing()) return;
        LmActsClient.Act a = LmActsClient.finishing(p);
        if (a != null && event.getEntity().getId() == a.target) event.setResult(Event.Result.DENY);
    }

    /** A vítima vê mais aberto (ele está em cima da cara dela). */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov() && victimView()) event.setFOV(event.getFOV() * 1.25D);
    }

    /** A cena do próprio jogador acabou (chegou do servidor): se foi até o fim, segura o plano do fim. */
    static void ended(int age) {
        afterStart = age >= LmFinish.TICKS - 5 && lastBelly != null ? LmClientEvents.ticks() : -1;
        lastShot = null;
    }

    /** Quanto do plano do fim já passou (0 a 1), ou -1 se não está nele. */
    private static float after(float partial) {
        if (afterStart < 0) return -1F;
        float t = (LmClientEvents.ticks() - afterStart + partial) / AFTER;
        if (t > 1F) {
            afterStart = -1;
            return -1F;
        }
        return Math.max(0F, t);
    }

    @Nullable
    private static LivingEntity victim(LocalPlayer p) {
        LmActsClient.Act a = LmActsClient.finishing(p);
        if (a == null || p.level() == null) return null;
        return p.level().getEntity(a.target) instanceof LivingEntity l ? l : null;
    }

    /** Depois do LmCamera (que também pode estar ligado): a cena manda. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Camera cam = event.getCamera();
        if (p == null || cam.getEntity() != p) return;
        float partial = (float) event.getPartialTick();
        LmActsClient.Act a = LmActsClient.finishing(p);
        LivingEntity v = victim(p);
        if (a == null || v == null) {
            float t = after(partial);
            if (t >= 0F && lastBelly != null) {
                // o corpo estourou: a câmera abre e sobe devagar em cima do monte
                float k = t * t * (3F - 2F * t);
                Vec3 r = new Vec3(-lastD.z, 0D, lastD.x);
                Vec3 pos = lastBelly.add(r.scale(2.6D + 0.6D * k)).add(UP.scale(1.3D + 0.9D * k)).subtract(lastD.scale(0.6D));
                aim(event, p, pos, lastBelly);
            }
            return;
        }
        float age = Math.min(a.age + partial, LmFinish.TICKS);
        Shot shot = Shot.at((int) age);
        float k = Mth.clamp((age - shot.from) / shot.length(), 0F, 1F);
        k = k * k * (3F - 2F * k);
        if (DEBUG && shot != lastShot) {
            lastShot = shot;
            LmHud.toast(net.minecraft.network.chat.Component.literal("plano " + shot + " t=" + (int) age));
        }

        // a vítima: os pés, pra onde a cabeça deitou (d) e o lado (r); a barriga fica a ~1 bloco dos pés
        LmKnock.State ks = LmKnockClient.get(v);
        Vec3 feet = v.getPosition(partial);
        Vec3 d = ks != null ? LmKnockClient.headDir(ks, ks.yaw) : Vec3.directionFromRotation(0F, v.getYRot());
        d = new Vec3(d.x, 0D, d.z).normalize();
        Vec3 r = new Vec3(-d.z, 0D, d.x);
        Vec3 belly = feet.add(d.scale(1.05D)).add(0D, v.getBbWidth() * 0.3D + 0.15D, 0D);
        lastBelly = belly;
        lastD = d;
        Vec3 lmFeet = p.getPosition(partial);
        Vec3 head = LmRenderer.localHead != null && LmCamera.frame() - LmRenderer.localHeadFrame <= 2
                ? lmFeet.add(LmRenderer.localHead).add(0D, 0.15D, 0D)
                : lmFeet.add(d.scale(0.35D)).add(0D, 1.0D, 0D);

        Vec3 pos;
        Vec3 look;
        switch (shot) {
            case WIDE -> {
                pos = belly.add(r.scale(3.4D)).add(UP.scale(1.3D)).add(d.scale(-0.4D + 0.6D * k));
                look = belly.add(UP.scale(0.5D));
            }
            case SHOULDER -> {
                pos = lmFeet.add(d.scale(-0.7D + 0.2D * k)).add(UP.scale(2.1D - 0.15D * k)).add(r.scale(1.3D));
                look = belly.add(UP.scale(0.1D));
            }
            case FROM_HEAD -> {
                // por cima da cabeça dela (de baixo, a cabeça tapava ele)
                pos = feet.add(d.scale(2.7D - 0.3D * k)).add(UP.scale(0.95D)).add(r.scale(0.5D));
                look = head.subtract(UP.scale(0.25D));
            }
            case TOP -> {
                // de cima, mas de lado: o corpo dele cobre a barriga se for reto de cima
                pos = belly.add(UP.scale(2.2D - 0.4D * k)).add(r.scale(1.1D)).add(d.scale(0.3D));
                look = belly;
            }
            case SIDE -> {
                pos = belly.add(r.scale(2.0D - 0.25D * k)).add(UP.scale(0.95D)).subtract(d.scale(0.25D));
                look = belly.add(head).scale(0.5D);
            }
            case FROM_HEAD_CLOSE -> {
                pos = feet.add(d.scale(2.35D - 0.2D * k)).add(UP.scale(1.0D)).subtract(r.scale(0.4D));
                look = head.subtract(UP.scale(0.2D));
            }
            case ORBIT -> {
                // do lado até a frente dele (pro lado da cabeça dela): de costas ele tapa tudo
                float ang = (20F + 80F * k) * Mth.DEG_TO_RAD;
                Vec3 around = r.scale(Mth.cos(ang)).add(d.scale(Mth.sin(ang)));
                pos = belly.add(around.scale(3.0D)).add(UP.scale(1.5D));
                look = belly.add(UP.scale(0.3D));
            }
            default -> {
                // ele olha pra cabeça dela (d): a câmera fica na frente da cara dele
                pos = head.add(d.scale(1.3D - 0.3D * k)).add(UP.scale(0.08D));
                look = head;
            }
        }
        aim(event, p, pos, look);
    }

    private static void aim(ViewportEvent.ComputeCameraAngles event, LocalPlayer p, Vec3 pos, Vec3 look) {
        pos = LmCamera.clip(p, look, pos);
        Vec3 to = look.subtract(pos);
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        Camera cam = event.getCamera();
        cam.setPosition(pos);
        cam.detached = true;
        event.setYaw((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F);
        event.setPitch((float) -(Mth.atan2(to.y, flat) * Mth.RAD_TO_DEG));
        event.setRoll(0F);
    }
}
