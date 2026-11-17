package com.froggydude.entity;

import com.froggydude.brain.ModWeapons;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.init.ModSounds;
import com.froggydude.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (v0.7.41) A cutscene JOGÁVEL da cabeça (LM 16/11: "Ele tentando arrancar a cabeça do jogador, vai ser uma cutscene
 * que é jogável, a cutscene vai pedir ao jogador para acertar ele para evitar com que ele arranque a cabeça... Se o
 * jogador tiver uma espada, a cutscene vai alertar em cima da hotbar (no meio entre corações e barra de fome),
 * piscando em vermelho com ícone de alerta para o jogador bater no froggydude (com ragdoll, voa bem menos. Precisa
 * acertar 2 vezes). Ou, se o jogador tiver arma, o jogo pede para ele atirar (isso tem ragdoll, voa longe) ou arco";
 * falhou: "Arranca a cabeça (morte)"). Medido nos vídeos 3 e 5 dele (o Grox) e refeito igual em 17/11 ("pula em cima do
 * player, derruba o jogador e olha levemente e morde a cabeça e começa a sacudir rapidamente com leve blur"): ele se joga
 * em cima de quem caiu de costas, olha um instante, crava a boca na cabeça e chacoalha dando trancos, a cabeça dela junto
 * (saindo do chão); ela saca a arma e atira na cara dele, e ele voa longe.
 * <p>
 * Começa no fim da raiva (entity.Rage): em vez de atacar normal, o bote. Deitado embaixo dele, a mira presa no corpo
 * dele (o mesmo "preso" do bote: PinTracker), a câmera em 3ª pessoa de lado (client.cutscene.GrabCamera), o rugido de
 * 4,2 s ({@link #WINDOW}). Acertou o que precisa (arma de fogo,
 * arco ou besta: 1 tiro; espada ou machado: 2 golpes; outra coisa: 3 socos) antes do rugido acabar: ele voa no ragdoll
 * (com tiro voa longe, com golpe voa pouco) e tu levanta. Não acertou: a cena da zebra, só a cabeça (Finisher.HEAD_ONLY).
 * O cliente mostra o alerta (client.HeadGrabHud) a partir dos números sincronizados aqui ({@link #state}).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.froggydude.init.ModEntityTypes.MOD_ID,
        bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class HeadGrab {

    /** Quem está agarrado agora (a vítima) -> o FroggyDude que agarrou: o tiro dela acha ele aqui. */
    private static final Map<UUID, FroggydudeEntity> GRABBED = new HashMap<>();

    /** O bote até montar em cima (no máximo). */
    public static final int LEAP = 14;
    /** O rugido (ROAR_RIP, 4,2 s): até aqui pra se soltar. */
    public static final int WINDOW = 84;
    /** A tela treme e o pescoço estala a cada isso (ticks), depois da mordida. */
    public static final int PULL = 9;
    /**
     * A linha do tempo da animação "head_grab" (tools/anim/build_anims.py: HG_GLANCE, HG_BITE, HG_SIDE), em ticks
     * desde que agarrou: olha pra cara dela até GLANCE, crava a boca na cabeça em BITE e daí chacoalha pra direita e
     * pra esquerda como cachorro rasgando pano (LM 17/11), um lado a cada SIDE ticks (0,09 s). A cabeça da vítima vai
     * junto ({@link #shake}: +1 = a boca pra direita dele, que é a esquerda dela).
     */
    public static final float GLANCE = 13F, BITE = 17F, SIDE = 1.8F;
    /**
     * (vídeo 3 do LM, 1,10-1,85 s) além da sacudida pros lados, a cabeça presa na boca sobe e desce: um ciclo a cada
     * LIFT ticks (0,5 s). A cabeça dele puxa pra cima (build_anims.py, hg_lift) e o corpo dela vai junto (VictimRender).
     */
    public static final float LIFT = 10F;
    /** De novo só depois disso (ticks): senão a raiva seguinte já vinha com outro bote na cabeça. */
    static final int COOLDOWN = 1200;
    /** Monta a essa distância da vítima (o mesmo do bote que prende). */
    static final double MOUNT = 1.5D;

    private HeadGrab() {
    }

    /**
     * Pra que lado a cabeça dele está na sacudida, de -1 a 1 (onda triangular: os picos são as chaves da animação, o
     * GeckoLib interpola reto entre elas); 0 antes da mordida.
     */
    public static float shake(float t) {
        if (t <= BITE) return 0F;
        float u = (t - BITE) / SIDE - 0.5F;
        if (u < 0F) return (t - BITE) / (SIDE * 0.5F);
        int k = (int) Math.floor(u);
        float sk = (k & 1) == 0 ? 1F : -1F;
        return sk * (1F - 2F * (u - k));
    }

    /** De 0 (no chão) a 1 (puxada pra cima), depois da mordida. */
    public static float lift(float t) {
        if (t <= BITE) return 0F;
        return 0.5F - 0.5F * (float) Math.cos(2D * Math.PI * (t - BITE) / LIFT);
    }

    /** O que só o servidor guarda. */
    static final class Run {
        final ServerPlayer victim;
        final int startTick;
        int grabTick = -1;
        /** A hora do mundo em que agarrou (o cliente conta o tempo do rugido a partir dela). */
        long grabTime;
        int need;
        int hits;
        int lastHit = -100;
        boolean shot;
        Vec3 from;

        Run(ServerPlayer victim, int startTick) {
            this.victim = victim;
            this.startTick = startTick;
        }
    }

    /** Quantos acertos o que está na mão pede (1 = atirar). */
    public static int need(ItemStack hand) {
        if (shoots(hand)) return 1;
        if (hand.getItem() instanceof SwordItem || hand.getItem() instanceof AxeItem
                || ModWeapons.isWeapon(hand.getItem())) {
            return 2;
        }
        return 3;
    }

    /** Arma de fogo de mod, arco ou besta: o alerta pede pra atirar. */
    public static boolean shoots(ItemStack hand) {
        return ModWeapons.isGun(hand) || hand.getItem() instanceof ProjectileWeaponItem;
    }

    /** O que o cliente lê: quem está embaixo, quando agarrou, quantos acertos faltam. Vazio = não está agarrando. */
    static CompoundTag state(Run run) {
        CompoundTag t = new CompoundTag();
        t.putInt("Victim", run.victim.getId());
        t.putLong("GrabTime", run.grabTime);
        t.putInt("Need", run.need);
        t.putInt("Hits", run.hits);
        return t;
    }

    // ---------------------------------------------------------------- começa

    /** No fim da raiva: dá pra pular na cabeça dessa pessoa? */
    static boolean can(FroggydudeEntity f, @Nullable Entity target) {
        return target instanceof ServerPlayer p && p.isAlive() && !p.isCreative() && !p.isSpectator()
                && f.tickCount >= f.headGrabCooldownUntil() && f.distanceToSqr(p) < 6D * 6D && f.hasLineOfSight(p)
                && !f.isFrenzyActive() && !f.isReviving() && !f.isFinishing();
    }

    static boolean begin(FroggydudeEntity f, Entity target) {
        if (!can(f, target)) return false;
        ServerPlayer p = (ServerPlayer) target;
        Run run = new Run(p, f.tickCount);
        run.from = f.position();
        f.beginHeadGrab(run);
        // o bote: baixo e rápido, direto nela (as mãos na frente)
        Vec3 d = p.position().subtract(f.position()).multiply(1, 0, 1);
        double dist = d.length();
        if (dist > 1.0E-3D) d = d.scale(1D / dist);
        double speed = Math.min(1.1D, 0.25D + dist * 0.16D);
        f.setDeltaMovement(d.x * speed, 0.38D, d.z * speed);
        f.setOnGround(false);
        f.hurtMarked = true;
        f.faceTarget(p);
        f.setNoFallTicks(40);
        f.playSound(ModSounds.LEAP.get(), 2.0F, 0.9F);
        f.openMouth(LEAP + WINDOW);
        f.brainLog("cabeça: pulei em " + p.getScoreboardName());
        return true;
    }

    /** O comando de teste (/froggydude cabeca): na pessoa mais perto, sem esperar a raiva. */
    public static boolean debugBegin(FroggydudeEntity f, @Nullable Entity target) {
        if (!(target instanceof ServerPlayer p) || p.isCreative() || p.isSpectator() || f.isReviving()) return false;
        f.clearHeadGrabCooldown();
        f.forceIdleForHeadGrab();
        return begin(f, p);
    }

    // ---------------------------------------------------------------- cada tick (servidor)

    static void tick(FroggydudeEntity f, Run run) {
        ServerPlayer v = run.victim;
        if (!v.isAlive() || v.hasDisconnected() || v.level() != f.level() || v.isSpectator()) {
            f.endHeadGrab();
            return;
        }
        f.getNavigation().stop();
        int age = f.tickCount - run.startTick;
        if (run.grabTick < 0) {
            // o bote: monta quando chega perto (ou no fim do pulo, se ela ainda está ali)
            double d = f.distanceToSqr(v);
            if ((age >= 3 && d < 2.2D * 2.2D) || (age >= LEAP && d < 4.5D * 4.5D)) {
                grab(f, run);
            } else if (age >= LEAP + 10) {
                f.brainLog("cabeça: errei o bote");
                f.endHeadGrab();
            }
            return;
        }
        int t = f.tickCount - run.grabTick;
        hold(f, run);
        // cravou os dentes na cabeça
        if (t == (int) BITE) {
            f.playSound(ModSounds.BITE.get(), 1.3F, 0.8F);
            ModNetwork.sendEffects(v, 0.8F, 6, WINDOW - t + 30, f.getId());
        }
        // chacoalhando: a câmera dela treme, o pescoço estala
        if (t > BITE && t % PULL == 0) {
            float k = Math.min(1F, t / (float) WINDOW);
            ModNetwork.sendEffects(v, 0.3F + 0.4F * k, 7, WINDOW - t + 30, f.getId());
            if (t % (PULL * 2) == 0) f.playSound(ModSounds.CRACK.get(), 0.5F + 0.5F * k, 1.2F - 0.3F * k);
        }
        if (run.hits >= run.need) {
            escape(f, run);
            return;
        }
        if (t >= WINDOW) {
            // não deu: a cena da zebra, só a cabeça (o golpe final tira ela daqui)
            f.brainLog("cabeça: " + v.getScoreboardName() + " não se soltou - arrancando");
            f.endHeadGrab();
            f.requestFinisher(v, true);
        }
    }

    /** Montou: ela deitada embaixo, a câmera presa na cara dele, o rugido. */
    private static void grab(FroggydudeEntity f, Run run) {
        ServerPlayer v = run.victim;
        run.grabTick = f.tickCount;
        run.grabTime = f.level().getGameTime();
        run.need = need(v.getMainHandItem());
        v.setSprinting(false);
        v.stopUsingItem();
        v.setDeltaMovement(0D, Math.min(v.getDeltaMovement().y, 0D), 0D);
        v.hurtMarked = true;
        PinTracker.pin(v, f, WINDOW + 30);
        ModNetwork.sendEffects(v, 1.4F, 14, WINDOW + 30, f.getId());
        f.setPinnedVictim(v);
        hold(f, run);
        f.speak(VoiceSituation.ROAR, true);
        f.openMouth(WINDOW + 10);
        f.playSound(ModSounds.PIN.get(), 1.4F, 0.85F);
        f.setHeadGrabState(state(run));
        GRABBED.put(v.getUUID(), f);
        f.brainLog("cabeça: agarrei " + v.getScoreboardName() + " (precisa de " + run.need + (run.need == 1 ? " tiro)" : " golpes)"));
    }

    /** Em cima dela, virado pra ela, parado. */
    private static void hold(FroggydudeEntity f, Run run) {
        ServerPlayer v = run.victim;
        Vec3 away = run.from.subtract(v.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4D) away = f.position().subtract(v.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : away.normalize();
        f.setPos(v.getX() + away.x * MOUNT, v.getY(), v.getZ() + away.z * MOUNT);
        f.setDeltaMovement(Vec3.ZERO);
        f.faceTarget(v);
    }

    // ---------------------------------------------------------------- apanhando

    /** Acabou o agarrão (ele chama ao sair do estado). */
    static void release(FroggydudeEntity f, @Nullable Run run) {
        if (run != null) GRABBED.remove(run.victim.getUUID(), f);
    }

    /**
     * Quem está embaixo disparou (bala de mod de arma, flecha, virote): conta como o tiro. A câmera presa nele mira o
     * corpo dele, mas a bala do TaCZ disparada deitada não registra o acerto (testado: dispara, gasta a munição e o dano
     * nunca chega); o que o alerta pede é atirar.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || GRABBED.isEmpty()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj)) return;
        if (!(proj.getOwner() instanceof ServerPlayer shooter)) return;
        FroggydudeEntity f = GRABBED.get(shooter.getUUID());
        if (f == null || f.isRemoved()) return;
        Run run = f.headGrabRun();
        if (run == null || run.victim != shooter || run.grabTick < 0) return;
        run.hits = run.need;
        run.shot = true;
        f.setHeadGrabState(state(run));
    }

    /** Depois do super.hurt (machucou): conta só o que veio de quem está embaixo. */
    static void onHurt(FroggydudeEntity f, Run run, DamageSource source) {
        if (run.grabTick < 0 || source.getEntity() != run.victim) return;
        Entity direct = source.getDirectEntity();
        boolean shot = direct != null && direct != run.victim;
        if (shot) {
            run.hits = run.need;
            run.shot = true;
        } else if (f.tickCount - run.lastHit >= 4) {
            run.hits++;
            run.lastHit = f.tickCount;
            f.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.8F);
        }
        f.setHeadGrabState(state(run));
    }

    /** Se soltou: ele voa (no tiro, longe; no golpe, pouco) e ela levanta. */
    private static void escape(FroggydudeEntity f, Run run) {
        ServerPlayer v = run.victim;
        PinTracker.release(v);
        f.brainLog("cabeça: " + v.getScoreboardName() + " se soltou" + (run.shot ? " com um tiro" : " na porrada"));
        f.endHeadGrab();
        Ragdoll.knockOff(f, v, run.shot);
    }
}
