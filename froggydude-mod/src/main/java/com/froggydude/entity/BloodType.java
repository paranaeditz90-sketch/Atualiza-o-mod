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
