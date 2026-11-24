# uso: python3 -I concat_marks.py saida.wav marks.txt gap_s "etiqueta|arquivo.wav" ...
# Junta os sons com silêncio entre eles e escreve "início fim etiqueta" de cada um.
import sys
import numpy as np
from scipy.io import wavfile
out, marks, gap = sys.argv[1], sys.argv[2], float(sys.argv[3])
SR = 44100
parts, lines, t = [np.zeros(int(SR * 0.5))], [], 0.5
for item in sys.argv[4:]:
    label, path = item.split('|', 1)
    sr, x = wavfile.read(path)
    assert sr == SR, path
    x = x.astype(np.float64) / 32768.0
    if x.ndim > 1:
        x = x.mean(axis=1)
    lines.append('%.2f %.2f %s' % (t, t + len(x) / SR, label))
    parts += [x, np.zeros(int(SR * gap))]
    t += len(x) / SR + gap
y = np.concatenate(parts)
wavfile.write(out, SR, (np.clip(y, -1, 1) * 32767).astype(np.int16))
open(marks, 'w').write('\n'.join(lines) + '\n')
print('\n'.join(lines))
