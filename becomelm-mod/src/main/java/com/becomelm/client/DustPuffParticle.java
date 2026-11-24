package com.becomelm.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Nuvem de poeira que o galope levanta (vs AJTHEBOLD, 0:34 e 7:45; "never kill
 * my dog", 10 s): sai dos pés, vai crescendo rente ao chão e some. Pega um pouco
 * da cor do chão onde nasceu, mas bem clara, como poeira de verdade.
 */
public class DustPuffParticle extends TextureSheetParticle {

    private final float startSize;
    private final float startAlpha;

    protected DustPuffParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.86F;
        this.gravity = -0.004F;   // fica rente ao chão, quase não sobe
        this.hasPhysics = true;
        this.lifetime = 14 + this.random.nextInt(14);
        this.startSize = 0.22F + this.random.nextFloat() * 0.2F;
        this.quadSize = startSize;
        this.startAlpha = 0.75F + this.random.nextFloat() * 0.15F;
        this.alpha = startAlpha;
        this.roll = this.random.nextFloat() * 6.2832F;
        this.oRoll = this.roll;

        // cor do chão embaixo, clareada pra parecer poeira (grama levanta terra, não verde)
        BlockPos below = BlockPos.containing(x, y - 0.25D, z);
        BlockState state = level.getBlockState(below);
        int col = state.isAir() ? 0 : state.is(BlockTags.DIRT) ? MapColor.DIRT.col
                : state.getMapColor(level, below).col;
        // bem mais clara que o chão (no tapete vermelho do vídeo ela sai rosa-esbranquiçada):
        // da cor do chão, ela sumia em cima dele
        float r = 0.86F, g = 0.84F, b = 0.80F;
        if (col != 0) {
            r = ((col >> 16) & 255) / 255F * 0.3F + 0.68F;
            g = ((col >> 8) & 255) / 255F * 0.3F + 0.64F;
            b = (col & 255) / 255F * 0.3F + 0.62F;
        }
        // em cima da água/vitória-régia (os saltos de folha em folha, fim do vídeo do LM):
        // a nuvem do vídeo é bege, não azul nem verde
        if (state.is(Blocks.LILY_PAD) || !state.getFluidState().isEmpty()
                || level.getBlockState(BlockPos.containing(x, y, z)).is(Blocks.LILY_PAD)) {
            r = 0.86F;
            g = 0.80F;
            b = 0.68F;
        }
        float shade = 0.92F + this.random.nextFloat() * 0.08F;
        this.setColor(Math.min(1F, r * shade), Math.min(1F, g * shade), Math.min(1F, b * shade));
    }

    @Override
    public void tick() {
        super.tick();
        float f = Math.min(1F, (float) this.age / this.lifetime);
        this.quadSize = startSize * (1F + 2.4F * f);
        this.alpha = startAlpha * (1F - f) * (1F - f * 0.3F);
        this.oRoll = this.roll;
        this.roll += 0.02F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            DustPuffParticle particle = new DustPuffParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
