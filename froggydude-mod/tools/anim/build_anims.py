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

# Corrida de quatro (vs AJTHEBOLD, 0:34,5-0:34,8, quadro a quadro, passando do
# lado da câmera): é um GALOPE DE CACHORRO, alto. As costas ficam retas na
# horizontal na altura da cintura, os braços e as pernas ESTICADOS até o chão,
# e o galope alterna: as mãos vão lá na frente enquanto as pernas empurram
# atrás, depois tudo se junta embaixo da barriga. A cara vai na frente, olhando
# pra frente e um pouco pra baixo. (Antes era baixo, de joelho dobrado - parecia
# engatinhando.) Com a cintura a 90 graus, braço a -90 e perna a 0, as mãos e os
# pés tocam o chão sozinhos (ombro e quadril ficam na mesma altura).
# 0,5 s por passada na velocidade 1; o jogo acelera ou freia junto com a
# velocidade real (caçando = rápido, apavorando = devagar).
def quad(waist, arm_r, arm_l, leg_r, leg_l, y, look=24, wz=0.0):
    return pose(root_y=y, waist=waist, head=-waist + look, arms=arm_r, arm_l=arm_l,
                legs=leg_r, leg_l=leg_l, arm_spread=4, leg_spread=4, waist_z=wz)


QUAD_STAND = dict(waist=90, arm_r=-90, arm_l=-88, leg_r=0, leg_l=4, y=-1.5)
ANIMS["run"] = animation(0.5, [
    (0.00, quad(92, -72, -78, -24, -16, -1.8, wz=2)),     # tudo junto embaixo da barriga
    (0.12, quad(88, -112, -100, 10, 18, -1.0)),           # as pernas empurram, as mãos vão pra frente
    (0.25, quad(82, -138, -128, 42, 50, 0.8, wz=-2)),     # esticado no ar
    (0.37, quad(90, -96, -106, 30, 22, -1.2)),            # as mãos batem no chão
    (0.50, quad(92, -72, -78, -24, -16, -1.8, wz=2)),
], True)


# Fase 2, ataque feroz (vs AJTHEBOLD, 7:44,6-7:45,4 desta cópia - 7:26 no
# YouTube): depois de se contorcer ele vem DE QUATRO, no mesmo galope de
# cachorro, só que muito mais rápido e mais esticado, levantando nuvens de
# poeira, com a cabeça meio tombada, e esmaga quem estiver no caminho.
# 0,5 s por passada na velocidade 1 (na fase 2 o jogo toca a 2,6x).
def quad_mad(waist, arm_r, arm_l, leg_r, leg_l, y, wz=0.0):
    return pose(root_y=y, waist=waist, head=-waist + 18, head_z=16, arms=arm_r, arm_l=arm_l,
                legs=leg_r, leg_l=leg_l, arm_spread=6, leg_spread=6, waist_z=wz)


ANIMS["run_frenzy"] = animation(0.5, [
    (0.00, quad_mad(94, -64, -72, -36, -26, -2.0, wz=4)),     # tudo junto embaixo da barriga
    (0.12, quad_mad(86, -118, -104, 14, 24, -0.6)),           # empurra
    (0.25, quad_mad(78, -150, -138, 54, 62, 1.2, wz=-4)),     # voando, bem esticado
    (0.37, quad_mad(90, -100, -112, 34, 24, -1.2)),           # as mãos batem no chão
    (0.50, quad_mad(94, -64, -72, -36, -26, -2.0, wz=4)),
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
], True)

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
    (0.58, pose(**{**PIN_POSE, "waist": 76, "head": -24, "head_z": 16, "head_y": 30, "arms": -74})),   # rasga sacudindo
    (0.66, pose(**{**PIN_POSE, "waist": 75, "head": -25, "head_z": -16, "head_y": -30, "arms": -76})),  # como cachorro
    (0.74, pose(**{**PIN_POSE, "waist": 74, "head": -28, "head_z": 12, "head_y": 22, "arms": -76})),
    (0.86, pose(**{**PIN_POSE, "waist": 64, "head": -42, "head_z": 4})),                 # volta te encarando
    (1.00, pose(**PIN_POSE)),
], True)

# Esmagamento (vs AJ, 0:57-1:01, visto de quem está no chão): em pé por cima da
# vítima, os dois braços juntos lá no alto, e desce tudo de uma vez - tronco,
# cabeça e punhos - na cara dela. Dois socos por segundo (era um, ficava lento);
# o impacto é aos 0,25 s de cada volta de 0,5 s.
SMASH_UP = dict(root_y=0, waist=-6, head=20, arms=-170, arm_spread=4, legs=0, leg_spread=6)
ANIMS["smash"] = animation(0.5, [
    (0.00, pose(**SMASH_UP)),
    (0.14, pose(**{**SMASH_UP, "root_y": 0.5, "waist": -14, "head": 10, "arms": -186})),       # arma o golpe
    (0.20, pose(**{**SMASH_UP, "waist": 30, "head": 26, "arms": -122})),                       # descendo
    (0.25, pose(root_y=-4, waist=78, head=40, arms=-56, arm_spread=2, legs=-22, leg_spread=8)),  # IMPACTO
    (0.31, pose(root_y=-4.5, waist=80, head=42, arms=-50, arm_spread=2, legs=-24, leg_spread=8)),
    (0.40, pose(**{**SMASH_UP, "root_y": -1.5, "waist": 35, "head": 26, "arms": -132})),       # sobe de novo
    (0.50, pose(**SMASH_UP)),
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

# Arrancar o braço (vs AJ, 2:21, e o jeito de comer da "Eating a Zebra"):
# agachado por cima, agarra o ombro esquerdo da vítima com as duas mãos, CRAVA
# os dentes no ombro e sacode a cabeça como cachorro rasgando (0,45-0,95 s),
# puxa com os dentes e as mãos e ARRANCA num tranco (1,1 s = tick 22 do
# servidor). Sai com o braço atravessado na boca, sacudindo, sangue voando.
RIP_BASE = dict(root_y=-8, waist=65, head=-30, arms=-80, legs=48, arm_spread=10, leg_spread=16)


def rip_p(**over):
    p = dict(RIP_BASE)
    p.update(over)
    return pose(**p)


def shake_p(side, **over):
    p = {"waist": 76, "head": -14, "head_y": 38 * side, "head_z": 14 * side, "waist_z": 5 * side,
         "arms": -98, "arm_spread": 2}
    p.update(over)
    return rip_p(**p)


def mouth_shake(side, amount=40):
    # em pé, braço atravessado na boca: sacode a cabeça pra rasgar
    return pose(root_y=-1, waist=6, head=-6, head_y=amount * side, head_z=12 * side, arms=-70, arm_l=-60,
                arm_spread=12, legs=4, leg_spread=8)


ANIMS["arm_rip"] = animation(2.0, [
    (0.00, rip_p()),
    (0.20, rip_p(waist=74, head=-22, arms=-96, arm_spread=3)),                              # agarra o ombro
    (0.32, rip_p(waist=70, head=-36, arms=-98, arm_spread=2)),                              # abre a boca
    (0.42, rip_p(waist=79, head=-10, arms=-98, arm_spread=2)),                              # CRAVA os dentes
    (0.52, shake_p(1)),
    (0.62, shake_p(-1)),
    (0.72, shake_p(1)),
    (0.82, shake_p(-1)),
    (0.92, shake_p(1, waist=76)),
    (1.02, rip_p(root_y=-6, waist=56, head=-44, arms=-124, arm_spread=1, waist_z=-6)),      # puxa com tudo
    (1.10, pose(root_y=-2, waist=-14, head=-36, arms=-150, arm_l=-60, arm_spread=8, legs=20, leg_spread=12)),  # TRANCO
    (1.22, mouth_shake(1)),
    (1.34, mouth_shake(-1)),
    (1.46, mouth_shake(1, 34)),
    (1.58, mouth_shake(-1, 34)),
    (1.75, pose(root_y=0, waist=8, head=-12, head_y=10, arms=-110, arm_l=-104, arm_ry=-20, arm_spread=-6, leg_spread=6)),
    (2.00, pose(root_y=0, waist=10, head=-4, arms=-124, arm_l=-118, arm_ry=-26, arm_spread=-8, leg_spread=4)),  # segura pra comer
], "hold_on_last_frame")


# Comer o braço: segura com as duas mãos na boca e RASGA - morde, a cabeça dá
# um tranco pra trás arrancando o pedaço e sacode de um lado pro outro.
# Mordidas aos 0,5 s, 1,25 s, 2 s, 2,75 s e 3,5 s (= ticks 10, 25, 40, 55 e 70
# do servidor, onde o sangue espirra). Engole aos 4 s, olhando pra cima.
def chew(bite, side, yank=False):
    if yank:   # arrancando o pedaço: cabeça pra trás e torta
        return pose(root_y=0, waist=4, head=-22, head_y=24 * side, head_z=18 * side,
                    arms=-120, arm_l=-112, arm_ry=-22, arm_spread=-6, legs=0, leg_spread=4)
    return pose(root_y=0, waist=18 if bite else 10, head=20 if bite else -4, head_z=10 * side if bite else 0,
                head_y=-14 * side if bite else 0,
                arms=-132 if bite else -124, arm_l=-126 if bite else -118, arm_ry=-26, arm_spread=-8,
                legs=0, leg_spread=4)


eat_keys = [(0.0, pose(root_y=0, waist=10, head=-4, arms=-124, arm_l=-118, arm_ry=-26, arm_spread=-8, leg_spread=4)),
            (0.30, chew(False, 0))]
for k in range(5):
    t = 0.5 + k * 0.75
    side = 1 if k % 2 == 0 else -1
    eat_keys.append((round(t, 3), chew(True, side)))
    eat_keys.append((round(t + 0.14, 3), chew(False, side, yank=True)))
    eat_keys.append((round(t + 0.26, 3), chew(False, -side, yank=True)))
    eat_keys.append((round(t + 0.40, 3), chew(False, 0)))
eat_keys += [
    (4.00, pose(root_y=0, waist=-8, head=-28, arms=-30, arm_l=-20, arm_spread=2, leg_spread=4)),     # engole
    (4.25, pose(root_y=0, waist=4, head=10, arms=-38, arm_l=-8, arm_spread=-16, leg_spread=4)),      # mão na barriga
    (4.50, pose(root_y=0, waist=4, head=12, arms=-30, arm_l=-6, arm_spread=-18, leg_spread=4)),
]
ANIMS["arm_eat"] = animation(4.5, eat_keys, "hold_on_last_frame")

# Contorção da fase 2 (vs AJTHEBOLD, 7:40,0-7:42,9 desta cópia - 7:26 no
# YouTube, quadro a quadro): em menos de um segundo o tronco CHICOTEIA - joga
# os braços pro alto, dobra de lado até quase deitar, a cabeça vira de ponta-
# cabeça, dobra pra frente até o chão, volta pelo outro lado e levanta com os
# braços se debatendo. Termina encurvado com a cabeça tombada uns 45 graus.
# 1,0 s (20 ticks); depois "contort_hold" (em loop, 2 a 4 s, quem decide é o
# servidor) e por fim "roar": a cabeça levanta num estalo e ele grita.
HUNCH = dict(root_y=-0.5, waist=20, waist_z=6, head=18, head_z=45, head_y=10, arms=-8, arm_l=-14,
             arm_spread=4, spread_l=-2, legs=0, leg_spread=8)


def hunch(**over):
    p = dict(HUNCH)
    p.update(over)
    return pose(**p)


ANIMS["contort"] = animation(1.0, [
    (0.00, pose()),
    (0.07, pose(waist=10, waist_z=35, waist_y=25, head_z=25, arms=-150, arm_l=-110, arm_spread=20)),   # CRACK: braços pro alto
    (0.13, pose(waist=20, waist_z=70, waist_y=30, head=20, head_z=40, arms=-60, arm_l=-170)),          # dobra de lado
    (0.20, pose(waist=70, waist_z=40, waist_y=-20, head=30, head_z=95, arms=-110, arm_l=-60)),         # cabeça de ponta-cabeça
    (0.27, pose(waist=85, waist_z=-20, waist_y=-40, head=20, head_z=150, arms=-90, arm_l=-40)),
    (0.33, pose(root_y=-1, waist=95, waist_z=-50, head=10, head_z=100, arms=-95, arm_l=-80)),          # CRACK: dobrado até o chão
    (0.40, pose(root_y=-0.5, waist=70, waist_z=-65, head=5, head_z=60, arms=-30, arm_l=-150)),         # pro outro lado
    (0.47, pose(waist=40, waist_z=-30, waist_y=20, head=-10, head_z=30, arms=-120, arm_l=-20, arm_spread=30)),
    (0.55, pose(waist=10, waist_z=10, head_z=-20, arms=-95, arm_l=-40, arm_spread=10)),               # levanta se debatendo
    (0.65, pose(waist=5, waist_y=-15, head_z=25, arms=-100, arm_l=-110, arm_spread=15)),
    (0.75, pose(waist=12, waist_z=8, head=10, head_z=-15, arms=-40, arm_l=-70)),                       # CRACK
    (0.87, pose(waist=16, head=12, head_z=35, arms=-10, arm_l=-25, arm_spread=6)),
    (1.00, hunch()),
], "hold_on_last_frame")

# Encurvado com a cabeça tombada (vs AJ, 7:40,9-7:42,9): a cabeça balança
# devagar, de vez em quando dá um tranco pro outro lado, o corpo oscila e os
# braços moles têm espasmos.
ANIMS["contort_hold"] = animation(2.0, [
    (0.00, hunch()),
    (0.40, hunch(head_z=32, head=22, waist_z=3, arms=-12)),
    (0.70, hunch(head_z=52, head=14, head_y=12)),
    (0.78, hunch(head_z=-18, head=26, head_y=-10, arm_l=-40)),                    # tranco
    (0.90, hunch(head_z=40, head=18)),
    (1.30, hunch(head_z=48, waist_z=-4, head_y=-6, root_px=0.3)),
    (1.60, hunch(head_z=36, head=24)),
    (1.66, hunch(head_z=70, head=5, arms=-30)),                                  # tranco
    (1.80, hunch(head_z=44)),
    (2.00, hunch()),
], True)

# A cabeça levanta num estalo e ele GRITA (o ataque feroz começa aqui): o
# tronco joga pra trás, a cabeça vai pro céu e os braços abrem; aí ele cai
# de quatro e dispara no galope. 0,9 s = 18 ticks; o grito sai no tick 4.
ANIMS["roar"] = animation(0.9, [
    (0.00, hunch()),
    (0.12, pose(root_y=-1, waist=24, head=-24, arms=-40, arm_spread=30, legs=-4, leg_spread=12)),
    (0.24, pose(root_y=0, waist=-20, head=-44, arms=-34, arm_spread=74, legs=0, leg_spread=12)),          # GRITO
    (0.40, pose(root_y=0, waist=-16, head=-36, head_z=8, arms=-46, arm_spread=80, legs=0, leg_spread=12)),
    (0.55, pose(root_y=0, waist=-18, head=-40, head_z=-8, arms=-38, arm_spread=76, legs=0, leg_spread=12)),
    (0.72, pose(root_y=-1, waist=60, head=-42, arms=-80, arm_l=-70, arm_spread=12, legs=-10, leg_spread=6)),  # cai de quatro
    (0.90, quad_mad(94, -64, -72, -36, -26, -2.0, wz=4)),
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
            "run": [0.0, 0.12, 0.25, 0.37],
            "run_frenzy": [0.0, 0.12, 0.25, 0.37],
            "crouch_idle": [0.0],
            "smash": [0.0, 0.14, 0.25, 0.4],
            "sky_drop": [0.3, 0.4, 1.1, 1.3, 1.7, 2.2],
            "arm_rip": [0.2, 0.42, 0.52, 1.1, 1.22, 2.0],
            "arm_eat": [0.5, 0.64, 4.0],
            "contort": [0.07, 0.13, 0.2, 0.33, 0.4, 0.55, 1.0],
            "contort_hold": [0.0, 0.78, 1.66],
            "roar": [0.24, 0.72, 0.9],
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
                dbg["animations"][f"dbg_{name}_{int(round(t * 100)):03d}"] = static(sample(ANIMS[name], t))
        with open(DEBUG, "w") as f:
            json.dump(dbg, f, indent=1)
        print("poses de teste:", len(dbg["animations"]))


if __name__ == "__main__":
    main()
