#!/usr/bin/env python3
"""Gera a versão "boca escancarada" de cada skin do Froggydude.

Quando ele solta a língua ou grita, só o ROSTO muda pra boca aberta (como no
vídeo contra o Parallax, 0:05 e 0:13): o resto da skin continua igual, com o
sangue que tiver. As faces de grito vêm das skins dele mesmo (histórico):

    grito-limpo         (01/10/2026)  -> skins limpas
    grito-boca-sangue   (31/08/2026)  -> com sangue / acabou de comer
    grito-boca-preta    (09/08/2026)  -> carbonizado / destroçado

Uso:  python tools/make_mouth_open.py
Gera froggydude_<tipo>_<estágio>_open.png ao lado das skins normais.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SKINS = os.path.join(HERE, "..", "..", "pesquisa", "skins")
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "froggydude", "textures", "entity")

FACES = {
    "limpo": "2026-10-01_grito-limpo.png",
    "sangue": "2026-08-31_grito-boca-sangue.png",
    "preta": "2026-08-09_grito-boca-preta.png",
}
# rosto (frente da cabeça) e a frente da camada de cima (coroa/chapéu)
REGIONS = [(8, 8, 16, 16), (40, 8, 48, 16)]


def face_for(variant, stage):
    if variant in ("burned", "smashed"):
        return "preta"
    if variant == "fed" or stage >= 2:
        return "sangue"
    return "limpo"


def main():
    faces = {k: Image.open(os.path.join(SKINS, v)).convert("RGBA") for k, v in FACES.items()}
    for variant in ("normal", "burned", "fed", "smashed"):
        for stage in range(4):
            base_path = os.path.join(OUT, f"froggydude_{variant}_{stage}.png")
            if not os.path.exists(base_path):
                continue
            img = Image.open(base_path).convert("RGBA")
            face = faces[face_for(variant, stage)]
            for box in REGIONS:
                img.paste(face.crop(box), box[:2])
            out = os.path.join(OUT, f"froggydude_{variant}_{stage}_open.png")
            img.save(out)
            print("ok:", os.path.basename(out), "<-", face_for(variant, stage))


if __name__ == "__main__":
    main()
