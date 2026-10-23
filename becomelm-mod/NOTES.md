# BecomeLM mode — notas

Mod **privado** do LM pra gravar vídeos como o FroggyDude (não é pra lançar). Minecraft
1.20.1, Forge 47.4.10, precisa do **GeckoLib 4.4.9** (o mesmo do Froggydude). Pode ficar
junto com o mod Froggydude, um não mexe no outro.

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
- Ainda não tem ataques (próxima parte), nem vozes (pacote à parte, depois).

## Desenvolvimento

- `python3 tools/make_anims.py` regenera `lm.animation.json` a partir das animações do
  Froggydude (com cópias dos pulos que já começam no ar).
- `./gradlew runClient -PquickWorld=<mundo> -PlmDebug=1` abre direto num mundo e loga a câmera.
- Mixin com refmap (MixinGradle) só pra desligar a terra da corrida no galope.
