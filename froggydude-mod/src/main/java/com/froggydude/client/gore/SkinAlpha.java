package com.froggydude.client.gore;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Onde a skin de cada jogador tem pixel (o mapa 64x64, true = aparece). O sangue grudado usa
 * pra saber se a camada de fora (chapéu, jaqueta, mangas, calça) tem alguma coisa ali: se não
 * tem, o sangue vai na pele, colado na parte de dentro. Na camada vazia ele ficava boiando meio
 * pixel pra fora (a "casca" em volta da cabeça).
 *
 * Lê do PNG, nunca da placa de vídeo (no celular ler textura de volta é arriscado): a textura
 * viva (DynamicTexture), o arquivo que o jogo guardou quando baixou a skin (HttpTexture) ou o
 * recurso (as skins padrão). Sem conseguir: null, e quem usa põe tudo na pele.
 */
final class SkinAlpha {

    private static final int N = 64;
    /** O jogo descarta pixel com menos de 0,1 de alfa. */
    private static final int VISIBLE = 26;
    private static final long RETRY_MS = 5000L;
    private static final boolean[] UNKNOWN = new boolean[0];
    private static final Map<ResourceLocation, boolean[]> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, Long> RETRY = new HashMap<>();
    @Nullable
    private static final Field HTTP_FILE = field();
    @Nullable
    private static final Method HTTP_LOAD = method();

    private SkinAlpha() {
    }

    @Nullable
    private static Field field() {
        try {
            return ObfuscationReflectionHelper.findField(HttpTexture.class, "f_117994_");
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** O mesmo leitor que o jogo usa pra skin baixada (converte a skin antiga 64x32). */
    @Nullable
    private static Method method() {
        try {
            return ObfuscationReflectionHelper.findMethod(HttpTexture.class, "m_118018_", InputStream.class);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** O mapa da skin dessa pessoa, ou null se não deu pra ler (ainda baixando, ou de outro mod). */
    @Nullable
    static boolean[] of(AbstractClientPlayer player) {
        ResourceLocation loc = player.getSkinTextureLocation();
        boolean[] got = CACHE.get(loc);
        if (got != null) return got == UNKNOWN ? null : got;
        long now = Util.getMillis();
        Long retry = RETRY.get(loc);
        if (retry != null && now < retry) return null;
        boolean[] read = read(loc);
        if (read != null) {
            CACHE.put(loc, read);
            RETRY.remove(loc);
            return read;
        }
        RETRY.put(loc, now + RETRY_MS);
        return null;
    }

    static void clear() {
        CACHE.clear();
        RETRY.clear();
    }

    @Nullable
    private static boolean[] read(ResourceLocation loc) {
        Minecraft mc = Minecraft.getInstance();
        AbstractTexture tex = mc.getTextureManager().getTexture(loc, null);
        try {
            if (tex instanceof DynamicTexture dt) {
                NativeImage img = dt.getPixels();
                return img == null ? null : alpha(img, false);
            }
            if (tex instanceof HttpTexture http) {
                File file = HTTP_FILE == null ? null : (File) HTTP_FILE.get(http);
                if (file == null || !file.isFile()) return null;
                try (InputStream in = new FileInputStream(file)) {
                    if (HTTP_LOAD != null) {
                        try (NativeImage img = (NativeImage) HTTP_LOAD.invoke(http, in)) {
                            return img == null ? null : alpha(img, false);
                        }
                    }
                    try (NativeImage img = NativeImage.read(in)) {
                        return alpha(img, true);
                    }
                }
            }
            // as skins padrão (e qualquer uma que venha de um pacote de recursos)
            Optional<Resource> res = mc.getResourceManager().getResource(loc);
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                return alpha(img, true);
            }
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /**
     * true onde o pixel aparece. Skin maior (HD) é lida no mesmo layout; a antiga 64x32 só tem a
     * metade de cima e, como no jogo, um chapéu sem nenhum furo não aparece.
     */
    @Nullable
    private static boolean[] alpha(NativeImage img, boolean legacyHack) {
        int w = img.getWidth(), h = img.getHeight();
        if (w < N || h < N / 2 || w % N != 0) return null;
        int s = w / N;
        boolean legacy = h * 2 == w;
        boolean[] out = new boolean[N * N];
        for (int y = 0; y < N; y++) {
            if (legacy && y >= N / 2) break;
            for (int x = 0; x < N; x++) {
                out[y * N + x] = ((img.getPixelRGBA(x * s, y * s) >>> 24) & 255) >= VISIBLE;
            }
        }
        if (legacy && legacyHack) {
            boolean hole = false;
            for (int y = 0; y < 16 && !hole; y++) {
                for (int x = 32; x < N; x++) {
                    if (((img.getPixelRGBA(x * s, y * s) >>> 24) & 255) < 128) {
                        hole = true;
                        break;
                    }
                }
            }
            if (!hole) {
                for (int y = 0; y < 16; y++) {
                    for (int x = 32; x < N; x++) out[y * N + x] = false;
                }
            }
        }
        return out;
    }
}
