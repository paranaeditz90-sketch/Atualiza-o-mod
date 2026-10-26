"""Gera as animações do BecomeLM a partir das do Froggydude.

O corpo do jogador usa o mesmo rig e as mesmas animações do FroggyDude (já acertadas
com os vídeos). Os pulos do mod principal começam agachando (o Froggy prepara o pulo
antes de sair do chão); o jogador sai do chão na hora que aperta o pulo, então aqui
vão cópias cortadas que já começam no ar.

Uso (da pasta do repositório):
    python3 becomelm-mod/tools/make_anims.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, '..', 'froggydude-mod', 'src', 'main', 'resources', 'assets', 'froggydude',
                   'animations', 'froggydude.animation.json')
DST = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'becomelm', 'animations', 'lm.animation.json')

# nova animação: (original, segundo em que começa)
CUTS = {
    'leap_air': ('jump_pin', 0.2),     # o bote, já esticado no ar
    'high_air': ('high_jump', 0.25),   # o pulo alto
    'sky_air': ('sky_drop', 0.38),     # o pulo super alto (cai em cima)
}


# animações novas, feitas à mão (o Froggydude não tem)
def _k(pairs):
    return {str(t): v for t, v in pairs}


EXTRA = {
    # a queda do céu (10v1 0:13.4-16.1): ajoelhado em cima da vítima de costas, um golpe a cada
    # 0,25 s com os DOIS punhos juntos (LM: "no original ele bate com as duas mãos ao mesmo
    # tempo"), e o tronco inteiro mergulha até a cara dela a cada golpe
    'sky_smash': {
        'loop': True,
        'animation_length': 0.5,
        'bones': {
            'root': {'position': _k([(0.0, [0, -4, 0]), (0.1, [0, -4.6, 0]), (0.25, [0, -4, 0]),
                                     (0.35, [0, -4.6, 0]), (0.5, [0, -4, 0])])},
            'waist': {'rotation': _k([(0.0, [-10, 0, 0]), (0.06, [30, 0, 0]), (0.1, [62, 0, 0]),
                                      (0.16, [45, 0, 0]), (0.25, [-10, 0, 0]), (0.31, [30, 0, 0]),
                                      (0.35, [62, 0, 0]), (0.41, [45, 0, 0]), (0.5, [-10, 0, 0])])},
            'head': {'rotation': _k([(0.0, [22, 0, 0]), (0.1, [2, 0, 0]), (0.16, [8, 0, 0]),
                                     (0.25, [22, 0, 0]), (0.35, [2, 0, 0]), (0.41, [8, 0, 0]),
                                     (0.5, [22, 0, 0])])},
            'right_arm': {'rotation': _k([(0.0, [-170, 0, 12]), (0.06, [-120, 0, 9]), (0.1, [-70, 0, 5]),
                                          (0.16, [-95, 0, 7]), (0.25, [-170, 0, 12]), (0.31, [-120, 0, 9]),
                                          (0.35, [-70, 0, 5]), (0.41, [-95, 0, 7]), (0.5, [-170, 0, 12])])},
            'left_arm': {'rotation': _k([(0.0, [-170, 0, -12]), (0.06, [-120, 0, -9]), (0.1, [-70, 0, -5]),
                                         (0.16, [-95, 0, -7]), (0.25, [-170, 0, -12]), (0.31, [-120, 0, -9]),
                                         (0.35, [-70, 0, -5]), (0.41, [-95, 0, -7]), (0.5, [-170, 0, -12])])},
            'right_leg': {'rotation': _k([(0.0, [-5, 0, 18])])},
            'left_leg': {'rotation': _k([(0.0, [-5, 0, -18])])},
            'right_shin': {'rotation': _k([(0.0, [95, 0, 0])])},
            'left_shin': {'rotation': _k([(0.0, [95, 0, 0])])},
        },
    },
}


# morder e segurar na boca (no lugar da língua: o personagem do LM é um cachorro). A pose de
# segurar abaixa a frente até a boca ficar rente ao chão (~1 bloco na frente, ~0,35 de altura),
# onde fica a cabeça de quem está deitado sendo arrastado.
def _hold(**over):
    pose = {
        'root': {'position': [0, -3, 0]},
        'waist': {'rotation': [105, 0, 0]},
        'head': {'rotation': [-75, 0, 0]},
        'right_arm': {'rotation': [-155, 0, 8]},
        'left_arm': {'rotation': [-155, 0, -8]},
        'right_leg': {'rotation': [-35, 0, 6]},
        'left_leg': {'rotation': [-35, 0, -6]},
        'right_shin': {'rotation': [70, 0, 0]},
        'left_shin': {'rotation': [70, 0, 0]},
    }
    for bone, chans in over.items():
        pose.setdefault(bone, {}).update(chans)
    return pose


def _anim(length, loop, keys):
    """keys: [(tempo, pose)] -> canais por osso (todos os ossos em todas as poses)."""
    bones = {}
    for t, pose in keys:
        for bone, chans in pose.items():
            for ch, v in chans.items():
                bones.setdefault(bone, {}).setdefault(ch, {})[str(t)] = v
    return {'loop': loop, 'animation_length': length, 'bones': bones}


EXTRA.update({
    'grab_start': _anim(0.3, 'hold_on_last_frame', [
        (0.0, _hold(root={'position': [0, -1, 0]}, waist={'rotation': [80, 0, 0]}, head={'rotation': [-60, 0, 0]},
                    right_arm={'rotation': [-120, 0, 8]}, left_arm={'rotation': [-120, 0, -8]},
                    right_leg={'rotation': [-20, 0, 6]}, left_leg={'rotation': [-20, 0, -6]},
                    right_shin={'rotation': [40, 0, 0]}, left_shin={'rotation': [40, 0, 0]})),
        (0.1, _hold(root={'position': [0, -3.5, 0]}, waist={'rotation': [112, 0, 0]}, head={'rotation': [-60, 0, 0]},
                    right_arm={'rotation': [-165, 0, 8]}, left_arm={'rotation': [-165, 0, -8]})),
        (0.2, _hold(waist={'rotation': [100, 0, 0]}, head={'rotation': [-80, 0, 0]})),
        (0.3, _hold()),
    ]),
    'grab_hold': _anim(1.0, True, [
        (0.0, _hold()),
        (0.5, _hold(waist={'rotation': [108, 0, 0]}, head={'rotation': [-78, 0, 0]})),
        (1.0, _hold()),
    ]),
    'grab_walk': _anim(0.5, True, [
        (0.0, _hold(right_arm={'rotation': [-170, 0, 8]}, left_arm={'rotation': [-140, 0, -8]},
                    right_leg={'rotation': [-20, 0, 6]}, left_leg={'rotation': [-50, 0, -6]},
                    waist={'rotation': [105, 4, 0]})),
        (0.125, _hold(root={'position': [0, -2.6, 0]})),
        (0.25, _hold(right_arm={'rotation': [-140, 0, 8]}, left_arm={'rotation': [-170, 0, -8]},
                     right_leg={'rotation': [-50, 0, 6]}, left_leg={'rotation': [-20, 0, -6]},
                     waist={'rotation': [105, -4, 0]})),
        (0.375, _hold(root={'position': [0, -2.6, 0]})),
        (0.5, _hold(right_arm={'rotation': [-170, 0, 8]}, left_arm={'rotation': [-140, 0, -8]},
                    right_leg={'rotation': [-20, 0, 6]}, left_leg={'rotation': [-50, 0, -6]},
                    waist={'rotation': [105, 4, 0]})),
    ]),
    # a sacudida: a cabeça vai e volta a cada 0,2 s (uma mordida forte por ida e volta)
    'grab_shake': _anim(0.2, True, [
        (0.0, _hold(right_arm={'rotation': [-150, 0, 10]}, left_arm={'rotation': [-150, 0, -10]})),
        (0.05, _hold(head={'rotation': [-75, 32, 0]}, waist={'rotation': [105, 12, 6]},
                     right_arm={'rotation': [-150, 0, 10]}, left_arm={'rotation': [-150, 0, -10]})),
        (0.1, _hold(right_arm={'rotation': [-150, 0, 10]}, left_arm={'rotation': [-150, 0, -10]})),
        (0.15, _hold(head={'rotation': [-75, -32, 0]}, waist={'rotation': [105, -12, -6]},
                     right_arm={'rotation': [-150, 0, 10]}, left_arm={'rotation': [-150, 0, -10]})),
        (0.2, _hold(right_arm={'rotation': [-150, 0, 10]}, left_arm={'rotation': [-150, 0, -10]})),
    ]),
    # arremesso: a cabeça chicoteia pra cima e pra frente e solta; volta pra de quatro
    'grab_throw': _anim(0.45, 'hold_on_last_frame', [
        (0.0, _hold()),
        (0.1, _hold(root={'position': [0, -2.5, 0]}, waist={'rotation': [70, 0, 0]}, head={'rotation': [-10, 0, 0]},
                    right_arm={'rotation': [-120, 0, 10]}, left_arm={'rotation': [-120, 0, -10]})),
        (0.22, _hold(root={'position': [0, -1, 0]}, waist={'rotation': [40, 0, 0]}, head={'rotation': [10, 0, 0]},
                     right_arm={'rotation': [-70, 0, 10]}, left_arm={'rotation': [-70, 0, -10]},
                     right_leg={'rotation': [-15, 0, 6]}, left_leg={'rotation': [-15, 0, -6]},
                     right_shin={'rotation': [25, 0, 0]}, left_shin={'rotation': [25, 0, 0]})),
        (0.45, _hold(root={'position': [0, -0.5, 0]}, waist={'rotation': [90, 0, 0]}, head={'rotation': [-66, 0, 0]},
                     right_arm={'rotation': [-90, 0, 4]}, left_arm={'rotation': [-88, 0, -4]},
                     right_leg={'rotation': [0, 0, 4]}, left_leg={'rotation': [4, 0, -4]},
                     right_shin={'rotation': [0, 0, 0]}, left_shin={'rotation': [0, 0, 0]})),
    ]),
})


# no monte de pedaços (10v1 0:21.8, How many days 0:44 e 1:20, DONT MESS 1:03, VS Every 14:06):
# deitado de bruços em cima dos cacos, os braços abertos por cima do monte, e a cabeça
# esfregando na carne - inclina ~30 graus pra um lado e pro outro, afunda, e de vez em quando
# levanta e olha em volta (o peito sobe e os braços empurram). Ele não come: só fica em cima.
def _prone(**over):
    pose = {
        'root': {'rotation': [90, 0, 0], 'position': [0, 5, 0]},
        'waist': {'rotation': [0, 0, 0]},
        'head': {'rotation': [-25, 0, 0]},
        'right_arm': {'rotation': [-30, 0, 75]},
        'left_arm': {'rotation': [-30, 0, -75]},
        'right_leg': {'rotation': [0, 0, 8]},
        'left_leg': {'rotation': [0, 0, -8]},
        'right_shin': {'rotation': [10, 0, 0]},
        'left_shin': {'rotation': [10, 0, 0]},
    }
    for bone, chans in over.items():
        pose.setdefault(bone, {}).update(chans)
    return pose


def _stand():
    return {
        'root': {'rotation': [0, 0, 0], 'position': [0, 0, 0]},
        'waist': {'rotation': [0, 0, 0]},
        'head': {'rotation': [0, 0, 0]},
        'right_arm': {'rotation': [0, 0, 0]},
        'left_arm': {'rotation': [0, 0, 0]},
        'right_leg': {'rotation': [0, 0, 0]},
        'left_leg': {'rotation': [0, 0, 0]},
        'right_shin': {'rotation': [0, 0, 0]},
        'left_shin': {'rotation': [0, 0, 0]},
    }


# comendo no monte em "flexão" (LM: "fica com o ombro pra cima enquanto come e o tronco sobe e
# desce, parecido com flexão mesmo", "sem levantar a bunda"). Medido a 60 qps no How many days
# (0:44, de frente) e no 10v1 (0:22 no deserto, 1:55 de pertinho): o quadril e as pernas ficam
# deitados nos cacos, o peito empinado e os braços retos pra frente por cima do monte, tipo esfinge
# (o LM escolheu; ele não abre os braços pro lado), o que sobe os ombros pro lado da cabeça (de
# frente ficam na altura do meio dela). A cada 0,3 s o tronco desce e a cara mergulha nos cacos
# entre os braços (de olhar pra frente a olhar pra baixo: o topo da cabeça desce ~6 px) e volta; a
# cabeça vai tombando devagar pra um lado e pro outro (~20 graus).
# (cintura X, cabeça X, braço X, braço Z) em cima e afundado; os braços vêm do esqueleto (rig.py):
# a mão fica parada em cima dos cacos (~2 blocos na frente dos pés, 0,4 pro lado) enquanto o tronco desce
_PUSH_UP = (-24, -46, -114.5, -19.0)
_PUSH_DOWN = (-12, -6, -134.5, -14.0)
_SHRUG = 2.0
# 8 mergulhadas no ciclo (2,4 s): o tombo (Y da cabeça, que de frente é tombar pro ombro) e o virar (Z)
_TILTS = (0, 12, 22, 16, 0, -14, -22, -12)
_TURNS = (0, 5, 9, 5, 0, -5, -9, -4)


def _push(dip, tilt=0, turn=0):
    """dip 0 = em cima (ombros altos, olhando pra frente), 1 = a cara afundada nos cacos."""
    w, h, ax, az = (a + (b - a) * dip for a, b in zip(_PUSH_UP, _PUSH_DOWN))
    return _prone(waist={'rotation': [round(w, 2), round(tilt * 0.15, 2), 0]},
                  head={'rotation': [round(h, 2), tilt, turn]},
                  right_arm={'rotation': [round(ax, 2), 0, round(az, 2)], 'position': [0, _SHRUG, 0]},
                  left_arm={'rotation': [round(ax, 2), 0, round(-az, 2)], 'position': [0, _SHRUG, 0]})


def _push_keys():
    keys = []
    for i, (tilt, turn) in enumerate(zip(_TILTS, _TURNS)):
        t = round(i * 0.3, 2)
        keys += [(t, _push(0, tilt, turn)), (round(t + 0.05, 2), _push(0.12, tilt, turn)),
                 (round(t + 0.15, 2), _push(1, tilt, turn)), (round(t + 0.2, 2), _push(0.92, tilt, turn))]
    return keys + [(2.4, _push(0, _TILTS[0], _TURNS[0]))]


EXTRA.update({
    # se joga no monte: sem pose em pé no começo (o controlador já mistura de onde ele estiver;
    # de 4 ele ficava em pé um instante antes de cair)
    'pile_drop': _anim(0.28, 'hold_on_last_frame', [
        (0.0, _prone(root={'rotation': [40, 0, 0], 'position': [0, 2, 0]}, head={'rotation': [-10, 0, 0]},
                     right_arm={'rotation': [-120, 0, 30]}, left_arm={'rotation': [-120, 0, -30]},
                     right_leg={'rotation': [0, 0, 4]}, left_leg={'rotation': [0, 0, -4]},
                     right_shin={'rotation': [0, 0, 0]}, left_shin={'rotation': [0, 0, 0]})),
        (0.13, _prone(root={'rotation': [92, 0, 0], 'position': [0, 5, 0]}, waist={'rotation': [-20, 0, 0]},
                      head={'rotation': [-30, 0, 0]},
                      right_arm={'rotation': [-50, 0, 62]}, left_arm={'rotation': [-50, 0, -62]})),
        (0.28, _push(0)),
    ]),
    'pile_lie': _anim(2.4, True, _push_keys()),
    'pile_rise': _anim(0.5, 'hold_on_last_frame', [
        (0.0, _push(0)),
        (0.15, _prone(root={'rotation': [70, 0, 0], 'position': [0, 5, 0]}, head={'rotation': [-30, 0, 0]},
                      right_arm={'rotation': [-90, 0, 12]}, left_arm={'rotation': [-90, 0, -12]})),
        (0.3, _prone(root={'rotation': [30, 0, 0], 'position': [0, 3, 0]}, waist={'rotation': [30, 0, 0]},
                     head={'rotation': [-20, 0, 0]},
                     right_arm={'rotation': [-60, 0, 8]}, left_arm={'rotation': [-60, 0, -8]},
                     right_leg={'rotation': [-40, 0, 8]}, left_leg={'rotation': [-40, 0, -8]},
                     right_shin={'rotation': [60, 0, 0]}, left_shin={'rotation': [60, 0, 0]})),
        (0.5, _stand()),
    ]),
})


# a finalização (LmFinish): ajoelhado em cima da vítima de costas, nas coxas dela, virado pra
# cabeça (a mesma base do sky_smash). Monta, cava a barriga com as patas alternando feito
# cachorro, enfia as mãos e abre pros lados, come (mergulha a cara, rasga sacudindo, puxa a tripa
# até a boca e mastiga: 3 vezes) e no fim levanta a cara e encara a câmera, respirando pesado.
def _kneel(**over):
    pose = {
        'root': {'rotation': [0, 0, 0], 'position': [0, -4, 0]},
        'waist': {'rotation': [25, 0, 0]},
        'head': {'rotation': [22, 0, 0]},
        'right_arm': {'rotation': [-55, 0, 10]},
        'left_arm': {'rotation': [-55, 0, -10]},
        'right_leg': {'rotation': [-5, 0, 18]},
        'left_leg': {'rotation': [-5, 0, -18]},
        'right_shin': {'rotation': [95, 0, 0]},
        'left_shin': {'rotation': [95, 0, 0]},
    }
    for bone, chans in over.items():
        pose.setdefault(bone, {}).update(chans)
    return pose


# cavando: o braço vai lá na frente (-125), afunda (-95), puxa de volta raspando pra baixo do
# peito (-45) e sobe de novo; o outro faz o mesmo meio ciclo depois
_REACH, _IN, _DRAG, _LIFT_ARM = -125, -95, -45, -100


def _dig(t, right, left, twist):
    return _kneel(root={'position': [0, -4.4 if t in (0.1, 0.3) else -4, 0]},
                  waist={'rotation': [55, twist, 0]}, head={'rotation': [12, -twist, 0]},
                  right_arm={'rotation': [right, 0, 14]}, left_arm={'rotation': [left, 0, -14]})


_FEED_HOLD = dict(right_arm={'rotation': [-70, 0, 35]}, left_arm={'rotation': [-70, 0, -35]})

EXTRA.update({
    'fin_mount': _anim(0.4, 'hold_on_last_frame', [
        (0.0, _kneel(root={'position': [0, -1, 0]}, waist={'rotation': [50, 0, 0]}, head={'rotation': [10, 0, 0]},
                     right_arm={'rotation': [-110, 0, 14]}, left_arm={'rotation': [-110, 0, -14]},
                     right_shin={'rotation': [60, 0, 0]}, left_shin={'rotation': [60, 0, 0]})),
        (0.2, _kneel(root={'position': [0, -4.6, 0]}, waist={'rotation': [35, 0, 0]},
                     right_arm={'rotation': [-80, 0, 12]}, left_arm={'rotation': [-80, 0, -12]})),
        (0.4, _kneel()),
    ]),
    'fin_dig': _anim(0.4, True, [
        (0.0, _dig(0.0, _REACH, _DRAG, 6)),
        (0.1, _dig(0.1, _IN, _LIFT_ARM, 3)),
        (0.2, _dig(0.2, _DRAG, _REACH, -6)),
        (0.3, _dig(0.3, _LIFT_ARM, _IN, -3)),
        (0.4, _dig(0.4, _REACH, _DRAG, 6)),
    ]),
    # as duas mãos afundam e puxam pros lados (o estalo das costelas é no tranco, 0,45 s)
    'fin_open': _anim(0.9, 'hold_on_last_frame', [
        (0.0, _kneel(waist={'rotation': [58, 0, 0]}, head={'rotation': [10, 0, 0]},
                     right_arm={'rotation': [-70, 0, 6]}, left_arm={'rotation': [-70, 0, -6]})),
        (0.25, _kneel(waist={'rotation': [62, 0, 0]}, head={'rotation': [14, 0, 0]},
                      right_arm={'rotation': [-55, 0, 10]}, left_arm={'rotation': [-55, 0, -10]})),
        (0.45, _kneel(waist={'rotation': [40, 0, 0]}, head={'rotation': [-5, 0, 0]},
                      right_arm={'rotation': [-75, 0, 58]}, left_arm={'rotation': [-75, 0, -58]})),
        (0.6, _kneel(waist={'rotation': [44, 0, 0]}, head={'rotation': [18, 0, 0]},
                     right_arm={'rotation': [-70, 0, 50]}, left_arm={'rotation': [-70, 0, -50]})),
        (0.9, _kneel(waist={'rotation': [45, 0, 0]}, head={'rotation': [24, 0, 0]}, **_FEED_HOLD)),
    ]),
    # 1,8 s (36 ticks, LmFinish.FEED_CYCLE): mergulha, rasga sacudindo, puxa a tripa até a boca, mastiga
    'fin_feed': _anim(1.8, True, [
        (0.0, _kneel(waist={'rotation': [45, 0, 0]}, head={'rotation': [24, 0, 0]}, **_FEED_HOLD)),
        (0.3, _kneel(root={'position': [0, -4.8, 0]}, waist={'rotation': [86, 0, 0]}, head={'rotation': [2, 0, 0]},
                     **_FEED_HOLD)),
        (0.45, _kneel(root={'position': [0, -4.8, 0]}, waist={'rotation': [84, 8, 4]}, head={'rotation': [0, 25, 0]},
                      **_FEED_HOLD)),
        (0.55, _kneel(root={'position': [0, -4.8, 0]}, waist={'rotation': [84, -8, -4]}, head={'rotation': [0, -25, 0]},
                      **_FEED_HOLD)),
        (0.65, _kneel(root={'position': [0, -4.8, 0]}, waist={'rotation': [84, 8, 4]}, head={'rotation': [0, 25, 0]},
                      **_FEED_HOLD)),
        (0.75, _kneel(root={'position': [0, -4.8, 0]}, waist={'rotation': [84, -6, -3]}, head={'rotation': [0, -18, 0]},
                      **_FEED_HOLD)),
        (1.0, _kneel(waist={'rotation': [28, 0, 0]}, head={'rotation': [-38, 0, 0]},
                     right_arm={'rotation': [-150, 0, 18]}, left_arm={'rotation': [-75, 0, -30]})),
        (1.2, _kneel(waist={'rotation': [24, 0, 0]}, head={'rotation': [-12, 0, 0]},
                     right_arm={'rotation': [-120, 0, 15]}, left_arm={'rotation': [-70, 0, -30]})),
        (1.32, _kneel(waist={'rotation': [24, 0, 0]}, head={'rotation': [-2, 0, 0]},
                      right_arm={'rotation': [-115, 0, 15]}, left_arm={'rotation': [-70, 0, -30]})),
        (1.44, _kneel(waist={'rotation': [24, 0, 0]}, head={'rotation': [-14, 0, 0]},
                      right_arm={'rotation': [-115, 0, 15]}, left_arm={'rotation': [-70, 0, -30]})),
        (1.56, _kneel(waist={'rotation': [24, 0, 0]}, head={'rotation': [-2, 0, 0]},
                      right_arm={'rotation': [-110, 0, 15]}, left_arm={'rotation': [-70, 0, -30]})),
        (1.8, _kneel(waist={'rotation': [45, 0, 0]}, head={'rotation': [24, 0, 0]}, **_FEED_HOLD)),
    ]),
    # encara a câmera (que está na frente da cara dele), os ombros subindo e descendo, a cabeça tombando
    'fin_stare': _anim(2.0, True, [
        (0.0, _kneel(waist={'rotation': [6, 0, 0]}, head={'rotation': [-6, 0, 8]},
                     right_arm={'rotation': [-22, 0, 12]}, left_arm={'rotation': [-22, 0, -12]})),
        (0.5, _kneel(root={'position': [0, -3.6, 0]}, waist={'rotation': [2, 0, 0]}, head={'rotation': [-9, 0, 12]},
                     right_arm={'rotation': [-18, 0, 14]}, left_arm={'rotation': [-18, 0, -14]})),
        (1.0, _kneel(waist={'rotation': [8, 0, 0]}, head={'rotation': [-5, 0, 6]},
                     right_arm={'rotation': [-24, 0, 12]}, left_arm={'rotation': [-24, 0, -12]})),
        (1.5, _kneel(root={'position': [0, -3.6, 0]}, waist={'rotation': [2, 0, 0]}, head={'rotation': [-9, 0, 14]},
                     right_arm={'rotation': [-18, 0, 14]}, left_arm={'rotation': [-18, 0, -14]})),
        (2.0, _kneel(waist={'rotation': [6, 0, 0]}, head={'rotation': [-6, 0, 8]},
                     right_arm={'rotation': [-22, 0, 12]}, left_arm={'rotation': [-22, 0, -12]})),
    ]),
})


# o mortal saindo do monte (How many days 0:47, 10v1 0:26.2, em 60 qps): os braços sobem na
# frente pro impulso, o corpo vai esticado (a volta pra trás é o desenho que gira, em volta da
# cintura), as pernas encolhem um pouco no meio e no fim os braços abrem retos pros lados - o
# "tcharam", que segura com o peito pra fora e o queixo pra cima
_TADA = dict(waist={'rotation': [-6, 0, 0]}, head={'rotation': [-10, 0, 0]},
             right_arm={'rotation': [0, 0, 88]}, left_arm={'rotation': [0, 0, -88]},
             right_leg={'rotation': [0, 0, 7]}, left_leg={'rotation': [0, 0, -7]})

EXTRA.update({
    'flip': _anim(0.45, 'hold_on_last_frame', [
        (0.0, _stand() | {'right_arm': {'rotation': [-120, 0, 10]}, 'left_arm': {'rotation': [-120, 0, -10]},
                          'waist': {'rotation': [10, 0, 0]}, 'head': {'rotation': [-15, 0, 0]}}),
        (0.12, _stand() | {'right_arm': {'rotation': [-170, 0, 12]}, 'left_arm': {'rotation': [-170, 0, -12]},
                           'waist': {'rotation': [-8, 0, 0]}, 'head': {'rotation': [-25, 0, 0]},
                           'right_leg': {'rotation': [-25, 0, 4]}, 'left_leg': {'rotation': [-25, 0, -4]},
                           'right_shin': {'rotation': [35, 0, 0]}, 'left_shin': {'rotation': [35, 0, 0]}}),
        (0.25, _stand() | {'right_arm': {'rotation': [-150, 0, 30]}, 'left_arm': {'rotation': [-150, 0, -30]},
                           'waist': {'rotation': [-5, 0, 0]}, 'head': {'rotation': [-20, 0, 0]},
                           'right_leg': {'rotation': [-15, 0, 4]}, 'left_leg': {'rotation': [-15, 0, -4]},
                           'right_shin': {'rotation': [20, 0, 0]}, 'left_shin': {'rotation': [20, 0, 0]}}),
        (0.45, _stand() | {'right_arm': {'rotation': [-20, 0, 70]}, 'left_arm': {'rotation': [-20, 0, -70]},
                           'waist': {'rotation': [-4, 0, 0]}, 'head': {'rotation': [-8, 0, 0]}}),
    ]),
    'tada': _anim(0.7, True, [
        (0.0, _stand() | _TADA),
        (0.35, _stand() | _TADA | {'waist': {'rotation': [-9, 0, 0]}, 'head': {'rotation': [-13, 0, 0]},
                                   'right_arm': {'rotation': [0, 0, 92]}, 'left_arm': {'rotation': [0, 0, -92]}}),
        (0.7, _stand() | _TADA),
    ]),
})


def value_at(frames, t):
    keys = sorted(frames, key=float)
    times = [float(k) for k in keys]
    if t <= times[0]:
        return list(frames[keys[0]])
    for i in range(1, len(times)):
        if t <= times[i]:
            a, b = frames[keys[i - 1]], frames[keys[i]]
            k = (t - times[i - 1]) / (times[i] - times[i - 1])
            return [round(x + (y - x) * k, 3) for x, y in zip(a, b)]
    return list(frames[keys[-1]])


def cut(anim, start):
    out = json.loads(json.dumps(anim))
    out['animation_length'] = round(anim['animation_length'] - start, 3)
    for bone in out.get('bones', {}).values():
        for ch, frames in list(bone.items()):
            if not isinstance(frames, dict):
                continue
            new = {'0.0': value_at(frames, start)}
            for k, v in frames.items():
                if float(k) > start + 1e-6:
                    new[str(round(float(k) - start, 3))] = v
            bone[ch] = new
    return out


def main():
    with open(SRC) as f:
        data = json.load(f)
    anims = data['animations']
    for name, (orig, start) in CUTS.items():
        anims[name] = cut(anims[orig], start)
    anims.update(json.loads(json.dumps(EXTRA)))
    with open(DST, 'w') as f:
        json.dump(data, f, separators=(',', ':'))
    print('ok:', DST, len(anims), 'animações')


if __name__ == '__main__':
    main()
