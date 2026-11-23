package com.froggydude.entity;

import com.froggydude.config.FroggyConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Quem fica protegido (v0.7.44, pedido do time do Touhou Little Maid, decisão do LM): com a opção "Touhou Little
 * Maid: gore" desligada (o padrão), as entidades do mod deles (as maids e o resto) ficam de fora - ele não ataca, não
 * revida nem come, e nada de sangue nem pedaços nelas. Ligada, tudo normal. Jogador, com ou sem Yes Steve Model,
 * continua igual.
 */
public final class GoreSafe {

    /** O id do mod Touhou Little Maid (o namespace das entidades dele). */
    public static final String TLM = "touhou_little_maid";

    private GoreSafe() {
    }

    /** Essa entidade fica protegida (sem ataque e sem gore)? */
    public static boolean spared(@Nullable Entity e) {
        if (e == null || FroggyConfig.tlmGore()) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && TLM.equals(id.getNamespace());
    }
}
