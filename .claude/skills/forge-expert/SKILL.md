---
name: forge-expert
description: Especialista nos dois mods deste repo (froggydude-mod e becomelm-mod) — Minecraft 1.20.1, Forge 47.4.10, mapeamento official, Java 17, Gradle 8.8 + ForgeGradle 6, GeckoLib 4.4.9. Use ao escrever ou revisar código Java dos mods, animações GeckoLib (.animation.json, controladores, transições, ossos), IA de entidade, eventos, rede (SimpleChannel), renderização, config, build/CI ou desempenho. Não use para Fabric/NeoForge nem outras versões do jogo.
---

# Forge 1.20.1 + GeckoLib 4.4.9 neste repo

## Versões reais (conferir em `*/gradle.properties` antes de afirmar qualquer coisa)

| Item | Valor |
|---|---|
| Minecraft | 1.20.1 (`minecraft_version_range=[1.20.1,1.21)`) |
| Forge | 47.4.10 (`net.minecraftforge.gradle` `[6.0,6.2)`) |
| Mapeamento | `official` 1.20.1 (nomes Mojang; **não** é Parchment, parâmetros vêm como `p_12345_`) |
| Java | toolchain 17 (o foojay baixa o JDK 17 sozinho em `~/.gradle/jdks`; o Java 21 do container não é usado no build) |
| Gradle | 8.8 (wrapper), `org.gradle.daemon=false`, `-Xmx3G` |
| GeckoLib | `software.bernie.geckolib:geckolib-forge-1.20.1:4.4.9` (pacotes `software.bernie.geckolib.*`, núcleo em `...geckolib.core.*`) |

Nunca use API de outra versão "porque parece igual": 1.20.1 ainda tem `SimpleChannel`/`NetworkRegistry`,
`FMLJavaModLoadingContext`, `ForgeConfigSpec`, `DeferredRegister` e `RegistryObject` do Forge
(não os nomes do NeoForge 1.20.4+). GeckoLib 4 não é a 3 (sem `IAnimatable`/`AnimationFactory`).

## De onde tirar a verdade (nesta ordem)

1. **O código do repo.** Os dois mods já resolvem quase tudo (rede, controladores, renderizadores,
   mixins). Procure o padrão aqui antes de inventar.
2. **As classes reais, com `javap`** (fonte de verdade das assinaturas; não precisa de rede):
   ```bash
   J=$(find ~/.gradle/jdks -name javap -type f | head -1)
   F=~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.10_mapped_official_1.20.1/forge-1.20.1-47.4.10_mapped_official_1.20.1.jar
   G=$(find ~/.gradle/caches/forge_gradle/deobf_dependencies -name 'geckolib-forge-1.20.1-4.4.9_mapped_official_1.20.1.jar' | head -1)
   $J -p -cp "$F" net.minecraft.world.entity.LivingEntity | grep -i hurt
   $J -p -cp "$G" software.bernie.geckolib.core.animation.AnimationController
   ```
   O cache só existe depois do primeiro `./gradlew build` (ou `compileJava`) na sessão.
   O fonte do Forge (sem o Minecraft) está em
   `~/.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-sources.jar`.
3. **Documentação da versão certa**: Forge <https://docs.minecraftforge.net/en/1.20.x/>;
   GeckoLib 4 <https://github.com/bernie-g/geckolib/wiki> (páginas "Geckolib4").
4. **Context7** (MCP do projeto): testado em 26/10/2026, a busca não devolve Forge 1.20.1 nem
   GeckoLib; só NeoForge de outras versões. Não use a doc do NeoForge pra responder sobre Forge 47.

## Mapa do código

- `froggydude-mod/src/main/java/com/froggydude/`: `entity/` (o mob, `FroggydudeEntity` com os
  controladores), `brain/` (decisão/IA), `client/` (renderizadores, sangue, câmera, `AnimCapture`),
  `network/ModNetwork.java` (`PROTOCOL`), `config/`, `event/`, `player/` (braço arrancado etc.).
- `becomelm-mod/src/main/java/com/becomelm/`: o jogador vira o FroggyDude (`client/LmAnimatable`
  é o animatable GeckoLib do jogador), `network/LmNetwork.java` (`VERSION`, aceita servidor sem o mod).
- Animações geradas por script (não editar o JSON à mão quando houver gerador):
  `froggydude-mod/tools/anim/build_anims.py` e `becomelm-mod/tools/make_anims.py`
  (o BecomeLM copia do Froggydude). Prévia/medidas: `froggydude-mod/tools/anim/rig.py` e `preview.py`.

## Regras que já custaram caro aqui

- **Estado ↔ animação ↔ dano:** o estado (`FroggyState`) é sincronizado servidor → cliente, o cliente
  toca a animação do estado e o servidor aplica o dano no tick do impacto. Duração da animação =
  duração do estado. Mudou um, muda o outro (e os ticks de impacto).
- **Transição do controlador:** `new AnimationController<>(this, "nome", ticksDeTransicao, handler)`.
  Com transição de 3 ticks, a pose nova só "chega" 3 ticks depois; constantes que dependem da pose
  (ex.: `PILE_LIE_START = PILE_DROP + 3` no BecomeLM) precisam somar isso.
- **Convenções do esqueleto** (medidas no jogo; ver cabeçalho do `build_anims.py`): waist X+ inclina
  pra frente; head X+ olha pra baixo; arm X− levanta o braço pra frente; leg X+ joga a perna pra
  trás; root X+ tomba o corpo todo. Ordem Rz·Ry·Rx. `position` do osso é no referencial do pai
  (no braço, y = encolher o ombro). O lado esquerdo no JSON já vem espelhado em Y/Z.
- **Rede:** mudou pacote (campo, ordem, id) → suba `PROTOCOL`/`VERSION` e escreva nas NOTES que os
  amigos precisam atualizar. Pacote do cliente nunca é confiável: valide no servidor (distância,
  modo, cooldown) e faça o trabalho em `ctx.enqueueWork`.
- **Lado (Dist):** classe de cliente (renderizador, `Minecraft`, câmera, input) nunca carregada no
  servidor dedicado. Registre o cliente só em eventos de cliente (`@Mod.EventBusSubscriber(value = Dist.CLIENT)`
  ou `DistExecutor`). Teste `./gradlew runServer` quando mexer em registro/rede.
- **Barramentos:** registro/atributos/renderizadores no barramento do mod (`FMLJavaModLoadingContext.get().getModEventBus()`);
  eventos de jogo em `MinecraftForge.EVENT_BUS`. Atributos de entidade em `EntityAttributeCreationEvent`.
- **Celular primeiro:** o LM joga no Android (Zalith Launcher 2 + MobileGlues). Antes de qualquer coisa
  de renderização (textura gerada, leitura da GPU, blend, shader, biblioteca nativa), leia a skill
  `zalith-compat`. Física e afins: Java puro, sem nativos.
- **Desempenho:** nada pesado por tick em todo cliente; partículas/pedaços com limite e config
  (`pedacos.cacos`, `pedacos.maximo`); evite alocação por quadro no renderizador.
- **Conteúdo:** o repo é público. Áudio original do FroggyDude e sons do LM (Voicemod/CapCut) nunca
  entram aqui; só som CC0 pode ir no mod.

## Fluxo de trabalho

1. Ler o código envolvido inteiro e as NOTES do mod. Conferir assinaturas com `javap`.
2. Mudar o mínimo. Se for animação, mudar o gerador e regenerar o JSON.
3. `cd <mod> && ./gradlew build --no-daemon` (primeira vez na sessão: alguns minutos, baixa Forge/JDK).
4. Ver no jogo: `./gradlew runClient -PquickWorld=<mundo>` (+ `-PfroggyDebug`, `-PanimSlow=0.05 -Pcapture=1`
   para gravar quadro a quadro; `-PlmDebug=1` e `-PwithFroggy=...jar` no BecomeLM). Gravar e comparar
   com a referência (skill `video-motion-analysis`).
5. Versão: `mod_version` em `gradle.properties` + seção nova nas `NOTES.md` (em português simples, pro LM).
6. Só fazer commit/push com o OK explícito do LM; depois conferir o GitHub Actions verde
   (`compile.yml` / `compile-becomelm.yml`, artefato com o jar + GeckoLib).
