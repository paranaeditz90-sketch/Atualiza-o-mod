#!/usr/bin/env python3
"""Gera as texturas do sangue e da poeira (pixel art, sempre igual - semente fixa).

    textures/particle/blood_0..3.png      cubinhos sólidos (branco: o jogo pinta de vermelho)
    textures/misc/blood_pool.png          a poça no chão (32x32, vista de cima)
    textures/particle/dust_puff_0..2.png  nuvem de poeira do galope (cinza: o jogo
                                          pinta com a cor do chão)

Uso:  python tools/make_blood_textures.py
"""
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "froggydude", "textures")


def chunks():
    """Cubinhos sólidos (v0.3.4: igual ao "Eating a Zebra" - quadrados cheios de
    vermelho vivo, nada de mancha/pó): miolo claro e borda um pouco mais escura,
    de tamanhos diferentes dentro do quadro de 8x8 (o jogo pinta de vermelho)."""
    os.makedirs(os.path.join(TEX, "particle"), exist_ok=True)
    for i, (off, size) in enumerate(((0, 8), (1, 6), (1, 7), (2, 4))):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        for y in range(off, off + size):
            for x in range(off, off + size):
                edge = x in (off, off + size - 1) or y in (off, off + size - 1)
                v = 200 if edge else 255
                img.putpixel((x, y), (v, v, v, 255))
        img.save(os.path.join(TEX, "particle", f"blood_{i}.png"))


def pool():
    os.makedirs(os.path.join(TEX, "misc"), exist_ok=True)
    rnd = random.Random(42)
    size = 32
    c = size / 2
    blobs = [(c, c, 9.5)]
    for _ in range(9):
        a = rnd.uniform(0, 2 * math.pi)
        d = rnd.uniform(4, 8.5)
        blobs.append((c + math.cos(a) * d, c + math.sin(a) * d, rnd.uniform(3.0, 6.0)))
    drops = []
    for _ in range(10):
        a = rnd.uniform(0, 2 * math.pi)
        d = rnd.uniform(11.5, 14.5)
        drops.append((c + math.cos(a) * d, c + math.sin(a) * d, rnd.uniform(0.7, 1.6)))
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            px, py = x + 0.5, y + 0.5
            depth = max(r - math.hypot(px - bx, py - by) for bx, by, r in blobs + drops)
            if depth <= 0:
                continue
            # centro mais escuro e grosso, borda mais viva e fina
            base = 92 + int(min(depth, 6) * -4) + rnd.randint(-10, 10)
            if depth < 1.2:
                base += 26
            r = max(55, min(150, base + 30))
            g = rnd.randint(0, 6)
            b = rnd.randint(2, 9)
            a = 245 if depth >= 1.2 else 205
            if rnd.random() < 0.05 and depth > 2:
                r, g, b = min(200, r + 50), 20, 20        # brilho molhado
            img.putpixel((x, y), (r, g, b, a))
    img.save(os.path.join(TEX, "misc", "blood_pool.png"))


def puffs():
    """Nuvens redondas e fofas, com a borda esfarelada e transparência."""
    os.makedirs(os.path.join(TEX, "particle"), exist_ok=True)
    rnd = random.Random(11)
    for i in range(3):
        size = 16
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        c = (size - 1) / 2
        lobes = [(c, c, 6.2)] + [(c + rnd.uniform(-3.5, 3.5), c + rnd.uniform(-3.5, 3.5), rnd.uniform(3.0, 4.6))
                                 for _ in range(4)]
        for y in range(size):
            for x in range(size):
                depth = max(r - math.hypot(x - lx, y - ly) for lx, ly, r in lobes)
                if depth <= 0 or (depth < 1.2 and rnd.random() < 0.45):
                    continue
                v = 205 + rnd.randint(-25, 30)
                a = int(min(1.0, depth / 3.0) * 210)
                img.putpixel((x, y), (min(255, v), min(255, v), min(255, v), a))
        img.save(os.path.join(TEX, "particle", f"dust_puff_{i}.png"))


if __name__ == "__main__":
    chunks()
    pool()
    puffs()
    print("ok")
