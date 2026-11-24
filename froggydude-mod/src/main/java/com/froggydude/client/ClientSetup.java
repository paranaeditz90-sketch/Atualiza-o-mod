package com.froggydude.client;

import com.froggydude.client.screen.FroggySettingsScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/** Só no cliente: o botão "Configurar" do mod na lista de mods abre a tela do gore. */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void init() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new FroggySettingsScreen(parent)));
    }
}
