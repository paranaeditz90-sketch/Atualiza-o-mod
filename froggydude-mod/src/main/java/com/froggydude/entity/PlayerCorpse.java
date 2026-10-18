package com.froggydude.entity;

import com.froggydude.init.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Extremo: o que sobrou de quem ele matou na cutscene (LM: "FroggyDude comendo
 * o corpo do player e achando delicioso e até brincando com o corpo do player
 * (física melhorada)"). O corpo com a skin do jogador, de costas, sem os
 * braços e sem a cabeça, encharcado; sangra pelos tocos. Tem física: cai,
 * quica, escorrega, e quando ele joga pro alto sai girando (o giro e a
 * inclinação vão sincronizados), até assentar de costas ou de bruços.
 *
 * Vai sumindo enquanto ele come ({@link #getEaten}: pernas primeiro); comido
 * tudo, some deixando a poça. Sozinho dura 20 minutos.
 */
public class PlayerCorpse extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(PlayerCorpse.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_NAME =
            SynchedEntityData.defineId(PlayerCorpse.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_SLIM =
            SynchedEntityData.defineId(PlayerCorpse.class, EntityDataSerializers.BOOLEAN);
    /** 1 = sem braço esquerdo, 2 = sem o direito, 4 = sem cabeça. */
    private static final EntityDataAccessor<Byte> DATA_MISSING =
            SynchedEntityData.defineId(PlayerCorpse.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_EATEN =
            SynchedEntityData.defineId(PlayerCorpse.class, EntityDataSerializers.FLOAT);
    /** Pra onde aponta o pescoço (yaw do Minecraft). */
    private static final EntityDataAccessor<Float> DATA_HEAD_YAW =
            SynchedEntityData.defineId(PlayerCorpse.class, EntityDataSerializers.FLOAT);
    /** Giro em volta do eixo dos ombros (cambalhota) e de lado (rolar), em graus. */
    private static final EntityDataAccessor<Float> DATA_FLIP =
            SynchedEntityData.defineId(PlayerCorpse.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL =
            SynchedEntityData.defineId(PlayerCorpse.class, EntityDataSerializers.FLOAT);
    /** Preso na boca dele (id do FroggyDude) ou -1. */
    private static final EntityDataAccessor<Integer> DATA_HELD_BY =
            SynchedEntityData.defineId(PlayerCorpse.class, EntityDataSerializers.INT);

    private static final int LIFE = 20 * 60 * 20;

    private float spinFlip;
    private float spinRoll;
    private int age;
    // só no cliente: pra interpolar o giro
    public float flipO, rollO;

    public PlayerCorpse(EntityType<? extends PlayerCorpse> type, Level level) {
        super(type, level);
        this.blocksBuilding = false;
    }

    @Nullable
    public static PlayerCorpse of(ServerLevel level, ServerPlayer victim, Vec3 at, float headYaw) {
        PlayerCorpse c = ModEntityTypes.CORPSE.get().create(level);
        if (c == null) return null;
        c.setPos(at.x, at.y + 0.05D, at.z);
        c.entityData.set(DATA_OWNER, Optional.of(victim.getUUID()));
        c.entityData.set(DATA_NAME, victim.getScoreboardName());
        c.entityData.set(DATA_MISSING, (byte) 7);
        c.entityData.set(DATA_HEAD_YAW, headYaw);
        c.setYRot(headYaw);
        return c;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER, Optional.empty());
        this.entityData.define(DATA_NAME, "");
        this.entityData.define(DATA_SLIM, false);
        this.entityData.define(DATA_MISSING, (byte) 0);
        this.entityData.define(DATA_EATEN, 0F);
        this.entityData.define(DATA_HEAD_YAW, 0F);
        this.entityData.define(DATA_FLIP, 0F);
        this.entityData.define(DATA_ROLL, 0F);
        this.entityData.define(DATA_HELD_BY, -1);
    }

    public Optional<UUID> getOwner() {
        return this.entityData.get(DATA_OWNER);
    }

    public String getOwnerName() {
        return this.entityData.get(DATA_NAME);
    }

    public int getMissing() {
        return this.entityData.get(DATA_MISSING);
    }

    public float getEaten() {
        return this.entityData.get(DATA_EATEN);
    }

    public float getHeadYaw() {
        return this.entityData.get(DATA_HEAD_YAW);
    }

    public float getFlip() {
        return this.entityData.get(DATA_FLIP);
    }

    public float getRoll() {
        return this.entityData.get(DATA_ROLL);
    }

    public int getHeldBy() {
        return this.entityData.get(DATA_HELD_BY);
    }

    public void setHeldBy(@Nullable Entity holder) {
        this.entityData.set(DATA_HELD_BY, holder == null ? -1 : holder.getId());
    }

    /** Na boca dele: quem segura vira o corpo (cabeça pra boca, pendurado). */
    public void hold(float headYaw, float flip, float roll) {
        this.entityData.set(DATA_HEAD_YAW, headYaw);
        this.entityData.set(DATA_FLIP, flip);
        this.entityData.set(DATA_ROLL, roll);
        this.spinFlip = 0F;
        this.spinRoll = 0F;
    }

    /** Uma mordida: some um pedaço (as pernas vão primeiro), espirra e faz poça. */
    public void bite(float amount) {
        float e = Math.min(1F, getEaten() + amount);
        this.entityData.set(DATA_EATEN, e);
        Vec3 c = this.position().add(0, 0.2D, 0);
        BloodFx.burst(this.level(), c, new Vec3(0, 1, 0), 30, 0.32F, BloodFx.RED, this);
        BloodFx.pool(this.level(), this.position(), 0.8F);
        if (e >= 1F) {
            BloodFx.burst(this.level(), c, new Vec3(0, 1, 0), 50, 0.4F, BloodFx.RED, this);
            BloodFx.pool(this.level(), this.position(), 2.4F);
            this.discard();
        }
    }

    /** Jogado pro alto (brincando): sai girando. */
    public void toss(Vec3 velocity, float flipSpeed, float rollSpeed) {
        setHeldBy(null);
        this.setDeltaMovement(velocity);
        this.spinFlip = flipSpeed;
        this.spinRoll = rollSpeed;
        this.hasImpulse = true;
    }

    @Override
    public void tick() {
        super.tick();
        flipO = getFlip();
        rollO = getRoll();
        if (this.level().isClientSide) return;
        age++;
        if (age > LIFE) {
            this.discard();
            return;
        }
        int held = getHeldBy();
        if (held >= 0) {
            // na boca dele: quem mexe é a FroggyCorpseGoal (balançando como cachorro)
            if (!(this.level().getEntity(held) instanceof FroggydudeEntity)) setHeldBy(null);
            return;
        }
        Vec3 v = this.getDeltaMovement();
        if (!this.isNoGravity()) v = v.add(0, -0.06D, 0);
        this.setDeltaMovement(v);
        boolean wasGround = this.onGround();
        this.move(MoverType.SELF, this.getDeltaMovement());
        v = this.getDeltaMovement();
        if (this.onGround()) {
            if (!wasGround && v.y < -0.25D) {
                // caiu de cima: espirra e quica um pouco (corpo mole, não bola)
                BloodFx.burst(this.level(), this.position().add(0, 0.2D, 0), new Vec3(0, 1, 0), 18, 0.25F, BloodFx.RED, this);
                BloodFx.pool(this.level(), this.position(), 1.2F);
                this.playSound(net.minecraft.sounds.SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
                v = new Vec3(v.x * 0.5D, -v.y * 0.25D, v.z * 0.5D);
                spinFlip *= 0.4F;
                spinRoll *= 0.4F;
            } else {
                v = v.multiply(0.6D, 1D, 0.6D);
                // assenta: de costas (0) ou de bruços (180), o que estiver mais perto
                spinFlip *= 0.5F;
                spinRoll *= 0.5F;
                settle();
            }
        } else {
            v = v.multiply(0.98D, 0.98D, 0.98D);
        }
        if (this.isInWater()) v = v.multiply(0.8D, 0.8D, 0.8D).add(0, 0.03D, 0);
        this.setDeltaMovement(v);
        if (Math.abs(spinFlip) > 0.01F) this.entityData.set(DATA_FLIP, Mth.wrapDegrees(getFlip() + spinFlip));
        if (Math.abs(spinRoll) > 0.01F) this.entityData.set(DATA_ROLL, Mth.wrapDegrees(getRoll() + spinRoll));
        // fresco: os tocos ainda pingam
        if (age < 1200 && age % 6 == 0) {
            BloodFx.spray(this.level(), this.position().add(0, 0.15D, 0), new Vec3(0, -1, 0), 1, 0.04F);
            if (age % 60 == 0 && this.onGround()) BloodFx.pool(this.level(), this.position(), 0.5F);
        }
    }

    private void settle() {
        float flip = getFlip();
        float target = Math.abs(Mth.wrapDegrees(flip)) < 90F ? 0F : 180F;
        float d = Mth.wrapDegrees(target - flip);
        if (Math.abs(d) > 0.5F) this.entityData.set(DATA_FLIP, Mth.wrapDegrees(flip + d * 0.3F));
        float roll = getRoll();
        if (Math.abs(roll) > 0.5F) this.entityData.set(DATA_ROLL, roll * 0.7F);
    }

    /** Socado/chutado: espirra e é empurrado (dá pra ficar brincando com o corpo também). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) return false;
        Entity e = source.getEntity();
        if (e != null) {
            Vec3 push = this.position().subtract(e.position()).multiply(1, 0, 1).normalize().scale(0.35D);
            this.setDeltaMovement(this.getDeltaMovement().add(push.x, 0.25D, push.z));
            this.spinRoll += (this.random.nextFloat() - 0.5F) * 30F;
            BloodFx.burst(this.level(), this.position().add(0, 0.3D, 0), push.add(0, 0.6D, 0), 16, 0.25F, BloodFx.RED, e);
            this.hasImpulse = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) this.entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
        this.entityData.set(DATA_NAME, tag.getString("Name"));
        this.entityData.set(DATA_SLIM, tag.getBoolean("Slim"));
        this.entityData.set(DATA_MISSING, tag.getByte("Missing"));
        this.entityData.set(DATA_EATEN, tag.getFloat("Eaten"));
        this.entityData.set(DATA_HEAD_YAW, tag.getFloat("HeadYaw"));
        this.entityData.set(DATA_FLIP, tag.getFloat("Flip"));
        this.age = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        getOwner().ifPresent(u -> tag.putUUID("Owner", u));
        tag.putString("Name", getOwnerName());
        tag.putBoolean("Slim", this.entityData.get(DATA_SLIM));
        tag.putByte("Missing", (byte) getMissing());
        tag.putFloat("Eaten", getEaten());
        tag.putFloat("HeadYaw", getHeadYaw());
        tag.putFloat("Flip", getFlip());
        tag.putInt("Age", age);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /** Centro do tronco (pra ele morder e pra câmera). */
    public Vec3 torso() {
        return this.position().add(0, 0.15D, 0);
    }

    public BlockPos feet() {
        return this.blockPosition();
    }
}
