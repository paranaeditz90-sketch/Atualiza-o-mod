#!/usr/bin/env bash
# Instala o WhisperX (transcrição local, CPU) usado pelo plugin /watch e pelo pipeline
# video-motion-analysis, neste container Linux da sessão cloud.
#
# Por que não usar o "setup.py --install-whisperx" do plugin: no Linux ele quebra em dois pontos
# (o próprio plugin diz que o Linux não foi testado):
#   1. instala o torch CPU mas deixa o whisperx puxar o torchvision do PyPI (build CUDA), e o
#      aquecimento morre com "operator torchvision::nms does not exist";
#   2. o VAD silero vem por torch.hub.load('snakers4/silero-vad') sem ramo, e o torch consulta
#      github.com/.../tree/main pra descobrir o ramo; aqui o github.com (web) é bloqueado.
#      Conserto: clonar a tag fixa no cache do torch (o git anônimo passa pelo proxy) e fixar a
#      mesma tag no whisperx, que aí lê do cache sem tocar na rede.
# E se rodar o setup.py do plugin depois, ele apaga o venv e volta o erro 1. Então, mesmo que o
# /watch diga "Run setup.py --install-whisperx", use ESTE script.
# No fim, liga WATCH_WHISPER_BACKEND=whisperx em ~/.config/watch/.env (o gancho de início de sessão
# deixa em "none" enquanto não estiver instalado).
#
# Idempotente: se já estiver pronto, só confere. Uso:
#   bash .claude/scripts/install-whisperx.sh          # instala/confere (~2-4 min do zero)
#   bash .claude/scripts/install-whisperx.sh --check  # só diz se está pronto (exit 0/1)
# Pastas (podem ser trocadas por variável de ambiente, útil pra testar do zero):
#   WHISPERX_VENV (padrão ~/.cache/watch/whisperx-venv), TORCH_HUB_DIR (padrão ~/.cache/torch/hub)
set -euo pipefail

WHISPERX_VERSION=3.8.6
TORCH_VERSION=2.8.0
TORCHVISION_VERSION=0.23.0
SILERO_TAG=v6.2.3
MODEL="${WATCH_WHISPERX_MODEL:-small}"
CPU_INDEX=https://download.pytorch.org/whl/cpu

VENV="${WHISPERX_VENV:-$HOME/.cache/watch/whisperx-venv}"
HUB="${TORCH_HUB_DIR:-$HOME/.cache/torch/hub}"
SILERO_DIR="$HUB/snakers4_silero-vad_$SILERO_TAG"
PY="$VENV/bin/python"
BIN="$VENV/bin/whisperx"
SENTINEL="$VENV/.deps-ok"

say() { echo "[whisperx-setup] $*" >&2; }

ready() {
  [ -f "$SENTINEL" ] && [ -x "$BIN" ] && [ -f "$SILERO_DIR/hubconf.py" ] || return 1
  grep -q "snakers4/silero-vad:$SILERO_TAG'" "$VENV"/lib/python3*/site-packages/whisperx/vads/silero.py 2>/dev/null || return 1
  "$PY" -c 'import torchvision, sys; from torchvision.ops import nms; sys.exit(0 if torchvision.__version__.endswith("+cpu") else 1)' 2>/dev/null
}

link_watch() {
  # Liga a transcrição no /watch (só pro venv padrão, que é o que o plugin usa). Também roda quando
  # já estava pronto: conserta um ~/.config/watch/.env que alguém tenha resetado.
  [ "$VENV" = "$HOME/.cache/watch/whisperx-venv" ] || return 0
  local conf="$HOME/.config/watch/.env" kv k v
  mkdir -p "$(dirname "$conf")"
  [ -f "$conf" ] || { : > "$conf"; chmod 600 "$conf"; }
  for kv in "WATCH_WHISPER_BACKEND=whisperx" "WATCH_WHISPERX_BIN=$BIN" "SETUP_COMPLETE=true"; do
    k="${kv%%=*}"; v="${kv#*=}"
    if grep -q "^$k=" "$conf"; then sed -i "s|^$k=.*|$k=\"$v\"|" "$conf"; else echo "$k=\"$v\"" >> "$conf"; fi
  done
}

if [ "${1:-}" = "--check" ]; then
  if ready; then echo "pronto: $BIN"; exit 0; else echo "nao instalado (rode: bash .claude/scripts/install-whisperx.sh)"; exit 1; fi
fi

start=$(date +%s)
if ready; then
  link_watch
  say "já está pronto: $BIN"
  exit 0
fi

command -v ffmpeg >/dev/null || { say "falta o ffmpeg"; exit 1; }
UV="$(command -v uv || true)"
[ -n "$UV" ] || UV="$HOME/.local/bin/uv"
[ -x "$UV" ] || { say "falta o uv (https://docs.astral.sh/uv/)"; exit 1; }
PYTHON312="$(command -v python3.12 || true)"

# 1. venv Python 3.12 (o do sistema, se tiver; senão o uv tenta baixar um)
if [ ! -x "$PY" ]; then
  # Só apaga um venv quebrado (tem pyvenv.cfg) ou uma pasta vazia. Qualquer outra coisa nesse
  # caminho (ex.: WHISPERX_VENV apontado pra uma pasta de verdade por engano) faz parar, sem apagar.
  if [ -L "$VENV" ]; then
    say "$VENV é um link; escolha outra pasta"; exit 1
  elif [ -f "$VENV/pyvenv.cfg" ]; then
    rm -rf "$VENV"
  elif [ -d "$VENV" ] && [ -n "$(ls -A "$VENV")" ]; then
    say "$VENV existe e não é um venv; não vou apagar. Use outra pasta em WHISPERX_VENV."; exit 1
  fi
  mkdir -p "$(dirname "$VENV")"
  say "criando o venv em $VENV"
  "$UV" venv "$VENV" --python "${PYTHON312:-3.12}" >&2
fi

# 2. torch/torchaudio/torchvision, os três da build CPU
say "torch $TORCH_VERSION (CPU)"
"$UV" pip install --python "$PY" "torch==$TORCH_VERSION" "torchaudio==$TORCH_VERSION" \
  "torchvision==$TORCHVISION_VERSION" --index-url "$CPU_INDEX" >&2

# 3. whisperx; se ele trocar o torchvision pela build CUDA, volta pra CPU
say "whisperx $WHISPERX_VERSION"
"$UV" pip install --python "$PY" "whisperx==$WHISPERX_VERSION" >&2
if ! "$PY" -c 'import torchvision, sys; sys.exit(0 if torchvision.__version__.endswith("+cpu") else 1)'; then
  say "trocando o torchvision pela build CPU"
  "$UV" pip install --python "$PY" "torchvision==$TORCHVISION_VERSION" --index-url "$CPU_INDEX" \
    --reinstall-package torchvision >&2
fi

# 4. silero-vad com tag fixa no cache do torch.hub
if [ ! -f "$SILERO_DIR/hubconf.py" ]; then
  say "silero-vad $SILERO_TAG -> $SILERO_DIR"
  mkdir -p "$HUB"
  rm -rf "$SILERO_DIR"
  GIT_LFS_SKIP_SMUDGE=1 git clone --quiet --depth 1 --branch "$SILERO_TAG" \
    https://github.com/snakers4/silero-vad "$SILERO_DIR" 2>&1 | grep -v detached >&2 || true
  [ -f "$SILERO_DIR/hubconf.py" ] || { say "não consegui clonar o silero-vad"; exit 1; }
fi
SILERO_PY="$(ls "$VENV"/lib/python3*/site-packages/whisperx/vads/silero.py)"
sed -i "s#repo_or_dir='snakers4/silero-vad'#repo_or_dir='snakers4/silero-vad:$SILERO_TAG'#" "$SILERO_PY"
grep -q "snakers4/silero-vad:$SILERO_TAG'" "$SILERO_PY" || { say "não consegui fixar a tag no whisperx"; exit 1; }

# 5. aquecimento: baixa o modelo (Hugging Face) e roda o caminho inteiro num áudio mudo
say "aquecendo (modelo $MODEL)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
ffmpeg -hide_banner -loglevel error -y -f lavfi -i anullsrc=r=16000:cl=mono -t 2 "$tmp/mudo.wav"
TORCH_HOME="$(dirname "$HUB")" PYTHONWARNINGS=ignore "$BIN" "$tmp/mudo.wav" --model "$MODEL" --device cpu \
  --compute_type int8 --output_dir "$tmp" --output_format json --no_align --vad_method silero \
  --verbose False >"$tmp/warm.log" 2>&1 || { tail -20 "$tmp/warm.log" >&2; say "o aquecimento falhou"; exit 1; }

echo "$WHISPERX_VERSION" > "$SENTINEL"
link_watch
say "pronto em $(( $(date +%s) - start )) s: $BIN"
