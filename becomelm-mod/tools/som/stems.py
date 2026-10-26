# uso: python3 -I stems.py pasta_com_vocals_e_no_vocals
# Energia (dB) da voz e do resto em janelas de 0,25 s, e os estalos (picos) do resto.
import sys, os
import numpy as np
from scipy.io import wavfile
from scipy.signal import find_peaks

d = sys.argv[1]


def load(n):
    sr, x = wavfile.read(os.path.join(d, n))
    x = x.astype(np.float64)
    if x.ndim > 1:
        x = x.mean(axis=1)
    return sr, x / 32768.0


sr, v = load('vocals.wav')
_, r = load('no_vocals.wav')
w = int(sr * 0.25)
for i in range(0, len(v) - w, w):
    ev = 20 * np.log10(np.sqrt(np.mean(v[i:i + w] ** 2)) + 1e-9)
    er = 20 * np.log10(np.sqrt(np.mean(r[i:i + w] ** 2)) + 1e-9)
    print('%5.2fs voz %6.1f dB  resto %6.1f dB  %s' % (i / sr, ev, er, '#' * max(0, int((er + 60) / 2))))
hop = int(sr * 0.002)
env = np.array([np.max(np.abs(r[i:i + hop])) for i in range(0, len(r) - hop, hop)])
pk, pr = find_peaks(env, height=np.max(env) * 0.2, distance=int(0.05 / 0.002))
print('estalos no resto:', ' '.join('%.3f(%.0f)' % (p * 0.002, 20 * np.log10(env[p])) for p in pk))
