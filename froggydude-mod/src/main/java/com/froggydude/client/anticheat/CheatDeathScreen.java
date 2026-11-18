package com.froggydude.client.anticheat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * (v0.7.42) A tela de morte do anti-trapaça: igual à do jogo (fundo vermelho, "Você morreu!", a mensagem dele, a
 * pontuação), só que o "Renascer" nunca acende e o "Menu principal" pergunta "Deseja mesmo sair?" com Menu principal /
 * Voltar. É uma tela própria (não a DeathScreen do jogo) de propósito: mod de tela de morte (o "Death screen FNAF" do LM
 * desenhava chiado por cima de qualquer DeathScreen, inclusive a nossa) não encosta nela. Nas mortes normais a tela
 * desses mods continua aparecendo.
 */
public class CheatDeathScreen extends Screen {

    private final boolean hardcore;
    private Component score = Component.empty();
    private Button respawn;
    private Button toTitle;
    private int delayTicker;
    /** Abriu: calam os sons de antes (o zumbido de outro mod de tela de morte) uma vez só. */
    private boolean silenced;

    public CheatDeathScreen(boolean hardcore) {
        super(Component.translatable(hardcore ? "deathScreen.title.hardcore" : "deathScreen.title"));
        this.hardcore = hardcore;
    }

    @Override
    protected void init() {
        delayTicker = 0;
        if (!silenced && this.minecraft != null) {
            silenced = true;
            this.minecraft.getSoundManager().stop();
        }
        respawn = addRenderableWidget(Button.builder(
                        Component.translatable(hardcore ? "deathScreen.spectate" : "deathScreen.respawn"), b -> {
                        })
                .bounds(this.width / 2 - 100, this.height / 4 + 72, 200, 20).build());
        respawn.active = false; // nunca
        toTitle = addRenderableWidget(Button.builder(Component.translatable("deathScreen.titleScreen"), b -> askQuit())
                .bounds(this.width / 2 - 100, this.height / 4 + 96, 200, 20).build());
        toTitle.active = false; // como no jogo: 1 s antes de poder sair (não sai sem querer)
        int points = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getScore() : 0;
        score = Component.translatable("deathScreen.score").append(": ")
                .append(Component.literal(Integer.toString(points)).withStyle(ChatFormatting.YELLOW));
    }

    private void askQuit() {
        Minecraft mc = Minecraft.getInstance();
        if (hardcore) {
            exitToTitle(mc);
            return;
        }
        mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) exitToTitle(mc);
            else mc.setScreen(this);
        }, Component.translatable("deathScreen.quit.confirm"), CommonComponents.EMPTY,
                Component.translatable("deathScreen.titleScreen"), CommonComponents.GUI_BACK));
    }

    /** O mesmo "Menu principal" da tela de morte do jogo. */
    static void exitToTitle(Minecraft mc) {
        if (mc.level != null) mc.level.disconnect();
        mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        mc.setScreen(new TitleScreen());
    }

    @Override
    public void tick() {
        super.tick();
        if (++delayTicker == 20 && toTitle != null) toTitle.active = true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // as mesmas cores e posições da tela de morte do jogo
        g.fillGradient(0, 0, this.width, this.height, 0x60500000, 0xA0803030);
        g.pose().pushPose();
        g.pose().scale(2.0F, 2.0F, 2.0F);
        g.drawCenteredString(this.font, this.title, this.width / 2 / 2, 30, 0xFFFFFF);
        g.pose().popPose();
        g.drawCenteredString(this.font, AntiCheatClient.deathMessage(Minecraft.getInstance()), this.width / 2, 85, 0xFFFFFF);
        g.drawCenteredString(this.font, score, this.width / 2, 100, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
