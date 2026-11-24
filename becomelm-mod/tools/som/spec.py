# uso: python3 -I spec.py saida.png arquivo.wav [arquivo2.wav ...]
# Espectrograma (0-12 kHz, log) de cada arquivo, um embaixo do outro, com o nome.
import sys
import numpy as np
from scipy.io import wavfile
from scipy.signal import spectrogram
from PIL import Image, ImageDraw

out = sys.argv[1]
rows = []
for path in sys.argv[2:]:
    sr, x = wavfile.read(path)
    x = x.astype(np.float64) / 32768.0
    f, t, s = spectrogram(x, sr, nperseg=1024, noverlap=896)
    keep = f <= 12000
    s = 10 * np.log10(s[keep] + 1e-12)
    s = np.clip((s - (s.max() - 70)) / 70, 0, 1)
    img = (s[::-1] * 255).astype(np.uint8)
    im = Image.fromarray(img).resize((int(len(t) * 4), 160))
    rgb = Image.merge('RGB', (im, im.point(lambda v: v * 0.6), im.point(lambda v: 255 - v if v > 0 else 0)))
    d = ImageDraw.Draw(rgb)
    d.text((4, 4), path.split('/')[-1] + '  %.2fs' % (len(x) / sr), fill=(255, 255, 0))
    rows.append(rgb)
w = max(r.width for r in rows)
sheet = Image.new('RGB', (w, sum(r.height for r in rows)), (0, 0, 0))
y = 0
for r in rows:
    sheet.paste(r, (0, y))
    y += r.height
sheet.save(out)
print(sheet.size)
