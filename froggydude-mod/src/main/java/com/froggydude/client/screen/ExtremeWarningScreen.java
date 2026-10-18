package com.froggydude.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * A tela própria do Extremo (LM: "Atenção ⚠️ O mod vai conter cenas pesadas,
 * deseja continuar?"). Agressiva de propósito: o fundo pulsa vermelho no
 * ritmo de um coração (o batimento do Warden), o sangue escorre do topo e o
 * texto treme. O "Sim" só libera depois de 2 segundos.
 */
public class ExtremeWarningScreen extends Screen {

    private final Screen parent;
    private final Runnable onConfirm;
    private final RandomSource random = RandomSource.create();
    private final List<float[]> drips = new ArrayList<>();
    private int ticks;
    private Button yes;

    public ExtremeWarningScreen(Screen parent, Runnable onConfirm) {
        super(Component.translatable("froggydude.extreme.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int w = 150;
        yes = Button.builder(Component.translatable("froggydude.extreme.yes")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF2020)).withBold(true)), b -> onConfirm.run())
                .bounds(this.width / 2 - w - 6, this.height - 44, w, 20).build();
        yes.active = ticks >= 40;
        this.addRenderableWidget(yes);
        this.addRenderableWidget(Button.builder(Component.translatable("froggydude.extreme.no"), b -> onClose())
                .bounds(this.width / 2 + 6, this.height - 44, w, 20).build());
        if (drips.isEmpty()) {
            for (int i = 0; i < 26; i++) {
                // x, largura, comprimento, velocidade
                drips.add(new float[]{random.nextFloat(), 2 + random.nextInt(5), 0F, 0.6F + random.nextFloat() * 2.4F});
            }
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (yes != null) yes.active = ticks >= 40;
        if (ticks % 22 == 1) GoreScreens.heartbeat(0.7F + Math.min(0.5F, ticks / 400F));
        for (float[] d : drips) d[2] = Math.min(this.height * 0.9F, d[2] + d[3]);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        float t = ticks + partial;
        // batida do coração: dois picos rápidos e uma pausa
        float phase = (t % 22F) / 22F;
        float beat = (float) (Math.exp(-Math.pow((phase - 0.08F) * 18F, 2)) + 0.6F * Math.exp(-Math.pow((phase - 0.28F) * 18F, 2)));
        int base = 0x16;
        int red = Mth.clamp((int) (0x40 + 0x70 * beat), 0, 255);
        g.fillGradient(0, 0, this.width, this.height, 0xFF000000 | (red << 16) | (base << 8) | base,
                0xFF000000 | ((red / 3) << 16));

        // sangue escorrendo do topo
        for (float[] d : drips) {
            int x = (int) (d[0] * this.width);
            int w = (int) d[1];
            int len = (int) d[2];
            g.fill(x, 0, x + w, len, 0xFF7A0505);
            g.fill(x - 1, len, x + w + 1, len + 3, 0xFF7A0505);
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.6F, 0.02F, 0.02F, 0.9F);
        int s = this.height / 2;
        g.blit(GoreScreens.SPLAT[2], -s / 3, this.height - s * 2 / 3, s, s, 0F, 0F, 256, 256, 256, 256);
        g.blit(GoreScreens.SPLAT[4], this.width - s * 2 / 3, this.height / 3, s, s, 0F, 0F, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();

        // ATENÇÃO, tremendo (mais forte na batida)
        float shake = 1.2F + beat * 2.5F;
        g.pose().pushPose();
        g.pose().translate(this.width / 2F + (random.nextFloat() - 0.5F) * shake, 40F + (random.nextFloat() - 0.5F) * shake, 0F);
        float scale = 3.2F + beat * 0.25F;
        g.pose().scale(scale, scale, 1F);
        g.drawCenteredString(this.font, this.title, 0, 0, 0xFFE0E0);
        g.pose().popPose();

        int y = 86;
        g.pose().pushPose();
        g.pose().translate(this.width / 2F, y, 0F);
        g.pose().scale(1.5F, 1.5F, 1F);
        List<FormattedCharSequence> q = this.font.split(Component.translatable("froggydude.extreme.question"),
                (int) (Math.min(this.width - 30, 420) / 1.5F));
        int qy = 0;
        for (FormattedCharSequence line : q) {
            g.drawCenteredString(this.font, line, 0, qy, 0xFFFFFF);
            qy += 11;
        }
        g.pose().popPose();
        y += (int) (qy * 1.5F) + 14;
        for (String key : new String[]{"froggydude.extreme.line1", "froggydude.extreme.line2"}) {
            for (FormattedCharSequence line : this.font.split(Component.translatable(key), Math.min(this.width - 40, 380))) {
                g.drawCenteredString(this.font, line, this.width / 2, y, 0xE0A0A0);
                y += 11;
            }
            y += 4;
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
