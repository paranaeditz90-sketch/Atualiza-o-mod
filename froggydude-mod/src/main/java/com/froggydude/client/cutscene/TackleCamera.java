package com.froggydude.client.cutscene;

import com.froggydude.entity.CutsceneCamera;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.Ragdoll;
import com.froggydude.client.pieces.PlayerRagdollScene;
import net.minecraft.client.player.AbstractClientPlayer;
import com.froggydude.init.ModEntityTypes;
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

/**
 * (v0.7.48/51) O bote com ragdoll, pra quem leva o bote. LM 23/11: "Atrás do froggydude, antes de ele começar a animação
 * do ragdoll, distância de 3 blocos (câmera dinâmica, se tiver bloco tapando, ela precisa se corrigir sozinha)": a câmera
 * para 3 blocos atrás dele (no rumo do voo, na altura dos olhos) no instante do bote e fica ali, virando pra acompanhar os
 * dois voando moles (o meio entre ele e quem voa); se um bloco tapar, ela anda pra frente na linha até ver. Quando caem,
 * a marreta (em 1ª pessoa) assume. Sem mão, sem mira, sem andar.
 * <p>
 * O corpo mole de quem voa (pra todo mundo que vê) é o client.pieces.PlayerRagdollScene, no lugar do desenho normal.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TackleCamera {

    /** Atrás dele (blocos) e a altura dos olhos de quem estava em pé ali. */
    private static final double BACK = 3.0D, HEIGHT = 1.5D;

    @Nullable
    private static FroggydudeEntity froggy;
    @Nullable
    private static CutsceneCamera cam;
    @Nullable
    private static CameraType savedType;
    /** Onde a câmera quer ficar (fixo) e onde ficou no último quadro (corrigida pelos blocos). */
    @Nullable
    private static Vec3 camWant;

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
        // 3 blocos atrás dele, no rumo do voo (o do ragdoll; sem ele, dele pra quem voa)
        Ragdoll.Params rp = f.ragdollParams();
        Vec3 dir = rp != null ? new Vec3(rp.dx(), 0, rp.dz()) : mc.player.position().subtract(f.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : dir.normalize();
        camWant = f.position().subtract(dir.scale(BACK)).add(0, HEIGHT, 0);
    }

    private static void stop(Minecraft mc) {
        if (froggy == null && savedType == null) return;
        if (mc.player != null && cam != null && mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
        froggy = null;
        camWant = null;
    }

    private static void place(Minecraft mc, float partial) {
        if (camWant == null || froggy == null) return;
        // olha pro meio dos dois
        Vec3 aim = mc.player.getPosition(partial).add(froggy.getPosition(partial)).scale(0.5D).add(0, 0.6D, 0);
        // bloco no caminho: vem pra frente na linha até enxergar (corrige sozinha a cada quadro)
        Vec3 pos = camWant;
        var hit = mc.level.clip(new ClipContext(aim, camWant, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 back = camWant.subtract(aim);
            double len = back.length();
            pos = len < 1.0E-4D ? aim : hit.getLocation().subtract(back.scale(0.3D / len));
        }
        Vec3 d = aim.subtract(pos);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        cam.setPos(pos.x, pos.y - cam.getEyeHeight(), pos.z);
        cam.xo = cam.xOld = cam.getX();
        cam.yo = cam.yOld = cam.getY();
        cam.zo = cam.zOld = cam.getZ();
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.yRotO = yaw;
        cam.xRotO = pitch;
    }

    /**
     * (v0.7.52) Com a câmera da cena, o jogo para de mandar a posição de quem joga pro servidor (o LocalPlayer só manda
     * quando a câmera é ele): o servidor achava que ela continuava lá onde o bote pegou (no topo do pilar) e a marreta
     * não vinha. Aqui manda a posição a cada tick enquanto voa.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.getCameraEntity() == mc.player) return;
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(mc.player.getX(),
                mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround()));
    }

    /** Quem voa no bote: o corpo mole (PlayerRagdollScene) no lugar do desenho normal, pra todo mundo que vê. */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!(p instanceof AbstractClientPlayer pl)) return;
        FroggydudeEntity f = tackler(p);
        Ragdoll.Params rp = f == null ? null : f.ragdollParams();
        if (rp == null) {
            PlayerRagdollScene.end(p.getId());
            return;
        }
        event.setCanceled(true);
        PlayerRagdollScene.of(pl, rp).render(pl, event.getRenderer().getModel(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick());
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
