package com.froggydude.brain;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * (v0.7.29) O que ele estudou dos mods instalados (LM: "a IA lê os mods abaixados e estuda pra ver qual precisa
 * tomar cuidado"): quais itens são ARMAS DE MOD. Vanilla e o próprio Froggydude nunca contam ("somente de mods";
 * Avaritia entra, "o mais roubado"). Arma = espada, arco/besta, tridente, item com dano de ataque alto, ou nome de
 * arma (gun, rifle, pistol, sword, katana...). Mais o que ele aprendeu apanhando (FroggyWorldData.pain): item de mod
 * que já doeu nele vira alvo também, mesmo que o estudo não tenha achado.
 */
public final class ModWeapons {

    /** Quanto um item de mod precisa ter doído (vida, somada) pra ele querer tirar da pessoa. */
    public static final float PAIN_LEARNED = 4.0F;

    /**
     * Pedaços do nome (separados por _ / . -) que dizem "arma" - o pedaço tem que ser IGUAL (antes valia "terminar
     * com", e "rainbow" virava arco, "balance" virava lança); os compostos comuns estão na lista.
     */
    private static final String[] WEAPON_WORDS = {"gun", "rifle", "pistol", "revolver", "shotgun", "sniper", "smg",
            "carbine", "launcher", "blaster", "cannon", "minigun", "railgun", "handgun", "musket", "sword", "greatsword",
            "longsword", "shortsword", "broadsword", "gunblade", "blade", "katana", "saber", "sabre", "scythe", "dagger",
            "knife", "spear", "lance", "halberd", "glaive", "mace", "hammer", "warhammer", "flail", "bow", "longbow",
            "shortbow", "crossbow", "trident", "battleaxe", "rapier", "cutlass", "claymore", "whip"};
    /** Pedaços que dizem "não é a arma" (munição, peça, material). */
    private static final String[] NOT_WEAPON = {"ammo", "bullet", "bullets", "shell", "shells", "cartridge", "magazine",
            "mag", "arrow", "arrows", "bolt", "bolts", "upgrade", "ingot", "nugget", "scrap", "template", "part", "parts",
            "barrel", "stock", "grip", "scope", "frame", "casing", "powder", "pattern", "mold", "mould", "blueprint",
            "cast", "fragment", "shard", "dust", "hilt", "handle", "core", "skin", "case", "box", "block", "spawn", "egg"};

    private static final Map<Item, Boolean> CACHE = new HashMap<>();

    private ModWeapons() {
    }

    /** Item de mod (nem do Minecraft, nem do próprio Froggydude)? */
    public static boolean isModItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return false;
        String ns = key.getNamespace();
        return !"minecraft".equals(ns) && !ModEntityTypes.MOD_ID.equals(ns) && !"forge".equals(ns);
    }

    public static String id(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "?" : key.toString();
    }

    /** O estudo: esse item de mod é uma arma? (guardado por item, a lista não muda com o jogo aberto) */
    public static boolean isWeapon(Item item) {
        Boolean known = CACHE.get(item);
        if (known != null) return known;
        boolean w = classify(item);
        CACHE.put(item, w);
        return w;
    }

    private static boolean classify(Item item) {
        ItemStack stack = new ItemStack(item);
        if (!isModItem(stack)) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        String path = key == null ? "" : key.getPath().toLowerCase(Locale.ROOT);
        String[] parts = path.split("[_/.\\-]");
        for (String p : parts) {
            for (String no : NOT_WEAPON) {
                if (p.equals(no)) return false;
            }
        }
        if (item instanceof SwordItem || item instanceof ProjectileWeaponItem || item instanceof TridentItem) return true;
        boolean named = false;
        for (String p : parts) {
            for (String w : WEAPON_WORDS) {
                if (p.equals(w)) named = true;
            }
        }
        if (named) return true;
        // ferramenta (picareta, pá, machado de cortar árvore) só se o nome disser que é arma
        if (item instanceof DiggerItem) return false;
        // bate forte na mão (as armas de mod que não são "espada" pro jogo)
        double damage = 0D;
        try {
            for (AttributeModifier m : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
                if (m.getOperation() == AttributeModifier.Operation.ADDITION) damage += m.getAmount();
            }
        } catch (RuntimeException ignored) {
            // mod que quebra pedindo os atributos de um item sem dono: não é a arma
        }
        return damage >= 6.0D;
    }

    /** Ele quer tirar isso da pessoa: arma de mod do estudo, ou item de mod que já doeu nele. */
    public static boolean stealable(ItemStack stack, FroggyWorldData data) {
        if (!isModItem(stack)) return false;
        return isWeapon(stack.getItem()) || data.pain.getOrDefault(id(stack), 0F) >= PAIN_LEARNED;
    }

    /** O quanto ele quer aquela (pra escolher qual tirar primeiro): o que mais doeu vem antes. */
    public static float want(ItemStack stack, FroggyWorldData data) {
        return (isWeapon(stack.getItem()) ? 1F : 0F) + data.pain.getOrDefault(id(stack), 0F);
    }

    /** Apanhou com um item de mod: anota (devolve true quando esse item acabou de virar "perigoso"). */
    public static boolean feel(FroggyWorldData data, ItemStack held, float damage) {
        if (!isModItem(held) || damage <= 0F) return false;
        String id = id(held);
        float before = data.pain.getOrDefault(id, 0F);
        data.pain.put(id, Math.min(1000F, before + damage));
        data.setDirty();
        return !isWeapon(held.getItem()) && before < PAIN_LEARNED && before + damage >= PAIN_LEARNED;
    }

    /**
     * Onde está a arma de mod que ele mais quer dessa pessoa: a da mão primeiro, depois a da outra mão, depois a da
     * mochila (índice do Inventory; -1 = nada).
     */
    public static int pickSlot(Player p, FroggyWorldData data) {
        Inventory inv = p.getInventory();
        if (stealable(inv.getItem(inv.selected), data)) return inv.selected;
        if (stealable(inv.getItem(Inventory.SLOT_OFFHAND), data)) return Inventory.SLOT_OFFHAND;
        int best = -1;
        float bestWant = 0F;
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (!stealable(s, data)) continue;
            float w = want(s, data);
            if (w > bestWant) {
                bestWant = w;
                best = i;
            }
        }
        return best;
    }

    /**
     * (LM: "ele come a munição que tava na arma e junto a arma") O que vai pra barriga: a arma sem a munição de dentro
     * (as chaves de munição comuns dos mods de arma: AmmoCount, GunCurrentAmmoCount, ChargedProjectiles...). As
     * outras balas da mochila continuam com a pessoa.
     */
    public static ItemStack unload(ItemStack stack) {
        ItemStack out = stack.copy();
        CompoundTag tag = out.getTag();
        if (tag == null) return out;
        for (String k : tag.getAllKeys().toArray(new String[0])) {
            String lk = k.toLowerCase(Locale.ROOT);
            net.minecraft.nbt.Tag v = tag.get(k);
            if ("ChargedProjectiles".equals(k)) {
                tag.remove(k); // a flecha carregada da besta (o jeito do vanilla, que mod de besta copia)
            } else if (v instanceof net.minecraft.nbt.NumericTag
                    && (lk.contains("ammo") || lk.contains("bullet") || "charged".equals(lk))) {
                tag.put(k, zero(v)); // AmmoCount, GunCurrentAmmoCount, HasBulletInBarrel, Charged: vazio
            }
        }
        if (tag.isEmpty()) out.setTag(null);
        return out;
    }

    /** O mesmo tipo de número que estava lá, valendo zero (pra não confundir o mod da arma). */
    private static net.minecraft.nbt.Tag zero(net.minecraft.nbt.Tag v) {
        return switch (v.getId()) {
            case net.minecraft.nbt.Tag.TAG_BYTE -> net.minecraft.nbt.ByteTag.valueOf((byte) 0);
            case net.minecraft.nbt.Tag.TAG_SHORT -> net.minecraft.nbt.ShortTag.valueOf((short) 0);
            case net.minecraft.nbt.Tag.TAG_LONG -> net.minecraft.nbt.LongTag.valueOf(0L);
            case net.minecraft.nbt.Tag.TAG_FLOAT -> net.minecraft.nbt.FloatTag.valueOf(0F);
            case net.minecraft.nbt.Tag.TAG_DOUBLE -> net.minecraft.nbt.DoubleTag.valueOf(0D);
            default -> net.minecraft.nbt.IntTag.valueOf(0);
        };
    }

    /** O estudo, ao abrir o mundo: quantas armas de cada mod (no log de depuração). */
    public static String study() {
        Map<String, Integer> perMod = new TreeMap<>();
        int total = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            if (!isWeapon(item)) continue;
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            perMod.merge(key == null ? "?" : key.getNamespace(), 1, Integer::sum);
            total++;
        }
        return total + " arma(s) de mod" + (perMod.isEmpty() ? "" : " " + perMod);
    }
}
