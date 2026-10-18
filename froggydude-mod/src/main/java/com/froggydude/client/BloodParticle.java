package com.froggydude.client;

import com.froggydude.client.gore.BloodStains;
import com.froggydude.client.gore.ScreenBlood;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

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
        return com.froggydude.client.cutscene.CinematicCamera.active() ? 0.8D : 0.7D;
    }


    private final float baseSize;
    private final float shade;
    /** A cor do sangue (sem a variação de tom), pra mancha na skin de quem a gota acertar. */
    private int color = 0x8E0A0A;
    /** Quem estava em volta do lugar onde ela nasceu (sai de dentro do corpo do bicho): não conta. */
    private int bornInside = Integer.MIN_VALUE;
    private static long lastFaceDrop;

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
        this.color = rgb;
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
        if (bornInside == Integer.MIN_VALUE) bornInside = playerAt(this.x, this.y, this.z);
        super.tick();
        // (v0.3.4) pedaço passando colado no olho de quem joga vira um quadrado gigante
        // tampando a tela (o braço arrancado, em primeira pessoa): some antes. Se vinha
        // na direção da cara, é uma gota NA CARA: suja o rosto e pinga na tela
        if (ClientBloodFx.nearCamera(this.x, this.y, this.z, near())) {
            faceDrop();
            this.remove();
            return;
        }
        // acertou alguém: gruda na skin, no ponto exato onde bateu
        if (!this.onGround && this.age > 1) {
            int id = playerAt(this.x, this.y, this.z);
            if (id >= 0 && id != bornInside && this.level.getEntity(id) instanceof Player p) {
                BloodStains.dropHit(p, new Vec3(this.x, this.y, this.z), new Vec3(this.xd, this.yd, this.zd), color, quadSize);
                this.remove();
                return;
            }
            if (id != bornInside) bornInside = -1;
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

    /** Id do jogador em cujo corpo esse ponto está, ou -1. */
    private int playerAt(double px, double py, double pz) {
        for (Player p : this.level.players()) {
            if (p.isSpectator() || p.isInvisible()) continue;
            if (p.getBoundingBox().inflate(0.04D).contains(px, py, pz)) return p.getId();
        }
        return -1;
    }

    /** A gota veio na cara de quem joga (em primeira pessoa): mancha o rosto e pinga na tela. */
    private void faceDrop() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || this.age < 2 || mc.getCameraEntity() != me || !mc.options.getCameraType().isFirstPerson()) return;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 v = new Vec3(this.xd, this.yd, this.zd);
        if (v.dot(eye.subtract(this.x, this.y, this.z)) <= 0D) return;
        BloodStains.dropHit(me, me.getEyePosition(), v, color, quadSize);
        long now = this.level.getGameTime();
        if (now - lastFaceDrop >= 4) {
            lastFaceDrop = now;
            ScreenBlood.splash(1.2F + quadSize * 10F, color, 0F);
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
