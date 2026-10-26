"""Testes do pipeline video-motion-analysis (vma.py + rigfit.py).

Vídeo sintético com resposta conhecida: 0-1 s parado, 1-2,5 s um bloco correndo, corte seco em
2,5 s pra outra cena parada, bipe de 1 kHz em 3,0-3,2 s. E circuito fechado do esqueleto: pontos
projetados de uma animação real do repo viram a "referência perfeita".
Rodar: python3 -m unittest discover -s .claude/tests -v
"""
import copy
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / '.claude' / 'skills' / 'video-motion-analysis' / 'scripts'
sys.path.insert(0, str(SCRIPTS))
import rigfit  # noqa: E402
import vma  # noqa: E402

LM_ANIMS = ROOT / 'becomelm-mod' / 'src' / 'main' / 'resources' / 'assets' / 'becomelm' / 'animations' / 'lm.animation.json'
FPS, W, H, DUR = 30, 320, 240, 4.0


def synth_video(path: Path):
    frames = []
    for i in range(int(DUR * FPS)):
        t = i / FPS
        img = np.full((H, W, 3), (90, 140, 200) if t < 2.5 else (40, 120, 40), dtype=np.uint8)
        if t < 2.5:
            x = 20 if t < 1.0 else int(20 + (min(t, 2.5) - 1.0) / 1.5 * 240)
            img[100:150, x:x + 40] = (230, 60, 40)
        frames.append(img)
    cmd = ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24',
           '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-', '-f', 'lavfi', '-i', f'sine=f=1000:d={DUR}',
           '-af', "volume='if(between(t,3.0,3.2),1,0)':eval=frame", '-shortest',
           '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', str(path)]
    subprocess.run(cmd, input=b''.join(f.tobytes() for f in frames), check=True)


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'precisa de ffmpeg')
class VideoSideTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp(prefix='vma_test_'))
        cls.video = cls.tmp / 'synth.mp4'
        synth_video(cls.video)
        cls.info = vma.probe(cls.video)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def test_probe(self):
        v = self.info['video']
        self.assertAlmostEqual(v['fps'], FPS, places=2)
        self.assertAlmostEqual(self.info['duration_s'], DUR, delta=0.15)
        self.assertAlmostEqual(v['frames'], DUR * FPS, delta=3)
        self.assertFalse(v['vfr'])
        self.assertIsNotNone(self.info['audio'])

    def test_scan_finds_motion_cut_and_beep(self):
        out = self.tmp / 'scan'
        out.mkdir()
        res = vma.scan(self.video, self.info, out, vma.Stats())
        self.assertEqual(res['scan_fps'], FPS)
        self.assertTrue(any(abs(c - 2.5) <= 0.05 for c in res['cuts']), res['cuts'])
        self.assertEqual(len(res['cuts']), 1, res['cuts'])
        moving = [iv for iv in res['intervals'] if iv['t0'] < 2.4 and iv['t1'] > 1.1]
        self.assertTrue(moving, res['intervals'])
        self.assertLess(abs(moving[0]['t0'] - 1.0), 0.35)
        self.assertLess(abs(moving[0]['t1'] - 2.5), 0.35)
        self.assertTrue(any(abs(e['t'] - 3.0) <= 0.1 for e in res['audio_events']), res['audio_events'])
        # cena parada não pode ser confundida com câmera lenta
        self.assertLess(res['repeats']['duplicate_ratio'], 0.2, res['repeats'])
        for name in ('motion.csv', 'audio.csv', 'intervals.csv', 'intervals.json'):
            self.assertTrue((out / name).is_file(), name)

    def test_adaptive_extraction_exact_timestamps(self):
        out = self.tmp / 'run'
        rc = vma.main(['run', str(self.video), '--out', str(out), '--start', '0.5', '--end', '3.5',
                       '--budget', '40', '--n', '6'])
        self.assertEqual(rc, 0)
        frames = json.loads((out / 'frames.json').read_text())
        self.assertLessEqual(len(frames), 40)
        self.assertGreater(len(frames), 10)
        for f in frames:  # pts exato no grid de 1/30 s e dentro do trecho
            self.assertAlmostEqual(f['t'] * FPS, round(f['t'] * FPS), delta=0.01)
            self.assertTrue(0.5 - 1e-6 <= f['t'] <= 3.5 + 1e-6, f['t'])
            self.assertTrue(Path(f['file']).is_file())
        moving = sum(1 for f in frames if 1.1 <= f['t'] < 2.4) / 1.3
        still = sum(1 for f in frames if 0.5 <= f['t'] < 0.95) / 0.45
        self.assertGreater(moving, 2 * still, (moving, still))  # denso onde mexe
        self.assertTrue(any(abs(f['t'] - 2.5) < 0.05 for f in frames), 'quadro logo após o corte')
        report = json.loads((out / 'report.json').read_text())
        self.assertIn('observations', report)
        self.assertIn('estimates', report)
        self.assertGreater(report['stats']['frames_total'], 0)
        self.assertGreater(report['stats']['total_seconds'], 0)
        self.assertTrue((out / 'overview.jpg').is_file())
        self.assertTrue(list(out.glob('sheet_*.jpg')))
        self.assertTrue((out / 'annotation_template.json').is_file())

    def test_refuses_output_inside_repo(self):
        with self.assertRaises(SystemExit) as ctx:
            vma.main(['probe', str(self.video), '--out', str(ROOT / 'vma_out_teste')])
        self.assertIn('dentro do repo', str(ctx.exception))
        self.assertFalse((ROOT / 'vma_out_teste').exists())

    def test_budget_thins_evenly_without_dropping_the_end(self):
        rows = [{'t': i / 30, 'motion': 0.05, 'coverage': 0.3, 'hist_diff': 0.0} for i in range(30 * 30)]
        cuts = [5.0, 12.0, 20.0, 27.0]
        offs = vma.adaptive_offsets(rows, 30.0, 60, 0.0, cuts)
        self.assertLessEqual(len(offs), 60)
        self.assertGreater(max(offs) / 30.0, 28.0)  # ainda tem quadro perto do fim (30 s)
        for c in cuts:  # e o quadro logo após cada corte
            self.assertIn(int(round(c * 30)), offs)

    def test_transcribe_without_audio_is_reported_not_raised(self):
        silent = self.tmp / 'mudo.mp4'
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i',
                        'testsrc2=size=160x120:rate=10', '-t', '1', '-an', str(silent)], check=True)
        info = vma.probe(silent)
        res = vma.transcribe(silent, self.tmp, vma.Stats(), info=info)
        self.assertNotIn('error', res)
        self.assertEqual(res.get('segments', []), [])

    def test_ffmpeg_failure_is_an_error_not_empty_success(self):
        with self.assertRaises(SystemExit) as ctx:  # recorte fora da imagem
            vma.motion_signal(self.video, self.info, vma.Stats(), crop='400:300:900:0')
        self.assertIn('ffmpeg', str(ctx.exception))


@unittest.skipUnless(LM_ANIMS.is_file(), 'precisa do lm.animation.json do BecomeLM')
class RigSideTest(unittest.TestCase):
    """Circuito fechado: a referência é a própria animação projetada, então a resposta é conhecida."""

    @classmethod
    def setUpClass(cls):
        cls.anim = rigfit.load_anim(f'{LM_ANIMS}:pile_lie')
        cls.tmp = Path(tempfile.mkdtemp(prefix='rigfit_test_'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def make_ann(self, anim, cam=None, times=None, t0=10.0, scale=1.0, hidden=(), code=None):
        cam = cam or {'az': 90, 'elev': 10}
        camera = rigfit.make_camera(cam)
        frames = []
        for t in times or [i * 0.3 for i in range(9)]:
            probe_ann = {'code_rotations': code or []}
            pts = rigfit.project(rigfit.rig_points(rigfit.pose_at(probe_ann, anim, t)), camera)
            frames.append({'t': round(t0 + t / scale, 4),
                           'keypoints': {k: [round(v[0], 2), round(v[1], 2)] for k, v in pts.items()},
                           'hidden': list(hidden)})
        return {'camera': cam, 'anim_t0': t0, 'frames': frames}

    def test_same_animation_matches(self):
        res = rigfit.compare_anim(self.make_ann(self.anim), self.anim, {'az': 90, 'elev': 10})
        self.assertLess(res['mean_abs_deg'], 0.5)

    def test_second_round_fixes_perturbation(self):
        ann = self.make_ann(self.anim, hidden=['shoulder_l', 'hand_l', 'knee_l', 'foot_l'])
        bad = copy.deepcopy(self.anim)
        for v in bad['bones']['waist']['rotation'].values():
            v[0] += 15
        for v in bad['bones']['right_arm']['rotation'].values():
            v[0] -= 20
        new, before, after, changed = rigfit.correct(ann, bad, tol=2.0)
        self.assertGreater(before['mean_abs_deg'], 5.0)
        self.assertLess(after['mean_abs_deg'], 1.0)
        self.assertTrue(changed)
        self.assertNotIn('left_arm', {b for c in changed for b in c['residual_deg']})
        length = float(new['animation_length'])
        for chans in new['bones'].values():  # nenhum keyframe fora da animação
            for key in chans.get('rotation', {}):
                self.assertTrue(0.0 <= float(key) <= length + 1e-6, key)

    def test_warp_correction_keeps_keys_inside_animation(self):
        ann = self.make_ann(self.anim, times=[0.0, 1.2, 2.4])
        bad = copy.deepcopy(self.anim)
        for v in bad['bones']['waist']['rotation'].values():
            v[0] += 25
        warped, _ = rigfit.warp(ann, bad)
        for f in warped['frames']:  # força tempos fora da animação, como o warp pode achar
            f['t_anim'] = -0.05 if f['t'] == ann['frames'][0]['t'] else f['t_anim']
        new, _, _, _ = rigfit.correct(warped, bad, tol=1.0)
        for chans in new['bones'].values():
            for key in chans.get('rotation', {}):
                self.assertGreaterEqual(float(key), 0.0)

    def test_outputs_refuse_repo(self):
        with self.assertRaises(SystemExit):
            rigfit.outside_repo(ROOT / 'qualquer')

    def test_plan_only_keys_visible_bones(self):
        ann = self.make_ann(self.anim, hidden=['shoulder_l', 'hand_l', 'knee_l', 'foot_l', 'head_top'])
        path = self.tmp / 'ann.json'
        path.write_text(json.dumps(ann))
        out = self.tmp / 'plan.json'
        rc = rigfit.main(['plan', str(path), '--out', str(out), '--emit', str(self.tmp / 'p.animation.json')])
        self.assertEqual(rc, 0)
        plan = json.loads(out.read_text())
        for kf in plan['keyframes']:
            self.assertNotIn('left_arm', kf['fitted_bones'])  # escondido = sem keyframe
            self.assertNotIn('head', kf['fitted_bones'])
            self.assertIn('left_arm', kf['no_evidence_bones'])
            self.assertLess(max(abs(v) for v in kf['residual_deg'].values()), 2.0)
        emitted = json.loads((self.tmp / 'p.animation.json').read_text())
        self.assertEqual(emitted['format_version'], '1.8.0')
        self.assertNotIn('left_arm', emitted['animations']['vma_plan']['bones'])

    def test_plan_skips_frames_before_anim_t0(self):
        ann = self.make_ann(self.anim, times=[0.0, 0.3, 0.6])
        ann['anim_t0'] = 10.2  # o 1º quadro (10.0) fica antes do início
        path = self.tmp / 'ann_early.json'
        path.write_text(json.dumps(ann))
        out = self.tmp / 'plan_early.json'
        rigfit.main(['plan', str(path), '--out', str(out)])
        plan = json.loads(out.read_text())
        self.assertEqual(len(plan['skipped']), 1)
        self.assertTrue(all(k['t'] >= 0 for k in plan['keyframes']))

    def test_align_recovers_slow_motion(self):
        ann = self.make_ann(self.anim, t0=5.0, scale=0.5)  # referência 2x mais lenta
        ann['anim_t0'] = 5.12  # começo chutado errado
        aligned, search = rigfit.align(ann, self.anim)
        self.assertAlmostEqual(search['time_scale'], 0.5, delta=0.051)
        self.assertAlmostEqual(aligned['anim_t0'], 5.0, delta=0.03)
        self.assertLess(search['mean_abs_deg'], 1.5)

    def test_warp_is_monotonic(self):
        times = [0.0, 0.2, 0.25, 0.7, 1.2]  # tempo irregular
        ann = self.make_ann(self.anim, times=times, t0=0.0)
        for f, t in zip(ann['frames'], [0.0, 0.1, 0.2, 0.3, 0.4]):  # vídeo com tempo "errado"
            f['t'] = t
        warped, search = rigfit.warp(ann, self.anim)
        self.assertEqual(search['t_anim'], sorted(search['t_anim']))
        self.assertLess(search['mean_abs_deg'], 2.0)

    def test_code_rotations_are_accounted(self):
        code = [{'bone': 'root', 'axis': 0, 't0': 0.0, 't1': 1.0, 'from': 0, 'to': -360, 'ease': 'smoothstep'}]
        flip = rigfit.load_anim(f'{LM_ANIMS}:flip')
        ann = self.make_ann(flip, times=[0.1, 0.2, 0.3, 0.4], t0=0.0, code=code)
        ann['code_rotations'] = code
        with_code = rigfit.compare_anim(ann, flip, ann['camera'])
        ann['code_rotations'] = []
        without = rigfit.compare_anim(ann, flip, ann['camera'])
        self.assertLess(with_code['mean_abs_deg'], 0.5)
        self.assertGreater(without['mean_abs_deg'], 20.0)

    def test_excluded_frames_are_ignored(self):
        ann = self.make_ann(self.anim)
        ann['frames'][0]['exclude'] = 'transição'
        ann['frames'][0]['keypoints']['neck'] = [0, 0]  # lixo que não pode pesar
        res = rigfit.compare_anim(ann, self.anim, ann['camera'])
        self.assertEqual(len(res['frames']), len(ann['frames']) - 1)
        self.assertLess(res['mean_abs_deg'], 0.5)


if __name__ == '__main__':
    unittest.main()
