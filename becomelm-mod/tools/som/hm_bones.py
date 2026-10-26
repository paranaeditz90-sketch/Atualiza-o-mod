# uso: python3 -I hm_bones.py começo.wav pasta_saida
# "Pente fino" no começo do MINECRAFT BUT I'M THE HORROR MOD: tira o gemido (tom de ~360 Hz e
# harmônicos) e deixa só o crec-crec dos ossos.
#   1. separa tom x batida (HPSS) e fica só com a batida
#   2. passa-alta em 700 Hz (onde mora o gemido)
#   3. portão: só abre quando o agudo (2-12 kHz) salta acima do que tem em volta
# Também escreve a versão "meio estourada" (saturada) e a "bem estourada".
import sys
import os
import numpy as np
import librosa
from scipy.io import wavfile
from scipy.signal import butter, sosfiltfilt, sosfilt
from scipy.ndimage import median_filter

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
SR = 44100
y, _ = librosa.load(src, sr=SR, mono=True)


def save(name, x, peak_db=-1.0):
    x = x / (np.max(np.abs(x)) + 1e-12) * 10 ** (peak_db / 20)
    wavfile.write(os.path.join(out, name), SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))


def band(x, lo, hi):
    return sosfiltfilt(butter(4, [lo, hi], 'bandpass', fs=SR, output='sos'), x)


def tone_db(x, freqs=(360, 720, 1300)):
    """Quanto sobrou do gemido: energia nos harmônicos dele (0,5-2,7 s)."""
    seg = x[int(0.5 * SR):int(2.7 * SR)]
    f = np.abs(np.fft.rfft(seg * np.hanning(len(seg)))) ** 2
    fr = np.fft.rfftfreq(len(seg), 1 / SR)
    e = sum(f[(fr > c - 40) & (fr < c + 40)].sum() for c in freqs)
    return 10 * np.log10(e + 1e-12)


# 1. tom x batida
D = librosa.stft(y, n_fft=2048, hop_length=256)
H, P = librosa.decompose.hpss(D, margin=(1.0, 4.0), kernel_size=(31, 31))
p = librosa.istft(P, hop_length=256, length=len(y))
# a camada do chiado: batida "solta" (margem 2) pra não perder o crec-crec fraco
_, P2 = librosa.decompose.hpss(D, margin=(1.0, 2.0), kernel_size=(31, 31))
p2 = librosa.istft(P2, hop_length=256, length=len(y))
# 2. passa-alta
hp = butter(4, 700, 'highpass', fs=SR, output='sos')
p = sosfiltfilt(hp, p)
p2 = sosfiltfilt(hp, p2)
# 3. portão pelo envelope do agudo (ataque 1 ms, soltura 45 ms)
hf = np.abs(band(y, 2000, 12000))
att, rel = np.exp(-1 / (0.001 * SR)), np.exp(-1 / (0.045 * SR))
env = np.zeros_like(hf)
e = 0.0
for i, v in enumerate(hf):
    e = att * e + (1 - att) * v if v > e else rel * e + (1 - rel) * v
    env[i] = e
edb = 20 * np.log10(env + 1e-9)
step = int(0.005 * SR)
floor = median_filter(edb[::step], size=int(0.25 / 0.005), mode='nearest')
floor = np.interp(np.arange(len(edb)), np.arange(0, len(edb), step)[:len(floor)], floor)
lp = butter(1, 120, 'lowpass', fs=SR, output='sos')
# estouros: abre de 0 a 1 entre 2 e 10 dB acima do fundo
g = sosfilt(lp, np.clip((edb - floor - 2.0) / 8.0, 0, 1) ** 1.5)
# chiado: portão leve (nunca fecha de todo; abre mais quando o agudo sobe)
g2 = sosfilt(lp, 0.35 + 0.65 * np.clip((edb - floor + 3.0) / 9.0, 0, 1))
CHIADO = float(sys.argv[3]) if len(sys.argv) > 3 else 0.5
A, B = p * g, p2 * g2
A /= np.max(np.abs(A)) + 1e-12
B /= np.max(np.abs(B)) + 1e-12
clean = A + B * CHIADO
# o começo do gemido (0,37-0,45 s) e o fim (3,35 s) ficam de fora
START = int(0.45 * SR)
fi, fo = int(0.01 * SR), int(0.05 * SR)
end = min(len(clean), int(3.38 * SR))
clean[end - fo:end] *= np.linspace(1, 0, fo)
clean = clean[START:end]
clean[:fi] *= np.linspace(0, 1, fi)
y_cut = y[START:end]
save('hm_original.wav', y_cut)
save('ossos_hm_limpo.wav', clean)
save('ossos_hm_so_estouros.wav', A[START:end])
save('ossos_hm_so_chiado.wav', B[START:end])
pad = lambda x: np.pad(x, (START, 0))
for nome, x in [('limpo', clean), ('estouros', A[START:end]), ('chiado', B[START:end])]:
    # gemido relativo ao total do próprio arquivo (quanto do som é o tom dele)
    rel_o = tone_db(y) - 10 * np.log10(np.sum(y[int(0.5 * SR):int(2.7 * SR)] ** 2) + 1e-12)
    xx = pad(x)
    rel_x = tone_db(xx) - 10 * np.log10(np.sum(xx[int(0.5 * SR):int(2.7 * SR)] ** 2) + 1e-12)
    print('%-9s gemido no som: original %.1f dB -> %.1f dB (%.1f dB a menos)' % (nome, rel_o, rel_x, rel_o - rel_x))


def estourar(x, drive, bite=0.0):
    """Saturação: ganho alto num tanh (o "estouro" de microfone) + um corpo no médio-grave
    (a distorção cria harmônicos e o estalo ganha peso). bite > 0 corta duro um pouco."""
    x = x / (np.max(np.abs(x)) + 1e-12)
    body = sosfilt(butter(2, [180, 900], 'bandpass', fs=SR, output='sos'), x)
    x = x + body * 0.8
    y2 = np.tanh(x * drive) / np.tanh(drive)
    if bite:
        c = 1.0 - bite
        y2 = np.clip(y2, -c, c) / c
    return y2


save('ossos_hm_meio_estourado.wav', estourar(clean, 4.0))
save('ossos_hm_bem_estourado.wav', estourar(clean, 9.0, 0.25))
print('duração %.2fs' % (len(clean) / SR))
