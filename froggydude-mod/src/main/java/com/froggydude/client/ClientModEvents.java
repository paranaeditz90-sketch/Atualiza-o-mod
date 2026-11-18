package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModParticles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.FROGGYDUDE.get(), FroggydudeRenderer::new);
        // o cinegrafista da cutscene nunca aparece
        event.registerEntityRenderer(ModEntityTypes.CUTSCENE_CAMERA.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.TONGUE_HOLD.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    /** Jogador sem o braço esquerdo: troca a armadura e põe o toco no ombro. E o sangue grudado na skin. */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        ArmlessRendering.addLayers(event);
        // aldeão na sequência da zebra: os tocos dos braços e do pescoço
        for (net.minecraft.world.entity.EntityType<?> type : java.util.List.of(
                net.minecraft.world.entity.EntityType.VILLAGER, net.minecraft.world.entity.EntityType.WANDERING_TRADER)) {
            net.minecraft.client.renderer.entity.LivingEntityRenderer r = event.getRenderer((net.minecraft.world.entity.EntityType) type);
            if (r != null) r.addLayer(new com.froggydude.client.cutscene.VillagerVictimRender.StumpLayer(r));
        }
        for (String skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.LivingEntityRenderer renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                pr.addLayer(new com.froggydude.client.gore.BloodStains.Layer(pr));
            }
        }
    }

    /** Sangue na tela: por baixo do HUD (a barra de itens fica por cima). */
    @SubscribeEvent
    public static void registerOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("blood_screen", (gui, gfx, partial, w, h) ->
                com.froggydude.client.gore.ScreenBlood.render(gfx, partial, w, h));
        // (v0.7.41) o alerta da cutscene da cabeça: em cima da hotbar, no vão entre os corações e a fome
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(), "headgrab_alert",
                (gui, gfx, partial, w, h) -> HeadGrabHud.render(gfx, partial, w, h));
        // faixas pretas e o escuro da cutscene do golpe final: por cima de tudo
        event.registerAboveAll("cutscene", (gui, gfx, partial, w, h) -> {
            com.froggydude.client.cutscene.CinematicCamera.renderOverlay(gfx, partial, w, h);
            JumpscareView.render(gfx, partial, w, h);
        });
    }

    /** Os pedaços de sangue. */
    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BLOOD.get(), BloodParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DUST_PUFF.get(), DustPuffParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOD_CLOUD.get(), BloodCloudParticle.Provider::new);
    }

    /**
     * (v0.7.38, LM 16/11: "da pra pegar o pacote de vozes agora e colocar dentro do mod, o froggydude autorizou") a voz
     * original do FroggyDude vem dentro do jar como um pacote de recursos interno, sempre ligado e por cima dos sons do
     * mod (o mesmo "FroggyDude-Voz" que o LM usava à parte; o sounds.json dele troca os eventos com replace). Ela NÃO
     * fica no repositório, que é público: o build junta a pasta voz-privada/ (fora do Git) quando ela existe. Sem ela
     * (o jar do GitHub sem o segredo da voz), nada é registrado e os sons caem nos do Minecraft, como antes.
     */
    @SubscribeEvent
    public static void addVoicePack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        var file = ModList.get().getModFileById(ModEntityTypes.MOD_ID);
        if (file == null) return;
        Path root = file.getFile().findResource("resourcepacks", "froggydude_voz");
        if (!Files.isDirectory(root)) return;
        Pack pack = Pack.readMetaAndCreate("froggydude_voz", Component.literal("FroggyDude: voz"), true,
                id -> new PathPackResources(id, root, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (pack != null) event.addRepositorySource(add -> add.accept(pack));
    }
}
