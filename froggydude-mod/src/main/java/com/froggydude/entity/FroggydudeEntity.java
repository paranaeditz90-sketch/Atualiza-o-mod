package com.froggydude.entity;

import com.froggydude.entity.ai.FroggyCombatGoal;
import com.froggydude.entity.ai.FroggyFeedGoal;
import com.froggydude.entity.ai.FroggyFrenzyGoal;
import com.froggydude.init.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import org.joml.Vector3f;

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
    /** Correndo de quatro (decidido no servidor, com folga, pra animação não ficar piscando). */
    private static final EntityDataAccessor<Boolean> DATA_RUNNING =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BOOLEAN);
    /** Andando (mesma ideia, pra não alternar parado/andando a cada tick). */
    private static final EntityDataAccessor<Boolean> DATA_MOVING =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.BOOLEAN);
    /** Teste: força uma animação em loop (NBT "DebugAnim"). Vazio = normal. */
    private static final EntityDataAccessor<String> DATA_DEBUG_ANIM =
            SynchedEntityData.defineId(FroggydudeEntity.class, EntityDataSerializers.STRING);

    private static final DustParticleOptions BLOOD_DUST =
            new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.4F);
    private static final BlockParticleOption BLOOD_CHUNK =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());

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
    private int movingHold = 0;
    private int pinnedVictimId = -1;
    private int vocalCooldown = 0;
    /** Teste: NBT "DebugHunt" faz ele caçar o porco mais perto (pra filmar de lado). */
    private boolean debugHunt = false;
    /** Teste: NBT "DebugAttack" (ex.: "HIGH_JUMP") faz ele só usar esse ataque. */
    @Nullable
    private FroggyState debugAttack = null;
    private int runningHold = 0;

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
        this.entityData.define(DATA_RUNNING, false);
        this.entityData.define(DATA_MOVING, false);
        this.entityData.define(DATA_DEBUG_ANIM, "");
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
    protected SoundEvent getAmbientSound() {
        return ModSounds.AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HURT.get();
    }

    /** Na maioria das vezes grunhe; às vezes, se não está falando, solta "I love pain". */
    @Override
    protected void playHurtSound(DamageSource source) {
        if (vocalCooldown <= 0 && this.getRandom().nextFloat() < 0.35F) {
            vocalize(ModSounds.LOVE_PAIN.get(), 1.6F);
        } else {
            super.playHurtSound(source);
        }
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160; // um ribbit a cada ~8 s em média
    }

    /** Quando acha uma vítima nova: som de caça (froggydude.hunt). */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity old = this.getTarget();
        super.setTarget(target);
        if (target instanceof Player && old != target && !this.level().isClientSide) {
            vocalize(ModSounds.HUNT.get(), 2.0F);
        }
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

    public boolean isRunning() {
        return this.entityData.get(DATA_RUNNING);
    }

    public boolean isMovingSynced() {
        return this.entityData.get(DATA_MOVING);
    }

    @Nullable
    public FroggyState getDebugAttack() {
        return debugAttack;
    }

    public String getDebugAnim() {
        return this.entityData.get(DATA_DEBUG_ANIM);
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

    /**
     * Onde está o rosto dele de verdade (no modelo), pra câmera de quem está
     * preso mirar ali. Montado, ele fica agachado: a cara fica baixa e à frente.
     */
    public Vec3 getFacePosition(float partialTick) {
        Vec3 pos = this.getPosition(partialTick);
        if (getFroggyState() == FroggyState.PIN_HOLD) {
            float yaw = Mth.rotLerp(partialTick, this.yBodyRotO, this.yBodyRot) * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw);
            double fz = Mth.cos(yaw);
            return pos.add(fx * 0.95D, 0.85D, fz * 0.95D);
        }
        return this.getEyePosition(partialTick);
    }

    /** A vítima derrubada embaixo dele (ou null). */
    @Nullable
    public LivingEntity getPinnedVictim() {
        if (pinnedVictimId < 0) return null;
        Entity e = this.level().getEntity(pinnedVictimId);
        return e instanceof LivingEntity living ? living : null;
    }

    public void setPinnedVictim(@Nullable LivingEntity victim) {
        this.pinnedVictimId = victim == null ? -1 : victim.getId();
    }

    // ---------------- sons dos golpes (chamados pelas Goals) ----------------

    /** Fala/grita, mas não por cima de outra fala (evita encavalar a voz). */
    public void vocalize(SoundEvent sound, float volume) {
        if (vocalCooldown > 0) return;
        this.playSound(sound, volume, 0.95F + this.getRandom().nextFloat() * 0.1F);
        vocalCooldown = 30;
    }

    public void onBiteHit() {
        this.playSound(ModSounds.BITE.get(), 1.0F, 0.9F + this.getRandom().nextFloat() * 0.2F);
    }

    /** O estalo da língua saindo (nos vídeos parece um tiro). */
    public void onTongueOut() {
        this.playSound(ModSounds.TONGUE.get(), 1.3F, 0.9F + this.getRandom().nextFloat() * 0.2F);
    }

    public void onTongueTaste() {
        if (this.getRandom().nextInt(3) == 0) vocalize(ModSounds.TASTE.get(), 1.2F);
    }

    public void onLeapLaunch() {
        if (this.getRandom().nextInt(3) == 0) vocalize(ModSounds.LEAP.get(), 1.0F);
    }

    public void onPinStart() {
        vocalize(ModSounds.PIN.get(), 1.4F);
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
        if (vocalCooldown > 0) vocalCooldown--;
        if (debugHunt && this.getTarget() == null && this.tickCount > 80 && this.tickCount % 10 == 0) {
            this.level().getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class,
                    this.getBoundingBox().inflate(48.0D)).stream().findFirst().ifPresent(this::setTarget);
        }
        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));

        // a vida também muda sem dano (cura, /summon, mundo recarregado)
        if (this.tickCount % 20 == 0) {
            updateGoreStage();
        }
        updateLocomotion();

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

    /**
     * Decide andando/correndo pela velocidade real, com folga (liga rápido,
     * desliga devagar). Assim a animação não fica trocando no meio da corrida.
     */
    private void updateLocomotion() {
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        boolean chasing = this.getTarget() != null || isFrenzyActive();

        if (speed > 0.02D) movingHold = 6;
        else if (movingHold > 0) movingHold--;
        if (chasing && speed > 0.12D) runningHold = 10;
        else if (runningHold > 0) runningHold--;

        boolean moving = movingHold > 0;
        boolean running = runningHold > 0 && moving;
        if (moving != this.entityData.get(DATA_MOVING)) this.entityData.set(DATA_MOVING, moving);
        if (running != this.entityData.get(DATA_RUNNING)) this.entityData.set(DATA_RUNNING, running);
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
        if (hurt && getFroggyState() == FroggyState.PIN_HOLD && amount >= 5.0F
                && source.getEntity() != null && source.getEntity() == getPinnedVictim()) {
            // pancada forte de quem está embaixo: ele é jogado pra trás e solta
            if (getPinnedVictim() instanceof ServerPlayer player) PinTracker.release(player);
            setPinnedVictim(null);
            setFroggyState(FroggyState.CHASE);
            setAttackCooldown(20);
        }
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
        if (!getDebugAnim().isEmpty()) tag.putString("DebugAnim", getDebugAnim());
        if (debugHunt) tag.putBoolean("DebugHunt", true);
        if (debugAttack != null) tag.putString("DebugAttack", debugAttack.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String id = tag.getString("SkinVariant");
        for (SkinVariant v : SkinVariant.values()) {
            if (v.id.equals(id)) setSkinVariant(v);
        }
        updateGoreStage();
        this.entityData.set(DATA_DEBUG_ANIM, tag.getString("DebugAnim"));
        this.debugHunt = tag.getBoolean("DebugHunt");
        this.debugAttack = null;
        for (FroggyState st : FroggyState.values()) {
            if (st.name().equals(tag.getString("DebugAttack"))) this.debugAttack = st;
        }
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
    private static final RawAnimation ANIM_LEAP =
            RawAnimation.begin().then("leap", Animation.LoopType.HOLD_ON_LAST_FRAME);
    private static final RawAnimation ANIM_PIN_HOLD = RawAnimation.begin().thenLoop("pin_hold");
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
        String debug = getDebugAnim();
        if (!debug.isEmpty()) {
            return state.setAndContinue(RawAnimation.begin().thenLoop(debug));
        }
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
            case LEAP:
                return state.setAndContinue(ANIM_LEAP);
            case PIN_HOLD:
                return state.setAndContinue(ANIM_PIN_HOLD);
            default:
                break;
        }
        if (isFrenzyTired()) {
            return state.setAndContinue(ANIM_TIRED);
        }
        if (isMovingSynced()) {
            // perseguindo rápido = de quatro (run); fora disso = walk
            return state.setAndContinue(isRunning() ? ANIM_RUN : ANIM_WALK);
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

    /**
     * Sangue de verdade: respingos vermelhos e pedacinhos, no ponto da mordida
     * (entre a boca dele e a vítima). Nada de partícula em cima da câmera de
     * quem apanhou, senão tampa a visão.
     */
    public void spawnBlood(LivingEntity target, int amount) {
        if (this.level() instanceof ServerLevel server) {
            Vec3 from = target.position().add(0, target.getBbHeight() * 0.5D, 0);
            Vec3 to = this.position().add(0, this.getBbHeight() * 0.45D, 0);
            Vec3 at = from.add(to.subtract(from).scale(0.55D));
            server.sendParticles(BLOOD_DUST, at.x, at.y, at.z, amount, 0.2D, 0.2D, 0.2D, 0.0D);
            server.sendParticles(BLOOD_CHUNK, at.x, at.y, at.z, Math.max(1, amount / 2), 0.15D, 0.15D, 0.15D, 0.12D);
        }
    }

    public void eatVictim(LivingEntity victim) {
        spawnBlood(victim, 25);
        this.playSound(ModSounds.EAT.get(), 1.4F, 1.0F);
        victim.hurt(this.damageSources().mobAttack(this), Float.MAX_VALUE / 2F);
        this.heal((float) (this.getMaxHealth() * 0.20D));
        setSkinVariant(SkinVariant.FED);
    }
}
