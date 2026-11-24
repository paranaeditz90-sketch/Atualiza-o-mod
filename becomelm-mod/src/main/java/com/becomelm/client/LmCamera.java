package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmAttack;
import com.becomelm.LmMode;
import com.becomelm.LmModes;
import com.becomelm.LmWallClimb;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * As câmeras de cinema (escolha do LM: as quatro). O jogador continua no controle (anda,
 * vira, ataca do próprio olho); só o que a tela mostra muda. Sem HUD, sem mão, sem o
 * contorno do bloco: é pra gravar.
 *
 * - Atrás e baixa: rente ao chão, atrás dele, virando junto quando ele vira.
 * - De lado acompanhando: do lado, correndo junto (como o carro de câmera do filme).
 * - Na cara dele: de frente pro rosto, a "câmera de selfie" dos vídeos.
 * - Da vítima: do olho de quem ele está caçando (o bicho/jogador na frente dele),
 *   olhando pra ele; sem vítima, do chão na frente dele, olhando pra cima. Martelando (v0.3.8), parada no chão na
 *   frente dele olhando pra cima, igual ao vídeo: ele inteiro de joelhos e a cabeça descendo até a tela.
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmCamera {

    public enum Shot {
        OFF("off"), BEHIND_LOW("behind_low"), SIDE("side"), FACE("face"), VICTIM("victim");

        public final String id;

        Shot(String id) {
            this.id = id;
        }

        public Component label() {
            return Component.translatable("becomelm.camera." + id);
        }
    }

    /**
     * "Da vítima" martelando (v0.3.8): o olho da vítima do vídeo, deitada na frente dele. A 1,7 bloco, no mergulho mais
     * fundo da marreta a cabeça dele para a ~0,35 bloco da câmera (medido com o rig do mod da IA), sem entrar.
     */
    private static final double HAMMER_VIEW_DIST = 1.7D;
    private static final double HAMMER_VIEW_EYE = 0.4D;
    private static final double HAMMER_VIEW_PITCH = 33D;
    private static Shot shot = Shot.OFF;
    private static boolean snap = true;
    /** Onde a câmera está, relativa a quem ela acompanha (suavizar isso não deixa ela ficar pra trás no galope). */
    @Nullable
    private static Vec3 offset;
    private static final boolean DEBUG = Boolean.getBoolean("becomelm.debug");
    private static float heading;
    private static long lastNanos;
    private static long frame;
    /** O balanço da caminhada do vanilla sacode a câmera: desligado enquanto a câmera de cinema está ligada. */
    @Nullable
    private static Boolean bobBefore;

    private static int victimId = -1;
    private static int victimLost;

    /** (v0.3.5) Entrando/saindo da parede o ponto de mira muda de lugar: a mira gira em ~0,2 s em vez de pular. */
    private static final float AIM_BLEND_S = 0.2F;
    private static boolean aimOnWall;
    private static float aimBlend = 1F;
    private static float aimFromYaw;
    private static float aimFromPitch;
    private static float lastYaw;
    private static float lastPitch;

    private LmCamera() {
    }

    public static Shot shot() {
        return shot;
    }

    public static void set(Shot s) {
        Minecraft mc = Minecraft.getInstance();
        if (s != Shot.OFF && bobBefore == null) {
            bobBefore = mc.options.bobView().get();
            mc.options.bobView().set(false);
        } else if (s == Shot.OFF && bobBefore != null) {
            mc.options.bobView().set(bobBefore);
            bobBefore = null;
        }
        shot = s;
        snap = true;
        victimId = -1;
        LmHud.toast(Component.translatable("becomelm.camera").append(": ").append(s.label()));
    }

    public static void next() {
        Shot[] all = Shot.values();
        set(all[(shot.ordinal() + 1) % all.length]);
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return shot != Shot.OFF && mc.player != null && !mc.player.isSpectator() && mc.getCameraEntity() == mc.player;
    }

    static long frame() {
        return frame;
    }

    /** A vítima da câmera da vítima não aparece (a câmera está dentro da cabeça dela). */
    static boolean hides(Entity e) {
        // recortando o corpo em pedaços: tem que desenhar a vítima mesmo assim
        if (com.becomelm.client.pieces.BodyPieces.capturing) return false;
        // na finalização a câmera é a da cena (a vítima tem que aparecer)
        if (LmFinishClient.directing()) return false;
        return active() && shot == Shot.VICTIM && e.getId() == victimId;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        frame++;
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Camera cam = event.getCamera();
        if (cam.getEntity() != p) return;
        float partial = (float) event.getPartialTick();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.05F : Mth.clamp((now - lastNanos) / 1.0E9F, 0F, 0.1F);
        lastNanos = now;

        Vec3 feet = new Vec3(Mth.lerp(partial, p.xo, p.getX()), Mth.lerp(partial, p.yo, p.getY()),
                Mth.lerp(partial, p.zo, p.getZ()));
        boolean gallop = LmModes.get(p) == LmMode.GALLOP;
        float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        float viewYaw = p.getViewYRot(partial);
        Vec3 head = head(p, feet, gallop, bodyYaw);

        Vec3 want;
        Vec3 look;
        float rate;
        Vec3 anchor = feet;
        // (v0.3.5) correndo pela parede: o "pra frente" das câmeras é pra cima e nenhuma mira dentro dos blocos
        // (com a mira dentro da parede a câmera ia parar na cara dele)
        Direction wall = LmWallClimb.climbingWall(p);
        LivingEntity victim = shot == Shot.VICTIM ? victim(p) : null;
        if (wall != null && victim == null) {
            Vec3 out = new Vec3(-wall.getStepX(), 0D, -wall.getStepZ());
            Vec3 side = new Vec3(-wall.getStepZ(), 0D, wall.getStepX());
            Vec3 body = feet.add(0D, 0.9D, 0D).add(out.scale(0.15D));
            // o rumo segue valendo (na saída do topo ele vai pra dentro da parede): sem isso a câmera voltava girando
            // do rumo de antes da subida
            heading = shot == Shot.BEHIND_LOW ? turn(heading, viewYaw, dt, 10F) : wall.toYRot();
            switch (shot) {
                case BEHIND_LOW -> {
                    // embaixo dele, um pouco pra fora, olhando parede acima
                    want = body.add(0D, -3.2D, 0D).add(out.scale(1.4D));
                    look = body.add(0D, 1.6D, 0D);
                    rate = 12F;
                }
                case SIDE -> {
                    want = body.add(side.scale(4.2D)).add(out.scale(1.5D)).add(0D, 0.8D, 0D);
                    look = body.add(0D, 0.6D, 0D);
                    rate = 6F;
                }
                default -> {
                    // na cara (e a da vítima sem vítima): quase em cima da cabeça olhando parede abaixo, ele vindo
                    // pela parede pra câmera (o plano do Grox 3:29, do alto do pilar)
                    Vec3 face = head.add(out.scale(0.15D));
                    want = face.add(0D, 1.8D, 0D).add(out.scale(0.35D));
                    look = face;
                    rate = 10F;
                }
            }
        } else switch (shot) {
            case BEHIND_LOW -> {
                heading = turn(heading, viewYaw, dt, 10F);
                Vec3 f = forward(heading);
                want = feet.add(0, 0.45D, 0).subtract(f.scale(3.6D));
                look = feet.add(0, 1.0D, 0).add(f.scale(1.5D));
                rate = 12F;
            }
            case SIDE -> {
                double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
                float move = dx * dx + dz * dz > 0.0025D ? (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F : heading;
                heading = turn(heading, move, dt, 3F);
                Vec3 f = forward(heading);
                Vec3 right = new Vec3(-f.z, 0, f.x);
                want = feet.add(0, 0.75D, 0).add(right.scale(4.2D)).add(f.scale(0.8D));
                look = feet.add(0, 0.75D, 0).add(f.scale(0.6D));
                rate = 6F;
            }
            case FACE -> {
                heading = turn(heading, gallop ? bodyYaw : viewYaw, dt, 8F);
                want = head.add(forward(heading).scale(1.4D)).add(0, 0.1D, 0);
                look = head.add(0, 0.1D, 0);
                rate = 10F;
            }
            default -> {
                LmActsClient.Act act = LmActsClient.get(p);
                if (act != null && (act.kind == LmAttack.HAMMER || act.kind == LmAttack.SMASH)) {
                    // (v0.3.8, LM: "a mesma perspectiva que esse do vídeo") martelando: a câmera fica onde estaria o
                    // olho da vítima deitada na frente dele (rente ao chão) e PARADA, olhando uns 33° pra cima: ele
                    // aparece inteiro de joelhos e a cabeça desce pra dentro da tela a cada pancada. Antes ela ficava
                    // no olho da vítima, que está embaixo dele, e ia atrás da cabeça (de perto, e entrava no corpo)
                    Vec3 fwd = forward(bodyYaw);
                    want = feet.add(fwd.scale(HAMMER_VIEW_DIST)).add(0, HAMMER_VIEW_EYE, 0);
                    look = want.subtract(fwd).add(0, Math.tan(Math.toRadians(HAMMER_VIEW_PITCH)), 0);
                    rate = 8F;
                } else {
                    if (victim != null) {
                        Vec3 eye = LmKnockClient.eye(victim, partial);
                        // um tiquinho pra fora da cabeça dela, na direção dele
                        want = eye.add(head.subtract(eye).normalize().scale(0.3D));
                        anchor = eye;
                        rate = 14F;
                    } else {
                        heading = turn(heading, viewYaw, dt, 8F);
                        want = feet.add(forward(heading).scale(2.6D)).add(0, 0.25D, 0);
                        rate = 10F;
                    }
                    look = head.add(0, 0.1D, 0);
                }
            }
        }
        look = lookOutside(p, feet.add(0D, 0.9D, 0D), look);
        want = clip(p, look, want);
        Vec3 rel = want.subtract(anchor);
        boolean onWallShot = wall != null && victim == null;
        boolean snapNow = snap || offset == null;
        if (onWallShot != aimOnWall) {
            aimOnWall = onWallShot;
            aimBlend = 0F;
            aimFromYaw = lastYaw;
            aimFromPitch = lastPitch;
        }
        if (snapNow) {
            aimBlend = 1F;
            offset = rel;
            snap = false;
            if (shot == Shot.BEHIND_LOW || shot == Shot.VICTIM) heading = viewYaw;
            else if (shot == Shot.FACE) heading = gallop ? bodyYaw : viewYaw;
            else heading = bodyYaw;
        } else {
            float k = 1F - (float) Math.exp(-dt * rate);
            offset = offset.add(rel.subtract(offset).scale(k));
        }
        Vec3 pos = clip(p, look, anchor.add(offset));
        if (DEBUG && frame % 20 == 0) {
            System.out.printf("[LMCAM] %s feet=%.1f,%.1f,%.1f cam=%.1f,%.1f,%.1f dist=%.2f victim=%s%n", shot,
                    feet.x, feet.y, feet.z, pos.x, pos.y, pos.z, pos.distanceTo(look),
                    victimId < 0 || mc.level == null ? "-" : String.valueOf(mc.level.getEntity(victimId)));
        }
        Vec3 d = look.subtract(pos);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        cam.setPosition(pos);
        cam.detached = true;
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG);
        if (aimBlend < 1F) {
            aimBlend = Math.min(1F, aimBlend + dt / AIM_BLEND_S);
            float k = aimBlend * aimBlend * (3F - 2F * aimBlend);
            yaw = aimFromYaw + Mth.wrapDegrees(yaw - aimFromYaw) * k;
            pitch = Mth.lerp(k, aimFromPitch, pitch);
        }
        lastYaw = yaw;
        lastPitch = pitch;
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(0F);
    }

    /** A cabeça do FroggyDude: do último quadro desenhado; senão, mais ou menos onde fica. */
    private static Vec3 head(LocalPlayer p, Vec3 feet, boolean gallop, float bodyYaw) {
        if (LmModes.get(p).froggy() && LmRenderer.localHead != null && frame - LmRenderer.localHeadFrame <= 2) {
            return feet.add(LmRenderer.localHead).add(0, 0.2D, 0);
        }
        if (gallop) return feet.add(0, 0.95D, 0).add(forward(bodyYaw).scale(0.5D));
        return feet.add(0, p.getEyeHeight(), 0);
    }

    @Nullable
    private static LivingEntity victim(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        // montado: a vítima é quem está embaixo
        LmActsClient.Act act = LmActsClient.get(p);
        if (act != null && (act.kind == LmAttack.SMASH || act.kind == LmAttack.HAMMER)
                && mc.level.getEntity(act.target) instanceof LivingEntity l) {
            victimId = l.getId();
            victimLost = 0;
            return l;
        }
        Entity cur = victimId >= 0 ? mc.level.getEntity(victimId) : null;
        if (cur instanceof LivingEntity l && l.isAlive() && l.distanceToSqr(p) < 24D * 24D) {
            // segura a mesma vítima (não fica pulando de bicho em bicho)
            if (p.hasLineOfSight(l)) victimLost = 0;
            if (victimLost++ < 60) return l;
        }
        victimId = -1;
        LivingEntity best = null;
        if (mc.crosshairPickEntity instanceof LivingEntity l && l.isAlive()) {
            best = l;
        } else {
            Vec3 look = p.getViewVector(1F);
            double bestScore = 0D;
            for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(20D))) {
                if (!(e instanceof LivingEntity l) || !l.isAlive() || l.isSpectator() || !p.hasLineOfSight(l)) continue;
                Vec3 to = l.getEyePosition().subtract(p.getEyePosition());
                double dist = to.length();
                double dot = look.dot(to.scale(1D / Math.max(dist, 1.0E-3D)));
                if (dot < 0.5D) continue;
                double score = dot / (1D + dist * 0.15D);
                if (score > bestScore) {
                    bestScore = score;
                    best = l;
                }
            }
        }
        if (best != null) {
            victimId = best.getId();
            victimLost = 0;
            snap = true;
        }
        return best;
    }

    /**
     * (v0.3.5) O ponto de mira não fica dentro de bloco: de cara pra parede (subindo ou no pé dela) a mira "na frente
     * dele" caía dentro dos blocos e a câmera grudava nele. Encolhe aos poucos conforme chega na parede (sem pulo).
     */
    private static Vec3 lookOutside(LocalPlayer p, Vec3 center, Vec3 look) {
        BlockHitResult hit = p.level().clip(new ClipContext(center, look, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) return look;
        Vec3 d = look.subtract(center);
        double len = d.length();
        if (len < 1.0E-3D) return look;
        double keep = Math.max(0D, hit.getLocation().distanceTo(center) - 0.2D);
        return center.add(d.scale(keep / len));
    }

    /** A câmera não entra na parede: encosta no que estiver entre ele e ela. */
    static Vec3 clip(LocalPlayer p, Vec3 from, Vec3 to) {
        BlockHitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) return to;
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-3D) return to;
        double keep = Math.max(0.3D, hit.getLocation().distanceTo(from) - 0.25D);
        return from.add(d.scale(keep / len));
    }

    private static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    private static float turn(float from, float to, float dt, float rate) {
        float k = 1F - (float) Math.exp(-dt * rate);
        return from + Mth.wrapDegrees(to - from) * k;
    }

    // ------------------------------------------------------------- sem HUD, sem mão

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if ((active() || LmFinishClient.directing() || LmFinishClient.victimView()) && !LmHud.ID.equals(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (active() || LmFinishClient.directing() || LmFinishClient.victimView()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        if (active() || LmFinishClient.directing()) event.setCanceled(true);
    }

    static void reset() {
        if (shot != Shot.OFF) set(Shot.OFF);
        offset = null;
    }
}
