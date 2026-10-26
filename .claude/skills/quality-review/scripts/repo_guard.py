#!/usr/bin/env python3
"""Guarda das regras deste repo sobre uma mudança (o que um revisor genérico não sabe).

Uso (na raiz do repo):
    python3 .claude/skills/quality-review/scripts/repo_guard.py            # mudanças não commitadas (+ arquivos novos)
    python3 .claude/skills/quality-review/scripts/repo_guard.py --base origin/main   # tudo desde que o ramo saiu

Verifica:
  ERRO   segredo (token/chave) em arquivo novo ou alterado
  ERRO   arquivo de áudio novo (o repo é público: voz/sons do FroggyDude e do LM nunca entram;
         só som CC0 com a origem anotada, e aí o LM confirma e a gente marca como revisado)
  ERRO   vídeo novo (leva a voz) ou transcrição (transcript/speech/captions .json/.srt/.vtt/.txt)
  ERRO   arquivo maior que 5 MB
  AVISO  .jpg novo (provável quadro de vídeo de referência; texturas dos mods são .png)
  AVISO  código de um mod mudou e o mod_version do gradle.properties não
  AVISO  pacote de rede mudou e PROTOCOL/VERSION do canal não
  AVISO  código de um mod mudou e o NOTES.md dele não
Sai com 1 se houver ERRO, 0 caso contrário. Só usa a biblioteca padrão.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

AUDIO = re.compile(r'\.(ogg|wav|mp3|m4a|flac|aac|opus)$', re.I)
VIDEO = re.compile(r'\.(mp4|webm|mov|mkv|avi|m4v)$', re.I)  # vídeo leva a voz junto
# legenda (.srt/.vtt/.ass) é sempre fala; e qualquer arquivo com transcript/speech/caption no nome
TRANSCRIPT = re.compile(r'\.(srt|vtt|ass)$|(transcri|speech|caption)[^/]*\.(json|txt|csv)$', re.I)
FRAME = re.compile(r'\.(jpe?g)$', re.I)  # quadro de vídeo extraído (as texturas dos mods são .png)
SECRETS = [
    ('token do GitHub', re.compile(r'\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}')),
    ('chave da OpenAI/Anthropic', re.compile(r'\bsk-(ant-)?[A-Za-z0-9_-]{20,}')),
    ('chave AWS', re.compile(r'\bAKIA[0-9A-Z]{16}\b')),
    ('chave do Google', re.compile(r'\bAIza[0-9A-Za-z_-]{35}\b')),
    ('token do Slack', re.compile(r'\bxox[abposr]-[A-Za-z0-9-]{10,}')),
    ('token do Hugging Face', re.compile(r'\bhf_[A-Za-z0-9]{30,}\b')),
    ('chave privada', re.compile(r'-----BEGIN [A-Z ]*PRIVATE KEY-----')),
    # sem \b: "_" conta como letra pro \b e aí OPENAI_API_KEY, HF_TOKEN, client_secret escapariam
    ('segredo atribuído', re.compile(
        r'(?i)[A-Za-z0-9_]*(api[_-]?key|secret|token|password|senha)[A-Za-z0-9_]*["\']?\s*[:=]\s*'
        r'["\'][^"\'\s$<>{}]{12,}["\']')),
]
MAX_BYTES = 5 * 1024 * 1024
MODS = ('froggydude-mod', 'becomelm-mod')
NETWORK = {'froggydude-mod': 'froggydude-mod/src/main/java/com/froggydude/network/',
           'becomelm-mod': 'becomelm-mod/src/main/java/com/becomelm/network/'}
CHANNEL_FILE = {'froggydude-mod': 'froggydude-mod/src/main/java/com/froggydude/network/ModNetwork.java',
                'becomelm-mod': 'becomelm-mod/src/main/java/com/becomelm/network/LmNetwork.java'}
CHANNEL_VERSION = re.compile(r'(PROTOCOL|VERSION)\s*=\s*"')


def git(*args: str) -> str:
    return subprocess.run(['git', *args], capture_output=True, text=True, check=True).stdout


def base_ref(base: str | None) -> str:
    """Com --base, compara contra o ponto onde o ramo saiu (merge-base), não contra o estado
    atual da base: o que entrou no main depois não é mudança deste ramo."""
    return git('merge-base', base, 'HEAD').strip() if base else 'HEAD'


def changed_files(base: str | None) -> dict[str, str]:
    """{caminho: status} com A/M/D/R; inclui arquivos novos não rastreados. Usa -z: sem isso o git
    põe aspas e escapes octais em nome com acento e o arquivo escaparia da checagem."""
    out = git('diff', '--name-status', '-z', '-M', base_ref(base))
    parts = out.split('\0')
    files, i = {}, 0
    while i < len(parts) and parts[i]:
        status = parts[i]
        if status[0] in 'RC':  # renomeado/copiado: status, antigo, novo
            files[parts[i + 2]] = status[0]
            i += 3
        else:
            files[parts[i + 1]] = status[0]
            i += 2
    for path in git('ls-files', '-z', '--others', '--exclude-standard').split('\0'):
        if path:
            files[path] = 'A'
    return files


def diff_text(path: str, base: str | None) -> str:
    return git('diff', '-U0', base_ref(base), '--', path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--base', help='ref de comparação (padrão: HEAD + não rastreados)')
    args = parser.parse_args()
    root = Path(git('rev-parse', '--show-toplevel').strip())
    files = changed_files(args.base)
    errors, warnings = [], []

    for path, status in sorted(files.items()):
        if status == 'D':
            continue
        full = root / path
        if not full.is_file():
            continue
        if AUDIO.search(path):
            errors.append(f'áudio novo/alterado: {path} (só CC0 com origem anotada, e com OK do LM)')
        if VIDEO.search(path):
            errors.append(f'vídeo novo/alterado: {path} (leva a voz junto; referência fica no scratchpad)')
        if TRANSCRIPT.search(path):
            errors.append(f'transcrição: {path} (fala do FroggyDude/LM não entra no repo público)')
        if FRAME.search(path):
            warnings.append(f'imagem .jpg nova: {path} (se for quadro de vídeo de referência, tira do repo)')
        if full.stat().st_size > MAX_BYTES:
            errors.append(f'arquivo grande ({full.stat().st_size // 1024} KB): {path}')
        try:
            text = full.read_text(encoding='utf-8')
        except (UnicodeDecodeError, OSError):
            continue
        for label, pattern in SECRETS:
            for match in pattern.finditer(text):
                line = text.count('\n', 0, match.start()) + 1
                errors.append(f'possível {label}: {path}:{line}')

    for mod in MODS:
        code = [p for p in files if p.startswith(f'{mod}/src/')]
        if not code:
            continue
        if f'{mod}/gradle.properties' not in files or 'mod_version' not in diff_text(f'{mod}/gradle.properties', args.base):
            warnings.append(f'{mod}: código mudou ({len(code)} arquivos) e o mod_version não')
        if f'{mod}/NOTES.md' not in files:
            warnings.append(f'{mod}: código mudou e o NOTES.md não (o LM lê as notas pra testar)')
        net = [p for p in code if p.startswith(NETWORK[mod])]
        if net and not CHANNEL_VERSION.search(diff_text(CHANNEL_FILE[mod], args.base)):
            warnings.append(f'{mod}: rede mudou ({", ".join(Path(p).name for p in net)}) e a versão do canal não')

    print(f'repo_guard: {len(files)} arquivos na mudança')
    for item in errors:
        print(f'ERRO   {item}')
    for item in warnings:
        print(f'AVISO  {item}')
    if not errors and not warnings:
        print('ok: nada a apontar')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
