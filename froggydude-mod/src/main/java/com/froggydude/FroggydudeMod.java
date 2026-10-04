package com.froggydude;

import com.froggydude.config.FroggyConfig;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModParticles;
import com.froggydude.init.ModSounds;
import com.froggydude.network.ModNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Classe principal do mod. Se você for colocar isso dentro de um mod que já
 * tem uma classe @Mod, apague este arquivo e copie só as 3 linhas do
 * construtor/setup pra sua.
 */
@Mod(ModEntityTypes.MOD_ID)
public class FroggydudeMod {

    public FroggydudeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntityTypes.register(modBus);
        ModSounds.register(modBus);
        ModParticles.register(modBus);
        modBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FroggyConfig.SPEC, "froggydude-common.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
