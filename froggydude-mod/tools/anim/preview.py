#!/usr/bin/env python3
"""Prévia rápida das poses, sem abrir o jogo: desenha os cubos do modelo
(mesmo esqueleto do rig.py) em perspectiva, com a câmera onde a gente quiser,
pra pôr lado a lado com um quadro do vídeo original e acertar a pose antes de
gastar uma rodada no jogo (o jogo continua sendo a prova final).

Cores aproximadas da skin: terno escuro, mão verde, coroa amarela, olhos e
boca na frente da cabeça (pra ver pra onde ela aponta), gravata no peito.

Uso:
    python tools/anim/preview.py contort 0.6 1.0 2.2      # tira uma folha com esses tempos
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rig  # noqa: E402

SUIT = (26, 36, 31)
GREEN = (98, 146, 84)
HAND = (112, 168, 96)
CROWN = (226, 180, 36)
WHITE = (205, 205, 205)
TIE = (36, 140, 118)
EYE = (14, 14, 14)
MOUTH = (214, 116, 64)
SHOE = (10, 12, 11)
TEAR = (170, 215, 240)
SKY = (150, 190, 235)
GROUND = (196, 182, 136)

# (osso, mínimo (f, y, s), máximo (f, y, s), cor) em px, relativo ao pivô do osso
PARTS = [
    ("body", (-2, 0, -4), (2, 12, 4), SUIT),
    ("body", (2, 4.5, -0.6), (2.12, 11.2, 0.6), TIE),
    ("body", (2, 11.2, -2.2), (2.1, 12, 2.2), WHITE),
    ("head", (-4, 0, -4), (4, 8, 4), GREEN),
    ("head", (4, 3, -3.4), (4.12, 5, -1.2), EYE),
    ("head", (4, 3, 1.2), (4.12, 5, 3.4), EYE),
    ("head", (4, 0, -4.5), (4.5, 1, 4.5), MOUTH),           # boca: fileira de baixo da camada de fora
    ("head", (3, -0.12, -4), (4, 0, -3), TEAR),             # queixo: dois pixels azuis nos cantos da frente
    ("head", (3, -0.12, 3), (4, 0, 4), TEAR),
    ("head", (4, 5.5, -4.5), (4.5, 8.5, 4.5), CROWN),       # coroa: faixa de cima da camada de fora
    ("head", (-4.5, 5.5, -4.5), (-4, 8.5, 4.5), CROWN),
    ("head", (-4, 5.5, 4), (4, 8.5, 4.5), CROWN),
    ("head", (-4, 5.5, -4.5), (4, 8.5, -4), CROWN),
]
for _arm in ("right_arm", "left_arm"):
    PARTS += [(_arm, (-2, -6.5, -2), (2, 2, 2), SUIT),
              (_arm, (-2.02, -7, -2.02), (2.02, -6.5, 2.02), WHITE),
              (_arm, (-2, -10, -2), (2, -7, 2), HAND)]
for _leg in ("right_leg", "left_leg"):
    PARTS += [(_leg, (-2, -11, -2), (2, 0, 2), SUIT),
              (_leg, (-2, -12, -2), (2, -11, 2), SHOE)]

# faces de um cubo: (índices dos cantos, normal local)
_FACES = [
    ((0, 1, 3, 2), (-1, 0, 0)), ((4, 6, 7, 5), (1, 0, 0)),
    ((0, 4, 5, 1), (0, -1, 0)), ((2, 3, 7, 6), (0, 1, 0)),
    ((0, 2, 6, 4), (0, 0, -1)), ((1, 5, 7, 3), (0, 0, 1)),
]
LIGHT = np.array([0.45, 1.0, -0.35]) / np.linalg.norm([0.45, 1.0, -0.35])


class Camera:
    """Câmera em blocos, no espaço dele (f = frente dele, y = cima, s = direita dele).
    az = 0 põe a câmera bem na frente; az > 0 gira pro lado direito dele."""

    def __init__(self, dist=3.0, eye=1.62, az=0.0, fov=70.0, look_y=1.0, size=(360, 360), look_f=0.0):
        a = math.radians(az)
        self.pos = np.array([dist * math.cos(a), eye, dist * math.sin(a)])
        target = np.array([look_f * math.cos(a), look_y, look_f * math.sin(a)])
        fwd = target - self.pos
        self.fwd = fwd / np.linalg.norm(fwd)
        right = np.cross(self.fwd, [0, 1, 0])
        self.right = right / np.linalg.norm(right)
        self.up = np.cross(self.right, self.fwd)
        self.size = size
        self.fpx = (size[1] / 2) / math.tan(math.radians(fov) / 2)

    def project(self, p):
        v = p - self.pos
        z = v @ self.fwd
        return (self.size[0] / 2 + self.fpx * (v @ self.right) / z,
                self.size[1] / 2 - self.fpx * (v @ self.up) / z, z)


def render(p, cam, bg=True):
    w, h = cam.size
    img = Image.new("RGB", cam.size, SKY)
    d = ImageDraw.Draw(img)
    if bg:
        horiz = cam.size[1] / 2 - cam.fpx * (cam.fwd[1] / math.hypot(cam.fwd[0], cam.fwd[2]))
        d.rectangle([0, int(horiz), w, h], fill=GROUND)
    fr = rig.frames(p)
    quads = []
    for bone, lo, hi, col in PARTS:
        R, o = fr[bone]
        # decalque (olho, boca, gravata): sempre por cima da face onde está colado
        bias = 0.08 if min(hi[i] - lo[i] for i in range(3)) < 0.2 else 0.0
        corners = []
        for i in range(8):
            local = np.array([(lo, hi)[i >> 2 & 1][0], (lo, hi)[i >> 1 & 1][1], (lo, hi)[i & 1][2]], dtype=float)
            corners.append((o + R @ local) * rig.PX)
        for idx, n in _FACES:
            nw = R @ np.array(n, dtype=float)
            c = sum(corners[i] for i in idx) / 4
            if nw @ (cam.pos - c) <= 0:
                continue
            pts = [cam.project(corners[i]) for i in idx]
            if min(q[2] for q in pts) <= 0.05:
                continue
            shade = 0.62 + 0.38 * max(0.0, float(nw @ LIGHT))
            quads.append((sum(q[2] for q in pts) / 4 - bias, [(q[0], q[1]) for q in pts],
                          tuple(int(min(255, ch * shade)) for ch in col)))
    for _, pts, col in sorted(quads, key=lambda q: -q[0]):
        d.polygon(pts, fill=col)
    return img


def sheet(images, labels, cols=None, pad=2):
    cols = cols or len(images)
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGB", (cols * (w + pad), rows * (h + pad)), (18, 18, 18))
    d = ImageDraw.Draw(out)
    for k, (im, lab) in enumerate(zip(images, labels)):
        x, y = (k % cols) * (w + pad), (k // cols) * (h + pad)
        out.paste(im, (x, y))
        if lab:
            d.rectangle([x, y, x + 6 * len(lab) + 4, y + 11], fill=(0, 0, 0))
            d.text((x + 2, y), lab, fill=(255, 230, 0))
    return out


if __name__ == "__main__":
    import build_anims
    name = sys.argv[1]
    ts = [float(t) for t in sys.argv[2:]] or [0.0]
    anim = build_anims.ANIMS[name]
    cam = Camera()
    ims = [render(build_anims.sample(anim, t), cam) for t in ts]
    out = os.path.join(os.getcwd(), f"preview_{name}.png")
    sheet(ims, [f"{t:.2f}" for t in ts]).save(out)
    print(out)
