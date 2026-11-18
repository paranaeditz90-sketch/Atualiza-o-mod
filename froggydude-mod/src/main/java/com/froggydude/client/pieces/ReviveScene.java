package com.froggydude.client.pieces;

import com.froggydude.client.ClientBloodFx;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.Revive;
import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.AxisAngle4f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * (v0.7.35) O renascer com a língua, no cliente (entity.Revive manda os números; réplica v3 aprovada pelo LM, viking
 * 13-renascer-lingua.md):
 * - SEPARA: os pedaços do PRÓPRIO modelo dele (tronco, braços, pernas) voam com a física do gore (Piece: quicam, tombam,
 *   param), cada braço/perna caindo do lado da junta dele, o tronco na frente; a CABEÇA vai num arco até o lugar dela e
 *   cai de cara no chão (P, o lugar que o servidor guardou: é ela quem manda onde o corpo vai ficar);
 * - fica PARADO 5 a 10 s;
 * - cada PUXADA (no tempo fixo do passo do servidor): a cabeça gira pro pedaço, ergue a testa, a língua sai por baixo,
 *   pega o pedaço pela ponta da JUNTA (pescoço/ombro/quadril), estica, arrasta (mola na ponta seguindo um caminho: vem
 *   pra perto, dá a volta pelo lado da junta e entra por trás; dois trancos pra cima = o pedaço pula e tomba) e encaixa
 *   (passa um tico e volta); a língua enrola na cabeça quando o pedaço dá a volta;
 * - as JUNTAS são a pose DEITADA rígida do modelo inteiro (a mesma do 1º quadro do "revive_lie" do GeckoLib), então
 *   quando o último encaixa o modelo inteiro assume sem pulo e o levantar é animação normal.
 * Tudo determinístico (a mesma semente em todo cliente); entrou no meio, o cliente simula do começo até agora.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ReviveScene {

    private static final Map<Integer, ReviveScene> SCENES = new HashMap<>();
    private static ClientLevel sceneLevel;
    private static final int SUB = 3;
    private static final float DT = 1F / (20F * SUB);
    private static final float S = FroggydudeEntity.MODEL_SCALE;
    private static final float PX = S / 16F;
    private static final ResourceLocation TONGUE_TEX = new ResourceLocation(ModEntityTypes.MOD_ID, "textures/entity/tongue.png");
    private static final float LIFT = 28F * Mth.DEG_TO_RAD;
    private static final float AIM_MAX = 40F * Mth.DEG_TO_RAD;
    private static final double R_CLEAR = 0.72D * S;
    private static final double R_WRAP = 0.34D * S;
    private static final float SPRING = 420F, DAMP = 26F;

    /** Os pedaços: os ossos de cada um (o tronco esconde cabeça e braços, que são pedaços próprios). */
    private enum PartId {
        HEAD(new String[]{"head"}, new String[0]),
        TORSO(new String[]{"body"}, new String[]{"head", "right_arm", "left_arm"}),
        RARM(new String[]{"right_arm"}, new String[0]),
        LARM(new String[]{"left_arm"}, new String[0]),
        RLEG(new String[]{"right_leg"}, new String[0]),
        LLEG(new String[]{"left_leg"}, new String[0]);

        final String[] bones;
        final String[] hide;

        PartId(String[] bones, String[] hide) {
            this.bones = bones;
            this.hide = hide;
        }
    }

    /** A ordem das puxadas (o tronco primeiro, igual ao vídeo; braços e depois pernas). */
    private static final PartId[] ORDER = {PartId.TORSO, PartId.RARM, PartId.LARM, PartId.RLEG, PartId.LLEG};

    private static final class Part {
        final PartId id;
        /** Centro (px do modelo, espaço do GeckoLib) e meia-caixa (blocos, já na escala). */
        final Vector3f center = new Vector3f();
        final Vector3f half = new Vector3f();
        Piece phys;
        double x, y, z, xo, yo, zo;
        final Quaternionf rot = new Quaternionf(), rotO = new Quaternionf();
        boolean attached;
        boolean pooled;
        // a puxada: o caminho, pra onde a cabeça mira (onde a língua pegou) e de onde o encaixe sai
        final List<Vec3> path = new ArrayList<>();
        double[] cum = new double[0];
        double ax, az;
        double sx, sy, sz;
        final Quaternionf sr = new Quaternionf();

        Part(PartId id) {
            this.id = id;
        }
    }

    private final int entityId;
    private final Revive.Params p;
    private final Part[] parts = new Part[PartId.values().length];
    private boolean geometry;
    private int simTick = -1;
    /** O tick de agora: os mais velhos que isso são o atraso (simulados de uma vez, sem os respingos). */
    private int live;
    private final Vec3 dir, side, head, feet;
    private final Quaternionf sock;
    private final RandomSource rand;
    private int bloodColor = 0x8A0303;
    /** Os respingos e pingos deste tick saem (não é o atraso). */
    private boolean fx;

    private ReviveScene(int entityId, Revive.Params p) {
        this.entityId = entityId;
        this.p = p;
        this.dir = p.dir();
        this.side = new Vec3(-dir.z, 0, dir.x);
        this.head = new Vec3(p.px(), p.py(), p.pz());
        double body = Revive.BODY * S;
        this.feet = new Vec3(p.px() - dir.x * body, p.py(), p.pz() - dir.z * body);
        this.sock = new Quaternionf().rotationY((180F - p.yaw()) * Mth.DEG_TO_RAD).rotateX(-Mth.HALF_PI);
        this.rand = RandomSource.create(p.seed());
        for (PartId id : PartId.values()) parts[id.ordinal()] = new Part(id);
    }

    // ---------------------------------------------------------------- acesso pelo renderer

    /** A cena dele (cria/troca se o renascer mudou); null se não está renascendo. */
    @Nullable
    public static ReviveScene of(FroggydudeEntity f) {
        if (f.getFroggyState() != FroggyState.REVIVE) {
            SCENES.remove(f.getId());
            return null;
        }
        Revive.Params rp = f.reviveParams();
        if (rp == null) return null;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != sceneLevel) {
            SCENES.clear();
            sceneLevel = level;
        }
        ReviveScene s = SCENES.get(f.getId());
        if (s == null || s.p.t0() != rp.t0() || s.p.seed() != rp.seed()) {
            s = new ReviveScene(f.getId(), rp);
            s.bloodColor = f.getMouthColor();
            SCENES.put(f.getId(), s);
        }
        return s;
    }

    /** Ticks (com fração) desde o começo do renascer. */
    public float age(Level level, float partial) {
        return (float) (level.getGameTime() - p.t0()) + partial;
    }

    /** Ainda são os pedaços (antes do levantar)? */
    public boolean piecesPhase(Level level) {
        return level.getGameTime() - p.t0() < p.standStart();
    }

    public boolean hasGeometry() {
        return geometry;
    }

    /**
     * A geometria dos pedaços, tirada do modelo do GeckoLib (o renderer passa: centro em px e meia-caixa em px, por
     * pedaço, na ordem de PartId). Só a primeira vez vale.
     */
    public void setGeometry(float[][] centerHalfPx) {
        if (geometry) return;
        for (PartId id : PartId.values()) {
            float[] v = centerHalfPx[id.ordinal()];
            Part part = parts[id.ordinal()];
            part.center.set(v[0], v[1], v[2]);
            part.half.set(Math.max(0.02F, v[3] * PX), Math.max(0.02F, v[4] * PX), Math.max(0.02F, v[5] * PX));
        }
        geometry = true;
    }

    public static String[] bonesOf(int part) {
        return PartId.values()[part].bones;
    }

    public static String[] hiddenOf(int part) {
        return PartId.values()[part].hide;
    }

    public static int partCount() {
        return PartId.values().length;
    }

    /** Quem desenha os ossos de um pedaço (o renderer, com o modelo do GeckoLib na pose de descanso). */
    public interface BoneDrawer {
        void draw(PoseStack poseStack, int part);
    }

    /**
     * Desenha os pedaços e a língua. O poseStack está na posição de desenho da entidade (eixos do mundo, sem giro);
     * base = essa posição no mundo.
     */
    public void render(PoseStack ps, Vec3 base, float partial, BoneDrawer drawer, MultiBufferSource buffers, int light) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !geometry) return;
        if (simTick < 0) catchUp(level);
        if (simTick < 0) return;
        Quaternionf q = new Quaternionf();
        for (Part part : parts) {
            double x = Mth.lerp(partial, part.xo, part.x), y = Mth.lerp(partial, part.yo, part.y), z = Mth.lerp(partial, part.zo, part.z);
            part.rotO.slerp(part.rot, partial, q);
            if (part.id == PartId.HEAD) headPose(age(level, partial));
            ps.pushPose();
            if (part.id == PartId.HEAD) {
                ps.translate(hx - base.x, hy - base.y, hz - base.z);
            } else {
                ps.translate(x - base.x, y - base.y, z - base.z);
            }
            ps.mulPose(part.id == PartId.HEAD ? hq : q);
            ps.scale(S, S, S);
            ps.translate(-part.center.x / 16F, -part.center.y / 16F, -part.center.z / 16F);
            drawer.draw(ps, part.id.ordinal());
            ps.popPose();
        }
        drawTongue(ps, base, partial, buffers, light, level);
    }

    // ---------------------------------------------------------------- a cabeça (roteirizada)

    private double hx, hy, hz;
    private final Quaternionf hq = new Quaternionf();

    /** A cabeça neste instante: o voo até P, a mira (gira) e a testa (sobe) nas puxadas. Resultado em hx/hy/hz/hq. */
    private void headPose(float t) {
        Part h = parts[PartId.HEAD.ordinal()];
        Vector3f c = sockCenter(h);
        if (t < 18F) {
            // o voo: do pescoço dele em pé até o lugar dela, um arco com cambalhota, e cai de cara no chão
            float k = t / 18F;
            float e = 1F - (1F - k) * (1F - k);
            Vector3f start = startCenter(h);
            hx = Mth.lerp(e, start.x, c.x);
            hy = Mth.lerp(e, start.y, c.y) + 1.3D * Math.sin(Math.PI * k);
            hz = Mth.lerp(e, start.z, c.z);
            Quaternionf q0 = startRot();
            hq.set(q0).slerp(sock, e);
            hq.premul(new Quaternionf().rotateAxis((1F - e) * Mth.TWO_PI, (float) side.x, 0F, (float) side.z));
            return;
        }
        hx = c.x;
        hy = c.y;
        hz = c.z;
        hq.set(sock);
        int st = p.step();
        float ps = p.partsStart();
        // tremidinha antes da primeira puxada (tá viva)
        if (t > ps - 14 && t < ps - 6) {
            float k = (t - (ps - 14)) / 8F;
            hq.premul(new Quaternionf().rotationY(0.09F * Mth.sin(k * Mth.PI * 6F) * (1F - k)));
        }
        if (t < ps || t >= p.standStart()) return;
        int i = Math.min(Revive.PARTS - 1, (int) ((t - ps) / st));
        float s = t - ps - i * st;
        Part target = parts[ORDER[i].ordinal()];
        // a mira: gira (no chão) pro pedaço; com o tronco preso, só até 40° no pescoço
        double tx = target.x - c.x, tz = target.z - c.z;
        if (s >= Revive.pullTick(st)) {
            tx = target.ax - c.x;
            tz = target.az - c.z;
        }
        float want = (float) (Math.atan2(tz, tx) - Math.atan2(dir.z, dir.x));
        want = Mth.wrapDegrees(want * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        if (i > 0) want = Mth.clamp(want, -AIM_MAX, AIM_MAX);
        float aimIn = smooth(s / Math.max(1F, Revive.liftTick(st)));
        float aimOut = 1F - smooth((s - Revive.doneTick(st)) / Math.max(1F, st - Revive.doneTick(st)));
        float yaw = want * Math.min(aimIn, aimOut);
        // a testa: sobe no tiro, segura, desce depois do encaixe
        float liftIn = smooth((s - Revive.liftTick(st)) / Math.max(1F, Revive.shotTick(st) - Revive.liftTick(st)));
        float liftOut = 1F - smooth((s - Revive.snapTick(st) - 2) / Math.max(1F, Revive.doneTick(st) - Revive.snapTick(st) - 2));
        float lift = LIFT * Math.min(liftIn, liftOut);
        // giro no chão (eixo do mundo y), em volta do centro (o tronco ainda solto) ou do pescoço
        Vector3f pivot = i == 0 ? new Vector3f(c) : worldOfHead(0F, 24F, 0F);
        Quaternionf qy = new Quaternionf().rotationY(-yaw);
        rotateAbout(qy, pivot);
        // a testa: em volta da beirada do queixo (antes do tronco) ou do pescoço, pro lado de cima subir
        Vector3f axis = new Vector3f(1, 0, 0).rotate(hq);
        Vector3f lp = i == 0 ? worldOfHead(0F, 24F, -4F) : worldOfHead(0F, 24F, 0F);
        Quaternionf ql = new Quaternionf(new AxisAngle4f(lift, axis.x, axis.y, axis.z));
        Vector3f top = new Vector3f(0, 1, 0).rotate(hq);
        if (new Vector3f(top).rotate(ql).y < top.y) ql = new Quaternionf(new AxisAngle4f(-lift, axis.x, axis.y, axis.z));
        rotateAbout(ql, lp);
    }

    /** Gira a pose da cabeça (hx..hq) em volta de um ponto do mundo. */
    private void rotateAbout(Quaternionf r, Vector3f pivot) {
        Vector3f d = new Vector3f((float) (hx - pivot.x), (float) (hy - pivot.y), (float) (hz - pivot.z)).rotate(r);
        hx = pivot.x + d.x;
        hy = pivot.y + d.y;
        hz = pivot.z + d.z;
        hq.premul(r);
    }

    /** Um ponto da cabeça (px do modelo) no mundo, na pose atual (hx..hq). */
    private Vector3f worldOfHead(float mx, float my, float mz) {
        Part h = parts[PartId.HEAD.ordinal()];
        Vector3f v = new Vector3f((mx - h.center.x) * PX, (my - h.center.y) * PX, (mz - h.center.z) * PX).rotate(hq);
        return v.add((float) hx, (float) hy, (float) hz);
    }

    // ---------------------------------------------------------------- poses de referência

    private Quaternionf startRot() {
        return new Quaternionf().rotationY((180F - p.bodyYaw()) * Mth.DEG_TO_RAD);
    }

    /** O centro do pedaço em pé, na hora da morte. */
    private Vector3f startCenter(Part part) {
        Vector3f v = new Vector3f(part.center).mul(PX).rotate(startRot());
        return v.add((float) p.ex(), (float) p.ey(), (float) p.ez());
    }

    /** O centro do pedaço na junta (deitado de bruços, os pés em F, erguido 2 px). */
    private Vector3f sockCenter(Part part) {
        Vector3f v = new Vector3f(part.center).mul(PX).rotate(sock);
        return v.add((float) feet.x, (float) feet.y + 2F * PX, (float) feet.z);
    }

    /** A ponta da junta (pescoço/ombro/quadril) no espaço do pedaço (blocos). */
    private Vector3f grabLocal(Part part) {
        return new Vector3f(0F, part.half.y, 0F);
    }

    private Vector3f grabWorld(Part part) {
        return grabLocal(part).rotate(new Quaternionf(part.rot)).add((float) part.x, (float) part.y, (float) part.z);
    }

    private Vector3f sockGrab(Part part) {
        return grabLocal(part).rotate(new Quaternionf(sock)).add(sockCenter(part));
    }

    // ---------------------------------------------------------------- a simulação (por tick)

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != sceneLevel) {
            SCENES.clear();
            sceneLevel = level;
            return;
        }
        if (mc.isPaused()) return;
        Iterator<Map.Entry<Integer, ReviveScene>> it = SCENES.entrySet().iterator();
        while (it.hasNext()) {
            ReviveScene s = it.next().getValue();
            Entity e = level.getEntity(s.entityId);
            if (!(e instanceof FroggydudeEntity f) || f.getFroggyState() != FroggyState.REVIVE) {
                it.remove();
                continue;
            }
            if (s.geometry) s.catchUp(level);
        }
    }

    /**
     * Simula até agora. Entrou no meio, ou ele só agora apareceu na tela (a geometria vem do 1º desenho): do começo, de
     * uma vez; os respingos e pingos desse atraso não saem (sairiam todos juntos, bem depois da explosão), as poças sim.
     */
    private void catchUp(Level level) {
        int now = (int) (level.getGameTime() - p.t0());
        live = now;
        int guard = 0;
        while (simTick < Math.min(now, p.standStart()) && guard++ < 2000) tickOnce(level, simTick + 1);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SCENES.clear();
        sceneLevel = null;
    }

    private void tickOnce(Level level, int t) {
        simTick = t;
        fx = t >= live - 1;
        for (Part part : parts) {
            part.xo = part.x;
            part.yo = part.y;
            part.zo = part.z;
            part.rotO.set(part.rot);
        }
        if (t == 0) start(level);
        // os soltos: física (separando, ou parados no chão esperando a vez)
        int ps = p.partsStart();
        int st = p.step();
        int cur = t < ps ? -1 : Math.min(Revive.PARTS - 1, (t - ps) / st);
        int s = cur < 0 ? -1 : t - ps - cur * st;
        for (int k = 0; k < Revive.PARTS; k++) {
            Part part = parts[ORDER[k].ordinal()];
            if (part.attached) {
                Vector3f c = sockCenter(part);
                part.x = c.x;
                part.y = c.y;
                part.z = c.z;
                part.rot.set(sock);
                continue;
            }
            boolean active = k == cur;
            if (active && s >= Revive.snapTick(st)) {
                snap(part, s, st, level);
                continue;
            }
            Piece ph = part.phys;
            if (active && s == Revive.grabTick(st)) grab(part);
            if (active && s == Revive.pullTick(st)) planPath(part);
            for (int sub = 0; sub < SUB; sub++) {
                if (active && s >= Revive.pullTick(st)) pullForce(part, (s - Revive.pullTick(st) + sub / (float) SUB)
                        / (float) (Revive.snapTick(st) - Revive.pullTick(st)));
                ph.step(level, DT);
            }
            part.x = ph.x;
            part.y = ph.y;
            part.z = ph.z;
            part.rot.set(ph.rot);
            // sangue: voando pinga; parado, a poça; arrastado, o rastro
            if (t < ps && ph.vel.length() > 2.5F && rand.nextInt(3) == 0 && fx) {
                ClientBloodFx.droplets(ph.x, ph.y, ph.z, 1, bloodColor);
            }
            if (ph.asleep && !part.pooled && t < ps) {
                part.pooled = true;
                ClientBloodFx.pool(ph.x, ph.y - part.half.y * 0.5D, ph.z, 0.45F, bloodColor);
            }
            if (active && s >= Revive.pullTick(st) && t % 4 == 0 && fx) {
                ClientBloodFx.droplets(ph.x, ph.y - 0.1D, ph.z, 1, bloodColor);
            }
        }
        Part h = parts[PartId.HEAD.ordinal()];
        Vector3f hc = sockCenter(h);
        h.x = hc.x;
        h.y = hc.y;
        h.z = hc.z;
        h.rot.set(sock);
        if (t == 18) ClientBloodFx.pool(head.x, head.y + 0.02D, head.z, 0.6F, bloodColor);
    }

    /** O estouro: cada pedaço sai do corpo em pé e voa pro lado da junta dele (o tronco pra frente). */
    private void start(Level level) {
        boolean boom = p.boom();
        Quaternionf q0 = startRot();
        for (int k = 0; k < Revive.PARTS; k++) {
            Part part = parts[ORDER[k].ordinal()];
            Vector3f c0 = startCenter(part);
            Piece ph = new Piece(-1 - entityId, new Vector3f(part.half), part.half.length());
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        ph.samples.add(new Vector3f(sx * part.half.x, sy * part.half.y, sz * part.half.z));
                    }
                }
            }
            ph.x = ph.xo = c0.x;
            ph.y = ph.yo = c0.y;
            ph.z = ph.zo = c0.z;
            ph.rot.set(q0);
            ph.rotO.set(q0);
            ph.life = Integer.MAX_VALUE;
            // pra onde: o tronco na frente da cabeça (onde o corpo vai deitar é pra trás), cada membro pro lado da junta
            Vector3f sg = sockCenter(part);
            double lat = (sg.x - head.x) * side.x + (sg.z - head.z) * side.z;
            double sgn = part.id == PartId.TORSO ? (rand.nextBoolean() ? 1 : -1) * 0.15D : Math.signum(lat);
            double spread = part.id == PartId.TORSO ? 0.15D : 0.55D + rand.nextDouble() * 0.6D;
            double ang = Math.atan2(dir.z, dir.x) + sgn * spread * (part.id == PartId.TORSO ? 1 : 1.35D);
            double dist = (part.id == PartId.TORSO ? 1.5D : 1.3D + rand.nextDouble() * 1.4D) * (boom ? 1.25D : 1D);
            // alvo no chão a partir da cabeça; o tempo de voo vem da subida
            double up = (boom ? 5.2D : 4.2D) + rand.nextDouble() * 1.2D;
            double tFly = 2D * up / Piece.GRAVITY + 0.15D;
            double gx = head.x + Math.cos(ang) * dist, gz = head.z + Math.sin(ang) * dist;
            float vx = (float) ((gx - c0.x) / tFly * 0.75D), vz = (float) ((gz - c0.z) / tFly * 0.75D);
            ph.vel.set(vx, (float) up, vz);
            float spin = boom ? 9F : 6F;
            ph.ang.set((float) rand.nextGaussian() * spin, (float) rand.nextGaussian() * spin, (float) rand.nextGaussian() * spin);
            part.phys = ph;
            part.x = part.xo = ph.x;
            part.y = part.yo = ph.y;
            part.z = part.zo = ph.z;
            part.rot.set(q0);
            part.rotO.set(q0);
        }
        // o sangue das juntas
        Vector3f neck = new Vector3f(0F, 24F * PX, 0F).rotate(q0).add((float) p.ex(), (float) p.ey(), (float) p.ez());
        if (fx) {
            ClientBloodFx.spray(neck.x, neck.y, neck.z, 0, 0.6D, 0, 30, 0.9F, bloodColor);
            ClientBloodFx.spray(p.ex(), p.ey() + 0.8D, p.ez(), 0, 0.4D, 0, 30, 0.8F, bloodColor);
        }
    }

    /** A língua pega: tranco pra cima e pra cabeça. */
    private void grab(Part part) {
        Piece ph = part.phys;
        ph.wake();
        Vector3f g = grabLocal(part).rotate(new Quaternionf(ph.rot));
        Vector3f toHead = new Vector3f((float) (head.x - ph.x), 0F, (float) (head.z - ph.z));
        if (toHead.lengthSquared() > 1.0E-6F) toHead.normalize();
        float m = 1F / ph.invMass;
        ph.applyImpulse(g, new Vector3f(toHead.x * 0.8F * m, 1.6F * m, toHead.z * 0.8F * m));
        if (fx) ClientBloodFx.droplets(ph.x, ph.y + 0.1D, ph.z, 2, bloodColor);
    }

    /** O caminho da mão (onde a língua puxa a junta): vem pra perto, dá a volta pelo lado da junta e entra por trás. */
    private void planPath(Part part) {
        part.path.clear();
        Vector3f g0 = grabWorld(part);
        Vector3f sg = sockGrab(part);
        part.ax = part.x;
        part.az = part.z;
        double thU = Math.atan2(dir.z, dir.x);
        double r0 = Math.hypot(g0.x - head.x, g0.z - head.z);
        double th0 = Math.atan2(g0.z - head.z, g0.x - head.x);
        double d0 = wrap(th0 - thU);
        double lat = (sg.x - head.x) * side.x + (sg.z - head.z) * side.z;
        double sgn = part.id == PartId.TORSO ? (Math.signum((g0.x - head.x) * side.x + (g0.z - head.z) * side.z)) : Math.signum(lat);
        if (sgn == 0) sgn = 1;
        Vec3 pre = new Vec3(sg.x - dir.x * 0.55D * S + side.x * sgn * 0.1D, sg.y, sg.z - dir.z * 0.55D * S + side.z * sgn * 0.1D);
        double rp = Math.hypot(pre.x - head.x, pre.z - head.z);
        double dp = wrap(Math.atan2(pre.z - head.z, pre.x - head.x) - thU);
        // o lado do "side" é ângulo positivo no atan2(z, x) (side = (-dir.z, dir.x), 90° à esquerda de dir)
        if (sgn > 0 && dp < 0) dp += Math.PI * 2;
        if (sgn < 0 && dp > 0) dp -= Math.PI * 2;
        double rc = Math.min(r0, R_CLEAR);
        double gy = g0.y;
        part.path.add(new Vec3(g0.x, gy, g0.z));
        for (int i = 1; i <= 10; i++) {
            double k = i / 10.0D;
            double r = r0 + (rc - r0) * smoothD(k);
            double d = d0 + (dp - d0) * 0.12D * k;
            part.path.add(new Vec3(head.x + r * Math.cos(thU + d), gy, head.z + r * Math.sin(thU + d)));
        }
        double dStart = d0 + (dp - d0) * 0.12D;
        for (int i = 1; i <= 28; i++) {
            double k = i / 28.0D;
            double r = rc + (rp - rc) * smoothD(k) * smoothD(k);
            double d = dStart + (dp - dStart) * smoothD(k);
            part.path.add(new Vec3(head.x + r * Math.cos(thU + d), gy, head.z + r * Math.sin(thU + d)));
        }
        for (int i = 1; i <= 8; i++) {
            double k = i / 8.0D;
            part.path.add(new Vec3(pre.x + (sg.x - pre.x) * k, gy, pre.z + (sg.z - pre.z) * k));
        }
        part.cum = new double[part.path.size()];
        for (int i = 1; i < part.path.size(); i++) {
            part.cum[i] = part.cum[i - 1] + part.path.get(i).distanceTo(part.path.get(i - 1));
        }
    }

    private Vec3 pathAt(Part part, double s) {
        double[] cum = part.cum;
        int n = part.path.size();
        if (n == 0) return new Vec3(part.x, part.y, part.z);
        s = Mth.clamp(s, 0D, cum[n - 1]);
        int i = 0;
        while (i < n - 2 && cum[i + 1] < s) i++;
        double k = (s - cum[i]) / Math.max(1.0E-9D, cum[i + 1] - cum[i]);
        return part.path.get(i).lerp(part.path.get(i + 1), Mth.clamp(k, 0D, 1D));
    }

    /** A mola da língua na ponta da junta, puxando pra mão no caminho (devagar no começo, rápido no fim) e os trancos. */
    private void pullForce(Part part, float u) {
        u = Mth.clamp(u, 0F, 1F);
        double total = part.cum.length == 0 ? 0 : part.cum[part.cum.length - 1];
        Vec3 target = pathAt(part, total * (0.06D * u + 0.94D * Math.pow(u, 1.9D)));
        double ty = head.y + 0.12D;
        for (double uc : new double[]{0.32D, 0.62D}) {
            if (Math.abs(u - uc) < 0.06D) ty += 0.42D * Math.sin((u - uc + 0.06D) / 0.12D * Math.PI);
        }
        Piece ph = part.phys;
        Vector3f r = grabLocal(part).rotate(new Quaternionf(ph.rot));
        Vector3f g = new Vector3f(r).add((float) ph.x, (float) ph.y, (float) ph.z);
        Vector3f vg = new Vector3f(ph.ang).cross(r).add(ph.vel);
        Vector3f acc = new Vector3f((float) target.x - g.x, (float) ty - g.y, (float) target.z - g.z).mul(SPRING)
                .sub(new Vector3f(vg).mul(DAMP));
        float m = 1F / ph.invMass;
        ph.wake();
        ph.applyImpulse(r, acc.mul(m * DT));
    }

    /** O encaixe: da pose de agora até a junta (a rotação passa um tico e volta). */
    private void snap(Part part, int s, int st, Level level) {
        int s0 = Revive.snapTick(st), s1 = Revive.doneTick(st);
        if (s == s0) {
            part.sx = part.x;
            part.sy = part.y;
            part.sz = part.z;
            part.sr.set(part.rot);
        }
        float k = (s - s0 + 1) / (float) Math.max(1, s1 - s0);
        Vector3f c = sockCenter(part);
        float e = 1F - (1F - Math.min(1F, k)) * (1F - Math.min(1F, k)) * (1F - Math.min(1F, k));
        part.x = Mth.lerp(e, part.sx, c.x);
        part.y = Mth.lerp(e, part.sy, c.y);
        part.z = Mth.lerp(e, part.sz, c.z);
        float back = easeBack(Math.min(1F, k));
        Quaternionf d = new Quaternionf(sock).mul(new Quaternionf(part.sr).invert());
        AxisAngle4f aa = new AxisAngle4f(d);
        part.rot.set(new Quaternionf(new AxisAngle4f(aa.angle * back, aa.x, aa.y, aa.z)).mul(part.sr));
        if (k >= 1F) {
            part.attached = true;
            part.rot.set(sock);
            Vector3f j = sockGrab(part);
            if (fx) ClientBloodFx.spray(j.x, j.y + 0.05D, j.z, 0, 0.5D, 0, 14, 0.6F, bloodColor);
        }
    }

    // ---------------------------------------------------------------- a língua

    private void drawTongue(PoseStack ps, Vec3 base, float partial, MultiBufferSource buffers, int light, Level level) {
        float t = age(level, partial);
        int pstart = p.partsStart();
        if (t < pstart || t >= p.standStart()) return;
        int st = p.step();
        int i = Math.min(Revive.PARTS - 1, (int) ((t - pstart) / st));
        float s = t - pstart - i * st;
        if (s < Revive.shotTick(st)) return;
        Part part = parts[ORDER[i].ordinal()];
        // a saída: debaixo da testa, no chão
        Vector3f fore = worldOfHead(0F, 32F, -4F);
        Vector3f top = new Vector3f(0, 1, 0).rotate(hq);
        top.y = 0;
        if (top.lengthSquared() < 1.0E-6F) top.set((float) dir.x, 0, (float) dir.z);
        top.normalize();
        Vector3f exit = new Vector3f(fore.x - top.x * 0.05F, (float) head.y + 0.05F, fore.z - top.z * 0.05F);
        // a ponta: indo (tiro), presa na junta (arrastando/encaixando) ou voltando pra boca
        Vector3f tip;
        float frac = 1F, wave = 0F, liftArc = 0F;
        double x = Mth.lerp(partial, part.xo, part.x), y = Mth.lerp(partial, part.yo, part.y), z = Mth.lerp(partial, part.zo, part.z);
        Quaternionf q = new Quaternionf();
        part.rotO.slerp(part.rot, partial, q);
        Vector3f grab = grabLocal(part).rotate(q).add((float) x, (float) y, (float) z);
        if (s < Revive.grabTick(st)) {
            float k = (s - Revive.shotTick(st)) / Math.max(1F, Revive.grabTick(st) - Revive.shotTick(st));
            tip = grab;
            frac = 1F - (1F - k) * (1F - k);
            wave = 0.09F * (1F - k);
            liftArc = 0.18F * Mth.sin(Mth.PI * k);
        } else if (s < Revive.snapTick(st) + (Revive.doneTick(st) - Revive.snapTick(st)) * 0.5F) {
            tip = grab;
            wave = s < Revive.pullTick(st) ? 0.03F : 0.006F;
        } else {
            float k = (s - Revive.snapTick(st) - (Revive.doneTick(st) - Revive.snapTick(st)) * 0.5F) / 5F;
            if (k >= 1F) return;
            tip = grab;
            frac = Math.max(0F, 1F - k * k);
        }
        List<Vector3f> rope = rope(exit, tip, top);
        List<Vector3f> pts = cut(rope, frac, wave, liftArc, t);
        if (pts.size() < 2) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TONGUE_TEX));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float hw = 0.06F * S;
        for (int k = 0; k < pts.size() - 1; k++) {
            Vector3f a = pts.get(k), b = pts.get(k + 1);
            Vector3f mid = new Vector3f(a).add(b).mul(0.5F);
            Vector3f seg = new Vector3f(b).sub(a);
            Vector3f view = new Vector3f((float) cam.x, (float) cam.y, (float) cam.z).sub(mid);
            Vector3f sd = new Vector3f(seg).cross(view);
            if (sd.lengthSquared() < 1.0E-10F) continue;
            sd.normalize().mul(k == pts.size() - 2 ? hw * 1.25F : hw);
            boolean dark = k % 5 == 3;
            int cr = dark ? 180 : 222, cg = dark ? 48 : 60, cb = dark ? 42 : 50;
            quad(vc, m, n, base, a, b, sd, cr, cg, cb, light);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 base, Vector3f a, Vector3f b, Vector3f sd,
                             int r, int g, int bl, int light) {
        float ax = (float) (a.x - base.x), ay = (float) (a.y - base.y), az = (float) (a.z - base.z);
        float bx = (float) (b.x - base.x), by = (float) (b.y - base.y), bz = (float) (b.z - base.z);
        vc.vertex(m, ax - sd.x, ay - sd.y, az - sd.z).color(r, g, bl, 255).uv(0F, 0F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, ax + sd.x, ay + sd.y, az + sd.z).color(r, g, bl, 255).uv(1F, 0F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, bx + sd.x, by + sd.y, bz + sd.z).color(r, g, bl, 255).uv(1F, 1F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, bx - sd.x, by - sd.y, bz - sd.z).color(r, g, bl, 255).uv(0F, 1F).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0, 1, 0).endVertex();
    }

    /** A língua de e até g; se o pedaço está do lado ou atrás da cabeça, ela enrola na cabeça (corda num poste). */
    private List<Vector3f> rope(Vector3f e, Vector3f g, Vector3f top) {
        List<Vector3f> pts = new ArrayList<>();
        float hxF = (float) head.x, hzF = (float) head.z;
        double thE = Math.atan2(top.z, top.x);
        Vector3f e1 = new Vector3f(hxF + (float) (Math.cos(thE) * R_WRAP), e.y, hzF + (float) (Math.sin(thE) * R_WRAP));
        pts.add(new Vector3f(e));
        pts.add(e1);
        double dx = g.x - hxF, dz = g.z - hzF;
        double rg = Math.hypot(dx, dz);
        if (rg > R_WRAP * 1.02D && dx * Math.cos(thE) + dz * Math.sin(thE) < R_WRAP) {
            double thG = Math.atan2(dz, dx);
            double delta = wrap(thG - thE);
            double sgn = delta > 0 ? 1D : -1D;
            double thT = thG - sgn * Math.acos(Math.min(1D, R_WRAP / rg));
            double span = wrap(thT - thE);
            if (sgn > 0 && span < 0) span += Math.PI * 2;
            if (sgn < 0 && span > 0) span -= Math.PI * 2;
            int n = Math.max(2, (int) (Math.abs(span) / 0.2D));
            for (int i = 1; i <= n; i++) {
                double a = thE + span * i / n;
                pts.add(new Vector3f(hxF + (float) (Math.cos(a) * R_WRAP), e.y, hzF + (float) (Math.sin(a) * R_WRAP)));
            }
        }
        pts.add(new Vector3f(g));
        return pts;
    }

    /** A fração frac do comprimento (a ponta indo/voltando), com onda de lado e arco pra cima. */
    private static List<Vector3f> cut(List<Vector3f> pts, float frac, float wave, float lift, float t) {
        int n = pts.size();
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) cum[i] = cum[i - 1] + pts.get(i).distance(pts.get(i - 1));
        double total = cum[n - 1];
        double len = total * frac;
        List<Vector3f> out = new ArrayList<>();
        if (len < 0.02D) return out;
        int segs = Math.max(8, (int) (total / 0.05D));
        for (int i = 0; i <= segs; i++) {
            double s = len * i / segs;
            int j = 0;
            while (j < n - 2 && cum[j + 1] < s) j++;
            float k = (float) ((s - cum[j]) / Math.max(1.0E-9D, cum[j + 1] - cum[j]));
            Vector3f q = new Vector3f(pts.get(j)).lerp(pts.get(j + 1), Mth.clamp(k, 0F, 1F));
            if (wave != 0F || lift != 0F) {
                Vector3f d = new Vector3f(pts.get(j + 1)).sub(pts.get(j));
                d.y = 0;
                float dl = d.length();
                Vector3f sd = dl > 1.0E-6F ? new Vector3f(-d.z / dl, 0, d.x / dl) : new Vector3f();
                float v = (float) (s / Math.max(len, 1.0E-6D));
                q.add(sd.mul(wave * Mth.sin(v * Mth.PI * 3F - (float) len * 9F) * v));
                q.y += lift * Mth.sin(Mth.PI * v);
            }
            out.add(q);
        }
        return out;
    }

    // ---------------------------------------------------------------- contas

    private static float smooth(float k) {
        k = Mth.clamp(k, 0F, 1F);
        return k * k * (3F - 2F * k);
    }

    private static double smoothD(double k) {
        k = Mth.clamp(k, 0D, 1D);
        return k * k * (3D - 2D * k);
    }

    private static float easeBack(float k) {
        float s = 1.8F;
        k = Mth.clamp(k, 0F, 1F) - 1F;
        return k * k * ((s + 1F) * k + s) + 1F;
    }

    private static double wrap(double a) {
        a = (a + Math.PI) % (Math.PI * 2);
        if (a < 0) a += Math.PI * 2;
        return a - Math.PI;
    }
}
