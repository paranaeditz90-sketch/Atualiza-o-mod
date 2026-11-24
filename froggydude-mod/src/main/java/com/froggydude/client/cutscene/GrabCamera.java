package com.froggydude.client.cutscene;

import com.froggydude.entity.CutsceneCamera;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.HeadGrab;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ClientShakeHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * (v0.7.41) A cutscene da cabeça em 3ª PESSOA (LM 17/11: "a cutscene é em 3 pessoa sempre, enquanto o froggydude sacode
 * pra arrancar a cabeça"; os planos de fora do vídeo 5 dele): enquanto ele está montado em quem joga, puxando a cabeça,
 * a câmera sai do olho e fica do lado, um pouco acima e perto, olhando entre ele e a cabeça de quem está embaixo, com
 * um giro lento; quem joga aparece deitado de costas se debatendo (VictimRender, o mesmo corpo da cena da zebra).
 * <p>
 * Os comandos continuam: o soco e a espada vão direto nele (o jogo apontaria a mira pela câmera de cinema, que não é o
 * olho de quem joga); a arma de fogo atira pelo olho de quem joga, que continua preso no corpo dele (ClientForgeEvents),
 * e o disparo conta (entity.HeadGrab). O alerta da tela (client.HeadGrabHud) continua aparecendo.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GrabCamera {

    private static CutsceneCamera cam;
    @Nullable
    private static FroggydudeEntity froggy;
    private static CameraType savedType;
    /** O lado da câmera (+1 / -1), escolhido pelo espaço livre quando começa. */
    private static float side = 1F;
    /**
     * A câmera do plano aberto do vídeo (ver {@link #place}): as cabeças ficam HEADS_AHEAD blocos na frente dele; a
     * câmera a CAM_DIST delas, a CAM_AZIMUTH graus da direção da cara dele, a CAM_HEIGHT do chão, mirando a AIM_UP do
     * chão, AIM_BACK blocos pro lado do corpo dele.
     */
    private static final double HEADS_AHEAD = 0.95D, CAM_DIST = 1.6D, CAM_AZIMUTH = 60D, CAM_HEIGHT = 0.25D,
            AIM_UP = 0.56D, AIM_BACK = 0.25D;
    /**
     * (LM 17/11: "a câmera tremer (não muito) enquanto ele tenta arrancar a cabeça sacudindo") graus de tremor no
     * compasso da sacudida (HeadGrab.shake), da mordida até o fim do rugido.
     */
    private static final float SHAKE_YAW = 1.1F, SHAKE_ROLL = 1.5F, SHAKE_PITCH = 0.8F;
    /** Quanto o jogo recua a câmera em 3ª pessoa (Camera.setup: getMaxZoom(4)). */
    private static final double PULLBACK = 4.0D;

    private GrabCamera() {
    }

    /** O FroggyDude que está agarrando a cabeça de quem joga agora (null se ninguém). */
    @Nullable
    public static FroggydudeEntity grabbing() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) return null;
        for (FroggydudeEntity f : mc.level.getEntitiesOfClass(FroggydudeEntity.class, me.getBoundingBox().inflate(6D))) {
            if (isGrabbing(f, me)) return f;
        }
        return null;
    }

    /** f está agarrando a cabeça de e? */
    public static boolean isGrabbing(FroggydudeEntity f, Entity e) {
        if (f.getFroggyState() != FroggyState.HEAD_GRAB) return false;
        CompoundTag t = f.headGrabState();
        return !t.isEmpty() && t.getInt("Victim") == e.getId();
    }

    /** O agarrão de quem estiver com a cabeça presa (pra VictimRender deitar o corpo): o FroggyDude, ou null. */
    @Nullable
    public static FroggydudeEntity grabberOf(Entity victim) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (FroggydudeEntity f : mc.level.getEntitiesOfClass(FroggydudeEntity.class, victim.getBoundingBox().inflate(6D))) {
            if (isGrabbing(f, victim)) return f;
        }
        return null;
    }

    /** Pra onde a cabeça de quem está embaixo aponta (dela pra ele, no chão). */
    public static Vec3 headDir(FroggydudeEntity f, Entity victim, float partial) {
        Vec3 d = f.getPosition(partial).subtract(victim.getPosition(partial)).multiply(1, 0, 1);
        return d.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : d.normalize();
    }

    /** Ticks (com fração) desde que agarrou. */
    public static float grabAge(FroggydudeEntity f, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0F;
        return mc.level.getGameTime() - f.headGrabState().getLong("GrabTime") + partial;
    }

    // ---------------------------------------------------------------- liga, desliga, posiciona

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || CinematicCamera.active()) {
            stop(mc);
            return;
        }
        FroggydudeEntity f = grabbing();
        if (f == null) {
            stop(mc);
            return;
        }
        if (cam == null || froggy != f || cam.level() != mc.level) start(mc, f);
        place(mc, f, event.renderTickTime);
        restoreAfterGui(mc);
        if (mc.getCameraEntity() != cam) {
            mc.setCameraEntity(cam); // (o jogo desliga o efeito de tela quando troca a câmera)
            CinematicCamera.blurOn(mc);
        }
    }

    /** A cutscene da cabeça está na tela. */
    static boolean active() {
        return froggy != null && cam != null;
    }

    /**
     * (LM 17/11: "sacudir rapidamente com leve blur"; depois "precisa aumentar levemente") o borrão do golpe final, só
     * no que se mexe (ele e quem está embaixo).
     */
    static final float BLUR_MIX = 0.36F;

    private static void start(Minecraft mc, FroggydudeEntity f) {
        froggy = f;
        if (cam == null || cam.level() != mc.level) {
            cam = new CutsceneCamera(ModEntityTypes.CUTSCENE_CAMERA.get(), mc.level);
            cam.setId(-91_7002);
        }
        if (savedType == null) savedType = mc.options.getCameraType();
        // 3ª pessoa de verdade: sem a arma/mão de 1ª pessoa (a do TaCZ desenha por conta própria) e sem a mira. O jogo
        // então recua a câmera {@link #PULLBACK} blocos atrás da entidade-câmera: ela fica esse tanto mais pra frente
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        // o lado com mais espaço livre
        side = clearance(mc, f, 1F) >= clearance(mc, f, -1F) - 0.3D ? 1F : -1F;
    }

    /** O tipo de câmera de quem joga, sem a 3ª pessoa desta cutscene (pro golpe final que vem logo depois). */
    static CameraType cameraTypeBefore(Minecraft mc) {
        return froggy != null && savedType != null ? savedType : mc.options.getCameraType();
    }

    private static void stop(Minecraft mc) {
        guiSwap = false;
        eyeWanted = null;
        if (froggy == null && cam == null) return;
        // o golpe final (se ele arrancou) liga o borrão dele; se ela se soltou, acaba aqui
        if (froggy != null && !CinematicCamera.active()) CinematicCamera.blurOff(mc);
        if (mc.player != null && mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
        // se soltou: deitado, a mira estava presa pra cima (nele); volta pro horizonte pra ver ele voando longe
        if (froggy != null && mc.player != null && mc.player.isAlive() && !CinematicCamera.active() && mc.player.getXRot() < -20F) {
            mc.player.setXRot(-5F);
            mc.player.xRotO = -5F;
        }
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
        froggy = null;
    }

    private static double clearance(Minecraft mc, FroggydudeEntity f, float s) {
        Vec3 h = headDir(f, mc.player, 1F);
        Vec3 l = new Vec3(-h.z, 0, h.x).scale(s);
        Vec3 from = focus(f, mc.player, 1F);
        Vec3 to = from.add(l.scale(3.5D)).add(0, 0.9D, 0);
        var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(from) : 3.5D;
    }

    /** A mordida: a boca dele na cabeça de quem está embaixo (~1 bloco na frente dele), um pouco acima do chão. */
    private static Vec3 focus(FroggydudeEntity f, Entity victim, float partial) {
        Vec3 h = headDir(f, victim, partial);
        return f.getPosition(partial).subtract(h.scale(0.8D)).add(0, 0.6D, 0);
    }

    private static void place(Minecraft mc, FroggydudeEntity f, float partial) {
        Vec3 h = headDir(f, mc.player, partial);
        Vec3 l = new Vec3(-h.z, 0, h.x);
        Vec3 fp = f.getPosition(partial);
        // (LM 17/11: "corrigir pra deixar igual ao do vídeo a posição da câmera") O plano aberto do vídeo 5 (6,1-8,6 s;
        // o mesmo do vídeo 3, 0,3-2,6 s), parado do começo ao fim. Medido varrendo posições no jogo e comparando com o
        // quadro do vídeo (a cabeça dele ~30% da altura da tela, o horizonte a ~60%, o par no meio): a câmera fica a
        // 1,6 bloco das cabeças, de lado e um pouco na frente (60° da direção da cara dele, do lado esquerdo dele), a um
        // quarto de bloco do chão, olhando de leve pra cima pras cabeças.
        Vec3 fwd = h.scale(-1D);
        Vec3 heads = fp.add(fwd.scale(HEADS_AHEAD));
        double a = Math.toRadians(CAM_AZIMUTH);
        Vec3 dir = l.scale(side * Math.sin(a)).add(fwd.scale(Math.cos(a)));
        Vec3 pos = new Vec3(heads.x + dir.x * CAM_DIST, fp.y + CAM_HEIGHT, heads.z + dir.z * CAM_DIST);
        // a mira: as cabeças, a pouco mais de meio bloco do chão, um tiquinho pro lado do corpo dele (o par no meio da tela)
        Vec3 aim = new Vec3(heads.x + h.x * AIM_BACK, fp.y + AIM_UP, heads.z + h.z * AIM_BACK);
        // não atravessa parede: chega mais perto
        var hit = mc.level.clip(new ClipContext(aim, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 back = aim.subtract(pos).normalize().scale(0.2D);
            pos = hit.getLocation().add(back);
        }
        Vec3 d = aim.subtract(pos);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        if (!mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        // a câmera fica exatamente aqui: o jogo recua a 3ª pessoa 4 blocos e {@link #onCameraAngles} devolve. Sem
        // como devolver (outro jogo/versão), a entidade vai PULLBACK blocos pra frente e o recuo traz de volta
        eyeWanted = pos;
        Vec3 eye = setPosMethod() != null ? pos : pos.add(d.normalize().scale(Math.min(PULLBACK, d.length() * 0.8D)));
        cam.setPos(eye.x, eye.y - cam.getEyeHeight(), eye.z);
        cam.xo = cam.xOld = cam.getX();
        cam.yo = cam.yOld = cam.getY();
        cam.zo = cam.zOld = cam.getZ();
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.yRotO = yaw;
        cam.xRotO = pitch;
    }

    /** Onde a câmera deve ficar neste quadro (place). */
    @Nullable
    private static Vec3 eyeWanted;
    private static Method setPos;
    private static boolean setPosLooked;

    /** Camera.setPosition(Vec3) é protegido: acha pela assinatura (o único método dela que só recebe um Vec3). */
    @Nullable
    private static Method setPosMethod() {
        if (!setPosLooked) {
            setPosLooked = true;
            for (Method m : Camera.class.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Vec3.class && m.getReturnType() == void.class) {
                    try {
                        m.setAccessible(true);
                        setPos = m;
                    } catch (RuntimeException ignored) {
                    }
                    break;
                }
            }
        }
        return setPos;
    }

    /**
     * A 3ª pessoa recua a câmera 4 blocos atrás da entidade (Camera.setup); aqui, depois disso e antes do mundo ser
     * desenhado, ela volta pro lugar certo (o plano baixo e de lado do vídeo 5), sem depender de ter espaço atrás.
     */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (froggy == null || cam == null || eyeWanted == null || event.getCamera().getEntity() != cam) return;
        Method m = setPosMethod();
        if (m != null) {
            try {
                m.invoke(event.getCamera(), eyeWanted);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        // o tremor da sacudida: pro lado junto com a cabeça dele, um tiquinho de giro e de sobe-desce
        if (froggy.headGrabState().isEmpty()) return;
        float t = grabAge(froggy, (float) event.getPartialTick());
        if (t <= HeadGrab.BITE || t >= HeadGrab.WINDOW) return;
        float in = Math.min(1F, (t - HeadGrab.BITE) / 4F);
        float s = HeadGrab.shake(t);
        event.setYaw(event.getYaw() + SHAKE_YAW * s * in);
        event.setRoll(event.getRoll() + SHAKE_ROLL * s * in);
        event.setPitch(event.getPitch() + SHAKE_PITCH * ClientShakeHandler.noise(0, t * 2.3F) * in);
    }

    // ---------------------------------------------------------------- a tela (corações, fome, hotbar)

    /** A câmera voltou pro jogador só pra desenhar a tela (ver {@link #onGuiPre}). */
    private static boolean guiSwap;

    /**
     * O jogo só desenha os corações, a fome e a hotbar quando a câmera é o jogador (Gui.getCameraPlayer). O alerta
     * fica no vão entre os corações e a fome (pedido do LM), então eles têm de aparecer: durante o desenho da tela a
     * câmera aponta pro jogador e volta pra câmera da cutscene logo depois (o campo direto, sem setCameraEntity, que
     * fecharia o pós-processamento).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGuiPre(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (froggy == null || cam == null || mc.player == null || mc.cameraEntity != cam) return;
        mc.cameraEntity = mc.player;
        guiSwap = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuiPost(RenderGuiEvent.Post event) {
        restoreAfterGui(Minecraft.getInstance());
    }

    /** Também no começo do quadro seguinte, se outro mod cancelou a tela no meio e o Post não veio. */
    private static void restoreAfterGui(Minecraft mc) {
        if (!guiSwap) return;
        guiSwap = false;
        if (cam != null && mc.cameraEntity == mc.player) mc.cameraEntity = cam;
    }

    // ---------------------------------------------------------------- quem joga, visto de fora

    /** O jogo não desenha quem joga quando a câmera é outra: desenha aqui (deitado, pelo VictimRender). */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || froggy == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.getCameraEntity() != cam) return;
        float partial = event.getPartialTick();
        Vec3 c = event.getCamera().getPosition();
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = event.getPoseStack();
        double x = Mth.lerp(partial, p.xOld, p.getX());
        double y = Mth.lerp(partial, p.yOld, p.getY());
        double z = Mth.lerp(partial, p.zOld, p.getZ());
        d.render(p, x - c.x, y - c.y, z - c.z, Mth.lerp(partial, p.yRotO, p.getYRot()), partial, ps, buffers,
                d.getPackedLightCoords(p, partial));
        buffers.endBatch();
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (froggy != null && Minecraft.getInstance().getCameraEntity() == cam) event.setCanceled(true);
    }

    /**
     * Soco e espada: direto nele (a mira do jogo sairia da câmera de cinema). A arma de fogo e o arco ficam com o próprio
     * jogo/mod (atiram pelo olho de quem joga, preso no corpo dele).
     */
    /** O botão de ataque estava segurado no fim do tick passado (pra um golpe por clique). */
    private static boolean attackWasDown;
    private static int attackTick = -1;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) attackWasDown = Minecraft.getInstance().options.keyAttack.isDown();
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (froggy == null || mc.player == null || mc.gameMode == null || !event.isAttack()) return;
        if (HeadGrab.shoots(mc.player.getMainHandItem())) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        // um golpe por clique: com o botão segurado o jogo manda isto de novo a cada tick (continueAttack, quando a mira
        // da câmera cai num bloco), inclusive no mesmo tick do clique; segurar não pode valer golpe
        if (attackWasDown || mc.player.tickCount == attackTick) return;
        attackTick = mc.player.tickCount;
        mc.gameMode.attack(mc.player, froggy);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}
