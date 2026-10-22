package com.froggydude.event;

import com.froggydude.entity.BloodFx;
import com.froggydude.entity.BloodType;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.player.GorePrefs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gore em todos os mobs (LM: "se o player acerta, por exemplo, um porco com
 * uma espada, o porco vai sair sangue"). Todo golpe que corta (espada, soco,
 * flecha, mordida, explosão, queda feia, espinho) em quem tem sangue manda um
 * golpe de sangue pros jogadores por perto; morrer assim, uma explosão de
 * sangue. Fogo, afogamento, veneno, fome e /kill não sangram. O próprio
 * FroggyDude cuida do sangue dos golpes dele (é mais elaborado).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GoreEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        float amount = event.getAmount();
        DamageSource src = event.getSource();
        if (amount < 0.5F || !cuts(src, amount) || byFroggy(src)) return;
        BloodType type = BloodType.of(victim);
        if (!type.bleeds()) return;
        Vec3[] hd = hitPoint(victim, src);
        BloodFx.hit(victim, src.getEntity(), hd[0], hd[1], amount, type.color);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        DamageSource src = event.getSource();
        if (!cuts(src, 4F) || byFroggy(src) || src.is(com.froggydude.init.ModDamageTypes.DEVOURED)) return;
        BloodType type = BloodType.of(victim);
        if (!type.bleeds()) return;
        Vec3[] hd = hitPoint(victim, src);
        float size = Math.min(1F, victim.getBbWidth() * victim.getBbHeight() / 1.8F);
        int count = (int) (18 + 40 * size);
        Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.5D, 0);
        BloodFx.burst(victim.level(), c, hd[1].add(0, 0.8D, 0), count, 0.25F + 0.15F * size, type.color, victim);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GorePrefs.forget(event.getEntity());
        com.froggydude.player.PlayerSight.forget(event.getEntity());
    }

    /** O golpe tira sangue? */
    static boolean cuts(DamageSource s, float amount) {
        if (s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypeTags.IS_DROWNING) || s.is(DamageTypeTags.IS_FREEZING)
                || s.is(DamageTypeTags.IS_LIGHTNING)) {
            return false;
        }
        if (s.is(DamageTypes.MAGIC) || s.is(DamageTypes.INDIRECT_MAGIC) || s.is(DamageTypes.WITHER)
                || s.is(DamageTypes.STARVE) || s.is(DamageTypes.FELL_OUT_OF_WORLD) || s.is(DamageTypes.IN_WALL)
                || s.is(DamageTypes.CRAMMING) || s.is(DamageTypes.DRAGON_BREATH) || s.is(DamageTypes.GENERIC_KILL)
                || s.is(DamageTypes.DRY_OUT) || s.is(DamageTypes.OUTSIDE_BORDER)) {
            return false;
        }
        // queda: só a feia (quebrou osso); o resto é golpe, flecha, explosão, espinho...
        if (s.is(DamageTypeTags.IS_FALL)) return amount >= 4F;
        if (s.is(DamageTypes.GENERIC)) return amount >= 2F;
        return true;
    }

    private static boolean byFroggy(DamageSource s) {
        return s.getEntity() instanceof FroggydudeEntity || s.getDirectEntity() instanceof FroggydudeEntity;
    }

    /** {ponto do corpo onde acertou, direção do golpe}. */
    static Vec3[] hitPoint(LivingEntity victim, DamageSource src) {
        Vec3 center = victim.position().add(0, victim.getBbHeight() * 0.6D, 0);
        Entity direct = src.getDirectEntity();
        Entity attacker = src.getEntity();
        Vec3 from = null;
        if (direct != null && direct != victim) {
            from = direct == attacker && direct instanceof LivingEntity le ? le.getEyePosition() : direct.position();
        } else if (src.getSourcePosition() != null) {
            from = src.getSourcePosition();
        }
        if (from == null) {
            // queda/espinho: sai de baixo pra cima
            return new Vec3[]{victim.position().add(0, 0.2D, 0), new Vec3(0, 1, 0)};
        }
        Vec3 dir = center.subtract(from);
        if (dir.lengthSqr() < 1.0E-4D) dir = new Vec3(0, 1, 0);
        Vec3 at = victim.getBoundingBox().inflate(0.05D).clip(from, center).orElse(center);
        return new Vec3[]{at, dir.normalize()};
    }
}
