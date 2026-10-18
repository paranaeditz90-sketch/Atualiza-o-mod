package com.froggydude.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Pedaço de sangue: um cubinho sólido (sprite quadrado cheio), que sai com a
 * velocidade que o jato mandou (sem a "freada" das partículas normais), cai
 * com peso, quica pouco e fica um tempo no chão antes de sumir. Tamanhos
 * variados, do respingo ao naco - igual ao "Eating a Zebra" (nada de pó/névoa).
 * Nasce vermelho; {@link #tint} pinta com a cor do sangue do bicho.
 */
public class BloodParticle extends TextureSheetParticle {

    /** Some quando chega perto da câmera; na cutscene (câmera de cinema) mais cedo, senão tampa o close. */
    static double near() {
        return com.froggydude.client.cutscene.CinematicCamera.active() ? 1.3D : 0.7D;
    }


    private final float baseSize;
    private final float shade;

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
        // cubinhos de tamanhos variados, como no "Eating a Zebra"
        this.baseSize = 0.045F + this.random.nextFloat() * 0.075F;
        this.quadSize = baseSize;
        this.shade = 0.78F + this.random.nextFloat() * 0.3F;
        tint(0x8E0A0A);
    }

    /** Cor do sangue (0xRRGGBB), com a variação de tom de cada pedaço. */
    public BloodParticle tint(int rgb) {
        float r = ((rgb >> 16) & 255) / 255F * shade;
        float g = ((rgb >> 8) & 255) / 255F * shade;
        float b = (rgb & 255) / 255F * shade;
        this.setColor(Math.min(1F, r), Math.min(1F, g), Math.min(1F, b));
        return this;
    }

    /** Pedaço maior (naco de carne) ou menor (gota). */
    public BloodParticle size(float mul) {
        this.quadSize = this.baseSize * mul;
        return this;
    }

    public BloodParticle life(int ticks) {
        this.lifetime = ticks;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        // (v0.3.4) pedaço passando colado no olho de quem joga vira um quadrado gigante
        // tampando a tela (o braço arrancado, em primeira pessoa): some antes
        if (ClientBloodFx.nearCamera(this.x, this.y, this.z, near())) {
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
            this.quadSize = Math.min(this.quadSize, this.baseSize * 1.6F) * Math.max(0, left) / 12F;
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
            if (ClientBloodFx.nearCamera(x, y, z, near())) return null;
            BloodParticle particle = new BloodParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
