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
 * estava), fica no chão e levanta rolando. Quem foi derrubado vê o mundo deitado e não
 * anda nem ataca até levantar.
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

    private LmKnockClient() {
    }

    /** Chegou do servidor. */
    public static void set(int entity, int age, int down, int side, float yaw) {
        STATES.put(entity, new LmKnock.State(age, down, side, yaw));
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
        STATES.values().removeIf(s -> ++s.age >= s.total());
    }

    /**
     * Tomba o desenho inteiro em volta dos pés: gira em volta do rumo do corpo e sobe o
     * suficiente pra não afundar no chão (deitado: meia largura; de ponta-cabeça no rolamento:
     * a altura toda).
     */
    static boolean apply(PoseStack pose, LivingEntity e, float partial) {
        LmKnock.State s = STATES.get(e.getId());
        if (s == null) return false;
        float roll = s.roll(partial) * Mth.DEG_TO_RAD;
        float yaw = s.yaw * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        float lift = Math.abs(Mth.sin(roll)) * e.getBbWidth() * 0.5F + Math.max(0F, -Mth.cos(roll)) * e.getBbHeight();
        pose.pushPose();
        pose.translate(0D, lift, 0D);
        pose.mulPose(new Quaternionf().rotationAxis(roll, fx, 0F, fz));
        return true;
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

    /** Primeira pessoa de quem caiu: olho no chão e o mundo de lado. */
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
        float yaw = s.yaw * Mth.DEG_TO_RAD;
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        double drop = (p.getEyeHeight() - LYING_EYE) * k;
        Vec3 pos = cam.getPosition().add(rx * s.side * drop * 0.8D, -drop, rz * s.side * drop * 0.8D);
        cam.setPosition(pos);
        event.setRoll(event.getRoll() + s.side * 90F * CAMERA_ROLL * k);
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
