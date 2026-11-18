#!/usr/bin/env python3
"""Prepara as skins do Froggydude para o mod.

Uso (da skin mais limpa para a mais ensanguentada, até 4 arquivos):

    python prepare_skins.py limpa.png leve.png media.png pesada.png
    python prepare_skins.py --variant burned fogo1.png fogo2.png

Gera froggydude_<tipo>_0.png ... _3.png na pasta de texturas do mod (ou em
--out). Tipos (--variant): normal (padrão), burned, fed, smashed.
O estágio (0 a 3) é escolhido pela vida que falta.

A língua do modelo usa uma região que fica vazia nas skins normais
(pixels x 56..61, y 16..18). Este script pinta essa região de rosa,
senão a língua ficaria invisível.

Precisa do Pillow:  pip install pillow
"""
import argparse
import os
import sys

from PIL import Image

TONGUE_BOX = (56, 16, 62, 19)      # x0, y0, x1, y1 (x1/y1 exclusivos)
TONGUE_COLOR = (226, 84, 108, 255)
VARIANTS = ("normal", "burned", "fed", "smashed")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("skins", nargs="+", help="1 a 4 skins 64x64, da mais limpa para a mais ensanguentada")
    ap.add_argument("--variant", default="normal", choices=VARIANTS)
    ap.add_argument("--out", default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..",
        "src", "main", "resources", "assets", "froggydude", "textures", "entity"))
    args = ap.parse_args()

    if len(args.skins) > 4:
        sys.exit("Passe no máximo 4 skins.")
    os.makedirs(args.out, exist_ok=True)

    def dest(i):
        return os.path.join(args.out, f"froggydude_{args.variant}_{i}.png")

    for i, path in enumerate(args.skins):
        img = Image.open(path).convert("RGBA")
        if img.size != (64, 64):
            sys.exit(f"{path}: a skin precisa ser 64x64 (a sua é {img.size[0]}x{img.size[1]}). "
                     "Baixe a versão 64x64 no NameMC.")
        px = img.load()
        for x in range(TONGUE_BOX[0], TONGUE_BOX[2]):
            for y in range(TONGUE_BOX[1], TONGUE_BOX[3]):
                px[x, y] = TONGUE_COLOR
        img.save(dest(i))
        print("ok:", dest(i))

    # se passou menos de 4, repete a última para os estágios que faltam
    for i in range(len(args.skins), 4):
        Image.open(dest(len(args.skins) - 1)).save(dest(i))
        print("repetido:", dest(i))


if __name__ == "__main__":
    main()
