package com.froggydude.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

/**
 * Os aldeões morrem de medo dele (LM: "deveriam ficar apavorados com a presença
 * do froggydude, correr bastante ou tremerem"). Quem vê ele (ou está a 10
 * blocos) entra em pânico - o mesmo pânico de quando vê um zumbi, que faz os
 * aldeões juntarem e chamarem golem - e foge correndo pra longe dele, bem mais
 * rápido que o pânico normal. Do lado do cliente eles tremem
 * (client.VillagerShiver).
 */
public final class VillagerFear {

    /** Até onde eles enxergam ele. */
    private static final double SEE = 24.0D;
    /** Daqui pra perto nem precisa ver: sente. */
    private static final double NEAR = 10.0D;
    /** Correndo: o pânico do jogo usa 0,75 (e a caminhada 0,5). */
    private static final float FLEE_SPEED = 1.0F;
    /** Pra não ficar repetindo o "hmm!" de medo. */
    private static final String SCARED_AT = "froggydude_scared_at";

    private VillagerFear() {
    }

    public static void tick(FroggydudeEntity froggy) {
        if (froggy.tickCount % 2 != 0 || !(froggy.level() instanceof ServerLevel level) || !froggy.isAlive()) return;
        long now = level.getGameTime();
        for (Villager v : level.getEntitiesOfClass(Villager.class, froggy.getBoundingBox().inflate(SEE),
                v -> v.isAlive() && !v.isSleeping() && !v.isPassenger())) {
            // quem já está na boca/embaixo dele não sai correndo
            if (v.getId() == froggy.getFeedVictimId()) continue;
            double d = v.distanceTo(froggy);
            if ((froggy.isFinishing() || froggy.getFroggyState().isOnVictim()) && d < 3.0D) continue;
            if (d > NEAR && !v.hasLineOfSight(froggy)) continue;
            scare(froggy, v, now);
        }
    }

    private static void scare(FroggydudeEntity froggy, Villager v, long now) {
        Brain<Villager> brain = v.getBrain();
        // o sensor de perigo do aldeão só conhece zumbi, illager etc.; a cada 2 ticks a
        // gente lembra ele de que o perigo é o sapo (senão ele "se acalma" no meio da fuga)
        brain.setMemory(MemoryModuleType.NEAREST_HOSTILE, froggy);
        boolean wasCalm = !brain.isActive(Activity.PANIC);
        brain.setActiveActivityIfPossible(Activity.PANIC);
        if (wasCalm || v.tickCount % 10 == 0 || !brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            Vec3 away = LandRandomPos.getPosAway(v, 16, 7, froggy.position());
            if (away != null) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(away, FLEE_SPEED, 0));
        }
        long last = v.getPersistentData().getLong(SCARED_AT);
        if (wasCalm && now - last > 200) {
            v.getPersistentData().putLong(SCARED_AT, now);
            v.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.25F + v.getRandom().nextFloat() * 0.2F);
        }
    }
}
