package com.froggydude.entity;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.nbt.CompoundTag;
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
 * (v0.7.33) O "gancho" invisível da língua: quem a língua pega (captura ou arremesso) monta nele, em pé, e o
 * servidor move o gancho (FroggyCombatGoal). Igual a um barco: a posição vem do servidor e o cliente de quem está
 * preso só acompanha, então subir 4 blocos e ficar pendurado 2 s fica liso na câmera (por velocidade tremia e o
 * servidor ainda podia expulsar o jogador por "voar"). Shift não solta enquanto a língua segura.
 *
 * Não salva no mundo e se desfaz sozinho quando o FroggyDude larga (morreu, mudou de estado, soltou).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TongueHold extends Entity {

    /** Segurança: nenhum ataque segura mais que isso (a captura dura 70 ticks, o arremesso 108). */
    private static final int MAX_LIFE = 200;

    private int ownerId = -1;
    private int life;

    public TongueHold(EntityType<? extends TongueHold> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    /** Põe o gancho nos pés da vítima e prende ela nele. Devolve null se não deu pra montar. */
    public static TongueHold grab(FroggydudeEntity owner, LivingEntity victim) {
        TongueHold hold = new TongueHold(ModEntityTypes.TONGUE_HOLD.get(), owner.level());
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

    /** O FroggyDude ainda está com a língua nela? */
    public boolean isHolding() {
        if (this.isRemoved() || this.getPassengers().isEmpty()) return false;
        Entity owner = this.level().getEntity(ownerId);
        return owner instanceof FroggydudeEntity f && f.isAlive() && f.getFroggyState().isCapture()
                && f.getCaptureId() == this.getPassengers().get(0).getId();
    }

    @Override
    public void tick() {
        super.tick();
        // no cliente ele vai direto pra posição que chega do servidor (o Entity comum já faz isso): quem está preso
        // anda suave entre um tick e outro, e no giro do arremesso não fica pra trás (com 3 ticks de atraso a
        // vítima saía voando de outro ponto da volta)
        if (this.level().isClientSide) return;
        if (++life > MAX_LIFE || !isHolding()) {
            // acabou de um jeito que a captura não esperava (ele morreu, levou o boom do warden, o golpe final...):
            // destrava quem estava presa e tira a pose deitada da derrubada
            for (Entity e : this.getPassengers()) {
                if (e instanceof ServerPlayer p) {
                    if (p.getForcedPose() == Pose.SWIMMING && !PinTracker.isPinned(p)) p.setForcedPose(null);
                    ModNetwork.sendRelease(p);
                }
                if (Boolean.getBoolean("froggydude.debug")) {
                    System.out.println("[FROGGYDEBUG] gancho: soltou " + e.getName().getString() + " sem a lingua (pose="
                            + (e instanceof ServerPlayer p ? p.getForcedPose() : null) + ")");
                }
            }
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

    /** Pendurada em pé, não sentada (o modelo do jogador senta em barco, cavalo...). */
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

    /** Shift não solta da língua (no servidor; o cliente já ignora as teclas enquanto está preso). */
    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (event.getLevel().isClientSide || !event.isDismounting()) return;
        if (event.getEntityBeingMounted() instanceof TongueHold hold && hold.isHolding()
                && event.getEntityMounting() instanceof Player p && p.isAlive() && p.isShiftKeyDown()) {
            event.setCanceled(true);
        }
    }
}
