# uso: python3 -I segment.py entrada.wav pasta_saida prefixo [limiar=0.2] [max_ms=450]
# Corta cada estalo (pico do envelope) num wav próprio: começa 8 ms antes do ataque e vai
# até o próximo estalo (ou max_ms), com fade de 3 ms na entrada e 40 ms na saída.
import sys
import os
import numpy as np
from scipy.io import wavfile
from scipy.signal import find_peaks

src, out, prefix = sys.argv[1], sys.argv[2], sys.argv[3]
thr = float(sys.argv[4]) if len(sys.argv) > 4 else 0.2
max_ms = float(sys.argv[5]) if len(sys.argv) > 5 else 450.0
sr, x = wavfile.read(src)
x = x.astype(np.float64) / 32768.0
hop = int(sr * 0.002)
env = np.array([np.max(np.abs(x[i:i + hop])) for i in range(0, len(x) - hop, hop)])
pk, _ = find_peaks(env, height=np.max(env) * thr, distance=int(0.12 / 0.002))
os.makedirs(out, exist_ok=True)
starts = []
for p in pk:
    # volta até o envelope ficar abaixo de 8% do pico (o começo do estalo), no máximo 60 ms
    s = p
    while s > 0 and p - s < int(0.06 / 0.002) and env[s] > env[p] * 0.08:
        s -= 1
    starts.append(max(0, s * hop - int(sr * 0.008)))
for k, s in enumerate(starts):
    end = starts[k + 1] - int(sr * 0.01) if k + 1 < len(starts) else len(x)
    end = min(end, s + int(sr * max_ms / 1000))
    seg = x[s:end].copy()
    if len(seg) < int(sr * 0.05):
        continue
    fi = int(sr * 0.003)
    fo = min(int(sr * 0.04), len(seg) // 3)
    seg[:fi] *= np.linspace(0, 1, fi)
    seg[-fo:] *= np.linspace(1, 0, fo)
    pkdb = 20 * np.log10(np.max(np.abs(seg)) + 1e-12)
    name = '%s_%02d.wav' % (prefix, k)
    wavfile.write(os.path.join(out, name), sr, (np.clip(seg, -1, 1) * 32767).astype(np.int16))
    print('%s em %.3fs dur %.0fms pico %.1fdB' % (name, s / sr, len(seg) / sr * 1000, pkdb))
