#!/usr/bin/env bash
# Monta o projeto do Froggydude a partir do MDK do Forge 1.20.1 e gera os .jar.
#
# Uso, no terminal do Codespace (na pasta onde você descompactou o zip):
#   bash froggydude-mod/tools/setup-codespace.sh
#
# No fim, a pasta PRONTO-PRO-CELULAR tem os 2 arquivos que vão pro Zalith.

MDK_VERSION="1.20.1-47.4.10"
GECKO_VERSION="4.4.9"

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # pasta froggydude-mod
WORK_DIR="$(dirname "$SRC_DIR")"
PROJ="$WORK_DIR/froggydude-projeto"
OUT="$WORK_DIR/PRONTO-PRO-CELULAR"

say() { echo; echo "==> $*"; }
die() { echo; echo "ERRO: $*"; exit 1; }

# ---------------------------------------------------------------- 1. MDK
say "1/6 Procurando o MDK do Forge 1.20.1"
MDK_ZIP="$(find "$WORK_DIR" /workspaces -maxdepth 3 -iname 'forge-1.20.1-*-mdk*.zip' 2>/dev/null | head -n 1)"
if [ -z "$MDK_ZIP" ]; then
  echo "Não achei o zip do MDK aqui. Vou baixar do site do Forge."
  MDK_ZIP="$WORK_DIR/forge-$MDK_VERSION-mdk.zip"
  wget -q -O "$MDK_ZIP" \
    "https://maven.minecraftforge.net/net/minecraftforge/forge/$MDK_VERSION/forge-$MDK_VERSION-mdk.zip" \
    || die "Não consegui baixar o MDK. Coloque o arquivo forge-$MDK_VERSION-mdk.zip nesta pasta e rode de novo."
fi
echo "Usando: $MDK_ZIP"

# ---------------------------------------------------------------- 2. projeto
say "2/6 Criando o projeto novo (pasta froggydude-projeto)"
rm -rf "$PROJ"
mkdir -p "$PROJ"
unzip -q "$MDK_ZIP" -d "$PROJ" || die "Não consegui abrir o zip do MDK."
BG="$(find "$PROJ" -maxdepth 2 -name build.gradle | head -n 1)"
[ -n "$BG" ] || die "O MDK não parece certo (não tem build.gradle)."
PROJ="$(dirname "$BG")"

rm -rf "$PROJ/src/main/java/com/example"          # código de exemplo do MDK
mkdir -p "$PROJ/src/main/java" "$PROJ/src/main/resources"
cp -r "$SRC_DIR/src/main/java/com" "$PROJ/src/main/java/"
cp -r "$SRC_DIR/src/main/resources/assets" "$PROJ/src/main/resources/"

# ---------------------------------------------------------------- 3. ajustes
say "3/6 Ajustando gradle.properties, build.gradle e mods.toml"
python3 - "$PROJ" "$GECKO_VERSION" <<'PY' || die "Falhou ao ajustar os arquivos do projeto."
import os
import re
import sys

proj, gecko = sys.argv[1], sys.argv[2]


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


# gradle.properties: nome e id do mod
path = os.path.join(proj, "gradle.properties")
s = read(path)
for key, val in [("mod_id", "froggydude"), ("mod_name", "Froggydude"),
                 ("mod_group_id", "com.froggydude"), ("mod_authors", "Froggydude Mod"),
                 ("mod_description", "Chefe Froggydude")]:
    if re.search(r"(?m)^%s=" % key, s):
        s = re.sub(r"(?m)^%s=.*$" % key, lambda m, k=key, v=val: "%s=%s" % (k, v), s)
    else:
        s += "\n%s=%s\n" % (key, val)
write(path, s)

# build.gradle: GeckoLib (repositório + dependência)
path = os.path.join(proj, "build.gradle")
s = read(path)
if "geckolib" not in s:
    repo = ("    maven {\n"
            "        name = 'GeckoLib'\n"
            "        url 'https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/'\n"
            "        content { includeGroup('software.bernie.geckolib') }\n"
            "    }\n")
    dep = "    implementation fg.deobf('software.bernie.geckolib:geckolib-forge-1.20.1:%s')\n" % gecko
    s, n = re.subn(r"(?m)^repositories\s*\{[ \t]*\r?\n", lambda m: m.group(0) + repo, s, count=1)
    if n == 0:
        s += "\nrepositories {\n" + repo + "}\n"
    s, n = re.subn(r"(?m)^dependencies\s*\{[ \t]*\r?\n", lambda m: m.group(0) + dep, s, count=1)
    if n == 0:
        s += "\ndependencies {\n" + dep + "}\n"
write(path, s)

# mods.toml: avisa o Forge que o mod precisa do GeckoLib
path = os.path.join(proj, "src", "main", "resources", "META-INF", "mods.toml")
s = read(path)
if "geckolib" not in s:
    s = s.rstrip() + """

[[dependencies.${mod_id}]]
    modId="geckolib"
    mandatory=true
    versionRange="[4.4,)"
    ordering="NONE"
    side="BOTH"
"""
write(path, s)
print("ok")
PY

# ---------------------------------------------------------------- 4. Java 17
say "4/6 Conferindo o Java 17"
is17() { java -version 2>&1 | head -n 1 | grep -q '"17\.'; }
if ! is17; then
  [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ] && source "$HOME/.sdkman/bin/sdkman-init.sh"
  J17="$(ls -d "$HOME"/.sdkman/candidates/java/17* 2>/dev/null | head -n 1)"
  if [ -z "$J17" ] && command -v sdk >/dev/null 2>&1; then
    echo "Instalando o Java 17 (só na primeira vez)..."
    ID="$(sdk list java 2>/dev/null | grep -o '17\.[0-9.]*-tem' | head -n 1)"
    [ -n "$ID" ] && echo n | sdk install java "$ID"
    J17="$(ls -d "$HOME"/.sdkman/candidates/java/17* 2>/dev/null | head -n 1)"
  fi
  if [ -n "$J17" ]; then
    export JAVA_HOME="$J17"
    export PATH="$J17/bin:$PATH"
  fi
fi
is17 || die "Não achei o Java 17. Me mande o resultado de: java -version"
echo "Java 17 ok."

# ---------------------------------------------------------------- 5. build
say "5/6 Gerando o mod (na primeira vez demora vários minutos, é normal)"
cd "$PROJ" || die "Pasta do projeto sumiu."
chmod +x gradlew
./gradlew build || die "O build falhou. Copie as últimas linhas desta tela (as de erro) e me mande."

# ---------------------------------------------------------------- 6. arquivos
say "6/6 Separando os arquivos pro celular"
rm -rf "$OUT"
mkdir -p "$OUT"
for f in build/libs/*.jar; do
  case "$f" in
    *sources*|*javadoc*) ;;
    *) cp "$f" "$OUT/" ;;
  esac
done

GECKO_JAR="$(find "$HOME/.gradle/caches/modules-2" -name "geckolib-forge-1.20.1-$GECKO_VERSION.jar" 2>/dev/null | head -n 1)"
if [ -n "$GECKO_JAR" ]; then
  cp "$GECKO_JAR" "$OUT/"
else
  wget -q -O "$OUT/geckolib-forge-1.20.1-$GECKO_VERSION.jar" \
    "https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-forge-1.20.1/$GECKO_VERSION/geckolib-forge-1.20.1-$GECKO_VERSION.jar" \
    || { rm -f "$OUT/geckolib-forge-1.20.1-$GECKO_VERSION.jar"
         echo "Não consegui pegar o GeckoLib sozinho. Baixe no Modrinth: GeckoLib $GECKO_VERSION, Forge, 1.20.1."; }
fi

echo
echo "PRONTO. Baixe os arquivos da pasta: $OUT"
ls -la "$OUT"
