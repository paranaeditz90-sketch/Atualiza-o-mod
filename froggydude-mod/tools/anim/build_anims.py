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

BONES = ("root", "waist", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_shin", "left_shin")


def pose(root_y=0.0, root_x=0.0, waist=0.0, head=0.0, arms=0.0, legs=0.0,
         arm_l=None, leg_l=None, head_z=0.0, waist_z=0.0, arm_spread=0.0, leg_spread=0.0,
         root_px=0.0, head_y=0.0, waist_y=0.0, arm_ry=0.0, arm_ly=None, spread_l=None,
         arm_py=0.0, arm_lpy=0.0, leg_spread_l=None, head_pos=None, knee=None, knee_l=None):
    """Uma pose completa. arms/legs valem pros dois lados, a não ser que
    arm_l/leg_l digam outra coisa pro esquerdo. spread abre os membros pros lados
    (spread_l pro braço esquerdo, se for diferente). arm_ry gira o braço no
    próprio eixo (Y). arm_py/arm_lpy sobem o OMBRO (px; o braço inteiro sobe,
    como um ombro deslocado - a pose torta da fase 2). knee/knee_l dobram o
    joelho (X+ joga a canela pra trás); sem eles a canela fica reta e o
    osso nem entra na animação."""
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

# Parado um instante no meio da caçada (v0.7.14): agachado pronto pra disparar, na pose
# de onde o galope sai (14/15: pernas por baixo da barriga, mãos no chão na frente, olhando
# pra frente), respirando. Antes era a "mesa" (cintura a 90 graus com braços e pernas
# retos e em pé - o mesmo "de quatro feito mesa" que o LM barrou no comer).
STALK = GALLOP[14][1:]
ANIMS["crouch_idle"] = animation(1.2, [
    (0.00, gallop_pose(*STALK)),
    (0.60, gallop_pose(STALK[0] + 4, *STALK[1:5], STALK[5] + 2, *STALK[6:])),
    (1.20, gallop_pose(*STALK)),
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

# Comendo em "flexão" (LM: "never kill my dog" 0:00-1:47 comendo o jogador, e o
# "Taste testing Minecraft mobs" 6,2-8,8 s em cima do warden): deitado de bruços em
# cima da vítima, mãos plantadas no chão dos lados dela, pés no chão lá atrás, e
# a cabeça (com o peito) DESCE rápido e mordendo, sobe devagar e segura um tico.
# Medido no warden (coroa quadro a quadro, 30 fps): um ciclo a cada 0,33 s - desce
# em ~0,07 s, fica embaixo ~0,03, sobe em ~0,17 e segura ~0,07. Aqui 0,3 s por
# mordida (6 ticks, o servidor morde junto) e duas mordidas por volta, uma
# puxando pra cada lado.
# Conferido no rig (pushup.py/check_poses.py): as mãos ficam no mesmo lugar (~0,4
# bloco na frente, embaixo do peito) em cima e embaixo; a boca vai de 0,69 a 0,42 de
# altura; com a vítima a 1,5 bloco a câmera dela fica a 0,19 bloco da cabeça dele.
# (LM) A cabeça fica RETA: alinhada com o corpo, sem virar pros lados e sem levantar -
# quem sobe e desce é o peito (e um tico o quadril, que vai pro outro lado).
PUSH_UP = dict(root_y=-6, waist=60, legs=70, head=-15, arms=-44, arm_spread=18, leg_spread=10)
PUSH_DOWN = dict(root_y=-3.5, waist=84, legs=58, head=-15, arms=-58, arm_spread=18, leg_spread=10)


def pushup_loop():
    """Duas mordidas (0,6 s): desce rápido, fica embaixo um tico, sobe devagar, segura."""
    keys = []
    for k in (0, 1):
        o = k * 0.3
        keys += [
            (o + 0.00, pose(**PUSH_UP)),
            (o + 0.06, pose(**PUSH_UP)),                                                  # segura em cima
            (o + 0.12, pose(**PUSH_DOWN)),                                                # MORDE
            (o + 0.15, pose(**{**PUSH_DOWN, "waist": PUSH_DOWN["waist"] + 2})),           # crava
        ]
    keys.append((0.60, pose(**PUSH_UP)))
    return keys


ANIMS["pin_hold"] = animation(0.6, pushup_loop(), True)

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
KNEEL = dict(root_y=-4, legs=0, knee=90, leg_spread=8, arm_spread=4)
SMASH_UP = dict(**KNEEL, waist=-8, head=35, arms=-170)
SMASH_HIT = dict(**KNEEL, waist=30, head=15, arms=-70)
ANIMS["smash"] = animation(0.5, [
    (0.00, pose(**SMASH_UP)),
    (0.14, pose(**{**SMASH_UP, "waist": -14, "head": 30, "arms": -186})),      # arma o golpe
    (0.20, pose(**{**KNEEL, "waist": 10, "head": 24, "arms": -125})),          # descendo
    (0.25, pose(**SMASH_HIT)),                                                 # IMPACTO
    (0.31, pose(**{**SMASH_HIT, "waist": 33, "head": 13, "arms": -66})),
    (0.40, pose(**{**KNEEL, "waist": 10, "head": 26, "arms": -140})),          # sobe de novo
    (0.50, pose(**SMASH_UP)),
], True)

# Pulo altíssimo (vs AJ, 0:56): agacha fundo, explode pra cima com os braços pro
# céu, abre em Y lá em cima (some no céu, ~12 blocos) e despenca olhando pra
# vítima com os braços prontos pro primeiro soco. Impulso no tick 8 (0,4 s),
# topo no ~25 (1,25 s), chão no ~44 (2,2 s). Ao pousar vira o esmagamento.
ANIMS["sky_drop"] = animation(2.2, [
    # (v0.4.0, vídeo do LM 48,8-50,1 s e 55,3-56,4 s) em pé de braços em V uns 0,3 s,
    # agacha num piscar (1-2 quadros) e sai como foguete: some do quadro em ~0,1 s
    (0.00, pose(root_y=0, waist=6, head=-8, arms=-40, arm_spread=12, legs=0, leg_spread=8)),
    (0.10, pose(root_y=0, waist=-4, head=-14, arms=-158, arm_spread=30, legs=0, leg_spread=10)),   # braços em V
    (0.31, pose(root_y=0, waist=-6, head=-16, arms=-162, arm_spread=32, legs=0, leg_spread=10)),
    (0.36, pose(root_y=-6, waist=34, head=-30, arms=-150, arm_spread=24, legs=leg_back(-6), leg_spread=14)),  # agacha (estalo)
    (0.40, pose(root_y=0, waist=-5, head=-10, arms=-176, arm_spread=8, legs=5)),                    # FOGUETE
    (0.70, pose(root_y=0, waist=0, head=-5, arms=-178, arm_spread=10, legs=0)),                     # subindo esticado
    (1.10, pose(root_y=0, waist=5, head=10, arms=-158, arm_spread=36, legs=0, leg_spread=22)),      # abre em Y
    (1.30, pose(root_y=0, waist=8, head=24, arms=-158, arm_spread=38, legs=0, leg_spread=24)),      # topo, olhando pra baixo
    (1.70, pose(root_y=0, waist=15, head=32, arms=-172, arm_spread=14, legs=-32, leg_spread=15)),   # despencando de braços erguidos
    (2.10, pose(root_y=0, waist=20, head=36, arms=-176, arm_spread=10, legs=-52, leg_spread=14)),
    (2.20, pose(root_y=-2, waist=22, head=36, arms=-176, arm_spread=8, legs=-40, leg_spread=12)),
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
ANIMS["feed_mob"] = animation(0.6, pushup_loop(), True)

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
            "sky_drop": [0.3, 0.4, 1.1, 1.3, 1.7, 2.2],
            "arm_rip": [0.2, 0.42, 0.52, 1.1, 1.22, 2.0],
            "arm_eat": [0.5, 0.64, 4.0],
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
