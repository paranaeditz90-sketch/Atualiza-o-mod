package com.froggydude.entity.ai;

import com.froggydude.brain.Drives;
import com.froggydude.brain.Intent;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.world.Manhunt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * (v0.7.42) O decisor (IA dinâmica, etapa 1; só na IA Real). Antes, os comportamentos tinham prioridade fixa (comer >
 * fugir > fase 2 > tocaia > combate > faro) e quem estava na frente tomava o controle sem avisar: daí o "para do nada"
 * e o "não sabe o que fazer". Agora, a cada 0,5 s ele dá nota pra tudo que pode fazer, com as vontades (brain.Drives),
 * a vida dele e o que ele sabe da presa, e escolhe a melhor. Sempre tem uma próxima ação.
 * <p>
 * Os comportamentos continuam sendo o "como": cada um, quando vê a sua oportunidade (bicho pra comer, rastro, lugar
 * de tocaia, vila sozinha, perigo), avisa aqui ({@link #offer}) e só roda se for a escolha ({@link #allows}). Fora da
 * IA Real nada disso roda ({@link #active}) e tudo fica como era.
 */
public final class FroggyDecider {

    /** Pensa a cada 0,5 s. */
    private static final int THINK = 10;
    /** Uma oportunidade vale 2 s depois da última vez que o comportamento viu. */
    private static final int OFFER_TTL = 40;
    /** Inércia: pra trocar, a nova tem que ganhar por isso (senão fica trocando à toa). */
    private static final float STICK = 0.12F;
    /** Fica pelo menos 3 s na mesma (a não ser que a de agora suma ou seja emergência). */
    private static final int MIN_HOLD = 60;
    /** Na emergência, a nova só precisa ganhar por isso. */
    private static final float EMERGENCY_MARGIN = 0.03F;

    private final FroggydudeEntity froggy;
    private final int[] offeredAt = new int[Intent.values().length];
    private final float[] score = new float[Intent.values().length];
    private Intent intent = Intent.REST;
    private int since;
    private int nextThink;

    public FroggyDecider(FroggydudeEntity froggy) {
        this.froggy = froggy;
        java.util.Arrays.fill(offeredAt, -10000);
    }

    /** O decisor está mandando (IA Real)? */
    public static boolean active() {
        return FroggyConfig.ai().real;
    }

    /** O comportamento dessa intenção pode rodar agora? (Fora da IA Real, sempre.) */
    public boolean allows(Intent i) {
        return !active() || intent == i;
    }

    public Intent intent() {
        return intent;
    }

    /** O comportamento viu a oportunidade dele (vale {@link #OFFER_TTL} ticks). */
    public void offer(Intent i) {
        offeredAt[i.ordinal()] = froggy.tickCount;
    }

    private boolean offered(Intent i) {
        return froggy.tickCount - offeredAt[i.ordinal()] <= OFFER_TTL;
    }

    public float score(Intent i) {
        return score[i.ordinal()];
    }

    /** Pensa a nota da hora de cada opção e troca de intenção quando vale a pena. */
    public void tick() {
        if (!active() || !(froggy.level() instanceof ServerLevel level) || froggy.tickCount < nextThink) return;
        nextThink = froggy.tickCount + THINK;
        Drives d = froggy.drives();
        float hp = froggy.getHealth() / froggy.getMaxHealth();
        LivingEntity t = froggy.getTarget();
        boolean prey = t instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator();
        double dist = prey ? froggy.distanceTo(t) : Double.MAX_VALUE;
        boolean sees = prey && froggy.getSensing().hasLineOfSight(t);
        boolean manhunt = Manhunt.isActive(level.getServer());
        boolean hurtLow = froggy.wantsToHeal();

        java.util.Arrays.fill(score, -1F);
        // caçar: ela na mira ou o rastro dela no nariz. A raiva puxa; vendo e perto, quase sempre; ferido e calmo, menos
        if (prey || offered(Intent.HUNT)) {
            score[Intent.HUNT.ordinal()] = 0.45F + 0.35F * d.anger + 0.2F * d.hunger
                    + (sees ? 0.3F : 0F) + (dist < 12D ? 0.2F : 0F) + (manhunt ? 0.3F : 0F)
                    - (hurtLow ? 0.45F * (1F - d.anger) : 0F);
        }
        // comer o bicho/aldeão à vista: a fome manda; ferido também ajuda (comer cura); ela perto e à vista, não
        if (offered(Intent.FEED)) {
            score[Intent.FEED.ordinal()] = 0.1F + 0.75F * d.hunger + (hurtLow ? 0.35F : 0F)
                    - (sees && dist < 24D ? 0.35F : 0F);
        }
        // fugir pra se curar: abaixo da metade da vida e calmo, foge (como antes); quanto menos vida, mais. Com raiva
        // (acima de ~1/3) ele fica e briga mesmo ferido
        if (offered(Intent.HEAL)) {
            score[Intent.HEAL.ordinal()] = 0.75F + 1.2F * Math.max(0F, 0.5F - hp) - 0.55F * d.anger;
        }
        // tocaia: paciência (calmo), um pouco de tédio; a fome tira a paciência
        if (offered(Intent.STALK)) {
            score[Intent.STALK.ordinal()] = 0.35F + 0.25F * (1F - d.anger) + 0.15F * d.boredom - 0.2F * d.hunger;
        }
        // a vila dela: fome e raiva (comer o que ela tem de mais seu)
        if (offered(Intent.VILLAGE)) {
            score[Intent.VILLAGE.ordinal()] = 0.25F + 0.35F * d.hunger + 0.35F * d.anger;
        }
        // explorar: o tédio. À toa é o piso: explora ~1 min e descansa ~1 min (o tédio cai explorando e sobe parado)
        score[Intent.EXPLORE.ordinal()] = 0.6F * d.boredom - 0.05F;
        score[Intent.REST.ordinal()] = 0.15F;

        Intent best = Intent.REST;
        for (Intent i : Intent.values()) if (score[i.ordinal()] > score[best.ordinal()]) best = i;
        if (best == intent) return;
        float now = score[intent.ordinal()];
        boolean gone = now < 0F; // a de agora não existe mais (a oportunidade sumiu)
        // fugir ferido e ir pra cima dela colada não esperam a inércia
        boolean emergency = best == Intent.HEAL || best == Intent.HUNT && sees && dist < 12D;
        if (!gone) {
            // emergência troca sem esperar, mas com uma margem pequena (senão fugir/caçar empatados ficam indo e vindo)
            if (emergency ? score[best.ordinal()] < now + EMERGENCY_MARGIN
                    : score[best.ordinal()] < now + STICK || froggy.tickCount - since < MIN_HOLD) return;
        }
        Intent old = intent;
        intent = best;
        since = froggy.tickCount;
        froggy.brainLog(String.format(Locale.ROOT, "decidi %s (%.2f), antes %s | %s | %s", best.label,
                score[best.ordinal()], old.label, ranking(), d));
    }

    /** As 3 melhores, pro log ("caçar 0,82 > comer 0,40 > explorar 0,20"). */
    private String ranking() {
        Intent[] all = Intent.values().clone();
        java.util.Arrays.sort(all, (a, b) -> Float.compare(score[b.ordinal()], score[a.ordinal()]));
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Intent i : all) {
            if (score[i.ordinal()] < 0F || n == 3) break;
            if (n++ > 0) sb.append(" > ");
            sb.append(String.format(Locale.ROOT, "%s %.2f", i.label, score[i.ordinal()]));
        }
        return sb.toString();
    }

    /** Pro /froggydude vontades: a intenção, há quanto tempo, e as notas da última vez. */
    public String describe() {
        return String.format(Locale.ROOT, "%s há %ds | %s", intent.label, (froggy.tickCount - since) / 20, ranking());
    }
}
