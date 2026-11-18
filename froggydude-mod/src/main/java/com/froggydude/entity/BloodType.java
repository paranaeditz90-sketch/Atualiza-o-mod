package com.froggydude.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.animal.Squid;

import java.util.Locale;

/**
 * De que cor cada bicho sangra - e quem não sangra (esqueleto, slime, golem,
 * blaze, vex...: "mobs que não faz sentido ter sangue", LM). Mob de outro mod
 * cai pelo nome da classe (skeleton/slime/golem/robot/ghost/spirit...) e
 * depois pelo tipo (morto-vivo = sangue podre, artrópode = verde).
 */
public enum BloodType {
    NONE(0x000000),
    RED(0x8E0A0A),
    /** Zumbi, zoglin, phantom: sangue podre, vermelho quase marrom. */
    DARK(0x4B1A0C),
    /** Creeper, aranha, traça. */
    GREEN(0x4E8A1E),
    /** Abelha. */
    YELLOW(0xC4A41A),
    /** Enderman, endermite, dragão, shulker. */
    PURPLE(0x6B1C92),
    /** Warden (sculk). */
    CYAN(0x10868C),
    /** Lula: tinta. */
    INK(0x121518),
    /** Lula brilhante. */
    GLOW(0x2CD6C4);

    public final int color;

    BloodType(int color) {
        this.color = color;
    }

    public boolean bleeds() {
        return this != NONE;
    }

    /**
     * A cor que fica na boca dele depois de comer o bicho (LM: comendo um golem de ferro a
     * boca dele ficou meio branca). Quem sangra deixa a cor do sangue; quem não sangra
     * deixa o que é feito: ferro, neve, osso, gosma, fogo...
     */
    public static int residue(LivingEntity e) {
        BloodType b = of(e);
        if (b.bleeds()) return b.color;
        if (e instanceof IronGolem) return 0xD6D6CE;
        if (e instanceof SnowGolem) return 0xF2F7F7;
        if (e instanceof AbstractSkeleton || e instanceof SkeletonHorse) return 0xE4DFCF;
        if (e instanceof net.minecraft.world.entity.monster.MagmaCube) return 0xC4501C;
        if (e instanceof Slime) return 0x7CC45A;
        if (e instanceof Blaze) return 0xEFAE2E;
        if (e instanceof Ghast) return 0xEEEEEE;
        if (e instanceof Vex) return 0xA8C0D4;
        if (e instanceof Allay) return 0x6FD3F2;
        if (e instanceof WitherBoss) return 0x2C2C2C;
        if (e instanceof ArmorStand) return 0xA88B5A;
        return 0xD0D0D0;
    }

    public static BloodType of(LivingEntity e) {
        if (e instanceof ArmorStand || e instanceof AbstractSkeleton || e instanceof SkeletonHorse
                || e instanceof Slime || e instanceof IronGolem || e instanceof SnowGolem || e instanceof Blaze
                || e instanceof Vex || e instanceof Allay || e instanceof WitherBoss || e instanceof Ghast) {
            return NONE;
        }
        if (e instanceof Zombie || e instanceof ZombieHorse || e instanceof Zoglin || e instanceof Phantom) return DARK;
        if (e instanceof Creeper || e instanceof Spider || e instanceof CaveSpider || e instanceof Silverfish) return GREEN;
        if (e instanceof Bee) return YELLOW;
        if (e instanceof EnderMan || e instanceof Endermite || e instanceof EnderDragon || e instanceof Shulker) return PURPLE;
        if (e instanceof Warden) return CYAN;
        if (e instanceof GlowSquid) return GLOW;
        if (e instanceof Squid) return INK;
        // mobs de outros mods: pelo nome
        String name = e.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("skeleton") || name.contains("slime") || name.contains("golem") || name.contains("robot")
                || name.contains("ghost") || name.contains("spirit") || name.contains("wisp") || name.contains("elemental")
                || name.contains("construct") || name.contains("automaton")) {
            return NONE;
        }
        MobType type = e.getMobType();
        if (type == MobType.UNDEAD) return DARK;
        if (type == MobType.ARTHROPOD) return GREEN;
        return RED;
    }
}
