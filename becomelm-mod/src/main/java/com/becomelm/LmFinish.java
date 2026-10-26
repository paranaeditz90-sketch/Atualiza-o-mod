package com.becomelm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;

/**
 * O golpe de finalização (pedido do LM: "começo a arranhar a barriga, aparecem os órgãos e o
 * canibalismo"). Raríssimo, pode sair até com o bicho de vida cheia. A vítima cai de costas, ele
 * senta no quadril dela virado pra cabeça, cava a barriga com as patas, abre, come as tripas e
 * no fim encara a câmera com a cara suja; aí ela morre e o corpo quebra. A linha do tempo vale
 * pros dois lados (o servidor conta e mata no fim, quem vê anima e filma).
 */
public final class LmFinish {

    /** Os pedaços da cena (o tick em que cada um começa). */
    public enum Phase {
        /** Monta em cima (escorrega até o quadril). */
        MOUNT(0),
        /** Cava a barriga com as patas, alternando, feito cachorro. */
        DIG(8),
        /** Enfia as mãos e puxa pros lados: as costelas estalam, abre. */
        OPEN(68),
        /** Come: enfia a cara, puxa a tripa até a boca, sacode e mastiga (3 vezes). */
        FEED(86),
        /** Levanta a cara ensanguentada e encara a câmera, rosnando. */
        STARE(196);

        public final int from;

        Phase(int from) {
            this.from = from;
        }
    }

    /** A cena inteira (~11,5 s). No último tick a vítima morre. */
    public static final int TICKS = 230;
    /** Cada mergulho na barriga pra comer. */
    public static final int FEED_CYCLE = 36;

    private LmFinish() {
    }

    public static Phase phase(int age) {
        Phase[] all = Phase.values();
        for (int i = all.length - 1; i > 0; i--) {
            if (age >= all[i].from) return all[i];
        }
        return Phase.MOUNT;
    }

    /** Há quantos ticks está nesse pedaço. */
    public static int inPhase(int age) {
        return age - phase(age).from;
    }

    /**
     * Dá pra finalizar esse? Primeiro os que ficam em pé e sangram (gente, aldeão, zumbi...),
     * do tamanho de gente; filhote e chefão não. Bicho de quatro fica pra depois (a barriga fica
     * embaixo e precisa de outra pose).
     */
    public static boolean canFinish(LivingEntity t) {
        if (!LmKnock.canKnock(t) || !LmKnock.upright(t) || t.isBaby() || t instanceof Warden) return false;
        if (!LmBloodType.of(t).bleeds()) return false;
        float h = t.getBbHeight();
        return h >= 1.3F && h <= 2.2F;
    }

    /** O jogador vítima também não pode estar no criativo (o canTarget já tira, mas o comando não passa por lá). */
    public static boolean alive(LivingEntity t) {
        return t.isAlive() && !t.isRemoved() && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
    }
}
