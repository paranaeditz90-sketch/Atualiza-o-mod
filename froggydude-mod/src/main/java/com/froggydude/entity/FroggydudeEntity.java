package com.froggydude.entity;

import com.froggydude.entity.ai.FroggyCombatGoal;
import com.froggydude.entity.ai.FroggyFeedGoal;
import com.froggydude.entity.ai.FroggyFrenzyGoal;
import com.froggydude.init.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.UUID;

/**
 * O chefe Froggydude: escolhe entre vários ataques de língua e de impacto,
 * entra na fase 2 quando está machucado, e caça mobs pra devorar quando a
 * vida está muito baixa.
 *
 * A "IA de qual ataque usar" mora nas Goals (pacote entity.ai). Esta classe
 * guarda o estado sincronizado (pra animação e textura) e executa as ações
 * que as Goals disparam: sangue, cura ao comer, etc.
 */
public class FroggydudeEntity extends Monster implements GeoEntity {

    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_GORE_STAGE =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_SKIN_VARIANT =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_FRENZY =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TIRED =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_TONGUE_LEN =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.FLOAT);

    private static final UUID FRENZY_SPEED_ID = UUID.fromString("6f3a2e6a-6b9e-4e9a-8f9a-6b6a1a2f9e11");
    private static final UUID FRENZY_TIRED_ID = UUID.fromString("1c1a9b0e-2d3f-4a5b-9c8d-7e6f5a4b3c2d");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // --- só no servidor (as Goals rodam só lá) ---
    private int stateTicks = 0;
    @Nullable
    private FroggyState lastAttack = null;
    private int attackCooldown = 0;
    private final EnumMap<FroggyState, Integer> cooldowns = new EnumMap<>(FroggyState.class);
    private int frenzyTicksLeft = 0;
    private int frenzyTiredTicksLeft = 0;
    private int frenzyCooldown = 0;
    private boolean phase2Started = false;
    private int noFallTicks = 0;
    @Nullable
    private LivingEntity feedTarget;
    private int feedSearchCooldown = 0;

    public FroggydudeEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 25;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, (byte) FroggyState.IDLE.ordinal());
        this.entityData.define(DATA_GORE_STAGE, (byte) 0);
        this.entityData.define(DATA_SKIN_VARIANT, (byte) SkinVariant.NORMAL.ordinal());
        this.entityData.define(DATA_FRENZY, false);
        this.entityData.define(DATA_TIRED, false);
        this.entityData.define(DATA_TONGUE_LEN, 0.0F);
    }

    @Override
    protected void registerGoals() {
        // 0: comer tem prioridade sobre tudo, inclusive lutar
        this.goalSelector.addGoal(0, new FroggyFeedGoal(this));
        // 1: fase 2 (contorção + grito + corrida). DESLIGADA na Parte 1 pra testar só o combate.
        // this.goalSelector.addGoal(1, new FroggyFrenzyGoal(this));
        // 2: cérebro de combate - só ataca quando tem certeza que acerta
        this.goalSelector.addGoal(2, new FroggyCombatGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // é um chefe, não some sozinho
    }

    // ---------------- estado sincronizado (animação e textura) ----------------

    public FroggyState getFroggyState() {
        return FroggyState.values()[this.entityData.get(DATA_STATE)];
    }

    public void setFroggyState(FroggyState state) {
        this.entityData.set(DATA_STATE, (byte) state.ordinal());
        this.stateTicks = 0;
    }

    /** Ticks desde que o estado atual começou (só é confiável no servidor). */
    public int getStateTicks() {
        return stateTicks;
    }

    public boolean isVulnerableState() {
        FroggyState s = getFroggyState();
        return s == FroggyState.FEEDING || s == FroggyState.CONTORTING;
    }

    /** 0 a 3 - quanto maior, mais sangue na skin (pela vida perdida). */
    public int getGoreStage() {
        return this.entityData.get(DATA_GORE_STAGE);
    }

    public SkinVariant getSkinVariant() {
        return SkinVariant.byOrdinal(this.entityData.get(DATA_SKIN_VARIANT));
    }

    public void setSkinVariant(SkinVariant variant) {
        this.entityData.set(DATA_SKIN_VARIANT, (byte) variant.ordinal());
    }

    public boolean isFrenzyActive() {
        return this.entityData.get(DATA_FRENZY);
    }

    public boolean isFrenzyTired() {
        return this.entityData.get(DATA_TIRED);
    }

    /** Comprimento da língua em blocos (o modelo estica a língua até aqui). */
    public float getTongueLength() {
        return this.entityData.get(DATA_TONGUE_LEN);
    }

    public void setTongueLength(float blocks) {
        this.entityData.set(DATA_TONGUE_LEN, blocks);
    }

    // ---------------- usado só pelas Goals ----------------

    @Nullable
    public FroggyState getLastAttack() {
        return lastAttack;
    }

    public void setLastAttack(FroggyState state) {
        this.lastAttack = state;
    }

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public void setAttackCooldown(int ticks) {
        this.attackCooldown = ticks;
    }

    /** Cada ataque tem seu próprio tempo de recarga. */
    public boolean isReady(FroggyState attack) {
        return cooldowns.getOrDefault(attack, 0) <= 0;
    }

    public void setCooldown(FroggyState attack, int ticks) {
        cooldowns.put(attack, ticks);
    }

    @Nullable
    public LivingEntity getFeedTarget() {
        return feedTarget;
    }

    public void setFeedTarget(@Nullable LivingEntity target) {
        this.feedTarget = target;
    }

    public int getFeedSearchCooldown() {
        return feedSearchCooldown;
    }

    public void setFeedSearchCooldown(int cooldown) {
        this.feedSearchCooldown = cooldown;
    }

    public boolean isLowHealth() {
        return this.getHealth() < this.getMaxHealth() * 0.3F;
    }

    // ----- fase 2 -----

    /** A fase 2 começa uma vez, quando a vida cai abaixo de 60%. */
    public boolean shouldStartPhase2() {
        return !phase2Started && this.getHealth() < this.getMaxHealth() * 0.6F;
    }

    public void markPhase2Started() {
        this.phase2Started = true;
    }

    public boolean isInPhase2() {
        return phase2Started;
    }

    public int getFrenzyCooldown() {
        return frenzyCooldown;
    }

    public void setFrenzyCooldown(int ticks) {
        this.frenzyCooldown = ticks;
    }

    /** Chamado pela FroggyFrenzyGoal assim que a contorção termina. */
    public void startFrenzy(int durationTicks) {
        this.frenzyTicksLeft = durationTicks;
        this.entityData.set(DATA_FRENZY, true);
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(FRENZY_SPEED_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(
                    FRENZY_SPEED_ID, "froggy_frenzy_speed", 0.9D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** O grito da fase 2: volume 8 é ouvido de bem longe. */
    public void playScream() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.SCREAM.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
    }

    // ----- pulos -----

    /** Durante um pulo de ataque ele não toma dano de queda. */
    public void setNoFallTicks(int ticks) {
        this.noFallTicks = ticks;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (noFallTicks > 0) return false;
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    /** Vira o corpo, a cabeça e o modelo de uma vez pra direção do alvo. */
    public void faceTarget(LivingEntity target) {
        faceDirection(target.getX() - this.getX(), target.getZ() - this.getZ());
    }

    public void faceDirection(double dx, double dz) {
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.yHeadRot = yaw;
        this.yHeadRotO = yaw;
    }

    // ---------------- tick ----------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        stateTicks++;
        if (attackCooldown > 0) attackCooldown--;
        if (feedSearchCooldown > 0) feedSearchCooldown--;
        if (frenzyCooldown > 0) frenzyCooldown--;
        if (noFallTicks > 0) noFallTicks--;
        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));

        // a vida também muda sem dano (cura, /summon, mundo recarregado)
        if (this.tickCount % 20 == 0) {
            updateGoreStage();
        }

        if (frenzyTicksLeft > 0) {
            frenzyTicksLeft--;
            if (frenzyTicksLeft == 0) {
                endFrenzy();
            }
        } else if (frenzyTiredTicksLeft > 0) {
            frenzyTiredTicksLeft--;
            if (frenzyTiredTicksLeft == 0) {
                AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(FRENZY_TIRED_ID);
                this.entityData.set(DATA_TIRED, false);
            }
        }
    }

    private void endFrenzy() {
        this.entityData.set(DATA_FRENZY, false);
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(FRENZY_SPEED_ID);
        this.frenzyTiredTicksLeft = 60;
        this.entityData.set(DATA_TIRED, true);
        speed.addTransientModifier(new AttributeModifier(
                FRENZY_TIRED_ID, "froggy_frenzy_tired", -0.3D, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // ---------------- dano / estágio de sangue / tipo de estrago ----------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean wasFeeding = getFroggyState() == FroggyState.FEEDING;
        if (wasFeeding) {
            amount *= 1.5F; // mais vulnerável enquanto come
        }
        if (isFrenzyActive() && source.is(DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.15F; // quase à prova de flecha durante o frenesi
        }

        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            updateGoreStage();
            if (source.is(DamageTypeTags.IS_FIRE)) {
                setSkinVariant(SkinVariant.BURNED);
            } else if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION)) {
                setSkinVariant(SkinVariant.SMASHED);
            }
            if (wasFeeding) {
                // interrompido comendo: larga a vítima e revida na hora
                setFeedTarget(null);
                setFroggyState(FroggyState.CHASE);
                if (source.getEntity() instanceof LivingEntity attacker) {
                    setTarget(attacker);
                }
            }
        }
        return hurt;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinVariant", getSkinVariant().id);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String id = tag.getString("SkinVariant");
        for (SkinVariant v : SkinVariant.values()) {
            if (v.id.equals(id)) setSkinVariant(v);
        }
        updateGoreStage();
    }

    private void updateGoreStage() {
        float pct = this.getHealth() / this.getMaxHealth();
        byte stage;
        if (pct > 0.75F) stage = 0;
        else if (pct > 0.5F) stage = 1;
        else if (pct > 0.25F) stage = 2;
        else stage = 3;
        if (stage != this.entityData.get(DATA_GORE_STAGE)) {
            this.entityData.set(DATA_GORE_STAGE, stage);
        }
    }

    // ---------------- GeckoLib: animações ----------------
    // Os nomes abaixo precisam bater com as animações do arquivo
    // assets/froggydude/animations/froggydude.animation.json

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ANIM_RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ANIM_TIRED = RawAnimation.begin().thenLoop("tired");
    private static final RawAnimation ANIM_FEED = RawAnimation.begin().thenLoop("feed");
    private static final RawAnimation ANIM_BITE =
            RawAnimation.begin().then("bite", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_WHIP =
            RawAnimation.begin().then("tongue_whip", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_GRAB =
            RawAnimation.begin().then("tongue_grab", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_CAPTURE =
            RawAnimation.begin().then("tongue_capture", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_JUMP_PIN =
            RawAnimation.begin().then("jump_pin", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_HIGH_JUMP =
            RawAnimation.begin().then("high_jump", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_CONTORT =
            RawAnimation.begin().then("contort", Animation.LoopType.HOLD_ON_LAST_FRAME);

    // controlador separado só pro osso da língua (some quando não está atacando com ela)
    private static final RawAnimation TONGUE_HIDDEN = RawAnimation.begin().thenLoop("tongue_hidden");
    private static final RawAnimation TONGUE_WHIP =
            RawAnimation.begin().then("tongue_whip_ext", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation TONGUE_GRAB =
            RawAnimation.begin().then("tongue_grab_ext", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation TONGUE_CAPTURE =
            RawAnimation.begin().then("tongue_capture_ext", Animation.LoopType.HOLD_ON_LAST_FRAME);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::mainPredicate));
        controllers.add(new AnimationController<>(this, "tongue", 0, this::tonguePredicate));
    }

    private PlayState mainPredicate(AnimationState<FroggydudeEntity> state) {
        switch (getFroggyState()) {
            case BITE:
                return state.setAndContinue(ANIM_BITE);
            case TONGUE_WHIP:
                return state.setAndContinue(ANIM_WHIP);
            case TONGUE_GRAB:
                return state.setAndContinue(ANIM_GRAB);
            case TONGUE_CAPTURE:
                return state.setAndContinue(ANIM_CAPTURE);
            case JUMP_PIN:
                return state.setAndContinue(ANIM_JUMP_PIN);
            case HIGH_JUMP:
                return state.setAndContinue(ANIM_HIGH_JUMP);
            case FEEDING:
                return state.setAndContinue(ANIM_FEED);
            case CONTORTING:
                return state.setAndContinue(ANIM_CONTORT);
            default:
                break;
        }
        if (isFrenzyTired()) {
            return state.setAndContinue(ANIM_TIRED);
        }
        if (state.isMoving()) {
            // andando devagar = walk; em velocidade de perseguição (ou na fase 2) = run
            boolean fast = isFrenzyActive() || state.getLimbSwingAmount() > 0.6F;
            return state.setAndContinue(fast ? ANIM_RUN : ANIM_WALK);
        }
        return state.setAndContinue(ANIM_IDLE);
    }

    private PlayState tonguePredicate(AnimationState<FroggydudeEntity> state) {
        switch (getFroggyState()) {
            case TONGUE_WHIP:
                return state.setAndContinue(TONGUE_WHIP);
            case TONGUE_GRAB:
                return state.setAndContinue(TONGUE_GRAB);
            case TONGUE_CAPTURE:
                return state.setAndContinue(TONGUE_CAPTURE);
            default:
                return state.setAndContinue(TONGUE_HIDDEN);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---------------- ações usadas pelas Goals ----------------

    public void spawnBlood(LivingEntity target, int amount) {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    amount, 0.3D, 0.3D, 0.3D, 0.0D);
        }
    }

    public void eatVictim(LivingEntity victim) {
        spawnBlood(victim, 25);
        this.playSound(SoundEvents.GENERIC_EAT, 1.2F, 0.7F);
        victim.hurt(this.damageSources().mobAttack(this), Float.MAX_VALUE / 2F);
        this.heal((float) (this.getMaxHealth() * 0.20D));
        setSkinVariant(SkinVariant.FED);
    }
}
