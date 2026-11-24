# uso: python3 -I mix.py pasta_trabalho pasta_saida
# Monta os candidatos de osso quebrando (tudo CC0):
#   grave_N    = "Deep Break" do OpenGameArt (Zane Little Music) como veio
#   seco_N     = estalo de aipo do BigSoundBank #0596 (Joseph Sardin)
#   misto_N    = grave + estalo seco (um tom abaixo) em cima do estalo mais forte do grave
#   transformacao_ossos = os mistos no tempo dos estalos da transformação do BecomeLM
import sys
import os
import numpy as np
from scipy.io import wavfile
from scipy.signal import resample_poly

work, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
SR = 44100


def load(name):
    sr, x = wavfile.read(os.path.join(work, name))
    assert sr == SR
    return x.astype(np.float64) / 32768.0


def save(name, x, peak_db=-1.0):
    x = x / (np.max(np.abs(x)) + 1e-12) * 10 ** (peak_db / 20)
    wavfile.write(os.path.join(out, name), SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))


def attack(x):
    """Onde está o estalo mais forte (envelope de 5 ms)."""
    hop = int(SR * 0.005)
    env = [np.sqrt(np.mean(x[i:i + hop] ** 2)) for i in range(0, len(x) - hop, hop)]
    return int(np.argmax(env)) * hop


def pitch(x, factor):
    """factor < 1 = mais grave (e mais longo)."""
    up, down = int(round(100 * factor)), 100
    # resample_poly(x, down, up): toca mais devagar quando factor < 1
    return resample_poly(x, down, up)


def tail_fade(x, ms=60):
    n = min(int(SR * ms / 1000), len(x) // 3)
    x = x.copy()
    x[-n:] *= np.linspace(1, 0, n)
    return x


def mix(deep, snap, snap_gain=0.75, snap_pitch=0.89):
    d = load(deep)
    s = pitch(load(snap), snap_pitch)
    at = attack(d) - int(SR * 0.010)
    at = max(0, at)
    n = max(len(d), at + len(s))
    y = np.zeros(n)
    y[:len(d)] += d / np.max(np.abs(d)) * 0.85
    y[at:at + len(s)] += s / np.max(np.abs(s)) * snap_gain
    return tail_fade(y)


GRAVE = ['oga_1.wav', 'oga_6.wav', 'oga_2.wav']
SECO = ['snaps/snap_04.wav', 'snaps/snap_17.wav', 'snaps/snap_06.wav']
MISTO = [('oga_1.wav', 'snaps/snap_04.wav'), ('oga_7.wav', 'snaps/snap_17.wav'), ('oga_8.wav', 'snaps/snap_18.wav')]

for i, g in enumerate(GRAVE, 1):
    save('grave_%d.wav' % i, tail_fade(load(g)))
for i, s in enumerate(SECO, 1):
    save('seco_%d.wav' % i, tail_fade(load(s)))
mixes = []
for i, (d, s) in enumerate(MISTO, 1):
    m = mix(d, s)
    mixes.append(m)
    save('misto_%d.wav' % i, m)

# a transformação do BecomeLM: estalos nos ticks 22, 29, 38, 43, 44, 51, 52, 63, 67, 69, 72
# (20 ticks por segundo); 0,5 s de silêncio antes, um pouco de variação em cada estalo
ticks = [22, 29, 38, 43, 44, 51, 52, 63, 67, 69, 72]


def sequence(pool, name, seed=7):
    rng = np.random.default_rng(seed)
    seq = np.zeros(int(SR * 4.6))
    for k, t in enumerate(ticks):
        snd = pool[k % len(pool)]
        snd = pitch(snd, 1.0 + rng.uniform(-0.07, 0.07))
        g = rng.uniform(0.75, 1.0)
        at = max(0, int(SR * (t / 20.0 - 0.5)) - attack(snd))
        seq[at:at + len(snd)] += snd[:len(seq) - at] * g
    save(name, tail_fade(seq, 200), -1.0)


grave_pool = [tail_fade(load(g)) for g in ['oga_1.wav', 'oga_6.wav', 'oga_2.wav', 'oga_7.wav', 'oga_8.wav', 'oga_3.wav']]
seco_pool = [pitch(tail_fade(load('snaps/snap_%02d.wav' % i)), 0.85) for i in (4, 17, 6, 18, 5, 19)]
misto_pool = mixes + [mix('oga_2.wav', 'snaps/snap_05.wav'), mix('oga_3.wav', 'snaps/snap_19.wav'),
                      mix('oga_10.wav', 'snaps/snap_29.wav')]
sequence(grave_pool, 'transformacao_grave.wav')
sequence(seco_pool, 'transformacao_seco.wav')
sequence(misto_pool, 'transformacao_misto.wav')
for f in sorted(os.listdir(out)):
    print(f)
