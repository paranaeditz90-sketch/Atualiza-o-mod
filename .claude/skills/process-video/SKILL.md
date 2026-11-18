---
name: process-video
description: Processar um vídeo (mp4/mov/webm/mkv) pra entender o conteúdo e, principalmente, analisar ANIMAÇÃO do Minecraft quadro a quadro gastando pouco - metadados, folha geral, cortes e trechos com movimento achados sozinhos, rajadas de 0,1-0,25 s nos momentos rápidos, cabeça/tronco/braços/pernas rastreados separados (deslocamento, giro, ciclo, quem acompanha quem, quem fica parado), imagens de comparação, linha do tempo e o que dá e o que NÃO dá pra afirmar; tudo com cache. Use quando o LM mandar um vídeo ou gravação e quiser saber o que acontece ou como o personagem se mexe. Pra virar keyframes GeckoLib e comparar com a animação do mod, siga na video-motion-analysis; pra só ver do que se trata um link, o /watch basta.
user-invocable: true
argument-hint: <caminho-do-vídeo> [início fim]
---

# process-video (versão pra animação de Minecraft)

Base: [bszymi/claude-code-process-video](https://github.com/bszymi/claude-code-process-video) (MIT, © 2026 Bartosz
Szymichowski; `LICENSE` ao lado). A original tirava um print a cada 5-30 s e mandava ler **todos**. Esta mede o
movimento com números e só abre a imagem que vale a pena.

- Script: `.claude/skills/process-video/scripts/pv.py` (subcomandos abaixo).
- Autoteste: `python3 .claude/skills/process-video/scripts/selftest.py` (vídeo sintético de resposta conhecida, ~10 s).
- Precisa de ffmpeg/ffprobe e python3 com numpy, Pillow e OpenCV. O matplotlib (gráficos) e o WhisperX do `/watch`
  (fala) são opcionais. Nesta nuvem já está tudo instalado.
- Rode **sem** `python3 -I`: o `-I` esconde o `dateutil` do matplotlib e os gráficos somem.

## Regras de economia (não gastar token à toa)

1. **Nunca** leia todos os quadros. A ordem é:
   - `probe` (texto);
   - `overview` (1 imagem);
   - `motion` (texto);
   - `burst` só do trecho escolhido (1 folha);
   - `grid` (1 imagem);
   - `parts` (texto);
   - `compare --auto` (2 imagens, só se precisar ver);
   - `report` (texto).
2. Cada passo tem **cache** em `<out>/<vídeo>-<hash>/manifest.json`.
   - Repetir com os mesmos parâmetros só reimprime o resumo, e `status` lista o que já foi feito.
   - Mudou o script, o cache antigo deixa de valer sozinho.
3. Tirou uma conclusão? Use `note "..."`: ela entra no `report.md`. Depois de compactar a conversa, leia `status` ou
   `report.md` em vez de reabrir imagens.
4. A saída vai **sempre pro scratchpad** com `--out <scratchpad>/pv`. Quadros, áudio e transcrição de vídeo de
   referência nunca vão pro repo, que é público.

## Passo a passo

```bash
PV=".claude/skills/process-video/scripts/pv.py"; O="--out <scratchpad>/pv"
python3 $PV probe    VIDEO $O    # duração, qps, áudio, VFR, girado
python3 $PV overview VIDEO $O    # 24 quadros espalhados: escolha o trecho olhando SÓ isto
python3 $PV motion   VIDEO $O [--start A --end B]
#   cortes de cena (detector do ffmpeg + pico local), área útil sem tarja/texto parado, % da área que mudou,
#   fundo que se mexe sozinho descontado, câmera (correlação de fase), quadros repetidos (qps efetivo),
#   trechos com movimento + "rajadas" (os instantes mais rápidos de cada trecho)
python3 $PV burst    VIDEO $O --start A --end B --step 0.1|0.25|all [--grid]   # rajada em folha com tempo
python3 $PV grid     VIDEO $O --t T          # 1 quadro com grade numerada (px da resolução de trabalho)
python3 $PV parts    VIDEO $O --start A --end B --ref T \
        --part cabeca:x0:y0:x1:y1 --part tronco:... --part braco_d:... --part braco_e:... --part perna_d:... --part pes:...
#   por parte e por quadro: posição, Δx/Δy, giro no plano da imagem, nota de confiança; período (fração de quadro),
#   fases do ciclo (em cima / descendo / embaixo / subindo), quem acompanha quem, postura (eixo pés→cabeça)
python3 $PV compare  VIDEO $O --auto         # extremos (mais alto x mais baixo) e o par de quadros mais rápido
python3 $PV compare  VIDEO $O --t1 A --t2 B  # par escolhido: A | B | diferença x2 com vetores x3 e Δ por parte
python3 $PV report   VIDEO $O                # report.md + timeline.png: trechos, partes, linha do tempo, limites
python3 $PV audio    VIDEO $O [--start A --end B] [--lang pt]   # fala (WhisperX do /watch, se instalado)
python3 $PV note     VIDEO $O "conclusão"    |   python3 $PV status VIDEO $O
```

Como marcar as partes: abra só a imagem do `grid` e escreva uma caixa por parte **visível**, cobrindo um pedaço com
detalhe (borda, coroa, botão, sapato). Peça lisa (terno todo escuro) acha a posição, mas o giro sai "indeterminável".
Nomes com `cabeca`/`head` e `perna`/`pe`/`leg` ligam o cálculo de postura.

## Como ler os números

- Px da resolução de trabalho (largura até 960, a mesma do `grid`). **Δy positivo = desce** na imagem.
- `acompanha cabeca +0,95` quer dizer que sobe e desce junto. Com −0,95, faz o contrário.
- Ciclo e fases saem em % do período. A resolução é 1 quadro (a 30 qps, um ciclo de 0,36 s tem ~11 quadros, ±9%).
- `(quase) PARADO` quer dizer que a parte mexe até 15% do que a parte que mais mexe.
- Câmera acima de 0,3 px/quadro é descontada nas partes, com erro de 1-2 px.
- Parte com nota abaixo de 0,55 fica "tapada/irreconhecível" naqueles quadros: **não use a posição nesses quadros**.

## O que NÃO dá pra afirmar (diga isso pro LM, sem inventar)

- **Textura animada** (lava, água, fogo, portal) entra como "área que mudou" no `motion`. Um ritmo de ~0,1 s ali é
  ela, não o corpo. O `parts` não sofre com isso.
- **Profundidade** (chegar perto ou longe) e **giro fora do plano** (virar de lado) não saem de uma vista só.
- Também não sai de uma vista só **qual osso** girou, se foi o tronco ou o osso pai.
- Vídeo de 24 qps num arquivo de 30 (quadros repetidos) dá um tempo efetivo de ±1/24 s. O `motion` avisa.
- Movimento mais curto que 2 quadros não é medido.

## Exemplo real (Taste Testing, flexão comendo perto da lava, 48,3-52,9 s, 30 qps)

`parts` com cabeça, braço de perto, braço de longe e pés, em ~7 s de máquina:

- **Cabeça:** sobe e desce 41,9 px e vai 22,4 px pro lado. Ciclo de 0,364 s: em cima 21%, descendo 26%, embaixo 17%,
  subindo 36%.
- **Braço de perto:** 3,4 px, quase parado, faz o contrário da cabeça (−0,95).
- **Pés:** 4,4 px, contrário da cabeça (−0,95).
- **`motion`:** achou os 21 cortes do vídeo, 18% de quadros repetidos (qps efetivo ~24,5) e a cena da flexão como um
  trecho próprio.

Isso bate com a medição feita à mão em 02/11 (viking `08-flexao-estudo.md`).

## Depois daqui

- Plano de keyframes GeckoLib e comparação com a animação do mod: skill `video-motion-analysis` (`rigfit.py`).
- O que o vídeo é, em linhas gerais, ou link do YouTube/TikTok: plugin `/watch`.
