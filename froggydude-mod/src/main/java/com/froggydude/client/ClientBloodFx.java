package com.froggydude.client;

import com.froggydude.client.gore.BloodStains;
import com.froggydude.client.gore.BloodStreams;
import com.froggydude.client.gore.ScreenBlood;
import com.froggydude.config.FroggyClientConfig;
import com.froggydude.config.GoreLevel;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Sangue no cliente, do tamanho do nível de gore de QUEM ESTÁ VENDO:
 * - jato: dezenas de pedaços (BloodParticle) da cor do sangue do bicho;
 * - poça: mancha no chão que vai se espalhando devagar; sangue caindo no mesmo
 *   lugar engrossa a poça em vez de criar outra. Some sozinha depois de uns
 *   minutos (e não aparece dentro d'água);
 * - golpe: a vítima espirra pra onde o golpe foi, um pouco volta em quem bateu
 *   (mancha a skin e respinga na tela, se foi tu), cai poça;
 * - explosão (membro arrancado, morte) e jorro (Extremo: jato contínuo de líquido).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientBloodFx {

    private static final ResourceLocation POOL_TEXTURE =
            new ResourceLocation(ModEntityTypes.MOD_ID, "textures/misc/blood_pool.png");

    private static final int MAX_POOLS = 220;
    private static final int POOL_LIFE = 3600;    // 3 minutos
    private static final int POOL_FADE = 400;
    private static final float POOL_MAX = 2.8F;

    private static final List<Pool> POOLS = new ArrayList<>();
    private static ClientLevel poolLevel;
    private static int poolCounter;

    private static final class Pool {
        final double x, y, z;
        final float angle;
        final boolean flip;
        float size;
        float sizeO;
        float target;
        int age;
        final int life;
        float r, g, b;

        Pool(double x, double y, double z, float target, float angle, boolean flip, int color, int life) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.target = target;
            this.size = target * 0.3F;
            this.sizeO = this.size;
            this.angle = angle;
            this.flip = flip;
            this.life = life;
            setColor(color);
        }

        void setColor(int color) {
            r = ((color >> 16) & 255) / 255F * 1.25F;
            g = ((color >> 8) & 255) / 255F * 1.25F;
            b = (color & 255) / 255F * 1.25F;
        }
    }

    private ClientBloodFx() {
    }

    private static GoreLevel gore() {
        return FroggyClientConfig.gore();
    }

    // ---------------------------------------------------------------- pedaços

    private static void particles(double x, double y, double z, double dx, double dy, double dz, int n,
                                  float power, int color, float sizeMul) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || n <= 0) return;
        RandomSource r = level.random;
        for (int i = 0; i < n; i++) {
            double speed = power * (0.45D + r.nextDouble() * 0.9D);
            double spread = power * 0.38D;
            Particle p = mc.particleEngine.createParticle(ModParticles.BLOOD.get(),
                    x + r.nextGaussian() * 0.06D, y + r.nextGaussian() * 0.06D, z + r.nextGaussian() * 0.06D,
                    dx * speed + r.nextGaussian() * spread,
                    dy * speed + r.nextGaussian() * spread + 0.02D,
                    dz * speed + r.nextGaussian() * spread);
            if (p instanceof BloodParticle bp) {
                bp.tint(color);
                if (sizeMul != 1F) bp.size(sizeMul * (0.7F + r.nextFloat() * 0.6F));
            }
        }
    }

    public static void spray(double x, double y, double z, double dx, double dy, double dz, int count, float power, int color) {
        GoreLevel g = gore();
        if (!g.any()) return;
        // em abundância (v0.3.5): 1,6x os pedaços que o servidor pediu (no Pesado)
        particles(x, y, z, dx, dy, dz, Math.round(count * 1.6F * g.amount), power, color, 1F);
        if (g.streams() && power >= 0.3F && count >= 18) {
            BloodStreams.fixed(new Vec3(x, y, z), new Vec3(dx, dy, dz), power * 0.9F, 5 + count / 10, color);
        }
    }

    /** Gotas soltas (pingando de um corpo, a ponta do jato que se desfaz). */
    public static void droplets(double x, double y, double z, int n, int color) {
        if (!gore().any()) return;
        particles(x, y, z, 0, -1, 0, n, 0.04F, color, 0.6F);
    }

    // ---------------------------------------------------------------- golpe

    public static void hit(int victimId, int attackerId, double x, double y, double z, double dx, double dy, double dz,
                           float damage, int color, int flags) {
        GoreLevel g = gore();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!g.any() || level == null) return;
        Entity victim = level.getEntity(victimId);
        Entity attacker = attackerId >= 0 ? level.getEntity(attackerId) : null;
        float dmg = Math.min(damage, 20F);
        int n = Math.round((5 + dmg * 3.2F) * g.amount);
        float power = 0.14F + dmg * 0.022F;
        if ((flags & 1) != 0) {
            // sai pelo outro lado (pra onde o golpe foi), subindo
            particles(x, y, z, dx * 0.8D, dy * 0.8D + 0.55D, dz * 0.8D, n, power, color, 1F);
            // e um pouco volta em quem bateu
            if (attacker != null) particles(x, y, z, -dx * 0.6D, 0.45D, -dz * 0.6D, Math.max(1, n / 3), power * 0.7F, color, 0.8F);
        }
        if ((flags & 2) != 0 && g.pools() && victim != null) {
            pool(victim.getX(), victim.getY() + 0.2D, victim.getZ(), (0.25F + dmg * 0.05F) * Math.max(1F, g.amount * 0.8F), color);
        }
        if ((flags & 1) != 0 && g.streams() && dmg >= 4F) {
            BloodStreams.fixed(new Vec3(x, y, z), new Vec3(dx, Math.max(dy, 0D) + 0.6D, dz), 0.2F + dmg * 0.02F,
                    (int) (6 + dmg), color);
        }
        if (g.skinStains()) {
            if (attacker instanceof Player ap && ap.distanceToSqr(x, y, z) < 3.4D * 3.4D) {
                BloodStains.splashFront(ap, new Vec3(x, y, z), dmg, color);
            }
            if (victim instanceof Player vp) BloodStains.splashAt(vp, new Vec3(x, y, z), new Vec3(dx, dy, dz), dmg, color);
        }
        Player me = mc.player;
        if (me != null && g.screen() && mc.options.getCameraType().isFirstPerson()) {
            if (attacker == me && me.distanceToSqr(x, y, z) < 3.6D * 3.6D) {
                ScreenBlood.splash(dmg, color, me.getMainArm() == HumanoidArm.RIGHT ? 1F : -1F);
            } else if (victim == me && attacker != null && dmg >= 1F) {
                ScreenBlood.splash(dmg * 0.6F, color, 0F);
                if (attacker instanceof FroggydudeEntity) ScreenBlood.film(0.02F + dmg * 0.012F, color);
            }
        }
    }

    // ---------------------------------------------------------------- explosão e jorro

    public static void burst(double x, double y, double z, double dx, double dy, double dz, int count, float power,
                             int color, int sourceId) {
        GoreLevel g = gore();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!g.any() || level == null) return;
        int n = Math.round(count * 1.6F * g.amount);
        particles(x, y, z, dx, dy, dz, n, power, color, 1F);
        // nacos maiores (carne), menos
        particles(x, y, z, dx, dy, dz, Math.max(1, n / 8), power * 0.8F, color, 2.2F);
        particles(x, y, z, 0, -1, 0, Math.max(1, n / 4), 0.08F, color, 0.8F);
        if (g.pools()) pool(x, y, z, Math.min(POOL_MAX, (0.9F + count * 0.02F) * Math.max(1F, g.amount * 0.7F)), color);
        if (g.streams()) BloodStreams.fixed(new Vec3(x, y, z), new Vec3(dx, dy, dz), power * 1.1F, 10, color);
        Player me = mc.player;
        if (me != null) {
            double d2 = me.getEyePosition().distanceToSqr(x, y, z);
            if (d2 < 3.2D * 3.2D) {
                if (g.screen() && mc.options.getCameraType().isFirstPerson()) ScreenBlood.splash(8F, color, 0F);
                if (g.skinStains()) BloodStains.splashFront(me, new Vec3(x, y, z), 8F, color);
            }
        }
        // quem estava colado também fica sujo
        if (g.skinStains()) {
            for (Player p : level.players()) {
                if (p != me && p.distanceToSqr(x, y, z) < 2.5D * 2.5D) BloodStains.splashFront(p, new Vec3(x, y, z), 6F, color);
            }
        }
    }

    public static void stream(int entityId, double lx, double ly, double lz, double dx, double dy, double dz,
                              float speed, int ticks, int color) {
        GoreLevel g = gore();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!g.any() || level == null) return;
        Entity e = entityId >= 0 ? level.getEntity(entityId) : null;
        if (g.streams()) {
            if (e != null) BloodStreams.attached(e, new Vec3(lx, ly, lz), new Vec3(dx, dy, dz), speed, ticks, color);
            else BloodStreams.fixed(new Vec3(lx, ly, lz), new Vec3(dx, dy, dz), speed, ticks, color);
            return;
        }
        // sem o jorro de líquido: o mesmo jato vira pedaços saindo em sequência
        Vec3 at = e != null ? BloodStreams.toWorld(e, new Vec3(lx, ly, lz), 1F) : new Vec3(lx, ly, lz);
        particles(at.x, at.y, at.z, dx, dy, dz, Math.round(ticks * 0.9F * g.amount), speed * 1.4F, color, 1F);
    }

    /** O ponto está colado na câmera de quem joga? (v0.3.4: sangue tampando a tela) */
    public static boolean nearCamera(double x, double y, double z, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || !mc.gameRenderer.getMainCamera().isInitialized()) return false;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        return cam.distanceToSqr(x, y, z) < radius * radius;
    }

    // ---------------------------------------------------------------- poças

    public static void pool(double x, double y, double z, float size, int color) {
        GoreLevel g = gore();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !g.pools()) return;
        if (level != poolLevel) {
            POOLS.clear();
            poolLevel = level;
        }
        // acha o chão (até 4 blocos abaixo)
        BlockPos start = BlockPos.containing(x, y + 0.3D, z);
        double ground = Double.NaN;
        for (int i = 0; i <= 4; i++) {
            BlockPos pos = start.below(i);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double top = pos.getY() + shape.max(Direction.Axis.Y);
                if (top <= y + 0.4D) {
                    if (!level.getFluidState(BlockPos.containing(x, top + 0.05D, z)).isEmpty()) return;
                    ground = top;
                    break;
                }
            }
        }
        if (Double.isNaN(ground)) return;

        for (Pool p : POOLS) {
            double ddx = p.x - x;
            double ddz = p.z - z;
            if (Math.abs(p.y - ground) < 0.2D && ddx * ddx + ddz * ddz < 0.45D * 0.45D) {
                p.target = Math.min(POOL_MAX, p.target + size * 0.4F);
                p.age = 0;
                return;
            }
        }
        if (POOLS.size() >= MAX_POOLS) POOLS.remove(0);
        // cada poça um pouquinho mais alta que a outra: sem "piscar" quando se sobrepõem
        double lift = 0.012D + (poolCounter++ % 12) * 0.0012D;
        RandomSource r = level.random;
        int life = g == GoreLevel.EXTREME ? POOL_LIFE * 3 : POOL_LIFE;
        POOLS.add(new Pool(x, ground + lift, z, Math.min(POOL_MAX, size), r.nextFloat() * 360F, r.nextBoolean(), color, life));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || POOLS.isEmpty()) return;
        if (Minecraft.getInstance().level != poolLevel) {
            POOLS.clear();
            return;
        }
        if (Minecraft.getInstance().isPaused()) return;
        if (!gore().pools()) {
            POOLS.clear();
            return;
        }
        POOLS.removeIf(p -> {
            p.sizeO = p.size;
            p.size += (p.target - p.size) * 0.035F;   // o sangue vai se espalhando
            return ++p.age > p.life;
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        POOLS.clear();
        poolLevel = null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || POOLS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != poolLevel) return;

        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(POOL_TEXTURE);
        VertexConsumer vc = buffers.getBuffer(type);

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        for (Pool p : POOLS) {
            double ddx = p.x - cam.x;
            double ddz = p.z - cam.z;
            if (ddx * ddx + ddz * ddz > 72 * 72) continue;
            float half = Mth.lerp(partial, p.sizeO, p.size) * 0.5F;
            int left = p.life - p.age;
            float alpha = left < POOL_FADE ? Math.max(0F, left / (float) POOL_FADE) : 1F;
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p.x, p.y + 0.1D, p.z));

            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(Axis.YP.rotationDegrees(p.angle));
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float u0 = p.flip ? 1F : 0F;
            float u1 = p.flip ? 0F : 1F;
            vertex(vc, m, n, -half, -half, u0, 0F, p, alpha, light);
            vertex(vc, m, n, -half, half, u0, 1F, p, alpha, light);
            vertex(vc, m, n, half, half, u1, 1F, p, alpha, light);
            vertex(vc, m, n, half, -half, u1, 0F, p, alpha, light);
            ps.popPose();
        }
        ps.popPose();
        buffers.endBatch(type);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v,
                               Pool p, float alpha, int light) {
        vc.vertex(m, x, 0F, z).color(Math.min(1F, p.r), Math.min(1F, p.g), Math.min(1F, p.b), alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0F, 1F, 0F).endVertex();
    }
}
