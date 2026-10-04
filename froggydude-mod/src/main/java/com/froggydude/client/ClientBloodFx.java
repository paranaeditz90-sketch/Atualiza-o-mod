package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Sangue no cliente:
 * - jato: dezenas de pedaços (BloodParticle) saindo numa direção, mais uma
 *   névoa vermelha;
 * - poça: mancha no chão que vai se espalhando devagar; sangue caindo no mesmo
 *   lugar engrossa a poça em vez de criar outra. Some sozinha depois de uns
 *   minutos (e não aparece dentro d'água).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientBloodFx {

    private static final ResourceLocation POOL_TEXTURE =
            new ResourceLocation(ModEntityTypes.MOD_ID, "textures/misc/blood_pool.png");
    private static final DustParticleOptions MIST =
            new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.02F), 1.6F);

    private static final int MAX_POOLS = 160;
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

        Pool(double x, double y, double z, float target, float angle, boolean flip) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.target = target;
            this.size = target * 0.3F;
            this.sizeO = this.size;
            this.angle = angle;
            this.flip = flip;
        }
    }

    private ClientBloodFx() {
    }

    public static void spray(double x, double y, double z, double dx, double dy, double dz, int count, float power) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            double speed = power * (0.45D + r.nextDouble() * 0.9D);
            double spread = power * 0.38D;
            level.addParticle(ModParticles.BLOOD.get(),
                    x + r.nextGaussian() * 0.06D, y + r.nextGaussian() * 0.06D, z + r.nextGaussian() * 0.06D,
                    dx * speed + r.nextGaussian() * spread,
                    dy * speed + r.nextGaussian() * spread + 0.04D,
                    dz * speed + r.nextGaussian() * spread);
        }
        for (int i = 0; i < count / 3; i++) {
            level.addParticle(MIST, x + r.nextGaussian() * 0.15D, y + r.nextGaussian() * 0.12D,
                    z + r.nextGaussian() * 0.15D, 0, 0, 0);
        }
    }

    public static void pool(double x, double y, double z, float size) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
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
        POOLS.add(new Pool(x, ground + lift, z, Math.min(POOL_MAX, size), r.nextFloat() * 360F, r.nextBoolean()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || POOLS.isEmpty()) return;
        if (Minecraft.getInstance().level != poolLevel) {
            POOLS.clear();
            return;
        }
        if (Minecraft.getInstance().isPaused()) return;
        POOLS.removeIf(p -> {
            p.sizeO = p.size;
            p.size += (p.target - p.size) * 0.035F;   // o sangue vai se espalhando
            return ++p.age > POOL_LIFE;
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
            int left = POOL_LIFE - p.age;
            float alpha = left < POOL_FADE ? Math.max(0F, left / (float) POOL_FADE) : 1F;
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p.x, p.y + 0.1D, p.z));

            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(Axis.YP.rotationDegrees(p.angle));
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float u0 = p.flip ? 1F : 0F;
            float u1 = p.flip ? 0F : 1F;
            vertex(vc, m, n, -half, -half, u0, 0F, alpha, light);
            vertex(vc, m, n, -half, half, u0, 1F, alpha, light);
            vertex(vc, m, n, half, half, u1, 1F, alpha, light);
            vertex(vc, m, n, half, -half, u1, 0F, alpha, light);
            ps.popPose();
        }
        ps.popPose();
        buffers.endBatch(type);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float z, float u, float v,
                               float alpha, int light) {
        vc.vertex(m, x, 0F, z).color(1F, 1F, 1F, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0F, 1F, 0F).endVertex();
    }
}
