---
name: implementar
description: Etapa 2 de 2 (só o LM chama, com /implementar). Executa o plano gravado em TAREFA.md pelo /planejar, um passo por vez - implementa, compila, depura e verifica com prova - e registra no próprio TAREFA.md o que fez. Nunca faz commit nem push sem o OK do LM.
argument-hint: "[continua | passo N | observação]"
model: opus
effort: max
disable-model-invocation: true
---

# /implementar: executar o plano, com prova

Observação do LM: **$ARGUMENTS**

## 0. O plano manda

- Leia o `TAREFA.md` inteiro antes de tudo. Ele é o elo com o `/planejar`: decisões, contexto
  técnico (`caminho:linha`), passos e verificação estão lá.
- Sem `TAREFA.md`, ou com "Perguntas em aberto" que travam o próximo passo: pare e diga ao LM que
  falta o `/planejar` (ou a resposta dele). Não planeje por conta própria.
- Releia o `CLAUDE.md`: as regras dele valem aqui (sem commit/push sem OK explícito, só no ramo da
  sessão, áudio e quadros de referência fora do repo, sem segredos, preservar o que é do LM).
- Mude o `Estado:` pra `em andamento` e siga pelo primeiro passo `- [ ]` ainda aberto.

## 1. Um passo por vez

Para cada passo:
1. Confira no código que o contexto anotado ainda vale (o arquivo pode ter mudado desde o plano).
2. Implemente só o que o passo pede, no estilo do código em volta. Use `forge-expert` pra
   Forge/GeckoLib e `zalith-compat` antes de qualquer coisa de render, textura, FPS ou RAM.
3. Compile e prove o passo do jeito que o plano diz:
   `cd froggydude-mod && ./gradlew build --no-daemon` (ou `becomelm-mod`), os testes do
   `.claude/` (`python3 -m unittest discover -s .claude/tests -v`) quando mexer lá, e
   `./gradlew runClient -PquickWorld=<mundo>` quando for coisa que só se vê no jogo.
4. Marque `- [x]` e escreva em "Registro da implementação": o que mudou (`caminho:linha`), o
   comando e o resultado de verdade (inclusive falha) e o que ficou pendente.

Desvio de plano: se um passo se mostrar errado, maior que o previsto ou pedir decisão do LM,
**pare**, escreva o problema em "Perguntas em aberto" e pergunte. Mudança grande só com o OK dele.

## 2. Quando quebrar

Use a skill `systematic-debugging`: reproduza, ache a causa raiz com evidência e só depois
corrija. "Flake" não é causa. Nada de desligar teste, pular verificação ou contornar o erro
pra ficar verde.

## 3. Antes de dizer "pronto"

- `verification-before-completion`: toda afirmação de "funciona" com a saída do comando que prova.
- `quality-review`: confere o pedido do LM contra o diff e roda
  `python3 .claude/skills/quality-review/scripts/repo_guard.py`.
- Estado do `TAREFA.md` vira `pronto pra revisar`, com o resumo do que foi feito e de como testar
  no celular.

## 4. Commit e push

Só com o OK explícito do LM nesta conversa. Com o OK: `git add` só dos arquivos da tarefa, mensagem
clara e push no ramo da sessão. Sem o OK: mostre o `git status`/`git diff --stat` e pergunte.

Sem subagentes: faça tudo neste contexto. A única exceção é o `/code-review` que a `quality-review`
usa, e só quando ela pedir.

Lembre o LM: o modelo desta etapa vale só para a resposta em que ele chamou `/implementar`. Pra
continuar outro passo depois, ele manda `/implementar continua`.
