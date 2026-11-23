package com.froggydude.client.cutscene;

import com.froggydude.entity.CutsceneCamera;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * (v0.7.48) O bote com ragdoll (vídeo do LM 23/11: "ele pula nele, ativando uma ragdoll (uns 1,5-2s de duração), e
 * automaticamente pula para a parte da marreta", "em outra visão"): quem voa é visto de fora, de uma câmera fixa no chão,
 * de lado, onde o bote pegou; o corpo gira mole no ar (aqui no cliente, pra todo mundo que vê). Quando ele cai, a marreta
 * (em 1ª pessoa) assume. Sem mão, sem mira, sem andar.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TackleCamera {

    /** De lado (blocos), baixo (perto do chão), um pouco pra trás de onde o bote pegou. */
    private static final double SIDE = 4.5D, HEIGHT = 0.35D, BACK = 1.0D;

    @Nullable
    private static FroggydudeEntity froggy;
    @Nullable
    private static CutsceneCamera cam;
    @Nullable
    private static CameraType savedType;
    @Nullable
    private static Vec3 camPos;
    /** Quando cada um que voa começou a voar (tick do jogo do cliente), pro giro. */
    private static final Map<Integer, Long> FLYING = new HashMap<>();

    private TackleCamera() {
    }

    public static boolean active() {
        return froggy != null && cam != null;
    }

    @Nullable
    private static FroggydudeEntity tackler(Entity victim) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (FroggydudeEntity f : mc.level.getEntitiesOfClass(FroggydudeEntity.class, victim.getBoundingBox().inflate(8D))) {
            if (f.tackleVictimId() == victim.getId()) return f;
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || CinematicCamera.active() || GrabCamera.active()) {
            stop(mc);
            return;
        }
        FroggydudeEntity f = tackler(mc.player);
        if (f == null) {
            stop(mc);
            return;
        }
        if (froggy != f || cam == null || cam.level() != mc.level) start(mc, f);
        place(mc, event.renderTickTime);
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
    }

    private static void start(Minecraft mc, FroggydudeEntity f) {
        froggy = f;
        if (cam == null || cam.level() != mc.level) {
            cam = new CutsceneCamera(ModEntityTypes.CUTSCENE_CAMERA.get(), mc.level);
            cam.setId(-91_7004);
        }
        if (savedType == null) savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        // a câmera fica parada: de lado do rumo do voo, baixa, um pouco atrás de onde pegou
        Vec3 p = mc.player.position();
        Vec3 dir = p.subtract(f.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 want = p.add(side.scale(SIDE)).subtract(dir.scale(BACK)).add(0, HEIGHT, 0);
        Vec3 from = p.add(0, 1.0D, 0);
        var hit = mc.level.clip(new ClipContext(from, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            // sem espaço desse lado: o outro
            want = p.subtract(side.scale(SIDE)).subtract(dir.scale(BACK)).add(0, HEIGHT, 0);
            hit = mc.level.clip(new ClipContext(from, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) want = hit.getLocation().add(from.subtract(want).normalize().scale(0.3D));
        }
        camPos = want;
    }

    private static void stop(Minecraft mc) {
        if (froggy == null && savedType == null) return;
        if (mc.player != null && cam != null && mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
        froggy = null;
        camPos = null;
    }

    private static void place(Minecraft mc, float partial) {
        if (camPos == null) return;
        Vec3 aim = mc.player.getPosition(partial).add(0, 0.8D, 0);
        Vec3 d = aim.subtract(camPos);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        cam.setPos(camPos.x, camPos.y - cam.getEyeHeight(), camPos.z);
        cam.xo = cam.xOld = cam.getX();
        cam.yo = cam.yOld = cam.getY();
        cam.zo = cam.zOld = cam.getZ();
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.yRotO = yaw;
        cam.xRotO = pitch;
    }

    /** O corpo de quem voa gira mole: cambalhota pra frente e um pouco de lado, mais rápido no começo. */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (tackler(p) == null) {
            FLYING.remove(p.getId());
            return;
        }
        long start = FLYING.computeIfAbsent(p.getId(), k -> mc.level.getGameTime());
        float age = mc.level.getGameTime() - start + event.getPartialTick();
        float spin = 720F * (1F - (float) Math.exp(-age / 14F)); // ~2 voltas, freando
        var ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0, 0.9D, 0);
        ps.mulPose(Axis.XP.rotationDegrees(spin));
        ps.mulPose(Axis.ZP.rotationDegrees(25F * Mth.sin(age * 0.45F)));
        ps.translate(0, -0.9D, 0);
        PUSHED.add(p.getId());
    }

    private static final java.util.Set<Integer> PUSHED = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (PUSHED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (active()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (active() && event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!active()) return;
        var in = event.getInput();
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }
}
