package com.froggydude.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Nuvem de poeira que o galope levanta (vs AJTHEBOLD, 0:34 e 7:45): sai dos
 * pés, vai crescendo, sobe devagar e some. Pega a cor do chão onde nasceu
 * (areia = bege, terra = marrom, pedra = cinza), meio clareada, como poeira.
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
        this.gravity = -0.012F;   // sobe devagarinho
        this.hasPhysics = true;
        this.lifetime = 18 + this.random.nextInt(16);
        this.startSize = 0.2F + this.random.nextFloat() * 0.14F;
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
        float r = 0.70F, g = 0.67F, b = 0.62F;
        if (col != 0) {
            r = ((col >> 16) & 255) / 255F * 0.6F + 0.32F;
            g = ((col >> 8) & 255) / 255F * 0.6F + 0.30F;
            b = (col & 255) / 255F * 0.6F + 0.28F;
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
