#!/usr/bin/env python3
"""Confere as poses do Froggydude com a geometria de verdade do modelo.

Monta o esqueleto (mesmos pivôs e cubos do froggydude.geo.json, escala 1,05)
em cada quadro das animações e mede:

  * chão: quanto a ponta da mão/pé/cabeça afunda abaixo do chão (as pernas
    "enterradas" dos prints eram isso);
  * câmera da vítima: distância do olho de quem está deitado embaixo dele
    (0,4 bloco de altura, a D blocos na frente) até cada cubo. Se der < 0, a
    câmera está DENTRO do cubo (a tela verde/vermelha no esmagamento).

Convenções (medidas no jogo; ver build_anims.py): waist X+ inclina pra
frente, head X+ olha pra baixo, arm X- levanta pra frente, leg X+ vai pra
trás. Eixos aqui: f = frente, y = cima, s = lado direito dele, em pixels do
modelo (1 bloco = 16 px / 1,05).

Uso:
    python tools/anim/check_poses.py                 # relatório das animações de ataque
    python tools/anim/check_poses.py smash 1.15      # uma animação, vítima a 1,15 bloco
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_anims  # noqa: E402
from rig import CUBES, EYE_LYING, PX, dist_to_cube, frames, lowest, tip  # noqa: E402,F401
import json  # noqa: E402

def anim_data(name):
    if name in build_anims.ANIMS:
        return build_anims.ANIMS[name]
    data = json.load(open(build_anims.MAIN))
    return data["animations"][name]


def check(name, victim_dist=None, step=0.02, show=True):
    a = anim_data(name)
    length = float(a.get("animation_length", 1.0))
    eye = None
    if victim_dist is not None:
        eye = np.array([victim_dist / PX, EYE_LYING / PX, 0.0])
    worst_ground, worst_cam = (0, None), (99, None)
    t = 0.0
    rows = []
    while t <= length + 1e-6:
        p = build_anims.sample(a, t)
        fr = frames(p)
        low = lowest(fr)
        g = min(low.values())
        if g < worst_ground[0]:
            worst_ground = (g, t)
        cam = None
        if eye is not None:
            cam = min((dist_to_cube(fr, b, eye), b) for b in CUBES)
            if cam[0] < worst_cam[0]:
                worst_cam = (cam[0], t, cam[1])
        hands = [tip(fr, b) for b in ("right_arm", "left_arm")]
        hand_f = max(h[0] for h in hands) * PX
        hand_y = min(h[1] for h in hands) * PX
        head_c = fr["head"][1] + fr["head"][0] @ np.array([0, 4.0, 0])
        rows.append((t, g * PX, cam, hand_f, hand_y, head_c[0] * PX, head_c[1] * PX))
        t += step
    if show:
        print(f"== {name} (comprimento {length}s){'' if victim_dist is None else f', vítima a {victim_dist} bloco(s)'}")
        for t, g, cam, hf, hy, cf, cy in rows[:: max(1, int(0.05 / step))]:
            cs = "" if cam is None else f" câmera->{cam[1]:9s} {cam[0] * PX:+.2f}"
            print(f"  t={t:4.2f} chão {g:+.2f}  mão f={hf:+.2f} y={hy:+.2f}  cabeça f={cf:+.2f} y={cy:+.2f}{cs}")
    return worst_ground[0] * PX, worst_ground[1], (None if eye is None else (worst_cam[0] * PX, worst_cam[1], worst_cam[2]))


if __name__ == "__main__":
    if len(sys.argv) > 1:
        d = float(sys.argv[2]) if len(sys.argv) > 2 else None
        g, gt, cam = check(sys.argv[1], d)
        print(f"pior chão: {g:+.2f} bloco em t={gt}")
        if cam:
            print(f"câmera mais perto: {cam[0]:+.2f} bloco ({cam[2]}) em t={cam[1]:.2f}")
    else:
        for name, d in (("smash", 1.5), ("pin_hold", 1.5), ("arm_rip", 1.5), ("jump_pin", None),
                        ("high_jump", None), ("leap", None), ("run", None), ("run_frenzy", None),
                        ("climb", None), ("crouch_idle", None)):
            g, gt, cam = check(name, d, show=False)
            cs = "" if cam is None else f"  câmera {cam[0]:+.2f} ({cam[2]}, t={cam[1]:.2f})"
            print(f"{name:12s} chão {g:+.2f} (t={gt:.2f}){cs}")
