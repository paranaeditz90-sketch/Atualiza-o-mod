package com.froggydude.client.cutscene;

import com.froggydude.entity.Finisher;
import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Quaternionf;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * A vítima do golpe final, pra todo mundo que está vendo: deitada de costas
 * no lugar onde caiu (cabeça pra longe dele), sem nome em cima, e os braços e
 * a cabeça somem no tick em que saem (os tocos são do ArmlessRendering).
 *
 * O corpo deitado: o modelo em pé virado pra -H é tombado pra trás em volta
 * do eixo do lado esquerdo dela, então a cabeça vai pra H, a cara pro céu e o
 * lado esquerdo continua em L (igual à geometria de entity.Finisher).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VictimRender {

    public static final int NORMAL = 0;
    public static final int LYING = 1;
    /** Já virou o cadáver do Extremo (a entidade do corpo é que aparece). */
    public static final int HIDDEN = 2;

    private static final class Saved {
        float bodyRot, bodyRotO, headRot, headRotO, xRot, xRotO, swim, swimO, walkPos, walkSpeed, walkSpeedO;
        int deathTime;
    }

    /** WalkAnimationState.position / speed / speedOld: as pernas e o braço chutando ao se debater. */
    private static final Field WALK_POS = ObfuscationReflectionHelper.findField(WalkAnimationState.class, "f_267358_");
    private static final Field WALK_SPEED = ObfuscationReflectionHelper.findField(WalkAnimationState.class, "f_267371_");
    private static final Field WALK_SPEED_O = ObfuscationReflectionHelper.findField(WalkAnimationState.class, "f_267406_");

    /**
     * Quanto ela se debate agora (0 a 1). Igual à zebra do vídeo: da mordida até arrancar,
     * forte (a cabeça virando de um lado pro outro, trancos no corpo, as pernas chutando);
     * depois de arrancar, vai morrendo em espasmos; antes da primeira mordida, só tenta se soltar.
     */
    static float thrash(float t, boolean skipLeft) {
        float a = t < Finisher.BEND_L ? 0.3F : 0.15F;
        if (!skipLeft) a = Math.max(a, bout(t, Finisher.BITE_L, Finisher.RIP_L));
        a = Math.max(a, bout(t, Finisher.BITE_R, Finisher.RIP_R));
        a = Math.max(a, bout(t, Finisher.BITE_H, Finisher.RIP_H));
        if (t >= Finisher.RIP_H) a = Math.max(0F, 0.5F * (1F - (t - Finisher.RIP_H) / 50F));
        return a;
    }

    private static float bout(float t, int bite, int rip) {
        if (t < bite - 3 || t > rip + 50) return 0F;
        if (t < bite) return (t - (bite - 3)) / 3F;
        if (t <= rip + 6) return 1F;
        return 0.3F + 0.7F * (1F - (t - rip - 6) / 44F);
    }

    /** LivingEntity.swimAmount / swimAmountO: quem estava preso no chão (pose de nado) ainda "nada" uns ticks. */
    private static final Field SWIM = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20931_");
    private static final Field SWIM_O = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20932_");

    private static final Map<Integer, Saved> PUSHED = new HashMap<>();

    private VictimRender() {
    }

    public static int state(Entity p) {
        ClientFinisher.Entry e = ClientFinisher.forVictim(p.getId());
        if (e == null) return NORMAL;
        if (!e.ended()) return LYING;
        if (e.corpse) return HIDDEN;
        // acabou e morreu: fica deitado sem cabeça até sumir; logo depois do fim, ainda esperando
        // o cadáver (Extremo) aparecer, também; se escapou viva (ele morreu no meio), levanta
        if (p instanceof Player pl && pl.isDeadOrDying()) return LYING;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null && level.getGameTime() - e.endedAt < 10 ? LYING : NORMAL;
    }

    /** Partes que já saíram dessa vítima (0 se não está na cutscene). */
    public static int missing(Entity p, float partial) {
        ClientFinisher.Entry e = ClientFinisher.forVictim(p.getId());
        if (e == null || state(p) == NORMAL) return 0;
        return ClientFinisher.missing(e, partial);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        int st = state(p);
        if (st == NORMAL) return;
        if (st == HIDDEN) {
            event.setCanceled(true);
            return;
        }
        ClientFinisher.Entry e = ClientFinisher.forVictim(p.getId());
        float partial = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        Vec3 pos = p.getPosition(partial);
        Vec3 h = e.h;
        float t = e.t(partial);
        float thr = (p.isDeadOrDying() || e.ended()) ? 0F : thrash(t, e.skipLeft);
        float time = p.level().getGameTime() + partial;
        ps.pushPose();
        // se debatendo: o corpo dá trancos (gira um pouco no próprio comprimento e pula)
        double lift = thr * 0.05D * Math.abs(Mth.sin(time * 1.9F));
        // pés em A - 0,9H, as costas encostando no chão
        ps.translate(e.anchor.x - pos.x, e.anchor.y - pos.y + 0.14D + lift, e.anchor.z - pos.z);
        ps.mulPose(new Quaternionf().rotationAxis(thr * 7F * Mth.DEG_TO_RAD * jitter(time, 2.3F), (float) h.x, 0F, (float) h.z));
        ps.translate(-h.x * 0.9D, 0D, -h.z * 0.9D);
        ps.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, (float) h.x, 0F, (float) h.z));

        Saved s = new Saved();
        s.bodyRot = p.yBodyRot;
        s.bodyRotO = p.yBodyRotO;
        s.headRot = p.yHeadRot;
        s.headRotO = p.yHeadRotO;
        s.xRot = p.getXRot();
        s.xRotO = p.xRotO;
        s.deathTime = p.deathTime;
        s.swim = getF(SWIM, p);
        s.swimO = getF(SWIM_O, p);
        s.walkPos = getF(WALK_POS, p.walkAnimation);
        s.walkSpeed = getF(WALK_SPEED, p.walkAnimation);
        s.walkSpeedO = getF(WALK_SPEED_O, p.walkAnimation);
        PUSHED.put(p.getId(), s);
        setF(SWIM, p, 0F);
        setF(SWIM_O, p, 0F);
        float face = e.yaw + 180F;
        p.yBodyRot = p.yBodyRotO = face;
        // a cabeça virando de um lado pro outro (a zebra, 81,2-83 s: ~a cada 0,2 s), o queixo mexendo
        p.yHeadRot = p.yHeadRotO = face + thr * 55F * jitter(time, 1.3F);
        p.setXRot(thr * 14F * jitter(time + 7F, 1.7F));
        p.xRotO = p.getXRot();
        // pernas e o braço que sobrou chutando/batendo (o balanço de andar, rápido e forte)
        setF(WALK_POS, p.walkAnimation, time * 1.15F);
        setF(WALK_SPEED, p.walkAnimation, thr * 0.85F);
        setF(WALK_SPEED_O, p.walkAnimation, thr * 0.85F);
        // sem o tombo de lado da morte (ela já está deitada)
        p.deathTime = 0;

        int miss = ClientFinisher.missing(e, partial);
        PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
        m.crouching = false;
        if ((miss & 1) != 0) {
            m.leftArm.visible = false;
            m.leftSleeve.visible = false;
        }
        if ((miss & 2) != 0) {
            m.rightArm.visible = false;
            m.rightSleeve.visible = false;
        }
        if ((miss & 4) != 0) {
            m.head.visible = false;
            m.hat.visible = false;
            part(EAR, m, false);
        }
        // a capa embaixo das costas atravessava o chão
        part(CLOAK, m, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        Saved s = PUSHED.remove(p.getId());
        if (s == null) return;
        event.getPoseStack().popPose();
        p.yBodyRot = s.bodyRot;
        p.yBodyRotO = s.bodyRotO;
        p.yHeadRot = s.headRot;
        p.yHeadRotO = s.headRotO;
        p.setXRot(s.xRot);
        p.xRotO = s.xRotO;
        p.deathTime = s.deathTime;
        setF(SWIM, p, s.swim);
        setF(SWIM_O, p, s.swimO);
        setF(WALK_POS, p.walkAnimation, s.walkPos);
        setF(WALK_SPEED, p.walkAnimation, s.walkSpeed);
        setF(WALK_SPEED_O, p.walkAnimation, s.walkSpeedO);
        PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
        m.rightArm.visible = true;
        m.rightSleeve.visible = p.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        m.head.visible = true;
        m.hat.visible = p.isModelPartShown(PlayerModelPart.HAT);
        part(EAR, m, true);
        part(CLOAK, m, true);
        // o braço esquerdo quem devolve é o ArmlessRendering (se ela estiver sem ele de verdade, continua escondido)
        m.leftArm.visible = true;
        m.leftSleeve.visible = p.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
    }

    /** PlayerModel.cloak / ear são privados. */
    private static final Field CLOAK = ObfuscationReflectionHelper.findField(PlayerModel.class, "f_103373_");
    private static final Field EAR = ObfuscationReflectionHelper.findField(PlayerModel.class, "f_103379_");

    private static void part(Field f, PlayerModel<?> m, boolean visible) {
        try {
            ((ModelPart) f.get(m)).visible = visible;
        } catch (IllegalAccessException | ClassCastException ignored) {
        }
    }

    /** Vai e volta irregular (-1 a 1), sem ficar com cara de pêndulo. */
    private static float jitter(float time, float speed) {
        return Mth.sin(time * speed) * 0.7F + Mth.sin(time * speed * 2.27F + 1.3F) * 0.3F;
    }

    private static float getF(Field f, Object o) {
        try {
            return f.getFloat(o);
        } catch (IllegalAccessException ex) {
            return 0F;
        }
    }

    private static void setF(Field f, Object o, float v) {
        try {
            f.setFloat(o, v);
        } catch (IllegalAccessException ignored) {
        }
    }

    /** Nome em cima de um corpo deitado sem cabeça: não. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (state(event.getEntity()) != NORMAL) event.setResult(Event.Result.DENY);
    }
}
