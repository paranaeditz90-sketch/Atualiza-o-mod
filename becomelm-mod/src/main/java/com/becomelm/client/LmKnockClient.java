package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmKnock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Derrubado, do lado de quem vê: o corpo tomba de lado (girando em volta do rumo em que
 * estava) ou de costas (a queda do céu), fica no chão e levanta rolando. Quem foi derrubado
 * vê o mundo deitado (ou o céu) e não anda nem ataca até levantar.
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmKnockClient {

    private static final Map<Integer, LmKnock.State> STATES = new HashMap<>();
    /** Quem ganhou um push no desenho (pra tirar no fim do mesmo desenho). */
    private static final Set<Integer> PUSHED = new HashSet<>();
    /** A câmera de quem caiu tomba menos que o corpo (90° de lado enjoa). */
    private static final float CAMERA_ROLL = 0.75F;
    /** Altura do olho deitado. */
    private static final float LYING_EYE = 0.3F;
    /** De costas a câmera olha pro céu (quase reto pra cima: dá pra ver quem está em cima). */
    private static final float BACK_PITCH = -80F;

    private LmKnockClient() {
    }

    /** Chegou do servidor. */
    public static void set(int entity, int age, int down, int side, float yaw, boolean back) {
        STATES.put(entity, new LmKnock.State(age, down, side, yaw, back));
    }

    @Nullable
    static LmKnock.State get(Entity e) {
        return STATES.get(e.getId());
    }

    static boolean lying(Entity e) {
        LmKnock.State s = STATES.get(e.getId());
        return s != null && s.lying();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        if (mc.isPaused()) return;
        STATES.entrySet().removeIf(en -> {
            LmKnock.State s = en.getValue();
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null) return true;
            // morreu no chão: fica deitado até sumir (não levanta rolando morto)
            if (e instanceof LivingEntity le && le.deathTime > 0 && s.lying()) {
                s.age = Math.min(s.age + 1, LmKnock.FALL + s.down - 1);
                return false;
            }
            return ++s.age >= s.total();
        });
    }

    /**
     * O eixo (no chão) em volta do qual o corpo tomba: de lado, o rumo do corpo (o sinal dá o
     * lado); de costas, a direita do corpo.
     */
    private static Vec3 axis(LmKnock.State s) {
        float yaw = s.yaw * Mth.DEG_TO_RAD;
        if (s.back) return new Vec3(-Mth.cos(yaw), 0D, -Mth.sin(yaw));
        return new Vec3(-Mth.sin(yaw) * s.side, 0D, Mth.cos(yaw) * s.side);
    }

    /** Pra onde a cabeça vai quando deita (de lado: a direita ou a esquerda; de costas: pra trás). */
    static Vec3 headDir(LmKnock.State s) {
        Vec3 a = axis(s);
        return new Vec3(-a.z, 0D, a.x);
    }

    /** O quanto sobe pra não afundar no chão (deitado: a espessura; de ponta-cabeça no rolamento: a altura toda). */
    private static float lift(LmKnock.State s, LivingEntity e, float a) {
        float thick = e.getBbWidth() * (s.back ? 0.3F : 0.5F);
        return Math.abs(Mth.sin(a)) * thick + Math.max(0F, -Mth.cos(a)) * e.getBbHeight();
    }

    /** Tomba o desenho inteiro em volta dos pés. */
    static boolean apply(PoseStack pose, LivingEntity e, float partial) {
        LmKnock.State s = STATES.get(e.getId());
        if (s == null) return false;
        // o corpo fica no rumo da queda (cada um calcula o corpo dos outros sozinho e virava ele)
        e.yBodyRot = s.yaw;
        e.yBodyRotO = s.yaw;
        float a = s.angle(partial) * Mth.DEG_TO_RAD;
        Vec3 ax = axis(s);
        Quaternionf q = new Quaternionf().rotationAxis(a, (float) ax.x, 0F, (float) ax.z);
        float flip = deathFlip(e, partial);
        if (flip != 0F) {
            float yaw = s.yaw * Mth.DEG_TO_RAD;
            q.rotateAxis(flip * Mth.DEG_TO_RAD, -Mth.sin(yaw), 0F, Mth.cos(yaw));
        }
        pose.pushPose();
        pose.translate(0D, lift(s, e, a), 0D);
        pose.mulPose(q);
        return true;
    }

    /**
     * Morrendo, o jogo tomba o corpo de lado sozinho (e às vezes pro outro lado: o corpo
     * deitado ficava em pé). Isso desfaz o tombo do jogo.
     */
    private static float deathFlip(LivingEntity e, float partial) {
        if (e.deathTime <= 0) return 0F;
        float f = Math.min(Mth.sqrt((e.deathTime + partial - 1F) / 20F * 1.6F), 1F);
        return f * (e instanceof Spider || e instanceof Silverfish || e instanceof Endermite ? 180F : 90F);
    }

    /**
     * Onde fica o olho de quem está no chão (o corpo tomba em volta do rumo, então a cabeça vai
     * pro lado); de pé, o olho normal.
     */
    static Vec3 eye(LivingEntity e, float partial) {
        LmKnock.State s = STATES.get(e.getId());
        if (s == null) return e.getEyePosition(partial);
        float a = s.angle(partial) * Mth.DEG_TO_RAD;
        Vec3 d = headDir(s);
        float eh = e.getEyeHeight();
        Vec3 feet = e.getPosition(partial);
        return feet.add(d.x * eh * Mth.sin(a), lift(s, e, a) + eh * Mth.cos(a), d.z * eh * Mth.sin(a));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (apply(event.getPoseStack(), event.getEntity(), event.getPartialTick())) PUSHED.add(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    /** Quanto a pessoa está "no chão" agora, de 0 a 1 (sobe de novo no rolamento). */
    private static float downAmount(LmKnock.State s, float partial) {
        float t = s.age + partial;
        if (t < LmKnock.FALL) return t / LmKnock.FALL;
        if (t < LmKnock.FALL + s.down) return 1F;
        return 1F - Mth.clamp((t - LmKnock.FALL - s.down) / LmKnock.ROLL, 0F, 1F);
    }

    /** Primeira pessoa de quem caiu: olho no chão e o mundo de lado (de costas: olhando pro céu). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || LmCamera.active() || !mc.options.getCameraType().isFirstPerson()) return;
        Camera cam = event.getCamera();
        if (cam.getEntity() != p) return;
        LmKnock.State s = STATES.get(p.getId());
        if (s == null) return;
        float k = downAmount(s, (float) event.getPartialTick());
        k = k * k * (3F - 2F * k);
        Vec3 d = headDir(s);
        double drop = (p.getEyeHeight() - LYING_EYE) * k;
        cam.setPosition(cam.getPosition().add(d.x * drop * 0.8D, -drop, d.z * drop * 0.8D));
        if (s.back) event.setPitch(event.getPitch() + (BACK_PITCH - event.getPitch()) * k);
        else event.setRoll(event.getRoll() + s.side * 90F * CAMERA_ROLL * k);
    }

    /** No chão não anda, não pula e não agacha. */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!lying(event.getEntity())) return;
        var in = event.getInput();
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** Nem bate nem usa item deitado. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && lying(p)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        PUSHED.clear();
    }
}
