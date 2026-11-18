package com.becomelm;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
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

    private static final java.util.Set<String> WORDS = java.util.Set.of("gun", "rifle", "pistol", "shotgun", "revolver",
            "smg", "sniper", "sword", "blade", "katana", "saber", "sabre", "dagger", "knife", "scythe", "spear", "mace",
            "halberd", "axe", "bow", "crossbow", "launcher");

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
        // de outro mod: pelo nome (palavra inteira: "gunpowder" não é arma) ou bate forte sem ser ferramenta (picareta,
        // pá e enxada de mod também batem forte e não são arma)
        for (String w : id.getPath().split("[_/]")) {
            if (WORDS.contains(w)) return true;
        }
        if (s.getItem() instanceof DiggerItem) return false;
        return s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
                .anyMatch(m -> m.getAmount() >= 3D);
    }
}
