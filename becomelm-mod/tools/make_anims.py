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
    with open(DST, 'w') as f:
        json.dump(data, f, separators=(',', ':'))
    print('ok:', DST, len(anims), 'animações')


if __name__ == '__main__':
    main()
