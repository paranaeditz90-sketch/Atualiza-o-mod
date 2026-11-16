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

    private static final class Saved {
        float bodyRot, bodyRotO, headRot, headRotO, xRot, xRotO, swim, swimO, walkPos, walkSpeed, walkSpeedO;
        int deathTime;
    }

    /** WalkAnimationState.position / speed / speedOld: as pernas e o braço chutando ao se debater. */
    static final Field WALK_POS = ObfuscationReflectionHelper.findField(WalkAnimationState.class, "f_267358_");
    static final Field WALK_SPEED = ObfuscationReflectionHelper.findField(WalkAnimationState.class, "f_267371_");
    static final Field WALK_SPEED_O = ObfuscationReflectionHelper.findField(WalkAnimationState.class, "f_267406_");

    /**
     * (v0.7.37, LM 16/11: "o jogador não se mexe, tipo, não fica igual a zebra se debatendo. Só fica parado [...]
     * Repare extremamente bem como a zebra se move") Como a vítima se debate neste instante da cena, medido na zebra
     * do "Eating a Zebra" (60 quadros por segundo, com a câmera descontada):
     * - a CABEÇA vai de um lado pro outro SEM PARAR, do começo até ele morder a cabeça (nem na pausa ela sossega):
     *   ~2,4 vezes por segundo antes da 1ª mordida, ~3,2 enquanto ele come o 1º pedaço e ~2,75 daí em diante; vira
     *   uns 90° de ponta a ponta (de cara pra câmera a de lado), demorando um instante em cada ponta, e o queixo
     *   sobe e desce junto;
     * - o CORPO só dá tranquinhos no mesmo compasso (o pedaço dela no chão mexe ~1/4 do que a cabeça mexe);
     * - depois que a cabeça sai, espasmos que somem em 2,5 s.
     * Tudo no relógio da CENA (ticks desde o começo). Antes era o tempo do mundo: passando de ~2 h de jogo, a conta
     * do seno do jogo (tabela com índice inteiro) estourava e dava sempre zero - a vítima ficava parada pra sempre.
     */
    static final class Thrash {
        private static final float HZ = (float) (Math.PI * 2D / 20D);
        private static final float W_BEFORE = 2.4F * HZ, W_FIRST = 3.2F * HZ, W_REST = 2.75F * HZ;
        /** Graus pra cada lado (a zebra: ~90° de ponta a ponta). */
        private static final float HEAD_YAW = 45F, HEAD_PITCH = 10F, ROLL = 6F;
        /** Blocos: o corpo escorrega pro lado e dá um pulinho a cada virada. */
        private static final float SLIDE = 0.035F, LIFT = 0.03F;
        /** Pernas e o braço que sobrou acompanhando (balanço de andar; 0,2 = uns 16° pra cada lado). LM, vendo o
         *  vídeo com 0,4 (~32°): "Faça as pernas chutarem menos". */
        private static final float LIMBS = 0.2F;
        static final Thrash STILL = new Thrash(0F, 0F, 0F, 0F, 0F, 0F, 0F);

        final float headYaw, headPitch, roll, slide, lift, limbPos, limbSpeed;

        private Thrash(float headYaw, float headPitch, float roll, float slide, float lift, float limbPos, float limbSpeed) {
            this.headYaw = headYaw;
            this.headPitch = headPitch;
            this.roll = roll;
            this.slide = slide;
            this.lift = lift;
            this.limbPos = limbPos;
            this.limbSpeed = limbSpeed;
        }

        /** t = tick da tabela (Finisher), com a fração do quadro; still = morta ou a cena já acabou. */
        static Thrash at(float t, boolean still, float partial) {
            float force = still ? 0F : force(t);
            if (force <= 0F) return STILL;
            float ph = phase(t);
            // a força respira um pouco (a zebra não é um metrônomo perfeito)
            force *= 1F + 0.12F * (float) Math.sin(t * 0.21D);
            // com a cabeça dentro da boca dele (da mordida até arrancar) ela quase não vira; o corpo é que se sacode
            float head = t >= Finisher.BITE_H ? 0.3F : 1F;
            float speed = force * LIMBS;
            return new Thrash(force * head * HEAD_YAW * swing(ph), force * head * HEAD_PITCH * swing(ph + 1.2F),
                    force * ROLL * swing(ph - 0.6F), force * SLIDE * swing(ph - 0.3F),
                    force * LIFT * (float) Math.abs(Math.cos(ph)),
                    // o jogo desconta speed * (1 - fração) da posição: devolve, senão as pernas dão um soluço por tick
                    ph / 0.6662F + speed * (1F - partial), speed);
        }

        /** Quanto ela se debate (0 a 1): forte desde o começo, inteira da 1ª mordida em diante. */
        static float force(float t) {
            if (t >= Finisher.RIP_H) return Math.max(0F, 0.5F * (1F - (t - Finisher.RIP_H) / 50F));
            return t < Finisher.BITE_L ? 0.9F : 1F;
        }

        /** Quantas viradas já foram (em radianos), com o ritmo de cada trecho emendado. */
        static float phase(float t) {
            t = Math.max(t, 0F);
            float before = Math.min(t, Finisher.BITE_L);
            float first = Mth.clamp(t - Finisher.BITE_L, 0F, Finisher.CUT_1 - Finisher.BITE_L);
            float rest = Math.max(t - Finisher.CUT_1, 0F);
            return before * W_BEFORE + first * W_FIRST + rest * W_REST;
        }

        /** Vai e volta de -1 a 1 que demora nas pontas (a cabeça para um instante virada e volta num tranco). */
        static float swing(float phase) {
            double s = Math.sin(phase);
            return (float) (Math.signum(s) * Math.pow(Math.abs(s), 0.6D));
        }
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
        ClientFinisher.Entry e = ClientFinisher.forVictim(p.getId());
        float partial = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        Vec3 pos = p.getPosition(partial);
        Vec3 h = e.h;
        Thrash th = Thrash.at(e.t(partial), p.isDeadOrDying() || e.ended(), partial);
        ps.pushPose();
        // se debatendo: o corpo dá tranquinhos no compasso da cabeça (escorrega pro lado, gira um pouco no próprio
        // comprimento e pula). Pés em A - 0,9H, as costas encostando no chão
        ps.translate(e.anchor.x - pos.x + e.l.x * th.slide, e.anchor.y - pos.y + 0.14D + th.lift,
                e.anchor.z - pos.z + e.l.z * th.slide);
        ps.mulPose(new Quaternionf().rotationAxis(th.roll * Mth.DEG_TO_RAD, (float) h.x, 0F, (float) h.z));
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
        // a cabeça virando de um lado pro outro sem parar (a zebra), o queixo subindo e descendo junto
        p.yHeadRot = p.yHeadRotO = face + th.headYaw;
        p.setXRot(th.headPitch);
        p.xRotO = p.getXRot();
        // pernas e o braço que sobrou acompanhando (o balanço de andar, no mesmo compasso)
        setF(WALK_POS, p.walkAnimation, th.limbPos);
        setF(WALK_SPEED, p.walkAnimation, th.limbSpeed);
        setF(WALK_SPEED_O, p.walkAnimation, th.limbSpeed);
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

    static float getF(Field f, Object o) {
        try {
            return f.getFloat(o);
        } catch (IllegalAccessException ex) {
            return 0F;
        }
    }

    static void setF(Field f, Object o, float v) {
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
