"""O ícone do Become HIM... (logo da lista de mods e icon.png): uma cabeça só, rachada no meio, metade Herobrine e metade
FroggyDude (a cara de verdade dele, a skin que o LM mandou em 18/11), com a rachadura verde brilhando e a pele do
Herobrine esverdeando perto do corte; "BECOME HIM..." em letra de pixel. Ideia do LM (a versão do ChatGPT: o jogador
virando o FroggyDude), refeita em pixel art de grade única. As cabeças são cubos com a textura 8x8 projetada.

Uso: python3 tools/icone.py   (grava icon.png e src/main/resources/becomehim_logo.png, 512x512)
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W = 512
U = 4  # 1 "pixel" do resto da arte = 4 px
C = {
 'G': (98, 168, 58), 'g': (74, 132, 44), 'd': (52, 98, 32), 'K': (14, 14, 16), 'W': (240, 240, 240),
 'B': (64, 180, 240), 'O': (228, 130, 66), 'o': (178, 88, 40), 'Y': (252, 204, 44), 'y': (210, 152, 24),
 'H': (54, 36, 20), 'h': (40, 26, 14), 'S': (184, 130, 96), 's': (154, 104, 74), 'N': (132, 86, 60),
 'M': (94, 56, 38), 'T': (0, 172, 172), 't': (0, 128, 132), 'J': (26, 28, 32), 'j': (44, 46, 52),
 'L': (228, 228, 228), 'R': (30, 112, 92),
}
# texturas 8x8 (+ coroa): frente, lado, topo
FROG_FRONT = ["gGGGGGGg", "GKWKGKWK", "GBKKGBKK", "GBGGGBGG", "GOOOOOOG", "GooooooG", "gGGGGGGg", "dggggggd"]
FROG_SIDE = ["gGGGGGGg", "GGGGGGGG", "GGGgGGGG", "GGGGGGgG", "GGGGGGGG", "gGGGGGGG", "GGgGGGGg", "dggggggd"]
FROG_TOP = ["gGGGGGGg"] * 8
CROWN_FRONT = ["Y..YY..Y", "YyYYYYyY"]
CROWN_SIDE = ["Y..YY..Y", "YyYYYYyY"]
STEVE_FRONT = [
 "#2f200d #2b1e0d #2f1f0f #281c0b #241808 #261a0a #2b1e0d #2a1d0d",
 "#2b1e0d #2b1e0d #2f1f0f #2f1f0f #2b1e0d #2b1e0d #2f1f0f #2a1d0d",
 "#2f1f0f #b6896c #bd8e72 #c69680 #bd8b72 #bd8e74 #ac765a #342512",
 "#aa7d66 #b4846d #ad806d #9c725c #bb8972 #9c694c #9c694c #aa7d66",
 "#b4846d #ffffff #ffffff #b57b67 #bb8972 #ffffff #ffffff #aa7d66",
 "#9c6346 #b37b62 #b78272 #6a4030 #6a4030 #be886c #a26a47 #805334",
 "#905e43 #965f40 #774235 #774235 #774235 #774235 #8f5e3e #815339",
 "#6f452c #6d432a #774235 #6a4030 #6a4030 #774235 #83553b #7a4e35",
]
STEVE_SIDE_L = [  # coluna 0 = nuca (cabelo), coluna 7 = perto do rosto (pele)
 "#2b1e0d #2f1f0f #2b1e0d #2a1d0d #2f200d #2b1e0d #2f1f0f #2b1e0d",
 "#2f200d #2a1d0d #2f1f0f #2b1e0d #2b1e0d #2f1f0f #2b1e0d #2f1f0f",
 "#2b1e0d #2f1f0f #2b1e0d #2f1f0f #2b1e0d #2f1f0f #ac765a #b4846d",
 "#2f1f0f #2a1d0d #2b1e0d #2f1f0f #2b1e0d #9c725c #b4846d #aa7d66",
 "#2b1e0d #2b1e0d #2f1f0f #2b1e0d #9c694c #b4846d #aa7d66 #bb8972",
 "#2a1d0d #2f1f0f #2b1e0d #b4846d #9c6346 #aa7d66 #b37b62 #a26a47",
 "#2f1f0f #2b1e0d #9c6346 #905e43 #965f40 #9c6346 #905e43 #965f40",
 "#6d432a #815339 #6f452c #7a4e35 #6d432a #815339 #6f452c #7a4e35",
]
STEVE_TOP = [
 "#2f200d #2b1e0d #2f1f0f #2b1e0d #2a1d0d #2f1f0f #2b1e0d #2f200d",
 "#2b1e0d #2f1f0f #2b1e0d #2f200d #2b1e0d #2b1e0d #2f1f0f #2b1e0d",
 "#2f1f0f #2b1e0d #2a1d0d #2b1e0d #2f1f0f #2b1e0d #2b1e0d #2f1f0f",
 "#2b1e0d #2f200d #2b1e0d #2f1f0f #2b1e0d #2f1f0f #2a1d0d #2b1e0d",
 "#2f1f0f #2b1e0d #2f1f0f #2b1e0d #2f200d #2b1e0d #2b1e0d #2f1f0f",
 "#2b1e0d #2f1f0f #2b1e0d #2a1d0d #2b1e0d #2f1f0f #2b1e0d #2b1e0d",
 "#2f200d #2b1e0d #2f1f0f #2b1e0d #2f1f0f #2b1e0d #2f1f0f #2a1d0d",
 "#2b1e0d #2f1f0f #2b1e0d #2f1f0f #2b1e0d #2a1d0d #2b1e0d #2f1f0f",
]


def proj(p, yaw, pitch, cx, cy, s):
    x, y, z = p
    cyw, syw = math.cos(yaw), math.sin(yaw)
    x, z = x * cyw + z * syw, -x * syw + z * cyw
    cp, sp = math.cos(pitch), math.sin(pitch)
    y, z = y * cp - z * sp, y * sp + z * cp
    return (cx + x * s, cy - y * s, z)


def head(d, cx, cy, s, yaw, pitch, front, side, top, crown=None, side_right=True):
    """Cubo 8x8x8 (meio em 0): frente em z=+4, lado direito (x=+4) ou esquerdo (x=-4), topo em y=+4."""
    quads = []
    shade = {'front': 1.0, 'side': 0.72, 'top': 1.12}
    def face(tex, origin, du, dv, kind, rows=8, top_down=True):
        tex = [row.split() if isinstance(row, str) and ' ' in row else row for row in tex]
        for j in range(rows):
            for i in range(8):
                c = tex[j][i] if isinstance(tex[j], str) else tex[j][i]
                if c == '.':
                    continue
                base = C[c] if len(c) == 1 else tuple(int(c[k:k + 2], 16) for k in (1, 3, 5))
                col = tuple(min(255, int(v * shade[kind])) for v in base)
                jj = j if top_down else rows - 1 - j
                p0 = np.array(origin) + du * i + dv * jj
                pts = [p0, p0 + du, p0 + du + dv, p0 + dv]
                pr = [proj(p, yaw, pitch, cx, cy, s) for p in pts]
                quads.append((sum(q[2] for q in pr) / 4, [(q[0], q[1]) for q in pr], col))
    # frente: da esquerda pra direita (x+), de cima pra baixo (y-)
    face(front, (-4, 4, 4), np.array([1, 0, 0]), np.array([0, -1, 0]), 'front')
    if side_right:
        face(side, (4, 4, 4), np.array([0, 0, -1]), np.array([0, -1, 0]), 'side')
    else:
        face(side, (-4, 4, -4), np.array([0, 0, 1]), np.array([0, -1, 0]), 'side')
    face(top, (-4, 4, -4), np.array([1, 0, 0]), np.array([0, 0, 1]), 'top')
    if crown:
        face(crown[0], (-4, 6, 4.05), np.array([1, 0, 0]), np.array([0, -1, 0]), 'front', rows=2)
        if side_right:
            face(crown[1], (4.05, 6, 4), np.array([0, 0, -1]), np.array([0, -1, 0]), 'side', rows=2)
        else:
            face(crown[1], (-4.05, 6, -4), np.array([0, 0, 1]), np.array([0, -1, 0]), 'side', rows=2)
    for _, pts, col in sorted(quads, key=lambda q: q[0]):
        d.polygon(pts, fill=col)



import random
# ---- a cabeça rachada: esquerda Herobrine, direita FroggyDude
def hexrow(r):
    return r.split()
SF = [hexrow(r) for r in STEVE_FRONT]
# a cara de verdade do FroggyDude (a skin que o LM mandou, 18/11): a coroa fica DENTRO da cara (3 linhas de cima)
FROG_REAL = [
 "#dcb10f #63935d #63935d #dcb10f #dcb10f #63935d #63935d #dcb10f",
 "#dcb10f #dcb10f #dcb10f #ffffff #ffffff #dcb10f #dcb10f #dcb10f",
 "#dcb10f #dcb10f #dcb10f #dcb10f #dcb10f #dcb10f #dcb10f #dcb10f",
 "#63935d #63935d #63935d #63935d #63935d #63935d #63935d #63935d",
 "#f1c2bd #000000 #ffffff #63935d #63935d #f1c2bd #000000 #ffffff",
 "#f4dbd9 #000000 #000000 #63935d #63935d #f4dbd9 #000000 #000000",
 "#f59662 #93d6fd #f59662 #f59662 #f59662 #f59662 #f59662 #93d6fd",
 "#73a36d #cfedfe #63935d #63935d #63935d #63935d #63935d #cfedfe",
]
FR = [hexrow(r) for r in FROG_REAL]
FRONT = [SF[j][:4] + FR[j][4:] for j in range(8)]
# a pele do Herobrine ficando verde perto do corte (a transformação andando)
for (j, i, c) in [(3, 3, "#63935d"), (2, 3, "#5a8a54"), (5, 3, "#63935d"), (6, 3, "#73a36d"), (6, 2, "#4f7d49"),
                  (7, 3, "#63935d"), (4, 3, "#5a8a54"), (7, 2, "#4f7d49")]:
    FRONT[j][i] = c
Y, Yd, Gr, Gd, Gl = "#dcb10f", "#b68f0a", "#63935d", "#4f7d49", "#73a36d"
# lateral direita: a faixa da coroa continua (pontas nas 3 primeiras linhas), o resto verde
SIDE_R = [
 [Y, Gr, Gr, Y, Y, Gr, Gr, Y],
 [Y, Y, Yd, Y, Y, Y, Yd, Y],
 [Yd, Y, Y, Y, Yd, Y, Y, Yd],
 [Gr, Gr, Gl, Gr, Gr, Gr, Gr, Gr],
 [Gr, Gr, Gr, Gr, Gl, Gr, Gr, Gr],
 [Gr, Gl, Gr, Gr, Gr, Gr, Gd, Gr],
 [Gr, Gr, Gr, Gd, Gr, Gr, Gr, Gr],
 [Gd, Gr, Gr, Gr, Gr, Gl, Gr, Gd],
]
# topo: a metade do Herobrine é cabelo; a do FroggyDude é verde com a borda da coroa em volta
HAIR = ["#2b1e0d", "#2f1f0f", "#2b1e0d", "#2f200d"]
TOP = []
for j in range(8):
    row = [HAIR[(i + j) % 4] for i in range(4)]
    for i in range(4, 8):
        row.append(Y if (i == 7 or j in (0, 7)) else (Gr if (i + j) % 3 else Gl))
    TOP.append(row)
TOP[3][3] = Gr
TOP[5][3] = Gd

n = W // U
yy, xx = np.mgrid[0:n, 0:n]
r = np.hypot((xx - n / 2) / n, (yy - n * 0.36) / n)
k = np.clip(1 - r * 1.65, 0, 1) ** 1.6
bg = np.zeros((n, n, 3))
bg[..., 0] = 4 + 22 * k
bg[..., 1] = 7 + 104 * k
bg[..., 2] = 6 + 22 * k
rng = np.random.default_rng(11)
for _ in range(140):
    x, y = rng.integers(1, n - 1, 2)
    a = rng.uniform(0.2, 0.75) * (0.25 + k[y, x])
    bg[y, x] = bg[y, x] * (1 - a) + np.array([70, 240, 85]) * a
img = Image.fromarray(np.clip(bg, 0, 255).astype('uint8')).resize((W, W), Image.NEAREST)

# brilho atrás da cabeça (pixelado)
gl = Image.new('L', (n, n), 0)
ImageDraw.Draw(gl).ellipse([20, 4, 104, 84], fill=170)
gl = gl.filter(ImageFilter.GaussianBlur(9)).resize((W, W), Image.NEAREST)
img = Image.composite(Image.new('RGB', (W, W), (55, 235, 80)), img, gl.point(lambda v: int(v * 0.5)))
d = ImageDraw.Draw(img)

# a cabeça (um tico virada pro lado do FroggyDude, vendo a lateral verde e a coroa)
CX, CY, SC = 248, 176, 25
YAW, PIT = math.radians(-7), math.radians(8)
layer = Image.new('RGBA', (W, W), (0, 0, 0, 0))
head(ImageDraw.Draw(layer), CX, CY, SC, YAW, PIT, FRONT, SIDE_R, TOP, side_right=True)
# contorno escuro em volta da cabeça (separa do brilho verde do fundo)
alpha = layer.split()[3]
ring = alpha.filter(ImageFilter.MaxFilter(13))
img.paste(Image.new('RGB', (W, W), (6, 16, 9)), (0, 0), ring)
img.paste(layer, (0, 0), layer)
d = ImageDraw.Draw(img)

# a rachadura: uma linha em escada, verde-clara brilhando, um tico além da cabeça em cima e embaixo
def P3(x, y, z=4.06):
    return proj((x, y, z), YAW, PIT, CX, CY, SC)[:2]
crack = [(0.0, 5.6), (0.0, 4.0), (-0.25, 3.3), (0.1, 2.5), (-0.2, 1.7), (0.15, 0.9), (-0.2, 0.1), (0.1, -0.7),
         (-0.15, -1.5), (0.15, -2.3), (-0.1, -3.1), (0.0, -4.0), (0.0, -4.6)]
pts = [P3(x, y) for x, y in crack]
lay = Image.new('L', (W, W), 0)
ld = ImageDraw.Draw(lay)
ld.line(pts, fill=255, width=22)
lay = lay.filter(ImageFilter.GaussianBlur(10))
img = Image.composite(Image.new('RGB', (W, W), (90, 255, 110)), img, lay.point(lambda v: int(v * 0.55)))
d = ImageDraw.Draw(img)
d.line(pts, fill=(40, 200, 70), width=9)
d.line(pts, fill=(200, 255, 200), width=4)
# faíscas saindo da rachadura (quadradinhos na grade)
rnd = random.Random(4)
for _ in range(26):
    t = rnd.random()
    y3 = 5.0 - t * 9.0
    x0, y0 = P3(rnd.uniform(-0.4, 0.4), y3)
    dx = rnd.choice([-1, 1]) * rnd.uniform(6, 60)
    x1 = int((x0 + dx) // U) * U
    y1 = int((y0 + rnd.uniform(-18, 18)) // U) * U
    c = (150, 255, 160) if rnd.random() < 0.5 else (70, 235, 90)
    sz = U * rnd.choice([1, 1, 2])
    d.rectangle([x1, y1, x1 + sz - 1, y1 + sz - 1], fill=c)

# faixa escura do título
band = Image.new("RGBA", (W, 196), (0, 0, 0, 0))
bd2 = ImageDraw.Draw(band)
for i in range(0, 196, U):
    bd2.rectangle([0, i, W, i + U - 1], fill=(3, 9, 5, min(235, i * 4)))
img.paste(band, (0, W - 196), band)
d = ImageDraw.Draw(img)
FONT = {
 'B': ["11110", "11011", "11011", "11110", "11011", "11011", "11110"],
 'E': ["11111", "11000", "11000", "11110", "11000", "11000", "11111"],
 'C': ["01111", "11000", "11000", "11000", "11000", "11000", "01111"],
 'O': ["01110", "11011", "11011", "11011", "11011", "11011", "01110"],
 'M': ["1100011", "1110111", "1111111", "1101011", "1100011", "1100011", "1100011"],
 'H': ["11011", "11011", "11011", "11111", "11011", "11011", "11011"],
 'I': ["1111", "0110", "0110", "0110", "0110", "0110", "1111"],
 '.': ["00", "00", "00", "00", "00", "11", "11"],
}
def wid(s, sc):
    return sum((len(FONT[c][0]) + 1) * sc for c in s) - sc
def text(s, y, sc, col, shade, edge, ow):
    x = (W - wid(s, sc)) // 2
    cells = []
    for ch in s:
        for j, row in enumerate(FONT[ch]):
            for i, v in enumerate(row):
                if v == '1':
                    cells.append((x + i * sc, y + j * sc, j))
        x += (len(FONT[ch][0]) + 1) * sc
    for cx, cy, j in cells:
        d.rectangle([cx - ow, cy - ow, cx + sc - 1 + ow, cy + sc - 1 + ow + (U if j == 6 else 0)], fill=edge)
    for cx, cy, j in cells:
        d.rectangle([cx, cy, cx + sc - 1, cy + sc - 1], fill=shade if j >= 4 else col)
band = Image.new("RGBA", (W, 196), (0, 0, 0, 0))
bd2 = ImageDraw.Draw(band)
for i in range(0, 196, U):
    bd2.rectangle([0, i, W, i + U - 1], fill=(4, 10, 6, min(230, i * 3)))
img.paste(band, (0, W - 196), band)
d = ImageDraw.Draw(img)

text("BECOME", 338, 12, (244, 244, 240), (196, 196, 196), (8, 10, 8), 8)
text("HIM...", 420, 12, (120, 245, 70), (64, 196, 40), (6, 30, 6), 8)
d.rectangle([0, 0, W - 1, W - 1], outline=(16, 48, 20), width=U)
import os
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
img.save(os.path.join(ROOT, 'icon.png'))
img.save(os.path.join(ROOT, 'src', 'main', 'resources', 'becomehim_logo.png'))
print('ok')
