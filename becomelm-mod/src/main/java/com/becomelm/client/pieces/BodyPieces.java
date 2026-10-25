package com.becomelm.client.pieces;

import com.becomelm.BecomeLM;
import com.becomelm.client.LmActsClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * O corpo despedaçado (o mesmo do Froggydude): quem o FroggyDude mata se desmonta nos cubos
 * do próprio modelo - cabeça, tronco, braços, pernas, com a skin, a roupa e a armadura -
 * estilhaçados em cacos que caem, quicam, rolam e se amontoam, tingidos de sangue. Ele deita
 * por cima (o "monte de pedaços" dos vídeos) e os cacos embaixo da cara pulam quando a cabeça
 * esfrega. Tudo do lado do cliente, como no Physics Mod: cada um vê o seu.
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BodyPieces {

    /** 3 passos de física por tick (60 por segundo). */
    private static final int SUBSTEPS = 3;
    private static final float DT = 1F / (20F * SUBSTEPS);
    /** Os pedaços do mesmo corpo não se batem no primeiro meio segundo. */
    private static final int SAME_BODY_GRACE = 10;
    /** Os últimos 2 s: afunda no chão e some. */
    private static final int SINK_TICKS = 40;
    /** Quanto tempo o monte fica (s), quantos cacos por corpo (o vídeo: ~60) e o máximo no mundo. */
    private static final int LIFE_SECONDS = 90;
    private static final int SHARDS = 60;
    private static final int MAX = 900;

    private static final List<Piece> PIECES = new ArrayList<>();
    /** Jogadores despedaçados (mortos): o corpo inteiro não é desenhado até renascer. */
    private static final Set<Integer> HIDDEN = new HashSet<>();
    private static ClientLevel pieceLevel;
    private static int nextBody;
    /** Desenhando a vítima "de mentira" pra recortar (a câmera da vítima não pode escondê-la). */
    public static boolean capturing;
    private static int squishCooldown;

    private BodyPieces() {
    }

    // ---------------------------------------------------------------- despedaçar

    /**
     * O servidor mandou: o FroggyDude matou essa entidade. dir = pra onde o golpe empurra, power
     * ~1 (montado em cima: pouco, o monte fica embaixo dele), color = cor do sangue.
     */
    public static void breakEntity(int id, double dx, double dy, double dz, float power, int color, boolean bleeds) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity e = level.getEntity(id);
        if (!(e instanceof LivingEntity victim) || HIDDEN.contains(id)) return;
        List<ModelCapture.Cube> cubes;
        capturing = true;
        try {
            cubes = ModelCapture.capture(victim);
        } finally {
            capturing = false;
        }
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
        int life = LIFE_SECONDS * 20;
        List<List<ModelCapture.Cube>> groups = ModelCapture.group(cubes);
        double all = 0D;
        for (List<ModelCapture.Cube> grp : groups) all += grp.get(0).volume();
        for (List<ModelCapture.Cube> group : groups) {
            // cada parte estilhaça em cacos, mais cacos nas partes maiores
            int n = Math.max(1, (int) Math.round(SHARDS * group.get(0).volume() / Math.max(1.0E-6D, all)));
            ModelCapture.Cube main = group.get(0);
            Quaternionf q = ModelCapture.rotation(main);
            Vec3 center = base.add(main.center);
            for (Fracture.Shard sh : shatter(group, n, r)) {
                Piece p = shardPiece(body, sh, q, center);
                p.life = life + r.nextInt(40);
                p.bleeds = bleeds;
                p.bloodColor = color;
                if (bleeds) tint(p, color, r);
                // o corpo se desmonta: cada caco sai pra fora (do corpo e da parte) e pra cima
                Vec3 out = new Vec3(p.x, p.y, p.z).subtract(mid);
                Vec3 burst = new Vec3(p.x, p.y, p.z).subtract(center);
                out = out.lengthSqr() < 1.0E-4D ? new Vec3(r.nextGaussian(), 0, r.nextGaussian()) : out.normalize();
                burst = burst.lengthSqr() < 1.0E-6D ? Vec3.ZERO : burst.normalize();
                float k = power * (0.5F + r.nextFloat() * 0.9F);
                p.vel.set((float) (drift.x + out.x * 1.2F * k + burst.x * 1.1F * k + dx * 1.6F * power + r.nextGaussian() * 0.45F * power),
                        (float) (drift.y + power + r.nextFloat() * 1.9F * power + burst.y * 0.8F * k + dy * power),
                        (float) (drift.z + out.z * 1.2F * k + burst.z * 1.1F * k + dz * 1.6F * power + r.nextGaussian() * 0.45F * power));
                p.ang.set((float) r.nextGaussian() * 7F * power, (float) r.nextGaussian() * 7F * power,
                        (float) r.nextGaussian() * 7F * power);
                PIECES.add(p);
            }
        }
        if (bleeds) LmBlood.spray(mid.x, mid.y, mid.z, dx, 0.6D, dz, 24, 0.9F, color);
        while (PIECES.size() > MAX) PIECES.remove(0);
        // o corpo inteiro some (no lugar dele, os pedaços)
        if (victim instanceof Player) {
            HIDDEN.add(id);
        } else {
            level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
    }

    /** Os cubos da parte no espaço dela e o estilhaço (os penduricalhos vão inteiros). */
    private static List<Fracture.Shard> shatter(List<ModelCapture.Cube> group, int n, RandomSource r) {
        ModelCapture.Cube main = group.get(0);
        Quaternionf inv = ModelCapture.rotation(main).conjugate();
        Vec3 c = main.center;
        List<RenderType> types = new ArrayList<>(), smallTypes = new ArrayList<>();
        List<float[]> big = new ArrayList<>(), small = new ArrayList<>();
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
            if (cube != main && cube.volume() < main.volume() * 0.05D) {
                small.add(dst);
                smallTypes.add(cube.type);
            } else {
                big.add(dst);
                types.add(cube.type);
            }
        }
        return Fracture.shatter(types, big, smallTypes, small, n, r);
    }

    /** Um caco vira um pedaço: centro no meio dos cantos dele, mesma rotação da parte. */
    private static Piece shardPiece(int body, Fracture.Shard sh, Quaternionf q, Vec3 partCenter) {
        float cx = 0, cy = 0, cz = 0;
        for (Vector3f p : sh.points) {
            cx += p.x;
            cy += p.y;
            cz += p.z;
        }
        cx /= sh.points.size();
        cy /= sh.points.size();
        cz /= sh.points.size();
        float hx = 0, hy = 0, hz = 0, radius = 0;
        List<Vector3f> samples = new ArrayList<>(sh.points.size());
        for (Vector3f p : sh.points) {
            Vector3f s = new Vector3f(p.x - cx, p.y - cy, p.z - cz);
            samples.add(s);
            hx = Math.max(hx, Math.abs(s.x));
            hy = Math.max(hy, Math.abs(s.y));
            hz = Math.max(hz, Math.abs(s.z));
            radius = Math.max(radius, s.length());
        }
        Piece piece = new Piece(body, new Vector3f(Math.max(hx, 0.02F), Math.max(hy, 0.02F), Math.max(hz, 0.02F)), radius);
        piece.samples.addAll(samples);
        for (java.util.Map.Entry<RenderType, List<Fracture.Poly>> e : sh.polys.entrySet()) {
            float[] quads = Fracture.toQuads(e.getValue());
            for (int i = 0; i < quads.length; i += ModelCapture.V) {
                quads[i] -= cx;
                quads[i + 1] -= cy;
                quads[i + 2] -= cz;
            }
            piece.draws.add(new Piece.Draw(e.getKey(), quads));
        }
        Vector3f w = new Vector3f(cx, cy, cz).rotate(q);
        piece.x = piece.xo = partCenter.x + w.x;
        piece.y = piece.yo = partCenter.y + w.y;
        piece.z = piece.zo = partCenter.z + w.z;
        piece.rot.set(q);
        piece.rotO.set(q);
        return piece;
    }

    /** Ensanguentado: uns mais, outros menos. */
    private static void tint(Piece p, int color, RandomSource r) {
        float cr = ((color >> 16) & 0xFF) / 255F, cg = ((color >> 8) & 0xFF) / 255F, cb = (color & 0xFF) / 255F;
        float k = 0.35F + r.nextFloat() * 0.45F;
        p.tr = Mth.lerp(k, 1F, cr * 1.15F);
        p.tg = Mth.lerp(k, 1F, cg * 1.15F);
        p.tb = Mth.lerp(k, 1F, cb * 1.15F);
    }

    /**
     * O meio do monte de pedaços perto desse ponto (na horizontal), ou null se ali não tem monte
     * (pelo menos 6 cacos).
     */
    @Nullable
    public static Vec3 pileNear(Vec3 at, double radius) {
        double sx = 0, sy = 0, sz = 0;
        int n = 0;
        for (Piece p : PIECES) {
            double dx = p.x - at.x, dz = p.z - at.z;
            if (dx * dx + dz * dz > radius * radius || Math.abs(p.y - at.y) > 2D) continue;
            sx += p.x;
            sy += p.y;
            sz += p.z;
            n++;
        }
        return n < 6 ? null : new Vec3(sx / n, sy / n, sz / n);
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
            return;
        }
        // quem renasceu volta a aparecer
        HIDDEN.removeIf(id -> !(level.getEntity(id) instanceof Player p) || !p.isDeadOrDying());
        if (PIECES.isEmpty() || mc.isPaused()) return;
        nuzzle(level);
        trample(level);
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
                    // do mesmo corpo, no começo: saem encavalados - se soltam primeiro
                    if (a.body == b.body && a.age < SAME_BODY_GRACE) continue;
                    if (!a.asleep) a.collide(b);
                    if (!b.asleep) b.collide(a);
                }
            }
        }
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
            if (!p.bleeds) continue;
            // voando, pinga; batendo forte, espirra
            float speed = p.vel.length();
            if (speed > 2.5F && r.nextInt(3) == 0) LmBlood.droplets(p.x, p.y, p.z, 1, p.bloodColor);
            if (p.lastImpact > 3.0F) LmBlood.droplets(p.x, p.y + 0.05D, p.z, 2 + r.nextInt(3), p.bloodColor);
            // parou: a poça embaixo
            if (p.asleep && !p.pooled && left > SINK_TICKS) {
                p.pooled = true;
                if (r.nextInt(3) != 0) LmBlood.pool(p.x, p.y - p.radius * 0.6D, p.z, 0.35F + p.radius * 0.8F, p.bloodColor);
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
     * Deitado no monte: quando a cabeça esfrega ou afunda, os cacos embaixo da cara pulam um
     * tico e giram (e fazem o barulho molhado).
     */
    private static void nuzzle(ClientLevel level) {
        RandomSource r = level.random;
        for (Player f : level.players()) {
            Vec3 head = LmActsClient.pileDip(f);
            if (head == null) continue;
            boolean squished = false;
            for (Piece p : PIECES) {
                double dx = p.x - head.x, dz = p.z - head.z;
                if (dx * dx + dz * dz > 0.8D * 0.8D || Math.abs(p.y - head.y) > 1.2D) continue;
                if (r.nextInt(3) == 0) continue;
                p.wake();
                p.vel.add((float) r.nextGaussian() * 0.3F, 0.5F + r.nextFloat() * 0.7F, (float) r.nextGaussian() * 0.3F);
                p.ang.add((float) r.nextGaussian() * 3F, (float) r.nextGaussian() * 3F, (float) r.nextGaussian() * 3F);
                if (p.bleeds && r.nextInt(2) == 0) LmBlood.droplets(p.x, p.y + 0.1D, p.z, 1 + r.nextInt(2), p.bloodColor);
                if (!squished && p.bleeds) {
                    squished = true;
                    level.playLocalSound(p.x, p.y, p.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.35F,
                            0.6F + r.nextFloat() * 0.3F, false);
                }
            }
        }
    }

    /**
     * Quem passa por cima empurra os cacos (pisar no monte ensanguentado faz um squish). Quem
     * está deitado no monte não conta (ia espalhar o monte em que ele está).
     */
    private static void trample(ClientLevel level) {
        if (squishCooldown > 0) squishCooldown--;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Piece p : PIECES) {
            minX = Math.min(minX, p.x - p.radius);
            minY = Math.min(minY, p.y - p.radius);
            minZ = Math.min(minZ, p.z - p.radius);
            maxX = Math.max(maxX, p.x + p.radius);
            maxY = Math.max(maxY, p.y + p.radius);
            maxZ = Math.max(maxZ, p.z + p.radius);
        }
        AABB all = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.5D);
        RandomSource r = level.random;
        Vector3f w = new Vector3f();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, all,
                en -> en.isAlive() && !en.isSpectator() && !HIDDEN.contains(en.getId()))) {
            if (e instanceof Player pl && LmActsClient.onPile(pl)) continue;
            AABB box = e.getBoundingBox().inflate(0.04D);
            double vx = (e.getX() - e.xo) * 20D, vy = (e.getY() - e.yo) * 20D, vz = (e.getZ() - e.zo) * 20D;
            double speed = Math.sqrt(vx * vx + vz * vz);
            // parado em cima do monte não mexe em nada (só passando ou caindo em cima)
            if (speed < 0.8D && vy > -2D) continue;
            if (speed > 7D) {
                vx *= 7D / speed;
                vz *= 7D / speed;
            }
            for (Piece p : PIECES) {
                if (p.x + p.radius < box.minX || p.x - p.radius > box.maxX || p.z + p.radius < box.minZ
                        || p.z - p.radius > box.maxZ || p.y + p.radius < box.minY || p.y - p.radius > box.maxY) {
                    continue;
                }
                boolean hit = false;
                for (Vector3f s : p.samples) {
                    p.worldPoint(s, w);
                    if (box.contains(w.x, w.y, w.z)) {
                        hit = true;
                        break;
                    }
                }
                if (!hit) continue;
                p.wake();
                double ax = p.x - e.getX(), az = p.z - e.getZ();
                double al = Math.sqrt(ax * ax + az * az);
                if (al < 1.0E-3D) {
                    ax = r.nextGaussian();
                    az = r.nextGaussian();
                    al = Math.sqrt(ax * ax + az * az) + 1.0E-6D;
                }
                ax /= al;
                az /= al;
                p.x += ax * 0.04D;
                p.z += az * 0.04D;
                // um chute por passada (o pé fica uns ticks em cima: sem isso soma e o caco decola)
                if (p.age < p.kickedUntil) continue;
                p.kickedUntil = p.age + 6;
                float kick = (float) Math.min(6D, speed);
                float up = (float) (0.7D + r.nextFloat() * 0.5D + kick * 0.18D + Math.max(0D, vy) * 0.2D);
                double push = 0.4D + kick * 0.12D;
                p.vel.set((float) (p.vel.x * 0.3D + ax * push + vx * 0.85D), Math.max(p.vel.y, up),
                        (float) (p.vel.z * 0.3D + az * push + vz * 0.85D));
                p.ang.add((float) r.nextGaussian() * (1.5F + kick * 0.8F), (float) r.nextGaussian() * (1.5F + kick * 0.8F),
                        (float) r.nextGaussian() * (1.5F + kick * 0.8F));
                if (p.bleeds && squishCooldown <= 0) {
                    squishCooldown = 4 + r.nextInt(5);
                    level.playLocalSound(p.x, p.y, p.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.35F,
                            0.7F + r.nextFloat() * 0.3F, false);
                    if (r.nextInt(2) == 0) LmBlood.droplets(p.x, p.y + 0.05D, p.z, 1, p.bloodColor);
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
        Matrix4f pm = new Matrix4f();
        Matrix3f pn = new Matrix3f();
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
            pm.set(m).translate((float) ddx, (float) ddy, (float) ddz).rotate(q);
            pn.set(nm).rotate(q);
            for (Piece.Draw d : p.draws) {
                VertexConsumer vc = buffers.getBuffer(d.type);
                used.add(d.type);
                float[] a = d.verts;
                for (int i = 0; i < a.length; i += ModelCapture.V) {
                    vc.vertex(pm, a[i], a[i + 1], a[i + 2])
                            .color(a[i + 3] * p.tr, a[i + 4] * p.tg, a[i + 5] * p.tb, a[i + 6])
                            .uv(a[i + 7], a[i + 8])
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(light)
                            .normal(pn, a[i + 9], a[i + 10], a[i + 11])
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
    }
}
