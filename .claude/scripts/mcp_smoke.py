#!/usr/bin/env python3
"""Teste de fumaça dos servidores MCP do .mcp.json (stdio): sobe o servidor do jeito que o Claude
Code sobe, faz initialize -> tools/list -> uma chamada real, e mede o tempo.

Uso (na raiz do repo):
    python3 .claude/scripts/mcp_smoke.py                 # todos do .mcp.json
    python3 .claude/scripts/mcp_smoke.py playwright      # só um
A chamada real de cada um está em CALLS. Sai com 1 se algum falhar.
"""
from __future__ import annotations

import json
import os
import queue
import re
import shlex
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# Uma chamada real por servidor (o que provar que ele funciona de verdade, não só que abriu).
CALLS = {
    'context7': ('resolve-library-id', {'libraryName': 'geckolib', 'query': 'GeckoLib 4 animation controller'}),
    'playwright': ('browser_navigate', {'url': 'data:text/html,<title>ok</title><h1>MCP Playwright ok</h1>'}),
    # exige o servidor de linguagem Java de verdade (não só o processo da Serena)
    'serena': ('get_symbols_overview', {'relative_path': 'becomelm-mod/src/main/java/com/becomelm/network/LmNetwork.java'}),
}


class Client:
    def __init__(self, cmd, env, cwd):
        self.proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                     env=env, cwd=cwd, text=True, bufsize=1)
        self.out = queue.Queue()
        self.err = []
        threading.Thread(target=self._read, daemon=True).start()
        threading.Thread(target=lambda: self.err.extend(self.proc.stderr), daemon=True).start()
        self.next_id = 0

    def _read(self):
        for line in self.proc.stdout:
            line = line.strip()
            if line.startswith('{'):
                self.out.put(json.loads(line))

    def request(self, method, params, timeout):
        self.next_id += 1
        rid = self.next_id
        self.proc.stdin.write(json.dumps({'jsonrpc': '2.0', 'id': rid, 'method': method, 'params': params}) + '\n')
        self.proc.stdin.flush()
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                msg = self.out.get(timeout=max(0.1, deadline - time.time()))
            except queue.Empty:
                break
            if msg.get('id') == rid:
                if 'error' in msg:
                    raise RuntimeError(f'{method}: {msg["error"]}')
                return msg['result']
        raise TimeoutError(f'{method}: sem resposta em {timeout} s; stderr: {"".join(self.err)[-800:]}')

    def notify(self, method):
        self.proc.stdin.write(json.dumps({'jsonrpc': '2.0', 'method': method}) + '\n')
        self.proc.stdin.flush()

    def close(self):
        try:
            self.proc.stdin.close()
            self.proc.wait(timeout=10)
        except Exception:  # noqa: BLE001 - servidor teimoso: mata
            self.proc.kill()


def expand(value: str) -> str:
    """${VAR} e ${VAR:-padrão}, como o Claude Code expande o .mcp.json."""
    env = dict(os.environ, CLAUDE_PROJECT_DIR=str(ROOT))
    return re.sub(r'\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?\}',
                  lambda m: env.get(m.group(1)) or (m.group(2) or ''), value)


def smoke(name, spec, timeout=240):
    env = dict(os.environ, CLAUDE_PROJECT_DIR=str(ROOT))
    env.update({k: expand(v) for k, v in spec.get('env', {}).items()})
    cmd = [expand(spec['command'])] + [expand(a) for a in spec.get('args', [])]
    t0 = time.time()
    cli = Client(cmd, env, str(ROOT))
    result = {'server': name, 'command': ' '.join(shlex.quote(c) for c in cmd)}
    try:
        init = cli.request('initialize', {'protocolVersion': '2025-06-18', 'capabilities': {},
                                          'clientInfo': {'name': 'mcp_smoke', 'version': '1'}}, timeout)
        cli.notify('notifications/initialized')
        result['server_info'] = init.get('serverInfo')
        result['startup_s'] = round(time.time() - t0, 1)
        tools = cli.request('tools/list', {}, 60)['tools']
        result['tools'] = len(tools)
        result['tool_names'] = sorted(t['name'] for t in tools)[:40]
        tool, args = CALLS[name]
        t1 = time.time()
        # a 1ª chamada da Serena em Java importa os projetos Gradle (ForgeGradle): pode levar minutos
        res = cli.request('tools/call', {'name': tool, 'arguments': args},
                          float(os.environ.get('MCP_SMOKE_CALL_TIMEOUT', '120')))
        text = ' '.join(c.get('text', '') for c in res.get('content', []) if c.get('type') == 'text')
        result['call'] = {'tool': tool, 'is_error': bool(res.get('isError')), 'seconds': round(time.time() - t1, 1),
                          'text_start': text[:400]}
        # "funcionou" = respondeu algo útil, não só "não deu erro de protocolo"
        refused = re.search(r'quota exceeded|rate limit|unauthori[sz]ed|invalid api key|forbidden', text, re.I)
        result['ok'] = not res.get('isError') and not refused
        if refused:
            result['error'] = f'o serviço recusou: "{refused.group(0)}" (chave/cota)'
    except Exception as exc:  # noqa: BLE001 - relatório, não trava os outros
        result['ok'] = False
        result['error'] = str(exc)[:1200]
    finally:
        cli.close()
    result['total_s'] = round(time.time() - t0, 1)
    return result


def main():
    servers = json.loads((ROOT / '.mcp.json').read_text())['mcpServers']
    names = sys.argv[1:] or list(servers)
    results = [smoke(n, servers[n]) for n in names]
    print(json.dumps(results, indent=1, ensure_ascii=False))
    return 0 if all(r['ok'] for r in results) else 1


if __name__ == '__main__':
    sys.exit(main())
