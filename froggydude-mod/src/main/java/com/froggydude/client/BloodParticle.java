package com.froggydude.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Pedaço de sangue: um cubinho vermelho sólido (sprite quadrado cheio), que sai
 * com a velocidade que o jato mandou (sem a "freada" das partículas normais),
 * cai com peso, quica pouco e fica um tempo no chão antes de sumir. Tamanhos
 * variados, do respingo ao naco - igual ao "Eating a Zebra" (nada de pó/névoa).
 */
public class BloodParticle extends TextureSheetParticle {

    private final float baseSize;

    protected BloodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        // pesado (v0.3.5, LM: "deveriam cair mais com a gravidade"): 0,06 b/t² - o
        // dobro de um item caindo - então o jato faz arco curto e despenca
        this.gravity = 1.5F;
        this.friction = 0.97F;
        this.hasPhysics = true;
        this.lifetime = 50 + this.random.nextInt(60);
        // cubinhos de vermelho vivo e tamanhos variados, como no "Eating a Zebra"
        this.baseSize = 0.045F + this.random.nextFloat() * 0.075F;
        this.quadSize = baseSize;
        float r = 0.70F + this.random.nextFloat() * 0.22F;
        this.setColor(r, r * 0.02F, r * 0.02F);
    }

    @Override
    public void tick() {
        super.tick();
        // (v0.3.4) pedaço passando colado no olho de quem joga vira um quadrado gigante
        // tampando a tela (o braço arrancado, em primeira pessoa): some antes
        if (ClientBloodFx.nearCamera(this.x, this.y, this.z, 0.7D)) {
            this.remove();
            return;
        }
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
            // nascendo na cara de quem joga: nem cria (senão aparece um quadro tampando tudo)
            if (ClientBloodFx.nearCamera(x, y, z, 0.7D)) return null;
            BloodParticle particle = new BloodParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
