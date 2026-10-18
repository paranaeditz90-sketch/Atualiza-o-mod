# Froggydude - v0.4.0: gore, o golpe final (a zebra) e sem anti-trapaça

Mod para Minecraft 1.20.1 / Forge + GeckoLib. O plano completo, por partes,
está em `../PLANO.md`.

- Parte 1 ✅ combate básico.
- Parte 2 ✅ corrida, bote que derruba, câmera, língua, sons.
- Parte 3 ✅ combate completo (esmagamento, pulo do céu, braço, fase 2...).
- Parte 4 ✅ (esta) config, mundo `-dev`, comando `/froggydude`, um FroggyDude
  por mundo, manhunt (speedrun ou sobrevivência), falas na ordem dos vídeos,
  e já um pedaço da Parte 8: o cérebro que aprende.
- v0.4.0: o vídeo do LM (0.1x, analisado em 60 fps): gore em 5 níveis, sangue
  em todos os mobs, o golpe final da zebra, a língua chata, o pulo foguete, e
  o anti-trapaça (com a invasão do 911) foi REMOVIDO inteiro.

O que testar agora (v0.4.0):
1) Primeira vez que o jogo abre: a tela de aviso escolhe o gore (Desligado,
   Leve, Médio, Pesado, Extremo); o Extremo tem tela própria ("Atenção ⚠").
   Depois muda em Mods > Froggydude > Configurar.
2) Bate num porco com espada: sangra (chão, a tua skin, a tela). Esqueleto,
   slime e golem não sangram; creeper verde, enderman roxo, zumbi podre.
3) O golpe dele que te mataria vira a cutscene: tu cai de costas e ele arranca
   o braço esquerdo, o direito e a cabeça (câmera de cinema, igual à zebra).
   `/froggydude golpefinal` testa na hora (em sobrevivência).
4) Extremo: o sangue jorra em jato; o teu corpo fica e ele come, joga pro alto
   e sacode na boca - tu vê isso antes da tela de morte.
5) A língua é uma fatia chata rosa (boca aberta e braços em V antes).
6) Pulo do céu: braços em V 0,3 s, agacha num piscar e sai como foguete.
7) Ele come um golem de ferro: a boca fica esbranquiçada (cada bicho mancha a
   boca com a cor do sangue dele, e some em ~2 min).
8) Sangue na tua skin escorre e seca; cada gota mancha onde bateu.
9) Comer = "flexão" (vídeos "Taste testing Minecraft mobs" e "never kill my
   dog"): o bicho cai de lado e ele deita de bruços por cima, mãos plantadas,
   e a cabeça desce mordendo a cada 0,3 s (desce rápido, sobe devagar). Igual
   quando te derruba e te come vivo, e no teu corpo no Extremo. Golem/esqueleto
   soltam lasca da cor deles, não sangue.
10) Correndo de 4 levanta poeira clara nos pés a cada passada.
11) Cutscene: a câmera ficou mais longe nos planos de corpo inteiro (no vídeo
   ele ocupa metade da tela) e o close é na frente-direita dele, com a tua
   cabeça saindo da boca pro lado (não tampa mais a cara dele).

Da v0.3.7 - o faro: esconde-te numa casa sem ele ver (ele fareja o rastro e
arromba); foge e dobra a esquina (ele segue o cheiro); na água o rastro some.

Da v0.3.6:
1) A cabeça não gira mais sozinha (andando, parado, caçando).
2) Língua: meio coração. Esmagamento: 24 socos com "tai!" em cada um, mais
   fraco por soco e mais longo (6,6 s).
3) Fase 2: contorção com o som original (ossos + gemido) e o grito ao levantar.
   Bate nele enquanto contorce: ele não sai do lugar nem gira.
5) Tranca-te numa casa: ele derruba a parede a tiros de língua (vidro e terra
   num tiro, tábua em dois). Em cima do teu telhado, come o telhado.
6) `/froggydude manhunt speedrun`: faz um portal do Nether e te afasta dele -
   ele vai lá e come a obsidiana; aldeões perto de ti viram comida.
7) Lava, fogo, explosão, cacto: onde ele se machucou, ele desvia depois.

## O que tem no pacote
Código (src/main/java/com/froggydude):
- entity/FroggydudeEntity.java - entidade (agora implementa GeoEntity)
- entity/FroggyState.java - estados e duração de cada um
- entity/PinTracker.java - jogador derrubado e preso (lado do servidor)
- entity/PacedAnimationController.java - controlador do GeckoLib que aceita
  velocidade mudando o tempo todo (o galope acelera e freia liso)
- player/ArmLoss.java - braço arrancado: vida máxima, mão secundária, sangramento
- entity/ai/ - FroggyCombatGoal (persegue aos saltos, escolhe o ataque,
  monta na vítima), FroggyFeedGoal (devora mobs com pouca vida, em "flexão"; o bicho fica
  caído e parado - entity/FeedLock e client/FeedVictimRender),
  FroggyFrenzyGoal (contorção + frenesi, desligada)
- client/ - FroggydudeModel (textura, cabeça encarando, comprimento da língua),
  FroggydudeRenderer (língua em fatia e o pedaço na boca), MouthStainLayer
  (a boca suja do que ele comeu), PlayerCorpseRenderer (o corpo do Extremo),
  ArmlessRendering (jogador sem braço: modelo, armadura, toco, 1ª pessoa),
  ClientModEvents e ClientForgeEvents (tremor, derrubado, câmera presa)
- network/ - ModNetwork, FroggyShakePacket, ClientShakeHandler,
  ArmStatePacket e ClientArmState (quem está sem braço)
- event/ModForgeEvents.java - tick do jogador preso/sem braço, tecla F,
  braço que volta na morte (e não volta saindo do End)
- event/FroggyServerEvents.java - Parte 4: um por mundo, manhunt, comando,
  aviso de mundo -dev
- event/GoreEvents.java - sangue de qualquer dano em qualquer mob (cor por tipo)
- event/FinisherEvents.java - o golpe que mataria vira a cutscene; a vítima não
  leva outro dano nem age durante ela
- entity/Finisher.java - a tabela de tempos e a geometria da cutscene (servidor
  e cliente contam do mesmo tick); entity/ai/FroggyFinisherGoal.java - o lado
  do servidor; client/cutscene/ - a câmera de cinema, o corpo deitado, os tocos
- entity/PlayerCorpse.java + entity/ai/FroggyCorpseGoal.java - Extremo: o corpo
  que fica, e ele comendo/jogando/sacudindo
- config/GoreLevel.java, config/FroggyClientConfig.java (cada jogador escolhe
  o seu gore), client/screen/ (aviso, aviso do Extremo, configurações)
- client/gore/ - BloodStains (sangue na skin), BloodStreams (o jato do Extremo),
  ScreenBlood (sangue na tela)
- entity/voice/ - VoiceLine (cada fala), VoiceSituation (situação -> sequência
  dos vídeos), FroggyVoice (em que fala de cada situação ele está)
- brain/ - o cérebro que aprende (FroggyBrain, PlayerMemory, Strategy, Style, Engagement)
- world/ - FroggyWorldData (data/froggydude.dat no save), FroggyKeeper (um por
  mundo, onde ele aparece), Manhunt e ManhuntMode
- entity/FroggyNavigation.java e FroggyNodeEvaluator.java - caminho de aranha,
  longe (80 blocos), abre porta, desvia de onde já se machucou
- brain/DangerMemory.java - onde ele se machucou (salvo no mundo, esquece em ~1 h)
- world/Sabotage.java e entity/ai/FroggySabotageGoal.java - o portal do Nether
  que ele vai comer no speedrun
- brain/Scent.java - o rastro de cheiro de cada jogador (só na memória)
- entity/ai/FroggyTrackGoal.java - o faro: segue o rastro e faz a varredura
- command/FroggyCommand.java - `/froggydude`
- config/FroggyConfig.java - `config/froggydude-common.toml`
- init/ - registro da entidade, atributos e sons

Recursos (src/main/resources/assets/froggydude):
- geo/froggydude.geo.json - modelo no formato de jogador (skin 64x64)
- animations/froggydude.animation.json - animações (corrida, salto, bote e
  montado são geradas por tools/anim/build_anims.py, veja abaixo)
- sounds.json - sons de categoria (com o "tai", a contorção, o farejar e o
  vidro) + 39 falas (froggydude.voice.*), com sons
  de reserva do Minecraft (a voz original vem no pacote de recursos
  FroggyDude-Voz.zip, que não fica no GitHub)
- textures/entity/arm_stump.png - toco do braço arrancado
- textures/entity/froggydude_<variante>_0..3.png - skins reais do FroggyDude
  (histórico dele no laby.net / NameMC), escolha provisória. Veja "Texturas"
- lang/ - nome da entidade, legendas dos sons (cada fala tem a sua), textos
  do manhunt e das telas do gore
Dados (src/main/resources/data): o tipo de dano `froggydude:devoured` (o fim do
golpe final: ignora totem, armadura e invencibilidade) e as tags dele.

tools/prepare_skins.py - prepara as skins do NameMC (veja "Texturas")
tools/anim/build_anims.py - gera as animações de locomoção, bote, esmagamento,
  pulo do céu, braço, contorção, língua, o golpe final e o corpo
tools/make_gore_textures.py - respingos da tela, poça, jato, nuvem na água e a
  mancha da boca

## Gerar o .jar
O projeto agora está completo nesta pasta (build.gradle, gradlew, mods.toml).
Não precisa mais do MDK nem de zip.

- **Pelo GitHub (celular):** todo push que mexe em `froggydude-mod/` compila
  sozinho. Abra a aba **Actions**, clique na última execução verde e baixe
  **froggydude-mods** em *Artifacts*. Dentro vêm os dois jars: o Froggydude
  e o GeckoLib 4.4.9. Também dá pra rodar na mão em Actions > "Compilar
  Froggydude (.jar)" > *Run workflow*.
- **No PC:** `./gradlew build` (Java 17). O jar sai em `build/libs/`.
- **Testar sem abrir o launcher:** `./gradlew runClient` ou `./gradlew runServer`.
  `./gradlew runClient -PquickWorld=nome_do_mundo` já entra direto no mundo.

## Testar no Zalith Launcher
1) Instale uma versão 1.20.1 com Forge (de preferência a mesma 47.x do
   MDK). Use uma versão separada da do FNaF, com isolamento de versão,
   pra os mods não se misturarem.
2) Na pasta mods dessa versão, coloque DOIS jars: o do Froggydude
   (build/libs/) e o do GeckoLib 4.4.9 para Forge 1.20.1 (Modrinth ou
   CurseForge). Sem o GeckoLib o jogo não abre.
3) Abra o jogo, crie um mundo criativo com cheats e use:
   /summon froggydude:froggydude
   Depois /gamemode survival pra ele te atacar.
4) Se crashar, veja logs/latest.log e a pasta crash-reports dentro da
   pasta da versão (o gerenciador de arquivos do Zalith abre).

## Como estado, animação e ataque se conectam
O estado (FroggyState) é sincronizado servidor -> cliente. O cliente toca
a animação do estado. O servidor aplica o dano no tick de impacto. Por
isso a duração da animação = duração do estado, e o golpe cai no frame
certo. Se mudar um, mude o outro.

| Estado          | Animação          | Duração   | Impacto                              |
|-----------------|-------------------|-----------|--------------------------------------|
| BITE            | bite              | 0,6 s     | 0,3 s                                |
| TONGUE_WHIP     | tongue_whip       | 0,7 s     | 0,4 s                                |
| TONGUE_GRAB     | tongue_grab       | 0,8 s     | 0,5 s                                |
| TONGUE_CAPTURE  | tongue_capture    | 1,4 s     | 0,6 s puxa, recolhe até 1,0 s        |
| JUMP_PIN        | jump_pin          | 1,0 s     | impulso 0,2 s, pouso ~0,75 s         |
| HIGH_JUMP       | high_jump         | 1,6 s     | impulso 0,3 s, pouso ~1,1 s          |
| SKY_DROP        | sky_drop          | até pousar| impulso 0,4 s, topo ~1,25 s, ~2,2 s  |
| PIN_HOLD        | pin_hold (1 s)    | 5 s       | mordida aos 0,5 s de cada segundo    |
| SMASH           | smash             | 6,6 s     | soco a cada 0,25 s (24x), "tai!" em cada |
| ARM_RIP         | arm_rip           | 2,0 s     | crava os dentes 0,4 s, sacode, arranca aos 1,1 s, solta aos 1,6 s |
| ARM_EAT         | arm_eat           | 4,5 s     | mordida + tranco a cada 0,75 s, engole aos 4 s |
| FEEDING         | feed (1 s, loop)  | 6,0 s     | mergulha e morde 0,34 s, arranca 0,52 s |
| FINISHER        | finisher / finisher_r | 11,6 s (8,6 sem o braço esq.) | arranca 1,6 s / 4,6 s / 8,3 s, engole 11,3 s |
| CORPSE_EAT      | feed (loop)       | 2,0 s     | mordida no corpo aos 0,35 s de cada volta |
| CORPSE_TOSS     | corpse_toss       | 1,2 s     | solta o corpo pro alto aos 0,4 s     |
| CORPSE_SHAKE    | corpse_shake      | 1,7 s     | o corpo na boca sacudindo            |
| CONTORTING      | contort + contort_hold | 1,0 s + 2 a 4 s | tronco chicoteia; encurvado, cabeça tombada |
| ROAR            | roar              | 0,9 s     | grito aos 0,2 s, depois o ataque feroz |
| perseguindo     | run / run_frenzy  | -         | galope de 4 (fase 2: mais esticado e rápido); poeira e barulho a cada patada |
| parou caçando   | crouch_idle       | -         | fica agachado (só levanta após 1,5 s)|
| escalando       | climb (loop)      | -         | parede/torre acima, igual aranha     |
| cansado         | tired (loop)      | -         | -                                    |
| parado / andando| idle / walk       | -         | -                                    |

Em pé ou de quatro quem decide é a INTENÇÃO (no servidor, sincronizado): com
uma vítima na mira ele anda sempre de quatro, rápido ou devagar; em pé só
passeando sem alvo. Antes era pela velocidade, que no celular oscila, e ele
ficava trocando de pose várias vezes por segundo.

A língua é um osso separado ("tongue"), filho da cabeça, com controlador
próprio: fica escondida (escala ~0) e as animações tongue_*_ext esticam
ela. O osso não tem cubo: o renderer desenha uma fatia chata
(da largura da boca, rosa-claro na boca e mais escura na ponta redonda). O comprimento é a distância da boca até o peito do alvo, e
ela mira porque a cabeça encara o alvo.

A velocidade da animação de correr/andar acompanha a velocidade real dele
(PacedAnimationController). Caçando pra matar ele corre a ~5,4 m/s; seguindo
de longe pra apavorar, ~2,7 m/s; na fase 2, ~8 m/s.

## Ossos do modelo (nomes que as animações usam)
root, waist (cintura: inclina o corpo), body, head, tongue, right_arm,
left_arm, right_leg, left_leg. Se você refizer o modelo no Blockbench,
mantenha esses nomes (ou renomeie também nas animações).

## Animações

Corrida de 4 (galope de cachorro, alto, membros esticados - vs AJ 0:34),
corrida da fase 2 (o mesmo galope, mais esticado e rápido - vs AJ 7:45),
parado de 4, escalada, salto de sapo, bote, salto alto, montado,
esmagamento, pulo do céu, arrancar e comer o braço (estilo "Eating a Zebra"),
contorção (dobrar, curvado, rugido), língua (braços erguidos) e comer mob são
geradas por `tools/anim/build_anims.py` (rode `python tools/anim/build_anims.py`).
As poses foram copiadas quadro a quadro dos vídeos (Parallax, FULL MOVIE 15:38;
vs AJTHEBOLD; vs Grox; "Eu sou o mod de terror"; "Eating a Zebra"). Ficam
como eram só idle, walk, tired, bite e a língua esticando (tongue_*_ext).

Convenções medidas no jogo (modelo de lado):
- waist X+ inclina o tronco pra frente; head X+ olha pra baixo;
- braço X- levanta pra frente; perna X+ vai pra trás;
- root X+ tomba o corpo todo; root position Y- abaixa.

A cabeça só soma o "olhar pro alvo" quando ele está EM PÉ (parado ou
andando), com limite de 50 graus pro lado e 30-35 pra cima/baixo; mirando a
língua ou mordendo, só o pra cima/baixo. De quatro, curvado, montado ou
comendo, a pose já aponta a cara pro lugar certo: somar o olhar ali fazia a
cabeça tombar de lado e pra cima/baixo do nada (o bug dos "tiques" da 0.3.1).
O RandomLookAroundGoal (olhar pros lados sem motivo) também saiu.

### Testar animação sem lutar (NBT de teste)
- `{DebugAnim:"run"}` toca uma animação em loop. Com o
  `python tools/anim/build_anims.py --debug`, nomes `dbg_<anim>_<tempo>`
  dão a pose congelada (arquivo local, fica fora do git).
- `{DebugHunt:1b}` faz ele caçar o porco mais perto (pra ver de lado).
- `{DebugAttack:"JUMP_PIN"}` faz ele só usar esse ataque. Também valem
  "SKY_DROP", "SMASH" e "PIN_HOLD" (bote que sempre esmaga / sempre devora)
  e "ARM_RIP" (língua de captura que sempre arranca o braço).
- `{DebugAnim:"tongue_whip"}` (ou grab/capture) estica a língua em loop.
- `{DebugStalk:1b}` faz ele sempre começar só olhando (modo apavorar).
- `{DebugPhase2In:60}` entra na fase 2 depois de 60 ticks (pra filmar a contorção do começo).
- `{DebugHeldArm:1b}` põe o braço do jogador mais perto na boca dele;
  `{DebugArmless:1b}` arranca o braço do jogador mais perto (sobrevivência).
- `./gradlew runClient -PquickWorld=<mundo> -PfroggyDebug=1` liga logs
  `[FROGGYDEBUG]` (pouso dos pulos, jogador preso, cada fala que ele diz).

## Texturas (skins reais do FroggyDude)
O modelo é o de jogador com braços de 4 px (skin "clássica"), 64x64.
Cada variante tem 4 estágios, escolhidos pela vida: acima de 75% = 0,
50-75% = 1, 25-50% = 2, abaixo de 25% = 3. As skins vieram do histórico
dele (45 skins, ver `pesquisa/SKINS.md`). Escolha provisória, até a parte
de skins por evento:

| Variante | Quando | 0 | 1 | 2 | 3 |
|---|---|---|---|---|---|
| normal  | golpe comum      | oficial limpa (2023-03-26) | sangue leve (2026-05-10) | sangue médio (2026-07-03) | sangue pesado (2026-07-27) |
| burned  | fogo / lava      | cara em chamas (2026-06-17) | corpo em chamas (2026-07-17) | carbonizado em brasa (2026-07-17) | carbonizado + sangue (2026-07-25) |
| fed     | acabou de comer  | sangue na boca (2026-09-04) | peito e coroa (2026-07-15) | frente toda (2026-07-10) | banho de sangue (2026-09-06) |
| smashed | queda / explosão | escuro (2026-08-05) | boca preta (2026-08-09) | destroçado (2026-06-09) | destroçado (2026-06-09) |

Para trocar uma variante, rode da mais limpa para a mais suja:

    pip install pillow
    python tools/prepare_skins.py --variant burned a.png b.png c.png d.png

O script também pinta de rosa a região da língua (pixels 56..61 x 16..18),
que é vazia nas skins normais. Sem isso a língua fica invisível.

**Boca escancarada:** cada skin tem uma versão `_open.png` com o rosto de
grito (a skin do NameMC dele, 01/10/2026; a de boca com sangue, 31/08; e a
de boca preta, 09/08 pras carbonizadas/destroçadas). Ele abre a boca pra
soltar a língua, gritar, morder, mastigar e falar, e fecha logo depois; na
fase 2 fica com ela aberta o tempo todo. Pra refazer depois de trocar uma
skin: `python tools/make_mouth_open.py`.

**Sangue grudado:** cada mordida, braço e vítima devorada suja ele mais
(até 3,5). A skin usa o pior entre isso e a vida perdida. Sai devagar com o
tempo (~6 min por estágio) e rápido na água ou na chuva.

## Braço arrancado
- A língua de captura puxa a vítima de volta; metade das vezes ele arranca o
  braço esquerdo em vez de morder. No "devorar", 1 em 3 vezes também.
- Sem braço (player/ArmLoss.java): -3 corações de vida máxima, sem mão
  secundária (vai pro inventário) e sem tecla F, sangramento contínuo
  (partículas + 1 de vida a cada 8 s até sobrar 3 corações). O faro dele acha
  quem está sangrando a 64 blocos.
- O braço volta só na morte (a marca fica em getPersistentData, que o Forge não
  copia quando o jogador morre; saindo do End a gente copia).
- Cliente: o braço some do modelo e da armadura (camada de armadura trocada por
  reflexão), aparece um toco no ombro, e a 1ª pessoa não desenha o braço.

## Sangue (partícula própria + poças)
- `blood` (client/BloodParticle): cubinho de sangue sólido, vermelho vivo, de
  tamanhos variados (igual ao "Eating a Zebra"), que sai com a velocidade do
  jato, cai com peso e fica um tempo no chão. Não tem névoa nem pó vermelho.
  Texturas em `textures/particle/blood_*.png` (`tools/make_blood_textures.py`).
  Some se passar colado na câmera (não tampa a tela de quem está preso).
- Poças (client/ClientBloodFx): mancha no chão que vai se espalhando; mais
  sangue no mesmo lugar engrossa a poça (até 2,8 blocos). Somem em ~3 min.
  Não aparecem na água. Quem perdeu o braço deixa rastro.
- O servidor manda um pacote por jato/poça (entity/BloodFx, rede id 2).
- Em quem está preso embaixo, o jato voa por cima do Froggy, não na câmera.

- `dust_puff` (client/DustPuffParticle): a nuvem de poeira do galope. Sai das
  patas a cada passada, cresce, sobe devagar e some; pega a cor do chão (grama
  levanta terra). Junto, cada passada toca `froggydude.gallop` e o passo do
  bloco do chão.

## Escalada
Navegação de aranha (WallClimberNavigation): encostou numa parede indo atrás
de alguém, ele sobe de quatro (0,3 bloco/tick; 0,42 na fase 2), seja qual for
a altura. Pendurado na parede só morde (fica grudado enquanto morde). Não
toma dano de queda se soltar da parede.

## Câmera e jogador preso
- Tremor: sacode em três eixos com ruído suave e some aos poucos. Quase
  sem giro de tela (o "vira pro lado e volta" da Parte 1 foi removido).
- Língua de captura: segura o jogador um instante (controles travados).
- Bote/salto alto que acerta: o jogador cai deitado (pose de rastejar,
  olho rente ao chão), controles travados, e a câmera fica presa no rosto
  do Froggy. Devorando, ele monta a 1,85 bloco e morde; esmagando, fica em
  pé a 1,15 e a câmera olha pra cima. Uma pancada de 5+ de dano nele
  solta. Solta também se ele morrer ou se afastar.
- O dano das mordidas montado não empurra (senão a vítima escorregava pra
  fora e o "preso" acabava antes da hora).

## Sons e falas
19 eventos de categoria em `sounds.json`: ambient (ribbit), hunt, hurt,
love_pain, death, scream (contorção da fase 2), pain (gemidos na fase 2), bite,
eat, chew (mastigando), tongue (estalo), taste, leap, pin, smash (soco),
sky_land (baque do pulo do céu), rip (braço arrancado), crack (ossos) e gallop.

E 38 falas, cada uma com o seu som (`froggydude.voice.<clipe>`), que o mod
escolhe NA ORDEM DOS VÍDEOS (entity/voice/VoiceSituation, com o tempo de cada
fala nas legendas dos vídeos do arco da raiva):
- avistou: "Hey, humans!" (só a 1ª vez), "I'm hungry", "I wanna eat you", "Come
  here, I want to eat you and digest you", "I'm very hungry", "I want your flesh";
- fugindo dele: "Get back here!", "You can run but you can't hide", "You will
  become my food", "I never lose my prey";
- subiu na torre e ele chegou: "You thought you could escape me?"; achou de
  novo quem sumiu: a risadinha;
- língua: pegou "I like the way you taste", desviaram "You're scared of my tongue";
- apanhando: "I love pain" (4 versões); derrubou: "Give me your body!";
  montado: "Stay on the ground", "You are mine", "I'm going to bite your balls";
  com a vítima quase morta: "I'll be the last thing you see";
- comendo: "Mmmm", "That was so delicious", "Very delicious"..., depois "I want
  more food"/"Who's next?"; braço: "I love the taste of humans"; panda: a fala
  do panda; bicho estranho: "Exotic meat"; blaze: "Freaking spicy!";
- morrendo: "No, no, noooo", "NOOOOO".
Não fala por cima de outra (espera o clipe acabar); derrubando alguém ele
corta a fala de antes. A posição de cada sequência fica no save
(data/froggydude.dat) e `/froggydude falas reiniciar` volta tudo pro começo.
Sem o pacote de voz, cada fala toca o som de reserva da categoria; a legenda
(se ligada) mostra o texto da fala.

## Manhunt (Parte 4)
`/froggydude manhunt speedrun`, `/froggydude manhunt sobrevivencia [dias]`,
`/froggydude manhunt iniciar` (o modo do config) e `/froggydude manhunt parar`.
- Ele aparece na tua frente, parado, te encarando durante a vantagem
  (`vantagem_segundos`, padrão 30) com contagem na tela; aí "CORRE".
- Speedrun: matou o Ender Dragon, tu vence. Sobrevivência: conta pelo tempo de
  jogo (dormir pula a noite, mas não pula os dias do manhunt).
- Te pegou: ele vence, come, fica uns segundos e vai embora.
- Morreu no meio: volta depois de `volta_segundos` (padrão 60), fora da tua vista.
- Foi pro Nether/End: depois de ~20 s ele vai atrás. Muito longe (72+ blocos):
  ele reaparece a ~30-44 blocos, sem tu ver (faro).

## Cérebro que aprende (começo da Parte 8)
Pequeno, offline, dentro do mod, e salvo no mundo (data/froggydude.dat).
- Observa o teu estilo: foge correndo, sobe em torre, briga de perto, atira
  de longe, se esconde (com esquecimento: se tu muda, ele acompanha).
- Escolhe COMO começar cada caçada: apavorar (segue de longe e encara), caçar
  direto, ou cercar por trás (vai pra trás de onde tu está olhando). É um
  bandido contextual: pra cada estilo teu, a média de recompensa de cada
  estratégia + bônus de curiosidade (UCB) pro que testou pouco, e otimismo
  pro que nunca testou.
- Recompensa: dano causado, derrubadas, matar (muito), menos o dano que tomou,
  a vítima escapar (sumiu 1 min), ele morrer, e o tempo gasto.
- Ataques: amostragem de Thompson - acertos/erros de cada ataque contra TI;
  quem sempre desvia da língua vê menos língua.
- No manhunt: speedrun - quanto mais perto do dragão (Nether, End, olho do
  End), mais direto ele vem; sobrevivência - de noite e no último dia também.
- `/froggydude cerebro [jogador]` mostra o que ele aprendeu;
  `/froggydude esquecer <jogador>` apaga. Em mundo `-dev`, o chat conta cada
  caçada ("caçada contra LM: cercar por trás, killed, recompensa +10.0").

## Gore (v0.4.0)
Cada jogador escolhe o seu (config/froggydude-client.toml, ou Mods > Froggydude >
Configurar). O servidor sabe o de cada um (pacote GoreLevel) pra decidir o corpo
do Extremo; o resto é só visual de quem está vendo.
- Desligado: nada de sangue; na cutscene a tela escurece em cada mordida.
- Leve: pouco sangue, sem poça nem na tela. Médio: poças, skin e tela (só golpe
  grande). Pesado (padrão): tudo. Extremo: o sangue JORRA em jato contínuo
  (BloodStreams), a skin só limpa na água ou na chuva, e o corpo fica.
- Sangue na skin (BloodStains): textura viva por jogador nas regiões da camada
  de fora da skin; molhado escorre pra baixo e seca escurecendo; cada gota
  (partícula) que bate num jogador mancha o ponto exato (na tua cara em
  primeira pessoa vira uma gota na tela).
- Na tela (ScreenBlood): respingos que escorrem e, preso embaixo dele, a tela
  inteira vai ficando vermelha (a opacidade sobe a cada soco).
- Cor por bicho (entity/BloodType): vermelho, podre (zumbi), verde (creeper,
  aranha), amarelo (abelha), roxo (ender), ciano (warden), tinta (lula); sem
  sangue: esqueleto, slime, golem, blaze, vex, allay, wither, ghast.
- A boca dele fica da cor do que comeu (golem de ferro: branca) e some em ~2 min.

## Golpe final (a cutscene da zebra)
O golpe dele que mataria o jogador (sem totem na mão) vira a cutscene: a vítima
cai de costas onde estava e ele, em pé por cima, arranca o braço esquerdo
(tick 32), o direito (92) e a cabeça (166), engolindo aos 226 (11,6 s; sem o
braço esquerdo começa no 60). Tempos e planos tirados quadro a quadro do vídeo
do LM (77,3-88,8 s em 60 fps). A vítima se debate como a zebra (cabeça virando
de um lado pro outro, trancos, pernas chutando) da mordida até arrancar.
- Câmera (client/cutscene/CinematicCamera): baixinha do lado da cabeça da vítima
  olhando pra cima; corte pro outro lado; close na cara dele com a cabeça na boca
  chicoteando ~5x por segundo. Não atravessa parede. Sem HUD, faixas pretas.
- Servidor e cliente contam do mesmo tick (entity.Finisher), a câmera e a pose
  são interpoladas por quadro: liso a 60 fps.
- Extremo: o corpo (PlayerCorpse) fica; ele come (as pernas somem primeiro),
  joga pro alto e sacode na boca. A vítima assiste uns 6,5 s antes de morrer de
  verdade (um jogador morto sai do mundo em 1 s e o cliente dele para de ver
  as entidades).

## Config (`config/froggydude-common.toml`)
- `manhunt.modo` (SPEEDRUN/SOBREVIVENCIA), `manhunt.dias` (5),
  `manhunt.vantagem_segundos` (30), `manhunt.volta_segundos` (60)
- `froggydude.caca_blocos_por_segundo` (15; um jogador correndo faz ~5,6)

## Um FroggyDude por mundo e o mundo `-dev`
- Só existe um: o último a nascer fica (o `/summon` ou `/froggydude invocar`
  "chama" ele pra perto e o antigo some); um antigo que aparece ao carregar um
  pedaço do mapa some sozinho.
- Mundo com `-dev` no nome (ou na pasta): o cérebro conta no chat o que
  aprende. Ao entrar, aparece um aviso.
- `/froggydude info`: modo do mundo, onde ele está, vida, estado, estratégia
  da caçada e o manhunt. `/froggydude fase2` força a fase 2.
  `/froggydude golpefinal` faz a cutscene em ti (sobrevivência).

## O que ainda não existe
Veja `../PLANO.md`: skins por evento, resto das animações, habilidades (água,
roubar arma com a língua), o resto da inteligência (faro de rastro, rotina da
base, diretor de tensão), o resto do terror, e como ele aparece no mundo sem
comando (Parte 10). Spawn egg e loot table também não existem.
Os números de dano, alcance, duração e cooldown ainda são ponto de partida.
