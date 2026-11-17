package com.froggydude.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/** Tipos de dano do mod (os arquivos ficam em data/froggydude/damage_type). */
public final class ModDamageTypes {

    /**
     * A cabeça arrancada no fim da cutscene: ignora armadura, totem e
     * invencibilidade (as tags em data/minecraft/tags/damage_type).
     */
    public static final ResourceKey<DamageType> DEVOURED =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ModEntityTypes.MOD_ID, "devoured"));

    /**
     * (v0.7.42) O anti-trapaça: quem entrou no criativo ou no espectador num mundo com "Trapaças? Não" morre com essa
     * mensagem depois do vídeo (world.AntiCheat). Ignora armadura, totem e invencibilidade, como o de cima.
     */
    public static final ResourceKey<DamageType> ANTICHEAT =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ModEntityTypes.MOD_ID, "anticheat"));

    private ModDamageTypes() {
    }
}
