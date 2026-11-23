package com.froggydude.entity;

/**
 * Os estados possíveis do Froggy. Cada um vira uma animação do lado do
 * renderer. "IDLE" e "CHASE" não têm uma duração fixa; os outros terminam
 * sozinhos depois de durationTicks.
 */
public enum FroggyState {
    IDLE(0),
    CHASE(0),
    TONGUE_WHIP(14),
    TONGUE_GRAB(16),
    /**
     * (v0.7.33) A captura (réplica v8 aprovada pelo LM, 09/11): a língua pega, ERGUE a vítima 4 blocos em 0,7 s,
     * segura 2 s lá em cima e puxa pra boca (depois morde, arranca o braço ou derruba, como antes). A vítima fica
     * presa num gancho invisível (TongueHold) que o servidor move.
     */
    TONGUE_CAPTURE(70),
    JUMP_PIN(20),
    HIGH_JUMP(32),
    BITE(12),
    FEEDING(120),
    /**
     * Fase 2, etapa 1 (o vídeo do LM em 60 fps, 89,28-91,75 s): os ossos estalam,
     * ele dobra até quase deitar, levanta, a cabeça afunda nos ombros e tomba, o
     * ombro direito sobe. Os 59 ticks são a animação "contort" (2,95 s, rotoscopia
     * do vídeo); o tempo torto depois disso quem decide é a FroggyFrenzyGoal.
     */
    CONTORTING(59),
    /** Montado em cima da vítima derrubada, mordendo e rasgando ("devorar"). */
    PIN_HOLD(100),
    /** Esmagamento: em pé por cima da vítima derrubada, desce os dois punhos juntos (vs AJ, 0:57). */
    SMASH(132),
    /** Pulo altíssimo: some no céu e cai em cima da vítima (vs AJ, 0:56). Dura até pousar. */
    SKY_DROP(80),
    /** Agarra a vítima presa e arranca o braço esquerdo (vs AJ, 2:22). */
    ARM_RIP(40),
    /** Come o braço arrancado: mastiga, engole e digere. Distraído e vulnerável. */
    ARM_EAT(90),
    /** Fase 2, etapa 2: a cabeça joga pra trás num estalo, grita e já dispara correndo. */
    ROAR(10),
    /**
     * O golpe final (cutscene): a vítima deitada, ele arranca o braço esquerdo,
     * o direito e a cabeça (entity.Finisher tem a tabela; quem cuida é a
     * FroggyFinisherGoal).
     */
    FINISHER(Finisher.END),
    /**
     * Atravessando água de vitória-régia em vitória-régia (fim do vídeo do LM,
     * 95,6-98,9 s): um salto a cada 6 ticks (FroggyPadHopGoal), enquanto tiver folha.
     */
    PAD_HOP(6),
    /** Pondo bloco como jogador pra chegar na vítima (ai.PlayerMoves: torre e ponte). */
    BUILD(0),
    /** A "dancinha" de jogador: agacha e levanta, pula, soca o ar (ai.PlayerMoves). */
    TAUNT(0),
    /**
     * IA Real (v0.6): grudado no canto do teto esperando a presa voltar pro baú, pra
     * cama (FroggyStakeoutGoal; Grox, RABIES 0:58-1:23). Sem gravidade enquanto dura.
     */
    CEILING_HANG(0),
    /**
     * IA Real (v0.6): do canto do teto ele se solta e se joga em cima dela (o pedido do
     * LM: "pulando contra o jogador, empurrando ou derrubando"). Termina ao pousar.
     */
    CEILING_DIVE(24),
    /**
     * (v0.6.2) O boom do warden jogou ele longe: voa dando cambalhota pra trás, cai de
     * costas e levanta (Grox, cidade antiga). Depois corre o dobro e come o warden.
     */
    FLUNG(34),
    /**
     * (v0.7) O banquete: matou, o corpo se desmontou em pedaços (client.pieces) e ele
     * deita por cima em "flexão", "comendo" sem engolir nada, com o nhum nhum NHOM
     * (vídeo "WHAT HAPPENED TO ME???" 2:50; Grox, RABIES 0:57). 3 a 7 s, no ritmo da
     * raiva e da fome (FroggyFeastGoal).
     */
    FEAST(0),
    /**
     * (v0.7.30) Comendo a arma (ou o item do Avaritia) que a língua roubou (FroggyDude VS Grox MANHUNT 1:24,0-1:25,1;
     * LM: "ele sacode a cabeça rapidamente e vai engolindo a arma"): atravessada na boca, ele sacode a cabeça rápido
     * e ela vai entrando aos poucos; engole olhando pra cima (FroggyStealGoal, animação "weapon_eat"), no tempo do
     * vídeo (~1 s até o "glup"). Apanhou no meio: cospe. Fica no fim da lista: o número do estado vai pela rede.
     */
    WEAPON_EAT(32),
    /**
     * (v0.7.33) O arremesso (réplica v3 aprovada pelo LM, 09/11; bem raro): pega com a língua, derruba e arrasta,
     * ergue, segura, vira o tronco com a língua mole e solta: a vítima voa uns 40 blocos pro lado livre.
     */
    TONGUE_THROW(108),
    /**
     * (v0.7.35) O renascer com a língua (entity.Revive; réplica v3 aprovada pelo LM em 16/11): despedaçado, os pedaços
     * no chão, a cabeça puxa um por um com a língua, flexão e mortal (ou sacode a cabeça de raiva), vira, estala o
     * pescoço. Sem IA e invulnerável; quem acaba é o Revive (linha do tempo fixa). No fim da lista: o número vai pela rede.
     */
    REVIVE(0),
    /**
     * (v0.7.40) O ragdoll do tiro (entity.Ragdoll; LM 16/11, vídeos v1/v2): bala de arma de mod com ele parado no chão
     * joga ele longe, mole de verdade (o corpo é física no cliente: client.pieces.RagdollScene); cai, fica deitado
     * (conversa com quem chega perto armado, leva mais tiro) e levanta com raiva. Sem IA enquanto dura. No fim da
     * lista: o número vai pela rede.
     */
    RAGDOLL(0),
    /**
     * (v0.7.40) A raiva depois das balas (entity.Rage; vídeo 4 do LM): estala o pescoço, avança correndo agachado e
     * para colado em "()" com a boca aberta, só pra pessoa recuar; repete e depois ataca. Sem as Goals enquanto dura.
     */
    RAGE(0),
    /**
     * (v0.7.41) A cutscene jogável da cabeça (entity.HeadGrab): o bote, em cima de quem está caído, olha, morde a cabeça
     * e chacoalha dando trancos durante o rugido; tiro/golpes o bastante = ele voa; não = arranca a cabeça.
     */
    HEAD_GRAB(0),
    /**
     * (v0.7.44) A entrada (vídeo do LM 23/11, 1 vez por mundo): ele surge parado na pose de pulo olhando pra quem joga
     * ("FroggyDudeMC entrou no jogo"), corta pra câmera baixa atrás dele e ele uiva pro céu. Sem IA e sem dano.
     */
    INTRO(0),
    /**
     * (v0.7.47/48) A raiva 2 (vídeos do LM 23/11 14:33): extremamente louco, troca de pose a cada tick (1,7 s) e trava na pose
     * da raiva, parado 25 s sem tomar dano, enquanto quem ele caça vê "FUJA! AGORA!". Sem IA.
     */
    RAGE2(0),
    /**
     * (v0.7.48) O bote com ragdoll (vídeo do LM 23/11, o do macaco): o bote baixo pega e arremessa quem caça, que voa
     * girando mole (~1,7 s, câmera fixa no chão vendo de fora), e corta pra marreta.
     */
    TACKLE(34);

    public final int durationTicks;

    FroggyState(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public boolean isAttack() {
        return this == TONGUE_WHIP || this == TONGUE_GRAB || this == TONGUE_CAPTURE || this == TONGUE_THROW
                || this == JUMP_PIN || this == HIGH_JUMP || this == BITE || this == SKY_DROP
                || this == CEILING_DIVE;
    }

    public boolean isTongue() {
        return this == TONGUE_WHIP || this == TONGUE_GRAB || this == TONGUE_CAPTURE || this == TONGUE_THROW;
    }

    /** (v0.7.33) A língua segurando alguém no ar (captura ou arremesso). */
    public boolean isCapture() {
        return this == TONGUE_CAPTURE || this == TONGUE_THROW;
    }

    /** Em cima da vítima derrubada (ela fica presa no chão). */
    public boolean isOnVictim() {
        return this == PIN_HOLD || this == SMASH || this == ARM_RIP || this == FINISHER;
    }

    /** Qualquer ação conduzida pela Goal de combate (ataques, montado, braço). */
    public boolean isCombatAction() {
        return isAttack() || isOnVictim() || this == ARM_EAT || this == FLUNG || this == TACKLE;
    }
}
