# uso: python3 -I pacote_osso5.py 5_misto_mais_horror_meio_estourado.wav ossos_hm_limpo.wav pasta_ossos pasta_saida
# O pacote pessoal com o osso 5 (o LM escolheu em 26/10):
#   contort  = o 5 inteiro (misto CC0 + crec-crec do horror mod, meio estourado), com 0,1 s de
#              silêncio na frente: o mod toca no tick 8 e o 5 foi montado pra começar no tick 10
#   crack    = 6 estalos soltos no mesmo estilo (um misto CC0 + um pedaço do crec-crec, meio estourado)
import sys
import os
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt, resample_poly

osso5, hm, work, out = sys.argv[1:5]
os.makedirs(out, exist_ok=True)
SR = 44100
MEIO = 3.5


def load(path):
    sr, x = wavfile.read(path)
    assert sr == SR, path
    x = x.astype(np.float64) / 32768.0
    return x.mean(axis=1) if x.ndim > 1 else x


def save(name, x, peak_db=-1.0):
    x = x / (np.max(np.abs(x)) + 1e-12) * 10 ** (peak_db / 20)
    wavfile.write(os.path.join(out, name), SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))


def rms(x):
    return np.sqrt(np.mean(x ** 2)) + 1e-12


def attack(x):
    hop = int(SR * 0.005)
    env = [np.sqrt(np.mean(x[i:i + hop] ** 2)) for i in range(0, len(x) - hop, hop)]
    return int(np.argmax(env)) * hop


def pitch(x, factor):
    return resample_poly(x, 100, int(round(100 * factor)))


def tail_fade(x, ms=60):
    n = min(int(SR * ms / 1000), len(x) // 3)
    x = x.copy()
    x[-n:] *= np.linspace(1, 0, n)
    return x


def mix(deep, snap, snap_gain=0.75, snap_pitch=0.89):
    d = load(os.path.join(work, deep))
    s = pitch(load(os.path.join(work, snap)), snap_pitch)
    at = max(0, attack(d) - int(SR * 0.010))
    y = np.zeros(max(len(d), at + len(s)))
    y[:len(d)] += d / np.max(np.abs(d)) * 0.85
    y[at:at + len(s)] += s / np.max(np.abs(s)) * snap_gain
    return tail_fade(y)


def estourar(x, drive):
    x = x / (np.max(np.abs(x)) + 1e-12)
    body = sosfilt(butter(2, [180, 900], 'bandpass', fs=SR, output='sos'), x)
    x = x + body * 0.8
    return np.tanh(x * drive) / np.tanh(drive)


# a transformação: o 5 como o LM ouviu, 0,1 s mais tarde
five = load(osso5)
save('osso5_transformacao.wav', np.concatenate([np.zeros(int(0.1 * SR)), five]))

# os estalos soltos: os mesmos 6 mistos da sequência do 5, cada um com um fiapo diferente
# (o mais denso que sobrar) do crec-crec. Curtos (~0,3 s): no "torto" eles tocam a cada
# 0,4-1 s e, compridos, viravam um crec-crec sem parar (os antigos tinham 0,05 s)
SNAP = 0.28
pool = [mix('oga_1.wav', 'snaps/snap_04.wav'), mix('oga_7.wav', 'snaps/snap_17.wav'),
        mix('oga_8.wav', 'snaps/snap_18.wav'), mix('oga_2.wav', 'snaps/snap_05.wav'),
        mix('oga_3.wav', 'snaps/snap_19.wav'), mix('oga_10.wav', 'snaps/snap_29.wav')]
cut = []
for one in pool:
    a = max(0, attack(one) - int(0.01 * SR))
    one = one[a:a + int(SNAP * SR)].copy()
    one[-int(0.08 * SR):] *= np.linspace(1, 0, int(0.08 * SR))
    cut.append(one)
pool = cut
crackle = load(hm)
n, hop = int(0.15 * SR), int(0.02 * SR)
starts = sorted(range(0, len(crackle) - n, hop), key=lambda i: -rms(crackle[i:i + n]))
taken = []
for i in starts:
    if all(abs(i - j) >= n // 2 for j in taken):
        taken.append(i)
    if len(taken) == len(pool):
        break
for k, (one, at) in enumerate(zip(pool, taken), 1):
    one = one / np.max(np.abs(one))
    piece = crackle[at:at + n].copy()
    piece[:int(0.01 * SR)] *= np.linspace(0, 1, int(0.01 * SR))
    piece[-int(0.08 * SR):] *= np.linspace(1, 0, int(0.08 * SR))
    piece = piece / np.max(np.abs(piece)) * 0.55
    y = np.zeros(max(len(one), int(0.03 * SR) + len(piece)))
    y[:len(one)] += one
    y[int(0.03 * SR):int(0.03 * SR) + len(piece)] += piece
    y[-int(0.04 * SR):] *= np.linspace(1, 0, int(0.04 * SR))
    save('osso5_estalo%d.wav' % k, estourar(y, MEIO))
    print('estalo %d: crec-crec de %.2f s' % (k, at / SR))
