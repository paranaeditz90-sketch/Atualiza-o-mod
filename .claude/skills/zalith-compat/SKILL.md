---
name: zalith-compat
description: Diagnóstico e prevenção de problemas dos mods deste repo no celular do LM — Zalith Launcher 2 no Android ARM64 com MobileGlues (OpenGL sobre GLES 3.2). Use ao investigar crash, tela preta, corpo/skin sumindo, textura errada, FPS baixo, falta de RAM, dependência que não carrega ou log do celular, e ANTES de escrever código de renderização, textura, shader, biblioteca nativa ou controle por toque. Não presuma que o que funciona no PC funciona no Android.
---

# Compatibilidade com o celular (Zalith Launcher 2 / Android ARM64)

O LM testa tudo no celular. O PC daqui (Linux x86, Mesa/llvmpipe no Xvfb) **não reproduz** os bugs
de renderização do celular: "funcionou aqui" não prova nada sobre o Zalith. Diga isso ao LM quando
for o caso e peça o teste dele (com o que olhar e onde).

## O ambiente dele

- Launcher: Zalith Launcher 2 (núcleo de lançamento do PojavLauncher). Java roda em ARM64.
- Renderizador usado: **MobileGlues** (implementação de OpenGL em cima do OpenGL ES 3.2 do aparelho).
  Outros que o Zalith 2 oferece: Krypton Wrapper, Holy GL4ES, Zink/Vulkan e plugins (ex.: LTW).
  Cada um tem bugs diferentes; pergunte qual estava ligado antes de concluir.
- Os mantenedores do MobileGlues dizem que não suportam oficialmente Sodium/Iris/OptiFine, shaders
  nem modpacks (é "melhor esforço"); há relato de crash nativo com shaderpack em GPU Mali no Zalith 2.
- Controles: os botões virtuais do Zalith 2 **só mandam tecla/clique**. Um mod não cria botão na tela
  dele. Por isso a roda do BecomeLM abre com dois toques no agachar (funciona com qualquer layout).
- LAN quase não serve no Zalith/Android (rede do celular); multiplayer é por servidor.

## Lições que já custaram versões (não repetir)

| Sintoma no celular | Causa achada | Solução que funcionou |
|---|---|---|
| Corpo some no F5/inventário, mão preta, depois de sujar de sangue (0.6.0–0.6.1) | `DynamicTexture` gerada na hora e desenhada por cima do corpo; camada quase toda transparente por cima da skin | Sangue como quadradinhos colados no modelo com **textura fixa branca pintada pela cor do vértice** (igual às poças, que sempre funcionaram) |
| Pixel transparente tapando o corpo de baixo | Renderizador do celular trata alfa/ordem de desenho diferente do PC | Camada extra só pinta cor, não tapa; nada de overlay "quase transparente" |
| Risco de travar/ler lixo | Ler textura da GPU (`glGetTexImage`/download) | Ler a skin do **PNG** (`SkinAlpha` no mod da IA, `LmSkinAlpha` no BecomeLM) |
| Jogo fecha ao abrir | Biblioteca nativa só x86 (Physics Mod = PhysX da NVIDIA) | Física em **Java puro** (os pedaços do corpo) |
| Mob "congelado" longe | Distância de simulação baixa no celular | Lógica não pode depender de chunk longe estar ativo |

Ainda em aberto: **#134** (skin/armadura somem no celular, persistiu na 0.6.x). Qualquer mudança de
render deve ser checada contra esse histórico.

## Regras para código novo de renderização

1. Prefira textura fixa empacotada no jar + cor por vértice a textura gerada em tempo real.
2. Não leia de volta da GPU. Não dependa de extensão GL específica, de geometry/compute shader nem de
   `glPolygonMode`, `glLineWidth` > 1 e afins.
3. Use os `RenderType` do vanilla (entity_cutout, entity_translucent...) em vez de estado GL na mão.
   Cuidado com translucidez empilhada (ordem de desenho difere).
4. Sem bibliotecas nativas (JNI/.so). Se uma dependência tiver nativo, ela não roda no ARM do LM.
5. Orçamento: celular tem CPU/GPU fracas e pouca RAM pro Java. Limite partículas, pedaços e
   entidades; tudo configurável (ex.: `pedacos.cacos` 60 → 30 se engasgar). Nada pesado por quadro.

## Diagnóstico (pedir ao LM, em português simples)

1. **Versões**: do mod, do GeckoLib (4.4.9 pra Forge 1.20.1), do Forge (47.x), qual renderizador, quanta
   RAM foi dada ao Java nas configurações do Zalith, outros mods na pasta.
2. **Logs**: `logs/latest.log` e a pasta `crash-reports/` dentro da pasta da versão (o gerenciador de
   arquivos do Zalith abre). Se o launcher fechar sem crash-report, o log do próprio launcher
   (Zalith → exportar/compartilhar log).
3. **Ler o log procurando**: `OutOfMemoryError`/`GC overhead` (RAM), `UnsatisfiedLinkError` ou `.so`
   (nativo), `GL_INVALID_*`/`Shader`/`MobileGlues` (renderizador), `Missing or unsupported mandatory
   dependencies` (falta o GeckoLib ou versão errada), `Mixin apply failed` (conflito), stack trace com
   o pacote `com.froggydude`/`com.becomelm` (bug nosso).
4. **Isolar**: só o mod + GeckoLib numa versão separada (isolamento de versão ligado); trocar de
   renderizador uma vez pra ver se o bug é do MobileGlues; Gore no mínimo pra ver se é o sangue.
5. **Reproduzir aqui o que der** (lógica, crash Java, desempenho de tick) e dizer claramente o que
   não dá pra reproduzir (aparência no MobileGlues). Mudança de render vai como "correção mais
   provável" com o teste exato pro LM fazer (ex.: "arranca o braço, F5, inventário, mão vazia").

## FPS / desempenho

- Peça o F3 (FPS, memória usada/alocada) com e sem o mod, mesmo lugar, mesma distância de renderização.
- Do nosso lado: medir ms por tick no `runClient`/`runServer` com debug, contar partículas e pedaços,
  e conferir que nada aloca por quadro. Lembre que o PC daqui é muito mais rápido que o celular.

## Fontes (consultadas em 26/10/2026)

- Releases do Zalith Launcher 2: <https://github.com/ZalithLauncher/ZalithLauncher2/releases>
- Releases e issues do MobileGlues: <https://github.com/MobileGL-Dev/MobileGlues-release/releases>
- Histórico deste repo: `froggydude-mod/NOTES.md` (v0.6.0, v0.6.1, v0.7.0) e `PROGRESSO.md`.
