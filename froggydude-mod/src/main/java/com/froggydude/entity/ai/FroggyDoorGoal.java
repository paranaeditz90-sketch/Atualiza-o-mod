package com.froggydude.entity.ai;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Porta de madeira no caminho (v0.6, LM: "chance dele quebrar a porta ou apenas abrir
 * ela"). Caçando de cara limpa (ou no massacre da vila) ele às vezes arromba no tranco,
 * sem parar: a porta voa em pedaços e quem está dentro ouve. De mansinho (calado, de
 * tocaia), sem alvo ou num mundo sem mobGriefing, abre quietinho como antes.
 */
public class FroggyDoorGoal extends OpenDoorGoal {

    private final FroggydudeEntity froggy;

    public FroggyDoorGoal(FroggydudeEntity froggy) {
        super(froggy, false);
        this.froggy = froggy;
    }

    @Override
    public void start() {
        if (shouldBreak()) {
            ServerLevel level = (ServerLevel) froggy.level();
            level.levelEvent(LevelEvent.SOUND_ZOMBIE_DOOR_CRASH, doorPos, 0);
            level.destroyBlock(doorPos, false, froggy);
            froggy.brainLog("arrombou a porta em " + doorPos.toShortString());
            return;
        }
        super.start();
    }

    private boolean shouldBreak() {
        if (!(froggy.level() instanceof ServerLevel level) || !FroggyConfig.ai().breakIn) return false;
        if (!ForgeEventFactory.getMobGriefingEvent(level, froggy)) return false;
        LivingEntity target = froggy.getTarget();
        if (froggy.isMuted() || froggy.isStakingOut()) return false;
        if (target == null && !froggy.isMassacring()) return false;
        // com raiva (fase 2, o warden que jogou ele longe) quase sempre; no massacre da
        // vila, mais da metade das casas; caçando, 4 em 10
        float chance = froggy.isFrenzyActive() || froggy.isEnraged() ? 0.85F : froggy.isMassacring() ? 0.6F : 0.4F;
        return froggy.getRandom().nextFloat() < chance;
    }
}
