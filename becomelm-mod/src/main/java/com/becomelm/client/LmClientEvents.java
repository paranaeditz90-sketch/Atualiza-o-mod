package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmMode;
import com.becomelm.LmModes;
import com.becomelm.LmMove;
import com.becomelm.network.LmNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** O dia a dia do cliente: abrir a roda, os pulos, o corpo do FroggyDude e as patadas do galope. */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmClientEvents {

    /** Dois toques no agachar com até isso de intervalo abrem a roda. */
    private static final int DOUBLE_TAP = 8;
    /** Segurando o agachar esse tempo (no chão), o próximo pulo é o super alto. */
    private static final int CHARGE = 14;
    /** Soltou o agachar: o pulo carregado ainda vale por esse tempo. */
    private static final int CHARGE_KEEP = 10;
    /** Pulo super alto (~11 blocos, como o do Froggydude caindo em cima). */
    private static final double SKY_VY = 1.5D;
    private static final double SKY_PUSH = 0.15D;
    /** Bote galopando: empurrão pra frente e um tiquinho mais alto. */
    private static final double LEAP_PUSH = 0.3D;
    private static final double LEAP_UP = 1.08D;

    private static int ticks;
    private static int lastShiftTap = -100;
    private static int charge;
    private static int chargeReleasedAt = -100;
    private static boolean charged;

    /** Patadas do galope de cada jogador: relógio da passada e qual pata. */
    private static final Map<Integer, double[]> STRIDES = new HashMap<>();

    private LmClientEvents() {
    }

    static int ticks() {
        return ticks;
    }

    static boolean serverHasMod() {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        return conn != null && LmNetwork.CHANNEL.isRemotePresent(conn.getConnection());
    }

    static void chooseMode(LmMode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // aplica na hora (o servidor confirma); servidor sem o mod: só eu vejo
        LmModes.setClient(mc.player.getUUID(), mode);
        if (serverHasMod()) LmNetwork.CHANNEL.sendToServer(new LmNetwork.SetMode(mode));
        LmHud.toast(Component.translatable("becomelm.mode.toast." + mode.id));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            STRIDES.clear();
            return;
        }
        input(mc, p);
        chargeTick(p);
        LmMoves.tick();
        strides(mc);
    }

    private static void input(Minecraft mc, LocalPlayer p) {
        while (LmKeys.WHEEL.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new LmWheelScreen(LmKeys.WHEEL.getKey().getValue()));
        }
        while (LmKeys.CAMERA.consumeClick()) {
            LmCamera.next();
        }
        // dois toques no agachar (funciona com qualquer botão do Zalith, até o fixo)
        while (mc.options.keyShift.consumeClick()) {
            if (ticks - lastShiftTap <= DOUBLE_TAP && mc.screen == null) {
                lastShiftTap = -100;
                mc.setScreen(new LmWheelScreen(InputConstants.UNKNOWN.getValue()));
            } else {
                lastShiftTap = ticks;
            }
        }
    }

    /** Segurar o agachar no chão carrega o pulo super alto (tremidinha + som quando fica pronto). */
    private static void chargeTick(LocalPlayer p) {
        boolean froggy = LmModes.get(p).froggy();
        if (froggy && p.isShiftKeyDown() && p.onGround()) {
            charge++;
            if (charge == CHARGE) {
                charged = true;
                p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), BecomeLM.CHARGED.get(), SoundSource.PLAYERS,
                        0.8F, 1.0F, false);
                for (int i = 0; i < 8; i++) {
                    double a = i / 8D * Math.PI * 2D;
                    p.level().addParticle(BecomeLM.DUST_PUFF.get(), p.getX() + Math.cos(a) * 0.4D, p.getY() + 0.05D,
                            p.getZ() + Math.sin(a) * 0.4D, Math.cos(a) * 0.05D, 0.01D, Math.sin(a) * 0.05D);
                }
            }
        } else {
            if (charge >= CHARGE) chargeReleasedAt = ticks;
            charge = 0;
            if (!froggy || ticks - chargeReleasedAt > CHARGE_KEEP) charged = false;
        }
    }

    /** Os pulos do FroggyDude (só o próprio jogador: o movimento dele é do cliente). */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer p) || p != Minecraft.getInstance().player) return;
        LmMode mode = LmModes.get(p);
        if (!mode.froggy()) return;
        Vec3 v = p.getDeltaMovement();
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        if (charged) {
            charged = false;
            charge = 0;
            p.setDeltaMovement(v.x + fx * SKY_PUSH, SKY_VY, v.z + fz * SKY_PUSH);
            LmMoves.local(p, LmMove.SKY);
        } else if (mode == LmMode.GALLOP && p.isSprinting()) {
            p.setDeltaMovement(v.x + fx * LEAP_PUSH, v.y * LEAP_UP, v.z + fz * LEAP_PUSH);
            LmMoves.local(p, LmMove.LEAP);
        } else if (mode == LmMode.GALLOP) {
            LmMoves.local(p, LmMove.HOP);
        }
    }

    /**
     * O jogo abre o campo de visão conforme a velocidade: com o galope (2,5x) a tela
     * ficava estourada de tão aberta. Conta só a corrida normal.
     */
    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Player p = event.getPlayer();
        if (LmModes.get(p) != LmMode.GALLOP || p.isUsingItem()) return;
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        float walk = p.getAbilities().getWalkingSpeed();
        if (speed == null || walk <= 0F) return;
        double base = speed.getBaseValue() * (p.isSprinting() ? 1.3D : 1D);
        float f = p.getAbilities().flying ? 1.1F : 1F;
        f *= ((float) base / walk + 1F) / 2F;
        // um pouquinho mais aberto que a corrida comum: dá a sensação de velocidade
        if (p.isSprinting()) f *= 1.05F;
        event.setNewFovModifier(Mth.lerp(Minecraft.getInstance().options.fovEffectScale().get().floatValue(), 1F, f));
    }

    // ---------------------------------------------------------------------- corpo

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (LmCamera.hides(player)) {
            event.setCanceled(true);
            return;
        }
        if (!(player instanceof AbstractClientPlayer p) || !LmModes.get(p).froggy() || !replaceable(p)) return;
        event.setCanceled(true);
        LmRenderer.get().render(p, p.getYRot(), event.getPartialTick(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight());
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (LmCamera.hides(event.getEntity())) event.setCanceled(true);
    }

    /** Dormindo, de élitro, nadando, montado ou espectador: o corpo de jogador normal. */
    private static boolean replaceable(AbstractClientPlayer p) {
        return !p.isSpectator() && !p.isSleeping() && !p.isFallFlying() && !p.isAutoSpinAttack()
                && !p.isVisuallySwimming() && !p.isPassenger();
    }

    // ------------------------------------------------------------- galope: patadas

    /**
     * Cada patada levanta poeira e terra; as de trás fazem o barulho do galope (igual ao
     * Froggydude: duas patadas por passada, ~5 ticks por passada na velocidade 1).
     */
    private static void strides(Minecraft mc) {
        for (AbstractClientPlayer p : mc.level.players()) {
            if (LmModes.get(p) != LmMode.GALLOP || p.isSpectator() || p.isInvisible() || !replaceable(p)) {
                STRIDES.remove(p.getId());
                continue;
            }
            double v = LmAnims.groundSpeed(p);
            if (!p.onGround() || v < 0.05D || p.isShiftKeyDown()) continue;
            double[] s = STRIDES.computeIfAbsent(p.getId(), k -> new double[2]);
            s[0] += LmAnims.runSpeed(v);
            if (s[0] >= 2.5D) {
                s[0] -= 2.5D;
                s[1] = s[1] > 0D ? 0D : 1D;
                strideEffects(p, s[1] > 0D, p.isSprinting());
            }
        }
    }

    private static void strideEffects(AbstractClientPlayer p, boolean hind, boolean fast) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double off = hind ? -0.45D : 0.55D;
        double px = p.getX() + fx * off, pz = p.getZ() + fz * off;
        var rnd = p.getRandom();
        int puffs = fast ? 6 : 3;
        for (int i = 0; i < puffs; i++) {
            double side = (rnd.nextDouble() - 0.5D) * 0.12D;
            p.level().addParticle(BecomeLM.DUST_PUFF.get(),
                    px + (rnd.nextDouble() - 0.5D) * 0.7D, p.getY() + 0.08D, pz + (rnd.nextDouble() - 0.5D) * 0.7D,
                    -fx * (0.02D + rnd.nextDouble() * 0.05D) + fz * side, 0.004D + rnd.nextDouble() * 0.012D,
                    -fz * (0.02D + rnd.nextDouble() * 0.05D) - fx * side);
        }
        if (hind) {
            float pitch = 0.9F + rnd.nextFloat() * 0.2F;
            p.level().playLocalSound(px, p.getY(), pz, BecomeLM.GALLOP.get(), SoundSource.PLAYERS,
                    fast ? 0.7F : 0.45F, pitch, false);
            BlockState under = p.getBlockStateOn();
            SoundType type = under.getSoundType();
            p.level().playLocalSound(px, p.getY(), pz, type.getStepSound(), SoundSource.PLAYERS,
                    type.getVolume() * 0.35F, type.getPitch() * 0.75F, false);
        }
        BlockState below = p.getBlockStateOn();
        if (below.isAir()) return;
        BlockParticleOption crumbs = new BlockParticleOption(ParticleTypes.BLOCK, below);
        // câmera de cinema rente ao chão: a terra voando bem na lente vira um quadrado marrom na tela
        if (p == Minecraft.getInstance().player && LmCamera.active()) return;
        for (int i = 0; i < 3; i++) {
            p.level().addParticle(crumbs, px + (rnd.nextDouble() - 0.5D) * 0.5D, p.getY() + 0.1D,
                    pz + (rnd.nextDouble() - 0.5D) * 0.5D,
                    -fx * 0.15D + (rnd.nextDouble() - 0.5D) * 0.15D, 0.18D, -fz * 0.15D + (rnd.nextDouble() - 0.5D) * 0.15D);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LmModes.clearClient();
        LmMoves.clear();
        LmCamera.reset();
        STRIDES.clear();
        charged = false;
        charge = 0;
    }
}
