#!/usr/bin/env python3
"""Refaz froggydude_fed_3.png (v0.4.3).

A skin do NameMC que estava ali era o corpo INTEIRO coberto de sangue escuro:
no jogo, depois da cutscene, ele virava um boneco vinho sem cara, sem coroa e
sem terno (o LM achou que era outra coisa). Agora o estágio 3 do "acabou de
comer" é o estágio 2 com o sangue do normal_3 por cima (peito, mãos, pernas),
sem tocar no rosto (só a boca) nem na coroa.

Uso:  python tools/make_fed_full.py   (depois: python tools/make_mouth_open.py)
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "froggydude", "textures", "entity")

# rosto (frente da cabeça) e toda a camada de cima da cabeça (coroa): não mancha
KEEP = [(8, 8, 16, 13), (32, 0, 64, 16)]


def kept(x, y):
    return any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in KEEP)


def main():
    base = Image.open(os.path.join(TEX, "froggydude_fed_2.png")).convert("RGBA")
    clean = Image.open(os.path.join(TEX, "froggydude_normal_0.png")).convert("RGBA")
    bloody = Image.open(os.path.join(TEX, "froggydude_normal_3.png")).convert("RGBA")
    out = base.copy()
    px, c, b = out.load(), clean.load(), bloody.load()
    for y in range(64):
        for x in range(64):
            if kept(x, y) or b[x, y][3] == 0:
                continue
            # onde o normal_3 tem sangue que a skin limpa não tem
            if sum(abs(b[x, y][i] - c[x, y][i]) for i in range(3)) > 60:
                r, g, bl, a = px[x, y]
                br, bg, bb, _ = b[x, y]
                k = 0.8
                px[x, y] = (int(r + (br - r) * k), int(g + (bg - g) * k), int(bl + (bb - bl) * k), max(a, 255))
    out.save(os.path.join(TEX, "froggydude_fed_3.png"))
    print("ok: froggydude_fed_3.png")


if __name__ == "__main__":
    main()
