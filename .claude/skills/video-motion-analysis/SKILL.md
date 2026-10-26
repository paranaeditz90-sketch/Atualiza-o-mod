---
name: video-motion-analysis
description: Analisar movimento em vídeo (referências do FroggyDude, gravações do jogo, vídeos do LM) com um pipeline reprodutível ffprobe/ffmpeg — achar os momentos certos sem gastar, extrair quadros com densidade adaptativa e tempo exato, folhas de contato, cortes, áudio e fala (WhisperX), anotar pontos-chave só com evidência visual, transformar em plano de keyframes GeckoLib, comparar a animação com a referência e corrigir numa 2ª rodada, medindo tempo e quadros. Use para estudar/copiar um movimento, conferir uma animação contra o vídeo ou medir tempos. Para só "ver do que se trata" um vídeo/link, o plugin /watch basta.
---

# Análise de movimento em vídeo → animação GeckoLib conferível

Scripts em `.claude/skills/video-motion-analysis/scripts/`:
- `vma.py`: o lado do vídeo (metadados, folha geral, varredura, trechos, extração, fala, relatório);
- `rigfit.py`: o lado da animação (ângulos, plano de keyframes, comparação, 2ª rodada, grade de anotação).
Requer ffmpeg/ffprobe, numpy e Pillow; a fala requer o WhisperX (`bash .claude/scripts/install-whisperx.sh`).

**Saídas sempre no scratchpad, nunca no repo.** O repo é público: quadro, áudio e transcrição de
vídeo do FroggyDude ou do LM não entram em commit. Os dois scripts recusam pasta de saída dentro do
repo (`--allow-repo` força, só pra material sem nada do FroggyDude/LM); no `vma.py` o `--out` é obrigatório.

## Regras (não negociáveis)

1. **Observação ≠ estimativa.** Medido (tempo, pixel, movimento, corte, nível de áudio) vai em
   `observations`/`observed`. Deduzido (ângulo 3D, intenção, "câmera mexendo") vai em `estimates`/`estimate`,
   com `confidence` e `evidence` (tempos dos quadros).
2. **Só anote o que está visível.** Ponto escondido, fora do quadro ou ambíguo (skin confusa) vai
   em `hidden`. Não marque joelho "no meio" se não dá pra ver a perna reta; não chute o lado.
   Osso sem segmento visível **não ganha keyframe**: segue a pose base.
3. **Tempo do vídeo ≠ tempo do jogo.** Câmera lenta, quadro repetido, gravação engasgada e atraso
   de rede existem. Use `--align` (deslocamento + escala) ou `--warp` (por quadro, crescente) e diga isso.
4. **Prove com número e imagem.** Toda conclusão cita o erro em graus e a folha de comparação.

## Fluxo

```bash
V=/caminho/video.mp4; O=$SCRATCH/vma_x; P=.claude/skills/video-motion-analysis/scripts
# 1. Olhar barato: metadados + 24 quadros espalhados + varredura de movimento/cortes/áudio + trechos
python3 $P/vma.py run "$V" --out $O                 # sem trecho: extrai pelos trechos detectados
python3 $P/vma.py run "$V" --out $O --start 43.4 --end 47.6 --budget 120   # trecho conhecido
#    -> report.json (probe, observations, estimates, stats), overview.jpg, motion.csv, audio.csv,
#       intervals.json/.csv, frames/ (nome = tempo exato), frames.json, sheet_*.jpg,
#       annotation_template.json. --transcribe [--lang en] acrescenta a fala com tempo.
# 2. Ler overview.jpg e as folhas (ferramenta Read). Escolher os quadros que mostram a pose.
# 3. Anotar com grade (coordenada sempre do quadro ORIGINAL):
python3 $P/rigfit.py grid $O/frames/iv03_t0007.5230.jpg --out $O/grid --crop 170,120,300,270 --zoom 3
#    preencher camera {az, elev, mirror_x}, anim_t0, frames[].keypoints/hidden, events[] (ver template)
# 4. Comparar a animação existente (1ª rodada) e alinhar o tempo:
python3 $P/rigfit.py compare ann.json --anim becomelm-mod/src/main/resources/assets/becomelm/animations/lm.animation.json:flip --out $O/cmp --warp
# 5. Plano novo a partir da referência (quando não há animação):
python3 $P/rigfit.py plan ann.json --out $O/plan.json --emit $O/plan.animation.json --name nova
# 6. 2ª rodada: reajusta só os quadros acima da tolerância e compara antes/depois:
python3 $P/rigfit.py correct ann.json --anim ARQ:NOME --out $O/corr --warp --tol 8
# 7. Levar para o mod: mudar o GERADOR (build_anims.py / make_anims.py) com os valores, regenerar,
#    gravar no jogo e repetir o passo 4 com a gravação (skill forge-expert para o resto).
```

O que cada etapa faz e por quê:
- **Varredura** decodifica uma vez, em cinza 96×54: movimento por quadro, `coverage` (fração de
  pixels que mudam: muito alto = provável câmera mexendo) e mudança de histograma (corte = pico
  ≥ 0,35 e ≥ 3× a vizinhança). Trecho curto (≤ 2 min) no fps nativo; vídeo longo a 10 fps. Também
  mede **quadros repetidos no meio de movimento** (`repeats`): ≥ 20% = câmera lenta/fonte de fps menor.
- **Trechos** = movimento acima de mediana + k·MAD do próprio vídeo (`--k`, padrão 3), cortados nos
  cortes de cena, com o tipo `rapido/medio/lento`.
- **Extração adaptativa**: com `--start/--end`, o trecho inteiro, com densidade pelo movimento local
  (parado 2 qps; mexendo ≥ 20 qps, escala log até 60). Sem trecho: por trecho detectado. Respeita
  `--budget` (reduz tudo na mesma proporção) e sempre pega o quadro logo após cada corte. Os nomes e o
  `frames.json` trazem o **pts exato** de cada quadro (`-copyts` + `showinfo`).
- **Folhas** de 16 quadros (4×4) com tempo e +delta: mostram 60 qps sem abrir centenas de arquivos.
- **Ângulos** são no plano da imagem (0° = direita, 90° = cima), por segmento: tronco (hip→neck),
  cabeça (neck→head_top), braço (shoulder→hand), coxa (hip→knee), canela (knee→foot). Não dependem
  de escala nem de posição. O esqueleto é o do repo (`froggydude-mod/tools/anim/rig.py`, o mesmo do
  BecomeLM), projetado com câmera quase ortográfica do mesmo lado (`az`: 0 = de frente pra ele,
  90 = lado direito dele, −90 = lado esquerdo).
- **Ajuste** (plan/correct): descida por coordenadas nos ossos dos segmentos visíveis; eixo X de
  lado, Z de frente, X e Z em 3/4. Raiz × cintura e raiz × perna se compensam numa vista só: o
  resultado é estimativa (o `residual_deg` diz quanto confiar).

## Armadilhas que já aconteceram (com o número)

- **Giro feito no Java, fora do JSON**: o mortal do BecomeLM gira o corpo com `PoseStack.mulPose`
  (`LmClientEvents.flip`, smoothstep, 9 ticks). Sem declarar isso em `code_rotations`, a comparação
  dá ~96° de "erro" que não existe. Antes de comparar, procure `mulPose`/`rotate` no código da ação.
- **Transição do controlador** (3 ticks): o quadro no meio da troca mistura duas animações. Marque
  `"exclude": "transição ..."` em vez de deixar o erro poluir a média.
- **Câmera lenta da gravação**: `mortal_lado.mp4` tinha 28% dos quadros em movimento repetidos
  (cena parada não conta). Tempo 1:1 deu
  96° de erro médio, `--align` 25,7°, `--warp` 7,3°.
- **Piso de ruído da anotação manual** (640 px, segmentos de 35–60 px): ±5 px ≈ ±5–8°. Erro médio
  abaixo de ~8° é ruído: não "corrija" a animação por isso (`--tol 8`).
- **Vista de frente não mede inclinação**: a flexão do How many days 0:44 é de frente; o mergulho vira
  deslocamento vertical, não ângulo. Use outra referência de lado, ou meça o deslocamento em pixels.
- **Montagem com cortes a cada 0,05–0,1 s** (How many days 40–41 s): são cortes de verdade; o limiar
  relativo à vizinhança evita confundir câmera girando com corte.
- **Fala**: o `/watch` usa o VAD padrão e perde grito/fala curta; `vma.py transcribe` usa
  `--vad_onset 0.2` e pegou a fala inteira no teste. Texto e idioma continuam sendo estimativa do modelo.

## Medidas de referência (container da nuvem, 4 CPUs, 26/10/2026)

| Caso | Tempo | Quadros analisados |
|---|---|---|
| 10 s a 60 qps (How many days 40–50 s), orçamento 300 | 6,5 s | 906 (6 folha + 600 varredura + 300 extraídos) |
| 4,2 s de flexão, orçamento 120 (sai 19–30 qps) | 3,9 s | 378 (6 + 252 + 120) |
| gravação de 11,5 s a 20 qps (mortal), orçamento 80 | 3,2 s | 300 |
| fala de 23 s (WhisperX small, CPU) | 19,5 s | — |
| compare --warp, 4 quadros anotados | 0,34 s | — |
| correct (2ª rodada) | 0,4–0,9 s | — |

Os testes automáticos estão em `.claude/tests/test_video_motion.py` (vídeo sintético com movimento,
corte e bipe conhecidos + circuito fechado do esqueleto).
