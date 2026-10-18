package com.froggydude.client.screen;

import com.froggydude.config.FroggyClientConfig;
import com.froggydude.config.GoreLevel;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * O aviso da primeira vez que o jogo abre com o mod (LM): conta que tem
 * violência gráfica e deixa escolher o nível. O Extremo ainda pede
 * confirmação na tela dele. Depois muda em Mods > Froggydude > Configurar.
 */
public class GoreWarningScreen extends Screen {

    private final Screen parent;
    private final boolean firstTime;
    private int ticks;

    public GoreWarningScreen(Screen parent, boolean firstTime) {
        super(Component.translatable("froggydude.warning.title"));
        this.parent = parent;
        this.firstTime = firstTime;
    }

    @Override
    protected void init() {
        GoreLevel[] levels = GoreLevel.values();
        int bw = Math.min(118, (this.width - 40) / levels.length - 4);
        int total = levels.length * (bw + 4) - 4;
        int x0 = (this.width - total) / 2;
        int y = this.height - 58;
        for (int i = 0; i < levels.length; i++) {
            GoreLevel level = levels[i];
            Component label = GoreScreens.name(level).copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GoreScreens.color(level))));
            Button b = Button.builder(label, btn -> pick(level))
                    .bounds(x0 + i * (bw + 4), y, bw, 20)
                    .tooltip(Tooltip.create(GoreScreens.describe(level)))
                    .build();
            this.addRenderableWidget(b);
        }
        if (!firstTime) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                    .bounds(this.width / 2 - 60, this.height - 30, 120, 20).build());
        }
    }

    private void pick(GoreLevel level) {
        if (level == GoreLevel.EXTREME) {
            this.minecraft.setScreen(new ExtremeWarningScreen(this, () -> {
                GoreScreens.apply(GoreLevel.EXTREME);
                this.minecraft.setScreen(parent);
            }));
            return;
        }
        GoreScreens.apply(level);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !firstTime;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        // umas manchas nos cantos, paradas
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.55F, 0.03F, 0.03F, 0.85F);
        int s = this.height / 3;
        g.blit(GoreScreens.SPLAT[0], -s / 4, -s / 4, s, s, 0F, 0F, 256, 256, 256, 256);
        g.blit(GoreScreens.SPLAT[3], this.width - s * 3 / 4, -s / 5, s, s, 0F, 0F, 256, 256, 256, 256);
        g.blit(GoreScreens.SPLAT[5], this.width - s * 2 / 3, this.height - s * 2 / 3, s, s, 0F, 0F, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();

        g.pose().pushPose();
        g.pose().translate(this.width / 2F, 26F, 0F);
        g.pose().scale(2.2F, 2.2F, 1F);
        g.drawCenteredString(this.font, this.title, 0, 0, 0xFF3030);
        g.pose().popPose();

        int y = 58;
        for (String key : new String[]{"froggydude.warning.line1", "froggydude.warning.line2", "froggydude.warning.line3"}) {
            List<FormattedCharSequence> lines = this.font.split(Component.translatable(key), Math.min(this.width - 40, 360));
            for (FormattedCharSequence line : lines) {
                g.drawCenteredString(this.font, line, this.width / 2, y, 0xE8E8E8);
                y += 11;
            }
            y += 6;
        }
        GoreLevel now = FroggyClientConfig.gore();
        if (!firstTime) {
            g.drawCenteredString(this.font, Component.translatable("froggydude.warning.current", GoreScreens.name(now)),
                    this.width / 2, this.height - 74, 0xA0A0A0);
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
