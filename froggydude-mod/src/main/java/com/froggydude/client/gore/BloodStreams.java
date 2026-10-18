package com.froggydude.client.gore;

import com.froggydude.client.ClientBloodFx;
import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * O sangue JORRANDO (Extremo): em vez de só pedaços soltos, um jato contínuo,
 * como mangueira - uma fita líquida que sai do ferimento, faz o arco da
 * gravidade, quebra em gotas no fim e vira poça onde cai. Se quem sangra se
 * mexe, o jato balança junto (é preso no corpo). Igual ao jorro do ombro da
 * zebra (77,3-80,6 s do vídeo do LM), só que de líquido.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BloodStreams {

    private static final ResourceLocation TEX = new ResourceLocation(ModEntityTypes.MOD_ID, "textures/misc/blood_stream.png");
    private static final int MAX_STREAMS = 48;
    /** Gotas por tick: quanto mais, mais liso o arco. */
    private static final int PER_TICK = 3;
    private static final double GRAVITY = 0.045D;

    private static final List<Stream> STREAMS = new ArrayList<>();
    private static ClientLevel streamLevel;

    private BloodStreams() {
    }

    private static final class Drop {
        double x, y, z, px, py, pz, vx, vy, vz;
        int age;
        boolean dead;
        /** Começo de um pedaço novo da fita (não liga com a gota de antes). */
        boolean breakBefore;
    }

    /** Um jato. A origem e a direção vêm de fora (presas num corpo, num toco de braço...). */
    public static final class Stream {
        final Supplier<Vec3> origin;
        final Supplier<Vec3> dir;
        final float speed;
        final int total;
        int left;
        final float r, g, b;
        final float width;
        final boolean pulse;
        final List<Drop> drops = new ArrayList<>();
        int age;
        int pools;
        boolean wasOff = true;
        /** Variação lenta da força (ruído suave, igual pra todas as gotas do tick). */
        double wob;
        double jx, jy, jz;

        Stream(Supplier<Vec3> origin, Supplier<Vec3> dir, float speed, int ticks, int color, float width, boolean pulse) {
            this.origin = origin;
            this.dir = dir;
            this.speed = speed;
            this.total = Math.max(1, ticks);
            this.left = ticks;
            this.r = ((color >> 16) & 255) / 255F;
            this.g = ((color >> 8) & 255) / 255F;
            this.b = (color & 255) / 255F;
            this.width = width;
            this.pulse = pulse;
        }

        /** Para de jorrar (o que já saiu continua caindo). */
        public void stop() {
            left = 0;
        }
    }

    private static boolean ready() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        if (level != streamLevel) {
            STREAMS.clear();
            streamLevel = level;
        }
        return true;
    }

    /** Jato com origem e direção calculadas a cada tick (ex.: o toco do braço na cutscene). */
    public static Stream custom(Supplier<Vec3> origin, Supplier<Vec3> dir, float speed, int ticks, int color,
                                float width, boolean pulse) {
        if (!ready()) return null;
        if (STREAMS.size() >= MAX_STREAMS) STREAMS.remove(0);
        Stream s = new Stream(origin, dir, speed, ticks, color, width, pulse);
        STREAMS.add(s);
        return s;
    }

    /** Jato parado no mundo (respingo forte de um golpe). */
    public static Stream fixed(Vec3 at, Vec3 dir, float speed, int ticks, int color) {
        Vec3 d = dir.normalize();
        return custom(() -> at, () -> d, speed, ticks, color, 0.06F, false);
    }

    /**
     * Jato preso no corpo de uma entidade: local = (direita, cima, frente) em
     * blocos, girando com o corpo; dir é a direção no mundo quando começou (gira
     * junto com o corpo depois).
     */
    public static Stream attached(Entity e, Vec3 local, Vec3 dir, float speed, int ticks, int color) {
        float yaw0 = bodyYaw(e, 1F);
        Vec3 d0 = dir.normalize();
        int id = e.getId();
        Supplier<Vec3> origin = () -> {
            Entity now = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
            Entity src = now != null ? now : e;
            return toWorld(src, local, 1F);
        };
        Supplier<Vec3> direction = () -> {
            Entity now = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
            float dy = (now == null ? yaw0 : bodyYaw(now, 1F)) - yaw0;
            return d0.yRot(-dy * Mth.DEG_TO_RAD);
        };
        return custom(origin, direction, speed, ticks, color, 0.075F, true);
    }

    static float bodyYaw(Entity e, float partial) {
        if (e instanceof LivingEntity le) return Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot);
        return e.getViewYRot(partial);
    }

    /** (direita, cima, frente) no corpo -> mundo. */
    public static Vec3 toWorld(Entity e, Vec3 local, float partial) {
        float yaw = bodyYaw(e, partial) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double rx = -fz, rz = fx; // direita = frente girada 90° no sentido horário (visto de cima)
        Vec3 p = e.getPosition(partial);
        return new Vec3(p.x + rx * local.x + fx * local.z, p.y + local.y, p.z + rz * local.x + fz * local.z);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STREAMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != streamLevel) {
            STREAMS.clear();
            return;
        }
        if (mc.isPaused()) return;
        RandomSource rnd = mc.level.random;
        Iterator<Stream> it = STREAMS.iterator();
        while (it.hasNext()) {
            Stream s = it.next();
            s.age++;
            if (s.left > 0) {
                s.left--;
                // pulsa no ritmo do coração (um jorro forte e um fraco), e vai perdendo força
                float fade = 0.55F + 0.45F * s.left / (float) s.total;
                float beat = s.pulse ? 0.65F + 0.35F * Mth.abs(Mth.sin(s.age * 0.42F)) : 1F;
                Vec3 o = s.origin.get();
                Vec3 d = s.dir.get();
                if (o == null || d == null) {
                    s.left = 0;
                } else {
                    // a força e o desvio mudam DEVAGAR (de tick em tick, igual pras gotas do mesmo tick):
                    // com um sorteio por gota, gotas mais rápidas passavam as mais lentas e a fita
                    // virava um zigue-zague de varas atravessando a tela
                    s.wob = s.wob * 0.75D + rnd.nextGaussian() * 0.25D;
                    s.jx = s.jx * 0.8D + rnd.nextGaussian() * 0.0025D;
                    s.jy = s.jy * 0.8D + rnd.nextGaussian() * 0.0025D;
                    s.jz = s.jz * 0.8D + rnd.nextGaussian() * 0.0025D;
                    for (int i = 0; i < PER_TICK; i++) {
                        Drop drop = new Drop();
                        double sp = s.speed * fade * beat * (1D + 0.05D * s.wob);
                        drop.vx = d.x * sp + s.jx;
                        drop.vy = d.y * sp + s.jy;
                        drop.vz = d.z * sp + s.jz;
                        // gotas do mesmo tick saem escalonadas no tempo: a fita fica lisa
                        double ahead = 1D - i / (double) PER_TICK;
                        drop.x = o.x + drop.vx * ahead;
                        drop.y = o.y + drop.vy * ahead;
                        drop.z = o.z + drop.vz * ahead;
                        drop.px = drop.x;
                        drop.py = drop.y;
                        drop.pz = drop.z;
                        drop.breakBefore = s.wasOff;
                        s.wasOff = false;
                        s.drops.add(drop);
                    }
                }
            } else {
                s.wasOff = true;
            }
            for (Drop d : s.drops) {
                if (d.dead) continue;
                d.px = d.x;
                d.py = d.y;
                d.pz = d.z;
                d.vy -= GRAVITY;
                d.vx *= 0.985D;
                d.vy *= 0.985D;
                d.vz *= 0.985D;
                d.x += d.vx;
                d.y += d.vy;
                d.z += d.vz;
                d.age++;
                if (d.age > 80 || hits(mc.level, d)) {
                    d.dead = true;
                    // onde cai vira poça (não a cada gota: a cada umas tantas), e espirra
                    if (d.age <= 80 && (s.pools++ % 9 == 0)) {
                        int col = ((int) (s.r * 255) << 16) | ((int) (s.g * 255) << 8) | (int) (s.b * 255);
                        ClientBloodFx.pool(d.x, d.y + 0.3D, d.z, 0.35F, col);
                        ClientBloodFx.droplets(d.x, d.y + 0.05D, d.z, 2, col);
                    }
                }
            }
            // a ponta do jato (as gotas mais velhas) se desfaz em gotas soltas
            if (s.age % 3 == 0 && !s.drops.isEmpty()) {
                Drop tip = null;
                for (Drop d : s.drops) {
                    if (!d.dead) {
                        tip = d;
                        break;
                    }
                }
                if (tip != null && tip.age > 6) {
                    int col = ((int) (s.r * 255) << 16) | ((int) (s.g * 255) << 8) | (int) (s.b * 255);
                    ClientBloodFx.droplets(tip.x, tip.y, tip.z, 1, col);
                }
            }
            s.drops.removeIf(d -> d.dead);
            if (s.left <= 0 && s.drops.isEmpty()) it.remove();
        }
    }

    private static boolean hits(ClientLevel level, Drop d) {
        BlockPos pos = BlockPos.containing(d.x, d.y, d.z);
        if (!level.getFluidState(pos).isEmpty()) return true;
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) return false;
        var shape = st.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        double lx = d.x - pos.getX(), ly = d.y - pos.getY(), lz = d.z - pos.getZ();
        for (var box : shape.toAabbs()) {
            if (box.contains(lx, ly, lz)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STREAMS.clear();
        streamLevel = null;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || STREAMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != streamLevel) return;
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutoutNoCull(TEX);
        VertexConsumer vc = buffers.getBuffer(type);
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (Stream s : STREAMS) {
            Drop prev = null;
            Vec3 pa = null;
            float va = 0F;
            for (Drop d : s.drops) {
                Vec3 p = new Vec3(Mth.lerp(pt, d.px, d.x), Mth.lerp(pt, d.py, d.y), Mth.lerp(pt, d.pz, d.z));
                // não liga gotas longe uma da outra (vira vara), nem desenha colado na câmera (tampa a tela)
                boolean link = prev != null && !d.breakBefore && pa.distanceToSqr(p) < 0.35D * 0.35D
                        && cam.distanceToSqr(p) > 0.5D * 0.5D && cam.distanceToSqr(pa) > 0.5D * 0.5D;
                if (link) {
                    // largura: grossa saindo do corpo, afina na ponta
                    float wa = s.width * Mth.clamp(1.2F - prev.age / 40F, 0.35F, 1.2F);
                    float wb = s.width * Mth.clamp(1.2F - d.age / 40F, 0.35F, 1.2F);
                    float vb = va + (float) pa.distanceTo(p) * 2F;
                    segment(vc, m, n, level, cam, pa, p, wa, wb, va, vb, s.r, s.g, s.b);
                    va = vb;
                }
                prev = d;
                pa = p;
            }
        }
        ps.popPose();
        buffers.endBatch(type);
    }

    private static void segment(VertexConsumer vc, Matrix4f m, Matrix3f n, ClientLevel level, Vec3 cam,
                                Vec3 a, Vec3 b, float wa, float wb, float va, float vb, float r, float g, float bl) {
        Vec3 along = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5D);
        Vec3 toCam = cam.subtract(mid);
        Vec3 side = along.cross(toCam);
        if (side.lengthSqr() < 1.0E-8D) return;
        side = side.normalize();
        Vec3 sa = side.scale(wa * 0.5D), sb = side.scale(wb * 0.5D);
        int la = LevelRenderer.getLightColor(level, BlockPos.containing(a));
        int lb = LevelRenderer.getLightColor(level, BlockPos.containing(b));
        Vector3f nn = toCam.normalize().toVector3f();
        put(vc, m, n, a.add(sa), 0F, va, la, r, g, bl, nn);
        put(vc, m, n, a.subtract(sa), 1F, va, la, r, g, bl, nn);
        put(vc, m, n, b.subtract(sb), 1F, vb, lb, r, g, bl, nn);
        put(vc, m, n, b.add(sb), 0F, vb, lb, r, g, bl, nn);
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int light,
                            float r, float g, float b, Vector3f nn) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, 1F).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nn.x(), nn.y(), nn.z()).endVertex();
    }
}
