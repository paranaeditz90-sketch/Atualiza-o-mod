package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;

/**
 * (v0.7.67) A isca (item 7 da fila, o sistema de fuga; LM 24/11: "carne jogada no chão"): caçando alguém, ele vê carne
 * (crua ou assada) que um JOGADOR jogou no chão perto, larga a caça, corre até ela e come: pega com a boca, sacode a
 * cabeça e engole (a comida na boca, como a arma roubada), e fica mordendo o resto; 3 s com 1 pedaço, 4 s com 2, 5 s com
 * 3 ou mais. Depois volta a caçar, e por 30 s não cai em outra isca.
 */
public class FroggyBaitGoal extends Goal {

    static final double RANGE = 14.0D;
    static final double EAT_DIST = 1.9D;
    static final int COOLDOWN = 600;
    static final int MAX_CHASE = 160;
    private static final int GULP = FroggyState.WEAPON_EAT.durationTicks;

    private final FroggydudeEntity froggy;
    @Nullable
    private ItemEntity bait;
    private int chase;
    /** -1 = indo até a isca; senão ticks comendo. */
    private int eat = -1;
    private int eatTotal;
    private long nextAt;

    public FroggyBaitGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Carne no chão, jogada por um jogador. */
    static boolean isBait(ItemEntity e) {
        if (!e.isAlive() || e.getItem().isEmpty() || !(e.getOwner() instanceof Player)) return false;
        FoodProperties food = e.getItem().getFoodProperties(null);
        return food != null && food.isMeat();
    }

    @Override
    public boolean canUse() {
        if (froggy.level().isClientSide || froggy.tickCount % 5 != 0 || froggy.isHolding() || froggy.inSkyRage()) return false;
        if (!(froggy.getTarget() instanceof Player)) return false;
        FroggyState s = froggy.getFroggyState();
        if (s != FroggyState.CHASE && s != FroggyState.IDLE) return false;
        if (froggy.level().getGameTime() < nextAt) return false;
        bait = froggy.level().getEntitiesOfClass(ItemEntity.class, froggy.getBoundingBox().inflate(RANGE), FroggyBaitGoal::isBait)
                .stream().filter(froggy::hasLineOfSight).min(Comparator.comparingDouble(froggy::distanceToSqr)).orElse(null);
        return bait != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (eat >= 0) return eat < eatTotal;
        return bait != null && bait.isAlive() && chase < MAX_CHASE;
    }

    @Override
    public void start() {
        chase = 0;
        eat = -1;
        froggy.setFroggyState(FroggyState.CHASE);
        if (bait != null) {
            froggy.brainLog("isca: carne no chão (" + bait.getItem().getCount() + "), vou comer");
        }
    }

    @Override
    public void tick() {
        if (eat < 0) {
            if (bait == null) return;
            chase++;
            froggy.getLookControl().setLookAt(bait, 30.0F, 30.0F);
            if (froggy.distanceTo(bait) <= EAT_DIST) {
                beginEat();
            } else if (chase % 10 == 1) {
                froggy.getNavigation().moveTo(bait, 1.4D);
            }
            return;
        }
        eat++;
        froggy.getNavigation().stop();
        froggy.setDeltaMovement(0, froggy.getDeltaMovement().y, 0);
        if (eat < GULP) {
            if (FroggyStealGoal.isBite(eat)) {
                froggy.playSound(SoundEvents.GENERIC_EAT, eat == FroggyStealGoal.FIRST_BITE ? 1.3F : 1.0F,
                        0.6F + froggy.getRandom().nextFloat() * 0.2F);
            }
            if (eat == FroggyStealGoal.SWALLOW) {
                froggy.setMouthItem(ItemStack.EMPTY);
                froggy.playSound(SoundEvents.PLAYER_BURP, 1.0F, 0.6F);
            }
        } else {
            // o resto: mordendo de pé
            if (eat == GULP) froggy.setFroggyState(FroggyState.FEEDING);
            if (eat % 7 == 0) froggy.onChew();
        }
    }

    private void beginEat() {
        ItemEntity b = bait;
        if (b == null) return;
        int count = b.getItem().getCount();
        ItemStack piece = b.getItem().copyWithCount(1);
        b.discard(); // come a pilha toda
        eatTotal = 20 * (2 + Math.min(count, 3)); // 3, 4 ou 5 s
        eat = 0;
        froggy.getNavigation().stop();
        froggy.setMouthItem(piece);
        froggy.setFroggyState(FroggyState.WEAPON_EAT);
        froggy.openMouth(GULP);
        froggy.brainLog("isca: comendo " + count + " (" + eatTotal / 20 + " s)");
    }

    @Override
    public void stop() {
        if (eat >= 0) {
            froggy.setMouthItem(ItemStack.EMPTY);
            FroggyState s = froggy.getFroggyState();
            if (s == FroggyState.WEAPON_EAT || s == FroggyState.FEEDING) froggy.setFroggyState(FroggyState.CHASE);
            nextAt = froggy.level().getGameTime() + COOLDOWN;
        } else {
            nextAt = froggy.level().getGameTime() + 100; // não chegou (longe, sem caminho): tenta de novo daqui a pouco
        }
        bait = null;
        eat = -1;
        froggy.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
