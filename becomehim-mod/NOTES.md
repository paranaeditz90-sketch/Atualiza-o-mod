# Become HIM... — notas

Mod **público** do LM: o jogador vira o FroggyDude (com a própria skin). Minecraft 1.20.1, Forge 47.4.10,
precisa do **GeckoLib 4.4.9**. Nasceu da cópia do BecomeLM 0.3.9 (17/11/2026), com nome, pacote (`com.becomehim`),
ID (`becomehim`), canal de rede e chaves próprios: dá pra ter os dois mods instalados juntos.

Licença: todos os direitos reservados. Modificar ou republicar só com permissão do autor (pedir no TikTok @XLMzola).
Sem sons do FroggyDude: só sons do próprio Minecraft.

A diferença pro BecomeLM: no lugar da mordida que segura e sacode (o LM é um cachorro), a **língua do FroggyDude**.
O resto (galope, em pé, fase 2, pulo alto, pulo do céu, marreta, monte, finalização, nado, lava, parede, câmeras) é
o mesmo do BecomeLM 0.3.9 (ver `../becomelm-mod/NOTES.md`).

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
