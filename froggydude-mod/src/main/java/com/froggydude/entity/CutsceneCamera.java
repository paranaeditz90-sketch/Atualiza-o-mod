package com.froggydude.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

/**
 * Só existe no cliente de quem está na cutscene: o "cinegrafista". A câmera do
 * jogo segue esta entidade (que nunca entra no mundo) e o client.cutscene põe
 * ela no lugar certo a cada quadro. O olho fica na mesma altura do de um
 * jogador em pé (1,62): trocando a câmera do jogador pra ela, o jogo não
 * "desce" a câmera nos primeiros ticks.
 */
public class CutsceneCamera extends Entity {

    public CutsceneCamera(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 1.62F;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
