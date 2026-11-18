package com.becomelm.client;

import com.becomelm.BecomeLM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/** O aviso curtinho no pé da tela quando troca de modo ou de câmera (some sozinho). */
final class LmHud {

    static final ResourceLocation ID = BecomeLM.id("hud");
    private static final int SHOW = 40;
    private static final int FADE = 10;

    @Nullable
    private static Component text;
    private static int shownAt;

    private LmHud() {
    }

    static void toast(Component c) {
        text = c;
        shownAt = LmClientEvents.ticks();
    }

    static void render(GuiGraphics gfx, float partial, int w, int h) {
        if (text == null) return;
        float age = LmClientEvents.ticks() - shownAt + partial;
        if (age > SHOW) {
            text = null;
            return;
        }
        int a = Math.round(Mth.clamp((SHOW - age) / FADE, 0F, 1F) * 255F);
        if (a < 8) return;
        Minecraft mc = Minecraft.getInstance();
        int tw = mc.font.width(text);
        int y = h - 58;
        gfx.fill(w / 2 - tw / 2 - 6, y - 4, w / 2 + tw / 2 + 6, y + 12, (a * 160 / 255) << 24);
        gfx.drawString(mc.font, text, w / 2 - tw / 2, y, (a << 24) | 0xE8F5E0, false);
    }
}
