# BecomeLM mode — notas

Mod **privado** do LM pra gravar vídeos como o FroggyDude (não é pra lançar). Minecraft
1.20.1, Forge 47.4.10, precisa do **GeckoLib 4.4.9** (o mesmo do Froggydude). Pode ficar
junto com o mod Froggydude, um não mexe no outro.

## v0.3.2 — as pancadas com os dois braços e o sangue colado na cara

- **Pancadas (em pé)**: segurando o **atacar** com um bicho na mira, a primeira pancada derruba
  ele de costas (olhando pra cima) e o FroggyDude fica em cima martelando com os **dois braços
  juntos**, 4 por segundo, 1 coração cada (o esmagamento do Froggydude). Soltou, ele levanta e o
  bicho fica mais um pouquinho no chão e levanta rolando. Um toque rápido dá a pancada que derruba
  e mais uma. Matou: deita no monte e come. Só em pé; de 4 continua o bote e o agarrar. Amigo só
  se o servidor deixar PvP.
- **Sem a casca de sangue em volta da cabeça**: a cara suja de comer no monte agora fica colada
  no rosto (antes boiava meio pixel pra fora, na camada do chapéu da skin). Com o mod Froggydude
  junto, atualiza ele pra **0.7.13** (o sangue dele na skin também ficou colado).
- Os amigos precisam da v0.3.2 (a v0.3.1 não conecta com a v0.3.2).

## v0.3.1 — o mortal saindo do monte

- **Mortal**: deitado no monte de pedaços comendo, **pular** = ele sai do monte num mortal
  pra trás (esticado, ~0,45 s no giro, ~1,4 bloco de altura) e pousa de **braços abertos**
  (o "tcharam", ~0,8 s); andar continua só levantando. Medido no How many days (0:47) e no
  10v1 (0:26). Em primeira pessoa a câmera dá a volta junto (céu, de cabeça pra baixo, o
  monte lá embaixo, o horizonte). Os amigos veem o mortal e os braços abertos.
- Os amigos precisam da v0.3.1 (a v0.3.0 não conecta com a v0.3.1).

## v0.3.0 — comer de verdade, o galope com menos fome, a pausa e a finalização (parte 1)

- **Comer os bichos enche a fome** (só quem sangra; esqueleto, slime e golem não):
  - cada mordida que tira sangue (as do esmagamento, a do agarrar, cada sacudida) = **meia
    coxinha**;
  - deitado no monte de pedaços, cada vez que a cara afunda = meia coxinha, **até 4 coxinhas**
    por monte;
  - a finalização: 3 mastigadas de **2 coxinhas** (6 no total) e bastante saturação.
  - Enche a saturação junto (a barra demora a cair depois). **Arrota** quando a barra enche.
- **O galope gasta fome igual um jogador correndo** (antes gastava ~2,5x mais, porque corre
  2,5x mais: a barra sumia em ~1 min galopando). Vale pra fase 2 também.
- **Bug consertado**: pausar o jogo correndo, ele continuava correndo parado atrás do menu.
- **Golpe de finalização, parte 1 (ainda sem o gore)**: raríssimo (**1 em 100** golpes, muda
  em `config/becomelm-common.toml`); pra gravar, `/becomelm finisher` (op) faz o próximo
  golpe virar a cena. Sai no soco, no bote, no agarrar e na queda do céu, em quem fica em pé e
  sangra (gente, aldeão, zumbi; filhote e chefão não). A vítima cai de costas, ele monta nas
  coxas dela e cava a barriga, abre, come 3 vezes e encara a câmera (~11,5 s); no fim ela
  morre e o corpo estoura embaixo dele. Câmera automática em 8 planos, sem HUD. O amigo que é
  a vítima vê dos próprios olhos, sem HUD, e não se mexe. A ferida, os órgãos, o sangue e os
  sons vêm na parte 2.
- Os amigos precisam da v0.3.0 (a v0.2.x não conecta com a v0.3.0).

## v0.2.1 — os ataques, parte 2 (cair do céu, agarrar, o monte de pedaços)

- **Cair do céu em cima**: pulo super alto (segurar o agachar até o "clique" e pular) e, na
  descida, um bicho/jogador até ~8 blocos embaixo puxa a queda pra cima dele. Caiu em cima:
  derruba de costas, senta no peito e **soca com as duas mãos juntas** (como no original,
  4 por segundo, 1 coração cada) e depois **morde** a cara. ~3,6 s. Sem ninguém embaixo,
  pousa normal (a "queda de cara" saiu: fica pro editor de animações).
- **Agarrar na boca** (galope ou fase 2): atacar com o bicho **colado** (até ~2,3 blocos) =
  morde e segura; andando, arrasta (mais devagar); **atacar de novo** = sacode;
  **pular** = arremessa. Solta sozinho depois de 10 s. Mais longe continua sendo o bote.
- **Quem o FroggyDude mata vira pedaços** (~60 cacos com a skin por fora, ficam 90 s) e
  sangue (cor de cada bicho; esqueleto, slime e golem não sangram).
- **Deitar no monte de pedaços**: matou no esmagamento, ele já deita em cima; ou **atacar**
  sem ninguém na mira com um monte na frente. A cara vai ficando **toda vermelha** (~6 s) e
  fica suja ~1,5 min. Andar = levanta.
- **Com o mod Froggydude junto** (precisa da **0.7.12**): o sangue que ele gruda na skin
  (bater, levar golpe, gotas caindo) agora aparece no corpo do FroggyDude também (antes o
  corpo ficava limpo). É igual ao do corpo normal, inclusive no "chapéu" da skin.
- Sons provisórios (do Minecraft) pros socos, mordida e arremesso até o pacote de sons.
- Os amigos precisam da v0.2.1 (a v0.2.0 não conecta com a v0.2.1).

## v0.2.0 — os ataques, parte 1 (derrubar + bote)

Medido nos vídeos dele em 60 qps (study/RELATORIO_1.md no scratchpad) e escolhido pelo LM.

- **Bote com dano**: galopando, **atacar** (o PRI do Zalith / clique esquerdo) com um bicho
  ou jogador na frente, até **7 blocos** = ele se joga em cima (não sai o soco normal).
  Mira: o que está no centro da tela; se não tiver, o mais no meio dentro de ~37° pra cada
  lado. Encostou: **3,5 corações** de dano (golpe seu: a morte conta pra você), empurrão e
  **derruba**. Depois de acertar ele passa por cima (igual ao vídeo do Grox).
- **Derrubado** (mob e amigo): cai de lado em 0,15 s, fica **~1,4 s** no chão e **levanta
  rolando** (0,45 s) — o mesmo tempo dos vídeos. No chão o bicho não anda, não morde, não
  atira e o creeper não explode. O amigo derrubado vê o mundo de lado e não anda nem
  ataca até levantar. Todo mundo que está vendo vê cair e levantar.
- Amigo só leva o bote se o servidor deixar PvP.
- Teste: `/becomelm knock <alvo>` (op) derruba sem bater.
- Os amigos precisam da v0.2 (a v0.1 não conecta num servidor com a v0.2).

## v0.1.0

- **Corpo**: o jogador vira o FroggyDude (o mesmo modelo e as mesmas animações do mod
  Froggydude) com a **própria skin** — o modelo usa o mapa de skin de jogador.
- **Roda** (estilo a de emotes do Essential), sem pausar o jogo:
  - abre com **dois toques rápidos no agachar** ou a tecla **R** (Controles > BecomeLM mode);
  - no meio: **Humano** (desliga); à esquerda **Galope de 4**; à direita **Em pé**;
  - à direita (celular deitado) ou embaixo: as **câmeras**;
  - toca no que quer; no PC também dá pra segurar o R, apontar e soltar.
- **Galope de 4**: correndo ~14 blocos/s (o FroggyDude caçando faz ~15), andando ~6,5;
  sobe 1 bloco sem pular; patadas com poeira, terra e o som do galope (pros outros também).
  A terra que o vanilla joga pra trás na corrida fica desligada galopando (voava na câmera).
- **Pulos**:
  - galopando + correndo + pular = **bote** (longo e baixo);
  - **segurar o agachar ~0,7 s** (até o "clique") e pular = **pulo super alto** (~11 blocos),
    cai com estrondo e nuvem de poeira;
  - no modo FroggyDude não tem dano de queda.
- **Câmeras de cinema** (tecla **V** passa pra próxima, ou pela roda), sem HUD nem mão:
  - **Atrás e baixa**: rente ao chão, atrás dele, vira junto;
  - **De lado**: correndo do lado dele;
  - **Na cara**: de frente pro rosto (a "selfie");
  - **Da vítima**: do olho do bicho/jogador que está na frente dele, olhando pra ele;
    sem ninguém, do chão na frente dele, olhando pra cima.
  - O jogador continua no controle (anda, vira, ataca do próprio olho).
- **Com amigos**: o modo de cada um vai pra todo mundo que vê (quem entra depois também
  recebe). Os amigos precisam ter o BecomeLM + GeckoLib pra ver o FroggyDude.
- **Comando** (reserva se a roda não abrir): `/becomelm gallop`, `/becomelm stand`,
  `/becomelm human` (op: `/becomelm gallop <jogador>`).

## Limites conhecidos

- Servidor **sem** o mod: só você se vê como FroggyDude e o servidor dá dano de queda.
- Skin fina (Alex): o modelo tem braço de 4 px, então sobra 1 px no braço.
- Armadura e item na mão não aparecem no corpo do FroggyDude (por enquanto).
- Nadando, dormindo, de élitro ou montado: volta o corpo de jogador normal.
- Ainda falta: os sons dele (pacote à parte), as vozes (pacote à parte), a câmera ajustável
  e o editor das próprias animações.

## Desenvolvimento

- `python3 tools/make_anims.py` regenera `lm.animation.json` a partir das animações do
  Froggydude (com cópias dos pulos que já começam no ar).
- `./gradlew runClient -PquickWorld=<mundo> -PlmDebug=1` abre direto num mundo e loga a câmera.
- `-PwithFroggy=../froggydude-mod/build/libs/froggydude-X.jar` roda junto com o mod Froggydude
  (pra testar o sangue na skin); o build normal não muda.
- Mixin com refmap (MixinGradle) só pra desligar a terra da corrida no galope.
