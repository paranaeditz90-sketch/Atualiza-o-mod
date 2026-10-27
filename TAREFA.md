# Tarefa: "Froggy's in your world" - a chegada (e 3 ajustes pequenos da IA)

Estado: planejado
Mod(s): froggydude-mod (versão atual: 0.7.15)

## Pedido do LM
"In your world? Tipo, ele aparece entre 4 a 10 dias no mundo?" (escolha: só a chegada, por agora).
Em 27/10, na ordem de prioridade, o LM pôs o "In your world" primeiro.

## Decisões já tomadas (27/10, caixinhas)
- Como liga → **opção no config** (liga/desliga na tela do mod). Ligado, todo mundo novo sorteia o dia
  da chegada (4 a 10) escondido.
- Antes da chegada → **sinais de leve**, com a ressalva do LM: "ele não pode aparecer quando não for a
  hora, e os bichos não podem morrer do nada na frente do jogador, somente fora do campo de visão dele".
- Quais sinais → **todos**: ribbit longe, bicho comido (morto fora da vista, sangue em volta), rastro de
  sangue/pegadas perto de onde ele anda, passos atrás que param quando ele vira.
- Quando começam → **1-2 dias antes** da chegada.
- A chegada → **longe e vem vindo**: "FroggyDude entrou no jogo" (amarelo), nasce a 200-400 blocos e vem
  (sabe onde o jogador está).
- LAN falsa → **sim, uns segundos antes dele entrar** (só a mensagem de "jogo local hospedado na porta",
  nenhuma rede aberta de verdade).
- Depois de chegar → **caça direto**, com o nível de IA escolhido (o LM joga na Real). O "ritmo de dias"
  (brinca → "Ó se eu quisesse" → perseguição) fica pra depois.
- Morreu → **some um tempo e volta** (respiro). O "zomba e vai embora" depois de 3 mortes no dia continua.
- Fim (dragão morto) → ele escreve no chat **"NAO! MERDA! Ah... Vá se ferrar"** e sai do jogo
  ("FroggyDude saiu do jogo"), some de vez naquele mundo.
- Chat como jogador → "em momentos certos ele fala no chat, como um jogador de verdade". Exemplo do LM:
  o jogador escondido (na fortaleza do Nether, mas vale pra qualquer lugar) e ele escreve
  **"EU QUERO SEU CORPO! Sniff sniff sniff... Ah.. eu consigo sentir o cheiro daquele corpo..."** - e
  aí volta a saber onde o jogador está, pelo cheiro.
- Também decidido em 27/10 (fora do in your world, pequenos):
  - Transformação "do nada quando irritado" → **rara e na hora certa** (apanhou, perdeu a presa, raiva de
    verdade); nunca no meio de uma corrida em que ele já está quase pegando.
  - Língua → **menos língua, mais bote**: a língua que puxa de 9 blocos fica pra quem foge longe ou está
    fora do alcance; de perto ele corre e derruba no bote (nos testes ela era o 1º golpe quase sempre).
  - BecomeLM → **depois** do in your world.
  - Bug da skin sumindo no celular (#134): o LM diz que **parou**. Travadinha no celular: **não trava**.

## Contexto técnico essencial
- Um FroggyDude por mundo: `froggydude-mod/src/main/java/com/froggydude/world/FroggyKeeper.java`
  (`spawnNear(player, minD, maxD, Sight, health)` linha 114, `findSpot` 159, `visible` 207 - já sabe
  achar lugar fora da vista; `onDeath` 86 e `respawnAt` usados pelo manhunt).
- Dados do mundo: `world/FroggyWorldData.java` (manhunt: `manhuntActive`, `respawnAt`, `manhuntStartDay`...);
  é aqui que entram o dia sorteado, "já chegou", "já saiu" (salvar no NBT, com valor padrão pra mundo velho).
- Config: `config/FroggyConfig.java` (+ a tela de config e o lang pt_br/en_us).
- Dragão: `event/FroggyServerEvents.java:80` já trata `EnderDragon` morto no speedrun (reaproveitar).
- Faro: `entity/ai/FroggyTrackGoal.java`; "sempre no teu rastro" no manhunt: `world/Manhunt.java`
  (`keepOnTrail`). O cheiro do chat ("EU QUERO SEU CORPO...") = o momento em que ele volta a saber.
- Sangue: `entity/BloodFx.java`; ribbit: `FroggydudeEntity.ribbit`; passos sem aparecer: o jeito do
  `FarScare.makeNoise` (passos do bloco embaixo, `SoundSource.HOSTILE`).
- Vista do jogador: `player/PlayerSight` (o cliente manda o que está na tela) - usar pra garantir
  "fora do campo de visão" nos sinais (bicho morto, passos).
- Mensagens: "entrou/saiu do jogo" com as chaves do próprio jogo (`multiplayer.player.joined/left`,
  amarelo) e a LAN com `commands.publish.started` - saem na língua do jogador. Fala no chat como
  jogador: `chat.type.text` com o nome "FroggyDude".
- IA desta versão (0.7.15): `entity/ai/RealTactics.java` ("viu, vai"), `entity/ai/FroggyDoorGoal.java`.
  Transformação do nada: `FroggyCombatGoal.irkNow` / `FroggydudeEntity.irk`. Língua de captura:
  `FroggyCombatGoal.pickAttack`/`runAttack` (TONGUE_GRAB / "lingua de captura").
- Regras do CLAUDE.md: repo público (as falas do chat são texto do LM, ok; sem áudio dele aqui),
  nada de commit/push sem OK do LM, só no ramo da sessão.

## Passos (uma coisa por vez)
- [ ] 1. Config + dados do mundo: liga/desliga, dia sorteado (4-10) ao criar/primeira entrada, estado
      (esperando / chegou / saiu). Comando de teste `/froggydude invasao <status|dia N|agora>`.
      Provar: `./gradlew build`, e no jogo o status mostra o dia (só pra teste).
- [ ] 2. Antes da chegada: nenhum FroggyDude (o keeper não cria, o `/summon` normal continua valendo).
- [ ] 3. Sinais 1-2 dias antes, raros e sempre fora da vista: ribbit longe, passos atrás (param quando
      vira), rastro de sangue, bicho comido (morre longe da vista, sobra o corpo/sangue). Provar no jogo
      com o comando forçando o dia.
- [ ] 4. A chegada: LAN falsa uns segundos antes, "FroggyDude entrou no jogo", nasce a 200-400 blocos
      fora da vista e vem caçando (IA escolhida). Chunk longe descarregado: andar "no papel" até perto.
- [ ] 5. Morreu: some uns minutos (padrão 3 min, LM pode mudar) e volta; o zombar de 3 mortes continua.
- [ ] 6. O cheiro: jogador sumido/escondido por um tempo (Nether, caverna, longe) → no chat "EU QUERO SEU
      CORPO! Sniff sniff sniff... Ah.. eu consigo sentir o cheiro daquele corpo..." e ele volta a saber
      onde está. No máximo de vez em quando.
- [ ] 7. Dragão morto: "NAO! MERDA! Ah... Vá se ferrar" no chat, "saiu do jogo", some de vez.
- [ ] 8. (pequeno) Transformação do nada rara e na hora certa.
- [ ] 9. (pequeno) Menos língua de perto, mais bote.
- [ ] 10. NOTES.md (0.7.16), versão, build, teste no jogo com o comando, jar pro LM.

## Como verificar no fim
- `cd froggydude-mod && ./gradlew build --no-daemon`
- No jogo (Xvfb): `/froggydude invasao dia 1` pra forçar sinais e chegada; matar o dragão com comando
  (`/kill @e[type=ender_dragon]` no End) pra ver o fim.
- O teste de IA de 27/10 (porta, campo aberto, parede) de novo, pra ver que nada piorou.

## Riscos e o que pode dar errado
- "Bicho morre fora da vista": a vista vem do cliente (`PlayerSight`); se o pacote atrasar, pode morrer
  na beirada da tela. Margem: só bicho atrás do jogador e longe (> 24 blocos).
- Nascer a 200-400 blocos no celular: chunk carregando pesa. Começar com a posição "no papel" e só criar
  a entidade quando chegar perto (~64 blocos).
- O cheiro + "sempre sabe onde tu tá" parece repetido: o "sempre sabe" vale pra chegada; no meio da
  caçada ele pode perder o jogador e o cheiro é como ele acha de novo (é assim que eu entendi o LM).

## Fora do escopo
- O ritmo de dias (brinca → "Ó se eu quisesse" → perseguição), o "piora a cada morte", multiplayer.
- A tela de alerta de emergência no começo do mundo (o LM escolheu os sinais em vez dela).
- BecomeLM (depois).

## Perguntas em aberto
- Quanto tempo ele some depois de te matar? (padrão 3 min)
- Em inglês (`en_us`), as falas do chat ficam em português ou traduzo?
- Outros "momentos certos" pro chat, além do cheiro e do fim? (o LM pode mandar mais frases)

## Registro da implementação
(preenchido pelo /implementar)
