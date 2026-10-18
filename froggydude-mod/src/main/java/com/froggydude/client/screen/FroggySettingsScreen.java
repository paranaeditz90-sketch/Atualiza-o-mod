package com.froggydude.client.screen;

import com.froggydude.config.FroggyClientConfig;
import com.froggydude.config.GoreLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Mods > Froggydude > Configurar: o nível de violência gráfica, com o aviso
 * escrito. Escolher o Extremo abre a tela de confirmação dele.
 */
public class FroggySettingsScreen extends Screen {

    private final Screen parent;
    private GoreLevel chosen;
    private CycleButton<GoreLevel> button;

    public FroggySettingsScreen(Screen parent) {
        super(Component.translatable("froggydude.settings.title"));
        this.parent = parent;
        this.chosen = FroggyClientConfig.gore();
    }

    @Override
    protected void init() {
        button = CycleButton.<GoreLevel>builder(level -> GoreScreens.name(level).copy()
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GoreScreens.color(level)))))
                .withValues(GoreLevel.values())
                .withInitialValue(chosen)
                .create(this.width / 2 - 110, 64, 220, 20, Component.translatable("froggydude.settings.gore"),
                        (b, level) -> choose(level));
        this.addRenderableWidget(button);
        this.addRenderableWidget(Button.builder(Component.translatable("froggydude.settings.warning"),
                b -> this.minecraft.setScreen(new GoreWarningScreen(this, false)))
                .bounds(this.width / 2 - 110, 90, 220, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void choose(GoreLevel level) {
        if (level == GoreLevel.EXTREME && chosen != GoreLevel.EXTREME) {
            this.minecraft.setScreen(new ExtremeWarningScreen(this, () -> {
                chosen = GoreLevel.EXTREME;
                GoreScreens.apply(chosen);
                this.minecraft.setScreen(this);
            }));
            return;
        }
        chosen = level;
        GoreScreens.apply(level);
    }

    @Override
    public void tick() {
        // voltou do aviso/Extremo sem confirmar: o botão volta pro que está valendo
        GoreLevel now = FroggyClientConfig.gore();
        if (button != null && button.getValue() != now) {
            chosen = now;
            button.setValue(now);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("froggydude.settings.caution"), this.width / 2, 40, 0xFF5050);
        int y = 122;
        for (FormattedCharSequence line : this.font.split(GoreScreens.describe(FroggyClientConfig.gore()), Math.min(this.width - 40, 340))) {
            g.drawCenteredString(this.font, line, this.width / 2, y, 0xC8C8C8);
            y += 11;
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
