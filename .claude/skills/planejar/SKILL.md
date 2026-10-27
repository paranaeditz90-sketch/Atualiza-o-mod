---
name: planejar
description: Etapa 1 de 2 (só o LM chama, com /planejar). Planeja uma tarefa deste repo (os dois mods ou as ferramentas do .claude/) sem mexer em código. Investiga o código e as notas, faz as perguntas que faltam e grava o plano em TAREFA.md, que a etapa /implementar segue.
argument-hint: "[o que você quer fazer]"
model: sonnet
effort: high
disable-model-invocation: true
disallowed-tools: Agent Edit NotebookEdit
---

# /planejar: só o plano, nada de código

Pedido do LM: **$ARGUMENTS**
(Se veio vazio, use a última mensagem dele; se ainda assim não der pra saber o que ele quer, pergunte.)

Esta etapa **só planeja**. Aqui não se edita código, asset, build ou nota de versão, não se compila,
não se roda o jogo e não se faz commit nem push. O único arquivo que você escreve é o `TAREFA.md`,
na raiz do repo. Quem executa é o `/implementar`, que roda em outro modelo e **só conhece o que
estiver escrito no `TAREFA.md`**. Escreva pensando nisso.

## 1. Entender antes de planejar

- Releia o `CLAUDE.md` (regras do LM), o `PROGRESSO.md` (onde paramos) e a parte do `PLANO.md`
  que tocar no pedido. Se já existe um `TAREFA.md` de outra tarefa ainda aberta, pergunte antes de
  sobrescrever.
- Ache no código onde a mudança cai: leia os arquivos de verdade (Read, Grep, Glob, `git log`),
  não chute pelo nome. Anote `caminho:linha` de cada ponto que vai mudar e do que pode quebrar junto.
- Use as skills do repo só como consulta: `forge-expert` (Forge, GeckoLib, rede, render),
  `zalith-compat` (celular: qualquer coisa de render, textura, FPS ou RAM),
  `video-motion-analysis` (quando o pedido for "igual ao vídeo").
- Sem subagentes: investigue você mesmo, neste contexto.

## 2. Perguntar o que é decisão do LM

Ele pede: perguntar antes de mudança grande, uma coisa por vez, e na dúvida perguntar.
- Faça as perguntas que mudam o plano (escopo, comportamento esperado, qual mod, qual referência),
  com uma opção recomendada e o porquê. Não pergunte o que dá pra descobrir lendo o código.
- Questione o pedido quando houver incoerência ou risco (Android, desempenho, rede, quebrar o que
  já funciona, som que não pode ir pro repo público). Diga o que pode dar errado.
- As respostas dele entram no `TAREFA.md` com as palavras dele.

## 3. Gravar o `TAREFA.md`

Use este formato (o `/implementar` depende dele):

```markdown
# Tarefa: <título curto>

Estado: planejado
Mod(s): froggydude-mod | becomelm-mod | .claude/   (versão atual: <mod_version do gradle.properties>)

## Pedido do LM
<as palavras dele, sem resumir o sentido>

## Decisões já tomadas
- <pergunta> → <resposta do LM>

## Contexto técnico essencial
- <caminho:linha>: o que faz hoje e por que importa pra tarefa
- APIs, ossos, pacotes de rede, configs ou animações envolvidos
- Restrições do CLAUDE.md que valem aqui (áudio, segredos, Android, sem commit sem OK)

## Passos (uma coisa por vez)
- [ ] 1. <arquivo(s)> - <mudança> - como provar que funcionou
- [ ] 2. ...

## Como verificar no fim
- build: `cd <mod> && ./gradlew build --no-daemon`
- testes, `runClient`, comparação com o vídeo, o que o LM vai conferir no celular

## Riscos e o que pode dar errado
## Fora do escopo
## Perguntas em aberto

## Registro da implementação
(preenchido pelo /implementar)
```

Regras do plano:
- Cada passo é pequeno, diz o arquivo e diz como provar que funcionou (comando, teste ou o que ver no jogo).
- Nada inventado: o que não foi confirmado no código vai em "Perguntas em aberto", não no plano.
- Se o pedido mudar nota de versão, inclua o passo do `NOTES.md` e do `mod_version`.

## 4. Fechar a etapa

Mostre ao LM um resumo curto do plano (os passos e os riscos) e as perguntas que sobraram. Diga
que, quando ele aprovar, é só mandar `/implementar` (de preferência com "continua" pra retomar
depois). Não comece a implementar, mesmo que ele diga "pode fazer": a troca de etapa é o
`/implementar`.
