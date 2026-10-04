package com.froggydude.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Pedaço de sangue: sai com a velocidade que o jato mandou (sem a "freada"
 * das partículas normais), cai com peso, quica pouco e fica um tempo no chão
 * antes de sumir. Tamanhos variados, do respingo ao naco.
 */
public class BloodParticle extends TextureSheetParticle {

    private final float baseSize;

    protected BloodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 0.9F;
        this.friction = 0.97F;
        this.hasPhysics = true;
        this.lifetime = 50 + this.random.nextInt(60);
        this.baseSize = 0.035F + this.random.nextFloat() * this.random.nextFloat() * 0.13F;
        this.quadSize = baseSize;
        float r = 0.40F + this.random.nextFloat() * 0.32F;
        this.setColor(r, r * 0.03F, r * 0.035F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.onGround) {
            // grudou no chão
            this.xd = 0;
            this.zd = 0;
        }
        int left = this.lifetime - this.age;
        if (left < 12) {
            this.quadSize = this.baseSize * Math.max(0, left) / 12F;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            BloodParticle particle = new BloodParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
