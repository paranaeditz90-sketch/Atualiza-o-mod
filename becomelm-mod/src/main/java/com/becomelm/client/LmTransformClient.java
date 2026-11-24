package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmTransform;
import com.becomelm.network.LmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A transformação, do lado de quem vê: a animação certa de cada pedaço e os estalos dos
 * ossos nos tempos do original (cada um toca o seu, ninguém precisa mandar som pela rede).
 * Quem está se transformando fica parado; torto, andar ou pular faz arrancar.
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmTransformClient {

    /**
     * O som original (com o pacote de sons: só os 11 estalos, sem o gemido) entra no mesmo
     * tick que no Froggydude: a dobra rápida começa ali.
     */
    private static final int CLIP_AT = 8;
    /**
     * Sem o pacote: estalos de osso do jogo nos tempos dos estalos do original (0,72 1,03
     * 1,51 1,74 1,80 2,14 2,21 2,74 2,93 3,06 3,21 s depois do começo do som).
     */
    private static final int[] CRACK_AT = {22, 29, 38, 43, 44, 51, 52, 63, 67, 69, 72};

    private static final Map<UUID, LmTransform.State> STATES = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_CRACK = new HashMap<>();
    private static boolean sentGo;

    private LmTransformClient() {
    }

    /** Chegou do servidor (fase -1 = acabou). */
    public static void set(UUID player, int phase, int age) {
        LmTransform.Phase ph = LmTransform.Phase.byOrdinal(phase);
        if (ph == null) {
            STATES.remove(player);
            NEXT_CRACK.remove(player);
            return;
        }
        LmTransform.State old = STATES.get(player);
        if (old != null && old.phase == ph) {
            old.age = age;
        } else {
            // começou um pedaço novo: os sons desse pedaço contam do início
            STATES.put(player, new LmTransform.State(ph, age));
        }
        if (ph == LmTransform.Phase.HOLD) NEXT_CRACK.put(player, age + 6);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && player.equals(mc.player.getUUID()) && ph != LmTransform.Phase.HOLD) sentGo = false;
    }

    @Nullable
    static LmTransform.Phase phase(Player p) {
        LmTransform.State s = STATES.get(p.getUUID());
        return s == null ? null : s.phase;
    }

    static boolean busy(Player p) {
        return STATES.containsKey(p.getUUID());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            NEXT_CRACK.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (Map.Entry<UUID, LmTransform.State> en : STATES.entrySet()) {
            LmTransform.State s = en.getValue();
            Player p = mc.level.getPlayerByUUID(en.getKey());
            s.age++;
            if (p != null) sounds(p, s);
        }
        LocalPlayer me = mc.player;
        if (me != null && !sentGo && phase(me) == LmTransform.Phase.HOLD && wantsToGo(mc)) {
            sentGo = true;
            LmNetwork.CHANNEL.sendToServer(new LmNetwork.TransformGo());
        }
    }

    private static boolean wantsToGo(Minecraft mc) {
        var o = mc.options;
        return mc.screen == null && (o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown()
                || o.keyRight.isDown() || o.keyJump.isDown());
    }

    private static void sounds(Player p, LmTransform.State s) {
        switch (s.phase) {
            case CONTORT -> {
                if (s.age == CLIP_AT) play(p, BecomeLM.CONTORT.get(), 1.6F, 1.0F);
                for (int c : CRACK_AT) if (s.age == c) crack(p, BecomeLM.CRACK_SEQ.get());
            }
            case HOLD -> {
                // torto: os ossos ainda estalam de vez em quando
                Integer next = NEXT_CRACK.get(p.getUUID());
                if (next != null && s.age >= next) {
                    crack(p, BecomeLM.CRACK.get());
                    NEXT_CRACK.put(p.getUUID(), s.age + 8 + p.getRandom().nextInt(14));
                }
            }
            case ROAR -> {
                if (s.age == 2) crack(p, BecomeLM.CRACK.get());
            }
        }
    }

    private static void crack(Player p, SoundEvent sound) {
        play(p, sound, 1.2F, 0.85F + p.getRandom().nextFloat() * 0.3F);
    }

    /** Som vazio (o pacote de sons cala o que ele substitui) não toca: o jogo reclamaria no log. */
    private static void play(Player p, SoundEvent sound, float vol, float pitch) {
        WeighedSoundEvents ev = Minecraft.getInstance().getSoundManager().getSoundEvent(sound.getLocation());
        if (ev == null || ev.getWeight() <= 0) return;
        p.level().playLocalSound(p.getX(), p.getY() + 1.0D, p.getZ(), sound, SoundSource.PLAYERS, vol, pitch, false);
    }

    /** Se transformando: parado (o servidor também trava a velocidade). */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!busy(event.getEntity())) return;
        var in = event.getInput();
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && busy(p)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        NEXT_CRACK.clear();
        sentGo = false;
    }
}
