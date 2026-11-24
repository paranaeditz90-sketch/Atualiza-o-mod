package com.froggydude.client.cutscene;

import com.froggydude.entity.CutsceneCamera;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * (v0.7.44) A cutscene de entrada (vídeo do LM 23/11; entity.FroggydudeEntity INTRO), em 2 planos:
 *
 *   1. do teu olho, travado nele: ele surgiu parado na pose de pulo, te olhando (~2,5 s);
 *   2. corte pra câmera baixa atrás dele, do lado do ombro esquerdo, olhando pro céu por cima da cabeça dele: o uivo.
 *
 * Quem assiste é quem o servidor escolheu (o id em FroggydudeEntity.introViewerId); ninguém mais vê nada diferente. Sem
 * pacote novo: o cliente percebe pelo estado sincronizado. Sem mão, sem mira, sem andar; o chat continua (o "entrou no
 * jogo" do vídeo).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class IntroCamera {

    /** Atrás dele, do lado e baixo (o 2º plano do vídeo: as costas e a cabeça dele no canto, a lua no meio). */
    private static final double BACK = 1.5D, SIDE = 0.8D, LOW = 0.35D, AIM_AHEAD = 3.0D, AIM_UP = 6.5D;

    @Nullable
    private static FroggydudeEntity froggy;
    @Nullable
    private static CutsceneCamera cam;
    @Nullable
    private static CameraType savedType;
    private static long startTime;
    /** O uivo já tocou nesta cena (toca no corte, aqui no cliente: o relógio do servidor corre na frente ao carregar). */
    private static boolean howled;

    private IntroCamera() {
    }

    public static boolean active() {
        return froggy != null && cam != null;
    }

    /** O FroggyDude fazendo a entrada pra quem joga aqui (null se nenhum). */
    @Nullable
    private static FroggydudeEntity watching(Minecraft mc) {
        for (FroggydudeEntity f : mc.level.getEntitiesOfClass(FroggydudeEntity.class, mc.player.getBoundingBox().inflate(64D))) {
            if (f.getFroggyState() == FroggyState.INTRO && f.introViewerId() == mc.player.getId()) return f;
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
        FroggydudeEntity f = watching(mc);
        if (f == null) {
            stop(mc);
            return;
        }
        if (froggy != f || cam == null || cam.level() != mc.level) start(mc, f);
        place(mc, f, event.renderTickTime);
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
    }

    private static void start(Minecraft mc, FroggydudeEntity f) {
        froggy = f;
        startTime = mc.level.getGameTime();
        howled = false;
        if (cam == null || cam.level() != mc.level) {
            cam = new CutsceneCamera(ModEntityTypes.CUTSCENE_CAMERA.get(), mc.level);
            cam.setId(-91_7003);
        }
        if (savedType == null) savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    private static void stop(Minecraft mc) {
        if (froggy == null && savedType == null) return;
        if (mc.player != null && cam != null && mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
        froggy = null;
    }

    private static void place(Minecraft mc, FroggydudeEntity f, float partial) {
        float age = mc.level.getGameTime() - startTime + partial;
        Vec3 fp = f.getPosition(partial);
        Vec3 pos;
        Vec3 aim;
        if (age < FroggydudeEntity.INTRO_HOWL) {
            // 1º plano: do teu olho, nele (a cara dele um pouco acima do meio da tela)
            // (a câmera fora da cabeça: o jogo desenha o corpo de quem joga quando a câmera é outra entidade)
            aim = fp.add(0, f.getBbHeight() * 0.72D, 0);
            Vec3 eye = mc.player.getEyePosition(partial);
            pos = eye.add(aim.subtract(eye).multiply(1, 0, 1).normalize().scale(0.6D));
        } else {
            if (!howled) {
                howled = true;
                mc.level.playLocalSound(fp.x, fp.y, fp.z, com.froggydude.init.ModSounds.INTRO_HOWL.get(),
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.8F, 1.0F, false);
            }
            // 2º plano: pra onde ele olha (o corpo), atrás dele, baixo e de lado, olhando pro céu por cima da cabeça
            float yaw = Mth.rotLerp(partial, f.yBodyRotO, f.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 left = new Vec3(fwd.z, 0, -fwd.x);
            aim = fp.add(fwd.scale(AIM_AHEAD)).add(0, AIM_UP, 0);
            pos = fp.subtract(fwd.scale(BACK)).add(left.scale(SIDE)).add(0, LOW, 0);
            Vec3 from = fp.add(0, 1.0D, 0);
            var hit = mc.level.clip(new ClipContext(from, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) pos = hit.getLocation().add(from.subtract(pos).normalize().scale(0.2D));
        }
        Vec3 d = aim.subtract(pos);
        float yawDeg = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        cam.setPos(pos.x, pos.y - cam.getEyeHeight(), pos.z);
        cam.xo = cam.xOld = cam.getX();
        cam.yo = cam.yOld = cam.getY();
        cam.zo = cam.zOld = cam.getZ();
        cam.setYRot(yawDeg);
        cam.setXRot(pitch);
        cam.yRotO = yawDeg;
        cam.xRotO = pitch;
    }

    /** Sem a mão/arma de 1ª pessoa no meio da cena. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (active()) event.setCanceled(true);
    }

    /** Sem a mira (o chat fica: o "entrou no jogo" faz parte da cena). */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (active() && event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) event.setCanceled(true);
    }

    /** O corpo de quem joga não aparece (a câmera fica logo na frente dele: o ombro entrava no pé da tela). */
    @SubscribeEvent
    public static void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        if (active() && event.getEntity() == Minecraft.getInstance().player) event.setCanceled(true);
    }

    /** Parado assistindo (os ~4 s da cena). */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!active()) return;
        var in = event.getInput();
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }
}
