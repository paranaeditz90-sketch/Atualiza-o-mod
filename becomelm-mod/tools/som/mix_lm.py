# uso: python3 -I mix_lm.py pasta_lm pasta_ossos pasta_saida
# Junta o som de osso do LM (o "crec-crec" contínuo) com o 3 misto (os estalos grandes):
# o dele vira o fundo moendo e os mistos estouram por cima, no tempo da transformação.
import sys
import os
import numpy as np
from scipy.io import wavfile

lm, bones, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(out, exist_ok=True)
SR = 44100


def load(path):
    sr, x = wavfile.read(path)
    assert sr == SR, path
    return x.astype(np.float64) / 32768.0


def rms(x):
    return np.sqrt(np.mean(x ** 2)) + 1e-12


def fades(x, fin, fout):
    x = x.copy()
    a, b = int(SR * fin), int(SR * fout)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def save(name, x, peak_db=-1.0):
    x = x / (np.max(np.abs(x)) + 1e-12) * 10 ** (peak_db / 20)
    wavfile.write(os.path.join(out, name), SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))


def densest(x, dur):
    """O trecho de dur segundos com mais estalos (mais energia)."""
    n = int(SR * dur)
    hop = int(SR * 0.05)
    best, at = -1, 0
    for i in range(0, len(x) - n, hop):
        e = rms(x[i:i + n])
        if e > best:
            best, at = e, i
    return x[at:at + n], at / SR


misto_seq = load(os.path.join(bones, 'transformacao_misto.wav'))
misto_one = load(os.path.join(bones, 'misto_1.wav'))
beds = {'voicemod': load(os.path.join(lm, 'voicemod.wav')), 'video': load(os.path.join(lm, 'lv_audio.wav'))}

for name, bed_src in beds.items():
    # a transformação: os estalos vão de 0,6 s a ~3,6 s; o fundo cobre de 0,3 a 4,2 s
    bed, at = densest(bed_src, 3.9)
    bed = fades(bed, 0.35, 0.6)
    seq = misto_seq / rms(misto_seq)
    full = np.zeros(len(seq))
    start = int(SR * 0.3)
    end = min(len(full), start + len(bed))
    # o fundo uns 7 dB abaixo dos estalos grandes (aparece, mas não engole o "CRACK")
    full[start:end] += bed[:end - start] / rms(bed) * 10 ** (-7 / 20)
    full += seq
    save('transformacao_%s_mais_misto.wav' % name, full)
    # o estalo sozinho (as costelas da finalização): o misto + 0,5 s do moído dele em volta
    one = misto_one / np.max(np.abs(misto_one))
    piece, _ = densest(bed_src, 0.55)
    piece = fades(piece, 0.02, 0.25) / np.max(np.abs(piece)) * 0.5
    y = np.zeros(max(len(one), int(SR * 0.08) + len(piece)))
    y[:len(one)] += one
    y[int(SR * 0.08):int(SR * 0.08) + len(piece)] += piece
    save('estalo_%s_mais_misto.wav' % name, fades(y, 0, 0.08))
    print(name, 'fundo de %.2fs' % at)
for f in sorted(os.listdir(out)):
    print(f)
