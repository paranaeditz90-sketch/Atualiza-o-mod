package com.becomehim;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * (roda) O que conta como arma pro "roubar arma" (escolha do LM, 18/11: só armas): espada, machado, arco, besta,
 * tridente, e arma de mod (TaCZ, Avaritia, ou qualquer item de mod que bata forte ou atire).
 */
public final class LmWeapons {

    private LmWeapons() {
    }

    public static boolean isWeapon(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem || s.getItem() instanceof TridentItem
                || s.getItem() instanceof ProjectileWeaponItem) {
            return true;
        }
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id == null || "minecraft".equals(id.getNamespace())) return false;
        if (id.getNamespace().equals("tacz") || id.getNamespace().equals("avaritia")) return true;
        // de outro mod: bate forte (mais que a mão) ou é de atirar
        boolean strong = s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
                .anyMatch(m -> m.getAmount() >= 3D);
        String path = id.getPath();
        return strong || path.contains("gun") || path.contains("rifle") || path.contains("pistol") || path.contains("sword");
    }
}
