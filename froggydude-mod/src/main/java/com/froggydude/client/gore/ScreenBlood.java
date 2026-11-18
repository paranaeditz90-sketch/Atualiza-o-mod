package com.froggydude.client.gore;

import com.froggydude.config.FroggyClientConfig;
import com.froggydude.config.GoreLevel;
import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

import java.util.ArrayList;
import java.util.List;

/**
 * Sangue na TELA (LM: "espirrando no chão, na skin do player e na tela do
 * jogo"):
 * - respingos: quando tu acerta um bicho de perto, quando ele te morde, quando
 *   arranca um braço do teu lado. Escorrem devagar e somem (na água, na hora);
 * - película: quando ele te esmaga no chão a tela vai ficando vermelha por
 *   igual, soco a soco, até não dar pra ver nada (POV do vídeo do LM, 36-48 s).
 * Desenhado por baixo do HUD (a barra de itens continua por cima).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScreenBlood {

    private static final ResourceLocation[] SPLAT = new ResourceLocation[6];

    static {
        for (int i = 0; i < SPLAT.length; i++) {
            SPLAT[i] = new ResourceLocation(ModEntityTypes.MOD_ID, "textures/gui/blood_splat_" + i + ".png");
        }
    }

    /**
     * No máximo 14 respingos na tela (eram 28: no celular, em luta, tapava metade da
     * visão - prints do LM, v0.4.2). Menores, mais transparentes e com o meio livre.
     */
    private static final int MAX = 14;
    private static final List<Splat> SPLATS = new ArrayList<>();
    private static float film, filmO;
    /** 1 coração: daqui pra baixo a tela vai ficando vermelha. */
    private static final float LOW_HEALTH = 2.0F;
    private static int filmHold;
    private static float filmR = 0.55F, filmG = 0.02F, filmB = 0.02F;

    private static final class Drip {
        float x;      // posição dentro do respingo (0..1)
        float w;      // largura (fração do respingo)
        float len, lenO, max, speed;
    }

    private static final class Splat {
        float x, y, size;
        int tex, rot;
        int age, life;
        float r, g, b, a;
        final List<Drip> drips = new ArrayList<>();
    }

    private ScreenBlood() {
    }

    private static GoreLevel gore() {
        return FroggyClientConfig.gore();
    }

    /**
     * Respingo na tela. strength ~ dano do golpe; side: -1 mais pra esquerda, 1 mais
     * pra direita (a mão da arma), 0 qualquer lugar.
     */
    public static void splash(float strength, int color, float side) {
        // na cutscene a câmera está do lado de fora: sangue "na tela" não faz sentido
        if (com.froggydude.client.cutscene.CinematicCamera.active()) return;
        GoreLevel g = gore();
        if (!g.screen()) return;
        if (g == GoreLevel.MEDIUM && strength < 6F) return; // Médio: só golpe grande
        RandomSource r = RandomSource.create();
        int n = Mth.clamp(Math.round((0.5F + strength / 5F) * (g == GoreLevel.EXTREME ? 1.25F : 1F)), 1, 4);
        for (int i = 0; i < n; i++) {
            if (SPLATS.size() >= MAX) SPLATS.remove(0);
            Splat s = new Splat();
            // nas bordas (o meio fica livre pra ver quem está na frente), puxado pro lado da arma
            float x = r.nextFloat();
            if (r.nextFloat() < 0.85F) x = x < 0.5F ? x * 0.5F : 1F - (1F - x) * 0.5F;
            if (side != 0 && r.nextFloat() < 0.6F) x = side > 0 ? 0.6F + x * 0.4F : x * 0.4F;
            s.x = x;
            s.y = 0.05F + r.nextFloat() * 0.7F;
            s.size = Mth.clamp(0.11F + strength * 0.015F + r.nextFloat() * 0.09F, 0.09F, 0.36F)
                    * (g == GoreLevel.EXTREME ? 1.1F : 1F);
            // caiu no meio mesmo assim: menor
            if (Math.abs(s.x - 0.5F) < 0.2F && s.y > 0.25F && s.y < 0.75F) s.size *= 0.6F;
            s.tex = r.nextInt(SPLAT.length);
            s.rot = r.nextInt(4);
            s.life = switch (g) {
                case MEDIUM -> 100;
                case HEAVY -> 180;
                default -> 360;
            } + r.nextInt(60);
            float shade = 0.85F + r.nextFloat() * 0.3F;
            s.r = Math.min(1F, ((color >> 16) & 255) / 255F * 1.5F * shade);
            s.g = Math.min(1F, ((color >> 8) & 255) / 255F * 1.5F * shade);
            s.b = Math.min(1F, (color & 255) / 255F * 1.5F * shade);
            s.a = 0.55F + r.nextFloat() * 0.2F;
            int drips = r.nextInt(g == GoreLevel.EXTREME ? 4 : 3);
            for (int k = 0; k < drips; k++) {
                Drip d = new Drip();
                d.x = 0.25F + r.nextFloat() * 0.5F;
                d.w = 0.02F + r.nextFloat() * 0.025F;
                d.max = 0.2F + r.nextFloat() * (g == GoreLevel.EXTREME ? 0.8F : 0.5F);
                d.speed = 0.004F + r.nextFloat() * 0.01F;
                s.drips.add(d);
            }
            SPLATS.add(s);
        }
    }

    /**
     * Película vermelha por igual (esmagado embaixo dele): soma a cada soco - mas só
     * com 1 coração ou menos (LM: "começa a ficar vermelha somente quando o player tá
     * quase morrendo"). Acima disso o soco só respinga.
     */
    public static void film(float add, int color) {
        if (com.froggydude.client.cutscene.CinematicCamera.active()) return;
        GoreLevel g = gore();
        if (!g.screen()) return;
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null || me.getHealth() > LOW_HEALTH) return;
        float mul = g == GoreLevel.EXTREME ? 1.4F : g == GoreLevel.MEDIUM ? 0.5F : 1F;
        film = Math.min(g == GoreLevel.EXTREME ? 1.0F : 0.85F, film + add * mul);
        filmHold = 40;
        filmR = Math.min(1F, ((color >> 16) & 255) / 255F * 1.2F);
        filmG = Math.min(1F, ((color >> 8) & 255) / 255F * 1.2F);
        filmB = Math.min(1F, (color & 255) / 255F * 1.2F);
    }

    public static void clear() {
        SPLATS.clear();
        film = filmO = 0F;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        LocalPlayer p = mc.player;
        if (p == null) {
            clear();
            return;
        }
        GoreLevel g = gore();
        if (!g.screen()) {
            clear();
            return;
        }
        boolean underwater = p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        boolean rain = p.level().isRainingAt(p.blockPosition().above());
        filmO = film;
        // quase morrendo: o vermelho sobe aos poucos (a opacidade aumentando, como no vídeo),
        // mais forte quanto menos vida sobra; com mais de 1 coração vai sumindo
        float hp = p.getHealth();
        float low = p.isAlive() && hp <= LOW_HEALTH ? 0.3F + 0.6F * (1F - hp / LOW_HEALTH) : 0F;
        if (film < low) {
            film = Math.min(low, film + 0.015F);
            filmHold = Math.max(filmHold, 10);
        } else if (filmHold > 0) {
            filmHold--;
        } else {
            float keep = g == GoreLevel.EXTREME ? 0.997F : 0.985F;
            film *= hp > LOW_HEALTH ? 0.95F : keep;
        }
        if (underwater) film *= 0.9F;
        if (film < 0.003F) film = 0F;
        SPLATS.removeIf(s -> {
            s.age += underwater ? 12 : rain ? 2 : 1;
            for (Drip d : s.drips) {
                d.lenO = d.len;
                if (d.len < d.max) d.len = Math.min(d.max, d.len + d.speed);
            }
            return s.age >= s.life;
        });
        if (p.isDeadOrDying()) {
            // morreu: a tela some no vermelho (sem respingo novo depois)
            filmHold = 2;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /** Chamado pelo overlay (ClientModEvents), por baixo do HUD. */
    public static void render(GuiGraphics gfx, float partial, int w, int h) {
        if (SPLATS.isEmpty() && film <= 0F && filmO <= 0F) return;
        float f = Mth.lerp(partial, filmO, film);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (f > 0.003F) {
            // a tela inteira vai ficando vermelha por igual, a opacidade subindo (o POV de quem
            // é esmagado no vídeo do LM, 36,2-48,6 s) - nada de vinheta fechando pelas bordas
            int a = (int) (Mth.clamp(f, 0F, 1F) * 0.92F * 255);
            int col = (a << 24) | ((int) (filmR * 255 * 0.85F) << 16) | ((int) (filmG * 255 * 0.85F) << 8) | (int) (filmB * 255 * 0.85F);
            gfx.fill(0, 0, w, h, col);
        }
        for (Splat s : SPLATS) {
            int left = s.life - s.age;
            float alpha = s.a * Mth.clamp(left / 50F, 0F, 1F);
            if (alpha <= 0.01F) continue;
            int size = (int) (s.size * h);
            int x = (int) (s.x * w) - size / 2;
            int y = (int) (s.y * h) - size / 2;
            gfx.pose().pushPose();
            gfx.pose().translate(x + size / 2F, y + size / 2F, 0F);
            gfx.pose().mulPose(Axis.ZP.rotationDegrees(s.rot * 90F));
            gfx.pose().translate(-size / 2F, -size / 2F, 0F);
            RenderSystem.setShaderColor(s.r, s.g, s.b, alpha);
            gfx.blit(SPLAT[s.tex], 0, 0, size, size, 0F, 0F, 256, 256, 256, 256);
            gfx.pose().popPose();
            // escorrendo: filetes que descem do respingo, em "pixel" grande (estilo do jogo)
            int px = Math.max(1, size / 64);
            int dark = ((int) (alpha * 230) << 24) | ((int) (s.r * 160) << 16) | ((int) (s.g * 160) << 8) | (int) (s.b * 160);
            for (Drip d : s.drips) {
                float len = Mth.lerp(partial, d.lenO, d.len);
                if (len <= 0.01F) continue;
                int dx = x + (int) (d.x * size);
                int dw = Math.max(px, (int) (d.w * size) / px * px);
                int top = y + size / 2;
                int bottom = top + (int) (len * size);
                gfx.fill(dx, top, dx + dw, bottom, dark);
                gfx.fill(dx - px, bottom, dx + dw + px, bottom + px * 2, dark); // a gota na ponta
            }
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }
}
