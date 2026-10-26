# uso: python3 -I analyze.py arquivo.wav [arquivo2.wav ...]
# Mostra, pra cada som: duração, pico, onde estão os estalos (picos do envelope),
# o "brilho" (centroide espectral) e quanto da energia está em grave/médio/agudo.
import sys
import numpy as np
from scipy.io import wavfile
from scipy.signal import find_peaks


def load(path):
    sr, x = wavfile.read(path)
    x = x.astype(np.float64)
    if x.ndim > 1:
        x = x.mean(axis=1)
    m = np.max(np.abs(x))
    if m > 0 and np.issubdtype(type(m), np.floating) and m > 1.5:
        x = x / 32768.0
    return sr, x


def bands(sr, x):
    f = np.abs(np.fft.rfft(x * np.hanning(len(x)))) ** 2
    fr = np.fft.rfftfreq(len(x), 1 / sr)
    tot = f.sum() + 1e-12
    lo = f[fr < 250].sum() / tot
    mid = f[(fr >= 250) & (fr < 2500)].sum() / tot
    hi = f[fr >= 2500].sum() / tot
    cen = (f * fr).sum() / tot
    return lo, mid, hi, cen


for path in sys.argv[1:]:
    sr, x = load(path)
    hop = int(sr * 0.005)
    env = np.array([np.sqrt(np.mean(x[i:i + hop] ** 2)) for i in range(0, len(x) - hop, hop)])
    peak_db = 20 * np.log10(np.max(np.abs(x)) + 1e-12)
    pk, _ = find_peaks(env, height=np.max(env) * 0.25, distance=int(0.04 / 0.005))
    times = ', '.join('%.3f' % (p * 0.005) for p in pk[:14])
    lo, mid, hi, cen = bands(sr, x)
    # quanto tempo até cair 30 dB do pico (o "rabo")
    top = np.argmax(env)
    thr = env[top] * 10 ** (-30 / 20)
    tail = top
    while tail < len(env) - 1 and env[tail] > thr:
        tail += 1
    print('%-28s %.2fs pico %5.1fdB estalos[%d] %s | grave %.0f%% medio %.0f%% agudo %.0f%% centro %4.0fHz | ataque %.3fs rabo %.2fs'
          % (path.split('/')[-1][:28], len(x) / sr, peak_db, len(pk), times, lo * 100, mid * 100, hi * 100, cen,
             top * 0.005, (tail - top) * 0.005))
