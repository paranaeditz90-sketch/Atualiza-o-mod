# Atualiza-o-mod: instruções pro Claude Code

Dois mods de Minecraft **1.20.1 / Forge 47.4.10 / GeckoLib 4.4.9 / Java 17** do LM:
- `froggydude-mod/`: o FroggyDude como mob com IA (fan-mod pessoal);
- `becomelm-mod/`: o jogador vira o FroggyDude pra gravar vídeo (privado, não é pra lançar).

O LM joga no **Android (Zalith Launcher 2 + MobileGlues)** e fala com o Claude pelo **app no celular**
(sessão na nuvem, sem terminal local). Responder em português, simples e direto.
Onde paramos: `PROGRESSO.md`. Plano: `PLANO.md`. Notas de cada versão (o LM testa por elas):
`froggydude-mod/NOTES.md`, `becomelm-mod/NOTES.md`.

## Regras do LM (sempre)

- Perguntar antes de aplicar mudança grande; uma coisa por vez; na dúvida, perguntar.
- **Nunca fazer commit nem push sem o OK explícito dele.** Só no ramo designado da sessão.
- O repo é **público**: voz e sons originais do FroggyDude e os sons do LM (Voicemod, CapCut)
  nunca entram aqui, só no pacote de sons pessoal dele. Som CC0 (com origem anotada) pode ir no mod.
  Quadros, áudio e transcrições de vídeo de referência ficam no scratchpad, nunca no repo.
- **Sem chaves, tokens ou segredos no Git** (vão em variável de ambiente da nuvem).
- Preservar os arquivos e as mudanças do LM.

## Skills do repo (`.claude/skills/`)

| Skill | Pra quê |
|---|---|
| `forge-expert` | código dos mods: Forge 47, GeckoLib 4.4.9, rede, render, animação, build |
| `zalith-compat` | celular: crash, skin sumindo, FPS, RAM, MobileGlues; antes de mexer em render |
| `video-motion-analysis` | vídeo → momentos, quadros, fala, pontos-chave → keyframes GeckoLib → comparação |
| `frontend-design` | páginas web/visualizadores, celular primeiro, validadas no Chromium |
| `quality-review` | revisão antes de dizer "pronto"; usa `/code-review` e `repo_guard.py` |
| `systematic-debugging`, `verification-before-completion`, `receiving-code-review` | 3 skills do Superpowers (MIT, só elas; o plugin não está instalado) |

## Comandos

```bash
cd froggydude-mod && ./gradlew build --no-daemon        # ou becomelm-mod; JDK 17 vem sozinho (foojay)
./gradlew runClient -PquickWorld=<mundo>                 # ver no jogo (Xvfb na nuvem)
python3 -m unittest discover -s .claude/tests -v         # testes das ferramentas do .claude/
python3 .claude/skills/quality-review/scripts/repo_guard.py   # regras do repo na mudança atual
bash .claude/scripts/install-whisperx.sh                 # transcrição local (~3 min, sob demanda)
python3 .claude/scripts/mcp_smoke.py                     # testa os MCPs do .mcp.json
```
Todo push que mexe num mod roda o GitHub Actions (`compile.yml`, `compile-becomelm.yml`) e gera os jars.

## Ambiente da nuvem (testado em 26/10/2026)

- `github.com` (páginas) é bloqueado; `git clone` de repo público e download de release funcionam.
- YouTube: metadados e legendas sim; o vídeo em si o YouTube recusa (403). TikTok funciona.
- Plugin `/watch` (claude-video) ligado em `.claude/settings.json`; o gancho de início recria a
  config dele. Transcrição sem legenda usa WhisperX local (instalar com o script acima).
- MCPs do projeto (`.mcp.json`): Context7, Playwright, Serena. Precisam ser aprovados na sessão.
  O Context7 só responde com a variável `CONTEXT7_API_KEY` (a cota anônima esgota).
