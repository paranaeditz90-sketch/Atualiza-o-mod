#!/usr/bin/env python3
"""video-motion-analysis, lado da animação: pontos-chave anotados nos quadros da referência ->
ângulos 2D -> pose no esqueleto real do FroggyDude (froggydude-mod/tools/anim/rig.py) ->
plano de keyframes GeckoLib -> comparação com a referência -> 2ª rodada de correção.

Subcomandos:
  angles  ANN.json [--csv F]                      ângulo 2D de cada segmento visível (observação, graus)
  plan    ANN.json --out plan.json [--emit F --name N] [--init FILE:ANIM]
                                                  ajusta uma pose por quadro anotado; só os ossos com
                                                  segmento visível ganham keyframe (nada inventado)
  compare ANN.json --anim FILE:ANIM [--out D]     erro por segmento e por quadro (graus) + folha lado a lado
  correct ANN.json --anim FILE:ANIM --out D [--tol 4]
                                                  2ª rodada: onde o erro passa de tol, reajusta partindo da
                                                  pose atual, insere o keyframe e compara antes/depois
  grid    IMG... --out D [--crop x0,y0,x1,y1] [--zoom 2]
                                                  cópia com grade numerada pra ler coordenadas ao anotar

Anotação (ver annotation_template.json do vma.py): camera {az, elev, mirror_x}, anim_t0 (tempo da
referência que vira t=0 da animação), frames [{t, image, keypoints {nome: [x, y]}, hidden [...]}].
Pontos: hip, neck, head_top, shoulder_r/l, hand_r/l, knee_r/l, foot_r/l ("_r" = direita DELE).
Ângulos são no plano da imagem (0° = direita, 90° = cima): não dependem de escala nem de onde ele
está no quadro. A pose 3D que sai do ajuste é ESTIMATIVA (uma vista só); o resíduo diz o quanto confiar.
"""
from __future__ import annotations

import argparse
import copy
import csv
import json
import math
import os
import sys
import tempfile
import time
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

try:
    FONT = ImageFont.load_default(size=13)  # Pillow >= 10.1
except TypeError:
    FONT = ImageFont.load_default()
REPO = Path(__file__).resolve().parents[4]
RIG_DIR = Path(os.environ.get('VMA_RIG_DIR', REPO / 'froggydude-mod' / 'tools' / 'anim'))


def outside_repo(path, allow=False) -> Path:
    """O repo é público: folhas com quadros da referência não podem cair nele por acidente."""
    p = Path(path).resolve()
    if not allow and (p == REPO or REPO in p.parents):
        raise SystemExit(f'{p} fica dentro do repo (público). Use uma pasta no scratchpad '
                         f'(ou --allow-repo se for de propósito).')
    return p
sys.path.insert(0, str(RIG_DIR))
import preview  # noqa: E402  (esqueleto e renderizador do próprio repo)
import rig  # noqa: E402

SEGMENTS = {
    'torso': ('hip', 'neck'), 'head': ('neck', 'head_top'),
    'arm_r': ('shoulder_r', 'hand_r'), 'arm_l': ('shoulder_l', 'hand_l'),
    'thigh_r': ('hip', 'knee_r'), 'shin_r': ('knee_r', 'foot_r'),
    'thigh_l': ('hip', 'knee_l'), 'shin_l': ('knee_l', 'foot_l'),
}
BONES_FOR = {
    'torso': ['root', 'waist'], 'head': ['head'], 'arm_r': ['right_arm'], 'arm_l': ['left_arm'],
    'thigh_r': ['root', 'right_leg'], 'shin_r': ['right_shin'],
    'thigh_l': ['root', 'left_leg'], 'shin_l': ['left_shin'],
}
STEPS = (45.0, 20.0, 10.0, 5.0, 2.0, 1.0, 0.5)


# ----------------------------------------------------------------------------- geometria

def wrap(a: float) -> float:
    return (a + 180.0) % 360.0 - 180.0


def angle(a, b) -> float:
    """Ângulo do segmento a->b na imagem (y pra baixo), em graus, 0 = direita, 90 = cima."""
    return math.degrees(math.atan2(-(b[1] - a[1]), b[0] - a[0]))


class CalibratedCamera(preview.Camera):
    """Câmera em perspectiva calibrada contra o vídeo (como a do ajuste antigo da contorção):
    projeta no quadro inteiro do vídeo (size), com rolagem. Campos em blocos, no espaço dele:
    az, dist, height (altura da câmera), ty/tx (alvo), fov (vertical), roll (graus)."""

    def __init__(self, az=0.0, dist=5.0, height=1.0, tx=0.0, ty=1.0, fov=47.0, roll=0.0, size=(720, 1280)):
        a = math.radians(az)
        self.pos = np.array([dist * math.cos(a), height, dist * math.sin(a)])
        tgt = np.array([0.0, ty, 0.0]) + tx * np.array([-math.sin(a), 0, math.cos(a)])
        f = tgt - self.pos
        f /= np.linalg.norm(f)
        r = np.cross(f, [0, 1, 0])
        r /= np.linalg.norm(r)
        u = np.cross(r, f)
        cr, sr = math.cos(math.radians(roll)), math.sin(math.radians(roll))
        self.fwd, self.right, self.up = f, r * cr + u * sr, -r * sr + u * cr
        self.size = tuple(size)
        self.fpx = (self.size[1] / 2) / math.tan(math.radians(fov) / 2)


def make_camera(cam: dict, size=(360, 360)):
    """Com 'dist' na anotação: câmera calibrada no quadro do vídeo (pontos no mesmo espaço de pixel).
    Sem: câmera quase ortográfica (longe e fechada), onde o ângulo 2D não depende da distância."""
    if 'dist' in cam:
        keys = ('az', 'dist', 'height', 'tx', 'ty', 'fov', 'roll')
        return CalibratedCamera(**{k: float(cam[k]) for k in keys if k in cam},
                                size=tuple(cam.get('size', (720, 1280))))
    az = float(cam.get('az') or 0.0)
    elev = float(cam.get('elev') or 0.0)
    dist, look_y = 60.0, float(cam.get('look_y', 0.9))
    eye = look_y + dist * math.tan(math.radians(elev))
    return preview.Camera(dist=dist, eye=eye, az=az, fov=5.0, look_y=look_y, size=size)


def rig_points(pose: dict) -> dict:
    fr = rig.frames(pose)
    _, o_body = fr['body']
    r_head, o_head = fr['head']
    pts = {'hip': o_body, 'neck': o_head, 'head_top': o_head + r_head @ np.array([0.0, 8.0, 0.0])}
    for side, arm, shin in (('r', 'right_arm', 'right_shin'), ('l', 'left_arm', 'left_shin')):
        pts[f'shoulder_{side}'] = fr[arm][1]
        pts[f'hand_{side}'] = rig.tip(fr, arm)
        pts[f'knee_{side}'] = fr[shin][1]
        pts[f'foot_{side}'] = rig.tip(fr, shin)
    return pts


def project(points: dict, cam) -> dict:
    return {k: cam.project(v * rig.PX)[:2] for k, v in points.items()}


def rig_angles(pose, cam) -> dict:
    p = project(rig_points(pose), cam)
    return {s: angle(p[a], p[b]) for s, (a, b) in SEGMENTS.items()}


def ref_angles(frame: dict, mirror_x=False) -> dict:
    """Ângulos dos segmentos com os dois pontos anotados e nenhum escondido."""
    kp = frame.get('keypoints', {})
    hidden = set(frame.get('hidden', []))
    out = {}
    for s, (a, b) in SEGMENTS.items():
        if a in kp and b in kp and a not in hidden and b not in hidden:
            pa, pb = list(kp[a]), list(kp[b])
            if mirror_x:
                pa[0], pb[0] = -pa[0], -pb[0]
            out[s] = angle(pa, pb)
    return out


# ----------------------------------------------------------------------------- animação

def load_anim(spec: str) -> dict:
    """FILE:ANIM (arquivo .animation.json do GeckoLib + nome da animação)."""
    path, _, name = spec.rpartition(':')
    data = json.loads(Path(path).read_text())
    anims = data.get('animations', data)
    if name not in anims:
        raise SystemExit(f'{name} não está em {path} (tem: {", ".join(sorted(anims)[:12])}...)')
    return anims[name]


def sample(anim: dict, t: float) -> dict:
    """Mesma interpolação linear do build_anims.sample (o GeckoLib usa linear por padrão)."""
    out = {}
    for bone, chans in anim.get('bones', {}).items():
        out[bone] = {}
        for ch, kf in chans.items():
            if isinstance(kf, list):  # valor fixo
                out[bone][ch] = [float(v) for v in kf]
                continue
            keys = sorted(kf, key=float)
            ts = [float(k) for k in keys]
            vals = [kf[k]['vector'] if isinstance(kf[k], dict) else kf[k] for k in keys]
            if t <= ts[0]:
                v = vals[0]
            elif t >= ts[-1]:
                v = vals[-1]
            else:
                i = max(j for j in range(len(ts) - 1) if ts[j] <= t)
                a = (t - ts[i]) / (ts[i + 1] - ts[i])
                v = [vals[i][j] + (vals[i + 1][j] - vals[i][j]) * a for j in range(3)]
            out[bone][ch] = [float(x) for x in v]
    return out


def get_rot(pose, bone):
    return list(pose.get(bone, {}).get('rotation', [0.0, 0.0, 0.0]))


def set_rot(pose, bone, vals):
    pose.setdefault(bone, {})['rotation'] = [round(float(v), 2) for v in vals]


# ----------------------------------------------------------------------------- ajuste

def dofs_for(cam: dict, visible_segments, override=None):
    """Graus de liberdade: só ossos de segmentos VISÍVEIS; eixos pela vista
    (de lado: X; de frente/costas: Z; 3/4: X e Z)."""
    if override:
        return [(b, ax) for b, axes in override.items() for ax in axes]
    az = abs(wrap(float(cam.get('az') or 0.0)))
    axes = [0] if 60 <= az <= 120 else ([2] if az <= 30 or az >= 150 else [0, 2])
    bones = []
    for s in visible_segments:
        for b in BONES_FOR[s]:
            if b not in bones:
                bones.append(b)
    return [(b, ax) for b in bones for ax in axes]


def cost(pose, ref, cam, init, lam):
    got = rig_angles(pose, cam)
    err = sum(wrap(got[s] - ref[s]) ** 2 for s in ref)
    reg = sum((get_rot(pose, b)[ax] - get_rot(init, b)[ax]) ** 2
              for b in init for ax in range(3)) if lam else 0.0
    return err + lam * reg


def fit(init: dict, ref: dict, cam, dofs, lam=0.002, steps=STEPS):
    """Descida por coordenadas: tenta ±passo em cada grau de liberdade, aceita o que melhora."""
    pose = copy.deepcopy(init)
    for b, _ in dofs:
        set_rot(pose, b, get_rot(pose, b))
        init.setdefault(b, {}).setdefault('rotation', get_rot(init, b))
    best = cost(pose, ref, cam, init, lam)
    evals = 1
    for step in steps:
        improved = True
        while improved:
            improved = False
            for b, ax in dofs:
                for sgn in (1, -1):
                    trial = copy.deepcopy(pose)
                    r = get_rot(trial, b)
                    r[ax] = max(-180.0, min(180.0, r[ax] + sgn * step))
                    set_rot(trial, b, r)
                    c = cost(trial, ref, cam, init, lam)
                    evals += 1
                    if c < best - 1e-9:
                        pose, best, improved = trial, c, True
    got = rig_angles(pose, cam)
    residual = {s: round(wrap(got[s] - ref[s]), 2) for s in ref}
    return pose, residual, evals


# ----------------------------------------------------------------------------- comandos

def annotated(ann):
    """Quadros com pontos anotados, menos os marcados {"exclude": "motivo"} (ex.: dentro da
    transição do controlador, que mistura a animação anterior e não é modelada aqui)."""
    return [f for f in ann.get('frames', []) if f.get('keypoints') and not f.get('exclude')]


def anim_t0(ann):
    frames = annotated(ann)
    return float(ann['anim_t0']) if ann.get('anim_t0') is not None else float(frames[0]['t'])


def anim_time(ann, t_ref):
    """Tempo da animação para um tempo do vídeo: (t_ref - anim_t0) * time_scale, ou o 't_anim'
    do próprio quadro se o --warp já definiu. time_scale < 1 = referência em câmera lenta."""
    for f in ann.get('frames', []):
        if f.get('t') == t_ref and f.get('t_anim') is not None:
            return float(f['t_anim'])
    return (t_ref - anim_t0(ann)) * float(ann.get('time_scale', 1.0))


def _ease(kind, x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x) if kind == 'smoothstep' else x


def code_offsets(ann, t_anim):
    """{(osso, eixo): graus} que o CÓDIGO soma por fora do .animation.json nesse instante
    (ex.: o giro do mortal do BecomeLM é um PoseStack.mulPose no Java). ann['code_rotations']:
    [{bone, axis (0=X,1=Y,2=Z), t0, t1, from, to, ease: linear|smoothstep}] em tempo da animação."""
    out = {}
    for cr in ann.get('code_rotations', []):
        span = max(float(cr['t1']) - float(cr['t0']), 1e-6)
        k = _ease(cr.get('ease', 'linear'), (t_anim - float(cr['t0'])) / span)
        key = (cr['bone'], int(cr['axis']))
        out[key] = out.get(key, 0.0) + float(cr['from']) + (float(cr['to']) - float(cr['from'])) * k
    return out


def add_offsets(pose, offsets, sign=1.0):
    for (bone, axis), deg in offsets.items():
        rot = get_rot(pose, bone)
        rot[axis] += sign * deg
        pose.setdefault(bone, {})['rotation'] = rot
    return pose


def pose_at(ann, anim, t_anim):
    """Pose que aparece na tela: a da animação + as rotações do código."""
    return add_offsets(sample(anim, t_anim), code_offsets(ann, t_anim))


def cmd_angles(ann, args):
    mirror = bool(ann.get('camera', {}).get('mirror_x'))
    rows = []
    for f in annotated(ann):
        ang = ref_angles(f, mirror)
        for s in SEGMENTS:
            rows.append({'t': f['t'], 'segment': s, 'visible': s in ang,
                         'angle_deg': round(ang[s], 2) if s in ang else ''})
    if args.csv:
        with open(args.csv, 'w', newline='') as fh:
            wr = csv.DictWriter(fh, fieldnames=['t', 'segment', 'visible', 'angle_deg'])
            wr.writeheader()
            wr.writerows(rows)
    return {'rows': rows}


def build_animation(keyframes, length, loop):
    bones = {}
    for kf in keyframes:
        for b in kf['fitted_bones']:
            bones.setdefault(b, {'rotation': {}})['rotation'][f'{kf["t"]:.4f}'] = kf['pose'][b]['rotation']
    return {'animation_length': round(length, 4), 'loop': loop, 'bones': bones}


def cmd_plan(ann, args):
    cam_cfg = ann.get('camera', {})
    cam = make_camera(cam_cfg)
    mirror = bool(cam_cfg.get('mirror_x'))
    t0 = anim_t0(ann)
    base = load_anim(args.init) if args.init else None
    keyframes, evals, skipped = [], 0, []
    for f in annotated(ann):
        ref = ref_angles(f, mirror)
        if not ref:
            continue
        t = round(anim_time(ann, f['t']), 4)
        if t < 0:  # antes do anim_t0: o GeckoLib não toca tempo negativo
            skipped.append({'t_ref': f['t'], 'reason': f'antes do anim_t0 ({t:+.3f} s)'})
            continue
        offs = code_offsets(ann, t)
        init = add_offsets(sample(base, t) if base else {}, offs)
        dofs = dofs_for(cam_cfg, ref, ann.get('dofs'))
        pose, residual, n = fit(init, ref, cam, dofs)
        pose = add_offsets(pose, offs, -1.0)  # o keyframe não leva o giro que o código já faz
        evals += n
        fitted = sorted({b for b, _ in dofs})
        keyframes.append({'t': t, 't_ref': f['t'], 'pose': {b: {'rotation': get_rot(pose, b)} for b in fitted},
                          'fitted_bones': fitted, 'visible_segments': sorted(ref),
                          'no_evidence_bones': sorted({b for s in SEGMENTS if s not in ref for b in BONES_FOR[s]}
                                                      - set(fitted)),
                          'residual_deg': residual,
                          'kind': 'estimate', 'evidence': [f['t']]})
    length = max((k['t'] for k in keyframes), default=0.0)
    plan = {'source': args.ann, 'camera': cam_cfg, 'anim_t0': t0, 'keyframes': keyframes, 'skipped': skipped,
            'notes': 'Rotações = ESTIMATIVA a partir de uma vista 2D. Ossos sem segmento visível não '
                     'ganham keyframe (seguem a animação/pose base). Raiz x cintura e perna x raiz '
                     'se compensam numa vista só: confira na prévia antes de usar.',
            'fit_evaluations': evals}
    plan['animation'] = build_animation(keyframes, length, bool(args.loop))
    outside_repo(args.out, args.allow_repo).write_text(json.dumps(plan, indent=1))
    if args.emit:
        outside_repo(args.emit, args.allow_repo).write_text(json.dumps({'format_version': '1.8.0',
                                               'animations': {args.name: plan['animation']}}, indent=1))
    return {'keyframes': len(keyframes), 'skipped': len(skipped), 'fit_evaluations': evals,
            'max_residual_deg': max((abs(v) for k in keyframes for v in k['residual_deg'].values()), default=0.0)}


def compare_anim(ann, anim, cam_cfg):
    cam = make_camera(cam_cfg)
    mirror = bool(cam_cfg.get('mirror_x'))
    frames, all_err = [], []
    for f in annotated(ann):
        ref = ref_angles(f, mirror)
        if not ref:
            continue
        t = anim_time(ann, f['t'])
        got = rig_angles(pose_at(ann, anim, t), cam)
        diff = {s: round(wrap(got[s] - ref[s]), 2) for s in ref}
        errs = [abs(v) for v in diff.values()]
        all_err += errs
        frames.append({'t_ref': f['t'], 't_anim': round(t, 4), 'diff_deg': diff,
                       'mean_abs_deg': round(sum(errs) / len(errs), 2),
                       'worst': max(diff, key=lambda s: abs(diff[s]))})
    return {'frames': frames, 'mean_abs_deg': round(sum(all_err) / max(len(all_err), 1), 2),
            'max_abs_deg': round(max(all_err, default=0.0), 2), 'segments_compared': len(all_err)}


def draw_skeleton(draw, pts, color, width=3):
    for a, b in SEGMENTS.values():
        if a in pts and b in pts:
            draw.line([tuple(pts[a]), tuple(pts[b])], fill=color, width=width)
    for p in pts.values():
        draw.ellipse([p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3], fill=color)


def align(ann, anim, offsets=None, scales=None):
    """Procura o deslocamento de anim_t0 e a escala de tempo que dão o menor erro médio
    (câmera lenta, atraso de rede, transição do controlador). Devolve (anotação alinhada, busca)."""
    base_t0 = anim_t0(ann)
    offsets = offsets if offsets is not None else [i / 100 for i in range(-30, 31)]
    scales = scales if scales is not None else ([float(ann['time_scale'])] if 'time_scale' in ann
                                                else [s / 20 for s in range(4, 31)])
    best = None
    for sc in scales:
        for off in offsets:
            trial = dict(ann, anim_t0=base_t0 + off, time_scale=sc)
            err = compare_anim(trial, anim, ann.get('camera', {}))['mean_abs_deg']
            if best is None or err < best[0]:
                best = (err, off, sc)
    aligned = dict(ann, anim_t0=round(base_t0 + best[1], 4), time_scale=best[2])
    return aligned, {'mean_abs_deg': best[0], 'anim_t0': aligned['anim_t0'], 'time_scale': best[2],
                     'searched': len(offsets) * len(scales)}


def warp(ann, anim, step=0.005, slack=0.1):
    """Tempo da animação POR QUADRO, sempre crescente (programação dinâmica), pra referência com
    tempo irregular (quadro perdido, jogo engasgando, gravação lenta desigual). O resto do erro
    depois disso é diferença de POSE. Os tempos achados são estimativa: confira se andam pra frente
    num ritmo plausível (salto enorme entre dois quadros = sinal de anotação errada)."""
    cam_cfg = ann.get('camera', {})
    cam = make_camera(cam_cfg)
    mirror = bool(cam_cfg.get('mirror_x'))
    frames = sorted(annotated(ann), key=lambda f: f['t'])
    length = float(anim.get('animation_length', 1.0))
    grid = [round(-slack + i * step, 4) for i in range(int((length + 2 * slack) / step) + 1)]
    grid_angles = [rig_angles(pose_at(ann, anim, t), cam) for t in grid]  # não depende do quadro
    cost = []
    for f in frames:
        ref = ref_angles(f, mirror)
        cost.append([sum(abs(wrap(got[s] - ref[s])) for s in ref) / max(len(ref), 1) for got in grid_angles])
    n, m = len(frames), len(grid)
    acc = [row[:] for row in cost]
    back = [[0] * m for _ in range(n)]
    for i in range(1, n):
        best_j, best_v = 0, acc[i - 1][0]
        for j in range(m):  # mínimo acumulado até j (não decrescente)
            if acc[i - 1][j] < best_v:
                best_j, best_v = j, acc[i - 1][j]
            acc[i][j] = cost[i][j] + best_v
            back[i][j] = best_j
    j = min(range(m), key=lambda k: acc[n - 1][k])
    path = [0] * n
    for i in range(n - 1, -1, -1):
        path[i] = j
        j = back[i][j]
    warped = copy.deepcopy(ann)
    by_t = {f['t']: grid[path[i]] for i, f in enumerate(frames)}
    for f in warped['frames']:
        if f.get('t') in by_t:
            f['t_anim'] = by_t[f['t']]
            f['t_anim_kind'] = 'estimate (warp)'
    return warped, {'mean_abs_deg': round(acc[n - 1][path[-1]] / max(n, 1), 2),
                    't_anim': [by_t[f['t']] for f in frames], 'grid_step': step}


def compare_sheet(ann, anim, cam_cfg, result, path, h=300):
    cam = make_camera(cam_cfg, size=(h, h))
    rows = []
    by_t = {fr['t_ref']: fr for fr in result['frames']}
    for f in annotated(ann):
        if f['t'] not in by_t:
            continue
        hidden = set(f.get('hidden', []))
        kp = {k: v for k, v in f['keypoints'].items() if k not in hidden}
        if f.get('image') and Path(f['image']).is_file():
            ref_im = Image.open(f['image']).convert('RGB')
        else:  # sem imagem: só o esqueleto anotado num fundo neutro
            xs = [p[0] for p in kp.values()] or [0]
            ys = [p[1] for p in kp.values()] or [0]
            ref_im = Image.new('RGB', (int(max(xs) + 40), int(max(ys) + 40)), (40, 44, 52))
        scale = h / ref_im.height
        ref_im = ref_im.resize((max(1, int(ref_im.width * scale)), h))
        d = ImageDraw.Draw(ref_im)
        draw_skeleton(d, {k: (v[0] * scale, v[1] * scale) for k, v in kp.items()}, (40, 255, 90))
        pose = pose_at(ann, anim, anim_time(ann, f['t']))
        rig_im = preview.render(pose, cam, bg=False)
        pts = project(rig_points(pose), cam)
        # recorte quadrado em volta do corpo (o modelo deitado fica pequeno e fora do centro)
        xs, ys = [p[0] for p in pts.values()], [p[1] for p in pts.values()]
        cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
        side = max(max(xs) - min(xs), max(ys) - min(ys), 40) * 1.5
        box = (cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        rig_im = rig_im.crop(tuple(int(v) for v in box)).resize((h, h))
        k = h / side
        draw_skeleton(ImageDraw.Draw(rig_im), {n: ((p[0] - box[0]) * k, (p[1] - box[1]) * k) for n, p in pts.items()},
                      (255, 60, 60))
        fr = by_t[f['t']]
        row = Image.new('RGB', (ref_im.width + rig_im.width + 6, h + 22), (16, 16, 16))
        row.paste(ref_im, (0, 22))
        row.paste(rig_im, (ref_im.width + 6, 22))
        # sem acento: a fonte embutida do Pillow não tem "é"/"ç"
        ImageDraw.Draw(row).text((4, 3), f't={f["t"]:.3f}  anim={fr["t_anim"]:.3f}  erro medio {fr["mean_abs_deg"]} graus  '
                                          f'pior: {fr["worst"]} {fr["diff_deg"][fr["worst"]]}   '
                                          f'(verde = referencia, vermelho = animacao)', fill=(255, 225, 0), font=FONT)
        rows.append(row)
    if not rows:
        return None
    w = max(r.width for r in rows)
    sheet = Image.new('RGB', (w, sum(r.height + 4 for r in rows)), (16, 16, 16))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.height + 4
    sheet.save(path, quality=88)
    return str(path)


def cmd_compare(ann, args):
    anim = load_anim(args.anim)
    out = outside_repo(args.out, args.allow_repo) if args.out else Path(tempfile.mkdtemp(prefix='rigfit_cmp_'))
    out.mkdir(parents=True, exist_ok=True)
    search = None
    if args.align:
        ann, search = align(ann, anim)
    if args.warp:
        ann, search = warp(ann, anim)
    if search:
        (out / 'aligned_annotation.json').write_text(json.dumps(ann, indent=1))
    res = compare_anim(ann, anim, ann.get('camera', {}))
    res['alignment'] = search
    res['sheet'] = compare_sheet(ann, anim, ann.get('camera', {}), res, out / 'compare.jpg')
    (out / 'compare.json').write_text(json.dumps(res, indent=1))
    return {k: res[k] for k in ('mean_abs_deg', 'max_abs_deg', 'segments_compared', 'alignment', 'sheet')}


def correct(ann, anim, tol=4.0):
    """2ª rodada: refaz só onde a 1ª comparação passou de tol, partindo da pose atual."""
    cam_cfg = ann.get('camera', {})
    cam = make_camera(cam_cfg)
    mirror = bool(cam_cfg.get('mirror_x'))
    before = compare_anim(ann, anim, cam_cfg)
    new = copy.deepcopy(anim)
    length = float(new.get('animation_length', 0.0))
    changed = []
    for fr in before['frames']:
        if fr['mean_abs_deg'] <= tol:
            continue
        f = next(x for x in annotated(ann) if x['t'] == fr['t_ref'])
        ref = ref_angles(f, mirror)
        # o --warp procura um pouco antes/depois da animação; keyframe fica dentro dela
        t = min(max(fr['t_anim'], 0.0), length) if length else max(fr['t_anim'], 0.0)
        offs = code_offsets(ann, t)
        init = add_offsets(sample(new, t), offs)
        dofs = dofs_for(cam_cfg, ref, ann.get('dofs'))
        pose, residual, _ = fit(init, ref, cam, dofs)
        pose = add_offsets(pose, offs, -1.0)
        init = add_offsets(init, offs, -1.0)
        for b in sorted({b for b, _ in dofs}):
            chans = new.setdefault('bones', {}).setdefault(b, {})
            if 'rotation' not in chans or isinstance(chans['rotation'], list):
                base = get_rot(init, b)
                chans['rotation'] = {'0.0': base, f'{length:.4f}': base}
            rot = chans['rotation']
            for key in [k for k in rot if abs(float(k) - t) < 1e-4]:  # "0.3" e "0.3000" são o mesmo instante
                del rot[key]
            rot[f'{t:.4f}'] = get_rot(pose, b)
        changed.append({'t_anim': t, 't_ref': fr['t_ref'], 'before_deg': fr['mean_abs_deg'], 'residual_deg': residual})
    after = compare_anim(ann, new, cam_cfg)
    return new, before, after, changed


def cmd_correct(ann, args):
    anim = load_anim(args.anim)
    search = None
    if args.align:  # alinha o tempo ANTES: senão a "correção" vira compensação de atraso
        ann, search = align(ann, anim)
    if args.warp:
        ann, search = warp(ann, anim)
    new, before, after, changed = correct(ann, anim, args.tol)
    out = outside_repo(args.out, args.allow_repo)
    out.mkdir(parents=True, exist_ok=True)
    name = args.anim.rpartition(':')[2]
    (out / 'corrected.animation.json').write_text(json.dumps({'format_version': '1.8.0', 'animations': {name: new}}, indent=1))
    report = {'alignment': search, 'round1': before, 'round2': after, 'keyframes_changed': changed,
              'tol_deg': args.tol}
    report['sheet_round1'] = compare_sheet(ann, anim, ann.get('camera', {}), before, out / 'round1.jpg')
    report['sheet_round2'] = compare_sheet(ann, new, ann.get('camera', {}), after, out / 'round2.jpg')
    (out / 'correct.json').write_text(json.dumps(report, indent=1))
    return {'round1_mean_deg': before['mean_abs_deg'], 'round2_mean_deg': after['mean_abs_deg'],
            'round1_max_deg': before['max_abs_deg'], 'round2_max_deg': after['max_abs_deg'],
            'keyframes_changed': len(changed)}


def grid_images(paths, out, step=20, crop=None, zoom=2):
    """Cópia de cada quadro com grade numerada (pra ler coordenadas de pixel ao anotar).
    crop=x0,y0,x1,y1 (pixels do quadro) e zoom ampliam a região; os números da grade são sempre
    as coordenadas do quadro ORIGINAL, que é o que vai na anotação."""
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    made = []
    for p in paths:
        im = Image.open(p).convert('RGB')
        x0, y0, x1, y1 = crop or (0, 0, im.width, im.height)
        im = im.crop((x0, y0, x1, y1)).resize(((x1 - x0) * zoom, (y1 - y0) * zoom), Image.NEAREST)
        d = ImageDraw.Draw(im)
        for gx in range((x0 // step + 1) * step, x1, step):
            major = gx % (step * 5) == 0
            X = (gx - x0) * zoom
            d.line([(X, 0), (X, im.height)], fill=(255, 255, 0) if major else (255, 255, 255), width=1)
            if major:
                d.text((X + 2, 2), str(gx), fill=(255, 255, 0), font=FONT)
        for gy in range((y0 // step + 1) * step, y1, step):
            major = gy % (step * 5) == 0
            Y = (gy - y0) * zoom
            d.line([(0, Y), (im.width, Y)], fill=(255, 255, 0) if major else (255, 255, 255), width=1)
            if major:
                d.text((2, Y + 2), str(gy), fill=(255, 255, 0), font=FONT)
        target = out / (Path(p).stem + '_grid.png')
        im.save(target)
        made.append(str(target))
    return made


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]
    if argv and argv[0] == 'grid':  # rigfit.py grid IMG... --out D [--step 20] [--crop x0,y0,x1,y1] [--zoom 2]
        gp = argparse.ArgumentParser(prog='rigfit.py grid')
        gp.add_argument('images', nargs='+')
        gp.add_argument('--out', required=True)
        gp.add_argument('--step', type=int, default=20)
        gp.add_argument('--crop')
        gp.add_argument('--zoom', type=int, default=2)
        gp.add_argument('--allow-repo', action='store_true')
        ga = gp.parse_args(argv[1:])
        crop = tuple(int(v) for v in ga.crop.split(',')) if ga.crop else None
        out = outside_repo(ga.out, ga.allow_repo)
        print(json.dumps(grid_images(ga.images, out, ga.step, crop, ga.zoom)))
        return 0
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('command', choices=['angles', 'plan', 'compare', 'correct'])
    ap.add_argument('ann', help='anotação JSON (pontos-chave por quadro)')
    ap.add_argument('--csv')
    ap.add_argument('--out')
    ap.add_argument('--emit', help='plan: grava também um .animation.json do GeckoLib')
    ap.add_argument('--name', default='vma_plan')
    ap.add_argument('--loop', action='store_true')
    ap.add_argument('--init', help='plan: FILE:ANIM como pose inicial (senão, pose neutra)')
    ap.add_argument('--anim', help='compare/correct: FILE:ANIM')
    ap.add_argument('--tol', type=float, default=4.0)
    ap.add_argument('--align', action='store_true',
                    help='compare/correct: procura deslocamento de anim_t0 (±0,3 s) e escala de tempo')
    ap.add_argument('--warp', action='store_true',
                    help='compare/correct: tempo da animação por quadro (crescente), pra tempo irregular')
    ap.add_argument('--allow-repo', action='store_true', help='permite gravar saída dentro do repo')
    args = ap.parse_args(argv)
    ann = json.loads(Path(args.ann).read_text())
    start = time.perf_counter()
    if args.command == 'plan' and not args.out:
        ap.error('plan precisa de --out')
    if args.command in ('compare', 'correct') and not args.anim:
        ap.error(f'{args.command} precisa de --anim FILE:ANIM')
    if args.command == 'correct' and not args.out:
        ap.error('correct precisa de --out')
    result = {'angles': cmd_angles, 'plan': cmd_plan, 'compare': cmd_compare, 'correct': cmd_correct}[args.command](ann, args)
    result = {k: v for k, v in result.items() if k != 'rows'}
    result['seconds'] = round(time.perf_counter() - start, 3)
    result['frames_annotated'] = len(annotated(ann))
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())
