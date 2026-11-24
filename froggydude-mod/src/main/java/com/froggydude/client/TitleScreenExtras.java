package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * (v0.7.39, LM 16/11) A tela inicial do jogo com a cara do mod:
 * - as frases amarelas sorteadas do lado do "Minecraft" ficam SÓ estas 6, no idioma do jogador (lang:
 *   froggydude.splash.1 a 6; a 6ª leva o nome de quem joga);
 * - uma caixinha do TikTok no canto de baixo à direita (em cima do "Copyright Mojang") que abre o canal do LM,
 *   passando pela tela de confirmação do próprio jogo (que também tem "Copiar", pro caso do celular não abrir o link).
 *   Fica acima das duas linhas do canto (o "Copyright Mojang" e o aviso de versão nova do Forge).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TitleScreenExtras {

    private static final ResourceLocation TIKTOK = new ResourceLocation(ModEntityTypes.MOD_ID, "textures/gui/tiktok.png");
    private static final String TIKTOK_URL = "https://www.tiktok.com/@lm54546";
    private static final int SPLASHES = 6;
    private static final RandomSource RANDOM = RandomSource.create();
    /** O campo da frase na TitleScreen (privado; achado pelo tipo, vale com qualquer nome). */
    private static Field splashField;
    /** A última frase nossa: se a tela só se redesenhou (mudou de tamanho), a frase continua a mesma. */
    private static SplashRenderer ours;

    private TitleScreenExtras() {
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) return;
        replaceSplash(title);
        ImageButton tiktok = new ImageButton(title.width - 22, title.height - 44, 20, 20, 0, 0, 20, TIKTOK, 20, 40,
                b -> ConfirmLinkScreen.confirmLinkNow(TIKTOK_URL, title, true), Component.literal("TikTok @lm54546"));
        tiktok.setTooltip(Tooltip.create(Component.literal("TikTok: @lm54546")));
        event.addListener(tiktok);
    }

    private static void replaceSplash(TitleScreen title) {
        try {
            if (splashField == null) {
                for (Field f : TitleScreen.class.getDeclaredFields()) {
                    if (f.getType() == SplashRenderer.class) {
                        f.setAccessible(true);
                        splashField = f;
                        break;
                    }
                }
                if (splashField == null) return;
            }
            Object cur = splashField.get(title);
            if (cur != null && cur == ours) return;
            ours = new SplashRenderer(pick());
            splashField.set(title, ours);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // sem a troca, fica a frase do jogo
        }
    }

    private static String pick() {
        String name = Minecraft.getInstance().getUser().getName();
        return I18n.get("froggydude.splash." + (1 + RANDOM.nextInt(SPLASHES)), name);
    }
}
