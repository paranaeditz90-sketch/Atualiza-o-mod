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
         arm_l=None, leg_l=None, head_z=0.0, waist_z=0.0, arm_spread=0.0, leg_spread=0.0):
    """Uma pose completa. arms/legs valem pros dois lados, a não ser que
    arm_l/leg_l digam outra coisa pro esquerdo. spread abre os membros pros lados."""
    al = arms if arm_l is None else arm_l
    ll = legs if leg_l is None else leg_l
    return {
        "root": {"position": [0, root_y, 0], "rotation": [root_x, 0, 0]},
        "waist": {"rotation": [waist, 0, waist_z]},
        "head": {"rotation": [head, 0, head_z]},
        "right_arm": {"rotation": [arms, 0, arm_spread]},
        "left_arm": {"rotation": [al, 0, -arm_spread]},
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

# Corrida de quatro (perto do alvo): galope baixo e bruto, braços e pernas
# alternando como um bicho. 0,5 s por passada.
ANIMS["run"] = animation(0.5, [
    (0.00, pose(root_y=-5, waist=64, head=-58, arms=-128, arm_l=-104, legs=66, leg_l=52, arm_spread=7, leg_spread=9)),
    (0.12, pose(root_y=-3.5, waist=60, head=-55, arms=-90, arm_l=-128, legs=46, leg_l=68, arm_spread=7, leg_spread=9)),
    (0.25, pose(root_y=-5.5, waist=66, head=-60, arms=-58, arm_l=-88, legs=36, leg_l=50, arm_spread=7, leg_spread=9)),
    (0.38, pose(root_y=-3.5, waist=61, head=-56, arms=-98, arm_l=-60, legs=54, leg_l=36, arm_spread=7, leg_spread=9)),
    (0.50, pose(root_y=-5, waist=64, head=-58, arms=-128, arm_l=-104, legs=66, leg_l=52, arm_spread=7, leg_spread=9)),
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

# Montado na vítima: agachado por cima, mãos segurando, cabeça descendo pra
# morder e sacudindo pra rasgar. 0,7 s, combina com a mordida a cada 14 ticks.
ANIMS["pin_hold"] = animation(0.7, [
    (0.00, pose(**PIN_POSE)),
    (0.14, pose(**{**PIN_POSE, "waist": 72, "head": -30, "arms": -76})),                 # avança a boca
    (0.22, pose(**{**PIN_POSE, "waist": 76, "head": -24, "head_z": 12, "arms": -74})),   # rasga pra um lado
    (0.32, pose(**{**PIN_POSE, "waist": 74, "head": -26, "head_z": -12, "arms": -76})),  # e pro outro
    (0.46, pose(**{**PIN_POSE, "waist": 64, "head": -42, "head_z": 6})),                 # volta te encarando
    (0.70, pose(**PIN_POSE)),
], True)


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
            "run": [0.0, 0.12, 0.25, 0.38],
            "leap": [0.0, 0.15, 0.25, 0.4, 0.6, 0.78, 0.86, 1.0],
            "jump_pin": [0.2, 0.45, 0.68, 0.76, 1.0],
            "high_jump": [0.2, 0.3, 0.55, 0.75, 0.95, 1.1],
            "pin_hold": [0.0, 0.2],
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
