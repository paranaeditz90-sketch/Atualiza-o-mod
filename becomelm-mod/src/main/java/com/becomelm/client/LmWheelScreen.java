package com.becomelm.client;

import com.becomelm.LmMode;
import com.becomelm.LmModes;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * A roda (estilo a de emotes do Essential): no meio fica o "Humano" (desliga), em volta
 * os jeitos do FroggyDude (galope à esquerda, em pé à direita, a fase 2 embaixo), e do
 * lado a fileira das câmeras. Toca/clica pra escolher;
 * no PC dá também pra segurar a tecla, apontar e soltar. Pensada pro celular: gomos
 * grandes, poucas coisas na tela, e o jogo não pausa.
 */
public class LmWheelScreen extends Screen {

    /**
     * Os gomos e onde cada um fica (graus da tela: 0 = direita, 90 = embaixo): o galope e o
     * em pé continuam onde estavam (esquerda e direita); a fase 2 entra embaixo.
     */
    private static final LmMode[] MODES = {LmMode.GALLOP, LmMode.STAND, LmMode.FRENZY};
    private static final float[] FROM = {135F, 270F, 45F};
    private static final float[] TO = {270F, 405F, 135F};
    private static final LmCamera.Shot[] SHOTS = LmCamera.Shot.values();

    private static final int BASE = 0xC0141A14;
    private static final int HOVER = 0xE0397A45;
    private static final int CURRENT = 0xD0244A2B;
    private static final int TEXT = 0xFFE8F5E0;

    /** Abriu pela tecla: soltar a tecla em cima de um gomo escolhe. */
    private final int heldKey;
    private final int openedAt;

    private int cx, cy, outer, inner;
    private int chipW, chipH, chipY, chipX0, chipGap;
    private boolean vertical;

    public LmWheelScreen(int heldKey) {
        super(Component.translatable("becomelm.wheel"));
        this.heldKey = heldKey;
        this.openedAt = LmClientEvents.ticks();
    }

    @Override
    protected void init() {
        chipH = 22;
        chipGap = 4;
        if (width > height * 1.3F) {
            // deitado (o celular jogando): roda grande à esquerda, câmeras numa coluna à direita
            outer = Mth.clamp(Math.min(height / 2 - 12, width * 28 / 100), 48, 150);
            inner = outer * 42 / 100;
            cx = Math.max(outer + 10, width * 38 / 100);
            cy = height / 2;
            chipW = Mth.clamp(width - (cx + outer + 24) - 10, 60, 110);
            chipX0 = cx + outer + 24;
            chipY = (height - SHOTS.length * (chipH + chipGap) + chipGap) / 2;
            vertical = true;
        } else {
            int rows = chipH + 16;
            outer = Mth.clamp(Math.min(width, height - rows * 2) * 40 / 100, 48, 150);
            inner = outer * 42 / 100;
            cx = width / 2;
            cy = Math.max(outer + 8, (height - rows) / 2);
            chipW = Mth.clamp((width - 16 - chipGap * (SHOTS.length - 1)) / SHOTS.length, 44, 96);
            int total = chipW * SHOTS.length + chipGap * (SHOTS.length - 1);
            chipX0 = (width - total) / 2;
            chipY = Math.min(height - chipH - 6, cy + outer + 12);
            vertical = false;
        }
    }

    /** Onde fica o botão da câmera i. */
    private int chipX(int i) {
        return vertical ? chipX0 : chipX0 + i * (chipW + chipGap);
    }

    private int chipY(int i) {
        return vertical ? chipY + i * (chipH + chipGap) : chipY;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        Minecraft mc = Minecraft.getInstance();
        LmMode mode = mc.player == null ? LmMode.HUMAN : LmModes.get(mc.player);
        Object hover = hovered(mouseX, mouseY);

        // gomos
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = gfx.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < MODES.length; i++) {
            int col = hover == MODES[i] ? HOVER : mode == MODES[i] ? CURRENT : BASE;
            ring(bb, m, FROM[i] + 2F, TO[i] - 2F, inner + 3, outer, col);
        }
        int mid = hover == LmMode.HUMAN ? HOVER : mode == LmMode.HUMAN ? CURRENT : BASE;
        ring(bb, m, 0F, 360F, 0, inner - 1, mid);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // nomes dos gomos
        for (int i = 0; i < MODES.length; i++) {
            float a = (FROM[i] + TO[i]) / 2F * Mth.DEG_TO_RAD;
            int r = (inner + outer) / 2;
            label(gfx, Component.translatable("becomelm.mode." + MODES[i].id), cx + Math.round(Mth.cos(a) * r),
                    cy + Math.round(Mth.sin(a) * r));
        }
        label(gfx, Component.translatable("becomelm.mode.human"), cx, cy);

        // câmeras
        LmCamera.Shot cur = LmCamera.shot();
        for (int i = 0; i < SHOTS.length; i++) {
            int x = chipX(i), y = chipY(i);
            int col = hover == SHOTS[i] ? HOVER : cur == SHOTS[i] ? CURRENT : BASE;
            gfx.fill(x, y, x + chipW, y + chipH, col);
            Component t = SHOTS[i].label();
            int tw = font.width(t);
            if (tw > chipW - 4) {
                // o nome não cabe (celular em pé): letra menor
                gfx.pose().pushPose();
                float s = (chipW - 4) / (float) tw;
                gfx.pose().translate(x + chipW / 2F, y + chipH / 2F, 0);
                gfx.pose().scale(s, s, 1F);
                gfx.drawString(font, t, -tw / 2, -4, TEXT, false);
                gfx.pose().popPose();
            } else {
                gfx.drawString(font, t, x + (chipW - tw) / 2, y + 7, TEXT, false);
            }
        }
        Component cam = Component.translatable("becomelm.camera");
        int camX = vertical ? chipX0 + (chipW - font.width(cam)) / 2 : (width - font.width(cam)) / 2;
        gfx.drawString(font, cam, camX, chipY - 11, 0xB0E8F5E0, false);
    }

    private void label(GuiGraphics gfx, Component c, int x, int y) {
        String[] lines = c.getString().split("\\n");
        int y0 = y - lines.length * 5;
        for (int i = 0; i < lines.length; i++) {
            gfx.drawString(font, lines[i], x - font.width(lines[i]) / 2, y0 + i * 10 + 1, TEXT, false);
        }
    }

    /** Um pedaço de anel (ou disco, com dentro = 0), em fatias de 6 graus. */
    private void ring(BufferBuilder bb, Matrix4f m, float fromDeg, float toDeg, float rIn, float rOut, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        int steps = Math.max(2, Math.round((toDeg - fromDeg) / 6F));
        for (int s = 0; s < steps; s++) {
            float a0 = (fromDeg + (toDeg - fromDeg) * s / steps) * Mth.DEG_TO_RAD;
            float a1 = (fromDeg + (toDeg - fromDeg) * (s + 1) / steps) * Mth.DEG_TO_RAD;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float ix0 = cx + c0 * rIn, iy0 = cy + s0 * rIn, ix1 = cx + c1 * rIn, iy1 = cy + s1 * rIn;
            float ox0 = cx + c0 * rOut, oy0 = cy + s0 * rOut, ox1 = cx + c1 * rOut, oy1 = cy + s1 * rOut;
            bb.vertex(m, ix0, iy0, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, ox0, oy0, 0).color(r, g, b, a).endVertex();
            bb.vertex(m, ox1, oy1, 0).color(r, g, b, a).endVertex();
            if (rIn > 0F) {
                bb.vertex(m, ix0, iy0, 0).color(r, g, b, a).endVertex();
                bb.vertex(m, ox1, oy1, 0).color(r, g, b, a).endVertex();
                bb.vertex(m, ix1, iy1, 0).color(r, g, b, a).endVertex();
            }
        }
    }

    /** O que está debaixo do dedo/mouse: um LmMode, uma câmera ou null. */
    private Object hovered(double mx, double my) {
        for (int i = 0; i < SHOTS.length; i++) {
            int x = chipX(i), y = chipY(i);
            if (mx >= x && mx < x + chipW && my >= y && my < y + chipH) return SHOTS[i];
        }
        double dx = mx - cx, dy = my - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < inner) return LmMode.HUMAN;
        if (d > outer * 1.25D) return null;
        float ang = (float) (Mth.atan2(dy, dx) * Mth.RAD_TO_DEG);
        for (int i = 0; i < MODES.length; i++) {
            float rel = ((ang - FROM[i]) % 360F + 360F) % 360F;
            if (rel < TO[i] - FROM[i]) return MODES[i];
        }
        return null;
    }

    private boolean choose(Object o) {
        if (o instanceof LmMode mode) {
            LmClientEvents.chooseMode(mode);
        } else if (o instanceof LmCamera.Shot s) {
            LmCamera.set(s);
        } else {
            return false;
        }
        onClose();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!choose(hovered(mx, my))) onClose();
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // segurou a tecla, apontou e soltou: escolhe (uma batidinha só deixa a roda aberta)
        if (heldKey != InputConstants.UNKNOWN.getValue() && keyCode == heldKey
                && LmClientEvents.ticks() - openedAt > 6 && minecraft != null) {
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            if (!choose(hovered(mx, my))) onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
}
