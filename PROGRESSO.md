# Onde paramos (pra retomar numa conversa nova)

Atualizado em 26/10/2026 (tarde). Se a conversa acabar ou ficar comprida demais, abre uma sessão nova
neste repositório, na branch `claude/froggydude-mod-research-pxrwch`, e escreve:
**"lê o PROGRESSO.md e continua de onde parou"**.

O que está no GitHub não se perde. O que fica só na sessão (vídeos, gravações, sons baixados)
some quando ela fecha. Por isso tudo que importa está anotado aqui ou nos `NOTES.md`.

---

## Os dois mods

| mod | pasta | versão | estado |
|---|---|---|---|
| Froggydude (a IA) | `froggydude-mod/` | 0.7.12 | CI verde, entregue |
| BecomeLM (tu vira o Froggy, só pra gravar) | `becomelm-mod/` | 0.3.0 (rede "5") | entregue em 26/10 |

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
- Ainda não foi colocado no mod (entra junto com a parte 2 da finalização).

Descobertas:
- O crec-crec do horror mod é a voz dele (gemido de dor rangido). O removedor de voz (Demucs) jogou tudo pra "voz", então o pente fino separa tom (gemido) de batida (estalo). O gemido cai 66 dB.

Pra refazer os 6 sons do zero: `becomelm-mod/tools/som/refazer_ossos.sh pasta_entrada pasta_saida`.
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

## Feito na 0.3.0 (26/10, pedidos do LM)

- Comer os bichos enche a fome e a saturação (o LM escolheu o "médio"): mordida que tira sangue
  = 1 ponto; afundar a cara no monte = 1 ponto, até 8 por monte; finalização = 3 x 4 pontos;
  arroto quando enche. Só bicho que sangra. Código: `LmAttacks` (bite, pileEat, eat).
- O galope (e a fase 2) gasta fome igual um jogador correndo normal: mixin
  `PlayerGallopHungerMixin` limita a cobrança de correr no chão a 0,028 por tick.
- Bug: correndo e pausando, o corpo continuava correndo atrás do menu. Causa: o relógio da
  animação usava `mc.getFrameTime()`, que gira de 0 a 1 com o jogo pausado. Agora usa o pedaço
  de tick do desenho (congela na pausa).

## Outras pendências

- **Halo na cabeça:** o sangue na skin desenha uma "casca" meio transparente em volta da cabeça (aparece igual no corpo normal e no do BecomeLM). Perguntar ao LM: deixa assim ou conserto nos dois mods?
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
