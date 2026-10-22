package com.froggydude.config;

/**
 * O quanto ele pensa (config "ia", Mods > Froggydude > Configurar, ou
 * /froggydude ia &lt;nível&gt;). ALTO é o FroggyDude de sempre (até a v0.4.2);
 * pra baixo ele fica mais lento de cabeça e esquece táticas, pra cima ele
 * enxerga mais longe, reage mais rápido, prevê melhor e ganha truques novos.
 *
 * <ul>
 * <li>MIN: vê de perto, demora pra reagir, corre pras costas da vítima (não
 *     prevê nada), não cerca, não põe bloco, não arromba, não sabota e escolhe
 *     estratégia e ataque no chute (o cérebro não manda).</li>
 * <li>MÉDIO: prevê a metade, arromba e põe bloco, mas não cerca nem sabota; o
 *     cérebro testa muito e aprende devagar.</li>
 * <li>ALTO: tudo como era.</li>
 * <li>EXTRA: + intercepta quem pula correndo (antes só quem estava no chão) e
 *     de mais perto; ziguezague quando miram arco/besta/tridente nele; contorna
 *     o escudo; o cérebro arrisca menos.</li>
 * <li>MÁX: tudo do Extra, mais longe e mais rápido; quase não arrisca o que
 *     não deu certo e não perde a pista.</li>
 * <li>REAL: a esperteza do Máx com o jeito do FroggyDude dos vídeos (VS Every
 *     Opponent, 10v1 Manhunt, How many days, Manhunt but I have RABIES): sabe o
 *     que está na tua tela, corre atrás de quem foge em vez de pular de longe,
 *     "I see you" quando chega sem ser visto, espera no escuro, cerca quem se
 *     trancou, foge pra se curar e volta com um plano, entra na lava e dá o
 *     pulo altíssimo só quando não tem outro jeito (ver entity/ai/RealTactics).</li>
 * </ul>
 */
public enum AiLevel {
    //          alcance reação previsão  cerca  inter. bloco arromba sabota cérebro curios. truques veloc. dano   real
    MIN("min",    0.55D, 1.70F, 0.00D, false, false, false, false, false, false, 2.00F, false, 0.85D, 0.75F, false),
    MEDIO("medio", 0.80D, 1.30F, 0.50D, false, true,  true,  true,  false, true,  1.50F, false, 0.93D, 0.88F, false),
    ALTO("alto",  1.00D, 1.00F, 1.00D, true,  true,  true,  true,  true,  true,  1.00F, false, 1.00D, 1.00F, false),
    EXTRA("extra", 1.25D, 0.80F, 1.15D, true,  true,  true,  true,  true,  true,  0.70F, true,  1.05D, 1.10F, false),
    MAX("max",    1.50D, 0.62F, 1.30D, true,  true,  true,  true,  true,  true,  0.45F, true,  1.10D, 1.20F, false),
    REAL("real",  1.50D, 0.62F, 1.30D, true,  true,  true,  true,  true,  true,  0.45F, true,  1.10D, 1.20F, true);

    public final String id;
    /** Alcance de visão, faro e rastro (x). */
    public final double sense;
    /** Tempo entre um ataque e outro, descanso da língua, refazer o caminho (x; menor = mais rápido). */
    public final float react;
    /** Quanto ele mira onde a vítima VAI estar (0 = corre pras costas dela). */
    public final double lead;
    /** Estratégia "cercar por trás". */
    public final boolean flank;
    /** Cortar caminho de quem foge em linha reta. */
    public final boolean intercept;
    /** Pôr bloco (torre e ponte) como jogador. */
    public final boolean build;
    /** Comer parede/telhado e arrombar porta de quem se trancou. */
    public final boolean breakIn;
    /** Speedrun: comer o portal do Nether. */
    public final boolean sabotage;
    /** Usa o que aprendeu (estratégia e ataque por jogador); sem isso, chuta. */
    public final boolean brain;
    /** Curiosidade do cérebro (x o bônus de testar o que ainda não testou). */
    public final float explore;
    /** Truques do Extra/Máx: interceptar no ar, ziguezague contra arco, contornar escudo. */
    public final boolean tricks;
    /** Velocidade da caçada (x). */
    public final double speed;
    /** Dano dos ataques (x). */
    public final float damage;
    /** O jeito do FroggyDude dos vídeos por cima da esperteza (ver entity/ai/RealTactics). */
    public final boolean real;

    AiLevel(String id, double sense, float react, double lead, boolean flank, boolean intercept, boolean build,
            boolean breakIn, boolean sabotage, boolean brain, float explore, boolean tricks, double speed,
            float damage, boolean real) {
        this.id = id;
        this.sense = sense;
        this.react = react;
        this.lead = lead;
        this.flank = flank;
        this.intercept = intercept;
        this.build = build;
        this.breakIn = breakIn;
        this.sabotage = sabotage;
        this.brain = brain;
        this.explore = explore;
        this.tricks = tricks;
        this.speed = speed;
        this.damage = damage;
        this.real = real;
    }

    /** Chave de tradução do nome ("froggydude.ai.alto"); a descrição é a mesma + ".desc". */
    public String key() {
        return "froggydude.ai." + id;
    }

    /** Ticks escalados pela reação (nunca menos de 1). */
    public int ticks(int base) {
        return Math.max(1, Math.round(base * react));
    }

    public static AiLevel byId(String id) {
        for (AiLevel l : values()) {
            if (l.id.equalsIgnoreCase(id) || l.name().equalsIgnoreCase(id)) return l;
        }
        return null;
    }
}
