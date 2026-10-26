---
name: frontend-design
description: Criar ou revisar páginas web e interfaces (HTML/CSS/JS) deste repo — visualizadores de animação, relatórios, comparações lado a lado, ferramentas no navegador e páginas pro LM — com visual original, responsivo (celular primeiro), acessível e profissional, validado de verdade no Chromium. Use ao fazer qualquer página, painel ou interface web; não use para telas dentro do Minecraft (isso é forge-expert).
---

# Front-end: original, responsivo, acessível, conferido no navegador

O LM vê quase tudo **no celular Android** (app do Claude). Celular primeiro, sempre.

## Antes de desenhar

1. Qual é a única tarefa da página (comparar dois vídeos? ler um relatório? mexer numa linha do tempo)?
   O layout serve essa tarefa; o resto é secundário.
2. Quem usa e onde: tela de ~360 px de largura, dedo (não mouse), às vezes tema escuro, internet do celular.
3. Se for publicar como Artifact, carregar a skill `artifact-design` antes (ela tem o contrato da página).

## Direção visual (sem cara de template)

- Escolha uma ideia visual ligada ao assunto (ex.: este repo = Minecraft/terror/sangue: tons terrosos e
  vermelho-sangue com parcimônia, tipografia forte nos títulos, sem roxo-gradiente genérico, sem
  emoji como ícone, sem "cards" iguais empilhados por padrão).
- Hierarquia por tamanho/peso/espaço antes de cor. Uma cor de destaque, usada pouco.
- Cores em tokens no `:root`, com versão escura em `@media (prefers-color-scheme: dark)`; `body` com fundo explícito.
- Movimento só quando explica algo (ex.: animação da pose); respeitar `prefers-reduced-motion`.

## Responsivo

- `<meta name="viewport" content="width=device-width, initial-scale=1">` sempre.
- Uma coluna no celular com margem lateral de 16 px; colunas só a partir de ~720 px (`min-width`).
- Nada de largura fixa maior que a tela; mídia com `max-width: 100%`; tabelas largas dentro de um
  contêiner com rolagem própria (nunca a página inteira rolando de lado).
- Vídeo/canvas lado a lado: no celular, empilhar (um embaixo do outro) com o mesmo controle de tempo.

## Acessível (WCAG 2.2 AA como piso)

- HTML semântico (`header/main/nav/button/label`), `lang` no `<html>`, `<title>` com nome curto.
- Contraste ≥ 4,5:1 no texto normal (3:1 em texto grande e ícones), nos dois temas.
- Alvo de toque ≥ 24×24 px (melhor 44×44); foco visível; tudo funciona por teclado.
- `alt` em imagem com conteúdo (`alt=""` se for decoração); campos com `<label>`; botão só com ícone
  precisa de `aria-label`.
- Não transmitir informação só por cor (ex.: "vermelho = errado" também precisa de texto/ícone).

## Validação prática (obrigatória antes de dizer que está pronto)

```bash
node .claude/skills/frontend-design/scripts/check_page.mjs caminho/pagina.html /tmp/.../saida
```

- Abre no Chromium do container em **celular 360×780** e **PC 1280×800**, temas claro e escuro.
- Gera os prints (`celular-light.png`, `celular-dark.png`, `pc-*.png`) e um JSON com: rolagem
  horizontal, erros de console, falta de `title`/`lang`/viewport, imagem sem `alt`, controle sem
  nome, campo sem rótulo, alvo de toque pequeno. Sai com código 1 se achar problema.
- **Olhe os prints** (ferramenta Read na imagem): o script não julga beleza nem legibilidade.
- Contraste: o script não mede; confira as cores dos tokens (calcule a razão) ou pelos prints.
- Se a página tiver interação (linha do tempo, botões), use o MCP do Playwright (`.mcp.json`) ou um
  script Playwright curto pra clicar e conferir o resultado, não só a carga inicial.
- Só então diga "pronto", citando o resultado (ex.: "0 problemas nas 4 combinações; prints conferidos").

Testado em 26/10/2026: página boa → 0 problemas; página sem viewport/alt/rótulo/nome e com erro de JS
→ 32 problemas; página com bloco de 900 px → rolagem horizontal de 548 px no celular acusada.
