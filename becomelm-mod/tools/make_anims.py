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
    # errou a queda do céu e caiu de cara no chão, em X (vídeo do LM: o AJ rola pra fora e o
    # FroggyDude se estatela de bruços, braços e pernas abertos, ~1 s parado, areia voando)
    'faceplant': {
        'loop': 'hold_on_last_frame',
        'animation_length': 1.7,
        'bones': {
            'root': {
                'rotation': _k([(0.0, [0, 0, 0]), (0.04, [45, 0, 0]), (0.08, [90, 0, 0]), (0.12, [86, 0, 0]),
                                (0.2, [90, 0, 0]), (1.2, [90, 0, 0]), (1.36, [62, 0, 0]), (1.52, [22, 0, 0]),
                                (1.7, [0, 0, 0])]),
                'position': _k([(0.0, [0, 0, 0]), (0.08, [0, 2, 0]), (0.12, [0, 3, 0]), (0.2, [0, 2, 0]),
                                (1.2, [0, 2, 0]), (1.36, [0, 3, 0]), (1.7, [0, 0, 0])]),
            },
            'waist': {'rotation': _k([(0.0, [20, 0, 0]), (0.08, [0, 0, 0]), (1.2, [0, 0, 0]),
                                      (1.36, [30, 0, 0]), (1.7, [0, 0, 0])])},
            'head': {'rotation': _k([(0.0, [30, 0, 0]), (0.08, [-12, 0, 0]), (0.14, [-4, 0, 0]),
                                     (0.6, [-8, 14, 0]), (1.2, [-8, 14, 0]), (1.45, [-30, 0, 0]), (1.7, [0, 0, 0])])},
            'right_arm': {'rotation': _k([(0.0, [-170, 0, 10]), (0.08, [-160, 0, 42]), (0.14, [-164, 0, 46]),
                                          (0.7, [-160, 0, 40]), (0.85, [-158, 0, 34]), (1.2, [-160, 0, 40]),
                                          (1.3, [-90, 0, 12]), (1.45, [-60, 0, 8]), (1.7, [0, 0, 0])])},
            'left_arm': {'rotation': _k([(0.0, [-170, 0, -10]), (0.08, [-160, 0, -42]), (0.14, [-164, 0, -46]),
                                         (0.7, [-160, 0, -40]), (1.2, [-160, 0, -40]),
                                         (1.3, [-90, 0, -12]), (1.45, [-60, 0, -8]), (1.7, [0, 0, 0])])},
            'right_leg': {'rotation': _k([(0.0, [-30, 0, 10]), (0.08, [6, 0, 24]), (0.14, [10, 0, 26]),
                                          (1.2, [6, 0, 24]), (1.45, [-40, 0, 8]), (1.7, [0, 0, 0])])},
            'left_leg': {'rotation': _k([(0.0, [-30, 0, -10]), (0.08, [6, 0, -24]), (0.14, [10, 0, -26]),
                                         (0.9, [12, 0, -22]), (1.2, [6, 0, -24]), (1.45, [-20, 0, -8]),
                                         (1.7, [0, 0, 0])])},
        },
    },
    # a queda do céu (10v1 0:13.4-16.1): ajoelhado em cima da vítima de costas, um soco a cada
    # 0,25 s alternando o punho, e o tronco inteiro mergulha até a cara dela a cada golpe
    'sky_smash': {
        'loop': True,
        'animation_length': 0.5,
        'bones': {
            'root': {'position': _k([(0.0, [0, -4, 0]), (0.1, [0, -4.6, 0]), (0.25, [0, -4, 0]),
                                     (0.35, [0, -4.6, 0]), (0.5, [0, -4, 0])])},
            'waist': {'rotation': _k([(0.0, [-10, 0, 0]), (0.06, [30, -6, 0]), (0.1, [62, -10, 0]),
                                      (0.16, [45, -6, 0]), (0.25, [-10, 0, 0]), (0.31, [30, 6, 0]),
                                      (0.35, [62, 10, 0]), (0.41, [45, 6, 0]), (0.5, [-10, 0, 0])])},
            'head': {'rotation': _k([(0.0, [22, 0, 0]), (0.1, [2, 0, 0]), (0.16, [8, 0, 0]),
                                     (0.25, [22, 0, 0]), (0.35, [2, 0, 0]), (0.41, [8, 0, 0]),
                                     (0.5, [22, 0, 0])])},
            'right_arm': {'rotation': _k([(0.0, [-165, 0, 14]), (0.06, [-120, 0, 10]), (0.1, [-70, 0, 4]),
                                          (0.16, [-95, 0, 6]), (0.25, [-40, 0, 8]), (0.35, [-50, 0, 8]),
                                          (0.45, [-120, 0, 12]), (0.5, [-165, 0, 14])])},
            'left_arm': {'rotation': _k([(0.0, [-40, 0, -8]), (0.1, [-50, 0, -8]), (0.2, [-120, 0, -12]),
                                         (0.25, [-165, 0, -14]), (0.31, [-120, 0, -10]), (0.35, [-70, 0, -4]),
                                         (0.41, [-95, 0, -6]), (0.5, [-40, 0, -8])])},
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


_LIFT = dict(waist={'rotation': [-12, 0, 0]}, root={'position': [0, 6, 0]},
             right_arm={'rotation': [-70, 0, 40]}, left_arm={'rotation': [-70, 0, -40]})

EXTRA.update({
    'pile_drop': _anim(0.4, 'hold_on_last_frame', [
        (0.0, _stand()),
        (0.12, _prone(root={'rotation': [40, 0, 0], 'position': [0, 2, 0]}, head={'rotation': [-10, 0, 0]},
                      right_arm={'rotation': [-120, 0, 30]}, left_arm={'rotation': [-120, 0, -30]},
                      right_leg={'rotation': [0, 0, 4]}, left_leg={'rotation': [0, 0, -4]},
                      right_shin={'rotation': [0, 0, 0]}, left_shin={'rotation': [0, 0, 0]})),
        (0.25, _prone(root={'rotation': [92, 0, 0], 'position': [0, 5, 0]},
                      right_arm={'rotation': [-40, 0, 70]}, left_arm={'rotation': [-40, 0, -70]})),
        (0.4, _prone()),
    ]),
    'pile_lie': _anim(2.4, True, [
        (0.0, _prone()),
        (0.3, _prone(head={'rotation': [-18, 10, 26]}, waist={'rotation': [3, 0, 0]})),
        (0.6, _prone(head={'rotation': [-28, 0, 0]})),
        (0.9, _prone(head={'rotation': [-18, -10, -26]}, waist={'rotation': [3, 0, 0]})),
        (1.2, _prone()),
        (1.4, _prone(head={'rotation': [6, 0, 0]}, waist={'rotation': [5, 0, 0]})),
        (1.55, _prone(head={'rotation': [-22, 0, 0]})),
        (1.9, _prone(head={'rotation': [-62, 14, 8]}, **_LIFT)),
        (2.15, _prone(head={'rotation': [-58, -12, -6]}, **_LIFT)),
        (2.4, _prone()),
    ]),
    'pile_rise': _anim(0.5, 'hold_on_last_frame', [
        (0.0, _prone()),
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
