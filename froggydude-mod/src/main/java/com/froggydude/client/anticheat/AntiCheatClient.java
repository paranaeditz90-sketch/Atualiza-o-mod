package com.froggydude.client.anticheat;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.AntiCheatPacket;
import com.froggydude.network.ModNetwork;
import com.froggydude.world.AntiCheat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * (v0.7.42) O lado do jogador no anti-trapaça (world.AntiCheat): o botão "Trapaças?" na tela de criar mundo, o vídeo
 * (CheatVideoScreen), que não deixa outra tela passar na frente, e a tela de morte de quem foi pego: com a mensagem
 * dele e sem o botão de renascer (nem o "renascer na hora" da regra do jogo salva).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AntiCheatClient {

    /** O servidor mandou: morto pra sempre neste mundo. */
    private static boolean condemned;
    /** O servidor mandou ver o vídeo; abre assim que o mundo carregar. */
    private static boolean pendingPlay;
    @Nullable
    private static CheatVideoScreen playing;
    /** A tela de morte que a gente abriu (com a mensagem dele). */
    @Nullable
    private static DeathScreen ourDeath;
    /** O "Deseja mesmo sair?" nosso: sem o "Renascer" que o do jogo tem (e o ESC dele renascia). */
    @Nullable
    private static ConfirmScreen ourQuit;
    // ----- a tela de criar mundo (fraca: não segura a tela velha na memória depois que o mundo é criado)
    private static java.lang.ref.WeakReference<Screen> createScreen = new java.lang.ref.WeakReference<>(null);
    @Nullable
    private static CycleButton<Boolean> cheatsButton;
    /** O que está no botão (true = Sim). Volta pro Não a cada tela nova de criar mundo. */
    private static boolean choice;

    private AntiCheatClient() {
    }

    public static void onPacket(byte action) {
        Minecraft mc = Minecraft.getInstance();
        switch (action) {
            case AntiCheatPacket.PLAY -> pendingPlay = true;
            case AntiCheatPacket.CONDEMNED -> {
                condemned = true;
                if (mc.player != null) mc.player.setShowDeathScreen(true);
            }
            case AntiCheatPacket.CLEAR -> {
                condemned = false;
                pendingPlay = false;
                ourDeath = null;
                // perdoado no meio do vídeo: para na hora
                if (playing != null) {
                    CheatVideoScreen v = playing;
                    playing = null;
                    v.abort();
                }
                // perdoado com a tela de morte aberta: o renascer volta
                if (mc.screen instanceof DeathScreen ds) {
                    Button b = respawnButton(ds);
                    if (b != null) b.active = true;
                }
            }
            default -> {
            }
        }
    }

    static void videoStarted() {
        ModNetwork.CHANNEL.sendToServer(new AntiCheatPacket(AntiCheatPacket.STARTED));
    }

    static void videoFinished(CheatVideoScreen screen) {
        if (playing == screen) playing = null;
        ModNetwork.CHANNEL.sendToServer(new AntiCheatPacket(AntiCheatPacket.DONE));
    }

    /** O vídeo saiu da tela sem acabar: se ainda está no mundo, começa de novo. */
    static void videoInterrupted(CheatVideoScreen screen) {
        if (playing != screen) return;
        playing = null;
        if (Minecraft.getInstance().getConnection() != null) pendingPlay = true;
    }

    private static boolean videoOn() {
        return playing != null && !playing.finished();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (pendingPlay && playing == null && !(mc.screen instanceof ReceivingLevelScreen)) {
            pendingPlay = false;
            playing = new CheatVideoScreen();
            mc.setScreen(playing);
        }
        if (condemned) {
            mc.player.setShowDeathScreen(true); // sem "renascer na hora"
            if (mc.screen == null && mc.player.isDeadOrDying()) {
                ourDeath = new DeathScreen(deathMessage(mc), mc.level.getLevelData().isHardcore());
                mc.setScreen(ourDeath);
            } else if (mc.screen instanceof DeathScreen ds) {
                if (ds != ourDeath) {
                    // a tela de morte de quando volta pro mundo não tem a mensagem: põe a dele
                    ourDeath = new DeathScreen(deathMessage(mc), mc.level.getLevelData().isHardcore());
                    mc.setScreen(ourDeath);
                } else {
                    lockRespawn(ds);
                }
            }
        }
    }

    private static Component deathMessage(Minecraft mc) {
        return Component.translatable("death.attack.froggydude.anticheat",
                mc.player == null ? Component.empty() : mc.player.getDisplayName());
    }

    /** Nenhuma tela passa na frente do vídeo (a de sair do mundo, sim). A de morte de quem foi pego vira a dele. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        Minecraft mc = Minecraft.getInstance();
        // escolheu "Sim" e cancelou a criação: volta pro padrão (senão vazava pro próximo mundo criado sem essa tela)
        if (event.getCurrentScreen() instanceof CreateWorldScreen
                && (next instanceof SelectWorldScreen || next instanceof TitleScreen)) {
            AntiCheat.choose(false);
        }
        if (videoOn() && next != playing && mc.getConnection() != null && mc.level != null
                && !(next instanceof ReceivingLevelScreen)) {
            event.setCanceled(true);
            return;
        }
        if (condemned && next instanceof DeathScreen && next != ourDeath && mc.level != null) {
            ourDeath = new DeathScreen(deathMessage(mc), mc.level.getLevelData().isHardcore());
            event.setNewScreen(ourDeath);
        } else if (condemned && next instanceof ConfirmScreen && next != ourQuit && isQuitConfirm(next)) {
            // o "Menu principal" da tela de morte pergunta "Deseja mesmo sair?" com Menu principal / Renascer (e o ESC
            // renasce): troca por um com Menu principal / Voltar
            ourQuit = new ConfirmScreen(yes -> {
                if (yes) exitToTitle(mc);
                else mc.setScreen(ourDeath != null ? ourDeath
                        : new DeathScreen(deathMessage(mc), mc.level != null && mc.level.getLevelData().isHardcore()));
            }, Component.translatable("deathScreen.quit.confirm"), CommonComponents.EMPTY,
                    Component.translatable("deathScreen.titleScreen"), CommonComponents.GUI_BACK);
            event.setNewScreen(ourQuit);
        }
    }

    private static boolean isQuitConfirm(Screen s) {
        return s.getTitle().getContents() instanceof TranslatableContents tc && tc.getKey().equals("deathScreen.quit.confirm");
    }

    /** O mesmo "Menu principal" da tela de morte do jogo. */
    private static void exitToTitle(Minecraft mc) {
        if (mc.level != null) mc.level.disconnect();
        mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        mc.setScreen(new TitleScreen());
    }

    // ----- o vídeo passando: o mundo atrás dele não precisa ser desenhado (60 quadros por segundo no celular).
    // Sem mixin: com 1 grau de visão o próprio jogo descarta quase todos os pedaços do mundo e os bichos antes de
    // desenhar; a mão e a barra de baixo saem. A tela do vídeo cobre tudo, ninguém vê.

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (videoOn()) event.setFOV(1.0D);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (videoOn()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Pre event) {
        if (videoOn()) event.setCanceled(true);
    }

    /** O vídeo passando: só o som dele toca. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (videoOn() && event.getSound() != null && !playing.isOurSound(event.getSound())) event.setSound(null);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        condemned = false;
        pendingPlay = false;
        playing = null;
        ourDeath = null;
        ourQuit = null;
    }

    // ----- a tela de morte: o renascer não funciona

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        // qualquer tela com "Renascer" (a de morte, alguma pergunta) fica sem ele
        if (condemned) lockRespawn(event.getScreen());
        if (event.getScreen() instanceof CreateWorldScreen && event.getScreen() == createScreen.get()) {
            placeCheatsButton(event.getScreen());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!condemned) return;
        Button b = respawnButton(event.getScreen());
        if (b != null && b.isMouseOver(event.getMouseX(), event.getMouseY())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!condemned) return;
        Button b = respawnButton(event.getScreen());
        if (b != null && b.isFocused()) event.setCanceled(true);
    }

    private static void lockRespawn(Screen s) {
        Button b = respawnButton(s);
        if (b != null) b.active = false;
    }

    @Nullable
    private static Button respawnButton(Screen s) {
        for (GuiEventListener l : s.children()) {
            if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                    && (tc.getKey().equals("deathScreen.respawn") || tc.getKey().equals("deathScreen.spectate"))) {
                return b;
            }
        }
        return null;
    }

    // ----- a tela de criar mundo: "Trapaças?" embaixo de "Permitir trapaças"

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen)) return;
        if (screen != createScreen.get()) {
            // tela nova: volta pro padrão (Não). A mesma tela mudando de tamanho (ou voltando de "Personalizar")
            // guarda a escolha
            createScreen = new java.lang.ref.WeakReference<>(screen);
            choice = false;
            AntiCheat.choose(false);
        }
        cheatsButton = CycleButton.<Boolean>builder(AntiCheatClient::label)
                .withValues(Boolean.FALSE, Boolean.TRUE)
                .withInitialValue(choice)
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable(v
                        ? "froggydude.cheats.tooltip.yes" : "froggydude.cheats.tooltip.no")))
                .create(0, 0, 210, 20, Component.translatable("froggydude.cheats.label"), (b, v) -> {
                    choice = v;
                    AntiCheat.choose(v);
                });
        cheatsButton.visible = false;
        event.addListener(cheatsButton);
        placeCheatsButton(screen);
    }

    private static MutableComponent label(Boolean allowed) {
        return Component.translatable("froggydude.cheats.label").append(" ").append(allowed
                ? Component.translatable("froggydude.cheats.yes").withStyle(ChatFormatting.RED)
                : Component.translatable("froggydude.cheats.no").withStyle(ChatFormatting.GREEN));
    }

    /** Só aparece na aba "Jogo", logo embaixo do "Permitir trapaças" (as abas trocam os botões da tela). */
    private static void placeCheatsButton(Screen screen) {
        if (cheatsButton == null) return;
        AbstractWidget allow = null;
        for (GuiEventListener l : screen.children()) {
            if (l instanceof CycleButton<?> c && isAllowCommands(c)) {
                allow = c;
                break;
            }
        }
        if (allow == null || !allow.visible) {
            cheatsButton.visible = false;
            return;
        }
        cheatsButton.visible = true;
        cheatsButton.setWidth(allow.getWidth());
        cheatsButton.setPosition(allow.getX(), allow.getY() + allow.getHeight() + 8);
    }

    /** O "Permitir trapaças: Sim/Não" do jogo ("options.generic_value" com o nome "selectWorld.allowCommands"). */
    private static boolean isAllowCommands(CycleButton<?> c) {
        if (!(c.getMessage().getContents() instanceof TranslatableContents tc) || !tc.getKey().equals("options.generic_value")
                || tc.getArgs().length < 1 || !(tc.getArgs()[0] instanceof Component name)) {
            return false;
        }
        return name.getContents() instanceof TranslatableContents n && n.getKey().equals("selectWorld.allowCommands");
    }
}
