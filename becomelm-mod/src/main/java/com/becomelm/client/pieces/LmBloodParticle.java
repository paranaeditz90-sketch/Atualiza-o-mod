package com.becomelm.client.pieces;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * Pedaço de sangue (o mesmo do Froggydude): um cubinho sólido que cai com peso, quica pouco
 * e fica um tempo no chão antes de sumir. Nasce vermelho; {@link #tint} pinta com a cor do
 * sangue do bicho. Perto do olho de quem joga some (virava um quadrado tampando a tela).
 */
public class LmBloodParticle extends TextureSheetParticle {

    private static final double NEAR = 0.7D;

    private final float baseSize;
    private final float shade;

    protected LmBloodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 1.5F;
        this.friction = 0.97F;
        this.hasPhysics = true;
        this.lifetime = 50 + this.random.nextInt(60);
        this.baseSize = 0.045F + this.random.nextFloat() * 0.075F;
        this.quadSize = baseSize;
        this.shade = 0.78F + this.random.nextFloat() * 0.3F;
        tint(0x8E0A0A);
    }

    /** Cor do sangue (0xRRGGBB), com a variação de tom de cada pedaço. */
    public LmBloodParticle tint(int rgb) {
        float r = ((rgb >> 16) & 255) / 255F * shade;
        float g = ((rgb >> 8) & 255) / 255F * shade;
        float b = (rgb & 255) / 255F * shade;
        this.setColor(Math.min(1F, r), Math.min(1F, g), Math.min(1F, b));
        return this;
    }

    /** Pedaço maior (naco) ou menor (gota). */
    public LmBloodParticle size(float mul) {
        this.quadSize = this.baseSize * mul;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (nearCamera(this.x, this.y, this.z)) {
            this.remove();
            return;
        }
        if (this.onGround) {
            // grudou no chão
            this.xd = 0;
            this.zd = 0;
        }
        int left = this.lifetime - this.age;
        if (left < 12) this.quadSize = Math.min(this.quadSize, this.baseSize * 1.6F) * Math.max(0, left) / 12F;
    }

    static boolean nearCamera(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || !mc.gameRenderer.getMainCamera().isInitialized()) return false;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        return cam.distanceToSqr(x, y, z) < NEAR * NEAR;
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
            // nascendo na cara de quem joga: nem cria
            if (nearCamera(x, y, z)) return null;
            LmBloodParticle particle = new LmBloodParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
