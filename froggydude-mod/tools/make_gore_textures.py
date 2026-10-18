#!/usr/bin/env python3
"""Gera as texturas do gore (v0.4.0). Tudo em tons de CINZA com transparência:
o jogo pinta com a cor do sangue de cada bicho (vermelho, podre, verde, roxo...).

    pip install pillow
    python tools/make_gore_textures.py

Sai em src/main/resources/assets/froggydude/textures/:
    gui/blood_splat_0..5.png   respingos na tela (pixel-art 64x64 aumentado pra 256)
    gui/blood_vignette.png     a borda vermelha da tela (POV de quem é esmagado)
    misc/blood_pool.png        a poça no chão
    misc/blood_stream.png      o jato de sangue (fita com brilho de líquido)
    particle/blood_cloud_0..2.png  sangue se espalhando na água
"""
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "froggydude", "textures")


def save(img, *path):
    out = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("ok", os.path.relpath(out, TEX))


def blob(rng, n, cx, cy, r, rough=0.35):
    """Máscara de mancha irregular (raio com ruído por ângulo)."""
    k = [rng.uniform(-1, 1) for _ in range(7)]
    ph = [rng.uniform(0, 6.28) for _ in range(7)]
    m = [[0.0] * n for _ in range(n)]
    for y in range(n):
        for x in range(n):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            rr = r * (1 + rough * sum(k[i] * math.sin((i + 2) * a + ph[i]) for i in range(7)) / 3.0)
            if d < rr:
                m[y][x] = 1.0 - d / max(rr, 1e-3)
    return m


def splat(seed, n=64):
    rng = random.Random(seed)
    lum = [[None] * n for _ in range(n)]

    def put(x, y, v):
        if 0 <= x < n and 0 <= y < n:
            cur = lum[y][x]
            lum[y][x] = v if cur is None else max(cur, v)

    cx, cy = rng.uniform(26, 38), rng.uniform(22, 34)
    r = rng.uniform(9, 14)
    core = blob(rng, n, cx, cy, r)
    for y in range(n):
        for x in range(n):
            if core[y][x] > 0:
                # borda mais escura, miolo médio, brilho perto do topo-esquerda
                v = 0.55 + 0.25 * min(1.0, core[y][x] * 2.2)
                put(x, y, v)
    # gotas satélite e riscos saindo do centro
    for _ in range(rng.randint(8, 14)):
        a = rng.uniform(0, 6.28)
        dist = rng.uniform(r * 1.1, r * 2.4)
        sx, sy = cx + math.cos(a) * dist, cy + math.sin(a) * dist
        sr = rng.uniform(1.0, 3.2)
        m = blob(rng, n, sx, sy, sr, 0.2)
        for y in range(n):
            for x in range(n):
                if m[y][x] > 0:
                    put(x, y, 0.6 + 0.15 * m[y][x])
        if rng.random() < 0.6:
            # risco ligando a gota ao centro
            steps = int(dist)
            for s in range(steps):
                t = s / max(1, steps)
                px = cx + math.cos(a) * (r * 0.8 + (dist - r * 0.8) * t)
                py = cy + math.sin(a) * (r * 0.8 + (dist - r * 0.8) * t)
                put(int(px), int(py), 0.58)
    # escorridos pra baixo (a tela "escorre")
    for _ in range(rng.randint(2, 5)):
        x0 = int(cx + rng.uniform(-r * 0.8, r * 0.8))
        y0 = int(cy + rng.uniform(0, r * 0.6))
        ln = rng.randint(6, 22)
        w = rng.choice((1, 2, 2, 3))
        for dy in range(ln):
            for dx in range(w):
                put(x0 + dx, y0 + dy, 0.6 if dy < ln - 2 else 0.7)
        # gota na ponta
        for dx in range(-1, w + 1):
            put(x0 + dx, y0 + ln, 0.72)
            put(x0 + dx, y0 + ln + 1, 0.66)
    # brilho molhado
    for _ in range(rng.randint(3, 6)):
        hx = int(cx - r * 0.35 + rng.uniform(-2, 2))
        hy = int(cy - r * 0.35 + rng.uniform(-2, 2))
        if lum[hy][hx] is not None:
            put(hx, hy, 1.0)
            if rng.random() < 0.5:
                put(hx + 1, hy, 0.92)

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            v = lum[y][x]
            if v is not None:
                c = int(255 * v)
                img.putpixel((x, y), (c, c, c, 235))
    return img.resize((n * 4, n * 4), Image.NEAREST)


def vignette(n=256):
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            dx, dy = (x + 0.5) / n * 2 - 1, (y + 0.5) / n * 2 - 1
            d = math.sqrt(dx * dx * 0.9 + dy * dy)
            a = max(0.0, min(1.0, (d - 0.45) / 0.75))
            a = a ** 1.6
            img.putpixel((x, y), (255, 255, 255, int(255 * a)))
    return img


def pool(n=64, seed=7):
    rng = random.Random(seed)
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    main = blob(rng, n, n / 2, n / 2, n * 0.36, 0.3)
    extra = [blob(rng, n, n / 2 + rng.uniform(-18, 18), n / 2 + rng.uniform(-18, 18), rng.uniform(3, 7), 0.25)
             for _ in range(6)]
    for y in range(n):
        for x in range(n):
            v = max([main[y][x]] + [e[y][x] for e in extra])
            if v > 0:
                lum = 0.62 + 0.2 * min(1.0, v * 1.8)
                if rng.random() < 0.02 and v > 0.4:
                    lum = 0.95
                c = int(255 * lum)
                img.putpixel((x, y), (c, c, c, 230))
    return img


def stream(n=16):
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    for y in range(n):
        for x in range(n):
            # atravessado na largura: escuro nas bordas, brilho no meio (líquido)
            t = abs((x + 0.5) / n * 2 - 1)
            lum = 0.62 + 0.38 * (1 - t) ** 3
            if 6 <= x <= 7 and y % 5 != 0:
                lum = 1.0
            c = int(255 * min(1.0, lum))
            img.putpixel((x, y), (c, c, c, 255))
    return img


def cloud(seed, n=32):
    rng = random.Random(seed)
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    parts = [blob(rng, n, n / 2 + rng.uniform(-5, 5), n / 2 + rng.uniform(-5, 5), rng.uniform(6, 11), 0.4)
             for _ in range(4)]
    for y in range(n):
        for x in range(n):
            v = max(p[y][x] for p in parts)
            if v > 0:
                img.putpixel((x, y), (235, 235, 235, int(255 * min(1.0, v * 1.4) * 0.8)))
    return img


def main():
    for i in range(6):
        save(splat(100 + i * 17), "gui", f"blood_splat_{i}.png")
    save(vignette(), "gui", "blood_vignette.png")
    save(pool(), "misc", "blood_pool.png")
    save(stream(), "misc", "blood_stream.png")
    for i in range(3):
        save(cloud(300 + i), "particle", f"blood_cloud_{i}.png")


if __name__ == "__main__":
    main()
