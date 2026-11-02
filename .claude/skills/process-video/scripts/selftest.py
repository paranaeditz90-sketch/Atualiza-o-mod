#!/usr/bin/env python3
"""Autoteste do pv.py com um vídeo SINTÉTICO de resposta conhecida (não depende dos vídeos de referência, que
não ficam no repo): uma "cabeça" sobe e desce 40 px a cada 0,4 s, "braço" e "pé" parados, corte de cena em 3,0 s.
Uso: python3 .claude/skills/process-video/scripts/selftest.py   (≈30 s; sai com erro se algo não bater)"""
import json, math, os, subprocess, sys, tempfile
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
PV = HERE / "pv.py"
FPS, W, H, DUR, PER, AMP = 30, 576, 576, 4.0, 0.4, 40.0


def make_video(path):
    rng = np.random.default_rng(7)
    bg1 = (rng.integers(90, 140, (H // 16, W // 16, 1)) * np.ones((1, 1, 3))).repeat(16, 0).repeat(16, 1).astype(np.uint8)
    bg2 = bg1[::-1, ::-1].copy()
    bg2[..., 2] = 200  # outro plano: cor bem diferente
    p = subprocess.Popen(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
                          "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "16", str(path)],
                         stdin=subprocess.PIPE)
    for i in range(int(DUR * FPS)):
        t = i / FPS
        f = (bg1 if t < 3.0 else bg2).copy()
        # cabeça: verde com faixa amarela (coroa) e "olhos"; desce rápido e sobe devagar (como a flexão)
        ph = (t % PER) / PER
        k = 0.0 if ph < 0.2 else (ph - 0.2) / 0.25 if ph < 0.45 else 1.0 if ph < 0.6 else 1 - (ph - 0.6) / 0.4
        y = int(round(200 + AMP * k))
        f[y:y + 80, 240:340] = (80, 170, 80)
        f[y:y + 18, 236:344] = (230, 190, 20)
        f[y + 40:y + 50, 260:275] = (20, 20, 20)
        f[y + 40:y + 50, 305:320] = (20, 20, 20)
        f[300:460, 360:400] = (15, 25, 20)      # braço parado (com punho branco)
        f[440:452, 360:400] = (230, 230, 230)
        f[470:520, 120:200] = (40, 40, 40)      # pé parado (com sola)
        f[505:520, 120:200] = (90, 60, 30)
        p.stdin.write(f.tobytes())
    p.stdin.close()
    p.wait()


def pv(*args, out):
    r = subprocess.run([sys.executable, str(PV), *args, "--out", out], capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"FALHOU: pv {' '.join(args)}\n{r.stdout}\n{r.stderr}")
    return r.stdout


def main():
    tmp = tempfile.mkdtemp(prefix="pv-selftest-")
    vid = os.path.join(tmp, "sintetico.mp4")
    make_video(vid)
    out = os.path.join(tmp, "out")
    erros = []

    def ok(cond, msg):
        print(("ok   " if cond else "ERRO ") + msg)
        if not cond:
            erros.append(msg)

    pv("probe", vid, out=out)
    pv("motion", vid, out=out)
    d = next(Path(out).iterdir())
    segs = json.loads((d / "segments.json").read_text())
    M = np.genfromtxt(d / "motion.csv", delimiter=",", names=True)
    cuts = [round(float(t), 3) for t in M["t"][M["corte"] > 0]]
    ok(float(np.median(M["energia"][1:])) < 3, f"energia de quadro parado baixa (mediana {np.median(M['energia'][1:]):.2f})")
    ok(any(abs(c - 3.0) < 0.07 for c in cuts), f"corte de cena achado em ~3,0 s (achou {cuts})")
    ok(any(s["start"] < 0.5 and s["end"] > 2.5 for s in segs), "trecho com movimento cobre 0-3 s")
    pv("grid", vid, "--t", "0.1", out=out)
    pv("parts", vid, "--start", "0", "--end", "2.9", "--ref", "0.1", "--part", "cabeca:236:200:344:280",
       "--part", "braco:356:296:404:464", "--part", "pe:116:466:204:524", out=out)
    st = json.loads((d / "parts.json").read_text())
    ok(abs(st["cabeca"]["sobe_desce_px"] - AMP) <= 3, f"cabeça sobe/desce {st['cabeca']['sobe_desce_px']} px (esperado {AMP:.0f}±3)")
    ok(st["cabeca"]["periodo_s"] and abs(st["cabeca"]["periodo_s"] - PER) <= 0.02, f"ciclo da cabeça {st['cabeca']['periodo_s']} s (esperado {PER})")
    ok(st["braco"]["sobe_desce_px"] <= 1.0 and st["pe"]["sobe_desce_px"] <= 1.0, "braço e pé parados (≤1 px)")
    cy = st["cabeca"].get("ciclo") or {}
    tol = 1 / (PER * FPS) + 0.02   # resolução: 1 quadro do ciclo (12 quadros → ±10%)
    ok(abs(cy.get("descendo", 0) - 0.25) <= tol and abs(cy.get("subindo", 0) - 0.40) <= tol
       and cy.get("subindo", 0) > cy.get("descendo", 1),
       f"fases do ciclo ~ desce 25% / sobe 40% (±1 quadro) e sobe mais devagar que desce (medido {cy})")
    pv("compare", vid, "--auto", out=out)
    ok(len(list(d.glob("compare_*.jpg"))) == 2, "2 imagens de comparação (extremos + mais rápido)")
    pv("note", vid, "teste: braço parado", out=out)
    pv("report", vid, out=out)
    rep = (d / "report.md").read_text()
    ok("PARADO" in rep and "NÃO dá pra afirmar" in rep and "teste: braço parado" in rep, "relatório com parado, limites e notas")
    again = pv("parts", vid, "--start", "0", "--end", "2.9", "--ref", "0.1", "--part", "cabeca:236:200:344:280",
               "--part", "braco:356:296:404:464", "--part", "pe:116:466:204:524", out=out)
    ok(again.startswith("[cache]"), "repetir o mesmo passo usa o cache (não refaz)")
    print(f"\nsaída em {tmp}")
    if erros:
        sys.exit(f"{len(erros)} verificação(ões) falharam")
    print("TUDO OK")


if __name__ == "__main__":
    main()
