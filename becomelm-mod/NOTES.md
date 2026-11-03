# BecomeLM mode — notas

Mod **privado** do LM pra gravar vídeos como o FroggyDude (não é pra lançar). Minecraft
1.20.1, Forge 47.4.10, precisa do **GeckoLib 4.4.9** (o mesmo do Froggydude). Pode ficar
junto com o mod Froggydude, um não mexe no outro.

## v0.3.9 — o nado do vídeo, a lava sem queimar e a tremida em primeira pessoa

Rede "8" (a mesma: nada mudou no que vai pela rede).

O que testar:
- **Tremida da marreta em quem bate (tu), em PRIMEIRA pessoa** (LM, 03/11: "1ª treme, F5 igual"): agora a tela
  chacoalha miúdo e sem parar enquanto tu martela também em primeira pessoa, o mesmo chacoalhão da 3ª pessoa (~0,8° em
  média, até ~1,5°, umas 10 vezes por segundo, e some uns 0,5 s depois da última pancada). Antes (0.3.6 a 0.3.8) em
  primeira pessoa quem bate não tremia. Atrás de ti e de frente pra tua cara (F5) e as câmeras de cinema: igual antes.
- Quem apanha continua com o tranco curto de sempre a cada pancada.
- **Nadando** (correr dentro d'água): o boneco deita como o jogador do jogo, inclina pra onde tu olha e faz o nado do
  FroggyDude do vídeo (How many days 1:48-1:59), a mesma animação do mod da IA 0.7.22: pernas juntas e retas batendo
  pra cima e pra baixo, o corpo gangorrando junto, braços esticados à frente na altura da cabeça.
  (Antes, nadando, o BecomeLM voltava pro corpo de jogador normal com o nado padrão do jogo, as pernas em tesoura:
  agora fica o boneco do FroggyDude. E o "tada" do pouso não ganha mais do nado.)
- **Lava** (LM: "a mesma de correr, levemente mais lento, sem queimar"): tu pisa e galopa POR CIMA da lava (o pé fica
  na superfície, como o strider), não pega fogo e anda ~15% mais devagar em cima dela. A animação é a de sempre.

## v0.3.8 — a câmera "Da vítima" na marreta igual ao vídeo

Rede "8" (a mesma: nada mudou no que vai pela rede).

O que testar:
- **Câmera "Da vítima" enquanto tu martela** (em pé ou montado; a tecla da câmera até "Vítima"): a câmera fica PARADA
  no chão na tua frente, a 1,7 bloco e na altura do olho de quem tá deitado (0,4), olhando 33° pra cima. Tu aparece
  inteiro de joelhos e a tua cabeça desce até a tela a cada pancada, igual ao vídeo do LM de 03/11 (LM: "deixa com a
  mesma perspectiva que esse do vídeo"). Antes ela ficava no olho da vítima, embaixo de ti e perto demais, e ia atrás
  da tua cabeça. Fora da marreta, a câmera da vítima é a mesma de antes. Testado no jogo (gravação).
- O mod da IA, na 0.7.21, ganhou a mesma vista pra quem apanha dele e o martelo encaixado no vídeo. Aqui o martelo é o
  teu de sempre: só a câmera mudou.
- O resto da 0.3.7 e da 0.3.6 continua pra testar, abaixo.

## v0.3.7 — parado no galope com as pernas retas "||"

Rede "8" (a mesma: nada mudou no que vai pela rede).

O que testar:
- **Parado no galope** (de 4 sem andar, e também segurando o agachar pra carregar o pulo super alto): as pernas agora
  ficam **retas e em pé, "||"**, embaixo do quadril (o LM: "repara na perna, tá tipo /" e "o meu pode ser reto ||"),
  o tronco quase deitado e os braços quase em pé com as **mãos no chão** na frente, respirando. O corpo desce um tico
  por cima das pernas pra mão alcançar o chão (igual à agachada do pulo do céu). Antes a perna ficava reta e inclinada
  (o "/") e as mãos ~0,2 bloco no ar. (O FroggyDude do mod da IA, na 0.7.20, ficou diferente: agachado de sapo.)
- O resto da 0.3.6 (o voo e a queda do pulo super alto, a tremida da marreta) continua pra testar, abaixo.

## v0.3.6 — o pulo super alto do vídeo do AJ e a tremida da marreta do jeito do vídeo

Rede "8" (a mesma da 0.3.5: nada mudou no que vai pela rede).

O que testar:
- **Pulo super alto** (segurar o agachar até o "clique" e pular): o voo e a queda agora são os do pulo do céu novo do
  mod da IA (a réplica do VS AJTHEBOLD, Every Opponent 29:48 e 29:51, que tu aprovou): decola de **braço de zumbi**
  levemente pra cima, aberto em "\ /"; caindo, a **perna direita vai pra frente** (a esquerda fica reta) e a **cabeça
  balança de leve pros lados** até cair. Aqui é só a parte do ar: o teu pulo sai na hora que tu pula, então a ameaça e a
  agachada do mod da IA não entram. Testado no mod da IA (o mesmo movimento, no jogo); no BecomeLM, confere tu.
- **Tremida da marreta** (em pé e montado):
  - **Quem apanha**, em primeira pessoa: o mesmo tranco curto da 0.3.5 a cada pancada.
  - **Quem bate (tu)**: em primeira pessoa **não treme mais**. Em **terceira pessoa** (F5 e as câmeras de cinema), a tela
    **chacoalha miúdo e sem parar** enquanto tu martela, igual à câmera de fora do VS Grox MANHUNT (~0,8° em média, até
    ~1,5°, umas 10 vezes por segundo), e some uns 0,5 s depois da última pancada. Agora as câmeras de cinema tremem
    também (no vídeo a câmera de fora treme). Compilado, mas ainda não testado no jogo: confere tu.
- **Dano da marreta**: cada pancada já tirava vida na hora dela (o BecomeLM zera a invencibilidade do jogo a cada
  pancada); quem engolia 1 soco a cada 2 era o esmagamento do mod da IA, corrigido lá na 0.7.19. Aqui só o teste
  ganhou uma linha a mais no log (se o dano entrou em cada pancada).

## v0.3.5 — o monte no ritmo da fome, subir parede de 4, a marreta do Grox e a mordida montado de volta

Rede "8": quem joga junto precisa estar na 0.3.5 também.

O que testar:
- **Monte de pedaços pela fome**: com a barra **vazia** ele come desesperado (uma mordida a cada ~0,25 s, igual ao monte
  do golem do Grox); conforme enche, **vai acalmando**; com a barra **cheia**, ~0,39 s (o Taste Testing perto da lava é
  0,36 s). Testado no jogo: barra em 5 → começou a 0,27 s e foi pra 0,32 s quando chegou em 13.
  - O corpo, a cara afundando na primeira pessoa, os cacos pulando e a comida da barra andam **no mesmo relógio**: antes
    a animação escorregava um tico a cada volta e a cara descia fora da hora da mordida.
  - **No criativo** (a barra não desce lá): o ritmo **extremo o tempo todo**, uma mordida a cada 0,25 s (o máximo; o
    LM: "velocidade extrema, não a ponto de virar o flash"). No sobrevivência vale a fome (no pacífico a barra não desce).
- **Escalada** (na escada ou trepadeira): agora é o **galope na parede** do mod da IA (o do Grox 3:29), de 4, as costas
  pra fora e a cara pra cima. A passada acompanha a subida (na escada, que é devagar, ele não fica correndo no lugar).
  **Parado na escada, ele fica parado agarrado.**
- **Subir parede sem escada (de 4)**: galopando reto pra qualquer parede (de frente, até uns 35° de lado), ele sobe
  correndo por ela no mesmo galope, a **10 blocos/s** (o Grox 3:29, medido com o pilar de pedra de régua; no jogo deu
  7,5 blocos em 15 ticks).
  - Segurando o **pra frente**, sobe; **soltou, para na hora** agarrado e escorrega devagar; **agachado**, fica parado
    na parede. Caindo de frente pra uma parede, ele agarra nela e escorrega (sem dano, como sempre).
  - No **topo** ele passa da beirada galopando e **fica em cima**, até num **pilar 1x1** (testado: pousa no meio do
    bloco e fica). Soltar o pra frente no último bloco e meio também serve: ele termina de subir em vez de escorregar.
  - Se continuar segurando o pra frente depois de chegar, ele **segue galopando** lá em cima (num pilar 1x1 isso é cair
    do outro lado: pra ficar no pilar, solta quando chegar).
  - As **patas ficam nos blocos** (não flutua): o corpo vira reto de frente pra parede mesmo chegando meio torto (chegando
    torto ele sobe meio de lado, então o melhor é correr reto, como tu falou).
  - Só de 4 (galope). Em pé, nadando, na lava, voando, de élitro ou montado não sobe. No meio de um golpe também não
    (arrastando alguém na boca, montado socando, no monte, transformando): arrastando, a vítima ia junto pra dentro da
    parede. Sem dano de queda, como sempre.
  - **Câmeras de cinema na parede**: "atrás e baixa" fica embaixo dele olhando parede acima; "de lado" acompanha do
    lado; "na cara" fica em cima da cabeça olhando parede abaixo, ele vindo pra câmera (o plano do Grox, do alto do
    pilar). Entrando e saindo da parede a câmera vira suave. Antes ela entrava na parede e grudava na cara dele (e
    também quando ele ficava parado de cara pra uma parede no chão).
- **Marreta** (em pé segurando o atacar, e os socos montado depois do pulo do céu): medida quadro a quadro no VS Grox
  MANHUNT 2:27-2:33 (a réplica que tu aprovou):
  - Os dois braços sobem juntos retos acima da cabeça (a cabeça um tico pra trás), e descem abertos com o tronco
    tombando bem mais que antes (~76°, era ~62°), a cabeça mergulhando até a da vítima. Ele sobe logo depois da
    pancada, segura um tico lá em cima e desce mais rápido.
  - 4 pancadas por segundo, igual ao vídeo. **O punho bate na hora da pancada** do começo ao fim (antes a animação
    escorregava e o golpe vinha fora do punho).
  - **Som de crítico** do Minecraft em cada pancada (só o som), colado no punho: no teste, no mesmo quadro do punho
    embaixo ou até 1 tick depois. O empurrão que derruba (o começo da marreta em pé) não é punho descendo: fica só o
    soco normal, sem crítico e sem tremer; o 1º crítico é a 1ª pancada, 0,2 s depois.
  - **Dano normal, mas 1 em cada 5 pancadas sai 1,5x** (por sorte).
  - **Tremidinha na tela** em primeira pessoa a cada pancada: em quem apanha (mais forte) e em quem bate (mais
    fraca). As câmeras de cinema não tremem (são pra gravar). Testado em quem bate (a tela balança ~0,8° a cada
    pancada); em quem apanha precisa de 2 jogadores, então confere tu com um amigo.
  - Montado (depois do pulo do céu) são 8 socos: depois do último ele fica embaixo, em cima da vítima, até começar a
    morder (antes o braço descia mais uma vez, mudo, sem pancada).
- **Agachado de 4 parado** (galope sem andar): o mesmo "pronto pra disparar" do mod da IA (antes era a "mesa").
- **Mordida montado** (depois dos socos da queda do céu): voltou a ser a da 0.3.3. Na 0.3.4 ela tinha virado a flexão
  comprida nova sem querer: mordia o ar depois da cabeça da vítima e 2x mais rápido.

## v0.3.4 — a flexão nova, igual à do mod da IA (mão plantada, braços retos "||")

O que testar (no monte de pedaços, como antes):
- **A pose**: o corpo inteiro é uma prancha deitada que **gira nos pés** — na mordida o ombro desce quase o mesmo
  que a cabeça, sem levantar a bunda. Os **braços ficam retos e parados feito pilares "||"**, com a mão plantada nos
  cacos; quem desce é o corpo entre eles (embaixo o topo do braço sobra acima das costas: o "|-|"). Acabou a esfinge.
- **O ritmo**: uma mordida a cada 0,35 s (desce rápido, segura um tico embaixo, sobe devagar). Ele come de verdade
  em 3 de cada 8 mergulhos, quase o mesmo tanto de antes.
- **Primeira pessoa**: o olho desce junto com a cara em cada mergulho (0,24 bloco).
- Ainda **não** muda de velocidade com a fome aqui (no mod da IA muda com a fome/raiva) — se quiser, é o próximo.
- O deitar e o levantar emendam na pose nova. A escalada e o agachado ficaram como estavam (o gerador queria trazer
  as do mod da IA 0.7.14, mas isso não foi testado aqui: fica pra quando tu quiser).

## v0.3.3 — comer no monte em flexão

- **Deitado no monte comendo**: agora é a "flexão" dos vídeos (medido a 60 qps no How many days
  0:44, de frente, e no 10v1 0:22 e 1:55). O quadril e as pernas ficam deitados nos cacos (a bunda
  não levanta), o peito fica empinado e os **braços vão retos pra frente por cima do monte**,
  tipo esfinge (o LM escolheu; ele não abre os braços pro lado), o que deixa os **ombros lá em
  cima**, do lado da cabeça. A cada **0,3 s** o tronco desce e a cara mergulha nos cacos entre os
  braços (de olhar pra frente a olhar pra baixo) e volta; as mãos ficam em cima dos cacos e a
  cabeça vai tombando devagar pra um lado e pro outro. Antes ele ficava deitado reto com a cara
  no chão.
- Ele deita um pouco mais pra trás do monte (os ombros em cima do meio dele), pra cara e os
  braços caírem em cima dos cacos.
- De 4, clicar no monte não fica mais em pé um instante antes de se jogar: cai direto.
- Os cacos embaixo da cara mexem a cada mergulho; nas mordidas de verdade (3 a cada 2,4 s, o
  mesmo ritmo de antes pra encher a fome) mexem mais, sangram e fazem o barulho molhado.
- Em primeira pessoa o olho fica em cima do peito empinado e desce um pouco em cada mergulho.
- A rede não mudou: quem está com a 0.3.2 ainda conecta, mas vê o jeito antigo. Atualiza todo mundo.

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
