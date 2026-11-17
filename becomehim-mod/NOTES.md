# Become HIM... — notas

Mod **público** do LM: o jogador vira o FroggyDude (com a própria skin). Minecraft 1.20.1, Forge 47.4.10,
precisa do **GeckoLib 4.4.9**. Nasceu da cópia do BecomeLM 0.3.9 (17/11/2026), com nome, pacote (`com.becomehim`),
ID (`becomehim`), canal de rede e chaves próprios: dá pra ter os dois mods instalados juntos.

Licença: todos os direitos reservados. Modificar ou republicar só com permissão do autor (pedir no TikTok @XLMzola).
Sem sons do FroggyDude: só sons do próprio Minecraft.

A diferença pro BecomeLM: no lugar da mordida que segura e sacode (o LM é um cachorro), a **língua do FroggyDude**.
O resto (galope, em pé, fase 2, pulo alto, pulo do céu, marreta, monte, finalização, nado, lava, parede, câmeras) é
o mesmo do BecomeLM 0.3.9 (ver `../becomelm-mod/NOTES.md`).

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
