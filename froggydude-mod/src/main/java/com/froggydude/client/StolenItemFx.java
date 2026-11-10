package com.froggydude.client;

import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

/**
 * (v0.7.30) A arma roubada na boca, do lado do cliente. Os mods de arma desenham a arma de dois jeitos:
 * <ul>
 * <li>ícone chato (o jeito comum; o Avaritia também, mesmo com o brilho cósmico desenhado por cima): o desenho vem na
 * diagonal, então gira 45° pra ficar atravessado na boca;</li>
 * <li>arma 3D desenhada só pelo mod, sem textura nenhuma no modelo (as armas de fogo do TaCZ, "builtin/entity"): vem
 * em pé saindo da boca, então gira 90°.</li>
 * </ul>
 * O farelo da mordida é sempre cinza (v0.7.31, LM: "tem como colocar uma textura cinza?"): o farelo tirado do desenho
 * do item parecia erro de textura (quadradinhos coloridos soltos, ou o quadriculado roxo nas armas sem textura).
 */
public final class StolenItemFx {

    private StolenItemFx() {
    }

    private static BakedModel model(ItemStack stack, Level level) {
        return Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
    }

    /** A arma 3D desenhada só pelo mod, sem textura no modelo (TaCZ)? Senão é o ícone chato comum. */
    public static boolean standingGun(ItemStack stack, Level level) {
        BakedModel m = model(stack, level);
        return m.isCustomRenderer() && !hasTexture(m);
    }

    /** O modelo tem textura (de onde sai o farelo)? Senão sai o quadriculado roxo. */
    private static boolean hasTexture(BakedModel m) {
        try {
            return !MissingTextureAtlasSprite.getLocation().equals(m.getParticleIcon(ModelData.EMPTY).contents().name());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** O farelo: pedacinho cinza liso (o do concreto cinza-claro). */
    private static final BlockParticleOption CRUMB =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());

    /** Uma mordida: farelo cinza voando da boca dele. */
    public static void crunch(FroggydudeEntity froggy, ItemStack stack, boolean first) {
        Level level = froggy.level();
        if (stack.isEmpty() || !level.isClientSide) return;
        Vec3 look = froggy.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4D ? Vec3.ZERO : look.normalize();
        Vec3 mouth = froggy.position().add(look.scale(0.45D)).add(0, froggy.getBbHeight() * 0.85D, 0);
        RandomSource r = froggy.getRandom();
        int n = first ? 12 : 6;
        for (int i = 0; i < n; i++) {
            Particle p = Minecraft.getInstance().particleEngine.createParticle(CRUMB, mouth.x + r.nextGaussian() * 0.15D,
                    mouth.y + r.nextGaussian() * 0.1D, mouth.z + r.nextGaussian() * 0.15D, r.nextGaussian() * 0.08D,
                    0.05D + r.nextDouble() * 0.08D, r.nextGaussian() * 0.08D);
            // pedaço liso inteiro parece quadrado grande: metade do tamanho, cara de farelo
            if (p != null) p.scale(0.5F);
        }
    }
}
