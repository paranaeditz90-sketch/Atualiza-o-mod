# Froggydude - Parte 1: combate consertado

Mod para Minecraft 1.20.1 / Forge. Estamos indo por partes:

- Parte 1 (este zip): combate. Língua mirando certo e com o tamanho certo,
  pulos calculados (só pula se acerta, sem dano de queda), jogador preso de
  verdade (sem zoom), IA sem indecisão.
- Parte 2 (próxima): fase 2 (contorção, grito, corrida) e animações.
- Parte 3: skins reais e as variantes por tipo de dano.

Nesta parte a fase 2 está DESLIGADA de propósito (FroggyFrenzyGoal existe
mas não é registrada), pra testar só o combate.

O que testar agora, e me contar:
1) a língua sai na direção de quem ele está atacando? chega até você?
2) ele só pula quando vai te acertar? cai sem tomar dano?
3) quando o pulo ou a língua de captura te acerta, você fica sem poder se
   mexer por uns 2 segundos, com a tela tremendo e inclinada?
4) ele ainda fica indeciso, parado sem atacar?

Compilado e testado num servidor e num cliente de teste: ele nasce,
renderiza com a skin real, troca o sangue conforme a vida e revida quando
apanha. O que ainda não foi testado com jogador de verdade: a língua, os
pulos e o tremor de tela.

## O que tem no pacote
Código (src/main/java/com/froggydude):
- entity/FroggydudeEntity.java - entidade (agora implementa GeoEntity)
- entity/FroggyState.java - estados e duração de cada um
- entity/ai/ - FroggyCombatGoal (escolhe o ataque), FroggyFeedGoal (devora
  mobs com pouca vida), FroggyFrenzyGoal (contorção + frenesi)
- client/ - FroggydudeModel, FroggydudeRenderer, ClientModEvents (registra
  o renderer) e ClientForgeEvents (tremor de câmera)
- network/ - ModNetwork, FroggyShakePacket, ClientShakeHandler
- init/ - registro da entidade e dos atributos

Recursos (src/main/resources/assets/froggydude):
- geo/froggydude.geo.json - modelo no formato de jogador (skin 64x64)
- animations/froggydude.animation.json - 16 animações (rascunho, veja abaixo)
- textures/entity/froggydude_<variante>_0..3.png - skins reais do FroggyDude
  (histórico dele no laby.net / NameMC), escolha provisória. Veja "Texturas"
- lang/ - nome da entidade

tools/prepare_skins.py - prepara as skins do NameMC (veja "Texturas")

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

| Estado          | Animação        | Duração | Impacto        |
|-----------------|-----------------|---------|----------------|
| BITE            | bite            | 0,6 s   | 0,3 s          |
| TONGUE_WHIP     | tongue_whip     | 0,7 s   | 0,4 s          |
| TONGUE_GRAB     | tongue_grab     | 0,8 s   | 0,5 s          |
| TONGUE_CAPTURE  | tongue_capture  | 1,4 s   | 0,6 s e 1,0 s  |
| JUMP_PIN        | jump_pin        | 1,0 s   | 0,7 s          |
| HIGH_JUMP       | high_jump       | 1,6 s   | 1,1 s          |
| FEEDING         | feed (loop)     | 4,0 s   | -              |
| CONTORTING      | contort         | 2,5 s   | -              |
| frenesi ativo   | run_quad (loop) | -       | correndo       |
| cansado         | tired (loop)    | -       | -              |
| parado / andando| idle / walk     | -       | -              |

A língua é um osso separado ("tongue") com um controlador próprio: fica
escondida (escala ~0) e as animações tongue_*_ext esticam ela pra frente.
Ela tem comprimento fixo (8 blocos na chibatada e no agarrão, 12 na
captura), não se ajusta à distância do alvo.

## Ossos do modelo (nomes que as animações usam)
root, waist (cintura: inclina o corpo), body, head, tongue, right_arm,
left_arm, right_leg, left_leg. Se você refizer o modelo no Blockbench,
mantenha esses nomes (ou renomeie também nas animações).

## As animações são um rascunho
Eu montei as poses e os tempos por código, sem ver o resultado no jogo.
Espero que estejam no lugar certo, mas é provável que precise ajustar:
- se alguma pose sair invertida (ex.: inclinando pra trás), troque o
  sinal da rotação no Blockbench;
- a corrida de quatro (run_quad) e a contorção (contort) são as que mais
  precisam de acabamento;
- abra o .geo.json e o .animation.json no Blockbench (plugin GeckoLib)
  para refinar. Mantenha os nomes das animações.

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

## Tremor de tela
Já está ligado: JUMP_PIN, HIGH_JUMP e TONGUE_CAPTURE mandam um tremor
para o jogador atingido (FroggyCombatGoal -> ModNetwork.sendShake). A
intensidade e a duração estão nessas chamadas.

## O que ainda não existe
- A forma final com asas e voo (aparece no vídeo perto do fim). Precisa
  de outro modelo/animações e de um gatilho na IA.
- Som próprio (por enquanto só o som de comer do Minecraft).
- Loot table, spawn egg.
- Os números de dano, alcance, duração e cooldown são um ponto de
  partida. Ajuste em FroggyState e FroggyCombatGoal testando no jogo.
