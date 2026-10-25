# Plano do mod Froggydude

Objetivo: **pavor psicológico.** O FroggyDude pensa, observa, aprende e caça.
Ele quer duas coisas: **comer** e **meter medo**. Gore existe, porque está na lore.

Este plano existe pra uma coisa: cada parte nova encaixar na anterior sem
quebrar animação, skin ou IA no meio do caminho. Tudo depende de um
**contrato único** (seção 1). Se algo não está no contrato, não entra.

Legenda: ✅ feito · 🔜 próxima · ⏳ depois · ❓ depende de resposta tua (seção 9)

---

## 0. Onde estamos

**Parte 1 ✅ (combate básico):** compila no GitHub e foi testada no jogo.

**Parte 2 ✅ (animações e sons, a prioridade que tu pediu):**
- **Corrida igual à dele.** A "corrida de quatro" dele nos vídeos é uma sequência de **saltos de sapo**: agacha encolhido, impulso em diagonal, plana deitado (pernas esticadas pra trás, braços pra frente), cai nas mãos, junta as pernas e salta de novo. De longe ele persegue assim; de perto, galopa de quatro. A troca entre andar e correr não pisca mais.
- **Bote e salto alto refeitos** pela referência. Quando acerta, **ele te derruba**: tu fica deitado no chão, sem andar, com a câmera **presa na cara dele**, que fica por cima de ti mordendo e rasgando. Se tu acertar uma pancada forte nele, ele é jogado pra trás e te solta.
- **A cabeça dele te encara** (antes ela nunca virava pro alvo).
- **A língua mira no peito** de quem ele ataca e tem o comprimento exato da distância. Antes ela saía reta e passava por cima da cabeça.
- **A câmera treme de verdade.** Nada de inclinar a tela pro lado e voltar.
- **Sangue em respingos vermelhos** no ponto da mordida, no lugar dos coraçõezinhos.
- **Sons:** 12 eventos (ribbit, caça, dor, "I love pain", morte, mordida, comendo, estalo da língua, provar, salto, montado, grito).
  - O mod vem com sons de reserva do Minecraft (sapo e ravager com outro tom).
  - A **voz original dele** vem num **pacote de recursos separado**, que não vai pro GitHub porque o repositório é público.

Tudo testado aqui num cliente de verdade, gravando a tela, inclusive do ponto de vista de quem é atacado.

**Parte 3 ✅ (o resto do combate dos vídeos):**
- **Tamanho de jogador** (1,05x), hitbox junto.
- **Corrida de perto = pulinhos de sapo** (vs AJ, 0:32), e a velocidade da animação acompanha a velocidade real: **caçando pra matar** ele galopa rápido (~5,4 m/s); **apavorando** ele vem devagar, para a uns 12 blocos e fica encarando; na **fase 2** dispara (~8 m/s).
- **Esmagamento** (vs AJ, 0:57): derruba, fica em pé por cima e desce os dois punhos juntos, 4 socos.
- **Pulo do céu** (vs AJ, 0:56): sobe ~12 blocos, some, cai em cima e emenda o esmagamento.
- **Devorar** dura 5 s (era 2,5 s), uma mordida por segundo. Comer mob também ficou mais longo (6 s).
- **Língua** fina em pirâmide (grossa na boca, ponta fina), como no vídeo, e bem mais rara (descanso de 8 a 13 s entre usos).
- **Braço arrancado** (vs AJ, 2:18-2:23): a língua puxa de volta, ele monta, arranca o braço esquerdo, levanta com ele na boca, mastiga, engole e esfrega a barriga. Tu é solto na hora do arranco e pode fugir enquanto ele come.
- **Sem braço:** braço some do modelo (e a manga da armadura), toco com osso no ombro, sem mão secundária (nem tecla F), 3 corações a menos, sangramento sem parar e sem estancar (tira vida devagar, até 3 corações), e o faro dele te acha a 64 blocos. O braço volta quando tu morre.
- **Fase 2** (vídeo "I'm the horror mod" + fim do manhunt contra o Grox, 5:10-5:30): contorção com a cabeça girando e os ossos estalando, grito, 30 s mais rápido que um jogador, gemendo de dor e à prova de bala/flecha. Liga quando ele come blaze ou cai abaixo de 55% de vida.

**Parte 3, ajustes (v0.3.2) ✅:**
- **Galope refeito e consertado:** agora é galope de cachorro, alto, costas retas na altura da cintura, braços e pernas esticados até o chão (vs AJ, 0:34) - antes era baixo, de joelho dobrado, parecendo engatinhar. Em pé ou de quatro é pela intenção (com vítima = de quatro), não pela velocidade, então não fica mais trocando de pose. Parou um instante caçando, continua de 4.
- **Bug da cabeça ("tiques") consertado:** o olhar só é somado na cabeça quando ele está em pé, com limite; saiu o "olhar aleatório pros lados".
- **Fase 2 em duas etapas:** (1) estala, dobra o tronco e fica curvado de 2 a 4 s com a cabeça girando no pescoço; (2) levanta num estalo gritando e dispara correndo EM PÉ, desesperado, como no vídeo "Eu sou o mod de terror" (4,1 s): boca aberta, mais rápido que um jogador, todo ataque com 1,6x de dano.
- **Boca escancarada** (skin de grito do NameMC) na língua, no grito, mordendo e mastigando; volta ao normal logo depois.
- **Língua:** ergue os dois braços, inclina o tronco pra frente e cospe a língua.
- **Braço mais canibal:** crava os dentes no ombro e sacode como cachorro, arranca num tranco, sai sacudindo o braço na boca; come rasgando com trancos da cabeça.
- **Gore:** partícula de sangue própria (pedaços que voam e caem), poças que se espalham no chão, rastro de quem perdeu o braço, sangue acumulando na skin (ref. "Eating a Zebra").
- **Esmagamento 2x mais rápido** (8 socos, mesmo dano total).
- **Bote de frente consertado:** se tu corre na direção dele, ele te agarra no ar (ou te derruba na hora, se já estiver colado).
- **Escala torres de quatro**, igual aranha, qualquer altura (vs Grox, 1:10).

**Parte 3, ajustes (v0.3.3) ✅** - comparado lado a lado com o vs AJTHEBOLD (7:26 no YouTube):
- **Contorção igual à do AJ:** o tronco chicoteia (dobra de lado, a cabeça vai de ponta-cabeça, dobra até o chão e volta) e ele fica encurvado com a cabeça tombada, balançando e dando trancos, de 2 a 4 s; aí grita.
- **Ataque feroz de 4:** depois do grito ele cai de quatro e vem no galope, mais esticado e muito mais rápido que o normal, igual no vídeo (a corrida em pé saiu).
- **Poeira e barulho no galope:** cada patada levanta uma nuvem de poeira com a cor do chão e faz barulho de galope.

**Parte 3, ajustes (v0.3.4) ✅** - investigar, achar a causa, reproduzir no jogo e só então mexer:
- **Cabeça virando pro nada e atacando pro lado:** a navegação guardava o alvo velho da escalada e ele ficava rodeando a vítima; o corpo agora segue exatamente pra onde o servidor vira ele (na hora, sincronizado) e o pescoço não torce mais que ~25°.
- **Não foge mais:** quando tu avança pra cima dele, ele avança junto e te derruba (bote/trombada). Antes, no modo "só olhando", ele recuava e voltava, e a língua saía pro lado errado na tela (mesmo acertando).
- **Escalada:** com a vítima lá em cima, ele sobe sempre (não precisa mais ficar no meio da torre).
- **Galope de 4 da "RACING A CHEETAH":** 0,25 s por passada, empina-estica-pousa-chuta-reúne, ~10 blocos/s caçando e ~15 na fase 2.
- **Fase 2 refeita quadro a quadro** (short "Throwing hands with AJTHEBOLD" + "I'm the horror mod"): dobra num estalo com o topo da cabeça pra frente, balança, sobe rápido, fica torto com o ombro deslocado pra cima, o tronco pra trás, uma perna na frente e a cabeça quebrada pra trás olhando pra cima de lado; depois grita e corre EM PÉ, desesperado, todo torto, com o braço se debatendo.
- **Braço arrancado igual à zebra:** sacode o braço na boca rápido (2,5x por segundo) depois de arrancar e a cada mordida, com jorro de cubinhos vermelhos (saiu a névoa/pó vermelho).
- **Sangue não tampa mais a tela** de quem está preso embaixo dele.

**Parte 4 ✅ (v0.3.5) - fundação + manhunt, e o começo das Partes 8 e 9:**
- **Corrida na velocidade do original:** galope a ~15 blocos/s (RACING A CHEETAH), fase 2 a ~19. Saiu o salto de sapo da perseguição (ele cobria 7,5 blocos em 1,2 s, mais devagar que o galope: era o "sapo pulando, bem lento").
- **Sangue** cai mais pesado (gravidade 1,5) e sai 1,6x mais.
- **Falas na ordem dos vídeos:** 38 falas, cada uma com seu som; cada situação (avistou, fugindo, língua, derrubou, montado, comeu, morrendo...) tem a sequência tirada das legendas dos vídeos, e ela continua de onde parou (salva no mundo).
- **Config** (`config/froggydude-common.toml`), **mundo `-dev`**, **comando `/froggydude`** (invocar, info, manhunt, fase2, cerebro, esquecer, falas, golpefinal) e **um FroggyDude por mundo**.
- **Manhunt, os dois modos** (decisão do LM): speedrun (matar o dragão antes dele te pegar) e sobrevivência (X dias). Vantagem com contagem, ele te segue pro Nether/End, volta depois de morrer, some quando vence.
- **Cérebro que aprende** (escolha do LM): por jogador, salvo no mundo; aprende teu estilo, qual estratégia de caçada funciona contra ele (apavorar / direto / cercar por trás) e quais ataques te acertam.
- ~~Anti-trapaça + invasão do 911~~ — **removido na v0.4.0** (LM: "percebi vários bugs relacionados a ela, pode remover sem dó").

**Parte 4, ajustes (v0.3.6) ✅** - em cima do teste do LM:
- **Cabeça girando do nada (bug crítico):** a marca de "osso mexido" do GeckoLib sobrevivia de um quadro pro outro e, na animação de andar (que não mexe na cabeça), o olhar era somado de novo a cada quadro. Agora a marca é limpa depois de cada ajuste e a animação de andar trava a cabeça na pose.
- **Língua:** meio coração por chicotada/puxão.
- **Esmagamento:** 24 socos (um a cada 0,25 s, 6,6 s ao todo), dano bem menor por soco, e o "tai!" do original (vs AJTHEBOLD) em cada um.
- **Fase 2:** o som da contorção é o do short original (ossos estalando + gemido torto) e o grito de raiva fica pro levantar. Contorcendo e gritando ele não é mais empurrado pelos golpes nem gira atrás de quem bate (no teste ele "patinava" com a pose congelada).
- **Inteligência:** caminho até 80 blocos e 4x mais rotas testadas; abre porta; lembra onde se machucou (lava, fogo, explosão, cacto, neve fofa, flecha de dispensador) e desvia por ~1 hora; corta caminho pra onde tu vai estar (intercepta) e, se a estratégia é cercar, vem por trás. Trancou-se em casa: derruba a parede/porta/janela a tiros de língua ("How strong is my tongue?": vidro e terra num tiro, tábua em dois) e come o telhado se tu estiver embaixo. No speedrun: come o portal do Nether quando tu está longe e come os aldeões perto de ti (sem troca).

**Faro e varredura (v0.3.7) ✅** - pedido do LM depois da v0.3.6:
- **Rastro de cheiro:** cada jogador deixa marcas por onde passa (dura 1 min; agachado ou na chuva, metade; dentro d'água não fica nada). Parado, a pessoa continua soltando cheiro.
- **Faro:** sem ver ninguém, ele sente o rastro a até 24 blocos (40 se for a presa que acabou de sumir) e vai atrás, de quatro, farejando. De longe o cheiro só dá a direção; perto, segue o rastro exato.
- **Perdeu de vista** (dobrou a esquina, entrou em casa): em 8 s larga o alvo e passa pro faro; quando acha de novo, ri ("achei").
- **Varredura:** onde o rastro termina (água, pérola, casa fechada) ele para, fareja, olha em volta e berra "you can run, but you can't hide"; a até 14 blocos sente de onde vem o cheiro (erro diminui com a distância); a 5 blocos sabe exatamente, mesmo atrás da parede.
- **Arrombar:** derruba parede/telhado a tiros de língua; porta, alçapão e portão até de ferro (5 tiros); sem parede que quebre, vai até a porta e arromba. Nada fica como item (a porta não volta pro jogador).
- **Barriga cheia:** depois de matar alguém, 45 s sem caçar essa pessoa (antes ele farejava quem renascia e matava de novo).

## 1. A arquitetura (o contrato)

```
        PERCEPÇÃO                 CÉREBRO                    CORPO
  visão / audição / faro   →   memória + decisão    →   estado (FroggyState)
  inventário visto              + diretor de tensão          │
                                                              ├─→ animação (GeckoLib)
                                                              ├─→ som
                                                              ├─→ expressão da skin
                                                              └─→ efeito no jogador
```

**Regra de ouro:** a IA nunca toca em animação, som ou textura diretamente.
Ela só escolhe um **estado**. Cada estado tem uma linha numa tabela única
(`FroggyState`) com:

| Campo | Exemplo (BITE) |
|---|---|
| animação | `bite` |
| duração | 0,6 s |
| ticks de impacto | 0,3 s |
| som | `froggydude:bite` |
| expressão | `GRITO` (boca aberta) |
| marca na skin | `SANGUE_BOCA +1` |

Mudou a duração? Muda num lugar só, e a animação, o golpe e o som continuam
sincronizados. Hoje isso já existe pela metade (estado + duração). A Parte 2
completa a tabela.

---

## 2. Skins por evento (Parte 5)

Hoje a skin é uma imagem inteira trocada por vida (16 PNGs). Não escala:
"queimado **e** comeu **e** gritando" viraria dezenas de arquivos.

**Proposta: skin em camadas**, todas recortadas das skins reais dele:

| Camada | Valores | De onde vem |
|---|---|---|
| Base | normal · em chamas · carbonizado | oficial · `corpo-em-chamas` · `carbonizado-brasa` |
| Sangue por zona | boca · cara · peito/braços · coroa · costas (0 a 3 cada) | diferença entre as skins de raiva e a oficial |
| Mancha de comida | verde · turquesa · roxo · vermelho | `respingo-verde`, `respingo-turquesa`, `hematomas-roxos` |
| Expressão | normal · grito limpo · grito com sangue | `grito-limpo`, `grito-boca-sangue` |

### Regras evento → skin

| Evento | O que muda | Some como |
|---|---|---|
| Língua toca/prova o jogador | sangue na **boca** +1 | água, ou com o tempo |
| Mordida | boca e **cara** +1 | água, ou com o tempo |
| **Come jogador/mob** | **cara, peito, braços e coroa** +1 por vítima. Mancha da cor do bicho (creeper verde, warden turquesa, enderman roxo) | só lavando na água |
| Cai na lava | **cara em chamas → corpo em chamas → carbonizado em brasa** (conforme o tempo na lava) | regenera aos poucos: carbonizado → normal em ~5 min |
| Vida baixa | sangue **dele** nas costas + hematomas roxos | volta com a vida |
| Ataque, grito, investida | expressão **grito** enquanto dura | fim do estado |
| Transformação (fase 2) | banho de sangue total | fim da fase |

**Detalhe de terror que sai daqui:** sapo gosta de água. Ele **vai lavar o
sangue no rio**. Tu pode encontrar ele agachado na água, limpando a cara,
de costas pra ti. Se tu chegar perto, ele vira a cabeça 180°.

---

## 3. Animações (corrida e bote na Parte 2 ✅, o resto na Parte 6)

Todas no Blockbench (formato GeckoLib), com os nomes de osso que já existem.
Agora eu consigo **abrir o jogo aqui e fotografar cada pose** antes de te
mandar, então não vai mais animação às cegas.

| Grupo | Animações |
|---|---|
| Movimento | `idle`, `walk`, `run_quad` (de quatro), `wall_climb`, `swim`, `crawl` (debaixo de cama/teto baixo) |
| Presença | `stalk_idle` (parado olhando, cabeça inclinada), `head_twist_360`, `peek` (espiar janela e porta), `knock` (bater na porta), `laugh` ("hehehe"), `wash` (lavando o sangue) |
| Língua | `tongue_whip`, `tongue_grab`, `tongue_capture`, `tongue_taste` (lambida), `tongue_yank` (arranca item da mão) |
| Ataque | `bite`, `jump_pin`, `high_jump`, `arm_rip` (arrancar o braço), `drag` (arrastar a vítima) |
| Comer | `feed` (loop), `feed_end` (lamber os dedos) |
| Corpo | `back_break` (quebrar as costas, o estalo do macaco), `contort`, `scream`, `lava_bath` (curtindo a lava), `death_fake` → `revive` ("I ALWAYS COME BACK"), `tired` |

---

## 4. Habilidades (Parte 7)

- **Movimento:** corrida de quatro, subir parede (igual aranha), nadar rápido, quebrar vidro, abrir porta de madeira, pular cerca.
- **Língua:** chicote, agarrar, capturar e **provar o gosto** (dá a marca de faro, seção 5.3). Também **arranca o item da mão**: escudo, arco, totem da mão secundária e, **principalmente, arma de mod de armas** (regra tua). Em vez de banir quem usa mod de armas, ele rouba a arma com a língua.
- **Comer (regra tua):** **é o único jeito de ele recuperar vida.** Não tem regeneração natural. Comendo, ele cura e as feridas dele na skin vão sumindo junto com a vida que volta. O sangue de quem ele comeu continua no terno até ele lavar na água. Também ganha buff conforme o que comeu (blaze = resistência ao fogo e força, carne = vida, aldeão = velocidade).
- **Imortal-ish:** a lava cura ele em vez de machucar. Ao "morrer" pela primeira vez, finge de morto e **volta**. A morte de verdade só vem na segunda vez, ou pelo void (é assim que o Parallax perdeu).
- **Arrancar o braço do jogador:** feito na Parte 3 (seção 6).

---

## 5. Inteligência (Parte 8)

Não é "IA que aprende" no sentido de rede neural. Isso seria pior e mais
lento. É **memória + estatística + regras**, que é o que dá a sensação de
"ele sabe o que eu faço".

### 5.1 Percepção
- **Visão:** cone de visão com linha de visada; à noite enxerga mais longe que tu.
- **Audição:** usa o sistema de vibrações do Minecraft (o mesmo do Warden), que capta passos, blocos quebrando, portas, comida e baú.
- **Faro:** seção 5.3.
- **Inventário (limitado):** só sabe o que **viu** (mão principal, mão secundária, armadura), o que **cheirou** (carne crua, comida, sangue) e o que **tu já usou contra ele** (arco, escudo, poção, totem). Nunca lê o baú nem o inventário inteiro.

### 5.2 Memória por jogador (salva no mundo)
- **Base:** onde tu dorme, onde passa mais tempo, por onde entra e sai.
- **Rotina:** em que hora sai e quando volta pra casa, se minera de noite.
- **Estilo:** luta ou foge? Usa torre de blocos, água, pérola, barco?
- **Fraquezas:** a vida média com que tu anda, se esquece de comer, se dorme sem fechar a porta.
- **As estatísticas do jogo**, que o Minecraft já guarda: blocos minerados, distância a pé/nadando/voando, mortes. É assim que ele sabe "o que tu faz longe dele".

### 5.3 Faro
- Tu deixa um rastro de cheiro (pontos com intensidade que somem com o tempo). A chuva apaga, e a água corta o rastro.
- Normal: ele sente o rastro a ~32 blocos.
- **Marcado:** se ele já te mordeu ou provou com a língua, sente a ~96 blocos.
- **Sangrando / sem braço:** sente a ~160 blocos e segue direto, mesmo sem te ver.
- Carregar carne crua aumenta o cheiro.

### 5.4 Decisão
A cada poucos segundos ele dá nota pra cada intenção e escolhe a melhor:

| Intenção | Quando sobe a nota |
|---|---|
| **Observar** | tu tá forte, é dia, ele tá ferido, a tensão ainda tá baixa |
| **Emboscar** | ele conhece tua rotina: espera na porta da base de noite, debaixo d'água, no túnel da mina |
| **Caçar** | tu tá fraco, com fome, sangrando, sozinho e longe de casa |
| **Aterrorizar** | é hora de subir a tensão (seção 5.5) |
| **Recuar e voltar** | tu tá com armadura de diamante e escudo. Ele some e volta quando tu relaxar (dormindo, AFK, minerando) |
| **Comer** | tem presa fácil ou ele tá ferido |
| **Lavar** | tá muito sujo e tem água perto |

**Estratégias pelo inventário que ele viu:**
- **Arco:** chega em zigue-zague, usando árvore e parede de cobertura.
- **Escudo:** arranca o escudo com a língua ou vai pelo lado.
- **Totem:** o alvo vira o **braço do totem**.
- **Comida/carne:** fareja de longe.
- **Pérola:** não persegue em linha reta, corta o caminho pela base.

### 5.5 Diretor de tensão
O que separa terror de comédia é o **ritmo**. Se ele aparecer a cada 30
segundos, vira piada na terceira vez. O diretor controla um "medidor de
tensão" e decide **quando** cada coisa pode acontecer:

1. **Calmaria:** nada acontece. Tu relaxa. Isso é obrigatório.
2. **Sinais:**
   - animal meio comido;
   - pegada de sangue;
   - ribbit distante;
   - uma tocha apagada;
   - a porta que tu fechou está aberta;
   - placa escrita "I SEE YOU".
3. **Vislumbre:** parado longe no escuro. Some quando tu olha, ou chega mais perto quando tu vira as costas.
4. **Perseguição:** risada, corrida de quatro, ele sobe pela parede.
5. **Ataque:** bote, língua, mordida, braço.
6. **Falso alívio:** ele recua e tu acha que acabou. Ele volta quando tu dormir.

Ele **nunca repete** o mesmo susto em sequência, e guarda o que já te assustou.

---

## 6. O braço arrancado (Parte 3 ✅)

- **Quando:** a língua de captura te puxa de volta (metade das vezes ele arranca em vez de morder), ou no meio do "devorar" (1 em 3). Só jogador, nunca no criativo.
- **Qual:** o esquerdo.
- **Efeitos:**
  - sem mão secundária: o que estiver ali vai pro inventário (ou cai) e a tecla F não funciona;
  - **sangramento contínuo, impossível de estancar:** esguicho no ritmo do coração, gotas caindo, e 1 de vida a cada 8 s até sobrar 3 corações;
  - **vida máxima −3 corações**;
  - **faro:** ele te acha a 64 blocos, sem precisar te ver;
  - **visual:** o braço some do teu personagem (1ª e 3ª pessoa, inclusive a manga da armadura) e fica o toco com o osso.
- **Ele come o braço:** com a tua skin, atravessado na boca, encurtando a cada mordida. Cura 15% e suja a boca dele.
- **Volta?** Só quando tu morre.

---

## 7. Anti-trapaça e a invasão — REMOVIDO (v0.4.0)

O LM pediu pra tirar tudo ("percebi vários bugs relacionados a ela, então não
compensa deixar ela. Pode remover sem dó."): detecção, reação, invasão do 911,
tela do boletim, banimento, `/froggydude trapaca` e `perdoar`, e as opções do
config. No lugar, a v0.4.0 trouxe o gore em 5 níveis e o golpe final (a zebra):
ver NOTES.md.

## 8. Ordem das partes

Tu pediu animação e som primeiro, e fez sentido: era o que mais destoava dos vídeos. Depois veio o resto do combate dos vídeos (esmagamento, pulo do céu, braço, fase 2) antes da fundação.

| Parte | O quê | Situação |
|---|---|---|
| 1 | Combate básico | ✅ |
| 2 | **Animações de corrida/bote + sons + câmera + língua** | ✅ |
| 3 | **Combate completo:** tamanho de jogador, pulinhos com velocidade variável, esmagamento, pulo do céu, devorar mais longo, língua em pirâmide e mais rara, braço arrancado e comido, sangramento, fase 2 | ✅ esta entrega |
| 4 | **Fundação:** config, mundo `-dev`, comando `/froggydude`, um por mundo, manhunt (2 modos), falas na ordem | ✅ v0.3.5 (+ cérebro adiantado) |
| 5 | **Skins em camadas** + regras evento → skin (comer suja a frente, lava carboniza, água lava) | |
| 6 | **Resto das animações** (parede, nadar, espiar janela, bater na porta, giro de cabeça, estalo de costas, lavar sangue) | |
| 7 | **Habilidades:** subir parede, nadar e arrastar pra água, comer pra curar, ressuscitar, roubar item com a língua | |
| 8 | **Inteligência:** percepção, faro, memória, decisão, diretor de tensão | 🟡 memória por jogador + decisão que aprende (v0.3.5); caminho, perigo, interceptar, derrubar parede e sabotar (v0.3.6); faro, varredura e arrombar (v0.3.7); falta rotina e diretor |
| 9 | **Terror** | 🟡 gore em 5 níveis e o golpe final da zebra (v0.4.0); o anti-trapaça saiu |
| 10 | Extras: FroggyDoom, filhotes, Ultimate Froggy, como ele aparece no mundo | |

Cada parte termina igual: compila no GitHub, eu testo aqui num cliente de
verdade (gravando a tela), te mando o `.jar` e o vídeo, tu testa no Zalith.

## 9. Respostas e o que ainda falta decidir

**Já decidido:**
- **Um FroggyDude por mundo.** Persistente, e volta depois de morrer.
- **Por enquanto ele só aparece por `/summon`.** O jeito de nascer no mundo fica pra Parte 10.
- **Só comendo ele recupera vida e limpa as feridas da skin.**
- **Voz original:** feita (pacote de voz separado, ver seção 0).
- **Mod de armas:** ele rouba a arma com a língua.
- **Modo dev:** mundo com `-dev` no nome.

- **Braço:** volta quando tu morre (a). Sangramento contínuo, sem estancar, com animação dele comendo o braço (feito na Parte 3).

- **Manhunt:** os dois modos, configurável (speedrun e sobrevivência), feito na v0.3.5.
- **IA:** o cérebro que aprende (por jogador, offline, salvo no mundo), começo feito na v0.3.5.

**Ainda em aberto ❓:**
- **O resto do Manhunt** (o LM disse que ainda vamos conversar): como ele aparece no mundo sem comando, se vale pra vários jogadores ao mesmo tempo (hoje: pegou um, acabou), e o equilíbrio. A 15 blocos/s ninguém escapa correndo em linha reta; o que salva é torre, água, porta e cabeça.

## 10. "Froggy's in your world" (plano, nada feito ainda)

O modo que o LM quer pro fim: ele age como um jogador que **invade o teu mundo**
e te caça até tu zerar o jogo. Respostas do LM:

- **Aviso no começo do mundo**, estilo analog horror / alerta de emergência de TV
  (barras de cor, chiado, letra que treme): *"Seja rápido, ele sabe onde você tá.
  E a vida do seu personagem depende de você. Zere o jogo antes que o froggydude
  [CENSURADO]"*. Um alerta genérico ("ALERTA DE EMERGÊNCIA"), sem imitar emissora
  de verdade.
- **Ele entra no mundo entre o dia 3 e o dia 10** (sorteado ao criar, escondido),
  com a mensagem amarela de "entrou no jogo" e a **LAN ligada**.
- **Sempre sabe onde tu tá.**
- **Se tu morre: renasce, e ele piora.**
- **Nível de IA nas opções do mod: Mín, Médio, Alto, Extra, Máx.** O nível mexe
  em tudo: esperteza (estratégia, emboscada), velocidade e dano, quanto ele sabe
  de ti (de quanto em quanto tempo e com que precisão ele "vê" tua posição),
  jeito de player e o mais humano possível (agachar, hesitar, olhar em volta,
  pular à toa, errar de propósito).
- **Acaba quando o dragão morre.**

### Como eu faria (por etapas)

1. Opção no mundo e no config (liga/desliga, nível), salva nos dados do mundo,
   com o dia sorteado.
2. A tela do aviso na primeira entrada (overlay do cliente + som de chiado).
3. A entrada: "FroggyDude entrou no jogo" no chat. Ele nasce longe (200-400
   blocos) e vem vindo; com o chunk descarregado, a posição dele anda "no papel"
   e ele só aparece de verdade quando chega perto (senão pesa no Android).
4. O faro global: ele mira tua posição de verdade, com atraso e erro conforme o
   nível (Mín: a cada minuto, com 30 blocos de erro; Máx: sempre, exato).
5. Jeito de player por nível: agachar na beira, pular andando, pilar bloco pra
   subir, ponte pra atravessar, quebrar porta/parede (já existe o arrombar),
   pausas e hesitação antes do bote, olhar pros lados.
6. Morte: tu renasce e ele sobe um degrau (mais rápido, mais dano, sabe mais),
   até o teto do nível escolhido.
7. Dragão morto: ele para de caçar (e uma cena final, se o LM quiser).

### Respostas do LM (19/10)

1. **Ritmo, no MAX:** primeiro ele **brinca** com o jogador; depois vêm os
   **"Ó, se eu quisesse..."** (chega perto, podia matar e não mata); só por
   fim vem a **perseguição**. Isso leva **dias** de jogo e não é frequente.
2. **Muitas mortes seguidas:** ele **zomba** do jogador por ser ruim e **vai
   embora**, falando **"Não mexe comigo"**. É a trégua: a espiral não continua.
3. **LAN de mentira:** pode ser (só a mensagem e o menu, sem abrir rede).
4. **Jeito de player (a IA melhorada):**
   - pensa como jogador: **caminho mais curto** e **sempre dá um jeito** de
     chegar (põe bloco pra subir/atravessar, **virando a câmera** pra olhar
     onde vai pôr, como gente faz);
   - **anda que nem player** (não como mob);
   - a **"dancinha"**: agacha e levanta rápido (o "shift spam"), pulando ou
     batendo no ar.

Depois (19/10): **só um jogador por enquanto** (nada de dois jogadores no
mesmo mundo). A IA em níveis **Mín, Médio, Alto, Extra, Máx** ficou nas
configurações do mod e no `/froggydude ia` (v0.4.3; o Alto é o de antes). O
ritmo de dias acima ainda falta e vai usar o nível escolhido. E ele está
**sempre com fome**: mata por fome qualquer bicho por perto quando não tem
ninguém pra caçar, reclama de fome e solta os **ribbits** toda hora.
5. **Multiplayer:** ainda sem resposta.

### O que pode dar errado (e as perguntas)

- **Dá pra ganhar?** Zerar leva horas; ele corre 15 blocos/s e sempre sabe onde
  tu tá. No Máx, quanto tempo um jogador normal dura? Se a resposta for "um
  minuto", o modo vira tela de morte. Eu poria janelas de respiro (ele some uns
  minutos depois de te pegar? de dia ele só segue e de noite ataca?). Qual tu
  prefere?
- **"Morreu, ele piora" é uma espiral:** quem tá apanhando apanha mais. Tem teto?
  Ou depois de X mortes seguidas ele dá uma trégua?
- **LAN de verdade abre uma porta na rede** (e no Zalith/Android quase não serve
  pra nada). Minha recomendação: LAN de mentira - só a mensagem no chat e o menu
  dizendo "aberto na LAN". Fica o susto, sem risco.
- **"Jeito de player" é o mais caro**: mob não constrói nem quebra bloco como
  gente. Pilar e ponte dá pra fazer; o resto é teatro (animação e pausa na hora
  certa). Quais 3 coisas de player assustam mais? É por elas que eu começaria.
- **Multiplayer:** com dois jogadores, ele caça quem? O mais perto, o que tá mais
  avançado no jogo, ou um de cada vez?

## 11. IA Real (v0.5.0)

Uma opção acima do Máx (config `ia = REAL`, Mods > Froggydude > Configurar ou
`/froggydude ia real`): a esperteza do Máx com o jeito do FroggyDude dos vídeos.
Estudados quadro a quadro e transcritos: "FroggyDude VS Every Opponent", "10v1
Manhunt but I have RABIES", "How many days can you survive against me?" e
"Minecraft Manhunt but I have RABIES" (Grox). Código: `entity/ai/RealTactics`,
`entity/ai/FroggyRetreatGoal`, `player/PlayerSight`, `client/ClientSight`.

- **Sabe o que está na tua tela.** O cliente manda 4 vezes por segundo se ele
  aparece na imagem (campo de visão e formato da janela de verdade, raio até a
  caixa dele: vidro e mato deixam ver, folha e parede não; de longe, no breu, ele
  some; conta o brilho e a visão noturna) e se tu está distraído (tela aberta,
  comendo, minerando).
- **Chegar sem ser visto** (tu não vê ele há 3 s): ele sorteia como chegar, pesando
  o lugar e o que já deu certo contra ti (amostragem de Thompson, guardada no mundo):
  - "I see you!... you can't hide from me!" e, no fim da fala, o bote de lado que
    esmaga (Grox, caverna 1:53-1:59);
  - de mansinho e calado pelas costas, e o bote;
  - parado e quieto no breu enquanto tu vem, e pega quando chega perto (10v1, 4:20-5:05);
  - direto.
  Se tu virar e ver ele, parte pra cima na hora. Bote só com o arco livre (na
  floresta ele batia na árvore).
- **Encarar:** às vezes, no começo, te encara parado de longe ("I can smell the
  fear in you! Time to eat!") e vem (How many days, 4:36).
- **Quem foge:** corre atrás e dá o bote colado, sem pulo de longe. **Fase 2: só
  corre** e derruba na trombada (Grox 5:28; o LM viu o mod pular).
- **Trancado em casa:** come o que tem em volta (aldeão, golem, bicho), aparece na
  janela rindo e olhando, e arromba quando tu te distrai (bau, inventário) ou depois
  de uns segundos. Casinha 1x1 e buraco ele arromba direto (Grox 0:44-1:35).
- **Lava:** só quando não tem outro caminho, anda por cima sem queimar (sem poção;
  só quando ele quer). **Pulo altíssimo:** só sem nenhum jeito de chegar (plataforma
  no ar, outro lado da lava), com a física do jogo calculada pra cair do teu lado,
  no máximo um a cada 2 min.
- **Fugir pra se curar:** abaixo de 50% apanhando, "Oh my god, I gotta get out of
  there now!", some da tua vista (mato, copa, água, breu), "I'm very low on health,
  I need food and a plan", come, "Back to full health!" e volta com "Time for the
  plan!": o plano diz como o bote termina (esmagar ou devorar).
- **Faro:** "I can smell him! He's nearby!".

## 12. Estudo da presa (v0.6, planejado com o LM em 21/10)

"O FroggyDude estuda a presa e vê o que mais assusta ou deixa o jogador paranoico,
pra deixar a carne mais saborosa." Referência: Grox 0:56-1:23 - o Grox se tranca,
vê ele comendo o aldeão lá fora, ele some, o Grox fica de janela em janela ("I think
he knows I'm in here") e ele já estava dentro, grudado no canto do teto, olhando pra
baixo; o Grox olha pra cima, "oh god", e ele cai em cima.

Respostas do LM:
1. **Tempero:** quando ele te acha, brinca uns minutos (some, aparece na janela, no
   teto, barulho, segue sem atacar) até o teu medo subir; aí ataca. Se tu bater nele
   ou fugir longe, ataca antes.
2. **Esconderijos onde ele te espera:** canto do teto (animação nova, pendurado no
   canto) e o breu da caverna.
3. **Animações:** ferramenta à minha escolha (vou fazer um visualizador no navegador:
   o modelo em 3D com a skin, cada animação com linha do tempo, câmera lenta e quadro
   a quadro, do lado dos quadros do vídeo).

Como fazer:
- **Rotina:** guardar os lugares que tu usa (bau, cama, bancada, onde minera, a casa),
  quanto tempo fica em cada e de quanto em quanto tempo volta. Prever "ele vai voltar
  pro bau daqui a pouco" e chegar antes, sem ser visto.
- **Medo:** medir a paranoia pelo jeito que tu joga (quanto vira a câmera, quantas vezes
  olha pra trás, se para de minerar, se se tranca, se foge) e ver qual susto subiu mais
  ela. Os sustos: aparecer na janela, vislumbre longe que some quando tu olha, ficar
  atrás de ti calado, barulho de onde tu não vê, esperar no teto, comer bicho/aldeão na
  tua frente. Cada um vira uma opção sorteada que aprende contigo, igual às táticas.
- **A hora de atacar:** quando o medo passa do ponto ("carne temperada"), a próxima
  emboscada é pra valer.

---

## Mod de gravação separado: "E se eu fosse o FroggyDude" (depois de terminar este)

Pedido do LM (22/10): um mod À PARTE (pra não dar conflito com este) em que o JOGADOR é o
FroggyDude - com a skin dele mesmo, só o comportamento - imitando o FroggyDude ORIGINAL dos vídeos
(não o do nosso mod), pra gravar vídeos quase iguais aos dele. Fácil de entender e de qualidade.

Respostas do LM:
- Controle no celular: estudar como os mods de animação de hoje fazem (roda de emotes, menus) e
  fazer um jeito melhor pra mobile: fácil e sem entupir a tela.
- Os ataques fazem efeito DE VERDADE (bote derruba, morde, arranca braço, mata, sangue, pedaços).
- Precisa ter: câmeras de cinema (ângulos dos vídeos, câmera lenta, sem HUD), a velocidade e os
  pulos dele (galope de quatro, pulo altíssimo, escalar), funcionar com amigos (multiplayer).
- Sons: só os que NÃO são a voz dele (galope, sangue escorrendo enquanto come...).

Direção do LM (23/10): a imitação MAIS PRÓXIMA possível dos vídeos do FroggyDude, com muitas e
muitas funções, mas a PRIORIDADE é ser fácil de usar.

Os riscos dessa direção (pra não esquecer):
- Muita função x fácil de usar puxam pra lados opostos, ainda mais na tela do celular. Cada função
  nova que vira botão ou item de menu deixa o mod mais difícil. Saída: o mod decide a maior parte
  sozinho pelo contexto (um botão de ataque que escolhe o golpe certo pela situação, igual ele faz
  nos vídeos) e só os especiais ficam numa roda; o resto vem em camadas (básico na tela, avançado
  escondido num menu).
- "Muitas e muitas funções" sem corte = mod que nunca sai. Primeira versão com os golpes que mais
  aparecem nos vídeos; o resto entra depois, em versões.
- "Mais próxima dos vídeos" sem a voz dele: a voz é metade dos vídeos. Decidir se o jogador dubla,
  edita depois, ou se o mod aceita um pacote de áudio que o próprio usuário põe (igual o pacote de
  voz deste mod).

Respostas do LM (23/10):
- Controles: estilo "emote", parecido com a roda de emotes do Essential Mod, com várias POSES +
  animações que fazem as coisas sozinhas. Ex: clica em "correr" e começa a galopar; se atacar um mob
  nesse modo, em vez de só bater o mod troca sozinho pra se jogar no mob (bote). Ou seja: a roda
  escolhe o MODO, e o contexto escolhe o golpe.
- Voz: uma opção de ligar ou desligar as vozes, com as vozes SEPARADAS (pacote à parte, igual
  neste mod). O mod em si não carrega a voz dele.

Respostas do LM (23/10, antes de começar):
- Nome: "BecomeLM mode" (privado, não vai ser lançado). Pasta becomelm-mod/, modid becomelm.
- Roda da v1: Galope de 4 e Em pé/normal.
- Câmeras de cinema da v1: as 4 - atrás e baixa, de lado acompanhando, na cara dele, a da vítima.

Pesquisa (23/10): não há nada confirmado sobre o que o FroggyDude usa; os jeitos comuns de virar
criatura no Java são o Figura e o Yes Steve Model (GeckoLib, Forge 1.20.1) - os dois trocam o corpo do
jogador por um modelo animado do Blockbench. O playerAnimator (a base do Emotecraft) foi abandonado.
O Zalith 2 só deixa os botões virtuais mandarem tecla - um mod não cria botão na tela dele.

Como vai ser feito:
- O corpo do jogador vira o modelo do FroggyDude deste mod (mesmo rig, mesmas animações já acertadas
  com os vídeos), com a skin do próprio jogador (o modelo usa o mapa de skin de jogador).
- A roda abre com dois toques rápidos no agachar (funciona com qualquer layout do Zalith) ou uma tecla
  no PC; é uma tela do jogo, então toca-se nos gomos com o dedo. A roda escolhe o modo; o contexto
  escolhe o golpe (fases seguintes).
- Por partes: v0.1 = corpo + roda (galope/em pé) + velocidade e pulos + as 4 câmeras + funcionar com
  amigos; depois os ataques de verdade; depois comer/arrancar/golpe final e as vozes (pacote à parte,
  com opção de ligar/desligar).

v0.1.0 (23/10) - feito e testado no jogo (sozinho e com 2 clientes num servidor dedicado):
corpo com a skin do jogador, roda (2 toques no agachar / R), galope ~14 b/s com poeira e som,
bote, pulo super alto carregado no agachar, sem dano de queda, as 4 câmeras (V), comando
/becomelm. Detalhes e limites em becomelm-mod/NOTES.md. Próximo: os ataques (o contexto escolhe
o golpe: bote em cima do bicho, esmagamento...).

Retorno do LM sobre a v0.1 (25/10): "tá incrível"; pulo alto, correr e pular funcionaram.
- O personagem do LM é um CACHORRO (skin: cachorro preto de coroa e terno), não um sapo:
  no BecomeLM NÃO vai ter língua.
- Método antes dos ataques (sugestão do LM): estudar mais a fundo como o FroggyDude se comporta
  nos vídeos, tentar adivinhar o que ele faz, montar teorias/hipóteses, mandar pro LM o que eu
  entendi e ele explica/corrige - só depois programar.
- A conferir: os botões PRI/SEC do Zalith ficam por cima de "De lado / Na cara / Da vítima"
  na roda (print do LM).

Estudo dos vídeos pro BecomeLM (25/10) - relatório 1 (cópia no scratchpad, study/RELATORIO_1.md):
- Medido em 60 qps: cair do céu (braços em V, ~10-15 blocos), socos montado 1 a cada 0,25 s
  alternando os punhos, mordida no mesmo ritmo (0,24 s), bote de frente (pulinho 0,15 s, pousa
  agachado, se joga), vítima derrubada (cai de lado ~0,13 s, fica ~1,4 s, levanta rolando ~0,45 s),
  comer deitado no monte de pedaços (cara vermelha um tempão), mortal pra trás + braços abertos.
- Sons: a patada do galope e a pancada do soco não são do Minecraft, nem do Epic Fight, nem do
  ParCool (detector conferido na nossa gravação: 0,89 no som certo; nos vídeos dele máx 0,28).
  Sempre no mesmo tom => som da animação do modelo dele.
- Hipóteses: modelo próprio (YSM/Figura) + Epic Fight (o derrubar/levantar rolando é igual ao
  "knockdown"/"knockdown_wakeup" dele, 0,60 s) + Physics Mod (cacos) + mod de arma + edição.

Respostas do LM (25/10) pra v0.2 do BecomeLM:
- Ataques: os 4 - bote de frente, cair do céu + socos, mordida, comer.
- No lugar da língua (cachorro): morder e sacudir + agarrar e arrastar.
- Sons da patada e do soco: recortar os originais dele, num pacote separado (uso pessoal, igual a voz).
- Derrubar: mobs e amigos (cai de lado ~1,4 s e levanta rolando).
- Ordem (um por vez): derrubar -> bote com dano -> céu + socos -> morder/arrastar -> comer.
