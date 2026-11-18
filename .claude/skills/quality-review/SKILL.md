---
name: quality-review
description: Revisão independente de uma mudança neste repo antes de dizer "pronto" ou pedir pra commitar — confere cada pedido do LM contra o diff, procura bugs e regressões, roda build/testes e as regras do repo (áudio, segredos, versão, protocolo de rede, notas) e usa o /code-review quando disponível. Use ao terminar qualquer tarefa com código, antes de entregar jar/versão, ou quando o LM pedir revisão.
---

# Revisão de qualidade (independente, com evidência)

Revise como quem **não escreveu** o código: desconfie do que parece óbvio e prove cada afirmação
com saída de comando. Junto com `verification-before-completion` (nada de "pronto" sem prova) e
`receiving-code-review` (como tratar os achados).

## 1. Requisitos → lista de conferência

- Releia as mensagens do LM desta tarefa e escreva a lista do que foi pedido, **com as palavras dele**
  (inclusive as restrições: "não muda o funcionamento do mod", "sem commit sem confirmação", "vai por vez").
- Para cada item: onde está no diff e qual prova mostra que funciona. Item sem prova = não feito.
- O que ficou de fora ou mudou de plano vai escrito, não escondido.

## 2. Inventário da mudança

```bash
git status --short && git diff --stat            # não commitado
git diff --stat origin/main...HEAD                # ramo inteiro, se for o caso
```
Leia o diff inteiro. Arquivo que ninguém pediu pra mexer = explicar ou desfazer.

## 3. Regras do repo (automático)

```bash
python3 .claude/skills/quality-review/scripts/repo_guard.py            # mudanças não commitadas
python3 .claude/skills/quality-review/scripts/repo_guard.py --base origin/main
```
- **ERRO** (bloqueia): segredo/token, áudio novo (o repo é público; voz e sons do FroggyDude e do LM
  nunca entram, só CC0 com origem anotada e OK do LM), vídeo novo (leva a voz), transcrição
  (`transcript/speech/captions`), arquivo > 5 MB. Nome com acento também é checado.
- **AVISO** (justificar ou corrigir): `.jpg` novo (provável quadro de referência), código do mod sem
  `mod_version` novo, rede sem versão do canal nova (`PROTOCOL` em `ModNetwork`, `VERSION` em
  `LmNetwork`), código sem seção nova no `NOTES.md`. Com `--base`, compara desde onde o ramo saiu.

## 4. Revisão de bugs

- Rode a skill **`/code-review`** (ferramenta Skill, `code-review`) no diff atual, nível `high` para
  código de mod e `medium` para ferramentas/documentação. Se não estiver disponível, faça a revisão
  à mão com a mesma pergunta: "que entrada/estado concreto faz isto dar errado?".
- Pontos que mais quebram aqui: lado cliente x servidor (classe de cliente no servidor dedicado),
  estado ↔ duração da animação ↔ tick do dano, transição de 3 ticks do GeckoLib, pacote sem
  validação no servidor, renderização que não roda no MobileGlues (ver `zalith-compat`), alocação por
  quadro, config nova sem padrão sensato, texto pro LM em inglês ou técnico demais.
- Cada achado: arquivo:linha, cenário concreto de falha, gravidade. Sem cenário = opinião, não achado.

## 5. Regressões e testes

- Mod mudou → `cd <mod> && ./gradlew build --no-daemon` (exit 0). Mexeu em registro/rede →
  também `./gradlew runServer` sobe sem erro. Comportamento → ver no jogo (`runClient`) e gravar.
- O que **não** mudou e depende do que mudou: procure os usos (`grep`) das constantes/métodos
  alterados e confira cada um.
- Ferramentas do repo (`.claude/`): `python3 -m unittest discover -s .claude/tests -v`.
- Animação: comparar com a referência pela skill `video-motion-analysis` (não "a olho").

## 6. Relatório

Curto, em português simples, nesta ordem: o que foi pedido × o que foi feito (lista), provas
(comandos e resultado), achados abertos com gravidade, o que só o LM pode testar (celular).
Depois: **perguntar se pode commitar**. Nunca commitar/push sem o OK explícito dele.
