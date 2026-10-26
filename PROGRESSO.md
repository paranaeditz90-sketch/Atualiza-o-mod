# Onde paramos (pra retomar numa conversa nova)

Atualizado em 26/10/2026 (fim da tarde). Se a conversa acabar ou ficar comprida demais, abre uma sessão nova
neste repositório, na branch `claude/froggydude-mod-research-pxrwch`, e escreve:
**"lê o PROGRESSO.md e continua de onde parou"**.

O que está no GitHub não se perde. O que fica só na sessão (vídeos, gravações, sons baixados)
some quando ela fecha. Por isso tudo que importa está anotado aqui ou nos `NOTES.md`.

---

## Os dois mods

| mod | pasta | versão | estado |
|---|---|---|---|
| Froggydude (a IA) | `froggydude-mod/` | 0.7.13 | entregue em 26/10 (o halo) |
| BecomeLM (tu vira o Froggy, só pra gravar) | `becomelm-mod/` | 0.3.3 (rede "7") | 26/10: a flexão no monte |

Detalhes de cada versão: `froggydude-mod/NOTES.md` e `becomelm-mod/NOTES.md`.
O plano grande da IA: `PLANO.md`.

## Regras do LM (valem sempre)

- Perguntar antes de aplicar mudança no mod. Uma coisa por vez.
- Perguntar sempre que tiver a menor dúvida. Pode decidir sozinho, mas avisando e dando atualização constante.
- Push só na branch `claude/froggydude-mod-research-pxrwch`. PR só se o LM pedir.
- **O repositório é público.** A voz e os sons originais do FroggyDude e os sons do LM (Voicemod, o do vídeo que ele editou no CapCut) **nunca** entram aqui. Vão só no pacote de sons pessoal dele. Som livre (CC0) pode ir dentro do mod.

---

## Som dos ossos (#160): o LM escolheu o 5

Em 26/10 o LM ouviu os 6 candidatos e escolheu o **5**: o meu misto (CC0) + os ossos do
"MINECRAFT BUT I'M THE HORROR MOD" no pente fino, meio estourado.
- Dentro do mod vai só a parte livre (o misto CC0, meio estourado).
- A versão completa (com o horror mod, que é do FroggyDude) vai no pacote pessoal BecomeLM-Sons.
- Usar na transformação (`crack_seq`/`crack`) e nas costelas da finalização (o estalo 6 é o modelo).
- **No pacote pessoal desde 26/10** (`BecomeLM-Sons.zip`, mandado pro LM):
  - `contort` = o 5 inteiro com 0,1 s de silêncio na frente. O mod toca no tick 8 e o 5 foi montado pra começar no tick 10.
  - `crack_seq` vazio.
  - `crack` = 6 estalos de 0,28 s no estilo do 5. Compridos (0,9 s) viravam um crec-crec sem parar no "torto", porque tocam a cada 0,4 a 1 s.
  - Testado no jogo: o som gravado bate com o 5 (correlação 0,85).
- Dentro do mod (a versão CC0) ainda não: entra junto com a parte 2 da finalização.

Descobertas:
- O crec-crec do horror mod é a voz dele (gemido de dor rangido). O removedor de voz (Demucs) jogou tudo pra "voz", então o pente fino separa tom (gemido) de batida (estalo). O gemido cai 66 dB.

Pra refazer os 6 sons e o pacote `BecomeLM-Sons.zip` do zero: `becomelm-mod/tools/som/refazer_ossos.sh pasta_entrada pasta_saida`.
A pasta de entrada precisa de 3 arquivos que o LM tem (estão no zip LM_SALVAR_26-10, pasta 3):
`horror.mp4` (Screenrecorder-2026-09-30-16-01-56, 5,6 s), `voicemod.mp3`, `video.mp4` (lv_0_20261025230010).
Os sons CC0 o script baixa sozinho:
- "Deep bone crack/break SFX", Zane Little Music, https://opengameart.org/content/deep-bone-crackbreak-sfx
- "Bones cracking" #0596, Joseph Sardin, https://bigsoundbank.com/bones-cracking-s0596.html

O que cada script faz está no começo de cada `.py` em `becomelm-mod/tools/som/`.

---

## Golpe de finalização com cutscene (#162)

**Parte 1 feita (26/10), esperando o LM aprovar o vídeo** (`BecomeLM_finalizacao_parte1.mp4`):
- `LmFinish` (linha do tempo), `LmAttack.FINISH`, `LmAttacks.tryFinish/tickFinish` (servidor),
  `LmConfig` (`becomelm-common.toml`, chance 0,01), `/becomelm finisher [jogadores]`, rede "5".
- Gatilho: soco normal (AttackEntityEvent), bote, agarrar e queda do céu.
- Cliente: senta nas coxas (0,45), escorrega montando; animações `fin_mount/dig/open/feed/stare`
  (make_anims.py); `LmFinishClient` = câmera em 8 planos + plano do fim (1,5 s) + sem HUD + sem nome;
  vítima jogador vê dos próprios olhos (LmKnockClient.onCamera), FOV maior, sem HUD.
- Testado: aldeão (sozinho) e amigo (2 clientes + servidor dedicado).

**Parte 2 (próxima):** o gore — ferida crescendo, sangue espirrando, costelas (som 5), órgãos,
a tripa até a boca, a cara suja, a vítima se debatendo; sons da cena.
**Parte 3:** bicho de quatro patas (ovelha, vaca...).

Respostas do LM:
- **Gatilho:** é um evento raríssimo, pode acontecer até com o bicho de vida cheia ("ovelha com vida cheia, ataco e começa uma cutscene").
- **Câmera:** automática, com planos.
- **Amigo que é a vítima:** vê pelos próprios olhos, deitado, sem se mexer, e morre no fim.

Decisões minhas (avisar o LM, ele pode mudar):
- Chance de ~1% por ataque, ajustável num config novo `becomelm-common.toml`.
- Comando `/becomelm finisher` (op) faz o próximo ataque virar cutscene, pra testar e gravar.
- Bebês não. Chefes (dragão, wither, warden) não. Só bicho que sangra (`LmBloodType`) e que pode ser derrubado; PvP respeitado.
- Primeiro os que ficam em pé (jogador, aldeão, zumbi...). Os de quatro patas (ovelha, vaca...) vêm logo depois, porque a barriga fica embaixo e precisa de outra pose.
- ~11,5 s. No fim o corpo quebra em pedaços (com pouca força, ~0,15).

Como montar (em cima do que já existe):
- Novo ato `LmAttack.FINISH` (no fim do enum). Começa no servidor: `AttackEntityEvent`, ou o acerto do bote, ou o comando.
- Vítima de costas e presa (`LmKnock.knockBack` + `steer`), mob ancorado no lugar. LM travado, sentado no quadril da vítima (~0,35 ao longo de `headDir`), olhando pra cabeça.
- Sem dano até o fim. No fim: dano gigante com a fonte do ataque do jogador, depois `BreakBody`.
- Linha do tempo (~230 ticks):
  1. 0-8: monta.
  2. 8-68: cava. Patas alternadas feito cachorro, sangue espirrando, ferida crescendo.
  3. 68-86: abre. Puxa pros lados, costelas estalam (o som escolhido), órgãos aparecem.
  4. 86-196: come. 3 ciclos: enfia a cara, puxa a tripa até a boca, sacode, mastiga. A cara suja, os órgãos somem um por um.
  5. 196-230: encara a câmera com a cara ensanguentada e rosna.
- Cliente (`LmFinishClient`): sons e sangue no tempo certo. A vítima se debate com o truque da `WalkAnimationState` do mod da IA (`f_267358_`, `f_267371_`, `f_267406_` + cabeça tremendo, `jitter(t,s) = sin(t*s)*0.7 + sin(t*s*2.27+1.3)*0.3`).
- Tripas desenhadas no ponto da barriga: `P + d*0.9 + up*(lift + bbWidth*0.21)` (0,35 pra corpo de aldeão). Ferida, caixas de órgão (tripa, estômago, fígado, coração pulsando, costelas quebradas) e a corda da tripa até a boca (pega a cabeça do osso salvo no `LmRenderer`).
- Câmera do LM: planos automáticos, HUD escondido (o `LmCamera` já faz isso). Câmera da vítima: olhando pros pés/barriga, pitch ~ −35°.
- Animações novas no `make_anims.py`: `fin_mount`, `fin_dig`, `fin_open`, `fin_feed`, `fin_stare`.
- Fazer de um jeito que o editor de keyframes (#157) depois consiga criar estilos novos de finalização.

Referência no mod da IA: `FroggyFinisherGoal`, `CinematicCamera`, `VictimRender`.

---

## Feito na 0.3.1 (26/10, pedido do LM)

- **Mortal**: deitado no monte comendo, pular = mortal pra trás e pousa de braços abertos ("tcharam").
  O LM escolheu: só deitado no monte comendo, só com o pular.
- Código:
  - `LmMove.FLIP`, `LmMoves` (`FLIPS`/`LANDED`, `flipTurn`, `flipAir`, `tada`).
  - `LmActsClient.flip`: pulo 0,5 pra cima e 0,12 pra trás.
  - `LmClientEvents.flip`: o corpo gira em volta da cintura, 0,95.
  - Animações `flip`/`tada` no `make_anims.py`.
  - Câmera em primeira pessoa dando a volta.
- Bug achado no teste com amigo: o "levantar" (RISE) que o cliente cria quando o servidor acaba o
  monte tapava o mortal pra quem vê. Agora não cria se o jogador está num mortal.
- Testado com 2 clientes + servidor dedicado.

## Feito na 0.3.0 (26/10, pedidos do LM)

- Comer os bichos enche a fome e a saturação (o LM escolheu o "médio"): mordida que tira sangue
  = 1 ponto; afundar a cara no monte = 1 ponto, até 8 por monte; finalização = 3 x 4 pontos;
  arroto quando enche. Só bicho que sangra. Código: `LmAttacks` (bite, pileEat, eat).
- O galope (e a fase 2) gasta fome igual um jogador correndo normal: mixin
  `PlayerGallopHungerMixin` limita a cobrança de correr no chão a 0,028 por tick.
- Bug: correndo e pausando, o corpo continuava correndo atrás do menu. Causa: o relógio da
  animação usava `mc.getFrameTime()`, que gira de 0 a 1 com o jogo pausado. Agora usa o pedaço
  de tick do desenho (congela na pausa).

## Fila (pedidos do LM em 26/10, à tarde), nessa ordem

1. **Pancadas com os dois braços: FEITO na 0.3.2** (testado com 2 clientes + servidor: toque rápido, segurar 3 s, segurar até matar e deitar no monte). Respostas do LM:
   - Segurar o atacar = martela sem parar, 4 por segundo (soltou, para).
   - A 1ª pancada derruba o bicho de costas, olhando pra cima pro jogador, e ele fica em cima martelando.
   - Só em pé (de 4 continua o bote e o agarrar). Dano: 1 coração por pancada (igual a queda do céu).
   - Código: `LmAttack.HAMMER`/`HAMMER_STOP`, `LmAttacks.startHammer/tickHammer`, `LmAttackClient.hammerTarget/hammerTick`;
     montado igual o esmagamento (`riding`), animação `sky_smash`, escorrega 0,2 s até em cima; rede "7".
2. **Comer no monte em flexão: FEITO na 0.3.3** (o LM mandou o vídeo: 10v1 "Manhunt but I have RABIES" ~1:50-1:56 + print circulado):
   - Medido a 60 qps: How many days 0:44 (de frente), 10v1 0:22 (deserto) e 1:55 (de pertinho). Uma mergulhada a cada
     0,25-0,4 s (usei 0,3 s); o topo da cabeça desce ~5-6 px (de olhar pra frente a olhar pra baixo); os ombros quase
     não mexem e ficam na altura do meio da cabeça (de frente); a cabeça tomba ~25 graus pros lados, devagar.
   - 1ª tentativa (braços abertos em "A", fincados do lado) o LM barrou: "ele não abre os braços". Mandei 3 jeitos de
     braço pra frente (esfinge, abraçando a comida, V aberto) e o LM escolheu a **esfinge**.
   - `pile_lie` novo (`make_anims.py`, `_push`): quadril e pernas deitados, peito empinado 24 graus que desce pra 12 a
     cada mergulho, braços retos pra frente por cima do monte (ângulos tirados do esqueleto: a mão fica nos cacos, ~2
     blocos na frente dos pés, escorrega ~0,1 bloco no mergulho), ombros encolhidos 2 px, 8 mergulhos em 2,4 s com o
     tombo da cabeça variando. `PILE_CENTER` 1,4 (era o `PILE_CHEST` 1,0): o meio do monte fica embaixo dos ombros.
   - `pile_drop` sem a pose em pé no começo (de 4 ele ficava em pé um instante antes de cair).
   - Servidor: `PILE_DIPS` = os 8 mergulhos, `PILE_BITES` = 3 deles (come no mesmo ritmo de antes), contando do
     `PILE_LIE_START` (deitar + 3 ticks de mistura do controlador). Cliente: os cacos mexem em todo mergulho, o
     barulho só nas mordidas; o olho em primeira pessoa fica em cima do peito e desce um pouco junto.
   - Testado com 2 clientes + servidor (de frente, de lado, na diagonal, deitar de 4 e o mortal saindo).
3. **Finalização parte 2** (o gore): o LM aprovou seguir.

## Outras pendências

- **Halo na cabeça: CONSERTADO e entregue (26/10, BecomeLM 0.3.2 + Froggydude 0.7.13)**:
  - Causa: o sangue ia na camada de fora da skin (meio pixel pra fora do corpo), e onde a skin não tem nada ali ele ficava boiando.
  - Agora o sangue fica na camada de fora só onde a skin tem pixel. No resto vai colado na pele.
  - A skin é lida do PNG (`SkinAlpha` no mod da IA, `LmSkinAlpha` no BecomeLM), nunca da placa de vídeo, porque no celular é arriscado.
  - A cara suja do BecomeLM (`LmBloodFace`) virou pingos colados, igual o sangue do mod da IA.
  - Testado com antes e depois (2 clientes + servidor, a câmera do amigo do lado da cabeça).
- #154 pacote de sons pessoal do BecomeLM: falta a patada do galope e o impacto do bote.
- #155 teste completo, notas e versão.
- #156 câmera ajustável (ângulo livre) + como gravar a tela no celular.
- #157 editor de keyframes (o LM fazer as próprias animações).
- Do mod da IA: #92 ritmo de dias do "Froggy's in your world", #133 fome igual aos vídeos (creeper voando com a língua), #134 skin some no celular, #144 baixar os vídeos que faltam (o YouTube bloqueia download daqui; os TikToks funcionam).

## Como testar aqui (pra quem retomar)

- Tela virtual: `Xvfb :99 -screen 0 1280x720x24 -nolisten tcp`.
- Os dois mods juntos: rodar o BecomeLM com `-PwithFroggy=<jar do froggydude>` e copiar o `froggydude-client.toml` pro `run/config` dele.
- Mirar com pitch 35 acerta um porco a 2 blocos (com pitch 15 erra).
- Câmera de cinema: tecla `v` (OFF → atrás baixa → lado → cara → vítima).
