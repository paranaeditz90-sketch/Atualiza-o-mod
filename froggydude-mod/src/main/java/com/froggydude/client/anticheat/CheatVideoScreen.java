package com.froggydude.client.anticheat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * (v0.7.42) O vídeo do anti-trapaça (world.AntiCheat), em tela cheia, sem fechar. Vem do pacote de voz interno do jar
 * (o vídeo é do FroggyDude, autorizado; fica fora do Git): {@code assets/froggydude/anticheat/video.json} diz a taxa e
 * quantos quadros, os quadros são JPEG ({@code f0001.jpg}...) e o som é o evento {@code froggydude:anticheat_video}.
 * <p>
 * Celular primeiro (skill zalith-compat): nada nativo, nada de shader. Dois fios de fundo decodificam os JPEG (o stb do
 * próprio jogo, via NativeImage) um pouco à frente; o fio do jogo só manda o quadro da hora pra uma textura fixa
 * (DynamicTexture) e desenha como uma imagem de tela qualquer. Ficou pra trás (celular lento)? Pula quadro, o som não
 * espera. Sem o pacote (o jar do GitHub sem o segredo), não tem vídeo: avisa o servidor na hora.
 */
public class CheatVideoScreen extends Screen {

    private static final ResourceLocation INFO = new ResourceLocation(ModEntityTypes.MOD_ID, "anticheat/video.json");
    private static final ResourceLocation SOUND = new ResourceLocation(ModEntityTypes.MOD_ID, "anticheat_video");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ModEntityTypes.MOD_ID, "anticheat/frame");
    /** Quantos quadros decodificados à frente (cada um ~1,6 MB fora do heap). */
    private static final int AHEAD = 8;
    /** 60 quadros por segundo: no celular um JPEG leva ~15-25 ms, então três fios dão conta com folga. */
    private static final int THREADS = 3;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Os primeiros quadros prontos antes de começar (som e imagem saem juntos). */
    private static final int PRELOAD = 3;
    /** Desiste de esperar o começo (algo deu errado no pacote) depois disso. */
    private static final long PRELOAD_TIMEOUT_NS = 4_000_000_000L;

    private final ConcurrentSkipListMap<Integer, NativeImage> ready = new ConcurrentSkipListMap<>();
    private final AtomicInteger nextClaim = new AtomicInteger();
    /** Pro log do fim (o LM manda o latest.log se travar no celular): quanto custou cada quadro. */
    private final java.util.concurrent.atomic.AtomicLong decodeNs = new java.util.concurrent.atomic.AtomicLong();
    private final AtomicInteger decoded = new AtomicInteger();
    private int shown;
    private volatile boolean running;
    /** System.nanoTime() do quadro 0 (0 = ainda não começou). */
    private volatile long startNs;
    private long openedNs;
    private float fps;
    private int frames;
    /** O tamanho do vídeo (não confundir com width/height da tela, que são do Screen). */
    private int videoW;
    private int videoH;
    private double duration;
    @Nullable
    private ExecutorService decoders;
    @Nullable
    private DynamicTexture texture;
    @Nullable
    private SoundInstance sound;
    private boolean prepared;
    private boolean finished;
    private boolean shownAny;
    /** Não tem o vídeo no jar (ou está quebrado): não passa nada, só avisa o servidor. */
    private boolean missing;

    public CheatVideoScreen() {
        super(Component.empty());
    }

    public boolean finished() {
        return finished;
    }

    @Override
    protected void init() {
        if (prepared || finished) return; // init roda de novo quando a janela muda de tamanho
        prepared = true;
        openedNs = System.nanoTime();
        if (!readInfo()) {
            missing = true; // fecha no primeiro tick (fechar de dentro do init abre uma tela dentro da outra)
            return;
        }
        texture = new DynamicTexture(videoW, videoH, false);
        texture.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
        Minecraft.getInstance().getMusicManager().stopPlaying();
        Minecraft.getInstance().getSoundManager().stop();
        running = true;
        decoders = Executors.newFixedThreadPool(THREADS, r -> {
            Thread t = new Thread(r, "FroggyDude anti-trapaça");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        for (int i = 0; i < THREADS; i++) decoders.execute(this::decodeLoop);
    }

    private boolean readInfo() {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        Optional<Resource> res = rm.getResource(INFO);
        if (res.isEmpty()) return false;
        try (InputStreamReader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            fps = o.get("fps").getAsFloat();
            frames = o.get("frames").getAsInt();
            videoW = o.get("width").getAsInt();
            videoH = o.get("height").getAsInt();
            duration = o.has("duration") ? o.get("duration").getAsDouble() : frames / (double) fps;
            return fps > 0F && frames > 0 && videoW > 0 && videoH > 0 && rm.getResource(frame(0)).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private static ResourceLocation frame(int index) {
        return new ResourceLocation(ModEntityTypes.MOD_ID, String.format(Locale.ROOT, "anticheat/f%04d.jpg", index + 1));
    }

    /** O quadro que tem que estar na tela agora. */
    private int target() {
        long s = startNs;
        if (s == 0L) return 0;
        return (int) ((System.nanoTime() - s) * (double) fps / 1.0E9D);
    }

    /** Fio de fundo: pega o próximo quadro que falta (nunca um que já passou) e decodifica. */
    private void decodeLoop() {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        while (running) {
            int target = target();
            int idx = nextClaim.get();
            if (idx < target) {
                nextClaim.compareAndSet(idx, target); // ficou pra trás: pula
                continue;
            }
            if (idx >= frames) return;
            if (idx > target + AHEAD + (startNs == 0L ? PRELOAD : 0)) {
                try {
                    Thread.sleep(3L);
                } catch (InterruptedException e) {
                    return;
                }
                continue;
            }
            if (!nextClaim.compareAndSet(idx, idx + 1)) continue;
            long t0 = System.nanoTime();
            NativeImage img = decode(rm, idx);
            if (img == null) continue;
            decodeNs.addAndGet(System.nanoTime() - t0);
            decoded.incrementAndGet();
            if (!running) {
                img.close();
                return;
            }
            ready.put(idx, img);
            if (!running) {
                NativeImage left = ready.remove(idx);
                if (left != null) left.close();
                return;
            }
        }
    }

    @Nullable
    private NativeImage decode(ResourceManager rm, int idx) {
        Optional<Resource> res = rm.getResource(frame(idx));
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            NativeImage img = NativeImage.read(NativeImage.Format.RGBA, in);
            if (img.getWidth() != videoW || img.getHeight() != videoH) {
                img.close();
                return null;
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void tick() {
        if (finished || !prepared) return;
        if (missing) {
            finish();
            return;
        }
        long now = System.nanoTime();
        if (startNs == 0L) {
            // começa quando os primeiros quadros estão prontos (ou desiste se o pacote não responde)
            if (ready.size() >= Math.min(PRELOAD, frames) || ready.containsKey(0) && now - openedNs > 500_000_000L) {
                start();
            } else if (now - openedNs > PRELOAD_TIMEOUT_NS) {
                finish();
            }
            return;
        }
        // acabou: o tempo do vídeo passou (um respiro de 0,3 s pro fim do som)
        if ((now - startNs) / 1.0E9D > duration + 0.3D) finish();
    }

    private void start() {
        startNs = System.nanoTime();
        AntiCheatClient.videoStarted();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager().getSoundEvent(SOUND) != null) {
            sound = new SimpleSoundInstance(SOUND, SoundSource.MASTER, 1.0F, 1.0F, RandomSource.create(), false, 0,
                    SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
            mc.getSoundManager().play(sound);
        }
    }

    /** É o som do vídeo? (Os outros sons ficam mudos enquanto ele passa.) */
    boolean isOurSound(SoundInstance s) {
        return s == sound || SOUND.equals(s.getLocation());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF000000);
        if (texture == null || finished) return;
        int target = target();
        Map.Entry<Integer, NativeImage> e = startNs == 0L ? null : ready.floorEntry(target);
        if (e != null) {
            int key = e.getKey();
            NativeImage img = ready.remove(key);
            // os que passaram sem aparecer (celular lento) vão embora
            for (Integer k : ready.headMap(key).keySet()) {
                NativeImage old = ready.remove(k);
                if (old != null) old.close();
            }
            if (img != null) {
                texture.setPixels(img); // fecha o quadro de antes
                texture.upload();
                shownAny = true;
                shown++;
            }
        }
        if (!shownAny) return;
        // cabe inteiro na tela, sem esticar (faixas pretas onde sobra)
        float scale = Math.min(this.width / (float) videoW, this.height / (float) videoH);
        int w = Math.round(videoW * scale);
        int h = Math.round(videoH * scale);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        g.blit(TEXTURE, x, y, w, h, 0.0F, 0.0F, videoW, videoH, videoW, videoH);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Só fecha quando acaba. */
    @Override
    public void onClose() {
        if (finished) super.onClose();
    }

    /** Perdoado no meio do vídeo: para na hora, sem avisar "acabou". */
    void abort() {
        if (finished) return;
        finished = true;
        cleanup();
        if (Minecraft.getInstance().screen == this) Minecraft.getInstance().setScreen(null);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        int n = decoded.get();
        LOGGER.info("[FroggyDude] anti-trapaca: video {} ({} de {} quadros na tela, {} decodificados, {} ms por quadro)",
                missing ? "nao esta no jar" : "acabou", shown, frames, n,
                n == 0 ? 0 : String.format(Locale.ROOT, "%.1f", decodeNs.get() / 1.0E6D / n));
        cleanup();
        AntiCheatClient.videoFinished(this);
        Minecraft.getInstance().setScreen(null);
    }

    /** Saiu da tela sem acabar (caiu a conexão): limpa tudo; o servidor manda ver de novo na volta. */
    @Override
    public void removed() {
        cleanup();
        if (!finished) AntiCheatClient.videoInterrupted(this);
    }

    private void cleanup() {
        running = false;
        if (decoders != null) {
            decoders.shutdown(); // sem interromper: interromper uma leitura de dentro do jar fecha o jar pro jogo todo
            decoders = null;
        }
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            sound = null;
        }
        for (Integer k : ready.keySet()) {
            NativeImage img = ready.remove(k);
            if (img != null) img.close();
        }
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE); // fecha a textura e o último quadro
            texture = null;
        }
    }
}
