package com.froggydude.client.cutscene;

import com.froggydude.config.FroggyClientConfig;
import com.froggydude.entity.CutsceneCamera;
import com.froggydude.entity.Finisher;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ClientShakeHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * A câmera de cinema de quem está sendo despedaçado: você assiste de fora,
 * no SEU lugar e com a SUA skin, igual ao vídeo da zebra (planos tirados de
 * lá, ver entity.Finisher):
 *
 *   1. câmera baixa do lado do braço esquerdo, chegando devagar: ele em pé por
 *      cima, abaixa, crava, arranca num tranco e a câmera sobe junto com o
 *      jorro e o braço na boca dele;
 *   2. corte pro outro lado, câmera baixa: o braço direito igual, ele parado
 *      mastigando, abaixa na cabeça;
 *   3. CLOSE na cara dele de baixo pra cima, a cabeça na boca sacudindo, a
 *      câmera tremendo e meio torta;
 *   4. escurece; atrás da tela de morte a câmera fica rodando devagar em
 *      volta do que sobrou.
 *
 * Cada quadro a posição sai da tabela com a fração do quadro (liso em 60 fps
 * ou o que a tela der). A câmera não atravessa parede: se tiver bloco entre o
 * ponto que ela olha e onde ela queria ficar, ela chega mais perto.
 *
 * Detalhe do jogo: quando a câmera não é o jogador, o Minecraft não desenha o
 * jogador local - então o corpo dela é desenhado aqui mesmo.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CinematicCamera {

    private static final ResourceLocation OVERLAY_ID = new ResourceLocation(ModEntityTypes.MOD_ID, "cutscene");

    /** Close: começa logo depois de arrancar a cabeça (no vídeo, 0,2 s depois). */
    private static final int CLOSE = Finisher.RIP_H + 4;
    /** Some no preto quando a cabeça acaba de descer (a última engolida aparece inteira). */
    private static final int FADE_OUT = Finisher.SWALLOW;

    private static ClientFinisher.Entry entry;
    private static CutsceneCamera cam;
    private static LocalPlayer owner;
    private static CameraType savedType;
    /** Lado de cada plano (+1 o esquerdo dela, -1 o direito), escolhido pelo espaço livre. */
    private static float sideA = 1F, sideB = -1F;
    private static float roll;
    private static float fov = 70F;
    private static Vec3 lastPos;
    /** Depois da morte: o ponto que a câmera segue (ele com o corpo), amaciado. */
    private static Vec3 follow;
    /** Quando ela morreu de fato (tick do jogo), pra escurecer; -1 viva. */
    private static long deadSince = -1;

    private CinematicCamera() {
    }

    public static boolean active() {
        return entry != null;
    }

    public static void begin(ClientFinisher.Entry e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (entry != null) end();
        entry = e;
        owner = mc.player;
        cam = new CutsceneCamera(ModEntityTypes.CUTSCENE_CAMERA.get(), mc.level);
        cam.setId(-91_7001);
        savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        chooseSides(mc, e);
        place(mc, 0F);
        mc.setCameraEntity(cam);
        // o sangue que estava na tela (dos socos de antes) sai: agora a câmera é de cinema
        com.froggydude.client.gore.ScreenBlood.clear();
        // a altura do olho da câmera do jogo vem "andando" da entidade anterior: já põe no lugar
        Camera c = mc.gameRenderer.getMainCamera();
        try {
            ObfuscationReflectionHelper.setPrivateValue(Camera.class, c, cam.getEyeHeight(), "f_90562_");
            ObfuscationReflectionHelper.setPrivateValue(Camera.class, c, cam.getEyeHeight(), "f_90563_");
        } catch (RuntimeException ignored) {
            // sem isso a câmera só desliza uns centímetros nos primeiros ticks
        }
    }

    public static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (entry == null) return;
        entry = null;
        if (mc.player != null && mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
        cam = null;
        owner = null;
        lastPos = null;
        follow = null;
        deadSince = -1;
        com.froggydude.client.gore.ScreenBlood.clear();
    }

    // ---------------------------------------------------------------- planos

    /** Plano 1 do lado com mais espaço; o 2 do outro (se os dois lados estão fechados, fica o padrão). */
    private static void chooseSides(Minecraft mc, ClientFinisher.Entry e) {
        double left = clearance(mc, e, 1F), right = clearance(mc, e, -1F);
        boolean skip = e.skipLeft;
        if (!skip && left < 2.0D && right > left + 0.4D) {
            sideA = -1F;
            sideB = 1F;
        } else if (right < 2.0D && left > right + 0.4D) {
            // o lado direito está fechado: o segundo plano fica do lado esquerdo também
            sideA = 1F;
            sideB = 1F;
        } else {
            sideA = 1F;
            sideB = -1F;
        }
    }

    private static double clearance(Minecraft mc, ClientFinisher.Entry e, float side) {
        Vec3 from = e.at(0.0D, 0D, 1.0D);
        Vec3 to = e.at(2.3D, 1.45D * side, 0.3D);
        var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(from) : from.distanceTo(to);
    }

    /** (ao longo da cabeça, pro lado esquerdo dela, pra cima) */
    private record Key(float t, double a, double s, double u) {
    }

    // Planos tirados do vídeo da zebra (77,3-86 s): a câmera fica BAIXINHA, quase no chão,
    // do lado da cabeça da vítima (a cabeça dela em primeiro plano), olhando pra cima pra
    // ele em pé por cima do corpo. Montados do lado esquerdo dela (+); o B é espelhado.
    // (prints lado a lado) No vídeo ele ocupa só metade da altura da tela: a câmera fica a
    // ~3 blocos dele. A 2 blocos ele enchia a tela e, ao abaixar pra morder, a cabeça
    // dele entrava na câmera (a tela verde/amarela).
    private static final Key[] POS_A = {
            new Key(0, 2.30, 1.45, 0.30), new Key(Finisher.RIP_L, 2.20, 1.38, 0.30),
            new Key(Finisher.CUT_1, 2.10, 1.30, 0.32)};
    private static final Key[] LOOK_A = {
            new Key(0, -0.15, 0.05, 1.25), new Key(Finisher.BEND_L, -0.05, 0.12, 1.05),
            new Key(Finisher.BITE_L, 0.35, 0.30, 0.45), new Key(Finisher.RIP_L, 0.35, 0.30, 0.55),
            new Key(Finisher.RIP_L + 6, -0.05, 0.15, 1.35), new Key(Finisher.CUT_1, -0.10, 0.10, 1.40)};
    private static final Key[] POS_B = {
            new Key(Finisher.CUT_1, 2.30, 1.45, 0.30), new Key(Finisher.STILL, 2.15, 1.35, 0.30),
            new Key(Finisher.BEND_H, 2.05, 1.25, 0.32), new Key(CLOSE, 2.00, 1.20, 0.32)};
    private static final Key[] LOOK_B = {
            new Key(Finisher.CUT_1, -0.10, 0.10, 1.30), new Key(Finisher.BEND_R, -0.05, 0.12, 1.05),
            new Key(Finisher.BITE_R, 0.35, 0.30, 0.45), new Key(Finisher.RIP_R, 0.35, 0.30, 0.55),
            new Key(Finisher.RIP_R + 6, -0.05, 0.15, 1.35), new Key(Finisher.STILL, -0.10, 0.10, 1.40),
            new Key(Finisher.BEND_H, -0.05, 0.05, 1.20), new Key(Finisher.BITE_H, 0.50, 0.02, 0.45),
            new Key(Finisher.RIP_H, 0.50, 0.00, 0.55), new Key(CLOSE, 0.10, 0.00, 1.40)};
    /**
     * Close na cara dele, perto e um pouco de baixo (86-88,8 s). No vídeo a câmera fica na
     * frente-DIREITA dele (lado L): a cara dele à esquerda da tela e a cabeça da vítima
     * saindo da boca pro lado direito. De frente, a cabeça na boca tampava a cara dele.
     */
    private static final Key[] POS_C = {
            new Key(CLOSE, 0.92, 0.95, 1.58), new Key(Finisher.END, 0.82, 0.88, 1.62)};
    // a cara dele fica a ~1,84 de altura (cabeça do rig de 1,54 a 2,13): mirando no queixo
    // (1,62) o close cortava a cara em cima e só sobrava a cabeça da vítima e o sangue
    private static final Key[] LOOK_C = {
            new Key(CLOSE, 0.00, 0.05, 1.80), new Key(Finisher.END, 0.00, 0.05, 1.82)};

    private static Vec3 track(ClientFinisher.Entry e, Key[] keys, float t, float side) {
        Key a = keys[0], b = keys[keys.length - 1];
        if (t <= a.t) b = a;
        else if (t < b.t) {
            for (int i = 0; i < keys.length - 1; i++) {
                if (t < keys[i + 1].t) {
                    a = keys[i];
                    b = keys[i + 1];
                    break;
                }
            }
        } else a = b;
        float f = b.t > a.t ? (t - a.t) / (b.t - a.t) : 0F;
        f = f * f * (3F - 2F * f);
        return e.at(Mth.lerp(f, a.a, b.a), Mth.lerp(f, a.s, b.s) * side, Mth.lerp(f, a.u, b.u));
    }

    /** Põe o cinegrafista no lugar deste quadro. */
    private static void place(Minecraft mc, float partial) {
        ClientFinisher.Entry e = entry;
        float t = e.t(partial);
        float time = (mc.level.getGameTime() + partial);
        Vec3 pos, look;
        float shake;
        if (e.ended() || t >= Finisher.END + 2) {
            // depois: rodando devagar em volta do que sobrou (atrás da tela de morte)
            float after = Math.max(0F, t - Finisher.END);
            double ang = Math.toRadians(e.yaw + 120F + after * 0.35F);
            // no Extremo ele leva o corpo pra lá e pra cá: a câmera vai atrás dele (devagar)
            Vec3 focus = e.anchor.add(0, 0.45D, 0);
            Entity froggy = mc.level.getEntity(e.froggyId);
            Entity body = e.corpseId >= 0 ? mc.level.getEntity(e.corpseId) : null;
            if (e.corpse && froggy != null && froggy.distanceToSqr(e.anchor) < 24D * 24D) {
                // enquadra o corpo e ele (mais o corpo: é o que ele está comendo)
                Vec3 f = froggy.getPosition(partial).add(0, 0.7D, 0);
                if (body != null) f = body.getPosition(partial).add(0, 0.25D, 0).lerp(f, 0.3D);
                follow = follow == null ? f : follow.lerp(f, 1D - Math.pow(0.88D, mc.getDeltaFrameTime()));
                focus = follow;
            }
            boolean close = e.corpse;
            pos = focus.add(-Math.sin(ang) * (close ? 3.0D : 4.2D), close ? 1.45D : 1.9D, Math.cos(ang) * (close ? 3.0D : 4.2D));
            look = focus;
            shake = 0.15F;
            roll = 0F;
            fov = 70F;
        } else if (t >= CLOSE) {
            pos = track(e, POS_C, t, 1F);
            look = track(e, LOOK_C, t, 1F);
            // sacudindo a cabeça na boca: a câmera treme forte e fica torta
            shake = t < Finisher.SWALLOW ? 2.6F : 0.8F;
            roll = 4F + ClientShakeHandler.noise(2, time * 0.9F) * 3F;
            fov = Mth.lerp((t - CLOSE) / (Finisher.END - CLOSE), 62F, 56F);
        } else if (t >= Finisher.CUT_1 || e.skipLeft) {
            // posição montada do lado esquerdo e espelhada pro lado do plano; o foco é sempre o braço da vez
            float tb = Math.max(t, Finisher.CUT_1);
            pos = mirror(e, track(e, POS_B, tb, 1F), sideB);
            look = track(e, LOOK_B, tb, -1F);
            shake = jolt(t, Finisher.RIP_R);
            roll = 0F;
            fov = 70F;
        } else {
            pos = mirror(e, track(e, POS_A, t, 1F), sideA);
            look = track(e, LOOK_A, t, 1F);
            shake = jolt(t, Finisher.RIP_L);
            roll = 0F;
            fov = 70F;
        }
        // não atravessa parede: se tem bloco entre o foco e a câmera, chega mais perto
        var hit = mc.level.clip(new ClipContext(look, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 back = look.subtract(pos).normalize().scale(0.25D);
            pos = hit.getLocation().add(back);
        }
        Vec3 d = look.subtract(pos);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG);
        // câmera na mão: respira sempre um pouco
        yaw += ClientShakeHandler.noise(0, time * 0.35F) * (0.5F + shake);
        pitch += ClientShakeHandler.noise(1, time * 0.35F) * (0.4F + shake);
        if (shake > 1F) {
            yaw += ClientShakeHandler.noise(0, time * 2.3F) * shake;
            pitch += ClientShakeHandler.noise(1, time * 2.3F) * shake;
        }
        cam.setPos(pos.x, pos.y - cam.getEyeHeight(), pos.z);
        cam.xo = cam.xOld = cam.getX();
        cam.yo = cam.yOld = cam.getY();
        cam.zo = cam.zOld = cam.getZ();
        cam.setYRot(yaw);
        cam.yRotO = yaw;
        cam.setXRot(Mth.clamp(pitch, -89F, 89F));
        cam.xRotO = cam.getXRot();
        lastPos = pos;
    }

    /** Os planos são montados do lado esquerdo dela (+); do outro lado, espelha. */
    private static Vec3 mirror(ClientFinisher.Entry e, Vec3 p, float side) {
        if (side > 0) return p;
        Vec3 rel = p.subtract(e.anchor);
        double s = rel.x * e.l.x + rel.z * e.l.z;
        return p.subtract(e.l.scale(2D * s));
    }

    /** O tranco de arrancar: a câmera dá um soco e volta. */
    private static float jolt(float t, int at) {
        float k = t - at;
        if (k < 0F || k > 12F) return 0F;
        return 2.2F * (1F - k / 12F);
    }

    // ---------------------------------------------------------------- eventos

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || entry == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!valid(mc)) {
            end();
            return;
        }
        place(mc, event.renderTickTime);
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
    }

    /** Fica valendo enquanto ela está na cutscene ou morta vendo o corpo; renasceu/trocou de mundo: acaba. */
    private static boolean valid(Minecraft mc) {
        if (mc.level == null || mc.player == null || cam == null || cam.level() != mc.level) return false;
        if (mc.player != owner) return false;
        // acabou: o pacote do fim pode chegar antes da vida zerar - espera um pouco pela morte
        // (no Extremo ela ainda fica viva uns segundos vendo ele comer o corpo)
        if (entry.ended()) {
            return mc.player.isDeadOrDying() || mc.level.getGameTime() - entry.endedAt < (entry.corpse ? 200 : 30);
        }
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || entry == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!valid(mc)) {
            end();
            return;
        }
        if (!mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);
        // nada de abrir inventário, jogar item, trocar de mão ou de visão no meio
        KeyMapping[] keys = {mc.options.keyInventory, mc.options.keyDrop, mc.options.keySwapOffhand,
                mc.options.keyTogglePerspective, mc.options.keyPickItem, mc.options.keyAttack, mc.options.keyUse,
                mc.options.keyAdvancements};
        for (KeyMapping k : keys) {
            while (k.consumeClick()) {
                // engole
            }
        }
        for (KeyMapping k : mc.options.keyHotbarSlots) {
            while (k.consumeClick()) {
                // engole
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (entry == null) return;
        Input input = event.getInput();
        input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
        input.forwardImpulse = 0F;
        input.leftImpulse = 0F;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (entry == null) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (entry != null) event.setCanceled(true);
    }

    /** Sem HUD nem sangue na tela: só as faixas pretas. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (entry == null) return;
        ResourceLocation id = event.getOverlay().id();
        if (!OVERLAY_ID.equals(id)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (entry == null || Minecraft.getInstance().getCameraEntity() != cam) return;
        event.setRoll(event.getRoll() + roll);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (entry == null || !event.usedConfiguredFov() || Minecraft.getInstance().getCameraEntity() != cam) return;
        event.setFOV(fov);
    }

    /** O jogo não desenha o jogador local quando a câmera é outra entidade: desenha aqui (deitado, pelo VictimRender). */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || entry == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.getCameraEntity() != cam || VictimRender.state(p) == VictimRender.HIDDEN) return;
        float partial = event.getPartialTick();
        Vec3 c = event.getCamera().getPosition();
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double x = Mth.lerp(partial, p.xOld, p.getX());
        double y = Mth.lerp(partial, p.yOld, p.getY());
        double z = Mth.lerp(partial, p.zOld, p.getZ());
        d.render(p, x - c.x, y - c.y, z - c.z, Mth.lerp(partial, p.yRotO, p.getYRot()), partial,
                event.getPoseStack(), buffers, d.getPackedLightCoords(p, partial));
        buffers.endBatch();
    }

    // ---------------------------------------------------------------- tela

    /** Faixas pretas de cinema, entrar/sair no preto e (no Desligado) o preto na hora de cada mordida. */
    public static void renderOverlay(GuiGraphics gfx, float partial, int w, int h) {
        ClientFinisher.Entry e = entry;
        if (e == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = e.t(partial);
        float start = e.skipLeft ? Finisher.SKIP : 0F;
        float in = Mth.clamp((t - start) / 10F, 0F, 1F);
        int bar = (int) (h * 0.11F * (in * in * (3F - 2F * in)));
        if (bar > 0) {
            gfx.fill(0, 0, w, bar, 0xFF000000);
            gfx.fill(0, h - bar, w, h, 0xFF000000);
        }
        float black = 0F;
        // corte do tombo pro cinema
        black = Math.max(black, 1F - Mth.clamp((t - start) / 5F, 0F, 1F));
        // no fim: preto depois de engolir. No Extremo volta do preto pra ela ver ele comendo o
        // corpo; quando morre de verdade (e sem o cadáver, logo no fim) fica preto atrás da tela de morte
        if (t >= FADE_OUT) {
            float out = Mth.clamp((t - FADE_OUT) / 6F, 0F, 1F);
            float back = e.corpse ? Mth.clamp((t - (Finisher.END + 4)) / 16F, 0F, 1F) : 0F;
            black = Math.max(black, out * (1F - back));
        }
        if (mc.player != null && mc.player.isDeadOrDying()) {
            if (deadSince < 0) deadSince = mc.level.getGameTime();
            black = Math.max(black, Mth.clamp((mc.level.getGameTime() + partial - deadSince) / 12F, 0F, 1F));
        } else {
            deadSince = -1;
        }
        if (!FroggyClientConfig.gore().dismember()) {
            // Desligado: não mostra nada sendo arrancado
            if (!e.skipLeft) black = Math.max(black, window(t, Finisher.BEND_L + 2, Finisher.RIP_L + 14));
            black = Math.max(black, window(t, Finisher.BEND_R + 2, Finisher.RIP_R + 14));
            black = Math.max(black, window(t, Finisher.BEND_H + 2, Finisher.END + 14));
        }
        if (black > 0.004F) {
            int a = (int) (Mth.clamp(black, 0F, 1F) * 255F);
            gfx.fill(0, 0, w, h, a << 24);
        }
    }

    private static float window(float t, float from, float to) {
        if (t < from - 4F || t > to + 6F) return 0F;
        if (t < from) return (t - (from - 4F)) / 4F;
        if (t > to) return 1F - (t - to) / 6F;
        return 1F;
    }
}
