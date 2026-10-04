#!/usr/bin/env python3
"""Gera as animações de locomoção/bote do Froggydude.

Uso:
    python tools/anim/build_anims.py              # atualiza froggydude.animation.json
    python tools/anim/build_anims.py --debug      # também gera poses congeladas (dbg_*) pra teste

As outras animações do arquivo (língua, comer, morder...) ficam como estão;
este script só reescreve as que estão definidas aqui.

Convenções (medidas no jogo, com o modelo de lado):
    waist X+  inclina o tronco pra frente (pivô no quadril)
    head  X+  olha pra baixo
    arm   X-  levanta o braço pra frente
    leg   X+  joga a perna pra trás
    root  X+  tomba o corpo todo pra frente (pivô nos pés); position Y- abaixa

Referência: luta contra o Parallax (FULL MOVIE 15:38 e o TikTok "Finally, a
worthy opponent"). A "corrida" dele é uma sequência de saltos de sapo:
agacha rente ao chão -> impulso -> voa quase na horizontal (pernas esticadas
pra trás, braços pra frente e pra baixo) -> cai nas mãos -> junta as pernas.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ANIM_DIR = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "froggydude", "animations")
MAIN = os.path.join(ANIM_DIR, "froggydude.animation.json")
DEBUG = os.path.join(ANIM_DIR, "froggydude_debug.animation.json")

BONES = ("root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg")


def pose(root_y=0.0, root_x=0.0, waist=0.0, head=0.0, arms=0.0, legs=0.0,
         arm_l=None, leg_l=None, head_z=0.0, waist_z=0.0, arm_spread=0.0, leg_spread=0.0,
         root_px=0.0, head_y=0.0, waist_y=0.0, arm_ry=0.0, arm_ly=None, spread_l=None):
    """Uma pose completa. arms/legs valem pros dois lados, a não ser que
    arm_l/leg_l digam outra coisa pro esquerdo. spread abre os membros pros lados
    (spread_l pro braço esquerdo, se for diferente). arm_ry gira o braço no
    próprio eixo (Y)."""
    al = arms if arm_l is None else arm_l
    ll = legs if leg_l is None else leg_l
    aly = arm_ry if arm_ly is None else arm_ly
    sl = arm_spread if spread_l is None else spread_l
    return {
        "root": {"position": [root_px, root_y, 0], "rotation": [root_x, 0, 0]},
        "waist": {"rotation": [waist, waist_y, waist_z]},
        "head": {"rotation": [head, head_y, head_z]},
        "right_arm": {"rotation": [arms, arm_ry, arm_spread]},
        "left_arm": {"rotation": [al, -aly, -sl]},
        "right_leg": {"rotation": [legs, 0, leg_spread]},
        "left_leg": {"rotation": [ll, 0, -leg_spread]},
    }


def animation(length, keys, loop):
    """keys: lista de (tempo, pose). Gera o formato do GeckoLib/Bedrock."""
    bones = {}
    for t, p in keys:
        for bone, channels in p.items():
            for ch, v in channels.items():
                bones.setdefault(bone, {}).setdefault(ch, {})[f"{t:.4g}" if t else "0.0"] = [round(x, 2) for x in v]
    return {"loop": loop, "animation_length": length, "bones": bones}


# ------------------------------------------------------------------ poses base

# agachado de sapo, rente ao chão (ref. 0,6 s e 0,8 s): tronco quase deitado,
# mãos no chão na frente, pernas dobradas pra trás, cabeça olhando à frente
CROUCH = dict(root_y=-7, waist=56, head=-52, arms=-62, legs=-52, arm_spread=6, leg_spread=16)


def crouch(**over):
    p = dict(CROUCH)
    p.update(over)
    return pose(**p)


ANIMS = {}

# Corrida de quatro (perto do alvo): no vídeo contra o AJ (0:32, quadro a quadro)
# não é galope de cachorro, são PULINHOS de sapo bem rápidos: agacha com as mãos
# no chão, estica o corpo pra cima e pra frente com as pernas pra trás, cai nas
# mãos de novo. 0,5 s por pulinho na velocidade 1; o jogo acelera ou freia a
# animação junto com a velocidade real (caçando = rápido, apavorando = devagar).
ANIMS["run"] = animation(0.5, [
    (0.00, pose(root_y=-5, waist=62, head=-56, arms=-74, arm_l=-66, legs=30, leg_l=24, arm_spread=7, leg_spread=10)),   # cai nas mãos
    (0.10, pose(root_y=-6.5, waist=58, head=-54, arms=-48, arm_l=-42, legs=-40, leg_l=-34, arm_spread=7, leg_spread=12)),  # pernas vêm por baixo
    (0.20, pose(root_y=-4, waist=40, head=-38, arms=-36, arm_l=-30, legs=-8, arm_spread=6, leg_spread=9)),             # empurra
    (0.30, pose(root_y=-1, waist=28, head=-26, arms=-122, arm_l=-110, legs=50, leg_l=44, arm_spread=5, leg_spread=7)), # esticado no ar
    (0.40, pose(root_y=-3, waist=50, head=-46, arms=-96, arm_l=-88, legs=44, leg_l=38, arm_spread=6, leg_spread=8)),   # descendo
    (0.50, pose(root_y=-5, waist=62, head=-56, arms=-74, arm_l=-66, legs=30, leg_l=24, arm_spread=7, leg_spread=10)),
], True)

# Fase 2 (manhunt dos blazes: "why is he running like that?"): os mesmos
# pulinhos, mas torto: cabeça caída de lado, um braço se debatendo, tronco torcendo.
ANIMS["run_frenzy"] = animation(0.5, [
    (0.00, pose(root_y=-4, waist=55, head=-40, head_z=34, arms=-60, arm_l=-142, legs=30, leg_l=22, arm_spread=8, spread_l=24, waist_z=8, leg_spread=10)),
    (0.12, pose(root_y=-6, waist=50, head=-28, head_z=22, arms=-40, arm_l=-88, legs=-36, leg_l=-28, arm_spread=8, spread_l=30, waist_z=-6, leg_spread=12)),
    (0.25, pose(root_y=-2, waist=28, head=-14, head_z=46, arms=-150, arm_l=-52, legs=46, leg_l=40, arm_spread=6, spread_l=40, waist_z=10, leg_spread=8)),
    (0.38, pose(root_y=-3, waist=46, head=-34, head_z=18, arms=-100, arm_l=-124, legs=40, leg_l=34, arm_spread=8, spread_l=18, waist_z=-8, leg_spread=9)),
    (0.50, pose(root_y=-4, waist=55, head=-40, head_z=34, arms=-60, arm_l=-142, legs=30, leg_l=22, arm_spread=8, spread_l=24, waist_z=8, leg_spread=10)),
], True)

# Salto de sapo da perseguição. Sincronizado com o servidor:
# impulso no tick 5 (0,25 s), 12 ticks no ar, cai no tick ~17 (0,85 s).
ANIMS["leap"] = animation(1.2, [
    (0.00, crouch()),
    (0.15, crouch(root_y=-8.5, waist=62, head=-58, arms=-56, legs=-58)),        # carrega (encolhe)
    (0.25, pose(root_y=-2, waist=46, head=-40, arms=-92, legs=36, arm_spread=4)),   # impulso (ref 1,3 s)
    (0.40, pose(root_y=0, waist=60, head=-54, arms=-105, legs=52, arm_spread=4)),   # subindo (ref 1,6 s)
    (0.60, pose(root_y=0, waist=84, head=-76, arms=-118, legs=90, arm_spread=4)),   # planando (ref 1,9 s)
    (0.78, pose(root_y=0, waist=96, head=-84, arms=-108, legs=102, arm_spread=6)),  # nariz pra baixo
    (0.86, pose(root_y=-2, waist=100, head=-84, arms=-100, legs=108, arm_spread=8)),  # cai nas mãos (ref 2,4 s)
    (1.00, crouch(root_y=-6, waist=70, head=-64, arms=-84, legs=10)),           # pernas vêm por baixo
    (1.20, crouch()),
], "hold_on_last_frame")

# Bote baixo que derruba: igual ao salto, mas os braços vão pra frente pra
# agarrar e ele cai montado na vítima. Impulso no tick 4, impacto no ~15.
# Cabeça olhando pro rosto da vítima (deitada a ~2 blocos): o rosto dele aponta
# uns 23 graus pra baixo, então quem está preso vê a CARA dele, não o topo da coroa.
PIN_POSE = dict(root_y=-9, waist=62, head=-39, arms=-82, legs=48, arm_spread=14, leg_spread=16)
ANIMS["jump_pin"] = animation(1.0, [
    (0.00, crouch()),
    (0.12, crouch(root_y=-8, waist=80, head=-72, arms=-92, legs=64)),
    (0.20, pose(root_y=-2, waist=50, head=-40, arms=-145, legs=72, arm_spread=8)),
    (0.45, pose(root_y=0, waist=78, head=-66, arms=-165, legs=86, arm_spread=10)),   # voando, braços pra agarrar
    (0.68, pose(root_y=0, waist=92, head=-56, arms=-150, legs=96, arm_spread=12)),
    (0.76, pose(root_y=-6, waist=82, head=-36, arms=-118, legs=64, arm_spread=14, leg_spread=12)),  # impacto
    (1.00, pose(**PIN_POSE)),
], "hold_on_last_frame")

# Salto alto (ref 2,7-3,2 s): junta, explode pra cima esticado, encolhe no
# topo e mergulha com os braços na vítima. Impulso no tick 6, impacto no ~22.
ANIMS["high_jump"] = animation(1.6, [
    (0.00, crouch()),
    (0.20, pose(root_y=-9, waist=58, head=-44, arms=-40, legs=34, arm_spread=8, leg_spread=10)),   # junta
    (0.30, pose(root_y=0, waist=12, head=-14, arms=-172, legs=8, arm_spread=6)),                   # explode
    (0.55, pose(root_y=0, waist=4, head=-6, arms=-176, legs=2, arm_spread=8)),                     # esticado subindo
    (0.75, pose(root_y=0, waist=34, head=-30, arms=-128, legs=-46, leg_l=-38, arm_spread=12)),     # encolhe no topo
    (0.95, pose(root_y=0, waist=72, head=-52, arms=-168, legs=34, arm_spread=12)),                 # mergulho
    (1.10, pose(root_y=-7, waist=82, head=-34, arms=-120, legs=58, arm_spread=14, leg_spread=12)), # impacto
    (1.35, pose(root_y=-10, waist=66, head=-10, arms=-90, legs=50, arm_spread=16, leg_spread=16)),
    (1.60, pose(**PIN_POSE)),
], "hold_on_last_frame")

# Montado na vítima ("devorar"): agachado por cima, mãos segurando, cabeça
# recua e desce pra morder (0,5 s) e sacode pra rasgar. 1 s por mordida, igual
# ao servidor (uma mordida a cada 20 ticks).
ANIMS["pin_hold"] = animation(1.0, [
    (0.00, pose(**PIN_POSE)),
    (0.30, pose(**{**PIN_POSE, "waist": 56, "head": -48, "arms": -86})),                 # recua
    (0.45, pose(**{**PIN_POSE, "waist": 74, "head": -26, "arms": -76})),                 # avança a boca
    (0.50, pose(**{**PIN_POSE, "waist": 77, "head": -22, "arms": -74})),                 # MORDE
    (0.60, pose(**{**PIN_POSE, "waist": 76, "head": -24, "head_z": 14, "arms": -74})),   # rasga pra um lado
    (0.72, pose(**{**PIN_POSE, "waist": 74, "head": -26, "head_z": -14, "arms": -76})),  # e pro outro
    (0.86, pose(**{**PIN_POSE, "waist": 64, "head": -42, "head_z": 4})),                 # volta te encarando
    (1.00, pose(**PIN_POSE)),
], True)

# Esmagamento (vs AJ, 0:57-1:01, visto de quem está no chão): em pé por cima da
# vítima, os dois braços juntos lá no alto, e desce tudo de uma vez - tronco,
# cabeça e punhos - na cara dela. Um soco por segundo; o impacto é aos 0,5 s.
SMASH_UP = dict(root_y=0, waist=-6, head=20, arms=-170, arm_spread=4, legs=0, leg_spread=6)
ANIMS["smash"] = animation(1.0, [
    (0.00, pose(**SMASH_UP)),
    (0.30, pose(**{**SMASH_UP, "root_y": 0.5, "waist": -14, "head": 10, "arms": -186})),       # arma o golpe
    (0.42, pose(**{**SMASH_UP, "waist": 30, "head": 26, "arms": -122})),                       # descendo
    (0.50, pose(root_y=-4, waist=78, head=40, arms=-56, arm_spread=2, legs=-22, leg_spread=8)),  # IMPACTO
    (0.62, pose(root_y=-4.5, waist=80, head=42, arms=-50, arm_spread=2, legs=-24, leg_spread=8)),
    (0.80, pose(**{**SMASH_UP, "root_y": -1.5, "waist": 35, "head": 26, "arms": -132})),       # sobe de novo
    (1.00, pose(**SMASH_UP)),
], True)

# Pulo altíssimo (vs AJ, 0:56): agacha fundo, explode pra cima com os braços pro
# céu, abre em Y lá em cima (some no céu, ~12 blocos) e despenca olhando pra
# vítima com os braços prontos pro primeiro soco. Impulso no tick 8 (0,4 s),
# topo no ~25 (1,25 s), chão no ~44 (2,2 s). Ao pousar vira o esmagamento.
ANIMS["sky_drop"] = animation(2.2, [
    (0.00, crouch()),
    (0.30, pose(root_y=-10, waist=60, head=-45, arms=-30, legs=-60, arm_spread=8, leg_spread=18)),   # agacha fundo
    (0.40, pose(root_y=0, waist=-5, head=-10, arms=-175, arm_spread=6, legs=5)),                       # explode
    (0.70, pose(root_y=0, waist=0, head=-5, arms=-178, arm_spread=10, legs=0)),                        # subindo esticado
    (1.10, pose(root_y=0, waist=5, head=10, arms=-158, arm_spread=36, legs=0, leg_spread=22)),         # abre em Y
    (1.30, pose(root_y=0, waist=8, head=24, arms=-158, arm_spread=38, legs=0, leg_spread=24)),         # topo, olhando pra baixo
    (1.70, pose(root_y=0, waist=15, head=32, arms=-172, arm_spread=8, legs=-32, leg_spread=15)),       # despencando
    (2.10, pose(root_y=0, waist=20, head=36, arms=-176, arm_spread=6, legs=-52, leg_spread=14)),
    (2.20, pose(root_y=-2, waist=22, head=36, arms=-176, arm_spread=6, legs=-40, leg_spread=12)),
], "hold_on_last_frame")

# Arrancar o braço (vs AJ, 2:21): agachado por cima, agarra o ombro esquerdo da
# vítima com as duas mãos, puxa, faz força pros dois lados e ARRANCA num tranco
# (1,1 s = tick 22 do servidor). Levanta segurando o braço na mão direita.
RIP_BASE = dict(root_y=-8, waist=65, head=-30, arms=-80, legs=48, arm_spread=10, leg_spread=16)
ANIMS["arm_rip"] = animation(2.0, [
    (0.00, pose(**RIP_BASE)),
    (0.30, pose(**{**RIP_BASE, "waist": 82, "head": -18, "arms": -96, "arm_spread": 3})),                    # agarra
    (0.70, pose(**{**RIP_BASE, "root_y": -6, "waist": 60, "head": -38, "arms": -112, "arm_spread": 1})),     # puxa
    (0.85, pose(**{**RIP_BASE, "root_y": -6, "waist": 52, "head": -46, "arms": -122, "arm_spread": 1, "waist_z": 7})),
    (1.00, pose(**{**RIP_BASE, "root_y": -6, "waist": 54, "head": -44, "arms": -120, "arm_spread": 1, "waist_z": -7})),
    (1.10, pose(root_y=-2, waist=-10, head=-30, arms=-162, arm_spread=4, legs=20, leg_spread=12)),           # TRANCO
    (1.30, pose(root_y=-1, waist=-5, head=-12, arms=-170, arm_l=-40, arm_spread=6, legs=8, leg_spread=8)),   # braço pro alto
    (1.60, pose(root_y=0, waist=5, head=10, arms=-110, arm_l=-10, arm_spread=2, legs=0, leg_spread=4)),      # olha o braço
    (2.00, pose(root_y=0, waist=6, head=6, arms=-100, arm_l=-14, arm_spread=-4, legs=0, leg_spread=4)),
], "hold_on_last_frame")

# Comer o braço: segura com as duas mãos na boca, como milho, e mastiga
# arrancando pedaço (cabeça sacode). Uma mordida a cada 0,75 s (aos 0,5 s,
# 1,25 s, 2 s, 2,75 s e 3,5 s = ticks 10, 25, 40, 55 e 70). Engole aos 4 s
# (olhando pra cima) e esfrega a barriga.
def chew(bite, side):
    # mãos na boca: braço bem pra cima e virado pra dentro
    return pose(root_y=0, waist=16 if bite else 10, head=18 if bite else -4, head_z=16 * side if bite else 0,
                arms=-132 if bite else -124, arm_l=-126 if bite else -118, arm_ry=-26, arm_spread=-8,
                legs=0, leg_spread=4)
eat_keys = [(0.0, pose(root_y=0, waist=6, head=6, arms=-100, arm_l=-14, arm_spread=-4, leg_spread=4)),
            (0.30, chew(False, 0))]
for k in range(5):
    t = 0.5 + k * 0.75
    eat_keys.append((round(t, 3), chew(True, 1 if k % 2 == 0 else -1)))
    eat_keys.append((round(t + 0.35, 3), chew(False, 0)))
eat_keys += [
    (4.00, pose(root_y=0, waist=-8, head=-28, arms=-30, arm_l=-20, arm_spread=2, leg_spread=4)),     # engole
    (4.25, pose(root_y=0, waist=4, head=10, arms=-38, arm_l=-8, arm_spread=-16, leg_spread=4)),      # mão na barriga
    (4.50, pose(root_y=0, waist=4, head=12, arms=-30, arm_l=-6, arm_spread=-18, leg_spread=4)),
]
ANIMS["arm_eat"] = animation(4.5, eat_keys, "hold_on_last_frame")

# Contorção da fase 2 (vídeo "I'm the horror mod", quadro a quadro): a cabeça
# cai pra frente e gira quase de ponta-cabeça, levanta torta, cai pro outro
# lado; os braços ficam moles e um ombro sobe. 2,7 s (54 ticks), com o grito.
def contort_p(head, head_z, arms=8, arm_l=None, spread=-4, spread_l=None, waist=22, waist_z=0, px=0.0):
    return pose(root_y=-1, root_px=px, waist=waist, waist_z=waist_z, head=head, head_z=head_z,
                arms=arms, arm_l=arm_l, arm_spread=spread, spread_l=spread_l, legs=-6, leg_spread=6)
ANIMS["contort"] = animation(2.7, [
    (0.00, pose()),
    (0.10, contort_p(90, 45, px=0.5)),
    (0.25, contort_p(96, 15, px=-0.4)),
    (0.40, contort_p(84, -25, px=0.5)),
    (0.55, contort_p(102, -52, px=-0.5)),
    (0.70, contort_p(58, 30, arms=-42, px=0.4)),
    (0.85, contort_p(-10, 20, waist=15, px=-0.3)),
    (1.10, contort_p(-6, 26, arms=-30, arm_l=16, waist=15, px=0.3)),
    (1.35, contort_p(10, -30, waist_z=8, px=-0.4)),
    (1.55, contort_p(60, -42, px=0.4)),
    (1.75, contort_p(20, 56, arm_l=-60, spread_l=40, waist=20, waist_z=-10, px=-0.3)),
    (2.00, contort_p(26, 62, arm_l=-72, spread_l=46, waist=20, waist_z=-10, px=0.3)),
    (2.30, contort_p(14, 50, arms=-26, arm_l=-50, spread_l=30, px=-0.2)),
    (2.55, contort_p(0, 20, arms=0, arm_l=0, spread_l=-4, waist=12)),
    (2.70, contort_p(-5, 10, arms=0, arm_l=0, spread_l=-4, waist=10)),
], "hold_on_last_frame")


# ------------------------------------------------------------------ amostragem (pra teste)

def sample(anim, t):
    out = {}
    for bone, chans in anim["bones"].items():
        out[bone] = {}
        for ch, kf in chans.items():
            ts = sorted(kf, key=float)
            tf = [float(x) for x in ts]
            vals = [kf[x] for x in ts]
            if t <= tf[0]:
                v = vals[0]
            elif t >= tf[-1]:
                v = vals[-1]
            else:
                for i in range(len(tf) - 1):
                    if tf[i] <= t <= tf[i + 1]:
                        a = (t - tf[i]) / (tf[i + 1] - tf[i])
                        v = [vals[i][j] + (vals[i + 1][j] - vals[i][j]) * a for j in range(3)]
                        break
            out[bone][ch] = v
    return out


def static(p):
    return {"loop": True, "animation_length": 1.0,
            "bones": {b: {c: {"0.0": v, "1.0": v} for c, v in ch.items()} for b, ch in p.items()}}


def main():
    data = json.load(open(MAIN))
    for name, anim in ANIMS.items():
        data["animations"][name] = anim
    with open(MAIN, "w") as f:
        json.dump(data, f, indent=2)
    print("atualizado:", ", ".join(ANIMS))

    if "--debug" in sys.argv:
        times = {
            "run": [0.0, 0.1, 0.2, 0.3, 0.4],
            "run_frenzy": [0.0, 0.12, 0.25, 0.38],
            "smash": [0.0, 0.3, 0.5, 0.8],
            "sky_drop": [0.3, 0.4, 1.1, 1.3, 1.7, 2.2],
            "arm_rip": [0.3, 0.7, 1.1, 1.6, 2.0],
            "arm_eat": [0.5, 0.85, 4.0, 4.5],
            "contort": [0.1, 0.55, 0.85, 1.55, 2.0],
            "leap": [0.0, 0.15, 0.25, 0.4, 0.6, 0.78, 0.86, 1.0],
            "jump_pin": [0.2, 0.45, 0.68, 0.76, 1.0],
            "high_jump": [0.2, 0.3, 0.55, 0.75, 0.95, 1.1],
            "pin_hold": [0.0, 0.5],
        }
        dbg = {"format_version": "1.8.0", "animations": {}}
        for name, ts in times.items():
            for t in ts:
                dbg["animations"][f"dbg_{name}_{int(round(t * 100)):03d}"] = static(sample(ANIMS[name], t))
        with open(DEBUG, "w") as f:
            json.dump(dbg, f, indent=1)
        print("poses de teste:", len(dbg["animations"]))


if __name__ == "__main__":
    main()
