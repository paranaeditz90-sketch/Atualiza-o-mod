"""Esqueleto do Froggydude (mesmos pivôs e cubos do froggydude.geo.json) pra
conferir poses sem abrir o jogo. Usado pelo build_anims.py (pra nunca enterrar
mão/pé no chão) e pelo check_poses.py (relatório, câmera da vítima).

Convenções (medidas no jogo): waist X+ inclina pra frente, head X+ olha pra
baixo, arm X- levanta pra frente, leg X+ vai pra trás. Eixos: f = frente,
y = cima, s = lado direito dele, em pixels do modelo (1 bloco = 16 px / 1,05).
"""
import math

import numpy as np

SCALE = 1.05
PX = SCALE / 16.0          # 1 px do modelo em blocos
EYE_LYING = 0.4            # altura do olho de um jogador deitado (pose de nadar)


def rx(a):
    a = math.radians(a)
    c, s = math.cos(a), math.sin(a)
    # (f, y, s): "cima" vai pra frente quando a > 0
    return np.array([[c, s, 0], [-s, c, 0], [0, 0, 1]])


def ry(a):
    a = math.radians(a)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, -s], [0, 1, 0], [s, 0, c]])


def rz(a):
    a = math.radians(a)
    c, s = math.cos(a), math.sin(a)
    # (y, s): "baixo" vai pro lado +s (direita dele) quando a > 0
    return np.array([[1, 0, 0], [0, c, s], [0, -s, c]])


def rot(x, y, z, side=1):
    """Ordem do GeckoLib: aplica X, depois Y, depois Z. side=-1 espelha Y/Z (lado esquerdo)."""
    return rz(z * side) @ ry(y * side) @ rx(x)


# cubos: (osso, mínimo local, máximo local) em px, relativos ao pivô do osso; inclui a camada de fora
CUBES = {
    "body": ((-2.25, -0.25, -4.25), (2.25, 12.25, 4.25)),
    "head": ((-4.5, -0.5, -4.5), (4.5, 8.5, 4.5)),
    "right_arm": ((-2.25, -10.25, -2.25), (2.25, 2.25, 2.25)),
    "left_arm": ((-2.25, -10.25, -2.25), (2.25, 2.25, 2.25)),
    "right_leg": ((-2.25, -12.25, -2.25), (2.25, 0.25, 2.25)),
    "left_leg": ((-2.25, -12.25, -2.25), (2.25, 0.25, 2.25)),
}
TIPS = {"right_arm": (0, -10, 0), "left_arm": (0, -10, 0), "right_leg": (0, -12, 0), "left_leg": (0, -12, 0)}


def frames(p):
    """Matriz (R, origem) de cada osso no espaço do modelo (px), pra uma pose amostrada."""
    def g(b, ch, default=(0, 0, 0)):
        return p.get(b, {}).get(ch, default)

    out = {}
    rpos = g("root", "position")
    rr = g("root", "rotation")
    R_root = rot(*rr)
    o_root = np.array([0.0, rpos[1], rpos[0]])  # position x vira lado
    # cintura/corpo: pivô (0,12,0) dentro do root
    wr = g("waist", "rotation")
    R_w = R_root @ rot(*wr)
    o_w = o_root + R_root @ np.array([0, 12.0, 0])
    out["body"] = (R_w, o_w)
    hr = g("head", "rotation")
    hp = g("head", "position")  # pescoço deslocado; no jogo: +X = pro lado esquerdo dele, +Z = pra trás
    out["head"] = (R_w @ rot(*hr), o_w + R_w @ np.array([-hp[2], 12.0 + hp[1], -hp[0]]))
    # o lado esquerdo já vem espelhado no próprio json (pose() grava -Y/-Z pra ele):
    # aqui NÃO espelha de novo (conferido no jogo: arm_spread abre os dois braços pra fora)
    for arm, sx in (("right_arm", 5.0), ("left_arm", -5.0)):
        ar = g(arm, "rotation")
        ap = g(arm, "position")  # deslocamento do ombro (só Y é usado: ombro subindo)
        out[arm] = (R_w @ rot(*ar), o_w + R_w @ np.array([0, 10.0 + ap[1], sx]))
    for leg, sx in (("right_leg", 1.9), ("left_leg", -1.9)):
        lr = g(leg, "rotation")
        out[leg] = (R_root @ rot(*lr), o_root + R_root @ np.array([0, 12.0, sx]))
    return out


def lowest(fr):
    """Ponto mais baixo (px) de mãos, pés e cabeça."""
    res = {}
    for b in ("right_arm", "left_arm", "right_leg", "left_leg", "head"):
        R, o = fr[b]
        lo, hi = CUBES[b]
        ys = []
        for x in (lo[0], hi[0]):
            for y in (lo[1], hi[1]):
                for z in (lo[2], hi[2]):
                    ys.append((o + R @ np.array([x, y, z]))[1])
        res[b] = min(ys)
    return res


def dist_to_cube(fr, b, point_px):
    """Distância (px, com sinal: < 0 = dentro) de um ponto até o cubo do osso."""
    R, o = fr[b]
    lo, hi = CUBES[b]
    local = R.T @ (point_px - o)
    d_out = np.maximum(np.maximum(np.array(lo) - local, local - np.array(hi)), 0)
    outside = np.linalg.norm(d_out)
    if outside > 0:
        return outside
    return -min(min(local[i] - lo[i], hi[i] - local[i]) for i in range(3))


def tip(fr, b):
    R, o = fr[b]
    return o + R @ np.array(TIPS[b], dtype=float)


