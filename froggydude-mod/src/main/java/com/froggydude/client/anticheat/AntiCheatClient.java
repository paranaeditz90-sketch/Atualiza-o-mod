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
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.TitleScreen;
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
 * (CheatVideoScreen), que não deixa outra tela passar na frente, e a tela de morte de quem foi pego (CheatDeathScreen):
 * com a mensagem dele e sem renascer (nem o "renascer na hora" da regra do jogo salva), por cima de mod de tela de morte.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AntiCheatClient {

    /** O servidor mandou: morto pra sempre neste mundo. */
    private static boolean condemned;
    /** O servidor mandou ver o vídeo; abre assim que o mundo carregar. */
    private static boolean pendingPlay;
    @Nullable
    private static CheatVideoScreen playing;
    /**
     * A tela de morte do condenado: própria (CheatDeathScreen), não a DeathScreen do jogo, que mod de tela de morte
     * pinta por cima (o "Death screen FNAF" do LM pôs chiado no lugar da mensagem).
     */
    @Nullable
    private static CheatDeathScreen ourDeath;
    /**
     * (v0.7.43, LM: "eu queria a tela de morte e depois a sua"; "só para quem tem o .jar do FNAF") Com o "Death screen
     * FNAF" do LM, o chiado dele passa primeiro, inteiro (35 ticks, 1,75 s), e só depois entra a nossa.
     */
    private static final int INTRO_TICKS = 35;
    private static final String FNAF_PACKAGE = "com.fnafuniverse.";
    @Nullable
    private static Screen intro;
    private static int introTicks;
    /** A de outro mod já passou nesta morte: daqui pra frente, só a nossa (senão as duas ficam trocando). */
    private static boolean introDone;
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
                // perdoado na tela de morte: volta a do jogo, com o renascer
                if (mc.screen instanceof CheatDeathScreen && mc.level != null) {
                    mc.setScreen(new DeathScreen(null, mc.level.getLevelData().isHardcore()));
                }
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
            if (intro != null && mc.screen == intro) {
                // a de outro mod passando: quando acabar o chiado dela, entra a nossa
                if (++introTicks >= INTRO_TICKS) {
                    introDone = true;
                    intro = null;
                    mc.setScreen(death(mc));
                }
                return;
            }
            // morto e sem a nossa tela (a do jogo, a de outro mod, nenhuma): põe a nossa
            if (mc.player.isDeadOrDying() && !(mc.screen instanceof CheatDeathScreen) && !allowedWhileDead(mc.screen)) {
                mc.setScreen(death(mc));
            }
        }
    }

    private static CheatDeathScreen death(Minecraft mc) {
        if (ourDeath == null) ourDeath = new CheatDeathScreen(mc.level != null && mc.level.getLevelData().isHardcore());
        return ourDeath;
    }

    /** As telas que podem aparecer com o condenado morto: a pergunta de sair (a nossa) e as de sair do mundo. */
    private static boolean allowedWhileDead(@Nullable Screen s) {
        return s instanceof ConfirmScreen && !(s instanceof DeathScreen.TitleConfirmScreen)
                || s instanceof TitleScreen || s instanceof GenericDirtMessageScreen || s instanceof ProgressScreen
                || s instanceof ReceivingLevelScreen || s instanceof DisconnectedScreen;
    }

    static Component deathMessage(Minecraft mc) {
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
    }

    /**
     * A tela de morte do condenado é a nossa, por cima de qualquer outra: roda por último (LOWEST), depois que outro mod
     * de tela de morte já trocou a do jogo pela dele.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeathScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (!condemned || mc.level == null || mc.player == null || mc.getConnection() == null) return;
        Screen next = event.getNewScreen();
        if (next instanceof CheatDeathScreen || next == intro) return;
        // a tela do "Death screen FNAF" (o mod do LM) passa primeiro, uma vez por morte. Sem o FNAF (ou com outro mod de
        // tela de morte), a nossa entra direto
        if (next instanceof DeathScreen && next.getClass().getName().startsWith(FNAF_PACKAGE) && !introDone && intro == null) {
            intro = next;
            introTicks = 0;
            return;
        }
        if (next instanceof DeathScreen || mc.player.isDeadOrDying() && !allowedWhileDead(next)) {
            event.setNewScreen(death(mc));
        }
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
        if (event.getSound() == null) return;
        if (videoOn() && !playing.isOurSound(event.getSound())) {
            event.setSound(null);
        } else if (Minecraft.getInstance().screen instanceof CheatDeathScreen) {
            // na nossa tela de morte, som de outro mod de morte (o chiado do FNAF) não toca; o mundo, sim
            String ns = event.getSound().getLocation().getNamespace();
            if (!ns.equals("minecraft") && !ns.equals(ModEntityTypes.MOD_ID)) event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        condemned = false;
        pendingPlay = false;
        playing = null;
        ourDeath = null;
        intro = null;
        introDone = false; // voltou pro mundo: a do outro mod passa de novo antes da nossa
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
