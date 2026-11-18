package com.becomehim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O "gancho" invisível da língua (o mesmo do mod da IA, 0.7.33): quem a língua pega monta nele, em pé, e o servidor
 * move o gancho (LmTongue). A posição vem do servidor e o cliente de quem está preso só acompanha, então subir 4
 * blocos e ficar pendurado fica liso na câmera (e o servidor não expulsa ninguém por "voar"). Shift não solta.
 *
 * Não salva no mundo e se desfaz sozinho quando quem segura larga (morreu, saiu, mudou de jeito).
 */
@Mod.EventBusSubscriber(modid = BecomeHim.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TongueHold extends Entity {

    /** Segurança: segurar dura no máximo 10 s (GRAB_MAX), o arremesso mais um pouco. */
    private static final int MAX_LIFE = LmAttacks.GRAB_MAX + 80;

    private int ownerId = -1;
    private int life;

    public TongueHold(EntityType<? extends TongueHold> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    /** Põe o gancho nos pés da vítima e prende ela nele. Devolve null se não deu pra montar. */
    public static TongueHold grab(ServerPlayer owner, LivingEntity victim) {
        TongueHold hold = new TongueHold(BecomeHim.TONGUE_HOLD.get(), owner.level());
        hold.ownerId = owner.getId();
        hold.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0F);
        if (!owner.level().addFreshEntity(hold)) return null;
        if (!victim.startRiding(hold, true)) {
            hold.discard();
            return null;
        }
        return hold;
    }

    /** Move o gancho (e a vítima junto, na hora). */
    public void moveVictim(Vec3 feet) {
        this.setPos(feet.x, feet.y, feet.z);
        for (Entity p : this.getPassengers()) this.positionRider(p);
    }

    /** Quem segura ainda está com a língua nela? */
    public boolean isHolding() {
        if (this.isRemoved() || this.getPassengers().isEmpty()) return false;
        Entity owner = this.level().getEntity(ownerId);
        return owner instanceof ServerPlayer p && p.isAlive() && LmAttacks.tongueOn(p, this.getPassengers().get(0));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (++life > MAX_LIFE || !isHolding()) {
            this.ejectPassengers();
            this.discard();
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (this.hasPassenger(passenger)) callback.accept(passenger, this.getX(), this.getY(), this.getZ());
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    /** Pendurada em pé, não sentada. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Solta exatamente onde ela está (o padrão procura chão por perto e teleporta). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return passenger.position();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
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

    /** Shift não solta da língua. */
    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (event.getLevel().isClientSide || !event.isDismounting()) return;
        if (event.getEntityBeingMounted() instanceof TongueHold hold && hold.isHolding()
                && event.getEntityMounting() instanceof Player p && p.isAlive() && p.isShiftKeyDown()) {
            event.setCanceled(true);
        }
    }
}
