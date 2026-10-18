package com.froggydude.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;

/**
 * Sangue lavando na água: uma nuvem vermelha que sai de quem entrou sujo,
 * vai crescendo, desbota devagar e fica um tempo boiando - a água em volta
 * fica vermelha onde o sangue foi (LM: "fica vermelho a parte onde o sangue
 * foi"). Fora d'água some na hora.
 */
public class BloodCloudParticle extends TextureSheetParticle {

    private final float startSize;
    private final float startAlpha;

    protected BloodCloudParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.92F;
        this.gravity = 0F;
        this.hasPhysics = false;
        this.lifetime = 260 + this.random.nextInt(240);   // 13 a 25 s
        this.startSize = 0.35F + this.random.nextFloat() * 0.3F;
        this.quadSize = startSize;
        this.startAlpha = 0.45F + this.random.nextFloat() * 0.2F;
        this.alpha = startAlpha;
        this.roll = this.random.nextFloat() * 6.2832F;
        this.oRoll = this.roll;
        this.setColor(0.55F, 0.02F, 0.03F);
    }

    public BloodCloudParticle tint(int rgb) {
        this.setColor(((rgb >> 16) & 255) / 255F * 0.9F, ((rgb >> 8) & 255) / 255F * 0.9F, (rgb & 255) / 255F * 0.9F);
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER)) {
            // saiu da água (boiou até a superfície): desmancha rápido
            this.age = Math.max(this.age, this.lifetime - 10);
        }
        float f = Math.min(1F, (float) this.age / this.lifetime);
        this.quadSize = startSize * (1F + 3.5F * (float) Math.sqrt(f));
        this.alpha = startAlpha * (1F - f * f);
        this.oRoll = this.roll;
        this.roll += 0.004F;
        // a corrente leva devagar; sobe um tiquinho
        this.yd += 0.0004D;
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
            BloodCloudParticle particle = new BloodCloudParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
