# Froggydude - Parte 2: corrida, bote e sons

Mod para Minecraft 1.20.1 / Forge + GeckoLib. O plano completo, por partes,
está em `../PLANO.md`.

- Parte 1 ✅ combate básico.
- Parte 2 ✅ (esta) corrida de salto de sapo, bote que derruba e monta na
  vítima, câmera, língua, cabeça que encara, sangue e sons.

A fase 2 (FroggyFrenzyGoal: contorção, grito, frenesi) continua DESLIGADA.

O que testar agora:
1) De longe ele vem aos saltos de sapo (agacha, voa deitado, cai nas mãos)?
2) Quando o bote te pega, tu cai no chão e a câmera fica presa na cara dele?
3) Uma pancada forte nele (machado/espada carregada) enquanto ele está em
   cima te solta?
4) A língua chega em ti (no peito), sem passar por cima da cabeça?
5) Com o pacote de voz ativado, as falas dele tocam nos momentos certos?

## O que tem no pacote
Código (src/main/java/com/froggydude):
- entity/FroggydudeEntity.java - entidade (agora implementa GeoEntity)
- entity/FroggyState.java - estados e duração de cada um
- entity/PinTracker.java - jogador derrubado e preso (lado do servidor)
- entity/ai/ - FroggyCombatGoal (persegue aos saltos, escolhe o ataque,
  monta na vítima), FroggyFeedGoal (devora mobs com pouca vida),
  FroggyFrenzyGoal (contorção + frenesi, desligada)
- client/ - FroggydudeModel (textura, cabeça encarando, língua),
  FroggydudeRenderer, ClientModEvents (registra o renderer) e
  ClientForgeEvents (tremor, derrubado no chão, câmera presa no rosto dele)
- network/ - ModNetwork, FroggyShakePacket, ClientShakeHandler
- event/ModForgeEvents.java - tick do jogador preso no servidor
- init/ - registro da entidade, atributos e sons

Recursos (src/main/resources/assets/froggydude):
- geo/froggydude.geo.json - modelo no formato de jogador (skin 64x64)
- animations/froggydude.animation.json - animações (corrida, salto, bote e
  montado são geradas por tools/anim/build_anims.py, veja abaixo)
- sounds.json - 12 sons, com sons de reserva do Minecraft (a voz original
  vem no pacote de recursos FroggyDude-Voz.zip, que não fica no GitHub)
- textures/entity/froggydude_<variante>_0..3.png - skins reais do FroggyDude
  (histórico dele no laby.net / NameMC), escolha provisória. Veja "Texturas"
- lang/ - nome da entidade e legendas dos sons

tools/prepare_skins.py - prepara as skins do NameMC (veja "Texturas")
tools/anim/build_anims.py - gera as animações de locomoção e bote

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

| Estado          | Animação        | Duração | Impacto                         |
|-----------------|-----------------|---------|---------------------------------|
| BITE            | bite            | 0,6 s   | 0,3 s                           |
| TONGUE_WHIP     | tongue_whip     | 0,7 s   | 0,4 s                           |
| TONGUE_GRAB     | tongue_grab     | 0,8 s   | 0,5 s                           |
| TONGUE_CAPTURE  | tongue_capture  | 1,4 s   | 0,6 s e 1,0 s                   |
| JUMP_PIN        | jump_pin        | 1,0 s   | impulso 0,2 s, pouso ~0,75 s    |
| HIGH_JUMP       | high_jump       | 1,6 s   | impulso 0,3 s, pouso ~1,1 s     |
| LEAP            | leap            | 1,2 s   | impulso 0,25 s, pouso ~0,85 s   |
| PIN_HOLD        | pin_hold (loop) | 2,2-2,5 s | mordida a cada 0,7 s          |
| FEEDING         | feed (loop)     | 4,0 s   | -                               |
| CONTORTING      | contort         | 2,5 s   | -                               |
| perseguindo     | run (loop)      | -       | galope de quatro, de perto      |
| cansado         | tired (loop)    | -       | -                               |
| parado / andando| idle / walk     | -       | -                               |

Andando/correndo é decidido no servidor pela velocidade real, com folga
(liga rápido, desliga devagar), e vai sincronizado. Por isso a animação não
pisca mais entre andar e correr.

A língua é um osso separado ("tongue"), filho da cabeça, com controlador
próprio: fica escondida (escala ~0) e as animações tongue_*_ext esticam
ela. O comprimento é a distância da boca até o peito do alvo, e ela mira
porque a cabeça encara o alvo.

## Ossos do modelo (nomes que as animações usam)
root, waist (cintura: inclina o corpo), body, head, tongue, right_arm,
left_arm, right_leg, left_leg. Se você refizer o modelo no Blockbench,
mantenha esses nomes (ou renomeie também nas animações).

## Animações

Corrida, salto de sapo, bote, salto alto e montado são geradas por
`tools/anim/build_anims.py` (rode `python tools/anim/build_anims.py`). As
poses foram copiadas quadro a quadro do vídeo contra o Parallax (FULL MOVIE
15:38). As outras animações (língua, comer, morder...) continuam as antigas.

Convenções medidas no jogo (modelo de lado):
- waist X+ inclina o tronco pra frente; head X+ olha pra baixo;
- braço X- levanta pra frente; perna X+ vai pra trás;
- root X+ tomba o corpo todo; root position Y- abaixa.

Montado (PIN_HOLD), o modelo não soma o "olhar pro alvo" na cabeça: a pose
já aponta a cara pro rosto de quem está embaixo.

### Testar animação sem lutar (NBT de teste)
- `{DebugAnim:"leap"}` toca uma animação em loop. Com o
  `python tools/anim/build_anims.py --debug`, nomes `dbg_<anim>_<tempo>`
  dão a pose congelada (arquivo local, fica fora do git).
- `{DebugHunt:1b}` faz ele caçar o porco mais perto (pra ver de lado).
- `{DebugAttack:"JUMP_PIN"}` faz ele só usar esse ataque.
- `./gradlew runClient -PquickWorld=<mundo> -PfroggyDebug=1` liga logs
  `[FROGGYDEBUG]` (pouso dos pulos, jogador preso).

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

## Câmera e jogador preso
- Tremor: sacode em três eixos com ruído suave e some aos poucos. Quase
  sem giro de tela (o "vira pro lado e volta" da Parte 1 foi removido).
- Língua de captura: segura o jogador um instante (controles travados).
- Bote/salto alto que acerta: o jogador cai deitado (pose de rastejar,
  olho rente ao chão), controles travados, e a câmera fica presa no rosto
  do Froggy, que monta a 2 blocos e morde. Uma pancada de 5+ de dano nele
  solta. Solta também se ele morrer ou se afastar.
- O dano das mordidas montado não empurra (senão a vítima escorregava pra
  fora e o "preso" acabava antes da hora).

## Sons
12 eventos em `sounds.json`: ambient (ribbit), hunt (quando te acha),
hurt, love_pain ("I love pain", às vezes no lugar do grunhido), death,
scream (fase 2), bite, eat, tongue (estalo), taste, leap e pin (montado).
O mod usa sons do Minecraft como reserva. O pacote de recursos
`FroggyDude-Voz.zip` (fora do GitHub, porque o repositório é público)
troca todos pela voz original dele, recortada dos vídeos.

## O que ainda não existe
Veja `../PLANO.md`: skins por evento, resto das animações, habilidades
(parede, água, braço arrancado, roubar arma com a língua), inteligência,
terror e anti-trapaça. Spawn egg e loot table também não existem.
Os números de dano, alcance, duração e cooldown ainda são ponto de partida.
