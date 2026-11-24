package com.froggydude.mind;

import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (v0.7.68, a Mente, etapa 1) Os sentidos honestos do FroggyDude: ele só VÊ o que está na frente dele (um cone de ~130°),
 * com a luz do lugar, de longe menos, agachado e no escuro bem menos; vidro deixa ver, folha e parede não. Muito perto
 * (encostado) ele sente mesmo de costas. Nada de saber pelo inventário ou pelo outro lado da parede.
 */
public final class Senses {

    /** Metade do cone de visão: ~65° pra cada lado. */
    static final double CONE_COS = Math.cos(Math.toRadians(65.0D));
    /** Encostado: sente de costas (o barulho, o cheiro, o calor). */
    static final double TOUCH = 2.5D;

    private Senses() {
    }

    /** Até onde ele enxerga essa pessoa agora (a luz dela, agachada, invisível). */
    public static double range(FroggydudeEntity f, Player p) {
        double base = Math.min(f.sightRange(), 96.0D);
        BlockPos at = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        int light = p.level().getMaxLocalRawBrightness(at);
        double k = 0.22D + 0.78D * light / 15.0D;
        if (p.isCrouching()) k *= 0.55D;
        if (p.hasEffect(MobEffects.INVISIBILITY)) k *= p.getArmorCoverPercentage() > 0F ? 0.5D : 0.12D;
        if (p.hasEffect(MobEffects.GLOWING)) k = Math.max(k, 1.2D);
        return Math.max(4.0D, base * k);
    }

    /** Ele está vendo essa pessoa agora (cone + luz + linha de visão de verdade). */
    public static boolean canSee(FroggydudeEntity f, Player p) {
        if (p.level() != f.level() || p.isSpectator()) return false;
        Vec3 eye = f.getEyePosition();
        Vec3 to = p.getEyePosition();
        double d = eye.distanceTo(to);
        if (d <= TOUCH) return true;
        if (d > range(f, p)) return false;
        if (!inCone(f, to)) return false;
        return clear(f, eye, to) || clear(f, eye, p.position().add(0, 0.9D, 0));
    }

    /** Um ponto (de procura) está à vista dele: dentro do cone, perto, sem nada na frente. */
    public static boolean canSeePoint(FroggydudeEntity f, Vec3 pt, double max) {
        Vec3 eye = f.getEyePosition();
        if (eye.distanceTo(pt) > max || !inCone(f, pt)) return false;
        return clear(f, eye, pt);
    }

    static boolean inCone(FroggydudeEntity f, Vec3 pt) {
        Vec3 dir = pt.subtract(f.getEyePosition());
        double len = dir.length();
        if (len < 1.0E-3D) return true;
        Vec3 look = Vec3.directionFromRotation(f.getXRot() * 0.5F, f.getYHeadRot());
        return look.dot(dir.scale(1.0D / len)) >= CONE_COS;
    }

    /** Linha de visão que deixa passar vidro (a forma visual dele é vazia) e para em parede e folha. */
    static boolean clear(FroggydudeEntity f, Vec3 from, Vec3 to) {
        return f.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, f)).getType()
                == HitResult.Type.MISS;
    }

    /**
     * Quanto a certeza sobe num olhar (a cada 2 ticks): perto e claro, na hora; no limite do que enxerga, ~1,5 s; quem corre
     * chama mais atenção.
     */
    static float gain(FroggydudeEntity f, Player p) {
        double d = f.distanceTo(p);
        if (d < 6.0D) return 1.0F;
        double r = range(f, p);
        float g = (float) (0.07D + 0.45D * Math.max(0.0D, 1.0D - d / r));
        if (p.isSprinting()) g *= 1.5F;
        return g;
    }
}
