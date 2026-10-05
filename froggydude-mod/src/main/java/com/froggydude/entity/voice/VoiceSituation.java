package com.froggydude.entity.voice;

import static com.froggydude.entity.voice.VoiceLine.*;

/**
 * Em que situação ele fala e EM QUE ORDEM, tirado das legendas dos vídeos do
 * arco da raiva (scratchpad/falas: "Minecraft Manhunt but I have RABIES",
 * "WHAT HAPPENED TO ME???", "FroggyDude VS AJTHEBOLD", "Finally, a worthy
 * opponent", "My Next Opponent Is... Me"):
 *
 * - avista: "I'm hungry" e "come here, I want to eat you" (AJ 0:00, Grox 0:11,
 *   worthy 0:49-1:01, FroggyDoom 0:48 "I want to digest you");
 * - persegue: "get back here" (Grox 0:00, AJ 4:51), "you can run but you can't
 *   hide" (origem 1:09), "you will become my dinner" (worthy 2:53);
 * - subiu/se trancou e ele chegou: "you thought you could escape me?" (Grox 3:24);
 *   achou escondido: risadinha (origem 4:21);
 * - língua: "I like the way you taste" (AJ 0:28); fugindo da língua: "you're
 *   scared of my tongue, aren't you?" (worthy 5:44);
 * - apanhando: "I love pain" (AJ 0:10, worthy 2:39, FroggyDoom 6:43);
 * - derrubou: "give me your body" (Grox 0:38, worthy 1:33, FroggyDoom 1:27);
 *   montado: "stay on the ground" (worthy 3:08), "you are mine" (FroggyDoom
 *   4:01), "I'm going to bite your balls" (FroggyDoom 3:22);
 * - comeu: "that was so delicious" (Grox 1:37, AJ 2:31), "very delicious"
 *   (worthy 1:12, AJ 7:12), "I want more food" (origem 0:19), "who's next?".
 *
 * A fila de cada situação anda uma fala por vez e fica salva no mundo, então
 * a sequência continua de onde parou. {@code loopFrom}: de onde recomeça depois
 * da última fala (as de abertura, tipo "hey humans", só saem uma vez).
 */
public enum VoiceSituation {
    SPOT(1, HEY_HUMANS, IM_HUNGRY, WANNA_EAT_YOU, COME_HERE_EAT_DIGEST, VERY_HUNGRY, WANT_YOUR_FLESH),
    CHASE(0, GET_BACK_HERE, CANT_RUN_CANT_HIDE, BECOME_FOOD, NEVER_LOSE_PREY),
    CAUGHT_UP(0, YOU_THINK_ESCAPE),
    FOUND(0, LAUGH),
    /** O rastro acabou e ele varre em volta: sabe que tu está ali perto. */
    SEARCH(0, CANT_RUN_CANT_HIDE, YOU_THINK_ESCAPE, NEVER_LOSE_PREY),
    TONGUE_HIT(0, LIKE_THE_WAY_YOU_TASTE),
    TONGUE_DODGED(0, SCARED_OF_MY_TONGUE),
    PAIN(0, LOVE_PAIN_1, LOVE_PAIN_2, LOVE_PAIN_3, LOVE_PAIN_4),
    PIN(0, GIVE_ME_YOUR_BODY_1, GIVE_ME_YOUR_BODY_2),
    ON_TOP(0, STAY_ON_GROUND, YOU_ARE_MINE, BITE_YOUR_BALLS),
    LAST_BITE(0, LAST_THING_YOU_SEE),
    CHEWING(0, MMMM),
    ATE(0, THAT_WAS_DELICIOUS, VERY_DELICIOUS, SO_FREAKING_DELICIOUS, TASTE_SO_GOOD, REALLY_GOOD),
    WANTS_MORE(0, WANT_MORE, WHOS_NEXT),
    ATE_ARM(0, LOVE_TASTE_HUMANS),
    ATE_ODD(0, EXOTIC_MEAT),
    ATE_PANDA(0, PANDA_DELICIOUS),
    SPICY(0, FREAKING_SPICY),
    /** Pegou trapaça: "What?! This is cheating! You'll pay for it!" (anticheat/Invasion). */
    CHEATING(0, CHEAT_REACTION),
    DEATH(0, NO_NO_NOOO, NOOOOO);

    final VoiceLine[] lines;
    final int loopFrom;

    VoiceSituation(int loopFrom, VoiceLine... lines) {
        this.lines = lines;
        this.loopFrom = loopFrom;
    }

    /** Caçando ele berra de longe (ouve-se a ~32 blocos); em cima da vítima fala de perto. */
    public float volume() {
        return switch (this) {
            case SPOT, CHASE, CAUGHT_UP, FOUND, SEARCH -> 2.0F;
            case DEATH -> 1.6F;
            case CHEATING -> 1.0F;
            default -> 1.4F;
        };
    }
}
