#!/usr/bin/env python3
"""video-motion-analysis, lado do vídeo: pipeline reprodutível com ffprobe/ffmpeg.

Subcomandos (todos gravam em --out e medem tempo e quadros analisados):
  probe    VIDEO                      metadados (fps real, VFR, rotação, quadros, áudio)
  overview VIDEO --out D [--n 24]     folha de contato barata (N quadros espalhados, com tempo)
  scan     VIDEO --out D              sinal de movimento em baixa resolução, cortes de cena,
                                      eventos de áudio e os trechos relevantes (intervals.json)
  extract  VIDEO --out D              extração adaptativa dos trechos (denso onde é rápido) com o
                                      tempo exato de cada quadro + folhas por trecho
  run      VIDEO --out D              tudo acima em sequência + report.json + modelo de anotação

Recorte no tempo: --start/--end (segundos) em overview/scan/extract/run.
Tudo que é medido vai em "observations"; o que é interpretação vai em "estimates" (com confiança).
Requisitos: ffmpeg/ffprobe, numpy, Pillow (pip install numpy pillow).
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import math
import os
import re
import subprocess
import sys
import tempfile
import time
from contextlib import contextmanager
from pathlib import Path

try:
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:  # pragma: no cover - depende do container
    sys.exit(f'falta {exc.name}: pip install numpy pillow')

FFMPEG = ['ffmpeg', '-hide_banner', '-nostdin', '-loglevel', 'error']
REPO = Path(__file__).resolve().parents[4]


def outside_repo(path, allow=False) -> Path:
    """O repo é público: quadro, áudio e transcrição de referência não podem cair nele por acidente."""
    p = Path(path).resolve()
    if not allow and (p == REPO or REPO in p.parents):
        raise SystemExit(f'{p} fica dentro do repo (público). Use uma pasta no scratchpad '
                         f'(ou --allow-repo se for de propósito e sem material do FroggyDude/LM).')
    return p


class Stats:
    """Tempo por etapa e quadros decodificados/analisados por etapa."""

    def __init__(self):
        self.start = time.perf_counter()
        self.seconds: dict[str, float] = {}
        self.frames: dict[str, int] = {}

    @contextmanager
    def stage(self, name):
        t = time.perf_counter()
        try:
            yield
        finally:
            self.seconds[name] = round(self.seconds.get(name, 0.0) + time.perf_counter() - t, 3)

    def count(self, name, n):
        self.frames[name] = self.frames.get(name, 0) + int(n)

    def as_dict(self):
        return {'seconds': self.seconds, 'total_seconds': round(time.perf_counter() - self.start, 3),
                'frames': self.frames, 'frames_total': sum(self.frames.values())}


# ----------------------------------------------------------------------------- probe

def _rate(text: str) -> float:
    num, _, den = (text or '0/1').partition('/')
    try:
        return float(num) / float(den or 1) if float(den or 1) else 0.0
    except ValueError:
        return 0.0


def probe(video) -> dict:
    out = subprocess.run(['ffprobe', '-v', 'error', '-print_format', 'json', '-show_format', '-show_streams',
                          str(video)], capture_output=True, text=True, check=True)
    data = json.loads(out.stdout)
    vs = next((s for s in data['streams'] if s.get('codec_type') == 'video'), None)
    aus = next((s for s in data['streams'] if s.get('codec_type') == 'audio'), None)
    if vs is None:
        raise SystemExit(f'{video}: sem faixa de vídeo')
    duration = float(data['format'].get('duration') or vs.get('duration') or 0.0)
    r, avg = _rate(vs.get('r_frame_rate')), _rate(vs.get('avg_frame_rate'))
    fps = avg or r
    rotation = 0
    for side in vs.get('side_data_list', []):
        if 'rotation' in side:
            rotation = int(side['rotation'])
    rotation = rotation or int(vs.get('tags', {}).get('rotate', 0) or 0)
    w, h = int(vs['width']), int(vs['height'])
    display = (h, w) if abs(rotation) % 180 == 90 else (w, h)
    nb = int(vs.get('nb_frames') or 0) or int(round(duration * fps))
    return {
        'file': str(video), 'duration_s': round(duration, 4), 'size_bytes': int(data['format'].get('size', 0)),
        'container': data['format'].get('format_name'),
        'video': {'codec': vs.get('codec_name'), 'width': w, 'height': h, 'display_size': list(display),
                  'rotation_deg': rotation, 'pix_fmt': vs.get('pix_fmt'), 'fps': round(fps, 4),
                  'r_frame_rate': vs.get('r_frame_rate'), 'avg_frame_rate': vs.get('avg_frame_rate'),
                  'vfr': bool(r and avg and abs(r - avg) > 0.01 * max(r, 1.0)), 'frames': nb,
                  'start_time': float(vs.get('start_time') or 0.0)},
        'audio': None if aus is None else {'codec': aus.get('codec_name'), 'sample_rate': int(aus.get('sample_rate', 0)),
                                           'channels': aus.get('channels')},
    }


# ----------------------------------------------------------------------------- imagens

def _font(size=14):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:  # Pillow antigo
        return ImageFont.load_default()


def fmt_t(t: float) -> str:
    m, s = divmod(t, 60)
    return f'{int(m)}:{s:06.3f}'


def grab(video, t: float, width: int, crop: str | None = None) -> Image.Image:
    """Um quadro no tempo t (busca exata: o primeiro quadro com pts >= t)."""
    vf = (f'crop={crop},' if crop else '') + f'scale={width}:-2'
    out = subprocess.run(FFMPEG + ['-ss', f'{t:.4f}', '-i', str(video), '-frames:v', '1', '-vf', vf,
                                   '-f', 'image2pipe', '-c:v', 'png', '-'], capture_output=True, check=True)
    return Image.open(io.BytesIO(out.stdout)).convert('RGB')


def make_sheet(images, labels, cols: int, pad: int = 3) -> Image.Image:
    w = max(im.width for im in images)
    h = max(im.height for im in images)
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * (w + pad) + pad, rows * (h + pad) + pad), (16, 16, 16))
    draw = ImageDraw.Draw(sheet)
    font = _font(max(11, w // 22))
    for k, (im, label) in enumerate(zip(images, labels)):
        x, y = pad + (k % cols) * (w + pad), pad + (k // cols) * (h + pad)
        sheet.paste(im, (x, y))
        box = draw.textbbox((x + 3, y + 2), label, font=font)
        draw.rectangle([box[0] - 2, box[1] - 1, box[2] + 2, box[3] + 1], fill=(0, 0, 0))
        draw.text((x + 3, y + 2), label, fill=(255, 225, 0), font=font)
    return sheet


def snap(t: float, fps: float) -> float:
    """Tempo no grid de quadros (vídeo de fps constante)."""
    return round(t * fps) / fps if fps else t


def overview(video, info, out: Path, stats: Stats, n=24, cols=6, width=320, start=None, end=None, crop=None):
    fps = info['video']['fps']
    a = start or 0.0
    b = end or info['duration_s']
    times = [min(b - 1.0 / max(fps, 1), snap(a + (i + 0.5) * (b - a) / n, fps)) for i in range(n)]
    with stats.stage('overview'):
        images = [grab(video, t, width, crop) for t in times]
        stats.count('overview', len(images))
        path = out / 'overview.jpg'
        make_sheet(images, [fmt_t(t) for t in times], cols).save(path, quality=88)
    return {'file': str(path), 'times': [round(t, 4) for t in times]}


# ----------------------------------------------------------------------------- scan

def _scan_fps(info, requested, span):
    """Trecho curto: fps nativo (até 60), senão movimento rápido parece corte. Vídeo longo: 10 fps."""
    native = info['video']['fps'] or 30.0
    if requested:
        return min(requested, native)
    return min(native, 60.0) if span <= 120 else min(native, 10.0)


def motion_signal(video, info, stats: Stats, fps=None, size=(96, 54), start=None, end=None, crop=None):
    """Decodifica UMA vez em cinza e baixa resolução; mede movimento e mudança de histograma."""
    fps = _scan_fps(info, fps, (end or info['duration_s']) - (start or 0.0))
    w, h = size
    cmd = list(FFMPEG)
    if start:
        cmd += ['-ss', f'{start:.4f}']
    cmd += ['-i', str(video)]
    if end:
        cmd += ['-t', f'{end - (start or 0.0):.4f}']
    vf = (f'crop={crop},' if crop else '') + f'fps={fps},scale={w}:{h}:flags=area,format=gray'
    cmd += ['-an', '-vf', vf, '-f', 'rawvideo', '-']
    rows = []
    errlog = tempfile.TemporaryFile()  # stderr em arquivo: vídeo quebrado gera muito erro e um pipe travaria
    with stats.stage('scan_video'):
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=errlog)
        prev = prev_hist = None
        i = 0
        nbytes = w * h
        while True:
            buf = proc.stdout.read(nbytes)
            if len(buf) < nbytes:
                break
            cur = np.frombuffer(buf, dtype=np.uint8).astype(np.int16)
            hist = np.bincount(cur >> 3, minlength=32) / cur.size
            if prev is None:
                motion = coverage = hdiff = 0.0
            else:
                diff = np.abs(cur - prev)
                motion = float(diff.mean() / 255.0)
                coverage = float((diff > 12).mean())
                hdiff = float(0.5 * np.abs(hist - prev_hist).sum())
            rows.append({'t': round((start or 0.0) + i / fps, 4), 'motion': round(motion, 5),
                         'coverage': round(coverage, 4), 'hist_diff': round(hdiff, 4)})
            prev, prev_hist = cur, hist
            i += 1
        proc.wait()
        stats.count('scan_video', i)
        errlog.seek(0)
        err = errlog.read().decode(errors='replace')
        errlog.close()
        if proc.returncode:  # crop fora da imagem, --start além do fim, arquivo quebrado...
            raise SystemExit(f'ffmpeg falhou na varredura (código {proc.returncode}): {err[-400:]}')
        if not rows:
            raise SystemExit(f'nenhum quadro decodificado no trecho pedido: {err[-400:]}')
    return fps, rows


def find_cuts(rows, threshold=0.35, contrast=3.0):
    """Corte de cena = mudança de histograma alta, pico local e bem acima da vizinhança
    (câmera girando rápido muda o histograma o tempo todo, mas sem se destacar dos vizinhos)."""
    cuts = []
    hd = [r['hist_diff'] for r in rows]
    for i, v in enumerate(hd):
        near = hd[max(0, i - 6):i] + hd[i + 1:i + 7]
        if (v >= threshold and v == max(hd[max(0, i - 2):i + 3])
                and v >= contrast * float(np.median(near) if near else 0.0)):
            cuts.append(rows[i]['t'])
    return cuts


def audio_events(video, info, stats: Stats, start=None, end=None, win=0.05):
    """RMS em janelas de 50 ms e começos de som (salto de >= 9 dB acima do fundo)."""
    if not info.get('audio'):
        return [], []
    sr = 8000
    cmd = list(FFMPEG)
    if start:
        cmd += ['-ss', f'{start:.4f}']
    cmd += ['-i', str(video)]
    if end:
        cmd += ['-t', f'{end - (start or 0.0):.4f}']
    cmd += ['-vn', '-ac', '1', '-ar', str(sr), '-f', 's16le', '-']
    with stats.stage('scan_audio'):
        raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    n = int(sr * win)
    if len(x) < n:
        return [], []
    frames = x[: len(x) // n * n].reshape(-1, n)
    db = 20 * np.log10(np.sqrt((frames ** 2).mean(axis=1)) + 1e-6)
    floor = float(np.median(db))
    t0 = start or 0.0
    levels = [{'t': round(t0 + i * win, 3), 'rms_db': round(float(v), 1)} for i, v in enumerate(db)]
    events, last = [], -1.0
    look = max(1, int(0.15 / win))
    for i in range(look, len(db)):
        base = float(db[i - look:i].min())
        t = t0 + i * win
        if db[i] - base >= 9.0 and db[i] > floor + 6.0 and t - last > 0.25:
            events.append({'t': round(t, 3), 'rms_db': round(float(db[i]), 1), 'jump_db': round(float(db[i] - base), 1)})
            last = t
    return levels, events


def find_intervals(rows, fps, cuts, audio, k=3.0, min_len=0.2, gap=0.3, pad=0.2, top=8):
    """Trechos onde o movimento sai do normal do próprio vídeo (mediana + k·MAD)."""
    if len(rows) < 3:
        return [], 0.0
    t = np.array([r['t'] for r in rows])
    m = np.array([r['motion'] for r in rows])
    cov = np.array([r['coverage'] for r in rows])
    cut_idx = {int(np.argmin(np.abs(t - c))) for c in cuts}
    for i in cut_idx:  # um corte não é ação: troca pelo vizinho
        m[i] = m[i - 1] if i > 0 else (m[i + 1] if i + 1 < len(m) else 0.0)
    win = max(1, int(round(0.2 * fps)))
    ms = np.convolve(m, np.ones(win) / win, mode='same')
    med = float(np.median(ms))
    mad = float(np.median(np.abs(ms - med))) or 1e-6
    thr = med + k * 1.4826 * mad
    active = ms > thr
    segs, i = [], 0
    while i < len(active):
        if active[i]:
            j = i
            while j + 1 < len(active) and active[j + 1]:
                j += 1
            segs.append([t[i], t[j]])
            i = j + 1
        else:
            i += 1
    merged = []
    for s in segs:
        if merged and s[0] - merged[-1][1] <= gap:
            merged[-1][1] = s[1]
        else:
            merged.append(list(s))
    end_t = float(t[-1])
    out = []
    for a, b in merged:
        a, b = max(float(t[0]), a - pad), min(end_t, b + pad)
        parts, cur = [], a
        for c in sorted(cuts):
            if a < c < b:
                parts.append((cur, c - 1.0 / fps))
                cur = c
        parts.append((cur, b))
        for pa, pb in parts:
            if pb - pa < min_len:
                continue
            sel = (t >= pa) & (t <= pb)
            seg_m = ms[sel]
            peak_i = int(np.argmax(seg_m))
            out.append({
                't0': round(pa, 3), 't1': round(pb, 3), 'duration_s': round(pb - pa, 3),
                'motion_mean': round(float(seg_m.mean()), 5), 'motion_peak': round(float(seg_m.max()), 5),
                'peak_t': round(float(t[sel][peak_i]), 3), 'coverage_mean': round(float(cov[sel].mean()), 3),
                'cuts_inside': [c for c in cuts if pa <= c <= pb],
                'audio_events_inside': [e['t'] for e in audio if pa <= e['t'] <= pb],
            })
    for iv in out:
        iv['score'] = round(iv['motion_peak'] * math.sqrt(iv['duration_s']), 6)
        ratio = iv['motion_peak'] / max(thr, 1e-6)
        iv['speed'] = 'rapido' if ratio >= 2.5 else ('medio' if ratio >= 1.5 else 'lento')
    out = sorted(sorted(out, key=lambda iv: -iv['score'])[:top], key=lambda iv: iv['t0'])
    for n, iv in enumerate(out, 1):
        iv['id'] = n
    return out, thr


def scan(video, info, out: Path, stats: Stats, fps=None, start=None, end=None, crop=None, k=3.0, top=8):
    sfps, rows = motion_signal(video, info, stats, fps, start=start, end=end, crop=crop)
    cuts = find_cuts(rows)
    levels, events = audio_events(video, info, stats, start, end)
    with stats.stage('intervals'):
        intervals, thr = find_intervals(rows, sfps, cuts, events, k=k, top=top)
    # Quadro idêntico ao anterior NO MEIO de movimento: câmera lenta feita repetindo quadros, ou
    # fonte com fps menor que o do arquivo. Cena parada (título, pausa) não conta: lá todo quadro
    # repete. Só é confiável com a varredura no fps nativo.
    changed = [r['motion'] >= 0.0002 for r in rows]
    near_cut = {i for i, r in enumerate(rows) for c in cuts if abs(r['t'] - c) <= 3.5 / sfps}
    active = [i for i in range(1, len(rows)) if i not in near_cut
              and sum(changed[j] for j in range(max(1, i - 3), min(len(rows), i + 4)) if j not in near_cut) >= 2]
    dups = sum(1 for i in active if not changed[i])
    span = max(len(active) / sfps, 1e-6)
    repeats = {'duplicate_ratio': round(dups / max(len(active), 1), 3),
               'unique_frames_per_s': round((len(active) - dups) / span, 2) if active else None,
               'frames_in_motion': len(active),
               'scan_at_native_fps': abs(sfps - info['video']['fps']) < 0.01}
    with open(out / 'motion.csv', 'w', newline='') as fh:
        wr = csv.DictWriter(fh, fieldnames=['t', 'motion', 'coverage', 'hist_diff'])
        wr.writeheader()
        wr.writerows(rows)
    if levels:
        with open(out / 'audio.csv', 'w', newline='') as fh:
            wr = csv.DictWriter(fh, fieldnames=['t', 'rms_db'])
            wr.writeheader()
            wr.writerows(levels)
    write_intervals_csv(out / 'intervals.csv', intervals)
    (out / 'intervals.json').write_text(json.dumps(intervals, indent=1))
    return {'scan_fps': sfps, 'motion_threshold': round(thr, 5), 'cuts': cuts, 'audio_events': events,
            'repeats': repeats, 'intervals': intervals, 'rows': rows}


def write_intervals_csv(path, intervals):
    cols = ['id', 't0', 't1', 'duration_s', 'speed', 'motion_mean', 'motion_peak', 'peak_t', 'coverage_mean',
            'score', 'cuts_inside', 'audio_events_inside']
    with open(path, 'w', newline='') as fh:
        wr = csv.DictWriter(fh, fieldnames=cols, extrasaction='ignore')
        wr.writeheader()
        for iv in intervals:
            wr.writerow({**iv, 'cuts_inside': ' '.join(map(str, iv['cuts_inside'])),
                         'audio_events_inside': ' '.join(map(str, iv['audio_events_inside']))})


# ----------------------------------------------------------------------------- extract

RATE_BY_SPEED = {'rapido': 60.0, 'medio': 24.0, 'lento': 8.0}
SHOWINFO = re.compile(r'Parsed_showinfo.*?\bn:\s*(\d+).*?\bpts_time:\s*([-\d.]+)')


def plan_rates(intervals, native, budget, max_fps=60.0):
    """Quadros por segundo de cada trecho: rápido = nativo; corta proporcionalmente se passar do orçamento."""
    rates = [min(native, max_fps, RATE_BY_SPEED.get(iv.get('speed', 'medio'), 24.0)) for iv in intervals]
    need = sum(r * iv['duration_s'] for r, iv in zip(rates, intervals))
    if need > budget > 0:
        f = budget / need
        rates = [max(2.0, r * f) for r in rates]
    steps = [max(1, int(round(native / r))) for r in rates]
    return [native / s for s in steps], steps


STATIC_COVERAGE = 0.002  # fração de pixels (96x54) mudando > 12 tons; ruído de compressão fica abaixo


def adaptive_offsets(rows, native, budget, start, cuts=(), min_rate=2.0, active_rate=20.0, max_fps=60.0,
                     static_cov=STATIC_COVERAGE):
    """Amostragem no trecho inteiro com densidade pelo movimento local:
    parado (coverage < static_cov) = min_rate qps; mexendo = pelo menos active_rate qps, subindo
    em escala log (entre o movimento mais calmo e o mais forte do próprio trecho) até o fps nativo
    (máx. 60). "Está mexendo?" usa a fração de pixels que mudam, não a média: um personagem
    pequeno e longe muda poucos pixels e mesmo assim precisa de densidade. Se passar do orçamento,
    reduz tudo na mesma proporção. Sempre pega o quadro logo após cada corte.
    Devolve deslocamentos em quadros nativos a partir de `start`."""
    t = np.array([r['t'] for r in rows])
    m = np.array([r['motion'] for r in rows])
    cov = np.array([r['coverage'] for r in rows])
    if len(t) < 2:
        return [0]
    dt = float(np.median(np.diff(t)))
    win = max(1, int(round(0.2 / dt)))
    kernel = np.ones(win) / win
    ms = np.convolve(m, kernel, mode='same')
    active = np.convolve(cov, kernel, mode='same') > static_cov
    top = min(native, max_fps)
    active_rate = min(active_rate, top)
    if active.any():
        lo = max(float(np.percentile(ms[active], 10)), 1e-6)
        hi = max(float(np.percentile(ms, 95)), lo * 1.01)
        level = np.clip(np.log(np.maximum(ms, lo) / lo) / math.log(hi / lo), 0.0, 1.0)
    else:
        level = np.zeros_like(ms)
    rate = np.where(active, active_rate + (top - active_rate) * level, min_rate)
    expected = float((rate * dt).sum())
    if expected > budget:
        rate = np.maximum(rate * budget / expected, min(min_rate, budget / max(t[-1] - t[0], dt)))
    picks, acc = [], 1.0  # acc=1: pega o primeiro quadro
    for ti, ri in zip(t, rate):
        acc += ri * dt
        while acc >= 1.0:
            picks.append(ti)
            acc -= 1.0
    to_frame = lambda p: max(0, int(round((p - start) * native)))  # noqa: E731
    cut_frames = sorted({to_frame(c) for c in cuts if t[0] <= c <= t[-1]})[:budget]
    rest = sorted({to_frame(p) for p in picks} - set(cut_frames))
    room = budget - len(cut_frames)
    if len(rest) > room:  # o piso por trecho pode passar do orçamento: afina por igual, sem cortar o fim
        rest = [rest[round(i * (len(rest) - 1) / max(room - 1, 1))] for i in range(room)] if room > 0 else []
    return sorted(set(cut_frames) | set(rest))


def _select_expr(offsets):
    if len(offsets) > 2:
        step = offsets[1] - offsets[0]
        if step > 0 and offsets[0] == 0 and all(b - a == step for a, b in zip(offsets, offsets[1:])):
            return f"not(mod(n\\,{step}))"
    return '+'.join(f'eq(n\\,{o})' for o in offsets)


def extract(video, info, out: Path, intervals, stats: Stats, budget=160, width=640, crop=None, max_fps=60.0,
            tile=240, per_sheet=16):
    """Cada trecho (intervals) vira um grupo; trecho com 'offsets' usa a amostragem adaptativa dada,
    senão um passo fixo pela velocidade do trecho."""
    native = info['video']['fps']
    rates, steps = plan_rates(intervals, native, budget, max_fps)
    frames_dir = out / 'frames'
    frames_dir.mkdir(exist_ok=True)
    manifest, sheets, total = [], [], 0
    with stats.stage('extract'):
        for iv, rate, step in zip(intervals, rates, steps):
            remaining = budget - total
            if remaining <= 0:
                iv['skipped'] = 'orçamento de quadros esgotado'
                continue
            offsets = iv.get('offsets') or list(range(0, int(iv['duration_s'] * native) + 1, step))
            offsets = offsets[:remaining]
            rate = len(offsets) / max(iv['duration_s'], 1e-6)
            vf = f"select='{_select_expr(offsets)}'," + (f'crop={crop},' if crop else '') + f'scale={width}:-2,showinfo'
            pattern = frames_dir / f'iv{iv["id"]:02d}_%05d.jpg'
            cmd = ['ffmpeg', '-hide_banner', '-nostdin', '-loglevel', 'info', '-copyts',
                   '-ss', f'{iv["t0"]:.4f}', '-i', str(video), '-to', f'{iv["t1"]:.4f}',
                   '-an', '-vf', vf, '-fps_mode', 'passthrough', '-frames:v', str(remaining),
                   '-q:v', '3', str(pattern)]
            res = subprocess.run(cmd, capture_output=True, text=True)
            if res.returncode:
                raise SystemExit(f'ffmpeg falhou no trecho {iv["id"]}: {res.stderr[-400:]}')
            times = [float(m.group(2)) for m in SHOWINFO.finditer(res.stderr)]
            files = sorted(frames_dir.glob(f'iv{iv["id"]:02d}_*.jpg'))
            items = []
            for k, (f, t) in enumerate(zip(files, times)):
                named = frames_dir / f'iv{iv["id"]:02d}_t{t:09.4f}.jpg'
                f.rename(named)
                items.append({'interval': iv['id'], 'index': k, 't': round(t, 4),
                              'source_frame': int(round(t * native)), 'file': str(named)})
            iv['extract_fps'] = round(rate, 3)
            iv['extracted'] = len(items)
            total += len(items)
            manifest += items
            for c in range(0, len(items), per_sheet):
                chunk = items[c:c + per_sheet]
                ims = []
                for it in chunk:
                    im = Image.open(it['file'])
                    ims.append(im.resize((tile, max(1, int(im.height * tile / im.width)))))
                t_first = chunk[0]['t']
                labels = [f'{it["t"]:.3f} +{it["t"] - t_first:.3f}' for it in chunk]
                path = out / f'sheet_iv{iv["id"]:02d}_{c // per_sheet + 1:02d}.jpg'
                make_sheet(ims, labels, cols=4).save(path, quality=88)
                sheets.append(str(path))
        stats.count('extract', total)
    (out / 'frames.json').write_text(json.dumps(manifest, indent=1))
    return {'frames': manifest, 'sheets': sheets, 'budget': budget, 'extracted': total}


# ----------------------------------------------------------------------------- transcrição

def whisperx_bin() -> Path:
    """Mesmas variáveis do install-whisperx.sh e do /watch (uma fonte só)."""
    venv = Path(os.environ.get('WHISPERX_VENV', Path.home() / '.cache' / 'watch' / 'whisperx-venv'))
    return venv / 'bin' / 'whisperx'


def transcribe(video, out: Path, stats: Stats, start=None, end=None, language=None, model=None, info=None):
    """Fala com tempo (WhisperX local, CPU). VAD mais sensível que o do /watch (onset 0.2): o
    padrão corta grito e fala curta, que é o que mais aparece nos vídeos do FroggyDude.
    Instalar: bash .claude/scripts/install-whisperx.sh (~3 min). Tempos no relógio do VÍDEO."""
    exe = whisperx_bin()
    model = model or os.environ.get('WATCH_WHISPERX_MODEL', 'small')
    if not exe.is_file():
        return {'available': False, 'reason': 'WhisperX não instalado: bash .claude/scripts/install-whisperx.sh'}
    if info is not None and not info.get('audio'):
        return {'available': True, 'segments': [], 'reason': 'o vídeo não tem faixa de áudio'}
    wav = out / 'speech.wav'
    cmd = list(FFMPEG)
    if start:
        cmd += ['-ss', f'{start:.4f}']
    cmd += ['-i', str(video)]
    if end:
        cmd += ['-t', f'{end - (start or 0.0):.4f}']
    cmd += ['-vn', '-ac', '1', '-ar', '16000', '-y', str(wav)]
    with stats.stage('transcribe'):
        cut = subprocess.run(cmd, capture_output=True, text=True)
        if cut.returncode:  # ex.: --start além do fim; não derruba o resto do relatório
            return {'available': True, 'error': f'ffmpeg (áudio): {cut.stderr[-400:]}'}
        wcmd = [str(exe), str(wav), '--model', model, '--device', 'cpu', '--compute_type', 'int8',
                '--output_dir', str(out), '--output_format', 'json', '--no_align', '--vad_method', 'silero',
                '--vad_onset', '0.2', '--vad_offset', '0.15', '--verbose', 'False']
        if language:
            wcmd += ['--language', language]
        res = subprocess.run(wcmd, capture_output=True, text=True)
    if res.returncode:
        return {'available': True, 'error': res.stderr[-600:]}
    data = json.loads((out / 'speech.json').read_text())
    off = start or 0.0
    segs = [{'t0': round(s['start'] + off, 2), 't1': round(s['end'] + off, 2), 'text': s['text'].strip()}
            for s in data.get('segments', [])]
    (out / 'transcript.json').write_text(json.dumps(segs, indent=1, ensure_ascii=False))
    return {'available': True, 'language': data.get('language'), 'segments': segs,
            'note': 'texto e idioma são saída do modelo (estimativa); confira trechos importantes ouvindo'}


# ----------------------------------------------------------------------------- run

def estimates_for(intervals, repeats=None):
    """Interpretações automáticas (sempre marcadas como estimativa, com o motivo)."""
    est = []
    if repeats and repeats.get('scan_at_native_fps') and repeats['duplicate_ratio'] >= 0.2:
        est.append({'interval': None,
                    'claim': 'quadros repetidos: câmera lenta por repetição ou fonte com fps menor; '
                             'o tempo do vídeo NÃO é o tempo do jogo (use rigfit --align/--warp)',
                    'basis': f'duplicate_ratio={repeats["duplicate_ratio"]}, '
                             f'unique_frames_per_s={repeats["unique_frames_per_s"]}', 'confidence': 0.7})
    for iv in intervals:
        if iv['coverage_mean'] >= 0.5:
            est.append({'interval': iv['id'], 'claim': 'câmera provavelmente em movimento (muitos pixels mudam juntos)',
                        'basis': f'coverage_mean={iv["coverage_mean"]}', 'confidence': 0.5})
        est.append({'interval': iv['id'], 'claim': f'ação {iv["speed"]} (pico de movimento x limiar)',
                    'basis': f'motion_peak={iv["motion_peak"]}', 'confidence': 0.6})
    return est


def annotation_template(info, extracted, scanned):
    return {
        'video': info['file'], 'fps': info['video']['fps'],
        'camera': {'az': None, 'elev': 0, 'mirror_x': False,
                   'notes': 'az: 0 = de frente pra ele, 90 = do lado direito dele, -90 = esquerdo'},
        'anim_t0': None,
        'frames': [{'t': f['t'], 'image': f['file'], 'keypoints': {}, 'hidden': [], 'notes': ''}
                   for f in extracted.get('frames', [])],
        'events': [],
        'keypoint_names': ['hip', 'neck', 'head_top', 'shoulder_r', 'hand_r', 'shoulder_l', 'hand_l',
                           'knee_r', 'foot_r', 'knee_l', 'foot_l'],
        'event_kinds': ['movement', 'rotation', 'displacement', 'gaze', 'camera', 'effect', 'audio'],
        'rules': 'Só anote o que aparece no quadro. Ponto escondido vai em "hidden" (não chute). '
                 'Cada evento: "observed" (o que se vê) separado de "estimate" (o que se deduz), '
                 'com "evidence" (tempos dos quadros) e "confidence" 0-1.',
        'scan_summary': {'cuts': scanned.get('cuts', []), 'audio_events': [e['t'] for e in scanned.get('audio_events', [])]},
    }


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('command', choices=['probe', 'overview', 'scan', 'extract', 'transcribe', 'run'])
    ap.add_argument('video')
    ap.add_argument('--out', required=True, help='pasta de saída (fora do repo: use o scratchpad)')
    ap.add_argument('--allow-repo', action='store_true', help='permite --out dentro do repo')
    ap.add_argument('--start', type=float)
    ap.add_argument('--end', type=float)
    ap.add_argument('--crop', help='w:h:x:y em pixels do vídeo original (foco no personagem)')
    ap.add_argument('--n', type=int, default=24, help='quadros da folha geral')
    ap.add_argument('--scan-fps', type=float, help='padrão: nativo até 60 (vídeo <= 2 min) ou 10')
    ap.add_argument('--k', type=float, default=3.0, help='sensibilidade: limiar = mediana + k·MAD')
    ap.add_argument('--top', type=int, default=8, help='máximo de trechos')
    ap.add_argument('--budget', type=int, default=160, help='máximo de quadros extraídos no total')
    ap.add_argument('--width', type=int, default=640, help='largura dos quadros extraídos')
    ap.add_argument('--intervals', help='intervals.json pronto (pula o scan no extract)')
    ap.add_argument('--transcribe', action='store_true', help='run: transcreve a fala também (WhisperX)')
    ap.add_argument('--lang', help='idioma da fala (ex.: en, pt); padrão: o modelo detecta')
    args = ap.parse_args(argv)

    video = Path(args.video)
    out = outside_repo(args.out, args.allow_repo)
    out.mkdir(parents=True, exist_ok=True)
    stats = Stats()
    with stats.stage('probe'):
        info = probe(video)
    report = {'probe': info}
    if args.command == 'probe':
        print(json.dumps(info, indent=1))
        return 0
    if args.command in ('overview', 'run'):
        report['overview'] = overview(video, info, out, stats, n=args.n, start=args.start, end=args.end, crop=args.crop)
    scanned = {}
    if args.command in ('scan', 'run') or (args.command == 'extract' and not args.intervals):
        scanned = scan(video, info, out, stats, args.scan_fps, args.start, args.end, args.crop, args.k, args.top)
        report['scan'] = {k: v for k, v in scanned.items() if k not in ('intervals', 'rows')}
    intervals = json.loads(Path(args.intervals).read_text()) if args.intervals else scanned.get('intervals', [])
    extracted = {}
    if args.command in ('extract', 'run'):
        if args.start is not None and args.end is not None and not args.intervals:
            # Trecho escolhido por quem pediu: extrai ele inteiro, denso onde mexe rápido.
            with stats.stage('adaptive_plan'):
                offsets = adaptive_offsets(scanned['rows'], info['video']['fps'], args.budget, args.start,
                                           scanned.get('cuts', ()))
            groups = [{'id': 0, 't0': args.start, 't1': args.end, 'duration_s': round(args.end - args.start, 3),
                       'speed': 'adaptativo', 'offsets': offsets}]
        else:
            groups = intervals
        extracted = extract(video, info, out, groups, stats, budget=args.budget, width=args.width, crop=args.crop)
        for g in groups:
            g.pop('offsets', None)
        report['extract'] = {'groups': groups, 'sheets': extracted['sheets'], 'extracted': extracted['extracted'],
                             'budget': args.budget}
        (out / 'intervals.json').write_text(json.dumps(intervals, indent=1))
        write_intervals_csv(out / 'intervals.csv', intervals)
    if args.command == 'transcribe' or (args.command == 'run' and args.transcribe):
        report['speech'] = transcribe(video, out, stats, args.start, args.end, args.lang, info=info)
    report['observations'] = {'intervals': intervals, 'cuts': scanned.get('cuts', []),
                              'audio_events': scanned.get('audio_events', []),
                              'repeats': scanned.get('repeats')}
    report['estimates'] = estimates_for(intervals, scanned.get('repeats'))
    report['stats'] = stats.as_dict()
    if args.command == 'run':
        (out / 'annotation_template.json').write_text(json.dumps(annotation_template(info, extracted, scanned), indent=1))
    (out / 'report.json').write_text(json.dumps(report, indent=1, ensure_ascii=False))
    s = report['stats']
    print(f'{args.command}: {len(intervals)} trechos, {s["frames_total"]} quadros analisados '
          f'({s["frames"]}), {s["total_seconds"]} s -> {out / "report.json"}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
