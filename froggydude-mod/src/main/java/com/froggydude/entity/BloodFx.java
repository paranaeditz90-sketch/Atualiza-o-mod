package com.froggydude.entity;

import com.froggydude.network.BloodFxPacket;
import com.froggydude.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Sangue do lado do servidor: manda pros jogadores por perto um jato (pedaços
 * que voam e caem, como no "Eating a Zebra"), uma poça no chão (que vai
 * crescendo se cair mais sangue no mesmo lugar), um golpe (quem bateu fica
 * manchado e a tela respinga), um jorro contínuo preso num corpo ou uma
 * explosão de sangue. Cada cliente desenha do tamanho do nível de gore dele.
 */
public final class BloodFx {

    private static final double RANGE = 64.0D;
    public static final int RED = BloodType.RED.color;

    private BloodFx() {
    }

    /**
     * Jato de sangue saindo de "at" na direção "dir" (não precisa ser unitária).
     * power ~0,15 = escorrendo, ~0,3 = mordida, ~0,5 = braço arrancado.
     */
    public static void spray(Level level, Vec3 at, Vec3 dir, int count, float power) {
        spray(level, at, dir, count, power, RED);
    }

    public static void spray(Level level, Vec3 at, Vec3 dir, int count, float power, int color) {
        if (!(level instanceof ServerLevel server) || count <= 0) return;
        Vec3 d = unit(dir);
        send(server, at, new BloodFxPacket(BloodFxPacket.SPRAY, at.x, at.y, at.z, d.x, d.y, d.z, count, power,
                color, -1, -1, 0));
    }

    /** Poça no chão embaixo de "at" (size = diâmetro em blocos). */
    public static void pool(Level level, Vec3 at, float size) {
        pool(level, at, size, RED);
    }

    public static void pool(Level level, Vec3 at, float size, int color) {
        if (!(level instanceof ServerLevel server)) return;
        send(server, at, new BloodFxPacket(BloodFxPacket.POOL, at.x, at.y, at.z, 0, 0, 0, 0, size, color, -1, -1, 0));
    }

    /**
     * Um golpe que tirou sangue: a vítima espirra pra onde o golpe foi, quem
     * bateu de perto fica respingado (skin e tela), cai poça embaixo.
     */
    public static void hit(LivingEntity victim, @Nullable Entity attacker, Vec3 at, Vec3 dir, float damage, int color) {
        hit(victim, attacker, at, dir, damage, color, HIT_ALL);
    }

    /** Golpe com tudo: pedaços voando, poça, mancha na skin e respingo na tela. */
    public static final int HIT_ALL = 3;
    /** Só a mancha na skin e o respingo na tela (o FroggyDude faz os pedaços dele). */
    public static final int HIT_MARKS = 0;

    public static void hit(LivingEntity victim, @Nullable Entity attacker, Vec3 at, Vec3 dir, float damage, int color, int flags) {
        if (!(victim.level() instanceof ServerLevel server)) return;
        Vec3 d = unit(dir);
        send(server, at, new BloodFxPacket(BloodFxPacket.HIT, at.x, at.y, at.z, d.x, d.y, d.z, flags, damage, color,
                victim.getId(), attacker == null ? -1 : attacker.getId(), 0));
    }

    /**
     * Jorro contínuo (o "jorrar" do Extremo; nos outros níveis vira pedaços em
     * sequência) saindo do corpo de "from": local = (direita, cima, frente) em
     * blocos, no corpo dele; dir = pra onde, no mundo; speed em blocos/tick.
     */
    public static void stream(Entity from, Vec3 local, Vec3 dir, float speed, int ticks, int color) {
        if (!(from.level() instanceof ServerLevel server)) return;
        Vec3 d = unit(dir);
        send(server, from.position(), new BloodFxPacket(BloodFxPacket.STREAM, local.x, local.y, local.z,
                d.x, d.y, d.z, 0, speed, color, from.getId(), -1, ticks));
    }

    /** Jorro parado num ponto do mundo (o toco de quem não se mexe mais). */
    public static void streamAt(Level level, Vec3 at, Vec3 dir, float speed, int ticks, int color) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 d = unit(dir);
        send(server, at, new BloodFxPacket(BloodFxPacket.STREAM, at.x, at.y, at.z, d.x, d.y, d.z, 0, speed, color,
                -1, -1, ticks));
    }

    /** Explosão de sangue: membro arrancado, mob devorado, morte feia. */
    public static void burst(Level level, Vec3 at, Vec3 dir, int count, float power, int color, @Nullable Entity source) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 d = unit(dir);
        send(server, at, new BloodFxPacket(BloodFxPacket.BURST, at.x, at.y, at.z, d.x, d.y, d.z, count, power, color,
                source == null ? -1 : source.getId(), -1, 0));
    }

    /**
     * O FroggyDude matou: o corpo se desmonta em pedaços com física em quem está vendo
     * (cada cliente decide pelo gore dele). dir = o empurrão, já com a força.
     */
    public static void breakBody(LivingEntity victim, Vec3 dir, float power) {
        if (!(victim.level() instanceof ServerLevel)) return;
        BloodType type = BloodType.of(victim);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> victim),
                new BloodFxPacket(BloodFxPacket.BREAK, victim.getX(), victim.getY(), victim.getZ(), dir.x, dir.y, dir.z,
                        type.bleeds() ? 1 : 0, power, type.bleeds() ? type.color : BloodType.residue(victim),
                        victim.getId(), -1, 0));
    }

    private static Vec3 unit(Vec3 dir) {
        return dir.lengthSqr() < 1.0E-6D ? new Vec3(0, 1, 0) : dir.normalize();
    }

    private static void send(ServerLevel server, Vec3 at, BloodFxPacket packet) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(at.x, at.y, at.z, RANGE, server.dimension())), packet);
    }
}
