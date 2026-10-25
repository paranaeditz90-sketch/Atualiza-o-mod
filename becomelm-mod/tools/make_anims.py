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
