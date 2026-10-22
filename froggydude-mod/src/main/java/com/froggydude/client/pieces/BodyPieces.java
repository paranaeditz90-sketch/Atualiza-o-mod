package com.froggydude.client.pieces;

import com.froggydude.client.ClientBloodFx;
import com.froggydude.config.FroggyClientConfig;
import com.froggydude.config.GoreLevel;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * O corpo despedaçado (v0.7, LM: "o mais próximo possível do mod original" - o
 * Physics Mod, que no celular não roda). Quem o FroggyDude mata se desmonta
 * nos cubos do próprio modelo - cabeça, tronco, braços, pernas, com a skin, a
 * roupa e a armadura - que caem, quicam, rolam e se amontoam, tingidos de
 * sangue; ele deita por cima e fica "comendo" (FroggyFeastGoal) e os pedaços
 * pulam a cada mordida. Tudo do lado do cliente, como no Physics Mod: cada um
 * vê o seu (e o nível de gore dele decide - Desligado = morte normal).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BodyPieces {

    /** 3 passos de física por tick (60 por segundo). */
    private static final int SUBSTEPS = 3;
    private static final float DT = 1F / (20F * SUBSTEPS);
    /** Os pedaços do mesmo corpo não se batem no primeiro meio segundo. */
    private static final int SAME_BODY_GRACE = 10;
    /** Os últimos 2 s: afunda no chão e some. */
    private static final int SINK_TICKS = 40;

    private static final List<Piece> PIECES = new ArrayList<>();
    /** Jogadores despedaçados (mortos): o corpo inteiro não é desenhado até renascer. */
    private static final Set<Integer> HIDDEN = new HashSet<>();
    private static ClientLevel pieceLevel;
    private static int nextBody;

    private BodyPieces() {
    }

    // ---------------------------------------------------------------- despedaçar

    /**
     * Servidor mandou: o FroggyDude matou essa entidade. dir = pra onde o golpe
     * empurra (do Froggy pra ela), power ~1, color = cor do sangue, bleeds = sangra.
     */
    public static void breakEntity(int id, double dx, double dy, double dz, float power, int color, boolean bleeds) {
        GoreLevel g = FroggyClientConfig.gore();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!g.any() || level == null) return;
        Entity e = level.getEntity(id);
        if (!(e instanceof LivingEntity victim) || HIDDEN.contains(id)) return;
        List<ModelCapture.Cube> cubes = ModelCapture.capture(victim);
        if (cubes.isEmpty()) return;
        if (pieceLevel != level) {
            PIECES.clear();
            pieceLevel = level;
        }
        RandomSource r = level.random;
        Vec3 base = victim.position();
        Vec3 mid = base.add(0, victim.getBbHeight() * 0.5D, 0);
        Vec3 drift = victim.getDeltaMovement().scale(20D);
        int body = nextBody++;
        int before = PIECES.size();
        int life = FroggyClientConfig.piecesSeconds() * 20;
        for (List<ModelCapture.Cube> group : ModelCapture.group(cubes)) {
            Piece p = build(body, group, base);
            p.life = life + r.nextInt(40);
            p.bleeds = bleeds;
            p.bloodColor = color;
            if (bleeds) tint(p, color, g, r);
            // o corpo se desmonta: cada parte sai um pouco pra fora e pra cima, levando o tranco
            Vec3 out = new Vec3(p.x, p.y, p.z).subtract(mid);
            out = out.lengthSqr() < 1.0E-4D ? new Vec3(r.nextGaussian(), 0, r.nextGaussian()) : out;
            out = out.normalize();
            float k = power * (0.6F + r.nextFloat() * 0.8F);
            p.vel.set((float) (drift.x + out.x * 1.4F * k + dx * 1.8F * power + r.nextGaussian() * 0.4F),
                    (float) (drift.y + 1.2F + r.nextFloat() * 1.8F * power + dy * power),
                    (float) (drift.z + out.z * 1.4F * k + dz * 1.8F * power + r.nextGaussian() * 0.4F));
            p.ang.set((float) r.nextGaussian() * 5F * power, (float) r.nextGaussian() * 5F * power,
                    (float) r.nextGaussian() * 5F * power);
            PIECES.add(p);
        }
        if (Boolean.getBoolean("froggydude.debug")) {
            System.out.println("[FROGGYDEBUG] pedacos: " + victim.getType().getDescriptionId() + " " + cubes.size()
                    + " cubos -> " + (PIECES.size() - before) + " pedaços");
        }
        // o máximo de pedaços no mundo: some o mais velho
        int max = FroggyClientConfig.piecesMax();
        while (PIECES.size() > max) PIECES.remove(0);
        // o corpo inteiro some (no lugar dele, os pedaços)
        if (victim instanceof Player) {
            HIDDEN.add(id);
        } else {
            level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
    }

    /** Monta o pedaço: o cubo maior manda (caixa de colisão); os outros vão junto. */
    private static Piece build(int body, List<ModelCapture.Cube> group, Vec3 base) {
        ModelCapture.Cube main = group.get(0);
        Quaternionf q = ModelCapture.rotation(main);
        Quaternionf inv = new Quaternionf(q).conjugate();
        Vector3f half = new Vector3f((float) main.half[0], (float) main.half[1], (float) main.half[2]);
        Vec3 c = main.center;
        float radius = 0F;
        List<Vector3f> samples = new ArrayList<>();
        List<Piece.Draw> draws = new ArrayList<>();
        Vector3f tmp = new Vector3f();
        for (ModelCapture.Cube cube : group) {
            float[] src = cube.quads;
            float[] dst = new float[src.length];
            for (int i = 0; i < src.length; i += ModelCapture.V) {
                tmp.set((float) (src[i] - c.x), (float) (src[i + 1] - c.y), (float) (src[i + 2] - c.z)).rotate(inv);
                dst[i] = tmp.x;
                dst[i + 1] = tmp.y;
                dst[i + 2] = tmp.z;
                System.arraycopy(src, i + 3, dst, i + 3, 6);
                tmp.set(src[i + 9], src[i + 10], src[i + 11]).rotate(inv);
                dst[i + 9] = tmp.x;
                dst[i + 10] = tmp.y;
                dst[i + 11] = tmp.z;
            }
            draws.add(new Piece.Draw(cube.type, dst));
            for (Vec3 k : cube.corners) {
                Vector3f s = new Vector3f((float) (k.x - c.x), (float) (k.y - c.y), (float) (k.z - c.z)).rotate(inv);
                boolean dup = false;
                for (Vector3f o : samples) {
                    if (o.distanceSquared(s) < 1.0E-4F) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) samples.add(s);
                radius = Math.max(radius, s.length());
            }
        }
        // a caixa principal com folga mínima (cubo achatado ainda colide)
        half.set(Math.max(half.x, 0.02F), Math.max(half.y, 0.02F), Math.max(half.z, 0.02F));
        Piece p = new Piece(body, half, radius);
        p.draws.addAll(draws);
        p.samples.addAll(samples);
        p.x = p.xo = base.x + c.x;
        p.y = p.yo = base.y + c.y;
        p.z = p.zo = base.z + c.z;
        p.rot.set(q);
        p.rotO.set(q);
        return p;
    }

    /** Ensanguentado: uns mais, outros menos (o Grox: quase tudo vermelho, a cabeça com a cor dela). */
    private static void tint(Piece p, int color, GoreLevel g, RandomSource r) {
        float cr = ((color >> 16) & 0xFF) / 255F, cg = ((color >> 8) & 0xFF) / 255F, cb = (color & 0xFF) / 255F;
        float k = (0.35F + r.nextFloat() * 0.45F) * Math.min(1F, g.amount);
        p.tr = Mth.lerp(k, 1F, cr * 1.15F);
        p.tg = Mth.lerp(k, 1F, cg * 1.15F);
        p.tb = Mth.lerp(k, 1F, cb * 1.15F);
    }

    // ---------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != pieceLevel) {
            if (!PIECES.isEmpty() && level != pieceLevel) PIECES.clear();
            HIDDEN.clear();
            pieceLevel = level;
            FeastSounds.clear();
            return;
        }
        // quem renasceu volta a aparecer
        HIDDEN.removeIf(id -> !(level.getEntity(id) instanceof Player p) || !p.isDeadOrDying());
        FeastSounds.tick(level);
        if (PIECES.isEmpty() || mc.isPaused()) return;

        jostle(level);
        for (Piece p : PIECES) {
            p.xo = p.x;
            p.yo = p.y;
            p.zo = p.z;
            p.rotO.set(p.rot);
        }
        for (int s = 0; s < SUBSTEPS; s++) {
            for (Piece p : PIECES) p.step(level, DT);
            for (int i = 0; i < PIECES.size(); i++) {
                Piece a = PIECES.get(i);
                for (int j = i + 1; j < PIECES.size(); j++) {
                    Piece b = PIECES.get(j);
                    if (a.asleep && b.asleep) continue;
                    // do mesmo corpo, no começo: saem encavalados (a perna dentro da túnica) - se soltam primeiro
                    if (a.body == b.body && a.age < SAME_BODY_GRACE) continue;
                    if (!a.asleep) a.collide(b);
                    if (!b.asleep) b.collide(a);
                }
            }
        }
        GoreLevel g = FroggyClientConfig.gore();
        RandomSource r = level.random;
        PIECES.removeIf(p -> ++p.age >= p.life);
        for (Piece p : PIECES) {
            int left = p.life - p.age;
            if (left < SINK_TICKS) {
                // afundando no chão no fim
                p.y -= 0.012D;
                p.asleep = true;
            } else if (p.asleep && p.age % 20 == 0 && !supported(level, p)) {
                p.wake();
            }
            if (!p.bleeds || !g.any()) continue;
            // voando, pinga; batendo forte, espirra
            float speed = p.vel.length();
            if (speed > 2.5F && r.nextInt(3) == 0) {
                ClientBloodFx.droplets(p.x, p.y, p.z, 1, p.bloodColor);
            }
            if (p.lastImpact > 3.0F) {
                ClientBloodFx.droplets(p.x, p.y + 0.05D, p.z, 2 + r.nextInt(3), p.bloodColor);
            }
            // parou: a poça embaixo
            if (p.asleep && !p.pooled && g.pools() && left > SINK_TICKS) {
                p.pooled = true;
                if (r.nextInt(3) != 0) {
                    ClientBloodFx.pool(p.x, p.y - p.radius * 0.6D, p.z, 0.35F + p.radius * 0.8F, p.bloodColor);
                }
            }
        }
    }

    /** Sem nada embaixo (quebraram o bloco): volta a cair. */
    private static boolean supported(ClientLevel level, Piece p) {
        Vector3f w = new Vector3f();
        for (Vector3f s : p.samples) {
            p.worldPoint(s, w);
            BlockPos bp = BlockPos.containing(w.x, w.y - 0.05D, w.z);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) return true;
        }
        for (Piece o : PIECES) {
            if (o != p && o.y < p.y && Math.abs(o.x - p.x) < o.radius + p.radius
                    && Math.abs(o.z - p.z) < o.radius + p.radius && p.y - o.y < o.radius + p.radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ele em cima dos pedaços, "comendo" (FEAST): a cada mordida, o que está
     * embaixo da boca dele pula um tico e gira (no ritmo do nhum nhum).
     */
    private static void jostle(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        RandomSource r = level.random;
        for (FroggydudeEntity f : level.getEntitiesOfClass(FroggydudeEntity.class, mc.player.getBoundingBox().inflate(64))) {
            if (f.getFroggyState() != FroggyState.FEAST || !f.feastBiteNow()) continue;
            float yaw = f.yBodyRot * Mth.DEG_TO_RAD;
            double mx = f.getX() - Mth.sin(yaw) * 1.0D, mz = f.getZ() + Mth.cos(yaw) * 1.0D;
            for (Piece p : PIECES) {
                double dx = p.x - mx, dz = p.z - mz;
                if (dx * dx + dz * dz > 1.1D * 1.1D || Math.abs(p.y - f.getY()) > 1.2D) continue;
                if (r.nextInt(3) == 0) continue;
                p.wake();
                float up = 0.6F + r.nextFloat() * 0.9F;
                p.vel.add((float) r.nextGaussian() * 0.35F, up, (float) r.nextGaussian() * 0.35F);
                p.ang.add((float) r.nextGaussian() * 3F, (float) r.nextGaussian() * 3F, (float) r.nextGaussian() * 3F);
                if (p.bleeds && FroggyClientConfig.gore().any() && r.nextInt(2) == 0) {
                    ClientBloodFx.droplets(p.x, p.y + 0.1D, p.z, 1 + r.nextInt(2), p.bloodColor);
                }
            }
        }
    }

    // ---------------------------------------------------------------- desenho

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || PIECES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != pieceLevel) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Set<RenderType> used = new LinkedHashSet<>();
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        Quaternionf q = new Quaternionf();
        Vector3f v = new Vector3f(), n = new Vector3f();
        for (Piece p : PIECES) {
            double px = Mth.lerp(partial, p.xo, p.x), py = Mth.lerp(partial, p.yo, p.y), pz = Mth.lerp(partial, p.zo, p.z);
            double ddx = px - cam.x, ddy = py - cam.y, ddz = pz - cam.z;
            if (ddx * ddx + ddy * ddy + ddz * ddz > 80 * 80) continue;
            if (!event.getFrustum().isVisible(new AABB(px - p.radius, py - p.radius, pz - p.radius,
                    px + p.radius, py + p.radius, pz + p.radius))) {
                continue;
            }
            p.rotO.slerp(p.rot, partial, q);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(px, py + 0.15D, pz));
            for (Piece.Draw d : p.draws) {
                VertexConsumer vc = buffers.getBuffer(d.type);
                used.add(d.type);
                float[] a = d.verts;
                for (int i = 0; i < a.length; i += ModelCapture.V) {
                    v.set(a[i], a[i + 1], a[i + 2]).rotate(q).add((float) ddx, (float) ddy, (float) ddz);
                    n.set(a[i + 9], a[i + 10], a[i + 11]).rotate(q);
                    vc.vertex(m, v.x, v.y, v.z)
                            .color(a[i + 3] * p.tr, a[i + 4] * p.tg, a[i + 5] * p.tb, a[i + 6])
                            .uv(a[i + 7], a[i + 8])
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(light)
                            .normal(nm, n.x, n.y, n.z)
                            .endVertex();
                }
            }
        }
        for (RenderType t : used) buffers.endBatch(t);
    }

    /** Jogador despedaçado: não desenha o corpo inteiro por cima dos pedaços. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (HIDDEN.contains(event.getEntity().getId()) && event.getEntity().isDeadOrDying()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PIECES.clear();
        HIDDEN.clear();
        pieceLevel = null;
        FeastSounds.clear();
    }

    /** Pra testes/depuração: quantos pedaços e quantos dormindo. */
    public static String debugSummary() {
        int asleep = 0;
        for (Piece p : PIECES) if (p.asleep) asleep++;
        return PIECES.size() + " pedaços (" + asleep + " parados)";
    }
}
