# Become HIM... — notas

Mod **público** do LM: o jogador vira o FroggyDude (com a própria skin). Minecraft 1.20.1, Forge 47.4.10,
precisa do **GeckoLib 4.4.9**. Nasceu da cópia do BecomeLM 0.3.9 (17/11/2026), com nome, pacote (`com.becomehim`),
ID (`becomehim`), canal de rede e chaves próprios: dá pra ter os dois mods instalados juntos.

Licença: todos os direitos reservados. Modificar ou republicar só com permissão do autor (pedir no TikTok @XLMzola).
Sem sons do FroggyDude: só sons do próprio Minecraft.

A diferença pro BecomeLM: no lugar da mordida que segura e sacode (o LM é um cachorro), a **língua do FroggyDude**.
O resto (galope, em pé, fase 2, pulo alto, pulo do céu, marreta, monte, finalização, nado, lava, parede, câmeras) é
o mesmo do BecomeLM 0.3.9 (ver `../becomelm-mod/NOTES.md`).

## v0.6.1 — o ícone

Rede a mesma da 0.6.0 ("6").
- **Ícone novo** na lista de mods (e o `icon.png` pra página do mod), da ideia do LM (a versão do ChatGPT: o jogador
  virando o FroggyDude): uma cabeça só, rachada no meio, metade Herobrine (olho branco) e metade FroggyDude (a cara de
  verdade dele: coroa, bochecha, olho, lágrima, boca), a rachadura verde brilhando e a pele do Herobrine já esverdeando
  perto do corte; "BECOME HIM..." em letra de pixel. Pixel art de grade única, 512x512; lê até em 64 px. Refazer:
  `python3 tools/icone.py`.
- A licença na lista de mods saía com os acentos quebrados ("sÃ³", "permissÃ£o": o Gradle lê o gradle.properties sem
  UTF-8): agora em inglês, sem acento, igual à descrição.

## v0.6.0 — voar longe na explosão

Rede "6".
- Transformado, levou uma **explosão** (creeper, TNT, cama no Nether, cristal...) ou o **grito do warden**: voa pra
  longe dela dando **cambalhota pra trás**, cai de costas e levanta (o "flung" do mod da IA, o boom do warden do Grox).
  Enquanto voa e levanta não dá pra andar; sem dano de queda. Se estava no meio de um golpe, larga (quem estava preso
  levanta). Testado: TNT a 2 blocos jogou uns 5 blocos pra trás e 2,5 pra cima.

## v0.5.2 — correções da 2ª revisão

Rede a mesma ("5"). A 2ª revisão do código (no que entrou depois do 1º pente fino) achou, eu conferi e corrigi:
- **Cabeça, o mais sério**: deitada, a vítima conseguia USAR ITEM (levantar escudo e sobreviver ao arranque, comer maçã
  dourada, beber poção). Agora, presa, só o golpe/tiro em quem está em cima conta; o resto não faz nada.
- **Cabeça**: "atire nele" saía com 1 soco pra quem segurava arco/tridente; e um "tiro" valia até com arco sem flecha ou
  besta descarregada. Agora é por pontos (precisa de 6): tiro de verdade 6 (arco com flecha, besta carregada, arma de
  fogo), golpe de arma 3, soco 2 = 1 tiro / 2 golpes / 3 socos, e o aviso mostra o que falta pelo que ela tem na mão
  AGORA. Testado: mão vazia pede "3x", depois de 1 soco "2x"; espada solta com 2 golpes; sem se soltar, morre.
- **Cabeça**: a espera de 30 s começava mesmo quando o servidor recusava (PvP desligado, alguém já pegou), e tu
  escorregava 1 s pra cima da pessoa à toa. Agora só conta se começou de verdade.
- **Cabeça**: virar Humano/transformar/ser derrubado no meio não parava a cena (ela morria do mesmo jeito). Agora para.
- **Cabeça**: sair do jogo no meio deixava a câmera da vítima presa em 3ª pessoa. Agora volta.
- **Chicote/puxão**: dava pra puxar alguém que outro FroggyDude estava segurando/arrancando a cabeça (quebrava a cena
  dele). Agora não.
- Limpeza: código morto tirado, um comentário fora do lugar (o "Cansado" estava em cima da cabeça), uma soltura dobrada.

## v0.5.1 — o toco no ombro e o acabamento pra lançar

Rede a mesma da 0.5.0 ("5").
- **Toco no ombro**: quem perdeu o braço agora mostra o toco vermelho com a ponta do osso no ombro, no corpo de jogador
  e no boneco do FroggyDude (antes o braço só sumia). Testado com 2 jogadores.
- O aviso da cabeça ("SE SOLTA!") agora sai no idioma do jogo (em inglês pra quem joga em inglês).
- **README.md** novo (inglês e português): controles, avisos e a licença (modificar/republicar só com permissão, TikTok
  @XLMzola). A descrição do mod agora é em inglês e português.

## v0.5.0 — cansado, arrancar a cabeça, chicote e puxão da língua

Rede "5". Quatro gomos novos (a roda agora tem 9 ações):
- **Cansado**: curvado, ofegando (o "tired" do mod da IA). Andando, anda normal e volta a ofegar quando para. Acaba
  quando tu ataca, escolhe de novo, ou em 1 min.
- **Arrancar a cabeça** (gomo armado; sai no próximo ataque em alguém a até 3,2 blocos; 30 s de espera entre um uso e
  outro, tu escolheu): derruba de costas, monta, olha, crava a boca na cabeça e chacoalha durante o rugido (4,2 s, a
  cutscene do mod da IA 0.7.41).
  - **Jogador** se defende: aparece em cima da barra, em vermelho, "SE SOLTA! atire nele / bata nele 2x com a arma /
    soque ele 3x" e o tempo que falta, e a câmera dele vai pra 3ª pessoa durante a cena (volta sozinha). Deitado, cada
    clique de atacar conta como golpe em ti (de dentro do teu corpo o golpe do jogo não acertava); usar arco/besta/arma
    de fogo conta como tiro. Conseguiu: tu voa pra trás (com tiro voa mais) e ele levanta. Não conseguiu: tu arranca a
    cabeça e ele morre (e vira o monte).
  - **Bicho** não se defende: arranca e morre.
  - Testado: zumbi morre em 4,2 s; jogador com espada que acertou 2x (por comando) te jogou pra trás e ficou vivo. O
    clique da vítima não deu pra testar aqui (o 2º jogador do teste não recebe teclado/mouse): confere com um amigo.
- **Chicote da língua** (armado, até 8 blocos): estala no bicho, tira 2 corações e joga ele pra longe. Testado: zumbi
  foi de 6,5 pra 12,5 blocos.
- **Puxar com a língua** (armado, até 8 blocos): gruda e puxa ele pra perto. Testado: zumbi veio de 6,5 pra 3 blocos.

## v0.4.1 — correções do pente fino (revisão do código)

Rede a mesma da 0.4.0 ("4"). O que mudou (a revisão achou, eu conferi no código e corrigi):
- **Lava**: andar em cima da lava sem queimar valia pra QUALQUER jogador com o mod, até no modo Humano (bug desde o
  BecomeLM 0.3.9). Agora só transformado. Testado: Humano afunda e pega fogo; no galope anda em cima sem fogo.
- **Teto**: o mergulho não achava o bicho se o teto fosse alto ou se tu olhasse reto pra baixo (só caía). Agora procura
  em 3D pra onde tu olha. Testado: teto a 3,2 blocos, olhando reto pra baixo, derrubou o zumbi.
- **Braço**: quem viu alguém sem braço e não estava perto quando ele renasceu continuava vendo ele sem braço. E sair do
  End pelo portal devolvia o braço sem morrer. Os dois corrigidos (não deu pra testar sozinho: precisa de 2 jogadores).
- **Comer vivo**: o bicho do golpe armado podia "sobrar" e ser comido num monte depois, sem nada a ver; outro jogador
  podia pegar o mesmo bicho; e ele seguia mordendo o bicho mesmo longe. Corrigidos.
- **Sair do jogo no meio de um golpe** (arrancando o braço, martelando, comendo vivo, montado): quem estava preso ficava
  até 1 min caído. Agora solta na hora.
- **Raiva**: clicar 2 vezes rápido pra sair dela às vezes começava outra raiva. Corrigido.
- **Roubar arma**: picareta/pá de mod que bate forte contava como arma, e "gunpowder" (pólvora) também (tinha "gun" no
  nome). Agora só nome de arma inteiro e ferramenta não conta.

## v0.4.0 — roubar e comer a arma

Rede "4".

O que testar (gomo novo na roda, "Roubar a arma", embaixo à direita):
- Tocar no gomo **arma** o golpe; o próximo ataque em quem estiver com uma **arma na mão** (espada, machado, arco,
  besta, tridente ou arma de mod: TaCZ, Avaritia...; tu escolheu "só armas") puxa ela com a **língua** (até 8 blocos). A arma **some** da mão da pessoa (ou
  do bicho) e ele **come**: sacode a cabeça com ela atravessada na boca, ela vai entrando aos poucos, com farelo cinza
  e barulho de mastigar, e engole olhando pra cima (o "glup", o arroto). Igual ao mod da IA (Grox 1:24).
- Sem arma na mão: aparece "Ele não tem arma na mão" e o golpe continua armado.
- Testado no jogo: zumbi com espada de ferro ficou de mão vazia.
- A roda agora tem 5 ações: estalar o pescoço (em cima), arrancar o braço, roubar a arma, teto e raiva.

## v0.3.0 — arrancar o braço (e comer vivo na flexão) e o teto

Rede "3".

O que testar (gomo novo na roda, "Arrancar o braço", à direita embaixo):
- Tocar no gomo **arma** o golpe (aparece o aviso em cima da barra; tocar de novo desarma). Ele sai no **próximo
  ataque** em alguém a até 3,2 blocos. Ninguém perto: continua armado.
- **Em jogador** (que não esteja no criativo e ainda tenha o braço): derruba de costas, crava os dentes no ombro (0,4 s),
  ARRANCA o braço esquerdo (1,1 s, jato de sangue) e come ele (4,5 s, o braço atravessado na boca, com a skin e a manga
  de quem perdeu, encurtando a cada mordida). Comer enche a fome (3 mordidas de 1 coxinha). Igual ao mod da IA (tu
  escolheu): quem perde o braço fica com 3 corações a menos, sem a mão secundária e sangrando pelo ombro (para em 3
  corações); o braço só volta quando ele morre. O braço some do corpo de jogador, da 1ª pessoa e do boneco do FroggyDude.
- **Em bicho** (tu: "mobs come normal, deitado, com a flexão"): derruba e deita em cima dele na flexão do monte, comendo
  ele VIVO (cada mordida de verdade tira 1 coração e enche a fome). Morreu: vira o monte de pedaços embaixo de ti e tu
  segue comendo. Andar levanta, pular sai no mortal, igual ao monte.
- Testado no jogo com 2 jogadores (vida máxima da vítima 20 -> 14) e num zumbi (20 -> 7 em ~5 s).
- **Teto** (gomo novo, embaixo na roda): sobe e gruda de cabeça pra baixo no teto que estiver em cima da cabeça (até 4
  blocos; sem teto aparece o aviso). Fica parado lá (a tocaia do canto do teto do mod da IA). **Atacar** = se joga em
  cima do bicho da mira (até 9 blocos), derruba e dá o dano do bote. **Pular ou agachar** = solta. Sozinho, solta em
  1 min. Testado: 8 s pendurado sem o servidor chutar por "voar", mergulho num zumbi (20 -> 13).
- A roda agora tem 4 gomos de ação: estalar o pescoço (em cima), arrancar o braço (direita), teto (embaixo) e raiva
  (esquerda).
- Ainda sem o toco vermelho no ombro de quem perdeu o braço (no mod da IA tem): fica pra depois se tu quiser.

## v0.2.0 — a roda de ações (estalar o pescoço e a raiva "()")

Rede "2". O mesmo da roda do BecomeLM 0.4.0: a roda ganhou o anel de fora com as ações; **estalar o pescoço** (gomo
de cima) e a **raiva "()"** (gomo de baixo: agachado fundo, braços abertos, boca aberta; andando avança curvado;
atacar, escolher de novo ou 30 s acaba). Testado no jogo.

## v0.1.0 — a língua do FroggyDude

O que testar:
- **Lançar a língua**: galopando, atacar um bicho bem perto (onde o BecomeLM mordia; mais longe continua o bote); em
  pé, atacar um bicho de 3,2 a 8 blocos (mais perto continua a marreta). A língua sai (0,3 s), enrola no peito dele
  (a volta em pentágono da réplica v8 que o LM aprovou pro mod da IA) e **ergue até 4 blocos** na tua frente, a 2,4
  blocos (até 1,0 s). Ele fica pendurado balançando de leve e vai junto pra onde tu anda e olha (andando devagar).
- **Atacar de novo**: puxa pra boca (0,4 s) e **morde** (4 corações, a mordida do mod da IA; come se sangrar) e solta
  na tua frente. Testado: zumbi de 40 de vida foi pra 30 (língua 1 + mordida 4 corações, menos a armadura).
- **Pular**: o **arremesso** (réplica v3): pega impulso pro outro lado, chicoteia girando o tronco e solta; ele voa
  rodando uns 40 blocos pro lado sem parede, árvore nem teto (sem lado livre, só larga). Testado: 39 blocos, pico 8.
- Sem fazer nada, solta sozinho em 10 s. Shift não solta quem está preso.
- A cabeça olha pra quem está pendurado (até 50° pra cima). Na 1ª gravação ela olhava pra baixo (sinal trocado):
  consertado e conferido no jogo.
- **1ª pessoa**: a língua sai de baixo da tela (fina, pra não tampar a vista).
- Quem é pego (jogador) fica preso no gancho, em pé, sem andar; no voo a câmera tomba de um lado pro outro.

Falta testar (precisa de 2 jogadores): ser pego sendo jogador, ver a língua de outro jogador.
Ainda não tem: os gomos de ação na roda (próximo passo, pedido do LM) nem o resto das animações do mod da IA.

Animações: `tools/make_anims.py` corta da `tongue_capture`/`tongue_throw` do mod da IA as `tongue_cap_start`,
`tongue_cap_hold` (loop do balanço), `tongue_cap_walk`, `tongue_cap_pull` e `tongue_throw_end`. O `sky_air`,
`sky_drop`, `finisher` e `finisher_r` ficam congelados em `tools/frozen/` (os do BecomeLM 0.3.9 testados): o
`sky_drop` do mod da IA ganhou a ameaça no começo e o corte de 0,38 s do `sky_air` passou a pegar a ameaça.
