#!/bin/bash
# Gancho de início de sessão (Claude Code na nuvem). Rápido de propósito (~30 ms): nada de baixar
# gigabytes a cada sessão. O que é pesado (WhisperX, build do Gradle) fica sob demanda.
#  1. Recria ~/.config/watch/.env (o container é novo a cada sessão) pra o plugin /watch não
#     abrir o questionário de primeira vez. SEM chaves. Só o que depende deste container:
#     SETUP_COMPLETE, o caminho do WhisperX e o backend (whisperx só se estiver instalado; senão
#     none, pra o plugin não mandar rodar o setup.py dele, que quebra no Linux). As escolhas fixas
#     (motor, detalhe, modelo) ficam no env do .claude/settings.json.
#  2. Diz numa linha o que está pronto (ffmpeg, yt-dlp, uv, WhisperX).
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

CONF_DIR="$HOME/.config/watch"
CONF="$CONF_DIR/.env"
VENV="$HOME/.cache/watch/whisperx-venv"
mkdir -p "$CONF_DIR"
[ -f "$CONF" ] || { : > "$CONF"; chmod 600 "$CONF"; }
set_key() {  # troca o valor se a chave existe, senão acrescenta; não mexe nas outras linhas
  if grep -q "^$1=" "$CONF"; then
    sed -i "s|^$1=.*|$1=\"$2\"|" "$CONF"
  else
    echo "$1=\"$2\"" >> "$CONF"
  fi
}

# Conferência rápida (só arquivos); a completa, que importa o torch (~5 s), é install-whisperx.sh --check
silero_ok=false
for f in "$HOME"/.cache/torch/hub/snakers4_silero-vad_v*/hubconf.py; do [ -f "$f" ] && silero_ok=true; done
if [ -f "$VENV/.deps-ok" ] && [ -x "$VENV/bin/whisperx" ] && $silero_ok; then
  backend=whisperx
  wx="pronto"
else
  backend=none
  wx="nao instalado (pra transcrever video sem legenda: bash .claude/scripts/install-whisperx.sh, ~3 min)"
fi
set_key SETUP_COMPLETE true
set_key WATCH_WHISPERX_BIN "$VENV/bin/whisperx"
set_key WATCH_WHISPER_BACKEND "$backend"

have() { command -v "$1" >/dev/null 2>&1 && echo ok || echo FALTA; }
echo "Ferramentas: ffmpeg $(have ffmpeg), ffprobe $(have ffprobe), yt-dlp $(have yt-dlp), uv $(have uv), node $(have node); WhisperX $wx."
