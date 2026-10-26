# uso: python3 -I mix_hm.py pasta_hm pasta_lm_mix pasta_ossos pasta_saida
# "Meio estourado": o 4 (som do vídeo do LM + 3 misto) e o osso do horror mod (pente fino)
# no lugar do som do LM, debaixo dos estalos grandes do misto.
import sys
import os
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

hm, lmmix, bones, out = sys.argv[1:5]
os.makedirs(out, exist_ok=True)
SR = 44100


def load(path):
    sr, x = wavfile.read(path)
    assert sr == SR, path
    x = x.astype(np.float64) / 32768.0
    return x.mean(axis=1) if x.ndim > 1 else x


def rms(x):
    return np.sqrt(np.mean(x ** 2)) + 1e-12


def save(name, x, peak_db=-1.0):
    x = x / (np.max(np.abs(x)) + 1e-12) * 10 ** (peak_db / 20)
    wavfile.write(os.path.join(out, name), SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))


def estourar(x, drive, bite=0.0):
    """Mesmo "estouro" do hm_bones.py: corpo no médio-grave + tanh com ganho alto."""
    x = x / (np.max(np.abs(x)) + 1e-12)
    body = sosfilt(butter(2, [180, 900], 'bandpass', fs=SR, output='sos'), x)
    x = x + body * 0.8
    y = np.tanh(x * drive) / np.tanh(drive)
    if bite:
        c = 1.0 - bite
        y = np.clip(y, -c, c) / c
    return y


MEIO = 3.5
four = load(os.path.join(lmmix, 'transformacao_video_mais_misto.wav'))
save('4_meio_estourado.wav', estourar(four, MEIO))

# o 5: misto + osso do horror mod (o crec-crec dele cobre de 0,45 a 3,38 s, igual no vídeo;
# os estalos do misto vão de 0,6 a ~3,6 s)
seq = load(os.path.join(bones, 'transformacao_misto.wav'))
crackle = load(os.path.join(hm, 'ossos_hm_limpo.wav'))
seq = seq / rms(seq)
full = np.zeros(max(len(seq), int(0.45 * SR) + len(crackle)))
full[:len(seq)] += seq
a = int(0.45 * SR)
# 4 dB abaixo dos estalos grandes: aparece bem mais que o fundo do 4 (que ia a -7 dB)
full[a:a + len(crackle)] += crackle / rms(crackle) * 10 ** (-4 / 20)
save('5_misto_mais_horror.wav', full)
save('5_misto_mais_horror_meio_estourado.wav', estourar(full, MEIO))

# o estalo das costelas: misto_1 + 0,5 s do trecho mais denso do crec-crec do horror mod
one = load(os.path.join(bones, 'misto_1.wav'))
one = one / np.max(np.abs(one))
n, hop = int(0.5 * SR), int(0.02 * SR)
at = max(range(0, len(crackle) - n, hop), key=lambda i: rms(crackle[i:i + n]))
piece = crackle[at:at + n].copy()
piece[:int(0.01 * SR)] *= np.linspace(0, 1, int(0.01 * SR))
piece[-int(0.2 * SR):] *= np.linspace(1, 0, int(0.2 * SR))
piece = piece / np.max(np.abs(piece)) * 0.55
y = np.zeros(max(len(one), int(0.05 * SR) + len(piece)))
y[:len(one)] += one
y[int(0.05 * SR):int(0.05 * SR) + len(piece)] += piece
y[-int(0.06 * SR):] *= np.linspace(1, 0, int(0.06 * SR))
save('6_estalo_costelas_meio_estourado.wav', estourar(y, MEIO))
print('crec-crec das costelas de %.2fs do pente fino' % (at / SR))
for f in sorted(os.listdir(out)):
    print(f)
