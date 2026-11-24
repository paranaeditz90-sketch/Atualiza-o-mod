"""Testes da configuração do Claude Code neste repo (.claude/ e .mcp.json).

Rodar: python3 -m unittest discover -s .claude/tests -v
"""
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLAUDE = ROOT / '.claude'
SECRET = re.compile(r'(ghp_|github_pat_|sk-(ant-)?[A-Za-z0-9]{16}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30}|hf_[A-Za-z0-9]{30}|'
                    r'xox[abposr]-|-----BEGIN [A-Z ]*PRIVATE KEY)')


def frontmatter(path: Path) -> dict:
    text = path.read_text(encoding='utf-8')
    m = re.match(r'^---\n(.*?)\n---\n', text, re.S)
    if not m:
        return {}
    out = {}
    for line in m.group(1).splitlines():
        key, _, value = line.partition(':')
        out[key.strip()] = value.strip()
    return out


class SkillsTest(unittest.TestCase):
    EXPECTED = {'forge-expert', 'zalith-compat', 'video-motion-analysis', 'frontend-design', 'quality-review',
                'systematic-debugging', 'verification-before-completion', 'receiving-code-review',
                'planejar', 'implementar'}
    VENDORED = {'systematic-debugging', 'verification-before-completion', 'receiving-code-review'}

    def test_every_skill_has_valid_frontmatter(self):
        found = set()
        for skill in sorted((CLAUDE / 'skills').glob('*/SKILL.md')):
            fm = frontmatter(skill)
            name = skill.parent.name
            found.add(name)
            with self.subTest(skill=name):
                self.assertEqual(fm.get('name'), name)
                self.assertRegex(fm['name'], r'^[a-z0-9-]{1,64}$')
                self.assertTrue(fm.get('description'))
                self.assertLessEqual(len(fm['description']), 1024)
        self.assertTrue(self.EXPECTED <= found, self.EXPECTED - found)

    def test_vendored_skills_keep_mit_license_and_attribution(self):
        for name in self.VENDORED:
            with self.subTest(skill=name):
                lic = (CLAUDE / 'skills' / name / 'LICENSE').read_text()
                self.assertIn('MIT License', lic)
                self.assertIn('Jesse Vincent', lic)
                self.assertIn('obra/superpowers', (CLAUDE / 'skills' / name / 'SKILL.md').read_text())

    def test_plan_and_implement_skills_are_manual_with_their_models(self):
        # o LM troca de etapa pelo /planejar e /implementar; o modelo da sessão não muda
        for name, model, effort in (('planejar', 'sonnet', 'high'), ('implementar', 'opus', 'max')):
            with self.subTest(skill=name):
                fm = frontmatter(CLAUDE / 'skills' / name / 'SKILL.md')
                self.assertEqual((fm.get('model'), fm.get('effort')), (model, effort))
                self.assertEqual(fm.get('disable-model-invocation'), 'true')
                self.assertNotIn('context', fm)  # roda no contexto da sessão, sem subagente
                self.assertIn('TAREFA.md', (CLAUDE / 'skills' / name / 'SKILL.md').read_text())
        settings = json.loads((CLAUDE / 'settings.json').read_text())
        self.assertNotIn('model', settings)  # as skills não mexem no modelo padrão

    def test_skill_scripts_referenced_exist(self):
        for skill in (CLAUDE / 'skills').glob('*/SKILL.md'):
            for ref in re.findall(r'\.claude/[\w./-]+\.(?:py|sh|mjs)', skill.read_text()):
                with self.subTest(skill=skill.parent.name, ref=ref):
                    self.assertTrue((ROOT / ref).is_file(), ref)


class SettingsAndHookTest(unittest.TestCase):
    def test_settings_json(self):
        s = json.loads((CLAUDE / 'settings.json').read_text())
        self.assertTrue(s['enabledPlugins']['watch@claude-video'])
        # o backend depende do container (gancho/instalador decidem); env do settings ganharia do .env
        self.assertNotIn('WATCH_WHISPER_BACKEND', s['env'])
        self.assertEqual(s['env']['WATCH_ENGINE'], 'local')
        cmd = s['hooks']['SessionStart'][0]['hooks'][0]['command']
        self.assertIn('.claude/hooks/session-start.sh', cmd)
        self.assertIsNone(SECRET.search(json.dumps(s)))

    def test_hook_is_fast_idempotent_and_has_no_keys(self):
        hook = CLAUDE / 'hooks' / 'session-start.sh'
        self.assertTrue(os.access(hook, os.X_OK))
        home = Path(tempfile.mkdtemp(prefix='hook_home_'))
        try:
            env = dict(os.environ, HOME=str(home), CLAUDE_CODE_REMOTE='true', CLAUDE_PROJECT_DIR=str(ROOT))
            t0 = time.time()
            res = subprocess.run([str(hook)], env=env, capture_output=True, text=True, timeout=30)
            self.assertLess(time.time() - t0, 3.0)
            self.assertEqual(res.returncode, 0, res.stderr)
            self.assertIn('Ferramentas:', res.stdout)
            conf = home / '.config' / 'watch' / '.env'
            text = conf.read_text()
            self.assertIn('SETUP_COMPLETE="true"', text)
            self.assertIn('WATCH_WHISPER_BACKEND="none"', text)  # sem WhisperX neste HOME vazio
            self.assertNotIn('KEY', text)
            self.assertEqual(oct(conf.stat().st_mode & 0o777), '0o600')
            subprocess.run([str(hook)], env=env, capture_output=True, text=True, timeout=30, check=True)
            self.assertEqual(conf.read_text(), text)  # idempotente
            local = dict(env, CLAUDE_CODE_REMOTE='')
            res = subprocess.run([str(hook)], env=local, capture_output=True, text=True, timeout=30)
            self.assertEqual((res.returncode, res.stdout), (0, ''))  # fora da nuvem não faz nada
        finally:
            shutil.rmtree(home, ignore_errors=True)

    def test_installer_relinks_watch_when_already_installed(self):
        venv = Path.home() / '.cache' / 'watch' / 'whisperx-venv'
        if not (venv / '.deps-ok').is_file():
            self.skipTest('WhisperX não instalado neste container')
        conf = Path.home() / '.config' / 'watch' / '.env'
        backup = conf.read_text() if conf.is_file() else None
        try:
            conf.parent.mkdir(parents=True, exist_ok=True)
            conf.write_text('WATCH_WHISPER_BACKEND="none"\n')  # config "resetada"
            res = subprocess.run(['bash', str(CLAUDE / 'scripts' / 'install-whisperx.sh')],
                                 capture_output=True, text=True, timeout=120)
            self.assertEqual(res.returncode, 0, res.stderr[-400:])
            text = conf.read_text()
            self.assertIn('WATCH_WHISPER_BACKEND="whisperx"', text)
            self.assertIn('SETUP_COMPLETE="true"', text)
        finally:
            if backup is not None:
                conf.write_text(backup)

    def test_whisperx_installer_check_mode(self):
        res = subprocess.run(['bash', str(CLAUDE / 'scripts' / 'install-whisperx.sh'), '--check'],
                             capture_output=True, text=True, timeout=60)
        self.assertIn(res.returncode, (0, 1))
        self.assertTrue(res.stdout.startswith(('pronto', 'nao instalado')), res.stdout)


class McpJsonTest(unittest.TestCase):
    def test_mcp_json_pinned_and_secret_free(self):
        data = json.loads((ROOT / '.mcp.json').read_text())
        servers = data['mcpServers']
        self.assertEqual(set(servers), {'context7', 'playwright', 'serena'})
        text = json.dumps(data)
        self.assertIsNone(SECRET.search(text))
        self.assertIn('@upstash/context7-mcp@', text)          # versão fixa, não "latest"
        self.assertIn('@playwright/mcp@', text)
        self.assertIn('serena-agent==', text)
        self.assertNotIn('@latest', text)
        key = servers['context7'].get('env', {}).get('CONTEXT7_API_KEY', '')
        self.assertTrue(key.startswith('${') or key == '', 'chave só por variável de ambiente')


class RepoGuardTest(unittest.TestCase):
    def test_guard_catches_planted_problems(self):
        tmp = Path(tempfile.mkdtemp(prefix='guard_'))
        try:
            run = lambda *a: subprocess.run(a, cwd=tmp, capture_output=True, text=True)  # noqa: E731
            run('git', 'init', '-q')
            guard = tmp / 'repo_guard.py'
            shutil.copy(CLAUDE / 'skills' / 'quality-review' / 'scripts' / 'repo_guard.py', guard)
            (tmp / 'froggydude-mod' / 'src').mkdir(parents=True)
            (tmp / 'froggydude-mod' / 'src' / 'A.java').write_text('class A {}\n')
            (tmp / 'froggydude-mod' / 'gradle.properties').write_text('mod_version=1.0.0\n')
            run('git', 'add', '-A')
            run('git', '-c', 'user.email=t@t', '-c', 'user.name=t', 'commit', '-qm', 'base')
            res = run('python3', str(guard))
            self.assertEqual(res.returncode, 0, res.stdout)
            (tmp / 'grito_ação.ogg').write_bytes(b'OggS' + b'\0' * 64)  # acento no nome
            (tmp / 'referência.mp4').write_bytes(b'\0' * 64)
            (tmp / 'transcript.json').write_text('[]')
            (tmp / 'quadro.jpg').write_bytes(b'\xff\xd8' + b'\0' * 64)
            (tmp / 'video.en.vtt').write_text('WEBVTT\n')
            fake = 'abcdefgh' * 2  # montado em pedaços: o próprio teste não pode parecer segredo pra guarda
            (tmp / 'env.txt').write_text('OPENAI_' + 'API_KEY="' + fake + '"\nHF_' + 'TOKEN: "' + fake + 'q"\n')
            (tmp / 'cfg.txt').write_text('token = "ghp_' + 'a' * 36 + '"\n')
            (tmp / 'froggydude-mod' / 'src' / 'A.java').write_text('class A { int x; }\n')
            res = run('python3', str(guard))
            self.assertEqual(res.returncode, 1, res.stdout)
            self.assertIn('áudio novo/alterado: grito_ação.ogg', res.stdout)
            self.assertIn('vídeo novo/alterado: referência.mp4', res.stdout)
            self.assertIn('transcrição: transcript.json', res.stdout)
            self.assertIn('imagem .jpg nova', res.stdout)
            self.assertIn('transcrição: video.en.vtt', res.stdout)
            self.assertIn('possível segredo atribuído: env.txt:1', res.stdout)
            self.assertIn('possível segredo atribuído: env.txt:2', res.stdout)
            self.assertIn('token do GitHub', res.stdout)
            self.assertIn('mod_version', res.stdout)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


@unittest.skipUnless(shutil.which('node') and Path('/opt/pw-browsers/chromium').exists(), 'precisa de node + chromium')
class PageCheckTest(unittest.TestCase):
    def test_good_page_passes_bad_page_fails(self):
        tmp = Path(tempfile.mkdtemp(prefix='page_'))
        try:
            good = tmp / 'good.html'
            good.write_text('<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">'
                            '<meta name="viewport" content="width=device-width,initial-scale=1"><title>Ok</title>'
                            '<style>body{margin:0;padding:16px;background:#fff}button,input{min-height:44px}</style>'
                            '</head><body><label>Nome <input></label><button>Salvar</button></body></html>')
            bad = tmp / 'bad.html'
            bad.write_text('<!doctype html><html><head><title></title></head><body>'
                           '<img src="x.png"><button></button><script>nope()</script></body></html>')
            script = CLAUDE / 'skills' / 'frontend-design' / 'scripts' / 'check_page.mjs'
            ok = subprocess.run(['node', str(script), str(good), str(tmp / 'o1')], capture_output=True, text=True, timeout=180)
            self.assertEqual(ok.returncode, 0, ok.stdout[-800:])
            ko = subprocess.run(['node', str(script), str(bad), str(tmp / 'o2')], capture_output=True, text=True, timeout=180)
            self.assertEqual(ko.returncode, 1)
            report = json.loads(ko.stdout)
            self.assertGreater(report['problems'], 4)
            self.assertTrue((tmp / 'o1' / 'celular-light.png').is_file())
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


if __name__ == '__main__':
    unittest.main()
