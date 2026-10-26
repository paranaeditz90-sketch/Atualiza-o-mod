#!/bin/bash
# Refaz do zero os candidatos de som de osso (os 6 que o LM avaliou em 26/10).
# uso: refazer_ossos.sh pasta_entrada pasta_saida
#   pasta_entrada tem os arquivos do LM (NÃO vão pro GitHub, o repositório é público):
#     horror.mp4   gravação de tela do começo do "MINECRAFT BUT I'M THE HORROR MOD"
#                  (Screenrecorder-2026-09-30-16-01-56, 5,6 s)
#     voicemod.mp3 o crec-crec que o LM fez no Voicemod
#     video.mp4    o vídeo do LM com o som que ele editou no CapCut (lv_0_20261025230010)
#   Os sons livres (CC0) são baixados aqui mesmo.
# Precisa: ffmpeg, curl, unzip, python3 com numpy, scipy, librosa.
set -e
IN=$(realpath "$1"); OUT=$(realpath -m "$2"); T=$(dirname "$(realpath "$0")")
mkdir -p "$OUT"/{dl,work,ossos,lm,lmmix,hm,hmout,hmmix}
cd "$OUT/dl"
# CC0: "Deep bone crack/break SFX" (Zane Little Music, OpenGameArt) e "Bones cracking" #0596 (Joseph Sardin, BigSoundBank)
[ -f deep_breaks.zip ] || curl -sSLo deep_breaks.zip https://opengameart.org/sites/default/files/deep_breaks.zip
[ -f 0596.mp3 ] || curl -sSLo 0596.mp3 https://bigsoundbank.com/UPLOAD/mp3/0596.mp3
mkdir -p x && unzip -oq deep_breaks.zip -d x
for i in $(seq 1 10); do
  f=$(find x -name "Deep Break $i.wav" | head -1)
  ffmpeg -nostdin -v error -y -i "$f" -ac 1 -ar 44100 -sample_fmt s16 "$OUT/work/oga_$i.wav"
done
ffmpeg -nostdin -v error -y -i 0596.mp3 -ac 1 -ar 44100 -sample_fmt s16 "$OUT/work/bsb.wav"
python3 -I "$T/segment.py" "$OUT/work/bsb.wav" "$OUT/work/snaps" snap 0.18 450 > /dev/null
# grave / seco / misto + a transformação de cada um
python3 -I "$T/mix.py" "$OUT/work" "$OUT/ossos" > /dev/null
# os sons do LM debaixo do misto (o 3 e o 4 da primeira rodada)
ffmpeg -nostdin -v error -y -i "$IN/voicemod.mp3" -ac 1 -ar 44100 -sample_fmt s16 "$OUT/lm/voicemod.wav"
ffmpeg -nostdin -v error -y -i "$IN/video.mp4" -vn -ac 1 -ar 44100 -sample_fmt s16 "$OUT/lm/lv_audio.wav"
python3 -I "$T/mix_lm.py" "$OUT/lm" "$OUT/ossos" "$OUT/lmmix" > /dev/null
# pente fino no começo do horror mod (0 a 3,4 s: ele se contorcendo, gemido de dor + ossos)
ffmpeg -nostdin -v error -y -i "$IN/horror.mp4" -vn -ac 1 -ar 44100 -sample_fmt s16 -t 3.4 "$OUT/hm/ini.wav"
python3 -I "$T/hm_bones.py" "$OUT/hm/ini.wav" "$OUT/hmout" 0.5
# meio estourado: o 4, o 5 (misto + ossos do horror) e o estalo das costelas
python3 -I "$T/mix_hm.py" "$OUT/hmout" "$OUT/lmmix" "$OUT/ossos" "$OUT/hmmix" > /dev/null
cp "$OUT/hmout/hm_original.wav" "$OUT/1_comeco_original.wav"
cp "$OUT/hmout/ossos_hm_limpo.wav" "$OUT/2_pente_fino_so_ossos.wav"
cp "$OUT/hmout/ossos_hm_meio_estourado.wav" "$OUT/3_pente_fino_meio_estourado.wav"
cp "$OUT/hmmix/4_meio_estourado.wav" "$OUT/4_teu_4_meio_estourado.wav"
cp "$OUT/hmmix/5_misto_mais_horror_meio_estourado.wav" "$OUT/5_misto_mais_ossos_horror_meio_estourado.wav"
cp "$OUT/hmmix/6_estalo_costelas_meio_estourado.wav" "$OUT/6_estalo_costelas_meio_estourado.wav"
ls "$OUT"/*.wav
