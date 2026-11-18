package com.froggydude.client.gore;

import com.froggydude.client.BloodParticle;
import com.froggydude.client.ClientBloodFx;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * O sangue JORRANDO (Extremo): um jato contínuo que sai do ferimento, faz o
 * arco da gravidade e vira poça onde cai. Se quem sangra se mexe, o jato vai
 * junto (é preso no corpo). Igual ao jorro do ombro da zebra (77,3-80,6 s do
 * vídeo do LM): uma COLUNA DE CUBINHOS de sangue subindo, cada um do tamanho
 * dos pedaços normais.
 *
 * (LM: "essa cobra ficou meio estranha") Antes as gotas eram ligadas numa fita
 * líquida; com o braço sacudindo na boca a origem pulava de lado a cada tick e
 * a fita virava uma cobra em zigue-zague. Agora cada gota é um cubinho solto
 * (BloodParticle: gruda na skin de quem acertar, some perto da câmera) e aqui
 * só fica a conta de onde cai, pra fazer a poça.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BloodStreams {

    private static final int MAX_STREAMS = 48;
    /** Cubinhos por tick: o jorro é cheio, mas não uma parede. */
    private static final int PER_TICK = 3;
    /** A mesma física da BloodParticle (gravidade 1,5 x 0,04 e atrito 0,97): a poça cai onde o cubinho cai. */
    private static final double GRAVITY = 0.06D;
    private static final double FRICTION = 0.97D;

    private static final List<Stream> STREAMS = new ArrayList<>();
    private static ClientLevel streamLevel;

    private BloodStreams() {
    }

    private static final class Drop {
        double x, y, z, vx, vy, vz;
        int age;
        boolean dead;
    }

    /** Um jato. A origem e a direção vêm de fora (presas num corpo, num toco de braço...). */
    public static final class Stream {
        final Supplier<Vec3> origin;
        final Supplier<Vec3> dir;
        final float speed;
        final int total;
        int left;
        final float r, g, b;
        final boolean pulse;
        final List<Drop> drops = new ArrayList<>();
        int age;
        int pools;
        /** Variação lenta da força (ruído suave, igual pra todas as gotas do tick). */
        double wob;
        double jx, jy, jz;

        Stream(Supplier<Vec3> origin, Supplier<Vec3> dir, float speed, int ticks, int color, boolean pulse) {
            this.origin = origin;
            this.dir = dir;
            this.speed = speed;
            this.total = Math.max(1, ticks);
            this.left = ticks;
            this.r = ((color >> 16) & 255) / 255F;
            this.g = ((color >> 8) & 255) / 255F;
            this.b = (color & 255) / 255F;
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
                                boolean pulse) {
        if (!ready()) return null;
        if (STREAMS.size() >= MAX_STREAMS) STREAMS.remove(0);
        Stream s = new Stream(origin, dir, speed, ticks, color, pulse);
        STREAMS.add(s);
        return s;
    }

    /** Jato parado no mundo (respingo forte de um golpe). */
    public static Stream fixed(Vec3 at, Vec3 dir, float speed, int ticks, int color) {
        Vec3 d = dir.normalize();
        return custom(() -> at, () -> d, speed, ticks, color, false);
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
            // jogador que renasceu volta com o MESMO número de entidade, mas é outro corpo:
            // o jato do toco parava nele e sujava a skin nova (LM, v0.4.9) - aqui para de jorrar
            if (e instanceof Player && now != null && now != e) return null;
            Entity src = now != null ? now : e;
            return toWorld(src, local, 1F);
        };
        Supplier<Vec3> direction = () -> {
            Entity now = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
            float dy = (now == null ? yaw0 : bodyYaw(now, 1F)) - yaw0;
            return d0.yRot(-dy * Mth.DEG_TO_RAD);
        };
        return custom(origin, direction, speed, ticks, color, true);
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
                    // o jorro sobe como coluna, não como chuveiro
                    s.wob = s.wob * 0.75D + rnd.nextGaussian() * 0.25D;
                    s.jx = s.jx * 0.8D + rnd.nextGaussian() * 0.0025D;
                    s.jy = s.jy * 0.8D + rnd.nextGaussian() * 0.0025D;
                    s.jz = s.jz * 0.8D + rnd.nextGaussian() * 0.0025D;
                    for (int i = 0; i < PER_TICK; i++) {
                        Drop drop = new Drop();
                        // x1,15: a gravidade do cubinho é mais forte que a da fita antiga; o jorro sobe igual
                        double sp = s.speed * 1.15D * fade * beat * (1D + 0.05D * s.wob);
                        drop.vx = d.x * sp + s.jx;
                        drop.vy = d.y * sp + s.jy;
                        drop.vz = d.z * sp + s.jz;
                        // gotas do mesmo tick saem escalonadas no tempo: a coluna fica cheia, sem buracos
                        double ahead = 1D - i / (double) PER_TICK;
                        drop.x = o.x + drop.vx * ahead;
                        drop.y = o.y + drop.vy * ahead;
                        drop.z = o.z + drop.vz * ahead;
                        s.drops.add(drop);
                        cube(mc, s, drop);
                    }
                }
            }
            for (Drop d : s.drops) {
                if (d.dead) continue;
                d.vy -= GRAVITY;
                d.x += d.vx;
                d.y += d.vy;
                d.z += d.vz;
                d.vx *= FRICTION;
                d.vy *= FRICTION;
                d.vz *= FRICTION;
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

    /** O cubinho que a gente vê: sai junto com a gota, com a mesma velocidade. */
    private static void cube(Minecraft mc, Stream s, Drop d) {
        Particle p = mc.particleEngine.createParticle(ModParticles.BLOOD.get(), d.x, d.y, d.z, d.vx, d.vy, d.vz);
        if (p instanceof BloodParticle bp) {
            int col = ((int) (s.r * 255) << 16) | ((int) (s.g * 255) << 8) | (int) (s.b * 255);
            bp.tint(col);
            // do tamanho dos pedaços normais (os gigantes pareciam bloco voando)
            bp.size(0.7F + mc.level.random.nextFloat() * 0.45F);
            bp.life(60 + mc.level.random.nextInt(30));
        }
    }
}
