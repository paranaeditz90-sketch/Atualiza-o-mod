#!/usr/bin/env python3
"""Gera as animações de locomoção/bote do Froggydude.

Uso:
    python tools/anim/build_anims.py              # atualiza froggydude.animation.json
    python tools/anim/build_anims.py --debug      # também gera poses congeladas (dbg_*) pra teste

As outras animações do arquivo (idle, walk, tired, bite, a língua esticando...)
ficam como estão; este script só reescreve as que estão definidas aqui.

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
import copy
import json
import os
import sys

import rig

HERE = os.path.dirname(os.path.abspath(__file__))
ANIM_DIR = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "froggydude", "animations")
MAIN = os.path.join(ANIM_DIR, "froggydude.animation.json")
DEBUG = os.path.join(ANIM_DIR, "froggydude_debug.animation.json")

BONES = ("root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg")


def pose(root_y=0.0, root_x=0.0, waist=0.0, head=0.0, arms=0.0, legs=0.0,
         arm_l=None, leg_l=None, head_z=0.0, waist_z=0.0, arm_spread=0.0, leg_spread=0.0,
         root_px=0.0, head_y=0.0, waist_y=0.0, arm_ry=0.0, arm_ly=None, spread_l=None,
         arm_py=0.0, arm_lpy=0.0, leg_spread_l=None, head_pos=None):
    """Uma pose completa. arms/legs valem pros dois lados, a não ser que
    arm_l/leg_l digam outra coisa pro esquerdo. spread abre os membros pros lados
    (spread_l pro braço esquerdo, se for diferente). arm_ry gira o braço no
    próprio eixo (Y). arm_py/arm_lpy sobem o OMBRO (px; o braço inteiro sobe,
    como um ombro deslocado - a pose torta da fase 2)."""
    al = arms if arm_l is None else arm_l
    ll = legs if leg_l is None else leg_l
    aly = arm_ry if arm_ly is None else arm_ly
    sl = arm_spread if spread_l is None else spread_l
    lsl = leg_spread if leg_spread_l is None else leg_spread_l
    p = {
        "root": {"position": [root_px, root_y, 0], "rotation": [root_x, 0, 0]},
        "waist": {"rotation": [waist, waist_y, waist_z]},
        "head": {"rotation": [head, head_y, head_z]},
        "right_arm": {"rotation": [arms, arm_ry, arm_spread]},
        "left_arm": {"rotation": [al, -aly, -sl]},
        "right_leg": {"rotation": [legs, 0, leg_spread]},
        "left_leg": {"rotation": [ll, 0, -lsl]},
    }
    if arm_py or arm_lpy:
        p["right_arm"]["position"] = [0, arm_py, 0]
        p["left_arm"]["position"] = [0, arm_lpy, 0]
    if head_pos:
        p["head"]["position"] = list(head_pos)  # pescoço deslocado (px, eixos do json)
    return p


def lift(p, slack=1.0):
    """Rede de segurança (v0.3.4): se mão ou pé afunda mais que `slack` px no
    chão, sobe o corpo inteiro o quanto afundou (as pernas não dobram)."""
    fr = rig.frames(p)
    low = rig.lowest(fr)
    m = min(low[b] for b in ("right_arm", "left_arm", "right_leg", "left_leg"))
    if m >= -slack:
        return p
    p = copy.deepcopy(p)
    p["root"]["position"][1] = round(p["root"]["position"][1] + (-slack - m), 2)
    return p


def blend(p1, p2, a):
    """Pose no meio do caminho entre duas (o GeckoLib interpola linear, canal por canal)."""
    out = {}
    for bone, chans in p1.items():
        out[bone] = {}
        for ch, v in chans.items():
            w = p2.get(bone, {}).get(ch, v)
            out[bone][ch] = [v[i] + (w[i] - v[i]) * a for i in range(3)]
    return out


def sinks(p, slack=1.5):
    low = rig.lowest(rig.frames(p))
    return min(low[b] for b in ("right_arm", "left_arm", "right_leg", "left_leg")) < -slack


def animation(length, keys, loop, ground=True):
    """keys: lista de (tempo, pose). Gera o formato do GeckoLib/Bedrock.
    ground=False pra animações fora do chão (escalando a parede)."""
    if ground:
        keys = [(t, lift(p)) for t, p in keys]
        # na transição entre dois quadros (quadril subindo, perna voltando) o pé
        # ainda pode afundar: põe um quadro no meio, levantado
        fixed = [keys[0]]
        for (t0, p0), (t1, p1) in zip(keys, keys[1:]):
            for a in (0.33, 0.5, 0.67):
                mid = blend(p0, p1, a)
                if sinks(mid):
                    fixed.append((round(t0 + (t1 - t0) * 0.5, 4), lift(blend(p0, p1, 0.5))))
                    break
            fixed.append((t1, p1))
        keys = fixed
    # canal que só aparece em alguns quadros (posição do ombro): zero nos outros,
    # senão o GeckoLib segura o valor do quadro vizinho
    extra = {(b, ch) for _, p in keys for b, chans in p.items() for ch in chans}
    for _, p in keys:
        for b, ch in extra:
            p.setdefault(b, {}).setdefault(ch, [0, 0, 0])
    bones = {}
    for t, p in keys:
        for bone, channels in p.items():
            for ch, v in channels.items():
                bones.setdefault(bone, {}).setdefault(ch, {})[f"{t:.4g}" if t else "0.0"] = [round(x, 2) for x in v]
    return {"loop": loop, "animation_length": length, "bones": bones}


# ------------------------------------------------------------------ poses base

def leg_back(root_y, slack=1.0):
    """Menor ângulo (pra trás) que a perna reta precisa pra o pé não afundar no
    chão com o quadril abaixado root_y px (as pernas não dobram: abaixar o
    quadril com a perna em pé enterra o pé - os "pranchões" dos prints)."""
    import math
    hip = 12.0 + root_y
    for deg in range(0, 91):
        r = math.radians(deg)
        if 12.0 * math.cos(r) + 2.25 * math.sin(r) <= hip + slack:
            return float(deg)
    return 90.0


# agachado de sapo, rente ao chão (ref. 0,6 s e 0,8 s): tronco quase deitado,
# mãos no chão na frente, pernas dobradas pra trás, cabeça olhando à frente
CROUCH = dict(root_y=-7, waist=56, head=-52, arms=-62, legs=leg_back(-7), arm_spread=6, leg_spread=16)


def crouch(**over):
    p = dict(CROUCH)
    p.update(over)
    return pose(**p)


ANIMS = {}

# Parado de quatro (crouch_idle) e helpers do galope antigo: com a cintura a 90
# graus, braço a -90 e perna a 0, as mãos e os pés tocam o chão sozinhos.
def quad(waist, arm_r, arm_l, leg_r, leg_l, y, look=24, wz=0.0):
    return pose(root_y=y, waist=waist, head=-waist + look, arms=arm_r, arm_l=arm_l,
                legs=leg_r, leg_l=leg_l, arm_spread=4, leg_spread=4, waist_z=wz)


QUAD_STAND = dict(waist=90, arm_r=-90, arm_l=-88, leg_r=0, leg_l=4, y=-1.5)


# Galope de 4 (v0.3.4) - RACING A CHEETAH, close de 5,25-6,6 s a 60 quadros por
# segundo (scratchpad/REFS_v034.md). Uma passada a cada 15 quadros (0,25 s), sem
# variar. NÃO é o trote de cachorro de antes: é um "cavalinho de pau" -
#   empina (tronco quase em pé, cabeça lá no alto, em pé nas pernas)
#   -> mergulha pra frente esticando os braços na horizontal (voando, todo esticado)
#   -> as mãos batem no chão e as pernas CHUTAM pra cima, acima das costas
#   -> as pernas descem e vêm pra frente por baixo da barriga (reunido, cabeça baixa,
#      a poeira sobe das patas de trás) -> empina de novo.
# Medido nesse vídeo (câmera parada na linha de chegada, escala pelos postes da
# cerca): ~15 blocos/s, passada de ~3,8 blocos. O jogo toca a animação mais devagar
# ou mais rápido junto com a velocidade real (mainAnimSpeed).
# Colunas: fração da passada, cintura, ângulo do braço direito e esquerdo no MUNDO
# (0 = pra baixo, + = pra frente), perna direita e esquerda (+ = pra trás), olhar
# (+ = cara pra baixo), quem encosta no chão, quanto o corpo sobe no ar (px), giro lateral.
GALLOP = [
    (0 / 15, 58, -4, 2, -38, -32, -6, "both", 0.0, 2),      # saindo da reunião: compacto, cabeça subindo
    (1 / 15, 24, 18, 24, 2, 8, -4, "feet", 0.0, 0),          # EMPINA: quase em pé, cabeça no alto
    (2 / 15, 45, 48, 54, 14, 20, 4, "feet", 0.0, -1),        # mergulha, braços indo pra frente
    (3 / 15, 66, 74, 80, 34, 40, 8, "feet", 0.0, -2),        # pernas ainda empurrando o chão
    (4 / 15, 84, 96, 100, 56, 60, 10, "air", 2.0, -2),       # voando
    (5 / 15, 90, 102, 106, 70, 74, 12, "air", 3.0, -1),      # TODO ESTICADO
    (6 / 15, 95, 62, 70, 96, 92, 16, "hands", 0.0, 0),       # as mãos pousam
    (7 / 15, 100, 26, 32, 124, 116, 24, "hands", 0.0, 1),    # mãos plantadas, pernas chutam pro alto
    (8 / 15, 102, 10, 14, 118, 108, 28, "hands", 0.0, 2),
    (9 / 15, 104, 0, 4, 82, 48, 30, "hands", 0.0, 2),        # pernas descendo (uma na frente)
    (10 / 15, 106, -12, -8, 28, 10, 32, "both", 0.0, 1),
    (11 / 15, 110, -20, -16, -30, -20, 34, "both", 0.0, 0),  # REÚNE: cabeça lá embaixo, quadril alto
    (12 / 15, 112, -22, -18, -34, -24, 36, "both", 0.0, -1), # (poeira)
    (13 / 15, 106, -18, -14, -36, -28, 30, "both", 0.0, -1),
    (14 / 15, 82, -12, -6, -38, -30, 10, "both", 0.0, 1),    # começa a subir, ainda compacto
]


def gallop_pose(w, th_r, th_l, l_r, l_l, look, contact, air, wz):
    p = pose(waist=w, head=-w + look, arms=-(w + th_r), arm_l=-(w + th_l), legs=l_r, leg_l=l_l,
             arm_spread=4, leg_spread=5, waist_z=wz)
    # altura do corpo: quem está no chão encosta (0,5 px), quem está no ar sobe
    low = rig.lowest(rig.frames(p))
    limbs = {"feet": ("right_leg", "left_leg"), "hands": ("right_arm", "left_arm"),
             "both": ("right_leg", "left_leg", "right_arm", "left_arm"),
             "air": ("right_leg", "left_leg", "right_arm", "left_arm")}[contact]
    lowest = min(low[b] for b in limbs)
    p["root"]["position"][1] = round(0.5 + air - lowest, 2)
    return p


def gallop(table, length):
    keys = [(round(f * length, 4), gallop_pose(*row)) for f, *row in table]
    keys.append((length, keys[0][1]))
    return animation(length, keys, True)


ANIMS["run"] = gallop(GALLOP, 0.25)


# Fase 2, ataque feroz (v0.3.4) - short "Throwing hands with AJTHEBOLD" (versão
# longa 8b375ecc, 11,6-12,4 s, quadro a quadro; scratchpad/REFS_v034.md): depois de
# se contorcer ele NÃO vai de quatro - dispara EM PÉ, de um jeito desesperado,
# gritando: o tronco quase em pé (nos quadros de perto ele fica comprido, inteiro -
# uns 15-20° pra frente, não mais), rolando de um lado pro outro a cada passo, a
# cabeça tombada uns 20-30° pro ombro esquerdo dele (os dois vídeos), balançando, o braço DIREITO aberto pro lado se
# debatendo (a mão na altura do quadril, pra fora), o esquerdo colado no corpo
# bombeando, passadas largas e MUITO rápido (atravessa ~15 blocos em menos de 1 s).
# 0,3 s por ciclo (dois passos) na velocidade 1.
# Colunas: fração, perna direita/esquerda (+ = trás), braço direito (X, abertura),
# braço esquerdo (X), cintura, giro lateral da cintura, tombo da cabeça, contato, ar (px).
SPRINT = [
    (0.00, -50, 40, 18, 34, -44, 16, -8, 26, "feet", 0.0),     # pisa com a direita, braço direito pra trás e aberto
    (0.12, -10, 10, 4, 40, -14, 20, -2, 16, "air", 1.0),       # passando, empurra
    (0.25, 30, -30, -20, 36, 16, 14, 4, 6, "air", 2.0),       # no ar
    (0.50, 40, -50, -28, 30, 30, 16, 8, 22, "feet", 0.0),      # pisa com a esquerda, braço direito pra frente
    (0.62, 10, -10, -10, 42, 8, 20, 2, 30, "air", 1.0),        # o braço direito se debatendo lá fora
    (0.75, -30, 30, 12, 36, -20, 14, -4, 18, "air", 2.0),
]


def sprint_pose(lr, ll, ar, spread_r, al, w, wz, hz, contact, air):
    p = pose(waist=w, head=-w + 6, head_z=hz, head_y=-wz * 1.5, arms=ar, arm_l=al,
             arm_spread=spread_r, spread_l=8, legs=lr, leg_l=ll, leg_spread=5, waist_z=wz)
    low = rig.lowest(rig.frames(p))
    feet = min(low["right_leg"], low["left_leg"])
    p["root"]["position"][1] = round(0.5 + air - feet, 2)
    return p


def sprint(table, length):
    keys = [(round(f * length, 4), sprint_pose(*row)) for f, *row in table]
    keys.append((length, keys[0][1]))
    return animation(length, keys, True)


ANIMS["run_frenzy"] = sprint(SPRINT, 0.3)

# Parado no meio da fase 2 (esperando o próximo bote): em pé, curvado, ofegando,
# cabeça tombada, um braço meio erguido - não fica de quatro nem em pé normal.
FRENZY_STAND = dict(root_y=-0.5, waist=20, head=-12, head_z=22, arms=-20, arm_spread=34, arm_l=-8,
                    spread_l=8, legs=6, leg_l=-4, leg_spread=9)
ANIMS["frenzy_idle"] = animation(0.8, [
    (0.00, pose(**FRENZY_STAND)),
    (0.25, pose(**{**FRENZY_STAND, "waist": 26, "head": -16, "head_z": 28, "arms": -28})),   # puxa o ar
    (0.50, pose(**{**FRENZY_STAND, "waist": 18, "head": -10, "head_z": 18, "arms": -16})),
    (0.62, pose(**{**FRENZY_STAND, "head_z": 34, "head_y": 12})),                          # tranco
    (0.80, pose(**FRENZY_STAND)),
], True)


# Escalando (igual aranha - "ele consegue escalar", vs Grox 1:10): grudado na
# parede de quatro, braços e pernas bem abertos, um braço alcança lá em cima
# enquanto o outro puxa, as pernas empurram alternando, a cabeça olhando pra
# cima (pra vítima). 0,5 s por passada.
def climb_p(side, y):
    # sapo grudado no vidro: braços e pernas bem abertos pros lados
    up, down = (1, 0) if side > 0 else (0, 1)
    arm = (-168, -96)
    spread = (38, 58)
    leg = (-74, 8)
    lspread = (42, 30)
    return pose(root_y=y, waist=20, head=-46, arms=arm[1 - up], arm_l=arm[1 - down],
                arm_spread=spread[1 - up], spread_l=spread[1 - down],
                legs=leg[up], leg_l=leg[down], leg_spread=36)
ANIMS["climb"] = animation(0.5, [
    (0.00, climb_p(1, 0.0)),
    (0.25, climb_p(-1, -1.0)),
    (0.50, climb_p(1, 0.0)),
], True, ground=False)

# Parado um instante no meio da caçada: continua de quatro, em pé nos braços e
# nas pernas (igual ao galope), respirando - não levanta e abaixa de novo.
ANIMS["crouch_idle"] = animation(1.2, [
    (0.00, quad(**QUAD_STAND)),
    (0.60, quad(**{**QUAD_STAND, "waist": 92, "y": -1.8})),
    (1.20, quad(**QUAD_STAND)),
], True)

# Salto de sapo da perseguição. Sincronizado com o servidor:
# impulso no tick 5 (0,25 s), 12 ticks no ar, cai no tick ~17 (0,85 s).
ANIMS["leap"] = animation(1.2, [
    (0.00, crouch()),
    (0.15, crouch(root_y=-8.5, waist=62, head=-58, arms=-56, legs=leg_back(-8.5))),  # carrega (encolhe)
    (0.25, pose(root_y=-2, waist=46, head=-40, arms=-92, legs=36, arm_spread=4)),   # impulso (ref 1,3 s)
    (0.40, pose(root_y=0, waist=60, head=-54, arms=-105, legs=52, arm_spread=4)),   # subindo (ref 1,6 s)
    (0.60, pose(root_y=0, waist=84, head=-76, arms=-118, legs=90, arm_spread=4)),   # planando (ref 1,9 s)
    (0.78, pose(root_y=0, waist=96, head=-84, arms=-108, legs=102, arm_spread=6)),  # nariz pra baixo
    (0.86, pose(root_y=-2, waist=100, head=-84, arms=-100, legs=108, arm_spread=8)),  # cai nas mãos (ref 2,4 s)
    (1.00, crouch(root_y=-6, waist=70, head=-64, arms=-84, legs=leg_back(-6))),  # pernas vêm por baixo
    (1.20, crouch()),
], "hold_on_last_frame")

# Bote baixo que derruba: igual ao salto, mas os braços vão pra frente pra
# agarrar e ele cai montado na vítima. Impulso no tick 4, impacto no ~15.
# Cabeça olhando pro rosto da vítima (deitada a ~2 blocos): o rosto dele aponta
# uns 23 graus pra baixo, então quem está preso vê a CARA dele, não o topo da coroa.
# (v0.3.4) Medido com tools/anim/check_poses.py: a pose antiga (quadril 9 px abaixo e
# pernas a 48 graus) enterrava as pernas meio bloco no chão - os "pranchões" com a
# perna dentro da terra. Agora o quadril desce 6 px e as pernas vão bem pra trás
# (74 graus): os pés encostam no chão, as mãos seguram a vítima (a 1,5 bloco) e a
# cabeça fica a meio bloco do olho dela.
PIN_POSE = dict(root_y=-6, waist=65, head=-30, arms=-105, legs=74, arm_spread=12, leg_spread=16)
ANIMS["jump_pin"] = animation(1.0, [
    (0.00, crouch()),
    (0.12, crouch(root_y=-8, waist=80, head=-72, arms=-92, legs=leg_back(-8))),
    (0.20, pose(root_y=-2, waist=50, head=-40, arms=-145, legs=72, arm_spread=8)),
    (0.45, pose(root_y=0, waist=78, head=-66, arms=-165, legs=86, arm_spread=10)),   # voando, braços pra agarrar
    (0.68, pose(root_y=0, waist=92, head=-56, arms=-150, legs=96, arm_spread=12)),
    (0.76, pose(root_y=-6, waist=82, head=-36, arms=-128, legs=leg_back(-6), arm_spread=14, leg_spread=12)),  # impacto
    (1.00, pose(**PIN_POSE)),
], "hold_on_last_frame")

# Salto alto (ref 2,7-3,2 s): junta, explode pra cima esticado, encolhe no
# topo e mergulha com os braços na vítima. Impulso no tick 6, impacto no ~22.
ANIMS["high_jump"] = animation(1.6, [
    (0.00, crouch()),
    (0.20, pose(root_y=-8, waist=58, head=-44, arms=-40, legs=leg_back(-8), arm_spread=8, leg_spread=10)),   # junta
    (0.30, pose(root_y=0, waist=12, head=-14, arms=-172, legs=8, arm_spread=6)),                   # explode
    (0.55, pose(root_y=0, waist=4, head=-6, arms=-176, legs=2, arm_spread=8)),                     # esticado subindo
    (0.75, pose(root_y=0, waist=34, head=-30, arms=-128, legs=-46, leg_l=-38, arm_spread=12)),     # encolhe no topo
    (0.95, pose(root_y=0, waist=72, head=-52, arms=-168, legs=34, arm_spread=12)),                 # mergulho
    (1.10, pose(root_y=-7, waist=82, head=-34, arms=-130, legs=leg_back(-7), arm_spread=14, leg_spread=12)), # impacto
    (1.35, pose(root_y=-7, waist=66, head=-14, arms=-100, legs=leg_back(-7), arm_spread=16, leg_spread=16)),
    (1.60, pose(**PIN_POSE)),
], "hold_on_last_frame")

# Montado na vítima ("devorar"): agachado por cima, mãos segurando, cabeça
# recua e desce pra morder (0,5 s) e sacode pra rasgar. 1 s por mordida, igual
# ao servidor (uma mordida a cada 20 ticks).
ANIMS["pin_hold"] = animation(1.0, [
    (0.00, pose(**PIN_POSE)),
    (0.30, pose(**{**PIN_POSE, "waist": 56, "head": -46, "arms": -98})),                  # recua
    (0.45, pose(**{**PIN_POSE, "waist": 76, "head": -26, "arms": -122})),                 # avança a boca
    (0.50, pose(**{**PIN_POSE, "waist": 80, "head": -24, "arms": -132})),                 # MORDE (mãos em cima dela)
    (0.58, pose(**{**PIN_POSE, "waist": 80, "head": -26, "head_z": 16, "head_y": 30, "arms": -132})),   # rasga sacudindo
    (0.66, pose(**{**PIN_POSE, "waist": 79, "head": -27, "head_z": -16, "head_y": -30, "arms": -130})),  # como cachorro
    (0.74, pose(**{**PIN_POSE, "waist": 77, "head": -28, "head_z": 12, "head_y": 22, "arms": -125})),
    (0.86, pose(**{**PIN_POSE, "waist": 62, "head": -36, "head_z": 4})),                  # volta te encarando
    (1.00, pose(**PIN_POSE)),
], True)

# Esmagamento (vs AJ, 0:57-1:01, visto de quem está no chão): em pé na frente
# da vítima, os dois braços juntos lá no alto, e desce tudo de uma vez - tronco
# e punhos - na cara dela. Dois socos por segundo; impacto aos 0,25 s de cada
# volta de 0,5 s.
# (v0.3.4) O impacto antigo (tronco a 78 graus, cabeça olhando pra baixo, vítima a
# 1,15 bloco) enfiava a CABEÇA dele no olho da vítima (a tela verde/vermelha a cada
# soco) e os punhos batiam no chão 0,8 bloco antes dela. Agora (check_poses.py,
# vítima a 1,5 bloco): no impacto os punhos param a ~0,45 bloco da câmera, a cabeça
# fica a meio bloco, levantada, olhando pra ela, e os pés não afundam.
SMASH_UP = dict(root_y=0, waist=-6, head=30, arms=-170, arm_spread=4, legs=0, leg_spread=6)
SMASH_HIT = dict(root_y=-1, waist=85, head=-40, arms=-120, arm_spread=3, legs=28, leg_spread=8)
ANIMS["smash"] = animation(0.5, [
    (0.00, pose(**SMASH_UP)),
    (0.14, pose(**{**SMASH_UP, "root_y": 0.5, "waist": -14, "head": 20, "arms": -186})),       # arma o golpe
    (0.20, pose(root_y=0, waist=40, head=0, arms=-150, arm_spread=4, legs=10, leg_spread=6)),  # descendo
    (0.25, pose(**SMASH_HIT)),                                                                 # IMPACTO
    (0.31, pose(**{**SMASH_HIT, "root_y": -1.2, "waist": 86, "head": -41, "arms": -116, "legs": 30})),
    (0.40, pose(root_y=0, waist=40, head=0, arms=-150, arm_spread=4, legs=14, leg_spread=6)),     # sobe de novo
    (0.50, pose(**SMASH_UP)),
], True)

# Pulo altíssimo (vs AJ, 0:56): agacha fundo, explode pra cima com os braços pro
# céu, abre em Y lá em cima (some no céu, ~12 blocos) e despenca olhando pra
# vítima com os braços prontos pro primeiro soco. Impulso no tick 8 (0,4 s),
# topo no ~25 (1,25 s), chão no ~44 (2,2 s). Ao pousar vira o esmagamento.
ANIMS["sky_drop"] = animation(2.2, [
    (0.00, crouch()),
    (0.30, pose(root_y=-8, waist=60, head=-45, arms=-60, legs=leg_back(-8), arm_spread=8, leg_spread=18)),   # agacha fundo
    (0.40, pose(root_y=0, waist=-5, head=-10, arms=-175, arm_spread=6, legs=5)),                       # explode
    (0.70, pose(root_y=0, waist=0, head=-5, arms=-178, arm_spread=10, legs=0)),                        # subindo esticado
    (1.10, pose(root_y=0, waist=5, head=10, arms=-158, arm_spread=36, legs=0, leg_spread=22)),         # abre em Y
    (1.30, pose(root_y=0, waist=8, head=24, arms=-158, arm_spread=38, legs=0, leg_spread=24)),         # topo, olhando pra baixo
    (1.70, pose(root_y=0, waist=15, head=32, arms=-172, arm_spread=8, legs=-32, leg_spread=15)),       # despencando
    (2.10, pose(root_y=0, waist=20, head=36, arms=-176, arm_spread=6, legs=-52, leg_spread=14)),
    (2.20, pose(root_y=-2, waist=22, head=36, arms=-176, arm_spread=6, legs=-40, leg_spread=12)),
], "hold_on_last_frame")

# Arrancar o braço (vs AJ, 2:21, e o jeito de comer da "Eating a Zebra"):
# agachado por cima, agarra o ombro esquerdo da vítima com as duas mãos, CRAVA
# os dentes no ombro e sacode a cabeça como cachorro rasgando (0,45-0,95 s),
# puxa com os dentes e as mãos e ARRANCA num tranco (1,1 s = tick 22 do
# servidor). Sai com o braço atravessado na boca, sacudindo, sangue voando.
# (v0.3.4) mesma base do montado (pés no chão, vítima a 1,5 bloco)
RIP_BASE = dict(PIN_POSE)


def rip_p(**over):
    p = dict(RIP_BASE)
    p.update(over)
    return pose(**p)


def shake_p(side, **over):
    p = {"waist": 77, "head": -24, "head_y": 38 * side, "head_z": 14 * side, "waist_z": 5 * side,
         "arms": -126, "arm_spread": 2}
    p.update(over)
    return rip_p(**p)


def mouth_shake(side, amount=50):
    # em pé, o braço atravessado na boca: SACODE a cabeça pra rasgar ("Eating a Zebra",
    # 1,9-3,1 s: ~2,5 sacudidas por segundo, 45-60° pra cada lado, o tronco torcendo
    # junto, o queixo um pouco pra cima, os braços soltos)
    return pose(root_y=0, waist=4, waist_y=14 * side, head=-12, head_y=amount * side, head_z=16 * side,
                arms=-28, arm_l=-18, arm_spread=12, legs=4, leg_spread=8)


# comendo: em pé, o braço arrancado atravessado na boca (o renderer desenha ele preso
# na cabeça), as mãos soltas - igual à zebra, ele não segura com as mãos
EAT_HOLD = dict(root_y=0, waist=8, head=-6, arms=-24, arm_l=-14, arm_spread=10, legs=2, leg_spread=6)

ANIMS["arm_rip"] = animation(2.0, [
    (0.00, rip_p()),
    (0.20, rip_p(waist=74, head=-22, arms=-120, arm_spread=3)),                             # agarra o ombro
    (0.32, rip_p(waist=70, head=-36, arms=-114, arm_spread=2)),                             # abre a boca
    (0.42, rip_p(waist=80, head=-22, arms=-132, arm_spread=2)),                             # CRAVA os dentes
    (0.52, shake_p(1)),
    (0.62, shake_p(-1)),
    (0.72, shake_p(1)),
    (0.82, shake_p(-1)),
    (0.92, shake_p(1, waist=76)),
    (1.02, rip_p(root_y=-5, waist=56, head=-44, arms=-112, arm_spread=1, waist_z=-6)),      # puxa com tudo
    (1.10, pose(root_y=-0.5, waist=-14, head=-36, arms=-150, arm_l=-60, arm_spread=8, legs=20, leg_spread=12)),  # TRANCO
    (1.20, mouth_shake(1)),                                                                 # sacode (2,5 por s)
    (1.40, mouth_shake(-1)),
    (1.60, mouth_shake(1)),
    (1.80, mouth_shake(-1, 40)),
    (2.00, pose(**EAT_HOLD)),                                                               # segura na boca pra comer
], "hold_on_last_frame")


# Comer o braço ("Eating a Zebra"): 3 mordidas - abaixa a cabeça e crava, dá um tranco
# pra trás arrancando o pedaço e SACODE com ele na boca (4 sacudidas, ~2,5 por segundo)
# enquanto o sangue jorra da boca; descansa um instante e repete. Mordidas aos 0,30 s,
# 1,45 s e 2,60 s (= ticks 6, 29 e 52 do servidor, ver FroggyCombatGoal.EAT_BITES).
# Engole aos 4 s, olhando pra cima.
def eat_yank(side):
    return pose(**{**EAT_HOLD, "waist": -6, "head": -28, "head_y": 20 * side, "head_z": 10 * side})


eat_keys = [(0.0, pose(**EAT_HOLD))]
for k in range(3):
    t0 = 0.30 + k * 1.15
    side = 1 if k % 2 == 0 else -1
    eat_keys += [
        (round(t0, 3), pose(**{**EAT_HOLD, "waist": 22, "head": 22})),        # abaixa e crava
        (round(t0 + 0.12, 3), eat_yank(side)),                                # tranco pra trás
        (round(t0 + 0.30, 3), mouth_shake(-side)),
        (round(t0 + 0.50, 3), mouth_shake(side)),
        (round(t0 + 0.70, 3), mouth_shake(-side)),
        (round(t0 + 0.90, 3), mouth_shake(side, 40)),
        (round(t0 + 1.05, 3), pose(**EAT_HOLD)),
    ]
eat_keys += [
    (4.00, pose(root_y=0, waist=-8, head=-28, arms=-30, arm_l=-20, arm_spread=2, leg_spread=4)),     # engole
    (4.25, pose(root_y=0, waist=4, head=10, arms=-38, arm_l=-8, arm_spread=-16, leg_spread=4)),      # mão na barriga
    (4.50, pose(root_y=0, waist=4, head=12, arms=-30, arm_l=-6, arm_spread=-18, leg_spread=4)),
]
ANIMS["arm_eat"] = animation(4.5, eat_keys, "hold_on_last_frame")

# Contorção da fase 2 (v0.3.4) - short "Throwing hands with AJTHEBOLD" (8b375ecc,
# 4,50-6,45 s) e "Minecraft but I'm the horror mod" (49b65850, 0,00-2,64 s), os
# dois quadro a quadro (scratchpad/REFS_v034.md). Tempo da animação = tempo do
# short - 4,07 s. Em pé encarando; a cabeça cai e ele DOBRA num estalo (~0,15 s)
# até o tronco ficar quase na horizontal, com o TOPO da cabeça virado pra quem
# olha e girado uns 45° (o "losango" da coroa nos dois vídeos), pernas abertas,
# mãos penduradas quase no chão; balança pro lado direito dele (a cabeça desce e
# vai pra esquerda de quem olha); SOBE rápido (~0,2 s) com a cabeça ainda virada
# pra frente até o tronco estar em pé, e só então a cabeça levanta; fica em pé
# meio torto (braço direito aberto pra frente, cabeça olha pra cima, depois pra
# baixo) e por fim TRAVA torto: o OMBRO direito sobe (desloca, não é o braço
# erguido: o braço continua pendurado, a mão na altura do peito, um pouco pra
# fora), o tronco inclina um pouco pra TRÁS, a perna esquerda dele vai pra frente e a
# direita pra trás, e a cabeça QUEBRA PRA TRÁS num estalo (~95°, a cara apontando pra
# cima) com o pescoço esticado (2 px pra cima, 1,5 pra frente e 2,5 pro ombro esquerdo -
# sem isso a frente do tronco tapa o queixo, e com mais de ~110° a nuca atravessa o
# peito) e tomba pro ombro esquerdo: de frente aparece o queixo inteiro em cima da gola
# (os dois pixels azuis da skin) com a fileira da boca na borda de cima, inclinado
# descendo pro ombro; olha pra cima de lado. Conferido no jogo contra o short 6,4-7,2 s.
# 2,2 s (44 ticks); depois "contort_hold" (pose torta em loop, quem decide o tempo é
# o servidor) e "roar": o grito, já disparando na corrida.
CROOK = dict(waist=-8, waist_z=10, head=-95, head_z=35, head_pos=(2.5, 2, -1.5), arm_py=4, arms=-6, arm_spread=10,
             arm_l=6, spread_l=4, legs=14, leg_l=-16, leg_spread=5, leg_spread_l=8)


def crook(**over):
    p = dict(CROOK)
    p.update(over)
    return pose(**p)


# dobrado: tronco a ~70° (a cabeça fica a meia altura, as pernas aparecendo embaixo),
# cabeça alinhada com ele (o topo pra frente) e
# girada 45° no próprio eixo, pernas abertas (a esquerda mais), mãos penduradas
FOLD = dict(waist=68, head=6, head_y=45, arm_spread=4, spread_l=6, leg_spread=5, leg_spread_l=14)


def fold(**over):
    p = dict(FOLD)
    p.update(over)
    p.setdefault("arms", -p["waist"])
    return pose(**p)


ANIMS["contort"] = animation(2.2, [
    (0.00, pose()),
    (0.20, pose(waist=8, head=48, arms=-4)),                                     # cabeça baixa
    (0.43, pose(waist=2, head=12, head_z=14, leg_spread=4, leg_spread_l=8)),     # sobe e ENCARA, cabeça tombada (short 4,50)
    (0.48, pose(waist=8, head=42, head_z=16, arms=-8, leg_spread=4, leg_spread_l=8)),    # a cabeça cai (4,55)
    (0.53, pose(waist=35, head=48, head_y=15, arms=-35, leg_spread=4, leg_spread_l=10)),  # CRACK: dobra (4,60)
    (0.57, pose(waist=62, head=14, head_y=35, arms=-60, leg_spread=5, leg_spread_l=12)),  # (4,64)
    (0.62, fold()),                                                              # dobrado, topo da cabeça pra frente (4,69)
    (0.70, fold(waist=70, waist_y=6, head_y=40, head_z=4)),                      # balança pro lado direito dele (4,77)
    (0.80, fold(waist=72, waist_y=12, head=10, head_y=32, head_z=6)),
    (0.92, fold(waist=76, waist_y=18, head=14, head_y=22, head_z=8, arm_l=-80)),  # mais baixo e de lado (4,99)
    (1.02, fold(waist=70, waist_y=10, head=8, head_y=30)),                      # CRACK, volta (5,09)
    (1.07, pose(waist=50, waist_y=4, head=36, head_y=40, arms=-48, leg_spread=5, leg_spread_l=12)),  # SOBE, topo da cabeça ainda pra frente (5,14)
    (1.12, pose(waist=18, head=70, head_y=40, arms=-16, leg_spread=5, leg_spread_l=11)),   # tronco quase em pé, cabeça dobrada (5,19)
    (1.20, pose(waist=10, head=55, head_y=20, head_z=10, arms=-8, arm_spread=10, leg_spread=5, leg_spread_l=10)),  # (5,27)
    # em pé (5,3-6,1 s no short): tronco levemente pra TRÁS, a perna esquerda dele na
    # frente e aberta, a direita pra trás (é a da direita de quem olha que fica à frente)
    (1.27, pose(waist=-2, head=20, head_z=16, arms=-20, arm_spread=28, spread_l=10, legs=4, leg_l=-6, leg_spread=5, leg_spread_l=11)),  # cabeça subindo (5,34)
    (1.38, pose(waist=-4, head=-2, head_z=14, head_y=-6, arms=-40, arm_spread=36, spread_l=8, legs=6, leg_l=-8, leg_spread=5, leg_spread_l=12)),  # braço aberto pra frente (5,45)
    (1.52, pose(waist=-6, head=-22, head_z=8, arms=-10, arm_spread=18, spread_l=8, legs=6, leg_l=-8, leg_spread=5, leg_spread_l=12)),  # olha pra cima (5,59)
    (1.62, pose(waist=-6, head=-20, head_z=4, arm_spread=8, spread_l=6, legs=6, leg_l=-8, leg_spread=5, leg_spread_l=12)),
    (1.75, pose(waist=-2, head=26, head_z=6, arm_spread=8, spread_l=6, legs=6, leg_l=-8, leg_spread=5, leg_spread_l=12)),     # olha pra baixo (5,82)
    (1.90, pose(waist=0, head=44, head_z=10, arms=-8, arm_spread=8, spread_l=6, legs=6, leg_l=-8, leg_spread=5, leg_spread_l=12)),
    (2.02, pose(waist=0, head=48, head_z=18, arm_py=2, arms=-8, arm_spread=8, spread_l=5, legs=8, leg_l=-10, leg_spread=5, leg_spread_l=11)),  # ombro começa (6,09)
    (2.10, crook(head=30, head_z=30, head_pos=(0.5, 0, 1), arm_py=3, waist=-4, waist_z=4, legs=11, leg_l=-13,
                 leg_spread=5, leg_spread_l=9)),                                   # CRACK do ombro, cabeça ainda baixa (6,17)
    (2.20, crook()),                                                             # TRAVA torto (6,27)
], "hold_on_last_frame")

# Pose torta (short 6,27-7,2 s; horror mod 1,71-2,64 s): quase parado - só uns
# trancos da cabeça, o ombro e a mão tremendo. Os ossos continuam estalando (servidor).
ANIMS["contort_hold"] = animation(2.0, [
    (0.00, crook()),
    (0.45, crook(head=-92, head_z=39, arm_spread=8)),
    (0.70, crook(head=-99, head_z=31, waist_z=12, arm_py=4.5)),
    (0.76, crook(head=-86, head_z=44, head_y=12, arm_spread=15, arms=-12)),      # tranco
    (0.90, crook(head=-97, head_z=36)),
    (1.35, crook(head=-94, head_z=33, waist_z=8, arm_spread=9, arm_py=3.5, root_px=0.2)),
    (1.62, crook(head=-98, head_z=38, head_y=6)),
    (1.68, crook(head=-104, head_z=27, head_y=-6, arm_spread=16, arms=-14, arm_py=5)),  # tranco
    (1.82, crook(head=-94, head_z=36)),
    (2.00, crook()),
], True)

# O GRITO: a cabeça joga pra trás num estalo, a boca escancara, os braços abrem
# e ele já se inclina pra frente disparando na corrida (0,5 s; grito no tick 4).
ANIMS["roar"] = animation(0.5, [
    (0.00, crook()),
    (0.10, pose(root_y=0, waist=-14, head=-40, arms=-50, arm_spread=60, legs=-4, leg_spread=10)),       # GRITO
    (0.25, pose(root_y=0, waist=6, head=-34, head_z=10, arms=-70, arm_spread=54, legs=-10, leg_l=8, leg_spread=8)),
    (0.38, pose(root_y=-1, waist=18, head=-18, head_z=16, arms=-10, arm_spread=50, arm_l=-30, legs=-30, leg_l=24,
                leg_spread=6)),                                                                          # inclina e arranca
    (0.50, sprint_pose(*SPRINT[0][1:])),
], "hold_on_last_frame")


# Língua: ele ergue os dois braços (garras pro alto), inclina o tronco pra
# frente e cospe a língua. Os tempos batem com tongue_*_ext (a língua sai em
# 0,25-0,4 s no chicote, 0,3-0,45 s na agarrada e 0,4-0,6 s na captura).
def tongue_p(waist, arms, spread, root_y=0.0, legs=0, leg_l=None):
    return pose(root_y=root_y, waist=waist, head=-waist * 0.5, arms=arms, arm_spread=spread,
                legs=legs, leg_l=leg_l, leg_spread=5)


ANIMS["tongue_whip"] = animation(0.7, [
    (0.00, pose()),
    (0.15, tongue_p(-6, -150, 24)),                                 # braços pro alto
    (0.25, tongue_p(18, -166, 30, root_y=-1)),                      # inclina
    (0.40, tongue_p(24, -170, 34, root_y=-1)),                      # LÍNGUA
    (0.55, tongue_p(20, -160, 30, root_y=-1)),
    (0.70, tongue_p(6, -60, 12)),
], "hold_on_last_frame")
ANIMS["tongue_grab"] = animation(0.8, [
    (0.00, pose()),
    (0.18, tongue_p(-6, -152, 24)),
    (0.30, tongue_p(18, -168, 32, root_y=-1)),
    (0.45, tongue_p(24, -172, 34, root_y=-1.5)),                    # LÍNGUA
    (0.65, tongue_p(14, -96, 14, root_y=-1.5, legs=-14, leg_l=14)),  # puxa
    (0.80, tongue_p(8, -70, 8)),
], "hold_on_last_frame")
ANIMS["tongue_capture"] = animation(1.4, [
    (0.00, pose()),
    (0.20, tongue_p(-8, -150, 24)),
    (0.40, tongue_p(20, -168, 32, root_y=-1.5, legs=-12, leg_l=12)),
    (0.60, tongue_p(26, -172, 36, root_y=-2, legs=-16, leg_l=16)),  # LÍNGUA
    (0.90, tongue_p(22, -150, 26, root_y=-2, legs=-16, leg_l=16)),
    (1.05, tongue_p(30, -100, 12, root_y=-2.5, legs=-18, leg_l=18)),  # puxa e abre os braços pra agarrar
    (1.25, tongue_p(34, -92, 6, root_y=-2.5, legs=-18, leg_l=18)),
    (1.40, tongue_p(24, -84, 8, root_y=-1.5)),
], "hold_on_last_frame")

# Devorando um mob (ref. "Eating a Zebra"): mergulha de cabeça na carcaça,
# morde, puxa a carne pra cima jogando sangue e sacode a cabeça como cachorro.
FEED_UP = dict(root_y=-2, waist=24, head=-10, arms=-60, arm_spread=10, legs=-10, leg_spread=12)
ANIMS["feed"] = animation(1.0, [
    (0.00, pose(**FEED_UP)),
    (0.22, pose(root_y=-4, waist=96, head=20, arms=-70, arm_spread=14, legs=-16, leg_spread=14)),     # mergulha
    (0.34, pose(root_y=-4.5, waist=100, head=34, arms=-66, arm_spread=14, legs=-16, leg_spread=14)),  # MORDE
    (0.52, pose(root_y=-1, waist=8, head=-34, arms=-50, arm_spread=16, legs=-6, leg_spread=12)),      # arranca pra cima
    (0.64, pose(root_y=-1, waist=12, head=-24, head_y=34, head_z=12, arms=-56, arm_spread=16, legs=-6, leg_spread=12)),
    (0.74, pose(root_y=-1, waist=12, head=-24, head_y=-34, head_z=-12, arms=-56, arm_spread=16, legs=-6, leg_spread=12)),
    (0.84, pose(root_y=-1, waist=14, head=-20, head_y=24, arms=-58, arm_spread=14, legs=-8, leg_spread=12)),
    (1.00, pose(**FEED_UP)),
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
            "run": [round(i * 0.25 / 15, 4) for i in range(15)],
            "run_frenzy": [round(i * 0.0375, 4) for i in range(8)],
            "frenzy_idle": [0.0],
            "crouch_idle": [0.0],
            "smash": [0.0, 0.14, 0.25, 0.4],
            "sky_drop": [0.3, 0.4, 1.1, 1.3, 1.7, 2.2],
            "arm_rip": [0.2, 0.42, 0.52, 1.1, 1.22, 2.0],
            "arm_eat": [0.5, 0.64, 4.0],
            "contort": [0.43, 0.47, 0.5, 0.53, 0.56, 0.6, 0.63, 0.69, 0.76, 0.82, 0.89, 0.95, 1.02, 1.08, 1.12, 1.15, 1.18, 1.21, 1.25, 1.31, 1.38, 1.51, 1.63, 1.78, 1.99, 2.06, 2.12, 2.2],
            "contort_hold": [0.0, 0.76, 1.68],
            "roar": [0.1, 0.25, 0.38],
            "tongue_capture": [0.2, 0.6, 1.05],
            "feed": [0.22, 0.52],
            "climb": [0.0, 0.25],
            "leap": [0.0, 0.15, 0.25, 0.4, 0.6, 0.78, 0.86, 1.0],
            "jump_pin": [0.2, 0.45, 0.68, 0.76, 1.0],
            "high_jump": [0.2, 0.3, 0.55, 0.75, 0.95, 1.1],
            "pin_hold": [0.0, 0.5],
        }
        dbg = {"format_version": "1.8.0", "animations": {}}
        for name, ts in times.items():
            for t in ts:
                dbg["animations"][f"dbg_{name}_{int(round(t * 1000)):04d}"] = static(sample(ANIMS[name], t))
        with open(DEBUG, "w") as f:
            json.dump(dbg, f, indent=1)
        print("poses de teste:", len(dbg["animations"]))


if __name__ == "__main__":
    main()
