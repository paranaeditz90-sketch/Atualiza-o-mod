package com.froggydude.client;

import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;

/**
 * (v0.7.47) O aviso da raiva 2 (LM: "aparece em baixo na hotbar: 'FUJA! AGORA!' piscando em vermelho, com símbolo de
 * perigo, amarelo"): enquanto um FroggyDude a até 64 blocos estiver na raiva 2 (trocando de pose ou parado no "()"),
 * "⚠ FUJA! AGORA! ⚠" em cima da hotbar, o texto vermelho piscando e os ⚠ amarelos.
 */
public final class Rage2Hud {

    private Rage2Hud() {
    }

    public static void render(GuiGraphics gfx, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || mc.options.hideGui) return;
        boolean on = false;
        for (FroggydudeEntity f : mc.level.getEntitiesOfClass(FroggydudeEntity.class, me.getBoundingBox().inflate(64D))) {
            if (f.inRage2()) {
                on = true;
                break;
            }
        }
        if (!on) return;
        float time = me.tickCount + partial;
        // pisca rápido (~2,5 vezes por segundo), quase some e volta
        float blink = 0.25F + 0.75F * (0.5F + 0.5F * Mth.sin(time * 0.8F));
        int a = Mth.clamp((int) (255 * blink), 8, 255) << 24;
        String text = I18n.get("froggydude.rage2.run");
        String warn = "\u26A0";
        int tw = mc.font.width(text), ww = mc.font.width(warn), gap = 4;
        int total = ww + gap + tw + gap + ww;
        int cx = w / 2, y = h - 62;
        gfx.pose().pushPose();
        gfx.pose().translate(cx, y, 0);
        gfx.pose().scale(1.5F, 1.5F, 1F);
        int x = -total / 2;
        gfx.fill(x - 4, -3, x + total + 4, 11, (int) (140 * blink) << 24);
        gfx.drawString(mc.font, warn, x, 0, a | 0xFFD200, true);
        gfx.drawString(mc.font, text, x + ww + gap, 0, a | 0xFF2020, true);
        gfx.drawString(mc.font, warn, x + ww + gap + tw + gap, 0, a | 0xFFD200, true);
        gfx.pose().popPose();
    }
}
