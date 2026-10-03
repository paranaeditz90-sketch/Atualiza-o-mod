# Plano do mod Froggydude

Objetivo: **pavor psicológico.** O FroggyDude pensa, observa, aprende e caça.
Ele quer duas coisas: **comer** e **meter medo**. Gore existe, porque está na lore.

Este plano existe pra uma coisa: cada parte nova encaixar na anterior sem
quebrar animação, skin ou IA no meio do caminho. Tudo depende de um
**contrato único** (seção 1). Se algo não está no contrato, não entra.

Legenda: ✅ feito · 🔜 próxima · ⏳ depois · ❓ depende de resposta tua (seção 9)

---

## 0. Onde estamos (Parte 1 ✅)

- Compila no GitHub Actions e gera o `.jar` com o GeckoLib junto.
- Testado aqui num servidor e num cliente de verdade:
  - ele nasce;
  - revida quando apanha (matou o zumbi que bateu nele e deixou o porco em paz);
  - renderiza com a **skin real**;
  - o sangue muda com a vida.
- **Ainda não testado com jogador:** língua, pulos, prender no chão e tremor de tela. É isso que tu testa agora.

---

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

## 2. Skins por evento (Parte 3)

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

## 3. Animações (Parte 4)

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

## 4. Habilidades (Parte 5)

- **Movimento:** corrida de quatro, subir parede (igual aranha), nadar rápido, quebrar vidro, abrir porta de madeira, pular cerca.
- **Língua:** chicote, agarrar, capturar e **provar o gosto** (dá a marca de faro, seção 5.3). Também **arranca o item da mão** (escudo, arco, totem da mão secundária).
- **Comer:** come o que prende (mob ou jogador), **cura** e ganha buff conforme o que comeu (blaze = resistência ao fogo e força, carne = vida, aldeão = velocidade).
- **Imortal-ish:** a lava cura ele em vez de machucar. Ao "morrer" pela primeira vez, finge de morto e **volta**. A morte de verdade só vem na segunda vez, ou pelo void (é assim que o Parallax perdeu).
- **Arrancar o braço do jogador:** seção 6.

---

## 5. Inteligência (Parte 6)

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

## 6. O braço arrancado (Parte 5)

- **Quando:** ataque especial `arm_rip`, só com tu preso no chão (bote ou captura) e com vida baixa. Nunca do nada.
- **Qual:** o da **mão secundária** (o esquerdo, ou o direito se tu joga canhoto).
- **Efeitos:**
  - perde o espaço da mão secundária: o que estiver ali cai no chão (escudo, totem, tocha) e não dá pra trocar com F;
  - **sangramento:** dano a cada poucos segundos e partículas de sangue até estancar ❓;
  - **vida máxima reduzida** (proposta: −3 corações) ❓;
  - **faro:** ele te sente a ~160 blocos (seção 5.3);
  - **visual:** o braço some do teu personagem (1ª e 3ª pessoa) e fica o toco ensanguentado.
- **Volta?** ❓ (pergunta 1)

---

## 7. Anti-trapaça e a invasão (Parte 7)

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

**Travas de segurança (não negociáveis):**
- **Nunca apaga mundo, arquivo ou nada fora do jogo.** O banimento é um registro dentro do save, que dá pra desfazer.
- **Perdão:** opção `perdoar = true` no arquivo de config. Na próxima entrada, o ban some.
- **Modo desenvolvedor:** `anti_trapaca = false` no config. **Sem isso, tu vai se banir testando o mod com /summon.**

---

## 8. Ordem das partes

| Parte | O quê | Por que nessa ordem | Entrega |
|---|---|---|---|
| 1 ✅ | Combate básico | | `.jar` pra testar |
| 2 🔜 | **Fundação:** contrato completo dos estados, sons (com `sounds.json`, que hoje crasha se ligar o frenesi), arquivo de config, comando de debug `/froggydude` (forçar estado, ver memória, perdoar) | Sem isso, cada parte nova quebra a anterior | `.jar` + prints das poses |
| 3 | **Skins em camadas** + regras evento → skin | A arte já existe (31 skins). É independente da IA | `.jar` + prints de cada combinação |
| 4 | **Animações** (lista da seção 3) | Depende do contrato | `.jar` + prints de cada animação |
| 5 | **Habilidades** + braço arrancado | Depende das animações | `.jar` |
| 6 | **Inteligência:** percepção, faro, memória, decisão, diretor | Orquestra tudo que veio antes | `.jar` |
| 7 | **Terror + anti-trapaça + invasão** | É o diretor usando todas as peças | `.jar` |
| 8 | Extras: FroggyDoom, filhotes, Ultimate Froggy | Só com o resto sólido | `.jar` |

Cada parte termina com: compilou no GitHub, testei aqui no servidor e no
cliente, te mando os prints e o `.jar`. Tu testa no Zalith e me fala.

---

## 9. Perguntas que mudam o código ❓

1. **O braço volta?**
   - (a) Volta quando tu morre.
   - (b) Volta com um item caro (ex.: maçã dourada encantada).
   - (c) Nunca volta naquele mundo.

   Eu iria de (a) ou (b). Com (c), o mundo vira castigo e tu larga em uma semana.
2. **Sangramento:** para sozinho depois de um tempo, ou precisa de algo (comer, enfaixar com lã/papel)?
3. **Quantos FroggyDudes?** Eu defendo **um só por mundo**, persistente, que volta depois de morrer. É o que faz a memória e o aprendizado fazerem sentido: é *ele* que te conhece. Com vários, cada um começa do zero e ele vira mob comum.
4. **Como ele aparece?** Depois de X dias de mundo, na primeira noite de lua cheia, ou quando tu comer carne de sapo? (Essa última é minha favorita.)
5. **Voz:** tu vai gravar as falas ("ribbit", "bark bark", "I SEE YOU", risada, grito), ou uso sons do jogo distorcidos por enquanto? Pra uso pessoal, decide tu. Pra publicar, áudio dele só com autorização dele.
6. **Trapaça:** a lista da seção 7 tá boa? Tem mais algum mod de trapaça além do Viltrumita?
