#!/usr/bin/env python3
"""process-video, versão pra animação de Minecraft (base: bszymi/claude-code-process-video, MIT).

Cada passo grava em <out>/<nome>-<hash8>/ (out = --out, $PV_OUT ou /tmp/process-video) e tem CACHE: rodar de novo
com os mesmos parâmetros só reimprime o resumo guardado (--force refaz). Coordenadas em px da RESOLUÇÃO DE TRABALHO
(largura --width, padrão min(largura do vídeo, 960)) — a mesma do `grid`, então as caixas saem direto da grade.

  probe    VÍDEO                         metadados, qps efetivo (quadros repetidos), VFR, rotação
  overview VÍDEO                         1 folha com o vídeo inteiro (24 quadros) pra decidir onde olhar
  motion   VÍDEO [--start --end]         energia de movimento, cortes, câmera, TRECHOS com movimento e ritmo
  burst    VÍDEO --start --end [--step]  rajada (0,1-0,25 s ou "all" = todos os quadros) em folha com tempo
  grid     VÍDEO --t T                   1 quadro com grade numerada pra marcar as partes
  parts    VÍDEO --start --end --ref T --part nome:x0:y0:x1:y1 ...   deslocamento, giro e Δ por quadro de cada parte
  compare  VÍDEO --auto | --t1 A --t2 B  lado a lado + diferença + vetores das partes
  report   VÍDEO                         linha do tempo (md + png) + o que dá e o que NÃO dá pra afirmar
  audio    VÍDEO [--start --end]         áudio 16 kHz + transcrição (WhisperX do /watch, se instalado)
  note     VÍDEO "texto"                 guarda uma conclusão (entra no report; evita reanalisar)
  status   VÍDEO                         o que já foi feito, com o resumo de cada passo
"""
import argparse, hashlib, json, math, os, subprocess, sys
from pathlib import Path

import numpy as np

try:
    import cv2
except ImportError:  # sem OpenCV: só probe/overview/burst/grid/audio funcionam
    cv2 = None
from PIL import Image, ImageDraw

CODE = hashlib.sha1(Path(__file__).read_bytes()).hexdigest()[:8]
HEAD = ("cabeca", "cabeça", "head", "coroa", "crown")
LEGS = ("perna", "leg", "pe", "pé", "feet", "foot")


# ----------------------------------------------------------------------------------------------- vídeo
def run(cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def _ratio(s):
    n, _, d = (s or "0/1").partition("/")
    try:
        return float(n) / float(d or 1) if float(d or 1) else 0.0
    except ValueError:
        return 0.0


def probe(video):
    r = run(["ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", str(video)])
    if r.returncode:
        sys.exit(f"ffprobe falhou: {r.stderr.strip()[:300]}")
    J = json.loads(r.stdout)
    v = next((s for s in J["streams"] if s.get("codec_type") == "video"), None)
    if v is None:
        sys.exit("o arquivo não tem trilha de vídeo")
    rot = 0
    for sd in v.get("side_data_list", []) or []:
        if "rotation" in sd:
            rot = int(float(sd["rotation"]))
    rot = int(v.get("tags", {}).get("rotate", rot) or 0)
    w, h = int(v["width"]), int(v["height"])
    if abs(rot) % 180 == 90:
        w, h = h, w  # o ffmpeg já gira ao decodificar
    fps, afps = _ratio(v.get("r_frame_rate")), _ratio(v.get("avg_frame_rate"))
    return {"width": w, "height": h, "fps": fps or afps, "avg_fps": afps, "vfr": bool(afps and abs(fps - afps) > 0.01 * fps),
            "duration": float(J["format"].get("duration") or v.get("duration") or 0.0), "rotation": rot,
            "audio": any(s.get("codec_type") == "audio" for s in J["streams"]), "codec": v.get("codec_name"),
            "nb_frames": int(v.get("nb_frames") or 0)}


_FPS_MODE = None


def _fps_mode():
    global _FPS_MODE
    if _FPS_MODE is None:
        out = run(["ffmpeg", "-version"]).stdout.split()
        try:
            major = int(out[2].split(".")[0].lstrip("n"))
        except (IndexError, ValueError):
            major = 6
        _FPS_MODE = ["-fps_mode", "passthrough"] if major >= 5 else ["-vsync", "0"]
    return _FPS_MODE


def frames(video, info, start, end, W, H):
    """Gera (t, quadro RGB HxWx3) de TODOS os quadros nativos do trecho, um por vez (pouca RAM)."""
    fps = info["fps"]
    t0 = math.ceil(start * fps - 1e-6) / fps
    cmd = ["ffmpeg", "-nostdin", "-loglevel", "error", "-ss", f"{start:.4f}", "-t", f"{max(0.0, end - start):.4f}",
           "-i", str(video), "-vf", f"scale={W}:{H}:flags=area", *_fps_mode(), "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    n, i, full = W * H * 3, 0, False
    try:
        while True:
            buf = p.stdout.read(n)
            if len(buf) < n:
                full = True
                break
            yield t0 + i / fps, np.frombuffer(buf, np.uint8).reshape(H, W, 3)
            i += 1
    finally:
        p.stdout.close()
        err = p.stderr.read().decode(errors="replace")
        p.stderr.close()
        p.wait()
        if full and p.returncode:  # parar cedo (um quadro só) fecha o cano de propósito: aí não é erro
            print(f"ffmpeg: {err.strip()[:300]}", file=sys.stderr)


def one_frame(video, info, t, W, H):
    for _, f in frames(video, info, t, t + 1.5 / info["fps"], W, H):
        return f.copy()
    sys.exit(f"não achei quadro em t={t}")


def gray(f):
    return cv2.cvtColor(f, cv2.COLOR_RGB2GRAY) if cv2 else np.asarray(Image.fromarray(f).convert("L"))


# ----------------------------------------------------------------------------------------------- cache
class Work:
    def __init__(self, video, out=None, width=None):
        self.video = Path(video).resolve()
        if not self.video.is_file():
            sys.exit(f"vídeo não encontrado: {video}")
        st, h = self.video.stat(), hashlib.sha1()
        h.update(str(st.st_size).encode())
        with open(self.video, "rb") as fh:
            h.update(fh.read(1 << 20))
            fh.seek(max(0, st.st_size - (1 << 20)))
            h.update(fh.read(1 << 20))
        self.dir = Path(out or os.environ.get("PV_OUT") or "/tmp/process-video") / f"{self.video.stem[:40]}-{h.hexdigest()[:8]}"
        self.dir.mkdir(parents=True, exist_ok=True)
        self.mpath = self.dir / "manifest.json"
        self.m = json.loads(self.mpath.read_text()) if self.mpath.exists() else {"video": str(self.video), "steps": {}, "notes": []}
        # entradas de outra versão do script não valem mais: some do manifesto (os arquivos são regravados)
        self.m["steps"] = {k: e for k, e in self.m["steps"].items() if e.get("code") == CODE}
        if "probe" not in self.m:
            self.m["probe"] = probe(self.video)
        self.info = self.m["probe"]
        ww = self.m.get("work_width")
        if width and ww and int(width) != ww:
            print(f"aviso: este vídeo já usa largura de trabalho {ww}; usando {width} (as coordenadas mudam!)")
        self.W = int(width or ww or min(self.info["width"], 960))
        self.W -= self.W % 2
        self.H = int(round(self.info["height"] * self.W / self.info["width"] / 2) * 2)
        self.m["work_width"] = self.W
        self.save_manifest()

    def save_manifest(self):
        self.mpath.write_text(json.dumps(self.m, indent=1, ensure_ascii=False))

    def key(self, step, params):
        # a versão do script entra na chave: mudou o código, o cache antigo não vale
        return step + ":" + hashlib.sha1((CODE + json.dumps(params, sort_keys=True)).encode()).hexdigest()[:10]

    def cached(self, step, params, force):
        e = self.m["steps"].get(self.key(step, params))
        if e and not force and all((self.dir / o).exists() for o in e["outputs"]):
            print(f"[cache] {step} já feito com esses parâmetros (--force refaz). Arquivos em {self.dir}:")
            print("  " + "\n  ".join(e["outputs"]))
            print("\n".join(e["summary"]))
            return e
        return None

    def done(self, step, params, outputs, summary, extra=None):
        e = {"params": params, "outputs": outputs, "summary": summary, "code": CODE}
        if extra:
            e.update(extra)
        self.m["steps"][self.key(step, params)] = e
        self.m[f"last_{step}"] = self.key(step, params)
        self.save_manifest()
        print(f"{step}: arquivos em {self.dir}:")
        print("  " + "\n  ".join(outputs))
        print("\n".join(summary))

    def last(self, step):
        k = self.m.get(f"last_{step}")
        return self.m["steps"].get(k) if k else None


# ----------------------------------------------------------------------------------------------- desenho
def _font(size=14):
    from PIL import ImageFont
    for f in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/TTF/DejaVuSans.ttf"):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def draw_grid(img, step=48, labels=True):
    d, fnt = ImageDraw.Draw(img), _font(12)
    for g in range(0, max(img.size), step):
        c = (255, 0, 255) if g % (step * 4) == 0 else (255, 255, 255)
        if g < img.size[0]:
            d.line([(g, 0), (g, img.size[1])], fill=c)
            if labels and g:
                d.text((g + 2, 2), str(g), fill=(255, 255, 0), font=fnt)
        if g < img.size[1]:
            d.line([(0, g), (img.size[0], g)], fill=c)
            if labels and g:
                d.text((2, g + 2), str(g), fill=(255, 255, 0), font=fnt)
    return img


def contact(tiles, labels, out, cols=6, tile_w=320):
    th = int(tiles[0].shape[0] * tile_w / tiles[0].shape[1])
    rows = (len(tiles) + cols - 1) // cols
    S = Image.new("RGB", (cols * tile_w, rows * (th + 18)), (0, 0, 0))
    d, fnt = ImageDraw.Draw(S), _font(13)
    for i, (t, lab) in enumerate(zip(tiles, labels)):
        x, y = (i % cols) * tile_w, (i // cols) * (th + 18)
        S.paste(Image.fromarray(t).resize((tile_w, th), Image.LANCZOS), (x, y + 18))
        d.text((x + 4, y + 2), lab, fill=(255, 255, 0), font=fnt)
    S.save(out, quality=85)


# ----------------------------------------------------------------------------------------------- probe / overview
def cmd_probe(w, a):
    i = w.info
    lines = [f"duração {i['duration']:.2f} s | {i['width']}x{i['height']} | {i['fps']:.3f} qps"
             + (" (VFR: qps variável, tempos ±1 quadro)" if i["vfr"] else "") + f" | áudio: {'sim' if i['audio'] else 'não'}"
             + f" | {i['codec']}" + (f" | girado {i['rotation']}°" if i["rotation"] else ""),
             f"resolução de trabalho {w.W}x{w.H} (coordenadas das partes/grade)"]
    print("\n".join(lines))


def cmd_overview(w, a):
    params = {"n": a.n, "W": w.W}
    if w.cached("overview", params, a.force):
        return
    dur = w.info["duration"]
    ts = [dur * (k + 0.5) / a.n for k in range(a.n)]
    tiles = [one_frame(w.video, w.info, t, w.W, w.H) for t in ts]
    out = "overview.jpg"
    contact(tiles, [f"{t:.2f}s" for t in ts], w.dir / out, cols=6, tile_w=240 if w.H > w.W else 320)
    w.done("overview", params, [out], [f"{a.n} quadros espalhados em {dur:.1f} s (1 a cada {dur / a.n:.2f} s). Leia SÓ esta folha antes de escolher o trecho."])


# ----------------------------------------------------------------------------------------------- motion
def _segments(t, es, thr, cuts, fps, min_dur=0.15, gap=0.12):
    on = es > thr
    segs, i, n = [], 0, len(t)
    while i < n:
        if not on[i]:
            i += 1
            continue
        j = i
        while j + 1 < n and on[j + 1] and not cuts[j + 1]:
            j += 1
        segs.append([i, j])
        i = j + 1
    merged = []
    for s in segs:
        if merged and (t[s[0]] - t[merged[-1][1]]) < gap and not cuts[merged[-1][1] + 1: s[0] + 1].any():
            merged[-1][1] = s[1]
        else:
            merged.append(s)
    return [s for s in merged if (t[s[1]] - t[s[0]] + 1 / fps) >= min_dur]


def _period(x, fps, lo=0.1, hi=1.0):
    x = np.asarray(x, float)
    bad = np.isnan(x)
    if bad.mean() > 0.3 or (~bad).sum() < 8:
        return None, 0.0
    if bad.any():  # buraco (parte tapada): preenche em linha reta; apagar o quadro encolheria o tempo
        x = np.interp(np.arange(len(x)), np.where(~bad)[0], x[~bad])
    if x.std() < 1e-6:
        return None, 0.0
    x = x - x.mean()
    k0, k1 = max(3, int(round(lo * fps))), min(len(x) // 2, int(round(hi * fps)))
    def ac(k):
        u, v = x[:-k], x[k:]
        return float(np.corrcoef(u, v)[0, 1]) if u.std() > 1e-9 and v.std() > 1e-9 else -1.0
    best = (None, 0.0)
    for k in range(k0, k1 + 1):
        r = ac(k)
        if r > best[1] + 0.02:  # o 1º pico forte (não o múltiplo)
            best = (k, r)
        elif best[0] and k > best[0] * 1.5:
            break
    if not best[0]:
        return None, best[1]
    k = best[0]
    while k + 1 <= k1 and ac(k + 1) > ac(k):  # sobe até o topo do pico
        k += 1
    a0, a1, a2 = ac(k - 1), ac(k), ac(k + 1) if k + 1 < len(x) // 2 else ac(k)
    d = a0 - 2 * a1 + a2
    frac = 0.5 * (a0 - a2) / d if d < 0 else 0.0   # pico entre dois quadros (ex.: 10,9 quadros)
    return (k + max(-0.5, min(0.5, frac))) / fps, max(a1, best[1])


def content_box(w, n=16):
    """Área útil do vídeo (sem tarja preta/texto parado de TikTok/Shorts), em px da resolução de trabalho."""
    if "content_box" in w.m:
        return w.m["content_box"]
    dur = w.info["duration"]
    sw = 160
    sh = int(round(w.H * sw / w.W / 2) * 2)
    G = np.stack([gray(one_frame(w.video, w.info, dur * (k + 0.5) / n, sw, sh)).astype(np.float32) for k in range(n)])
    dyn = G.std(0) > 6
    rows, cols = np.where(dyn.mean(1) > 0.02)[0], np.where(dyn.mean(0) > 0.02)[0]
    if len(rows) < 8 or len(cols) < 8:
        box = [0, 0, w.W, w.H]
    else:
        f = w.W / sw
        box = [int(cols[0] * f), int(rows[0] * f), int(min(w.W, (cols[-1] + 1) * f)), int(min(w.H, (rows[-1] + 1) * f))]
    w.m["content_box"] = box
    w.save_manifest()
    return box


def scene_cuts(w, start, end, box, thr=0.3):
    """Cortes de cena pelo detector do próprio ffmpeg (select=scene), só na área útil, em 160 px."""
    x0, y0, x1, y1 = box
    sx = w.info["width"] / w.W
    vf = f"crop={int((x1 - x0) * sx)}:{int((y1 - y0) * sx)}:{int(x0 * sx)}:{int(y0 * sx)},scale=160:-2,select='gt(scene,{thr})',showinfo"
    r = run(["ffmpeg", "-nostdin", "-hide_banner", "-ss", f"{start:.4f}", "-t", f"{end - start:.4f}", "-i", str(w.video),
             "-vf", vf, "-an", "-f", "null", "-"])
    out = []
    for ln in r.stderr.splitlines():
        if "pts_time:" in ln:
            try:
                out.append(start + float(ln.split("pts_time:")[1].split()[0]))
            except ValueError:
                pass
    return out


def cmd_motion(w, a):
    """Movimento por ÁREA que mudou (não pela média: uma cabeça mexendo é 2% da tela e sumia na média), só na área útil
    e sem o fundo que se mexe sozinho (lava/água/fogo animados: pixel que muda em >85% dos quadros do mesmo plano)."""
    start, end = a.start or 0.0, a.end if a.end is not None else w.info["duration"]
    params = {"start": round(start, 3), "end": round(end, 3), "min_area": a.min_area, "anim": a.anim_rate}
    if w.cached("motion", params, a.force):
        return
    if cv2 is None:
        sys.exit("motion precisa do OpenCV (python3 -c 'import cv2')")
    bx0, by0, bx1, by1 = content_box(w)
    aw = 320
    ah = int(round(w.H * aw / w.W / 2) * 2)
    f = aw / w.W
    cx0, cy0, cx1, cy1 = int(bx0 * f), int(by0 * f), max(int(bx0 * f) + 16, int(bx1 * f)), max(int(by0 * f) + 16, int(by1 * f))
    cw, chh = cx1 - cx0, cy1 - cy0
    win = cv2.createHanningWindow((cw, chh), cv2.CV_32F)
    k3 = np.ones((3, 3), np.uint8)
    rows, masks, prev, ph = [], [], None, None
    for t, fr in frames(w.video, w.info, start, end, aw, ah):
        g = gray(fr[cy0:cy1, cx0:cx1]).astype(np.float32)
        hist = cv2.calcHist([g.astype(np.uint8)], [0], None, [32], [0, 256])
        if prev is None:
            rows.append((t, 0.0, 1.0, 0.0, 0.0, 0.0))
            ch = np.zeros((chh, cw), np.uint8)
        else:
            d = np.abs(g - prev)
            ch = cv2.morphologyEx((d > 12).astype(np.uint8), cv2.MORPH_OPEN, k3)   # tira ruído de compressão solto
            (dx, dy), resp = cv2.phaseCorrelate(prev.copy(), g.copy(), win)  # cópias: o OpenCV 5 aplica a janela nos dados de entrada
            rows.append((t, float(d.mean()), float(cv2.compareHist(ph, hist, cv2.HISTCMP_CORREL)), dx / f, dy / f, resp))
        masks.append(np.packbits(ch[: chh // 2 * 2, : cw // 2 * 2].reshape(chh // 2, 2, cw // 2, 2).max((1, 3))))
        prev, ph = g, hist
    A = np.array(rows)
    if len(A) < 3:
        sys.exit("trecho curto demais")
    t, e, corr = A[:, 0], A[:, 1], A[:, 2]
    fps = w.info["fps"]
    cuts = np.zeros(len(t), bool)
    for i in range(1, len(t)):
        nb = np.r_[e[max(1, i - 5):i], e[i + 1:i + 6]]
        if e[i] > 15 and e[i] > 3 * max(1.0, np.median(nb)) and corr[i] < 0.97:
            cuts[i] = True
    for tc in scene_cuts(w, start, end, (bx0, by0, bx1, by1)):
        cuts[int(np.argmin(np.abs(t - tc)))] = True
    cuts[0] = False
    dup = e < 0.2
    dup[0] = False
    mh, mw = chh // 2, cw // 2
    unpack = lambda i: np.unpackbits(masks[i])[: mh * mw].reshape(mh, mw).astype(bool)
    area, animf = np.zeros(len(t)), np.zeros(len(t))
    edges = [0] + [i for i in np.where(cuts)[0]] + [len(t)]
    for s0, s1 in zip(edges[:-1], edges[1:]):
        idx = [i for i in range(s0, s1) if not dup[i] and i > s0]
        anim = np.zeros((mh, mw), bool)
        if len(idx) >= max(8, int(fps)):
            rate = np.mean([unpack(i) for i in idx], axis=0)
            anim = cv2.dilate((rate > a.anim_rate).astype(np.uint8), k3).astype(bool)
        keep = ~anim
        nk = max(1, keep.sum())
        for i in range(s0, s1):
            area[i] = (unpack(i) & keep).sum() / nk
            animf[i] = anim.mean()
    for i in np.where(dup)[0]:  # quadro repetido (24 qps num arquivo de 30) não é "parou"
        if 0 < i < len(area) - 1:
            area[i] = max(area[i - 1], area[i + 1])
    k = max(1, int(round(0.1 * fps)))
    ars = np.convolve(np.where(cuts, 0.0, area), np.ones(k) / k, mode="same")
    segs = _segments(t, ars, a.min_area, cuts, fps)
    cam = np.hypot(A[:, 3], A[:, 4]) * (A[:, 5] > 0.1)
    S = []
    for i, j in segs:
        seg = ars[i:j + 1]
        per, r = _period(area[i:j + 1], fps)
        cm = float(np.median(cam[i:j + 1][~dup[i:j + 1]])) if (~dup[i:j + 1]).any() else 0.0
        pk = [i + q for q in range(1, len(seg) - 1) if seg[q] >= seg[q - 1] and seg[q] > seg[q + 1]]
        pk = sorted(pk, key=lambda q: -ars[q])[:3]
        S.append({"start": round(float(t[i]), 3), "end": round(float(t[j] + 1 / fps), 3),
                  "area_media": round(float(area[i:j + 1].mean()), 4), "area_pico": round(float(seg.max()), 4),
                  "fundo_animado": round(float(animf[i:j + 1].mean()), 3),
                  "tipo": "grande (corpo inteiro/câmera)" if seg.mean() > 0.05 or cm > 1.0 else "localizado (partes do corpo)",
                  "camera_px_por_quadro": round(cm, 2), "camera": "parada" if cm < 1.0 else "mexendo",
                  "ritmo_s": round(per, 3) if per and r > 0.35 else None, "ritmo_r": round(r, 2),
                  "rajadas": [round(float(t[q]), 3) for q in sorted(pk)]})
    np.savetxt(w.dir / "motion.csv", np.c_[A[:, :1], A[:, 1:2], area, A[:, 2:], animf, cuts, dup], delimiter=",", fmt="%.5f",
               header="t,energia,area_mudou,corr_hist,cam_dx,cam_dy,cam_resp,fundo_animado,corte,repetido", comments="")
    (w.dir / "segments.json").write_text(json.dumps(S, indent=1, ensure_ascii=False))
    outs = ["motion.csv", "segments.json"]
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        fig, ax = plt.subplots(2, 1, figsize=(12, 4.6), sharex=True, gridspec_kw={"height_ratios": [3, 1]})
        ax[0].plot(t, area * 100, lw=0.6, color="#999", label="área que mudou (%)")
        ax[0].plot(t, ars * 100, lw=1.6, color="#1f6feb", label="suavizada 0,1 s")
        ax[0].axhline(a.min_area * 100, color="#d29922", ls="--", lw=1, label="limiar")
        for s_ in S:
            ax[0].axvspan(s_["start"], s_["end"], color="#2da44e", alpha=0.18)
        for tc in t[cuts]:
            ax[0].axvline(tc, color="#cf222e", lw=1.2)
        ax[0].set_yscale("symlog", linthresh=0.5)
        ax[0].legend(loc="upper right", fontsize=8)
        ax[0].set_ylabel("% da área útil")
        ax[1].plot(t, cam, lw=0.8, color="#8250df")
        ax[1].set_ylabel("câmera px/q")
        ax[1].set_xlabel("tempo (s)")
        fig.tight_layout()
        fig.savefig(w.dir / "motion.png", dpi=90)
        plt.close(fig)
        outs.append("motion.png")
    except Exception as ex:  # sem matplotlib (ou com dependência quebrada): só os dados
        print(f"(gráfico pulado: {ex.__class__.__name__}: {ex})", file=sys.stderr)
    dupf = float(dup.mean())
    summ = [f"{len(t)} quadros {t[0]:.2f}-{t[-1]:.2f} s | área útil {bx0},{by0}-{bx1},{by1} | cortes: "
            + (", ".join(f"{x:.2f}" for x in t[cuts]) or "nenhum")
            + (f" | {dupf:.0%} quadros iguais ao anterior → qps efetivo ~{fps * (1 - dupf):.1f}" if dupf > 0.05 else ""),
            f"{len(S)} trecho(s) com movimento (área ≥ {a.min_area:.1%}, fundo animado descontado):"]
    for s_ in sorted(S, key=lambda s_: s_["start"])[:15]:
        summ.append(f"  {s_['start']:.2f}-{s_['end']:.2f} s  {s_['tipo']}  área {s_['area_media']:.1%} (pico {s_['area_pico']:.1%})"
                    f"  câmera {s_['camera']}" + (f"  fundo animado {s_['fundo_animado']:.0%} ignorado" if s_["fundo_animado"] > 0.01 else "")
                    + (f"  ritmo ~{s_['ritmo_s']:.3f} s (r {s_['ritmo_r']}" + (", provavelmente textura animada do Minecraft (troca a cada 0,1 s), não o corpo"
                       if 0.07 <= s_["ritmo_s"] <= 0.12 else ", pode ser meio ciclo") + ")" if s_["ritmo_s"] else "")
                    + (f"  rajadas em {', '.join(map(str, s_['rajadas']))}" if s_["rajadas"] else ""))
    if len(S) > 15:
        summ.append(f"  … mais {len(S) - 15} em segments.json")
    w.done("motion", params, outs, summ)


# ----------------------------------------------------------------------------------------------- burst / grid
def cmd_burst(w, a):
    step = None if a.step == "all" else float(a.step)
    params = {"start": round(a.start, 3), "end": round(a.end, 3), "step": a.step, "W": w.W, "grid": a.grid}
    if w.cached("burst", params, a.force):
        return
    fps = w.info["fps"]
    every = 1 if step is None else max(1, int(round(step * fps)))
    tiles, labs = [], []
    for i, (t, f) in enumerate(frames(w.video, w.info, a.start, a.end, w.W, w.H)):
        if i % every == 0:
            im = Image.fromarray(f.copy())
            if a.grid:
                draw_grid(im, 48, labels=False)
            tiles.append(np.asarray(im))
            labs.append(f"{t:.3f}s  q{int(round(t * fps))}")
    if not tiles:
        sys.exit("nenhum quadro no trecho")
    outs, per = [], 24
    tag = f"{a.start:.2f}-{a.end:.2f}_{a.step}"
    for p in range(0, len(tiles), per):
        name = f"burst_{tag}_{p // per + 1}.jpg"
        contact(tiles[p:p + per], labs[p:p + per], w.dir / name, cols=6, tile_w=240 if w.H > w.W else 320)
        outs.append(name)
    real = every / fps
    summ = [f"{len(tiles)} quadros, 1 a cada {real:.3f} s ({every} quadro(s) nativo(s)); {len(outs)} folha(s).",
            "Movimento rápido (0,1 s) ainda pode cair entre dois quadros: use --step all no pedacinho que importa."]
    if len(tiles) > 48:
        summ.append("Muitos quadros: leia só a 1ª folha e escolha um trecho menor antes de ler o resto.")
    w.done("burst", params, outs, summ)


def cmd_grid(w, a):
    params = {"t": round(a.t, 3), "W": w.W, "step": a.gstep}
    if w.cached("grid", params, a.force):
        return
    im = draw_grid(Image.fromarray(one_frame(w.video, w.info, a.t, w.W, w.H)), a.gstep)
    name = f"grid_{a.t:.3f}.png"
    im.save(w.dir / name)
    w.done("grid", params, [name], [f"grade a cada {a.gstep} px (magenta a cada {a.gstep * 4}) no quadro {a.t:.3f} s, {w.W}x{w.H}.",
                                    "Marque cada parte VISÍVEL como nome:x0:y0:x1:y1 (px desta imagem) e rode `parts`."])


# ----------------------------------------------------------------------------------------------- parts
def _sub(r, y, x):
    def p(a, b, c):
        d = a - 2 * b + c
        return 0.0 if d == 0 else 0.5 * (a - c) / d
    fy = p(r[y - 1, x], r[y, x], r[y + 1, x]) if 0 < y < r.shape[0] - 1 else 0.0
    fx = p(r[y, x - 1], r[y, x], r[y, x + 1]) if 0 < x < r.shape[1] - 1 else 0.0
    return fx, fy


def _rotations(tpl_g, max_deg, step=2.0):
    h, w = tpl_g.shape
    out = []
    for ang in np.arange(-max_deg, max_deg + 1e-6, step):
        M = cv2.getRotationMatrix2D(((w - 1) / 2, (h - 1) / 2), float(ang), 1.0)
        rt = cv2.warpAffine(tpl_g, M, (w, h), flags=cv2.INTER_LINEAR, borderValue=0)
        mk = cv2.warpAffine(np.full((h, w), 255, np.uint8), M, (w, h), flags=cv2.INTER_NEAREST, borderValue=0)
        mk = cv2.erode(mk, np.ones((3, 3), np.uint8))
        out.append((float(ang), rt, mk))
    return out


def _rot_search(fg, x, y, rots, pad=4):
    h, w = rots[0][1].shape
    H, W = fg.shape
    xi, yi = int(round(x)), int(round(y))
    x0, y0, x1, y1 = max(0, xi - pad), max(0, yi - pad), min(W, xi + w + pad), min(H, yi + h + pad)
    reg = fg[y0:y1, x0:x1]
    if reg.shape[0] < h or reg.shape[1] < w:
        return np.nan, 0.0
    sc = []
    for ang, rt, mk in rots:
        r = cv2.matchTemplate(reg, rt, cv2.TM_CCORR_NORMED, mask=mk)
        sc.append(float(np.nanmax(r)) if np.isfinite(r).any() else 0.0)
    sc = np.array(sc)
    k = int(np.argmax(sc))
    spread = float(sc.max() - np.median(sc))
    ang = rots[k][0]
    if 0 < k < len(sc) - 1:
        d = sc[k - 1] - 2 * sc[k] + sc[k + 1]
        if d < 0:
            ang += 0.5 * (sc[k - 1] - sc[k + 1]) / d * (rots[1][0] - rots[0][0])
    return ang, spread


def _parse_part(s):
    try:
        name, x0, y0, x1, y1 = s.split(":")
        x0, y0, x1, y1 = map(int, (x0, y0, x1, y1))
        assert x1 > x0 + 3 and y1 > y0 + 3
        return name, x0, y0, x1, y1
    except (ValueError, AssertionError):
        sys.exit(f"parte inválida '{s}' (use nome:x0:y0:x1:y1 em px da grade)")


def cmd_parts(w, a):
    if cv2 is None:
        sys.exit("parts precisa do OpenCV")
    parts = [_parse_part(p) for p in a.part]
    params = {"start": round(a.start, 3), "end": round(a.end, 3), "ref": round(a.ref, 3), "parts": parts, "W": w.W,
              "rot": a.rot, "search": a.search}
    if w.cached("parts", params, a.force):
        return
    fps = w.info["fps"]
    ref = one_frame(w.video, w.info, a.ref, w.W, w.H)
    rg = gray(ref)
    T = {}
    for name, x0, y0, x1, y1 in parts:
        tg = rg[y0:y1, x0:x1]
        T[name] = {"tpl": ref[y0:y1, x0:x1].copy(), "box": (x0, y0, x1, y1), "pos": (float(x0), float(y0)),
                   "rots": _rotations(tg, a.rot) if a.rot > 0 else None, "flat": float(tg.std()) < 6.0, "miss": 0}
    win = cv2.createHanningWindow((w.W, w.H), cv2.CV_32F)
    rows, prev, camx, camy = [], None, 0.0, 0.0
    for t, f in frames(w.video, w.info, a.start, a.end, w.W, w.H):
        fg = gray(f)
        if prev is not None:
            (dx, dy), resp = cv2.phaseCorrelate(prev.copy(), fg.astype(np.float32), win)  # cópia (o OpenCV 5 mexe na entrada)
            if resp > 0.1:
                camx, camy = camx + dx, camy + dy
        prev = fg.astype(np.float32)
        row = [t, camx, camy]
        for name, x0, y0, x1, y1 in parts:
            P = T[name]
            bw, bh = x1 - x0, y1 - y0
            s = a.search * (2 if P["miss"] else 1)
            px, py = P["pos"]
            sx0, sy0 = max(0, int(px) - s), max(0, int(py) - s)
            sx1, sy1 = min(w.W, int(px) + bw + s), min(w.H, int(py) + bh + s)
            reg = f[sy0:sy1, sx0:sx1]
            if reg.shape[0] < bh or reg.shape[1] < bw:
                row += [np.nan] * 6
                P["miss"] = 1
                continue
            r = cv2.matchTemplate(reg, P["tpl"], cv2.TM_CCOEFF_NORMED)
            _, sc, _, loc = cv2.minMaxLoc(r)
            fx, fy = _sub(r, loc[1], loc[0])
            nx, ny = sx0 + loc[0] + fx, sy0 + loc[1] + fy
            ang, conf = (np.nan, 0.0)
            if P["rots"] is not None and not P["flat"] and sc >= 0.4:
                ang, conf = _rot_search(fg, nx, ny, P["rots"])
                if conf < 0.004:
                    ang = np.nan  # curva de giro chata: não dá pra afirmar
            if sc >= 0.4:
                P["pos"], P["miss"] = (nx, ny), 0
            else:
                P["miss"] = 1
            row += [nx + bw / 2, ny + bh / 2, nx - x0, ny - y0, ang, sc]
        rows.append(row)
    A = np.array(rows, float)
    hdr = ["t", "cam_x", "cam_y"] + [f"{n}_{k}" for n, *_ in parts for k in ("cx", "cy", "dx", "dy", "ang", "score")]
    np.savetxt(w.dir / "parts.csv", A, delimiter=",", fmt="%.3f", header=",".join(hdr), comments="")
    camm = float(np.nanmean(np.hypot(np.diff(A[:, 1]), np.diff(A[:, 2])))) if len(A) > 1 else 0.0
    use_comp = camm > 0.3
    stats, summ = {}, []
    first = None
    for k, (name, *_b) in enumerate(parts):
        c = 3 + 6 * k
        dx, dy, ang, sc = A[:, c + 2].copy(), A[:, c + 3].copy(), A[:, c + 4], A[:, c + 5]
        if use_comp:
            dx, dy = dx - A[:, 1], dy - A[:, 2]
        ok = sc >= 0.55
        vis = float(ok.mean())
        dyo, dxo = np.where(ok, dy, np.nan), np.where(ok, dx, np.nan)
        def amp(v):
            v = v[~np.isnan(v)]
            return float(np.percentile(v, 95) - np.percentile(v, 5)) if len(v) > 2 else float("nan")
        ampy, ampx = amp(dyo), amp(dxo)
        angv = ang[ok & ~np.isnan(ang)]
        ampa = float(np.percentile(angv, 95) - np.percentile(angv, 5)) if len(angv) > 4 else None
        rot_known = float((~np.isnan(ang)).mean())
        sig = dyo if (np.nan_to_num(ampy) >= np.nan_to_num(ampx)) else dxo
        per, r = _period(sig, fps, 0.08, 1.2) if max(np.nan_to_num(ampy), np.nan_to_num(ampx)) >= 3 else (None, 0.0)
        vy = np.abs(np.diff(np.nan_to_num(dyo))) * fps
        low, i = [], 0
        while i < len(ok):
            if not ok[i]:
                j = i
                while j + 1 < len(ok) and not ok[j + 1]:
                    j += 1
                low.append([round(float(A[i, 0]), 3), round(float(A[j, 0]), 3)])
                i = j + 1
            else:
                i += 1
        st = {"visivel": round(vis, 3), "sobe_desce_px": round(ampy, 1), "lado_px": round(ampx, 1),
              "giro_graus": None if ampa is None else round(ampa, 1), "giro_medido": round(rot_known, 2),
              "vel_max_px_s": round(float(np.nanmax(vy)) if len(vy) else 0.0, 1),
              "periodo_s": round(per, 3) if per and r > 0.4 else None, "periodo_r": round(r, 2), "tapado": low[:12]}
        if first is None:
            first = dyo
        elif np.nan_to_num(ampy) >= 2:
            m = ~np.isnan(first) & ~np.isnan(dyo)
            if m.sum() > 5:
                st["corr_com_" + parts[0][0]] = round(float(np.corrcoef(first[m], dyo[m])[0, 1]), 2)
        st["_dy"] = dyo
        stats[name] = st
        line = f"{name}: sobe/desce {ampy:.1f} px, lado {ampx:.1f} px"
        line += f", giro {ampa:.1f}°" if ampa is not None else ", giro: indeterminável (peça lisa ou tapada)"
        line += f", visível {vis:.0%}"
        if st["periodo_s"]:
            line += f", ciclo {per:.3f} s (r {r:.2f})"
        if "corr_com_" + parts[0][0] in st:
            line += f", acompanha {parts[0][0]} {st['corr_com_' + parts[0][0]]:+.2f}"
        summ.append(line)
    big = max([np.nan_to_num(v["sobe_desce_px"]) for v in stats.values()] + [0.0])
    for name, st in stats.items():
        dyo = st.pop("_dy")
        if st["periodo_s"] and np.nan_to_num(st["sobe_desce_px"]) >= max(6.0, 0.3 * big):
            st["ciclo"] = _cycle(A[:, 0], dyo, st["periodo_s"])
            cy = st["ciclo"]
            if cy:
                i = [k for k, l in enumerate(summ) if l.startswith(name + ":")]
                if i:
                    summ[i[0]] += (f" [em cima {cy['cima']:.0%}, descendo {cy['descendo']:.0%}, embaixo {cy['baixo']:.0%},"
                                   f" subindo {cy['subindo']:.0%}]")
    post = _posture(A, parts)
    if post:
        stats["_postura"] = post
        summ.append(f"postura (eixo pés→cabeça na imagem): {post['min']:.0f}° a {post['max']:.0f}° (variação {post['amp']:.1f}°)")
    stats["_camera"] = {"px_por_quadro": round(camm, 2), "compensado": use_comp}
    summ.insert(0, f"{len(A)} quadros {A[0, 0]:.3f}-{A[-1, 0]:.3f} s, câmera {'MEXENDO (descontei)' if use_comp else 'parada'} ({camm:.2f} px/quadro)")
    (w.dir / "parts.json").write_text(json.dumps(stats, indent=1, ensure_ascii=False))
    outs = ["parts.csv", "parts.json"]
    if _plot_parts(w.dir / "parts.png", A, parts, use_comp):
        outs.append("parts.png")
    w.done("parts", params, outs, summ, {"parts": parts})


def _cycle(t, dy, per):
    """Fases de um ciclo de sobe-desce (dy cresce = desce na imagem): em cima / descendo / embaixo / subindo."""
    bad = np.isnan(dy)
    if bad.mean() > 0.3:
        return None
    if bad.any():
        dy = np.interp(np.arange(len(dy)), np.where(~bad)[0], dy[~bad])
    lo, hi = np.percentile(dy, 3), np.percentile(dy, 97)
    if hi - lo < 1e-6:
        return None
    k = (dy - lo) / (hi - lo)
    lab = np.where(k < 0.07, "cima", np.where(k > 0.93, "baixo", "meio"))
    seq = []
    for i in range(len(lab)):
        if lab[i] == "meio":
            prev = next((lab[j] for j in range(i - 1, -1, -1) if lab[j] in ("cima", "baixo")), None)
            lab_i = "descendo" if prev == "cima" else "subindo" if prev == "baixo" else ""
        else:
            lab_i = lab[i]
        seq.append(lab_i)
    cnt = {s: seq.count(s) for s in ("cima", "descendo", "baixo", "subindo")}
    tot = sum(cnt.values()) or 1
    return {s: round(c / tot, 2) for s, c in cnt.items()} | {"periodo_s": round(per, 3)}


def _posture(A, parts):
    hi = [k for k, p in enumerate(parts) if any(h in p[0].lower() for h in HEAD)]
    li = [k for k, p in enumerate(parts) if any(h in p[0].lower() for h in LEGS)]
    if not hi or not li:
        return None
    h = A[:, 3 + 6 * hi[0]: 5 + 6 * hi[0]]
    l = np.nanmean(np.stack([A[:, 3 + 6 * k: 5 + 6 * k] for k in li]), axis=0)
    v = h - l
    ang = np.degrees(np.arctan2(-v[:, 1], v[:, 0]))
    ang = ang[~np.isnan(ang)]
    if len(ang) < 3:
        return None
    return {"min": float(np.percentile(ang, 5)), "max": float(np.percentile(ang, 95)),
            "amp": float(np.percentile(ang, 95) - np.percentile(ang, 5))}


def _plot_parts(out, A, parts, comp):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except Exception as ex:
        print(f"(gráfico pulado: {ex.__class__.__name__}: {ex})", file=sys.stderr)
        return False
    t = A[:, 0]
    fig, ax = plt.subplots(3, 1, figsize=(12, 7), sharex=True)
    for k, (name, *_b) in enumerate(parts):
        c = 3 + 6 * k
        ok = A[:, c + 5] >= 0.55
        dx, dy = A[:, c + 2] - (A[:, 1] if comp else 0), A[:, c + 3] - (A[:, 2] if comp else 0)
        ax[0].plot(t, np.where(ok, dy, np.nan), lw=1.4, label=name)
        ax[1].plot(t, np.where(ok, dx, np.nan), lw=1.4, label=name)
        ax[2].plot(t, np.where(ok, A[:, c + 4], np.nan), lw=1.4, label=name)
    ax[0].set_ylabel("Δy px (+ = desce)")
    ax[0].invert_yaxis()
    ax[1].set_ylabel("Δx px (+ = direita)")
    ax[2].set_ylabel("giro ° (imagem)")
    ax[2].set_xlabel("tempo (s)")
    ax[0].legend(fontsize=8, ncol=min(6, len(parts)), loc="upper right")
    for x in ax:
        x.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(out, dpi=90)
    plt.close(fig)
    return True


# ----------------------------------------------------------------------------------------------- compare
COLORS = [(255, 64, 64), (64, 220, 64), (64, 160, 255), (255, 200, 0), (255, 64, 255), (0, 230, 230), (255, 140, 0)]


def _load_parts_csv(w):
    e = w.last("parts")
    if not e:
        return None, None
    A = np.genfromtxt(w.dir / "parts.csv", delimiter=",", skip_header=1)
    return A, e["parts"]


def _compare_img(w, ta, tb, A, parts, label):
    fa, fb = one_frame(w.video, w.info, ta, w.W, w.H), one_frame(w.video, w.info, tb, w.W, w.H)
    diff = np.clip(np.abs(fa.astype(int) - fb.astype(int)).sum(2) * 2, 0, 255).astype(np.uint8)
    heat = np.stack([diff, (diff * 0.4).astype(np.uint8), np.zeros_like(diff)], 2)
    ims = [draw_grid(Image.fromarray(fa), 48, labels=False), draw_grid(Image.fromarray(fb), 48, labels=False),
           draw_grid(Image.fromarray(heat), 48, labels=False)]   # grade ANTES: caixas e vetores ficam por cima
    fnt_b = _font(13)
    lines = []
    if A is not None:
        ia, ib = int(np.argmin(np.abs(A[:, 0] - ta))), int(np.argmin(np.abs(A[:, 0] - tb)))
        for k, (name, x0, y0, x1, y1) in enumerate(parts):
            c, col = 3 + 6 * k, COLORS[k % len(COLORS)]
            bw, bh = x1 - x0, y1 - y0
            for im, i in ((ims[0], ia), (ims[1], ib)):
                cx, cy = A[i, c], A[i, c + 1]
                if not np.isnan(cx):
                    dd = ImageDraw.Draw(im)
                    dd.rectangle([cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2], outline=(0, 0, 0), width=5)
                    dd.rectangle([cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2], outline=col, width=3)
                    dd.text((cx - bw / 2 + 3, cy - bh / 2 - 16), name, fill=col, font=fnt_b, stroke_width=2, stroke_fill=(0, 0, 0))
            ca, cb = A[ia, c:c + 2], A[ib, c:c + 2]
            if not (np.isnan(ca).any() or np.isnan(cb).any()):
                d = ImageDraw.Draw(ims[2])
                tip = ca + (cb - ca) * 3
                d.line([tuple(ca), tuple(tip)], fill=col, width=4)
                d.ellipse([tip[0] - 5, tip[1] - 5, tip[0] + 5, tip[1] + 5], fill=col)
                d.text((tip[0] + 6, tip[1] - 8), name, fill=col, font=fnt_b, stroke_width=2, stroke_fill=(0, 0, 0))
                da = A[ib, c + 4] - A[ia, c + 4]
                lines.append(f"{name}: Δx {cb[0] - ca[0]:+.1f}  Δy {cb[1] - ca[1]:+.1f} px"
                             + (f"  giro {da:+.1f}°" if not np.isnan(da) else "  giro ?")
                             + ("  (baixa confiança)" if min(A[ia, c + 5], A[ib, c + 5]) < 0.55 else ""))
    W_, H_ = w.W, w.H
    cv = Image.new("RGB", (W_ * 3, H_ + 30 + 18 * max(1, len(lines))), (0, 0, 0))
    d, fnt = ImageDraw.Draw(cv), _font(14)
    for k, (im, tt) in enumerate(zip(ims, (f"A {ta:.3f}s", f"B {tb:.3f}s", "diferença x2 + vetor x3"))):
        cv.paste(im, (k * W_, 30))
        d.text((k * W_ + 6, 6), tt, fill=(255, 255, 0), font=fnt)
    for i, ln in enumerate(lines):
        d.text((6, H_ + 32 + 18 * i), ln, fill=(255, 255, 255), font=fnt)
    name = f"compare_{label}_{ta:.3f}_{tb:.3f}.jpg"
    if cv.width > 1800:
        cv = cv.resize((1800, int(cv.height * 1800 / cv.width)), Image.LANCZOS)
    cv.save(w.dir / name, quality=85)
    return name, lines


def cmd_compare(w, a):
    A, parts = _load_parts_csv(w)
    if a.auto:
        if A is None:
            sys.exit("--auto precisa de um `parts` antes")
        params = {"auto": True, "parts": w.m.get("last_parts")}
    else:
        params = {"t1": round(a.t1, 3), "t2": round(a.t2, 3), "parts": w.m.get("last_parts")}
    if w.cached("compare", params, a.force):
        return
    pairs = []
    if a.auto:
        sc, dy = A[:, 3 + 5], A[:, 3 + 3]
        dy = np.where(sc >= 0.55, dy, np.nan)
        if np.isnan(dy).all():
            sys.exit("a 1ª parte não ficou visível; use --t1/--t2")
        hi, lo = int(np.nanargmin(dy)), int(np.nanargmax(dy))
        pairs.append((A[hi, 0], A[lo, 0], "extremos"))
        spd = np.zeros(len(A) - 1)
        for k in range(len(parts)):
            c = 3 + 6 * k
            spd += np.nan_to_num(np.hypot(np.diff(A[:, c]), np.diff(A[:, c + 1])))
        j = int(np.argmax(spd))
        pairs.append((A[j, 0], A[j + 1, 0], "mais_rapido"))
    else:
        pairs.append((a.t1, a.t2, "manual"))
    outs, summ = [], []
    for ta, tb, lab in pairs:
        name, lines = _compare_img(w, ta, tb, A, parts, lab)
        outs.append(name)
        summ.append(f"{lab}: {ta:.3f} → {tb:.3f} s")
        summ += ["  " + ln for ln in lines]
    w.done("compare", params, outs, summ)


# ----------------------------------------------------------------------------------------------- report
def _phases(t, v, fps, thr_px_s):
    """Fases de um sinal (px): parado / + / − com início e fim."""
    if len(v) < 3:
        return []
    vs = np.convolve(np.nan_to_num(v), np.ones(3) / 3, mode="same")
    vel = np.gradient(vs) * fps
    lab = np.where(np.isnan(v), "?", np.where(np.abs(vel) < thr_px_s, "0", np.where(vel > 0, "+", "-")))
    out, i = [], 0
    while i < len(lab):
        j = i
        while j + 1 < len(lab) and lab[j + 1] == lab[i]:
            j += 1
        out.append((lab[i], float(t[i]), float(t[j] + 1 / fps), float(np.nan_to_num(vs[j]) - np.nan_to_num(vs[i]))))
        i = j + 1
    return [p for p in out if p[2] - p[1] >= 1.5 / fps or p[0] == "?"]


def cmd_report(w, a):
    params = {"steps": sorted(w.m["steps"].keys()), "notes": len(w.m["notes"])}
    if w.cached("report", params, a.force):
        return
    i = w.info
    L = [f"# Análise de vídeo: {Path(w.m['video']).name}", "",
         f"- {i['duration']:.2f} s, {i['width']}x{i['height']}, {i['fps']:.3f} qps" + (" (VFR)" if i["vfr"] else "")
         + f", áudio: {'sim' if i['audio'] else 'não'}; trabalho em {w.W}x{w.H} px.", ""]
    mot = w.last("motion")
    if mot:
        S = json.loads((w.dir / "segments.json").read_text())
        L += ["## Trechos com movimento", "", "| início | fim | tipo | área | câmera | ritmo (área) | rajadas |", "|---|---|---|---|---|---|---|"]
        L += [f"| {s['start']:.2f} | {s['end']:.2f} | {s['tipo']} | {s['area_media']:.1%} | {s['camera']} | {s['ritmo_s'] or '-'} | "
              f"{', '.join(map(str, s['rajadas'])) or '-'} |" for s in S]
        if any(s["ritmo_s"] and 0.07 <= s["ritmo_s"] <= 0.12 for s in S):
            L += ["", "Ritmo de 0,07-0,12 s na área = provavelmente lava/água/fogo/portal trocando de quadro (textura animada do Minecraft, 0,1 s), não o personagem: confira com `parts`."]
        L += ["", *mot["summary"][:1], ""]
    vis, inv = [], []
    fps = i["fps"]
    inv.append(f"Movimento que dura menos de 2 quadros ({2 / fps:.3f} s) não dá pra medir; os tempos têm ±{1 / fps:.3f} s.")
    if i["vfr"]:
        inv.append("O vídeo tem qps variável: tempos podem errar 1 quadro.")
    if mot:
        Mm = np.genfromtxt(w.dir / "motion.csv", delimiter=",", skip_header=1)
        dupf = float(Mm[1:, 9].mean()) if len(Mm) > 1 else 0.0
        if dupf > 0.05:
            inv.append(f"{dupf:.0%} dos quadros são repetidos: o qps efetivo é ~{fps * (1 - dupf):.1f}, não {fps:.0f}.")
    par = w.last("parts")
    if par:
        A = np.genfromtxt(w.dir / "parts.csv", delimiter=",", skip_header=1)
        stats = json.loads((w.dir / "parts.json").read_text())
        parts = par["parts"]
        comp = stats.get("_camera", {}).get("compensado", False)
        L += ["## Partes (medido quadro a quadro)", ""] + [f"- {s}" for s in par["summary"]] + [""]
        L += ["## Linha do tempo por parte", "",
              "Sinal: Δy (+ = desce na imagem) e Δx (+ = direita), fases de ≥2 quadros; 'parado' = menos de 25 px/s.", ""]
        events = []
        for k, (name, *_b) in enumerate(parts):
            c = 3 + 6 * k
            ok = A[:, c + 5] >= 0.55
            dy = np.where(ok, A[:, c + 3] - (A[:, 2] if comp else 0), np.nan)
            dx = np.where(ok, A[:, c + 2] - (A[:, 1] if comp else 0), np.nan)
            st = stats.get(name, {})
            per = st.get("periodo_s") if st.get("periodo_r", 0) >= 0.6 else None
            tlim = A[0, 0] + per * 1.05 if per else A[-1, 0] + 1
            for axis, v, words in (("y", dy, {"+": "desce", "-": "sobe"}), ("x", dx, {"+": "vai pra direita", "-": "vai pra esquerda"})):
                vv = v[~np.isnan(v)]
                amp_ = float(np.percentile(vv, 95) - np.percentile(vv, 5)) if len(vv) > 2 else 0.0
                for lab, t0, t1, delta in _phases(A[:, 0], v, fps, 25.0):
                    if t0 > tlim:
                        break
                    if lab in "+-" and abs(delta) >= max(3.0, 0.15 * amp_):
                        events.append((t0, t1, f"{name} {words[lab]} {abs(delta):.0f} px"))
            if per:
                n_ = (A[-1, 0] - A[0, 0]) / per
                events.append((A[0, 0] + per, A[-1, 0], f"{name}: o mesmo ciclo de {per:.3f} s se repete ~{n_:.0f} vezes até o fim do trecho"))
            if st.get("visivel", 1) < 0.999:
                tt = ", ".join(f"{a_:.2f}" if a_ == b_ else f"{a_:.2f}-{b_:.2f}" for a_, b_ in st["tapado"][:6])
                inv.append(f"{name}: visível em {st['visivel']:.0%} dos quadros; tapado/irreconhecível em {tt}"
                           + (" …" if len(st["tapado"]) > 6 else "") + " (nesses quadros a posição é chute: não use).")
            if st.get("giro_graus") is None:
                inv.append(f"{name}: giro indeterminável (peça lisa, sem detalhe, ou tapada) — só posição.")
            else:
                vis.append(f"{name}: giro no plano da imagem medido em {st['giro_medido']:.0%} dos quadros ({st['giro_graus']}°).")
            if st.get("ciclo"):
                cy = st["ciclo"]
                vis.append(f"{name}: ciclo {cy['periodo_s']} s = em cima {cy['cima']:.0%}, descendo {cy['descendo']:.0%}, "
                           f"embaixo {cy['baixo']:.0%}, subindo {cy['subindo']:.0%}.")
            big_ = max(max(np.nan_to_num(v.get("sobe_desce_px", 0)), np.nan_to_num(v.get("lado_px", 0)))
                       for k_, v in stats.items() if not k_.startswith("_"))
            mv = max(np.nan_to_num(st.get("sobe_desce_px", 0)), np.nan_to_num(st.get("lado_px", 0)))
            if st.get("visivel", 0) > 0.8 and mv <= max(3.0, 0.15 * big_):
                vis.append(f"{name}: (quase) PARADO — mexe só {mv:.0f} px, {mv / max(big_, 1e-6):.0%} do que a parte que mais mexe.")
        events.sort()
        maxn = a.max_events
        L += [f"- **{t0:.3f}-{t1:.3f} s** {txt}" for t0, t1, txt in events[:maxn]]
        if len(events) > maxn:
            L.append(f"- … mais {len(events) - maxn} eventos em parts.csv")
        L.append("")
        if comp:
            inv.append("A câmera se mexe: as medidas descontam o deslocamento da câmera (correlação de fase), sobra erro de 1-2 px.")
    inv += ["Profundidade (chegar perto/longe da câmera) não é medida: só posição na imagem e escala.",
            "Giro é só no PLANO da imagem; virar de lado (fora do plano) aparece como a peça encolher/mudar de forma.",
            "Mesma imagem não diz qual osso girou: um deslocamento pode ser o tronco ou o osso pai; confirme com a vista de outro ângulo."]
    L += ["## O que dá pra afirmar (visível)", ""] + [f"- {x}" for x in vis or ["(rode `parts` pra ter medidas por parte)"]] + [""]
    L += ["## O que NÃO dá pra afirmar pelas imagens", ""] + [f"- {x}" for x in inv] + [""]
    if w.m["notes"]:
        L += ["## Notas (conclusões já tiradas — não refazer)", ""] + [f"- {n}" for n in w.m["notes"]] + [""]
    imgs = sorted(p.name for p in w.dir.glob("*.png")) + sorted(p.name for p in w.dir.glob("*.jpg"))
    L += ["## Arquivos", ""] + [f"- `{w.dir / n}`" for n in imgs]
    (w.dir / "report.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    outs = ["report.md"]
    if par and _timeline_png(w, mot is not None):
        outs.append("timeline.png")
    w.done("report", params, outs, [f"relatório: {w.dir / 'report.md'} ({len(L)} linhas). Leia ele em vez das imagens."])


def _timeline_png(w, has_motion):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except Exception as ex:
        print(f"(gráfico pulado: {ex.__class__.__name__}: {ex})", file=sys.stderr)
        return False
    A = np.genfromtxt(w.dir / "parts.csv", delimiter=",", skip_header=1)
    parts = w.last("parts")["parts"]
    n = 1 + int(has_motion)
    fig, ax = plt.subplots(n, 1, figsize=(12, 3 * n), sharex=True, squeeze=False)
    ax = ax[:, 0]
    if has_motion:
        M = np.genfromtxt(w.dir / "motion.csv", delimiter=",", skip_header=1)
        m = (M[:, 0] >= A[0, 0] - 0.5) & (M[:, 0] <= A[-1, 0] + 0.5)
        ax[0].plot(M[m, 0], M[m, 2] * 100, color="#888", lw=1)
        ax[0].set_ylabel("% da tela mudou")
    for k, (name, *_b) in enumerate(parts):
        c = 3 + 6 * k
        ok = A[:, c + 5] >= 0.55
        ax[-1].plot(A[:, 0], np.where(ok, A[:, c + 3], np.nan), lw=1.4, label=f"{name} Δy")
    ax[-1].invert_yaxis()
    ax[-1].set_ylabel("Δy px (+ = desce)")
    ax[-1].set_xlabel("tempo (s)")
    ax[-1].legend(fontsize=8, ncol=min(6, len(parts)))
    for x in ax:
        x.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(w.dir / "timeline.png", dpi=90)
    plt.close(fig)
    return True


# ----------------------------------------------------------------------------------------------- audio / note / status
def cmd_audio(w, a):
    start, end = a.start or 0.0, a.end if a.end is not None else w.info["duration"]
    params = {"start": round(start, 3), "end": round(end, 3), "lang": a.lang}
    if w.cached("audio", params, a.force):
        return
    if not w.info["audio"]:
        w.done("audio", params, [], ["o vídeo não tem áudio."])
        return
    wav = f"audio_{start:.2f}-{end:.2f}.wav"
    r = run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-ss", f"{start}", "-t", f"{end - start}", "-i", str(w.video),
             "-vn", "-ac", "1", "-ar", "16000", "-acodec", "pcm_s16le", str(w.dir / wav)])
    if r.returncode:
        sys.exit(f"ffmpeg (áudio) falhou: {r.stderr[:300]}")
    exe = Path(os.environ.get("WHISPERX_VENV", Path.home() / ".cache" / "watch" / "whisperx-venv")) / "bin" / "whisperx"
    if not exe.exists():
        w.done("audio", params, [wav], ["áudio extraído; transcrição indisponível (instale: bash .claude/scripts/install-whisperx.sh)."])
        return
    # mesmas opções da video-motion-analysis: sem alinhamento (não precisa do NLTK) e VAD silero (sem token do HF)
    cmd = [str(exe), str(w.dir / wav), "--model", os.environ.get("WATCH_WHISPERX_MODEL", "small"), "--compute_type", "int8",
           "--device", "cpu", "--output_format", "json", "--output_dir", str(w.dir), "--no_align", "--vad_method", "silero",
           "--vad_onset", "0.2", "--vad_offset", "0.15", "--verbose", "False"]
    if a.lang:
        cmd += ["--language", a.lang]
    r = run(cmd)
    js = w.dir / (Path(wav).stem + ".json")
    if r.returncode or not js.exists():
        w.done("audio", params, [wav], [f"WhisperX falhou: {(r.stderr or r.stdout)[-300:]}"])
        return
    segs = json.loads(js.read_text()).get("segments", [])
    txt = [f"[{start + s['start']:.2f}-{start + s['end']:.2f}] {s['text'].strip()}" for s in segs]
    (w.dir / (Path(wav).stem + ".txt")).write_text("\n".join(txt) + "\n", encoding="utf-8")
    w.done("audio", params, [wav, Path(wav).stem + ".txt"], txt[:20] or ["(sem fala detectada)"])


def cmd_note(w, a):
    w.m["notes"].append(a.text)
    w.save_manifest()
    print(f"nota guardada ({len(w.m['notes'])}): {a.text}")


def cmd_status(w, a):
    print(f"pasta: {w.dir}\nvídeo: {w.m['video']}  trabalho {w.W}x{w.H}")
    for k, e in w.m["steps"].items():
        print(f"- {k.split(':')[0]} {json.dumps(e['params'], ensure_ascii=False)[:120]}")
        print("    " + (e["summary"][0] if e["summary"] else ""))
    if w.m["notes"]:
        print("notas:\n  " + "\n  ".join(w.m["notes"]))


# ----------------------------------------------------------------------------------------------- CLI
def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    def add(name, fn):
        p = sub.add_parser(name)
        p.add_argument("video")
        p.add_argument("--out")
        p.add_argument("--width", type=int)
        p.add_argument("--force", action="store_true")
        p.set_defaults(fn=fn)
        return p
    add("probe", cmd_probe)
    add("overview", cmd_overview).add_argument("--n", type=int, default=24)
    p = add("motion", cmd_motion)
    p.add_argument("--start", type=float)
    p.add_argument("--end", type=float)
    p.add_argument("--min-area", type=float, default=0.004, help="fração da área útil que precisa mudar (0.004 = 0,4%%)")
    p.add_argument("--anim-rate", type=float, default=0.85, help="pixel que muda em mais que isso dos quadros do plano = fundo animado")
    p = add("burst", cmd_burst)
    p.add_argument("--start", type=float, required=True)
    p.add_argument("--end", type=float, required=True)
    p.add_argument("--step", default="0.1", help="segundos entre quadros (0.1-0.25) ou 'all'")
    p.add_argument("--grid", action="store_true")
    p = add("grid", cmd_grid)
    p.add_argument("--t", type=float, required=True)
    p.add_argument("--gstep", type=int, default=48)
    p = add("parts", cmd_parts)
    p.add_argument("--start", type=float, required=True)
    p.add_argument("--end", type=float, required=True)
    p.add_argument("--ref", type=float, required=True, help="tempo do quadro onde as caixas foram marcadas")
    p.add_argument("--part", action="append", required=True, help="nome:x0:y0:x1:y1 (repita pra cada parte)")
    p.add_argument("--rot", type=float, default=20.0, help="giro máximo procurado (graus; 0 = sem giro)")
    p.add_argument("--search", type=int, default=40, help="busca em px em volta da posição anterior")
    p = add("compare", cmd_compare)
    p.add_argument("--auto", action="store_true")
    p.add_argument("--t1", type=float)
    p.add_argument("--t2", type=float)
    add("report", cmd_report).add_argument("--max-events", type=int, default=40)
    p = add("audio", cmd_audio)
    p.add_argument("--start", type=float)
    p.add_argument("--end", type=float)
    p.add_argument("--lang")
    add("note", cmd_note).add_argument("text")
    add("status", cmd_status)
    a = ap.parse_args(argv)
    if a.cmd == "compare" and not a.auto and (a.t1 is None or a.t2 is None):
        ap.error("compare precisa de --auto ou --t1 e --t2")
    a.fn(Work(a.video, a.out, a.width), a)


if __name__ == "__main__":
    main()
