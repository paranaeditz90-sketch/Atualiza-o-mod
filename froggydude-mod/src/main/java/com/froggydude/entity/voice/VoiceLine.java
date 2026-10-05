package com.froggydude.entity.voice;

/**
 * Cada fala dele é um som próprio ({@code froggydude.voice.<clipe>}), pra o
 * mod escolher QUAL fala vai tocar (a ordem dos vídeos, ver {@link VoiceSituation}).
 * Sem o pacote de voz, cada uma cai no som de reserva da categoria
 * (sounds.json); com o pacote "FroggyDude-Voz", toca o clipe original.
 *
 * A duração (em ticks) é a do clipe original: serve pra ele não começar uma
 * fala por cima da outra.
 */
public enum VoiceLine {
    // avistou a presa
    HEY_HUMANS("hunt_hey_humans", 30),
    IM_HUNGRY("hunt_im_hungry", 29),
    WANNA_EAT_YOU("hunt_wanna_eat_you", 23),
    COME_HERE_EAT_DIGEST("hunt_come_here_eat_digest", 79),
    VERY_HUNGRY("hunt_very_hungry", 39),
    WANT_YOUR_FLESH("hunt_want_your_flesh", 25),
    // a presa fugindo
    GET_BACK_HERE("hunt_get_back_here", 33),
    CANT_RUN_CANT_HIDE("hunt_cant_run_cant_hide", 46),
    BECOME_FOOD("hunt_become_food", 35),
    NEVER_LOSE_PREY("hunt_never_lose_prey", 43),
    // subiu/escondeu e ele chegou
    YOU_THINK_ESCAPE("hunt_you_think_escape", 42),
    LAUGH("ambient_laugh", 52),
    // língua
    LIKE_THE_WAY_YOU_TASTE("taste_like_the_way_you_taste", 31),
    SCARED_OF_MY_TONGUE("taste_scared_of_my_tongue", 58),
    // apanhando
    LOVE_PAIN_1("hurt_love_pain_1", 47),
    LOVE_PAIN_2("hurt_love_pain_2", 53),
    LOVE_PAIN_3("hurt_love_pain_3", 31),
    LOVE_PAIN_4("hurt_love_pain_4", 103),
    // derrubou / montado
    GIVE_ME_YOUR_BODY_1("pin_give_me_your_body_1", 40),
    GIVE_ME_YOUR_BODY_2("pin_give_me_your_body_2", 35),
    STAY_ON_GROUND("pin_stay_on_ground", 92),
    YOU_ARE_MINE("pin_you_are_mine", 36),
    BITE_YOUR_BALLS("pin_bite_your_balls", 30),
    LAST_THING_YOU_SEE("pin_last_thing_you_see_1", 104),
    // comendo
    MMMM("eat_mmmm", 19),
    THAT_WAS_DELICIOUS("eat_that_was_delicious", 26),
    VERY_DELICIOUS("eat_very_delicious", 70),
    SO_FREAKING_DELICIOUS("eat_so_freaking_delicious", 54),
    TASTE_SO_GOOD("eat_taste_so_good", 59),
    REALLY_GOOD("eat_really_good", 34),
    WANT_MORE("eat_want_more", 33),
    WHOS_NEXT("hunt_whos_next", 20),
    LOVE_TASTE_HUMANS("eat_love_taste_humans", 52),
    EXOTIC_MEAT("eat_exotic_meat", 62),
    PANDA_DELICIOUS("eat_panda_delicious", 58),
    FREAKING_SPICY("pain_freaking_spicy", 25),
    // pegou alguém trapaceando (vira pra pessoa e fala, sem texto)
    CHEAT_REACTION("cheat_reaction", 70),
    // morrendo
    NO_NO_NOOO("death_no_no_nooo", 50),
    NOOOOO("death_nooooo", 33);

    /** Nome do clipe (o som é froggydude.voice.&lt;clip&gt;). */
    public final String clip;
    /** Duração do clipe original, em ticks. */
    public final int ticks;

    VoiceLine(String clip, int ticks) {
        this.clip = clip;
        this.ticks = ticks;
    }

    public String soundName() {
        return "froggydude.voice." + clip;
    }
}
