package com.froggydude.event;

import com.froggydude.brain.ModWeapons;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModDamageTypes;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.player.FinisherTracker;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * (v0.7.30) A fúria do Avaritia (LM, 10/11: "Armaduras e ferramentas de avaritia o froggydude fica muito bravo"). Na
 * IA Extra, Máx e Real, contra quem tem qualquer item do Avaritia (FroggydudeEntity.furyAgainst):
 * <ul>
 * <li>a proteção do Avaritia não vale contra ele. O Re:Avaritia (conferido no jar) cancela, na prioridade HIGHEST, o
 * ataque e o dano em quem veste a armadura infinita inteira ou usa a elytra infinita, e a morte (devolvendo a vida); e
 * zera o que sobra do dano na NORMAL. Aqui, LOGO DEPOIS dele, isso é desfeito quando quem bate é ele: o ataque, o
 * dano e a morte na HIGH; o dano zerado volta no começo do ouvinte do golpe final (FinisherEvents.onDamage, LOW), o
 * único jeito de ficar garantido entre a NORMAL do Avaritia e a decisão da cutscene. Tudo que vem depois vê o golpe
 * como ele é: o golpe que mataria vira a cutscene, como sempre;</li>
 * <li>o triplo da força (e o triplo da velocidade, no FroggydudeEntity);</li>
 * <li>bater nele com coisa do Avaritia não faz nada (raramente um arranhão): o ataque é cancelado ANTES da espada
 * infinita rodar (ela zera a vida e mata direto, sem passar pelo dano normal), nos dois lados.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AvaritiaFury {

    /** O triplo da força. */
    private static final float FURY_DAMAGE = 3.0F;

    /** O golpe dele (já triplicado, antes da armadura) e a absorção que a pessoa tinha, no tick do golpe. */
    private record Pending(float amount, float absorption, long tick) {
    }

    /** Por pessoa; só vale no mesmo tick (o golpe que parou no meio não deixa sobra pro próximo). */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private AvaritiaFury() {
    }

    /** Quem bateu foi ele, com a fúria contra essa pessoa? (só no servidor) */
    @Nullable
    private static FroggydudeEntity furious(DamageSource source, LivingEntity victim) {
        return !victim.level().isClientSide && source.getEntity() instanceof FroggydudeEntity f && f.furyAgainst(victim)
                ? f : null;
    }

    /** Na cutscene do golpe final, a regra dela vale: só o "devorado" do fim chega na vítima. */
    private static boolean finisherBlocks(LivingEntity victim, DamageSource source) {
        return victim instanceof Player p && FinisherTracker.isVictim(p) && !source.is(ModDamageTypes.DEVOURED);
    }

    /**
     * O jogador bate nele com coisa do Avaritia: nada acontece (a espada infinita nem chega a rodar). Cancela também no
     * cliente: lá a espada "matava" ele só na tela (caído pra sempre, com o servidor achando que estava vivo).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof FroggydudeEntity froggy)) return;
        Player p = event.getEntity();
        if (!FroggyConfig.ai().stealsWeapons() || !ModWeapons.isAvaritia(p.getMainHandItem())) return;
        event.setCanceled(true);
        if (!froggy.level().isClientSide) froggy.avaritiaHit(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.isCanceled() && furious(event.getSource(), victim) != null && !finisherBlocks(victim, event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (furious(source, victim) == null) return;
        if (event.isCanceled()) {
            if (finisherBlocks(victim, source)) return;
            event.setCanceled(false);
        }
        float amount = event.getAmount();
        // o triplo; o "devorado" do fim da cutscene (dano infinito) fica como está
        if (amount > 0F && amount < 1.0E6F) amount *= FURY_DAMAGE;
        event.setAmount(amount);
        // vestindo peça do Avaritia (armadura ou a elytra infinita): é quando a proteção dele liga
        if (victim instanceof Player p && ModWeapons.wearsAvaritia(p)) {
            PENDING.put(victim.getUUID(), new Pending(amount, victim.getAbsorptionAmount(), victim.level().getGameTime()));
        } else {
            PENDING.remove(victim.getUUID());
        }
    }

    /**
     * A armadura do Avaritia não protege contra ele: o dano volta a ser o que a pessoa levaria SEM as peças do
     * Avaritia (o resto da armadura, a Resistência e a Proteção das outras peças continuam valendo), e a absorção (os
     * corações amarelos) só gasta uma vez. Chamado pelo FinisherEvents.onDamage (LOW), antes de ele decidir se o golpe
     * mata: o Avaritia zera o dano na NORMAL, então aqui tem que ser depois dela e antes da cutscene.
     */
    public static void undoAvaritiaArmor(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Pending pend = PENDING.remove(victim.getUUID());
        if (pend == null || pend.tick() != victim.level().getGameTime() || !(victim instanceof Player p)) return;
        DamageSource source = event.getSource();
        if (furious(source, victim) == null || finisherBlocks(victim, source)) return;
        event.setCanceled(false);
        float real = withoutAvaritia(p, source, pend.amount());
        // o jogo já tirou da absorção uma parte (do dano que a armadura infinita tinha reduzido); tira o resto agora
        float absorbed = Math.max(0F, pend.absorption() - p.getAbsorptionAmount());
        float left = Math.max(0F, real - absorbed);
        float more = Math.min(p.getAbsorptionAmount(), left);
        if (more > 0F) p.setAbsorptionAmount(p.getAbsorptionAmount() - more);
        event.setAmount(Math.max(event.getAmount(), left - more));
    }

    /**
     * O Avaritia "ressuscitou" quem ele matou (HIGHEST): contra ele, não. Desfeito logo em seguida (HIGH), quem ouve a
     * morte depois (o manhunt, a invasão, o corpo se desmontando, o próprio Avaritia tirando o voo) vê a morte de verdade.
     */
    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.isCanceled() || furious(event.getSource(), event.getEntity()) == null) return;
        event.setCanceled(false);
        event.getEntity().setHealth(0.0F);
    }

    /** O mesmo cálculo do jogo (armadura, Resistência, Proteção), contando só as peças que NÃO são do Avaritia. */
    private static float withoutAvaritia(Player p, DamageSource source, float amount) {
        float armor = p.getArmorValue(), toughness = (float) p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        List<ItemStack> others = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack s = p.getItemBySlot(slot);
            if (s.isEmpty()) continue;
            if (ModWeapons.isAvaritia(s)) {
                armor -= added(s, slot, Attributes.ARMOR);
                toughness -= added(s, slot, Attributes.ARMOR_TOUGHNESS);
            } else {
                others.add(s);
            }
        }
        float dmg = amount;
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            dmg = CombatRules.getDamageAfterAbsorb(dmg, Math.max(0F, armor), Math.max(0F, toughness));
        }
        if (source.is(DamageTypeTags.BYPASSES_EFFECTS)) return dmg;
        MobEffectInstance res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (res != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            dmg = Math.max(dmg * (25 - (res.getAmplifier() + 1) * 5) / 25.0F, 0.0F);
        }
        if (dmg <= 0F || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return dmg;
        int prot = EnchantmentHelper.getDamageProtection(others, source);
        return prot > 0 ? CombatRules.getDamageAfterMagicAbsorb(dmg, prot) : dmg;
    }

    /** Quanto a peça soma nesse atributo (o número que aparece nela: "+8 de armadura"). */
    private static float added(ItemStack s, EquipmentSlot slot, Attribute attribute) {
        float sum = 0F;
        try {
            for (AttributeModifier m : s.getAttributeModifiers(slot).get(attribute)) {
                if (m.getOperation() == AttributeModifier.Operation.ADDITION) sum += (float) m.getAmount();
            }
        } catch (RuntimeException ignored) {
            // peça de mod que quebra pedindo os atributos: conta como zero
        }
        return sum;
    }
}
