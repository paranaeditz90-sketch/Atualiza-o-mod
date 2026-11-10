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

import numpy as np

import rig

HERE = os.path.dirname(os.path.abspath(__file__))
ANIM_DIR = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "froggydude", "animations")
MAIN = os.path.join(ANIM_DIR, "froggydude.animation.json")
DEBUG = os.path.join(ANIM_DIR, "froggydude_debug.animation.json")

BONES = ("root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_shin", "left_shin")


def pose(root_y=0.0, root_x=0.0, waist=0.0, head=0.0, arms=0.0, legs=0.0,
         arm_l=None, leg_l=None, head_z=0.0, waist_z=0.0, arm_spread=0.0, leg_spread=0.0,
         root_px=0.0, head_y=0.0, waist_y=0.0, arm_ry=0.0, arm_ly=None, spread_l=None,
         arm_py=0.0, arm_lpy=0.0, leg_spread_l=None, head_pos=None, knee=None, knee_l=None,
         leg_pos=None):
    """Uma pose completa. arms/legs valem pros dois lados, a não ser que
    arm_l/leg_l digam outra coisa pro esquerdo. spread abre os membros pros lados
    (spread_l pro braço esquerdo, se for diferente). arm_ry gira o braço no
    próprio eixo (Y). arm_py/arm_lpy sobem o OMBRO (px; o braço inteiro sobe,
    como um ombro deslocado - a pose torta da fase 2). knee/knee_l dobram o
    joelho (X+ joga a canela pra trás); sem eles a canela fica reta e o
    osso nem entra na animação. leg_pos (x, y, z) ANDA as duas pernas, sem girar
    (px, eixos do json: x+ = pro lado esquerdo dele, espelhado na esquerda;
    y+ = sobe, entra no corpo; z+ = pra trás) - a agachada "||" do pulo do céu."""
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
    if knee is not None or knee_l is not None:
        k = knee or 0.0
        p["right_shin"] = {"rotation": [k, 0, 0]}
        p["left_shin"] = {"rotation": [k if knee_l is None else knee_l, 0, 0]}
    if leg_pos:
        x, y, z = leg_pos
        p["right_leg"]["position"] = [x, y, z]
        p["left_leg"]["position"] = [-x, y, z]
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


# Correndo de 4 na parede (v0.7.14) - Grox (RABIES) 3:29,5-3:31, quadro a quadro: a
# câmera fica no alto do pilar olhando pra baixo e ele vem galopando pela parede acima
# como se ela fosse o chão - o MESMO galope (empina, estica, mãos, chuta, reúne), com as
# patas na parede, as costas pra fora e a cabeça pra cima; na empinada o tronco sai da
# parede. Antes ele subia em pé, feito gente na escada (o LM: "animação bugada").
# É o galope girado 90 graus (root X -90: a frente vira cima e o "chão" vira a parede).
# O root se mexe no espaço do bicho (o GeckoLib move antes de girar): a altura do galope
# (quem está no ar sobe) vira distância da parede, e o plano das patas fica na parede,
# a meia caixa (0,3 bloco) na frente dele. Z negativo = pra frente (a cara do modelo).
WALL_PX = 0.3 * 16 / 1.05   # meia largura da caixa, em px do modelo (escala 1,05)
WALL_UP = 4.0               # sobe um pouco: as patas de trás não passam do pé da caixa


def wall_gallop(table, length):
    keys = []
    for f, *row in table:
        p = gallop_pose(*row)
        lift_px = p["root"]["position"][1]
        p["root"]["rotation"] = [p["root"]["rotation"][0] - 90.0, 0, 0]
        p["root"]["position"] = [0, WALL_UP, -round(WALL_PX - lift_px, 2)]
        keys.append((round(f * length, 4), p))
    keys.append((length, keys[0][1]))
    return animation(length, keys, True, ground=False)


ANIMS["climb"] = wall_gallop(GALLOP, 0.25)

# Parado de 4 (no meio da caçada; no BecomeLM, parado no galope). v0.7.20 (LM 03/11: "repara na perna, tá tipo /"
# -> "joelho dobrado, como um sapo"): agachado de SAPO - a coxa pra frente e o joelho dobrado (a canela volta), o
# quadril baixo, o tronco a ~60° com a cabeça olhando pra frente e os braços quase em pé com as MÃOS NO CHÃO na frente,
# respirando. Antes (v0.7.14) era a pose de onde o galope sai, com as pernas RETAS inclinadas embaixo da barriga (o "/"
# que o LM marcou) e as mãos ~3 px no ar. E antes disso a "mesa", que o LM barrou no comer.


def frog_idle(w, leg=-82, knee=150, look=10):
    """Sapo parado: acha o braço (quase em pé, 0-40° pra frente no mundo) que põe a mão no chão junto com o pé."""
    best = None
    for phi in range(0, 41):
        a = -(w + phi)
        p = pose(waist=w, head=-w + look, arms=a, arm_l=a, legs=leg, leg_l=leg + 3, knee=knee, knee_l=knee - 3,
                 arm_spread=4, leg_spread=8)
        low = rig.lowest(rig.frames(p))
        feet = min(low["right_leg"], low["left_leg"])
        hands = min(low["right_arm"], low["left_arm"])
        if best is None or abs(hands - feet) < best[0]:
            best = (abs(hands - feet), p, min(feet, hands))
    _, p, lowest = best
    p["root"]["position"][1] = round(p["root"]["position"][1] + 0.5 - lowest, 2)
    return p


ANIMS["crouch_idle"] = animation(1.2, [
    (0.00, frog_idle(60)),
    (0.60, frog_idle(63, look=12)),
    (1.20, frog_idle(60)),
], True)

# Bote baixo que derruba: salta rente ao chão com os braços pra frente pra
# agarrar e cai montado na vítima. Impulso no tick 4, impacto no ~15.
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

# Comendo em "flexão" (v0.7.17; estudo de 02/11, viking 08-flexao-estudo rodada 6). Medido quadro a quadro nas
# duas referências que o LM aprovou (Taste Testing perto da lava, 30 qps; o monte do golem no Grox, 60 qps) e
# conferido por cima do vídeo com a câmera encaixada ("TA PERFEITO", só os braços retos "||"):
#  - a PRANCHA inteira (pernas + tronco) gira nos PÉS: em cima ~75° de bruços, na mordida +9,5° (o ombro desce
#    quase o mesmo que a cabeça); o pé fica, sem levantar a bunda (o "\" mais deitado);
#  - os braços são PILARES parados no mundo, retos "||" (em pé, sem abrir): a mão fica plantada e quem desliza é
#    o tronco - embaixo o topo do braço sobra acima das costas e a cabeça desce entre eles ("|-|");
#  - a cabeça vai junto com o ombro, fica quase de pé no mundo e mergulha um tico;
#  - ritmo: em cima 13% do ciclo, desce 29% (rápido), embaixo 17%, sobe 41% (devagar).
# Um ciclo = uma mordida = PUSHUP_TICKS (7 ticks, 0,35 s; lava 0,364, warden 0,33). O Java acelera pela
# fome/raiva (feastRate 0,8-1,4: ~0,44 a 0,25 s; o golem foi 0,246) e a mordida do servidor anda na mesma fase.
# SEM lift() no ciclo: era ele que subia o corpo quando a mão descia junto e apagava a descida da prancha.
FLEX = dict(root=74.66, plank=9.46, waist=-13.43, head_top=-59.21, head_bot=-58.17, dive_f=-1.13, dive_y=-0.54,
            hold_top=0.13, down=0.29, hold_bot=0.17, lift=2.26)
PUSHUP_LEN = 0.35  # = FroggydudeEntity.PUSHUP_TICKS / 20


def _ease(a, b, k):
    k = max(0.0, min(1.0, k))
    k = k * k * (3 - 2 * k)
    return a + (b - a) * k


def flexao_k(ph):
    """Fase 0..1 do ciclo -> 0 (em cima) .. 1 (embaixo, mordendo)."""
    a, d, b = FLEX["hold_top"], FLEX["down"], FLEX["hold_bot"]
    if ph < a:
        return 0.0
    if ph < a + d:
        return _ease(0, 1, (ph - a) / d)
    if ph < a + d + b:
        return 1.0
    return _ease(1, 0, (ph - a - d - b) / (1 - a - d - b))


def flexao_pose(k):
    """Pose da flexão em k (0 = em cima, 1 = embaixo), com os braços parados no mundo."""
    def base(kk):
        rx = FLEX["root"] + FLEX["plank"] * kk
        return dict(root_y=FLEX["lift"], root_x=rx, waist=FLEX["waist"], legs=0, leg_spread=3,
                    head=_ease(FLEX["head_top"], FLEX["head_bot"], kk),
                    arms=-(rx + FLEX["waist"]), arm_spread=0)          # braço reto: em pé no mundo, sem abrir
    top = rig.frames(pose(**base(0.0)))
    p = pose(**base(k))
    Rw, ow = rig.frames(p)["body"]
    for arm, sx in (("right_arm", 5.0), ("left_arm", -5.0)):
        loc = Rw.T @ (top[arm][1] - ow) - np.array([0, 10.0, sx])     # ombro no lugar da pose de cima (f, y, s)
        p[arm]["position"] = [round(-loc[2], 3), round(loc[1], 3), round(-loc[0], 3)]   # json: x = −lado, z = −frente
    if k > 0:
        p["head"]["position"] = [0.0, round(FLEX["dive_y"] * k, 3), round(-FLEX["dive_f"] * k, 3)]
    return p


def flexao_loop(n=14):
    """Um ciclo (uma mordida) em n quadros + o fecho do loop."""
    keys = [(round(PUSHUP_LEN * i / n, 4), flexao_pose(flexao_k(i / n))) for i in range(n)]
    return keys + [(PUSHUP_LEN, flexao_pose(0.0))]


ANIMS["pin_hold"] = animation(PUSHUP_LEN, flexao_loop(), True, ground=False)

# Esmagamento (vs AJ, 0:57-1:01, visto de quem está no chão): na frente da
# vítima, os dois braços juntos lá no alto, e desce tudo de uma vez - tronco
# e punhos - na cara dela. Dois socos por segundo; impacto aos 0,25 s de cada
# volta de 0,5 s.
# (v0.3.4) O impacto antigo (tronco a 78 graus, cabeça olhando pra baixo, vítima a
# 1,15 bloco) enfiava a CABEÇA dele no olho da vítima (a tela verde/vermelha a cada
# soco) e os punhos batiam no chão 0,8 bloco antes dela. Agora (check_poses.py,
# vítima a 1,5 bloco): no impacto os punhos param a ~0,45 bloco da câmera, a cabeça
# fica a meio bloco, levantada, olhando pra ela, e os pés não afundam.
# (v0.5.2, vs AJTHEBOLD 2:06, print do LM) DE JOELHOS: os dois joelhos no chão,
# coxas em pé e canelas deitadas pra trás; o tronco quase reto ergue os punhos
# acima da cabeça e desce inclinando só uns 30 graus, braços esticados pra
# frente e pra baixo. A cabeça fica alta (longe da câmera), então a vítima chega
# mais perto: 1,1 bloco (SMASH_DISTANCE), e os punhos param a ~0,4 bloco do olho dela.
# (v0.7.21, vídeo do LM de 03/11 visto pela vítima, 8,25 s em cima e 10,1 s na pancada; LM: "as mãos deveriam bater
# no chão, que no caso é o jogador", "os braços ainda tão levemente abertos pra fora", "usa grade pra medir")
# Encaixado no vídeo pelos pontos e tamanhos medidos na grade (mãos, punhos, cabeça, ombros) mais a silhueta, com a
# câmera da vítima parada (olho a 0,4, 31° pra cima, ele a 1,5 bloco = SMASH_DISTANCE):
# - EM CIMA: os braços pra cima e um pouco pra frente, um de cada lado da cabeça, as mãos no alto da tela;
# - PANCADA: tronco ~50°, a cabeça dobrada (o topo dela enche o alto da tela) e os braços FECHADOS descendo reto: os
#   punhos param a ~0,4 bloco do olho, nos cantos de baixo da tela (só pela perspectiva, sem abrir o braço).
# O ritmo é o mesmo (0,5 s, impacto aos 0,25 s); a nossa cabeça é maior que a do vídeo, então em cima ela chega
# quase na altura das mãos.
KNEEL = dict(root_y=-4, legs=0, knee=90, leg_spread=8, arm_spread=4)
SMASH_UP = {**KNEEL, "waist": 14, "head": -15, "arms": -154, "arm_spread": 9, "arm_ry": 10}
SMASH_HIT = {**KNEEL, "waist": 50, "head": 49, "arms": -79, "arm_spread": 3, "arm_ry": 4}
ANIMS["smash"] = animation(0.5, [
    (0.00, pose(**SMASH_UP)),
    (0.14, pose(**{**SMASH_UP, "waist": 8, "head": -20, "arms": -170})),       # arma o golpe
    (0.20, pose(**{**KNEEL, "waist": 32, "head": 20, "arms": -115, "arm_spread": 6, "arm_ry": 7})),   # descendo
    (0.25, pose(**SMASH_HIT)),                                                 # IMPACTO
    (0.31, pose(**{**SMASH_HIT, "waist": 52, "head": 50, "arms": -76})),
    (0.40, pose(**{**KNEEL, "waist": 30, "head": 15, "arms": -120, "arm_spread": 6, "arm_ry": 7})),   # sobe de novo
    (0.50, pose(**SMASH_UP)),
], True)

# Nado (v0.7.22, LM 03/11; How many days 1:52,95-1:53,57 medido a 60 qps com grade; "as pernas vão pra cima e pra
# baixo ao mesmo tempo, como se fosse um dardo", "ele impulsiona mais", "as mãos ficam retas na altura da cabeça"):
# pernas JUNTAS e retas batendo (golfinho) e o corpo gangorrando no quadril junto (alfa: cintura e pernas giram juntas;
# beta: as pernas batem a mais). Desliza reto de cabeça pra cima, as pernas sobem rápido (0,08 s) enquanto ele deita,
# seguram e descem de uma vez (o impulso). Braços esticados à frente, na altura da cabeça. Ciclo 0,62 s.
# Pose do corpo EM PÉ: o deitar e a inclinação pra onde ele nada vêm do FroggydudeRenderer.
def _swim(alfa, beta, knee=0):
    return pose(waist=alfa, head=-65 - 0.8 * alfa, arms=-172, arm_spread=8, legs=alfa + beta, knee=knee, leg_spread=2)


ANIMS["swim"] = animation(0.62, [
    (0.00, _swim(-20, 6)),            # fim do deslize, as pernas começam a subir (1:52,95)
    (0.08, _swim(6, 40, 14)),         # pernas sobem rápido, ele deitando (1:53,03)
    (0.14, _swim(18, 24, 12)),        # deitado, pernas em cima (1:53,10)
    (0.26, _swim(16, 20, 10)),        # segura (1:53,21)
    (0.36, _swim(-18, -2)),           # a batida pra baixo: a cabeça sobe, o impulso (1:53,31)
    (0.48, _swim(-22, 0)),            # deslizando reto (1:53,43)
    (0.62, _swim(-20, 6)),
], True)

# Pulo altíssimo (v0.7.19, LM 02/11): réplica do VS AJTHEBOLD (FroggyDude VS Every Opponent 29:48,20-48,67 de frente e
# 29:51,40-51,74 visto de baixo) medida por SOBREPOSIÇÃO: o rig desenhado por cima do quadro do original e os ângulos
# ajustados até a silhueta cobrir a dele (ameaça 77-79%, queda 77-89%, agachada e decolagem 51-73%).
#   ameaça (0,05-0,26 s): ergue os braços PELOS LADOS até um V em cima da cabeça, cabeça pra trás no grito;
#   agachada (0,29-0,39 s): os braços caem, o direito vai pra frente antes do esquerdo; o corpo desce com as pernas
#     RETAS e em pé ("||", não "\"), um pouco pra frente (sobem pra dentro do corpo, o pé fica no chão); em 0,357 os
#     dois braços retos na altura do ombro, abertos ~54°;
#   decola no 0,40 (impulso do servidor no tick 8) e voa de braço de ZUMBI levemente pra cima, aberto em "\ /"
#     (giro Y: com o braço pra frente o Z só torceria ele no próprio eixo); caindo, a perna direita vai pra frente (a
#     esquerda fica reta) e a cabeça balança de leve pros lados. Vai até 3,2 s (o arco muda com a distância) e segura.
# O BecomeLM corta daqui a partir de 0,38 s (sky_air, becomelm-mod/tools/make_anims.py).
SKY_V = 150          # o V da ameaça
SKY_GRITO = -25      # cabeça pra trás no grito
SKY_ZUMBI = -113     # braço de zumbi na queda: 23° acima do horizontal
SKY_ABRE = 19        # abertura do V do zumbi (giro Y)
SKY_PERNA = -50      # perna direita pra frente na queda; a esquerda fica reta (+4)
SKY_BALANCO = 10     # a cabeça balança de leve pros lados só caindo
SKY_CICLO = 0.6      # uma ida e volta da cabeça (s)
SKY_FIM = 3.2


def _sky_zumbi(arms=SKY_ZUMBI, ry=SKY_ABRE, perna=0.0, perna_l=0.0, **kw):
    return pose(arms=arms, arm_ry=ry, legs=perna, leg_l=perna_l, leg_spread=3, **kw)


def _sky_agacha(fwd, desce, **kw):
    """Pernas retas e em pé, fwd px pra frente; o corpo desce `desce` px e as pernas sobem o mesmo tanto."""
    return pose(root_y=-desce, leg_pos=(0, desce, -fwd), **kw)


_SKY = [
    (0.000, pose(arm_spread=8, leg_spread=3)),
    (0.050, pose(arm_spread=33, leg_spread=3, head=-9)),
    (0.082, pose(arm_spread=76, leg_spread=3, head=-17, waist=6)),
    (0.124, pose(arm_spread=129, leg_spread=3, head=SKY_GRITO, waist=8)),
    (0.167, pose(arm_spread=145, leg_spread=3, head=SKY_GRITO, waist=8)),
    (0.209, pose(arm_spread=SKY_V, leg_spread=3, head=SKY_GRITO, waist=8)),
    (0.262, pose(arm_spread=SKY_V - 2, leg_spread=3, head=-20, waist=8)),
    (0.294, _sky_agacha(1.5, 1.5, arm_spread=80, leg_spread=3, head=-5)),                          # os braços caem
    (0.315, _sky_agacha(3.0, 2.2, arm_spread=65, spread_l=40, arms=-5, arm_l=-20, leg_spread=3, head=-10, waist=8)),
    (0.336, _sky_agacha(3.0, 4.5, arm_spread=65, spread_l=42, arms=-40, arm_l=-15, arm_ry=15, arm_ly=0,
                        leg_spread=4, head=-10, waist=16)),
    (0.347, _sky_agacha(3.0, 4.5, arm_spread=70, spread_l=45, arms=-75, arm_l=-15, arm_ry=25, arm_ly=0,
                        leg_spread=4, head=-9, waist=19)),
    (0.357, _sky_agacha(3.0, 5.0, arms=-142, arm_ry=54, leg_spread=4, head=25, waist=44)),        # braços no ombro
    (0.368, _sky_agacha(3.0, 5.0, arms=-142, arm_ry=54, leg_spread=4, head=25, waist=44)),
    (0.379, _sky_agacha(3.0, 5.0, arm_spread=8, arms=-118, arm_ry=39, leg_spread=3, head=25, waist=21)),
    (0.389, _sky_agacha(1.5, 2.5, arm_spread=8, arms=-118, arm_ry=39, leg_spread=3, head=20, waist=21)),
    (0.400, _sky_zumbi(arms=-105, ry=37, waist=15, head=12)),                                      # DECOLA
    (0.700, _sky_zumbi(arms=-111, ry=24, waist=4, head=-2)),
    (1.100, _sky_zumbi(arms=-112, ry=20, waist=4, head=0, perna=-30, perna_l=2)),
    (1.300, _sky_zumbi(perna=SKY_PERNA, perna_l=4, waist=6, head=0)),                               # caindo
]
_t, _k = 1.30 + SKY_CICLO / 4, 0
while _t < SKY_FIM - 1e-6:   # a cabeça balança de leve pros lados até o fim
    _SKY.append((round(_t, 3), _sky_zumbi(perna=SKY_PERNA, perna_l=4, waist=6, head=0,
                                          head_z=SKY_BALANCO if _k % 2 == 0 else -SKY_BALANCO)))
    _t += SKY_CICLO / 2
    _k += 1
_SKY.append((SKY_FIM, _sky_zumbi(perna=SKY_PERNA, perna_l=4, waist=6, head=0)))
ANIMS["sky_drop"] = animation(SKY_FIM, _SKY, "hold_on_last_frame")

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

# Contorção da fase 2 (v0.4.2) - ROTOSCOPIA do vídeo do LM (real60, 89,28-91,75 s, 60 fps).
# Cada pose de contort_fit.json foi ajustada contra o quadro do vídeo comparando a COR de
# cada pixel (cara, coroa, lágrima, mãos pesam 4x o terno), com o esqueleto (rig.py)
# conferido contra prints do jogo de verdade tirados com a mesma câmera. Uma pose a cada
# 3 quadros do vídeo (20 por segundo, uma por tick; o jogo interpola entre elas).
# Antes (v0.4.1) o ajuste era por "classe de cor", e pra ele a cara e o topo da cabeça
# eram o mesmo verde: a cabeça saía virada pra trás, mostrando o cocuruto, quando no vídeo
# ela afunda nos ombros e tomba de lado com a cara aparecendo.
# Tempo da animação = tempo do vídeo - 88,82 s. De 0 a 0,46 s o vídeo corta pra outra cena:
# aí ele só sai da pose em pé até a primeira pose ajustada. 2,95 s (59 ticks); depois
# "contort_hold" (a última pose, travado torto, em loop) e "roar" (o grito).
_FIT = json.load(open(os.path.join(HERE, "contort_fit.json"), encoding="utf-8"))
CROOK = dict(_FIT["frames"][-1]["pose"])


def crook(**over):
    p = dict(CROOK)
    p.update(over)
    return planted(pose(**p))


def planted(p):
    """Perna aberta encurta: baixa o corpo até o pé mais baixo encostar no chão (sem
    isso ele flutua 1-2 px com as pernas abertas)."""
    low = rig.lowest(rig.frames(p))
    m = min(low["right_leg"], low["left_leg"])
    if m <= 0.05:
        return p
    p = copy.deepcopy(p)
    p["root"]["position"][1] = round(p["root"]["position"][1] - m, 2)
    return p


def contort_p(**kw):
    return planted(pose(**kw))


ANIMS["contort"] = animation(_FIT["length"], [(0.00, pose())] + [
    (f["t"], contort_p(**f["pose"])) for f in _FIT["frames"]
], "hold_on_last_frame")

# Pose torta (short 6,27-7,2 s; horror mod 1,71-2,64 s): quase parado - só uns
# trancos da cabeça, o ombro e a mão tremendo. Os ossos continuam estalando (servidor).
ANIMS["contort_hold"] = animation(2.0, [
    (0.00, crook()),
    (0.45, crook(head=CROOK["head"] + 3, head_z=CROOK["head_z"] + 3, arm_spread=CROOK["arm_spread"] - 2)),
    (0.70, crook(head=CROOK["head"] - 4, head_z=CROOK["head_z"] - 5, waist_z=CROOK["waist_z"] + 6,
                 arm_py=CROOK.get("arm_py", 0) + 0.5)),
    (0.76, crook(head=CROOK["head"] + 9, head_z=CROOK["head_z"] + 8, head_y=CROOK["head_y"] + 10,
                 arm_spread=CROOK["arm_spread"] + 6, arms=CROOK["arms"] - 6)),                    # tranco
    (0.90, crook(head=CROOK["head"] - 2, head_z=CROOK["head_z"])),
    (1.35, crook(head=CROOK["head"] + 1, head_z=CROOK["head_z"] - 3, waist_z=CROOK["waist_z"] + 3,
                 arm_spread=CROOK["arm_spread"] + 1, arm_py=CROOK.get("arm_py", 0) - 0.5,
                 root_px=CROOK.get("root_px", 0) + 0.2)),
    (1.62, crook(head=CROOK["head"] - 3, head_z=CROOK["head_z"] + 2, head_y=CROOK["head_y"] + 4)),
    (1.68, crook(head=CROOK["head"] - 9, head_z=CROOK["head_z"] - 9, head_y=CROOK["head_y"] - 8,
                 arm_spread=CROOK["arm_spread"] + 8, arms=CROOK["arms"] - 8,
                 arm_py=CROOK.get("arm_py", 0) + 1)),                                              # tranco
    (1.82, crook(head=CROOK["head"] + 1, head_z=CROOK["head_z"])),
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


# Vitória-régia (fim do vídeo do LM, 95,6-98,9 s): de folha em folha num salto de
# sapo rasante, um a cada 0,3 s (FroggyPadHopGoal: 5 ticks no ar + 1 em pé). O
# corpo inteiro deita no ar (pivô nos pés: root), braços esticados pra frente e
# pernas pra trás, como um mergulho raspando a água; as pernas descem pra pousar,
# ele bate na folha agachado, fica em pé um instante e já se joga de novo.
# t = 0 é o 1º tick no ar (o salto sai no fim do tick em pé, 0,25).
ANIMS["pad_hop"] = animation(0.3, [
    (0.00, pose(root_x=50, waist=12, head=-45, arms=-150, arm_spread=14, legs=24, leg_l=18, leg_spread=4)),   # saindo
    (0.05, pose(root_x=76, waist=6, head=-62, arms=-168, arm_spread=12, legs=8, leg_l=4, leg_spread=6)),       # deitado no ar
    (0.10, pose(root_x=78, waist=4, head=-64, arms=-164, arm_spread=16, legs=6, leg_l=10, leg_spread=6)),
    (0.15, pose(root_x=40, waist=8, head=-34, arms=-110, arm_spread=20, legs=-34, leg_l=-28, leg_spread=8)),   # pernas descendo
    (0.20, pose(root_y=-1.5, root_x=10, waist=30, head=-10, arms=-40, arm_spread=16, legs=-22, leg_l=20,
                leg_spread=6)),                                                                                # bate na folha agachado
    (0.25, pose(root_x=18, waist=8, head=-14, arms=-30, arm_spread=10, legs=26, leg_l=20, leg_spread=4)),      # em pé, já empurrando
    (0.30, pose(root_x=50, waist=12, head=-45, arms=-150, arm_spread=14, legs=24, leg_l=18, leg_spread=4)),
], True, ground=False)


# Língua: ele ergue os dois braços (garras pro alto), inclina o tronco pra
# frente e cospe a língua. Os tempos batem com tongue_*_ext (a língua sai em
# 0,25-0,4 s no chicote, 0,3-0,45 s na agarrada e 0,4-0,6 s na captura).
def tongue_p(waist, arms, spread, root_y=0.0, legs=0, leg_l=None):
    return pose(root_y=root_y, waist=waist, head=-waist * 0.5, arms=arms, arm_spread=spread,
                legs=legs, leg_l=leg_l, leg_spread=5)


# Língua (vídeo do LM, raposa 68,5-69,7 s e a câmera 54,3-54,9 s): já de BOCA ABERTA
# (FroggyCombatGoal abre no começo do ataque), ergue os dois braços em V em ~0,27 s,
# e só então a língua sai - num estalo (alcança em ~0,07 s), fica esticada e volta.
# A língua é uma fita chata (o renderer desenha); o osso "tongue" estica no eixo Z
# pelas animações *_ext abaixo (0 = recolhida, 100 = até o alvo).
ANIMS["tongue_whip"] = animation(0.7, [
    (0.00, pose()),
    (0.14, tongue_p(-4, -120, 18)),                                 # braços subindo
    (0.27, tongue_p(-6, -158, 28)),                                 # braços em V
    (0.36, tongue_p(20, -168, 32, root_y=-1)),                      # LÍNGUA (estalo)
    (0.50, tongue_p(22, -166, 32, root_y=-1)),
    (0.70, tongue_p(6, -60, 12)),
], "hold_on_last_frame")
ANIMS["tongue_grab"] = animation(0.8, [
    (0.00, pose()),
    (0.15, tongue_p(-4, -122, 18)),
    (0.30, tongue_p(-6, -160, 28)),                                 # braços em V
    (0.44, tongue_p(22, -170, 34, root_y=-1.5)),                    # LÍNGUA
    (0.65, tongue_p(14, -96, 14, root_y=-1.5, legs=-14, leg_l=14)),  # puxa
    (0.80, tongue_p(8, -70, 8)),
], "hold_on_last_frame")
ANIMS["tongue_capture"] = animation(1.4, [
    (0.00, pose()),
    (0.18, tongue_p(-4, -120, 18)),
    (0.35, tongue_p(-8, -160, 28)),                                 # braços em V
    (0.56, tongue_p(26, -172, 36, root_y=-2, legs=-16, leg_l=16)),  # LÍNGUA
    (0.90, tongue_p(22, -150, 26, root_y=-2, legs=-16, leg_l=16)),
    (1.05, tongue_p(30, -100, 12, root_y=-2.5, legs=-18, leg_l=18)),  # puxa e abre os braços pra agarrar
    (1.25, tongue_p(34, -92, 6, root_y=-2.5, legs=-18, leg_l=18)),
    (1.40, tongue_p(24, -84, 8, root_y=-1.5)),
], "hold_on_last_frame")


def tongue_ext(length, out, full, hold, back):
    """Só o osso da língua: recolhida até `out`, esticada em `full` (o estalo), segura até
    `hold` e volta até `back`. O impacto do servidor (NOTES.md) cai depois de `full`."""
    z = lambda v: [1, 1, v]
    return {"loop": "hold_on_last_frame", "animation_length": length, "bones": {"tongue": {"scale": {
        "0.0": z(0.001), f"{out:.4g}": z(0.001), f"{full:.4g}": z(100), f"{hold:.4g}": z(100),
        f"{back:.4g}": z(0.001)}}}}


ANIMS["tongue_whip_ext"] = tongue_ext(0.7, 0.28, 0.35, 0.5, 0.68)
ANIMS["tongue_grab_ext"] = tongue_ext(0.8, 0.36, 0.44, 0.6, 0.8)
ANIMS["tongue_capture_ext"] = tongue_ext(1.4, 0.47, 0.56, 1.1, 1.4)

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


# Comendo um mob caído (o "Taste testing": warden, golem, dragão...): a mesma
# flexão de quando come o jogador.
ANIMS["feed_mob"] = animation(PUSHUP_LEN, flexao_loop(), True, ground=False)

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


# Golpe final (a cutscene, entity.Finisher): a matança da zebra quadro a quadro
# (vídeo do LM, 77,3-88,8 s). Ele em pé por cima do quadril da vítima deitada de
# costas (pés dos dois lados do corpo), virado pra cabeça dela. Tempos = ticks da
# tabela / 20. As poses de morder foram ajustadas com o esqueleto (rig.py) pra
# boca cair EM CIMA do ombro (0,68 bloco à frente, 0,37 pro lado, 0,27 de altura)
# e do pescoço (0,82 à frente): o braço esquerdo dela fica do lado DIREITO dele.
OVER = dict(root_y=0, waist=18, head=30, arms=-22, arm_l=-18, arm_spread=12, legs=0, leg_spread=16)
FIN_HOLD = dict(root_y=0, waist=8, head=-6, arms=-26, arm_l=-16, arm_spread=12, legs=2, leg_spread=16)


def bend(side, **over):
    # side +1 = ombro esquerdo dela (direita dele), -1 = o direito dela
    p = dict(root_y=-4, waist=100, head=15, waist_y=30 * side, arms=-75, legs=leg_back(-4),
             arm_spread=16, leg_spread=18)
    p.update(over)
    return pose(**p)


def neck_bite(**over):
    p = dict(root_y=-4, waist=105, head=0, arms=-70, legs=leg_back(-4), arm_spread=18, leg_spread=18)
    p.update(over)
    return pose(**p)


def tear(side, k, neck=False):
    # cravado, rasgando: a cabeça sacode pros lados como cachorro (o resto quase parado)
    w = 1 if k % 2 == 0 else -1
    if neck:
        return neck_bite(head=4, head_y=22 * w, head_z=10 * w, waist_z=4 * w)
    return bend(side, waist=102, head=18, head_y=20 * w, head_z=10 * w, waist_z=4 * w)


def yank():
    # o TRANCO: arranca pra cima e pra trás com tudo, o pedaço sai na boca
    return pose(root_y=-0.5, waist=-14, head=-36, arms=-60, arm_l=-42, arm_spread=10, legs=10, leg_spread=16)


def fin_shake(side, amount=50, body=5):
    # em pé com o pedaço na boca sacudindo: quem sacode é a CABEÇA dele (o pedaço vai
    # junto); o tronco quase não mexe (LM: "é a cabeça que ele sacode, não o corpo")
    return pose(root_y=0, waist=4, waist_y=body * side, head=-12, head_y=amount * side, head_z=16 * side,
                arms=-30, arm_l=-20, arm_spread=12, legs=4, leg_spread=16)


def bite_sequence(t_bend, t_bite, t_rip, side, neck=False):
    keys = [(t_bend, pose(**OVER) if not neck else pose(**FIN_HOLD))]
    target = (neck_bite if neck else (lambda **o: bend(side, **o)))
    keys.append((round(t_bend + (t_bite - t_bend) * 0.6, 3), target(head=-6 if not neck else -14)))  # abre a boca
    keys.append((t_bite, target()))                                                                  # CRAVA
    k = 0
    t = t_bite + 0.1
    while t < t_rip - 0.12:
        keys.append((round(t, 3), tear(side, k, neck)))
        k += 1
        t += 0.1
    keys.append((round(t_rip - 0.08, 3), target(root_y=-6, waist=80, head=-20)))                      # puxa
    keys.append((t_rip, yank()))
    return keys


def shaking(t0, t1, period=0.2, amount=50, body=5):
    keys = []
    k = 0
    t = t0
    while t < t1 - 0.01:
        keys.append((round(t, 3), fin_shake(1 if k % 2 == 0 else -1, amount, body)))
        k += 1
        t += period
    return keys


# Em pé comendo o pedaço (vídeo da zebra, 2,1-2,9 s e 4,7-5,8 s): ereto, braços
# soltos do lado, o pedaço ATRAVESSADO na boca (cachorro com osso)
EAT = dict(root_y=0, waist=2, head=0, arms=-12, arm_l=-8, arm_spread=10, legs=2, leg_spread=16)


def eat_p(**over):
    return pose(**{**EAT, **over})


def gulp(t0, t1, side):
    """Uma engolida (v0.4.5, vídeo da zebra quadro a quadro): joga a cabeça pra trás
    com a boca pro céu e virada pra um lado, dá um tranco curto lá em cima (o pedaço
    desce pela goela; Finisher.GULPS_*, o pedaço encolhe no mesmo intervalo) e volta."""
    d = t1 - t0
    at = lambda f: round(t0 + d * f, 3)
    return [(round(t0, 3), eat_p(head=8, head_z=4 * side)),
            (at(0.25), eat_p(head=-44, head_y=14 * side, head_z=10 * side, waist=-6)),
            (at(0.5), eat_p(head=-52, head_y=-4 * side, head_z=-6 * side, waist=-8)),
            (at(0.75), eat_p(head=-42, head_y=10 * side, head_z=8 * side, waist=-6)),
            (round(t1, 3), eat_p(head=-16, waist=-3))]


def forward(side):
    # entre uma engolida e outra: a cabeça vem pra frente e pra baixo, tombada (2,8 s)
    return eat_p(head=12, head_y=6 * side, head_z=12 * side, waist=4)


# (v0.7.30) Comendo a arma roubada (FroggyDude VS Grox MANHUNT 1:24,0-1:25,1; LM: "ele sacode a cabeça
# rapidamente e vai engolindo a arma"). No vídeo: a arma atravessada na boca, a cabeça vira pra um lado e pro
# outro a cada ~0,1 s (é a CABEÇA que sacode, o corpo quase parado) e a arma vai encurtando até sumir em ~1,1 s;
# depois a boca aberta, vazia. No tempo do vídeo (LM: "igual do vídeo"): a sacudida de 0,15 a 1 s com o renderer
# encolhendo a arma junto, engole aos 1,05 s (tick 21 = FroggyStealGoal.SWALLOW) com a cabeça pra trás, 1,6 s ao todo.
ANIMS["weapon_eat"] = animation(1.6, (
    [(0.00, eat_p()),
     (0.08, eat_p(head=14, waist=6))]                                    # crava os dentes
    + shaking(0.15, 1.0, period=0.1, amount=30, body=4)                  # sacode rápido, ela entrando
    + gulp(1.02, 1.4, 1)                                                 # joga a cabeça pra trás e engole
    + [(1.60, eat_p())]), "hold_on_last_frame")


T = lambda ticks: round(ticks / 20.0, 3)
fin = [(0.00, pose(**OVER)),
       (0.35, pose(**{**OVER, "head": 36, "head_y": 10})),
       (T(14) - 0.05, pose(**OVER))]
fin += bite_sequence(T(14), T(20), T(32), 1)
# arrancou: um tranco curto rasgando e logo duas engolidas, a cabeça pra frente no meio;
# o braço some na boca no tick 64 (Finisher.GULPS_L/GONE_L)
fin += shaking(T(34), T(37), period=0.1, amount=40)
fin += gulp(T(37), T(46), 1)
fin += [(T(48), forward(-1))]
fin += gulp(T(50), T(60), -1)
fin += [(T(62), forward(1)), (T(64), eat_p()), (T(69), eat_p(head=4, waist=4))]
fin += bite_sequence(T(74), T(80), T(92), -1)
fin += gulp(T(94), T(102), -1)
fin += [(T(105), forward(1)), (T(108), forward(-1))]
fin += gulp(T(110), T(118), 1)
# sumiu (tick 120): uns 2 s parado, em pé, boca fechada, olhando pra frente (5,9-7,7 s)
fin += [(T(120), eat_p(head=-4)), (T(124), eat_p()), (T(134), eat_p(head=3, waist=3)),
        (T(144), eat_p()), (T(152), eat_p(head=3, waist=3))]
fin += bite_sequence(T(158), T(163), T(169), 0, neck=True)
# CLOSE (8,3-10,5 s): segura uns 0,5 s quase parado, só tremendo, e então chicoteia a
# cabeça pros lados MUITO rápido; o corpo dele não mexe
fin += [(T(171), fin_shake(1, 8, 0)), (T(175), fin_shake(-1, 10, 0)), (T(179), fin_shake(1, 8, 0))]
fin += shaking(T(181), T(214), period=0.1, amount=70, body=0)
# engole a cabeça (10,55-11,2 s): pra trás, uma chicotada pro lado, pra trás de novo
# encolhendo até sumir (Finisher.GULPS_H/SWALLOW) e fica parado olhando pra câmera
fin += gulp(T(215), T(219), 1)
fin += [(T(220), fin_shake(1, 55, 0)), (T(221), fin_shake(1, 45, 0))]
fin += gulp(T(222), T(228), -1)
fin += [(T(229), eat_p(head=-6)), (T(232), eat_p())]
fin.sort(key=lambda k: k[0])
ANIMS["finisher"] = animation(T(232), fin, "hold_on_last_frame")

# sem o braço esquerdo (a língua já arrancou): a mesma coisa a partir do tick 60
ANIMS["finisher_r"] = animation(T(232) - 3.0,
                                [(0.0, sample(ANIMS["finisher"], 3.0))] +
                                [(round(t - 3.0, 3), p) for t, p in fin if t > 3.0 + 1e-6],
                                "hold_on_last_frame")

# ------------------------------------------------------------------ no canto do teto (v0.6)

# Esperando a presa grudado no canto do teto (o print do LM; Grox, "Minecraft but I
# have rabies" 0:58-1:23): o agachado de sapo de cabeça pra baixo - mãos e pés no
# teto, o corpo pendurado embaixo deles, a cabeça torta olhando pra baixo, pro quarto.
# É o CROUCH virado 180 graus no eixo da frente (rz): continua de frente pra onde ele
# olha, e o que era o chão (mãos e pés) vira o teto. A entidade fica com o topo da
# caixa encostado no teto (FroggyStakeoutGoal): o teto está a 1,95 bloco dos pés dela.
CEILING_PX = 1.95 / rig.PX


def ceiling_p(waist=80, head=-100, head_z=0.0, sag=0.0):
    # tronco mais deitado que o agachado (colado no teto), braços abertos pra frente
    # agarrando o teto, pernas abertas (conferido no rig: mãos e pés a ~1,95-2,03, a
    # cabeça pendurada até 0,86 - acima do olho de quem está embaixo, num quarto de 3)
    p = crouch(waist=waist, head=head, head_z=head_z, arms=-140, arm_spread=30, leg_spread=30)
    p["root"]["rotation"] = [0, 0, 180]
    # virado, y vira -y: o chão do agachado (y = 0) fica no teto (CEILING_PX)
    p["root"]["position"][1] = round(CEILING_PX - CROUCH["root_y"] - sag, 2)
    return p


# respira devagar (o peito desce e sobe) e a cabeça vai tombando de um lado pro outro
ANIMS["ceiling_hang"] = animation(3.2, [
    (0.0, ceiling_p(head_z=-14)),
    (0.8, ceiling_p(waist=84, head=-104, head_z=-4, sag=0.4)),
    (1.6, ceiling_p(head_z=12)),
    (2.4, ceiling_p(waist=84, head=-104, head_z=4, sag=0.4)),
    (3.2, ceiling_p(head_z=-14)),
], True, ground=False)

# O bote do canto do teto (LM: "pulando contra o jogador, empurrando ou derrubando"):
# se solta, rola de barriga pra baixo no ar e mergulha de cabeça, braços esticados
# pra agarrar (o voo do jump_pin, inclinado 30 graus pra baixo). Fica no último
# quadro até pousar (FroggyCombatGoal.ceilingDive).
_DIVE = pose(root_x=30, waist=78, head=-70, arms=-165, legs=80, arm_spread=12, leg_spread=8)
_flip = blend(ceiling_p(), _DIVE, 0.5)
ANIMS["ceiling_dive"] = animation(0.4, [
    (0.0, ceiling_p()),
    (0.1, _flip),
    (0.2, _DIVE),
    (0.4, pose(root_x=36, waist=86, head=-56, arms=-150, legs=86, arm_spread=16, leg_spread=10)),  # agarra
], "hold_on_last_frame", ground=False)

# ------------------------------------------------------------------ o boom do warden (v0.6.2)

# O boom joga ele longe (LM; Grox, cidade antiga): o tranco dobra ele pra trás, voa se
# debatendo (braços e pernas abertos, trocando), cai de costas espalhado, ergue a cabeça
# olhando o warden, senta e volta pro agachado de sapo - e aí corre o dobro. 1,7 s
# (FroggyState.FLUNG, 34 ticks); o voo de verdade dura uns 0,6-0,9 s.
ANIMS["flung"] = animation(1.7, [
    (0.00, pose(root_x=-10, waist=-35, head=-45, arms=-140, arm_spread=45, legs=-25, leg_spread=14)),   # o tranco
    (0.12, pose(root_x=-40, waist=-25, head=-30, arms=-175, arm_l=-120, arm_spread=60, legs=-55, leg_l=-20,
                leg_spread=22)),                                                                       # voando, se debatendo
    (0.28, pose(root_x=-60, waist=-10, head=-20, arms=-120, arm_l=-175, arm_spread=70, legs=-25, leg_l=-65,
                leg_spread=26)),
    (0.45, pose(root_x=-75, waist=0, head=-10, arms=-175, arm_spread=55, legs=-70, leg_l=-35, leg_spread=20)),
    (0.60, pose(root_x=-90, waist=0, head=20, arms=-15, arm_spread=50, legs=0, leg_spread=18)),         # cai de costas
    (0.95, pose(root_x=-90, waist=4, head=38, arms=-10, arm_spread=55, legs=-4, leg_spread=20)),        # ergue a cabeça
    (1.20, pose(root_x=-90, waist=95, head=-40, arms=25, arm_spread=20, legs=0, leg_spread=14)),        # senta
    (1.45, crouch(head=-40)),                                                                           # agacha
    (1.70, crouch()),
], "hold_on_last_frame")

# ------------------------------------------------------------------ amostragem (pra teste)

def static(p):
    return {"loop": True, "animation_length": 1.0,
            "bones": {b: {c: {"0.0": v, "1.0": v} for c, v in ch.items()} for b, ch in p.items()}}


def main():
    data = json.load(open(MAIN))
    for name, anim in ANIMS.items():
        data["animations"][name] = anim
    # saíram (v0.5.0: a cena do corpo no Extremo foi tirada)
    for gone in ("corpse_toss", "corpse_shake"):
        data["animations"].pop(gone, None)
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
            "sky_drop": [0.209, 0.315, 0.347, 0.357, 0.4, 1.1, 1.45, 1.75, 3.2],
            "arm_rip": [0.2, 0.42, 0.52, 1.1, 1.22, 2.0],
            "arm_eat": [0.5, 0.64, 4.0],
            "weapon_eat": [0.08, 0.2, 0.3, 0.8, 1.2],
            "contort": [0.46, 0.7, 0.95, 1.2, 1.45, 1.7, 1.95, 2.2, 2.45, 2.7, 2.95],
            "contort_hold": [0.0, 0.76, 1.68],
            "roar": [0.1, 0.25, 0.38],
            "tongue_capture": [0.2, 0.6, 1.05],
            "feed": [0.22, 0.52],
            "climb": [round(i * 0.25 / 15, 4) for i in range(15)],
            "jump_pin": [0.2, 0.45, 0.68, 0.76, 1.0],
            "high_jump": [0.2, 0.3, 0.55, 0.75, 0.95, 1.1],
            "pin_hold": [0.0, 0.06, 0.12, 0.15, 0.3, 0.42],
            "feed_mob": [0.0, 0.12, 0.15],
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
