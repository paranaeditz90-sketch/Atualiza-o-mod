package com.froggydude.client.screen;

import com.froggydude.config.AiLevel;
import com.froggydude.config.FroggyClientConfig;
import com.froggydude.config.FroggyConfig;
import com.froggydude.config.GoreLevel;
import com.froggydude.init.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Mods > Froggydude > Configurar, em abas com ícone (v0.7.28; o LM pediu pro FroggyDude original, que não entende
 * português): IA (o quanto ele pensa e a invasão), Gore (a violência gráfica, com o aviso) e Idioma (as duas
 * bandeiras: o mod segue o idioma do Minecraft e a bandeira acesa é a que vale agora). A IA é do mundo: vale no mundo
 * de um jogador (no servidor, quem manda é o config do servidor).
 */
public class FroggySettingsScreen extends Screen {

    private enum Tab { AI, GORE, LANGUAGE }

    /** A aba aberta (volta nela depois dos avisos, da tela de idioma e ao reabrir). */
    private static Tab lastTab = Tab.AI;

    private static final ResourceLocation FLAG_US = flag("flag_us");
    private static final ResourceLocation FLAG_BR = flag("flag_br");
    private static final ResourceLocation FLAG_US_SMALL = flag("flag_us_small");
    private static final ResourceLocation FLAG_BR_SMALL = flag("flag_br_small");
    private static final int US_W = 74, BR_W = 56, FLAG_H = 39;
    /** Onde começa o conteúdo da aba (embaixo do título e das abas). */
    private static final int TOP = 52;

    private final Screen parent;
    private GoreLevel chosen;
    private CycleButton<GoreLevel> button;
    private CycleButton<AiLevel> aiButton;
    private CycleButton<Boolean> invasionButton;
    private Button warningButton;
    private Button languageButton;

    public FroggySettingsScreen(Screen parent) {
        super(Component.translatable("froggydude.settings.title"));
        this.parent = parent;
        this.chosen = FroggyClientConfig.gore();
    }

    private static ResourceLocation flag(String name) {
        return new ResourceLocation(ModEntityTypes.MOD_ID, "textures/gui/" + name + ".png");
    }

    @Override
    protected void init() {
        button = null;
        aiButton = null;
        invasionButton = null;
        warningButton = null;
        languageButton = null;
        Tab[] tabs = Tab.values();
        int gap = 4;
        int tabW = Math.min(96, (this.width - 16 - gap * (tabs.length - 1)) / tabs.length);
        int x = this.width / 2 - (tabW * tabs.length + gap * (tabs.length - 1)) / 2;
        for (Tab t : tabs) {
            this.addRenderableWidget(new TabButton(x, 24, tabW, 20, t));
            x += tabW + gap;
        }
        int cx = this.width / 2;
        switch (lastTab) {
            case AI -> {
                aiButton = CycleButton.<AiLevel>builder(level -> Component.translatable(level.key())
                                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(aiColor(level)))))
                        .withValues(AiLevel.values())
                        .withInitialValue(FroggyConfig.ai())
                        .create(cx - 110, TOP, 220, 20, Component.translatable("froggydude.settings.ai"),
                                (b, level) -> FroggyConfig.setAi(level));
                this.addRenderableWidget(aiButton);
                invasionButton = CycleButton.onOffBuilder(FroggyConfig.invasion())
                        .create(cx - 110, TOP + 60, 220, 20, Component.translatable("froggydude.settings.invasion"),
                                (b, on) -> FroggyConfig.setInvasion(on));
                this.addRenderableWidget(invasionButton);
            }
            case GORE -> {
                button = CycleButton.<GoreLevel>builder(level -> GoreScreens.name(level).copy()
                                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GoreScreens.color(level)))))
                        .withValues(GoreLevel.values())
                        .withInitialValue(chosen)
                        .create(cx - 110, TOP + 14, 220, 20, Component.translatable("froggydude.settings.gore"),
                                (b, level) -> choose(level));
                this.addRenderableWidget(button);
                warningButton = Button.builder(Component.translatable("froggydude.settings.warning"),
                                b -> this.minecraft.setScreen(new GoreWarningScreen(this, false)))
                        .bounds(cx - 110, TOP + 70, 220, 20).build();
                this.addRenderableWidget(warningButton);
            }
            case LANGUAGE -> {
                // a tela de idioma do próprio Minecraft (o mod segue ela)
                languageButton = Button.builder(Component.translatable("froggydude.settings.language.change"),
                                b -> this.minecraft.setScreen(new LanguageSelectScreen(this, this.minecraft.options,
                                        this.minecraft.getLanguageManager())))
                        .bounds(cx - 110, TOP + 100, 220, 20).build();
                this.addRenderableWidget(languageButton);
            }
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build());
    }

    private void select(Tab tab) {
        if (tab == lastTab) return;
        lastTab = tab;
        this.rebuildWidgets();
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

    /** Do cinza (Mín) ao verde-sapo forte (Máx); o Real é vermelho-sangue. */
    private static int aiColor(AiLevel level) {
        return switch (level) {
            case MIN -> 0xB0B0B0;
            case MEDIO -> 0xC8E0A0;
            case ALTO -> 0x8CD25A;
            case EXTRA -> 0x5AC83C;
            case MAX -> 0x2EE02E;
            case REAL -> 0xE03030;
        };
    }

    /** O mod está em português agora? (Só tem pt_br; qualquer outro idioma cai no inglês.) */
    private static boolean portuguese() {
        return "pt_br".equals(Minecraft.getInstance().getLanguageManager().getSelected());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        int wrap = Math.min(this.width - 40, 340);
        // nada passa por cima do Concluído (no celular a tela é baixa)
        int bottom = this.height - 34;
        int cx = this.width / 2;
        switch (lastTab) {
            case AI -> {
                int y = TOP + 26;
                if (aiButton != null) {
                    y = text(g, Component.translatable(aiButton.getValue().key() + ".desc"), y, wrap, bottom - 24);
                }
                if (invasionButton != null) {
                    invasionButton.setY(Math.min(y + 4, bottom - 20));
                    text(g, Component.translatable("froggydude.settings.invasion.desc"), invasionButton.getY() + 24, wrap, bottom);
                }
            }
            case GORE -> {
                g.drawCenteredString(this.font, Component.translatable("froggydude.settings.caution"), cx, TOP, 0xFF5050);
                int y = text(g, GoreScreens.describe(FroggyClientConfig.gore()), TOP + 40, wrap, bottom - 24);
                if (warningButton != null) warningButton.setY(Math.min(y + 4, bottom - 20));
            }
            case LANGUAGE -> renderLanguage(g, cx, wrap, bottom);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    /** As duas bandeiras: a do idioma que vale agora acesa e com moldura, a outra apagada. */
    private void renderLanguage(GuiGraphics g, int cx, int wrap, int bottom) {
        boolean pt = portuguese();
        int gap = 24;
        int x = cx - (US_W + gap + BR_W) / 2;
        int y = TOP + 4;
        drawFlag(g, FLAG_US, x, y, US_W, !pt);
        drawFlag(g, FLAG_BR, x + US_W + gap, y, BR_W, pt);
        g.drawCenteredString(this.font, "English", x + US_W / 2, y + FLAG_H + 6, pt ? 0x707070 : 0xFFFFFF);
        g.drawCenteredString(this.font, "Português", x + US_W + gap + BR_W / 2, y + FLAG_H + 6, pt ? 0xFFFFFF : 0x707070);
        String code = this.minecraft.getLanguageManager().getSelected();
        LanguageInfo info = this.minecraft.getLanguageManager().getLanguage(code);
        Component name = info != null ? info.toComponent() : Component.literal(code);
        int ty = text(g, Component.translatable("froggydude.settings.language.follows", name), y + FLAG_H + 22, wrap, bottom);
        // outro idioma (espanhol, português de Portugal...): o jogo cai no inglês pro que não tem tradução
        if (!pt && !code.startsWith("en_")) {
            ty = text(g, Component.translatable("froggydude.settings.language.fallback"), ty, wrap, bottom);
        }
        if (languageButton != null) languageButton.setY(Math.min(ty + 6, bottom - 20));
    }

    private static void drawFlag(GuiGraphics g, ResourceLocation tex, int x, int y, int w, boolean on) {
        if (on) {
            g.renderOutline(x - 2, y - 2, w + 4, FLAG_H + 4, 0xFFFFFFFF);
        } else {
            g.setColor(0.3F, 0.3F, 0.3F, 1.0F);
        }
        g.blit(tex, x, y, w, FLAG_H, 0F, 0F, w, FLAG_H, w, FLAG_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Texto centralizado, quebrado em linhas, de y até antes de maxY; devolve onde parou. */
    private int text(GuiGraphics g, Component c, int y, int wrap, int maxY) {
        for (FormattedCharSequence line : this.font.split(c, wrap)) {
            if (y + 9 > maxY) break;
            g.drawCenteredString(this.font, line, this.width / 2, y, 0xC8C8C8);
            y += 11;
        }
        return y;
    }

    /** Uma aba: fundo, ícone e o nome; a aberta fica verde-sapo. */
    private final class TabButton extends Button {
        private final Tab tab;

        TabButton(int x, int y, int w, int h, Tab tab) {
            super(x, y, w, h, Component.translatable("froggydude.settings.tab." + tab.name().toLowerCase(Locale.ROOT)),
                    b -> select(tab), DEFAULT_NARRATION);
            this.tab = tab;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean open = tab == lastTab;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, open ? 0xFF2F4A22 : isHoveredOrFocused() ? 0xFF303030 : 0xFF1C1C1C);
            g.renderOutline(x, y, w, h, open ? 0xFF8CD25A : 0xFF4A4A4A);
            int iy = y + (h - 16) / 2;
            switch (tab) {
                case AI -> g.renderItem(new ItemStack(Items.ENDER_EYE), x + 3, iy);
                case GORE -> g.renderItem(new ItemStack(Items.REDSTONE), x + 3, iy);
                case LANGUAGE -> {
                    // a bandeira do idioma que vale agora
                    if (portuguese()) g.blit(FLAG_BR_SMALL, x + 4, y + 5, 0F, 0F, 15, 10, 15, 10);
                    else g.blit(FLAG_US_SMALL, x + 2, y + 5, 0F, 0F, 19, 10, 19, 10);
                }
            }
            g.drawString(FroggySettingsScreen.this.font, getMessage(), x + 24, y + (h - 8) / 2, open ? 0xFFFFFF : 0xB0B0B0);
        }
    }
}
