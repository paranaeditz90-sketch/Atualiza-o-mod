# Froggydude - v0.7.6: a boca só abre pra língua e pra arrancar (sobre a v0.7.5)

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

O que testar agora (v0.7.6) - a boca:
A) "Ele fica abrindo a boca toda hora" (LM): a skin de boca aberta aparecia em toda fala, ribbit,
   pancada, mordida, mastigada, no grito e na fase 2 inteira. Agora (escolha do LM) só em três casos:
   a língua (o tiro, o chicote, a captura e o tiro na parede), o "vai usar a língua" logo antes, e
   arrancando um membro (o braço no combate e os braços/cabeça no golpe final, cravando e sacudindo).
   Falando, comendo, gritando e na fase 2: boca fechada.

Da v0.7.5 - a visão e o sorteio de longe:
A) VISÃO: do Alto pra cima (Alto, Extra, Máx, REAL) ele te enxerga até a tua distância de renderização
   (12 chunks = 192 blocos), não mais 48-72 blocos. Precisa estar à vista: árvore, morro e parede tapam.
   O jogo cortava a visão dos mobs em 128 blocos; a dele não corta mais. Mín e Médio continuam como eram
   (os níveis fáceis seguem fáceis).
B) O SORTEIO (escolha do LM): quando ele te vê de longe (mais de 32 blocos), sorteia meio a meio:
   - ATAQUE DIRETO: "Hey, humans!" e vem galopando, sem brincadeira antes.
   - O BARULHO SEM APARECER: calado (sem o "Hey, humans!"), chega por trás de ti sem ser visto e a uns 10
     blocos faz barulho: passos, ribbit, passos e a risadinha. Se tu virar e ver ele, ele some na hora.
     Depois fica sumido de 8 a 16 s e vem a caçada. Na REAL, esse sorteio substitui a brincadeira
     daquela caçada.
C) BICHO E ALDEÃO DE LONGE: ele também vê de longe, mas só vai atrás com fome (escolha do LM) - e só se
   não tiver nada perto. Testado: vaca a 100 blocos, ele foi lá, comeu e "want more".
   Testado: tu a 150 blocos de costas: chegou por trás, 4 barulhos, sumiu, voltou e te pegou ("I see you"
   na REAL). Tu a 150 blocos no ataque direto: correu os 150 blocos em ~17 s.
   No servidor dedicado vale a view-distance do servidor (o jogo não conta a de cada jogador).

Da v0.7.4 - o pulo altíssimo pra te pegar lá em cima (IA REAL):
A) O QUE O LM VIU: na árvore ele quebrou o bloco embaixo de ti com a língua, mas nem tentou pular. A
   conta do pulo calculado só rodava com 2 s preso, e nessa hora ele estava pendurado no tronco tentando
   escalar ("não está no chão": desistia por mais 2 s) - a língua, com 3 s, chegava antes.
B) AGORA: contigo lá em cima (2,5+ blocos acima dele, sem caminho andando), o pulo vem PRIMEIRO, em
   ~meio segundo. Se de onde ele está não passa (a copa no meio), ele vai pra um lugar de onde a conta
   passa e pula de lá; cai do teu lado lá em cima - ou em cima de ti, se não tem "do lado" (o topo da
   copa, pilar de 1 bloco) - e já começa o esmagamento ali. A língua no bloco e a torre de blocos viraram
   plano B (sem de onde pular, ou depois de 3 lugares tentados). Errou o pulo: tenta de novo em 15 s
   (antes, 2 minutos).
   Testado: tu na beirada da copa (5 blocos de altura): pulo de 3,3 blocos e esmagamento na copa; tu no
   topo da copa (7 blocos): pulo de 4,9 blocos, golpe final lá em cima.

Da v0.7.3 - tu em cima da árvore:
A) A CAUSA: na IA REAL, antes de comer ele passa 2 a 4 minutos "brincando" contigo (os sustos) e nesse
   tempo não ataca. Um dos sustos é chegar calado e rir no teu ouvido - contigo lá em cima da árvore ele
   nunca chega no ouvido e ficava até 20 s rodando embaixo, sumia, voltava... parecia não saber o que
   fazer (o teu print). Nos outros níveis e na caçada da REAL ele já se virava.
B) AGORA: subir onde ele não chega andando (árvore, pilar, telhado sem escada) conta como fugir, igual
   correr pra longe. Depois de ~8 s lá em cima ele encerra a brincadeira, te encara ("hora de comer") e
   parte pra caçada: arranca com a língua as folhas debaixo de ti, escala o tronco ou faz o pulo
   calculado. E enquanto tu tá lá em cima, o susto de rir no ouvido nem entra no sorteio.
   Testado: tu na beirada da copa de um carvalho (5 blocos de altura), com a brincadeira ativa: em ~8 s
   ele parte pra cima, derruba as folhas com a língua e te pega. No ALTO (padrão) e na caçada da REAL,
   também testado: língua nas folhas embaixo de ti.

Da v0.7.2 - o bug do "corre parado":
A) A CAUSA: o Minecraft congela os mobs que ficam fora da "distância de simulação" do jogador (no celular
   ela é curta). Ele parava no meio da corrida e o teu jogo continuava mostrando a última animação dele,
   correndo - por isso "corre parado no mesmo lugar" (e as partículas da poção paradas no teu vídeo).
   Agora o pedaço do mapa em volta dele (3x3 chunks) continua rodando enquanto tiver jogador na dimensão
   dele: longe de ti ele segue caçando, farejando, indo atrás. Testado: tu a 165 blocos, antes ele
   congelava; agora continua andando.
B) O FARO EM LOOP: quando o teu rastro some do nada (tp, pérola, portal), ele chegava no fim do rastro,
   varria, achava que tinha "cheiro novo" (o teu, lá longe), voltava pro fim do rastro velho... e ficava
   ciscando no mesmo ponto. Agora só cheiro novo PERTO dele faz ele voltar pro rastro; senão varre em
   volta (anéis de 6 e 11 blocos) e desiste depois de ~30 s.

Da v0.7.1 - os cacos:
A) CACOS TORTOS: o corpo não quebra mais em 3-8 blocos inteiros. Ele estilhaça em ~60 cacos tortos
   (aldeão 60, porco 61), cortados por planos aleatórios igual ao Physics Mod: por fora a skin/roupa/
   armadura de verdade, por dentro (a face do corte) a própria skin esticada e mais escura - por isso cada
   caco tem cara de pedaço de carne, não de bloco. Quem não sangra (esqueleto, golem, slime...) vira caco
   sem sangue. Config do cliente: pedacos.cacos (60, de 6 a 150) e pedacos.maximo (300 no mundo todo).
B) CHUTÁVEIS: passou por cima, empurra. Tu, ele, um mob - quem passar andando pelo monte chuta os cacos
   pra frente com um pulinho rasteiro e um "squish"; correndo/galopando espalha mais (com limite, pra não
   virar projétil). Parado em cima não mexe nada. Ele deitado comendo continua fazendo os cacos pularem a
   cada mordida.
C) PESO: os cacos dormem quando param (não gastam física), e cada caco desenha a metade dos vértices de
   antes. No PC de teste, 60 cacos = ~0,4 ms por tick depois que assentam. Se o celular engasgar, baixa
   pedacos.cacos pra 30.

Da v0.7.0 - o corpo despedaçado e o banquete:
POR QUE NÃO O PHYSICS MOD: o motor dele é o PhysX da NVIDIA, que vem compilado só pra PC (Windows,
   Linux x86, Mac). No celular (Linux ARM) ele tenta carregar a biblioteca do PC, dá erro e o jogo fecha -
   não tem plano B no código dele, e a licença (All rights reserved) não deixa mexer no jar. Então a
   física foi feita aqui dentro, em Java puro: roda no celular.
A) DESPEDAÇADO: quem o FroggyDude mata (jogador, aldeão, bicho, monstro) se desmonta nos cubos do próprio
   modelo - cabeça, tronco, braços, pernas, a aba do chapéu, com a skin, a roupa e a armadura - que caem,
   quicam, tombam, rolam e se amontoam de verdade (colidem com os blocos e uns com os outros), tingidos de
   sangue, pingando e deixando poça onde param. O que ele já arrancou (braço, cabeça) não aparece no monte.
   Ficam 90 s e afundam no chão (config do cliente: pedacos.segundos e pedacos.maximo). Gore Desligado =
   morte normal, sem pedaços. Testado: aldeão em 8 pedaços, porco em 6, o jogador de armadura em 3 (sem
   os braços e a cabeça, que ele comeu no golpe final).
B) O BANQUETE: depois de matar, ele deita em cima dos pedaços em "flexão" e fica "comendo" sem engolir nada
   (o vídeo do Grox e o "WHAT HAPPENED TO ME???" 2:50) por 3 a 7 s; a cada mordida os pedaços embaixo dele
   pulam e espirra sangue. O ritmo muda com a raiva (fase 2, boom do warden, irritado, apanhou agora) e
   a fome (ferido, barriga vazia): de 0,8x (cheio, calmo) a 1,4x (furioso e faminto). Só no fim vem o
   "that was so delicious". Se alguém bater nele, larga e revida.
C) O SOM: com o pacote de voz NOVO (FroggyDude-Voz.zip desta versão), o nhum nhum NHOM com o sangue
   escorrendo, tirado do vídeo que tu mandou, em loop perfeito (emenda no vale entre duas mordidas) e
   acelerado/desacelerado junto com o ritmo. Sem o pacote: mastigada + molhado do sangue a cada mordida.
D) Bicho: agora ele come 2,5 s com o bicho vivo e aí o bicho morre e se desmonta - o resto do tempo é em
   cima dos pedaços.

Da v0.6.4 - a transformação do nada:
A) IRRITADO DO NADA: na hora que ele decide partir pra cima de ti (o bote, a mordida, a língua...),
   às vezes, mesmo com a vida cheia, em vez do ataque vem a raiva: 5 ribbits cada vez mais altos,
   mais graves e mais colados (~2,4 s), ele vem chegando sem atacar, para na tua frente e se
   transforma - a fase 2 inteira (contorção, grito, 30 s feroz). Só contra jogador, nunca no meio
   do susto da IA Real (aí é medo, não ataque). Se ele estiver falando, a raiva espera a fala acabar.
   Testado: com a vida 250/250 ele irritou a 19,7 blocos, chegou ribbitando, contorceu, gritou e veio.
B) QUANTO: config "irritado_chance" (padrão 25%): sorteada no 1º ataque contra ti e depois no máximo
   a cada 30 s. Depois de uma transformação, só de novo em 60-90 s (como antes). 0 = só com a vida
   baixa, como era.
C) PRA VER NA HORA: /froggydude irritar (com ele já partindo pra cima de ti).

Da v0.6.3 - a velocidade:
A) LEVEMENTE MAIS RÁPIDO QUE TU: perseguindo, ele mede a tua velocidade (média de ~1 s, só no chão,
   sem contar pérola/teleporte) e corre ~25% mais que ela. Parado ou andando conta como quem corre
   pulando (~7 b/s): ele vem a ~9 b/s. Antes era um galope fixo de 15 b/s, quase 3x o teu.
B) TRANSFORMADO (fase 2): ~50% mais que tu - vem correndo e vai chegando rápido.
C) SE TU FICA MAIS RÁPIDO, ELE TAMBÉM: poção de velocidade, cavalo, elytra baixo - em ~1 s ele sobe
   junto, até o teto de 20 b/s. Se tu para de correr, ele desacelera em ~1 s.
   Medido no servidor com um porco andando reto em velocidade fixa (IA Real):
     porco parado       -> ele 0,445 b/t (8,9 b/s)
     porco 0,40 b/t (8)  -> ele 0,502 (+25%)      transformado -> 0,602 (+50%)
     porco 0,60 b/t (12) -> ele 0,753 (+25%)
     porco 0,90 b/t (18) -> ele 1,004 (o teto, 20 b/s)
   Nos níveis abaixo do Máx/Real a folga encolhe junto (Alto ~23%, Mín ~19%).
   O galope fora da perseguição (seguindo rastro, indo pra vila, se escondendo) continua o do config
   (caca_blocos_por_segundo, 15).

Da v0.6.2:
A) MAIS RESISTENTE: vida 150 -> 250 (125 corações), armadura 6 -> 10 e um pouco de dureza de armadura.
   Quem já tinha ele no mundo ganha a vida nova quando o mundo carrega (na mesma proporção).
   A fuga pra se curar e a fase 2 continuam nas mesmas porcentagens de vida.
B) O BOOM DO WARDEN JOGA ELE LONGE (com animação): voa uns 10 blocos pra trás, arqueado, se
   debatendo de braços e pernas abertos, cai de costas, ergue a cabeça olhando o warden, senta, volta
   pro agachado de sapo e aí vai com raiva, no dobro da velocidade, e come o warden vivo. Testado:
   o boom acertou, ele voou pra fora da tela e voltou pra comer o warden.

Da v0.6.1:
O que testar na v0.6.1 - SÓ o bug da skin (uma coisa por vez):
A) SKIN SUMINDO NO CELULAR (MobileGlues, Zalith Launcher 2): com sangue grudado na skin, o teu corpo
   sumia no F5 e na prévia do inventário, e a mão em primeira pessoa ficava preta. A 0.5.1 não resolveu.
   A culpa era a textura de sangue GERADA na hora (DynamicTexture) desenhada por cima do corpo: no
   MobileGlues ela apagava tudo. As poças de sangue no chão, que usam textura fixa, sempre funcionaram aí.
   Agora o sangue grudado não usa textura gerada: cada pingo é um quadradinho colado na camada de fora
   da skin (já na pose da animação), com uma textura branca fixa pintada pela cor do vértice, do mesmo
   jeito das poças. No PC: corpo aparece no F5, no inventário e na mão, com o sangue escorrendo igual.
   Confere aí: arranca o braço (ou deixa ele te sujar), F5, inventário e a mão vazia.

Da v0.6.0:
O que testar na v0.6.0. B a E são da IA Real (Mods > Froggydude > Configurar > IA: Real, ou
`/froggydude ia real`); o A vale em todos os níveis.
A) ESMAGAMENTO DE JOELHOS (vs AJTHEBOLD 2:06, debaixo d'água): o modelo ganhou JOELHO (a perna virou coxa
   + canela, sem mexer na skin: a canela usa as mesmas 6 fileiras de baixo da perna). No esmagamento ele cai
   de joelhos do teu lado, canelas deitadas pra trás, tronco quase reto, ergue os punhos acima da cabeça e
   desce. Tu fica a 1,1 bloco dele (antes 1,5): os punhos param a ~0,4 bloco do teu olho e a cabeça dele
   fica alta, longe da câmera. As outras animações não mudaram (a canela fica reta nelas).
B) ESTUDO DA PRESA: ele aprende a tua rotina - onde tu guarda as coisas (baú), dorme (cama), trabalha
   (bancada) e minera - e de quanto em quanto tempo tu volta pra cada lugar. Mede o teu medo pelo jeito que
   tu mexe a câmera (girando mais que o normal, olhando pra trás de repente, olhando pro teto).
   1. TEMPERO: uma vez por dia, antes de comer, brinca contigo uns 2-4 min: aparece de longe e some, chega
      calado e ri no teu ouvido, aparece na janela, faz barulho sem aparecer, come um bicho na tua frente.
      Cada susto ele mede (ficou paranoico? saiu correndo?) e repete o que funciona CONTIGO. Quando o medo
      chega no ponto: "I can smell the fear in you! Time to eat!" e aí é caçada. Bateu nele ou fugiu pra
      longe: acaba na hora.
   2. TOCAIA: longe de ti, ele vai pro lugar que tu está pra voltar e espera lá dentro - grudado no CANTO
      DO TETO (cômodo de 3+ de altura) ou parado no breu (caverna, mina).
C) O BOTE DO CANTO DO TETO (o teu pedido): de tocaia no teto, quando tu chega (ou abre o baú de costas),
   ele se solta, rola de barriga pra baixo no ar e mergulha em ti. Metade das vezes só EMPURRA: tu voa de
   costas, tonto e lento, e ele já sai correndo atrás. Na outra cai MONTADO e devora.
D) PORTA (o teu pedido): porta de madeira no caminho, caçando de cara limpa ele às vezes ARROMBA no tranco
   (4 em 10; com raiva, fase 2 ou o warden que jogou ele longe, quase sempre) - a porta voa em pedaços e
   quem está dentro ouve. De mansinho (calado, de tocaia) sempre abre quietinho. Precisa do mobGriefing.
E) MASSACRE DA VILA (o teu pedido): tu mora numa vila (a tua cama ou baú com 3+ aldeões em volta) e foi
   minerar (longe da vila, ou lá embaixo sem céu em cima)? Ele vai até lá e come os aldeões um por um, casa
   por casa (arromba a porta de mais da metade). Tu voltou e apareceu perto: ele larga a vila e vem atrás de
   ti. No máximo um massacre por dia do jogo. OS PEDAÇOS dos aldeões espalhados pelas casas dependem da
   física dos pedaços (o Physics Mod que tu mandou) - isso fica pra depois do teu veredito, como tu pediu.
F) Comandos de teste (`/froggydude estudo`): mostra o que ele sabe da tua rotina e o teu medo;
   `lugar bau|cama|bancada|mina` marca onde tu está; `tocaia` manda ele te esperar agora; `brincar <s>`
   começa o tempero (0 = sem brincadeira); `massacre` manda ele já pra tua vila.

Da v0.5.1:
Os bugs que tu achou na 0.4.8:
A) GOLEM: golem de ferro que vem brigar com ele vira comida, igual aos outros bichos. Ele não troca
   soco: dá o bote, prende no chão e come (antes ele brigava do jeito que briga com gente e morria).
   Vale pra qualquer bicho que venha pra cima dele (lobo, urso, abelha...), menos chefe e creeper.
B) WARDEN (em qualquer nível de IA): o warden sente ele chegando e muda o jeito de brigar - em vez
   das mãos, usa o BOOM pra jogar ele longe. Ele voa, levanta com raiva, corre o dobro e come o
   warden vivo de primeira (Grox, cidade antiga). Testado: um boom, ele voou, e comeu o warden.
C) PAREDE: ele não sobe mais parede feito aranha quando tem caminho andando. Antes qualquer
   encostada numa parede virava escalada - no teste, tu no 2º andar de uma torre com porta e escada
   por dentro, ele chegava na porta, raspava na parede do lado e subia por fora até o telhado, e
   ficava lá em cima. Agora ele entra pela porta e sobe a escada. Sobe parede só quando não tem
   caminho (torre, pilar, muro). E se estiver escalando sem ganhar altura (teto, beiral), larga.
D) SKIN SUMINDO (o teu vídeo das 23:22, no celular): depois do "banho de sangue" o teu corpo sumia
   (3ª pessoa e a prévia do inventário pretas) e voltava "do nada" - quando a chuva lavava o sangue.
   A culpa era a camada de sangue por cima da skin: ela é o modelo de novo, quase todo
   transparente, e no renderizador do celular o pixel transparente tapava o corpo de baixo.
   Agora ela só pinta a cor (não tapa nada), e a textura do sangue é criada e enviada fora do
   desenho. No PC eu não consigo reproduzir o sumiço (aqui sempre apareceu), então isso aqui é
   a correção mais provável - confere aí no teu: braço arrancado sangrando, F5, e o inventário.

Da v0.5.0:
A) IA REAL: Mods > Froggydude > Configurar > IA: Real (vermelho), ou `/froggydude ia real`. É a
   esperteza do Máx com o jeito do FroggyDude dos vídeos (VS Every Opponent, 10v1, How many days,
   Manhunt do Grox). O que ele faz:
   1. Sabe o que está na TUA TELA (o teu cliente conta pro servidor 4 vezes por segundo: se ele
      aparece na imagem, sem bloco no meio, e se tu está de tela aberta, comendo ou minerando).
   2. Chegando sem ser visto, sorteia como chegar e aprende contigo o que dá certo: o "I see
      you!... you can't hide from me!" e o bote de lado que esmaga (caverna do Grox); de mansinho
      e calado pelas costas; parado no breu esperando tu chegar perto; ou direto. Se tu virar e
      ver ele, ele parte pra cima na hora. No `-dev` o chat mostra o que ele escolheu.
   3. Às vezes, no começo, te encara de longe ("I can smell the fear in you! Time to eat!") e vem.
   4. Tu fugindo: corre atrás e dá o bote colado, sem pulo de longe. FASE 2: só corre e te derruba
      na trombada (nada de pulo, como tu viu no vídeo).
   5. Trancado em casa: ele come o que tem lá fora (aldeão, golem, bicho), aparece na janela rindo
      e olhando, e arromba quando tu abre bau/inventário ou depois de uns segundos.
   6. Lava: só atravessa quando não tem outro caminho, andando por cima e sem queimar.
   7. Pulo altíssimo: só quando não tem jeito nenhum de chegar (plataforma no ar, outro lado da
      lava), com a conta da física pra cair do teu lado. No máximo um a cada 2 minutos.
   8. Apanhando com a vida baixa: "Oh my god, I gotta get out of there now!", some da tua vista,
      "I'm very low on health, I need food and a plan", come, "Back to full health!" e volta com
      "Time for the plan!" (o plano diz se ele vai esmagar ou devorar).
   9. Faro: "I can smell him! He's nearby!".
B) Pacote de voz NOVO (0.5.0): 9 falas novas, do Grox e do VS Every Opponent ("I see you", "I can
   smell him", "I can smell the fear in you", "I gotta get out of there", "I'm very low on health",
   "Back to full health", e os dois "Time for the plan").
C) SAIU a cena de depois no Extremo (ele comendo e brincando com o teu corpo). O golpe final
   continua igual: braço, braço e cabeça. Tu morre quando ele engole a cabeça.
D) CONSERTADO: o jogador ficava invisível ao morrer/renascer, e renascia com sangue na skin (o jato
   do toco continuava no corpo novo, porque o jogador renasce com o mesmo número de entidade). Agora
   renasce limpo: sem sangue na skin, na tela, e fora da cutscene.
E) A fase 2 "uma vez" agora fica salva no mundo (saindo e voltando ele não se transforma de novo
   só pela vida baixa).
F) CONSERTADO (achado nos testes): com lava entre vocês ele entrava nela andando reto. Eram duas
   coisas: o "vai reto" do fim do caminho (que é pra subir parede) e um caminho "com lava" que ficava
   guardado depois que ele desistia de atravessar. Agora ele para na beira de lava, fogo e magma, e
   quando atravessa (IA Real, sem outro jeito) anda POR CIMA da lava, sem afundar. Testado: lava de
   12 blocos entre vocês - de céu aberto ele passa no pulo calculado; com teto baixo, faz ponte.
G) CONSERTADO: o pulo calculado não saía. Tu numa plataforma alta, ele: (1) largava o alvo depois
   de 3 s sem te ver lá debaixo; (2) conferia "tem caminho?" olhando o caminho errado (o do ponto
   pra onde estava andando, não até ti); (3) procurava um ponto de onde te VER, mas de perto a borda
   da plataforma tapa a vista e de longe (14+ blocos) o pulo não alcança (o ar freia). Agora ele sabe
   onde tu está lá em cima, procura um ponto de onde a CONTA do pulo passa (5 a 8 blocos), vai até
   lá e pula. Testado: plataforma 9 blocos acima, ele pulou de 3,6 blocos e caiu do teu lado.
H) Fugindo pra se curar, ele corre pra onde tem bicho pra comer (antes podia se esconder longe de
   tudo e ficar esperando a cura que não vinha).

Da v0.4.9:
A) Fase 2 com o pacote de voz NOVO: o grito inteiro do vídeo do Grox (5:20-5:34), 13,6 s,
   com as falas do Grox e os dois tiros tirados (os buracos foram tapados com o próprio
   grito, então a duração é a do vídeo). O grito agora vai junto com ele (antes ficava no
   lugar onde ele começou a gritar e sumia quando ele corria pra longe).
B) Esmagamento com o pacote: o som de cada soco é o da caverna do vídeo do Grox (2:02-2:08,
   23 socos, um a cada 0,25 s), limpo: alinhei os 23 e tirei a média, que apaga a voz e a
   música por baixo e deixa só a pancada.

Da v0.4.8:
A) Fase 2 com o pacote de voz NOVO: o grito agora segue como no vídeo do Grox, uns 9 s.
   Começa com o "AAAAAAHHH!!!" parado, entra a continuação quando ele dispara, e ele
   continua gritando enquanto corre atrás de ti (no vídeo o Grox atira e não adianta).
   Tirei do clipe as duas falas do Grox que ficavam no meio ("AHH! AHH! Leave me
   alone!" e "He's bulletproof now!"): são três pedaços do mesmo grito emendados.
   Atenção: o clipe da 0.4.7 ainda tinha o "AHH! AHH!" do Grox no finalzinho.
B) Enquanto ele grita, nenhuma fala entra por cima (nem o "give me your body" quando
   derruba, que antes cortava tudo), e o esmagamento soca sem o "tai!". Da contorção
   até o grito ele fica calado (antes podia sair um "I'm hungry" no silêncio). Se a
   cutscene final começar no meio do grito, o grito para e entra o áudio da zebra.

Da v0.4.7:
A) Fase 2 (`/froggydude fase2`) com o pacote de voz NOVO, nos tempos do vídeo do Grox
   ("Manhunt but I have RABIES", depois dos blazes, 5:15-5:25): o gemido da contorção,
   1 s de silêncio, e o grito "AAAAAAHHH!!!" ainda parado e torto (4,5 s depois do
   começo do gemido), que vai morrendo por 3,7 s; a continuação, alta, entra quando ele
   dispara (8,2 s). O grito é o do vídeo, cortado antes da fala do Grox; o "ah! ah! ah!"
   picotado de antes saiu do pacote. A contorção agora dura sempre 8,25 s (antes eram
   5-7 s sorteados) e, com o pacote, não tem grunhido no silêncio.

Da v0.4.6:
A) Fase 2 (`/froggydude fase2`) com o pacote de voz NOVO: a transformação toca o som
   original dele se contorcendo (3,4 s de gemido gutural com os ossos estalando, o mesmo
   som no short da fase 2, na versão longa e no TikTok "I'm the horror mod"). Ele entra
   quando a dobra rápida aparece (8 ticks depois do começo, a animação começa devagar).
   Os estalos de "bloco de osso" e os gemidos que o mod tocava por cima ficam mudos com
   o pacote (eventos próprios, froggydude.contort_crack/contort_grunt); sem o pacote,
   fica como era. O clipe que saiu do pacote na 0.4.3 era esse mesmo som, só que
   cortado (2,4 s), de qualidade pior e embolado com os estalos do mod; a transcrição
   automática tinha "ouvido" uma frase no gemido.

Da v0.4.5:
A) Cutscene (`/froggydude golpefinal`) refeita quadro a quadro em cima do "Eating a
   Zebra" (60 fps, em câmera lenta). O braço arrancado fica ATRAVESSADO na boca, de um
   lado ao outro do rosto (cachorro com osso, igual a perna da zebra), e não mais
   espetado pra frente. Ele come cada braço em duas engolidas: joga a cabeça pra trás,
   com a boca pro céu, dá um tranco lá em cima e o braço encurta; volta a cabeça pra
   frente e repete, até sumir (o esquerdo some em 3,2 s, o direito em 6 s). Depois do
   direito fica uns 2 s parado, em pé, boca fechada, como no vídeo.
B) A cabeça: morde, arranca, close quase parado, chicoteia rápido e então engole em
   duas vezes (pra trás, uma chicotada pro lado, pra trás de novo); ela fica PRESA na
   boca (antes era empurrada pra fora e flutuava solta em cima dele) e DIMINUI de
   verdade até sumir (antes encolhia só no comprimento, que aponta pra câmera
   do close, então parecia do mesmo tamanho). O escuro do fim só vem depois que ela
   desce.
C) O corpo dele andava uns 5 ticks atrasado em relação ao áudio, à câmera e ao pedaço
   na boca (o GeckoLib só começa a contar a animação depois da transição e de o estado
   chegar no cliente). Agora a animação da cutscene anda no mesmo relógio da tabela:
   mordida, arrancada, engolida e som caem juntos.
D) Vídeo lado a lado (zebra x mod, com o áudio original) e o mesmo em câmera lenta,
   pra comparar.

Da v0.4.4:
A) Cutscene (`/froggydude golpefinal`): o braço fica FIRME nos dentes (antes
   balançava solto, como se tivesse quebrado). Arrancou: meio segundo sacudindo,
   depois uma mastigada a cada 0,25 s (boca abre e fecha, a cabeça morde e puxa,
   som pesado com osso estalando uma sim, outra não), e o braço vai entrando na
   boca até sumir; ele engole ("glup") e só então vai pro outro. A cabeça também
   desce goela abaixo no fim.
B) Com o pacote de voz novo: a cutscene inteira toca o áudio dele comendo a zebra,
   tirado do próprio "Eating a Zebra" (a cutscene foi feita em cima desse vídeo, então
   cada mordida, arrancada e troca de câmera cai no lugar; o áudio entra 0,2 s depois
   do começo, que é onde o vídeo começa). Enquanto toca, os sons de cada golpe e o
   gemido da vítima ficam mudos pra não embolar. Sem o pacote, fica como antes.
C) Ele come o teu corpo (Extremo) em FLEXÃO de novo, deitado por cima, e emenda uma
   rodada na outra: a "mesa de quatro" da tua print era ele parado de quatro entre
   uma rodada e outra (a pose de "parado no meio da caçada").
D) Ferido, ele se cura comendo: abaixo de 50% de vida vai matando e comendo o que
   tiver por perto (procura mais longe) até voltar a 90%; cada mordida da flexão
   cura um tico. Antes: só abaixo de 30%, e uma refeição já tirava ele dali.
E) Ribbit = raiva (a lore): à toa ele quase não faz barulho (só o resmungo e a
   risada, bem de vez em quando); o ribbit sai quando ele apanha, quando tu foge
   dele, quando não consegue chegar em ti, quando tu escapa, e direto na fase 2.

Da v0.4.3:
A) IA em 5 níveis: Mods > Froggydude > Configurar > "IA do FroggyDude" (ou
   `/froggydude ia <min|medio|alto|extra|max>`; sem nada, mostra o atual).
   Alto = o de sempre. Mín: vê de perto, reage devagar, não prevê, não cerca,
   não põe bloco, não arromba, não sabota, chuta a tática. Médio: meio termo.
   Extra: enxerga/fareja 25% mais longe, reage 20% mais rápido, corta caminho
   até de quem vai pulando, vem em ZIGUEZAGUE quando tu mira arco/besta/tridente
   nele e DÁ A VOLTA no teu escudo. Máx: tudo isso mais forte (alcance 1,5x,
   reação 0,6x), quase não arrisca o que já falhou contra ti e não larga a pista.
   Velocidade e dano mudam pouco (0,85x no Mín, 1,1x/1,2x no Máx). Só um jogador
   por enquanto (a tela muda o config do mundo).
B) Pacote de voz novo (FroggyDude-Voz.zip): fora as falas do FroggyDoom (o
   "digest you", "you are mine", "bite your balls" e o "God damn it, I love
   pain" eram do vídeo "My Next Opponent Is... Me", outro personagem), o
   "last thing you see" que tinha sido cortado errado ("Listen."), os gritos de
   OUTRAS pessoas (vítima gritando "STOP", "WHAT THE H-", "I'll do it", "BRO",
   "Yes", "Bye") e as sobras do anti-trapaça. O "get back here" sem o "d-d-d-d"
   que parecia áudio travando. Ribbits com mais peso no som ambiente.
C) Contorção: o som era uma FALA ("This is what we want to do!") tocada no
   começo da dobra. Agora são estalos de osso nos trancos da animação (os ticks
   em que a pose anda mais rápido na rotoscopia) e um grunhido curto no 1º
   tranco e quando o ombro sobe. O grito continua no fim (ROAR).
D) Depois da cutscene (Extremo): ele não vira mais um boneco vinho sem cara (a
   skin "acabou de comer" no último estágio cobria TUDO de sangue escuro; agora
   é sangue no peito/mãos com a cara, a coroa e o terno aparecendo) e come o teu
   corpo DE PÉ, curvado, mergulhando a cabeça (o "Eating a Zebra"), em vez da
   flexão do lado do corpo - que de longe virava uma mesa de quatro pés.
E) Sangue na tela: no máximo 14 respingos (eram 28), menores, mais transparentes,
   escorridos mais curtos, nas bordas (o meio fica livre) e somem mais rápido.
F) Aldeões apavorados: quem vê ele (ou está a 10 blocos) entra em pânico, solta
   o "hmm!" e foge CORRENDO pra longe (mais rápido que o pânico de zumbi); com
   ele a 16 blocos ou menos eles tremem, mais forte quanto mais perto.
G) Sempre com fome: 15 s depois de comer um bicho (30 s se for aldeão ou gente),
   sem ninguém na mira, qualquer bicho por perto vira comida (vaca, porco,
   ovelha, golem, o cachorro de alguém) - no teste ele limpou 2 vacas, 1 porco,
   1 ovelha e 1 aldeão em ~1 min. À toa e de barriga vazia ele reclama: "I'm
   hungry", "I'm very hungry", "I want your flesh", "I want more". Ribbit a cada
   ~5 s em média (eram ~8 s), sem falar por cima de uma fala.

Da v0.4.2:
A) Fase 2 (`/froggydude fase2`): a contorção agora é ROTOSCOPIA do teu vídeo
   (60 fps, 89,28-91,75 s). Cada pose foi ajustada contra o quadro do vídeo pela
   cor de cada pixel (cara, coroa e lágrima pesam mais que o terno), com o
   esqueleto conferido contra prints do jogo de verdade tirados com a mesma
   câmera. 2,95 s. A v0.4.1 errava a cabeça (virava pra trás e mostrava o
   cocuruto) porque o ajuste antigo achava a cara e o topo da cabeça "o mesmo verde".
B) Jeito de jogador (a IA melhorada):
   - Sem caminho até ti (fosso de lava, buraco, tu numa plataforma no ar que ele
     não escala nem come), ele PÕE BLOCO: torre embaixo dele (olha pro pé, pula
     e põe o bloco onde estava) ou ponte agachado (olha pro lugar, põe, anda).
     Pedregulho, até 64 por vez. Respeita mobGriefing.
   - A dancinha: agacha e levanta rápido (shift spam), pulando ou socando o ar.
     Às vezes quando ele está "só olhando" de longe. `/froggydude dancar` testa.
   - Matou o mesmo jogador 3 vezes num dia: da próxima vez ele chega andando,
     ri, dança na tua frente, fala "kkkkkkk muito ruim" e "Não mexe comigo" no
     chat e vai embora. Um dia de trégua - se tu bater nele, acabou a trégua.
     `/froggydude zombar` testa.
   - Andando à toa (e no "só olhando"), uns pulos sem motivo, como gente.

Da v0.4.1 (do 7 em diante é o que mudou depois da v0.4.0):
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
    O jorro do Extremo não é mais a "cobra" (fita ligando as gotas, que virava
    zigue-zague com o braço sacudindo): agora é uma coluna de cubinhos de
    sangue, igual ao ombro da zebra no vídeo. Ainda faz poça onde cai e suja
    a skin de quem acerta.
11) Cutscene: a câmera ficou mais longe nos planos de corpo inteiro (no vídeo
   ele ocupa metade da tela) e o close é na frente-direita dele, com a tua
   cabeça saindo da boca pro lado (não tampa mais a cara dele).
12) Contorção da fase 2 refeita quadro a quadro com o teu vídeo em 60 fps
   (89,2-91,6 s): dobra até quase deitar com o topo da cabeça pra frente
   (losango da coroa), desce pro lado direito dele, sobe com a cabeça ainda
   pendurada, levanta tombada com o braço direito pra fora, e a cabeça vai
   caindo PRA TRÁS até quebrar e deitar na frente do ombro esquerdo, enquanto
   os ombros encolhem (o esquerdo primeiro, depois o direito sobe e fica
   deslocado). 2,5 s. A boca fica fechada (cara de choro) até o grito.
13) Flexão com a cabeça reta (alinhada com o corpo).
14) Aldeão é a comida favorita: bote, flexão, e arranca os braços (juntos)
   e a cabeça, com o gore (bebê não).
15) A tela só começa a ficar vermelha com 1 coração.
16) Vitória-régia (fim do teu vídeo, 95,6-98,9 s): atravessando água com
   folha no rumo de quem ele caça, ele não nada - vai de folha em folha num
   salto de sapo rasante (deitado no ar, braços pra frente), pousa em pé com
   uma nuvem bege e já sai no próximo, um salto a cada 0,3 s (até 5,5 blocos).
   O último salto é pra margem. Sem folha, nada como antes.

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
  velocidade mudando o tempo todo (o galope acelera e freia liso) e um relógio de
  fora (a cutscene anda no tick da tabela, junto com o áudio)
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
- client/gore/ - BloodStains (sangue na skin), BloodStreams (o jorro do Extremo, em cubinhos),
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
| CORPSE_EAT      | feed_mob (loop)   | 2,0 s     | a flexão por cima do corpo; emenda as rodadas |
| CORPSE_TOSS     | corpse_toss       | 1,2 s     | solta o corpo pro alto aos 0,4 s     |
| CORPSE_SHAKE    | corpse_shake      | 1,7 s     | o corpo na boca sacudindo            |
| PAD_HOP         | pad_hop (loop)    | 0,3 s     | de vitória-régia em vitória-régia, pousa aos 0,2 s |
| CONTORTING      | contort + contort_hold | 2,95 s + 2 a 4 s | rotoscopia do vídeo (tools/anim/contort_fit.json); torto, ombro deslocado |
| BUILD           | walk / idle + agachar e braço (modelo) | - | pondo bloco: torre e ponte (ai.PlayerMoves) |
| TAUNT           | walk / idle + agachar e braço (modelo) | - | a dancinha; zombando                 |
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
  `[FROGGYDEBUG]` (pouso dos pulos, jogador preso, cada fala que ele diz,
  e o que o cérebro decide: pôr bloco, zombar...).
- Comparar animação com vídeo quadro a quadro (como foi feita a contorção da
  v0.4.2): `-PwinW=720 -PwinH=1280 -PanimSlow=0.05 -Pcapture=1` abre a janela
  em pé (igual TikTok), roda a animação 20x mais devagar e salva um print por
  quadro em `run/screenshots/cap_<tempo da animação>.png`. Com `{DebugAnim:"contort"}`,
  o jogador em espectador no lugar da câmera do vídeo e `fovEffectScale:0.0`
  (voando o jogo abre o campo de visão em 10%), cada print casa com o quadro
  do vídeo do mesmo tempo. As poses da contorção ficam em
  `tools/anim/contort_fit.json` (uma por tick; o build_anims lê de lá).

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
contorção fica FECHADA (geme de boca fechada) e no frenesi da fase 2 fica
aberta o tempo todo. Pra refazer depois de trocar uma
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
