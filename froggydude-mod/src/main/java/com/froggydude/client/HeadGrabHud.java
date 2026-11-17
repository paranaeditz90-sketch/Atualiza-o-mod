package com.froggydude.client;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.HeadGrab;
import com.froggydude.init.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/**
 * (v0.7.41) O alerta da cutscene da cabeça (entity.HeadGrab; LM: "a cutscene vai alertar em cima da hotbar (no meio
 * entre corações e barra de fome), piscando em vermelho com ícone de alerta"): o triângulo vermelho no vão entre os
 * corações e a fome, e em cima dele o que fazer, piscando: "ATIRE NELE!" (arma de fogo, arco, besta), "BATA NELE! 0/2"
 * (espada, machado, arma de mod) ou "SOQUE ELE! 0/3", com a barra do tempo que sobra (o rugido) diminuindo embaixo.
 * Só pra quem está embaixo dele.
 */
public final class HeadGrabHud {

    private static final ResourceLocation ICON = new ResourceLocation(ModEntityTypes.MOD_ID, "textures/gui/alert.png");

    private HeadGrabHud() {
    }

    public static void render(GuiGraphics gfx, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || mc.options.hideGui) return;
        CompoundTag st = null;
        for (FroggydudeEntity f : mc.level.getEntitiesOfClass(FroggydudeEntity.class, me.getBoundingBox().inflate(6D))) {
            if (f.getFroggyState() != FroggyState.HEAD_GRAB) continue;
            CompoundTag t = f.headGrabState();
            if (!t.isEmpty() && t.getInt("Victim") == me.getId()) {
                st = t;
                break;
            }
        }
        if (st == null) return;
        int need = Math.max(1, st.getInt("Need"));
        int hits = Mth.clamp(st.getInt("Hits"), 0, need);
        float left = 1F - Mth.clamp((mc.level.getGameTime() - st.getLong("GrabTime") + partial) / HeadGrab.WINDOW, 0F, 1F);
        float time = (me.tickCount + partial);
        // pisca ~3 vezes por segundo, sem sumir de todo
        float blink = 0.55F + 0.45F * (0.5F + 0.5F * Mth.sin(time * 0.95F));
        int a = (int) (255 * blink) << 24;
        int cx = w / 2;
        // o ícone no vão entre os corações e a fome (os dois começam 10 px do meio)
        int iy = h - 44;
        gfx.setColor(1F, 1F, 1F, blink);
        gfx.blit(ICON, cx - 7, iy, 14, 14, 0F, 0F, 16, 16, 16, 16);
        gfx.setColor(1F, 1F, 1F, 1F);
        // o texto acima (acima da armadura também, que fica do lado dos corações)
        String text;
        if (need == 1) {
            text = I18n.get("froggydude.qte.shoot");
        } else {
            ItemStack hand = me.getMainHandItem();
            boolean blade = hand.getItem() instanceof SwordItem || hand.getItem() instanceof AxeItem || need == 2;
            text = I18n.get(blade ? "froggydude.qte.hit" : "froggydude.qte.punch", hits, need);
        }
        int tw = mc.font.width(text);
        int ty = h - 64;
        gfx.fill(cx - tw / 2 - 4, ty - 3, cx + tw / 2 + 4, ty + 12, (int) (150 * blink) << 24);
        gfx.drawString(mc.font, text, cx - tw / 2, ty, a | 0xFF2A2A, true);
        // o tempo que sobra
        int bw = Math.max(tw, 60);
        int filled = (int) (bw * left);
        gfx.fill(cx - bw / 2, ty + 10, cx + bw / 2, ty + 12, 0x88000000);
        gfx.fill(cx - bw / 2, ty + 10, cx - bw / 2 + filled, ty + 12, a | 0xE01818);
    }
}
