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
- **Config** (`config/froggydude-common.toml`), **mundo `-dev`**, **comando `/froggydude`** (invocar, info, manhunt, fase2, cerebro, esquecer, falas, trapaca testar, perdoar) e **um FroggyDude por mundo**.
- **Manhunt, os dois modos** (decisão do LM): speedrun (matar o dragão antes dele te pegar) e sobrevivência (X dias). Vantagem com contagem, ele te segue pro Nether/End, volta depois de morrer, some quando vence.
- **Cérebro que aprende** (escolha do LM): por jogador, salvo no mundo; aprende teu estilo, qual estratégia de caçada funciona contra ele (apavorar / direto / cercar por trás) e quais ataques te acertam.
- **Anti-trapaça + invasão do 911** (seção 7), com `-dev` desligando e `/froggydude trapaca testar` pra ver sem banir.

**Parte 4, ajustes (v0.3.6) ✅** - em cima do teste do LM:
- **Cabeça girando do nada (bug crítico):** a marca de "osso mexido" do GeckoLib sobrevivia de um quadro pro outro e, na animação de andar (que não mexe na cabeça), o olhar era somado de novo a cada quadro. Agora a marca é limpa depois de cada ajuste e a animação de andar trava a cabeça na pose.
- **Língua:** meio coração por chicotada/puxão.
- **Esmagamento:** 24 socos (um a cada 0,25 s, 6,6 s ao todo), dano bem menor por soco, e o "tai!" do original (vs AJTHEBOLD) em cada um.
- **Fase 2:** o som da contorção é o do short original (ossos estalando + gemido torto) e o grito de raiva fica pro levantar. Contorcendo e gritando ele não é mais empurrado pelos golpes nem gira atrás de quem bate (no teste ele "patinava" com a pose congelada).
- **Anti-trapaça:** vale com manhunt OU quando ele está te caçando (fugir dele pro criativo é trapaça). Sem aviso: ele vira pra ti e reage FALANDO, com o áudio original ("What... the hell?! That's cheating! You can't do that!") e a invasão segue o vídeo segundo a segundo: escurece, a janela estoura (o vidro do vídeo), a casa e a ligação pro 911 com o jogador desesperado, e no "AHHH!" ele te derruba. Áudio só pra quem trapaceou. Nada de texto no chat.
- **Inteligência:** caminho até 80 blocos e 4x mais rotas testadas; abre porta; lembra onde se machucou (lava, fogo, explosão, cacto, neve fofa, flecha de dispensador) e desvia por ~1 hora; corta caminho pra onde tu vai estar (intercepta) e, se a estratégia é cercar, vem por trás. Trancou-se em casa: derruba a parede/porta/janela a tiros de língua ("How strong is my tongue?": vidro e terra num tiro, tábua em dois) e come o telhado se tu estiver embaixo. No speedrun: come o portal do Nether quando tu está longe e come os aldeões perto de ti (sem troca).

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

## 7. Anti-trapaça e a invasão (Parte 9)

Inspirado no fim de **"How many days can you survive against me?"** (6:42–7:50):
- o último jogador trapaceia e vai pro criativo;
- "nem o criativo te salva";
- o FroggyDude invade a casa;
- som de rasgo;
- ligação pro 911.

**O que conta como trapaça** ❓:
- mudar pra criativo/espectador;
- usar comando (`/give`, `/tp`, `/effect`, `/kill` nele);
- voar no sobrevivência;
- resistência 255;
- mod de trapaça instalado. Detectado pela lista de mods: o **Viltrumita** tem versão Forge 1.20.1 (`viltrumitecore-forge`), então dá pra pegar pelo ID.

**A sequência:**
1. **1ª vez: aviso.** A tela dá um glitch, aparece no chat `<FroggyDude> I SEE YOU, <teu nome>` e ele aparece na tua janela, olhando. Mais nada.
2. **2ª vez: invasão.**
   - Ele **tira teu criativo** e tu volta pro sobrevivência.
   - Vira noite.
   - As tochas da tua base apagam uma a uma.
   - Batida na porta, e a porta abre.
   - Ele está **dentro da tua casa** (que ele já conhece pela memória).
   - Nada te protege: o dano dele ignora invulnerabilidade.
   - Jumpscare, som de rasgo.
3. **Banimento:** tela de desconexão estilo boletim de ocorrência ("Jogador X encontrado morto em casa. Porta destrancada. Polícia investiga."). O mundo grava tua conta como banida e te expulsa toda vez que tu tentar entrar.

**Modo desenvolvedor pelo nome do mundo (ideia tua):** se o nome do mundo tiver **`-dev`** (ex.: `teste-dev`), o anti-trapaça fica desligado **só naquele mundo**: sem aviso, sem invasão e sem banimento. Os outros mundos funcionam normal. Assim tu testa com comandos sem mexer em config.

**Mod de armas não dá ban:** ele arranca a arma com a língua (seção 4). Ban é pra trapaça de verdade (criativo, comandos, voar, Viltrumita...).

**Travas de segurança (não negociáveis):**
- **Nunca apaga mundo, arquivo ou nada fora do jogo.** O banimento é um registro dentro do save, que dá pra desfazer.
- **Perdão:** opção `perdoar = true` no arquivo de config. Na próxima entrada, o ban some.
- **Modo desenvolvedor:** mundo com `-dev` no nome (acima). Também fica a opção `anti_trapaca = false` no config, pra desligar em todos os mundos.

---

## 8. Ordem das partes

Tu pediu animação e som primeiro, e fez sentido: era o que mais destoava dos vídeos. Depois veio o resto do combate dos vídeos (esmagamento, pulo do céu, braço, fase 2) antes da fundação.

| Parte | O quê | Situação |
|---|---|---|
| 1 | Combate básico | ✅ |
| 2 | **Animações de corrida/bote + sons + câmera + língua** | ✅ |
| 3 | **Combate completo:** tamanho de jogador, pulinhos com velocidade variável, esmagamento, pulo do céu, devorar mais longo, língua em pirâmide e mais rara, braço arrancado e comido, sangramento, fase 2 | ✅ esta entrega |
| 4 | **Fundação:** config, mundo `-dev`, comando `/froggydude`, um por mundo, manhunt (2 modos), falas na ordem | ✅ v0.3.5 (+ cérebro e anti-trapaça adiantados) |
| 5 | **Skins em camadas** + regras evento → skin (comer suja a frente, lava carboniza, água lava) | |
| 6 | **Resto das animações** (parede, nadar, espiar janela, bater na porta, giro de cabeça, estalo de costas, lavar sangue) | |
| 7 | **Habilidades:** subir parede, nadar e arrastar pra água, comer pra curar, ressuscitar, roubar item com a língua | |
| 8 | **Inteligência:** percepção, faro, memória, decisão, diretor de tensão | 🟡 memória por jogador + decisão que aprende (v0.3.5); caminho, perigo, interceptar, derrubar parede e sabotar (v0.3.6); falta faro, rotina, diretor |
| 9 | **Terror + anti-trapaça + invasão** | 🟡 anti-trapaça e invasão do 911 (v0.3.5), com a reação e o áudio originais (v0.3.6) |
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
- **Anti-trapaça:** só vale com manhunt rodando (senão ia banir quem só usa `/gamemode` construindo).

**Ainda em aberto ❓:**
- **O resto do Manhunt** (o LM disse que ainda vamos conversar): como ele aparece no mundo sem comando, se vale pra vários jogadores ao mesmo tempo (hoje: pegou um, acabou), e o equilíbrio. A 15 blocos/s ninguém escapa correndo em linha reta; o que salva é torre, água, porta e cabeça.
